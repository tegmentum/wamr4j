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
}