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
import ai.tegmentum.wamr4j.WebAssemblyFunction;
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
 * Integration tests for custom data on WebAssembly instances (Phase 10).
 *
 * <p>Tests setCustomData/getCustomData for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class CustomDataTest {

    private static final Logger LOGGER =
        Logger.getLogger(CustomDataTest.class.getName());

    /**
     * Builds a minimal module with a simple add function.
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
    void testCustomDataDefaultIsZeroParity() throws Exception {
        LOGGER.info("Testing getCustomData() returns 0 by default on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final long jniResult = instance.getCustomData();
            LOGGER.info("JNI getCustomData() default: " + jniResult);
            assertEquals(0L, jniResult, "JNI: Default custom data should be 0");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final long panamaResult = instance.getCustomData();
                LOGGER.info("Panama getCustomData() default: " + panamaResult);
                assertEquals(0L, panamaResult, "Panama: Default custom data should be 0");
            }
        }
    }

    @Test
    void testSetAndGetCustomDataParity() throws Exception {
        LOGGER.info("Testing setCustomData/getCustomData round-trip on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();
        final long testValue = 42L;

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            instance.setCustomData(testValue);
            final long jniResult = instance.getCustomData();
            LOGGER.info("JNI setCustomData(" + testValue + ") -> getCustomData(): " + jniResult);
            assertEquals(testValue, jniResult, "JNI: Custom data round-trip failed");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                instance.setCustomData(testValue);
                final long panamaResult = instance.getCustomData();
                LOGGER.info("Panama setCustomData(" + testValue + ") -> getCustomData(): " + panamaResult);
                assertEquals(testValue, panamaResult, "Panama: Custom data round-trip failed");
            }
        }
    }

    @Test
    void testCustomDataLargeValueParity() throws Exception {
        LOGGER.info("Testing custom data with large value on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();
        final long largeValue = Long.MAX_VALUE;

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            instance.setCustomData(largeValue);
            final long jniResult = instance.getCustomData();
            LOGGER.info("JNI custom data with Long.MAX_VALUE: " + jniResult);
            assertEquals(largeValue, jniResult,
                "JNI: Custom data should handle Long.MAX_VALUE");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                instance.setCustomData(largeValue);
                final long panamaResult = instance.getCustomData();
                LOGGER.info("Panama custom data with Long.MAX_VALUE: " + panamaResult);
                assertEquals(largeValue, panamaResult,
                    "Panama: Custom data should handle Long.MAX_VALUE");
            }
        }
    }

    @Test
    void testCustomDataOverwriteParity() throws Exception {
        LOGGER.info("Testing that setCustomData overwrites previous value on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            instance.setCustomData(100L);
            assertEquals(100L, instance.getCustomData(), "JNI: First set failed");

            instance.setCustomData(200L);
            final long jniResult = instance.getCustomData();
            LOGGER.info("JNI custom data after overwrite: " + jniResult);
            assertEquals(200L, jniResult, "JNI: Overwrite failed");

            instance.setCustomData(0L);
            assertEquals(0L, instance.getCustomData(), "JNI: Reset to 0 failed");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                instance.setCustomData(100L);
                assertEquals(100L, instance.getCustomData(), "Panama: First set failed");

                instance.setCustomData(200L);
                final long panamaResult = instance.getCustomData();
                LOGGER.info("Panama custom data after overwrite: " + panamaResult);
                assertEquals(200L, panamaResult, "Panama: Overwrite failed");

                instance.setCustomData(0L);
                assertEquals(0L, instance.getCustomData(), "Panama: Reset to 0 failed");
            }
        }
    }

    @Test
    void testCustomDataPersistsAcrossFunctionCalls() throws Exception {
        LOGGER.info("Testing that custom data persists across function calls on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();
        final long testValue = 12345L;

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            instance.setCustomData(testValue);
            final WebAssemblyFunction addFunc = instance.getFunction("add");
            addFunc.invoke(1, 2);  // Call a function
            final long jniResult = instance.getCustomData();
            LOGGER.info("JNI custom data after function call: " + jniResult);
            assertEquals(testValue, jniResult,
                "JNI: Custom data should persist across function calls");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                instance.setCustomData(testValue);
                final WebAssemblyFunction addFunc = instance.getFunction("add");
                addFunc.invoke(1, 2);  // Call a function
                final long panamaResult = instance.getCustomData();
                LOGGER.info("Panama custom data after function call: " + panamaResult);
                assertEquals(testValue, panamaResult,
                    "Panama: Custom data should persist across function calls");
            }
        }
    }

    @Test
    void testCustomDataOnClosedInstanceThrows() throws Exception {
        LOGGER.info("Testing custom data operations on closed instance throw on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        final WamrInstanceExtensions jniInstance;
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {
            jniInstance = (WamrInstanceExtensions) module.instantiate();
            jniInstance.close();
        }

        assertThrows(IllegalStateException.class, () -> jniInstance.setCustomData(1L),
            "JNI: setCustomData on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> jniInstance.getCustomData(),
            "JNI: getCustomData on closed instance should throw");
        LOGGER.info("JNI: Closed instance correctly threw IllegalStateException");

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            final WamrInstanceExtensions panamaInstance;
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes)) {
                panamaInstance = (WamrInstanceExtensions) module.instantiate();
                panamaInstance.close();
            }

            assertThrows(IllegalStateException.class, () -> panamaInstance.setCustomData(1L),
                "Panama: setCustomData on closed instance should throw");
            assertThrows(IllegalStateException.class, () -> panamaInstance.getCustomData(),
                "Panama: getCustomData on closed instance should throw");
            LOGGER.info("Panama: Closed instance correctly threw IllegalStateException");
        }
    }

    @Test
    void testCustomDataIsolationBetweenInstances() throws Exception {
        LOGGER.info("Testing that custom data is isolated between instances on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance1 =
                 (WamrInstanceExtensions) module.instantiate();
             final WamrInstanceExtensions instance2 =
                 (WamrInstanceExtensions) module.instantiate()) {

            instance1.setCustomData(111L);
            instance2.setCustomData(222L);

            assertEquals(111L, instance1.getCustomData(),
                "JNI: Instance 1 custom data should be isolated");
            assertEquals(222L, instance2.getCustomData(),
                "JNI: Instance 2 custom data should be isolated");
            LOGGER.info("JNI: Custom data correctly isolated between instances");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WamrInstanceExtensions instance1 =
                     (WamrInstanceExtensions) module.instantiate();
                 final WamrInstanceExtensions instance2 =
                     (WamrInstanceExtensions) module.instantiate()) {

                instance1.setCustomData(111L);
                instance2.setCustomData(222L);

                assertEquals(111L, instance1.getCustomData(),
                    "Panama: Instance 1 custom data should be isolated");
                assertEquals(222L, instance2.getCustomData(),
                    "Panama: Instance 2 custom data should be isolated");
                LOGGER.info("Panama: Custom data correctly isolated between instances");
            }
        }
    }
}
