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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * functions with different parameter counts and types to assess JNI vs Panama
 * calling conventions.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.OPERATIONS)
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
    private WebAssemblyFunction jniFourArgs;
    private WebAssemblyFunction jniEightArgs;
    private WebAssemblyFunction panamaNoArgs;
    private WebAssemblyFunction panamaOneArg;
    private WebAssemblyFunction panamaTwoArgs;
    private WebAssemblyFunction panamaFourArgs;
    private WebAssemblyFunction panamaEightArgs;

    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] callModule = createFunctionCallModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(callModule);
            jniInstance = jniModule.instantiate();
            jniNoArgs = jniInstance.getFunction("no_args");
            jniOneArg = jniInstance.getFunction("one_arg");
            jniTwoArgs = jniInstance.getFunction("two_args");
            jniFourArgs = jniInstance.getFunction("four_args");
            jniEightArgs = jniInstance.getFunction("eight_args");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(callModule);
                panamaInstance = panamaModule.instantiate();
                panamaNoArgs = panamaInstance.getFunction("no_args");
                panamaOneArg = panamaInstance.getFunction("one_arg");
                panamaTwoArgs = panamaInstance.getFunction("two_args");
                panamaFourArgs = panamaInstance.getFunction("four_args");
                panamaEightArgs = panamaInstance.getFunction("eight_args");
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Function call benchmark setup failed", e);
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
    public void benchmarkJniNoArgs(final Blackhole bh) {
        if (jniNoArgs == null) {
            return;
        }

        try {
            final Object result = jniNoArgs.invoke();
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI no args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaNoArgs(final Blackhole bh) {
        if (panamaNoArgs == null) {
            return;
        }

        try {
            final Object result = panamaNoArgs.invoke();
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama no args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniOneArg(final Blackhole bh) {
        if (jniOneArg == null) {
            return;
        }

        try {
            final Object result = jniOneArg.invoke(42);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI one arg benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaOneArg(final Blackhole bh) {
        if (panamaOneArg == null) {
            return;
        }

        try {
            final Object result = panamaOneArg.invoke(42);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama one arg benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniTwoArgs(final Blackhole bh) {
        if (jniTwoArgs == null) {
            return;
        }

        try {
            final Object result = jniTwoArgs.invoke(42, 24);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI two args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaTwoArgs(final Blackhole bh) {
        if (panamaTwoArgs == null) {
            return;
        }

        try {
            final Object result = panamaTwoArgs.invoke(42, 24);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama two args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniFourArgs(final Blackhole bh) {
        if (jniFourArgs == null) {
            return;
        }

        try {
            final Object result = jniFourArgs.invoke(10, 20, 30, 40);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI four args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaFourArgs(final Blackhole bh) {
        if (panamaFourArgs == null) {
            return;
        }

        try {
            final Object result = panamaFourArgs.invoke(10, 20, 30, 40);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama four args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniEightArgs(final Blackhole bh) {
        if (jniEightArgs == null) {
            return;
        }

        try {
            final Object result = jniEightArgs.invoke(1, 2, 3, 4, 5, 6, 7, 8);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI eight args benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaEightArgs(final Blackhole bh) {
        if (panamaEightArgs == null) {
            return;
        }

        try {
            final Object result = panamaEightArgs.invoke(1, 2, 3, 4, 5, 6, 7, 8);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama eight args benchmark failed", e);
        }
    }

    private byte[] createFunctionCallModule() {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // WASM magic number and version
            baos.write(new byte[] {0x00, 0x61, 0x73, 0x6d}); // magic
            baos.write(new byte[] {0x01, 0x00, 0x00, 0x00}); // version

            // Type section: 5 function types
            baos.write(new byte[] {0x01}); // type section
            baos.write(new byte[] {0x1e}); // section size
            baos.write(new byte[] {0x05}); // 5 types

            // Type 0: () -> i32
            baos.write(new byte[] {0x60, 0x00, 0x01, 0x7f});

            // Type 1: (i32) -> i32
            baos.write(new byte[] {0x60, 0x01, 0x7f, 0x01, 0x7f});

            // Type 2: (i32, i32) -> i32
            baos.write(new byte[] {0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f});

            // Type 3: (i32, i32, i32, i32) -> i32
            baos.write(new byte[] {0x60, 0x04, 0x7f, 0x7f, 0x7f, 0x7f, 0x01, 0x7f});

            // Type 4: (i32, i32, i32, i32, i32, i32, i32, i32) -> i32
            baos.write(
                    new byte[] {0x60, 0x08, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x01,
                            0x7f});

            // Function section: 5 functions
            baos.write(new byte[] {0x03}); // function section
            baos.write(new byte[] {0x06}); // section size
            baos.write(new byte[] {0x05}); // 5 functions
            baos.write(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04}); // function types

            // Export section: export all 5 functions
            baos.write(new byte[] {0x07}); // export section
            baos.write(new byte[] {0x3f}); // section size
            baos.write(new byte[] {0x05}); // 5 exports

            // Export "no_args"
            baos.write(new byte[] {0x07}); // name length
            baos.write("no_args".getBytes());
            baos.write(new byte[] {0x00, 0x00}); // function 0

            // Export "one_arg"
            baos.write(new byte[] {0x07}); // name length
            baos.write("one_arg".getBytes());
            baos.write(new byte[] {0x00, 0x01}); // function 1

            // Export "two_args"
            baos.write(new byte[] {0x08}); // name length
            baos.write("two_args".getBytes());
            baos.write(new byte[] {0x00, 0x02}); // function 2

            // Export "four_args"
            baos.write(new byte[] {0x09}); // name length
            baos.write("four_args".getBytes());
            baos.write(new byte[] {0x00, 0x03}); // function 3

            // Export "eight_args"
            baos.write(new byte[] {0x0a}); // name length
            baos.write("eight_args".getBytes());
            baos.write(new byte[] {0x00, 0x04}); // function 4

            // Code section: function bodies
            baos.write(new byte[] {0x0a}); // code section
            baos.write(new byte[] {0x3e}); // section size
            baos.write(new byte[] {0x05}); // 5 functions

            // Function 0: no_args() -> 42
            baos.write(new byte[] {
                0x04, // body size
                0x00, // local count
                0x41, 0x2a, // i32.const 42
                0x0b // end
            });

            // Function 1: one_arg(a) -> a
            baos.write(new byte[] {
                0x04, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0
                0x0b // end
            });

            // Function 2: two_args(a, b) -> a + b
            baos.write(new byte[] {
                0x07, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x6a, // i32.add
                0x0b // end
            });

            // Function 3: four_args(a, b, c, d) -> a + b + c + d
            baos.write(new byte[] {
                0x0d, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x6a, // i32.add
                0x20, 0x02, // local.get 2
                0x6a, // i32.add
                0x20, 0x03, // local.get 3
                0x6a, // i32.add
                0x0b // end
            });

            // Function 4: eight_args(a-h) -> sum of all
            baos.write(new byte[] {
                0x19, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x6a, // i32.add
                0x20, 0x02, // local.get 2
                0x6a, // i32.add
                0x20, 0x03, // local.get 3
                0x6a, // i32.add
                0x20, 0x04, // local.get 4
                0x6a, // i32.add
                0x20, 0x05, // local.get 5
                0x6a, // i32.add
                0x20, 0x06, // local.get 6
                0x6a, // i32.add
                0x20, 0x07, // local.get 7
                0x6a, // i32.add
                0x0b // end
            });

            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create function call module", e);
        }
    }
}
