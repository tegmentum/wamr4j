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
