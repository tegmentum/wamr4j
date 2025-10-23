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
 * Comparison tests for i32 remainder operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i32 remainder operations (signed and unsigned).
 *
 * @since 1.0.0
 */
class I32RemainderComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i32 remainder comparison tests");
    }

    @Test
    void testI32RemS() {
        final byte[] module = WasmModuleBuilder.createI32RemSModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{10, 3},
            new Object[]{1},
            "10 % 3 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{17, 5},
            new Object[]{2},
            "17 % 5 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{-10, 3},
            new Object[]{-1},
            "-10 % 3 = -1"));

        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{10, -3},
            new Object[]{1},
            "10 % -3 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{-10, -3},
            new Object[]{-1},
            "-10 % -3 = -1"));

        runAndCompare(module);
    }

    @Test
    void testI32RemU() {
        final byte[] module = WasmModuleBuilder.createI32RemUModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{10, 3},
            new Object[]{1},
            "10 % 3 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{17, 5},
            new Object[]{2},
            "17 % 5 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{100, 7},
            new Object[]{2},
            "100 % 7 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{1000, 13},
            new Object[]{12},
            "1000 % 13 = 12"));

        runAndCompare(module);
    }

    @Test
    void testI32RemSEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32RemSModule();
        runner = new ComparisonTestRunner(module);

        // Remainder by 1 is always 0
        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{100, 1},
            new Object[]{0},
            "100 % 1 = 0"));

        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{-100, 1},
            new Object[]{0},
            "-100 % 1 = 0"));

        // Remainder by self is 0
        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{42, 42},
            new Object[]{0},
            "42 % 42 = 0"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{0, 1},
            new Object[]{0},
            "0 % 1 = 0"));

        // Smaller dividend
        runner.addAssertion(TestAssertion.assertReturn("rem_s",
            new Object[]{5, 10},
            new Object[]{5},
            "5 % 10 = 5"));

        runAndCompare(module);
    }

    @Test
    void testI32RemUEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32RemUModule();
        runner = new ComparisonTestRunner(module);

        // Remainder by 1 is always 0
        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{100, 1},
            new Object[]{0},
            "100 % 1 = 0"));

        // Remainder by self is 0
        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{42, 42},
            new Object[]{0},
            "42 % 42 = 0"));

        // Zero dividend
        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{0, 1},
            new Object[]{0},
            "0 % 1 = 0"));

        // Smaller dividend
        runner.addAssertion(TestAssertion.assertReturn("rem_u",
            new Object[]{5, 10},
            new Object[]{5},
            "5 % 10 = 5"));

        runAndCompare(module);
    }

    @Test
    void testI32RemSTrapOnRemainderByZero() {
        final byte[] module = WasmModuleBuilder.createI32RemSModule();
        runner = new ComparisonTestRunner(module);

        // Remainder by zero should trap
        runner.addAssertion(TestAssertion.assertTrap("rem_s",
            new Object[]{10, 0},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("rem_s",
            new Object[]{-10, 0},
            "integer divide by zero"));

        runAndCompare(module);
    }

    @Test
    void testI32RemUTrapOnRemainderByZero() {
        final byte[] module = WasmModuleBuilder.createI32RemUModule();
        runner = new ComparisonTestRunner(module);

        // Remainder by zero should trap
        runner.addAssertion(TestAssertion.assertTrap("rem_u",
            new Object[]{10, 0},
            "integer divide by zero"));

        runner.addAssertion(TestAssertion.assertTrap("rem_u",
            new Object[]{100, 0},
            "integer divide by zero"));

        runAndCompare(module);
    }
}
