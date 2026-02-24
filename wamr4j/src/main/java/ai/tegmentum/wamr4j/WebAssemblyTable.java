/*
 * Copyright (c) 2024 Tegmentum AI, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.wamr4j;

import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Represents a WebAssembly table.
 *
 * <p>WebAssembly tables are arrays of reference values (typically function references) that
 * enable indirect function calls. Tables are used by the {@code call_indirect} instruction
 * to dispatch calls to functions by index rather than by name.
 *
 * <p>Table objects maintain a reference to their parent instance and become invalid when the
 * instance is closed. Tables are not thread-safe - concurrent access should be synchronized
 * by the caller.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WebAssemblyTable table = instance.getTable("__indirect_function_table");
 * int size = table.getSize();
 * ElementKind kind = table.getElementKind();
 *
 * // Get a function at a specific table index
 * WebAssemblyFunction fn = table.getFunctionAtIndex(0);
 * Object result = fn.invoke(42);
 *
 * // Call a function indirectly by table element index
 * Object result = table.callIndirect(0, new Object[]{42}, new int[]{0});
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WebAssemblyTable {

    /**
     * Returns the current number of elements in this table.
     *
     * @return the current table size, always >= 0
     * @throws IllegalStateException if the parent instance has been closed
     */
    int getSize();

    /**
     * Returns the maximum number of elements this table can hold.
     *
     * <p>A value of -1 indicates no maximum limit is set.
     *
     * @return the maximum table size, or -1 if unlimited
     * @throws IllegalStateException if the parent instance has been closed
     */
    int getMaxSize();

    /**
     * Returns the element kind stored in this table.
     *
     * @return the element kind (typically {@link ElementKind#FUNCREF}), or null if unknown
     * @throws IllegalStateException if the parent instance has been closed
     */
    ElementKind getElementKind();

    /**
     * Returns the name of this table.
     *
     * @return the table name, never null
     */
    String getName();

    /**
     * Retrieves the function stored at the specified index in this table.
     *
     * <p>The returned function can be invoked like any other exported function. Not all
     * table entries may contain valid function references - null entries will result in
     * a {@link WasmRuntimeException}.
     *
     * @param index the zero-based table element index
     * @return the function at the specified index, never null
     * @throws WasmRuntimeException if the index is out of bounds or the entry is not a valid function
     * @throws IllegalArgumentException if index is negative
     * @throws IllegalStateException if the parent instance has been closed
     */
    WebAssemblyFunction getFunctionAtIndex(int index) throws WasmRuntimeException;

    /**
     * Calls a function indirectly by its element index in this table.
     *
     * <p>This is equivalent to the WebAssembly {@code call_indirect} instruction. The function
     * at the given element index is called with the provided arguments.
     *
     * @param elementIndex the zero-based element index in the table
     * @param args the arguments to pass to the function, may be null or empty for void functions
     * @param resultTypeOrdinals array of result type ordinals (0=i32, 1=i64, 2=f32, 3=f64),
     *     may be null or empty for void functions
     * @return the result of the function call, or null for void functions
     * @throws WasmRuntimeException if the call fails (invalid index, type mismatch, trap, etc.)
     * @throws IllegalArgumentException if elementIndex is negative
     * @throws IllegalStateException if the parent instance has been closed
     */
    Object callIndirect(int elementIndex, Object[] args, int[] resultTypeOrdinals)
        throws WasmRuntimeException;

    /**
     * Checks if the parent instance is still valid.
     *
     * @return true if the table is still usable, false otherwise
     */
    boolean isValid();
}
