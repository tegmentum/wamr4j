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
import ai.tegmentum.wamr4j.WamrInstanceExtensions;
import ai.tegmentum.wamr4j.WamrRuntimeExtensions;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for debugging and profiling APIs (Phase 11).
 *
 * <p>Tests call stack retrieval, performance profiling, and memory consumption
 * dump methods for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class DebuggingProfilingTest {

    private static final Logger LOGGER =
        Logger.getLogger(DebuggingProfilingTest.class.getName());

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
    void testGetCallStackReturnsParity() throws Exception {
        LOGGER.info("Testing getCallStack() on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI — call stack outside of execution should be null or empty
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final String jniCallStack = instance.getCallStack();
            LOGGER.info("JNI getCallStack() outside execution: "
                + (jniCallStack == null ? "null" : "'" + jniCallStack + "'"));
            // Outside of active execution, call stack may be null or empty
            assertTrue(jniCallStack == null || jniCallStack.isEmpty(),
                "JNI: Call stack outside execution should be null or empty");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final String panamaCallStack = instance.getCallStack();
            LOGGER.info("Panama getCallStack() outside execution: "
                + (panamaCallStack == null ? "null" : "'" + panamaCallStack + "'"));
            assertTrue(panamaCallStack == null || panamaCallStack.isEmpty(),
                "Panama: Call stack outside execution should be null or empty");
        }
    }

    @Test
    void testDumpCallStackDoesNotCrashParity() throws Exception {
        LOGGER.info("Testing dumpCallStack() does not crash on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertDoesNotThrow(() -> instance.dumpCallStack(),
                "JNI: dumpCallStack should not throw");
            LOGGER.info("JNI: dumpCallStack completed without crash");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertDoesNotThrow(() -> instance.dumpCallStack(),
                "Panama: dumpCallStack should not throw");
            LOGGER.info("Panama: dumpCallStack completed without crash");
        }
    }

    @Test
    void testDumpPerfProfilingDoesNotCrashParity() throws Exception {
        LOGGER.info("Testing dumpPerfProfiling() does not crash on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final WebAssemblyFunction addFunc = instance.getFunction("add");
            addFunc.invoke(1, 2); // Execute something first

            assertDoesNotThrow(() -> instance.dumpPerfProfiling(),
                "JNI: dumpPerfProfiling should not throw");
            LOGGER.info("JNI: dumpPerfProfiling completed without crash");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final WebAssemblyFunction addFunc = instance.getFunction("add");
            addFunc.invoke(1, 2);

            assertDoesNotThrow(() -> instance.dumpPerfProfiling(),
                "Panama: dumpPerfProfiling should not throw");
            LOGGER.info("Panama: dumpPerfProfiling completed without crash");
        }
    }

    @Test
    void testSumWasmExecTimeParity() throws Exception {
        LOGGER.info("Testing sumWasmExecTime() on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            // Before any execution
            final double jniBefore = instance.sumWasmExecTime();
            LOGGER.info("JNI sumWasmExecTime() before execution: " + jniBefore);
            assertTrue(jniBefore >= 0.0, "JNI: Exec time should be >= 0");

            // After execution
            final WebAssemblyFunction addFunc = instance.getFunction("add");
            for (int i = 0; i < 100; i++) {
                addFunc.invoke(i, i + 1);
            }
            final double jniAfter = instance.sumWasmExecTime();
            LOGGER.info("JNI sumWasmExecTime() after 100 calls: " + jniAfter);
            assertTrue(jniAfter >= 0.0, "JNI: Exec time after calls should be >= 0");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final double panamaBefore = instance.sumWasmExecTime();
            LOGGER.info("Panama sumWasmExecTime() before execution: " + panamaBefore);
            assertTrue(panamaBefore >= 0.0, "Panama: Exec time should be >= 0");

            final WebAssemblyFunction addFunc = instance.getFunction("add");
            for (int i = 0; i < 100; i++) {
                addFunc.invoke(i, i + 1);
            }
            final double panamaAfter = instance.sumWasmExecTime();
            LOGGER.info("Panama sumWasmExecTime() after 100 calls: " + panamaAfter);
            assertTrue(panamaAfter >= 0.0, "Panama: Exec time after calls should be >= 0");
        }
    }

    @Test
    void testGetWasmFuncExecTimeParity() throws Exception {
        LOGGER.info("Testing getWasmFuncExecTime() on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final WebAssemblyFunction addFunc = instance.getFunction("add");
            addFunc.invoke(1, 2);

            final double jniTime = instance.getWasmFuncExecTime("add");
            LOGGER.info("JNI getWasmFuncExecTime('add'): " + jniTime);
            assertTrue(jniTime >= 0.0, "JNI: Function exec time should be >= 0");

            // Non-existent function returns -1.0 from WAMR
            final double jniNone = instance.getWasmFuncExecTime("nonexistent");
            LOGGER.info("JNI getWasmFuncExecTime('nonexistent'): " + jniNone);
            assertTrue(jniNone <= 0.0,
                "JNI: Non-existent function exec time should be <= 0, got: " + jniNone);
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            final WebAssemblyFunction addFunc = instance.getFunction("add");
            addFunc.invoke(1, 2);

            final double panamaTime = instance.getWasmFuncExecTime("add");
            LOGGER.info("Panama getWasmFuncExecTime('add'): " + panamaTime);
            assertTrue(panamaTime >= 0.0, "Panama: Function exec time should be >= 0");

            // Non-existent function returns -1.0 from WAMR
            final double panamaNone = instance.getWasmFuncExecTime("nonexistent");
            LOGGER.info("Panama getWasmFuncExecTime('nonexistent'): " + panamaNone);
            assertTrue(panamaNone <= 0.0,
                "Panama: Non-existent function exec time should be <= 0, got: "
                    + panamaNone);
        }
    }

    @Test
    void testGetWasmFuncExecTimeNullNameThrows() throws Exception {
        LOGGER.info("Testing getWasmFuncExecTime(null) throws on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.getWasmFuncExecTime(null),
                "JNI: getWasmFuncExecTime(null) should throw");
            LOGGER.info("JNI: null funcName correctly threw IllegalArgumentException");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.getWasmFuncExecTime(null),
                "Panama: getWasmFuncExecTime(null) should throw");
            LOGGER.info("Panama: null funcName correctly threw IllegalArgumentException");
        }
    }

    @Test
    void testDumpMemConsumptionDoesNotCrashParity() throws Exception {
        LOGGER.info("Testing dumpMemConsumption() does not crash on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertDoesNotThrow(() -> instance.dumpMemConsumption(),
                "JNI: dumpMemConsumption should not throw");
            LOGGER.info("JNI: dumpMemConsumption completed without crash");
        }

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WamrInstanceExtensions instance =
                 (WamrInstanceExtensions) module.instantiate()) {

            assertDoesNotThrow(() -> instance.dumpMemConsumption(),
                "Panama: dumpMemConsumption should not throw");
            LOGGER.info("Panama: dumpMemConsumption completed without crash");
        }
    }

    @Test
    void testDebuggingMethodsOnClosedInstanceThrow() throws Exception {
        LOGGER.info("Testing debugging methods on closed instance throw on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        final WamrInstanceExtensions jniInstance;
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {
            jniInstance = (WamrInstanceExtensions) module.instantiate();
            jniInstance.close();
        }

        assertThrows(IllegalStateException.class, () -> jniInstance.getCallStack(),
            "JNI: getCallStack on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> jniInstance.dumpCallStack(),
            "JNI: dumpCallStack on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> jniInstance.dumpPerfProfiling(),
            "JNI: dumpPerfProfiling on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> jniInstance.sumWasmExecTime(),
            "JNI: sumWasmExecTime on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> jniInstance.getWasmFuncExecTime("add"),
            "JNI: getWasmFuncExecTime on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> jniInstance.dumpMemConsumption(),
            "JNI: dumpMemConsumption on closed instance should throw");
        LOGGER.info("JNI: All closed instance methods correctly threw IllegalStateException");

        // Panama
        System.setProperty("wamr4j.runtime", "panama");
        final WamrInstanceExtensions panamaInstance;
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes)) {
            panamaInstance = (WamrInstanceExtensions) module.instantiate();
            panamaInstance.close();
        }

        assertThrows(IllegalStateException.class, () -> panamaInstance.getCallStack(),
            "Panama: getCallStack on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> panamaInstance.dumpCallStack(),
            "Panama: dumpCallStack on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> panamaInstance.dumpPerfProfiling(),
            "Panama: dumpPerfProfiling on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> panamaInstance.sumWasmExecTime(),
            "Panama: sumWasmExecTime on closed instance should throw");
        assertThrows(IllegalStateException.class,
            () -> panamaInstance.getWasmFuncExecTime("add"),
            "Panama: getWasmFuncExecTime on closed instance should throw");
        assertThrows(IllegalStateException.class, () -> panamaInstance.dumpMemConsumption(),
            "Panama: dumpMemConsumption on closed instance should throw");
        LOGGER.info(
            "Panama: All closed instance methods correctly threw IllegalStateException");
    }

    @Test
    void testCopyCallstackParity() throws Exception {
        LOGGER.info("Testing copyCallstack() on both runtimes");

        final byte[] moduleBytes = buildSimpleModule();

        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(moduleBytes);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                // Call a function first so there has been some execution
                final WebAssemblyFunction addFunc = instance.getFunction("add");
                addFunc.invoke(1, 2);

                // copyCallstack outside of active execution may return empty or null
                final int[][] frames = instance.copyCallstack(10, 0);
                LOGGER.info(runtime.toUpperCase() + ": copyCallstack(10, 0) = "
                    + (frames != null ? "int[" + frames.length + "][]" : "null"));

                // Should not crash and should return either null or a valid array
                if (frames != null) {
                    assertTrue(frames.length >= 0,
                        runtime.toUpperCase() + ": frames length should be non-negative");
                    for (int i = 0; i < frames.length; i++) {
                        assertNotNull(frames[i],
                            runtime.toUpperCase() + ": frame " + i + " should not be null");
                        LOGGER.info(runtime.toUpperCase() + ": frame[" + i + "] length="
                            + frames[i].length);
                    }
                }
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testGetLastErrorParity() throws Exception {
        LOGGER.info("Testing getLastError() on both runtimes");

        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                final String lastError = ext.getLastError();
                LOGGER.info(runtime.toUpperCase() + ": getLastError() = "
                    + (lastError == null ? "null" : "'" + lastError + "'"));

                // No error expected on a fresh runtime
                assertTrue(lastError == null || lastError.isEmpty(),
                    runtime.toUpperCase()
                        + ": getLastError() on fresh runtime should be null or empty, got: '"
                        + lastError + "'");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }
}
