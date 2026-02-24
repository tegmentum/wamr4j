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

import ai.tegmentum.wamr4j.PackageType;
import ai.tegmentum.wamr4j.RunningMode;
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
        static final MethodHandle GET_VERSION_PARTS;
        static final MethodHandle IS_RUNNING_MODE_SUPPORTED;
        static final MethodHandle SET_DEFAULT_RUNNING_MODE;
        static final MethodHandle SET_LOG_LEVEL;
        static final MethodHandle INIT_THREAD_ENV;
        static final MethodHandle DESTROY_THREAD_ENV;
        static final MethodHandle IS_THREAD_ENV_INITED;
        static final MethodHandle SET_MAX_THREAD_NUM;
        static final MethodHandle GET_FILE_PACKAGE_TYPE;
        static final MethodHandle GET_CURRENT_PACKAGE_VERSION;

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
            GET_VERSION_PARTS = linker.downcallHandle(
                lookup.find("wamr_get_version_parts").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            IS_RUNNING_MODE_SUPPORTED = linker.downcallHandle(
                lookup.find("wamr_is_running_mode_supported").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            SET_DEFAULT_RUNNING_MODE = linker.downcallHandle(
                lookup.find("wamr_set_default_running_mode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            SET_LOG_LEVEL = linker.downcallHandle(
                lookup.find("wamr_set_log_level").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            INIT_THREAD_ENV = resolveOptional(lookup, linker,
                "wamr_init_thread_env",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            DESTROY_THREAD_ENV = resolveOptional(lookup, linker,
                "wamr_destroy_thread_env",
                FunctionDescriptor.ofVoid());
            IS_THREAD_ENV_INITED = resolveOptional(lookup, linker,
                "wamr_is_thread_env_inited",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            SET_MAX_THREAD_NUM = resolveOptional(lookup, linker,
                "wamr_set_max_thread_num",
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            GET_FILE_PACKAGE_TYPE = resolveOptional(lookup, linker,
                "wamr_get_file_package_type",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_CURRENT_PACKAGE_VERSION = resolveOptional(lookup, linker,
                "wamr_get_current_package_version",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        }

        private static MethodHandle resolveOptional(final SymbolLookup lookup, final Linker linker,
                final String name, final FunctionDescriptor desc) {
            final var symbol = lookup.find(name);
            return symbol.isPresent() ? linker.downcallHandle(symbol.get(), desc) : null;
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
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            final MemorySegment moduleHandle = (MemorySegment) Handles.COMPILE_MODULE.invoke(
                nativeHandle, wasmBuffer, (long) wasmBytes.length, errorBuf, WasmTypes.ERROR_BUF_SIZE);

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
    public int getMajorVersion() {
        ensureNotClosed();
        return getVersionPart(0);
    }

    @Override
    public int getMinorVersion() {
        ensureNotClosed();
        return getVersionPart(1);
    }

    @Override
    public int getPatchVersion() {
        ensureNotClosed();
        return getVersionPart(2);
    }

    @Override
    public boolean isRunningModeSupported(final RunningMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Running mode cannot be null");
        }
        ensureNotClosed();
        try {
            final int result = (int) Handles.IS_RUNNING_MODE_SUPPORTED.invoke(mode.getNativeValue());
            return result != 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to check running mode support: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setDefaultRunningMode(final RunningMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Running mode cannot be null");
        }
        ensureNotClosed();
        try {
            final int result = (int) Handles.SET_DEFAULT_RUNNING_MODE.invoke(mode.getNativeValue());
            return result == 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set default running mode: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setLogLevel(final int level) {
        ensureNotClosed();
        try {
            Handles.SET_LOG_LEVEL.invoke(level);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set log level: " + e.getMessage());
        }
    }

    @Override
    public boolean initThreadEnv() {
        ensureNotClosed();

        if (Handles.INIT_THREAD_ENV == null) {
            LOGGER.warning("Native function 'wamr_init_thread_env' not available");
            return false;
        }

        try {
            final int result = (int) Handles.INIT_THREAD_ENV.invoke();
            return result == 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to init thread env: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void destroyThreadEnv() {
        ensureNotClosed();

        if (Handles.DESTROY_THREAD_ENV == null) {
            LOGGER.warning("Native function 'wamr_destroy_thread_env' not available");
            return;
        }

        try {
            Handles.DESTROY_THREAD_ENV.invoke();
        } catch (final Throwable e) {
            LOGGER.warning("Failed to destroy thread env: " + e.getMessage());
        }
    }

    @Override
    public boolean isThreadEnvInited() {
        ensureNotClosed();

        if (Handles.IS_THREAD_ENV_INITED == null) {
            return false;
        }

        try {
            final int result = (int) Handles.IS_THREAD_ENV_INITED.invoke();
            return result != 0;
        } catch (final Throwable e) {
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

        if (Handles.SET_MAX_THREAD_NUM == null) {
            LOGGER.warning("Native function 'wamr_set_max_thread_num' not available");
            return;
        }

        try {
            Handles.SET_MAX_THREAD_NUM.invoke(num);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set max thread num: " + e.getMessage());
        }
    }

    @Override
    public PackageType getFilePackageType(final byte[] wasmBytes) {
        if (wasmBytes == null) {
            throw new IllegalArgumentException("WebAssembly bytes cannot be null");
        }
        ensureNotClosed();

        if (Handles.GET_FILE_PACKAGE_TYPE == null) {
            LOGGER.warning("wamr_get_file_package_type not available");
            return PackageType.UNKNOWN;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment buf = arena.allocate(wasmBytes.length);
            MemorySegment.copy(wasmBytes, 0, buf, ValueLayout.JAVA_BYTE, 0, wasmBytes.length);
            final int typeVal = (int) Handles.GET_FILE_PACKAGE_TYPE.invoke(buf, wasmBytes.length);
            return PackageType.fromNativeValue(typeVal);
        } catch (final Throwable e) {
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

        if (Handles.GET_CURRENT_PACKAGE_VERSION == null) {
            LOGGER.warning("wamr_get_current_package_version not available");
            return 0;
        }

        try {
            return (int) Handles.GET_CURRENT_PACKAGE_VERSION.invoke(packageType.getNativeValue());
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get current package version: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Retrieves a specific version part (0=major, 1=minor, 2=patch).
     */
    private int getVersionPart(final int index) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment majorPtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment minorPtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment patchPtr = arena.allocate(ValueLayout.JAVA_INT);
            Handles.GET_VERSION_PARTS.invoke(majorPtr, minorPtr, patchPtr);
            return switch (index) {
                case 0 -> majorPtr.get(ValueLayout.JAVA_INT, 0);
                case 1 -> minorPtr.get(ValueLayout.JAVA_INT, 0);
                case 2 -> patchPtr.get(ValueLayout.JAVA_INT, 0);
                default -> 0;
            };
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get version part: " + e.getMessage());
            return 0;
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