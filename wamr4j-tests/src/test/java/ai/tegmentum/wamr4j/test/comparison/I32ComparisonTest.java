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

package ai.tegmentum.wamr4j.test.comparison;

import ai.tegmentum.wamr4j.test.framework.ComparisonTestRunner;
import ai.tegmentum.wamr4j.test.framework.TestAssertion;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comparison tests for i32 comparison operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i32 comparison operations (eq, ne, lt, gt, le, ge).
 *
 * <p><b>Note:</b> WebAssembly comparison operations return i32 values
 * (0 for false, 1 for true) rather than boolean types.
 *
 * @since 1.0.0
 */
class I32ComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i32 comparison tests");
    }

    @Test
    void testI32Eq() {
        final byte[] module = createComparisonModule("eq", WasmModuleBuilder.I32_EQ);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{5, 5},
            new Object[]{1},
            "5 == 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{5, 6},
            new Object[]{0},
            "5 == 6 is false"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{0, 0},
            new Object[]{1},
            "0 == 0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{-5, -5},
            new Object[]{1},
            "-5 == -5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{-5, 5},
            new Object[]{0},
            "-5 == 5 is false"));

        runAndCompare(module);
    }

    @Test
    void testI32Ne() {
        final byte[] module = createComparisonModule("ne", WasmModuleBuilder.I32_NE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{5, 5},
            new Object[]{0},
            "5 != 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{5, 6},
            new Object[]{1},
            "5 != 6 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{0, 0},
            new Object[]{0},
            "0 != 0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{-5, 5},
            new Object[]{1},
            "-5 != 5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32LtS() {
        final byte[] module = createComparisonModule("lt_s", WasmModuleBuilder.I32_LT_S);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("lt_s",
            new Object[]{5, 10},
            new Object[]{1},
            "5 < 10 is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt_s",
            new Object[]{10, 5},
            new Object[]{0},
            "10 < 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt_s",
            new Object[]{5, 5},
            new Object[]{0},
            "5 < 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt_s",
            new Object[]{-10, -5},
            new Object[]{1},
            "-10 < -5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt_s",
            new Object[]{-5, 5},
            new Object[]{1},
            "-5 < 5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32LtU() {
        final byte[] module = createComparisonModule("lt_u", WasmModuleBuilder.I32_LT_U);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("lt_u",
            new Object[]{5, 10},
            new Object[]{1},
            "5 < 10 is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt_u",
            new Object[]{10, 5},
            new Object[]{0},
            "10 < 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt_u",
            new Object[]{5, 5},
            new Object[]{0},
            "5 < 5 is false"));

        // Unsigned comparison treats -1 as large positive
        runner.addAssertion(TestAssertion.assertReturn("lt_u",
            new Object[]{-1, 5},
            new Object[]{0},
            "-1 (unsigned) < 5 is false"));

        runAndCompare(module);
    }

    @Test
    void testI32GtS() {
        final byte[] module = createComparisonModule("gt_s", WasmModuleBuilder.I32_GT_S);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("gt_s",
            new Object[]{10, 5},
            new Object[]{1},
            "10 > 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("gt_s",
            new Object[]{5, 10},
            new Object[]{0},
            "5 > 10 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt_s",
            new Object[]{5, 5},
            new Object[]{0},
            "5 > 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt_s",
            new Object[]{-5, -10},
            new Object[]{1},
            "-5 > -10 is true"));

        runner.addAssertion(TestAssertion.assertReturn("gt_s",
            new Object[]{5, -5},
            new Object[]{1},
            "5 > -5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32GtU() {
        final byte[] module = createComparisonModule("gt_u", WasmModuleBuilder.I32_GT_U);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("gt_u",
            new Object[]{10, 5},
            new Object[]{1},
            "10 > 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("gt_u",
            new Object[]{5, 10},
            new Object[]{0},
            "5 > 10 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt_u",
            new Object[]{5, 5},
            new Object[]{0},
            "5 > 5 is false"));

        // Unsigned comparison treats -1 as large positive
        runner.addAssertion(TestAssertion.assertReturn("gt_u",
            new Object[]{-1, 5},
            new Object[]{1},
            "-1 (unsigned) > 5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32LeS() {
        final byte[] module = createComparisonModule("le_s", WasmModuleBuilder.I32_LE_S);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("le_s",
            new Object[]{5, 10},
            new Object[]{1},
            "5 <= 10 is true"));

        runner.addAssertion(TestAssertion.assertReturn("le_s",
            new Object[]{10, 5},
            new Object[]{0},
            "10 <= 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("le_s",
            new Object[]{5, 5},
            new Object[]{1},
            "5 <= 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("le_s",
            new Object[]{-10, -5},
            new Object[]{1},
            "-10 <= -5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32LeU() {
        final byte[] module = createComparisonModule("le_u", WasmModuleBuilder.I32_LE_U);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("le_u",
            new Object[]{5, 10},
            new Object[]{1},
            "5 <= 10 is true"));

        runner.addAssertion(TestAssertion.assertReturn("le_u",
            new Object[]{10, 5},
            new Object[]{0},
            "10 <= 5 is false"));

        runner.addAssertion(TestAssertion.assertReturn("le_u",
            new Object[]{5, 5},
            new Object[]{1},
            "5 <= 5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32GeS() {
        final byte[] module = createComparisonModule("ge_s", WasmModuleBuilder.I32_GE_S);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ge_s",
            new Object[]{10, 5},
            new Object[]{1},
            "10 >= 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ge_s",
            new Object[]{5, 10},
            new Object[]{0},
            "5 >= 10 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ge_s",
            new Object[]{5, 5},
            new Object[]{1},
            "5 >= 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ge_s",
            new Object[]{-5, -10},
            new Object[]{1},
            "-5 >= -10 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32GeU() {
        final byte[] module = createComparisonModule("ge_u", WasmModuleBuilder.I32_GE_U);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ge_u",
            new Object[]{10, 5},
            new Object[]{1},
            "10 >= 5 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ge_u",
            new Object[]{5, 10},
            new Object[]{0},
            "5 >= 10 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ge_u",
            new Object[]{5, 5},
            new Object[]{1},
            "5 >= 5 is true"));

        runAndCompare(module);
    }

    @Test
    void testI32ComparisonEdgeCases() {
        final byte[] module = createComparisonModule("eq", WasmModuleBuilder.I32_EQ);
        runner = new ComparisonTestRunner(module);

        // Edge values
        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{Integer.MAX_VALUE, Integer.MAX_VALUE},
            new Object[]{1},
            "MAX_VALUE == MAX_VALUE"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{Integer.MIN_VALUE, Integer.MIN_VALUE},
            new Object[]{1},
            "MIN_VALUE == MIN_VALUE"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{Integer.MAX_VALUE, Integer.MIN_VALUE},
            new Object[]{0},
            "MAX_VALUE != MIN_VALUE"));

        runAndCompare(module);
    }

    /**
     * Helper method to create a comparison module.
     */
    private byte[] createComparisonModule(final String name, final byte opcode) {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        final int typeIndex = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int funcIndex = builder.addFunction(typeIndex);
        builder.addExport(name, funcIndex);
        builder.addCode(
            new byte[]{},
            new byte[]{WasmModuleBuilder.LOCAL_GET, 0x00, WasmModuleBuilder.LOCAL_GET, 0x01, opcode}
        );
        return builder.build();
    }
}
