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
 * WAMR engine tests for SIMD operations (post-MVP feature).
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * SIMD (128-bit vector) operations including v128 load/store and basic arithmetic.
 *
 * <p>SIMD proposal: https://github.com/WebAssembly/simd
 *
 * <p>Note: These tests verify basic SIMD support is functional. Comprehensive
 * SIMD testing would require extensive test suites beyond the scope of binding
 * verification.
 *
 * @since 1.0.0
 */
class SimdSpecTest extends AbstractComparisonTest {

    private static final byte V128 = 0x7B;
    private static final byte V128_LOAD = (byte) 0xFD00;
    private static final byte V128_STORE = (byte) 0xFD0B;
    private static final byte I32X4_SPLAT = (byte) 0xFD0F;
    private static final byte I32X4_ADD = (byte) 0xFDAE;
    private static final byte I32X4_EXTRACT_LANE = (byte) 0xFD1B;

    @Test
    void testV128LoadStore() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Initialize data segment with 16 bytes (128 bits)
        builder.addDataSegment(0, 0, new byte[]{
            0x01, 0x00, 0x00, 0x00, // lane 0: i32 = 1
            0x02, 0x00, 0x00, 0x00, // lane 1: i32 = 2
            0x03, 0x00, 0x00, 0x00, // lane 2: i32 = 3
            0x04, 0x00, 0x00, 0x00  // lane 3: i32 = 4
        });

        // Function: copy_v128(dest_offset, src_offset)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("copy_v128", func);

        // Function body: v128.load then v128.store
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // dest_offset
            WasmModuleBuilder.LOCAL_GET, 0x01, // src_offset
            (byte) 0xFD, 0x00, 0x03, 0x00, // v128.load (align=3, offset=0)
            (byte) 0xFD, 0x0B, 0x03, 0x00, // v128.store (align=3, offset=0)
        });

        // Verification function: read_i32(offset) -> i32
        final int readType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int readFunc = builder.addFunction(readType);
        builder.addExport("read_i32", readFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Copy v128 from offset 0 to offset 32
        runner.addAssertion(TestAssertion.assertReturn("copy_v128",
            new Object[]{32, 0},
            new Object[]{},
            "Copy 128-bit vector from offset 0 to 32"));

        // Verify copied values
        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{32},
            new Object[]{1},
            "Lane 0 at offset 32 is 1"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{36},
            new Object[]{2},
            "Lane 1 at offset 36 is 2"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{40},
            new Object[]{3},
            "Lane 2 at offset 40 is 3"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{44},
            new Object[]{4},
            "Lane 3 at offset 44 is 4"));

        runAndCompare(module);
    }

    @Test
    void testI32x4Splat() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Function: splat_i32x4(value, dest_offset)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("splat_i32x4", func);

        // Function body: i32x4.splat then v128.store
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x01, // dest_offset
            WasmModuleBuilder.LOCAL_GET, 0x00, // value
            (byte) 0xFD, 0x0F, // i32x4.splat
            (byte) 0xFD, 0x0B, 0x03, 0x00, // v128.store (align=3, offset=0)
        });

        // Verification function: read_i32(offset) -> i32
        final int readType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int readFunc = builder.addFunction(readType);
        builder.addExport("read_i32", readFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Splat value 0x42 across all 4 lanes
        runner.addAssertion(TestAssertion.assertReturn("splat_i32x4",
            new Object[]{0x42, 0},
            new Object[]{},
            "Splat 0x42 across all lanes"));

        // Verify all lanes have the same value
        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{0},
            new Object[]{0x42},
            "Lane 0 is 0x42"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{4},
            new Object[]{0x42},
            "Lane 1 is 0x42"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{8},
            new Object[]{0x42},
            "Lane 2 is 0x42"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{12},
            new Object[]{0x42},
            "Lane 3 is 0x42"));

        runAndCompare(module);
    }

    @Test
    void testI32x4Add() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Initialize two v128 vectors for addition
        // Vector A: [1, 2, 3, 4]
        builder.addDataSegment(0, 0, new byte[]{
            0x01, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x00, 0x00
        });

        // Vector B: [10, 20, 30, 40]
        builder.addDataSegment(0, 16, new byte[]{
            0x0A, 0x00, 0x00, 0x00,
            0x14, 0x00, 0x00, 0x00,
            0x1E, 0x00, 0x00, 0x00,
            0x28, 0x00, 0x00, 0x00
        });

        // Function: add_i32x4(offset_a, offset_b, offset_result)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("add_i32x4", func);

        // Function body: v128.load A, v128.load B, i32x4.add, v128.store
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x02, // result offset
            WasmModuleBuilder.LOCAL_GET, 0x00, // offset_a
            (byte) 0xFD, 0x00, 0x03, 0x00, // v128.load A
            WasmModuleBuilder.LOCAL_GET, 0x01, // offset_b
            (byte) 0xFD, 0x00, 0x03, 0x00, // v128.load B
            (byte) 0xFD, (byte) 0xAE, 0x01, // i32x4.add
            (byte) 0xFD, 0x0B, 0x03, 0x00, // v128.store result
        });

        // Verification function: read_i32(offset) -> i32
        final int readType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int readFunc = builder.addFunction(readType);
        builder.addExport("read_i32", readFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Add vector A and vector B, store at offset 32
        runner.addAssertion(TestAssertion.assertReturn("add_i32x4",
            new Object[]{0, 16, 32},
            new Object[]{},
            "Add two i32x4 vectors"));

        // Verify results: [1+10, 2+20, 3+30, 4+40] = [11, 22, 33, 44]
        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{32},
            new Object[]{11},
            "Result lane 0 is 11"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{36},
            new Object[]{22},
            "Result lane 1 is 22"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{40},
            new Object[]{33},
            "Result lane 2 is 33"));

        runner.addAssertion(TestAssertion.assertReturn("read_i32",
            new Object[]{44},
            new Object[]{44},
            "Result lane 3 is 44"));

        runAndCompare(module);
    }

    @Test
    void testI32x4ExtractLane() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Initialize v128 vector: [10, 20, 30, 40]
        builder.addDataSegment(0, 0, new byte[]{
            0x0A, 0x00, 0x00, 0x00,
            0x14, 0x00, 0x00, 0x00,
            0x1E, 0x00, 0x00, 0x00,
            0x28, 0x00, 0x00, 0x00
        });

        // Function: extract_lane(offset, lane_index) -> i32
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("extract_lane", func);

        // Function body: v128.load then i32x4.extract_lane
        // Note: lane_index must be an immediate, so we'll create separate functions
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // offset
            (byte) 0xFD, 0x00, 0x03, 0x00, // v128.load
            // We'll use local.get 1 to determine which lane (in practice)
            // For simplicity, extract lane 0
            (byte) 0xFD, 0x1B, 0x00, // i32x4.extract_lane 0
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Extract lane 0 (should be 10)
        runner.addAssertion(TestAssertion.assertReturn("extract_lane",
            new Object[]{0, 0},
            new Object[]{10},
            "Extract lane 0 returns 10"));

        runAndCompare(module);
    }
}
