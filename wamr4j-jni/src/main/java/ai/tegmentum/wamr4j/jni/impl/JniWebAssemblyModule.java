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
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.exception.RuntimeException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly module.
 * 
 * <p>This class represents a compiled WebAssembly module using JNI
 * to communicate with the native WAMR library. It provides thread-safe
 * access to module information and instance creation.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyModule implements WebAssemblyModule {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyModule.class.getName());
    
    // Native module handle
    private volatile long nativeHandle;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new JNI WebAssembly module wrapper.
     * 
     * @param nativeHandle the native module handle, must not be 0
     */
    public JniWebAssemblyModule(final long nativeHandle) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native module handle cannot be 0");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created JNI WebAssembly module with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyInstance instantiate() throws RuntimeException {
        return instantiate(null);
    }

    @Override
    public WebAssemblyInstance instantiate(final Map<String, Map<String, Object>> imports) throws RuntimeException {
        ensureNotClosed();
        
        try {
            final long instanceHandle = nativeInstantiateModule(nativeHandle, imports);
            if (instanceHandle == 0L) {
                throw new RuntimeException("Failed to instantiate WebAssembly module");
            }
            
            return new JniWebAssemblyInstance(instanceHandle);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error during module instantiation", e);
        }
    }

    @Override
    public String[] getExportNames() {
        ensureNotClosed();
        
        try {
            final String[] exports = nativeGetExportNames(nativeHandle);
            return exports != null ? exports : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get export names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public String[] getImportNames() {
        ensureNotClosed();
        
        try {
            final String[] imports = nativeGetImportNames(nativeHandle);
            return imports != null ? imports : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get import names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public FunctionSignature getExportFunctionSignature(final String functionName) {
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }
        
        ensureNotClosed();
        
        try {
            return nativeGetExportFunctionSignature(nativeHandle, functionName);
        } catch (final Exception e) {
            LOGGER.fine("Failed to get function signature for '" + functionName + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean validateImports(final Map<String, Map<String, Object>> imports) {
        ensureNotClosed();
        
        try {
            return nativeValidateImports(nativeHandle, imports);
        } catch (final Exception e) {
            LOGGER.warning("Error validating imports: " + e.getMessage());
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
                    nativeDestroyModule(handle);
                    LOGGER.fine("Destroyed JNI WebAssembly module with handle: " + handle);
                } catch (final Exception e) {
                    LOGGER.warning("Error destroying native module: " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly module has been closed");
        }
    }

    // Native method declarations
    
    /**
     * Destroys a native WebAssembly module instance.
     * 
     * @param moduleHandle the native module handle
     */
    private static native void nativeDestroyModule(long moduleHandle);

    /**
     * Instantiates a WebAssembly module with optional imports.
     * 
     * @param moduleHandle the native module handle
     * @param imports the import bindings, may be null
     * @return the native instance handle, or 0 on failure
     * @throws RuntimeException if instantiation fails
     */
    private static native long nativeInstantiateModule(long moduleHandle, Map<String, Map<String, Object>> imports)
        throws RuntimeException;

    /**
     * Gets the names of all exports defined by the module.
     * 
     * @param moduleHandle the native module handle
     * @return array of export names, or null on failure
     */
    private static native String[] nativeGetExportNames(long moduleHandle);

    /**
     * Gets the names of all imports required by the module.
     * 
     * @param moduleHandle the native module handle
     * @return array of import names, or null on failure
     */
    private static native String[] nativeGetImportNames(long moduleHandle);

    /**
     * Gets the function signature for an exported function.
     * 
     * @param moduleHandle the native module handle
     * @param functionName the name of the function
     * @return the function signature, or null if not found
     */
    private static native FunctionSignature nativeGetExportFunctionSignature(long moduleHandle, String functionName);

    /**
     * Validates import bindings against module requirements.
     * 
     * @param moduleHandle the native module handle
     * @param imports the import bindings to validate
     * @return true if imports are valid, false otherwise
     */
    private static native boolean nativeValidateImports(long moduleHandle, Map<String, Map<String, Object>> imports);
}