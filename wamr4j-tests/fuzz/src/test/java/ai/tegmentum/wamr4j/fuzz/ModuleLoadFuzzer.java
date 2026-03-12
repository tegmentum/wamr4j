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
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Fuzz tests for WebAssembly module loading and compilation.
 *
 * <p>These tests feed arbitrary byte sequences to the WAMR compiler to ensure
 * that malformed input never causes JVM crashes, only graceful exceptions.
 *
 * @since 1.0.0
 */
class ModuleLoadFuzzer {

    /**
     * Minimal valid WASM module: magic + version + no sections.
     */
    private static final byte[] MINIMAL_WASM = {
        0x00, 0x61, 0x73, 0x6d, // magic: \0asm
        0x01, 0x00, 0x00, 0x00  // version: 1
    };

    /**
     * Feeds arbitrary bytes to runtime.compile() and verifies no JVM crash occurs.
     *
     * <p>Any byte sequence should either compile successfully or throw a well-defined
     * exception. The JVM must never crash regardless of input.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzModuleFromBytes(final FuzzedDataProvider data) {
        final byte[] wasmBytes = data.consumeRemainingAsBytes();

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            try (WebAssemblyModule module = runtime.compile(wasmBytes)) {
                // If compilation succeeded, exercise basic module API
                module.getExportNames();
                module.getImportNames();
                module.getPackageType();
                module.getPackageVersion();
                module.isUnderlyingBinaryFreeable();
            }
        } catch (final CompilationException | ValidationException e) {
            // Expected for malformed input
        } catch (final WasmRuntimeException e) {
            // Expected for runtime-level rejections
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for null/empty input or closed runtime
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during module load fuzz", e);
        }
    }

    /**
     * Compiles a valid module and exercises metadata APIs with fuzzed names.
     *
     * <p>Starts from a known-good minimal WASM module and then calls module
     * inspection methods with fuzzed string arguments to ensure no crashes.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzModuleValidation(final FuzzedDataProvider data) {
        final String fuzzedName = data.consumeString(256);
        final String fuzzedSectionName = data.consumeString(256);

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            try (WebAssemblyModule module = runtime.compile(MINIMAL_WASM)) {
                // Exercise module name operations with fuzzed input
                module.setName(fuzzedName);
                module.getName();

                // Exercise export/import inspection
                module.getExportNames();
                module.getImportNames();

                // Exercise function signature lookup with fuzzed name
                module.getExportFunctionSignature(fuzzedName);

                // Exercise custom section lookup with fuzzed name
                module.getCustomSection(fuzzedSectionName);

                // Exercise type info lookups with fuzzed names
                module.getExportGlobalTypeInfo(fuzzedName);
                module.getExportMemoryTypeInfo(fuzzedName);

                // Exercise registration with fuzzed name
                module.register(fuzzedName);
            }
        } catch (final CompilationException | ValidationException e) {
            // Expected if minimal module fails on some runtimes
        } catch (final WasmRuntimeException e) {
            // Expected for runtime-level errors
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for invalid arguments
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during module validation fuzz", e);
        }
    }
}
