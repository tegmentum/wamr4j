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

//! Basic performance benchmarks for native FFI operations
//!
//! This benchmark measures the fundamental overhead of our FFI layer
//! to establish baseline performance metrics and identify optimization targets.

use criterion::{black_box, criterion_group, criterion_main, Criterion};
use std::os::raw::{c_char, c_int, c_void};

/// Benchmark the most basic native function calls
fn bench_basic_ffi_calls(c: &mut Criterion) {
    let mut group = c.benchmark_group("basic_ffi");
    
    // Test the most basic function with no parameters
    group.bench_function("test_function", |b| {
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr4j_test_function()
            };
            black_box(result);
        });
    });
    
    // Test version string access (static data)
    group.bench_function("get_version", |b| {
        b.iter(|| {
            let version = unsafe {
                wamr4j_native::wamr_get_version()
            };
            black_box(version);
        });
    });
    
    // Test library info (static data)
    group.bench_function("get_library_name", |b| {
        b.iter(|| {
            let name = unsafe {
                wamr4j_native::wamr4j_get_library_name()
            };
            black_box(name);
        });
    });
    
    group.finish();
}

/// Benchmark parameter validation patterns
fn bench_parameter_validation(c: &mut Criterion) {
    let mut group = c.benchmark_group("parameter_validation");
    
    // Single pointer validation
    group.bench_function("runtime_is_valid", |b| {
        let runtime = 0x1000 as *mut c_void;
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr_runtime_is_valid(black_box(runtime))
            };
            black_box(result);
        });
    });
    
    // Multiple parameter validation
    group.bench_function("runtime_init_with_config", |b| {
        b.iter(|| {
            let result = unsafe {
                wamr4j_native::wamr_runtime_init_with_config(
                    black_box(16384),   // stack_size
                    black_box(1048576), // heap_size  
                    black_box(4),       // max_threads
                )
            };
            black_box(result);
            // Clean up if not null
            if !result.is_null() {
                unsafe {
                    wamr4j_native::wamr_runtime_destroy(result);
                }
            }
        });
    });
    
    group.finish();
}

/// Benchmark error handling overhead  
fn bench_error_handling(c: &mut Criterion) {
    let mut group = c.benchmark_group("error_handling");
    
    // Clear error (should be very fast)
    group.bench_function("clear_error", |b| {
        b.iter(|| {
            unsafe {
                wamr4j_native::wamr_clear_last_error();
            }
        });
    });
    
    // Get error with buffer
    group.bench_function("get_error", |b| {
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

/// Benchmark memory operations that should be very fast
fn bench_fast_memory_ops(c: &mut Criterion) {
    let mut group = c.benchmark_group("fast_memory_ops");
    
    // Memory size (should just return a field)
    group.bench_function("memory_size", |b| {
        let memory = 0x1000 as *mut c_void;
        b.iter(|| {
            let size = unsafe {
                wamr4j_native::wamr_memory_size(black_box(memory))
            };
            black_box(size);
        });
    });
    
    // Memory data pointer (should just return a field)
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

/// Benchmark CPU-intensive operations for comparison baseline
fn bench_cpu_baseline(c: &mut Criterion) {
    let mut group = c.benchmark_group("cpu_baseline");
    
    // Simple arithmetic
    group.bench_function("simple_add", |b| {
        let a = 42i32;
        let b = 17i32;
        b.iter(|| {
            let result = black_box(a) + black_box(b);
            black_box(result);
        });
    });
    
    // Array access
    group.bench_function("array_access", |b| {
        let data = [1, 2, 3, 4, 5, 6, 7, 8];
        let index = 3;
        b.iter(|| {
            let value = black_box(&data)[black_box(index)];
            black_box(value);
        });
    });
    
    // String creation (more expensive baseline)
    group.bench_function("string_format", |b| {
        let value = 42;
        b.iter(|| {
            let s = format!("Value: {}", black_box(value));
            black_box(s);
        });
    });
    
    group.finish();
}

/// Benchmark optimized vs unoptimized code paths
fn bench_optimization_potential(c: &mut Criterion) {
    let mut group = c.benchmark_group("optimization_potential");
    
    // Direct field access vs function call
    group.bench_function("direct_null_check", |b| {
        let ptr = 0x1000 as *mut c_void;
        b.iter(|| {
            let is_null = black_box(ptr).is_null();
            black_box(is_null);
        });
    });
    
    // Inline function call
    group.bench_function("inline_function", |b| {
        #[inline(always)]
        fn inline_add(a: i32, b: i32) -> i32 {
            a + b
        }
        
        b.iter(|| {
            let result = inline_add(black_box(42), black_box(17));
            black_box(result);
        });
    });
    
    // Non-inline function call
    group.bench_function("noinline_function", |b| {
        #[inline(never)]
        fn noinline_add(a: i32, b: i32) -> i32 {
            a + b
        }
        
        b.iter(|| {
            let result = noinline_add(black_box(42), black_box(17));
            black_box(result);
        });
    });
    
    group.finish();
}

criterion_group!(
    benches,
    bench_basic_ffi_calls,
    bench_parameter_validation,
    bench_error_handling,
    bench_fast_memory_ops,
    bench_cpu_baseline,
    bench_optimization_potential
);
criterion_main!(benches);