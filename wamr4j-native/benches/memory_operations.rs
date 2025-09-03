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

//! Benchmarks for memory operations and zero-copy optimizations
//!
//! This benchmark suite focuses on memory-intensive operations including
//! allocation patterns, data transfer, and zero-copy optimizations that
//! are critical for minimizing GC pressure and achieving high performance.

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId, Throughput};
use std::os::raw::{c_int, c_long, c_void};
use wamr4j_native::utils::{
    aligned_alloc, aligned_free, safe_memcpy, safe_memset,
    validate_buffer, format_slice
};

/// Benchmark memory allocation patterns
fn bench_memory_allocation(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_allocation");
    
    // Test different allocation sizes
    for size in [64, 256, 1024, 4096, 16384, 65536].iter() {
        group.throughput(Throughput::Bytes(*size as u64));
        
        group.bench_with_input(
            BenchmarkId::new("aligned_alloc", size),
            size,
            |b, &size| {
                b.iter(|| {
                    let ptr = aligned_alloc(black_box(size), 64);
                    if !ptr.is_null() {
                        aligned_free(ptr, size, 64);
                    }
                    black_box(ptr);
                });
            }
        );
        
        group.bench_with_input(
            BenchmarkId::new("standard_alloc", size),
            size,
            |b, &size| {
                b.iter(|| {
                    let layout = std::alloc::Layout::from_size_align(size, 64).unwrap();
                    let ptr = unsafe { std::alloc::alloc(layout) };
                    if !ptr.is_null() {
                        unsafe { std::alloc::dealloc(ptr, layout) };
                    }
                    black_box(ptr);
                });
            }
        );
    }
    
    group.finish();
}

/// Benchmark zero-copy memory transfer techniques
fn bench_zero_copy_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("zero_copy");
    
    // Test different data sizes for zero-copy potential
    for size in [1024, 4096, 16384, 65536, 262144].iter() {
        group.throughput(Throughput::Bytes(*size as u64));
        
        let source_data: Vec<u8> = (0..*size).map(|i| (i % 256) as u8).collect();
        
        // Standard memory copy
        group.bench_with_input(
            BenchmarkId::new("memcpy", size),
            &source_data,
            |b, data| {
                let mut destination = vec![0u8; data.len()];
                b.iter(|| {
                    unsafe {
                        std::ptr::copy_nonoverlapping(
                            black_box(data.as_ptr()),
                            black_box(destination.as_mut_ptr()),
                            black_box(data.len())
                        );
                    }
                    black_box(&destination);
                });
            }
        );
        
        // Safe wrapper around memcpy
        group.bench_with_input(
            BenchmarkId::new("safe_memcpy", size),
            &source_data,
            |b, data| {
                let mut destination = vec![0u8; data.len()];
                b.iter(|| {
                    let success = safe_memcpy(
                        black_box(destination.as_mut_ptr()),
                        black_box(data.as_ptr()),
                        black_box(data.len()),
                        black_box(destination.len())
                    );
                    black_box(success && !destination.is_empty());
                });
            }
        );
        
        // Slice-based copy (Rust idiomatic)
        group.bench_with_input(
            BenchmarkId::new("slice_copy", size),
            &source_data,
            |b, data| {
                let mut destination = vec![0u8; data.len()];
                b.iter(|| {
                    black_box(&mut destination[..]).copy_from_slice(black_box(data));
                    black_box(&destination);
                });
            }
        );
        
        // Zero-copy via pointer sharing (simulation)
        group.bench_with_input(
            BenchmarkId::new("pointer_sharing", size),
            &source_data,
            |b, data| {
                b.iter(|| {
                    // Simulate zero-copy by just passing pointer and length
                    let ptr = black_box(data.as_ptr());
                    let len = black_box(data.len());
                    black_box((ptr, len));
                });
            }
        );
    }
    
    group.finish();
}

/// Benchmark memory validation overhead
fn bench_memory_validation(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_validation");
    
    let data: Vec<u8> = (0..4096).map(|i| (i % 256) as u8).collect();
    let ptr = data.as_ptr();
    
    // Simple null check
    group.bench_function("null_check", |b| {
        b.iter(|| {
            let is_null = black_box(ptr).is_null();
            black_box(is_null);
        });
    });
    
    // Alignment validation
    group.bench_function("alignment_check", |b| {
        b.iter(|| {
            let aligned = (black_box(ptr) as usize) % 8 == 0;
            black_box(aligned);
        });
    });
    
    // Buffer bounds validation
    group.bench_function("bounds_check", |b| {
        b.iter(|| {
            let valid = validate_buffer(
                black_box(ptr),
                black_box(data.len()),
                black_box(8192)
            );
            black_box(valid);
        });
    });
    
    // Combined validation
    group.bench_function("full_validation", |b| {
        b.iter(|| {
            let ptr = black_box(ptr);
            let size = black_box(data.len());
            
            let not_null = !ptr.is_null();
            let aligned = (ptr as usize) % 8 == 0;
            let in_bounds = size <= 8192;
            
            let valid = not_null && aligned && in_bounds;
            black_box(valid);
        });
    });
    
    group.finish();
}

