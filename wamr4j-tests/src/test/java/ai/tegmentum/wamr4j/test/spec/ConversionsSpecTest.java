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
 * WAMR engine tests for type conversion operations.
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * execution of type conversion operations between integer and floating-point types.
 *
 * <p>These tests verify the bindings work correctly, not WebAssembly spec compliance
 * (which WAMR itself handles).
 *
 * @since 1.0.0
 */
class ConversionsSpecTest extends AbstractComparisonTest {

    @Test
    void testIntegerExtensionOperations() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // i64.extend_i32_s
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I64}
        );
        final int func1 = builder.addFunction(type1);
        builder.addExport("extend_s", func1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I64_EXTEND_I32_S,
        });

        // i64.extend_i32_u
        final int type2 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I64}
        );
        final int func2 = builder.addFunction(type2);
        builder.addExport("extend_u", func2);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I64_EXTEND_I32_U,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("extend_s",
            new Object[]{-1},
            new Object[]{-1L},
            "i64.extend_i32_s with negative"));

        runner.addAssertion(TestAssertion.assertReturn("extend_u",
            new Object[]{-1},
            new Object[]{4294967295L},
            "i64.extend_i32_u treats as unsigned"));

        runAndCompare(module);
    }

    @Test
    void testIntegerWrapOperations() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I64},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("wrap", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_WRAP_I64,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("wrap",
            new Object[]{0x1_0000_0000L},
            new Object[]{0},
            "i32.wrap_i64 keeps lower 32 bits"));

        runner.addAssertion(TestAssertion.assertReturn("wrap",
            new Object[]{0x1_0000_002AL},
            new Object[]{42},
            "i32.wrap_i64 discards upper bits"));

        runAndCompare(module);
    }

    @Test
    void testFloatToIntTruncation() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // i32.trunc_f32_s
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.F32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func1 = builder.addFunction(type1);
        builder.addExport("trunc_f32_s", func1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_TRUNC_F32_S,
        });

        // i32.trunc_f64_s
        final int type2 = builder.addType(
            new byte[]{WasmModuleBuilder.F64},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func2 = builder.addFunction(type2);
        builder.addExport("trunc_f64_s", func2);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_TRUNC_F64_S,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("trunc_f32_s",
            new Object[]{42.9f},
            new Object[]{42},
            "i32.trunc_f32_s truncates decimal"));

        runner.addAssertion(TestAssertion.assertReturn("trunc_f64_s",
            new Object[]{-123.7},
            new Object[]{-123},
            "i32.trunc_f64_s handles negative"));

        runAndCompare(module);
    }

    @Test
    void testIntToFloatConversion() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // f32.convert_i32_s
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.F32}
        );
        final int func1 = builder.addFunction(type1);
        builder.addExport("convert_i32_s", func1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.F32_CONVERT_I32_S,
        });

        // f64.convert_i32_s
        final int type2 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.F64}
        );
        final int func2 = builder.addFunction(type2);
        builder.addExport("convert_i32_s_to_f64", func2);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.F64_CONVERT_I32_S,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("convert_i32_s",
            new Object[]{42},
            new Object[]{42.0f},
            "f32.convert_i32_s basic conversion"));

        runner.addAssertion(TestAssertion.assertReturn("convert_i32_s_to_f64",
            new Object[]{-100},
            new Object[]{-100.0},
            "f64.convert_i32_s handles negative"));

        runAndCompare(module);
    }

    @Test
    void testFloatPromotionDemotion() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // f64.promote_f32
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.F32},
            new byte[]{WasmModuleBuilder.F64}
        );
        final int func1 = builder.addFunction(type1);
        builder.addExport("promote", func1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.F64_PROMOTE_F32,
        });

        // f32.demote_f64
        final int type2 = builder.addType(
            new byte[]{WasmModuleBuilder.F64},
            new byte[]{WasmModuleBuilder.F32}
        );
        final int func2 = builder.addFunction(type2);
        builder.addExport("demote", func2);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.F32_DEMOTE_F64,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("promote",
            new Object[]{3.14f},
            new Object[]{(double) 3.14f},
            "f64.promote_f32"));

        runner.addAssertion(TestAssertion.assertReturn("demote",
            new Object[]{3.14159265359},
            new Object[]{3.1415927f},
            "f32.demote_f64 loses precision"));

        runAndCompare(module);
    }

    @Test
    void testReinterpretOperations() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // f32.reinterpret_i32
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.F32}
        );
        final int func1 = builder.addFunction(type1);
        builder.addExport("reinterpret_to_f32", func1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.F32_REINTERPRET_I32,
        });

        // i32.reinterpret_f32
        final int type2 = builder.addType(
            new byte[]{WasmModuleBuilder.F32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func2 = builder.addFunction(type2);
        builder.addExport("reinterpret_to_i32", func2);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_REINTERPRET_F32,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Round-trip test: i32 -> f32 bits -> i32 should be identity
        runner.addAssertion(TestAssertion.assertReturn("reinterpret_to_i32",
            new Object[]{1.0f},
            new Object[]{0x3F800000},
            "i32.reinterpret_f32(1.0) = 0x3F800000"));

        runner.addAssertion(TestAssertion.assertReturn("reinterpret_to_f32",
            new Object[]{0x3F800000},
            new Object[]{1.0f},
            "f32.reinterpret_i32(0x3F800000) = 1.0"));

        runAndCompare(module);
    }

    @Test
    void testConversionEdgeCases() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Test i32.wrap_i64 with max values
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I64},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("wrap_edge", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_WRAP_I64,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("wrap_edge",
            new Object[]{-1L},
            new Object[]{-1},
            "Wrap preserves -1"));

        runner.addAssertion(TestAssertion.assertReturn("wrap_edge",
            new Object[]{0xFFFF_FFFF_FFFF_FFFFL},
            new Object[]{-1},
            "Wrap handles max unsigned"));

        runAndCompare(module);
    }
}
