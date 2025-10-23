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
 * Comparison tests for i64 numeric operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i64 arithmetic operations (add, sub, mul).
 *
 * <p><b>Note:</b> i64 operations use 64-bit signed integers (long in Java).
 *
 * @since 1.0.0
 */
class I64NumericComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i64 numeric comparison tests");
    }

    @Test
    void testI64Add() {
        final byte[] module = WasmModuleBuilder.createI64AddModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1L, 2L},
            new Object[]{3L},
            "1 + 2 = 3"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{100L, 200L},
            new Object[]{300L},
            "100 + 200 = 300"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-1L, 1L},
            new Object[]{0L},
            "-1 + 1 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-10L, -20L},
            new Object[]{-30L},
            "-10 + -20 = -30"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Long.MAX_VALUE, 1L},
            new Object[]{Long.MIN_VALUE},
            "MAX_VALUE + 1 = MIN_VALUE (overflow)"));

        runAndCompare(module);
    }

    @Test
    void testI64Sub() {
        final byte[] module = WasmModuleBuilder.createI64SubModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{5L, 3L},
            new Object[]{2L},
            "5 - 3 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{3L, 5L},
            new Object[]{-2L},
            "3 - 5 = -2"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{-5L, -3L},
            new Object[]{-2L},
            "-5 - -3 = -2"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{10L, 10L},
            new Object[]{0L},
            "10 - 10 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{Long.MIN_VALUE, 1L},
            new Object[]{Long.MAX_VALUE},
            "MIN_VALUE - 1 = MAX_VALUE (underflow)"));

        runAndCompare(module);
    }

    @Test
    void testI64Mul() {
        final byte[] module = WasmModuleBuilder.createI64MulModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{3L, 4L},
            new Object[]{12L},
            "3 * 4 = 12"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{-3L, 4L},
            new Object[]{-12L},
            "-3 * 4 = -12"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{-3L, -4L},
            new Object[]{12L},
            "-3 * -4 = 12"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0L, 100L},
            new Object[]{0L},
            "0 * 100 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{1L, 100L},
            new Object[]{100L},
            "1 * 100 = 100"));

        runAndCompare(module);
    }

    @Test
    void testI64AddEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64AddModule();
        runner = new ComparisonTestRunner(module);

        // Zero identity
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{0L, 0L},
            new Object[]{0L},
            "0 + 0 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{42L, 0L},
            new Object[]{42L},
            "42 + 0 = 42"));

        // Large values
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1000000000000L, 2000000000000L},
            new Object[]{3000000000000L},
            "1 trillion + 2 trillion = 3 trillion"));

        // Max value boundary
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Long.MAX_VALUE, 0L},
            new Object[]{Long.MAX_VALUE},
            "MAX_VALUE + 0 = MAX_VALUE"));

        runAndCompare(module);
    }

    @Test
    void testI64SubEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64SubModule();
        runner = new ComparisonTestRunner(module);

        // Zero identity
        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{42L, 0L},
            new Object[]{42L},
            "42 - 0 = 42"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{0L, 0L},
            new Object[]{0L},
            "0 - 0 = 0"));

        // Self subtraction
        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{42L, 42L},
            new Object[]{0L},
            "42 - 42 = 0"));

        // Large values
        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{3000000000000L, 1000000000000L},
            new Object[]{2000000000000L},
            "3 trillion - 1 trillion = 2 trillion"));

        runAndCompare(module);
    }

    @Test
    void testI64MulEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64MulModule();
        runner = new ComparisonTestRunner(module);

        // Zero multiplication
        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0L, 0L},
            new Object[]{0L},
            "0 * 0 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{42L, 0L},
            new Object[]{0L},
            "42 * 0 = 0"));

        // Identity
        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{42L, 1L},
            new Object[]{42L},
            "42 * 1 = 42"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{1L, 1L},
            new Object[]{1L},
            "1 * 1 = 1"));

        // Negative one
        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{42L, -1L},
            new Object[]{-42L},
            "42 * -1 = -42"));

        runAndCompare(module);
    }
}
