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

import java.nio.file.Path;

import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;

/**
 * Main entry point for WebAssembly operations using WAMR4J.
 *
 * <p>This interface provides the primary API for compiling WebAssembly modules and managing the
 * WebAssembly runtime. It abstracts away the underlying implementation details (JNI or Panama) and
 * provides a consistent interface for WebAssembly operations.
 *
 * <p>Runtime instances are thread-safe and can be used concurrently. However, proper resource
 * management is essential - always close runtime instances when no longer needed to prevent
 * resource leaks.
 *
 * <p>For WAMR-specific advanced features, cast to {@link WamrRuntimeExtensions}:
 *
 * <pre>{@code
 * try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
 *     WebAssemblyModule module = runtime.compile(wasmBytes);
 *
 *     // Access advanced features
 *     if (runtime instanceof WamrRuntimeExtensions ext) {
 *         ext.setLogLevel(3);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WebAssemblyRuntime extends AutoCloseable {

    /**
     * Compiles WebAssembly bytecode into a module.
     *
     * <p>This method validates and compiles the provided WebAssembly bytecode into a module that
     * can be instantiated. The compilation process includes validation of the bytecode structure
     * and type checking.
     *
     * @param wasmBytes the WebAssembly bytecode to compile, must not be null
     * @return a compiled WebAssembly module
     * @throws CompilationException if the bytecode cannot be compiled
     * @throws ValidationException if the bytecode is invalid or malformed
     * @throws IllegalArgumentException if wasmBytes is null or empty
     * @throws IllegalStateException if the runtime has been closed
     */
    WebAssemblyModule compile(byte[] wasmBytes) throws CompilationException, ValidationException;

    /**
     * Compiles a WebAssembly file into a module.
     *
     * <p>This method reads the WebAssembly file from the specified path and compiles it into a
     * module. This is a convenience method that handles file I/O operations internally.
     *
     * @param wasmFile the path to the WebAssembly file to compile, must not be null
     * @return a compiled WebAssembly module
     * @throws CompilationException if the file cannot be read or compiled
     * @throws ValidationException if the file content is invalid or malformed
     * @throws IllegalArgumentException if wasmFile is null
     * @throws IllegalStateException if the runtime has been closed
     * @throws java.io.IOException if the file cannot be read
     */
    WebAssemblyModule compile(Path wasmFile)
            throws CompilationException, ValidationException, java.io.IOException;

    /**
     * Returns information about this WebAssembly runtime implementation.
     *
     * @return a string describing the runtime implementation (e.g., "JNI", "Panama")
     */
    String getImplementation();

    /**
     * Returns the version of the underlying WAMR runtime.
     *
     * @return the WAMR version string (e.g., "2.4.4")
     */
    String getVersion();

    /**
     * Checks if the runtime has been closed.
     *
     * @return true if the runtime has been closed, false otherwise
     */
    boolean isClosed();

    /**
     * Closes this runtime and releases any associated resources.
     *
     * <p>After calling this method, any attempt to use this runtime will result in an {@link
     * IllegalStateException}. Any modules created by this runtime should be closed before closing
     * the runtime.
     *
     * <p>This method is idempotent - calling it multiple times has no effect.
     */
    @Override
    void close();
}
