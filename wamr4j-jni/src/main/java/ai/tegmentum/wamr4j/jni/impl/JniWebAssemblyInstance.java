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

import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.exception.RuntimeException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly instance.
 * 
 * <p>This class represents an instantiated WebAssembly module using JNI
 * to communicate with the native WAMR library. It provides access to
 * functions, memory, and global variables.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyInstance implements WebAssemblyInstance {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyInstance.class.getName());
    
    // Native instance handle
    private volatile long nativeHandle;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new JNI WebAssembly instance wrapper.
     * 
     * @param nativeHandle the native instance handle, must not be 0
     */
    public JniWebAssemblyInstance(final long nativeHandle) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native instance handle cannot be 0");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created JNI WebAssembly instance with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyFunction getFunction(final String functionName) throws RuntimeException {
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }
        
        ensureNotClosed();
        
        try {
            final long functionHandle = nativeGetFunction(nativeHandle, functionName);
            if (functionHandle == 0L) {
                throw new RuntimeException("Function not found: " + functionName);
            }
            
            return new JniWebAssemblyFunction(functionHandle, functionName, this);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error getting function: " + functionName, e);
        }
    }

    @Override
    public WebAssemblyMemory getMemory() throws RuntimeException {
        ensureNotClosed();
        
        try {
            final long memoryHandle = nativeGetMemory(nativeHandle);
            if (memoryHandle == 0L) {
                throw new RuntimeException("Memory not exported by instance");
            }
            
            return new JniWebAssemblyMemory(memoryHandle, this);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error getting memory", e);
        }
    }

    @Override
    public Object getGlobal(final String globalName) throws RuntimeException {
        if (globalName == null) {
            throw new IllegalArgumentException("Global name cannot be null");
        }
        
        ensureNotClosed();
        
        try {
            return nativeGetGlobal(nativeHandle, globalName);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to get global variable: " + globalName, e);
        }
    }

    @Override
    public void setGlobal(final String globalName, final Object value) throws RuntimeException {
        if (globalName == null) {
            throw new IllegalArgumentException("Global name cannot be null");
        }
        
        ensureNotClosed();
        
        try {
            nativeSetGlobal(nativeHandle, globalName, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to set global variable: " + globalName, e);
        }
    }

    @Override
    public String[] getFunctionNames() {
        ensureNotClosed();
        
        try {
            final String[] functions = nativeGetFunctionNames(nativeHandle);
            return functions != null ? functions : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get function names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public String[] getGlobalNames() {
        ensureNotClosed();
        
        try {
            final String[] globals = nativeGetGlobalNames(nativeHandle);
            return globals != null ? globals : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get global names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public boolean hasMemory() {
        ensureNotClosed();
        
        try {
            return nativeHasMemory(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to check memory availability: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            final long handle = nativeHandle;
            nativeHandle = 0L;
            
            if (handle != 0L) {
                try {
                    nativeDestroyInstance(handle);
                    LOGGER.fine("Destroyed JNI WebAssembly instance with handle: " + handle);
                } catch (final Exception e) {
                    LOGGER.warning("Error destroying native instance: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Internal method to check if the instance is valid.
     * Used by Function and Memory objects.
     * 
     * @return true if the instance is valid, false otherwise
     */
    boolean isValid() {
        return !closed.get() && nativeHandle != 0L;
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly instance has been closed");
        }
    }

    // Native method declarations
    
    /**
     * Destroys a native WebAssembly instance.
     * 
     * @param instanceHandle the native instance handle
     */
    private static native void nativeDestroyInstance(long instanceHandle);

    /**
     * Gets a function from the instance.
     * 
     * @param instanceHandle the native instance handle
     * @param functionName the name of the function
     * @return the native function handle, or 0 if not found
     */
    private static native long nativeGetFunction(long instanceHandle, String functionName);

    /**
     * Gets the memory from the instance.
     * 
     * @param instanceHandle the native instance handle
     * @return the native memory handle, or 0 if not available
     */
    private static native long nativeGetMemory(long instanceHandle);

    /**
     * Gets a global variable value.
     * 
     * @param instanceHandle the native instance handle
     * @param globalName the name of the global variable
     * @return the global variable value
     * @throws RuntimeException if the global is not found
     */
    private static native Object nativeGetGlobal(long instanceHandle, String globalName) throws RuntimeException;

    /**
     * Sets a global variable value.
     * 
     * @param instanceHandle the native instance handle
     * @param globalName the name of the global variable
     * @param value the new value for the global variable
     * @throws RuntimeException if the global is not found or cannot be set
     */
    private static native void nativeSetGlobal(long instanceHandle, String globalName, Object value) 
        throws RuntimeException;

    /**
     * Gets the names of all exported functions.
     * 
     * @param instanceHandle the native instance handle
     * @return array of function names, or null on failure
     */
    private static native String[] nativeGetFunctionNames(long instanceHandle);

    /**
     * Gets the names of all exported global variables.
     * 
     * @param instanceHandle the native instance handle
     * @return array of global names, or null on failure
     */
    private static native String[] nativeGetGlobalNames(long instanceHandle);

    /**
     * Checks if the instance exports memory.
     * 
     * @param instanceHandle the native instance handle
     * @return true if memory is available, false otherwise
     */
    private static native boolean nativeHasMemory(long instanceHandle);
}