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
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly function.
 *
 * <p>This class represents a callable WebAssembly function using Panama FFI
 * to communicate with the native WAMR library. It provides type-safe
 * function invocation with comprehensive parameter validation.
 *
 * <p>For common function signatures (e.g. {@code (i32, i32) -> i32}),
 * typed fast-path native calls bypass Arena allocation and WasmValueFFI
 * marshalling entirely, passing primitives directly to the native layer.
 *
 * @since 1.0.0
 */
public final class PanamaWebAssemblyFunction implements WebAssemblyFunction {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyFunction.class.getName());

    /** Size of WasmValueFFI struct: i32 type + padding + f64 value = 16 bytes. */
    private static final long WASM_VALUE_FFI_SIZE = 16;

    /** Size of the per-call error buffer used by fast-path calls. */
    private static final int FAST_ERROR_BUF_SIZE = 256;

    // Native function handle as MemorySegment
    private volatile MemorySegment nativeHandle;

    // Function metadata
    private final String name;

    // Parent instance for lifecycle tracking
    private final PanamaWebAssemblyInstance parentInstance;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Cached signature (loaded lazily, like JNI implementation)
    private volatile FunctionSignature cachedSignature;

    // Cached fast path for optimized invocation dispatch
    private volatile FastPath fastPath;

    // Pre-allocated buffers for fast-path calls (one per function, owned by fastArena)
    private final Arena fastArena;
    private final MemorySegment fastErrorBuf;
    private final MemorySegment fastResultI32;
    private final MemorySegment fastResultI64;
    private final MemorySegment fastResultF64;

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle CALL_FUNCTION;
        static final MethodHandle GET_SIGNATURE;
        static final MethodHandle DESTROY_FUNCTION;

        // Typed fast-path handles
        static final MethodHandle CALL_V_V;
        static final MethodHandle CALL_V_I;
        static final MethodHandle CALL_I_V;
        static final MethodHandle CALL_I_I;
        static final MethodHandle CALL_II_V;
        static final MethodHandle CALL_II_I;
        static final MethodHandle CALL_III_I;
        static final MethodHandle CALL_J_J;
        static final MethodHandle CALL_JJ_J;
        static final MethodHandle CALL_DD_D;

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

            // Typed fast-path handles — resolve optionally
            CALL_V_V = resolveOptional(lookup, linker, "wamr_function_call_v_v",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_V_I = resolveOptional(lookup, linker, "wamr_function_call_v_i",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_I_V = resolveOptional(lookup, linker, "wamr_function_call_i_v",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_I_I = resolveOptional(lookup, linker, "wamr_function_call_i_i",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_II_V = resolveOptional(lookup, linker, "wamr_function_call_ii_v",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_II_I = resolveOptional(lookup, linker, "wamr_function_call_ii_i",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_III_I = resolveOptional(lookup, linker, "wamr_function_call_iii_i",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_J_J = resolveOptional(lookup, linker, "wamr_function_call_j_j",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_JJ_J = resolveOptional(lookup, linker, "wamr_function_call_jj_j",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_DD_D = resolveOptional(lookup, linker, "wamr_function_call_dd_d",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        }

        private static MethodHandle resolveOptional(final SymbolLookup lookup,
                final Linker linker, final String name, final FunctionDescriptor desc) {
            final Optional<MemorySegment> sym = lookup.find(name);
            return sym.isPresent() ? linker.downcallHandle(sym.get(), desc) : null;
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

        // Pre-allocate long-lived buffers for fast-path calls (confined = no atomic overhead)
        this.fastArena = Arena.ofConfined();
        this.fastErrorBuf = fastArena.allocate(FAST_ERROR_BUF_SIZE);
        this.fastResultI32 = fastArena.allocate(ValueLayout.JAVA_INT);
        this.fastResultI64 = fastArena.allocate(ValueLayout.JAVA_LONG);
        this.fastResultF64 = fastArena.allocate(ValueLayout.JAVA_DOUBLE);

        LOGGER.fine("Created Panama WebAssembly function '" + name + "' with handle: " + nativeHandle);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FunctionSignature getSignature() {
        ensureNotClosed();

        FunctionSignature signature = cachedSignature;
        if (signature == null) {
            synchronized (this) {
                signature = cachedSignature;
                if (signature == null) {
                    signature = fetchSignature();
                    cachedSignature = signature;
                }
            }
        }
        return signature;
    }

    @Override
    public Object invoke(final Object... args) throws WasmRuntimeException {
        if (args == null) {
            throw new IllegalArgumentException("Function arguments cannot be null");
        }

        ensureNotClosed();

        try {
            return invokeFastPath(args);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error calling function: " + name, e);
        }
    }

    /**
     * Dispatches to a typed native fast path when the function signature matches
     * a known pattern, bypassing Arena allocation and WasmValueFFI marshalling.
     * Falls back to the generic path for unrecognized signatures.
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private Object invokeFastPath(final Object... args) throws Throwable {
        final FastPath path = resolveFastPath();
        final MemorySegment handle = nativeHandle;

        switch (path) {
            case V_V:
                return invokeVoidVoid(handle);
            case V_I:
                return invokeVoidI32(handle);
            case I_V:
                return invokeI32Void(handle, args);
            case I_I:
                return invokeI32I32(handle, args);
            case II_V:
                return invokeII32Void(handle, args);
            case II_I:
                return invokeII32I32(handle, args);
            case III_I:
                return invokeIII32I32(handle, args);
            case J_J:
                return invokeJ64J64(handle, args);
            case JJ_J:
                return invokeJJ64J64(handle, args);
            case DD_D:
                return invokeDD64D64(handle, args);
            default:
                return invokeGeneric(args);
        }
    }

    private Object invokeVoidVoid(final MemorySegment handle) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_V_V.invoke(handle, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return null;
    }

    private Object invokeVoidI32(final MemorySegment handle) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_V_I.invoke(handle, fastResultI32,
                fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultI32.get(ValueLayout.JAVA_INT, 0);
    }

    private Object invokeI32Void(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_I_V.invoke(handle, ((Number) args[0]).intValue(),
                fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return null;
    }

    private Object invokeI32I32(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_I_I.invoke(handle, ((Number) args[0]).intValue(),
                fastResultI32, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultI32.get(ValueLayout.JAVA_INT, 0);
    }

    private Object invokeII32Void(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_II_V.invoke(handle,
                ((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
                fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return null;
    }

    private Object invokeII32I32(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_II_I.invoke(handle,
                ((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
                fastResultI32, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultI32.get(ValueLayout.JAVA_INT, 0);
    }

    private Object invokeIII32I32(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_III_I.invoke(handle,
                ((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
                ((Number) args[2]).intValue(),
                fastResultI32, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultI32.get(ValueLayout.JAVA_INT, 0);
    }

    private Object invokeJ64J64(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_J_J.invoke(handle, ((Number) args[0]).longValue(),
                fastResultI64, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultI64.get(ValueLayout.JAVA_LONG, 0);
    }

    private Object invokeJJ64J64(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_JJ_J.invoke(handle,
                ((Number) args[0]).longValue(), ((Number) args[1]).longValue(),
                fastResultI64, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultI64.get(ValueLayout.JAVA_LONG, 0);
    }

    private Object invokeDD64D64(final MemorySegment handle, final Object[] args) throws Throwable {
        clearErrorBuf();
        final int rc = (int) Handles.CALL_DD_D.invoke(handle,
                ((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue(),
                fastResultF64, fastErrorBuf, FAST_ERROR_BUF_SIZE);
        checkFastPathError(rc);
        return fastResultF64.get(ValueLayout.JAVA_DOUBLE, 0);
    }

    /**
     * Generic fallback path using WasmValueFFI marshalling.
     */
    private Object invokeGeneric(final Object[] args) throws Throwable {
        try (final Arena arena = Arena.ofConfined()) {
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

            if (resultCount == 0) {
                return null;
            }

            final Object[] results = convertResultsFromNative(resultsBuffer, resultCount);
            return results.length == 1 ? results[0] : results;
        }
    }

    private void clearErrorBuf() {
        fastErrorBuf.set(ValueLayout.JAVA_BYTE, 0, (byte) 0);
    }

    private void checkFastPathError(final int rc) throws WasmRuntimeException {
        if (rc < 0) {
            final String errorMsg = fastErrorBuf.getString(0);
            throw new WasmRuntimeException(
                errorMsg.isEmpty() ? "Function call failed for: " + name : errorMsg);
        }
    }

    /**
     * Resolves and caches the fast path enum for this function's signature.
     */
    private FastPath resolveFastPath() {
        FastPath path = fastPath;
        if (path == null) {
            path = FastPath.resolve(getSignature());
            fastPath = path;
        }
        return path;
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

            fastArena.close();
        }
    }

    private void ensureNotClosed() {
        if (closed.get() || parentInstance.isClosed()) {
            throw new IllegalStateException("WebAssembly function has been closed");
        }
    }

    private FunctionSignature fetchSignature() {
        if (Handles.GET_SIGNATURE == null) {
            return new FunctionSignature(new ValueType[0], new ValueType[0]);
        }

        try (final Arena arena = Arena.ofConfined()) {
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

    private MemorySegment convertArgumentsToNative(final Object[] args, final Arena arena) {
        final MemorySegment buffer = arena.allocate(WASM_VALUE_FFI_SIZE * Math.max(args.length, 1));

        for (int i = 0; i < args.length; i++) {
            final long offset = i * WASM_VALUE_FFI_SIZE;
            final Object arg = args[i];

            if (arg instanceof Integer) {
                buffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.I32);
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

    // =========================================================================
    // Fast path signature matching (mirrors JNI FastPath enum)
    // =========================================================================

    /**
     * Enum of recognized function signatures for typed fast-path dispatch.
     * Each variant corresponds to a typed native function that bypasses
     * Arena allocation and WasmValueFFI marshalling.
     */
    @SuppressWarnings("checkstyle:JavadocVariable")
    enum FastPath {
        V_V,     // () -> void
        V_I,     // () -> i32
        I_V,     // (i32) -> void
        I_I,     // (i32) -> i32
        II_V,    // (i32, i32) -> void
        II_I,    // (i32, i32) -> i32
        III_I,   // (i32, i32, i32) -> i32
        J_J,     // (i64) -> i64
        JJ_J,    // (i64, i64) -> i64
        DD_D,    // (f64, f64) -> f64
        GENERIC; // fallback to WasmValueFFI path

        /**
         * Resolves the fast path for a given function signature.
         *
         * @param signature the function signature
         * @return the matching fast path, or GENERIC if no fast path applies
         */
        @SuppressWarnings("checkstyle:CyclomaticComplexity")
        static FastPath resolve(final FunctionSignature signature) {
            final ValueType[] params = signature.getParameterTypes();
            final ValueType[] results = signature.getReturnTypes();
            final int pc = params.length;
            final int rc = results.length;

            if (rc == 0) {
                if (pc == 0 && Handles.CALL_V_V != null) {
                    return V_V;
                }
                if (pc == 1 && params[0] == ValueType.I32 && Handles.CALL_I_V != null) {
                    return I_V;
                }
                if (pc == 2 && params[0] == ValueType.I32 && params[1] == ValueType.I32
                        && Handles.CALL_II_V != null) {
                    return II_V;
                }
            } else if (rc == 1) {
                final ValueType rt = results[0];
                if (rt == ValueType.I32) {
                    if (pc == 0 && Handles.CALL_V_I != null) {
                        return V_I;
                    }
                    if (pc == 1 && params[0] == ValueType.I32 && Handles.CALL_I_I != null) {
                        return I_I;
                    }
                    if (pc == 2 && params[0] == ValueType.I32 && params[1] == ValueType.I32
                            && Handles.CALL_II_I != null) {
                        return II_I;
                    }
                    if (pc == 3 && params[0] == ValueType.I32 && params[1] == ValueType.I32
                            && params[2] == ValueType.I32 && Handles.CALL_III_I != null) {
                        return III_I;
                    }
                } else if (rt == ValueType.I64) {
                    if (pc == 1 && params[0] == ValueType.I64 && Handles.CALL_J_J != null) {
                        return J_J;
                    }
                    if (pc == 2 && params[0] == ValueType.I64 && params[1] == ValueType.I64
                            && Handles.CALL_JJ_J != null) {
                        return JJ_J;
                    }
                } else if (rt == ValueType.F64) {
                    if (pc == 2 && params[0] == ValueType.F64 && params[1] == ValueType.F64
                            && Handles.CALL_DD_D != null) {
                        return DD_D;
                    }
                }
            }

            return GENERIC;
        }
    }
}
