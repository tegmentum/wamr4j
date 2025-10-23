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
 * Comparison tests for i32 numeric operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i32 arithmetic operations.
 *
 * @since 1.0.0
 */
class I32NumericComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i32 numeric comparison tests");
    }

    @Test
    void testI32Add() {
        final byte[] module = WasmModuleBuilder.createI32AddModule();
        runner = new ComparisonTestRunner(module);

        // Add test assertions
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1, 2},
            new Object[]{3},
            "1 + 2 = 3"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{-1, 1},
            new Object[]{0},
            "-1 + 1 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Integer.MAX_VALUE, 1},
            new Object[]{Integer.MIN_VALUE},
            "overflow wraps to MIN_VALUE"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{0, 0},
            new Object[]{0},
            "0 + 0 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{1000000, 2000000},
            new Object[]{3000000},
            "large numbers"));

        runAndCompare(module);
    }

    @Test
    void testI32Sub() {
        final byte[] module = WasmModuleBuilder.createI32SubModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{5, 3},
            new Object[]{2},
            "5 - 3 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{3, 5},
            new Object[]{-2},
            "3 - 5 = -2"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{Integer.MIN_VALUE, 1},
            new Object[]{Integer.MAX_VALUE},
            "underflow wraps to MAX_VALUE"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{0, 0},
            new Object[]{0},
            "0 - 0 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{-10, -5},
            new Object[]{-5},
            "negative numbers"));

        runAndCompare(module);
    }

    @Test
    void testI32Mul() {
        final byte[] module = WasmModuleBuilder.createI32MulModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{3, 4},
            new Object[]{12},
            "3 * 4 = 12"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{-3, 4},
            new Object[]{-12},
            "-3 * 4 = -12"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{-3, -4},
            new Object[]{12},
            "-3 * -4 = 12"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{0, 1000},
            new Object[]{0},
            "0 * 1000 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{1, 1000},
            new Object[]{1000},
            "1 * 1000 = 1000"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{100, 100},
            new Object[]{10000},
            "100 * 100 = 10000"));

        runAndCompare(module);
    }

    @Test
    void testI32AddEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32AddModule();
        runner = new ComparisonTestRunner(module);

        // Test boundary values
        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Integer.MAX_VALUE, 0},
            new Object[]{Integer.MAX_VALUE},
            "MAX_VALUE + 0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Integer.MIN_VALUE, 0},
            new Object[]{Integer.MIN_VALUE},
            "MIN_VALUE + 0"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Integer.MAX_VALUE, Integer.MAX_VALUE},
            new Object[]{-2},
            "MAX_VALUE + MAX_VALUE wraps"));

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{Integer.MIN_VALUE, Integer.MIN_VALUE},
            new Object[]{0},
            "MIN_VALUE + MIN_VALUE wraps"));

        runAndCompare(module);
    }

    @Test
    void testI32SubEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32SubModule();
        runner = new ComparisonTestRunner(module);

        // Test boundary values
        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{Integer.MAX_VALUE, 0},
            new Object[]{Integer.MAX_VALUE},
            "MAX_VALUE - 0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{Integer.MIN_VALUE, 0},
            new Object[]{Integer.MIN_VALUE},
            "MIN_VALUE - 0"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{0, Integer.MAX_VALUE},
            new Object[]{-Integer.MAX_VALUE},
            "0 - MAX_VALUE"));

        runner.addAssertion(TestAssertion.assertReturn("sub",
            new Object[]{Integer.MAX_VALUE, Integer.MAX_VALUE},
            new Object[]{0},
            "MAX_VALUE - MAX_VALUE = 0"));

        runAndCompare(module);
    }

    @Test
    void testI32MulEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32MulModule();
        runner = new ComparisonTestRunner(module);

        // Test boundary values
        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{Integer.MAX_VALUE, 0},
            new Object[]{0},
            "MAX_VALUE * 0 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{Integer.MAX_VALUE, 1},
            new Object[]{Integer.MAX_VALUE},
            "MAX_VALUE * 1 = MAX_VALUE"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{Integer.MAX_VALUE, -1},
            new Object[]{-Integer.MAX_VALUE},
            "MAX_VALUE * -1"));

        runner.addAssertion(TestAssertion.assertReturn("mul",
            new Object[]{Integer.MAX_VALUE, 2},
            new Object[]{-2},
            "MAX_VALUE * 2 wraps"));

        runAndCompare(module);
    }
}
