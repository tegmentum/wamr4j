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
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly module.
 *
 * <p>This class represents a compiled WebAssembly module using Panama FFI
 * to communicate with the native WAMR library. It provides thread-safe
 * access to module information and instance creation with better type
 * safety than JNI.
 *
 * @since 1.0.0
 */
public final class PanamaWebAssemblyModule implements WebAssemblyModule {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyModule.class.getName());

    private static final long DEFAULT_STACK_SIZE = 16L * 1024;
    private static final long DEFAULT_HEAP_SIZE = 16L * 1024 * 1024;

    // Native module handle as MemorySegment
    private volatile MemorySegment nativeHandle;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle DESTROY_MODULE;
        static final MethodHandle INSTANTIATE;

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            DESTROY_MODULE = linker.downcallHandle(
                lookup.find("wamr_module_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            INSTANTIATE = linker.downcallHandle(
                lookup.find("wamr_instance_create").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
        }
    }

    /**
     * Creates a new Panama WebAssembly module wrapper.
     *
     * @param nativeHandle the native module handle, must not be NULL
     */
    public PanamaWebAssemblyModule(final MemorySegment nativeHandle) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native module handle cannot be null or NULL");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created Panama WebAssembly module with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyInstance instantiate() throws WasmRuntimeException {
        return instantiate(Map.of());
    }

    @Override
    public WebAssemblyInstance instantiate(final Map<String, Map<String, Object>> imports) throws WasmRuntimeException {
        if (imports != null && !imports.isEmpty()) {
            throw new UnsupportedOperationException("Import handling is not yet supported");
        }

        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            final int errorBufSize = 1024;
            final MemorySegment errorBuf = arena.allocate(errorBufSize);

            final MemorySegment instanceHandle = (MemorySegment) Handles.INSTANTIATE.invoke(
                nativeHandle, DEFAULT_STACK_SIZE, DEFAULT_HEAP_SIZE, errorBuf, errorBufSize);
            if (instanceHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Failed to instantiate WebAssembly module" : errorMsg);
            }

            return new PanamaWebAssemblyInstance(instanceHandle);
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error during module instantiation", e);
        }
    }

    @Override
    public String[] getExportNames() {
        ensureNotClosed();
        // Export names are only available on the instance level via wamr_get_function_names
        return new String[0];
    }

    @Override
    public String[] getImportNames() {
        ensureNotClosed();
        // WAMR does not expose import enumeration at the module level
        return new String[0];
    }

    @Override
    public FunctionSignature getExportFunctionSignature(final String functionName) {
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }
        ensureNotClosed();
        // Function signatures are only available on instantiated function handles
        return new FunctionSignature(new ValueType[0], new ValueType[0]);
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
                    Handles.DESTROY_MODULE.invoke(handle);
                    LOGGER.fine("Destroyed Panama WebAssembly module with handle: " + handle);
                } catch (final Throwable e) {
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
}
