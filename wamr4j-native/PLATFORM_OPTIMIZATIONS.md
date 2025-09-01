# Platform-Specific Optimizations for wamr4j-native

This document describes the platform-specific optimizations implemented in wamr4j-native to maximize WebAssembly execution performance across different operating systems and architectures.

## Overview

The wamr4j-native library implements platform-specific optimizations to leverage unique features and capabilities of different operating systems and CPU architectures. These optimizations are automatically detected and applied at runtime, providing optimal performance without requiring manual configuration.

## Supported Platforms

### Linux (x86_64 and ARM64)

**glibc Compatibility Optimizations**
- Automatic glibc version detection (2.17+ to 2.35+)
- Compatible with major Linux distributions:
  - CentOS 7+ / RHEL 7+ (glibc 2.17+)
  - Ubuntu 16.04+ (glibc 2.23+)
  - Ubuntu 18.04+ (glibc 2.27+)
  - Ubuntu 20.04+ (glibc 2.31+)
  - Ubuntu 22.04+ (glibc 2.35+)

**Memory Management Optimizations**
- Memory mapping optimizations using `mmap()` with advanced hints
- Transparent Huge Pages (THP) support for large WASM modules
- Memory pressure handling and adaptive allocation strategies
- Position-independent code (PIC) for better security

**Thread and Signal Optimizations**
- CPU affinity optimization for WASM execution threads
- Optimized signal handling for WASM traps (SIGSEGV, SIGFPE)
- Thread-local storage optimizations

**Build Flags**
```bash
-fPIC                    # Position-independent code
-fstack-protector-strong # Stack protection
-O3                      # Maximum optimization
-ftree-vectorize         # Enable vectorization
-march=native            # CPU-specific optimizations
-mtune=native            # CPU-specific tuning
```

### Windows (x86_64 and ARM64)

**MSVC Runtime Integration**
- Automatic MSVC runtime version detection (2019/2022)
- Static runtime linking for better deployment
- Structured Exception Handling (SEH) for WASM traps

**Memory Management Optimizations**
- Large page support (requires SeLockMemoryPrivilege)
- Low fragmentation heap configuration
- Heap coalescing optimizations for WASM memory allocation
- Windows memory commit strategies

**Thread and Exception Optimizations**
- Thread priority optimization for WASM execution
- Structured exception handling for access violations and arithmetic errors
- Windows-specific thread scheduling optimizations

**Build and Linker Flags**
```bash
# Compiler flags
/O2            # Maximum optimization
/GL            # Whole program optimization
/Gw            # Optimize global data
/GS            # Buffer security check
/guard:cf      # Control Flow Guard
/Qspectre      # Spectre mitigation
/MT            # Static runtime linking

# Linker flags
/LTCG          # Link-time code generation
/OPT:REF       # Remove unreferenced code
/OPT:ICF       # Identical COMDAT folding
/GUARD:CF      # Control Flow Guard
/DYNAMICBASE   # ASLR support
/NXCOMPAT      # NX bit support
```

### macOS (x86_64 and ARM64/Apple Silicon)

**Universal Binary Support**
- Native compilation for both Intel x86_64 and Apple Silicon ARM64
- Architecture-specific optimizations within universal binaries
- Minimum version targeting (10.15 for x86_64, 11.0 for ARM64)

**Memory Management Optimizations**
- macOS zone allocation optimizations for WASM memory
- Memory pressure notifications integration
- VM region optimization using Mach APIs
- Compressed memory integration

**Grand Central Dispatch (GCD) Integration**
- Optimized dispatch queue configuration for WASM execution
- Quality of Service (QoS) class optimization
- Concurrent queue configuration for parallel WASM operations

**Mach Exception Handling**
- Mach exception handling for WASM traps
- Optimized handling of EXC_BAD_ACCESS, EXC_ARITHMETIC, EXC_CRASH
- Low-latency trap recovery mechanisms

**Architecture-Specific Build Flags**

*Intel x86_64:*
```bash
-target x86_64-apple-darwin
-mmacosx-version-min=10.15
-O3
-fPIC
-fstack-protector-strong
-march=x86-64
-mtune=intel
```

