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
package ai.tegmentum.wamr4j.spi;

import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Service provider interface for WebAssembly runtime implementations.
 *
 * <p>This interface is used by the {@code RuntimeFactory} to discover and instantiate different
 * WebAssembly runtime implementations (JNI, Panama, etc.). Implementation modules should provide
 * this service via the Java ServiceLoader mechanism.
 *
 * <p>Implementation classes should be registered in {@code
 * META-INF/services/ai.tegmentum.wamr4j.spi.RuntimeProvider} files within their respective JAR
 * files.
 *
 * <p>Providers are expected to be thread-safe and lightweight. The actual runtime instances may be
 * created lazily or cached as appropriate for the implementation.
 *
 * @since 1.0.0
 */
public interface RuntimeProvider {

    /**
     * Returns the name of this runtime implementation.
     *
     * <p>This name is used for identification and logging purposes. Common names include "JNI",
     * "Panama", "Native", etc.
     *
     * @return the runtime implementation name, never null
     */
    String getName();

    /**
     * Returns the priority of this runtime provider.
     *
     * <p>When multiple providers are available, the one with the highest priority will be selected
     * by default. This allows implementations to specify their preference order (e.g., Panama over
     * JNI on Java 23+).
     *
     * <p>Common priority values:
     *
     * <ul>
     *   <li>100 - Default priority
     *   <li>200 - Higher priority (preferred)
     *   <li>50 - Lower priority (fallback)
     * </ul>
     *
     * @return the provider priority, higher values indicate higher priority
     */
    int getPriority();

    /**
     * Checks if this runtime provider is available in the current environment.
     *
     * <p>This method should perform lightweight checks to determine if the runtime can be
     * successfully created. For example, it might check for the presence of required native
     * libraries, Java version compatibility, or system capabilities.
     *
     * <p>This method should not perform expensive operations like loading native libraries - such
     * operations should be deferred to {@link #createRuntime()}.
     *
     * @return true if the runtime is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Creates a new WebAssembly runtime instance.
     *
     * <p>This method creates and initializes a new runtime instance. It may perform expensive
     * operations like loading native libraries or initializing subsystems.
     *
     * <p>Implementations should ensure proper resource cleanup if creation fails, and should not
     * leave the system in an inconsistent state.
     *
     * @return a new WebAssembly runtime instance, never null
     * @throws WasmRuntimeException if the runtime cannot be created
     * @throws UnsupportedOperationException if the runtime is not supported in the current
     *     environment
     */
    WebAssemblyRuntime createRuntime() throws WasmRuntimeException;

    /**
     * Returns the minimum Java version required by this provider.
     *
     * <p>This information is used by the RuntimeFactory to filter out incompatible providers based
     * on the current Java version.
     *
     * @return the minimum Java version (e.g., 8, 17, 23), or 0 if no minimum
     */
    default int getMinimumJavaVersion() {
        return 0;
    }
}
