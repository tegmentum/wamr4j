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
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for WebAssembly runtime performance comparison.
 * 
 * <p>This benchmark suite compares the performance characteristics of
 * JNI and Panama FFI implementations across various WebAssembly operations
 * including compilation, instantiation, and function execution.
 * 
 * @since 1.0.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class RuntimeBenchmark {

    private WebAssemblyRuntime jniRuntime;
    private WebAssemblyRuntime panamaRuntime;
    private byte[] simpleWasmModule;
    private WebAssemblyModule compiledModule;
    private WebAssemblyInstance moduleInstance;
    private WebAssemblyFunction addFunction;

    @Setup(Level.Trial)
    public void setupTrial() {
        // Create test WebAssembly module (simple add function)
        simpleWasmModule = createSimpleAddModule();
        
        try {
            // Force JNI runtime
            System.setProperty("wamr4j.runtime", "jni");
            jniRuntime = RuntimeFactory.createRuntime();
            
            // Force Panama runtime (if available)
            System.setProperty("wamr4j.runtime", "panama");
            try {
                panamaRuntime = RuntimeFactory.createRuntime();
            } catch (final Exception e) {
                panamaRuntime = null; // Panama not available
            }
            
            // Reset to auto-detection
            System.clearProperty("wamr4j.runtime");
            
        } catch (final Exception e) {
            throw new RuntimeException("Benchmark setup failed", e);
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

    @Setup(Level.Iteration)
    public void setupIteration() {
        try {
            if (jniRuntime != null) {
                compiledModule = jniRuntime.compile(simpleWasmModule);
                moduleInstance = compiledModule.instantiate();
                addFunction = moduleInstance.getFunction("add");
            }
        } catch (final Exception e) {
            throw new RuntimeException("Iteration setup failed", e);
        }
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() {
        try {
            if (addFunction != null && !addFunction.isClosed()) {
                addFunction.close();
            }
            if (moduleInstance != null && !moduleInstance.isClosed()) {
                moduleInstance.close();
            }
            if (compiledModule != null && !compiledModule.isClosed()) {
                compiledModule.close();
            }
        } catch (final Exception e) {
            // Log but don't fail benchmark
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }

    @Benchmark
    public void benchmarkJniRuntimeCreation(final Blackhole bh) {
        if (jniRuntime == null) return;
        
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            bh.consume(runtime);
        } catch (final Exception e) {
            throw new RuntimeException("JNI runtime creation benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaRuntimeCreation(final Blackhole bh) {
        if (panamaRuntime == null) return;
        
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
            bh.consume(runtime);
        } catch (final Exception e) {
            throw new RuntimeException("Panama runtime creation benchmark failed", e);
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Benchmark
    public void benchmarkJniModuleCompilation(final Blackhole bh) {
        if (jniRuntime == null) return;
        
        try (final WebAssemblyModule module = jniRuntime.compile(simpleWasmModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("JNI compilation benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkPanamaModuleCompilation(final Blackhole bh) {
        if (panamaRuntime == null) return;
        
        try (final WebAssemblyModule module = panamaRuntime.compile(simpleWasmModule)) {
            bh.consume(module);
        } catch (final Exception e) {
            throw new RuntimeException("Panama compilation benchmark failed", e);
        }
    }

    @Benchmark
    public void benchmarkJniFunctionCall(final Blackhole bh) {
        if (addFunction == null) return;
        
        try {
            final Object[] result = addFunction.call(42, 24);
            bh.consume(result);
        } catch (final Exception e) {
            throw new RuntimeException("JNI function call benchmark failed", e);
        }
    }

    private byte[] createSimpleAddModule() {
        // WebAssembly binary format for: (module (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add))
        return new byte[] {
            0x00, 0x61, 0x73, 0x6d, // WASM magic number
            0x01, 0x00, 0x00, 0x00, // WASM version
            0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f, // Type section: (i32, i32) -> i32
            0x03, 0x02, 0x01, 0x00, // Function section: 1 function of type 0
            0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00, // Export section: export "add" as function 0
            0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b // Code section: local.get 0, local.get 1, i32.add
        };
    }
}