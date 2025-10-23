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

package ai.tegmentum.wamr4j.test.integration;

import ai.tegmentum.wamr4j.test.comparison.AbstractComparisonTest;
import ai.tegmentum.wamr4j.test.framework.ComparisonTestRunner;
import ai.tegmentum.wamr4j.test.framework.TestAssertion;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests validating complete WebAssembly MVP programs.
 *
 * <p>These tests combine multiple WebAssembly features to validate
 * real-world program patterns:
 * - Memory operations with globals
 * - Control flow with function calls
 * - Tables with indirect dispatch
 * - Complex algorithms using full feature set
 *
 * <p>Purpose: Validate that all MVP features work together correctly
 * in realistic program scenarios.
 *
 * @since 1.0.0
 */
class MVPIntegrationTest extends AbstractComparisonTest {

    @BeforeEach
    void setUp() {
        LOGGER.info("Starting MVP integration tests");
    }

    @Test
    void testFibonacciWithMemoization() {
        // Fibonacci using memory for memoization cache
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Add memory for cache (100 i32 values = 400 bytes)
        builder.addMemory(1);

        // Global: cache initialized flag
        builder.addGlobal(WasmModuleBuilder.I32, true, 0);

        // Function 0: initialize_cache()
        final int initType = builder.addType(new byte[]{}, new byte[]{});
        final int initFunc = builder.addFunction(initType);
        builder.addCode(new byte[]{}, new byte[]{
            // Store fib(0) = 0 at address 0
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.I32_CONST, 0x00,
            WasmModuleBuilder.I32_STORE, 0x02, 0x00,

            // Store fib(1) = 1 at address 4
            WasmModuleBuilder.I32_CONST, 0x04,
            WasmModuleBuilder.I32_CONST, 0x01,
            WasmModuleBuilder.I32_STORE, 0x02, 0x00,

            // Set initialized flag
            WasmModuleBuilder.I32_CONST, 0x01,
            WasmModuleBuilder.GLOBAL_SET, 0x00,
        });

        // Function 1: fib(n) -> i32
        final int fibType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int fibFunc = builder.addFunction(fibType);
        builder.addExport("fib_memo", fibFunc);
        builder.addCode(new byte[]{}, new byte[]{
            // Check if cache initialized, if not initialize
            WasmModuleBuilder.GLOBAL_GET, 0x00,
            WasmModuleBuilder.I32_EQZ,
            WasmModuleBuilder.IF, WasmModuleBuilder.VOID_TYPE,
                WasmModuleBuilder.CALL, 0x00,  // initialize_cache
            WasmModuleBuilder.END,

            // if n < 2, load from cache
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_CONST, 0x02,
            WasmModuleBuilder.I32_LT_U,
            WasmModuleBuilder.IF, WasmModuleBuilder.I32,
                // Load cache[n] from address n*4
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x02,
                WasmModuleBuilder.I32_SHL,  // n * 4
                WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
            WasmModuleBuilder.ELSE,
                // Compute fib(n-1) + fib(n-2)
                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x01,
                WasmModuleBuilder.I32_SUB,
                WasmModuleBuilder.CALL, 0x01,  // fib(n-1)

                WasmModuleBuilder.LOCAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x02,
                WasmModuleBuilder.I32_SUB,
                WasmModuleBuilder.CALL, 0x01,  // fib(n-2)

                WasmModuleBuilder.I32_ADD,
            WasmModuleBuilder.END,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("fib_memo",
            new Object[]{0},
            new Object[]{0},
            "integration: fib(0) = 0"));

        runner.addAssertion(TestAssertion.assertReturn("fib_memo",
            new Object[]{1},
            new Object[]{1},
            "integration: fib(1) = 1"));

        runner.addAssertion(TestAssertion.assertReturn("fib_memo",
            new Object[]{5},
            new Object[]{5},
            "integration: fib(5) = 5"));

        runner.addAssertion(TestAssertion.assertReturn("fib_memo",
            new Object[]{10},
            new Object[]{55},
            "integration: fib(10) = 55"));

        runAndCompare(module);
    }

    @Test
    void testBufferWithGlobals() {
        // Circular buffer implementation using memory and globals
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Memory for buffer (64 i32 values)
        builder.addMemory(1);

        // Global 0: write position
        builder.addGlobal(WasmModuleBuilder.I32, true, 0);
        // Global 1: read position
        builder.addGlobal(WasmModuleBuilder.I32, true, 0);
        // Global 2: count
        builder.addGlobal(WasmModuleBuilder.I32, true, 0);

        // Function 0: push(value) -> i32 (returns 1 on success, 0 on full)
        final int pushType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int pushFunc = builder.addFunction(pushType);
        builder.addCode(new byte[]{}, new byte[]{
            // Check if buffer full (count == 64)
            WasmModuleBuilder.GLOBAL_GET, 0x02,
            WasmModuleBuilder.I32_CONST, 0x40,  // 64
            WasmModuleBuilder.I32_EQ,
            WasmModuleBuilder.IF, WasmModuleBuilder.I32,
                WasmModuleBuilder.I32_CONST, 0x00,  // Return 0 (failure)
            WasmModuleBuilder.ELSE,
                // Store value at write position
                WasmModuleBuilder.GLOBAL_GET, 0x00,  // write_pos
                WasmModuleBuilder.I32_CONST, 0x02,
                WasmModuleBuilder.I32_SHL,  // * 4
                WasmModuleBuilder.LOCAL_GET, 0x00,  // value
                WasmModuleBuilder.I32_STORE, 0x02, 0x00,

                // Increment write position (mod 64)
                WasmModuleBuilder.GLOBAL_GET, 0x00,
                WasmModuleBuilder.I32_CONST, 0x01,
                WasmModuleBuilder.I32_ADD,
                WasmModuleBuilder.I32_CONST, 0x40,
                WasmModuleBuilder.I32_REM_U,
                WasmModuleBuilder.GLOBAL_SET, 0x00,

                // Increment count
                WasmModuleBuilder.GLOBAL_GET, 0x02,
                WasmModuleBuilder.I32_CONST, 0x01,
                WasmModuleBuilder.I32_ADD,
                WasmModuleBuilder.GLOBAL_SET, 0x02,

                WasmModuleBuilder.I32_CONST, 0x01,  // Return 1 (success)
            WasmModuleBuilder.END,
        });

        // Function 1: pop() -> i32 (returns value, or -1 if empty)
        final int popType = builder.addType(
            new byte[]{},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int popFunc = builder.addFunction(popType);
        builder.addCode(new byte[]{}, new byte[]{
            // Check if buffer empty (count == 0)
            WasmModuleBuilder.GLOBAL_GET, 0x02,
            WasmModuleBuilder.I32_EQZ,
            WasmModuleBuilder.IF, WasmModuleBuilder.I32,
                WasmModuleBuilder.I32_CONST, (byte) 0xFF,  // Return -1 (empty)
            WasmModuleBuilder.ELSE,
                // Load value from read position
                WasmModuleBuilder.GLOBAL_GET, 0x01,  // read_pos
                WasmModuleBuilder.I32_CONST, 0x02,
                WasmModuleBuilder.I32_SHL,  // * 4
                WasmModuleBuilder.I32_LOAD, 0x02, 0x00,

                // Increment read position (mod 64)
                WasmModuleBuilder.GLOBAL_GET, 0x01,
                WasmModuleBuilder.I32_CONST, 0x01,
                WasmModuleBuilder.I32_ADD,
                WasmModuleBuilder.I32_CONST, 0x40,
                WasmModuleBuilder.I32_REM_U,
                WasmModuleBuilder.GLOBAL_SET, 0x01,

                // Decrement count
                WasmModuleBuilder.GLOBAL_GET, 0x02,
                WasmModuleBuilder.I32_CONST, 0x01,
                WasmModuleBuilder.I32_SUB,
                WasmModuleBuilder.GLOBAL_SET, 0x02,
            WasmModuleBuilder.END,
        });

        // Function 2: test_harness() -> i32
        final int testType = builder.addType(new byte[]{}, new byte[]{WasmModuleBuilder.I32});
        final int testFunc = builder.addFunction(testType);
        builder.addExport("buffer_test", testFunc);
        builder.addCode(new byte[]{}, new byte[]{
            // Push 10, 20, 30
            WasmModuleBuilder.I32_CONST, 0x0A,
            WasmModuleBuilder.CALL, 0x00,
            WasmModuleBuilder.DROP,

            WasmModuleBuilder.I32_CONST, 0x14,
            WasmModuleBuilder.CALL, 0x00,
            WasmModuleBuilder.DROP,

            WasmModuleBuilder.I32_CONST, 0x1E,
            WasmModuleBuilder.CALL, 0x00,
            WasmModuleBuilder.DROP,

            // Pop and sum
            WasmModuleBuilder.CALL, 0x01,  // Pop 10
            WasmModuleBuilder.CALL, 0x01,  // Pop 20
            WasmModuleBuilder.I32_ADD,
            WasmModuleBuilder.CALL, 0x01,  // Pop 30
            WasmModuleBuilder.I32_ADD,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("buffer_test",
            new Object[]{},
            new Object[]{60},
            "integration: circular buffer push/pop (10+20+30=60)"));

        runAndCompare(module);
    }

    @Test
    void testCalculatorWithDispatchTable() {
        // Calculator using indirect calls for operation dispatch
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Global: last result
        builder.addGlobal(WasmModuleBuilder.I32, true, 0);

        final int opType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Function 0: add
        builder.addFunction(opType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_ADD,
        });

        // Function 1: sub
        builder.addFunction(opType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_SUB,
        });

        // Function 2: mul
        builder.addFunction(opType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_MUL,
        });

        // Function 3: div
        builder.addFunction(opType);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_DIV_S,
        });

        // Function 4: calculate(a, b, op) -> result
        final int calcType = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int calcFunc = builder.addFunction(calcType);
        builder.addExport("calculator", calcFunc);
        builder.addCode(new byte[]{}, new byte[]{
            // Call operation via table
            WasmModuleBuilder.LOCAL_GET, 0x00,  // a
            WasmModuleBuilder.LOCAL_GET, 0x01,  // b
            WasmModuleBuilder.LOCAL_GET, 0x02,  // op index
            WasmModuleBuilder.CALL_INDIRECT, 0x00, 0x00,

            // Store result in global
            WasmModuleBuilder.GLOBAL_SET, 0x00,

            // Return result
            WasmModuleBuilder.GLOBAL_GET, 0x00,
        });

        // Setup table
        builder.addTable(4);
        builder.addTableElement(0, 0, new int[]{0, 1, 2, 3});

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("calculator",
            new Object[]{15, 5, 0},
            new Object[]{20},
            "integration: calculator add (15+5=20)"));

        runner.addAssertion(TestAssertion.assertReturn("calculator",
            new Object[]{15, 5, 1},
            new Object[]{10},
            "integration: calculator sub (15-5=10)"));

        runner.addAssertion(TestAssertion.assertReturn("calculator",
            new Object[]{15, 5, 2},
            new Object[]{75},
            "integration: calculator mul (15*5=75)"));

        runner.addAssertion(TestAssertion.assertReturn("calculator",
            new Object[]{15, 5, 3},
            new Object[]{3},
            "integration: calculator div (15/5=3)"));

        runAndCompare(module);
    }

    @Test
    void testArraySum() {
        // Sum array in memory using loop
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        builder.addMemory(1);

        // Function 0: init_array(size) - initialize array 0..size-1
        final int initType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{}
        );
        final int initFunc = builder.addFunction(initType);
        builder.addCode(
            new byte[]{WasmModuleBuilder.I32},  // Local: counter
            new byte[]{
                // counter = 0
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.LOCAL_SET, 0x01,

                // Loop: while counter < size
                WasmModuleBuilder.BLOCK, WasmModuleBuilder.VOID_TYPE,
                    WasmModuleBuilder.LOOP, WasmModuleBuilder.VOID_TYPE,
                        // Check if done
                        WasmModuleBuilder.LOCAL_GET, 0x01,
                        WasmModuleBuilder.LOCAL_GET, 0x00,
                        WasmModuleBuilder.I32_GE_U,
                        WasmModuleBuilder.BR_IF, 0x01,  // Exit loop

                        // Store counter at address counter*4
                        WasmModuleBuilder.LOCAL_GET, 0x01,
                        WasmModuleBuilder.I32_CONST, 0x02,
                        WasmModuleBuilder.I32_SHL,
                        WasmModuleBuilder.LOCAL_GET, 0x01,
                        WasmModuleBuilder.I32_STORE, 0x02, 0x00,

                        // Increment counter
                        WasmModuleBuilder.LOCAL_GET, 0x01,
                        WasmModuleBuilder.I32_CONST, 0x01,
                        WasmModuleBuilder.I32_ADD,
                        WasmModuleBuilder.LOCAL_SET, 0x01,

                        WasmModuleBuilder.BR, 0x00,  // Continue loop
                    WasmModuleBuilder.END,
                WasmModuleBuilder.END,
            });

        // Function 1: sum_array(size) -> i32
        final int sumType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int sumFunc = builder.addFunction(sumType);
        builder.addCode(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},  // Locals: sum, counter
            new byte[]{
                // sum = 0, counter = 0
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.LOCAL_SET, 0x01,
                WasmModuleBuilder.I32_CONST, 0x00,
                WasmModuleBuilder.LOCAL_SET, 0x02,

                // Loop: while counter < size
                WasmModuleBuilder.BLOCK, WasmModuleBuilder.VOID_TYPE,
                    WasmModuleBuilder.LOOP, WasmModuleBuilder.VOID_TYPE,
                        // Check if done
                        WasmModuleBuilder.LOCAL_GET, 0x02,
                        WasmModuleBuilder.LOCAL_GET, 0x00,
                        WasmModuleBuilder.I32_GE_U,
                        WasmModuleBuilder.BR_IF, 0x01,  // Exit loop

                        // Load value at counter*4 and add to sum
                        WasmModuleBuilder.LOCAL_GET, 0x01,
                        WasmModuleBuilder.LOCAL_GET, 0x02,
                        WasmModuleBuilder.I32_CONST, 0x02,
                        WasmModuleBuilder.I32_SHL,
                        WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
                        WasmModuleBuilder.I32_ADD,
                        WasmModuleBuilder.LOCAL_SET, 0x01,

                        // Increment counter
                        WasmModuleBuilder.LOCAL_GET, 0x02,
                        WasmModuleBuilder.I32_CONST, 0x01,
                        WasmModuleBuilder.I32_ADD,
                        WasmModuleBuilder.LOCAL_SET, 0x02,

                        WasmModuleBuilder.BR, 0x00,  // Continue loop
                    WasmModuleBuilder.END,
                WasmModuleBuilder.END,

                // Return sum
                WasmModuleBuilder.LOCAL_GET, 0x01,
            });

        // Function 2: test(n) -> i32
        final int testType = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int testFunc = builder.addFunction(testType);
        builder.addExport("array_sum", testFunc);
        builder.addCode(new byte[]{}, new byte[]{
            // Initialize array
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.CALL, 0x00,

            // Sum array
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.CALL, 0x01,
        });

        final byte[] module = builder.build();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("array_sum",
            new Object[]{5},
            new Object[]{10},
            "integration: sum(0,1,2,3,4) = 10"));

        runner.addAssertion(TestAssertion.assertReturn("array_sum",
            new Object[]{10},
            new Object[]{45},
            "integration: sum(0..9) = 45"));

        runAndCompare(module);
    }
}
