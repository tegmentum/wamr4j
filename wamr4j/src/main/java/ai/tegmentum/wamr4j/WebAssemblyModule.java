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

import java.util.Map;

import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Represents a compiled WebAssembly module.
 *
 * <p>A WebAssembly module is the result of compiling WebAssembly bytecode. It contains all the
 * information needed to create instances of the module, including function signatures, memory
 * requirements, and import/export definitions.
 *
 * <p>Modules are immutable and thread-safe. Multiple instances can be created from the same module
 * concurrently. However, proper resource management is essential - always close modules when no
 * longer needed.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try (WebAssemblyModule module = runtime.compile(wasmBytes)) {
 *     String[] exports = module.getExportNames();
 *     try (WebAssemblyInstance instance = module.instantiate()) {
 *         // Use the instance...
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WebAssemblyModule extends AutoCloseable {

    /**
     * Creates a new instance of this WebAssembly module.
     *
     * <p>Instantiation creates a new execution context for the module, including memory allocation
     * and initialization of global variables. Each instance is independent and maintains its own
     * state.
     *
     * @return a new WebAssembly instance
     * @throws WasmRuntimeException if instantiation fails
     * @throws IllegalStateException if the module has been closed
     */
    WebAssemblyInstance instantiate() throws WasmRuntimeException;

    /**
     * Creates a new instance of this WebAssembly module with import bindings.
     *
     * <p>This method allows providing import bindings for functions, globals, memory, and tables
     * that the module requires. The imports map should contain module names as keys and maps of
     * import names to their values as values.
     *
     * @param imports a map of module names to import bindings, may be null or empty
     * @return a new WebAssembly instance with the provided imports
     * @throws WasmRuntimeException if instantiation fails or imports are invalid
     * @throws IllegalStateException if the module has been closed
     */
    WebAssemblyInstance instantiate(Map<String, Map<String, Object>> imports)
            throws WasmRuntimeException;

    /**
     * Returns the names of all exports defined by this module.
     *
     * <p>Exports are functions, globals, memory, or tables that this module makes available to
     * other modules or the host environment.
     *
     * @return an array of export names, never null but may be empty
     * @throws IllegalStateException if the module has been closed
     */
    String[] getExportNames();

    /**
     * Returns the names of all imports required by this module.
     *
     * <p>Imports are functions, globals, memory, or tables that this module requires from other
     * modules or the host environment.
     *
     * @return an array of import names in "module.name" format, never null but may be empty
     * @throws IllegalStateException if the module has been closed
     */
    String[] getImportNames();

    /**
     * Returns the type signature of the specified exported function.
     *
     * @param functionName the name of the exported function
     * @return the function signature, or null if the function is not exported
     * @throws IllegalArgumentException if functionName is null
     * @throws IllegalStateException if the module has been closed
     */
    FunctionSignature getExportFunctionSignature(String functionName);

    /**
     * Sets a name for this module.
     *
     * <p>Module names are used for identification in multi-module scenarios
     * and debugging.
     *
     * @param name the module name, must not be null
     * @return true if the name was set successfully, false otherwise
     * @throws IllegalArgumentException if name is null
     * @throws IllegalStateException if the module has been closed
     */
    boolean setName(String name);

    /**
     * Returns the name of this module, if one has been set.
     *
     * @return the module name, or an empty string if no name has been set
     * @throws IllegalStateException if the module has been closed
     */
    String getName();

    /**
     * Returns the package type of this module.
     *
     * @return the package type (WASM, AOT, or UNKNOWN)
     * @throws IllegalStateException if the module has been closed
     */
    PackageType getPackageType();

    /**
     * Returns the package version of this module.
     *
     * @return the package version number
     * @throws IllegalStateException if the module has been closed
     */
    int getPackageVersion();

    /**
     * Returns the hash string for this module.
     *
     * @return the module hash string, or an empty string if unavailable
     * @throws IllegalStateException if the module has been closed
     */
    String getHash();

    /**
     * Checks if the underlying binary data can be freed after loading.
     *
     * <p>Some module formats allow the original bytecode buffer to be freed
     * after loading, while others require it to remain valid.
     *
     * @return true if the underlying binary can be freed, false otherwise
     * @throws IllegalStateException if the module has been closed
     */
    boolean isUnderlyingBinaryFreeable();

    /**
     * Configures WASI parameters on this module before instantiation.
     *
     * <p>This must be called before {@link #instantiate()} for WASI modules.
     * The configuration includes command-line arguments, environment variables,
     * preopened directories, and network access controls.
     *
     * @param config the WASI configuration, must not be null
     * @throws IllegalArgumentException if config is null
     * @throws IllegalStateException if the module has been closed
     */
    void configureWasi(WasiConfiguration config);

    /**
     * Creates a new instance of this WebAssembly module with extended configuration.
     *
     * <p>This method allows specifying custom stack size, heap size, and maximum
     * memory pages for the instance. Use this when the default values are not
     * suitable for your workload.
     *
     * @param defaultStackSize the default execution stack size in bytes
     * @param hostManagedHeapSize the host-managed heap size in bytes
     * @param maxMemoryPages the maximum number of memory pages (64KB each)
     * @return a new WebAssembly instance
     * @throws WasmRuntimeException if instantiation fails
     * @throws IllegalStateException if the module has been closed
     * @throws IllegalArgumentException if any parameter is negative
     */
    WebAssemblyInstance instantiateEx(int defaultStackSize, int hostManagedHeapSize,
            int maxMemoryPages) throws WasmRuntimeException;

    /**
     * Returns the raw bytes of a custom section by name.
     *
     * <p>Custom sections are named sections in the WebAssembly binary that contain
     * arbitrary data. They are used for debugging information, metadata, and
     * other purposes.
     *
     * @param name the name of the custom section
     * @return the section data as a byte array, or null if the section does not exist
     * @throws IllegalArgumentException if name is null
     * @throws IllegalStateException if the module has been closed
     */
    byte[] getCustomSection(String name);

    /**
     * Gets the type info for an exported global by name.
     *
     * <p>Returns an int array where index 0 is the value kind (WASM type) and
     * index 1 is 1 if mutable, 0 if immutable. Returns null if the global is
     * not found.
     *
     * @param name the name of the exported global
     * @return an int array [valkind, is_mutable] or null if not found
     * @throws IllegalStateException if the module has been closed
     */
    int[] getExportGlobalTypeInfo(String name);

    /**
     * Gets the type info for an exported memory by name.
     *
     * <p>Returns an int array where index 0 is 1 if shared (0 otherwise),
     * index 1 is the initial page count, and index 2 is the maximum page count.
     * Returns null if the memory is not found.
     *
     * @param name the name of the exported memory
     * @return an int array [is_shared, init_page_count, max_page_count] or null if not found
     * @throws IllegalStateException if the module has been closed
     */
    int[] getExportMemoryTypeInfo(String name);

    /**
     * Creates a new instance of this module using the opaque InstantiationArgs2 API.
     *
     * <p>This is the ABI-stable alternative to {@link #instantiateEx(int, int, int)}.
     * It uses the opaque InstantiationArgs2 struct which allows new fields to be added
     * without breaking ABI compatibility.
     *
     * @param defaultStackSize the default stack size in bytes
     * @param hostManagedHeapSize the host-managed heap size in bytes
     * @param maxMemoryPages the maximum number of memory pages
     * @return a new WebAssembly instance
     * @throws WasmRuntimeException if instantiation fails
     * @throws IllegalStateException if the module has been closed
     */
    WebAssemblyInstance instantiateEx2(int defaultStackSize, int hostManagedHeapSize,
            int maxMemoryPages) throws WasmRuntimeException;

    /**
     * Checks if the module has been closed.
     *
     * @return true if the module has been closed, false otherwise
     */
    boolean isClosed();

    /**
     * Closes this module and releases any associated resources.
     *
     * <p>After calling this method, any attempt to use this module will result in an {@link
     * IllegalStateException}. Any instances created from this module should be closed before
     * closing the module.
     *
     * <p>This method is idempotent - calling it multiple times has no effect.
     */
    @Override
    void close();
}
