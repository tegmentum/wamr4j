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

import ai.tegmentum.wamr4j.exception.RuntimeException;

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
     * @throws RuntimeException if instantiation fails
     * @throws IllegalStateException if the module has been closed
     */
    WebAssemblyInstance instantiate() throws RuntimeException;

    /**
     * Creates a new instance of this WebAssembly module with import bindings.
     *
     * <p>This method allows providing import bindings for functions, globals, memory, and tables
     * that the module requires. The imports map should contain module names as keys and maps of
     * import names to their values as values.
     *
     * @param imports a map of module names to import bindings, may be null or empty
     * @return a new WebAssembly instance with the provided imports
     * @throws RuntimeException if instantiation fails or imports are invalid
     * @throws IllegalStateException if the module has been closed
     */
    WebAssemblyInstance instantiate(Map<String, Map<String, Object>> imports)
            throws RuntimeException;

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
     * Validates that all required imports can be satisfied by the provided bindings.
     *
     * @param imports a map of module names to import bindings to validate
     * @return true if all imports can be satisfied, false otherwise
     * @throws IllegalStateException if the module has been closed
     */
    boolean validateImports(Map<String, Map<String, Object>> imports);

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
