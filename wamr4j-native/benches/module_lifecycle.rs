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

//! Benchmarks for WebAssembly module lifecycle operations
//!
//! This benchmark suite measures the performance of module compilation,
//! instantiation, and function execution with a focus on achieving the
//! target of <100ms for typical module operations and <50ms startup time.

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId, Throughput};
use std::os::raw::{c_char, c_int, c_long, c_uchar, c_void};

/// Generate minimal valid WASM module for testing
fn generate_minimal_wasm_module() -> Vec<u8> {
    // Minimal WASM module with magic number and version
    vec![
        0x00, 0x61, 0x73, 0x6D, // Magic number
        0x01, 0x00, 0x00, 0x00, // Version
    ]
}

/// Generate WASM module with a simple function
fn generate_simple_function_wasm() -> Vec<u8> {
    // Simple WASM module with one function that adds two i32 values
    vec![
        0x00, 0x61, 0x73, 0x6D, // Magic number
        0x01, 0x00, 0x00, 0x00, // Version
        
        // Type section
        0x01, 0x07,             // Section ID and size
        0x01,                   // Number of types
        0x60, 0x02, 0x7F, 0x7F, // Function type: (i32, i32) -> i32
        0x01, 0x7F,
        
        // Function section
        0x03, 0x02,             // Section ID and size
        0x01, 0x00,             // One function of type 0
        
        // Export section
        0x07, 0x07,             // Section ID and size
        0x01,                   // One export
        0x03, 0x61, 0x64, 0x64, // Export name "add"
        0x00, 0x00,             // Function index 0
        
        // Code section
        0x0A, 0x09,             // Section ID and size
        0x01, 0x07, 0x00,       // One function body, size 7, no locals
        0x20, 0x00,             // local.get 0
        0x20, 0x01,             // local.get 1
        0x6A,                   // i32.add
        0x0B,                   // end
    ]
}

/// Generate larger WASM module for stress testing
fn generate_complex_wasm_module() -> Vec<u8> {
    let mut module = generate_simple_function_wasm();
    
    // Extend with additional dummy data to simulate larger modules
    let padding_size = 8192;
    let mut padding = vec![0u8; padding_size];
    
    // Add custom section with padding
    padding[0] = 0x00; // Custom section ID
    padding[1] = ((padding_size - 2) & 0x7F) as u8; // Size (simplified)
    padding[2] = ((padding_size - 2) >> 7) as u8;
    
    module.extend_from_slice(&padding);
    module
}

/// Benchmark runtime initialization and cleanup
fn bench_runtime_lifecycle(c: &mut Criterion) {
    let mut group = c.benchmark_group("runtime_lifecycle");
    
    // Default runtime initialization
    group.bench_function("runtime_init_default", |b| {
        b.iter(|| {
            let runtime = unsafe { 
                wamr4j_native::wamr_runtime_init()
            };
            black_box(runtime);
            
            if !runtime.is_null() {
                unsafe {
                    wamr4j_native::wamr_runtime_destroy(runtime);
                }
            }
        });
    });
    
    // Custom configuration runtime
    group.bench_function("runtime_init_custom", |b| {
        b.iter(|| {
            let runtime = unsafe {
                wamr4j_native::wamr_runtime_init_with_config(
                    black_box(16384),  // 16KB stack
                    black_box(1048576), // 1MB heap
                    black_box(4),      // 4 threads
                )
            };
            black_box(runtime);
            
            if !runtime.is_null() {
                unsafe {
                    wamr4j_native::wamr_runtime_destroy(runtime);
                }
            }
        });
    });
    
    // Runtime validation check
    group.bench_function("runtime_validation", |b| {
        let runtime = unsafe { wamr4j_native::wamr_runtime_init() };
        
        b.iter(|| {
            let is_valid = unsafe {
                wamr4j_native::wamr_runtime_is_valid(black_box(runtime))
            };
            black_box(is_valid);
        });
        
        if !runtime.is_null() {
            unsafe {
                wamr4j_native::wamr_runtime_destroy(runtime);
            }
        }
    });
    
    group.finish();
}

