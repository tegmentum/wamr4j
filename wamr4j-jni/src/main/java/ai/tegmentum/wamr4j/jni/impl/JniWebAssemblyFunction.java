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

package ai.tegmentum.wamr4j.jni.impl;

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly function.
 * 
 * <p>This class represents a callable WebAssembly function using JNI
 * to communicate with the native WAMR library. It provides type-safe
 * function invocation with proper error handling.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyFunction implements WebAssemblyFunction {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyFunction.class.getName());

    // Native function handle
    private volatile long nativeHandle;

    // Function metadata
    private final String functionName;
    private final JniWebAssemblyInstance parentInstance;

    // Cached signature (loaded lazily)
    private volatile FunctionSignature cachedSignature;

    /**
     * Creates a new JNI WebAssembly function wrapper.
     * 
     * @param nativeHandle the native function handle, must not be 0
     * @param functionName the name of the function, must not be null
     * @param parentInstance the parent instance, must not be null
     */
    public JniWebAssemblyFunction(final long nativeHandle, final String functionName, 
                                  final JniWebAssemblyInstance parentInstance) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native function handle cannot be 0");
        }
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }
        if (parentInstance == null) {
            throw new IllegalArgumentException("Parent instance cannot be null");
        }
        
        this.nativeHandle = nativeHandle;
        this.functionName = functionName;
        this.parentInstance = parentInstance;
        
        LOGGER.fine("Created JNI WebAssembly function '" + functionName + "' with handle: " + nativeHandle);
    }

    @Override
    public Object invoke(final Object... args) throws WasmRuntimeException {
        // Defensive programming - validate state
        if (!isValid()) {
            throw new IllegalStateException("Function is no longer valid - parent instance has been closed");
        }
        
        try {
            return nativeInvokeFunction(nativeHandle, args);
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error invoking function: " + functionName, e);
        }
    }

    @Override
    public FunctionSignature getSignature() {
        if (!isValid()) {
            throw new IllegalStateException("Function is no longer valid - parent instance has been closed");
        }
        
        FunctionSignature signature = cachedSignature;
        if (signature == null) {
            synchronized (this) {
                signature = cachedSignature;
                if (signature == null) {
                    try {
                        signature = nativeGetFunctionSignature(nativeHandle);
                        if (signature == null) {
                            throw new IllegalStateException("Failed to get function signature");
                        }
                        cachedSignature = signature;
                    } catch (final Exception e) {
                        throw new IllegalStateException("Failed to get function signature for: " + functionName, e);
                    }
                }
            }
        }
        return signature;
    }

    @Override
    public String getName() {
        return functionName;
    }

    @Override
    public boolean isValid() {
        return nativeHandle != 0L && parentInstance.isValid();
    }

    /**
     * Destroys the native function handle, freeing the Rust Box wrapper.
     * Called by the parent instance during cleanup.
     */
    void close() {
        final long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L) {
            try {
                nativeDestroyFunction(handle);
            } catch (final Exception e) {
                LOGGER.warning("Error destroying native function '" + functionName + "': " + e.getMessage());
            }
        }
    }

    // Native method declarations

    /**
     * Destroys a native WebAssembly function handle.
     *
     * @param functionHandle the native function handle
     */
    private static native void nativeDestroyFunction(long functionHandle);

    /**
     * Invokes a WebAssembly function with the given arguments.
     *
     * @param functionHandle the native function handle
     * @param args the function arguments
     * @return the function result, or null for void functions
     * @throws WasmRuntimeException if execution fails
     */
    private static native Object nativeInvokeFunction(long functionHandle, Object[] args) throws WasmRuntimeException;

    /**
     * Gets the signature of a WebAssembly function.
     *
     * @param functionHandle the native function handle
     * @return the function signature
     */
    private static native FunctionSignature nativeGetFunctionSignature(long functionHandle);
}