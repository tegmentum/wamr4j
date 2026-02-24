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
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for advanced instantiation and custom sections (Phase 13).
 *
 * <p>Tests instantiateEx with custom stack/heap/memory-page configuration
 * and getCustomSection for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class AdvancedInstantiationTest {

    private static final Logger LOGGER =
        Logger.getLogger(AdvancedInstantiationTest.class.getName());

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
    void testInstantiateExBasicParity() throws Exception {
        LOGGER.info("Testing instantiateEx with valid params on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiateEx(
                 16 * 1024, 16 * 1024 * 1024, 256)) {

            assertNotNull(instance, "JNI: instantiateEx should return a valid instance");
            LOGGER.info("JNI: instantiateEx succeeded");

            // Verify the instance is functional
            final WebAssemblyFunction addFn = instance.getFunction("add");
            assertNotNull(addFn, "JNI: should find 'add' function");
            final Object result = addFn.invoke(3, 4);
            assertEquals(7, ((Number) result).intValue(), "JNI: add(3,4) should return 7");
            LOGGER.info("JNI: Function call on instantiateEx instance works: add(3,4) = " + result);
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiateEx(
                 16 * 1024, 16 * 1024 * 1024, 256)) {

            assertNotNull(instance, "Panama: instantiateEx should return a valid instance");
            LOGGER.info("Panama: instantiateEx succeeded");

            final WebAssemblyFunction addFn = instance.getFunction("add");
            assertNotNull(addFn, "Panama: should find 'add' function");
            final Object result = addFn.invoke(3, 4);
            assertEquals(7, ((Number) result).intValue(), "Panama: add(3,4) should return 7");
            LOGGER.info("Panama: Function call on instantiateEx instance works: add(3,4) = " + result);
        }
    }

    @Test
    void testInstantiateExSmallStackParity() throws Exception {
        LOGGER.info("Testing instantiateEx with small stack size on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI - small stack
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiateEx(
                 4 * 1024, 4 * 1024 * 1024, 128)) {

            assertNotNull(instance, "JNI: instantiateEx with small stack should succeed");
            LOGGER.info("JNI: instantiateEx with 4KB stack succeeded");
        }

        // Panama - small stack
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiateEx(
                 4 * 1024, 4 * 1024 * 1024, 128)) {

            assertNotNull(instance, "Panama: instantiateEx with small stack should succeed");
            LOGGER.info("Panama: instantiateEx with 4KB stack succeeded");
        }
    }

    @Test
    void testInstantiateExNegativeParamsThrow() throws Exception {
        LOGGER.info("Testing instantiateEx with negative params throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertThrows(IllegalArgumentException.class,
                () -> module.instantiateEx(-1, 1024, 256),
                "JNI: negative stackSize should throw");
            assertThrows(IllegalArgumentException.class,
                () -> module.instantiateEx(1024, -1, 256),
                "JNI: negative heapSize should throw");
            assertThrows(IllegalArgumentException.class,
                () -> module.instantiateEx(1024, 1024, -1),
                "JNI: negative maxMemoryPages should throw");
            LOGGER.info("JNI: All negative param checks threw IllegalArgumentException");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertThrows(IllegalArgumentException.class,
                () -> module.instantiateEx(-1, 1024, 256),
                "Panama: negative stackSize should throw");
            assertThrows(IllegalArgumentException.class,
                () -> module.instantiateEx(1024, -1, 256),
                "Panama: negative heapSize should throw");
            assertThrows(IllegalArgumentException.class,
                () -> module.instantiateEx(1024, 1024, -1),
                "Panama: negative maxMemoryPages should throw");
            LOGGER.info("Panama: All negative param checks threw IllegalArgumentException");
        }
    }

    @Test
    void testInstantiateExOnClosedModuleThrows() throws Exception {
        LOGGER.info("Testing instantiateEx on closed module throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        final WebAssemblyRuntime jniRuntime = RuntimeFactory.createRuntime();
        final WebAssemblyModule jniModule = jniRuntime.compile(moduleBytes);
        jniModule.close();

        assertThrows(IllegalStateException.class,
            () -> jniModule.instantiateEx(16384, 16384, 256),
            "JNI: instantiateEx on closed module should throw");
        LOGGER.info("JNI: instantiateEx on closed module threw IllegalStateException");
        jniRuntime.close();

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        final WebAssemblyRuntime panamaRuntime = RuntimeFactory.createRuntime();
        final WebAssemblyModule panamaModule = panamaRuntime.compile(moduleBytes);
        panamaModule.close();

        assertThrows(IllegalStateException.class,
            () -> panamaModule.instantiateEx(16384, 16384, 256),
            "Panama: instantiateEx on closed module should throw");
        LOGGER.info("Panama: instantiateEx on closed module threw IllegalStateException");
        panamaRuntime.close();
    }

    @Test
    void testGetCustomSectionNonExistentParity() throws Exception {
        LOGGER.info("Testing getCustomSection for non-existent section on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            final byte[] jniResult = module.getCustomSection("nonexistent");
            LOGGER.info("JNI getCustomSection('nonexistent'): " + jniResult);
            assertNull(jniResult, "JNI: non-existent custom section should return null");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            final byte[] panamaResult = module.getCustomSection("nonexistent");
            LOGGER.info("Panama getCustomSection('nonexistent'): " + panamaResult);
            assertNull(panamaResult, "Panama: non-existent custom section should return null");
        }
    }

    @Test
    void testGetCustomSectionNullNameThrows() throws Exception {
        LOGGER.info("Testing getCustomSection with null name throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertThrows(IllegalArgumentException.class,
                () -> module.getCustomSection(null),
                "JNI: null name should throw IllegalArgumentException");
            LOGGER.info("JNI: null name getCustomSection threw IllegalArgumentException");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {

            assertThrows(IllegalArgumentException.class,
                () -> module.getCustomSection(null),
                "Panama: null name should throw IllegalArgumentException");
            LOGGER.info("Panama: null name getCustomSection threw IllegalArgumentException");
        }
    }

    @Test
    void testGetCustomSectionOnClosedModuleThrows() throws Exception {
        LOGGER.info("Testing getCustomSection on closed module throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        final WebAssemblyRuntime jniRuntime = RuntimeFactory.createRuntime();
        final WebAssemblyModule jniModule = jniRuntime.compile(moduleBytes);
        jniModule.close();

        assertThrows(IllegalStateException.class,
            () -> jniModule.getCustomSection("test"),
            "JNI: getCustomSection on closed module should throw");
        LOGGER.info("JNI: getCustomSection on closed module threw IllegalStateException");
        jniRuntime.close();

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        final WebAssemblyRuntime panamaRuntime = RuntimeFactory.createRuntime();
        final WebAssemblyModule panamaModule = panamaRuntime.compile(moduleBytes);
        panamaModule.close();

        assertThrows(IllegalStateException.class,
            () -> panamaModule.getCustomSection("test"),
            "Panama: getCustomSection on closed module should throw");
        LOGGER.info("Panama: getCustomSection on closed module threw IllegalStateException");
        panamaRuntime.close();
    }
}
