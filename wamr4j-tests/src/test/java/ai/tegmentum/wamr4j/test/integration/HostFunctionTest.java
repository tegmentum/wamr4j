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

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.HostFunction;
import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for host function imports.
 *
 * <p>These tests verify that Java host functions can be called from
 * WebAssembly modules through both JNI and Panama implementations.
 *
 * @since 1.0.0
 */
class HostFunctionTest {

    private static final Logger LOGGER = Logger.getLogger(HostFunctionTest.class.getName());

    /**
     * Tests a simple i32 host function: add_host(i32, i32) -> i32.
     *
     * <p>The WASM module imports "env.add_host" and exports "call_add" which
     * calls the import with its arguments and returns the result.
     */
    @Test
    void testI32HostFunction() {
        LOGGER.info("Testing i32 host function on both runtimes");

        // Build a WASM module that imports env.add_host(i32, i32) -> i32
        // and exports call_add(i32, i32) -> i32 which calls the import
        final byte[] moduleBytes = buildI32ImportModule();

        // Create the host function: add two i32s
        final HostFunction addHost = new HostFunction(
            new FunctionSignature(
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[]{ValueType.I32}
            ),
            args -> {
                final int a = (Integer) args[0];
                final int b = (Integer) args[1];
                LOGGER.info("Host function called: add_host(" + a + ", " + b + ")");
                return a + b;
            }
        );

        final Map<String, Map<String, Object>> imports = new HashMap<>();
        final Map<String, Object> envImports = new HashMap<>();
        envImports.put("add_host", addHost);
        imports.put("env", envImports);

        // Test on JNI
        testOnRuntime("jni", moduleBytes, imports, "call_add", new Object[]{10, 32}, 42);

        // Test on Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            testOnRuntime("panama", moduleBytes, imports, "call_add", new Object[]{10, 32}, 42);
        }
    }

    /**
     * Tests a void host function (no return value).
     *
     * <p>The WASM module imports "env.notify" (i32) -> void, and exports
     * "do_notify" which calls the import and returns a constant.
     */
    @Test
    void testVoidHostFunction() {
        LOGGER.info("Testing void host function on both runtimes");

        final AtomicInteger notifyValue = new AtomicInteger(0);

        final HostFunction notifyHost = new HostFunction(
            new FunctionSignature(
                new ValueType[]{ValueType.I32},
                new ValueType[]{}
            ),
            args -> {
                final int val = (Integer) args[0];
                LOGGER.info("Host function called: notify(" + val + ")");
                notifyValue.set(val);
                return null;
            }
        );

        final byte[] moduleBytes = buildVoidImportModule();

        final Map<String, Map<String, Object>> imports = new HashMap<>();
        final Map<String, Object> envImports = new HashMap<>();
        envImports.put("notify", notifyHost);
        imports.put("env", envImports);

        // Test on JNI
        notifyValue.set(0);
        testOnRuntime("jni", moduleBytes, imports, "do_notify", new Object[]{99}, 1);
        assertEquals(99, notifyValue.get(), "JNI: notify host function should have been called with 99");

        // Test on Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            notifyValue.set(0);
            testOnRuntime("panama", moduleBytes, imports, "do_notify", new Object[]{99}, 1);
            assertEquals(99, notifyValue.get(), "Panama: notify host function should have been called with 99");
        }
    }

    /**
     * Tests multiple host functions in the same module.
     */
    @Test
    void testMultipleHostFunctions() {
        LOGGER.info("Testing multiple host functions on both runtimes");

        final HostFunction addHost = new HostFunction(
            new FunctionSignature(
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[]{ValueType.I32}
            ),
            args -> (Integer) args[0] + (Integer) args[1]
        );

        final HostFunction mulHost = new HostFunction(
            new FunctionSignature(
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[]{ValueType.I32}
            ),
            args -> (Integer) args[0] * (Integer) args[1]
        );

        final byte[] moduleBytes = buildMultiImportModule();

        final Map<String, Map<String, Object>> imports = new HashMap<>();
        final Map<String, Object> envImports = new HashMap<>();
        envImports.put("add_host", addHost);
        envImports.put("mul_host", mulHost);
        imports.put("env", envImports);

        // call_add_mul(a, b) = add_host(a, b) + mul_host(a, b) = (3+4) + (3*4) = 7+12 = 19
        testOnRuntime("jni", moduleBytes, imports, "call_add_mul", new Object[]{3, 4}, 19);
        if (RuntimeFactory.isProviderAvailable("panama")) {
            testOnRuntime("panama", moduleBytes, imports, "call_add_mul", new Object[]{3, 4}, 19);
        }
    }

    /**
     * Tests that instantiation fails when required imports are not provided.
     */
    @SuppressWarnings("try")
    @Test
    void testMissingImportFails() {
        LOGGER.info("Testing missing import error on both runtimes");

        final byte[] moduleBytes = buildI32ImportModule();

        // No imports provided — instantiation should fail
        assertThrows(Exception.class, () -> {
            final String originalProp = System.getProperty("wamr4j.runtime");
            try {
                System.setProperty("wamr4j.runtime", "jni");
                try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                     final WebAssemblyModule module = runtime.compile(moduleBytes);
                     final WebAssemblyInstance instance = module.instantiate()) {
                    fail("JNI: Should have thrown exception for missing imports");
                }
            } finally {
                if (originalProp != null) {
                    System.setProperty("wamr4j.runtime", originalProp);
                } else {
                    System.clearProperty("wamr4j.runtime");
                }
            }
        }, "JNI: Instantiation should fail without required imports");

        if (RuntimeFactory.isProviderAvailable("panama")) {
            assertThrows(Exception.class, () -> {
                final String originalProp = System.getProperty("wamr4j.runtime");
                try {
                    System.setProperty("wamr4j.runtime", "panama");
                    try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                         final WebAssemblyModule module = runtime.compile(moduleBytes);
                         final WebAssemblyInstance instance = module.instantiate()) {
                        fail("Panama: Should have thrown exception for missing imports");
                    }
                } finally {
                    if (originalProp != null) {
                        System.setProperty("wamr4j.runtime", originalProp);
                    } else {
                        System.clearProperty("wamr4j.runtime");
                    }
                }
            }, "Panama: Instantiation should fail without required imports");
        }
    }

    /**
     * Tests i64 host function: add_i64(i64, i64) -> i64.
     */
    @Test
    void testI64HostFunction() {
        LOGGER.info("Testing i64 host function on both runtimes");

        final HostFunction addI64Host = new HostFunction(
            new FunctionSignature(
                new ValueType[]{ValueType.I64, ValueType.I64},
                new ValueType[]{ValueType.I64}
            ),
            args -> (Long) args[0] + (Long) args[1]
        );

        final byte[] moduleBytes = buildI64ImportModule();

        final Map<String, Map<String, Object>> imports = new HashMap<>();
        final Map<String, Object> envImports = new HashMap<>();
        envImports.put("add_i64_host", addI64Host);
        imports.put("env", envImports);

        final long a = 1000000000L;
        final long b = 2000000000L;
        testOnRuntime("jni", moduleBytes, imports, "call_add_i64", new Object[]{a, b}, a + b);
        if (RuntimeFactory.isProviderAvailable("panama")) {
            testOnRuntime("panama", moduleBytes, imports, "call_add_i64", new Object[]{a, b}, a + b);
        }
    }

    // --- Helper methods ---

    private void testOnRuntime(final String runtimeType, final byte[] moduleBytes,
            final Map<String, Map<String, Object>> imports, final String funcName,
            final Object[] args, final Object expected) {
        final String originalProp = System.getProperty("wamr4j.runtime");
        try {
            System.setProperty("wamr4j.runtime", runtimeType);

            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = runtime.compile(moduleBytes);
                 final WebAssemblyInstance instance = module.instantiate(imports)) {

                final String impl = runtime.getImplementation();
                LOGGER.info("Testing on " + impl + " runtime");

                final WebAssemblyFunction function = instance.getFunction(funcName);
                assertNotNull(function, impl + ": Function '" + funcName + "' should exist");

                final Object result = function.invoke(args);
                LOGGER.info(impl + ": " + funcName + "(" + argsToString(args) + ") = " + result);

                assertEquals(expected, result,
                    impl + ": " + funcName + " result should match expected value");
            }
        } catch (final Exception e) {
            fail(runtimeType.toUpperCase() + ": Unexpected error: " + e.getMessage(), e);
        } finally {
            if (originalProp != null) {
                System.setProperty("wamr4j.runtime", originalProp);
            } else {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    private static String argsToString(final Object[] args) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    // --- WASM module builders ---

    /**
     * Builds a WASM module that:
     * - Imports env.add_host (i32, i32) -> i32
     * - Exports call_add (i32, i32) -> i32 which calls add_host
     */
    private static byte[] buildI32ImportModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32, i32) -> i32
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Import: env.add_host with type 0 — this is function index 0
        builder.addImport("env", "add_host", WasmModuleBuilder.IMPORT_FUNC, type0);

        // Function 0 (local index 0, global index 1): call_add
        builder.addFunction(type0);
        builder.addExport("call_add", 1); // index 1 because import is index 0
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.CALL, 0x00  // call import (function index 0)
        });

        return builder.build();
    }

    /**
     * Builds a WASM module that:
     * - Imports env.notify (i32) -> void
     * - Exports do_notify (i32) -> i32 which calls notify and returns 1
     */
    private static byte[] buildVoidImportModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32) -> void
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{}
        );

        // Type 1: (i32) -> i32
        final int type1 = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Import: env.notify with type 0 — function index 0
        builder.addImport("env", "notify", WasmModuleBuilder.IMPORT_FUNC, type0);

        // Function (global index 1): do_notify
        builder.addFunction(type1);
        builder.addExport("do_notify", 1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.CALL, 0x00,       // call notify(arg)
            WasmModuleBuilder.I32_CONST, 0x01    // return 1 (success indicator)
        });

        return builder.build();
    }

    /**
     * Builds a WASM module that:
     * - Imports env.add_host (i32, i32) -> i32
     * - Imports env.mul_host (i32, i32) -> i32
     * - Exports call_add_mul (i32, i32) -> i32 which returns add_host(a,b) + mul_host(a,b)
     */
    private static byte[] buildMultiImportModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i32, i32) -> i32
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );

        // Import: env.add_host — function index 0
        builder.addImport("env", "add_host", WasmModuleBuilder.IMPORT_FUNC, type0);
        // Import: env.mul_host — function index 1
        builder.addImport("env", "mul_host", WasmModuleBuilder.IMPORT_FUNC, type0);

        // Function (global index 2): call_add_mul
        builder.addFunction(type0);
        builder.addExport("call_add_mul", 2);
        builder.addCode(new byte[]{}, new byte[]{
            // call add_host(a, b)
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.CALL, 0x00,
            // call mul_host(a, b)
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.CALL, 0x01,
            // add_host_result + mul_host_result
            WasmModuleBuilder.I32_ADD
        });

        return builder.build();
    }

    /**
     * Builds a WASM module that:
     * - Imports env.add_i64_host (i64, i64) -> i64
     * - Exports call_add_i64 (i64, i64) -> i64 which calls add_i64_host
     */
    private static byte[] buildI64ImportModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();

        // Type 0: (i64, i64) -> i64
        final int type0 = builder.addType(
            new byte[]{WasmModuleBuilder.I64, WasmModuleBuilder.I64},
            new byte[]{WasmModuleBuilder.I64}
        );

        // Import: env.add_i64_host with type 0 — function index 0
        builder.addImport("env", "add_i64_host", WasmModuleBuilder.IMPORT_FUNC, type0);

        // Function (global index 1): call_add_i64
        builder.addFunction(type0);
        builder.addExport("call_add_i64", 1);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.CALL, 0x00
        });

        return builder.build();
    }
}
