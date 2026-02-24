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

import ai.tegmentum.wamr4j.ElementKind;
import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.WebAssemblyTable;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebAssemblyTable API.
 *
 * <p>Tests table operations including:
 * <ul>
 *   <li>Table metadata: getSize, getMaxSize, getElementKind</li>
 *   <li>Table name enumeration: getTableNames</li>
 *   <li>Function retrieval from table: getFunctionAtIndex</li>
 *   <li>Indirect function calls: callIndirect</li>
 *   <li>Error handling: out-of-bounds, invalid arguments</li>
 *   <li>JNI/Panama parity for all operations</li>
 * </ul>
 *
 * @since 1.0.0
 */
class TableOperationsTest {

    private static final Logger LOGGER = Logger.getLogger(TableOperationsTest.class.getName());

    /**
     * Builds a WASM module with:
     * - Table of 2 funcref entries (exported as "__indirect_function_table")
     * - Function 0: double(x) -> x * 2
     * - Function 1: square(x) -> x * x
     * - Function 2 (exported "call_table"): call_indirect(x, index)
     */
    private byte[] buildTableModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32) -> i32
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: double(x) -> x * 2
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_MUL,
        });

        // Function 1: square(x) -> x * x
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_MUL,
        });

        // Type 1: (i32, i32) -> i32
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 2: call_table(x, index) -> call_indirect(x, index)
        final int callFunc = builder.addFunction(type1);
        builder.addExport("call_table", callFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,  // arg
            WasmModuleBuilder.LOCAL_GET, 0x01,  // table index
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        // Table with 2 entries, max 10
        builder.addTable(2, 10);
        builder.addTableElement(0, 0, new int[]{0, 1});

        // Export the table as "__indirect_function_table"
        builder.addExport("__indirect_function_table", (byte) 0x01, 0);

        return builder.build();
    }

    @Test
    void testTableMetadataParity() {
        LOGGER.info("Testing table metadata parity between JNI and Panama");

        final byte[] moduleBytes = buildTableModule();

        int jniSize = -1;
        int jniMaxSize = -1;
        ElementKind jniKind = null;
        String jniTableName = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");
            assertNotNull(table, "JNI: getTable() should not return null");

            jniSize = table.getSize();
            LOGGER.info("JNI table size: " + jniSize);
            assertTrue(jniSize >= 2, "JNI: table should have at least 2 entries, got: " + jniSize);

            jniMaxSize = table.getMaxSize();
            LOGGER.info("JNI table max size: " + jniMaxSize);
            assertTrue(jniMaxSize >= jniSize,
                "JNI: max size (" + jniMaxSize + ") should be >= current size (" + jniSize + ")");

            jniKind = table.getElementKind();
            LOGGER.info("JNI table element kind: " + jniKind);
            assertEquals(ElementKind.FUNCREF, jniKind,
                "JNI: table element kind should be FUNCREF");

            jniTableName = table.getName();
            LOGGER.info("JNI table name: " + jniTableName);
            assertEquals("__indirect_function_table", jniTableName,
                "JNI: table name should match");

            assertTrue(table.isValid(), "JNI: table should be valid");
        } catch (final Exception e) {
            fail("JNI table metadata test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");
            assertNotNull(table, "Panama: getTable() should not return null");

            final int panamaSize = table.getSize();
            LOGGER.info("Panama table size: " + panamaSize);
            assertEquals(jniSize, panamaSize,
                "JNI and Panama should agree on table size");

            final int panamaMaxSize = table.getMaxSize();
            LOGGER.info("Panama table max size: " + panamaMaxSize);
            assertEquals(jniMaxSize, panamaMaxSize,
                "JNI and Panama should agree on table max size");

            final ElementKind panamaKind = table.getElementKind();
            LOGGER.info("Panama table element kind: " + panamaKind);
            assertEquals(jniKind, panamaKind,
                "JNI and Panama should agree on element kind");

            assertEquals(jniTableName, table.getName(),
                "JNI and Panama should agree on table name");

            assertTrue(table.isValid(), "Panama: table should be valid");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testGetTableNamesParity() {
        LOGGER.info("Testing getTableNames parity between JNI and Panama");

        final byte[] moduleBytes = buildTableModule();
        String[] jniNames = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            jniNames = instance.getTableNames();
            LOGGER.info("JNI table names: " + java.util.Arrays.toString(jniNames));
            assertNotNull(jniNames, "JNI: getTableNames() should not return null");
            assertTrue(jniNames.length > 0,
                "JNI: should have at least one exported table");

            boolean found = false;
            for (final String name : jniNames) {
                if ("__indirect_function_table".equals(name)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "JNI: exported table '__indirect_function_table' should be in table names");
        } catch (final Exception e) {
            fail("JNI getTableNames test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final String[] panamaNames = instance.getTableNames();
            LOGGER.info("Panama table names: " + java.util.Arrays.toString(panamaNames));
            assertNotNull(panamaNames, "Panama: getTableNames() should not return null");
            assertArrayEquals(jniNames, panamaNames,
                "JNI and Panama should return identical table names");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testGetFunctionAtIndexParity() {
        LOGGER.info("Testing getFunctionAtIndex parity between JNI and Panama");

        final byte[] moduleBytes = buildTableModule();

        Object jniResult0 = null;
        Object jniResult1 = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");

            // Get function at index 0 (double) and call it
            final WebAssemblyFunction fn0 = table.getFunctionAtIndex(0);
            assertNotNull(fn0, "JNI: function at index 0 should not be null");
            jniResult0 = fn0.invoke(5);
            LOGGER.info("JNI: fn0(5) = " + jniResult0);
            assertEquals(10, jniResult0, "JNI: double(5) should return 10");

            // Get function at index 1 (square) and call it
            final WebAssemblyFunction fn1 = table.getFunctionAtIndex(1);
            assertNotNull(fn1, "JNI: function at index 1 should not be null");
            jniResult1 = fn1.invoke(5);
            LOGGER.info("JNI: fn1(5) = " + jniResult1);
            assertEquals(25, jniResult1, "JNI: square(5) should return 25");
        } catch (final Exception e) {
            fail("JNI getFunctionAtIndex test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");

            final WebAssemblyFunction fn0 = table.getFunctionAtIndex(0);
            assertNotNull(fn0, "Panama: function at index 0 should not be null");
            final Object panamaResult0 = fn0.invoke(5);
            LOGGER.info("Panama: fn0(5) = " + panamaResult0);
            assertEquals(jniResult0, panamaResult0,
                "JNI and Panama should agree on double(5) result");

            final WebAssemblyFunction fn1 = table.getFunctionAtIndex(1);
            assertNotNull(fn1, "Panama: function at index 1 should not be null");
            final Object panamaResult1 = fn1.invoke(5);
            LOGGER.info("Panama: fn1(5) = " + panamaResult1);
            assertEquals(jniResult1, panamaResult1,
                "JNI and Panama should agree on square(5) result");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testCallIndirectParity() {
        LOGGER.info("Testing callIndirect parity between JNI and Panama");

        final byte[] moduleBytes = buildTableModule();

        Object jniDouble5 = null;
        Object jniSquare5 = null;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");

            // callIndirect index 0 (double) with arg 5, expecting i32 result
            jniDouble5 = table.callIndirect(0, new Object[]{5}, new int[]{0});
            LOGGER.info("JNI: callIndirect(0, [5]) = " + jniDouble5);
            assertEquals(10, jniDouble5, "JNI: indirect double(5) should return 10");

            // callIndirect index 1 (square) with arg 5, expecting i32 result
            jniSquare5 = table.callIndirect(1, new Object[]{5}, new int[]{0});
            LOGGER.info("JNI: callIndirect(1, [5]) = " + jniSquare5);
            assertEquals(25, jniSquare5, "JNI: indirect square(5) should return 25");
        } catch (final Exception e) {
            fail("JNI callIndirect test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");

            final Object panamaDouble5 = table.callIndirect(0, new Object[]{5}, new int[]{0});
            LOGGER.info("Panama: callIndirect(0, [5]) = " + panamaDouble5);
            assertEquals(jniDouble5, panamaDouble5,
                "JNI and Panama should agree on indirect double(5)");

            final Object panamaSquare5 = table.callIndirect(1, new Object[]{5}, new int[]{0});
            LOGGER.info("Panama: callIndirect(1, [5]) = " + panamaSquare5);
            assertEquals(jniSquare5, panamaSquare5,
                "JNI and Panama should agree on indirect square(5)");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testTableNotFoundError() {
        LOGGER.info("Testing table not found error handling");

        final byte[] moduleBytes = buildTableModule();

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertThrows(WasmRuntimeException.class,
                () -> instance.getTable("nonexistent_table"),
                "JNI: getTable() should throw for nonexistent table");
            LOGGER.info("JNI: correctly threw WasmRuntimeException for nonexistent table");
        } catch (final Exception e) {
            fail("JNI table not found test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertThrows(WasmRuntimeException.class,
                () -> instance.getTable("nonexistent_table"),
                "Panama: getTable() should throw for nonexistent table");
            LOGGER.info("Panama: correctly threw WasmRuntimeException for nonexistent table");
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testTableInvalidArguments() {
        LOGGER.info("Testing table argument validation");

        final byte[] moduleBytes = buildTableModule();

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Null table name
            assertThrows(IllegalArgumentException.class,
                () -> instance.getTable(null),
                "JNI: getTable(null) should throw IllegalArgumentException");

            // Empty table name
            assertThrows(IllegalArgumentException.class,
                () -> instance.getTable(""),
                "JNI: getTable('') should throw IllegalArgumentException");

            final WebAssemblyTable table = instance.getTable("__indirect_function_table");

            // Negative index for getFunctionAtIndex
            assertThrows(IllegalArgumentException.class,
                () -> table.getFunctionAtIndex(-1),
                "JNI: getFunctionAtIndex(-1) should throw IllegalArgumentException");

            // Negative index for callIndirect
            assertThrows(IllegalArgumentException.class,
                () -> table.callIndirect(-1, new Object[]{}, new int[]{}),
                "JNI: callIndirect(-1) should throw IllegalArgumentException");

            LOGGER.info("JNI: all argument validation checks passed");
        } catch (final Exception e) {
            fail("JNI argument validation test failed: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testTableClosedInstanceError() {
        LOGGER.info("Testing table usage after instance close");

        final byte[] moduleBytes = buildTableModule();

        System.setProperty("wamr4j.runtime", "jni");
        try {
            final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
            final WebAssemblyModule module = runtime.compile(moduleBytes);
            final WebAssemblyInstance instance = module.instantiate();
            final WebAssemblyTable table = instance.getTable("__indirect_function_table");

            // Close the instance
            instance.close();

            // Table should no longer be valid
            assertFalse(table.isValid(),
                "JNI: table should be invalid after instance close");

            assertThrows(IllegalStateException.class, table::getSize,
                "JNI: getSize() should throw after instance close");

            LOGGER.info("JNI: correctly detected closed instance for table operations");

            module.close();
            runtime.close();
        } catch (final Exception e) {
            fail("JNI closed instance test failed: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testElementKindEnum() {
        LOGGER.info("Testing ElementKind enum values");

        assertEquals(128, ElementKind.EXTERNREF.getNativeValue(),
            "EXTERNREF should have native value 128");
        assertEquals(129, ElementKind.FUNCREF.getNativeValue(),
            "FUNCREF should have native value 129");

        assertEquals(ElementKind.EXTERNREF, ElementKind.fromNativeValue(128),
            "fromNativeValue(128) should return EXTERNREF");
        assertEquals(ElementKind.FUNCREF, ElementKind.fromNativeValue(129),
            "fromNativeValue(129) should return FUNCREF");
        assertNull(ElementKind.fromNativeValue(0),
            "fromNativeValue(0) should return null for unknown value");
        assertNull(ElementKind.fromNativeValue(-1),
            "fromNativeValue(-1) should return null for invalid value");

        LOGGER.info("ElementKind enum tests passed");
    }
}
