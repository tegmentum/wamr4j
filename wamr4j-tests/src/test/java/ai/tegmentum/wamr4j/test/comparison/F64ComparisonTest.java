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
 * Comparison tests for f64 comparison operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for f64 comparison operations (eq, ne, lt, gt, le, ge).
 *
 * <p><b>Note:</b> WebAssembly comparison operations return i32 values
 * (0 for false, 1 for true) rather than boolean types.
 *
 * @since 1.0.0
 */
class F64ComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting f64 comparison tests");
    }

    @Test
    void testF64Eq() {
        final byte[] module = createComparisonModule("eq", WasmModuleBuilder.F64_EQ);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{5.0, 5.0},
            new Object[]{1},
            "5.0 == 5.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{5.0, 6.0},
            new Object[]{0},
            "5.0 == 6.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{0.0, 0.0},
            new Object[]{1},
            "0.0 == 0.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{-0.0, 0.0},
            new Object[]{1},
            "-0.0 == 0.0 is true (IEEE 754)"));

        runAndCompare(module);
    }

    @Test
    void testF64Ne() {
        final byte[] module = createComparisonModule("ne", WasmModuleBuilder.F64_NE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{5.0, 5.0},
            new Object[]{0},
            "5.0 != 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{5.0, 6.0},
            new Object[]{1},
            "5.0 != 6.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{0.0, 0.0},
            new Object[]{0},
            "0.0 != 0.0 is false"));

        runAndCompare(module);
    }

    @Test
    void testF64Lt() {
        final byte[] module = createComparisonModule("lt", WasmModuleBuilder.F64_LT);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{5.0, 10.0},
            new Object[]{1},
            "5.0 < 10.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{10.0, 5.0},
            new Object[]{0},
            "10.0 < 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{5.0, 5.0},
            new Object[]{0},
            "5.0 < 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{-5.0, 5.0},
            new Object[]{1},
            "-5.0 < 5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF64Gt() {
        final byte[] module = createComparisonModule("gt", WasmModuleBuilder.F64_GT);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{10.0, 5.0},
            new Object[]{1},
            "10.0 > 5.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{5.0, 10.0},
            new Object[]{0},
            "5.0 > 10.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{5.0, 5.0},
            new Object[]{0},
            "5.0 > 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{5.0, -5.0},
            new Object[]{1},
            "5.0 > -5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF64Le() {
        final byte[] module = createComparisonModule("le", WasmModuleBuilder.F64_LE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("le",
            new Object[]{5.0, 10.0},
            new Object[]{1},
            "5.0 <= 10.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("le",
            new Object[]{10.0, 5.0},
            new Object[]{0},
            "10.0 <= 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("le",
            new Object[]{5.0, 5.0},
            new Object[]{1},
            "5.0 <= 5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF64Ge() {
        final byte[] module = createComparisonModule("ge", WasmModuleBuilder.F64_GE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ge",
            new Object[]{10.0, 5.0},
            new Object[]{1},
            "10.0 >= 5.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ge",
            new Object[]{5.0, 10.0},
            new Object[]{0},
            "5.0 >= 10.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ge",
            new Object[]{5.0, 5.0},
            new Object[]{1},
            "5.0 >= 5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF64ComparisonInfinity() {
        final byte[] module = createComparisonModule("lt", WasmModuleBuilder.F64_LT);
        runner = new ComparisonTestRunner(module);

        // Infinity comparisons
        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY},
            new Object[]{0},
            "INFINITY < INFINITY is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{5.0, Double.POSITIVE_INFINITY},
            new Object[]{1},
            "5.0 < INFINITY is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{Double.NEGATIVE_INFINITY, 5.0},
            new Object[]{1},
            "-INFINITY < 5.0 is true"));

        runAndCompare(module);
    }

    /**
     * Helper method to create a comparison module.
     */
    private byte[] createComparisonModule(final String name, final byte opcode) {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        final int typeIndex = builder.addType(
            new byte[]{WasmModuleBuilder.F64, WasmModuleBuilder.F64},
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
