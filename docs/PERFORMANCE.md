# Performance Optimization Guide

This document provides comprehensive guidance on performance optimization strategies, benchmarking methodologies, and performance monitoring for the WAMR4J project.

## Performance Targets

WAMR4J aims to achieve the following performance targets across all supported platforms:

| Target | Threshold | Rationale |
|--------|-----------|-----------|
| Function Call Overhead | <10ns | Minimize FFI boundary crossing cost |
| Module Loading | <100ms | Acceptable startup time for typical modules |
| Startup Time | <50ms | Fast native library initialization |
| Memory Allocation | Zero-copy where possible | Reduce GC pressure |

## Architecture Overview

### FFI Layer Optimization

The WAMR4J performance characteristics are primarily determined by the Foreign Function Interface (FFI) layer efficiency:

```
Java Application
    ↓
Public WAMR4J API (ai.tegmentum.wamr4j)
    ↓
Runtime Implementation (JNI/Panama)
    ↓
Native Rust Library (wamr4j-native)
    ↓
WAMR C Runtime
```

Key optimization points:
1. **FFI Boundary Crossing**: Minimize data marshaling overhead
2. **Memory Management**: Reduce allocation/deallocation frequency
3. **Call Batching**: Group related operations to reduce round trips
4. **Data Structures**: Use cache-friendly layouts and zero-copy transfers

## Optimization Strategies

### 1. Function Call Boundary Optimization

#### JNI Optimizations

```rust
// Use direct field access for simple getters
#[no_mangle]
pub extern "C" fn wamr_memory_size(memory: *mut c_void) -> u32 {
    if memory.is_null() {
        return 0;
    }
    
    // Direct field access - optimized to single memory read
    unsafe { (*(memory as *mut WamrMemory)).size }
}
```

#### Panama FFI Optimizations

```rust
// Use repr(C) and field alignment for optimal memory layout
#[repr(C)]
#[derive(Clone)]
pub struct WamrModuleInfo {
    pub name_ptr: *const c_char,
    pub name_len: usize,
    pub size: u32,
    pub function_count: u32,
}

// Export functions with consistent calling convention
#[no_mangle]
pub extern "C" fn wamr_module_get_info(
    module: *mut c_void,
    info: *mut WamrModuleInfo
) -> bool {
    // Zero-copy information transfer
    // No heap allocation required
}
```

### 2. Memory Management Optimization

#### Custom Allocators

```rust
use std::alloc::{GlobalAlloc, Layout};

// Custom allocator for specific use cases
pub struct ArenaAllocator {
    // Implementation for temporary object allocation
}

// Pool allocator for frequently created/destroyed objects
pub struct PoolAllocator<T> {
    // Object pooling implementation
}
```

#### Zero-Copy Data Transfer

```rust
// Avoid data copying between Java and native code
#[no_mangle]
pub extern "C" fn wamr_memory_direct_access(
    memory: *mut c_void,
    offset: u32,
    length: u32
) -> *mut u8 {
    // Return direct pointer to WASM memory
    // No copying required
    unsafe {
        let wamr_memory = &mut *(memory as *mut WamrMemory);
        wamr_memory.data.as_mut_ptr().add(offset as usize)
    }
}
```

### 3. Compiler Optimizations

#### Cargo.toml Configuration

```toml
[profile.release]
opt-level = 3
lto = "fat"           # Link-time optimization
codegen-units = 1     # Better optimization, slower compile
panic = "abort"       # Smaller binary size
debug = false
strip = true          # Strip debug symbols

[profile.release.build-override]
opt-level = 3
codegen-units = 1
```

#### Performance-Critical Function Annotations

```rust
// Inline hot path functions
#[inline(always)]
pub fn validate_pointer(ptr: *mut c_void) -> bool {
    !ptr.is_null() && (ptr as usize) >= 0x1000
}

// Optimize for specific CPU features
#[target_feature(enable = "sse2,avx")]
unsafe fn optimized_memory_copy(src: *const u8, dst: *mut u8, len: usize) {
    // SIMD-optimized memory operations
}

// Branch prediction hints
#[inline]
pub fn likely_success_path(condition: bool) -> bool {
    if std::intrinsics::likely(condition) {
        true
    } else {
        false
    }
}
```

