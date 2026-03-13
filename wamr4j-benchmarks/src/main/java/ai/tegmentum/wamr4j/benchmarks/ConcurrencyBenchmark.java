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

package ai.tegmentum.wamr4j.benchmarks;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for concurrent WebAssembly operations.
 *
 * <p>This benchmark suite measures the thread safety and scalability of
 * WAMR4J under concurrent access patterns. It tests parallel instance
 * creation, parallel function invocation with per-thread instances, and
 * module compilation under contention.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ConcurrencyBenchmark {

    /**
     * A simple add module: (i32, i32) -> i32.
     */
    private static final byte[] ADD_MODULE = {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b
    };

    /**
     * Shared state across all threads: runtime and compiled module.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        private WebAssemblyRuntime jniRuntime;
        private WebAssemblyModule jniModule;

        /**
         * Sets up the shared JNI runtime and compiles the module once.
         */
        @Setup(Level.Trial)
        public void setup() {
            try {
                System.setProperty("wamr4j.runtime", "jni");
                jniRuntime = RuntimeFactory.createRuntime();
                jniModule = jniRuntime.compile(ADD_MODULE);
            } catch (final Exception e) {
                throw new RuntimeException("Concurrency benchmark shared setup failed", e);
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }

        /**
         * Tears down the shared runtime and module.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (jniModule != null && !jniModule.isClosed()) {
                jniModule.close();
            }
            if (jniRuntime != null && !jniRuntime.isClosed()) {
                jniRuntime.close();
            }
        }

        /**
         * Returns the compiled module.
         *
         * @return the compiled WebAssembly module
         */
        public WebAssemblyModule getModule() {
            return jniModule;
        }

        /**
         * Returns the runtime.
         *
         * @return the WebAssembly runtime
         */
        public WebAssemblyRuntime getRuntime() {
            return jniRuntime;
        }
    }

    /**
     * Per-thread state: each thread gets its own instance.
     */
    @State(Scope.Thread)
    public static class ThreadState {

        private WebAssemblyInstance instance;
        private WebAssemblyFunction addFunction;

        /**
         * Creates a per-thread instance from the shared module.
         *
         * @param shared the shared benchmark state
         */
        @Setup(Level.Iteration)
        public void setup(final SharedState shared) {
            try {
                instance = shared.getModule().instantiate();
                addFunction = instance.getFunction("add");
            } catch (final Exception e) {
                throw new RuntimeException("Thread state setup failed", e);
            }
        }

        /**
         * Tears down the per-thread instance.
         */
        @TearDown(Level.Iteration)
        public void tearDown() {
            if (instance != null && !instance.isClosed()) {
                instance.close();
            }
        }

        /**
         * Returns the add function.
         *
         * @return the add function handle
         */
        public WebAssemblyFunction getAddFunction() {
            return addFunction;
        }
    }

    /**
     * Benchmarks parallel function calls with 4 threads, each using its own instance.
     *
     * @param threadState the per-thread state with an isolated instance
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    @Threads(4)
    public void benchmarkParallelFunctionCalls4Threads(final ThreadState threadState,
            final Blackhole bh) {
        if (threadState.getAddFunction() == null) {
            return;
        }

        try {
            final Object result = threadState.getAddFunction().invoke(42, 24);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Parallel function call failed", e);
        }
    }

    /**
     * Benchmarks parallel function calls with 8 threads, each using its own instance.
     *
     * @param threadState the per-thread state with an isolated instance
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    @Threads(8)
    public void benchmarkParallelFunctionCalls8Threads(final ThreadState threadState,
            final Blackhole bh) {
        if (threadState.getAddFunction() == null) {
            return;
        }

        try {
            final Object result = threadState.getAddFunction().invoke(42, 24);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Parallel function call failed", e);
        }
    }

    /**
     * Benchmarks parallel instance creation from a shared module with 4 threads.
     *
     * @param shared the shared benchmark state with the compiled module
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    @Threads(4)
    public void benchmarkParallelInstantiation4Threads(final SharedState shared,
            final Blackhole bh) {
        if (shared.getModule() == null) {
            return;
        }

        try (final WebAssemblyInstance instance = shared.getModule().instantiate()) {
            bh.consume(instance);
        } catch (final Exception e) {
            throw new RuntimeException("Parallel instantiation failed", e);
        }
    }

    /**
     * Benchmarks parallel module compilation with 4 threads sharing a runtime.
     *
     * @param shared the shared benchmark state with the runtime
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    @Threads(4)
    public void benchmarkParallelCompilation4Threads(final SharedState shared,
            final Blackhole bh) {
        if (shared.getRuntime() == null) {
            return;
        }

        try (final WebAssemblyModule module = shared.getRuntime().compile(ADD_MODULE)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("Parallel compilation failed", e);
        }
    }

    /**
     * Single-threaded baseline for function calls (for comparison).
     *
     * @param threadState the per-thread state with an isolated instance
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    @Threads(1)
    public void benchmarkBaselineSingleThread(final ThreadState threadState,
            final Blackhole bh) {
        if (threadState.getAddFunction() == null) {
            return;
        }

        try {
            final Object result = threadState.getAddFunction().invoke(42, 24);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Baseline function call failed", e);
        }
    }
}
