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
 * WAMR engine tests for table operations.
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * table operations including indirect calls and function references.
 *
 * <p>These tests verify the bindings work correctly, not WebAssembly spec compliance
 * (which WAMR itself handles).
 *
 * @since 1.0.0
 */
class TableSpecTest extends AbstractComparisonTest {

    @Test
    void testBasicTableIndirectCall() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Function type: (i32) -> i32
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: double(x) -> x * 2
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_MUL,
        });

        // Function 1: square(x) -> x * x
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_MUL,
        });

        // Function 2: call_indirect(x, index)
        final int callType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int callFunc = builder.addFunction(callType);
        builder.addExport("call_table", callFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,  // arg
            WasmModuleBuilder.LOCAL_GET, 0x01,  // table index
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        // Create table with functions
        builder.addTable(2);
        builder.addTableElement(0, 0, new int[]{0, 1});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("call_table",
            new Object[]{5, 0},
            new Object[]{10},
            "Call double via table index 0"));

        runner.addAssertion(TestAssertion.assertReturn("call_table",
            new Object[]{5, 1},
            new Object[]{25},
            "Call square via table index 1"));

        runAndCompare(module);
    }

    @Test
    void testTableWithMultipleFunctions() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: add
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_ADD,
        });

        // Function 1: sub
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_SUB,
        });

        // Function 2: mul
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_MUL,
        });

        // Function 3: div
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_DIV_S,
        });

        // Function 4: dispatcher(a, b, op)
        final int dispatchType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int dispatchFunc = builder.addFunction(dispatchType);
        builder.addExport("operate", dispatchFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,  // a
            WasmModuleBuilder.LOCAL_GET, 0x01,  // b
            WasmModuleBuilder.LOCAL_GET, 0x02,  // op index
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        builder.addTable(4);
        builder.addTableElement(0, 0, new int[]{0, 1, 2, 3});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("operate",
            new Object[]{10, 5, 0},
            new Object[]{15},
            "Add via table"));

        runner.addAssertion(TestAssertion.assertReturn("operate",
            new Object[]{10, 5, 1},
            new Object[]{5},
            "Subtract via table"));

        runner.addAssertion(TestAssertion.assertReturn("operate",
            new Object[]{10, 5, 2},
            new Object[]{50},
            "Multiply via table"));

        runner.addAssertion(TestAssertion.assertReturn("operate",
            new Object[]{10, 5, 3},
            new Object[]{2},
            "Divide via table"));

        runAndCompare(module);
    }

    @Test
    void testTableOutOfBounds() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Single function
        builder.addFunction(type);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
        });

        // Caller that tries to call index
        final int callType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int callFunc = builder.addFunction(callType);
        builder.addExport("call_index", callFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,  // arg
            WasmModuleBuilder.LOCAL_GET, 0x01,  // index
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        builder.addTable(1);
        builder.addTableElement(0, 0, new int[]{0});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Valid call
        runner.addAssertion(TestAssertion.assertReturn("call_index",
            new Object[]{42, 0},
            new Object[]{42},
            "Valid table index 0"));

        // Out of bounds - should trap
        runner.addAssertion(TestAssertion.assertTrap("call_index",
            new Object[]{42, 5},
            "Out of bounds table access traps"));

        runAndCompare(module);
    }

    @Test
    void testTableWithDifferentSignatures() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: () -> i32
        final int type0 = builder.addType(
            new byte[]{},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Type 1: (i32) -> i32
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: return_42() -> 42
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.I32_CONST, 0x2A,
        });

        // Function 1: identity(x) -> x
        builder.addFunction(type1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
        });

        // Function 2: call first type
        final int caller0 = builder.addFunction(type0);
        builder.addExport("call_no_args", caller0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.I32_CONST, 0x00,  // table index 0
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        // Function 3: call second type
        final int caller1 = builder.addFunction(type1);
        builder.addExport("call_with_arg", caller1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x01,  // table index 1
            WasmModuleBuilder.CALL_INDIRECT, 0x01, 0x00,
        });

        builder.addTable(2);
        builder.addTableElement(0, 0, new int[]{0, 1});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("call_no_args",
            new Object[]{},
            new Object[]{42},
            "Call function with no args via table"));

        runner.addAssertion(TestAssertion.assertReturn("call_with_arg",
            new Object[]{100},
            new Object[]{100},
            "Call function with arg via table"));

        runAndCompare(module);
    }

    @Test
    void testTableRecursiveCall() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Recursive factorial via table
        final int factFunc = builder.addFunction(type);
        builder.addExport("factorial", factFunc);
        builder.addCode(new byte[]{}, new byte[]{
            // if (n <= 1) return 1
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x01,
            WasmModuleBuilder.I32_LE_S,
            WasmModuleBuilder.IF, WasmModuleBuilder.I32,
                WasmModuleBuilder.I32_CONST, 0x01,
            WasmModuleBuilder.ELSE,
                // return n * factorial(n-1)
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x01,
                WasmModuleBuilder.I32_SUB,
                WasmModuleBuilder.I32_CONST, 0x00,  // table index 0 (self)
                WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
                WasmModuleBuilder.I32_MUL,
            WasmModuleBuilder.END,
        });

        builder.addTable(1);
        builder.addTableElement(0, 0, new int[]{0});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("factorial",
            new Object[]{0},
            new Object[]{1},
            "factorial(0) = 1"));

        runner.addAssertion(TestAssertion.assertReturn("factorial",
            new Object[]{1},
            new Object[]{1},
            "factorial(1) = 1"));

        runner.addAssertion(TestAssertion.assertReturn("factorial",
            new Object[]{5},
            new Object[]{120},
            "factorial(5) = 120"));

        runner.addAssertion(TestAssertion.assertReturn("factorial",
            new Object[]{7},
            new Object[]{5040},
            "factorial(7) = 5040"));

        runAndCompare(module);
    }
}
