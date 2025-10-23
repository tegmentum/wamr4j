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
import ai.tegmentum.wamr4j.exception.RuntimeException;
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
    
    // Function descriptors for native calls
    private static final FunctionDescriptor DESTROY_INSTANCE_DESC = 
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
    private static final FunctionDescriptor GET_FUNCTION_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor GET_MEMORY_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

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
    public WebAssemblyFunction getFunction(final String name) throws RuntimeException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        
        ensureNotClosed();
        
        try (final Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getFunctionFunc = lookup.find("wamr_get_function")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_get_function' not found"));
            
            final MethodHandle getFunction = Linker.nativeLinker()
                .downcallHandle(getFunctionFunc, GET_FUNCTION_DESC);
            
            // Allocate memory for function name
            final MemorySegment nameBuffer = arena.allocateFrom(name);
            
            final MemorySegment functionHandle = (MemorySegment) getFunction.invoke(
                nativeHandle, nameBuffer);
                
            if (functionHandle.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Function not found: " + name);
            }
            
            return new PanamaWebAssemblyFunction(functionHandle, name);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new RuntimeException("Unexpected error getting function: " + name, e);
        }
    }

    @Override
    public WebAssemblyMemory getMemory() throws RuntimeException {
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getMemoryFunc = lookup.find("wamr_get_memory")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_get_memory' not found"));
            
            final MethodHandle getMemory = Linker.nativeLinker()
                .downcallHandle(getMemoryFunc, GET_MEMORY_DESC);
            
            final MemorySegment memoryHandle = (MemorySegment) getMemory.invoke(nativeHandle);
            if (memoryHandle.equals(MemorySegment.NULL)) {
                throw new RuntimeException("No memory exported by WebAssembly module");
            }
            
            return new PanamaWebAssemblyMemory(memoryHandle);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new RuntimeException("Unexpected error getting memory", e);
        }
    }

    @Override
    public String[] getFunctionNames() {
        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getFunctionNamesFunc = lookup.find("wamr_get_function_names")
                .orElse(MemorySegment.NULL);

            if (getFunctionNamesFunc.equals(MemorySegment.NULL)) {
                LOGGER.warning("wamr_get_function_names not found in native library");
                return new String[0];
            }

            final FunctionDescriptor descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            );

            final MethodHandle getFunctionNames = Linker.nativeLinker()
                .downcallHandle(getFunctionNamesFunc, descriptor);

            final MemorySegment namesBufferPtr = arena.allocate(ValueLayout.ADDRESS);
            final MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);

            final int result = (int) getFunctionNames.invoke(nativeHandle, namesBufferPtr, countPtr);

            if (result != 0) {
                LOGGER.warning("Failed to get function names from native library");
                return new String[0];
            }

            final int count = countPtr.get(ValueLayout.JAVA_INT, 0);
            if (count == 0) {
                return new String[0];
            }

            final MemorySegment namesArray = namesBufferPtr.get(ValueLayout.ADDRESS, 0);
            final String[] names = new String[count];

            for (int i = 0; i < count; i++) {
                final MemorySegment namePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i);
                names[i] = namePtr.getString(0);
            }

            // Free the native array
            final MemorySegment freeNamesFunc = lookup.find("wamr_free_names")
                .orElse(MemorySegment.NULL);
            if (!freeNamesFunc.equals(MemorySegment.NULL)) {
                final FunctionDescriptor freeDescriptor = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                );
                final MethodHandle freeNames = Linker.nativeLinker()
                    .downcallHandle(freeNamesFunc, freeDescriptor);
                freeNames.invoke(namesArray, count);
            }

            return names;
        } catch (final Throwable e) {
            LOGGER.warning("Error getting function names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public String[] getGlobalNames() {
        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getGlobalNamesFunc = lookup.find("wamr_get_global_names")
                .orElse(MemorySegment.NULL);

            if (getGlobalNamesFunc.equals(MemorySegment.NULL)) {
                LOGGER.warning("wamr_get_global_names not found in native library");
                return new String[0];
            }

            final FunctionDescriptor descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            );

            final MethodHandle getGlobalNames = Linker.nativeLinker()
                .downcallHandle(getGlobalNamesFunc, descriptor);

            final MemorySegment namesBufferPtr = arena.allocate(ValueLayout.ADDRESS);
            final MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);

            final int result = (int) getGlobalNames.invoke(nativeHandle, namesBufferPtr, countPtr);

            if (result != 0) {
                LOGGER.warning("Failed to get global names from native library");
                return new String[0];
            }

            final int count = countPtr.get(ValueLayout.JAVA_INT, 0);
            if (count == 0) {
                return new String[0];
            }

            final MemorySegment namesArray = namesBufferPtr.get(ValueLayout.ADDRESS, 0);
            final String[] names = new String[count];

            for (int i = 0; i < count; i++) {
                final MemorySegment namePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i);
                names[i] = namePtr.getString(0);
            }

            // Free the native array
            final MemorySegment freeNamesFunc = lookup.find("wamr_free_names")
                .orElse(MemorySegment.NULL);
            if (!freeNamesFunc.equals(MemorySegment.NULL)) {
                final FunctionDescriptor freeDescriptor = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                );
                final MethodHandle freeNames = Linker.nativeLinker()
                    .downcallHandle(freeNamesFunc, freeDescriptor);
                freeNames.invoke(namesArray, count);
            }

            return names;
        } catch (final Throwable e) {
            LOGGER.warning("Error getting global names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public Object getGlobal(final String globalName) throws RuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }

        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getGlobalFunc = lookup.find("wamr_get_global")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_get_global' not found"));

            final FunctionDescriptor descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT
            );

            final MethodHandle getGlobal = Linker.nativeLinker()
                .downcallHandle(getGlobalFunc, descriptor);

            final MemorySegment nameBuffer = arena.allocateFrom(globalName);
            final MemorySegment valueTypePtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment valueBuffer = arena.allocate(8); // 8 bytes for i64/f64
            final int valueSize = 8;

            final int result = (int) getGlobal.invoke(
                nativeHandle, nameBuffer, valueTypePtr, valueBuffer, valueSize);

            if (result != 0) {
                throw new RuntimeException("Failed to get global variable: " + globalName);
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
                    throw new RuntimeException("Unsupported global type: " + valueType);
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException("Unexpected error getting global: " + globalName, e);
        }
    }

    @Override
    public void setGlobal(final String globalName, final Object value) throws RuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Global value cannot be null");
        }

        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment setGlobalFunc = lookup.find("wamr_set_global")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_set_global' not found"));

            final FunctionDescriptor descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS
            );

            final MethodHandle setGlobal = Linker.nativeLinker()
                .downcallHandle(setGlobalFunc, descriptor);

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

            final int result = (int) setGlobal.invoke(
                nativeHandle, nameBuffer, valueType, valueBuffer);

            if (result != 0) {
                throw new RuntimeException("Failed to set global variable: " + globalName);
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException("Unexpected error setting global: " + globalName, e);
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
                    final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
                    final MemorySegment destroyInstanceFunc = lookup.find("wamr_destroy_instance")
                        .orElse(MemorySegment.NULL);
                        
                    if (!destroyInstanceFunc.equals(MemorySegment.NULL)) {
                        final MethodHandle destroyInstance = Linker.nativeLinker()
                            .downcallHandle(destroyInstanceFunc, DESTROY_INSTANCE_DESC);
                        destroyInstance.invoke(handle);
                        LOGGER.fine("Destroyed Panama WebAssembly instance with handle: " + handle);
                    }
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native instance: " + e.getMessage());
                }
            }
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
        } catch (RuntimeException e) {
            return false;
        }
    }
}