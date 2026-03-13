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
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
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
 * JMH benchmarks for Java-side typed memory access methods (readInt32, writeInt32, etc.).
 *
 * <p>This benchmark measures the overhead of the Java memory access API,
 * comparing JNI (which crosses into native for each typed read/write) against
 * Panama (which uses a cached DirectByteBuffer). This reveals the cost of
 * JNI boundary crossings for simple memory operations.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MemoryAccessBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;
    private WebAssemblyMemory jniMemory;
    private WebAssemblyMemory panamaMemory;

    /** Test data for bulk read/write benchmarks (1KB). */
    private byte[] bulkTestData;

    /**
     * Sets up JNI and Panama runtimes with a minimal module that exports memory.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] wasmBytes = createMemoryModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(wasmBytes);
            jniInstance = jniModule.instantiate();
            jniMemory = jniInstance.getMemory();
            // Warm the memory by writing initial values
            jniMemory.writeInt32(0, 42);

            // Create test data for bulk benchmarks
            bulkTestData = new byte[1024];
            for (int i = 0; i < bulkTestData.length; i++) {
                bulkTestData[i] = (byte) (i & 0xFF);
            }
            jniMemory.write(0, bulkTestData);

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(wasmBytes);
                panamaInstance = panamaModule.instantiate();
                panamaMemory = panamaInstance.getMemory();
                panamaMemory.writeInt32(0, 42);
            } catch (final Exception e) {
                panamaRuntime = null;
            }
        } catch (final Exception e) {
            throw new RuntimeException("Memory access benchmark setup failed", e);
        } finally {
            System.clearProperty("wamr4j.runtime");
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
     * Benchmarks JNI readInt32 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkJniReadInt32(final Blackhole bh) throws Exception {
        if (jniMemory == null) {
            return;
        }
        bh.consume(jniMemory.readInt32(0));
    }

    /**
     * Benchmarks Panama readInt32 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkPanamaReadInt32(final Blackhole bh) throws Exception {
        if (panamaMemory == null) {
            return;
        }
        bh.consume(panamaMemory.readInt32(0));
    }

    /**
     * Benchmarks JNI writeInt32 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the write fails
     */
    @Benchmark
    public void benchmarkJniWriteInt32(final Blackhole bh) throws Exception {
        if (jniMemory == null) {
            return;
        }
        jniMemory.writeInt32(0, 42);
        bh.consume(42);
    }

    /**
     * Benchmarks Panama writeInt32 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the write fails
     */
    @Benchmark
    public void benchmarkPanamaWriteInt32(final Blackhole bh) throws Exception {
        if (panamaMemory == null) {
            return;
        }
        panamaMemory.writeInt32(0, 42);
        bh.consume(42);
    }

    /**
     * Benchmarks JNI readInt64 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkJniReadInt64(final Blackhole bh) throws Exception {
        if (jniMemory == null) {
            return;
        }
        bh.consume(jniMemory.readInt64(0));
    }

    /**
     * Benchmarks Panama readInt64 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkPanamaReadInt64(final Blackhole bh) throws Exception {
        if (panamaMemory == null) {
            return;
        }
        bh.consume(panamaMemory.readInt64(0));
    }

    /**
     * Benchmarks JNI readFloat64 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkJniReadFloat64(final Blackhole bh) throws Exception {
        if (jniMemory == null) {
            return;
        }
        bh.consume(jniMemory.readFloat64(0));
    }

    /**
     * Benchmarks Panama readFloat64 throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkPanamaReadFloat64(final Blackhole bh) throws Exception {
        if (panamaMemory == null) {
            return;
        }
        bh.consume(panamaMemory.readFloat64(0));
    }

    /**
     * Benchmarks JNI bulk read (1KB) throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkJniBulkRead1K(final Blackhole bh) throws Exception {
        if (jniMemory == null) {
            return;
        }
        bh.consume(jniMemory.read(0, 1024));
    }

    /**
     * Benchmarks Panama bulk read (1KB) throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the read fails
     */
    @Benchmark
    public void benchmarkPanamaBulkRead1K(final Blackhole bh) throws Exception {
        if (panamaMemory == null) {
            return;
        }
        bh.consume(panamaMemory.read(0, 1024));
    }

    /**
     * Benchmarks JNI bulk write (1KB) throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the write fails
     */
    @Benchmark
    public void benchmarkJniBulkWrite1K(final Blackhole bh) throws Exception {
        if (jniMemory == null) {
            return;
        }
        jniMemory.write(0, bulkTestData);
        bh.consume(bulkTestData);
    }

    /**
     * Benchmarks Panama bulk write (1KB) throughput.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     * @throws Exception if the write fails
     */
    @Benchmark
    public void benchmarkPanamaBulkWrite1K(final Blackhole bh) throws Exception {
        if (panamaMemory == null) {
            return;
        }
        panamaMemory.write(0, bulkTestData);
        bh.consume(bulkTestData);
    }

    /**
     * Creates a minimal WASM module with 1 page of exported memory.
     *
     * <p>WAT equivalent:
     * <pre>
     * (module
     *   (memory (export "memory") 1)
     * )
     * </pre>
     *
     * @return the compiled WASM binary bytes
     */
    private byte[] createMemoryModule() {
        return new byte[] {
            // WASM header
            0x00, 0x61, 0x73, 0x6d,  // magic "\0asm"
            0x01, 0x00, 0x00, 0x00,  // version 1

            // Memory section (id=5), size=3
            0x05, 0x03,
            0x01,                    // 1 memory
            0x00, 0x01,              // min 1 page, no max

            // Export section (id=7), size=10
            0x07, 0x0a,
            0x01,                    // 1 export
            0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79,  // "memory"
            0x02, 0x00,              // memory export, index 0
        };
    }
}
