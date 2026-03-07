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
import ai.tegmentum.wamr4j.HostFunction;
import ai.tegmentum.wamr4j.PackageType;
import ai.tegmentum.wamr4j.WasiConfiguration;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import java.util.Collections;
import java.util.HashMap;
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
    public WebAssemblyInstance instantiate() throws WasmRuntimeException {
        return instantiate(Collections.emptyMap());
    }

    @Override
    public WebAssemblyInstance instantiate(final Map<String, Map<String, Object>> imports) throws WasmRuntimeException {
        ensureNotClosed();

        long registrationHandle = 0L;

        try {
            // Register host functions if imports are provided
            if (imports != null && !imports.isEmpty()) {
                // Extract HostFunction objects from the imports map
                final Map<String, Map<String, HostFunction>> hostFunctionImports = new HashMap<>();
                for (final Map.Entry<String, Map<String, Object>> moduleEntry : imports.entrySet()) {
                    final Map<String, HostFunction> moduleFunctions = new HashMap<>();
                    for (final Map.Entry<String, Object> funcEntry : moduleEntry.getValue().entrySet()) {
                        if (funcEntry.getValue() instanceof HostFunction) {
                            moduleFunctions.put(funcEntry.getKey(), (HostFunction) funcEntry.getValue());
                        }
                    }
                    if (!moduleFunctions.isEmpty()) {
                        hostFunctionImports.put(moduleEntry.getKey(), moduleFunctions);
                    }
                }

                if (!hostFunctionImports.isEmpty()) {
                    registrationHandle = nativeRegisterHostFunctions(hostFunctionImports);
                    if (registrationHandle == 0L) {
                        throw new WasmRuntimeException("Failed to register host functions");
                    }
                }
            }

            final long instanceHandle = nativeInstantiateModule(nativeHandle);
            if (instanceHandle == 0L) {
                // Clean up registration on failure
                if (registrationHandle != 0L) {
                    JniWebAssemblyInstance.destroyRegistration(registrationHandle);
                }
                throw new WasmRuntimeException("Failed to instantiate WebAssembly module");
            }

            return new JniWebAssemblyInstance(instanceHandle, registrationHandle);
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            // Clean up registration on unexpected failure
            if (registrationHandle != 0L) {
                JniWebAssemblyInstance.destroyRegistration(registrationHandle);
            }
            throw new WasmRuntimeException("Unexpected error during module instantiation", e);
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
    public boolean setName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Module name cannot be null");
        }
        ensureNotClosed();
        try {
            return nativeSetModuleName(nativeHandle, name);
        } catch (final Exception e) {
            LOGGER.warning("Failed to set module name: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getName() {
        ensureNotClosed();
        try {
            final String name = nativeGetModuleName(nativeHandle);
            return name != null ? name : "";
        } catch (final Exception e) {
            LOGGER.warning("Failed to get module name: " + e.getMessage());
            return "";
        }
    }

    @Override
    public PackageType getPackageType() {
        ensureNotClosed();
        try {
            return PackageType.fromNativeValue(nativeGetPackageType(nativeHandle));
        } catch (final Exception e) {
            LOGGER.warning("Failed to get package type: " + e.getMessage());
            return PackageType.UNKNOWN;
        }
    }

    @Override
    public int getPackageVersion() {
        ensureNotClosed();
        try {
            return nativeGetPackageVersion(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get package version: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public String getHash() {
        ensureNotClosed();
        try {
            final String hash = nativeGetModuleHash(nativeHandle);
            return hash != null ? hash : "";
        } catch (final Exception e) {
            LOGGER.warning("Failed to get module hash: " + e.getMessage());
            return "";
        }
    }

    @Override
    public boolean isUnderlyingBinaryFreeable() {
        ensureNotClosed();
        try {
            return nativeIsUnderlyingBinaryFreeable(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to check binary freeability: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void configureWasi(final WasiConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("WASI configuration cannot be null");
        }
        ensureNotClosed();

        try {
            nativeSetWasiArgs(nativeHandle,
                config.getPreopens(),
                config.getMappedDirs(),
                config.getEnvVars(),
                config.getArgs());

            final String[] addrPool = config.getAddrPool();
            if (addrPool.length > 0) {
                nativeSetWasiAddrPool(nativeHandle, addrPool);
            }

            final String[] nsLookupPool = config.getNsLookupPool();
            if (nsLookupPool.length > 0) {
                nativeSetWasiNsLookupPool(nativeHandle, nsLookupPool);
            }
        } catch (final Exception e) {
            LOGGER.warning("Failed to configure WASI: " + e.getMessage());
        }
    }

    @Override
    public WebAssemblyInstance instantiateEx(final int defaultStackSize,
            final int hostManagedHeapSize, final int maxMemoryPages) throws WasmRuntimeException {
        if (defaultStackSize < 0) {
            throw new IllegalArgumentException("defaultStackSize cannot be negative");
        }
        if (hostManagedHeapSize < 0) {
            throw new IllegalArgumentException("hostManagedHeapSize cannot be negative");
        }
        if (maxMemoryPages < 0) {
            throw new IllegalArgumentException("maxMemoryPages cannot be negative");
        }
        ensureNotClosed();

        try {
            final long instanceHandle = nativeInstantiateEx(nativeHandle,
                defaultStackSize, hostManagedHeapSize, maxMemoryPages);
            if (instanceHandle == 0L) {
                throw new WasmRuntimeException(
                    "Failed to instantiate WebAssembly module with extended args");
            }
            return new JniWebAssemblyInstance(instanceHandle, 0L);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException(
                "Unexpected error during extended module instantiation", e);
        }
    }

    @Override
    public byte[] getCustomSection(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Custom section name cannot be null");
        }
        ensureNotClosed();

        try {
            return nativeGetCustomSection(nativeHandle, name);
        } catch (final Exception e) {
            LOGGER.fine("Failed to get custom section '" + name + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public WebAssemblyInstance instantiateEx2(final int defaultStackSize,
            final int hostManagedHeapSize, final int maxMemoryPages) throws WasmRuntimeException {
        ensureNotClosed();
        if (defaultStackSize < 0 || hostManagedHeapSize < 0 || maxMemoryPages < 0) {
            throw new IllegalArgumentException("Parameters must be non-negative");
        }
        final long instanceHandle = nativeInstantiateEx2(nativeHandle,
            defaultStackSize, hostManagedHeapSize, maxMemoryPages);
        if (instanceHandle == 0) {
            throw new WasmRuntimeException("Failed to instantiate module with ex2 API");
        }
        return new JniWebAssemblyInstance(instanceHandle, 0L);
    }

    @Override
    public int[] getExportGlobalTypeInfo(final String name) {
        ensureNotClosed();
        if (name == null) {
            return null;
        }
        return nativeGetExportGlobalTypeInfo(nativeHandle, name);
    }

    @Override
    public int[] getExportMemoryTypeInfo(final String name) {
        ensureNotClosed();
        if (name == null) {
            return null;
        }
        return nativeGetExportMemoryTypeInfo(nativeHandle, name);
    }

    @Override
    public boolean register(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Module name cannot be null");
        }
        ensureNotClosed();
        try {
            return nativeRegister(nativeHandle, name);
        } catch (final Exception e) {
            LOGGER.warning("Failed to register module: " + e.getMessage());
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
     * Instantiates a WebAssembly module.
     *
     * @param moduleHandle the native module handle
     * @return the native instance handle, or 0 on failure
     * @throws WasmRuntimeException if instantiation fails
     */
    private static native long nativeInstantiateModule(long moduleHandle) throws WasmRuntimeException;

    /**
     * Registers host functions with WAMR for import resolution.
     * Must be called before instantiation.
     *
     * @param imports map of module_name to map of func_name to HostFunction
     * @return the native registration handle, or 0 on failure
     */
    private static native long nativeRegisterHostFunctions(Map<String, Map<String, HostFunction>> imports);

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
     * Sets the name of a module.
     *
     * @param moduleHandle the native module handle
     * @param name the module name
     * @return true if set successfully
     */
    private static native boolean nativeSetModuleName(long moduleHandle, String name);

    /**
     * Gets the name of a module.
     *
     * @param moduleHandle the native module handle
     * @return the module name, or null
     */
    private static native String nativeGetModuleName(long moduleHandle);

    /**
     * Gets the hash string for a module.
     *
     * @param moduleHandle the native module handle
     * @return the module hash, or null
     */
    private static native String nativeGetModuleHash(long moduleHandle);

    /**
     * Gets the package type of a module.
     *
     * @param moduleHandle the native module handle
     * @return the native package type constant
     */
    private static native int nativeGetPackageType(long moduleHandle);

    /**
     * Gets the package version of a module.
     *
     * @param moduleHandle the native module handle
     * @return the package version number
     */
    private static native int nativeGetPackageVersion(long moduleHandle);

    /**
     * Checks if the underlying binary is freeable.
     *
     * @param moduleHandle the native module handle
     * @return true if freeable
     */
    private static native boolean nativeIsUnderlyingBinaryFreeable(long moduleHandle);

    /**
     * Sets WASI arguments on the module.
     *
     * @param moduleHandle the native module handle
     * @param dirList preopened directories
     * @param mapDirList mapped directories (guest::host format)
     * @param envVars environment variables (KEY=VALUE format)
     * @param argv command-line arguments
     */
    private static native void nativeSetWasiArgs(long moduleHandle,
        String[] dirList, String[] mapDirList, String[] envVars, String[] argv);

    /**
     * Sets WASI address pool on the module.
     *
     * @param moduleHandle the native module handle
     * @param addrPool allowed IP addresses
     */
    private static native void nativeSetWasiAddrPool(long moduleHandle, String[] addrPool);

    /**
     * Sets WASI NS lookup pool on the module.
     *
     * @param moduleHandle the native module handle
     * @param nsLookupPool allowed name servers
     */
    private static native void nativeSetWasiNsLookupPool(long moduleHandle, String[] nsLookupPool);

    /**
     * Instantiates a module with extended configuration.
     *
     * @param moduleHandle the native module handle
     * @param defaultStackSize the default stack size in bytes
     * @param hostManagedHeapSize the host-managed heap size in bytes
     * @param maxMemoryPages the maximum memory pages
     * @return the native instance handle, or 0 on failure
     * @throws WasmRuntimeException if instantiation fails
     */
    private static native long nativeInstantiateEx(long moduleHandle,
        int defaultStackSize, int hostManagedHeapSize, int maxMemoryPages)
        throws WasmRuntimeException;

    /**
     * Gets the raw bytes of a custom section by name.
     *
     * @param moduleHandle the native module handle
     * @param name the custom section name
     * @return the section data, or null if not found
     */
    private static native byte[] nativeGetCustomSection(long moduleHandle, String name);

    /**
     * Gets global type info for an exported global.
     *
     * @param moduleHandle the native module handle
     * @param name the export name
     * @return int array [valkind, is_mutable] or null
     */
    private static native int[] nativeGetExportGlobalTypeInfo(long moduleHandle, String name);

    /**
     * Gets memory type info for an exported memory.
     *
     * @param moduleHandle the native module handle
     * @param name the export name
     * @return int array [is_shared, init_page_count, max_page_count] or null
     */
    private static native int[] nativeGetExportMemoryTypeInfo(long moduleHandle, String name);

    /**
     * Instantiates a module using the opaque InstantiationArgs2 API.
     *
     * @param moduleHandle the native module handle
     * @param defaultStackSize the default stack size in bytes
     * @param hostManagedHeapSize the host-managed heap size in bytes
     * @param maxMemoryPages the maximum number of memory pages
     * @return the native instance handle, or 0 on failure
     * @throws WasmRuntimeException if instantiation fails
     */
    private static native long nativeInstantiateEx2(long moduleHandle,
        int defaultStackSize, int hostManagedHeapSize, int maxMemoryPages)
        throws WasmRuntimeException;

    /**
     * Registers a module under a given name for inter-module linking.
     *
     * @param moduleHandle the native module handle
     * @param name the name to register the module under
     * @return true if registration succeeded
     */
    private static native boolean nativeRegister(long moduleHandle, String name);
}