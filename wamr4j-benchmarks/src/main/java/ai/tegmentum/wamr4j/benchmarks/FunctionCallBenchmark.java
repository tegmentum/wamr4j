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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for function call overhead comparison.
 *
 * <p>This benchmark suite measures the raw overhead of calling WebAssembly
 * functions with different parameter counts to assess JNI vs Panama
 * calling conventions. Functions are trivial (identity, add) so the
 * benchmark measures call dispatch overhead, not WASM execution time.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FunctionCallBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;
    private WebAssemblyFunction jniNoArgs;
    private WebAssemblyFunction jniOneArg;
    private WebAssemblyFunction jniTwoArgs;
    private WebAssemblyFunction panamaNoArgs;
    private WebAssemblyFunction panamaOneArg;
    private WebAssemblyFunction panamaTwoArgs;

    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] wasmBytes = createFunctionCallModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(wasmBytes);
            jniInstance = jniModule.instantiate();
            jniNoArgs = jniInstance.getFunction("no_args");
            jniOneArg = jniInstance.getFunction("one_arg");
            jniTwoArgs = jniInstance.getFunction("two_args");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(wasmBytes);
                panamaInstance = panamaModule.instantiate();
                panamaNoArgs = panamaInstance.getFunction("no_args");
                panamaOneArg = panamaInstance.getFunction("one_arg");
                panamaTwoArgs = panamaInstance.getFunction("two_args");
            } catch (final Exception e) {
                panamaRuntime = null;
            }
        } catch (final Exception e) {
            throw new RuntimeException("Function call benchmark setup failed", e);
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (jniInstance != null && !jniInstance.isClosed()) {
            jniInstance.close();
        }
        if (jniModule != null && !jniModule.isClosed()) {
            jniModule.close();
        }
        if (jniRuntime != null && !jniRuntime.isClosed()) {
            jniRuntime.close();
        }
        if (panamaInstance != null && !panamaInstance.isClosed()) {
            panamaInstance.close();
        }
        if (panamaModule != null && !panamaModule.isClosed()) {
            panamaModule.close();
        }
        if (panamaRuntime != null && !panamaRuntime.isClosed()) {
            panamaRuntime.close();
        }
    }

    @Benchmark
    public void benchmarkJniNoArgs(final Blackhole bh) throws Exception {
        if (jniNoArgs == null) {
            return;
        }
        bh.consume(jniNoArgs.invoke());
    }

    @Benchmark
    public void benchmarkPanamaNoArgs(final Blackhole bh) throws Exception {
        if (panamaNoArgs == null) {
            return;
        }
        bh.consume(panamaNoArgs.invoke());
    }

    @Benchmark
    public void benchmarkJniOneArg(final Blackhole bh) throws Exception {
        if (jniOneArg == null) {
            return;
        }
        bh.consume(jniOneArg.invoke(42));
    }

    @Benchmark
    public void benchmarkPanamaOneArg(final Blackhole bh) throws Exception {
        if (panamaOneArg == null) {
            return;
        }
        bh.consume(panamaOneArg.invoke(42));
    }

    @Benchmark
    public void benchmarkJniTwoArgs(final Blackhole bh) throws Exception {
        if (jniTwoArgs == null) {
            return;
        }
        bh.consume(jniTwoArgs.invoke(42, 24));
    }

    @Benchmark
    public void benchmarkPanamaTwoArgs(final Blackhole bh) throws Exception {
        if (panamaTwoArgs == null) {
            return;
        }
        bh.consume(panamaTwoArgs.invoke(42, 24));
    }

    // =========================================================================
    // Batch invoke vs loop comparison benchmarks
    // =========================================================================

    /**
     * Benchmarks JNI batch invokeMultiple for 10 invocations in a single crossing.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniBatchInvoke10(final Blackhole bh) throws Exception {
        if (jniTwoArgs == null) {
            return;
        }
        bh.consume(jniTwoArgs.invokeMultiple(
            new Object[]{1, 2}, new Object[]{3, 4}, new Object[]{5, 6},
            new Object[]{7, 8}, new Object[]{9, 10}, new Object[]{11, 12},
            new Object[]{13, 14}, new Object[]{15, 16}, new Object[]{17, 18},
            new Object[]{19, 20}));
    }

    /**
     * Benchmarks JNI loop invoke for 10 individual invocations (10 crossings).
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniLoopInvoke10(final Blackhole bh) throws Exception {
        if (jniTwoArgs == null) {
            return;
        }
        bh.consume(jniTwoArgs.invoke(1, 2));
        bh.consume(jniTwoArgs.invoke(3, 4));
        bh.consume(jniTwoArgs.invoke(5, 6));
        bh.consume(jniTwoArgs.invoke(7, 8));
        bh.consume(jniTwoArgs.invoke(9, 10));
        bh.consume(jniTwoArgs.invoke(11, 12));
        bh.consume(jniTwoArgs.invoke(13, 14));
        bh.consume(jniTwoArgs.invoke(15, 16));
        bh.consume(jniTwoArgs.invoke(17, 18));
        bh.consume(jniTwoArgs.invoke(19, 20));
    }

    /**
     * Benchmarks Panama batch invokeMultiple for 10 invocations in a single crossing.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaBatchInvoke10(final Blackhole bh) throws Exception {
        if (panamaTwoArgs == null) {
            return;
        }
        bh.consume(panamaTwoArgs.invokeMultiple(
            new Object[]{1, 2}, new Object[]{3, 4}, new Object[]{5, 6},
            new Object[]{7, 8}, new Object[]{9, 10}, new Object[]{11, 12},
            new Object[]{13, 14}, new Object[]{15, 16}, new Object[]{17, 18},
            new Object[]{19, 20}));
    }

    /**
     * Benchmarks Panama loop invoke for 10 individual invocations (10 crossings).
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaLoopInvoke10(final Blackhole bh) throws Exception {
        if (panamaTwoArgs == null) {
            return;
        }
        bh.consume(panamaTwoArgs.invoke(1, 2));
        bh.consume(panamaTwoArgs.invoke(3, 4));
        bh.consume(panamaTwoArgs.invoke(5, 6));
        bh.consume(panamaTwoArgs.invoke(7, 8));
        bh.consume(panamaTwoArgs.invoke(9, 10));
        bh.consume(panamaTwoArgs.invoke(11, 12));
        bh.consume(panamaTwoArgs.invoke(13, 14));
        bh.consume(panamaTwoArgs.invoke(15, 16));
        bh.consume(panamaTwoArgs.invoke(17, 18));
        bh.consume(panamaTwoArgs.invoke(19, 20));
    }

    /**
     * Creates a WASM module with three exported functions:
     * <ul>
     *   <li>{@code no_args: () -> i32} — returns constant 42</li>
     *   <li>{@code one_arg: (i32) -> i32} — returns the argument</li>
     *   <li>{@code two_args: (i32, i32) -> i32} — returns arg0 + arg1</li>
     * </ul>
     */
    private byte[] createFunctionCallModule() {
        return new byte[] {
            // WASM header
            0x00, 0x61, 0x73, 0x6d,  // magic "\0asm"
            0x01, 0x00, 0x00, 0x00,  // version 1

            // === Type section (id=1), size=16 ===
            0x01, 0x10,
            0x03,                    // 3 types
            0x60, 0x00, 0x01, 0x7f,                    // type 0: () -> i32
            0x60, 0x01, 0x7f, 0x01, 0x7f,              // type 1: (i32) -> i32
            0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,        // type 2: (i32, i32) -> i32

            // === Function section (id=3), size=4 ===
            0x03, 0x04,
            0x03, 0x00, 0x01, 0x02,  // 3 functions: types 0, 1, 2

            // === Export section (id=7), size=32 ===
            0x07, 0x20,
            0x03,                    // 3 exports
            0x07, 0x6e, 0x6f, 0x5f, 0x61, 0x72, 0x67, 0x73, 0x00, 0x00, // "no_args" -> func 0
            0x07, 0x6f, 0x6e, 0x65, 0x5f, 0x61, 0x72, 0x67, 0x00, 0x01, // "one_arg" -> func 1
            0x08, 0x74, 0x77, 0x6f, 0x5f, 0x61, 0x72, 0x67, 0x73, 0x00, 0x02, // "two_args" -> func 2

            // === Code section (id=10), size=19 ===
            0x0a, 0x13,
            0x03,                    // 3 function bodies
            0x04, 0x00, 0x41, 0x2a, 0x0b,                          // body 0: i32.const 42, end
            0x04, 0x00, 0x20, 0x00, 0x0b,                          // body 1: local.get 0, end
            0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b,        // body 2: local.get 0, local.get 1, i32.add, end
        };
    }
}
