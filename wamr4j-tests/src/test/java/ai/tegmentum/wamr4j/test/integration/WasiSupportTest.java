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
import ai.tegmentum.wamr4j.WasiConfiguration;
import ai.tegmentum.wamr4j.WamrInstanceExtensions;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WASI support (Phase 9).
 *
 * <p>Tests WASI configuration, mode detection, exit codes, and execution.
 * All operations are tested for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class WasiSupportTest {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    private static final Logger LOGGER =
        Logger.getLogger(WasiSupportTest.class.getName());

    /**
     * Builds a minimal non-WASI module with a simple add function.
     */
    private byte[] buildSimpleModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("add", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_ADD,
        });

        return builder.build();
    }

    @Test
    void testIsWasiModeNonWasiModuleParity() throws Exception {
        LOGGER.info("Testing isWasiMode() returns false for non-WASI module on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final boolean jniResult = instance.isWasiMode();
            LOGGER.info("JNI isWasiMode() on non-WASI module: " + jniResult);
            assertFalse(jniResult, "JNI: Non-WASI module should not be in WASI mode");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final boolean panamaResult = instance.isWasiMode();
                LOGGER.info("Panama isWasiMode() on non-WASI module: " + panamaResult);
                assertFalse(panamaResult, "Panama: Non-WASI module should not be in WASI mode");
            }
        }
    }

    @Test
    void testGetWasiExitCodeDefaultParity() throws Exception {
        LOGGER.info("Testing getWasiExitCode() default value on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final int jniCode = instance.getWasiExitCode();
            LOGGER.info("JNI getWasiExitCode() default: " + jniCode);
            assertEquals(0, jniCode, "JNI: Default WASI exit code should be 0");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final int panamaCode = instance.getWasiExitCode();
                LOGGER.info("Panama getWasiExitCode() default: " + panamaCode);
                assertEquals(0, panamaCode, "Panama: Default WASI exit code should be 0");
            }
        }
    }

    @Test
    void testHasWasiStartFunctionNonWasiParity() throws Exception {
        LOGGER.info("Testing hasWasiStartFunction() on non-WASI module on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final boolean jniResult = instance.hasWasiStartFunction();
            LOGGER.info("JNI hasWasiStartFunction() on non-WASI module: " + jniResult);
            assertFalse(jniResult, "JNI: Non-WASI module should not have _start function");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final boolean panamaResult = instance.hasWasiStartFunction();
                LOGGER.info("Panama hasWasiStartFunction() on non-WASI module: " + panamaResult);
                assertFalse(panamaResult, "Panama: Non-WASI module should not have _start function");
            }
        }
    }

    @Test
    void testConfigureWasiDoesNotCrashParity() throws Exception {
        LOGGER.info("Testing configureWasi() on a regular module does not crash on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        final WasiConfiguration config = new WasiConfiguration()
            .setArgs("test_program", "--verbose")
            .setEnvVars("HOME=" + TEMP_DIR, "PATH=/usr/bin")
            .setPreopens(TEMP_DIR);

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertDoesNotThrow(() -> module.configureWasi(config),
                "JNI: configureWasi should not throw on regular module");
            LOGGER.info("JNI configureWasi() on regular module: succeeded without crash");

            try (final WebAssemblyInstance instance = module.instantiate()) {
                LOGGER.info("JNI: Module still instantiates after configureWasi");
                assertNotNull(instance, "JNI: Instance should still be creatable");
            }
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                assertDoesNotThrow(() -> module.configureWasi(config),
                    "Panama: configureWasi should not throw on regular module");
                LOGGER.info("Panama configureWasi() on regular module: succeeded without crash");

                try (final WebAssemblyInstance instance = module.instantiate()) {
                    LOGGER.info("Panama: Module still instantiates after configureWasi");
                    assertNotNull(instance, "Panama: Instance should still be creatable");
                }
            }
        }
    }

    @Test
    void testConfigureWasiNullConfigThrows() throws Exception {
        LOGGER.info("Testing configureWasi(null) throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertThrows(IllegalArgumentException.class, () -> module.configureWasi(null),
                "JNI: configureWasi(null) should throw IllegalArgumentException");
            LOGGER.info("JNI configureWasi(null): correctly threw IllegalArgumentException");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                assertThrows(IllegalArgumentException.class, () -> module.configureWasi(null),
                    "Panama: configureWasi(null) should throw IllegalArgumentException");
                LOGGER.info("Panama configureWasi(null): correctly threw IllegalArgumentException");
            }
        }
    }

    @Test
    void testWasiConfigurationBuilder() {
        LOGGER.info("Testing WasiConfiguration builder methods");

        final WasiConfiguration config = new WasiConfiguration()
            .setArgs("program", "arg1", "arg2")
            .setEnvVars("KEY1=val1", "KEY2=val2")
            .setPreopens("/tmp", "/data")
            .setMappedDirs("/guest::/host")
            .setAddrPool("127.0.0.1")
            .setNsLookupPool("localhost");

        assertArrayEquals(new String[]{"program", "arg1", "arg2"}, config.getArgs(),
            "Args should match what was set");
        assertArrayEquals(new String[]{"KEY1=val1", "KEY2=val2"}, config.getEnvVars(),
            "Env vars should match what was set");
        assertArrayEquals(new String[]{"/tmp", "/data"}, config.getPreopens(),
            "Preopens should match what was set");
        assertArrayEquals(new String[]{"/guest::/host"}, config.getMappedDirs(),
            "Mapped dirs should match what was set");
        assertArrayEquals(new String[]{"127.0.0.1"}, config.getAddrPool(),
            "Addr pool should match what was set");
        assertArrayEquals(new String[]{"localhost"}, config.getNsLookupPool(),
            "NS lookup pool should match what was set");

        LOGGER.info("WasiConfiguration builder: all fields set and retrieved correctly");
    }

    @Test
    void testWasiConfigurationDefaults() {
        LOGGER.info("Testing WasiConfiguration defaults");

        final WasiConfiguration config = new WasiConfiguration();

        assertArrayEquals(new String[0], config.getArgs(), "Default args should be empty");
        assertArrayEquals(new String[0], config.getEnvVars(), "Default env vars should be empty");
        assertArrayEquals(new String[0], config.getPreopens(), "Default preopens should be empty");
        assertArrayEquals(new String[0], config.getMappedDirs(), "Default mapped dirs should be empty");
        assertArrayEquals(new String[0], config.getAddrPool(), "Default addr pool should be empty");
        assertArrayEquals(new String[0], config.getNsLookupPool(), "Default NS lookup pool should be empty");

        LOGGER.info("WasiConfiguration defaults: all arrays correctly empty");
    }

    @Test
    void testWasiConfigurationNullSafety() {
        LOGGER.info("Testing WasiConfiguration null handling");

        final WasiConfiguration config = new WasiConfiguration()
            .setArgs((String[]) null)
            .setEnvVars((String[]) null)
            .setPreopens((String[]) null)
            .setMappedDirs((String[]) null)
            .setAddrPool((String[]) null)
            .setNsLookupPool((String[]) null);

        assertArrayEquals(new String[0], config.getArgs(), "Null args should become empty array");
        assertArrayEquals(new String[0], config.getEnvVars(), "Null env vars should become empty array");
        assertArrayEquals(new String[0], config.getPreopens(), "Null preopens should become empty array");
        assertArrayEquals(new String[0], config.getMappedDirs(), "Null mapped dirs should become empty array");
        assertArrayEquals(new String[0], config.getAddrPool(), "Null addr pool should become empty array");
        assertArrayEquals(new String[0], config.getNsLookupPool(), "Null NS lookup pool should become empty array");

        LOGGER.info("WasiConfiguration null safety: all null inputs handled correctly");
    }

    @Test
    void testExecuteFuncOnNonWasiModuleParity() throws Exception {
        LOGGER.info("Testing executeFunc() on a non-WASI module with string args on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final boolean jniResult = instance.executeFunc("add", new String[]{"10", "20"});
            LOGGER.info("JNI executeFunc('add', ['10', '20']): " + jniResult);
            // executeFunc with string args works for non-WASI modules too
            // (wasm_application_execute_func is a general API)
            assertTrue(jniResult, "JNI: executeFunc should succeed for exported function");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final boolean panamaResult = instance.executeFunc("add", new String[]{"10", "20"});
                LOGGER.info("Panama executeFunc('add', ['10', '20']): " + panamaResult);
                assertTrue(panamaResult, "Panama: executeFunc should succeed for exported function");
            }
        }
    }

    @Test
    void testExecuteFuncNullNameThrows() throws Exception {
        LOGGER.info("Testing executeFunc(null, ...) throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.executeFunc(null, new String[0]),
                "JNI: executeFunc(null, ...) should throw IllegalArgumentException");
            LOGGER.info("JNI executeFunc(null): correctly threw IllegalArgumentException");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                assertThrows(IllegalArgumentException.class,
                    () -> instance.executeFunc(null, new String[0]),
                    "Panama: executeFunc(null, ...) should throw IllegalArgumentException");
                LOGGER.info("Panama executeFunc(null): correctly threw IllegalArgumentException");
            }
        }
    }

    @Test
    void testWasiMethodsOnClosedInstanceThrow() throws Exception {
        LOGGER.info("Testing WASI methods throw on closed instance on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            final WamrInstanceExtensions instance =
                (WamrInstanceExtensions) module.instantiate();
            instance.close();

            assertThrows(IllegalStateException.class, instance::isWasiMode,
                "JNI: isWasiMode() on closed instance should throw");
            assertThrows(IllegalStateException.class, instance::getWasiExitCode,
                "JNI: getWasiExitCode() on closed instance should throw");
            assertThrows(IllegalStateException.class, instance::hasWasiStartFunction,
                "JNI: hasWasiStartFunction() on closed instance should throw");
            assertThrows(IllegalStateException.class, () -> instance.executeMain(new String[0]),
                "JNI: executeMain() on closed instance should throw");
            assertThrows(IllegalStateException.class,
                () -> instance.executeFunc("add", new String[0]),
                "JNI: executeFunc() on closed instance should throw");
            LOGGER.info("JNI: All WASI methods correctly throw on closed instance");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                final WamrInstanceExtensions instance =
                    (WamrInstanceExtensions) module.instantiate();
                instance.close();

                assertThrows(IllegalStateException.class, instance::isWasiMode,
                    "Panama: isWasiMode() on closed instance should throw");
                assertThrows(IllegalStateException.class, instance::getWasiExitCode,
                    "Panama: getWasiExitCode() on closed instance should throw");
                assertThrows(IllegalStateException.class, instance::hasWasiStartFunction,
                    "Panama: hasWasiStartFunction() on closed instance should throw");
                assertThrows(IllegalStateException.class, () -> instance.executeMain(new String[0]),
                    "Panama: executeMain() on closed instance should throw");
                assertThrows(IllegalStateException.class,
                    () -> instance.executeFunc("add", new String[0]),
                    "Panama: executeFunc() on closed instance should throw");
                LOGGER.info("Panama: All WASI methods correctly throw on closed instance");
            }
        }
    }

    @Test
    void testConfigureWasiOnClosedModuleThrows() throws Exception {
        LOGGER.info("Testing configureWasi() on closed module throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();
        final WasiConfiguration config = new WasiConfiguration();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {

            final WebAssemblyModule module = runtime.compile(moduleBytes);
            module.close();

            assertThrows(IllegalStateException.class, () -> module.configureWasi(config),
                "JNI: configureWasi() on closed module should throw");
            LOGGER.info("JNI: configureWasi on closed module correctly threw IllegalStateException");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {

                final WebAssemblyModule module = runtime.compile(moduleBytes);
                module.close();

                assertThrows(IllegalStateException.class, () -> module.configureWasi(config),
                    "Panama: configureWasi() on closed module should throw");
                LOGGER.info("Panama: configureWasi on closed module correctly threw IllegalStateException");
            }
        }
    }

    @Test
    void testConfigureWasiWithFullConfigParity() throws Exception {
        LOGGER.info("Testing configureWasi() with all options set on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        final WasiConfiguration config = new WasiConfiguration()
            .setArgs("program_name", "--flag")
            .setEnvVars("HOME=" + TEMP_DIR, "USER=test")
            .setPreopens(TEMP_DIR)
            .setMappedDirs("/virtual::" + TEMP_DIR)
            .setAddrPool("127.0.0.1")
            .setNsLookupPool("localhost");

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertDoesNotThrow(() -> module.configureWasi(config),
                "JNI: configureWasi with full config should not throw");
            LOGGER.info("JNI configureWasi with full config: OK");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {

                assertDoesNotThrow(() -> module.configureWasi(config),
                    "Panama: configureWasi with full config should not throw");
                LOGGER.info("Panama configureWasi with full config: OK");
            }
        }
    }

    @Test
    void testWasiConfigurationStdioFds() {
        LOGGER.info("Testing WasiConfiguration stdio FD setters and hasCustomStdio");

        final WasiConfiguration config = new WasiConfiguration();

        // By default, no custom stdio should be set
        assertFalse(config.hasCustomStdio(),
            "Default WasiConfiguration should not have custom stdio");
        LOGGER.info("hasCustomStdio() default: false (correct)");

        // Set stdin FD
        config.setStdinFd(0L);
        LOGGER.info("After setStdinFd(0): hasCustomStdio() = " + config.hasCustomStdio());
        assertTrue(config.hasCustomStdio(),
            "hasCustomStdio should be true after setting stdin FD");

        // Create a fresh config and set stdout
        final WasiConfiguration config2 = new WasiConfiguration();
        config2.setStdoutFd(1L);
        LOGGER.info("After setStdoutFd(1): hasCustomStdio() = " + config2.hasCustomStdio());
        assertTrue(config2.hasCustomStdio(),
            "hasCustomStdio should be true after setting stdout FD");

        // Create a fresh config and set stderr
        final WasiConfiguration config3 = new WasiConfiguration();
        config3.setStderrFd(2L);
        LOGGER.info("After setStderrFd(2): hasCustomStdio() = " + config3.hasCustomStdio());
        assertTrue(config3.hasCustomStdio(),
            "hasCustomStdio should be true after setting stderr FD");

        // Set all three on one config
        final WasiConfiguration configAll = new WasiConfiguration()
            .setStdinFd(10L)
            .setStdoutFd(11L)
            .setStderrFd(12L);
        assertTrue(configAll.hasCustomStdio(),
            "hasCustomStdio should be true after setting all stdio FDs");
        LOGGER.info("All stdio FDs set: hasCustomStdio() = true (correct)");
    }
}
