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

package ai.tegmentum.wamr4j.jni.internal;

import ai.tegmentum.wamr4j.internal.NativePlatform;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for loading the native WAMR library for JNI operations.
 *
 * <p>This class handles the complexity of loading platform-specific native
 * libraries from the classpath and making them available to the JNI runtime.
 * It supports automatic platform detection and library extraction.
 *
 * <p>The library loading process:
 * <ol>
 * <li>Detect current platform (OS and architecture)</li>
 * <li>Locate appropriate native library in classpath</li>
 * <li>Extract library to temporary location if needed</li>
 * <li>Load library using System.load()</li>
 * <li>Cache loading state to avoid repeated operations</li>
 * </ol>
 *
 * <p>This class is thread-safe and uses lazy initialization with proper
 * synchronization to ensure the library is loaded exactly once.
 *
 * @since 1.0.0
 */
public final class NativeLibraryLoader {

    private static final Logger LOGGER = Logger.getLogger(NativeLibraryLoader.class.getName());

    private static final String LIBRARY_NAME = "wamr4j_native";

    private static volatile boolean loaded = false;
    private static volatile Throwable loadingError = null;

    private NativeLibraryLoader() {
        throw new UnsupportedOperationException("NativeLibraryLoader cannot be instantiated");
    }

    /**
     * Ensures the native library is loaded and ready for use.
     *
     * <p>This method is idempotent - calling it multiple times has no effect
     * after the first successful load. If the library cannot be loaded, this
     * method will throw an exception on every call.
     *
     * @throws UnsupportedOperationException if the current platform is not supported
     * @throws RuntimeException if the library cannot be loaded
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }

        if (loadingError != null) {
            throw new RuntimeException("Native library loading failed previously", loadingError);
        }

        synchronized (NativeLibraryLoader.class) {
            if (loaded) {
                return;
            }

            if (loadingError != null) {
                throw new RuntimeException("Native library loading failed previously", loadingError);
            }

            try {
                loadNativeLibrary();
                loaded = true;
                LOGGER.info("Successfully loaded native library for platform: "
                        + NativePlatform.getPlatformName());
            } catch (final Exception e) {
                loadingError = e;
                LOGGER.log(Level.SEVERE, "Failed to load native library", e);
                throw new RuntimeException("Failed to load native library", e);
            }
        }
    }

    /**
     * Checks if the native library has been successfully loaded.
     *
     * @return true if the library is loaded and ready, false otherwise
     */
    public static boolean isLoaded() {
        return loaded;
    }

    private static void loadNativeLibrary() throws Exception {
        // Try system library path first
        try {
            System.loadLibrary(LIBRARY_NAME);
            LOGGER.fine("Loaded native library from system path: " + LIBRARY_NAME);
            return;
        } catch (final UnsatisfiedLinkError e) {
            LOGGER.fine("System library loading failed: " + e.getMessage());
        }

        // Fall back to extracting from JAR resources
        final Path extracted = NativePlatform.resolveLibraryPath(
                NativeLibraryLoader.class, LIBRARY_NAME);

        if (extracted == null) {
            throw new UnsupportedOperationException(
                    "Native library not found for platform: "
                            + NativePlatform.getPlatformName());
        }

        System.load(extracted.toString());
    }
}
