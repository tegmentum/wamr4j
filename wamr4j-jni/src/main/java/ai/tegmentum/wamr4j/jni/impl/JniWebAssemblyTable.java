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

import ai.tegmentum.wamr4j.ElementKind;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyTable;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly table.
 *
 * <p>This class represents a WebAssembly table using JNI to communicate with the
 * native WAMR library. It provides read-only access to table metadata and supports
 * indirect function calls via the table.
 *
 * @since 1.0.0
 */
public final class JniWebAssemblyTable implements WebAssemblyTable {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyTable.class.getName());

    // Native table handle
    private volatile long nativeHandle;

    // Table metadata
    private final String tableName;
    private final JniWebAssemblyInstance parentInstance;

    /**
     * Creates a new JNI WebAssembly table wrapper.
     *
     * @param nativeHandle the native table handle, must not be 0
     * @param tableName the name of the table, must not be null
     * @param parentInstance the parent instance, must not be null
     */
    public JniWebAssemblyTable(final long nativeHandle, final String tableName,
                               final JniWebAssemblyInstance parentInstance) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native table handle cannot be 0");
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

        LOGGER.fine("Created JNI WebAssembly table '" + tableName + "' with handle: " + nativeHandle);
    }

    @Override
    public int getSize() {
        ensureValid();

        try {
            return nativeGetSize(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get table size: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public int getMaxSize() {
        ensureValid();

        try {
            return nativeGetMaxSize(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get table max size: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public ElementKind getElementKind() {
        ensureValid();

        try {
            final int kind = nativeGetElementKind(nativeHandle);
            return ElementKind.fromNativeValue(kind);
        } catch (final Exception e) {
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

        ensureValid();

        try {
            final long functionHandle = nativeGetFunction(nativeHandle, index);
            if (functionHandle == 0L) {
                throw new WasmRuntimeException(
                    "No function at table index " + index + " in table '" + tableName + "'");
            }
            return new JniWebAssemblyFunction(functionHandle, tableName + "[" + index + "]", parentInstance);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException(
                "Unexpected error getting function at index " + index + " from table '" + tableName + "'", e);
        }
    }

    @Override
    public Object callIndirect(final int elementIndex, final Object[] args,
                               final int[] resultTypeOrdinals) throws WasmRuntimeException {
        if (elementIndex < 0) {
            throw new IllegalArgumentException("Element index cannot be negative: " + elementIndex);
        }

        ensureValid();

        try {
            return nativeCallIndirect(nativeHandle, elementIndex, args, resultTypeOrdinals);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException(
                "Unexpected error calling indirect at index " + elementIndex
                    + " in table '" + tableName + "'", e);
        }
    }

    @Override
    public boolean isValid() {
        return nativeHandle != 0L && parentInstance.isValid();
    }

    /**
     * Destroys the native table handle, freeing the Rust Box wrapper.
     * Called by the parent instance during cleanup.
     */
    void close() {
        final long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L) {
            try {
                nativeDestroyTable(handle);
            } catch (final Exception e) {
                LOGGER.warning("Error destroying native table '" + tableName + "': " + e.getMessage());
            }
        }
    }

    private void ensureValid() {
        if (!isValid()) {
            throw new IllegalStateException(
                "Table is no longer valid - parent instance has been closed");
        }
    }

    // Native method declarations

    /**
     * Destroys a native WebAssembly table handle.
     *
     * @param tableHandle the native table handle
     */
    private static native void nativeDestroyTable(long tableHandle);

    /**
     * Gets the current size of the table.
     *
     * @param tableHandle the native table handle
     * @return the table size
     */
    private static native int nativeGetSize(long tableHandle);

    /**
     * Gets the maximum size of the table.
     *
     * @param tableHandle the native table handle
     * @return the maximum table size
     */
    private static native int nativeGetMaxSize(long tableHandle);

    /**
     * Gets the element kind of the table.
     *
     * @param tableHandle the native table handle
     * @return the element kind native value
     */
    private static native int nativeGetElementKind(long tableHandle);

    /**
     * Gets a function from the table at the specified index.
     *
     * @param tableHandle the native table handle
     * @param index the table element index
     * @return the native function handle, or 0 if not found
     */
    private static native long nativeGetFunction(long tableHandle, int index);

    /**
     * Calls a function indirectly via the table.
     *
     * @param tableHandle the native table handle
     * @param elementIndex the table element index
     * @param args the function arguments
     * @param resultTypeOrdinals the result type ordinals (0=i32, 1=i64, 2=f32, 3=f64)
     * @return the function result, or null for void functions
     * @throws WasmRuntimeException if the call fails
     */
    private static native Object nativeCallIndirect(long tableHandle, int elementIndex,
        Object[] args, int[] resultTypeOrdinals) throws WasmRuntimeException;
}
