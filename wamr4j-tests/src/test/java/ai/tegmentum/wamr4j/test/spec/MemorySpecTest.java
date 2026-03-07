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
 * WAMR engine tests for memory operations.
 *
 * <p>This test class validates that our Java bindings correctly handle WAMR's
 * memory operations including load, store, grow, and data segments.
 *
 * <p>These tests verify the bindings work correctly, not WebAssembly spec compliance
 * (which WAMR itself handles).
 *
 * @since 1.0.0
 */
class MemorySpecTest extends AbstractComparisonTest {

    @Test
    void testMemoryLoadOperations() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);

        // Store test data, then load it back
        final int type = builder.addType(new byte[]{}, new byte[]{WasmModuleBuilder.I32});
        final int func = builder.addFunction(type);
        builder.addExport("load_test", func);
        builder.addCode(new byte[]{}, new byte[]{
            // Store i32 42 at address 0
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.I32_CONST, 0x2A,
            WasmModuleBuilder.I32_STORE, 0x02, 0x00,

            // Load it back
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("load_test",
            new Object[]{},
            new Object[]{42},
            "Memory load after store"));

        runAndCompare(module);
    }

    @Test
    void testMemoryStoreOperations() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);

        // Test all store/load sizes
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("store_test", func);
        builder.addCode(new byte[]{}, new byte[]{
            // Store value as i32
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_STORE, 0x02, 0x00,

            // Load back and return
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("store_test",
            new Object[]{12345},
            new Object[]{12345},
            "i32.store/load round-trip"));

        runAndCompare(module);
    }

    @Test
    void testMemoryAddressing() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);

        // Test different memory addresses
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("address_test", func);
        builder.addCode(new byte[]{}, new byte[]{
            // Store value at given address
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_STORE, 0x02, 0x00,

            // Load from same address
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("address_test",
            new Object[]{0, 100},
            new Object[]{100},
            "Store/load at address 0"));

        runner.addAssertion(TestAssertion.assertReturn("address_test",
            new Object[]{100, 200},
            new Object[]{200},
            "Store/load at address 100"));

        runAndCompare(module);
    }

    @Test
    void testMemoryAlignment() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);

        // Test unaligned access (WAMR should handle it)
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("unaligned", func);
        builder.addCode(new byte[]{}, new byte[]{
            // Store at unaligned address (3)
            WasmModuleBuilder.I32_CONST, 0x03,
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_STORE, 0x00, 0x00,  // alignment=0 (byte-aligned)

            // Load from same unaligned address
            WasmModuleBuilder.I32_CONST, 0x03,
            WasmModuleBuilder.I32_LOAD, 0x00, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("unaligned",
            new Object[]{123456},
            new Object[]{123456},
            "Unaligned memory access"));

        runAndCompare(module);
    }

    @Test
    void testMemoryGrowSize() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);  // Start with 1 page

        // Test memory.size and memory.grow
        final int type = builder.addType(new byte[]{}, new byte[]{WasmModuleBuilder.I32});
        final int func = builder.addFunction(type);
        builder.addExport("size_test", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.MEMORY_SIZE, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("size_test",
            new Object[]{},
            new Object[]{1},
            "Initial memory size is 1 page"));

        runAndCompare(module);
    }

    @Test
    void testDataSegments() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);

        // Initialize memory with data segment at offset 0
        builder.addDataSegment(0, 0, new byte[]{0x01, 0x02, 0x03, 0x04});

        // Read data back
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("read_data", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD8_U, 0x00, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("read_data",
            new Object[]{0},
            new Object[]{1},
            "Data segment byte 0"));

        runner.addAssertion(TestAssertion.assertReturn("read_data",
            new Object[]{1},
            new Object[]{2},
            "Data segment byte 1"));

        runner.addAssertion(TestAssertion.assertReturn("read_data",
            new Object[]{2},
            new Object[]{3},
            "Data segment byte 2"));

        runner.addAssertion(TestAssertion.assertReturn("read_data",
            new Object[]{3},
            new Object[]{4},
            "Data segment byte 3"));

        runAndCompare(module);
    }

    @Test
    void testMemoryBounds() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1);  // 1 page = 65536 bytes

        // Try to access beyond bounds
        final int type = builder.addType(new byte[]{}, new byte[]{WasmModuleBuilder.I32});
        final int func = builder.addFunction(type);
        builder.addExport("out_of_bounds", func);
        builder.addCode(new byte[]{}, new byte[]{
            // i32.const 65536 (0x10000) — beyond 1 page of memory
            WasmModuleBuilder.I32_CONST, (byte) 0x80, (byte) 0x80, 0x04,
            // i32.load align=2 offset=0
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertTrap("out_of_bounds",
            new Object[]{},
            "Out of bounds memory access traps"));

        runAndCompare(module);
    }
}
