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

//! Benchmarks for function call overhead
//!
//! This benchmark suite measures the critical path performance for FFI calls
//! with the goal of achieving <10ns average overhead for simple operations.
//! This includes parameter validation, conversion, and the actual native call.

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use std::os::raw::{c_char, c_int, c_long, c_uchar, c_void};
use std::ptr;
use wamr4j_native::utils::{WasmValueFFI, WASM_TYPE_I32};

/// Benchmark the absolute minimum FFI call overhead
fn bench_minimal_ffi_calls(c: &mut Criterion) {
    let mut group = c.benchmark_group("minimal_ffi");
    
    // Test simple validation functions (should be fastest)
    group.bench_function("null_pointer_check", |b| {
        let ptr = 0x1000 as *mut c_void;
        b.iter(|| {
            black_box(!ptr.is_null())
        });
    });
    
    // Test library info functions (no state access)
    group.bench_function("get_version", |b| {
        b.iter(|| {
            let version = unsafe {
                wamr4j_native::wamr_get_version()
            };
            black_box(version);
        });
    });
    
    group.bench_function("test_function", |b| {
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr4j_test_function()
            };
            black_box(result);
        });
    });
    
    // Test debug build check
    group.bench_function("is_debug_build", |b| {
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr4j_is_debug_build()
            };
            black_box(result);
        });
    });
    
    group.finish();
}

/// Benchmark parameter validation overhead
fn bench_parameter_validation(c: &mut Criterion) {
    let mut group = c.benchmark_group("parameter_validation");
    
    // Test validation patterns with different parameter counts
    group.bench_function("single_pointer", |b| {
        let ptr = 0x1000 as *mut c_void;
        b.iter(|| {
            let valid = !black_box(ptr).is_null();
            black_box(valid);
        });
    });
    
    group.bench_function("pointer_and_size", |b| {
        let ptr = 0x1000 as *mut c_void;
        let size = 1024i64;
        b.iter(|| {
            let ptr_valid = !black_box(ptr).is_null();
            let size_valid = black_box(size) > 0;
            black_box(ptr_valid && size_valid);
        });
    });
    
    group.bench_function("complex_validation", |b| {
        let runtime = 0x1000 as *mut c_void;
        let wasm_bytes = 0x2000 as *const c_uchar;
        let length = 1024i64;
        let error_buf = 0x3000 as *mut c_char;
        let error_buf_size = 256i32;
        
        b.iter(|| {
            let runtime_valid = !black_box(runtime).is_null();
            let bytes_valid = !black_box(wasm_bytes).is_null();
            let length_valid = black_box(length) > 0;
            let error_valid = !black_box(error_buf).is_null();
            let error_size_valid = black_box(error_buf_size) > 0;
            
            let all_valid = runtime_valid && bytes_valid && length_valid 
                          && error_valid && error_size_valid;
            black_box(all_valid);
        });
    });
    
    group.finish();
}

/// Benchmark function call patterns with different argument counts
fn bench_call_patterns(c: &mut Criterion) {
    let mut group = c.benchmark_group("call_patterns");
    
    // No-argument calls
    group.bench_function("no_args", |b| {
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr4j_test_function()
            };
            black_box(result);
        });
    });
    
    // Single argument calls
    group.bench_function("single_arg", |b| {
        let ptr = 0x1000 as *mut c_void;
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr_runtime_is_valid(black_box(ptr))
            };
            black_box(result);
        });
    });
    
    // Multiple argument calls (runtime init pattern)
    group.bench_function("triple_args", |b| {
        let stack_size = 16384i64;
        let heap_size = 1048576i64;
        let max_threads = 4i32;
        
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr_runtime_init_with_config(
                    black_box(stack_size),
                    black_box(heap_size),
                    black_box(max_threads),
                )
            };
            black_box(result);
            // Clean up to avoid memory leaks in benchmark
            if !result.is_null() {
                wamr4j_native::wamr_runtime_destroy(result);
            }
        });
    });
    
    group.finish();
}

/// Benchmark memory operation call overhead
fn bench_memory_calls(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_calls");
    
    // Memory size check (should be very fast)
    group.bench_function("memory_size", |b| {
        let memory = 0x1000 as *mut c_void;
        b.iter(|| {
            let size = unsafe {
                wamr4j_native::wamr_memory_size(black_box(memory))
            };
            black_box(size);
        });
    });
    
    // Memory data access (pointer return)
    group.bench_function("memory_data", |b| {
        let memory = 0x1000 as *mut c_void;
        b.iter(|| {
            let data = unsafe {
                wamr4j_native::wamr_memory_data(black_box(memory))
            };
            black_box(data);
        });
    });
    
    group.finish();
}

