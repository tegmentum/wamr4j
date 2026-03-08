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

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.RunningMode;
import ai.tegmentum.wamr4j.WamrInstanceExtensions;
import ai.tegmentum.wamr4j.WamrRuntimeExtensions;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for runtime configuration: running modes, log level, bounds checks.
 *
 * <p>Validates that:
 * <ul>
 *   <li>Running mode queries work correctly on both JNI and Panama</li>
 *   <li>Interpreter mode is always supported</li>
 *   <li>Log level can be set without error</li>
 *   <li>Bounds checks can be toggled on instances</li>
 *   <li>JNI and Panama produce identical results</li>
 * </ul>
 *
 * @since 1.0.0
 */
class RuntimeConfigurationTest {

    private static final Logger LOGGER = Logger.getLogger(RuntimeConfigurationTest.class.getName());

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
    void testRunningModeSupportParity() {
        LOGGER.info("Testing running mode support parity between JNI and Panama");

        final boolean[] jniResults = new boolean[RunningMode.values().length];
        final boolean[] panamaResults = new boolean[RunningMode.values().length];

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            for (final RunningMode mode : RunningMode.values()) {
                jniResults[mode.ordinal()] = ext.isRunningModeSupported(mode);
                LOGGER.info("JNI isRunningModeSupported(" + mode + "): " + jniResults[mode.ordinal()]);
            }

            // Interpreter should always be supported
            assertTrue(ext.isRunningModeSupported(RunningMode.INTERP),
                "JNI: Interpreter mode should always be supported");
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            for (final RunningMode mode : RunningMode.values()) {
                panamaResults[mode.ordinal()] = ext.isRunningModeSupported(mode);
                LOGGER.info("Panama isRunningModeSupported(" + mode + "): " + panamaResults[mode.ordinal()]);
            }

            assertTrue(ext.isRunningModeSupported(RunningMode.INTERP),
                "Panama: Interpreter mode should always be supported");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping comparison: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Compare results
        for (final RunningMode mode : RunningMode.values()) {
            assertEquals(jniResults[mode.ordinal()], panamaResults[mode.ordinal()],
                "JNI and Panama should agree on " + mode + " support");
        }
    }

    @Test
    void testSetDefaultRunningModeParity() {
        LOGGER.info("Testing setDefaultRunningMode parity between JNI and Panama");

        boolean jniResult;
        boolean panamaResult;

        // JNI runtime - set interpreter as default (should always succeed)
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            jniResult = ext.setDefaultRunningMode(RunningMode.INTERP);
            LOGGER.info("JNI setDefaultRunningMode(INTERP): " + jniResult);
            assertTrue(jniResult, "JNI: Setting interpreter as default should succeed");
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            panamaResult = ext.setDefaultRunningMode(RunningMode.INTERP);
            LOGGER.info("Panama setDefaultRunningMode(INTERP): " + panamaResult);
            assertTrue(panamaResult, "Panama: Setting interpreter as default should succeed");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        assertEquals(jniResult, panamaResult,
            "JNI and Panama should agree on setDefaultRunningMode(INTERP)");
    }

