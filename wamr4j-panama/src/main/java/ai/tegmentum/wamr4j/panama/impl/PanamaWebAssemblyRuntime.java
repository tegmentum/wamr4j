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

import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly runtime.
 * 
 * <p>This class provides a WebAssembly runtime implementation using Panama FFI
 * to communicate with the native WAMR library. It provides better type safety
 * and performance compared to JNI while maintaining the same defensive
 * programming practices.
 * 
 * <p>All native calls are properly validated and errors are handled
 * gracefully to ensure JVM stability.
 * 
 * @since 1.0.0
 */
public final class PanamaWebAssemblyRuntime implements WebAssemblyRuntime {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyRuntime.class.getName());
    
    // Native runtime handle as MemorySegment
    private volatile MemorySegment nativeHandle;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle CREATE_RUNTIME;
        static final MethodHandle DESTROY_RUNTIME;
        static final MethodHandle COMPILE_MODULE;
        static final MethodHandle GET_VERSION;

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            CREATE_RUNTIME = linker.downcallHandle(
                lookup.find("wamr_runtime_init").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS));
            DESTROY_RUNTIME = linker.downcallHandle(
                lookup.find("wamr_runtime_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            COMPILE_MODULE = linker.downcallHandle(
                lookup.find("wamr_module_compile").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_VERSION = linker.downcallHandle(
                lookup.find("wamr_get_version").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        }
    }

    /**
     * Creates a new Panama WebAssembly runtime instance.
     * 
     * @throws ai.tegmentum.wamr4j.exception.WasmRuntimeException if the runtime cannot be initialized
     */
    public PanamaWebAssemblyRuntime() throws ai.tegmentum.wamr4j.exception.WasmRuntimeException {
        // Ensure native library is loaded
        NativeLibraryLoader.ensureLoaded();
        
        try {
            this.nativeHandle = (MemorySegment) Handles.CREATE_RUNTIME.invoke();
            if (nativeHandle.equals(MemorySegment.NULL)) {
                throw new ai.tegmentum.wamr4j.exception.WasmRuntimeException(
                    "Failed to create native WebAssembly runtime");
            }
            LOGGER.fine("Created Panama WebAssembly runtime with handle: " + nativeHandle);
        } catch (final Throwable e) {
            throw new ai.tegmentum.wamr4j.exception.WasmRuntimeException(
                "Failed to initialize Panama WebAssembly runtime", e);
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
        
        try (final Arena arena = Arena.ofConfined()) {
            // Allocate memory for WASM bytes and error buffer
            final MemorySegment wasmBuffer = arena.allocate(wasmBytes.length);
            MemorySegment.copy(wasmBytes, 0, wasmBuffer, ValueLayout.JAVA_BYTE, 0, wasmBytes.length);
            final int errorBufSize = 1024;
            final MemorySegment errorBuf = arena.allocate(errorBufSize);

            final MemorySegment moduleHandle = (MemorySegment) Handles.COMPILE_MODULE.invoke(
                nativeHandle, wasmBuffer, (long) wasmBytes.length, errorBuf, errorBufSize);

            if (moduleHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new CompilationException(
                    errorMsg.isEmpty() ? "Failed to compile WebAssembly module" : errorMsg);
            }
            
            return new PanamaWebAssemblyModule(moduleHandle);
        } catch (final CompilationException | ValidationException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
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
        return "Panama";
    }

    @Override
    public String getVersion() {
        ensureNotClosed();
        
        try {
            final MemorySegment versionPtr = (MemorySegment) Handles.GET_VERSION.invoke();
            if (versionPtr.equals(MemorySegment.NULL)) {
                return "unknown";
            }

            return versionPtr.reinterpret(256).getString(0);
        } catch (final Throwable e) {
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
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;
            
            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_RUNTIME.invoke(handle);
                    LOGGER.fine("Destroyed Panama WebAssembly runtime with handle: " + handle);
                } catch (final Throwable e) {
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
}