# WAMR4J - WebAssembly Micro Runtime for Java

[![Maven Central](https://img.shields.io/maven-central/v/ai.tegmentum/wamr4j.svg)](https://central.sonatype.com/search?q=ai.tegmentum.wamr4j)
[![Java Version](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://openjdk.org/projects/jdk/11/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Build Status](https://github.com/tegmentum-ai/wamr4j/workflows/CI/badge.svg)](https://github.com/tegmentum-ai/wamr4j/actions)

**WAMR4J** provides unified Java bindings for the [WAMR (WebAssembly Micro Runtime)](https://github.com/bytecodealliance/wamr), offering both JNI and Panama Foreign Function API implementations with a common interface that abstracts engine-specific details.

## Key Features

- **Unified API**: Single interface for both JNI and Panama implementations
- **Multi-Runtime**: Automatic JNI/Panama selection based on Java version
- **Defensive Programming**: JVM crash prevention with comprehensive validation
- **High Performance**: Optimized native bindings with minimal overhead
- **Cross-Platform**: Support for Linux, macOS, and Windows (x86_64, ARM64)
- **Zero Dependencies**: No external runtime dependencies
- **Post-MVP Features**: Bulk Memory, Reference Types, and SIMD support

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>ai.tegmentum</groupId>
    <artifactId>wamr4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle Dependency

```gradle
implementation 'ai.tegmentum:wamr4j:1.0.0'
```

### Basic Usage

```java
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyInstance;

// Load WebAssembly module
byte[] wasmBytes = Files.readAllBytes(Paths.get("module.wasm"));

// Create runtime (automatically selects JNI or Panama)
try (WebAssemblyRuntime runtime = WebAssemblyRuntime.create()) {
    // Compile WebAssembly module
    try (WebAssemblyModule module = runtime.loadModule(wasmBytes)) {
        // Create instance with imports
        Map<String, Object> imports = Map.of(
            "env.memory", WebAssemblyMemory.create(1, 10),
            "env.print", (WebAssemblyFunction) (args) -> {
                System.out.println("Hello from WebAssembly: " + args[0]);
                return new Object[0];
            }
        );
        
        try (WebAssemblyInstance instance = module.instantiate(imports)) {
            // Call exported function
            WebAssemblyFunction add = instance.getFunction("add");
            Object[] result = add.call(20, 22);
            System.out.println("20 + 22 = " + result[0]); // Output: 20 + 22 = 42
        }
    }
}
```

## Architecture

WAMR4J follows a multi-runtime architecture with automatic runtime selection:

### Module Structure

- **`wamr4j`**: Public API interfaces and factory (users only interact with this)
- **`wamr4j-jni`**: JNI implementation for Java 11-22 (internal/private)
- **`wamr4j-panama`**: Panama FFI implementation for Java 23+ (internal/private)  
- **`wamr4j-native`**: Shared native Rust library for both implementations
- **`wamr4j-benchmarks`**: Performance benchmarks comparing implementations
- **`wamr4j-tests`**: Integration tests and WebAssembly test suites

### Runtime Selection

WAMR4J automatically selects the optimal runtime:

1. **Java 23+**: Prefers Panama Foreign Function API (if available)
2. **Java 11-22**: Uses JNI implementation
3. **Fallback**: Uses JNI if Panama unavailable (with warning)
4. **Manual Override**: Use system property `-Dwamr4j.runtime=jni` to force JNI

## Development Setup

### Prerequisites

- **Java 11+** for core development
- **Java 23+** for Panama Foreign Function API development (optional)
- **Maven 3.8+** or use included wrapper (`./mvnw`)
- **Git** for version control

### Quick Setup (5 minutes)

```bash
# Clone the repository
git clone https://github.com/tegmentum-ai/wamr4j.git
cd wamr4j

# Build the project
./mvnw clean compile

# Run tests
./mvnw test -q

# Open in your favorite IDE
# IntelliJ IDEA: File > Open > Select wamr4j directory
# Eclipse: See eclipse/ECLIPSE_SETUP.md
```

### Essential Commands

```bash
# Build all modules
./mvnw clean compile

# Run tests (quiet mode)
./mvnw test -q

# Run static analysis (code quality checks)
./mvnw checkstyle:check spotless:check spotbugs:check

# Auto-format code
./mvnw spotless:apply

# Build and package
./mvnw clean package

# Install to local Maven repository
./mvnw clean install
```

## Code Quality

WAMR4J maintains high code quality standards:

- **Google Java Style Guide**: Strict adherence to formatting and conventions
- **Checkstyle**: Automated coding standards enforcement
- **SpotBugs**: Static analysis for bug detection
- **Spotless**: Automated code formatting
- **100% Javadoc Coverage**: Comprehensive API documentation

### Code Quality Commands

```bash
# Check code style compliance
./mvnw checkstyle:check

# Detect potential bugs
./mvnw spotbugs:check

# Verify code formatting
./mvnw spotless:check

# Auto-fix formatting issues
./mvnw spotless:apply

# Run all quality checks
./mvnw checkstyle:check spotless:check spotbugs:check
```

## Performance

WAMR4J is optimized for high-performance WebAssembly execution:

- **Minimal Native Call Overhead**: Batched operations and efficient data transfer
- **Memory Optimization**: Smart allocation patterns and GC-friendly design
- **Defensive Safety**: JVM crash prevention without performance compromise
- **Benchmarking Suite**: Comprehensive performance testing and monitoring

### Benchmark Categories

- **Runtime Benchmarks**: Runtime creation, module compilation, function invocation
- **Memory Operations**: Load, store, and copy throughput (1KB operations)
- **Large Module Compilation**: Scaling behavior with 10/100/500 function modules
- **Function Call Overhead**: Calling conventions with 0-8 parameters (JNI vs Panama)

### Running Benchmarks

```bash
# Build benchmark JAR
./mvnw clean package -pl wamr4j-benchmarks

# Run all benchmarks
java -jar wamr4j-benchmarks/target/benchmarks.jar

# Run specific benchmark
java -jar wamr4j-benchmarks/target/benchmarks.jar MemoryOperationsBenchmark

# Run with custom JMH options
java -jar wamr4j-benchmarks/target/benchmarks.jar -wi 10 -i 20 -f 3
```

## Platform Support

| Platform | Architecture | JNI Support | Panama Support | Status |
|----------|-------------|-------------|----------------|---------|
| Linux    | x86_64      | Yes         | Yes             | Stable  |
| Linux    | ARM64       | Yes         | Yes             | Stable  |
| macOS    | x86_64      | Yes         | Yes             | Stable  |
| macOS    | ARM64 (M1+) | Yes         | Yes             | Stable  |
| Windows  | x86_64      | Yes         | Yes             | Beta    |
| Windows  | ARM64       | --          | --             | Planned |

## Testing

WAMR4J includes comprehensive test coverage with **183 passing tests**:

### Test Categories

- **Comparison Tests** (134 tests): Verify JNI and Panama implementations produce identical results
  - Integer operations (i32/i64): arithmetic, bitwise, shifts, comparisons
  - Floating-point operations (f32/f64): arithmetic, division, comparisons
  - All WebAssembly MVP numeric operations

- **Integration Tests** (4 tests): Complex real-world WebAssembly programs
  - Fibonacci with memoization (memory + globals + recursion)
  - Circular buffer (memory + globals + arithmetic)
  - Calculator with dispatch table (tables + indirect calls)
  - Array sum (memory + loops + locals)

- **WAMR Engine Tests** (29 tests): Validate WAMR execution through bindings
  - Control flow: blocks, loops, branches, calls, unreachable
  - Memory: load, store, grow, data segments, bounds checking
  - Type conversions: wrap, extend, trunc, convert, reinterpret
  - Tables: indirect calls, out-of-bounds, recursion

- **Post-MVP Feature Tests** (16 tests): Validate post-MVP WebAssembly features
  - Bulk Memory Operations (5 tests): memory.copy, memory.fill, overlapping regions
  - Reference Types (6 tests): funcref, externref, ref.null, ref.is_null, ref.func
  - SIMD Operations (5 tests): v128 load/store, i32x4 arithmetic, lane operations

### Test Coverage Report

See [`wamr4j-tests/TEST_COVERAGE_REPORT.md`](wamr4j-tests/TEST_COVERAGE_REPORT.md) for detailed coverage analysis.

### Running Tests

```bash
# Run all tests (183 tests)
./mvnw test

# Run specific test module
./mvnw test -pl wamr4j-tests

# Run specific test category
./mvnw test -Dtest=*ComparisonTest     # Comparison tests
./mvnw test -Dtest=*IntegrationTest    # Integration tests
./mvnw test -Dtest=*SpecTest          # WAMR engine tests
./mvnw test -Dtest=*postmvp.*         # Post-MVP feature tests

# Run with coverage report
./mvnw test jacoco:report
```

### Test Quality

- **No Mocks**: All tests use real WAMR engine
- **Deterministic**: Consistent, reproducible results
- **Fast**: Complete suite runs in ~12 seconds
- **Isolated**: Proper resource cleanup
- **Comprehensive**: Happy paths, edge cases, error conditions

## Documentation

- **[Development Guide](DEVELOPMENT.md)**: Comprehensive development setup and workflow
- **[Troubleshooting Guide](TROUBLESHOOTING.md)**: Solutions to common issues
- **[Static Analysis Guide](STATIC_ANALYSIS.md)**: Code quality tooling documentation  
- **[API Documentation](https://tegmentum-ai.github.io/wamr4j/)**: Generated Javadoc
- **[Eclipse Setup Guide](eclipse/ECLIPSE_SETUP.md)**: Eclipse IDE configuration

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Related Projects

- **[WAMR](https://github.com/bytecodealliance/wamr)**: WebAssembly Micro Runtime (native implementation)
- **[Wasmtime Java](https://github.com/kawamuray/wasmtime-java)**: Java bindings for Wasmtime
- **[GraalWasm](https://www.graalvm.org/latest/reference-manual/wasm/)**: WebAssembly support in GraalVM

---

**Ready to get started?** Check out our [Development Guide](DEVELOPMENT.md) and dive in!