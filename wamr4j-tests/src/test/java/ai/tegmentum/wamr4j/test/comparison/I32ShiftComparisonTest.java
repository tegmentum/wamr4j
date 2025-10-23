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
 * Comparison tests for i32 shift operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i32 shift operations (shl, shr_s, shr_u).
 *
 * @since 1.0.0
 */
class I32ShiftComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i32 shift comparison tests");
    }

    @Test
    void testI32Shl() {
        final byte[] module = WasmModuleBuilder.createI32ShlModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1, 0},
            new Object[]{1},
            "1 << 0 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1, 1},
            new Object[]{2},
            "1 << 1 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1, 2},
            new Object[]{4},
            "1 << 2 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1, 8},
            new Object[]{256},
            "1 << 8 = 256"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{5, 2},
            new Object[]{20},
            "5 << 2 = 20"));

        runAndCompare(module);
    }

    @Test
    void testI32ShrS() {
        final byte[] module = WasmModuleBuilder.createI32ShrSModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{8, 1},
            new Object[]{4},
            "8 >> 1 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{16, 2},
            new Object[]{4},
            "16 >> 2 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{256, 8},
            new Object[]{1},
            "256 >> 8 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{-8, 1},
            new Object[]{-4},
            "-8 >> 1 = -4 (sign extended)"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{-16, 2},
            new Object[]{-4},
            "-16 >> 2 = -4 (sign extended)"));

        runAndCompare(module);
    }

    @Test
    void testI32ShrU() {
        final byte[] module = WasmModuleBuilder.createI32ShrUModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{8, 1},
            new Object[]{4},
            "8 >>> 1 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{16, 2},
            new Object[]{4},
            "16 >>> 2 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{256, 8},
            new Object[]{1},
            "256 >>> 8 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{-8, 1},
            new Object[]{2147483644},
            "-8 >>> 1 (zero extended)"));

        runAndCompare(module);
    }

    @Test
    void testI32ShlEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32ShlModule();
        runner = new ComparisonTestRunner(module);

        // Shift by 0
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{12345, 0},
            new Object[]{12345},
            "x << 0 = x"));

        // Shift 0
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{0, 5},
            new Object[]{0},
            "0 << n = 0"));

        // Shift by 31 (max meaningful shift)
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1, 31},
            new Object[]{Integer.MIN_VALUE},
            "1 << 31 = MIN_VALUE"));

        // Shift wraps bits
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1, 32},
            new Object[]{1},
            "1 << 32 = 1 (shift amount wrapped)"));

        runAndCompare(module);
    }

    @Test
    void testI32ShrSEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32ShrSModule();
        runner = new ComparisonTestRunner(module);

        // Shift by 0
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{12345, 0},
            new Object[]{12345},
            "x >> 0 = x"));

        // Shift 0
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{0, 5},
            new Object[]{0},
            "0 >> n = 0"));

        // Shift -1 (all bits set)
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{-1, 5},
            new Object[]{-1},
            "-1 >> n = -1 (sign extended)"));

        // Shift by 31
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{Integer.MIN_VALUE, 31},
            new Object[]{-1},
            "MIN_VALUE >> 31 = -1"));

        runAndCompare(module);
    }

    @Test
    void testI32ShrUEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32ShrUModule();
        runner = new ComparisonTestRunner(module);

        // Shift by 0
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{12345, 0},
            new Object[]{12345},
            "x >>> 0 = x"));

        // Shift 0
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{0, 5},
            new Object[]{0},
            "0 >>> n = 0"));

        // Shift -1 (all bits set)
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{-1, 1},
            new Object[]{Integer.MAX_VALUE},
            "-1 >>> 1 = MAX_VALUE"));

        // Shift by 31
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{Integer.MIN_VALUE, 31},
            new Object[]{1},
            "MIN_VALUE >>> 31 = 1"));

        runAndCompare(module);
    }

    @Test
    void testI32ShiftCombinations() {
        // Test using all operations module
        final byte[] module = WasmModuleBuilder.createI32AllOpsModule();
        runner = new ComparisonTestRunner(module);

        // Shift left then right should recover value
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{5, 2},
            new Object[]{20},
            "5 << 2 = 20"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{20, 2},
            new Object[]{5},
            "20 >>> 2 = 5"));

        // Combine shifts with bitwise operations
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFF, 0x0F},
            new Object[]{0x0F},
            "0xFF & 0x0F = 0x0F"));

        runAndCompare(module);
    }
}
