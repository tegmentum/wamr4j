/*
 * Copyright (c) 2024 Tegmentum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.tegmentum.wamr4j.panama.impl;

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly function.
 *
 * <p>This class represents a callable WebAssembly function using Panama FFI
 * to communicate with the native WAMR library. It provides type-safe
 * function invocation with comprehensive parameter validation.
 *
 * @since 1.0.0
 */
public final class PanamaWebAssemblyFunction implements WebAssemblyFunction {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyFunction.class.getName());

    /** Size of WasmValueFFI struct: i32 type + padding + f64 value = 16 bytes. */
    private static final long WASM_VALUE_FFI_SIZE = 16;


    // Native function handle as MemorySegment
    private volatile MemorySegment nativeHandle;

    // Function metadata
    private final String name;

    // Parent instance for lifecycle tracking
    private final PanamaWebAssemblyInstance parentInstance;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle CALL_FUNCTION;
        static final MethodHandle GET_SIGNATURE;
        static final MethodHandle DESTROY_FUNCTION;

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            CALL_FUNCTION = linker.downcallHandle(
                lookup.find("wamr_function_call").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            final var sigSymbol = lookup.find("wamr_function_get_signature");
            GET_SIGNATURE = sigSymbol.isPresent()
                ? linker.downcallHandle(sigSymbol.get(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                : null;
            final var destroySymbol = lookup.find("wamr_function_destroy");
            DESTROY_FUNCTION = destroySymbol.isPresent()
                ? linker.downcallHandle(destroySymbol.get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
                : null;
        }
    }

    /**
     * Creates a new Panama WebAssembly function wrapper.
     *
     * @param nativeHandle the native function handle, must not be NULL
     * @param name the function name for debugging purposes
     * @param parentInstance the parent instance that owns this function
     */
    public PanamaWebAssemblyFunction(final MemorySegment nativeHandle, final String name,
            final PanamaWebAssemblyInstance parentInstance) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native function handle cannot be null or NULL");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        if (parentInstance == null) {
            throw new IllegalArgumentException("Parent instance cannot be null");
        }

        this.nativeHandle = nativeHandle;
        this.name = name;
        this.parentInstance = parentInstance;
        LOGGER.fine("Created Panama WebAssembly function '" + name + "' with handle: " + nativeHandle);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FunctionSignature getSignature() {
        ensureNotClosed();

        if (Handles.GET_SIGNATURE == null) {
            return new FunctionSignature(new ValueType[0], new ValueType[0]);
        }

        try (final Arena arena = Arena.ofConfined()) {
            // Allocate output buffers for counts and up to 32 param/result types
            final MemorySegment paramCountPtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment resultCountPtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment paramTypesPtr = arena.allocate(ValueLayout.JAVA_INT, 32);
            final MemorySegment resultTypesPtr = arena.allocate(ValueLayout.JAVA_INT, 32);

            final int rc = (int) Handles.GET_SIGNATURE.invoke(
                nativeHandle, paramTypesPtr, paramCountPtr, resultTypesPtr, resultCountPtr);

            if (rc != 0) {
                return new FunctionSignature(new ValueType[0], new ValueType[0]);
            }

            final int paramCount = paramCountPtr.get(ValueLayout.JAVA_INT, 0);
            final int resultCount = resultCountPtr.get(ValueLayout.JAVA_INT, 0);

            final ValueType[] paramTypes = new ValueType[paramCount];
            for (int i = 0; i < paramCount; i++) {
                paramTypes[i] = wasmTypeToValueType(paramTypesPtr.getAtIndex(ValueLayout.JAVA_INT, i));
            }

            final ValueType[] resultTypes = new ValueType[resultCount];
            for (int i = 0; i < resultCount; i++) {
                resultTypes[i] = wasmTypeToValueType(resultTypesPtr.getAtIndex(ValueLayout.JAVA_INT, i));
            }

            return new FunctionSignature(paramTypes, resultTypes);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get function signature for '" + name + "': " + e.getMessage());
            return new FunctionSignature(new ValueType[0], new ValueType[0]);
        }
    }

    @Override
    public Object invoke(final Object... args) throws WasmRuntimeException {
        if (args == null) {
            throw new IllegalArgumentException("Function arguments cannot be null");
        }

        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            // Convert arguments to WasmValueFFI structs
            final MemorySegment argsBuffer = convertArgumentsToNative(args, arena);
            final int maxResults = 8;
            final MemorySegment resultsBuffer = arena.allocate(WASM_VALUE_FFI_SIZE * maxResults);
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            final int resultCount = (int) Handles.CALL_FUNCTION.invoke(
                nativeHandle, argsBuffer, args.length,
                resultsBuffer, maxResults,
                errorBuf, WasmTypes.ERROR_BUF_SIZE);

            if (resultCount < 0) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Function call failed for: " + name : errorMsg);
            }

            // Convert native results back to Java objects
            if (resultCount == 0) {
                return null; // Void function
            }

            final Object[] results = convertResultsFromNative(resultsBuffer, resultCount);
            return results.length == 1 ? results[0] : results;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error calling function: " + name, e);
        }
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;

            if (handle != null && !handle.equals(MemorySegment.NULL) && Handles.DESTROY_FUNCTION != null) {
                try {
                    Handles.DESTROY_FUNCTION.invoke(handle);
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native function '" + name + "': " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get() || parentInstance.isClosed()) {
            throw new IllegalStateException("WebAssembly function has been closed");
        }
    }

    private MemorySegment convertArgumentsToNative(final Object[] args, final Arena arena) {
        final MemorySegment buffer = arena.allocate(WASM_VALUE_FFI_SIZE * Math.max(args.length, 1));

        for (int i = 0; i < args.length; i++) {
            final long offset = i * WASM_VALUE_FFI_SIZE;
            final Object arg = args[i];

            if (arg instanceof Integer) {
                buffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.I32);
                // Value at offset + 8 (aligned to 8 bytes for the union)
                buffer.set(ValueLayout.JAVA_INT, offset + 8, (Integer) arg);
            } else if (arg instanceof Long) {
                buffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.I64);
                buffer.set(ValueLayout.JAVA_LONG, offset + 8, (Long) arg);
            } else if (arg instanceof Float) {
                buffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.F32);
                buffer.set(ValueLayout.JAVA_FLOAT, offset + 8, (Float) arg);
            } else if (arg instanceof Double) {
                buffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.F64);
                buffer.set(ValueLayout.JAVA_DOUBLE, offset + 8, (Double) arg);
            } else {
                throw new IllegalArgumentException(
                    "Unsupported argument type: " + (arg == null ? "null" : arg.getClass().getName()));
            }
        }

        return buffer;
    }

    private Object[] convertResultsFromNative(final MemorySegment resultsBuffer, final int count) {
        final List<Object> results = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final long offset = i * WASM_VALUE_FFI_SIZE;
            final int type = resultsBuffer.get(ValueLayout.JAVA_INT, offset);

            switch (type) {
                case WasmTypes.I32:
                    results.add(resultsBuffer.get(ValueLayout.JAVA_INT, offset + 8));
                    break;
                case WasmTypes.I64:
                    results.add(resultsBuffer.get(ValueLayout.JAVA_LONG, offset + 8));
                    break;
                case WasmTypes.F32:
                    results.add(resultsBuffer.get(ValueLayout.JAVA_FLOAT, offset + 8));
                    break;
                case WasmTypes.F64:
                    results.add(resultsBuffer.get(ValueLayout.JAVA_DOUBLE, offset + 8));
                    break;
                default:
                    LOGGER.warning("Unknown result type: " + type);
                    results.add(0);
                    break;
            }
        }

        return results.toArray();
    }

    private static ValueType wasmTypeToValueType(final int type) {
        switch (type) {
            case WasmTypes.I32:
                return ValueType.I32;
            case WasmTypes.I64:
                return ValueType.I64;
            case WasmTypes.F32:
                return ValueType.F32;
            case WasmTypes.F64:
                return ValueType.F64;
            default:
                return ValueType.I32;
        }
    }

    @Override
    public boolean isValid() {
        return !parentInstance.isClosed() && !closed.get()
            && nativeHandle != null && !nativeHandle.equals(MemorySegment.NULL);
    }
}
