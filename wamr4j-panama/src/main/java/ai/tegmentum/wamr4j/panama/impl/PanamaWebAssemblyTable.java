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

import ai.tegmentum.wamr4j.ElementKind;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyTable;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly table.
 *
 * <p>This class represents a WebAssembly table using Panama FFI to communicate
 * with the native WAMR library. It provides read-only access to table metadata
 * and supports indirect function calls via the table.
 *
 * @since 1.0.0
 */
public final class PanamaWebAssemblyTable implements WebAssemblyTable {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyTable.class.getName());

    /** Size of WasmValueFFI struct: i32 type + padding + f64 value = 16 bytes. */
    private static final long WASM_VALUE_FFI_SIZE = 16;

    // Native table handle as MemorySegment
    private volatile MemorySegment nativeHandle;

    // Table metadata
    private final String tableName;

    // Parent instance for lifecycle tracking
    private final PanamaWebAssemblyInstance parentInstance;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle DESTROY_TABLE;
        static final MethodHandle GET_SIZE;
        static final MethodHandle GET_MAX_SIZE;
        static final MethodHandle GET_ELEM_KIND;
        static final MethodHandle GET_FUNCTION;
        static final MethodHandle CALL_INDIRECT;

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            DESTROY_TABLE = linker.downcallHandle(
                lookup.find("wamr_table_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            GET_SIZE = linker.downcallHandle(
                lookup.find("wamr_table_size").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_MAX_SIZE = linker.downcallHandle(
                lookup.find("wamr_table_max_size").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_ELEM_KIND = linker.downcallHandle(
                lookup.find("wamr_table_elem_kind").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_FUNCTION = linker.downcallHandle(
                lookup.find("wamr_table_get_function").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            CALL_INDIRECT = linker.downcallHandle(
                lookup.find("wamr_call_indirect").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, // instance
                    ValueLayout.JAVA_INT, // element_index
                    ValueLayout.ADDRESS, // args
                    ValueLayout.JAVA_INT, // arg_count
                    ValueLayout.ADDRESS, // results
                    ValueLayout.JAVA_INT, // result_capacity
                    ValueLayout.ADDRESS, // result_type_tags
                    ValueLayout.JAVA_INT, // result_type_count
                    ValueLayout.ADDRESS, // error_buf
                    ValueLayout.JAVA_INT)); // error_buf_size
        }
    }

    /**
     * Creates a new Panama WebAssembly table wrapper.
     *
     * @param nativeHandle the native table handle, must not be NULL
     * @param tableName the name of the table, must not be null
     * @param parentInstance the parent instance that owns this table
     */
    public PanamaWebAssemblyTable(final MemorySegment nativeHandle, final String tableName,
                                  final PanamaWebAssemblyInstance parentInstance) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native table handle cannot be null or NULL");
        }
        if (tableName == null) {
            throw new IllegalArgumentException("Table name cannot be null");
        }
        if (parentInstance == null) {
            throw new IllegalArgumentException("Parent instance cannot be null");
        }

        this.nativeHandle = nativeHandle;
        this.tableName = tableName;
        this.parentInstance = parentInstance;
        LOGGER.fine("Created Panama WebAssembly table '" + tableName + "' with handle: " + nativeHandle);
    }

    @Override
    public int getSize() {
        ensureNotClosed();

        try {
            return (int) Handles.GET_SIZE.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get table size: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public int getMaxSize() {
        ensureNotClosed();

        try {
            return (int) Handles.GET_MAX_SIZE.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get table max size: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public ElementKind getElementKind() {
        ensureNotClosed();

        try {
            final int kind = (int) Handles.GET_ELEM_KIND.invoke(nativeHandle);
            return ElementKind.fromNativeValue(kind);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get table element kind: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() {
        return tableName;
    }

    @Override
    public WebAssemblyFunction getFunctionAtIndex(final int index) throws WasmRuntimeException {
        if (index < 0) {
            throw new IllegalArgumentException("Table index cannot be negative: " + index);
        }

        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            final MemorySegment functionHandle = (MemorySegment) Handles.GET_FUNCTION.invoke(
                nativeHandle, index, errorBuf, WasmTypes.ERROR_BUF_SIZE);

            if (functionHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty()
                        ? "No function at table index " + index + " in table '" + tableName + "'"
                        : errorMsg);
            }

            return new PanamaWebAssemblyFunction(
                functionHandle, tableName + "[" + index + "]", parentInstance);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException(
                "Unexpected error getting function at index " + index
                    + " from table '" + tableName + "'", e);
        }
    }

    @Override
    public Object callIndirect(final int elementIndex, final Object[] args,
                               final int[] resultTypeOrdinals) throws WasmRuntimeException {
        if (elementIndex < 0) {
            throw new IllegalArgumentException("Element index cannot be negative: " + elementIndex);
        }

        ensureNotClosed();

        try (final Arena arena = Arena.ofConfined()) {
            // Convert arguments to WasmValueFFI structs
            final int argCount = (args != null) ? args.length : 0;
            final MemorySegment argsBuffer = arena.allocate(
                WASM_VALUE_FFI_SIZE * Math.max(argCount, 1));

            for (int i = 0; i < argCount; i++) {
                final long offset = i * WASM_VALUE_FFI_SIZE;
                final Object arg = args[i];

                if (arg instanceof Integer) {
                    argsBuffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.I32);
                    argsBuffer.set(ValueLayout.JAVA_INT, offset + 8, (Integer) arg);
                } else if (arg instanceof Long) {
                    argsBuffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.I64);
                    argsBuffer.set(ValueLayout.JAVA_LONG, offset + 8, (Long) arg);
                } else if (arg instanceof Float) {
                    argsBuffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.F32);
                    argsBuffer.set(ValueLayout.JAVA_FLOAT, offset + 8, (Float) arg);
                } else if (arg instanceof Double) {
                    argsBuffer.set(ValueLayout.JAVA_INT, offset, WasmTypes.F64);
                    argsBuffer.set(ValueLayout.JAVA_DOUBLE, offset + 8, (Double) arg);
                } else {
                    throw new IllegalArgumentException(
                        "Unsupported argument type: " + (arg == null ? "null" : arg.getClass().getName()));
                }
            }

            // Convert result type ordinals to native int array
            final int resultTypeCount = (resultTypeOrdinals != null) ? resultTypeOrdinals.length : 0;
            final MemorySegment resultTypeTags = arena.allocate(
                ValueLayout.JAVA_INT, Math.max(resultTypeCount, 1));
            for (int i = 0; i < resultTypeCount; i++) {
                resultTypeTags.setAtIndex(ValueLayout.JAVA_INT, i, resultTypeOrdinals[i]);
            }

            // Allocate results buffer
            final int resultCapacity = Math.max(resultTypeCount, 8);
            final MemorySegment resultsBuffer = arena.allocate(
                WASM_VALUE_FFI_SIZE * resultCapacity);
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            // wamr_call_indirect takes an instance handle as first parameter
            final int resultCount = (int) Handles.CALL_INDIRECT.invoke(
                getParentNativeHandle(),
                elementIndex,
                argsBuffer, argCount,
                resultsBuffer, resultCapacity,
                resultTypeTags, resultTypeCount,
                errorBuf, WasmTypes.ERROR_BUF_SIZE);

            if (resultCount < 0) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty()
                        ? "Indirect call failed at index " + elementIndex
                            + " in table '" + tableName + "'"
                        : errorMsg);
            }

            if (resultCount == 0) {
                return null;
            }

            return convertFirstResult(resultsBuffer);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException(
                "Unexpected error calling indirect at index " + elementIndex
                    + " in table '" + tableName + "'", e);
        }
    }

    @Override
    public boolean isValid() {
        return !parentInstance.isClosed() && !closed.get()
            && nativeHandle != null && !nativeHandle.equals(MemorySegment.NULL);
    }

    /**
     * Destroys the native table handle, freeing the Rust Box wrapper.
     * Called by the parent instance during cleanup.
     */
    void close() {
        if (closed.compareAndSet(false, true)) {
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;
            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_TABLE.invoke(handle);
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native table '" + tableName + "': " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get() || parentInstance.isClosed()) {
            throw new IllegalStateException(
                "Table is no longer valid - parent instance has been closed");
        }
    }

    /**
     * Gets the parent instance's native handle for call_indirect.
     * Package-private so the parent can expose it.
     */
    private MemorySegment getParentNativeHandle() {
        return parentInstance.getNativeHandle();
    }

    private Object convertFirstResult(final MemorySegment resultsBuffer) {
        final int type = resultsBuffer.get(ValueLayout.JAVA_INT, 0);

        switch (type) {
            case WasmTypes.I32:
                return resultsBuffer.get(ValueLayout.JAVA_INT, 8);
            case WasmTypes.I64:
                return resultsBuffer.get(ValueLayout.JAVA_LONG, 8);
            case WasmTypes.F32:
                return resultsBuffer.get(ValueLayout.JAVA_FLOAT, 8);
            case WasmTypes.F64:
                return resultsBuffer.get(ValueLayout.JAVA_DOUBLE, 8);
            default:
                LOGGER.warning("Unknown result type: " + type);
                return 0;
        }
    }
}
