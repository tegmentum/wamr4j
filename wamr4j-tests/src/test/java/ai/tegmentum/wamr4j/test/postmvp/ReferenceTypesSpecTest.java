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

package ai.tegmentum.wamr4j.test.postmvp;

import ai.tegmentum.wamr4j.test.comparison.AbstractComparisonTest;
import ai.tegmentum.wamr4j.test.framework.ComparisonTestRunner;
import ai.tegmentum.wamr4j.test.framework.TestAssertion;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;

/**
 * WAMR engine tests for Reference Types (post-MVP feature).
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * reference types including funcref and externref types, as well as ref.null,
 * ref.is_null, and ref.func operations.
 *
 * <p>Reference Types proposal: https://github.com/WebAssembly/reference-types
 *
 * @since 1.0.0
 */
class ReferenceTypesSpecTest extends AbstractComparisonTest {

    private static final byte FUNCREF = 0x70;
    private static final byte EXTERNREF = 0x6F;
    private static final byte REF_NULL = (byte) 0xD0;
    private static final byte REF_IS_NULL = (byte) 0xD1;
    private static final byte REF_FUNC = (byte) 0xD2;

    @Test
    void testFuncRefBasic() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32) -> i32
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: add_one(x) -> x + 1
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x01,
            WasmModuleBuilder.I32_ADD,
        });

        // Type 1: () -> funcref
        final int type1 = builder.addType(
            new byte[]{},
            new byte[]{FUNCREF}
        );

        // Function 1: get_func_ref() -> funcref to function 0
        final int getRefFunc = builder.addFunction(type1);
        builder.addExport("get_func_ref", getRefFunc);
        builder.addCode(new byte[]{}, new byte[]{
            REF_FUNC, 0x00, // ref.func 0
        });

        // Declare function 0 for ref.func usage
        builder.addDeclarativeElement(0);

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // This test verifies that ref.func returns a valid function reference
        // We can't directly test the reference value, but we verify it doesn't trap
        runner.addAssertion(TestAssertion.assertReturn("get_func_ref",
            new Object[]{},
            new Object[]{null}, // funcref is opaque, we expect non-null object
            "Get function reference to function 0"));

        runAndCompare(module);
    }

    @Test
    void testRefNullFuncRef() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type: () -> funcref
        final int type = builder.addType(
            new byte[]{},
            new byte[]{FUNCREF}
        );

        // Function: get_null_funcref() -> null funcref
        final int func = builder.addFunction(type);
        builder.addExport("get_null_funcref", func);
        builder.addCode(new byte[]{}, new byte[]{
            REF_NULL, FUNCREF, // ref.null funcref
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Verify that ref.null funcref returns null
        runner.addAssertion(TestAssertion.assertReturn("get_null_funcref",
            new Object[]{},
            new Object[]{null},
            "Get null funcref"));

        runAndCompare(module);
    }

    @Test
    void testRefIsNullFuncRef() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: () -> i32
        final int type0 = builder.addType(
            new byte[]{},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: check_null_funcref() -> i32 (1 if null, 0 if not)
        final int func0 = builder.addFunction(type0);
        builder.addExport("check_null_funcref", func0);
        builder.addCode(new byte[]{}, new byte[]{
            REF_NULL, FUNCREF, // ref.null funcref
            REF_IS_NULL, // ref.is_null
        });

        // Function 1: dummy function for ref.func test
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.I32_CONST, 0x42,
        });

        // Function 2: check_non_null_funcref() -> i32 (1 if null, 0 if not)
        final int func2 = builder.addFunction(type0);
        builder.addExport("check_non_null_funcref", func2);
        builder.addCode(new byte[]{}, new byte[]{
            REF_FUNC, 0x01, // ref.func 1 (non-null reference)
            REF_IS_NULL, // ref.is_null
        });

        // Declare function 1 for ref.func usage
        builder.addDeclarativeElement(1);

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Null funcref should return 1 (true)
        runner.addAssertion(TestAssertion.assertReturn("check_null_funcref",
            new Object[]{},
            new Object[]{1},
            "ref.is_null on null funcref returns 1"));

        // Non-null funcref should return 0 (false)
        runner.addAssertion(TestAssertion.assertReturn("check_non_null_funcref",
            new Object[]{},
            new Object[]{0},
            "ref.is_null on non-null funcref returns 0"));

        runAndCompare(module);
    }

    @Test
    void testRefNullExternRef() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type: () -> externref
        final int type = builder.addType(
            new byte[]{},
            new byte[]{EXTERNREF}
        );

        // Function: get_null_externref() -> null externref
        final int func = builder.addFunction(type);
        builder.addExport("get_null_externref", func);
        builder.addCode(new byte[]{}, new byte[]{
            REF_NULL, EXTERNREF, // ref.null externref
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Verify that ref.null externref returns null
        runner.addAssertion(TestAssertion.assertReturn("get_null_externref",
            new Object[]{},
            new Object[]{null},
            "Get null externref"));

        runAndCompare(module);
    }

    @Test
    void testRefIsNullExternRef() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type: () -> i32
        final int type = builder.addType(
            new byte[]{},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function: check_null_externref() -> i32 (1 if null, 0 if not)
        final int func = builder.addFunction(type);
        builder.addExport("check_null_externref", func);
        builder.addCode(new byte[]{}, new byte[]{
            REF_NULL, EXTERNREF, // ref.null externref
            REF_IS_NULL, // ref.is_null
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Null externref should return 1 (true)
        runner.addAssertion(TestAssertion.assertReturn("check_null_externref",
            new Object[]{},
            new Object[]{1},
            "ref.is_null on null externref returns 1"));

        runAndCompare(module);
    }

    @Test
    void testFuncRefInTable() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32) -> i32
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: double(x) -> x * 2
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_MUL,
        });

        // Function 1: square(x) -> x * x
        builder.addFunction(type0);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_MUL,
        });

        // Type 1: (i32, i32) -> i32
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 2: call_indirect(x, index)
        final int callFunc = builder.addFunction(type1);
        builder.addExport("call_indirect", callFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // arg
            WasmModuleBuilder.LOCAL_GET, 0x01, // table index
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,
        });

        // Create table with funcref elements
        builder.addTable(2);
        builder.addTableElement(0, 0, new int[]{0, 1});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Call double via table index 0
        runner.addAssertion(TestAssertion.assertReturn("call_indirect",
            new Object[]{5, 0},
            new Object[]{10},
            "Call double via funcref in table"));

        // Call square via table index 1
        runner.addAssertion(TestAssertion.assertReturn("call_indirect",
            new Object[]{5, 1},
            new Object[]{25},
            "Call square via funcref in table"));

        runAndCompare(module);
    }
}
