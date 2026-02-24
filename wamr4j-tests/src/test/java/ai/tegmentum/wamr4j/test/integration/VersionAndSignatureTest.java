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

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Version API and module-level function signature introspection.
 *
 * <p>Validates that:
 * <ul>
 *   <li>getVersion() returns a valid version string on both JNI and Panama</li>
 *   <li>getMajorVersion(), getMinorVersion(), getPatchVersion() return consistent values</li>
 *   <li>JNI and Panama return identical version information</li>
 *   <li>getExportFunctionSignature() works on both JNI and Panama (previously Panama returned null)</li>
 * </ul>
 *
 * @since 1.0.0
 */
class VersionAndSignatureTest {

    private static final Logger LOGGER = Logger.getLogger(VersionAndSignatureTest.class.getName());

    @Test
    void testVersionStringConsistency() {
        LOGGER.info("Testing version string consistency across JNI and Panama");

        final String jniVersion;
        final String panamaVersion;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime jniRuntime = RuntimeFactory.createRuntime()) {
            jniVersion = jniRuntime.getVersion();
            LOGGER.info("JNI version: " + jniVersion);
            assertNotNull(jniVersion, "JNI version should not be null");
            assertNotEquals("unknown", jniVersion, "JNI version should not be 'unknown'");
            assertTrue(jniVersion.matches("\\d+\\.\\d+\\.\\d+"),
                "JNI version should be in major.minor.patch format, got: " + jniVersion);
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime panamaRuntime = RuntimeFactory.createRuntime()) {
            panamaVersion = panamaRuntime.getVersion();
            LOGGER.info("Panama version: " + panamaVersion);
            assertNotNull(panamaVersion, "Panama version should not be null");
            assertNotEquals("unknown", panamaVersion, "Panama version should not be 'unknown'");
            assertTrue(panamaVersion.matches("\\d+\\.\\d+\\.\\d+"),
                "Panama version should be in major.minor.patch format, got: " + panamaVersion);
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping comparison: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        assertEquals(jniVersion, panamaVersion,
            "JNI and Panama should report the same WAMR version");
    }

    @Test
    void testVersionPartsConsistency() {
        LOGGER.info("Testing version parts (major/minor/patch) consistency");

        final int jniMajor;
        final int jniMinor;
        final int jniPatch;
        final String jniVersion;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime jniRuntime = RuntimeFactory.createRuntime()) {
            jniMajor = jniRuntime.getMajorVersion();
            jniMinor = jniRuntime.getMinorVersion();
            jniPatch = jniRuntime.getPatchVersion();
            jniVersion = jniRuntime.getVersion();

            LOGGER.info(String.format("JNI version parts: %d.%d.%d (string: %s)",
                jniMajor, jniMinor, jniPatch, jniVersion));

            // Verify parts are consistent with the string
            final String expectedFromParts = jniMajor + "." + jniMinor + "." + jniPatch;
            assertEquals(expectedFromParts, jniVersion,
                "JNI version string should match assembled parts");

            // Verify version numbers are reasonable
            assertTrue(jniMajor >= 0, "Major version should be non-negative");
            assertTrue(jniMinor >= 0, "Minor version should be non-negative");
            assertTrue(jniPatch >= 0, "Patch version should be non-negative");
            assertTrue(jniMajor > 0 || jniMinor > 0,
                "At least major or minor version should be positive");
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime panamaRuntime = RuntimeFactory.createRuntime()) {
            final int panamaMajor = panamaRuntime.getMajorVersion();
            final int panamaMinor = panamaRuntime.getMinorVersion();
            final int panamaPatch = panamaRuntime.getPatchVersion();
            final String panamaVersion = panamaRuntime.getVersion();

            LOGGER.info(String.format("Panama version parts: %d.%d.%d (string: %s)",
                panamaMajor, panamaMinor, panamaPatch, panamaVersion));

            // Verify parts are consistent with the string
            final String expectedFromParts = panamaMajor + "." + panamaMinor + "." + panamaPatch;
            assertEquals(expectedFromParts, panamaVersion,
                "Panama version string should match assembled parts");

            // Verify JNI and Panama agree
            assertEquals(jniMajor, panamaMajor, "Major version should match between JNI and Panama");
            assertEquals(jniMinor, panamaMinor, "Minor version should match between JNI and Panama");
            assertEquals(jniPatch, panamaPatch, "Patch version should match between JNI and Panama");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping comparison: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testModuleLevelFunctionSignature() {
        LOGGER.info("Testing module-level getExportFunctionSignature parity");

        // Build a simple module with known function signatures:
        // - add(i32, i32) -> i32
        // - identity_i64(i64) -> i64
        // - void_func() -> void
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32, i32) -> i32
        final int addType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int addFunc = builder.addFunction(addType);
        builder.addExport("add", addFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_ADD,
        });

