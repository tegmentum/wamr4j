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

package ai.tegmentum.wamr4j.test.spec;

import ai.tegmentum.wamr4j.test.comparison.AbstractComparisonTest;
import ai.tegmentum.wamr4j.test.framework.ComparisonTestRunner;
import ai.tegmentum.wamr4j.test.framework.TestAssertion;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;

/**
 * WAMR engine tests for control flow operations.
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * execution of control flow constructs including blocks, loops, branches,
 * conditionals, and function calls.
 *
 * <p>These tests verify the bindings work correctly, not WebAssembly spec compliance
 * (which WAMR itself handles).
 *
 * @since 1.0.0
 */
class ControlFlowSpecTest extends AbstractComparisonTest {

    @Test
    void testBlockStructures() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Simple block that returns a value
        final int type = builder.addType(new byte[]{}, new byte[]{WasmModuleBuilder.I32});
        final int func = builder.addFunction(type);
        builder.addExport("block_test", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.BLOCK, WasmModuleBuilder.I32,
                WasmModuleBuilder.I32_CONST, 0x2A,  // 42
            WasmModuleBuilder.END,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("block_test",
            new Object[]{},
            new Object[]{42},
            "Block returns value correctly"));

        runAndCompare(module);
    }

    @Test
    void testLoopStructures() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Countdown loop
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("countdown", func);
        builder.addCode(
            new byte[]{WasmModuleBuilder.I32},  // result accumulator
            new byte[]{
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.LOCAL_SET, 0x01,

                WasmModuleBuilder.BLOCK, WasmModuleBuilder.VOID_TYPE,
                    WasmModuleBuilder.LOOP, WasmModuleBuilder.VOID_TYPE,
                        // Add current counter to result
                        WasmModuleBuilder.LOCAL_GET, 0x01,
                        WasmModuleBuilder.LOCAL_GET, 0x00,
                        WasmModuleBuilder.I32_ADD,
                        WasmModuleBuilder.LOCAL_SET, 0x01,

                        // Decrement counter
                        WasmModuleBuilder.LOCAL_GET, 0x00,
                        WasmModuleBuilder.I32_CONST, 0x01,
                        WasmModuleBuilder.I32_SUB,
                        WasmModuleBuilder.LOCAL_TEE, 0x00,

                        // Continue if not zero
                        WasmModuleBuilder.BR_IF, 0x00,
                    WasmModuleBuilder.END,
                WasmModuleBuilder.END,

                WasmModuleBuilder.LOCAL_GET, 0x01,
            });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("countdown",
            new Object[]{5},
            new Object[]{15},  // 5+4+3+2+1 = 15
            "Loop executes correctly"));

        runAndCompare(module);
    }

    @Test
    void testConditionals() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Max function using if/else
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("max", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_GT_S,
            WasmModuleBuilder.IF, WasmModuleBuilder.I32,
                WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.ELSE,
                WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.END,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{10, 5},
            new Object[]{10},
            "If branch taken correctly"));

        runner.addAssertion(TestAssertion.assertReturn("max",
            new Object[]{5, 10},
            new Object[]{10},
            "Else branch taken correctly"));

        runAndCompare(module);
    }

    @Test
    void testBranchOperations() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Early exit using br
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("early_exit", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.BLOCK, WasmModuleBuilder.I32,
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.I32_LE_S,
                WasmModuleBuilder.IF, WasmModuleBuilder.VOID_TYPE,
                    WasmModuleBuilder.I32_CONST, 0x00,
                    WasmModuleBuilder.BR, 0x01,  // Exit block early
                WasmModuleBuilder.END,
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x02,
                WasmModuleBuilder.I32_MUL,
            WasmModuleBuilder.END,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("early_exit",
            new Object[]{-5},
            new Object[]{0},
            "Branch exits early"));

        runner.addAssertion(TestAssertion.assertReturn("early_exit",
            new Object[]{5},
            new Object[]{10},
            "Normal execution path"));

        runAndCompare(module);
    }

    @Test
    void testConditionalBranches() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Find first positive number
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("first_positive", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.BLOCK, WasmModuleBuilder.I32,
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.I32_GT_S,
                WasmModuleBuilder.BR_IF, 0x00,
                WasmModuleBuilder.DROP,

                WasmModuleBuilder.LOCAL_GET, 0x01,
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.I32_GT_S,
                WasmModuleBuilder.BR_IF, 0x00,
                WasmModuleBuilder.DROP,

                WasmModuleBuilder.LOCAL_GET, 0x02,
            WasmModuleBuilder.END,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("first_positive",
            new Object[]{5, -1, -2},
            new Object[]{5},
            "br_if finds first match"));

        runner.addAssertion(TestAssertion.assertReturn("first_positive",
            new Object[]{-1, 10, -2},
            new Object[]{10},
            "br_if finds second match"));

        runAndCompare(module);
    }

    @Test
    void testBranchTables() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Switch-like behavior
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("switch", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.BLOCK, WasmModuleBuilder.I32,
                WasmModuleBuilder.BLOCK, WasmModuleBuilder.VOID_TYPE,
                    WasmModuleBuilder.BLOCK, WasmModuleBuilder.VOID_TYPE,
                        WasmModuleBuilder.BLOCK, WasmModuleBuilder.VOID_TYPE,
                            WasmModuleBuilder.LOCAL_GET, 0x00,
                            WasmModuleBuilder.BR_TABLE, 0x02, 0x00, 0x01, 0x02,
                        WasmModuleBuilder.END,
                        WasmModuleBuilder.I32_CONST, 0x0A,  // Case 0: return 10
                        WasmModuleBuilder.BR, 0x02,
                    WasmModuleBuilder.END,
                    WasmModuleBuilder.I32_CONST, 0x14,  // Case 1: return 20
                    WasmModuleBuilder.BR, 0x01,
                WasmModuleBuilder.END,
                WasmModuleBuilder.I32_CONST, 0x1E,  // Default: return 30
            WasmModuleBuilder.END,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("switch",
            new Object[]{0},
            new Object[]{10},
            "br_table case 0"));

        runner.addAssertion(TestAssertion.assertReturn("switch",
            new Object[]{1},
            new Object[]{20},
            "br_table case 1"));

        runner.addAssertion(TestAssertion.assertReturn("switch",
            new Object[]{5},
            new Object[]{30},
            "br_table default"));

        runAndCompare(module);
    }

    @Test
    void testDirectCalls() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Helper function
        final int helperType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        builder.addFunction(helperType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_MUL,
        });

        // Main function that calls helper
        final int mainType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int mainFunc = builder.addFunction(mainType);
        builder.addExport("call_test", mainFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.CALL, 0x00,
            WasmModuleBuilder.I32_CONST, 0x05,
            WasmModuleBuilder.I32_ADD,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("call_test",
            new Object[]{10},
            new Object[]{25},  // (10*2)+5
            "Direct call works correctly"));

        runAndCompare(module);
    }

    @Test
    void testIndirectCalls() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Two functions with same signature
        final int opType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        builder.addFunction(opType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_MUL,
        });

        builder.addFunction(opType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x03,
            WasmModuleBuilder.I32_ADD,
        });

        // Dispatcher function
        final int dispatchType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int dispatchFunc = builder.addFunction(dispatchType);
        builder.addExport("dispatch", dispatchFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        builder.addTable(2);
        builder.addTableElement(0, 0, new int[]{0, 1});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("dispatch",
            new Object[]{10, 0},
            new Object[]{20},
            "Indirect call to function 0"));

        runner.addAssertion(TestAssertion.assertReturn("dispatch",
            new Object[]{10, 1},
            new Object[]{13},
            "Indirect call to function 1"));

        runAndCompare(module);
    }

    @Test
    void testReturnStatements() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("early_return", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.I32_LE_S,
            WasmModuleBuilder.IF, WasmModuleBuilder.VOID_TYPE,
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.RETURN,
            WasmModuleBuilder.END,

            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_MUL,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("early_return",
            new Object[]{-5},
            new Object[]{0},
            "Early return executed"));

        runner.addAssertion(TestAssertion.assertReturn("early_return",
            new Object[]{5},
            new Object[]{10},
            "Normal return executed"));

        runAndCompare(module);
    }

    @Test
    void testUnreachableCode() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(new byte[]{}, new byte[]{WasmModuleBuilder.I32});
        final int func = builder.addFunction(type);
        builder.addExport("unreachable_test", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.UNREACHABLE,
            WasmModuleBuilder.I32_CONST, 0x42,  // Never reached
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertTrap("unreachable_test",
            new Object[]{},
            "Unreachable instruction traps"));

        runAndCompare(module);
    }
}
