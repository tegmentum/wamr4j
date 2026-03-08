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
 * WAMR-specific advanced extensions for {@link WebAssemblyInstance}.
 *
 * <p>This interface provides access to WAMR-specific functionality beyond the core
 * WebAssembly instance API. Implementations of this interface can be obtained by casting
 * the result of module instantiation:
 *
 * <pre>{@code
 * WebAssemblyInstance instance = module.instantiate();
 * if (instance instanceof WamrInstanceExtensions ext) {
 *     ext.setRunningMode(RunningMode.INTERPRETER);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WamrInstanceExtensions extends WebAssemblyInstance {

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
     * Looks up a memory instance by export name.
     *
     * @param name the export name of the memory
     * @return true if the memory was found, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean lookupMemory(String name);

    /**
     * Begins a blocking operation on this instance's execution environment.
     *
     * <p>This allows {@link #terminate()} to interrupt long-running host calls.
     * Must be paired with {@link #endBlockingOp()}.
     *
     * @return true if the blocking operation was started successfully
     * @throws IllegalStateException if the instance has been closed
     */
    boolean beginBlockingOp();

    /**
     * Ends a blocking operation on this instance's execution environment.
     *
     * <p>Must be called after {@link #beginBlockingOp()}.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void endBlockingOp();

    /**
     * Detects if a native stack overflow would occur.
     *
     * @return true if stack overflow is detected, false if safe
     * @throws IllegalStateException if the instance has been closed
     */
    boolean detectNativeStackOverflow();

    /**
     * Detects if a native stack overflow would occur given a required stack size.
     *
     * @param requiredSize the required stack size in bytes
     * @return true if stack overflow is detected, false if safe
     * @throws IllegalStateException if the instance has been closed
     */
    boolean detectNativeStackOverflowSize(int requiredSize);

    /**
     * Sets a context value on this instance for the given key.
     *
     * @param key the context key handle (from {@link WebAssemblyRuntime#createContextKey()})
     * @param ctx the context value (as a long/pointer)
     * @throws IllegalStateException if the instance has been closed
     */
    void setContext(long key, long ctx);

    /**
     * Gets the context value from this instance for the given key.
     *
     * @param key the context key handle
     * @return the context value, or 0 if not set
     * @throws IllegalStateException if the instance has been closed
     */
    long getContext(long key);

    /**
     * Validates whether a native (host) memory address is within the instance's
     * linear memory bounds.
     *
     * @param nativeAddr the native address to validate
     * @param size the size of the address range
     * @return true if the address range is valid, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean validateNativeAddr(long nativeAddr, long size);

    /**
     * Converts a WebAssembly app-space offset to a native (host) pointer.
     *
     * @param appOffset the application-space offset
     * @return the native address, or 0 if the offset is invalid
     * @throws IllegalStateException if the instance has been closed
     */
    long addrAppToNative(long appOffset);

    /**
     * Converts a native (host) pointer to a WebAssembly app-space offset.
     *
     * @param nativeAddr the native address
     * @return the application-space offset, or 0 if the address is invalid
     * @throws IllegalStateException if the instance has been closed
     */
    long addrNativeToApp(long nativeAddr);

    /**
     * Sets context on this instance and propagates it to all child instances.
     *
     * <p>Unlike {@link #setContext(long, long)}, this also sets the context on
     * any threads spawned from this instance.
     *
     * @param key the context key handle
     * @param ctx the context value
     * @throws IllegalStateException if the instance has been closed
     */
    void setContextSpread(long key, long ctx);

    /**
     * Spawns a new execution environment for parallel execution within this instance.
     *
     * <p>The returned handle must be destroyed with {@link #destroySpawnedExecEnv(long)}
     * when no longer needed.
     *
     * @return a native handle to the spawned execution environment, or 0 on failure
     * @throws IllegalStateException if the instance has been closed
     */
    long spawnExecEnv();

    /**
     * Destroys a previously spawned execution environment.
     *
     * @param execEnv the native handle of the spawned execution environment
     * @throws IllegalStateException if the instance has been closed
     */
    void destroySpawnedExecEnv(long execEnv);

    /**
     * Copies the current call stack into structured arrays.
     *
     * <p>Returns an array where result[0] contains function indices and result[1]
     * contains code offsets for each frame. Returns null if the call stack is empty
     * or unavailable.
     *
     * @param maxFrames the maximum number of frames to copy
     * @param skip the number of top frames to skip
     * @return int[2][n] where [0][i] is func index and [1][i] is code offset, or null
     * @throws IllegalArgumentException if maxFrames is not positive
     * @throws IllegalStateException if the instance has been closed
     */
    int[][] copyCallstack(int maxFrames, int skip);

    /**
     * Maps a host object pointer to a WebAssembly externref index.
     *
     * @param externObj the host object pointer
     * @return the externref index, or -1 on failure
     * @throws IllegalStateException if the instance has been closed
     */
    int externrefObj2Ref(long externObj);

    /**
     * Removes a host object from the externref mapping table.
     *
     * @param externObj the host object pointer to remove
     * @throws IllegalStateException if the instance has been closed
     */
    void externrefObjDel(long externObj);

    /**
     * Attaches a shared heap to this instance.
     *
     * <p>Once attached, the instance can allocate from the shared heap using
     * {@link #sharedHeapMalloc(long)}.
     *
     * @param heapHandle the native handle of the shared heap
     * @return true if attachment succeeded, false otherwise
     * @throws IllegalStateException if the instance has been closed
     */
    boolean attachSharedHeap(long heapHandle);

    /**
     * Detaches the shared heap from this instance.
     *
     * @throws IllegalStateException if the instance has been closed
     */
    void detachSharedHeap();

    /**
     * Allocates memory from the shared heap attached to this instance.
     *
     * @param size the number of bytes to allocate
     * @return the application offset of the allocated memory, or 0 on failure
     * @throws IllegalStateException if the instance has been closed
     */
    long sharedHeapMalloc(long size);

    /**
     * Frees memory previously allocated from the shared heap.
     *
     * @param ptr the application offset returned by {@link #sharedHeapMalloc(long)}
     * @throws IllegalStateException if the instance has been closed
     */
    void sharedHeapFree(long ptr);

    /**
     * Gets the native address range containing the specified pointer.
     *
     * <p>Returns the start and end addresses of the memory region that contains
     * the given native pointer. This is useful for validating that a native pointer
     * falls within a known memory region.
     *
     * @param nativePtr the native pointer to query
     * @return a two-element array {@code [startAddr, endAddr]}, or null if the
     *         pointer does not fall within any known memory region
     * @throws IllegalStateException if the instance has been closed
     */
    long[] getNativeAddrRange(long nativePtr);
}
