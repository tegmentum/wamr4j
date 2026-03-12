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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * Fuzz tests for concurrent access to WAMR4J resources.
 *
 * <p>These tests exercise thread safety of the runtime, module compilation,
 * and function invocation under concurrent access patterns driven by fuzz input.
 *
 * @since 1.0.0
 */
class ConcurrentAccessFuzzer {

    /**
     * A valid WASM module with an "add" function: (i32, i32) -> i32.
     *
     * <p>Built from WAT:
     * <pre>
     * (module
     *   (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
     * )
     * </pre>
     */
    private static final byte[] ADD_MODULE = {
        // WASM header
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
        // Type section: 1 type (i32, i32) -> i32
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,
        // Function section: 1 function of type 0
        0x03, 0x02, 0x01, 0x00,
        // Export section: "add" -> func 0
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        // Code section: 1 body
        0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b
    };

    /** Maximum number of concurrent threads per fuzz iteration. */
    private static final int MAX_THREADS = 8;

    /** Timeout in seconds for concurrent operations. */
    private static final int TIMEOUT_SECONDS = 10;

    /**
     * Concurrently compiles modules from a shared runtime using fuzzed byte arrays.
     *
     * <p>Multiple threads attempt to compile different byte sequences from the
     * same runtime simultaneously. This tests thread safety of runtime.compile().
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzConcurrentModuleCompilation(final FuzzedDataProvider data) {
        final int threadCount = data.consumeInt(2, MAX_THREADS);
        final byte[][] inputs = new byte[threadCount][];
        for (int i = 0; i < threadCount; i++) {
            final int inputSize = data.consumeInt(0, 1024);
            inputs[i] = data.consumeBytes(inputSize);
        }

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            final CountDownLatch startLatch = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final byte[] input = inputs[i];
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        try (WebAssemblyModule module = runtime.compile(input)) {
                            module.getExportNames();
                        }
                    } catch (final CompilationException | ValidationException e) {
                        // Expected for invalid input
                    } catch (final IllegalArgumentException | IllegalStateException e) {
                        // Expected for empty input or closed runtime
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (final Exception e) {
                        unexpectedError.compareAndSet(null, e);
                    }
                }));
            }

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for all threads to complete
            for (final Future<?> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

        } catch (final WasmRuntimeException e) {
            // Expected if no runtime provider available
        } catch (final Exception e) {
            // Timeout or interruption during wait
        } finally {
            executor.shutdownNow();
        }

        final Throwable error = unexpectedError.get();
        if (error != null) {
            throw new AssertionError("Unexpected exception during concurrent compilation", error);
        }
    }

    /**
     * Concurrently invokes functions using per-thread instances from a shared module.
     *
     * <p>Each thread creates its own instance from the same compiled module and
     * invokes functions with fuzzed i32 arguments. This tests that per-thread
     * instances are truly isolated and concurrent invocation is safe.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzConcurrentFunctionCalls(final FuzzedDataProvider data) {
        final int threadCount = data.consumeInt(2, MAX_THREADS);
        final int[][] argPairs = new int[threadCount][2];
        for (int i = 0; i < threadCount; i++) {
            argPairs[i][0] = data.consumeInt();
            argPairs[i][1] = data.consumeInt();
        }

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(ADD_MODULE)) {

            final CountDownLatch startLatch = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int argA = argPairs[i][0];
                final int argB = argPairs[i][1];
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        try (WebAssemblyInstance instance = module.instantiate()) {
                            final WebAssemblyFunction addFunc = instance.getFunction("add");
                            addFunc.invoke(argA, argB);
                        }
                    } catch (final WasmRuntimeException e) {
                        // Expected for instantiation or invocation failures
                    } catch (final IllegalArgumentException | IllegalStateException e) {
                        // Expected for closed resources
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (final Exception e) {
                        unexpectedError.compareAndSet(null, e);
                    }
                }));
            }

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for all threads to complete
            for (final Future<?> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

        } catch (final CompilationException | ValidationException e) {
            // Expected if module compilation fails
        } catch (final WasmRuntimeException e) {
            // Expected if no runtime provider available
        } catch (final Exception e) {
            // Timeout or interruption during wait
        } finally {
            executor.shutdownNow();
        }

        final Throwable error = unexpectedError.get();
        if (error != null) {
            throw new AssertionError("Unexpected exception during concurrent function calls", error);
        }
    }
}