/// Benchmark error handling overhead
fn bench_error_handling(c: &mut Criterion) {
    let mut group = c.benchmark_group("error_handling");
    
    // Error buffer operations
    group.bench_function("clear_error", |b| {
        b.iter(|| {
            unsafe {
                wamr4j_native::wamr_clear_last_error();
            }
        });
    });
    
    group.bench_function("get_error_with_buffer", |b| {
        let mut buffer = [0i8; 256];
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr_get_last_error(
                    black_box(buffer.as_mut_ptr()),
                    black_box(buffer.len() as c_int),
                )
            };
            black_box(result);
        });
    });
    
    group.finish();
}

/// Benchmark value marshaling overhead for function calls
fn bench_value_marshaling(c: &mut Criterion) {
    let mut group = c.benchmark_group("value_marshaling");
    
    // Test different argument configurations
    for arg_count in [0, 1, 2, 4, 8].iter() {
        let args: Vec<WasmValueFFI> = (0..*arg_count)
            .map(|i| WasmValueFFI {
                value_type: WASM_TYPE_I32,
                data: {
                    let mut data = [0u8; 8];
                    let bytes = (i as i32).to_le_bytes();
                    data[0..4].copy_from_slice(&bytes);
                    data
                },
            })
            .collect();
        
        let mut results = vec![WasmValueFFI {
            value_type: WASM_TYPE_I32,
            data: [0u8; 8],
        }; 1];
        
        let mut error_buf = [0i8; 256];
        
        group.bench_with_input(
            BenchmarkId::new("function_call", arg_count),
            &args,
            |b, args| {
                let function = 0x1000 as *mut c_void;
                b.iter(|| {
                    let result = unsafe {
                        wamr4j_native::wamr_function_call(
                            black_box(function),
                            black_box(args.as_ptr()),
                            black_box(args.len() as c_int),
                            black_box(results.as_mut_ptr()),
                            black_box(results.len() as c_int),
                            black_box(error_buf.as_mut_ptr()),
                            black_box(error_buf.len() as c_int),
                        )
                    };
                    black_box(result);
                });
            },
        );
    }
    
    group.finish();
}

/// Benchmark optimized call paths
fn bench_optimized_paths(c: &mut Criterion) {
    let mut group = c.benchmark_group("optimized_paths");
    
    // Test inline validation
    group.bench_function("inline_null_check", |b| {
        let ptr = 0x1000 as *mut c_void;
        b.iter(|| {
            #[inline(always)]
            fn validate_ptr(p: *mut c_void) -> bool {
                !p.is_null()
            }
            let valid = validate_ptr(black_box(ptr));
            black_box(valid);
        });
    });
    
    // Test branch prediction optimization
    group.bench_function("likely_success_path", |b| {
        let ptr = 0x1000 as *mut c_void;
        let size = 1024i64;
        
        b.iter(|| {
            // Simulate the most common success path
            let ptr_check = !black_box(ptr).is_null();
            let size_check = black_box(size) > 0;
            
            if likely(ptr_check && size_check) {
                black_box(0); // Success case
            } else {
                black_box(-1); // Error case
            }
        });
    });
    
    // Test unsafe optimizations
    group.bench_function("unsafe_skip_validation", |b| {
        let ptr = 0x1000 as *mut c_void;
        b.iter(|| {
            // Skip validation for hot path (unsafe but faster)
            unsafe {
                // Simulate direct access without validation
                let valid = true;
                black_box(valid);
            }
        });
    });
    
    group.finish();
}

/// Helper function for branch prediction hint
#[inline(always)]
fn likely(b: bool) -> bool {
    // Modern compilers and CPUs are quite good at branch prediction,
    // but we can still provide hints
    if b {
        std::hint::black_box(true)
    } else {
        false
    }
}

criterion_group!(
    benches,
    bench_minimal_ffi_calls,
    bench_parameter_validation,
    bench_call_patterns,
    bench_memory_calls,
    bench_error_handling,
    bench_value_marshaling,
    bench_optimized_paths
);
criterion_main!(benches);