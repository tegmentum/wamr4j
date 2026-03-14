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
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for batch API operations: getGlobals, setGlobals, invokeMultiple.
 *
 * <p>Tests cover both JNI and Panama runtimes, verifying that batch operations
 * produce identical results to individual operations and handle errors correctly.
 *
 * @since 1.0.0
 */
class BatchOperationsTest {

    private static final Logger LOGGER = Logger.getLogger(BatchOperationsTest.class.getName());

    /**
     * Builds a WASM module with 4 mutable globals (one per type) and an add_i32 function.
     * Globals: g_i32=42, g_i64=100L, g_f32=3.14f, g_f64=2.718
     * Function: add_i32(i32, i32) -> i32
     */
    private byte[] buildBatchTestModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Globals: one per type, all mutable
        final int gI32 = builder.addGlobal(WasmModuleBuilder.I32, true, 42);
        final int gI64 = builder.addGlobal(WasmModuleBuilder.I64, true, 100);

        // For f32/f64, addGlobal expects Double.doubleToRawLongBits encoding
        final int gF32 = builder.addGlobal(WasmModuleBuilder.F32, true,
                Double.doubleToRawLongBits(3.14f));
        final int gF64 = builder.addGlobal(WasmModuleBuilder.F64, true,
                Double.doubleToRawLongBits(2.718));

        builder.addExport("g_i32", (byte) 0x03, gI32);
        builder.addExport("g_i64", (byte) 0x03, gI64);
        builder.addExport("g_f32", (byte) 0x03, gF32);
        builder.addExport("g_f64", (byte) 0x03, gF64);

