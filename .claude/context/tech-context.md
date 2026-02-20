---
created: 2025-08-31T16:14:15Z
last_updated: 2025-08-31T16:14:15Z
version: 1.0
author: Claude Code PM System
---

# Technical Context

## Project Technology Stack

### Primary Languages
- **Java**: Primary language for public API and implementations
  - **JNI Version Support**: Java 8+ (minimum for JNI implementation)
  - **Panama Version Support**: Java 23+ (latest stable Panama release)
  - **Runtime Selection**: Automatic detection with manual override capability
- **Rust**: Native library development for WAMR bindings
  - **Target Version**: Latest stable Rust toolchain
  - **Purpose**: Single shared library with both JNI and Panama FFI exports

### WebAssembly Runtime
- **WAMR (WebAssembly Micro Runtime)**: Version 2.4.4 (latest)
- **Repository**: https://github.com/bytecodealliance/wamr
- **Integration**: Build from source during Maven build process
- **API Access**: Through Rust wrapper layer

## Build System & Tools

### Maven Configuration
- **Build Tool**: Apache Maven with Maven Wrapper (`mvnw`)
- **Project Structure**: Multi-module Maven project
- **Java Versions**: Support range from Java 8 to Java 23+
- **Dependency Management**: Centralized in parent POM

### Static Analysis & Code Quality
- **Checkstyle**: Google Java Style Guide enforcement
- **Spotless**: Automatic code formatting with Google Java Style
- **SpotBugs**: Bug pattern detection with FindSecBugs plugin
- **PMD**: Additional static analysis
- **JaCoCo**: Test coverage reporting

### Testing Framework
- **Primary Framework**: JUnit 5 (Jupiter)
- **Test Runner**: Maven Surefire Plugin
- **Performance Testing**: JMH (Java Microbenchmark Harness)
- **Test Categories**: Unit, Integration, Performance, WebAssembly test suites

## Development Dependencies

### Core Dependencies (Planned)
```xml
<!-- Expected main dependencies -->
<dependencies>
  <!-- JUnit 5 for testing -->
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  
  <!-- JMH for benchmarking -->
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-core</artifactId>
  
  <!-- Logging (minimal external dependencies) -->
  <groupId>java.util.logging</groupId> <!-- Built-in JUL -->
</dependencies>
```

### Build Plugins (Planned)
- **Maven Compiler Plugin**: Java compilation with version targeting
- **Maven Surefire Plugin**: Test execution
- **Checkstyle Plugin**: Code style enforcement
- **Spotless Plugin**: Automatic formatting
- **SpotBugs Plugin**: Static analysis
- **JaCoCo Plugin**: Coverage reporting
- **Rust Cargo Integration**: Custom plugin/configuration for Rust build

### Native Development Tools
- **Rust Toolchain**: 
  - `rustc` (Rust compiler)
  - `cargo` (Rust build system)
  - Cross-compilation targets for multiple platforms
- **WAMR Build Requirements**:
  - CMake (for WAMR compilation)
  - C compiler (gcc/clang)
  - Platform-specific build tools

## Platform Support

### Target Platforms
- **Linux**: x86_64, ARM64
- **Windows**: x86_64, ARM64  
- **macOS**: x86_64, ARM64 (Apple Silicon)

### Cross-Compilation Strategy
- **Build Process**: Maven build coordinates Rust cross-compilation
- **Native Libraries**: Platform-specific binaries packaged into JARs
- **Runtime Loading**: Automatic platform detection and library extraction

## Logging Strategy

### Logging Framework
- **Primary**: `java.util.logging` (JUL) - built-in, minimal dependencies
- **Levels Used**: SEVERE, WARNING, INFO, FINE, FINER, FINEST
- **Configuration**: Programmatic configuration to avoid external files
- **Structured Logging**: Consistent patterns across modules

### Logging Categories
- **Native Library Loading**: Library extraction, platform detection, loading success/failure
- **WebAssembly Operations**: Module compilation, instantiation, execution
- **Runtime Selection**: JNI vs Panama detection and selection logic
- **Error Conditions**: All error paths with context for debugging
- **Performance Metrics**: Key operation timings and resource usage

## Performance Considerations

### Optimization Priorities
1. **Safety First**: All optimizations must maintain defensive programming principles
2. **JNI/Panama Overhead**: Minimize native call frequency through batching
3. **Memory Management**: Efficient allocation patterns, GC-friendly designs
4. **Native Resource Caching**: Appropriate caching without memory leaks
5. **Benchmark-Driven**: JMH performance tests guide optimization decisions

### Key Performance Areas
- **Module Loading**: WebAssembly module compilation and caching
- **Function Invocation**: Host-to-WASM and WASM-to-host call overhead
- **Memory Operations**: WebAssembly linear memory access patterns
- **Runtime Switching**: Overhead of factory-based runtime selection

## Security Considerations

### Input Validation
- **WebAssembly Modules**: Validate module format and safety before compilation
- **Function Parameters**: Comprehensive validation for all native calls
- **Memory Access**: Bounds checking for WebAssembly memory operations
- **Resource Limits**: Configurable limits on memory usage, execution time

### Native Code Safety
- **JVM Crash Prevention**: Defensive programming as highest priority
- **Error Handling**: Graceful error propagation without native crashes
- **Resource Cleanup**: Proper cleanup of native resources
- **Memory Safety**: Rust's memory safety features protect native layer

## Development Environment Requirements

### Required Tools
- **Java Development Kit**: Version 8+ for development, 23+ for Panama testing
- **Maven**: Version 3.6+ (or use included Maven wrapper)
- **Rust Toolchain**: Latest stable version
- **Git**: Version control

### Optional Tools
- **Docker**: For cross-platform build testing
- **IDE Support**: IntelliJ IDEA, Eclipse, VS Code with appropriate plugins
- **Debugger**: Native debugging tools for troubleshooting native layer

## Integration Points

### External Systems
- **WAMR Runtime**: Native WebAssembly execution engine
- **JNI**: Java Native Interface for native integration
- **Panama FFI**: Foreign Function & Memory API for modern Java
- **Rust FFI**: Foreign Function Interface from Rust to C-compatible APIs

### Internal Architecture
- **Factory Pattern**: Runtime selection and instantiation
- **Interface Segregation**: Clean API boundaries between modules  
- **Dependency Injection**: Configurable runtime selection
- **Error Translation**: Native errors to Java exceptions

## Notes
- **Minimal External Dependencies**: Prefer built-in Java APIs where possible
- **Cross-Platform First**: All features must work across supported platforms
- **Version Compatibility**: Support wide range of Java versions through runtime selection
- **Performance Testing**: Comprehensive benchmarking from the beginning