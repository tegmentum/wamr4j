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
import ai.tegmentum.wamr4j.WasiConfiguration;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Fuzz tests for WASI configuration.
 *
 * <p>These tests exercise the WasiConfiguration builder with fuzzed strings
 * for arguments, environment variables, preopened directories, and other
 * WASI parameters to ensure no JVM crashes occur on malformed configuration.
 *
 * @since 1.0.0
 */
class WasiConfigFuzzer {

    /**
     * Minimal valid WASM module (magic + version, no sections).
     *
     * <p>Used as a target for configureWasi() calls. Even though this module
     * is not a WASI module, calling configureWasi() on it should not crash
     * the JVM.
     */
    private static final byte[] MINIMAL_WASM = {
        0x00, 0x61, 0x73, 0x6d,
        0x01, 0x00, 0x00, 0x00
    };

    /**
     * Fuzzes WasiConfiguration with random strings for all configuration fields.
     *
     * <p>Constructs a WasiConfiguration with fuzzed args, env vars, preopened dirs,
     * mapped dirs, address pool entries, NS lookup pool entries, and stdio file
     * descriptors, then applies it to a module and attempts instantiation.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzWasiConfiguration(final FuzzedDataProvider data) {
        // Build fuzzed args
        final int argCount = data.consumeInt(0, 20);
        final String[] args = new String[argCount];
        for (int i = 0; i < argCount; i++) {
            args[i] = data.consumeString(256);
        }

        // Build fuzzed env vars
        final int envCount = data.consumeInt(0, 20);
        final String[] envVars = new String[envCount];
        for (int i = 0; i < envCount; i++) {
            envVars[i] = data.consumeString(256);
        }

        // Build fuzzed preopens
        final int preopenCount = data.consumeInt(0, 10);
        final String[] preopens = new String[preopenCount];
        for (int i = 0; i < preopenCount; i++) {
            preopens[i] = data.consumeString(256);
        }

        // Build fuzzed mapped dirs
        final int mappedDirCount = data.consumeInt(0, 10);
        final String[] mappedDirs = new String[mappedDirCount];
        for (int i = 0; i < mappedDirCount; i++) {
            mappedDirs[i] = data.consumeString(256);
        }

        // Build fuzzed address pool
        final int addrPoolCount = data.consumeInt(0, 10);
        final String[] addrPool = new String[addrPoolCount];
        for (int i = 0; i < addrPoolCount; i++) {
            addrPool[i] = data.consumeString(64);
        }

        // Build fuzzed NS lookup pool
        final int nsPoolCount = data.consumeInt(0, 10);
        final String[] nsPool = new String[nsPoolCount];
        for (int i = 0; i < nsPoolCount; i++) {
            nsPool[i] = data.consumeString(64);
        }

        // Fuzzed stdio file descriptors
        final long stdinFd = data.consumeLong();
        final long stdoutFd = data.consumeLong();
        final long stderrFd = data.consumeLong();

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(MINIMAL_WASM)) {

            // Build and apply the fuzzed WASI configuration
            final WasiConfiguration config = new WasiConfiguration()
                    .setArgs(args)
                    .setEnvVars(envVars)
                    .setPreopens(preopens)
                    .setMappedDirs(mappedDirs)
                    .setAddrPool(addrPool)
                    .setNsLookupPool(nsPool)
                    .setStdinFd(stdinFd)
                    .setStdoutFd(stdoutFd)
                    .setStderrFd(stderrFd);

            // Verify getter round-trip does not crash
            config.getArgs();
            config.getEnvVars();
            config.getPreopens();
            config.getMappedDirs();
            config.getAddrPool();
            config.getNsLookupPool();
            config.getStdinFd();
            config.getStdoutFd();
            config.getStderrFd();
            config.hasCustomStdio();

            // Apply configuration to module
            module.configureWasi(config);

            // Attempt instantiation with the fuzzed WASI config
            try (WebAssemblyInstance instance = module.instantiate()) {
                instance.getFunctionNames();
            }

        } catch (final CompilationException | ValidationException e) {
            // Expected for module-level errors
        } catch (final WasmRuntimeException e) {
            // Expected for WASI configuration or instantiation errors
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for invalid configuration values or closed resources
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during WASI config fuzz", e);
        }
    }
}
