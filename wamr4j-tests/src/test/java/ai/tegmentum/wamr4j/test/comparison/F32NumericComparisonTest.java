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
 * Comparison tests for f32 numeric operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for f32 floating-point arithmetic operations.
 *
 * <p><b>Note:</b> f32 operations use 32-bit IEEE 754 floating-point numbers.
 * Comparisons use epsilon-based equality to handle rounding differences.
 *
 * @since 1.0.0
 */
class F32NumericComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting f32 numeric comparison tests");
    }

    @Test
    void testF32Add() {
        final byte[] module = WasmModuleBuilder.createF32AddModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1.0f, 2.0f},
            new Object[]{3.0f},
            "1.0 + 2.0 = 3.0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{0.5f, 0.5f},
            new Object[]{1.0f},
            "0.5 + 0.5 = 1.0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-1.0f, 1.0f},
            new Object[]{0.0f},
            "-1.0 + 1.0 = 0.0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-10.5f, -20.5f},
            new Object[]{-31.0f},
            "-10.5 + -20.5 = -31.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Sub() {
        final byte[] module = WasmModuleBuilder.createF32SubModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{5.0f, 3.0f},
            new Object[]{2.0f},
            "5.0 - 3.0 = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{3.0f, 5.0f},
            new Object[]{-2.0f},
            "3.0 - 5.0 = -2.0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{10.0f, 10.0f},
            new Object[]{0.0f},
            "10.0 - 10.0 = 0.0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{1.5f, 0.5f},
            new Object[]{1.0f},
            "1.5 - 0.5 = 1.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Mul() {
        final byte[] module = WasmModuleBuilder.createF32MulModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{3.0f, 4.0f},
            new Object[]{12.0f},
            "3.0 * 4.0 = 12.0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{-3.0f, 4.0f},
            new Object[]{-12.0f},
            "-3.0 * 4.0 = -12.0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0.5f, 0.5f},
            new Object[]{0.25f},
            "0.5 * 0.5 = 0.25"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0.0f, 100.0f},
            new Object[]{0.0f},
            "0.0 * 100.0 = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Sqrt() {
        final byte[] module = WasmModuleBuilder.createF32SqrtModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{4.0f},
            new Object[]{2.0f},
            "sqrt(4.0) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{9.0f},
            new Object[]{3.0f},
            "sqrt(9.0) = 3.0"));

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{0.25f},
            new Object[]{0.5f},
            "sqrt(0.25) = 0.5"));

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{0.0f},
            new Object[]{0.0f},
            "sqrt(0.0) = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Abs() {
        final byte[] module = WasmModuleBuilder.createF32AbsModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{5.0f},
            new Object[]{5.0f},
            "abs(5.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{-5.0f},
            new Object[]{5.0f},
            "abs(-5.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{0.0f},
            new Object[]{0.0f},
            "abs(0.0) = 0.0"));

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{-0.0f},
            new Object[]{0.0f},
            "abs(-0.0) = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Neg() {
        final byte[] module = WasmModuleBuilder.createF32NegModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("neg",
            new Object[]{5.0f},
            new Object[]{-5.0f},
            "neg(5.0) = -5.0"));

        runner.addAssertion(TestAssertion.assertReturn("neg",
            new Object[]{-5.0f},
            new Object[]{5.0f},
            "neg(-5.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("neg",
            new Object[]{0.0f},
            new Object[]{-0.0f},
            "neg(0.0) = -0.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Ceil() {
        final byte[] module = WasmModuleBuilder.createF32CeilModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{1.5f},
            new Object[]{2.0f},
            "ceil(1.5) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{-1.5f},
            new Object[]{-1.0f},
            "ceil(-1.5) = -1.0"));

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{2.0f},
            new Object[]{2.0f},
            "ceil(2.0) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{0.1f},
            new Object[]{1.0f},
            "ceil(0.1) = 1.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Floor() {
        final byte[] module = WasmModuleBuilder.createF32FloorModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{1.5f},
            new Object[]{1.0f},
            "floor(1.5) = 1.0"));

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{-1.5f},
            new Object[]{-2.0f},
            "floor(-1.5) = -2.0"));

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{2.0f},
            new Object[]{2.0f},
            "floor(2.0) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{0.9f},
            new Object[]{0.0f},
            "floor(0.9) = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Min() {
        final byte[] module = WasmModuleBuilder.createF32MinModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("min",
            new Object[]{5.0f, 3.0f},
            new Object[]{3.0f},
            "min(5.0, 3.0) = 3.0"));

        runner.addAssertion(TestAssertion.assertReturn("min",
            new Object[]{-5.0f, -3.0f},
            new Object[]{-5.0f},
            "min(-5.0, -3.0) = -5.0"));

        runner.addAssertion(TestAssertion.assertReturn("min",
            new Object[]{5.0f, 5.0f},
            new Object[]{5.0f},
            "min(5.0, 5.0) = 5.0"));

        runAndCompare(module);
    }

    @Test
    void testF32Max() {
        final byte[] module = WasmModuleBuilder.createF32MaxModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{5.0f, 3.0f},
            new Object[]{5.0f},
            "max(5.0, 3.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{-5.0f, -3.0f},
            new Object[]{-3.0f},
            "max(-5.0, -3.0) = -3.0"));

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{5.0f, 5.0f},
            new Object[]{5.0f},
            "max(5.0, 5.0) = 5.0"));

        runAndCompare(module);
    }

    @Test
    void testF32SpecialValues() {
        final byte[] module = WasmModuleBuilder.createF32AddModule();
        runner = new ComparisonTestRunner(module);

        // Infinity
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Float.POSITIVE_INFINITY, 1.0f},
            new Object[]{Float.POSITIVE_INFINITY},
            "INFINITY + 1.0 = INFINITY"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Float.NEGATIVE_INFINITY, 1.0f},
            new Object[]{Float.NEGATIVE_INFINITY},
            "-INFINITY + 1.0 = -INFINITY"));

        // Very small values
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Float.MIN_VALUE, Float.MIN_VALUE},
            new Object[]{Float.MIN_VALUE * 2},
            "MIN_VALUE + MIN_VALUE"));

        runAndCompare(module);
    }
}
