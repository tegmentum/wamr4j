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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * <li>Load library using System.loadLibrary() or System.load()</li>
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
    private static final String LIBRARY_PATH_PREFIX = "/native/";
    
    // Loading state tracking
    private static volatile boolean loaded = false;
    private static volatile Throwable loadingError = null;
    
    // Platform detection
    private static final String OS_NAME = System.getProperty("os.name", "unknown").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch", "unknown").toLowerCase();

    // Private constructor to prevent instantiation
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
                LOGGER.info("Successfully loaded native library for platform: " + getPlatformName());
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

    /**
     * Returns the name of the current platform for diagnostic purposes.
     * 
     * @return a string describing the current platform (e.g., "linux-x86_64")
     */
    public static String getPlatformName() {
        return getNormalizedOS() + "-" + getNormalizedArchitecture();
    }

    private static void loadNativeLibrary() throws Exception {
        final String platformName = getPlatformName();
        final String libraryFileName = getLibraryFileName();
        final String libraryResourcePath = LIBRARY_PATH_PREFIX + platformName + "/" + libraryFileName;
        
        LOGGER.fine("Attempting to load native library: " + libraryResourcePath);
        
        // Try to load from classpath resource
        try (final InputStream libraryStream = NativeLibraryLoader.class.getResourceAsStream(libraryResourcePath)) {
            if (libraryStream == null) {
                throw new UnsupportedOperationException(
                    "Native library not found for platform: " + platformName +
                    " (expected: " + libraryResourcePath + ")");
            }
            
            // Extract to temporary file and load
            final Path tempLibrary = extractToTemporaryFile(libraryStream, libraryFileName);
            System.load(tempLibrary.toString());
            
            // Mark temporary file for deletion on JVM exit
            tempLibrary.toFile().deleteOnExit();
        }
    }

    private static Path extractToTemporaryFile(final InputStream libraryStream, final String fileName) throws IOException {
        final Path tempDir = Files.createTempDirectory("wamr4j-native");
        final Path tempLibrary = tempDir.resolve(fileName);
        
        Files.copy(libraryStream, tempLibrary, StandardCopyOption.REPLACE_EXISTING);
        
        // Mark directory and file for deletion on JVM exit
        tempDir.toFile().deleteOnExit();
        tempLibrary.toFile().deleteOnExit();
        
        return tempLibrary;
    }

    private static String getLibraryFileName() {
        final String os = getNormalizedOS();
        switch (os) {
            case "windows":
                return LIBRARY_NAME + ".dll";
            case "macos":
                return "lib" + LIBRARY_NAME + ".dylib";
            case "linux":
            default:
                return "lib" + LIBRARY_NAME + ".so";
        }
    }

    private static String getNormalizedOS() {
        if (OS_NAME.contains("windows")) {
            return "windows";
        } else if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            return "macos";
        } else if (OS_NAME.contains("linux")) {
            return "linux";
        } else {
            return "unknown";
        }
    }

    private static String getNormalizedArchitecture() {
        final String arch = OS_ARCH.toLowerCase();
        
        // x86_64 variants
        if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            return "x86_64";
        }
        
        // ARM64 variants
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        
        // x86 32-bit variants
        if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }
        
        // ARM 32-bit variants
        if (arch.contains("arm")) {
            return "arm";
        }
        
        return "unknown";
    }
}