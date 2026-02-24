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
 * Integration tests for exception and execution control (Phase 8).
 *
 * <p>Tests exception get/set/clear, terminate, and instruction count limit.
 * All operations are tested for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class ExceptionExecutionControlTest {

    private static final Logger LOGGER =
        Logger.getLogger(ExceptionExecutionControlTest.class.getName());

    /**
     * Builds a minimal WASM module with a simple add function.
     */
    private byte[] buildSimpleModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Function: add(i32, i32) -> i32
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
    void testGetExceptionInitiallyNullParity() throws Exception {
        LOGGER.info("Testing getException() is initially null on both JNI and Panama");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final String jniException = instance.getException();
            LOGGER.info("JNI getException() initial: " + jniException);
            assertNull(jniException, "JNI: No exception should be set initially");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final String panamaException = instance.getException();
            LOGGER.info("Panama getException() initial: " + panamaException);
            assertNull(panamaException, "Panama: No exception should be set initially");
        }
    }

    @Test
    void testSetGetClearExceptionParity() throws Exception {
        LOGGER.info("Testing exception set/get/clear cycle on both JNI and Panama");

        final byte[] moduleBytes = buildSimpleModule();
        final String testMessage = "test exception from host";

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Set exception
            instance.setException(testMessage);
            final String jniSet = instance.getException();
            LOGGER.info("JNI getException() after set: " + jniSet);
            assertNotNull(jniSet, "JNI: Exception should be set");
            assertTrue(jniSet.contains(testMessage),
                "JNI: Exception message should contain our text, got: " + jniSet);

            // Clear exception
            instance.clearException();
            final String jniCleared = instance.getException();
            LOGGER.info("JNI getException() after clear: " + jniCleared);
            assertNull(jniCleared, "JNI: Exception should be null after clear");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Set exception
            instance.setException(testMessage);
            final String panamaSet = instance.getException();
            LOGGER.info("Panama getException() after set: " + panamaSet);
            assertNotNull(panamaSet, "Panama: Exception should be set");
            assertTrue(panamaSet.contains(testMessage),
                "Panama: Exception message should contain our text, got: " + panamaSet);

            // Clear exception
            instance.clearException();
            final String panamaCleared = instance.getException();
            LOGGER.info("Panama getException() after clear: " + panamaCleared);
            assertNull(panamaCleared, "Panama: Exception should be null after clear");
        }
    }

    @Test
    void testSetExceptionMultipleTimes() throws Exception {
        LOGGER.info("Testing multiple setException calls overwrite correctly");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            instance.setException("first exception");
            String jniEx = instance.getException();
            LOGGER.info("JNI first exception: " + jniEx);
            assertNotNull(jniEx, "JNI: First exception should be set");

            // Clear and set again
            instance.clearException();
            instance.setException("second exception");
            jniEx = instance.getException();
            LOGGER.info("JNI second exception: " + jniEx);
            assertNotNull(jniEx, "JNI: Second exception should be set");
            assertTrue(jniEx.contains("second exception"),
                "JNI: Should contain second exception text, got: " + jniEx);
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            instance.setException("first exception");
            String panamaEx = instance.getException();
            LOGGER.info("Panama first exception: " + panamaEx);
            assertNotNull(panamaEx, "Panama: First exception should be set");

            // Clear and set again
            instance.clearException();
            instance.setException("second exception");
            panamaEx = instance.getException();
            LOGGER.info("Panama second exception: " + panamaEx);
            assertNotNull(panamaEx, "Panama: Second exception should be set");
            assertTrue(panamaEx.contains("second exception"),
                "Panama: Should contain second exception text, got: " + panamaEx);
        }
    }

    @Test
    void testSetExceptionNullThrows() throws Exception {
        LOGGER.info("Testing setException(null) throws IllegalArgumentException");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.setException(null),
                "JNI: setException(null) should throw IllegalArgumentException");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.setException(null),
                "Panama: setException(null) should throw IllegalArgumentException");
        }
    }

    @Test
    void testTerminateParity() throws Exception {
        LOGGER.info("Testing terminate() does not crash on both JNI and Panama");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI — terminate and verify instance can still be closed cleanly
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Call function first to verify it works
            final WebAssemblyFunction add = instance.getFunction("add");
            final Object result = add.invoke(10, 20);
            LOGGER.info("JNI add(10, 20) before terminate: " + result);
            assertEquals(30, ((Number) result).intValue(), "JNI: add should work before terminate");

            // Terminate
            instance.terminate();
            LOGGER.info("JNI terminate() called successfully");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyFunction add = instance.getFunction("add");
            final Object result = add.invoke(10, 20);
            LOGGER.info("Panama add(10, 20) before terminate: " + result);
            assertEquals(30, ((Number) result).intValue(),
                "Panama: add should work before terminate");

            // Terminate
            instance.terminate();
            LOGGER.info("Panama terminate() called successfully");
        }
    }

    @Test
    void testSetInstructionCountLimitParity() throws Exception {
        LOGGER.info("Testing setInstructionCountLimit on both JNI and Panama");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI — set a high limit, function should still work
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Set a generous limit
            instance.setInstructionCountLimit(1000000);
            LOGGER.info("JNI setInstructionCountLimit(1000000) called");

            final WebAssemblyFunction add = instance.getFunction("add");
            final Object result = add.invoke(5, 7);
            LOGGER.info("JNI add(5, 7) with high limit: " + result);
            assertEquals(12, ((Number) result).intValue(),
                "JNI: add should work with high instruction limit");

            // Remove limit
            instance.setInstructionCountLimit(-1);
            LOGGER.info("JNI setInstructionCountLimit(-1) to remove limit");

            final Object result2 = add.invoke(100, 200);
            LOGGER.info("JNI add(100, 200) after removing limit: " + result2);
            assertEquals(300, ((Number) result2).intValue(),
                "JNI: add should work after removing limit");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            instance.setInstructionCountLimit(1000000);
            LOGGER.info("Panama setInstructionCountLimit(1000000) called");

            final WebAssemblyFunction add = instance.getFunction("add");
            final Object result = add.invoke(5, 7);
            LOGGER.info("Panama add(5, 7) with high limit: " + result);
            assertEquals(12, ((Number) result).intValue(),
                "Panama: add should work with high instruction limit");

            instance.setInstructionCountLimit(-1);
            LOGGER.info("Panama setInstructionCountLimit(-1) to remove limit");

            final Object result2 = add.invoke(100, 200);
            LOGGER.info("Panama add(100, 200) after removing limit: " + result2);
            assertEquals(300, ((Number) result2).intValue(),
                "Panama: add should work after removing limit");
        }
    }

    @Test
    void testExceptionAfterFunctionCallParity() throws Exception {
        LOGGER.info("Testing that function call clears any prior exception");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Set an exception
            instance.setException("pre-call exception");
            LOGGER.info("JNI set exception before call");

            // Clear it so we can call the function
            instance.clearException();
            final String afterClear = instance.getException();
            assertNull(afterClear, "JNI: Exception should be null after clear");

            // Now call function
            final WebAssemblyFunction add = instance.getFunction("add");
            final Object result = add.invoke(3, 4);
            LOGGER.info("JNI add(3, 4) after clearing exception: " + result);
            assertEquals(7, ((Number) result).intValue(),
                "JNI: Function should work after clearing exception");

            // No exception should be set after successful call
            final String afterCall = instance.getException();
            LOGGER.info("JNI getException() after successful call: " + afterCall);
            assertNull(afterCall, "JNI: No exception after successful call");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            instance.setException("pre-call exception");
            LOGGER.info("Panama set exception before call");

            instance.clearException();
            final String afterClear = instance.getException();
            assertNull(afterClear, "Panama: Exception should be null after clear");

            final WebAssemblyFunction add = instance.getFunction("add");
            final Object result = add.invoke(3, 4);
            LOGGER.info("Panama add(3, 4) after clearing exception: " + result);
            assertEquals(7, ((Number) result).intValue(),
                "Panama: Function should work after clearing exception");

            final String afterCall = instance.getException();
            LOGGER.info("Panama getException() after successful call: " + afterCall);
            assertNull(afterCall, "Panama: No exception after successful call");
        }
    }

    @Test
    void testClearExceptionWhenNoExceptionParity() throws Exception {
        LOGGER.info("Testing clearException() when no exception is set does not crash");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertNull(instance.getException(), "JNI: No exception initially");
            instance.clearException(); // Should not crash
            assertNull(instance.getException(), "JNI: Still no exception after clear");
            LOGGER.info("JNI clearException() with no exception: OK");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertNull(instance.getException(), "Panama: No exception initially");
            instance.clearException(); // Should not crash
            assertNull(instance.getException(), "Panama: Still no exception after clear");
            LOGGER.info("Panama clearException() with no exception: OK");
        }
    }

    @Test
    void testExceptionOnClosedInstanceThrows() throws Exception {
        LOGGER.info("Testing exception methods on closed instance throw IllegalStateException");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        final WebAssemblyRuntime jniRuntime = RuntimeFactory.createRuntime();
        final WebAssemblyModule jniModule = jniRuntime.compile(moduleBytes);
        final WebAssemblyInstance jniInstance = jniModule.instantiate();
        jniInstance.close();

        assertThrows(IllegalStateException.class,
            () -> jniInstance.getException(),
            "JNI: getException on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> jniInstance.setException("test"),
            "JNI: setException on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> jniInstance.clearException(),
            "JNI: clearException on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> jniInstance.terminate(),
            "JNI: terminate on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> jniInstance.setInstructionCountLimit(100),
            "JNI: setInstructionCountLimit on closed instance should throw");

        jniModule.close();
        jniRuntime.close();
        LOGGER.info("JNI closed instance exception handling: OK");

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        final WebAssemblyRuntime panamaRuntime = RuntimeFactory.createRuntime();
        final WebAssemblyModule panamaModule = panamaRuntime.compile(moduleBytes);
        final WebAssemblyInstance panamaInstance = panamaModule.instantiate();
        panamaInstance.close();

        assertThrows(IllegalStateException.class,
            () -> panamaInstance.getException(),
            "Panama: getException on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> panamaInstance.setException("test"),
            "Panama: setException on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> panamaInstance.clearException(),
            "Panama: clearException on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> panamaInstance.terminate(),
            "Panama: terminate on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> panamaInstance.setInstructionCountLimit(100),
            "Panama: setInstructionCountLimit on closed instance should throw");

        panamaModule.close();
        panamaRuntime.close();
        LOGGER.info("Panama closed instance exception handling: OK");
    }
}