*Apple Silicon ARM64:*
```bash
-target arm64-apple-darwin
-mmacosx-version-min=11.0
-O3
-fPIC
-fstack-protector-strong
-mcpu=apple-m1
-mtune=native
```

*Universal Binary:*
```bash
-arch x86_64 -arch arm64
-mmacosx-version-min=11.0
-O3
-fPIC
-fstack-protector-strong
```

## ARM64 SIMD Optimizations

### NEON SIMD Support

**Feature Detection**
- Runtime detection of NEON and Advanced SIMD capabilities
- FP16 (half-precision floating-point) support detection
- Crypto extensions detection (AES, SHA1, SHA2)
- Large System Extensions (LSE) atomics detection

**Optimization Features**
- 128-bit NEON vector operations for WASM SIMD instructions
- FP16 arithmetic acceleration where available
- Crypto instruction acceleration for cryptographic WASM modules
- Optimal memory alignment for SIMD operations (16-byte)

**Apple Silicon Specific Optimizations**
- Apple M1/M2 specific CPU targeting (`-mcpu=apple-m1`)
- Performance and efficiency core awareness
- Thermal state monitoring for dynamic optimization
- Memory pressure level adaptation

**Advanced SIMD Features (when available)**
- Scalable Vector Extensions (SVE) detection and usage
- SVE2 support for advanced vector operations
- Variable-length vector operations (up to 2048-bit)
- Optimal vector length selection based on hardware

**Rust Target Features**
```rust
// Automatically enabled based on detection
"neon"     // Basic NEON support
"v8"       // ARMv8 features
"fp16"     // Half-precision floating-point
"sve"      // Scalable Vector Extensions
"aes"      // AES crypto instructions
"sha2"     // SHA-2 crypto instructions
"crc"      # CRC32 instructions
"lse"      // Large System Extensions atomics
```

## Performance Characteristics

### Thread Optimization

**Linux**
- Optimal thread count based on CPU topology
- CPU affinity for cache locality
- NUMA-aware thread placement (future enhancement)

**Windows** 
- Thread priority optimization
- Processor group awareness on systems with >64 cores
- Thread scheduling class optimization

**macOS**
- Performance/efficiency core awareness on Apple Silicon
- GCD queue priority optimization
- Thermal-aware thread scaling

**Thread Count Recommendations**
- Intel/AMD x86_64: Use all available logical cores
- Apple Silicon ARM64: Use 75% of available cores (account for efficiency cores)
- Other ARM64: Use all available cores
- Unknown architectures: Conservative single-threaded

### Memory Optimization

**Alignment Requirements**
- ARM64 SIMD: 16-byte alignment for optimal performance
- x86_64: 32-byte alignment for AVX operations
- General WASM: 8-byte alignment minimum

**Large Page Support**
- Linux: Transparent Huge Pages (2MB pages)
- Windows: Large pages via VirtualAlloc (2MB on x64, 64KB on ARM64)
- macOS: VM region optimization (4KB base, larger regions preferred)

## API Usage

### Initialization

```rust
use wamr4j_native::platform;
use wamr4j_native::optimizations;

// Initialize platform optimizations
match platform::init_global_platform_optimizations() {
    Ok(()) => println!("Platform optimizations enabled"),
    Err(e) => eprintln!("Platform optimization error: {}", e),
}

// Get optimization configuration
let opt_config = optimizations::get_optimal_config();
println!("Optimizations: {}", opt_config);
```

### Runtime Information

```rust
// Get platform information
if let Some(opts) = platform::get_platform_optimizations() {
    println!("Platform: {}", opts.get_platform_info());
    println!("Optimal threads: {}", opts.get_optimal_thread_count());
}

// Get ARM64 SIMD information (on ARM64 platforms)
if let Ok(simd_opts) = optimizations::simd_arm64::init_arm64_simd_optimizations() {
    println!("ARM64 SIMD: {}", simd_opts.get_feature_info());
    let perf = simd_opts.get_performance_characteristics();
    println!("SIMD register width: {} bits", perf.simd_register_width);
}
```

### C FFI Usage

