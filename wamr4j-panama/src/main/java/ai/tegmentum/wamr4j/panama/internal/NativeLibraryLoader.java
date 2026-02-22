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

package ai.tegmentum.wamr4j.panama.internal;

import ai.tegmentum.wamr4j.internal.NativePlatform;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Internal utility for loading the native WAMR library for Panama FFI access.
 *
 * <p>This class handles platform-specific library loading and provides
 * symbol lookup functionality for Panama FFI calls. It ensures the
 * native library is loaded exactly once and provides defensive error
 * handling to prevent JVM crashes.
 *
 * <p>This class is internal to the Panama implementation and should not
 * be used by external code.
 *
 * @since 1.0.0
 */
public final class NativeLibraryLoader {

    private static final Logger LOGGER = Logger.getLogger(NativeLibraryLoader.class.getName());

    private static final String LIBRARY_NAME = "wamr4j_native";
    private static final String LIBRARY_PATH_PREFIX = "/META-INF/native/";
    private static final AtomicBoolean attempted = new AtomicBoolean(false);
    private static volatile SymbolLookup symbolLookup;
    private static volatile String loadError;

    private NativeLibraryLoader() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Ensures the native library is loaded and ready for use.
     *
     * @throws RuntimeException if the library cannot be loaded
     */
    public static void ensureLoaded() throws RuntimeException {
        if (!attempted.get()) {
            synchronized (NativeLibraryLoader.class) {
                if (!attempted.get()) {
                    loadNativeLibrary();
                }
            }
        }

        if (loadError != null) {
            throw new RuntimeException("Native library failed to load: " + loadError);
        }
    }

    /**
     * Gets the symbol lookup for the loaded native library.
     *
     * @return the symbol lookup instance
     * @throws RuntimeException if the library is not loaded
     */
    public static SymbolLookup getSymbolLookup() throws RuntimeException {
        ensureLoaded();
        return symbolLookup;
    }

    /**
     * Checks if the native library is loaded.
     *
     * @return true if the library is loaded successfully
     */
    public static boolean isLoaded() {
        return attempted.get() && loadError == null;
    }

    private static void loadNativeLibrary() {
        try {
            LOGGER.info("Loading native library: " + LIBRARY_NAME);

            // Try to load from system library path first
            SymbolLookup lookup = tryLoadFromSystem();

            // If system loading fails, try loading from JAR resources
            if (lookup == null) {
                lookup = tryLoadFromResources();
            }

            if (lookup == null) {
                throw new RuntimeException(
                        "Failed to load native library from all attempted locations");
            }

            // Verify essential symbols are available
            verifyRequiredSymbols(lookup);

            symbolLookup = lookup;
            LOGGER.info("Successfully loaded native library: " + LIBRARY_NAME);

        } catch (final Exception e) {
            loadError = e.getMessage();
            LOGGER.severe("Failed to load native library: " + e.getMessage());
            throw new RuntimeException("Native library loading failed", e);
        } finally {
            attempted.set(true);
        }
    }

    private static SymbolLookup tryLoadFromSystem() {
        try {
            return SymbolLookup.libraryLookup(LIBRARY_NAME, Arena.global());
        } catch (final Exception e) {
            LOGGER.fine("System library loading failed: " + e.getMessage());
            return null;
        }
    }

    private static SymbolLookup tryLoadFromResources() {
        try {
            final String platformName = NativePlatform.getPlatformName();
            final String libraryFileName = NativePlatform.getLibraryFileName(LIBRARY_NAME);
            final String resourcePath =
                    LIBRARY_PATH_PREFIX + platformName + "/" + libraryFileName;

            LOGGER.fine("Attempting to load from resources: " + resourcePath);

            final Path extracted = NativePlatform.extractLibraryFromResources(
                    NativeLibraryLoader.class, resourcePath);

            if (extracted == null) {
                LOGGER.fine("Resource not found: " + resourcePath);
                return null;
            }

            return SymbolLookup.libraryLookup(extracted, Arena.global());

        } catch (final Exception e) {
            LOGGER.fine("Resource library loading failed: " + e.getMessage());
            return null;
        }
    }

    private static void verifyRequiredSymbols(final SymbolLookup lookup) throws RuntimeException {
        final String[] requiredSymbols = {
            "wamr_runtime_init",
            "wamr_runtime_destroy",
            "wamr_module_compile",
            "wamr_module_destroy",
            "wamr_instance_create",
            "wamr_instance_destroy",
            "wamr_function_lookup",
            "wamr_function_call",
            "wamr_memory_get",
            "wamr_memory_size",
            "wamr_memory_data",
            "wamr_get_version"
        };

        for (final String symbol : requiredSymbols) {
            if (lookup.find(symbol).isEmpty()) {
                throw new RuntimeException("Required symbol not found: " + symbol);
            }
        }

        LOGGER.fine("All required symbols verified in native library");
    }
}
