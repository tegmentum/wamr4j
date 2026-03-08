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
package ai.tegmentum.wamr4j.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared utility for platform detection and native library resource extraction.
 *
 * <p>This class centralizes OS/architecture detection and JAR resource extraction
 * logic used by both the JNI and Panama native library loaders. It eliminates
 * duplication of platform-specific code across implementation modules.
 *
 * <p>This class is internal to wamr4j and should not be used by external code.
 *
 * @since 1.0.0
 */
public final class NativePlatform {

    private static final Logger LOGGER = Logger.getLogger(NativePlatform.class.getName());

    private static final String OS_NAME = System.getProperty("os.name", "unknown").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch", "unknown").toLowerCase();

    private NativePlatform() {
        throw new UnsupportedOperationException("NativePlatform cannot be instantiated");
    }

    /**
     * Returns the normalized operating system name.
     *
     * @return one of {@code "linux"}, {@code "darwin"}, {@code "windows"}, or {@code "unknown"}
     */
    public static String getNormalizedOS() {
        if (OS_NAME.contains("windows")) {
            return "windows";
        } else if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            return "darwin";
        } else if (OS_NAME.contains("linux")) {
            return "linux";
        } else {
            return "unknown";
        }
    }

    /**
     * Returns the normalized CPU architecture name.
     *
     * @return one of {@code "x86_64"}, {@code "aarch64"}, {@code "x86"}, {@code "arm"},
     *     or {@code "unknown"}
     */
    public static String getNormalizedArchitecture() {
        final String arch = OS_ARCH;

        if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            return "x86_64";
        }

        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }

        if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }

        if (arch.contains("arm")) {
            return "arm";
        }

        return "unknown";
    }

    /**
     * Returns the platform identifier string in the format {@code "{os}-{arch}"}.
     *
     * @return the platform name, e.g. {@code "darwin-aarch64"} or {@code "linux-x86_64"}
     */
    public static String getPlatformName() {
        return getNormalizedOS() + "-" + getNormalizedArchitecture();
    }

    /**
     * Returns the current Java major version.
     *
     * <p>Handles both the legacy {@code 1.x} format (Java 8 and below) and the modern
     * {@code x.y.z} format (Java 9+).
     *
     * @return the major version number (e.g. 8, 11, 17, 23)
     */
    public static int getJavaMajorVersion() {
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
            LOGGER.log(Level.WARNING, "Failed to determine Java version, assuming Java 8", e);
            return 8;
        }
    }

    /**
     * Returns the platform-specific native library file name for the given library base name.
     *
     * <p>Applies platform conventions: {@code lib} prefix and {@code .so} / {@code .dylib} /
     * {@code .dll} suffix.
     *
     * @param libraryName the base library name (e.g. {@code "wamr4j_native"})
     * @return the platform-specific file name (e.g. {@code "libwamr4j_native.dylib"})
     */
    public static String getLibraryFileName(final String libraryName) {
        final String os = getNormalizedOS();
        switch (os) {
            case "windows":
                return libraryName + ".dll";
            case "darwin":
                return "lib" + libraryName + ".dylib";
            case "linux":
            default:
                return "lib" + libraryName + ".so";
        }
    }

    /**
     * Resolves the native library path by extracting it from JAR resources.
     *
     * <p>Builds the platform-specific resource path and extracts the library to a
     * temporary location. The resource is expected at
     * {@code /META-INF/native/{platform}/{libraryFileName}}.
     *
     * @param anchor the class whose classloader is used to locate the resource
     * @param libraryName the base library name (e.g. {@code "wamr4j_native"})
     * @return the path to the extracted library, or {@code null} if not found
     * @throws IOException if extraction fails
     */
    public static Path resolveLibraryPath(final Class<?> anchor, final String libraryName)
            throws IOException {
        final String platformName = getPlatformName();
        final String libraryFileName = getLibraryFileName(libraryName);
        final String resourcePath = "/META-INF/native/" + platformName + "/" + libraryFileName;

        LOGGER.fine("Resolving native library from resources: " + resourcePath);
        return extractLibraryFromResources(anchor, resourcePath);
    }

    /**
     * Extracts a native library from JAR resources to a temporary file.
     *
     * <p>The resource is located using the provided anchor class's classloader. The extracted
     * file and its parent temp directory are marked for deletion on JVM exit.
     *
     * @param anchor the class whose classloader is used to locate the resource
     * @param resourcePath the absolute resource path (e.g.
     *     {@code "/META-INF/native/darwin-aarch64/libwamr4j_native.dylib"})
     * @return the path to the extracted temporary file, or {@code null} if the resource was
     *     not found
     * @throws IOException if extraction fails due to an I/O error
     */
    public static Path extractLibraryFromResources(final Class<?> anchor, final String resourcePath)
            throws IOException {
        try (final InputStream stream = anchor.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.fine("Resource not found: " + resourcePath);
                return null;
            }

            final String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            final Path tempDir = Files.createTempDirectory("wamr4j-native");
            final Path tempFile = tempDir.resolve(fileName);

            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            tempDir.toFile().deleteOnExit();
            tempFile.toFile().deleteOnExit();

            LOGGER.fine("Extracted native library to: " + tempFile);
            return tempFile;
        }
    }
}
