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
 * Represents an instantiated WebAssembly module with its own execution state.
 *
 * <p>A WebAssembly instance is created from a module and maintains its own execution state,
 * including memory, global variables, and table entries. Instances are isolated from each other -
 * modifications to one instance do not affect others.
 *
 * <p>Instances are not thread-safe by default. Concurrent access to the same instance should be
 * synchronized by the caller. However, different instances can be used concurrently without
 * synchronization.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try (WebAssemblyInstance instance = module.instantiate()) {
 *     WebAssemblyFunction function = instance.getFunction("add");
 *     Object result = function.invoke(42, 24);
 *
 *     WebAssemblyMemory memory = instance.getMemory();
 *     byte[] data = memory.read(0, 100);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WebAssemblyInstance extends AutoCloseable {

    /**
     * Retrieves an exported function from this instance.
     *
     * <p>Functions are the primary way to interact with WebAssembly code. The returned function can
     * be invoked with appropriate arguments to execute the WebAssembly code.
     *
     * @param functionName the name of the exported function, must not be null
     * @return the WebAssembly function, never null
     * @throws WasmRuntimeException if the function is not found or cannot be retrieved
     * @throws IllegalArgumentException if functionName is null
     * @throws IllegalStateException if the instance has been closed
     */
    WebAssemblyFunction getFunction(String functionName) throws WasmRuntimeException;

    /**
     * Retrieves the memory exported by this instance.
     *
     * <p>WebAssembly memory is a linear array of bytes that can be read from and written to. Not
     * all modules export memory - this method will throw an exception if memory is not available.
     *
     * @return the WebAssembly memory, never null
     * @throws WasmRuntimeException if memory is not exported or cannot be retrieved
     * @throws IllegalStateException if the instance has been closed
     */
    WebAssemblyMemory getMemory() throws WasmRuntimeException;

    /**
     * Retrieves a global variable exported by this instance.
     *
     * @param globalName the name of the exported global variable, must not be null
     * @return the current value of the global variable
     * @throws WasmRuntimeException if the global is not found or cannot be retrieved
     * @throws IllegalArgumentException if globalName is null
     * @throws IllegalStateException if the instance has been closed
     */
    Object getGlobal(String globalName) throws WasmRuntimeException;

    /**
     * Sets the value of a mutable global variable.
     *
     * @param globalName the name of the global variable, must not be null
     * @param value the new value for the global variable
     * @throws WasmRuntimeException if the global is not found, immutable, or the value is invalid
     * @throws IllegalArgumentException if globalName is null
     * @throws IllegalStateException if the instance has been closed
     */
    void setGlobal(String globalName, Object value) throws WasmRuntimeException;

    /**
     * Returns the names of all functions exported by this instance.
     *
     * @return an array of function names, never null but may be empty
     * @throws IllegalStateException if the instance has been closed
     */
    String[] getFunctionNames();

    /**
     * Returns the names of all global variables exported by this instance.
     *
     * @return an array of global variable names, never null but may be empty
     * @throws IllegalStateException if the instance has been closed
     */
    String[] getGlobalNames();

    /**
     * Checks whether this instance exports memory.
     *
     * @return true if memory is exported, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean hasMemory();

    /**
     * Sets the running mode for this instance.
     *
     * @param mode the running mode to set, must not be null
     * @return true if the mode was set successfully, false otherwise
     * @throws IllegalArgumentException if mode is null
     * @throws IllegalStateException if the instance has been closed
     */
    boolean setRunningMode(RunningMode mode);

    /**
     * Gets the current running mode for this instance.
     *
     * @return the current running mode, or null if the mode is unknown
     * @throws IllegalStateException if the instance has been closed
     */
    RunningMode getRunningMode();

    /**
     * Enables or disables bounds checks for this instance.
     *
     * @param enable true to enable bounds checks, false to disable
     * @return true if the setting was applied successfully, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean setBoundsChecks(boolean enable);

    /**
     * Checks if bounds checks are enabled for this instance.
     *
     * @return true if bounds checks are enabled, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean isBoundsChecksEnabled();

    /**
     * Retrieves an exported table from this instance.
     *
     * <p>Tables are arrays of reference values used for indirect function calls via the
     * {@code call_indirect} instruction.
     *
     * @param tableName the name of the exported table, must not be null
     * @return the WebAssembly table, never null
     * @throws WasmRuntimeException if the table is not found or cannot be retrieved
     * @throws IllegalArgumentException if tableName is null or empty
     * @throws IllegalStateException if the instance has been closed
     */
    WebAssemblyTable getTable(String tableName) throws WasmRuntimeException;

    /**
     * Returns the names of all tables exported by this instance.
     *
     * @return an array of table names, never null but may be empty
     * @throws IllegalStateException if the instance has been closed
     */
    String[] getTableNames();

    /**
     * Allocates memory from the module instance's internal heap.
     *
     * <p>The returned offset is a WASM application address that can be passed to WASM functions.
     * The allocated memory must be freed with {@link #moduleFree(long)} when no longer needed.
     *
     * @param size the number of bytes to allocate, must be greater than 0
     * @return the application offset of the allocated memory
     * @throws WasmRuntimeException if allocation fails (e.g., out of memory)
     * @throws IllegalArgumentException if size is not positive
     * @throws IllegalStateException if the instance has been closed
     */
    long moduleMalloc(long size) throws WasmRuntimeException;

    /**
     * Frees memory previously allocated by {@link #moduleMalloc(long)}.
     *
     * @param offset the application offset returned by {@link #moduleMalloc(long)}
     * @throws IllegalStateException if the instance has been closed
     */
    void moduleFree(long offset);

    /**
     * Copies a byte array into the module instance's memory.
     *
     * <p>This allocates space in the WASM heap, copies the data, and returns the application
     * offset. The caller must free the allocated memory with {@link #moduleFree(long)}.
     *
     * @param data the data to copy into WASM memory, must not be null or empty
     * @return the application offset of the duplicated data
     * @throws WasmRuntimeException if the data cannot be copied (e.g., out of memory)
     * @throws IllegalArgumentException if data is null or empty
     * @throws IllegalStateException if the instance has been closed
     */
    long moduleDupData(byte[] data) throws WasmRuntimeException;

    /**
     * Validates that an application address range is within bounds.
     *
     * @param appOffset the application offset to validate
     * @param size the size of the address range
     * @return true if the address range is valid, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean validateAppAddr(long appOffset, long size);

    /**
     * Validates that an application string address is valid (points to a null-terminated string).
     *
     * @param appStrOffset the application offset of the string
     * @return true if the address is a valid string address, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean validateAppStrAddr(long appStrOffset);

    /**
     * Retrieves a memory instance by its index.
     *
     * <p>Most WASM modules have a single memory at index 0, but the multi-memory proposal
     * allows additional memories.
     *
     * @param index the zero-based index of the memory
     * @return the WebAssembly memory at the specified index, never null
     * @throws WasmRuntimeException if no memory exists at the given index
     * @throws IllegalArgumentException if index is negative
     * @throws IllegalStateException if the instance has been closed
     */
    WebAssemblyMemory getMemoryByIndex(int index) throws WasmRuntimeException;

    /**
     * Gets the current exception on this instance, if any.
     *
     * @return the exception message, or null if no exception is set
     * @throws IllegalStateException if the instance has been closed
     */
    String getException();

    /**
     * Sets a custom exception on this instance.
     *
     * <p>This allows the host to raise an exception that will be visible to the WASM runtime.
     *
     * @param exception the exception message to set, must not be null
     * @throws IllegalArgumentException if exception is null
     * @throws IllegalStateException if the instance has been closed
     */
    void setException(String exception);

    /**
     * Clears the current exception on this instance.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void clearException();

    /**
     * Terminates execution of this instance.
     *
     * <p>This can be called from another thread to interrupt long-running execution.
     * After termination, any ongoing function call will return with an error.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void terminate();

    /**
     * Sets the instruction count limit for this instance's execution environment.
     *
     * <p>When the limit is reached, execution will trap. This is useful for preventing
     * infinite loops or limiting resource consumption by untrusted WASM modules.
     *
     * @param limit the instruction count limit, or -1 to remove the limit
     * @throws IllegalStateException if the instance has been closed
     */
    void setInstructionCountLimit(long limit);

    /**
     * Checks if this instance is running in WASI mode.
     *
     * @return true if the instance is in WASI mode, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean isWasiMode();

    /**
     * Gets the WASI exit code from this instance.
     *
     * <p>After a WASI command completes (via proc_exit), this returns its exit code.
     *
     * @return the WASI exit code
     * @throws IllegalStateException if the instance has been closed
     */
    int getWasiExitCode();

    /**
     * Checks if this instance has a WASI _start function.
     *
     * @return true if a _start function exists, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean hasWasiStartFunction();

    /**
     * Executes the WASI _start entry point.
     *
     * @param argv the command-line arguments to pass, may be empty
     * @return true if execution completed successfully, false on failure
     * @throws IllegalStateException if the instance has been closed
     */
    boolean executeMain(String[] argv);

    /**
     * Executes a named function with string arguments (WASI-style).
     *
     * @param name the name of the function to execute
     * @param argv the string arguments to pass
     * @return true if execution completed successfully, false on failure
     * @throws IllegalArgumentException if name is null
     * @throws IllegalStateException if the instance has been closed
     */
    boolean executeFunc(String name, String[] argv);

    /**
     * Sets custom data on this instance.
     *
     * <p>Custom data is an opaque long value that the host application can associate
     * with this instance. WAMR stores this internally and it persists across function calls.
     *
     * @param customData the custom data value
     * @throws IllegalStateException if the instance has been closed
     */
    void setCustomData(long customData);

    /**
     * Gets the custom data previously set on this instance.
     *
     * @return the custom data value, or 0 if none was set
     * @throws IllegalStateException if the instance has been closed
     */
    long getCustomData();

    /**
     * Gets the call stack as a string.
     *
     * <p>Returns the current call stack frames as a human-readable string.
     * This is useful for debugging WASM execution. The call stack is only
     * meaningful during or immediately after a function call.
     *
     * @return the call stack string, or null if unavailable
     * @throws IllegalStateException if the instance has been closed
     */
    String getCallStack();

    /**
     * Dumps the call stack to stdout.
     *
     * <p>Prints the current call stack frames directly to standard output.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void dumpCallStack();

    /**
     * Dumps performance profiling data to stdout.
     *
     * <p>Prints detailed performance profiling information for all functions
     * executed in this instance. Requires WAMR_BUILD_PERF_PROFILING to be enabled.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void dumpPerfProfiling();

    /**
     * Gets the total WASM execution time in milliseconds.
     *
     * <p>Returns the cumulative execution time of all WASM functions in this instance.
     * Requires WAMR_BUILD_PERF_PROFILING to be enabled.
     *
     * @return the total execution time in milliseconds, or 0.0 if profiling is not available
     * @throws IllegalStateException if the instance has been closed
     */
    double sumWasmExecTime();

    /**
     * Gets the execution time for a specific function in milliseconds.
     *
     * <p>Returns the cumulative execution time of the named function.
     * Requires WAMR_BUILD_PERF_PROFILING to be enabled.
     *
     * @param funcName the name of the function, must not be null
     * @return the execution time in milliseconds, or 0.0 if the function is not found
     * @throws IllegalArgumentException if funcName is null
     * @throws IllegalStateException if the instance has been closed
     */
    double getWasmFuncExecTime(String funcName);

    /**
     * Dumps memory consumption information to stdout.
     *
     * <p>Prints detailed memory consumption statistics for this instance.
     * Requires WAMR_BUILD_MEMORY_PROFILING to be enabled.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void dumpMemConsumption();

    /**
     * Checks if the instance has been closed.
     *
     * @return true if the instance has been closed, false otherwise
     */
    boolean isClosed();

    /**
     * Closes this instance and releases any associated resources.
     *
     * <p>After calling this method, any attempt to use this instance will result in an {@link
     * IllegalStateException}. This includes any functions or memory objects obtained from this
     * instance.
     *
     * <p>This method is idempotent - calling it multiple times has no effect.
     */
    @Override
    void close();
}