        // Type 1: (i64) -> i64
        final int i64Type = builder.addType(
            new byte[]{WasmModuleBuilder.I64},
            new byte[]{WasmModuleBuilder.I64}
        );
        final int i64Func = builder.addFunction(i64Type);
        builder.addExport("identity_i64", i64Func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
        });

        // Type 2: () -> void
        final int voidType = builder.addType(new byte[]{}, new byte[]{});
        final int voidFunc = builder.addFunction(voidType);
        builder.addExport("void_func", voidFunc);
        builder.addCode(new byte[]{}, new byte[]{});

        final byte[] moduleBytes = builder.build();

        FunctionSignature jniAddSig = null;
        FunctionSignature jniI64Sig = null;
        FunctionSignature jniVoidSig = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            jniAddSig = module.getExportFunctionSignature("add");
            jniI64Sig = module.getExportFunctionSignature("identity_i64");
            jniVoidSig = module.getExportFunctionSignature("void_func");

            LOGGER.info("JNI add signature: " + jniAddSig);
            LOGGER.info("JNI identity_i64 signature: " + jniI64Sig);
            LOGGER.info("JNI void_func signature: " + jniVoidSig);

            assertNotNull(jniAddSig, "JNI should find 'add' function signature");
            assertArrayEquals(new ValueType[]{ValueType.I32, ValueType.I32},
                jniAddSig.getParameterTypes(), "add params should be [I32, I32]");
            assertArrayEquals(new ValueType[]{ValueType.I32},
                jniAddSig.getReturnTypes(), "add returns should be [I32]");

            assertNotNull(jniI64Sig, "JNI should find 'identity_i64' function signature");
            assertArrayEquals(new ValueType[]{ValueType.I64},
                jniI64Sig.getParameterTypes(), "identity_i64 params should be [I64]");
            assertArrayEquals(new ValueType[]{ValueType.I64},
                jniI64Sig.getReturnTypes(), "identity_i64 returns should be [I64]");

            assertNotNull(jniVoidSig, "JNI should find 'void_func' function signature");
            assertEquals(0, jniVoidSig.getParameterTypes().length,
                "void_func should have no params");
            assertEquals(0, jniVoidSig.getReturnTypes().length,
                "void_func should have no results");

            // Non-existent function should return null
            assertNull(module.getExportFunctionSignature("nonexistent"),
                "JNI should return null for non-existent function");

        } catch (final Exception e) {
            fail("Failed JNI signature test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            final FunctionSignature panamaAddSig = module.getExportFunctionSignature("add");
            final FunctionSignature panamaI64Sig = module.getExportFunctionSignature("identity_i64");
            final FunctionSignature panamaVoidSig = module.getExportFunctionSignature("void_func");

            LOGGER.info("Panama add signature: " + panamaAddSig);
            LOGGER.info("Panama identity_i64 signature: " + panamaI64Sig);
            LOGGER.info("Panama void_func signature: " + panamaVoidSig);

            assertNotNull(panamaAddSig,
                "Panama should find 'add' function signature (was null before Phase 1)");
            assertNotNull(panamaI64Sig,
                "Panama should find 'identity_i64' function signature");
            assertNotNull(panamaVoidSig,
                "Panama should find 'void_func' function signature");

            // Compare JNI and Panama signatures
            assertEquals(jniAddSig, panamaAddSig,
                "JNI and Panama 'add' signatures should match");
            assertEquals(jniI64Sig, panamaI64Sig,
                "JNI and Panama 'identity_i64' signatures should match");
            assertEquals(jniVoidSig, panamaVoidSig,
                "JNI and Panama 'void_func' signatures should match");

            // Non-existent function should return null
            assertNull(module.getExportFunctionSignature("nonexistent"),
                "Panama should return null for non-existent function");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping comparison: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }
}
