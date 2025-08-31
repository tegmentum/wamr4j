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
     * @throws ai.tegmentum.wamr4j.exception.RuntimeException if the runtime cannot be initialized
     */
    public JniWebAssemblyRuntime() throws ai.tegmentum.wamr4j.exception.RuntimeException {
        // Ensure native library is loaded
        NativeLibraryLoader.ensureLoaded();
        
        try {
            this.nativeHandle = nativeCreateRuntime();
            if (nativeHandle == 0L) {
                throw new ai.tegmentum.wamr4j.exception.RuntimeException(
                    "Failed to create native WebAssembly runtime");
            }
            LOGGER.fine("Created JNI WebAssembly runtime with handle: " + nativeHandle);
        } catch (final Exception e) {
            throw new ai.tegmentum.wamr4j.exception.RuntimeException(
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
}