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
 * Comparison tests for i32 division operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i32 division operations (signed and unsigned).
 *
 * @since 1.0.0
 */
class I32DivisionComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i32 division comparison tests");
    }

    @Test
    void testI32DivS() {
        final byte[] module = WasmModuleBuilder.createI32DivSModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{10, 2},
            new Object[]{5},
            "10 / 2 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{20, 4},
            new Object[]{5},
            "20 / 4 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-10, 2},
            new Object[]{-5},
            "-10 / 2 = -5"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{10, -2},
            new Object[]{-5},
            "10 / -2 = -5"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-10, -2},
            new Object[]{5},
            "-10 / -2 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{7, 3},
            new Object[]{2},
            "7 / 3 = 2 (truncated)"));

        runAndCompare(module);
    }

    @Test
    void testI32DivU() {
        final byte[] module = WasmModuleBuilder.createI32DivUModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{10, 2},
            new Object[]{5},
            "10 / 2 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{100, 10},
            new Object[]{10},
            "100 / 10 = 10"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{1000, 100},
            new Object[]{10},
            "1000 / 100 = 10"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{7, 3},
            new Object[]{2},
            "7 / 3 = 2 (truncated)"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{Integer.MAX_VALUE, 1},
            new Object[]{Integer.MAX_VALUE},
            "MAX_VALUE / 1 = MAX_VALUE"));

        runAndCompare(module);
    }

    @Test
    void testI32DivSEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32DivSModule();
        runner = new ComparisonTestRunner(module);

        // Division by one
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{100, 1},
            new Object[]{100},
            "100 / 1 = 100"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{-100, 1},
            new Object[]{-100},
            "-100 / 1 = -100"));

        // Division by self
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{42, 42},
            new Object[]{1},
            "42 / 42 = 1"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{0, 1},
            new Object[]{0},
            "0 / 1 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{0, 100},
            new Object[]{0},
            "0 / 100 = 0"));

        runAndCompare(module);
    }

    @Test
    void testI32DivUEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32DivUModule();
        runner = new ComparisonTestRunner(module);

        // Division by one
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{100, 1},
            new Object[]{100},
            "100 / 1 = 100"));

        // Division by self
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{42, 42},
            new Object[]{1},
            "42 / 42 = 1"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{0, 1},
            new Object[]{0},
            "0 / 1 = 0"));

        // Large numbers
        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{1000000, 1000},
            new Object[]{1000},
            "1000000 / 1000 = 1000"));

        runAndCompare(module);
    }

    @Test
    void testI32DivSTrapOnDivisionByZero() {
        final byte[] module = WasmModuleBuilder.createI32DivSModule();
        runner = new ComparisonTestRunner(module);

        // Division by zero should trap
        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{10, 0},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{-10, 0},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_s",
            new Object[]{Integer.MAX_VALUE, 0},
            "integer divide by zero"));

        runAndCompare(module);
    }

    @Test
    void testI32DivUTrapOnDivisionByZero() {
        final byte[] module = WasmModuleBuilder.createI32DivUModule();
        runner = new ComparisonTestRunner(module);

        // Division by zero should trap
        runner.addAssertion(TestAssertion.assertTrap("div_u",
            new Object[]{10, 0},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_u",
            new Object[]{100, 0},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("div_u",
            new Object[]{Integer.MAX_VALUE, 0},
            "integer divide by zero"));

        runAndCompare(module);
    }

    @Test
    void testI32AllOperations() {
        final byte[] module = WasmModuleBuilder.createI32AllOpsModule();
        runner = new ComparisonTestRunner(module);

        // Test all operations in one module
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{5, 3},
            new Object[]{8},
            "add: 5 + 3 = 8"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{5, 3},
            new Object[]{2},
            "sub: 5 - 3 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{5, 3},
            new Object[]{15},
            "mul: 5 * 3 = 15"));

        runner.addAssertion(TestAssertion.assertReturn("div_s",
            new Object[]{15, 3},
            new Object[]{5},
            "div_s: 15 / 3 = 5"));

        runner.addAssertion(TestAssertion.assertReturn("div_u",
            new Object[]{15, 3},
            new Object[]{5},
            "div_u: 15 / 3 = 5"));

        runAndCompare(module);
    }
}