        // add_i32(i32, i32) -> i32
        final int addType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int addFunc = builder.addFunction(addType);
        builder.addExport("add_i32", addFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_ADD,
        });

        return builder.build();
    }

    @Test
    void testBatchGetGlobalsAllTypes() throws Exception {
        LOGGER.info("Testing batch getGlobals with all 4 value types");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final Map<String, Object> globals = instance.getGlobals(
                    "g_i32", "g_i64", "g_f32", "g_f64");

                LOGGER.info(runtime.toUpperCase() + ": batch getGlobals returned " + globals);

                assertEquals(4, globals.size(), runtime + ": should return 4 globals");
                assertEquals(42, globals.get("g_i32"), runtime + ": g_i32 should be 42");
                assertEquals(100L, globals.get("g_i64"), runtime + ": g_i64 should be 100L");
                assertEquals(3.14f, (Float) globals.get("g_f32"), 0.001f,
                    runtime + ": g_f32 should be ~3.14");
                assertEquals(2.718, (Double) globals.get("g_f64"), 0.001,
                    runtime + ": g_f64 should be ~2.718");

                // Verify insertion order matches request order
                final String[] keys = globals.keySet().toArray(new String[0]);
                assertArrayEquals(new String[]{"g_i32", "g_i64", "g_f32", "g_f64"}, keys,
                    runtime + ": result order should match input order");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchSetGlobalsAllTypes() throws Exception {
        LOGGER.info("Testing batch setGlobals with all 4 value types");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final Map<String, Object> newValues = new LinkedHashMap<>();
                newValues.put("g_i32", 99);
                newValues.put("g_i64", 999L);
                newValues.put("g_f32", 1.5f);
                newValues.put("g_f64", 9.99);

                instance.setGlobals(newValues);

                // Verify each global was set correctly using individual getGlobal
                assertEquals(99, instance.getGlobal("g_i32"),
                    runtime + ": g_i32 should be 99 after batch set");
                assertEquals(999L, instance.getGlobal("g_i64"),
                    runtime + ": g_i64 should be 999L after batch set");
                assertEquals(1.5f, (Float) instance.getGlobal("g_f32"), 0.001f,
                    runtime + ": g_f32 should be 1.5 after batch set");
                assertEquals(9.99, (Double) instance.getGlobal("g_f64"), 0.001,
                    runtime + ": g_f64 should be 9.99 after batch set");

                LOGGER.info(runtime.toUpperCase() + ": batch setGlobals verified successfully");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchGetGlobalsEmpty() throws Exception {
        LOGGER.info("Testing batch getGlobals with empty array");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final Map<String, Object> result = instance.getGlobals();
                assertNotNull(result, runtime + ": empty batch should return non-null map");
                assertTrue(result.isEmpty(), runtime + ": empty batch should return empty map");

                LOGGER.info(runtime.toUpperCase() + ": empty batch getGlobals returned empty map");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchSetGlobalsEmpty() throws Exception {
        LOGGER.info("Testing batch setGlobals with empty map");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                // Should be a no-op, not throw
                instance.setGlobals(new LinkedHashMap<>());

                // Original values unchanged
                assertEquals(42, instance.getGlobal("g_i32"),
                    runtime + ": g_i32 unchanged after empty batch set");

                LOGGER.info(runtime.toUpperCase() + ": empty batch setGlobals was no-op");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchGetGlobalsSingleElement() throws Exception {
        LOGGER.info("Testing batch getGlobals with single element");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final Map<String, Object> result = instance.getGlobals("g_i32");
                assertEquals(1, result.size(), runtime + ": single-element batch should have 1 entry");
                assertEquals(42, result.get("g_i32"), runtime + ": g_i32 should be 42");

                LOGGER.info(runtime.toUpperCase() + ": single-element batch getGlobals = " + result);
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchGetGlobalsNonExistentNameThrows() throws Exception {
        LOGGER.info("Testing batch getGlobals with non-existent global name");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final Exception ex = assertThrows(WasmRuntimeException.class,
                    () -> instance.getGlobals("g_i32", "nonexistent", "g_i64"),
                    runtime + ": should throw for non-existent global in batch");
                LOGGER.info(runtime.toUpperCase() + ": batch getGlobals error: " + ex.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchSetGlobalsNonExistentNameThrows() throws Exception {
        LOGGER.info("Testing batch setGlobals with non-existent global name");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final Map<String, Object> values = new LinkedHashMap<>();
                values.put("g_i32", 10);
                values.put("nonexistent", 20);

                final Exception ex = assertThrows(WasmRuntimeException.class,
                    () -> instance.setGlobals(values),
                    runtime + ": should throw for non-existent global in batch set");
                LOGGER.info(runtime.toUpperCase() + ": batch setGlobals error: " + ex.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchGetGlobalsNullArgThrows() throws Exception {
        LOGGER.info("Testing batch getGlobals with null argument");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                assertThrows(IllegalArgumentException.class,
                    () -> instance.getGlobals((String[]) null),
                    runtime + ": should throw for null global names array");

                LOGGER.info(runtime.toUpperCase() + ": null arg correctly rejected");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchSetGlobalsNullArgThrows() throws Exception {
        LOGGER.info("Testing batch setGlobals with null argument");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                assertThrows(IllegalArgumentException.class,
                    () -> instance.setGlobals(null),
                    runtime + ": should throw for null globals map");

                LOGGER.info(runtime.toUpperCase() + ": null arg correctly rejected");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchGetThenSetRoundTrip() throws Exception {
        LOGGER.info("Testing batch get -> modify -> batch set -> batch get roundtrip");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                // Read originals
                final Map<String, Object> originals = instance.getGlobals(
                    "g_i32", "g_i64", "g_f32", "g_f64");
                LOGGER.info(runtime.toUpperCase() + ": originals = " + originals);

                // Modify all
                final Map<String, Object> modified = new LinkedHashMap<>();
                modified.put("g_i32", ((Integer) originals.get("g_i32")) + 1);
                modified.put("g_i64", ((Long) originals.get("g_i64")) + 1L);
                modified.put("g_f32", ((Float) originals.get("g_f32")) + 1.0f);
                modified.put("g_f64", ((Double) originals.get("g_f64")) + 1.0);
                instance.setGlobals(modified);

                // Read back
                final Map<String, Object> readBack = instance.getGlobals(
                    "g_i32", "g_i64", "g_f32", "g_f64");
                LOGGER.info(runtime.toUpperCase() + ": readBack = " + readBack);

                assertEquals(43, readBack.get("g_i32"), runtime + ": g_i32 incremented");
                assertEquals(101L, readBack.get("g_i64"), runtime + ": g_i64 incremented");
                assertEquals(4.14f, (Float) readBack.get("g_f32"), 0.01f,
                    runtime + ": g_f32 incremented");
                assertEquals(3.718, (Double) readBack.get("g_f64"), 0.01,
                    runtime + ": g_f64 incremented");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInvokeMultipleBasic() throws Exception {
        LOGGER.info("Testing invokeMultiple with add_i32 function");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final WebAssemblyFunction addFn = instance.getFunction("add_i32");

                final Object[] results = addFn.invokeMultiple(
                    new Object[]{1, 2},
                    new Object[]{10, 20},
                    new Object[]{100, 200},
                    new Object[]{-5, 10}
                );

                LOGGER.info(runtime.toUpperCase() + ": invokeMultiple results count = " + results.length);

                assertEquals(4, results.length, runtime + ": should return 4 results");
                assertEquals(3, results[0], runtime + ": 1+2=3");
                assertEquals(30, results[1], runtime + ": 10+20=30");
                assertEquals(300, results[2], runtime + ": 100+200=300");
                assertEquals(5, results[3], runtime + ": -5+10=5");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInvokeMultipleEmpty() throws Exception {
        LOGGER.info("Testing invokeMultiple with empty arg sets");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final WebAssemblyFunction addFn = instance.getFunction("add_i32");
                final Object[] results = addFn.invokeMultiple();

                assertNotNull(results, runtime + ": empty invokeMultiple should return non-null");
                assertEquals(0, results.length, runtime + ": empty invokeMultiple should return empty array");

                LOGGER.info(runtime.toUpperCase() + ": empty invokeMultiple returned empty array");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInvokeMultipleSingleCall() throws Exception {
        LOGGER.info("Testing invokeMultiple with single invocation");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final WebAssemblyFunction addFn = instance.getFunction("add_i32");
                final Object[] results = addFn.invokeMultiple(new Object[]{7, 8});

                assertEquals(1, results.length, runtime + ": single invokeMultiple should have 1 result");
                assertEquals(15, results[0], runtime + ": 7+8=15");

                LOGGER.info(runtime.toUpperCase() + ": single invokeMultiple = " + results[0]);
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInvokeMultipleNullArgThrows() throws Exception {
        LOGGER.info("Testing invokeMultiple with null argument");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final WebAssemblyFunction addFn = instance.getFunction("add_i32");

                assertThrows(IllegalArgumentException.class,
                    () -> addFn.invokeMultiple((Object[][]) null),
                    runtime + ": should throw for null arg sets");

                LOGGER.info(runtime.toUpperCase() + ": null arg correctly rejected");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testBatchGetGlobalsMatchesIndividual() throws Exception {
        LOGGER.info("Testing that batch getGlobals matches individual getGlobal results");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                // Individual reads
                final Object i32 = instance.getGlobal("g_i32");
                final Object i64 = instance.getGlobal("g_i64");
                final Object f32 = instance.getGlobal("g_f32");
                final Object f64 = instance.getGlobal("g_f64");

                // Batch read
                final Map<String, Object> batch = instance.getGlobals(
                    "g_i32", "g_i64", "g_f32", "g_f64");

                assertEquals(i32, batch.get("g_i32"), runtime + ": batch g_i32 matches individual");
                assertEquals(i64, batch.get("g_i64"), runtime + ": batch g_i64 matches individual");
                assertEquals(f32, batch.get("g_f32"), runtime + ": batch g_f32 matches individual");
                assertEquals(f64, batch.get("g_f64"), runtime + ": batch g_f64 matches individual");

                LOGGER.info(runtime.toUpperCase() + ": batch results match individual results");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInvokeMultipleMatchesIndividual() throws Exception {
        LOGGER.info("Testing that invokeMultiple matches individual invoke results");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(buildBatchTestModule());
                 final WebAssemblyInstance instance = module.instantiate()) {

                final WebAssemblyFunction addFn = instance.getFunction("add_i32");

                // Individual invokes
                final Object r1 = addFn.invoke(1, 2);
                final Object r2 = addFn.invoke(10, 20);
                final Object r3 = addFn.invoke(100, 200);

                // Batch invoke
                final Object[] batch = addFn.invokeMultiple(
                    new Object[]{1, 2},
                    new Object[]{10, 20},
                    new Object[]{100, 200}
                );

                assertEquals(r1, batch[0], runtime + ": batch[0] matches individual invoke");
                assertEquals(r2, batch[1], runtime + ": batch[1] matches individual invoke");
                assertEquals(r3, batch[2], runtime + ": batch[2] matches individual invoke");

                LOGGER.info(runtime.toUpperCase() + ": invokeMultiple results match individual invokes");
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }
}
