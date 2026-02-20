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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NativePlatform} shared utility.
 */
class NativePlatformTest {

    private static final List<String> KNOWN_OS_VALUES =
            Arrays.asList("linux", "darwin", "windows", "unknown");

    private static final List<String> KNOWN_ARCH_VALUES =
            Arrays.asList("x86_64", "aarch64", "x86", "arm", "unknown");

    @Test
    void testGetNormalizedOSReturnsKnownValue() {
        final String os = NativePlatform.getNormalizedOS();
        System.out.println("Detected OS: " + os);
        assertThat(os)
                .as("getNormalizedOS() should return a known OS identifier")
                .isNotNull()
                .isIn(KNOWN_OS_VALUES);
    }

    @Test
    void testGetNormalizedArchitectureReturnsKnownValue() {
        final String arch = NativePlatform.getNormalizedArchitecture();
        System.out.println("Detected architecture: " + arch);
        assertThat(arch)
                .as("getNormalizedArchitecture() should return a known architecture identifier")
                .isNotNull()
                .isIn(KNOWN_ARCH_VALUES);
    }

    @Test
    void testGetPlatformNameFormat() {
        final String platform = NativePlatform.getPlatformName();
        System.out.println("Detected platform: " + platform);
        assertThat(platform)
                .as("getPlatformName() should be in '{os}-{arch}' format")
                .isNotNull()
                .contains("-");

        final String[] parts = platform.split("-", 2);
        assertThat(parts).hasSize(2);
        assertThat(parts[0])
                .as("OS portion of platform name")
                .isIn(KNOWN_OS_VALUES);
        assertThat(parts[1])
                .as("Architecture portion of platform name")
                .isIn(KNOWN_ARCH_VALUES);
    }

    @Test
    void testGetPlatformNameConsistency() {
        final String expected = NativePlatform.getNormalizedOS()
                + "-" + NativePlatform.getNormalizedArchitecture();
        assertThat(NativePlatform.getPlatformName())
                .as("getPlatformName() should equal getNormalizedOS() + '-' + getNormalizedArchitecture()")
                .isEqualTo(expected);
    }

    @Test
    void testGetLibraryFileNameForCurrentOS() {
        final String fileName = NativePlatform.getLibraryFileName("wamr4j_native");
        final String os = NativePlatform.getNormalizedOS();
        System.out.println("Library file name for OS '" + os + "': " + fileName);

        assertThat(fileName).isNotNull().isNotEmpty();

        switch (os) {
            case "darwin":
                assertThat(fileName)
                        .as("macOS library should use .dylib extension with lib prefix")
                        .isEqualTo("libwamr4j_native.dylib");
                break;
            case "windows":
                assertThat(fileName)
                        .as("Windows library should use .dll extension with no prefix")
                        .isEqualTo("wamr4j_native.dll");
                break;
            case "linux":
                assertThat(fileName)
                        .as("Linux library should use .so extension with lib prefix")
                        .isEqualTo("libwamr4j_native.so");
                break;
            default:
                // For unknown OS, falls through to linux convention
                assertThat(fileName).endsWith(".so");
                break;
        }
    }

    @Test
    void testGetLibraryFileNameWithDifferentNames() {
        final String os = NativePlatform.getNormalizedOS();

        final String result = NativePlatform.getLibraryFileName("mylib");
        switch (os) {
            case "darwin":
                assertThat(result).isEqualTo("libmylib.dylib");
                break;
            case "windows":
                assertThat(result).isEqualTo("mylib.dll");
                break;
            default:
                assertThat(result).isEqualTo("libmylib.so");
                break;
        }
    }

    @Test
    void testExtractLibraryFromResourcesMissingResource() throws IOException {
        final Path result = NativePlatform.extractLibraryFromResources(
                NativePlatformTest.class, "/nonexistent/resource/path/libfake.so");

        assertThat(result)
                .as("extractLibraryFromResources() should return null for missing resources")
                .isNull();
    }
}
