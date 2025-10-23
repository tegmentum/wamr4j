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
 * Comparison tests for f32 comparison operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for f32 comparison operations (eq, ne, lt, gt, le, ge).
 *
 * <p><b>Note:</b> WebAssembly comparison operations return i32 values
 * (0 for false, 1 for true) rather than boolean types.
 *
 * @since 1.0.0
 */
class F32ComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting f32 comparison tests");
    }

    @Test
    void testF32Eq() {
        final byte[] module = createComparisonModule("eq", WasmModuleBuilder.F32_EQ);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{5.0f, 5.0f},
            new Object[]{1},
            "5.0 == 5.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{5.0f, 6.0f},
            new Object[]{0},
            "5.0 == 6.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{0.0f, 0.0f},
            new Object[]{1},
            "0.0 == 0.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("eq",
            new Object[]{-0.0f, 0.0f},
            new Object[]{1},
            "-0.0 == 0.0 is true (IEEE 754)"));

        runAndCompare(module);
    }

    @Test
    void testF32Ne() {
        final byte[] module = createComparisonModule("ne", WasmModuleBuilder.F32_NE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{5.0f, 5.0f},
            new Object[]{0},
            "5.0 != 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{5.0f, 6.0f},
            new Object[]{1},
            "5.0 != 6.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ne",
            new Object[]{0.0f, 0.0f},
            new Object[]{0},
            "0.0 != 0.0 is false"));

        runAndCompare(module);
    }

    @Test
    void testF32Lt() {
        final byte[] module = createComparisonModule("lt", WasmModuleBuilder.F32_LT);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{5.0f, 10.0f},
            new Object[]{1},
            "5.0 < 10.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{10.0f, 5.0f},
            new Object[]{0},
            "10.0 < 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{5.0f, 5.0f},
            new Object[]{0},
            "5.0 < 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{-5.0f, 5.0f},
            new Object[]{1},
            "-5.0 < 5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF32Gt() {
        final byte[] module = createComparisonModule("gt", WasmModuleBuilder.F32_GT);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{10.0f, 5.0f},
            new Object[]{1},
            "10.0 > 5.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{5.0f, 10.0f},
            new Object[]{0},
            "5.0 > 10.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{5.0f, 5.0f},
            new Object[]{0},
            "5.0 > 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("gt",
            new Object[]{5.0f, -5.0f},
            new Object[]{1},
            "5.0 > -5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF32Le() {
        final byte[] module = createComparisonModule("le", WasmModuleBuilder.F32_LE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("le",
            new Object[]{5.0f, 10.0f},
            new Object[]{1},
            "5.0 <= 10.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("le",
            new Object[]{10.0f, 5.0f},
            new Object[]{0},
            "10.0 <= 5.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("le",
            new Object[]{5.0f, 5.0f},
            new Object[]{1},
            "5.0 <= 5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF32Ge() {
        final byte[] module = createComparisonModule("ge", WasmModuleBuilder.F32_GE);
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ge",
            new Object[]{10.0f, 5.0f},
            new Object[]{1},
            "10.0 >= 5.0 is true"));

        runner.addAssertion(TestAssertion.assertReturn("ge",
            new Object[]{5.0f, 10.0f},
            new Object[]{0},
            "5.0 >= 10.0 is false"));

        runner.addAssertion(TestAssertion.assertReturn("ge",
            new Object[]{5.0f, 5.0f},
            new Object[]{1},
            "5.0 >= 5.0 is true"));

        runAndCompare(module);
    }

    @Test
    void testF32ComparisonInfinity() {
        final byte[] module = createComparisonModule("lt", WasmModuleBuilder.F32_LT);
        runner = new ComparisonTestRunner(module);

        // Infinity comparisons
        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY},
            new Object[]{0},
            "INFINITY < INFINITY is false"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{5.0f, Float.POSITIVE_INFINITY},
            new Object[]{1},
            "5.0 < INFINITY is true"));

        runner.addAssertion(TestAssertion.assertReturn("lt",
            new Object[]{Float.NEGATIVE_INFINITY, 5.0f},
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
            new byte[]{WasmModuleBuilder.F32, WasmModuleBuilder.F32},
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