### 4. Platform-Specific Optimizations

#### x86_64 Optimizations

```rust
#[cfg(target_arch = "x86_64")]
mod x86_optimizations {
    use std::arch::x86_64::*;
    
    // SIMD operations for bulk data processing
    #[target_feature(enable = "sse2")]
    unsafe fn simd_memory_clear(ptr: *mut u8, len: usize) {
        // Use SIMD instructions for faster memory operations
    }
    
    // Cache-aware data structures
    #[repr(align(64))] // Align to cache line
    pub struct CacheAlignedData {
        pub data: [u8; 64],
    }
}
```

#### ARM64 Optimizations

```rust
#[cfg(target_arch = "aarch64")]
mod arm_optimizations {
    use std::arch::aarch64::*;
    
    // NEON SIMD operations
    #[target_feature(enable = "neon")]
    unsafe fn neon_optimized_operations() {
        // ARM NEON optimizations
    }
}
```

### 5. Error Handling Optimization

#### Fast Path Error Handling

```rust
// Optimize common success path
#[inline(always)]
pub fn wamr_fast_call(func: *mut c_void, args: &[u32]) -> Result<u32, WamrError> {
    // Fast path: assume success (most common case)
    let result = unsafe { wamr_call_function_fast(func, args.as_ptr(), args.len()) };
    
    if std::intrinsics::likely(result != WAMR_CALL_ERROR) {
        Ok(result)
    } else {
        // Slow path: handle error (uncommon case)
        Err(get_last_error())
    }
}
```

## Benchmarking Methodology

### Native Benchmarks (Criterion.rs)

```rust
use criterion::{black_box, criterion_group, criterion_main, Criterion};

fn bench_function_call_overhead(c: &mut Criterion) {
    let mut group = c.benchmark_group("function_call_overhead");
    
    // Configure measurement parameters
    group.significance_level(0.01);
    group.confidence_level(0.99);
    group.measurement_time(std::time::Duration::from_secs(10));
    
    group.bench_function("basic_call", |b| {
        b.iter(|| {
            let result = unsafe { wamr4j_test_function() };
            black_box(result);
        });
    });
    
    group.finish();
}
```

### JMH Java Benchmarks

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, warmups = 1)
public class FunctionCallBenchmark {
    
    private WebAssemblyFunction function;
    
    @Setup
    public void setup() {
        // Initialize WebAssembly function
    }
    
    @Benchmark
    public Object[] benchmarkFunctionCall() {
        return function.call(42, 24);
    }
}
```

## Performance Monitoring

### Continuous Performance Monitoring

The project includes automated performance monitoring with:

1. **CI Integration**: Performance benchmarks run on every commit
2. **Regression Detection**: Automatically detect performance regressions >10%
3. **Cross-Platform Validation**: Ensure consistent performance across platforms
4. **Historical Tracking**: Maintain performance trends over time

### Monitoring Scripts

#### Baseline Validation

```bash
# Validate performance baselines on current platform
./scripts/performance/validate-baselines.sh

# Expected output:
# [INFO] Starting performance baseline validation...
# [SUCCESS] Function call overhead: 8.42ns ≤ 10ns target
# [SUCCESS] Module loading: 87.3ms ≤ 100ms target
# [SUCCESS] All performance baselines validated successfully!
```

#### Continuous Monitoring

```bash
# Run performance monitoring with historical comparison
python3 ./scripts/performance/monitor-performance.py --ci

