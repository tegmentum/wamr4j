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

//! Benchmarks for WebAssembly value conversion operations
//!
//! This benchmark suite measures the performance of converting between
//! Rust WasmValue types and FFI-compatible representations, which is
//! critical for achieving the <10ns function call overhead target.

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use wamr4j_native::utils::{
    wasm_value_from_ffi, wasm_value_to_ffi, WasmValueFFI,
    WASM_TYPE_I32, WASM_TYPE_I64, WASM_TYPE_F32, WASM_TYPE_F64
};
use wamr4j_native::WasmValue;

/// Benchmark single value conversions to FFI
fn bench_single_to_ffi(c: &mut Criterion) {
    let mut group = c.benchmark_group("single_to_ffi");
    
    // I32 conversion
    let i32_value = WasmValue::I32(42);
    group.bench_function("i32", |b| {
        b.iter(|| wasm_value_to_ffi(black_box(&i32_value)));
    });
    
    // I64 conversion
    let i64_value = WasmValue::I64(0x1234567890ABCDEF);
    group.bench_function("i64", |b| {
        b.iter(|| wasm_value_to_ffi(black_box(&i64_value)));
    });
    
    // F32 conversion
    let f32_value = WasmValue::F32(3.14159);
    group.bench_function("f32", |b| {
        b.iter(|| wasm_value_to_ffi(black_box(&f32_value)));
    });
    
    // F64 conversion
    let f64_value = WasmValue::F64(2.718281828459045);
    group.bench_function("f64", |b| {
        b.iter(|| wasm_value_to_ffi(black_box(&f64_value)));
    });
    
    group.finish();
}

/// Benchmark single value conversions from FFI
fn bench_single_from_ffi(c: &mut Criterion) {
    let mut group = c.benchmark_group("single_from_ffi");
    
    // I32 conversion
    let i32_ffi = WasmValueFFI {
        value_type: WASM_TYPE_I32,
        data: [42, 0, 0, 0, 0, 0, 0, 0],
    };
    group.bench_function("i32", |b| {
        b.iter(|| wasm_value_from_ffi(black_box(&i32_ffi)));
    });
    
    // I64 conversion
    let i64_ffi = WasmValueFFI {
        value_type: WASM_TYPE_I64,
        data: [0xEF, 0xCD, 0xAB, 0x90, 0x78, 0x56, 0x34, 0x12],
    };
    group.bench_function("i64", |b| {
        b.iter(|| wasm_value_from_ffi(black_box(&i64_ffi)));
    });
    
    // F32 conversion
    let f32_ffi = WasmValueFFI {
        value_type: WASM_TYPE_F32,
        data: [0xD8, 0x0F, 0x49, 0x40, 0, 0, 0, 0], // 3.14159 in little-endian
    };
    group.bench_function("f32", |b| {
        b.iter(|| wasm_value_from_ffi(black_box(&f32_ffi)));
    });
    
    // F64 conversion
    let f64_ffi = WasmValueFFI {
        value_type: WASM_TYPE_F64,
        data: [0x69, 0x57, 0x14, 0x8B, 0x0A, 0xBF, 0x05, 0x40], // 2.718281... in little-endian
    };
    group.bench_function("f64", |b| {
        b.iter(|| wasm_value_from_ffi(black_box(&f64_ffi)));
    });
    
    group.finish();
}

