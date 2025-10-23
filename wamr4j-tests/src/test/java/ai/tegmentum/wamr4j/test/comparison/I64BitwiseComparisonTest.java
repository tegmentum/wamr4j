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
 * Comparison tests for i64 bitwise operations.
 *
 * <p>These tests verify that JNI and Panama implementations produce
 * identical results for i64 bitwise operations (AND, OR, XOR).
 *
 * @since 1.0.0
 */
class I64BitwiseComparisonTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting i64 bitwise comparison tests");
    }

    @Test
    void testI64And() {
        final byte[] module = WasmModuleBuilder.createI64AndModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0b1111L, 0b1010L},
            new Object[]{0b1010L},
            "0b1111 & 0b1010 = 0b1010"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFFL, 0x0FL},
            new Object[]{0x0FL},
            "0xFF & 0x0F = 0x0F"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFFFFFFFFFFFFFFFFL, 0x0000000000000000L},
            new Object[]{0x0000000000000000L},
            "all bits & no bits = no bits"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL},
            new Object[]{0xFFFFFFFFFFFFFFFFL},
            "all bits & all bits = all bits"));

        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{0x123456789ABCDEF0L, 0xFF00FF00FF00FF00L},
            new Object[]{0x1200560098004E00L},
            "mask with AND"));

        runAndCompare(module);
    }

    @Test
    void testI64Or() {
        final byte[] module = WasmModuleBuilder.createI64OrModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0b1111L, 0b1010L},
            new Object[]{0b1111L},
            "0b1111 | 0b1010 = 0b1111"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0xFFL, 0x0FL},
            new Object[]{0xFFL},
            "0xFF | 0x0F = 0xFF"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0xFFFFFFFFFFFFFFFFL, 0x0000000000000000L},
            new Object[]{0xFFFFFFFFFFFFFFFFL},
            "all bits | no bits = all bits"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0x0000000000000000L, 0x0000000000000000L},
            new Object[]{0x0000000000000000L},
            "no bits | no bits = no bits"));

        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{0x1234000000000000L, 0x000000009ABCDEF0L},
            new Object[]{0x123400009ABCDEF0L},
            "combine with OR"));

        runAndCompare(module);
    }

    @Test
    void testI64Xor() {
        final byte[] module = WasmModuleBuilder.createI64XorModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0b1111L, 0b1010L},
            new Object[]{0b0101L},
            "0b1111 ^ 0b1010 = 0b0101"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0xFFL, 0x0FL},
            new Object[]{0xF0L},
            "0xFF ^ 0x0F = 0xF0"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0xFFFFFFFFFFFFFFFFL, 0x0000000000000000L},
            new Object[]{0xFFFFFFFFFFFFFFFFL},
            "all bits ^ no bits = all bits"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL},
            new Object[]{0x0000000000000000L},
            "all bits ^ all bits = no bits"));

        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{42L, 42L},
            new Object[]{0L},
            "x ^ x = 0"));

        runAndCompare(module);
    }

    @Test
    void testI64AndEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64AndModule();
        runner = new ComparisonTestRunner(module);

        // AND with 0 always gives 0
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{12345L, 0L},
            new Object[]{0L},
            "x & 0 = 0"));

        // AND with -1 (all bits set) gives x
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{12345L, -1L},
            new Object[]{12345L},
            "x & -1 = x"));

        // AND with self gives self
        runner.addAssertion(TestAssertion.assertReturn("and",
            new Object[]{12345L, 12345L},
            new Object[]{12345L},
            "x & x = x"));

        runAndCompare(module);
    }

    @Test
    void testI64OrEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64OrModule();
        runner = new ComparisonTestRunner(module);

        // OR with 0 gives x
        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{12345L, 0L},
            new Object[]{12345L},
            "x | 0 = x"));

        // OR with -1 (all bits set) gives -1
        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{12345L, -1L},
            new Object[]{-1L},
            "x | -1 = -1"));

        // OR with self gives self
        runner.addAssertion(TestAssertion.assertReturn("or",
            new Object[]{12345L, 12345L},
            new Object[]{12345L},
            "x | x = x"));

        runAndCompare(module);
    }

    @Test
    void testI64XorEdgeCases() {
        final byte[] module = WasmModuleBuilder.createI64XorModule();
        runner = new ComparisonTestRunner(module);

        // XOR with 0 gives x
        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{12345L, 0L},
            new Object[]{12345L},
            "x ^ 0 = x"));

        // XOR with -1 inverts all bits
        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{0L, -1L},
            new Object[]{-1L},
            "0 ^ -1 = -1"));

        // XOR with self gives 0
        runner.addAssertion(TestAssertion.assertReturn("xor",
            new Object[]{12345L, 12345L},
            new Object[]{0L},
            "x ^ x = 0"));

        runAndCompare(module);
    }
}
