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

import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly instance.
 * 
 * <p>This class represents an instantiated WebAssembly module using Panama FFI
 * to communicate with the native WAMR library. It provides access to exported
 * functions and memory with enhanced type safety.
 * 
 * @since 1.0.0
 */
public final class PanamaWebAssemblyInstance implements WebAssemblyInstance {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyInstance.class.getName());
    
    // Native instance handle as MemorySegment
    private volatile MemorySegment nativeHandle;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle DESTROY_INSTANCE;
        static final MethodHandle GET_FUNCTION;
        static final MethodHandle GET_MEMORY;
        static final MethodHandle GET_FUNCTION_NAMES;
        static final MethodHandle GET_GLOBAL_NAMES;
        static final MethodHandle FREE_NAMES;
        static final MethodHandle GET_GLOBAL;
        static final MethodHandle SET_GLOBAL;

        private static final FunctionDescriptor NAMES_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
        private static final FunctionDescriptor FREE_NAMES_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
        private static final FunctionDescriptor GET_GLOBAL_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
        private static final FunctionDescriptor SET_GLOBAL_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            DESTROY_INSTANCE = linker.downcallHandle(
                lookup.find("wamr_instance_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            GET_FUNCTION = linker.downcallHandle(
                lookup.find("wamr_function_lookup").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_MEMORY = linker.downcallHandle(
                lookup.find("wamr_memory_get").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            GET_FUNCTION_NAMES = resolveOptional(lookup, linker, "wamr_get_function_names", NAMES_DESC);
            GET_GLOBAL_NAMES = resolveOptional(lookup, linker, "wamr_get_global_names", NAMES_DESC);
            FREE_NAMES = resolveOptional(lookup, linker, "wamr_free_names", FREE_NAMES_DESC);
            GET_GLOBAL = resolveOptional(lookup, linker, "wamr_get_global", GET_GLOBAL_DESC);
            SET_GLOBAL = resolveOptional(lookup, linker, "wamr_set_global", SET_GLOBAL_DESC);
        }

        private static MethodHandle resolveOptional(final SymbolLookup lookup, final Linker linker,
                final String name, final FunctionDescriptor desc) {
            final var symbol = lookup.find(name);
            return symbol.isPresent() ? linker.downcallHandle(symbol.get(), desc) : null;
        }
    }

    /**
     * Creates a new Panama WebAssembly instance wrapper.
     * 
     * @param nativeHandle the native instance handle, must not be NULL
     */
    public PanamaWebAssemblyInstance(final MemorySegment nativeHandle) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native instance handle cannot be null or NULL");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created Panama WebAssembly instance with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyFunction getFunction(final String name) throws WasmRuntimeException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        
        ensureNotClosed();
        
        try (final Arena arena = Arena.ofConfined()) {
            // Allocate memory for function name and error buffer
            final MemorySegment nameBuffer = arena.allocateFrom(name);
            final int errorBufSize = 1024;
            final MemorySegment errorBuf = arena.allocate(errorBufSize);

            final MemorySegment functionHandle = (MemorySegment) Handles.GET_FUNCTION.invoke(
                nativeHandle, nameBuffer, errorBuf, errorBufSize);

            if (functionHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Function not found: " + name : errorMsg);
            }
            
            return new PanamaWebAssemblyFunction(functionHandle, name);
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting function: " + name, e);
        }
    }

    @Override
    public WebAssemblyMemory getMemory() throws WasmRuntimeException {
        ensureNotClosed();

        try {
            final MemorySegment memoryHandle = (MemorySegment) Handles.GET_MEMORY.invoke(nativeHandle);
            if (memoryHandle.equals(MemorySegment.NULL)) {
                throw new WasmRuntimeException("No memory exported by WebAssembly module");
            }
            
            return new PanamaWebAssemblyMemory(memoryHandle);
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting memory", e);
        }
    }

    @Override
    public String[] getFunctionNames() {
        return getExportedNames(Handles.GET_FUNCTION_NAMES, "function");
    }

    @Override
    public String[] getGlobalNames() {
        return getExportedNames(Handles.GET_GLOBAL_NAMES, "global");
    }

    @Override
    public Object getGlobal(final String globalName) throws WasmRuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }

        ensureNotClosed();

        if (Handles.GET_GLOBAL == null) {
            throw new WasmRuntimeException("Native function 'wamr_get_global' not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuffer = arena.allocateFrom(globalName);
            final MemorySegment valueTypePtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment valueBuffer = arena.allocate(8); // 8 bytes for i64/f64
            final int valueSize = 8;

            final int result = (int) Handles.GET_GLOBAL.invoke(
                nativeHandle, nameBuffer, valueTypePtr, valueBuffer, valueSize);

            if (result != 0) {
                throw new WasmRuntimeException("Failed to get global variable: " + globalName);
            }

            final int valueType = valueTypePtr.get(ValueLayout.JAVA_INT, 0);

            // Convert based on type (WASM_I32=0, WASM_I64=1, WASM_F32=2, WASM_F64=3)
            switch (valueType) {
                case 0: // WASM_I32
                    return valueBuffer.get(ValueLayout.JAVA_INT, 0);
                case 1: // WASM_I64
                    return valueBuffer.get(ValueLayout.JAVA_LONG, 0);
                case 2: // WASM_F32
                    return valueBuffer.get(ValueLayout.JAVA_FLOAT, 0);
                case 3: // WASM_F64
                    return valueBuffer.get(ValueLayout.JAVA_DOUBLE, 0);
                default:
                    throw new WasmRuntimeException("Unsupported global type: " + valueType);
            }
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting global: " + globalName, e);
        }
    }

    @Override
    public void setGlobal(final String globalName, final Object value) throws WasmRuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Global value cannot be null");
        }

        ensureNotClosed();

        if (Handles.SET_GLOBAL == null) {
            throw new WasmRuntimeException("Native function 'wamr_set_global' not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuffer = arena.allocateFrom(globalName);
            final MemorySegment valueBuffer = arena.allocate(8); // 8 bytes for i64/f64

            // Determine type and write value
            final int valueType;
            if (value instanceof Integer) {
                valueType = 0; // WASM_I32
                valueBuffer.set(ValueLayout.JAVA_INT, 0, (Integer) value);
            } else if (value instanceof Long) {
                valueType = 1; // WASM_I64
                valueBuffer.set(ValueLayout.JAVA_LONG, 0, (Long) value);
            } else if (value instanceof Float) {
                valueType = 2; // WASM_F32
                valueBuffer.set(ValueLayout.JAVA_FLOAT, 0, (Float) value);
            } else if (value instanceof Double) {
                valueType = 3; // WASM_F64
                valueBuffer.set(ValueLayout.JAVA_DOUBLE, 0, (Double) value);
            } else {
                throw new IllegalArgumentException(
                    "Unsupported global value type: " + value.getClass().getName() +
                    ". Expected Integer, Long, Float, or Double.");
            }

            final int result = (int) Handles.SET_GLOBAL.invoke(
                nativeHandle, nameBuffer, valueType, valueBuffer);

            if (result != 0) {
                throw new WasmRuntimeException("Failed to set global variable: " + globalName);
            }
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error setting global: " + globalName, e);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;

            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_INSTANCE.invoke(handle);
                    LOGGER.fine("Destroyed Panama WebAssembly instance with handle: " + handle);
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native instance: " + e.getMessage());
                }
            }
        }
    }

    private String[] getExportedNames(final MethodHandle namesHandle, final String kind) {
        ensureNotClosed();

        if (namesHandle == null) {
            LOGGER.warning("wamr_get_" + kind + "_names not found in native library");
            return new String[0];
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment namesBufferPtr = arena.allocate(ValueLayout.ADDRESS);
            final MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);

            final int result = (int) namesHandle.invoke(nativeHandle, namesBufferPtr, countPtr);

            if (result != 0) {
                LOGGER.warning("Failed to get " + kind + " names from native library");
                return new String[0];
            }

            final int count = countPtr.get(ValueLayout.JAVA_INT, 0);
            if (count == 0) {
                return new String[0];
            }

            final MemorySegment namesArray = namesBufferPtr.get(ValueLayout.ADDRESS, 0)
                .reinterpret((long) count * ValueLayout.ADDRESS.byteSize());
            final String[] names = new String[count];

            for (int i = 0; i < count; i++) {
                final MemorySegment namePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i)
                    .reinterpret(1024);
                names[i] = namePtr.getString(0);
            }

            // Free the native array
            if (Handles.FREE_NAMES != null) {
                Handles.FREE_NAMES.invoke(namesArray, count);
            }

            return names;
        } catch (final Throwable e) {
            LOGGER.warning("Error getting " + kind + " names: " + e.getMessage());
            return new String[0];
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly instance has been closed");
        }
    }
    
    @Override
    public boolean hasMemory() {
        ensureNotClosed();
        try {
            getMemory();
            return true;
        } catch (WasmRuntimeException e) {
            return false;
        }
    }
}