    @Test
    void testSetLogLevelDoesNotThrow() {
        LOGGER.info("Testing setLogLevel does not throw on either runtime");

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            assertDoesNotThrow(() -> ext.setLogLevel(0), "JNI: setLogLevel(0) should not throw");
            assertDoesNotThrow(() -> ext.setLogLevel(3), "JNI: setLogLevel(3) should not throw");
            assertDoesNotThrow(() -> ext.setLogLevel(5), "JNI: setLogLevel(5) should not throw");
            LOGGER.info("JNI: setLogLevel tested at levels 0, 3, 5");
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            assertDoesNotThrow(() -> ext.setLogLevel(0), "Panama: setLogLevel(0) should not throw");
            assertDoesNotThrow(() -> ext.setLogLevel(3), "Panama: setLogLevel(3) should not throw");
            assertDoesNotThrow(() -> ext.setLogLevel(5), "Panama: setLogLevel(5) should not throw");
            LOGGER.info("Panama: setLogLevel tested at levels 0, 3, 5");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testInstanceRunningModeParity() {
        LOGGER.info("Testing instance running mode get/set parity");

        final byte[] moduleBytes = buildMinimalModule();
        RunningMode jniMode = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WamrInstanceExtensions instExt = (WamrInstanceExtensions) instance;
            jniMode = instExt.getRunningMode();
            LOGGER.info("JNI initial running mode: " + jniMode);
            assertNotNull(jniMode, "JNI: getRunningMode() should return a non-null value");

            // Try setting interpreter mode (should succeed)
            final boolean setResult = instExt.setRunningMode(RunningMode.INTERP);
            LOGGER.info("JNI setRunningMode(INTERP): " + setResult);
            assertTrue(setResult, "JNI: setRunningMode(INTERP) should succeed");

            final RunningMode afterSet = instExt.getRunningMode();
            LOGGER.info("JNI running mode after set: " + afterSet);
            assertEquals(RunningMode.INTERP, afterSet,
                "JNI: Running mode should be INTERP after set");
        } catch (final Exception e) {
            fail("Failed JNI instance running mode test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WamrInstanceExtensions instExt = (WamrInstanceExtensions) instance;
            final RunningMode panamaMode = instExt.getRunningMode();
            LOGGER.info("Panama initial running mode: " + panamaMode);
            assertNotNull(panamaMode, "Panama: getRunningMode() should return a non-null value");

            assertEquals(jniMode, panamaMode,
                "JNI and Panama should report the same initial running mode");

            final boolean setResult = instExt.setRunningMode(RunningMode.INTERP);
            LOGGER.info("Panama setRunningMode(INTERP): " + setResult);
            assertTrue(setResult, "Panama: setRunningMode(INTERP) should succeed");

            final RunningMode afterSet = instExt.getRunningMode();
            LOGGER.info("Panama running mode after set: " + afterSet);
            assertEquals(RunningMode.INTERP, afterSet,
                "Panama: Running mode should be INTERP after set");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testBoundsChecksParity() {
        LOGGER.info("Testing bounds checks parity between JNI and Panama");

        final byte[] moduleBytes = buildMinimalModule();

        // Track results from both runtimes for parity comparison
        boolean jniInitialBounds = false;
        boolean jniAfterDisable = true;
        boolean jniAfterReenable = false;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WamrInstanceExtensions instExt = (WamrInstanceExtensions) instance;
            jniInitialBounds = instExt.isBoundsChecksEnabled();
            LOGGER.info("JNI initial bounds checks: " + jniInitialBounds);

            // Disable bounds checks
            final boolean jniSetResult = instExt.setBoundsChecks(false);
            LOGGER.info("JNI setBoundsChecks(false): " + jniSetResult);

            jniAfterDisable = instExt.isBoundsChecksEnabled();
            LOGGER.info("JNI bounds checks after disable: " + jniAfterDisable);

            // Re-enable bounds checks
            instExt.setBoundsChecks(true);

            jniAfterReenable = instExt.isBoundsChecksEnabled();
            LOGGER.info("JNI bounds checks after re-enable: " + jniAfterReenable);

            // Note: On platforms with hardware bounds checking (e.g., macOS/Linux 64-bit),
            // WAMR overrides setBoundsChecks(true) to false because HW bound checks
            // are always active. We verify parity between JNI and Panama rather than
            // asserting specific values.
        } catch (final Exception e) {
            fail("Failed JNI bounds checks test: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime — verify identical behavior
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WamrInstanceExtensions instExt = (WamrInstanceExtensions) instance;
            final boolean panamaInitialBounds = instExt.isBoundsChecksEnabled();
            LOGGER.info("Panama initial bounds checks: " + panamaInitialBounds);
            assertEquals(jniInitialBounds, panamaInitialBounds,
                "JNI and Panama should agree on initial bounds checks state");

            instExt.setBoundsChecks(false);
            final boolean panamaAfterDisable = instExt.isBoundsChecksEnabled();
            LOGGER.info("Panama bounds checks after disable: " + panamaAfterDisable);
            assertEquals(jniAfterDisable, panamaAfterDisable,
                "JNI and Panama should agree on bounds checks after disable");

            instExt.setBoundsChecks(true);
            final boolean panamaAfterReenable = instExt.isBoundsChecksEnabled();
            LOGGER.info("Panama bounds checks after re-enable: " + panamaAfterReenable);
            assertEquals(jniAfterReenable, panamaAfterReenable,
                "JNI and Panama should agree on bounds checks after re-enable");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testNullRunningModeThrowsException() {
        LOGGER.info("Testing null RunningMode throws IllegalArgumentException");

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) runtime;
            assertThrows(IllegalArgumentException.class,
                () -> ext.isRunningModeSupported(null),
                "isRunningModeSupported(null) should throw IllegalArgumentException");
            assertThrows(IllegalArgumentException.class,
                () -> ext.setDefaultRunningMode(null),
                "setDefaultRunningMode(null) should throw IllegalArgumentException");
        } catch (final Exception e) {
            fail("Failed to create JNI runtime: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        final byte[] moduleBytes = buildMinimalModule();
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {
            final WamrInstanceExtensions instExt = (WamrInstanceExtensions) instance;
            assertThrows(IllegalArgumentException.class,
                () -> instExt.setRunningMode(null),
                "instance.setRunningMode(null) should throw IllegalArgumentException");
        } catch (final Exception e) {
            fail("Failed to create JNI instance: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }
}
