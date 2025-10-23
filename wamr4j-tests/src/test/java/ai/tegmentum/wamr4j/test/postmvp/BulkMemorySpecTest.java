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
 * WAMR engine tests for Bulk Memory Operations (post-MVP feature).
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * bulk memory operations including memory.copy, memory.fill, table.copy, and table.init.
 *
 * <p>Bulk Memory Operations proposal: https://github.com/WebAssembly/bulk-memory-operations
 *
 * @since 1.0.0
 */
class BulkMemorySpecTest extends AbstractComparisonTest {

    private static final byte MEMORY_COPY = (byte) 0xFC0A; // memory.copy opcode extension
    private static final byte MEMORY_FILL = (byte) 0xFC0B; // memory.fill opcode extension
    private static final byte TABLE_INIT = (byte) 0xFC0C; // table.init opcode extension
    private static final byte TABLE_COPY = (byte) 0xFC0E; // table.copy opcode extension

    @Test
    void testMemoryCopyBasic() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Initialize data segment with source bytes
        builder.addDataSegment(0, 0, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});

        // Function: copy_memory(dest_offset, src_offset, size)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("copy_memory", func);

        // Function body: memory.copy
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // dest
            WasmModuleBuilder.LOCAL_GET, 0x01, // src
            WasmModuleBuilder.LOCAL_GET, 0x02, // size
            (byte) 0xFC, 0x0A, 0x00, 0x00, // memory.copy (mem_dst=0, mem_src=0)
        });

        // Verification function: read_byte(offset) -> i32
        final int readType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int readFunc = builder.addFunction(readType);
        builder.addExport("read_byte", readFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD8_U, 0x00, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Copy 3 bytes from offset 0 to offset 10
        runner.addAssertion(TestAssertion.assertReturn("copy_memory",
            new Object[]{10, 0, 3},
            new Object[]{},
            "Copy 3 bytes from offset 0 to 10"));

        // Verify copied bytes
        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{10},
            new Object[]{1},
            "Byte at offset 10 is 1"));

        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{11},
            new Object[]{2},
            "Byte at offset 11 is 2"));

        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{12},
            new Object[]{3},
            "Byte at offset 12 is 3"));

        runAndCompare(module);
    }

    @Test
    void testMemoryFillBasic() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Function: fill_memory(offset, value, size)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("fill_memory", func);

        // Function body: memory.fill
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // offset
            WasmModuleBuilder.LOCAL_GET, 0x01, // value
            WasmModuleBuilder.LOCAL_GET, 0x02, // size
            (byte) 0xFC, 0x0B, 0x00, // memory.fill (mem=0)
        });

        // Verification function: read_byte(offset) -> i32
        final int readType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int readFunc = builder.addFunction(readType);
        builder.addExport("read_byte", readFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD8_U, 0x00, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Fill 5 bytes starting at offset 100 with value 0x42
        runner.addAssertion(TestAssertion.assertReturn("fill_memory",
            new Object[]{100, 0x42, 5},
            new Object[]{},
            "Fill 5 bytes at offset 100 with 0x42"));

        // Verify filled bytes
        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{100},
            new Object[]{0x42},
            "Byte at offset 100 is 0x42"));

        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{104},
            new Object[]{0x42},
            "Byte at offset 104 is 0x42"));

        runAndCompare(module);
    }

    @Test
    void testMemoryCopyOverlapping() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Initialize data segment with source bytes
        builder.addDataSegment(0, 0, new byte[]{
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
        });

        // Function: copy_memory(dest_offset, src_offset, size)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("copy_memory", func);

        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // dest
            WasmModuleBuilder.LOCAL_GET, 0x01, // src
            WasmModuleBuilder.LOCAL_GET, 0x02, // size
            (byte) 0xFC, 0x0A, 0x00, 0x00, // memory.copy
        });

        // Verification function: read_byte(offset) -> i32
        final int readType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int readFunc = builder.addFunction(readType);
        builder.addExport("read_byte", readFunc);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD8_U, 0x00, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Overlapping copy: from offset 0 to offset 2 (6 bytes)
        // Should correctly handle overlapping regions
        runner.addAssertion(TestAssertion.assertReturn("copy_memory",
            new Object[]{2, 0, 6},
            new Object[]{},
            "Overlapping copy from offset 0 to 2"));

        // Verify result
        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{2},
            new Object[]{1},
            "Byte at offset 2 is 1"));

        runner.addAssertion(TestAssertion.assertReturn("read_byte",
            new Object[]{3},
            new Object[]{2},
            "Byte at offset 3 is 2"));

        runAndCompare(module);
    }

    @Test
    void testMemoryFillZeroLength() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page minimum)
        builder.addMemory(1);

        // Function: fill_memory(offset, value, size)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("fill_memory", func);

        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // offset
            WasmModuleBuilder.LOCAL_GET, 0x01, // value
            WasmModuleBuilder.LOCAL_GET, 0x02, // size
            (byte) 0xFC, 0x0B, 0x00, // memory.fill
            WasmModuleBuilder.I32_CONST, 0x00, // return 0 (success)
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Fill 0 bytes (should be a no-op)
        runner.addAssertion(TestAssertion.assertReturn("fill_memory",
            new Object[]{0, 0x42, 0},
            new Object[]{0},
            "Fill 0 bytes is a no-op"));

        runAndCompare(module);
    }

    @Test
    void testMemoryCopyOutOfBounds() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory (1 page = 64KB)
        builder.addMemory(1);

        // Function: copy_memory(dest_offset, src_offset, size)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int func = builder.addFunction(type);
        builder.addExport("copy_memory", func);

        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00, // dest
            WasmModuleBuilder.LOCAL_GET, 0x01, // src
            WasmModuleBuilder.LOCAL_GET, 0x02, // size
            (byte) 0xFC, 0x0A, 0x00, 0x00, // memory.copy
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        // Try to copy past memory bounds (should trap)
        runner.addAssertion(TestAssertion.assertTrap("copy_memory",
            new Object[]{65000, 0, 1000}, // dest + size exceeds 64KB
            "Copy past memory bounds traps"));

        runAndCompare(module);
    }
}
