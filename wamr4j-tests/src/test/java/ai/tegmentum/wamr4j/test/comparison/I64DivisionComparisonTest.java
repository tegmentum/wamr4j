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
 * Comparison tests for i64 division operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i64 division operations (signed and unsigned),
 * including proper trap behavior for division by zero.
 *
 * @since 1.0.0
 */
class I64DivisionComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i64 division comparison tests");
    }

    @Test
    void testI64DivS() {
        final byte[] module = WasmModuleBuilder.createI64DivSModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{10L, 3L},
            new Object[]{3L},
            "10 / 3 = 3"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{20L, 4L},
            new Object[]{5L},
            "20 / 4 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-10L, 3L},
            new Object[]{-3L},
            "-10 / 3 = -3"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{10L, -3L},
            new Object[]{-3L},
            "10 / -3 = -3"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-10L, -3L},
            new Object[]{3L},
            "-10 / -3 = 3"));

        runAndCompare(module);
    }

    @Test
    void testI64DivU() {
        final byte[] module = WasmModuleBuilder.createI64DivUModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{10L, 3L},
            new Object[]{3L},
            "10 / 3 = 3"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{20L, 4L},
            new Object[]{5L},
            "20 / 4 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{100L, 7L},
            new Object[]{14L},
            "100 / 7 = 14"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{1000L, 13L},
            new Object[]{76L},
            "1000 / 13 = 76"));

        runAndCompare(module);
    }

    @Test
    void testI64DivSEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64DivSModule();
        runner = new ComparisonTestRunner(module);

        // Division by 1
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{100L, 1L},
            new Object[]{100L},
            "100 / 1 = 100"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-100L, 1L},
            new Object[]{-100L},
            "-100 / 1 = -100"));

        // Division by -1
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{100L, -1L},
            new Object[]{-100L},
            "100 / -1 = -100"));

        // Division by self
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{42L, 42L},
            new Object[]{1L},
            "42 / 42 = 1"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{0L, 1L},
            new Object[]{0L},
            "0 / 1 = 0"));

        // Smaller dividend
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{5L, 10L},
            new Object[]{0L},
            "5 / 10 = 0"));

        // Large values
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{1000000000000L, 1000L},
            new Object[]{1000000000L},
            "1 trillion / 1000 = 1 billion"));

        runAndCompare(module);
    }

    @Test
    void testI64DivUEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64DivUModule();
        runner = new ComparisonTestRunner(module);

        // Division by 1
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{100L, 1L},
            new Object[]{100L},
            "100 / 1 = 100"));

        // Division by self
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{42L, 42L},
            new Object[]{1L},
            "42 / 42 = 1"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{0L, 1L},
            new Object[]{0L},
            "0 / 1 = 0"));

        // Smaller dividend
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{5L, 10L},
            new Object[]{0L},
            "5 / 10 = 0"));

        // Large values
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{1000000000000L, 1000L},
            new Object[]{1000000000L},
            "1 trillion / 1000 = 1 billion"));

        runAndCompare(module);
    }

    @Test
    void testI64DivSTrapOnDivideByZero() {
        final byte[] module = WasmModuleBuilder.createI64DivSModule();
        runner = new ComparisonTestRunner(module);

        // Division by zero should trap
        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{10L, 0L},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{-10L, 0L},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{0L, 0L},
            "integer divide by zero"));

        runAndCompare(module);
    }

    @Test
    void testI64DivUTrapOnDivideByZero() {
        final byte[] module = WasmModuleBuilder.createI64DivUModule();
        runner = new ComparisonTestRunner(module);

        // Division by zero should trap
        runner.addAssertion(TestAssertion.assertTrap("div_u",
            new Object[]{10L, 0L},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_u",
            new Object[]{100L, 0L},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_u",
            new Object[]{0L, 0L},
            "integer divide by zero"));

        runAndCompare(module);
    }

    @Test
    void testI64DivSTrapOnOverflow() {
        final byte[] module = WasmModuleBuilder.createI64DivSModule();
        runner = new ComparisonTestRunner(module);

        // MIN_VALUE / -1 should trap (overflow)
        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{Long.MIN_VALUE, -1L},
            "integer overflow"));

        runAndCompare(module);
    }

    @Test
    void testI64DivisionTruncation() {
        final byte[] module = WasmModuleBuilder.createI64DivSModule();
        runner = new ComparisonTestRunner(module);

        // Verify truncation behavior
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{7L, 2L},
            new Object[]{3L},
            "7 / 2 = 3 (truncated)"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-7L, 2L},
            new Object[]{-3L},
            "-7 / 2 = -3 (truncated toward zero)"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{7L, -2L},
            new Object[]{-3L},
            "7 / -2 = -3 (truncated toward zero)"));

        runAndCompare(module);
    }
}