/// Benchmark memory initialization patterns
fn bench_memory_initialization(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_init");
    
    for size in [1024, 4096, 16384, 65536].iter() {
        group.throughput(Throughput::Bytes(*size as u64));
        
        // Zero initialization via memset
        group.bench_with_input(
            BenchmarkId::new("memset_zero", size),
            size,
            |b, &size| {
                let mut buffer = vec![0xFFu8; size]; // Start with non-zero
                b.iter(|| {
                    let success = safe_memset(
                        black_box(buffer.as_mut_ptr()),
                        black_box(0),
                        black_box(size),
                        black_box(buffer.len())
                    );
                    black_box(success);
                });
            }
        );
        
        // Pattern initialization
        group.bench_with_input(
            BenchmarkId::new("memset_pattern", size),
            size,
            |b, &size| {
                let mut buffer = vec![0u8; size];
                b.iter(|| {
                    let success = safe_memset(
                        black_box(buffer.as_mut_ptr()),
                        black_box(0xAA),
                        black_box(size),
                        black_box(buffer.len())
                    );
                    black_box(success);
                });
            }
        );
        
        // Rust slice fill
        group.bench_with_input(
            BenchmarkId::new("slice_fill", size),
            size,
            |b, &size| {
                let mut buffer = vec![0u8; size];
                b.iter(|| {
                    black_box(&mut buffer[..]).fill(black_box(0xBB));
                    black_box(&buffer);
                });
            }
        );
    }
    
    group.finish();
}

/// Benchmark WASM memory operations simulation
fn bench_wasm_memory_simulation(c: &mut Criterion) {
    let mut group = c.benchmark_group("wasm_memory_sim");
    
    // Simulate WASM page operations (64KB pages)
    const WASM_PAGE_SIZE: usize = 65536;
    
    // Memory growth simulation
    group.bench_function("page_allocation", |b| {
        b.iter(|| {
            let ptr = aligned_alloc(black_box(WASM_PAGE_SIZE), 64);
            if !ptr.is_null() {
                aligned_free(ptr, WASM_PAGE_SIZE, 64);
            }
            black_box(ptr);
        });
    });
    
    // Memory read/write patterns
    let mut wasm_memory = vec![0u8; WASM_PAGE_SIZE * 4]; // 4 pages
    
    group.bench_function("sequential_read", |b| {
        b.iter(|| {
            let mut checksum: u64 = 0;
            for chunk in wasm_memory.chunks(8) {
                if chunk.len() == 8 {
                    checksum ^= u64::from_le_bytes([
                        chunk[0], chunk[1], chunk[2], chunk[3],
                        chunk[4], chunk[5], chunk[6], chunk[7]
                    ]);
                }
            }
            black_box(checksum);
        });
    });
    
    group.bench_function("random_access", |b| {
        let offsets: Vec<usize> = (0..1000)
            .map(|i| (i * 37 + 17) % (wasm_memory.len() - 8))
            .collect();
        
        b.iter(|| {
            let mut checksum: u64 = 0;
            for &offset in black_box(&offsets) {
                let value = u64::from_le_bytes([
                    wasm_memory[offset + 0], wasm_memory[offset + 1],
                    wasm_memory[offset + 2], wasm_memory[offset + 3],
                    wasm_memory[offset + 4], wasm_memory[offset + 5],
                    wasm_memory[offset + 6], wasm_memory[offset + 7],
                ]);
                checksum ^= value;
            }
            black_box(checksum);
        });
    });
    
    group.bench_function("bulk_write", |b| {
        let pattern: Vec<u8> = (0..1024).map(|i| (i % 256) as u8).collect();
        
        b.iter(|| {
            for (i, chunk) in wasm_memory.chunks_mut(pattern.len()).enumerate() {
                if chunk.len() == pattern.len() {
                    chunk.copy_from_slice(black_box(&pattern));
                }
                // Limit iterations to avoid excessive runtime
                if i >= 16 {
                    break;
                }
            }
            black_box(&wasm_memory[0..1024]);
        });
    });
    
    group.finish();
}

/// Benchmark cache-friendly access patterns
fn bench_cache_patterns(c: &mut Criterion) {
    let mut group = c.benchmark_group("cache_patterns");
    
    // Large data set to test cache behavior
    let large_data: Vec<u64> = (0..65536).map(|i| i as u64).collect();
    
    // Sequential access (cache-friendly)
    group.bench_function("sequential_sum", |b| {
        b.iter(|| {
            let mut sum = 0u64;
            for &value in black_box(&large_data) {
                sum = sum.wrapping_add(value);
            }
            black_box(sum);
        });
    });
    
    // Strided access patterns
    for stride in [1, 2, 4, 8, 16, 64].iter() {
        group.bench_with_input(
            BenchmarkId::new("strided_sum", stride),
            stride,
            |b, &stride| {
                b.iter(|| {
                    let mut sum = 0u64;
                    let mut i = 0;
                    while i < large_data.len() {
                        sum = sum.wrapping_add(large_data[i]);
                        i += stride;
                    }
                    black_box(sum);
                });
            }
        );
    }
    
    // Prefetch simulation
    group.bench_function("prefetch_aware", |b| {
        b.iter(|| {
            let mut sum = 0u64;
            for chunk in large_data.chunks(64) {
                // Simulate prefetch by accessing first element
                let _prefetch = chunk[0];
                for &value in chunk {
                    sum = sum.wrapping_add(value);
                }
            }
            black_box(sum);
        });
    });
    
    group.finish();
}

criterion_group!(
    benches,
    bench_memory_allocation,
    bench_zero_copy_operations,
    bench_memory_validation,
    bench_memory_initialization,
    bench_wasm_memory_simulation,
    bench_cache_patterns
);
criterion_main!(benches);