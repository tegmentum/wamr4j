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
 * JMH benchmarks for large WebAssembly module compilation performance.
 *
 * <p>This benchmark suite measures the compilation time for modules of various
 * sizes to assess how well each implementation scales with module complexity.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class LargeModuleBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private byte[] smallModule;
    private byte[] mediumModule;
    private byte[] largeModule;

    @Setup(Level.Trial)
    public void setupTrial() {
        smallModule = createModuleWithFunctions(10);
        mediumModule = createModuleWithFunctions(100);
        largeModule = createModuleWithFunctions(500);

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Large module benchmark setup failed", e);
        }
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (jniRuntime != null && !jniRuntime.isClosed()) {
            jniRuntime.close();
        }
        if (panamaRuntime != null && !panamaRuntime.isClosed()) {
            panamaRuntime.close();
        }
    }

    @Benchmark
    public void benchmarkJniSmallModuleCompilation(final Blackhole bh) {
        if (jniRuntime == null) {
            return;
        }

        try (final WebAssemblyModule module = jniRuntime.compile(smallModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("JNI small module compilation failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaSmallModuleCompilation(final Blackhole bh) {
        if (panamaRuntime == null) {
            return;
        }

        try (final WebAssemblyModule module = panamaRuntime.compile(smallModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("Panama small module compilation failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniMediumModuleCompilation(final Blackhole bh) {
        if (jniRuntime == null) {
            return;
        }

        try (final WebAssemblyModule module = jniRuntime.compile(mediumModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("JNI medium module compilation failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaMediumModuleCompilation(final Blackhole bh) {
        if (panamaRuntime == null) {
            return;
        }

        try (final WebAssemblyModule module = panamaRuntime.compile(mediumModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("Panama medium module compilation failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniLargeModuleCompilation(final Blackhole bh) {
        if (jniRuntime == null) {
            return;
        }

        try (final WebAssemblyModule module = jniRuntime.compile(largeModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("JNI large module compilation failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaLargeModuleCompilation(final Blackhole bh) {
        if (panamaRuntime == null) {
            return;
        }

        try (final WebAssemblyModule module = panamaRuntime.compile(largeModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("Panama large module compilation failed", e);
        }
    }

    private byte[] createModuleWithFunctions(final int functionCount) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // WASM magic number and version
            baos.write(new byte[] {0x00, 0x61, 0x73, 0x6d}); // magic
            baos.write(new byte[] {0x01, 0x00, 0x00, 0x00}); // version

            // Type section: Define function type (i32, i32) -> i32
            baos.write(new byte[] {0x01}); // type section
            baos.write(new byte[] {0x07}); // section size
            baos.write(new byte[] {0x01}); // 1 type
            baos.write(new byte[] {0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f}); // (i32, i32) -> i32

            // Function section: All functions use the same type
            final ByteArrayOutputStream funcSection = new ByteArrayOutputStream();
            funcSection.write(encodeUnsignedLeb128(functionCount)); // function count
            for (int i = 0; i < functionCount; i++) {
                funcSection.write(0x00); // all use type 0
            }
            baos.write(0x03); // function section
            final byte[] funcSectionBytes = funcSection.toByteArray();
            baos.write(encodeUnsignedLeb128(funcSectionBytes.length));
            baos.write(funcSectionBytes);

            // Export section: Export first function
            baos.write(new byte[] {0x07}); // export section
            baos.write(new byte[] {0x08}); // section size
            baos.write(new byte[] {0x01}); // 1 export
            baos.write(new byte[] {0x04}); // name length
            baos.write("func".getBytes());
            baos.write(new byte[] {0x00, 0x00}); // function 0

            // Code section: Function bodies (simple add operations)
            final ByteArrayOutputStream codeSection = new ByteArrayOutputStream();
            codeSection.write(encodeUnsignedLeb128(functionCount)); // function count

            for (int i = 0; i < functionCount; i++) {
                // Each function: (i32, i32) -> i32 performing (a + b) + i
                final ByteArrayOutputStream funcBody = new ByteArrayOutputStream();
                funcBody.write(0x00); // local count
                funcBody.write(0x20);
                funcBody.write(0x00); // local.get 0
                funcBody.write(0x20);
                funcBody.write(0x01); // local.get 1
                funcBody.write(0x6a); // i32.add
                funcBody.write(0x41);
                funcBody.write(encodeUnsignedLeb128(i)); // i32.const i
                funcBody.write(0x6a); // i32.add
                funcBody.write(0x0b); // end

                final byte[] funcBodyBytes = funcBody.toByteArray();
                codeSection.write(encodeUnsignedLeb128(funcBodyBytes.length));
                codeSection.write(funcBodyBytes);
            }

            baos.write(0x0a); // code section
            final byte[] codeSectionBytes = codeSection.toByteArray();
            baos.write(encodeUnsignedLeb128(codeSectionBytes.length));
            baos.write(codeSectionBytes);

            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create module with functions", e);
        }
    }

    private byte[] encodeUnsignedLeb128(final int value) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int remaining = value;
        do {
            byte b = (byte) (remaining & 0x7f);
            remaining >>>= 7;
            if (remaining != 0) {
                b |= (byte) 0x80;
            }
            baos.write(b);
        } while (remaining != 0);
        return baos.toByteArray();
    }
}