# Database-backed regression detection
python3 ./scripts/performance/monitor-performance.py --db performance-history.db
```

### Performance Dashboard

Key metrics tracked:

- **Function Call Latency**: P50, P95, P99 percentiles
- **Memory Usage**: Peak allocation, GC pressure
- **Throughput**: Operations per second
- **Regression Trends**: Performance over time

## Platform-Specific Considerations

### Linux Performance

- **glibc Optimizations**: Link against optimized glibc builds
- **NUMA Awareness**: Consider memory locality for multi-socket systems
- **Scheduler Affinity**: Pin performance-critical threads

### macOS Performance

- **Rosetta 2 Impact**: Consider x86_64 vs ARM64 performance differences
- **Code Signing**: Minimize impact on load-time performance
- **Memory Pressure**: Handle macOS memory management efficiently

### Windows Performance

- **MSVC Optimizations**: Use profile-guided optimization (PGO)
- **Windows API Integration**: Optimize system call usage
- **DLL Loading**: Minimize dynamic linking overhead

## Troubleshooting Performance Issues

### Common Performance Problems

1. **High FFI Overhead**
   - **Symptom**: Function calls taking >50ns
   - **Solution**: Reduce parameter marshaling, use direct memory access

2. **Memory Allocation Pressure**
   - **Symptom**: Frequent GC pauses, high allocation rates
   - **Solution**: Implement object pooling, zero-copy patterns

3. **Lock Contention**
   - **Symptom**: Poor multi-threaded scaling
   - **Solution**: Use lock-free data structures, reduce critical sections

4. **Cache Misses**
   - **Symptom**: Lower than expected single-threaded performance
   - **Solution**: Improve data locality, align structures to cache lines

### Performance Profiling Tools

#### Native Profiling

```bash
# Linux perf profiling
perf record --call-graph=dwarf ./target/release/benchmark
perf report

# Valgrind memory profiling
valgrind --tool=callgrind ./target/release/benchmark

# Intel VTune (commercial)
vtune -collect hotspots ./target/release/benchmark
```

#### Java Profiling

```bash
# JFR (Java Flight Recorder)
java -XX:+FlightRecorder -XX:StartFlightRecording=duration=30s,filename=profile.jfr

# Async-profiler
java -javaagent:async-profiler.jar=start,event=cpu,file=profile.html
```

## Performance Testing Strategy

### Testing Levels

1. **Unit Performance Tests**: Individual function call overhead
2. **Integration Performance Tests**: End-to-end workflow timing
3. **Load Testing**: Multi-threaded performance validation
4. **Stress Testing**: Resource exhaustion scenarios

### Test Environment Requirements

- **Dedicated Hardware**: Consistent, isolated test environment
- **Stable OS Configuration**: Minimal background processes
- **Reproducible Conditions**: Fixed CPU frequencies, memory configurations
- **Multiple Iterations**: Statistical significance validation

## Future Optimization Opportunities

### Planned Optimizations

1. **Async Function Calls**: Non-blocking FFI operations
2. **SIMD Acceleration**: Vector operations for bulk data processing
3. **JIT Compilation**: Runtime code generation for hot paths
4. **Memory Pool Optimization**: Custom allocators for specific use cases

### Research Areas

- **GPU Acceleration**: Offload computationally intensive operations
- **WASM-to-Native Compilation**: Direct native code generation
- **Profile-Guided Optimization**: Dynamic optimization based on usage patterns

## Performance Best Practices

### Development Guidelines

1. **Measure Before Optimizing**: Always profile before making changes
2. **Optimize Hot Paths**: Focus on frequently executed code
3. **Maintain Safety**: Never compromise memory safety for performance
4. **Document Trade-offs**: Explain optimization decisions and trade-offs
5. **Validate Optimizations**: Ensure optimizations actually improve performance

### Code Review Checklist

- [ ] Performance impact assessed and measured
- [ ] Memory allocation patterns reviewed
- [ ] Error handling paths optimized for common cases
- [ ] Platform-specific optimizations documented
- [ ] Benchmarks updated to cover new code paths
- [ ] Performance regression tests added

## Conclusion

Performance optimization in WAMR4J requires a systematic approach combining:

- **Target-driven development** with specific performance goals
- **Comprehensive benchmarking** using both native and JVM tools
- **Continuous monitoring** with automated regression detection
- **Platform-specific optimizations** while maintaining portability
- **Safety-first approach** that never compromises memory safety

The performance monitoring and optimization infrastructure documented here provides the foundation for achieving and maintaining the <10ns function call overhead target while ensuring consistent performance across all supported platforms.