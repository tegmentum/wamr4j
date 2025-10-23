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
 * Comparison tests for f32 division operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for f32 division, including special floating-point
 * cases like infinity and division by zero.
 *
 * <p><b>Note:</b> Unlike integer division, floating-point division by zero
 * produces infinity rather than trapping.
 *
 * @since 1.0.0
 */
class F32DivisionComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting f32 division comparison tests");
    }

    @Test
    void testF32Div() {
        final byte[] module = WasmModuleBuilder.createF32DivModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0f, 2.0f},
            new Object[]{5.0f},
            "10.0 / 2.0 = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{15.0f, 3.0f},
            new Object[]{5.0f},
            "15.0 / 3.0 = 5.0"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1.0f, 2.0f},
            new Object[]{0.5f},
            "1.0 / 2.0 = 0.5"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{-10.0f, 2.0f},
            new Object[]{-5.0f},
            "-10.0 / 2.0 = -5.0"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{-10.0f, -2.0f},
            new Object[]{5.0f},
            "-10.0 / -2.0 = 5.0"));

        runAndCompare(module);
    }

    @Test
    void testF32DivByZero() {
        final byte[] module = WasmModuleBuilder.createF32DivModule();
        runner = new ComparisonTestRunner(module);

        // Division by zero produces infinity (not a trap)
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0f, 0.0f},
            new Object[]{Float.POSITIVE_INFINITY},
            "10.0 / 0.0 = INFINITY"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{-10.0f, 0.0f},
            new Object[]{Float.NEGATIVE_INFINITY},
            "-10.0 / 0.0 = -INFINITY"));

        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0f, -0.0f},
            new Object[]{Float.NEGATIVE_INFINITY},
            "10.0 / -0.0 = -INFINITY"));

        runAndCompare(module);
    }

    @Test
    void testF32DivEdgeCases() {
        final byte[] module = WasmModuleBuilder.createF32DivModule();
        runner = new ComparisonTestRunner(module);

        // Division by 1
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{42.0f, 1.0f},
            new Object[]{42.0f},
            "42.0 / 1.0 = 42.0"));

        // Division by -1
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{42.0f, -1.0f},
            new Object[]{-42.0f},
            "42.0 / -1.0 = -42.0"));

        // Division by self
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{42.0f, 42.0f},
            new Object[]{1.0f},
            "42.0 / 42.0 = 1.0"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{0.0f, 10.0f},
            new Object[]{0.0f},
            "0.0 / 10.0 = 0.0"));

        // Very small divisor
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1.0f, 0.001f},
            new Object[]{1000.0f},
            "1.0 / 0.001 = 1000.0"));

        runAndCompare(module);
    }

    @Test
    void testF32DivInfinity() {
        final byte[] module = WasmModuleBuilder.createF32DivModule();
        runner = new ComparisonTestRunner(module);

        // Infinity divided by finite
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{Float.POSITIVE_INFINITY, 10.0f},
            new Object[]{Float.POSITIVE_INFINITY},
            "INFINITY / 10.0 = INFINITY"));

        // Finite divided by infinity
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{10.0f, Float.POSITIVE_INFINITY},
            new Object[]{0.0f},
            "10.0 / INFINITY = 0.0"));

        // Negative infinity
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{Float.NEGATIVE_INFINITY, 10.0f},
            new Object[]{Float.NEGATIVE_INFINITY},
            "-INFINITY / 10.0 = -INFINITY"));

        runAndCompare(module);
    }

    @Test
    void testF32DivPrecision() {
        final byte[] module = WasmModuleBuilder.createF32DivModule();
        runner = new ComparisonTestRunner(module);

        // Test precision with small numbers
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1.0f, 3.0f},
            new Object[]{0.33333334f},  // f32 precision
            "1.0 / 3.0 (limited precision)"));

        // Test precision with large numbers
        runner.addAssertion(TestAssertion.assertReturn("div",
            new Object[]{1000000.0f, 3.0f},
            new Object[]{333333.34f},
            "1000000.0 / 3.0"));

        runAndCompare(module);
    }
}
