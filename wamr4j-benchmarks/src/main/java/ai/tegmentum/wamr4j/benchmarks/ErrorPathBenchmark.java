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
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
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
 * JMH benchmarks for WebAssembly error and trap handling overhead.
 *
 * <p>This benchmark suite measures the cost of trap handling paths including
 * unreachable instructions, integer division by zero, and out-of-bounds
 * memory access. A no-trap baseline is included for comparison.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ErrorPathBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;
    private WebAssemblyFunction jniNoTrap;
    private WebAssemblyFunction jniTrapUnreachable;
    private WebAssemblyFunction jniTrapDivZero;
    private WebAssemblyFunction jniTrapOob;
    private WebAssemblyFunction panamaNoTrap;
    private WebAssemblyFunction panamaTrapUnreachable;
    private WebAssemblyFunction panamaTrapDivZero;
    private WebAssemblyFunction panamaTrapOob;

    /**
     * Sets up JNI and Panama runtimes with a module containing trap functions.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] trapModule = createTrapModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(trapModule);
            jniInstance = jniModule.instantiate();
            jniNoTrap = jniInstance.getFunction("no_trap");
            jniTrapUnreachable = jniInstance.getFunction("trap_unreachable");
            jniTrapDivZero = jniInstance.getFunction("trap_div_zero");
            jniTrapOob = jniInstance.getFunction("trap_oob");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(trapModule);
                panamaInstance = panamaModule.instantiate();
                panamaNoTrap = panamaInstance.getFunction("no_trap");
                panamaTrapUnreachable = panamaInstance.getFunction("trap_unreachable");
                panamaTrapDivZero = panamaInstance.getFunction("trap_div_zero");
                panamaTrapOob = panamaInstance.getFunction("trap_oob");
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Error path benchmark setup failed", e);
        }
    }

    /**
     * Tears down all runtime resources.
     */
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

    /**
     * Benchmarks JNI successful function call (baseline, no trap).
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniNoTrapBaseline(final Blackhole bh) {
        if (jniNoTrap == null) {
            return;
        }

        try {
            final Object result = jniNoTrap.invoke();
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI no-trap baseline failed unexpectedly", e);
        }
    }

    /**
     * Benchmarks Panama successful function call (baseline, no trap).
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaNoTrapBaseline(final Blackhole bh) {
        if (panamaNoTrap == null) {
            return;
        }

        try {
            final Object result = panamaNoTrap.invoke();
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama no-trap baseline failed unexpectedly", e);
        }
    }

    /**
     * Benchmarks JNI unreachable trap handling.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniTrapUnreachable(final Blackhole bh) {
        if (jniTrapUnreachable == null) {
            return;
        }

        try {
            jniTrapUnreachable.invoke();
        } catch (final WasmRuntimeException e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks Panama unreachable trap handling.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaTrapUnreachable(final Blackhole bh) {
        if (panamaTrapUnreachable == null) {
            return;
        }

        try {
            panamaTrapUnreachable.invoke();
        } catch (final WasmRuntimeException e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks JNI integer division by zero trap handling.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniTrapDivByZero(final Blackhole bh) {
        if (jniTrapDivZero == null) {
            return;
        }

        try {
            jniTrapDivZero.invoke();
        } catch (final WasmRuntimeException e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks Panama integer division by zero trap handling.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaTrapDivByZero(final Blackhole bh) {
        if (panamaTrapDivZero == null) {
            return;
        }

        try {
            panamaTrapDivZero.invoke();
        } catch (final WasmRuntimeException e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks JNI out-of-bounds memory access trap handling.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniTrapOutOfBounds(final Blackhole bh) {
        if (jniTrapOob == null) {
            return;
        }

        try {
            jniTrapOob.invoke();
        } catch (final WasmRuntimeException e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks Panama out-of-bounds memory access trap handling.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaTrapOutOfBounds(final Blackhole bh) {
        if (panamaTrapOob == null) {
            return;
        }

        try {
            panamaTrapOob.invoke();
        } catch (final WasmRuntimeException e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks JNI compilation of invalid WASM bytes.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniInvalidModuleCompilation(final Blackhole bh) {
        if (jniRuntime == null) {
            return;
        }

        try {
            jniRuntime.compile(new byte[]{0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
                    (byte) 0xFF});
        } catch (final Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Benchmarks Panama compilation of invalid WASM bytes.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaInvalidModuleCompilation(final Blackhole bh) {
        if (panamaRuntime == null) {
            return;
        }

        try {
            panamaRuntime.compile(new byte[]{0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
                    (byte) 0xFF});
        } catch (final Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Creates a WASM module with functions that trigger various traps.
     *
     * <p>Module structure (WAT equivalent):
     * <pre>
     * (module
     *   (memory 1)
     *   (func (export "no_trap") (result i32) (i32.const 42))
     *   (func (export "trap_unreachable") (unreachable))
     *   (func (export "trap_div_zero") (result i32) (i32.div_s (i32.const 1) (i32.const 0)))
     *   (func (export "trap_oob") (result i32) (i32.load (i32.const 65536)))
     * )
     * </pre>
     *
     * @return the compiled WASM binary bytes
     */
    private byte[] createTrapModule() {
        try {
            final ByteArrayOutputStream module = new ByteArrayOutputStream();

            // WASM header
            module.write(new byte[]{0x00, 0x61, 0x73, 0x6d}); // magic
            module.write(new byte[]{0x01, 0x00, 0x00, 0x00}); // version

            // Type section: 2 types
            writeSection(module, 0x01, new byte[]{
                0x02, // 2 types
                // Type 0: () -> i32
                0x60, 0x00, 0x01, 0x7f,
                // Type 1: () -> ()
                0x60, 0x00, 0x00
            });

            // Function section: 4 functions
            writeSection(module, 0x03, new byte[]{
                0x04, // 4 functions
                0x00, // no_trap: type 0 (() -> i32)
                0x01, // trap_unreachable: type 1 (() -> ())
                0x00, // trap_div_zero: type 0 (() -> i32)
                0x00  // trap_oob: type 0 (() -> i32)
            });

            // Memory section: 1 page
            writeSection(module, 0x05, new byte[]{
                0x01, // 1 memory
                0x00, 0x01 // min=1 page, no max
            });

            // Export section: 4 exports
            final ByteArrayOutputStream exports = new ByteArrayOutputStream();
            exports.write(0x04); // 4 exports

            // "no_trap" -> func 0
            exports.write(0x07);
            exports.write("no_trap".getBytes());
            exports.write(new byte[]{0x00, 0x00});

            // "trap_unreachable" -> func 1
            exports.write(0x10);
            exports.write("trap_unreachable".getBytes());
            exports.write(new byte[]{0x00, 0x01});

            // "trap_div_zero" -> func 2
            exports.write(0x0d);
            exports.write("trap_div_zero".getBytes());
            exports.write(new byte[]{0x00, 0x02});

            // "trap_oob" -> func 3
            exports.write(0x08);
            exports.write("trap_oob".getBytes());
            exports.write(new byte[]{0x00, 0x03});

            writeSection(module, 0x07, exports.toByteArray());

            // Code section: 4 function bodies
            final ByteArrayOutputStream code = new ByteArrayOutputStream();
            code.write(0x04); // 4 functions

            // Func 0: no_trap() -> 42
            code.write(new byte[]{
                0x04, // body size
                0x00, // 0 locals
                0x41, 0x2a, // i32.const 42
                0x0b // end
            });

            // Func 1: trap_unreachable()
            code.write(new byte[]{
                0x03, // body size
                0x00, // 0 locals
                0x00, // unreachable
                0x0b // end
            });

            // Func 2: trap_div_zero() -> i32
            code.write(new byte[]{
                0x07, // body size
                0x00, // 0 locals
                0x41, 0x01, // i32.const 1
                0x41, 0x00, // i32.const 0
                0x6d, // i32.div_s
                0x0b // end
            });

            // Func 3: trap_oob() -> i32 (load from address 65536, beyond 1 page)
            code.write(new byte[]{
                0x09, // body size (1 + 4 + 3 + 1 = 9)
                0x00, // 0 locals
                0x41, (byte) 0x80, (byte) 0x80, 0x04, // i32.const 65536 (LEB128)
                0x28, 0x02, 0x00, // i32.load align=2 offset=0
                0x0b // end
            });

            writeSection(module, 0x0a, code.toByteArray());

            return module.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create trap module", e);
        }
    }

    private void writeSection(final ByteArrayOutputStream out, final int sectionId,
            final byte[] payload) throws IOException {
        out.write(sectionId);
        out.write(encodeUleb128(payload.length));
        out.write(payload);
    }

    private byte[] encodeUleb128(final int value) {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int remaining = value;
        do {
            byte b = (byte) (remaining & 0x7f);
            remaining >>>= 7;
            if (remaining != 0) {
                b |= (byte) 0x80;
            }
            buf.write(b);
        } while (remaining != 0);
        return buf.toByteArray();
    }
}
