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
 * Comparison tests for i64 shift operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i64 shift operations (shl, shr_s, shr_u).
 *
 * @since 1.0.0
 */
class I64ShiftComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i64 shift comparison tests");
    }

    @Test
    void testI64Shl() {
        final byte[] module = WasmModuleBuilder.createI64ShlModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1L, 0L},
            new Object[]{1L},
            "1 << 0 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1L, 1L},
            new Object[]{2L},
            "1 << 1 = 2"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1L, 2L},
            new Object[]{4L},
            "1 << 2 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1L, 8L},
            new Object[]{256L},
            "1 << 8 = 256"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{5L, 2L},
            new Object[]{20L},
            "5 << 2 = 20"));

        runAndCompare(module);
    }

    @Test
    void testI64ShrS() {
        final byte[] module = WasmModuleBuilder.createI64ShrSModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{8L, 1L},
            new Object[]{4L},
            "8 >> 1 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{16L, 2L},
            new Object[]{4L},
            "16 >> 2 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{256L, 8L},
            new Object[]{1L},
            "256 >> 8 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{-8L, 1L},
            new Object[]{-4L},
            "-8 >> 1 = -4 (sign extended)"));

        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{-16L, 2L},
            new Object[]{-4L},
            "-16 >> 2 = -4 (sign extended)"));

        runAndCompare(module);
    }

    @Test
    void testI64ShrU() {
        final byte[] module = WasmModuleBuilder.createI64ShrUModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{8L, 1L},
            new Object[]{4L},
            "8 >>> 1 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{16L, 2L},
            new Object[]{4L},
            "16 >>> 2 = 4"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{256L, 8L},
            new Object[]{1L},
            "256 >>> 8 = 1"));

        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{-8L, 1L},
            new Object[]{9223372036854775804L},
            "-8 >>> 1 (zero extended)"));

        runAndCompare(module);
    }

    @Test
    void testI64ShlEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64ShlModule();
        runner = new ComparisonTestRunner(module);

        // Shift by 0
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{12345L, 0L},
            new Object[]{12345L},
            "x << 0 = x"));

        // Shift 0
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{0L, 5L},
            new Object[]{0L},
            "0 << n = 0"));

        // Shift by 63 (max meaningful shift)
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1L, 63L},
            new Object[]{Long.MIN_VALUE},
            "1 << 63 = MIN_VALUE"));

        // Shift wraps bits (shift amount is masked to 6 bits: amount % 64)
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1L, 64L},
            new Object[]{1L},
            "1 << 64 = 1 (shift amount wrapped)"));

        runAndCompare(module);
    }

    @Test
    void testI64ShrSEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64ShrSModule();
        runner = new ComparisonTestRunner(module);

        // Shift by 0
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{12345L, 0L},
            new Object[]{12345L},
            "x >> 0 = x"));

        // Shift 0
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{0L, 5L},
            new Object[]{0L},
            "0 >> n = 0"));

        // Shift -1 (all bits set)
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{-1L, 5L},
            new Object[]{-1L},
            "-1 >> n = -1 (sign extended)"));

        // Shift by 63
        runner.addAssertion(TestAssertion.assertReturn("shr_s",
            new Object[]{Long.MIN_VALUE, 63L},
            new Object[]{-1L},
            "MIN_VALUE >> 63 = -1"));

        runAndCompare(module);
    }

    @Test
    void testI64ShrUEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64ShrUModule();
        runner = new ComparisonTestRunner(module);

        // Shift by 0
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{12345L, 0L},
            new Object[]{12345L},
            "x >>> 0 = x"));

        // Shift 0
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{0L, 5L},
            new Object[]{0L},
            "0 >>> n = 0"));

        // Shift -1 (all bits set)
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{-1L, 1L},
            new Object[]{Long.MAX_VALUE},
            "-1 >>> 1 = MAX_VALUE"));

        // Shift by 63
        runner.addAssertion(TestAssertion.assertReturn("shr_u",
            new Object[]{Long.MIN_VALUE, 63L},
            new Object[]{1L},
            "MIN_VALUE >>> 63 = 1"));

        runAndCompare(module);
    }

    @Test
    void testI64ShiftLargeValues() {
        final byte[] module = WasmModuleBuilder.createI64ShlModule();
        runner = new ComparisonTestRunner(module);

        // Large value shifts
        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{1000000000000L, 2L},
            new Object[]{4000000000000L},
            "1 trillion << 2 = 4 trillion"));

        runner.addAssertion(TestAssertion.assertReturn("shl",
            new Object[]{0x0000000100000000L, 8L},
            new Object[]{0x0000010000000000L},
            "shift across 32-bit boundary"));

        runAndCompare(module);
    }
}
