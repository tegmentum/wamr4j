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
 * Comparison tests for f64 division operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for f64 division, including special floating-point
 * cases like infinity and division by zero.
 *
 * <p><b>Note:</b> Unlike integer division, floating-point division by zero
 * produces infinity rather than trapping.
 *
 * @since 1.0.0
 */
class F64DivisionComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting f64 division comparison tests");
    }

    @Test
    void testF64Div() {
        final byte[] module = WasmModuleBuilder.createF64DivModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0, 2.0},
            new Object[]{5.0},
            "10.0 / 2.0 = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{15.0, 3.0},
            new Object[]{5.0},
            "15.0 / 3.0 = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1.0, 2.0},
            new Object[]{0.5},
            "1.0 / 2.0 = 0.5"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{-10.0, 2.0},
            new Object[]{-5.0},
            "-10.0 / 2.0 = -5.0"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{-10.0, -2.0},
            new Object[]{5.0},
            "-10.0 / -2.0 = 5.0"));

        runAndCompare(module);
    }

    @Test
    void testF64DivByZero() {
        final byte[] module = WasmModuleBuilder.createF64DivModule();
        runner = new ComparisonTestRunner(module);

        // Division by zero produces infinity (not a trap)
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0, 0.0},
            new Object[]{Double.POSITIVE_INFINITY},
            "10.0 / 0.0 = INFINITY"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{-10.0, 0.0},
            new Object[]{Double.NEGATIVE_INFINITY},
            "-10.0 / 0.0 = -INFINITY"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0, -0.0},
            new Object[]{Double.NEGATIVE_INFINITY},
            "10.0 / -0.0 = -INFINITY"));

        runAndCompare(module);
    }

    @Test
    void testF64DivEdgeCases() {
        final byte[] module = WasmModuleBuilder.createF64DivModule();
        runner = new ComparisonTestRunner(module);

        // Division by 1
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{42.0, 1.0},
            new Object[]{42.0},
            "42.0 / 1.0 = 42.0"));

        // Division by -1
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{42.0, -1.0},
            new Object[]{-42.0},
            "42.0 / -1.0 = -42.0"));

        // Division by self
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{42.0, 42.0},
            new Object[]{1.0},
            "42.0 / 42.0 = 1.0"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{0.0, 10.0},
            new Object[]{0.0},
            "0.0 / 10.0 = 0.0"));

        // Very small divisor
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1.0, 0.000001},
            new Object[]{1000000.0},
            "1.0 / 0.000001 = 1000000.0"));

        runAndCompare(module);
    }

    @Test
    void testF64DivInfinity() {
        final byte[] module = WasmModuleBuilder.createF64DivModule();
        runner = new ComparisonTestRunner(module);

        // Infinity divided by finite
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{Double.POSITIVE_INFINITY, 10.0},
            new Object[]{Double.POSITIVE_INFINITY},
            "INFINITY / 10.0 = INFINITY"));

        // Finite divided by infinity
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0, Double.POSITIVE_INFINITY},
            new Object[]{0.0},
            "10.0 / INFINITY = 0.0"));

        // Negative infinity
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{Double.NEGATIVE_INFINITY, 10.0},
            new Object[]{Double.NEGATIVE_INFINITY},
            "-INFINITY / 10.0 = -INFINITY"));

        runAndCompare(module);
    }

    @Test
    void testF64DivHighPrecision() {
        final byte[] module = WasmModuleBuilder.createF64DivModule();
        runner = new ComparisonTestRunner(module);

        // Test precision with small numbers
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1.0, 3.0},
            new Object[]{0.3333333333333333},  // f64 precision
            "1.0 / 3.0 (high precision)"));

        // Test precision with large numbers
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1e15, 3.0},
            new Object[]{3.3333333333333334e14},
            "1e15 / 3.0"));

        // Test very small result
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1e-100, 1e100},
            new Object[]{1e-200},
            "1e-100 / 1e100 = 1e-200"));

        runAndCompare(module);
    }
}
