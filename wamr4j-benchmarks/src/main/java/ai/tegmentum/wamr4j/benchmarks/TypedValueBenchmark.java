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
 * JMH benchmarks for typed value operations across different WASM value types.
 *
 * <p>This benchmark suite measures the performance differences between i32, i64,
 * f32, and f64 function calls and global variable access. This reveals any
 * type-specific overhead in the JNI/Panama marshalling layer.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TypedValueBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;

    private WebAssemblyFunction jniAddI32;
    private WebAssemblyFunction jniAddI64;
    private WebAssemblyFunction jniAddF32;
    private WebAssemblyFunction jniAddF64;
    private WebAssemblyFunction panamaAddI32;
    private WebAssemblyFunction panamaAddI64;
    private WebAssemblyFunction panamaAddF32;
    private WebAssemblyFunction panamaAddF64;

    /**
     * Sets up JNI and Panama runtimes with a multi-type module.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] typedModule = createTypedModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(typedModule);
            jniInstance = jniModule.instantiate();
            jniAddI32 = jniInstance.getFunction("add_i32");
            jniAddI64 = jniInstance.getFunction("add_i64");
            jniAddF32 = jniInstance.getFunction("add_f32");
            jniAddF64 = jniInstance.getFunction("add_f64");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(typedModule);
                panamaInstance = panamaModule.instantiate();
                panamaAddI32 = panamaInstance.getFunction("add_i32");
                panamaAddI64 = panamaInstance.getFunction("add_i64");
                panamaAddF32 = panamaInstance.getFunction("add_f32");
                panamaAddF64 = panamaInstance.getFunction("add_f64");
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Typed value benchmark setup failed", e);
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
     * Benchmarks JNI i32 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniAddI32(final Blackhole bh) {
        if (jniAddI32 == null) {
            return;
        }
        try {
            bh.consume(jniAddI32.invoke(42, 24));
        } catch (final Exception e) {
            throw new RuntimeException("JNI i32 add failed", e);
        }
    }

    /**
     * Benchmarks Panama i32 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaAddI32(final Blackhole bh) {
        if (panamaAddI32 == null) {
            return;
        }
        try {
            bh.consume(panamaAddI32.invoke(42, 24));
        } catch (final Exception e) {
            throw new RuntimeException("Panama i32 add failed", e);
        }
    }

    /**
     * Benchmarks JNI i64 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniAddI64(final Blackhole bh) {
        if (jniAddI64 == null) {
            return;
        }
        try {
            bh.consume(jniAddI64.invoke(42L, 24L));
        } catch (final Exception e) {
            throw new RuntimeException("JNI i64 add failed", e);
        }
    }

    /**
     * Benchmarks Panama i64 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaAddI64(final Blackhole bh) {
        if (panamaAddI64 == null) {
            return;
        }
        try {
            bh.consume(panamaAddI64.invoke(42L, 24L));
        } catch (final Exception e) {
            throw new RuntimeException("Panama i64 add failed", e);
        }
    }

    /**
     * Benchmarks JNI f32 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniAddF32(final Blackhole bh) {
        if (jniAddF32 == null) {
            return;
        }
        try {
            bh.consume(jniAddF32.invoke(3.14f, 2.72f));
        } catch (final Exception e) {
            throw new RuntimeException("JNI f32 add failed", e);
        }
    }

    /**
     * Benchmarks Panama f32 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaAddF32(final Blackhole bh) {
        if (panamaAddF32 == null) {
            return;
        }
        try {
            bh.consume(panamaAddF32.invoke(3.14f, 2.72f));
        } catch (final Exception e) {
            throw new RuntimeException("Panama f32 add failed", e);
        }
    }

    /**
     * Benchmarks JNI f64 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniAddF64(final Blackhole bh) {
        if (jniAddF64 == null) {
            return;
        }
        try {
            bh.consume(jniAddF64.invoke(3.14159265358979, 2.71828182845904));
        } catch (final Exception e) {
            throw new RuntimeException("JNI f64 add failed", e);
        }
    }

    /**
     * Benchmarks Panama f64 add function call.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaAddF64(final Blackhole bh) {
        if (panamaAddF64 == null) {
            return;
        }
        try {
            bh.consume(panamaAddF64.invoke(3.14159265358979, 2.71828182845904));
        } catch (final Exception e) {
            throw new RuntimeException("Panama f64 add failed", e);
        }
    }

    /**
     * Benchmarks JNI global get for i32.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniGlobalGetI32(final Blackhole bh) {
        if (jniInstance == null) {
            return;
        }
        try {
            bh.consume(jniInstance.getGlobal("g_i32"));
        } catch (final Exception e) {
            throw new RuntimeException("JNI global get i32 failed", e);
        }
    }

    /**
     * Benchmarks Panama global get for i32.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaGlobalGetI32(final Blackhole bh) {
        if (panamaInstance == null) {
            return;
        }
        try {
            bh.consume(panamaInstance.getGlobal("g_i32"));
        } catch (final Exception e) {
            throw new RuntimeException("Panama global get i32 failed", e);
        }
    }

    /**
     * Benchmarks JNI global set for i32.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniGlobalSetI32(final Blackhole bh) {
        if (jniInstance == null) {
            return;
        }
        try {
            jniInstance.setGlobal("g_i32", 42);
            bh.consume(42);
        } catch (final Exception e) {
            throw new RuntimeException("JNI global set i32 failed", e);
        }
    }

    /**
     * Benchmarks Panama global set for i32.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaGlobalSetI32(final Blackhole bh) {
        if (panamaInstance == null) {
            return;
        }
        try {
            panamaInstance.setGlobal("g_i32", 42);
            bh.consume(42);
        } catch (final Exception e) {
            throw new RuntimeException("Panama global set i32 failed", e);
        }
    }

    /**
     * Creates a WASM module with functions and globals for all 4 value types.
     *
     * <p>Module structure (WAT equivalent):
     * <pre>
     * (module
     *   (func (export "add_i32") (param i32 i32) (result i32) (i32.add (local.get 0) (local.get 1)))
     *   (func (export "add_i64") (param i64 i64) (result i64) (i64.add (local.get 0) (local.get 1)))
     *   (func (export "add_f32") (param f32 f32) (result f32) (f32.add (local.get 0) (local.get 1)))
     *   (func (export "add_f64") (param f64 f64) (result f64) (f64.add (local.get 0) (local.get 1)))
     *   (global (export "g_i32") (mut i32) (i32.const 0))
     *   (global (export "g_i64") (mut i64) (i64.const 0))
     *   (global (export "g_f32") (mut f32) (f32.const 0))
     *   (global (export "g_f64") (mut f64) (f64.const 0))
     * )
     * </pre>
     *
     * @return the compiled WASM binary bytes
     */
    private byte[] createTypedModule() {
        try {
            final ByteArrayOutputStream module = new ByteArrayOutputStream();

            // WASM header
            module.write(new byte[]{0x00, 0x61, 0x73, 0x6d}); // magic
            module.write(new byte[]{0x01, 0x00, 0x00, 0x00}); // version

            // Type section: 4 types
            writeSection(module, 0x01, new byte[]{
                0x04, // 4 types
                // Type 0: (i32, i32) -> i32
                0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,
                // Type 1: (i64, i64) -> i64
                0x60, 0x02, 0x7e, 0x7e, 0x01, 0x7e,
                // Type 2: (f32, f32) -> f32
                0x60, 0x02, 0x7d, 0x7d, 0x01, 0x7d,
                // Type 3: (f64, f64) -> f64
                0x60, 0x02, 0x7c, 0x7c, 0x01, 0x7c
            });

            // Function section: 4 functions
            writeSection(module, 0x03, new byte[]{
                0x04, // 4 functions
                0x00, 0x01, 0x02, 0x03
            });

            // Global section: 4 mutable globals
            final ByteArrayOutputStream globals = new ByteArrayOutputStream();
            globals.write(0x04); // 4 globals
            // g_i32: mut i32 = 0
            globals.write(new byte[]{0x7f, 0x01, 0x41, 0x00, 0x0b});
            // g_i64: mut i64 = 0
            globals.write(new byte[]{0x7e, 0x01, 0x42, 0x00, 0x0b});
            // g_f32: mut f32 = 0.0
            globals.write(new byte[]{0x7d, 0x01, 0x43, 0x00, 0x00, 0x00, 0x00, 0x0b});
            // g_f64: mut f64 = 0.0
            globals.write(new byte[]{
                0x7c, 0x01, 0x44,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x0b
            });
            writeSection(module, 0x06, globals.toByteArray());

            // Export section: 4 functions + 4 globals = 8 exports
            final ByteArrayOutputStream exports = new ByteArrayOutputStream();
            exports.write(0x08); // 8 exports

            // Function exports
            writeExport(exports, "add_i32", (byte) 0x00, 0);
            writeExport(exports, "add_i64", (byte) 0x00, 1);
            writeExport(exports, "add_f32", (byte) 0x00, 2);
            writeExport(exports, "add_f64", (byte) 0x00, 3);

            // Global exports
            writeExport(exports, "g_i32", (byte) 0x03, 0);
            writeExport(exports, "g_i64", (byte) 0x03, 1);
            writeExport(exports, "g_f32", (byte) 0x03, 2);
            writeExport(exports, "g_f64", (byte) 0x03, 3);

            writeSection(module, 0x07, exports.toByteArray());

            // Code section: 4 function bodies
            final ByteArrayOutputStream code = new ByteArrayOutputStream();
            code.write(0x04); // 4 functions

            // Func 0: add_i32(a, b) -> a + b
            code.write(new byte[]{
                0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b
            });

            // Func 1: add_i64(a, b) -> a + b
            code.write(new byte[]{
                0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x7c, 0x0b
            });

            // Func 2: add_f32(a, b) -> a + b
            code.write(new byte[]{
                0x07, 0x00, 0x20, 0x00, 0x20, 0x01, (byte) 0x92, 0x0b
            });

            // Func 3: add_f64(a, b) -> a + b
            code.write(new byte[]{
                0x07, 0x00, 0x20, 0x00, 0x20, 0x01, (byte) 0xa0, 0x0b
            });

            writeSection(module, 0x0a, code.toByteArray());

            return module.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create typed module", e);
        }
    }

    private void writeExport(final ByteArrayOutputStream out, final String name,
            final byte kind, final int index) throws IOException {
        final byte[] nameBytes = name.getBytes();
        out.write(nameBytes.length);
        out.write(nameBytes);
        out.write(kind);
        out.write(encodeUleb128(index));
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
