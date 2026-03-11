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

package ai.tegmentum.wamr4j.test.integration;

import ai.tegmentum.wamr4j.PackageType;
import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WamrRuntimeExtensions;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for module management: naming, package type detection, version query, hash.
 *
 * <p>Validates that:
 * <ul>
 *   <li>Module naming round-trip works on both JNI and Panama</li>
 *   <li>Package type detection correctly identifies WASM bytecode</li>
 *   <li>Package version queries return valid values</li>
 *   <li>Module hash returns consistently on both runtimes</li>
 *   <li>isUnderlyingBinaryFreeable returns consistent results</li>
 *   <li>JNI and Panama produce identical results</li>
 * </ul>
 *
 * @since 1.0.0
 */
class ModuleManagementTest {

    private static final Logger LOGGER = Logger.getLogger(ModuleManagementTest.class.getName());

    /**
     * Builds a minimal WASM module with a single exported function for testing.
     */
    private byte[] buildMinimalModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        final int voidType = builder.addType(new byte[]{}, new byte[]{});
        final int func = builder.addFunction(voidType);
        builder.addExport("noop", func);
        builder.addCode(new byte[]{}, new byte[]{});
        return builder.build();
    }

    @Test
    void testModuleNamingParity() {
        LOGGER.info("Testing module naming round-trip parity between JNI and Panama");

        final byte[] moduleBytes = buildMinimalModule();
        final String testName = "test-module";

        String jniNameBefore = null;
        boolean jniSetResult = false;
        String jniNameAfter = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            jniNameBefore = module.getName();
            LOGGER.info("JNI getName() before set: '" + jniNameBefore + "'");
            assertNotNull(jniNameBefore, "JNI: getName() should not return null");

            jniSetResult = module.setName(testName);
            LOGGER.info("JNI setName('" + testName + "'): " + jniSetResult);
            assertTrue(jniSetResult, "JNI: setName() should succeed");

            jniNameAfter = module.getName();
            LOGGER.info("JNI getName() after set: '" + jniNameAfter + "'");
            assertEquals(testName, jniNameAfter, "JNI: getName() should return the name that was set");
        } catch (final Exception e) {
            fail("Failed JNI module naming test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                final String panamaNameBefore = module.getName();
                LOGGER.info("Panama getName() before set: '" + panamaNameBefore + "'");
                assertNotNull(panamaNameBefore, "Panama: getName() should not return null");

                final boolean panamaSetResult = module.setName(testName);
                LOGGER.info("Panama setName('" + testName + "'): " + panamaSetResult);
                assertTrue(panamaSetResult, "Panama: setName() should succeed");

                assertEquals(jniSetResult, panamaSetResult,
                    "JNI and Panama should agree on setName() result");

                final String panamaNameAfter = module.getName();
                LOGGER.info("Panama getName() after set: '" + panamaNameAfter + "'");
                assertEquals(testName, panamaNameAfter,
                    "Panama: getName() should return the name that was set");
            } catch (final Exception e) {
                LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testPackageTypeDetectionParity() {
        LOGGER.info("Testing package type detection parity between JNI and Panama");

        final byte[] moduleBytes = buildMinimalModule();

        PackageType jniFileType = null;
        PackageType jniModuleType = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            jniFileType = runtime.getFilePackageType(moduleBytes);
            LOGGER.info("JNI getFilePackageType(): " + jniFileType);
            assertEquals(PackageType.WASM, jniFileType,
                "JNI: WASM bytecode should be detected as WASM package type");

            jniModuleType = module.getPackageType();
            LOGGER.info("JNI module.getPackageType(): " + jniModuleType);
            assertEquals(PackageType.WASM, jniModuleType,
                "JNI: Compiled WASM module should report WASM package type");

            // Test with invalid bytes
            final PackageType unknownType = runtime.getFilePackageType(new byte[]{0, 0, 0, 0});
            LOGGER.info("JNI getFilePackageType(invalid): " + unknownType);
            assertEquals(PackageType.UNKNOWN, unknownType,
                "JNI: Invalid bytes should be detected as UNKNOWN");
        } catch (final Exception e) {
            fail("Failed JNI package type test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WamrRuntimeExtensions runtime =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                final PackageType panamaFileType = runtime.getFilePackageType(moduleBytes);
                LOGGER.info("Panama getFilePackageType(): " + panamaFileType);
                assertEquals(PackageType.WASM, panamaFileType,
                    "Panama: WASM bytecode should be detected as WASM package type");
                assertEquals(jniFileType, panamaFileType,
                    "JNI and Panama should agree on file package type");

                final PackageType panamaModuleType = module.getPackageType();
                LOGGER.info("Panama module.getPackageType(): " + panamaModuleType);
                assertEquals(PackageType.WASM, panamaModuleType,
                    "Panama: Compiled WASM module should report WASM package type");
                assertEquals(jniModuleType, panamaModuleType,
                    "JNI and Panama should agree on module package type");

                final PackageType unknownType = runtime.getFilePackageType(new byte[]{0, 0, 0, 0});
                LOGGER.info("Panama getFilePackageType(invalid): " + unknownType);
                assertEquals(PackageType.UNKNOWN, unknownType,
                    "Panama: Invalid bytes should be detected as UNKNOWN");
            } catch (final Exception e) {
                LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testPackageVersionParity() {
        LOGGER.info("Testing package version parity between JNI and Panama");

        final byte[] moduleBytes = buildMinimalModule();

        int jniModuleVersion = -1;
        int jniCurrentVersion = -1;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            jniModuleVersion = module.getPackageVersion();
            LOGGER.info("JNI module.getPackageVersion(): " + jniModuleVersion);
            assertTrue(jniModuleVersion >= 0,
                "JNI: Module package version should be non-negative");

            jniCurrentVersion = runtime.getCurrentPackageVersion(PackageType.WASM);
            LOGGER.info("JNI getCurrentPackageVersion(WASM): " + jniCurrentVersion);
            assertTrue(jniCurrentVersion >= 0,
                "JNI: Current package version should be non-negative");
        } catch (final Exception e) {
            fail("Failed JNI package version test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WamrRuntimeExtensions runtime =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                final int panamaModuleVersion = module.getPackageVersion();
                LOGGER.info("Panama module.getPackageVersion(): " + panamaModuleVersion);
                assertEquals(jniModuleVersion, panamaModuleVersion,
                    "JNI and Panama should agree on module package version");

                final int panamaCurrentVersion = runtime.getCurrentPackageVersion(PackageType.WASM);
                LOGGER.info("Panama getCurrentPackageVersion(WASM): " + panamaCurrentVersion);
                assertEquals(jniCurrentVersion, panamaCurrentVersion,
                    "JNI and Panama should agree on current WASM package version");
            } catch (final Exception e) {
                LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testIsUnderlyingBinaryFreeableParity() {
        LOGGER.info("Testing isUnderlyingBinaryFreeable parity between JNI and Panama");

        final byte[] moduleBytes = buildMinimalModule();

        boolean jniFreeable = false;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            jniFreeable = module.isUnderlyingBinaryFreeable();
            LOGGER.info("JNI isUnderlyingBinaryFreeable(): " + jniFreeable);
        } catch (final Exception e) {
            fail("Failed JNI binary freeable test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                final boolean panamaFreeable = module.isUnderlyingBinaryFreeable();
                LOGGER.info("Panama isUnderlyingBinaryFreeable(): " + panamaFreeable);
                assertEquals(jniFreeable, panamaFreeable,
                    "JNI and Panama should agree on isUnderlyingBinaryFreeable()");
            } catch (final Exception e) {
                LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testNullArgumentsThrowExceptions() {
        LOGGER.info("Testing null arguments throw IllegalArgumentException");

        final byte[] moduleBytes = buildMinimalModule();

        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
            assertThrows(IllegalArgumentException.class,
                () -> runtime.getFilePackageType(null),
                "getFilePackageType(null) should throw IllegalArgumentException");
            assertThrows(IllegalArgumentException.class,
                () -> runtime.getCurrentPackageVersion(null),
                "getCurrentPackageVersion(null) should throw IllegalArgumentException");
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {
            assertThrows(IllegalArgumentException.class,
                () -> module.setName(null),
                "setName(null) should throw IllegalArgumentException");
        } catch (final Exception e) {
            fail("Failed to create JNI module: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testIsXipFileParity() {
        LOGGER.info("Testing isXipFile on both runtimes");

        final byte[] moduleBytes = buildMinimalModule();

        for (final String runtime : new String[]{"jni", "panama"}) {
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                continue;
            }
            System.setProperty("wamr4j.runtime", runtime);
            try (final WamrRuntimeExtensions rt =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {

                final boolean isXip = rt.isXipFile(moduleBytes);
                LOGGER.info(runtime.toUpperCase() + ": isXipFile(wasmBytes) = " + isXip);
                assertFalse(isXip,
                    runtime.toUpperCase()
                        + ": regular WASM bytecode should not be detected as XIP/AOT");
            } catch (final Exception e) {
                fail(runtime.toUpperCase() + ": isXipFile threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testGetFilePackageVersionParity() {
        LOGGER.info("Testing getFilePackageVersion on both runtimes");

        final byte[] moduleBytes = buildMinimalModule();

        int jniVersion = -1;

        for (final String runtime : new String[]{"jni", "panama"}) {
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                continue;
            }
            System.setProperty("wamr4j.runtime", runtime);
            try (final WamrRuntimeExtensions rt =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {

                final int version = rt.getFilePackageVersion(moduleBytes);
                LOGGER.info(runtime.toUpperCase()
                    + ": getFilePackageVersion(wasmBytes) = " + version);
                assertTrue(version >= 0,
                    runtime.toUpperCase()
                        + ": file package version should be non-negative, got: " + version);

                if ("jni".equals(runtime)) {
                    jniVersion = version;
                } else {
                    assertEquals(jniVersion, version,
                        "JNI and Panama should agree on file package version");
                }
            } catch (final Exception e) {
                fail(runtime.toUpperCase()
                    + ": getFilePackageVersion threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }
}