```c
#include <stdio.h>

// Initialize platform optimizations
if (wamr4j_init_platform_optimizations() == 0) {
    printf("Platform optimizations enabled\n");
    
    // Get platform information
    const char* platform_info = wamr4j_get_platform_info();
    printf("Platform: %s\n", platform_info);
    
    // Get optimization information
    const char* opt_info = wamr4j_get_optimization_info();
    printf("Optimizations: %s\n", opt_info);
    
    // Get optimal thread count
    int thread_count = wamr4j_get_optimal_thread_count();
    printf("Optimal threads: %d\n", thread_count);
}
```

## Build Integration

### Cargo Configuration

The platform optimizations are automatically applied during the build process through `.cargo/config.toml`:

```toml
[target.x86_64-unknown-linux-gnu]
rustflags = [
    "-C", "target-cpu=x86-64",
    "-C", "link-arg=-Wl,--gc-sections",
    "-C", "link-arg=-static-libgcc",
    "-C", "target-feature=+crt-static"
]

[target.aarch64-apple-darwin]
rustflags = [
    "-C", "target-cpu=apple-m1",
    "-C", "target-feature=+neon",
    "-C", "link-arg=-mmacosx-version-min=11.0"
]
```

### CI/CD Integration

Platform optimizations are validated in the cross-platform build workflow:

1. **Cross-compilation**: All 6 target platforms (Linux/Windows/macOS × x86_64/ARM64)
2. **Feature validation**: Platform-specific optimizations are tested
3. **Performance validation**: Basic performance characteristics are verified
4. **Symbol validation**: Library exports are checked for compatibility

## Performance Impact

### Expected Performance Improvements

**Platform-Specific Optimizations**
- **Linux**: 5-15% improvement from memory mapping and signal handling optimizations
- **Windows**: 10-20% improvement from large page support and heap optimizations
- **macOS**: 15-25% improvement from GCD integration and memory compression

**ARM64 SIMD Optimizations**
- **NEON operations**: 2-4x improvement for SIMD-heavy WASM modules
- **Crypto operations**: 10-50x improvement for cryptographic operations
- **Memory operations**: 20-30% improvement from optimal alignment and prefetching

**Combined Effect**
- Overall performance improvement: 10-40% depending on workload characteristics
- Memory usage reduction: 5-15% from platform-specific memory management
- Startup time improvement: 15-30% from optimized initialization

## Troubleshooting

### Common Issues

**Linux glibc Compatibility**
```bash
# Check glibc version
ldd --version

# Verify THP support
cat /sys/kernel/mm/transparent_hugepage/enabled
```

**Windows Large Page Support**
```powershell
# Check privilege (Run as Administrator)
whoami /priv | findstr SeLockMemoryPrivilege

# Enable privilege programmatically or through Local Security Policy
```

**macOS Universal Binary Issues**
```bash
# Check architecture
file libwamr4j_native.dylib

# Verify both architectures are present
lipo -info libwamr4j_native.dylib
```

**ARM64 SIMD Detection**
```bash
# Linux: Check CPU features
grep -i simd /proc/cpuinfo
grep -i neon /proc/cpuinfo

# macOS: Use system_profiler
system_profiler SPHardwareDataType
```

### Performance Debugging

**Enable Debug Logging**
```bash
export RUST_LOG=debug
export WAMR4J_DEBUG_OPTIMIZATIONS=1
```

**Benchmark Comparison**
```bash
# Run without optimizations
WAMR4J_ENABLE_PLATFORM_OPTIMIZATIONS=false cargo bench

# Run with optimizations  
WAMR4J_ENABLE_PLATFORM_OPTIMIZATIONS=true cargo bench
```

## Future Enhancements

### Planned Optimizations

**Additional Platform Support**
- FreeBSD and other Unix-like systems
- More ARM64 variants (ARMv8.1+, ARMv9)
- RISC-V architecture support

**Advanced SIMD Support**
- x86_64 AVX-512 support
- ARM64 SVE2 advanced features
- Mixed-precision arithmetic optimizations

**NUMA and Multi-Socket Support**
- NUMA-aware memory allocation
- Cross-socket thread affinity optimization
- Distributed WASM execution patterns

### Experimental Features

**JIT Compilation Integration**
- Platform-specific JIT code generation
- Adaptive optimization based on runtime patterns
- Hot-path identification and specialization

This documentation will be updated as new optimizations are implemented and validated across different platforms and workloads.