/// Benchmark batch conversions (simulating function call arguments)
fn bench_batch_conversion(c: &mut Criterion) {
    let mut group = c.benchmark_group("batch_conversion");
    
    // Create various batch sizes to test scaling
    for size in [1, 2, 4, 8, 16].iter() {
        let values: Vec<WasmValue> = (0..*size)
            .map(|i| match i % 4 {
                0 => WasmValue::I32(i as i32),
                1 => WasmValue::I64(i as i64 * 1000),
                2 => WasmValue::F32(i as f32 * 0.1),
                _ => WasmValue::F64(i as f64 * 0.01),
            })
            .collect();
        
        group.bench_with_input(BenchmarkId::new("to_ffi", size), &values, |b, values| {
            b.iter(|| {
                let ffi_values: Vec<WasmValueFFI> = values
                    .iter()
                    .map(|v| wasm_value_to_ffi(black_box(v)))
                    .collect();
                black_box(ffi_values);
            });
        });
        
        // Create corresponding FFI values for from_ffi benchmark
        let ffi_values: Vec<WasmValueFFI> = values
            .iter()
            .map(|v| wasm_value_to_ffi(v))
            .collect();
        
        group.bench_with_input(BenchmarkId::new("from_ffi", size), &ffi_values, |b, ffi_values| {
            b.iter(|| {
                let wasm_values: Vec<WasmValue> = ffi_values
                    .iter()
                    .map(|ffi_v| wasm_value_from_ffi(black_box(ffi_v)))
                    .collect();
                black_box(wasm_values);
            });
        });
        
        // Round-trip conversion benchmark
        group.bench_with_input(BenchmarkId::new("round_trip", size), &values, |b, values| {
            b.iter(|| {
                let round_trip: Vec<WasmValue> = values
                    .iter()
                    .map(|v| {
                        let ffi_val = wasm_value_to_ffi(black_box(v));
                        wasm_value_from_ffi(black_box(&ffi_val))
                    })
                    .collect();
                black_box(round_trip);
            });
        });
    }
    
    group.finish();
}

/// Benchmark memory layout efficiency
fn bench_memory_layout(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_layout");
    
    // Test cache efficiency with large arrays
    let large_values: Vec<WasmValue> = (0..1000)
        .map(|i| WasmValue::I32(i))
        .collect();
    
    group.bench_function("sequential_access", |b| {
        b.iter(|| {
            let mut sum = 0i64;
            for value in &large_values {
                if let WasmValue::I32(v) = value {
                    sum += *v as i64;
                }
            }
            black_box(sum);
        });
    });
    
    let large_ffi_values: Vec<WasmValueFFI> = large_values
        .iter()
        .map(|v| wasm_value_to_ffi(v))
        .collect();
    
    group.bench_function("ffi_sequential_access", |b| {
        b.iter(|| {
            let mut sum = 0i64;
            for ffi_value in &large_ffi_values {
                if ffi_value.value_type == WASM_TYPE_I32 {
                    let bytes = [ffi_value.data[0], ffi_value.data[1], ffi_value.data[2], ffi_value.data[3]];
                    sum += i32::from_le_bytes(bytes) as i64;
                }
            }
            black_box(sum);
        });
    });
    
    group.finish();
}

/// Benchmark optimized conversion paths for hot functions
fn bench_optimized_paths(c: &mut Criterion) {
    let mut group = c.benchmark_group("optimized_paths");
    
    // Test optimized I32 conversion (most common case)
    let i32_value = WasmValue::I32(42);
    group.bench_function("i32_optimized_to_ffi", |b| {
        b.iter(|| {
            // Inline optimized version for I32
            let v = black_box(&i32_value);
            if let WasmValue::I32(val) = v {
                WasmValueFFI {
                    value_type: WASM_TYPE_I32,
                    data: {
                        let mut data = [0u8; 8];
                        let bytes = val.to_le_bytes();
                        data[0] = bytes[0];
                        data[1] = bytes[1];
                        data[2] = bytes[2];
                        data[3] = bytes[3];
                        data
                    },
                }
            } else {
                unreachable!()
            }
        });
    });
    
    // Compare with standard conversion
    group.bench_function("i32_standard_to_ffi", |b| {
        b.iter(|| wasm_value_to_ffi(black_box(&i32_value)));
    });
    
    // Test zero-copy potential for large values
    let i64_value = WasmValue::I64(0x1234567890ABCDEF);
    group.bench_function("i64_zero_copy_attempt", |b| {
        b.iter(|| {
            // Attempt to avoid intermediate allocation
            let v = black_box(&i64_value);
            if let WasmValue::I64(val) = v {
                let bytes = val.to_le_bytes();
                WasmValueFFI {
                    value_type: WASM_TYPE_I64,
                    data: bytes,
                }
            } else {
                unreachable!()
            }
        });
    });
    
    group.finish();
}

criterion_group!(
    benches,
    bench_single_to_ffi,
    bench_single_from_ffi,
    bench_batch_conversion,
    bench_memory_layout,
    bench_optimized_paths
);
criterion_main!(benches);