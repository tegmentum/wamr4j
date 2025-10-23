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
 * Comparison tests for f64 numeric operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for f64 floating-point arithmetic operations.
 *
 * <p><b>Note:</b> f64 operations use 64-bit IEEE 754 floating-point numbers.
 * Comparisons use epsilon-based equality to handle rounding differences.
 *
 * @since 1.0.0
 */
class F64NumericComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting f64 numeric comparison tests");
    }

    @Test
    void testF64Add() {
        final byte[] module = WasmModuleBuilder.createF64AddModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1.0, 2.0},
            new Object[]{3.0},
            "1.0 + 2.0 = 3.0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{0.5, 0.5},
            new Object[]{1.0},
            "0.5 + 0.5 = 1.0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-1.0, 1.0},
            new Object[]{0.0},
            "-1.0 + 1.0 = 0.0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-10.5, -20.5},
            new Object[]{-31.0},
            "-10.5 + -20.5 = -31.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Sub() {
        final byte[] module = WasmModuleBuilder.createF64SubModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{5.0, 3.0},
            new Object[]{2.0},
            "5.0 - 3.0 = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{3.0, 5.0},
            new Object[]{-2.0},
            "3.0 - 5.0 = -2.0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{10.0, 10.0},
            new Object[]{0.0},
            "10.0 - 10.0 = 0.0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{1.5, 0.5},
            new Object[]{1.0},
            "1.5 - 0.5 = 1.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Mul() {
        final byte[] module = WasmModuleBuilder.createF64MulModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{3.0, 4.0},
            new Object[]{12.0},
            "3.0 * 4.0 = 12.0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{-3.0, 4.0},
            new Object[]{-12.0},
            "-3.0 * 4.0 = -12.0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0.5, 0.5},
            new Object[]{0.25},
            "0.5 * 0.5 = 0.25"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0.0, 100.0},
            new Object[]{0.0},
            "0.0 * 100.0 = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Sqrt() {
        final byte[] module = WasmModuleBuilder.createF64SqrtModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{4.0},
            new Object[]{2.0},
            "sqrt(4.0) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{9.0},
            new Object[]{3.0},
            "sqrt(9.0) = 3.0"));

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{0.25},
            new Object[]{0.5},
            "sqrt(0.25) = 0.5"));

        runner.addAssertion(TestAssertion.assertReturn("sqrt",
            new Object[]{0.0},
            new Object[]{0.0},
            "sqrt(0.0) = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Abs() {
        final byte[] module = WasmModuleBuilder.createF64AbsModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{5.0},
            new Object[]{5.0},
            "abs(5.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{-5.0},
            new Object[]{5.0},
            "abs(-5.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{0.0},
            new Object[]{0.0},
            "abs(0.0) = 0.0"));

        runner.addAssertion(TestAssertion.assertReturn("abs",
            new Object[]{-0.0},
            new Object[]{0.0},
            "abs(-0.0) = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Neg() {
        final byte[] module = WasmModuleBuilder.createF64NegModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("neg",
            new Object[]{5.0},
            new Object[]{-5.0},
            "neg(5.0) = -5.0"));

        runner.addAssertion(TestAssertion.assertReturn("neg",
            new Object[]{-5.0},
            new Object[]{5.0},
            "neg(-5.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("neg",
            new Object[]{0.0},
            new Object[]{-0.0},
            "neg(0.0) = -0.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Ceil() {
        final byte[] module = WasmModuleBuilder.createF64CeilModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{1.5},
            new Object[]{2.0},
            "ceil(1.5) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{-1.5},
            new Object[]{-1.0},
            "ceil(-1.5) = -1.0"));

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{2.0},
            new Object[]{2.0},
            "ceil(2.0) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("ceil",
            new Object[]{0.1},
            new Object[]{1.0},
            "ceil(0.1) = 1.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Floor() {
        final byte[] module = WasmModuleBuilder.createF64FloorModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{1.5},
            new Object[]{1.0},
            "floor(1.5) = 1.0"));

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{-1.5},
            new Object[]{-2.0},
            "floor(-1.5) = -2.0"));

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{2.0},
            new Object[]{2.0},
            "floor(2.0) = 2.0"));

        runner.addAssertion(TestAssertion.assertReturn("floor",
            new Object[]{0.9},
            new Object[]{0.0},
            "floor(0.9) = 0.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Min() {
        final byte[] module = WasmModuleBuilder.createF64MinModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("min",
            new Object[]{5.0, 3.0},
            new Object[]{3.0},
            "min(5.0, 3.0) = 3.0"));

        runner.addAssertion(TestAssertion.assertReturn("min",
            new Object[]{-5.0, -3.0},
            new Object[]{-5.0},
            "min(-5.0, -3.0) = -5.0"));

        runner.addAssertion(TestAssertion.assertReturn("min",
            new Object[]{5.0, 5.0},
            new Object[]{5.0},
            "min(5.0, 5.0) = 5.0"));

        runAndCompare(module);
    }

    @Test
    void testF64Max() {
        final byte[] module = WasmModuleBuilder.createF64MaxModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{5.0, 3.0},
            new Object[]{5.0},
            "max(5.0, 3.0) = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{-5.0, -3.0},
            new Object[]{-3.0},
            "max(-5.0, -3.0) = -3.0"));

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{5.0, 5.0},
            new Object[]{5.0},
            "max(5.0, 5.0) = 5.0"));

        runAndCompare(module);
    }

    @Test
    void testF64HighPrecision() {
        final byte[] module = WasmModuleBuilder.createF64AddModule();
        runner = new ComparisonTestRunner(module);

        // Test higher precision than f32
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{0.1, 0.2},
            new Object[]{0.30000000000000004},  // f64 precision
            "0.1 + 0.2 (high precision)"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1e-10, 1e-10},
            new Object[]{2e-10},
            "Very small values"));

        runAndCompare(module);
    }

    @Test
    void testF64SpecialValues() {
        final byte[] module = WasmModuleBuilder.createF64AddModule();
        runner = new ComparisonTestRunner(module);

        // Infinity
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Double.POSITIVE_INFINITY, 1.0},
            new Object[]{Double.POSITIVE_INFINITY},
            "INFINITY + 1.0 = INFINITY"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Double.NEGATIVE_INFINITY, 1.0},
            new Object[]{Double.NEGATIVE_INFINITY},
            "-INFINITY + 1.0 = -INFINITY"));

        // Very small values
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Double.MIN_VALUE, Double.MIN_VALUE},
            new Object[]{Double.MIN_VALUE * 2},
            "MIN_VALUE + MIN_VALUE"));

        runAndCompare(module);
    }
}
