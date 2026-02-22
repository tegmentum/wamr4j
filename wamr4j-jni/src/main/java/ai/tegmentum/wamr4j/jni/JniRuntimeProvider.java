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

package ai.tegmentum.wamr4j.jni;

import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.internal.NativePlatform;
import ai.tegmentum.wamr4j.jni.impl.JniWebAssemblyRuntime;
import ai.tegmentum.wamr4j.spi.RuntimeProvider;

/**
 * JNI-based runtime provider for WebAssembly execution.
 * 
 * <p>This provider creates WebAssembly runtimes that use JNI to communicate
 * with the native WAMR library. It is compatible with Java 8+ and serves
 * as the primary runtime implementation for older Java versions.
 * 
 * <p>The JNI provider has a lower priority than Panama on Java 23+ but
 * serves as a reliable fallback for all Java versions.
 * 
 * @since 1.0.0
 */
public final class JniRuntimeProvider implements RuntimeProvider {

    private static final String PROVIDER_NAME = "JNI";
    private static final int DEFAULT_PRIORITY = 100;
    private static final int JAVA_23_PRIORITY = 50; // Lower priority on Java 23+
    
    // Cached availability check result
    private volatile Boolean availabilityCache;

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public int getPriority() {
        // Use lower priority on Java 23+ to prefer Panama when available
        final int javaVersion = getJavaMajorVersion();
        return javaVersion >= 23 ? JAVA_23_PRIORITY : DEFAULT_PRIORITY;
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
                "JNI runtime is not available. Ensure native library can be loaded.");
        }
        
        try {
            return new JniWebAssemblyRuntime();
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to create JNI WebAssembly runtime", e);
        }
    }

    @Override
    public int getMinimumJavaVersion() {
        return 8; // JNI is available since Java 1.1, but we support Java 8+
    }

    private boolean checkAvailability() {
        // Check if native library resource exists without loading it
        final String resourcePath = "/META-INF/native/"
            + NativePlatform.getPlatformName() + "/"
            + NativePlatform.getLibraryFileName("wamr4j_native");
        return getClass().getResource(resourcePath) != null;
    }

    private static int getJavaMajorVersion() {
        try {
            final String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                // Java 8 and below use 1.x format
                return Integer.parseInt(version.substring(2, 3));
            } else {
                // Java 9+ use x.y.z, x-ea, or x+build format
                int endIndex = version.length();
                for (int i = 0; i < version.length(); i++) {
                    final char ch = version.charAt(i);
                    if (ch == '.' || ch == '-' || ch == '+') {
                        endIndex = i;
                        break;
                    }
                }
                return Integer.parseInt(version.substring(0, endIndex));
            }
        } catch (final Exception e) {
            return 8; // Default to Java 8
        }
    }
}