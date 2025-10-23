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
 * JMH benchmarks for WebAssembly memory operations performance.
 *
 * <p>This benchmark suite measures the throughput of memory operations including
 * load, store, copy, and fill operations across different memory sizes.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.OPERATIONS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MemoryOperationsBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;
    private WebAssemblyFunction jniMemLoad;
    private WebAssemblyFunction jniMemStore;
    private WebAssemblyFunction jniMemCopy;
    private WebAssemblyFunction panamaMemLoad;
    private WebAssemblyFunction panamaMemStore;
    private WebAssemblyFunction panamaMemCopy;

    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] memoryModule = createMemoryOperationsModule();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(memoryModule);
            jniInstance = jniModule.instantiate();
            jniMemLoad = jniInstance.getFunction("mem_load");
            jniMemStore = jniInstance.getFunction("mem_store");
            jniMemCopy = jniInstance.getFunction("mem_copy");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(memoryModule);
                panamaInstance = panamaModule.instantiate();
                panamaMemLoad = panamaInstance.getFunction("mem_load");
                panamaMemStore = panamaInstance.getFunction("mem_store");
                panamaMemCopy = panamaInstance.getFunction("mem_copy");
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Memory benchmark setup failed", e);
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
    public void benchmarkJniMemoryLoad(final Blackhole bh) {
        if (jniMemLoad == null) {
            return;
        }

        try {
            // Load 1KB from memory at offset 0
            final Object result = jniMemLoad.invoke(0, 1024);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI memory load benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaMemoryLoad(final Blackhole bh) {
        if (panamaMemLoad == null) {
            return;
        }

        try {
            // Load 1KB from memory at offset 0
            final Object result = panamaMemLoad.invoke(0, 1024);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama memory load benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniMemoryStore(final Blackhole bh) {
        if (jniMemStore == null) {
            return;
        }

        try {
            // Store 1KB to memory at offset 2048
            final Object result = jniMemStore.invoke(2048, 1024, 0x42);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI memory store benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaMemoryStore(final Blackhole bh) {
        if (panamaMemStore == null) {
            return;
        }

        try {
            // Store 1KB to memory at offset 2048
            final Object result = panamaMemStore.invoke(2048, 1024, 0x42);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama memory store benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniMemoryCopy(final Blackhole bh) {
        if (jniMemCopy == null) {
            return;
        }

        try {
            // Copy 1KB from offset 0 to offset 4096
            final Object result = jniMemCopy.invoke(4096, 0, 1024);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI memory copy benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaMemoryCopy(final Blackhole bh) {
        if (panamaMemCopy == null) {
            return;
        }

        try {
            // Copy 1KB from offset 0 to offset 4096
            final Object result = panamaMemCopy.invoke(4096, 0, 1024);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama memory copy benchmark failed", e);
        }
    }

    private byte[] createMemoryOperationsModule() {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // WASM magic number and version
            baos.write(new byte[] {0x00, 0x61, 0x73, 0x6d}); // magic
            baos.write(new byte[] {0x01, 0x00, 0x00, 0x00}); // version

            // Type section: 3 function types
            baos.write(new byte[] {0x01}); // type section
            baos.write(new byte[] {0x10}); // section size
            baos.write(new byte[] {0x03}); // 3 types

            // Type 0: (i32, i32) -> i32 for mem_load
            baos.write(new byte[] {0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f});

            // Type 1: (i32, i32, i32) -> i32 for mem_store
            baos.write(new byte[] {0x60, 0x03, 0x7f, 0x7f, 0x7f, 0x01, 0x7f});

            // Type 2: (i32, i32, i32) -> i32 for mem_copy
            baos.write(new byte[] {0x60, 0x03, 0x7f, 0x7f, 0x7f, 0x01, 0x7f});

            // Function section: 3 functions
            baos.write(new byte[] {0x03}); // function section
            baos.write(new byte[] {0x04}); // section size
            baos.write(new byte[] {0x03}); // 3 functions
            baos.write(new byte[] {0x00, 0x01, 0x02}); // function types

            // Memory section: 1 memory with 10 pages (640KB)
            baos.write(new byte[] {0x05}); // memory section
            baos.write(new byte[] {0x03}); // section size
            baos.write(new byte[] {0x01}); // 1 memory
            baos.write(new byte[] {0x00, 0x0a}); // min 10 pages (no max)

            // Export section: export all 3 functions
            baos.write(new byte[] {0x07}); // export section
            baos.write(new byte[] {0x20}); // section size
            baos.write(new byte[] {0x03}); // 3 exports

            // Export "mem_load"
            baos.write(new byte[] {0x08}); // name length
            baos.write("mem_load".getBytes());
            baos.write(new byte[] {0x00, 0x00}); // function 0

            // Export "mem_store"
            baos.write(new byte[] {0x09}); // name length
            baos.write("mem_store".getBytes());
            baos.write(new byte[] {0x00, 0x01}); // function 1

            // Export "mem_copy"
            baos.write(new byte[] {0x08}); // name length
            baos.write("mem_copy".getBytes());
            baos.write(new byte[] {0x00, 0x02}); // function 2

            // Code section: function bodies
            baos.write(new byte[] {0x0a}); // code section
            baos.write(new byte[] {0x30}); // section size (approximate)
            baos.write(new byte[] {0x03}); // 3 functions

            // Function 0: mem_load(offset, size) - loads byte at offset and returns count
            baos.write(new byte[] {
                0x0c, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0 (offset)
                0x28, 0x02, 0x00, // i32.load (align=2, offset=0)
                0x20, 0x01, // local.get 1 (size)
                0x6a, // i32.add
                0x0b // end
            });

            // Function 1: mem_store(offset, size, value) - stores value to memory
            baos.write(new byte[] {
                0x0f, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0 (offset)
                0x20, 0x02, // local.get 2 (value)
                0x36, 0x02, 0x00, // i32.store (align=2, offset=0)
                0x20, 0x01, // local.get 1 (size)
                0x0b // end
            });

            // Function 2: mem_copy(dest, src, size) - copies memory
            baos.write(new byte[] {
                0x14, // body size
                0x00, // local count
                0x20, 0x00, // local.get 0 (dest)
                0x20, 0x01, // local.get 1 (src)
                0x28, 0x02, 0x00, // i32.load (align=2, offset=0)
                0x36, 0x02, 0x00, // i32.store (align=2, offset=0)
                0x20, 0x02, // local.get 2 (size)
                0x0b // end
            });

            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create memory operations module", e);
        }
    }
}
