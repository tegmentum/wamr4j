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

package ai.tegmentum.wamr4j.panama;

import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.internal.NativePlatform;
import ai.tegmentum.wamr4j.panama.impl.PanamaWebAssemblyRuntime;
import ai.tegmentum.wamr4j.spi.RuntimeProvider;

/**
 * Panama Foreign Function API-based runtime provider for WebAssembly execution.
 * 
 * <p>This provider creates WebAssembly runtimes that use Panama FFI to communicate
 * with the native WAMR library. It requires Java 23+ and provides the highest
 * performance and safety for modern Java applications.
 * 
 * <p>The Panama provider has higher priority than JNI on Java 23+ and
 * offers better type safety and performance characteristics.
 * 
 * @since 1.0.0
 */
public final class PanamaRuntimeProvider implements RuntimeProvider {

    private static final String PROVIDER_NAME = "Panama";
    private static final int HIGH_PRIORITY = 200; // Higher priority than JNI
    private static final int MINIMUM_JAVA_VERSION = 23;
    
    // Cached availability check result
    private volatile Boolean availabilityCache;

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public int getPriority() {
        return HIGH_PRIORITY; // Always high priority when available
    }

    @Override
    public boolean isAvailable() {
        Boolean cached = availabilityCache;
        if (cached == null) {
            synchronized (this) {
                cached = availabilityCache;
                if (cached == null) {
                    cached = checkAvailability();
                    availabilityCache = cached;
                }
            }
        }
        return cached;
    }

    @Override
    public WebAssemblyRuntime createRuntime() throws WasmRuntimeException {
        if (!isAvailable()) {
            throw new WasmRuntimeException(
                "Panama runtime is not available. Requires Java 23+ with Panama FFI support.");
        }
        
        try {
            return new PanamaWebAssemblyRuntime();
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to create Panama WebAssembly runtime", e);
        }
    }

    @Override
    public int getMinimumJavaVersion() {
        return MINIMUM_JAVA_VERSION;
    }

    private boolean checkAvailability() {
        // Check Java version first
        final int javaVersion = NativePlatform.getJavaMajorVersion();
        if (javaVersion < MINIMUM_JAVA_VERSION) {
            return false;
        }

        try {
            // Check if Panama FFI classes are available
            Class.forName("java.lang.foreign.MemorySegment");
            Class.forName("java.lang.foreign.FunctionDescriptor");

            // Check if native library resource exists without loading it
            final String resourcePath = "/META-INF/native/"
                + NativePlatform.getPlatformName() + "/"
                + NativePlatform.getLibraryFileName("wamr4j_native");
            return getClass().getResource(resourcePath) != null;
        } catch (final Exception e) {
            return false;
        }
    }

}