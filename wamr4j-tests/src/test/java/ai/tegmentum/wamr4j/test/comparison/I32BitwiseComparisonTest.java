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
 * Comparison tests for i32 bitwise operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i32 bitwise operations (AND, OR, XOR).
 *
 * @since 1.0.0
 */
class I32BitwiseComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i32 bitwise comparison tests");
    }

    @Test
    void testI32And() {
        final byte[] module = WasmModuleBuilder.createI32AndModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0b1111, 0b1010},
            new Object[]{0b1010},
            "0b1111 & 0b1010 = 0b1010"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFF, 0x0F},
            new Object[]{0x0F},
            "0xFF & 0x0F = 0x0F"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFFFFFFFF, 0x00000000},
            new Object[]{0x00000000},
            "all bits & no bits = no bits"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFFFFFFFF, 0xFFFFFFFF},
            new Object[]{0xFFFFFFFF},
            "all bits & all bits = all bits"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0x12345678, 0xFF00FF00},
            new Object[]{0x12005600},
            "mask with AND"));

        runAndCompare(module);
    }

    @Test
    void testI32Or() {
        final byte[] module = WasmModuleBuilder.createI32OrModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0b1111, 0b1010},
            new Object[]{0b1111},
            "0b1111 | 0b1010 = 0b1111"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0xFF, 0x0F},
            new Object[]{0xFF},
            "0xFF | 0x0F = 0xFF"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0xFFFFFFFF, 0x00000000},
            new Object[]{0xFFFFFFFF},
            "all bits | no bits = all bits"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0x00000000, 0x00000000},
            new Object[]{0x00000000},
            "no bits | no bits = no bits"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0x12340000, 0x00005678},
            new Object[]{0x12345678},
            "combine with OR"));

        runAndCompare(module);
    }

    @Test
    void testI32Xor() {
        final byte[] module = WasmModuleBuilder.createI32XorModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0b1111, 0b1010},
            new Object[]{0b0101},
            "0b1111 ^ 0b1010 = 0b0101"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0xFF, 0x0F},
            new Object[]{0xF0},
            "0xFF ^ 0x0F = 0xF0"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0xFFFFFFFF, 0x00000000},
            new Object[]{0xFFFFFFFF},
            "all bits ^ no bits = all bits"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0xFFFFFFFF, 0xFFFFFFFF},
            new Object[]{0x00000000},
            "all bits ^ all bits = no bits"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{42, 42},
            new Object[]{0},
            "x ^ x = 0"));

        runAndCompare(module);
    }

    @Test
    void testI32AndEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32AndModule();
        runner = new ComparisonTestRunner(module);

        // AND with 0 always gives 0
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{12345, 0},
            new Object[]{0},
            "x & 0 = 0"));

        // AND with -1 (all bits set) gives x
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{12345, -1},
            new Object[]{12345},
            "x & -1 = x"));

        // AND with self gives self
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{12345, 12345},
            new Object[]{12345},
            "x & x = x"));

        runAndCompare(module);
    }

    @Test
    void testI32OrEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32OrModule();
        runner = new ComparisonTestRunner(module);

        // OR with 0 gives x
        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{12345, 0},
            new Object[]{12345},
            "x | 0 = x"));

        // OR with -1 (all bits set) gives -1
        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{12345, -1},
            new Object[]{-1},
            "x | -1 = -1"));

        // OR with self gives self
        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{12345, 12345},
            new Object[]{12345},
            "x | x = x"));

        runAndCompare(module);
    }

    @Test
    void testI32XorEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI32XorModule();
        runner = new ComparisonTestRunner(module);

        // XOR with 0 gives x
        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{12345, 0},
            new Object[]{12345},
            "x ^ 0 = x"));

        // XOR with -1 inverts all bits
        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0, -1},
            new Object[]{-1},
            "0 ^ -1 = -1"));

        // XOR with self gives 0
        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{12345, 12345},
            new Object[]{0},
            "x ^ x = 0"));

        runAndCompare(module);
    }
}
