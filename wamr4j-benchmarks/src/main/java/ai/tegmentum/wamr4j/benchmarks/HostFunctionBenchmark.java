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

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.HostFunction;
import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
 * JMH benchmarks for host function callback overhead.
 *
 * <p>This benchmark suite measures the performance of WASM-to-Java host function
 * callbacks, which are critical for applications that need to provide native
 * capabilities (I/O, networking, system calls) to WebAssembly modules.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class HostFunctionBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private WebAssemblyModule jniModule;
    private WebAssemblyModule panamaModule;
    private WebAssemblyInstance jniInstance;
    private WebAssemblyInstance panamaInstance;
    private WebAssemblyFunction jniCallHost;
    private WebAssemblyFunction panamaCallHost;

    /**
     * Sets up JNI and Panama runtimes with host function imports.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        final byte[] hostModule = createHostCallModule();
        final Map<String, Map<String, Object>> imports = createImports();

        try {
            // Setup JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            jniModule = jniRuntime.compile(hostModule);
            jniInstance = jniModule.instantiate(imports);
            jniCallHost = jniInstance.getFunction("call_host");

            // Setup Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
                panamaModule = panamaRuntime.compile(hostModule);
                panamaInstance = panamaModule.instantiate(imports);
                panamaCallHost = panamaInstance.getFunction("call_host");
            } catch (final Exception e) {
                panamaRuntime = null;
            }

            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");

        } catch (final Exception e) {
            throw new RuntimeException("Host function benchmark setup failed", e);
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
     * Benchmarks JNI host function callback round-trip.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkJniHostCallback(final Blackhole bh) {
        if (jniCallHost == null) {
            return;
        }

        try {
            final Object result = jniCallHost.invoke(10, 20);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI host callback benchmark failed", e);
        }
    }

    /**
     * Benchmarks Panama host function callback round-trip.
     *
     * @param bh the JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void benchmarkPanamaHostCallback(final Blackhole bh) {
        if (panamaCallHost == null) {
            return;
        }

        try {
            final Object result = panamaCallHost.invoke(10, 20);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("Panama host callback benchmark failed", e);
        }
    }

    private Map<String, Map<String, Object>> createImports() {
        final FunctionSignature addSignature = new FunctionSignature(
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[]{ValueType.I32}
        );

        final HostFunction hostAdd = new HostFunction(addSignature, args -> {
            final int a = (Integer) args[0];
            final int b = (Integer) args[1];
            return a + b;
        });

        final Map<String, Object> envFunctions = new HashMap<>();
        envFunctions.put("host_add", hostAdd);

        final Map<String, Map<String, Object>> imports = new HashMap<>();
        imports.put("env", envFunctions);

        return imports;
    }

    /**
     * Creates a WASM module that imports and calls a host function.
     *
     * <p>Module structure (WAT equivalent):
     * <pre>
     * (module
     *   (type (func (param i32 i32) (result i32)))
     *   (import "env" "host_add" (func $host_add (type 0)))
     *   (func (export "call_host") (type 0)
     *     (call $host_add (local.get 0) (local.get 1)))
     * )
     * </pre>
     *
     * @return the compiled WASM binary bytes
     */
    private byte[] createHostCallModule() {
        try {
            final ByteArrayOutputStream module = new ByteArrayOutputStream();

            // WASM header
            module.write(new byte[]{0x00, 0x61, 0x73, 0x6d}); // magic
            module.write(new byte[]{0x01, 0x00, 0x00, 0x00}); // version

            // Type section: 1 type (i32, i32) -> i32
            writeSection(module, 0x01, new byte[]{
                0x01, // 1 type
                0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f
            });

            // Import section: import "env"."host_add" as func type 0
            final ByteArrayOutputStream importPayload = new ByteArrayOutputStream();
            importPayload.write(0x01); // 1 import
            importPayload.write(0x03); // module name length
            importPayload.write("env".getBytes());
            importPayload.write(0x08); // field name length
            importPayload.write("host_add".getBytes());
            importPayload.write(0x00); // import kind: function
            importPayload.write(0x00); // type index
            writeSection(module, 0x02, importPayload.toByteArray());

            // Function section: 1 function (type 0)
            // Note: func index 0 is the import, so our func is index 1
            writeSection(module, 0x03, new byte[]{
                0x01, // 1 function
                0x00  // type 0
            });

            // Export section: export "call_host" as func 1
            final ByteArrayOutputStream exportPayload = new ByteArrayOutputStream();
            exportPayload.write(0x01); // 1 export
            exportPayload.write(0x09); // name length
            exportPayload.write("call_host".getBytes());
            exportPayload.write(0x00); // function export kind
            exportPayload.write(0x01); // function index (1, after the import)
            writeSection(module, 0x07, exportPayload.toByteArray());

            // Code section: 1 function body
            writeSection(module, 0x0a, new byte[]{
                0x01, // 1 function
                0x08, // body size
                0x00, // 0 locals
                0x20, 0x00, // local.get 0
                0x20, 0x01, // local.get 1
                0x10, 0x00, // call 0 (the imported host_add)
                0x0b // end
            });

            return module.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create host call module", e);
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
