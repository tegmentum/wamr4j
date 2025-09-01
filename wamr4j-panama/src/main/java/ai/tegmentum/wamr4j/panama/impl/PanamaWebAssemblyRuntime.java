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
    
    // Function descriptors for native calls
    private static final FunctionDescriptor CREATE_RUNTIME_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS);
    private static final FunctionDescriptor DESTROY_RUNTIME_DESC = 
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
    private static final FunctionDescriptor COMPILE_MODULE_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
    private static final FunctionDescriptor GET_VERSION_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS);

    /**
     * Creates a new Panama WebAssembly runtime instance.
     * 
     * @throws ai.tegmentum.wamr4j.exception.RuntimeException if the runtime cannot be initialized
     */
    public PanamaWebAssemblyRuntime() throws ai.tegmentum.wamr4j.exception.RuntimeException {
        // Ensure native library is loaded
        NativeLibraryLoader.ensureLoaded();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment createRuntimeFunc = lookup.find("wamr_create_runtime")
                .orElseThrow(() -> new ai.tegmentum.wamr4j.exception.RuntimeException(
                    "Native function 'wamr_create_runtime' not found"));
            
            final MethodHandle createRuntime = Linker.nativeLinker()
                .downcallHandle(createRuntimeFunc, CREATE_RUNTIME_DESC);
            
            this.nativeHandle = (MemorySegment) createRuntime.invoke();
            if (nativeHandle.equals(MemorySegment.NULL)) {
                throw new ai.tegmentum.wamr4j.exception.RuntimeException(
                    "Failed to create native WebAssembly runtime");
            }
            LOGGER.fine("Created Panama WebAssembly runtime with handle: " + nativeHandle);
        } catch (final Throwable e) {
            throw new ai.tegmentum.wamr4j.exception.RuntimeException(
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
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment compileModuleFunc = lookup.find("wamr_compile_module")
                .orElseThrow(() -> new CompilationException(
                    "Native function 'wamr_compile_module' not found"));
            
            final MethodHandle compileModule = Linker.nativeLinker()
                .downcallHandle(compileModuleFunc, COMPILE_MODULE_DESC);
            
            // Allocate memory for WASM bytes
            final MemorySegment wasmBuffer = arena.allocate(wasmBytes.length);
            MemorySegment.copy(wasmBytes, 0, wasmBuffer, ValueLayout.JAVA_BYTE, 0, wasmBytes.length);
            
            final MemorySegment moduleHandle = (MemorySegment) compileModule.invoke(
                nativeHandle, wasmBuffer, wasmBytes.length);
                
            if (moduleHandle.equals(MemorySegment.NULL)) {
                throw new CompilationException("Failed to compile WebAssembly module");
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
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getVersionFunc = lookup.find("wamr_get_version")
                .orElse(MemorySegment.NULL);
                
            if (getVersionFunc.equals(MemorySegment.NULL)) {
                return "unknown";
            }
            
            final MethodHandle getVersion = Linker.nativeLinker()
                .downcallHandle(getVersionFunc, GET_VERSION_DESC);
            
            final MemorySegment versionPtr = (MemorySegment) getVersion.invoke();
            if (versionPtr.equals(MemorySegment.NULL)) {
                return "unknown";
            }
            
            return versionPtr.getString(0);
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
                    final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
                    final MemorySegment destroyRuntimeFunc = lookup.find("wamr_destroy_runtime")
                        .orElse(MemorySegment.NULL);
                        
                    if (!destroyRuntimeFunc.equals(MemorySegment.NULL)) {
                        final MethodHandle destroyRuntime = Linker.nativeLinker()
                            .downcallHandle(destroyRuntimeFunc, DESTROY_RUNTIME_DESC);
                        destroyRuntime.invoke(handle);
                        LOGGER.fine("Destroyed Panama WebAssembly runtime with handle: " + handle);
                    }
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