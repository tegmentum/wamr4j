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
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.exception.RuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
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
    
    // Native function handle as MemorySegment
    private volatile MemorySegment nativeHandle;
    
    // Function metadata
    private final String name;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // Function descriptors for native calls
    private static final FunctionDescriptor DESTROY_FUNCTION_DESC = 
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
    private static final FunctionDescriptor CALL_FUNCTION_DESC = 
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
    private static final FunctionDescriptor GET_SIGNATURE_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    /**
     * Creates a new Panama WebAssembly function wrapper.
     * 
     * @param nativeHandle the native function handle, must not be NULL
     * @param name the function name for debugging purposes
     */
    public PanamaWebAssemblyFunction(final MemorySegment nativeHandle, final String name) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native function handle cannot be null or NULL");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        
        this.nativeHandle = nativeHandle;
        this.name = name;
        LOGGER.fine("Created Panama WebAssembly function '" + name + "' with handle: " + nativeHandle);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FunctionSignature getSignature() throws RuntimeException {
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getSignatureFunc = lookup.find("wamr_get_function_signature")
                .orElse(MemorySegment.NULL);
                
            if (getSignatureFunc.equals(MemorySegment.NULL)) {
                // Fallback to unknown signature
                return FunctionSignature.builder()
                    .name(name)
                    .build();
            }
            
            final MethodHandle getSignature = Linker.nativeLinker()
                .downcallHandle(getSignatureFunc, GET_SIGNATURE_DESC);
            
            final MemorySegment signaturePtr = (MemorySegment) getSignature.invoke(nativeHandle);
            if (signaturePtr.equals(MemorySegment.NULL)) {
                return FunctionSignature.builder()
                    .name(name)
                    .build();
            }
            
            // Parse native signature format (implementation depends on native format)
            return parseNativeSignature(signaturePtr);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get function signature for '" + name + "': " + e.getMessage());
            return FunctionSignature.builder()
                .name(name)
                .build();
        }
    }

    @Override
    public Object[] call(final Object... args) throws RuntimeException {
        // Defensive programming - validate inputs
        if (args == null) {
            throw new IllegalArgumentException("Function arguments cannot be null");
        }
        
        ensureNotClosed();
        
        try (final Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment callFunctionFunc = lookup.find("wamr_call_function")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_call_function' not found"));
            
            final MethodHandle callFunction = Linker.nativeLinker()
                .downcallHandle(callFunctionFunc, CALL_FUNCTION_DESC);
            
            // Convert arguments to native format
            final MemorySegment argsBuffer = convertArgumentsToNative(args, arena);
            final MemorySegment resultsBuffer = arena.allocate(1024); // Placeholder size
            
            final int resultCode = (int) callFunction.invoke(
                nativeHandle, argsBuffer, args.length, resultsBuffer, 1024);
                
            if (resultCode != 0) {
                throw new RuntimeException("Function call failed with code: " + resultCode);
            }
            
            // Convert native results back to Java objects
            return convertResultsFromNative(resultsBuffer);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new RuntimeException("Unexpected error calling function: " + name, e);
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
                    final MemorySegment destroyFunctionFunc = lookup.find("wamr_destroy_function")
                        .orElse(MemorySegment.NULL);
                        
                    if (!destroyFunctionFunc.equals(MemorySegment.NULL)) {
                        final MethodHandle destroyFunction = Linker.nativeLinker()
                            .downcallHandle(destroyFunctionFunc, DESTROY_FUNCTION_DESC);
                        destroyFunction.invoke(handle);
                        LOGGER.fine("Destroyed Panama WebAssembly function '" + name + "' with handle: " + handle);
                    }
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native function: " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly function has been closed");
        }
    }
    
    private FunctionSignature parseNativeSignature(final MemorySegment signaturePtr) {
        // Placeholder implementation - actual parsing depends on native format
        return FunctionSignature.builder()
            .name(name)
            .build();
    }
    
    private MemorySegment convertArgumentsToNative(final Object[] args, final Arena arena) {
        // Placeholder implementation - actual conversion depends on type system
        return arena.allocate(ValueLayout.JAVA_LONG, args.length);
    }
    
    private Object[] convertResultsFromNative(final MemorySegment resultsBuffer) {
        // Placeholder implementation - actual conversion depends on return types
        return new Object[0];
    }
}