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
import ai.tegmentum.wamr4j.WebAssemblyTable;
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
 * JMH benchmarks for WebAssembly table operations and indirect calls.
 *
 * <p>This benchmark suite measures the performance of table element access
 * and indirect function calls ({@code call_indirect}) which are critical for
 * compiled languages that use function pointers or virtual dispatch.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TableOperationsBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;
    private WebAssemblyTable jniTable;
    private WebAssemblyTable panamaTable;
    private WebAssemblyFunction jniDispatch;
    private WebAssemblyFunction panamaDispatch;

    /**
     * Sets up JNI and Panama runtimes with a table-based module.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] tableModule = createTableModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(tableModule);
            jniInstance = jniModule.instantiate();
            jniTable = jniInstance.getTable("table");
            jniDispatch = jniInstance.getFunction("dispatch");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(tableModule);
                panamaInstance = panamaModule.instantiate();
                panamaTable = panamaInstance.getTable("table");
                panamaDispatch = panamaInstance.getFunction("dispatch");
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Table benchmark setup failed", e);
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
     * Benchmarks JNI table size query.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniTableGetSize(final Blackhole bh) {
        if (jniTable == null) {
            return;
        }

        bh.consume(jniTable.getSize());
    }

    /**
     * Benchmarks Panama table size query.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaTableGetSize(final Blackhole bh) {
        if (panamaTable == null) {
            return;
        }

        bh.consume(panamaTable.getSize());
    }

    /**
     * Benchmarks JNI table element access by index.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniTableGetFunction(final Blackhole bh) {
        if (jniTable == null) {
            return;
        }

        try {
            final WebAssemblyFunction func = jniTable.getFunctionAtIndex(0);
            bh.consume(func);
        } catch (final Exception e) {
            throw new RuntimeException("JNI table get function failed", e);
        }
    }

    /**
     * Benchmarks Panama table element access by index.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaTableGetFunction(final Blackhole bh) {
        if (panamaTable == null) {
            return;
        }

        try {
            final WebAssemblyFunction func = panamaTable.getFunctionAtIndex(0);
            bh.consume(func);
        } catch (final Exception e) {
            throw new RuntimeException("Panama table get function failed", e);
        }
    }

    /**
     * Benchmarks JNI indirect function call via dispatch wrapper.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniCallIndirect(final Blackhole bh) {
        if (jniDispatch == null) {
            return;
        }

        try {
            // dispatch(10, 20, 0) calls table[0] which is the add function
            final Object result = jniDispatch.invoke(10, 20, 0);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI call_indirect benchmark failed", e);
        }
    }

    /**
     * Benchmarks Panama indirect function call via dispatch wrapper.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaCallIndirect(final Blackhole bh) {
        if (panamaDispatch == null) {
            return;
        }

        try {
            // dispatch(10, 20, 0) calls table[0] which is the add function
            final Object result = panamaDispatch.invoke(10, 20, 0);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama call_indirect benchmark failed", e);
        }
    }

    /**
     * Creates a WASM module with a function table and indirect call support.
     *
     * <p>Module structure (WAT equivalent):
     * <pre>
     * (module
     *   (type $binop (func (param i32 i32) (result i32)))
     *   (type $dispatch_t (func (param i32 i32 i32) (result i32)))
     *   (func $add (type $binop) (local.get 0) (local.get 1) (i32.add))
     *   (func $sub (type $binop) (local.get 0) (local.get 1) (i32.sub))
     *   (func $mul (type $binop) (local.get 0) (local.get 1) (i32.mul))
     *   (table (export "table") 4 funcref)
     *   (elem (i32.const 0) func $add $sub $mul)
     *   (func (export "dispatch") (type $dispatch_t)
     *     (call_indirect (type $binop) (local.get 0) (local.get 1) (local.get 2)))
     * )
     * </pre>
     *
     * @return the compiled WASM binary bytes
     */
    private byte[] createTableModule() {
        try {
            final ByteArrayOutputStream module = new ByteArrayOutputStream();

            // WASM header
            module.write(new byte[]{0x00, 0x61, 0x73, 0x6d}); // magic
            module.write(new byte[]{0x01, 0x00, 0x00, 0x00}); // version

            // Type section: 2 types
            writeSection(module, 0x01, new byte[]{
                0x02, // 2 types
                // Type 0: (i32, i32) -> i32
                0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,
                // Type 1: (i32, i32, i32) -> i32
                0x60, 0x03, 0x7f, 0x7f, 0x7f, 0x01, 0x7f
            });

            // Function section: 4 functions (add=0, sub=1, mul=2, dispatch=3)
            writeSection(module, 0x03, new byte[]{
                0x04, // 4 functions
                0x00, 0x00, 0x00, 0x01 // types: 0, 0, 0, 1
            });

            // Table section: 1 table, min=4, funcref
            writeSection(module, 0x04, new byte[]{
                0x01, // 1 table
                0x70, // funcref
                0x00, 0x04 // limits: min=4, no max
            });

            // Export section
            final ByteArrayOutputStream exports = new ByteArrayOutputStream();
            exports.write(0x02); // 2 exports
            // Export "table" as table 0
            exports.write(0x05); // name length
            exports.write("table".getBytes());
            exports.write(0x01); // table export kind
            exports.write(0x00); // table index
            // Export "dispatch" as func 3
            exports.write(0x08); // name length
            exports.write("dispatch".getBytes());
            exports.write(0x00); // function export kind
            exports.write(0x03); // function index
            writeSection(module, 0x07, exports.toByteArray());

            // Element section: populate table with func refs
            writeSection(module, 0x09, new byte[]{
                0x01, // 1 element segment
                0x00, // flags: active, table 0
                0x41, 0x00, 0x0b, // offset: i32.const 0, end
                0x03, // 3 function indices
                0x00, 0x01, 0x02 // func 0 (add), func 1 (sub), func 2 (mul)
            });

            // Code section: 4 function bodies
            final ByteArrayOutputStream code = new ByteArrayOutputStream();
            code.write(0x04); // 4 functions

            // Func 0: add(a, b) -> a + b
            code.write(new byte[]{
                0x07, // body size
                0x00, // 0 locals
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x6a, // i32.add
                0x0b // end
            });

            // Func 1: sub(a, b) -> a - b
            code.write(new byte[]{
                0x07, // body size
                0x00, // 0 locals
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x6b, // i32.sub
                0x0b // end
            });

            // Func 2: mul(a, b) -> a * b
            code.write(new byte[]{
                0x07, // body size
                0x00, // 0 locals
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x6c, // i32.mul
                0x0b // end
            });

            // Func 3: dispatch(a, b, idx) -> call_indirect(a, b, table[idx])
            code.write(new byte[]{
                0x0b, // body size
                0x00, // 0 locals
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x20, 0x02, // local.get 2 (table index)
                0x11, 0x00, 0x00, // call_indirect type=0, table=0
                0x0b // end
            });

            writeSection(module, 0x0a, code.toByteArray());

            return module.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create table module", e);
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
