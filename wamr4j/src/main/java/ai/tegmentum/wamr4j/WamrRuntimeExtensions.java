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

/**
 * WAMR-specific advanced extensions for {@link WebAssemblyRuntime}.
 *
 * <p>This interface provides access to WAMR-specific functionality beyond the core
 * WebAssembly runtime API. Implementations of this interface can be obtained by casting
 * the result of {@link RuntimeFactory#createRuntime()}:
 *
 * <pre>{@code
 * WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
 * if (runtime instanceof WamrRuntimeExtensions ext) {
 *     ext.setLogLevel(3);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WamrRuntimeExtensions extends WebAssemblyRuntime {

    /**
     * Returns the major version number of the underlying WAMR runtime.
     *
     * @return the major version number
     */
    int getMajorVersion();

    /**
     * Returns the minor version number of the underlying WAMR runtime.
     *
     * @return the minor version number
     */
    int getMinorVersion();

    /**
     * Returns the patch version number of the underlying WAMR runtime.
     *
     * @return the patch version number
     */
    int getPatchVersion();

    /**
     * Checks if the specified running mode is supported by this WAMR build.
     *
     * @param mode the running mode to check, must not be null
     * @return true if the mode is supported, false otherwise
     * @throws IllegalArgumentException if mode is null
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean isRunningModeSupported(RunningMode mode);

    /**
     * Sets the default running mode for newly created module instances.
     *
     * @param mode the running mode to set as default, must not be null
     * @return true if the mode was set successfully, false otherwise
     * @throws IllegalArgumentException if mode is null
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean setDefaultRunningMode(RunningMode mode);

    /**
     * Sets the WAMR log verbosity level.
     *
     * <p>Level values range from 0 (no logging) to 5 (most verbose).
     *
     * @param level the log verbosity level (0-5)
     * @throws IllegalStateException if the runtime has been closed
     */
    void setLogLevel(int level);

    /**
     * Determines the package type of a WebAssembly binary buffer.
     *
     * @param wasmBytes the binary data to inspect, must not be null
     * @return the detected package type (WASM, AOT, or UNKNOWN)
     * @throws IllegalArgumentException if wasmBytes is null
     * @throws IllegalStateException if the runtime has been closed
     */
    PackageType getFilePackageType(byte[] wasmBytes);

    /**
     * Returns the current supported package version for a given package type.
     *
     * @param packageType the package type to query, must not be null
     * @return the current supported version number
     * @throws IllegalArgumentException if packageType is null
     * @throws IllegalStateException if the runtime has been closed
     */
    int getCurrentPackageVersion(PackageType packageType);

    /**
     * Initializes the thread environment for the current native thread.
     *
     * <p>This must be called from any non-main thread that wants to interact with WAMR.
     * The main thread's environment is initialized automatically when the runtime is created.
     *
     * @return true if initialization succeeded, false otherwise
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean initThreadEnv();

    /**
     * Destroys the thread environment for the current native thread.
     *
     * <p>Call this when a thread that called {@link #initThreadEnv()} is about to exit.
     *
     * @throws IllegalStateException if the runtime has been closed
     */
    void destroyThreadEnv();

    /**
     * Checks if the thread environment has been initialized for the current thread.
     *
     * @return true if the thread environment is initialized, false otherwise
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean isThreadEnvInited();

    /**
     * Sets the maximum number of threads for WASM thread management.
     *
     * <p>This controls the maximum number of threads that can be created by WASM modules
     * using the threading proposal (e.g., via lib-pthread).
     *
     * @param num the maximum number of threads, must be non-negative
     * @throws IllegalArgumentException if num is negative
     * @throws IllegalStateException if the runtime has been closed
     */
    void setMaxThreadNum(int num);

    /**
     * Checks if an import function is linked (has a registered host implementation).
     *
     * @param moduleName the module name of the import
     * @param funcName the function name of the import
     * @return true if the import function is linked, false otherwise
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean isImportFuncLinked(String moduleName, String funcName);

    /**
     * Checks if an import global is linked (has a registered host implementation).
     *
     * @param moduleName the module name of the import
     * @param globalName the global name of the import
     * @return true if the import global is linked, false otherwise
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean isImportGlobalLinked(String moduleName, String globalName);

    /**
     * Gets memory allocation info from the runtime allocator.
     *
     * <p>Returns an int array where index 0 is total size, index 1 is total free size,
     * and index 2 is highmark size. Returns null if info is unavailable.
     *
     * @return int array [total_size, total_free_size, highmark_size] or null
     * @throws IllegalStateException if the runtime has been closed
     */
    int[] getMemAllocInfo();

    /**
     * Creates a context key for per-instance context storage.
     *
     * <p>Context keys allow associating arbitrary data with module instances.
     * The returned key handle should be destroyed with {@link #destroyContextKey(long)}
     * when no longer needed.
     *
     * @return a native handle to the context key, or 0 on failure
     * @throws IllegalStateException if the runtime has been closed
     */
    long createContextKey();

    /**
     * Destroys a context key previously created with {@link #createContextKey()}.
     *
     * @param key the context key handle to destroy
     * @throws IllegalStateException if the runtime has been closed
     */
    void destroyContextKey(long key);

    /**
     * Retrieves the last error message from the thread-local error store.
     *
     * <p>This is useful for diagnostics when a native call fails without throwing.
     * The error is thread-local and cleared at the start of most native operations.
     *
     * @return the last error message, or null if no error has occurred
     * @throws IllegalStateException if the runtime has been closed
     */
    String getLastError();

    /**
     * Checks if a binary buffer contains an XIP (eXecute In Place) AOT file.
     *
     * <p>XIP files are a WAMR-specific AOT format that can be executed directly
     * from flash/ROM without loading into RAM.
     *
     * @param wasmBytes the binary data to inspect, must not be null
     * @return true if the buffer is an XIP file, false otherwise
     * @throws IllegalArgumentException if wasmBytes is null
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean isXipFile(byte[] wasmBytes);

    /**
     * Gets the package version from raw WebAssembly binary bytes.
     *
     * <p>This inspects the binary header without compiling the module.
     *
     * @param wasmBytes the binary data to inspect, must not be null
     * @return the package version number
     * @throws IllegalArgumentException if wasmBytes is null
     * @throws IllegalStateException if the runtime has been closed
     */
    int getFilePackageVersion(byte[] wasmBytes);

    /**
     * Retrieves the host object pointer from an externref index.
     *
     * <p>This is the reverse of {@link WamrInstanceExtensions#externrefObj2Ref(long)}.
     * It maps a WASM externref index back to the original host object pointer.
     *
     * @param externrefIdx the externref index
     * @return the host object pointer, or 0 if not found
     * @throws IllegalStateException if the runtime has been closed
     */
    long externrefRef2Obj(int externrefIdx);

    /**
     * Retains an externref, preventing it from being garbage collected by WAMR.
     *
     * @param externrefIdx the externref index to retain
     * @return true if the retain succeeded, false otherwise
     * @throws IllegalStateException if the runtime has been closed
     */
    boolean externrefRetain(int externrefIdx);

    /**
     * Creates a shared heap that can be attached to multiple instances.
     *
     * <p>Shared heaps enable cross-instance memory sharing. The returned handle
     * must be used with {@link WamrInstanceExtensions#attachSharedHeap(long)} and
     * should eventually be freed by the caller.
     *
     * @param size the initial size of the shared heap in bytes
     * @return a native handle to the shared heap, or 0 on failure
     * @throws IllegalArgumentException if size is not positive
     * @throws IllegalStateException if the runtime has been closed
     */
    long createSharedHeap(int size);

    /**
     * Chains two shared heaps together to form a linked heap structure.
     *
     * @param head the handle of the head heap
     * @param body the handle of the body heap to chain
     * @return a handle to the chained heap, or 0 on failure
     * @throws IllegalStateException if the runtime has been closed
     */
    long chainSharedHeaps(long head, long body);

    /**
     * Finds a previously registered module by name.
     *
     * <p>Modules are registered using {@link WebAssemblyModule#register(String)}.
     * This method looks up a module that was registered under the given name.
     *
     * @param name the registration name to look up
     * @return the native module handle, or 0 if not found
     * @throws IllegalArgumentException if name is null
     * @throws IllegalStateException if the runtime has been closed
     */
    long findRegisteredModule(String name);
}