/// Benchmark module compilation with different sizes
fn bench_module_compilation(c: &mut Criterion) {
    let mut group = c.benchmark_group("module_compilation");
    
    // Initialize runtime once for all compilation tests
    let runtime = unsafe { wamr4j_native::wamr_runtime_init() };
    if runtime.is_null() {
        println!("Failed to initialize runtime for compilation benchmarks");
        return;
    }
    
    // Test different module sizes
    let test_cases = [
        ("minimal", generate_minimal_wasm_module()),
        ("simple_function", generate_simple_function_wasm()),
        ("complex", generate_complex_wasm_module()),
    ];
    
    for (name, wasm_bytes) in test_cases.iter() {
        group.throughput(Throughput::Bytes(wasm_bytes.len() as u64));
        
        group.bench_with_input(
            BenchmarkId::new("compile", name),
            wasm_bytes,
            |b, bytes| {
                let mut error_buf = [0i8; 256];
                
                b.iter(|| {
                    let module = unsafe {
                        wamr4j_native::wamr_module_compile(
                            black_box(runtime),
                            black_box(bytes.as_ptr()),
                            black_box(bytes.len() as c_long),
                            black_box(error_buf.as_mut_ptr()),
                            black_box(error_buf.len() as c_int),
                        )
                    };
                    
                    black_box(module);
                    
                    // Clean up module
                    if !module.is_null() {
                        unsafe {
                            wamr4j_native::wamr_module_destroy(module);
                        }
                    }
                });
            }
        );
        
        // Also test validation without compilation
        group.bench_with_input(
            BenchmarkId::new("validate", name),
            wasm_bytes,
            |b, bytes| {
                let mut error_buf = [0i8; 256];
                
                b.iter(|| {
                    let result = unsafe {
                        wamr4j_native::wamr_module_validate(
                            black_box(runtime),
                            black_box(bytes.as_ptr()),
                            black_box(bytes.len() as c_long),
                            black_box(error_buf.as_mut_ptr()),
                            black_box(error_buf.len() as c_int),
                        )
                    };
                    
                    black_box(result);
                });
            }
        );
    }
    
    // Clean up runtime
    unsafe {
        wamr4j_native::wamr_runtime_destroy(runtime);
    }
    
    group.finish();
}

/// Benchmark module instantiation
fn bench_module_instantiation(c: &mut Criterion) {
    let mut group = c.benchmark_group("module_instantiation");
    
    // Set up runtime and compile a module once
    let runtime = unsafe { wamr4j_native::wamr_runtime_init() };
    if runtime.is_null() {
        println!("Failed to initialize runtime for instantiation benchmarks");
        return;
    }
    
    let wasm_bytes = generate_simple_function_wasm();
    let mut error_buf = [0i8; 256];
    
    let module = unsafe {
        wamr4j_native::wamr_module_compile(
            runtime,
            wasm_bytes.as_ptr(),
            wasm_bytes.len() as c_long,
            error_buf.as_mut_ptr(),
            error_buf.len() as c_int,
        )
    };
    
    if module.is_null() {
        println!("Failed to compile module for instantiation benchmarks");
        unsafe { wamr4j_native::wamr_runtime_destroy(runtime); }
        return;
    }
    
    // Test different memory configurations
    let configurations = [
        ("small", 8192, 65536),      // 8KB stack, 64KB heap
        ("medium", 16384, 262144),   // 16KB stack, 256KB heap
        ("large", 32768, 1048576),   // 32KB stack, 1MB heap
    ];
    
    for (name, stack_size, heap_size) in configurations.iter() {
        group.bench_with_input(
            BenchmarkId::new("instantiate", name),
            &(*stack_size, *heap_size),
            |b, &(stack, heap)| {
                b.iter(|| {
                    let instance = unsafe {
                        wamr4j_native::wamr_instance_create(
                            black_box(module),
                            black_box(stack as c_long),
                            black_box(heap as c_long),
                            black_box(error_buf.as_mut_ptr()),
                            black_box(error_buf.len() as c_int),
                        )
                    };
                    
                    black_box(instance);
                    
                    // Clean up instance
                    if !instance.is_null() {
                        unsafe {
                            wamr4j_native::wamr_instance_destroy(instance);
                        }
                    }
                });
            }
        );
    }
    
    // Instance validation benchmark
    let instance = unsafe {
        wamr4j_native::wamr_instance_create(
            module,
            16384,
            262144,
            error_buf.as_mut_ptr(),
            error_buf.len() as c_int,
        )
    };
    
    if !instance.is_null() {
        group.bench_function("instance_validation", |b| {
            b.iter(|| {
                let is_valid = unsafe {
                    wamr4j_native::wamr_instance_is_valid(black_box(instance))
                };
                black_box(is_valid);
            });
        });
        
        unsafe {
            wamr4j_native::wamr_instance_destroy(instance);
        }
    }
    
    // Clean up
    unsafe {
        wamr4j_native::wamr_module_destroy(module);
        wamr4j_native::wamr_runtime_destroy(runtime);
    }
    
    group.finish();
}

