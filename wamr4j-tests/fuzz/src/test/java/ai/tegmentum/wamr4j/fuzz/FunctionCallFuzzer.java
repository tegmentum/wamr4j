/*
 * Copyright (c) 2024 Tegmentum AI, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.wamr4j.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Fuzz tests for WebAssembly function invocation.
 *
 * <p>These tests invoke exported functions with fuzzed arguments and fuzzed
 * function names to ensure the JVM never crashes on invalid input.
 *
 * @since 1.0.0
 */
class FunctionCallFuzzer {

    /**
     * A valid WASM module with an "add" function: (i32, i32) -> i32.
     *
     * <p>Module exports:
     * <ul>
     *   <li>"add" - adds two i32 parameters and returns i32</li>
     *   <li>"identity" - returns its single i32 parameter</li>
     *   <li>"nop" - void function with no parameters</li>
     * </ul>
     *
     * <p>Built from WAT:
     * <pre>
     * (module
     *   (func $add (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
     *   (func $identity (param i32) (result i32) local.get 0)
     *   (func $nop)
     *   (export "add" (func $add))
     *   (export "identity" (func $identity))
     *   (export "nop" (func $nop))
     * )
     * </pre>
     */
    private static final byte[] FUNC_MODULE = {
        // WASM header
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
        // Type section (3 types)
        0x01, 0x0d,
        0x03,                                     // 3 types
        0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,     // (i32, i32) -> i32
        0x60, 0x01, 0x7f, 0x01, 0x7f,           // (i32) -> i32
        0x60, 0x00, 0x00,                         // () -> ()
        // Function section (3 functions)
        0x03, 0x04, 0x03, 0x00, 0x01, 0x02,
        // Export section (3 exports)
        0x07, 0x19,
        0x03,                                     // 3 exports
        0x03, 0x61, 0x64, 0x64, 0x00, 0x00,     // "add" -> func 0
        0x08, 0x69, 0x64, 0x65, 0x6e, 0x74, 0x69, 0x74, 0x79, 0x00, 0x01, // "identity" -> func 1
        0x03, 0x6e, 0x6f, 0x70, 0x00, 0x02,     // "nop" -> func 2
        // Code section (3 function bodies)
        0x0a, 0x12,
        0x03,                                     // 3 bodies
        0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b, // add: local.get 0, local.get 1, i32.add, end
        0x04, 0x00, 0x20, 0x00, 0x0b,           // identity: local.get 0, end
        0x02, 0x00, 0x0b                          // nop: end
    };

    /**
     * Calls exported functions with fuzzed arguments of various types and counts.
     *
     * <p>The fuzzer picks a function name from the known exports and then
     * constructs arguments with fuzzed types and values to verify that
     * invalid calls produce exceptions, not crashes.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzFunctionCall(final FuzzedDataProvider data) {
        final String[] functionNames = {"add", "identity", "nop"};
        final String funcName = data.pickValue(functionNames);
        final int argCount = data.consumeInt(0, 10);

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(FUNC_MODULE);
                WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyFunction func = instance.getFunction(funcName);

            // Build fuzzed arguments array
            final Object[] args = new Object[argCount];
            for (int i = 0; i < argCount; i++) {
                final int typeChoice = data.consumeInt(0, 5);
                switch (typeChoice) {
                    case 0:
                        args[i] = data.consumeInt();
                        break;
                    case 1:
                        args[i] = data.consumeLong();
                        break;
                    case 2:
                        args[i] = data.consumeFloat();
                        break;
                    case 3:
                        args[i] = data.consumeDouble();
                        break;
                    case 4:
                        args[i] = data.consumeString(64);
                        break;
                    default:
                        args[i] = null;
                        break;
                }
            }

            func.invoke(args);

        } catch (final CompilationException | ValidationException e) {
            // Expected for module-level errors
        } catch (final WasmRuntimeException e) {
            // Expected for invalid function calls or execution errors
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for wrong argument count/type or closed resources
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during function call fuzz", e);
        }
    }

    /**
     * Looks up functions by fuzzed names to verify graceful handling of missing exports.
     *
     * <p>The fuzzer generates arbitrary function name strings and attempts to
     * retrieve them from a valid instance, ensuring no crashes on lookup failures.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzFunctionByName(final FuzzedDataProvider data) {
        final String fuzzedName = data.consumeString(512);

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(FUNC_MODULE);
                WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyFunction func = instance.getFunction(fuzzedName);

            // If we got a function, try to invoke it with no args
            func.invoke();

        } catch (final CompilationException | ValidationException e) {
            // Expected for module-level errors
        } catch (final WasmRuntimeException e) {
            // Expected when function is not found or invocation fails
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for null names or closed resources
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during function name fuzz", e);
        }
    }
}
