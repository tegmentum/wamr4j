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

import ai.tegmentum.wamr4j.PackageType;
import ai.tegmentum.wamr4j.RunningMode;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;
import ai.tegmentum.wamr4j.jni.internal.NativeLibraryLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly runtime.
 * 
 * <p>This class provides a WebAssembly runtime implementation using JNI
 * to communicate with the native WAMR library. It handles resource
 * management, error translation, and provides defensive programming
 * practices to prevent JVM crashes.
 * 
 * <p>All native calls are properly validated and errors are handled
 * gracefully to ensure JVM stability.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyRuntime implements WebAssemblyRuntime {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyRuntime.class.getName());
    
    // Native runtime handle
    private volatile long nativeHandle = 0L;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new JNI WebAssembly runtime instance.
     * 
     * @throws ai.tegmentum.wamr4j.exception.WasmRuntimeException if the runtime cannot be initialized
     */
    public JniWebAssemblyRuntime() throws ai.tegmentum.wamr4j.exception.WasmRuntimeException {
        // Ensure native library is loaded
        NativeLibraryLoader.ensureLoaded();
        
        try {
            this.nativeHandle = nativeCreateRuntime();
            if (nativeHandle == 0L) {
                throw new ai.tegmentum.wamr4j.exception.WasmRuntimeException(
                    "Failed to create native WebAssembly runtime");
            }
            LOGGER.fine("Created JNI WebAssembly runtime with handle: " + nativeHandle);
        } catch (final Exception e) {
            throw new ai.tegmentum.wamr4j.exception.WasmRuntimeException(
                "Failed to initialize JNI WebAssembly runtime", e);
        }
    }

    @Override
    public WebAssemblyModule compile(final byte[] wasmBytes) throws CompilationException, ValidationException {
        // Defensive programming - validate inputs
        if (wasmBytes == null) {
            throw new IllegalArgumentException("WebAssembly bytes cannot be null");
        }
        if (wasmBytes.length == 0) {
            throw new IllegalArgumentException("WebAssembly bytes cannot be empty");
        }
        
        ensureNotClosed();
        
        try {
            final long moduleHandle = nativeCompileModule(nativeHandle, wasmBytes);
            if (moduleHandle == 0L) {
                throw new CompilationException("Failed to compile WebAssembly module");
            }
            
            return new JniWebAssemblyModule(moduleHandle);
        } catch (final CompilationException | ValidationException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new CompilationException("Unexpected error during module compilation", e);
        }
    }

    @Override
    public WebAssemblyModule compile(final Path wasmFile) throws CompilationException, ValidationException, IOException {
        // Defensive programming - validate inputs
        if (wasmFile == null) {
            throw new IllegalArgumentException("WebAssembly file path cannot be null");
        }
        
        ensureNotClosed();
        
        try {
            final byte[] wasmBytes = Files.readAllBytes(wasmFile);
            return compile(wasmBytes);
        } catch (final IOException e) {
            throw e; // Re-throw IO exceptions as-is
        } catch (final Exception e) {
            throw new CompilationException("Failed to read WebAssembly file: " + wasmFile, e);
        }
    }

    @Override
    public String getImplementation() {
        return "JNI";
    }

    @Override
    public String getVersion() {
        ensureNotClosed();

        try {
            final String version = nativeGetVersion();
            return version != null ? version : "unknown";
        } catch (final Exception e) {
            LOGGER.warning("Failed to get WAMR version: " + e.getMessage());
            return "unknown";
        }
    }

    @Override
    public int getMajorVersion() {
        ensureNotClosed();
        return nativeGetMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        ensureNotClosed();
        return nativeGetMinorVersion();
    }

    @Override
    public int getPatchVersion() {
        ensureNotClosed();
        return nativeGetPatchVersion();
    }

    @Override
    public boolean isRunningModeSupported(final RunningMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Running mode cannot be null");
        }
        ensureNotClosed();
        return nativeIsRunningModeSupported(mode.getNativeValue());
    }

    @Override
    public boolean setDefaultRunningMode(final RunningMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Running mode cannot be null");
        }
        ensureNotClosed();
        return nativeSetDefaultRunningMode(mode.getNativeValue());
    }

    @Override
    public void setLogLevel(final int level) {
        ensureNotClosed();
        nativeSetLogLevel(level);
    }

    @Override
    public boolean initThreadEnv() {
        ensureNotClosed();
        try {
            return nativeInitThreadEnv();
        } catch (final Exception e) {
            LOGGER.warning("Failed to init thread env: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void destroyThreadEnv() {
        ensureNotClosed();
        try {
            nativeDestroyThreadEnv();
        } catch (final Exception e) {
            LOGGER.warning("Failed to destroy thread env: " + e.getMessage());
        }
    }

    @Override
    public boolean isThreadEnvInited() {
        ensureNotClosed();
        try {
            return nativeIsThreadEnvInited();
        } catch (final Exception e) {
            LOGGER.warning("Failed to check thread env: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setMaxThreadNum(final int num) {
        if (num < 0) {
            throw new IllegalArgumentException("Max thread number cannot be negative: " + num);
        }
        ensureNotClosed();
        try {
            nativeSetMaxThreadNum(num);
        } catch (final Exception e) {
            LOGGER.warning("Failed to set max thread num: " + e.getMessage());
        }
    }

    @Override
    public PackageType getFilePackageType(final byte[] wasmBytes) {
        if (wasmBytes == null) {
            throw new IllegalArgumentException("WebAssembly bytes cannot be null");
        }
        ensureNotClosed();
        try {
            return PackageType.fromNativeValue(nativeGetFilePackageType(wasmBytes));
        } catch (final Exception e) {
            LOGGER.warning("Failed to get file package type: " + e.getMessage());
            return PackageType.UNKNOWN;
        }
    }

    @Override
    public int getCurrentPackageVersion(final PackageType packageType) {
        if (packageType == null) {
            throw new IllegalArgumentException("Package type cannot be null");
        }
        ensureNotClosed();
        try {
            return nativeGetCurrentPackageVersion(packageType.getNativeValue());
        } catch (final Exception e) {
            LOGGER.warning("Failed to get current package version: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isImportFuncLinked(final String moduleName, final String funcName) {
        ensureNotClosed();
        if (moduleName == null || funcName == null) {
            return false;
        }
        return nativeIsImportFuncLinked(moduleName, funcName);
    }

    @Override
    public boolean isImportGlobalLinked(final String moduleName, final String globalName) {
        ensureNotClosed();
        if (moduleName == null || globalName == null) {
            return false;
        }
        return nativeIsImportGlobalLinked(moduleName, globalName);
    }

    @Override
    public int[] getMemAllocInfo() {
        ensureNotClosed();
        return nativeGetMemAllocInfo();
    }

    @Override
    public long createContextKey() {
        ensureNotClosed();
        return nativeCreateContextKey();
    }

    @Override
    public void destroyContextKey(final long key) {
        ensureNotClosed();
        if (key != 0) {
            nativeDestroyContextKey(key);
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
                    nativeDestroyRuntime(handle);
                    LOGGER.fine("Destroyed JNI WebAssembly runtime with handle: " + handle);
                } catch (final Exception e) {
                    LOGGER.warning("Error destroying native runtime: " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly runtime has been closed");
        }
    }

    // Native method declarations
    
    /**
     * Creates a new native WebAssembly runtime instance.
     * 
     * @return the native runtime handle, or 0 on failure
     */
    private static native long nativeCreateRuntime();

    /**
     * Destroys a native WebAssembly runtime instance.
     * 
     * @param runtimeHandle the native runtime handle
     */
    private static native void nativeDestroyRuntime(long runtimeHandle);

    /**
     * Compiles WebAssembly bytecode into a module.
     * 
     * @param runtimeHandle the native runtime handle
     * @param wasmBytes the WebAssembly bytecode
     * @return the native module handle, or 0 on failure
     * @throws CompilationException if compilation fails
     * @throws ValidationException if validation fails
     */
    private static native long nativeCompileModule(long runtimeHandle, byte[] wasmBytes)
        throws CompilationException, ValidationException;

    /**
     * Gets the version of the underlying WAMR runtime.
     *
     * @return the WAMR version string
     */
    private static native String nativeGetVersion();

    /**
     * Gets the major version number of the underlying WAMR runtime.
     *
     * @return the major version number
     */
    private static native int nativeGetMajorVersion();

    /**
     * Gets the minor version number of the underlying WAMR runtime.
     *
     * @return the minor version number
     */
    private static native int nativeGetMinorVersion();

    /**
     * Gets the patch version number of the underlying WAMR runtime.
     *
     * @return the patch version number
     */
    private static native int nativeGetPatchVersion();

    /**
     * Checks if a running mode is supported.
     *
     * @param mode the native running mode constant
     * @return true if supported
     */
    private static native boolean nativeIsRunningModeSupported(int mode);

    /**
     * Sets the default running mode for new module instances.
     *
     * @param mode the native running mode constant
     * @return true if set successfully
     */
    private static native boolean nativeSetDefaultRunningMode(int mode);

    /**
     * Sets the WAMR log verbosity level.
     *
     * @param level the log level (0-5)
     */
    private static native void nativeSetLogLevel(int level);

    /**
     * Initializes the thread environment for the current native thread.
     *
     * @return true if initialization succeeded
     */
    private static native boolean nativeInitThreadEnv();

    /**
     * Destroys the thread environment for the current native thread.
     */
    private static native void nativeDestroyThreadEnv();

    /**
     * Checks if the thread environment has been initialized.
     *
     * @return true if initialized
     */
    private static native boolean nativeIsThreadEnvInited();

    /**
     * Sets the maximum number of threads.
     *
     * @param num the maximum number of threads
     */
    private static native void nativeSetMaxThreadNum(int num);

    /**
     * Determines the package type of a WebAssembly binary buffer.
     *
     * @param wasmBytes the binary data to inspect
     * @return the native package type constant
     */
    private static native int nativeGetFilePackageType(byte[] wasmBytes);

    /**
     * Returns the current supported package version for a given package type.
     *
     * @param packageType the native package type constant
     * @return the current supported version number
     */
    private static native int nativeGetCurrentPackageVersion(int packageType);

    /**
     * Check if an import function is linked.
     *
     * @param moduleName the import module name
     * @param funcName the import function name
     * @return true if linked
     */
    private static native boolean nativeIsImportFuncLinked(String moduleName, String funcName);

    /**
     * Check if an import global is linked.
     *
     * @param moduleName the import module name
     * @param globalName the import global name
     * @return true if linked
     */
    private static native boolean nativeIsImportGlobalLinked(String moduleName, String globalName);

    /**
     * Get memory allocation info.
     *
     * @return int array [total_size, total_free_size, highmark_size] or null
     */
    private static native int[] nativeGetMemAllocInfo();

    /**
     * Create a context key.
     *
     * @return native context key handle
     */
    private static native long nativeCreateContextKey();

    /**
     * Destroy a context key.
     *
     * @param key the context key handle
     */
    private static native void nativeDestroyContextKey(long key);
}