/// Benchmark function lookup and signature operations
fn bench_function_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("function_operations");
    
    // Set up runtime, module, and instance
    let runtime = unsafe { wamr4j_native::wamr_runtime_init() };
    if runtime.is_null() {
        return;
    }
    
    let wasm_bytes = generate_simple_function_wasm();
    let mut error_buf = [0i8; 256];
    
    let module = unsafe {
        wamr4j_native::wamr_module_compile(
            runtime,
            wasm_bytes.as_ptr(),
            wasm_bytes.len() as c_long,
            error_buf.as_mut_ptr(),
            error_buf.len() as c_int,
        )
    };
    
    if module.is_null() {
        unsafe { wamr4j_native::wamr_runtime_destroy(runtime); }
        return;
    }
    
    let instance = unsafe {
        wamr4j_native::wamr_instance_create(
            module,
            16384,
            262144,
            error_buf.as_mut_ptr(),
            error_buf.len() as c_int,
        )
    };
    
    if instance.is_null() {
        unsafe {
            wamr4j_native::wamr_module_destroy(module);
            wamr4j_native::wamr_runtime_destroy(runtime);
        }
        return;
    }
    
    // Function lookup benchmark
    let function_name = std::ffi::CString::new("add").unwrap();
    group.bench_function("function_lookup", |b| {
        b.iter(|| {
            let function = unsafe {
                wamr4j_native::wamr_function_lookup(
                    black_box(instance),
                    black_box(function_name.as_ptr()),
                    black_box(error_buf.as_mut_ptr()),
                    black_box(error_buf.len() as c_int),
                )
            };
            black_box(function);
            
            // Note: In real implementation, we'd need to clean up function handle
            // For benchmarking, we assume it's handled internally
        });
    });
    
    // Get function for signature testing
    let function = unsafe {
        wamr4j_native::wamr_function_lookup(
            instance,
            function_name.as_ptr(),
            error_buf.as_mut_ptr(),
            error_buf.len() as c_int,
        )
    };
    
    if !function.is_null() {
        // Function signature retrieval
        group.bench_function("function_signature", |b| {
            let mut param_types = [0c_int; 8];
            let mut param_count = 0c_int;
            let mut result_types = [0c_int; 8];
            let mut result_count = 0c_int;
            
            b.iter(|| {
                let result = unsafe {
                    wamr4j_native::wamr_function_get_signature(
                        black_box(function),
                        black_box(param_types.as_mut_ptr()),
                        black_box(&mut param_count),
                        black_box(result_types.as_mut_ptr()),
                        black_box(&mut result_count),
                    )
                };
                black_box((result, param_count, result_count));
            });
        });
    }
    
    // Clean up
    unsafe {
        wamr4j_native::wamr_instance_destroy(instance);
        wamr4j_native::wamr_module_destroy(module);
        wamr4j_native::wamr_runtime_destroy(runtime);
    }
    
    group.finish();
}

/// Benchmark complete module lifecycle
fn bench_complete_lifecycle(c: &mut Criterion) {
    let mut group = c.benchmark_group("complete_lifecycle");
    
    let wasm_bytes = generate_simple_function_wasm();
    
    // Complete workflow benchmark
    group.bench_function("full_lifecycle", |b| {
        b.iter(|| {
            // 1. Initialize runtime
            let runtime = unsafe { wamr4j_native::wamr_runtime_init() };
            if runtime.is_null() {
                return;
            }
            
            // 2. Compile module
            let mut error_buf = [0i8; 256];
            let module = unsafe {
                wamr4j_native::wamr_module_compile(
                    runtime,
                    black_box(wasm_bytes.as_ptr()),
                    black_box(wasm_bytes.len() as c_long),
                    error_buf.as_mut_ptr(),
                    error_buf.len() as c_int,
                )
            };
            
            if !module.is_null() {
                // 3. Create instance
                let instance = unsafe {
                    wamr4j_native::wamr_instance_create(
                        module,
                        16384,
                        262144,
                        error_buf.as_mut_ptr(),
                        error_buf.len() as c_int,
                    )
                };
                
                if !instance.is_null() {
                    // 4. Lookup function
                    let function_name = std::ffi::CString::new("add").unwrap();
                    let _function = unsafe {
                        wamr4j_native::wamr_function_lookup(
                            instance,
                            function_name.as_ptr(),
                            error_buf.as_mut_ptr(),
                            error_buf.len() as c_int,
                        )
                    };
                    
                    // 5. Clean up instance
                    unsafe {
                        wamr4j_native::wamr_instance_destroy(instance);
                    }
                }
                
                // 6. Clean up module
                unsafe {
                    wamr4j_native::wamr_module_destroy(module);
                }
            }
            
            // 7. Clean up runtime
            unsafe {
                wamr4j_native::wamr_runtime_destroy(runtime);
            }
            
            black_box(());
        });
    });
    
    group.finish();
}

criterion_group!(
    benches,
    bench_runtime_lifecycle,
    bench_module_compilation,
    bench_module_instantiation,
    bench_function_operations,
    bench_complete_lifecycle
);
criterion_main!(benches);