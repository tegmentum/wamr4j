---
name: core-native-wamr-integration
description: Foundational Rust native library providing complete WAMR 2.4.1 API integration for both JNI and Panama implementations
status: backlog
created: 2025-09-01T18:43:56Z
---

# PRD: Core Native WAMR Integration

## Executive Summary

The core native WAMR integration is the foundational component of the wamr4j project that provides a unified Rust native library exposing the complete WAMR (WebAssembly Micro Runtime) 2.4.1 API surface. This shared native library will serve as the single source of truth for both JNI and Panama FFI implementations, eliminating code duplication while ensuring consistent behavior across all Java runtime versions (8-23+).

The library will implement comprehensive defensive programming practices to prevent JVM crashes, provide complete cross-platform support for all target architectures, and expose every WAMR API through both JNI and Panama-compatible interfaces.

## Problem Statement

### What problem are we solving?
Currently, there is no Java library that provides safe, comprehensive access to the WAMR WebAssembly runtime across both traditional JNI (Java 8-22) and modern Panama FFI (Java 23+) approaches. Existing solutions either:
- Support only JNI or only Panama, limiting Java version compatibility
- Provide incomplete WAMR API coverage
- Lack proper defensive programming to prevent JVM crashes
- Require separate native libraries for each interface approach

### Why is this important now?
1. **Java Ecosystem Evolution**: Java 23+ introduces Panama FFI as the preferred native interop mechanism, but JNI remains critical for legacy support
2. **WebAssembly Adoption**: Growing enterprise adoption of WebAssembly requires production-ready Java bindings
3. **Safety Requirements**: JVM crashes in production systems are unacceptable, requiring defensive native code
4. **Performance Demands**: High-performance applications need direct access to all WAMR capabilities

## User Stories

### Primary User Personas

**Enterprise Java Developer (Sarah)**
- Needs to integrate WebAssembly modules into existing Java applications
- Requires guaranteed stability (no JVM crashes) in production environments
- Wants comprehensive API access without learning native programming
- Expects consistent behavior across different Java versions

**Framework Developer (Marcus)**  
- Building higher-level WebAssembly abstractions for Java ecosystem
- Needs complete WAMR API coverage to implement advanced features
- Requires both JNI and Panama support for broad compatibility
- Wants efficient native calls with minimal overhead

**Platform Engineer (Alex)**
- Deploying wamr4j across heterogeneous infrastructure (Linux/Windows/macOS, x86_64/ARM64)
- Needs reliable cross-platform native library distribution
- Requires consistent API behavior across all supported platforms
- Expects comprehensive error handling and diagnostics

### Detailed User Journeys

**Story 1: Safe WebAssembly Module Execution**
- As Sarah, I want to load and execute WebAssembly modules without risking JVM crashes
- Given a WebAssembly module file, when I load it through wamr4j, then all invalid inputs should be caught and handled gracefully
- Acceptance Criteria:
  - Invalid module files never cause JVM crashes
  - All WAMR errors are translated to appropriate Java exceptions
  - Resource cleanup happens automatically even on failures

**Story 2: Complete API Access**
- As Marcus, I want access to all WAMR capabilities to build comprehensive WebAssembly tooling
- Given the complete WAMR 2.4.1 API surface, when I use wamr4j, then every WAMR function should be accessible
- Acceptance Criteria:
  - 100% WAMR API coverage including advanced features
  - Consistent API signatures between JNI and Panama implementations
  - Full access to WAMR configuration options and runtime controls

**Story 3: Universal Platform Support**
- As Alex, I want to deploy the same wamr4j code across all infrastructure platforms
- Given applications running on different OS/architecture combinations, when I deploy wamr4j, then it should work identically everywhere
- Acceptance Criteria:
  - Native libraries available for Linux/Windows/macOS on x86_64/ARM64
  - Identical API behavior across all platform combinations
  - Automated platform detection and library loading

## Requirements

### Functional Requirements

**Core WAMR Integration**
- Complete binding of WAMR 2.4.1 API surface including:
  - Runtime creation and configuration
  - Module loading, compilation, and validation
  - Instance creation and management
  - Function invocation with all supported parameter/return types
  - Memory management and inspection
  - Global variable access
  - Table operations
  - Import/export handling
  - Exception handling and stack traces

**Dual Interface Support**
- JNI interface compatible with Java 8-22
- Panama FFI interface compatible with Java 23+
- Identical functional behavior between both interfaces
- Consistent error handling and resource management

**Cross-Platform Native Library**
- Single Rust codebase targeting all platforms:
  - Linux x86_64 and ARM64
  - Windows x86_64 and ARM64  
  - macOS x86_64 and ARM64
- Platform-specific optimizations where beneficial
- Consistent ABI across all platforms

**Resource Management**
- Automatic cleanup of WAMR resources on Java object finalization
- Manual resource disposal through AutoCloseable pattern
- Prevention of resource leaks even on abnormal termination
- Thread-safe resource access and cleanup

### Non-Functional Requirements

**Safety & Reliability**
- Zero JVM crashes under any input conditions
- Comprehensive input validation before native calls
- Graceful handling of all WAMR error conditions
- Memory safety guarantees for all native operations

**Performance**
- Minimal overhead for native calls (target <10ns per call)
- Efficient parameter marshalling between Java and native code
- Zero-copy operations where possible
- Batched operations to reduce call overhead

**Security**
- No buffer overflows or memory corruption vulnerabilities
- Proper handling of malicious WebAssembly modules
- Secure resource isolation between different modules
- No information leakage between WebAssembly instances

**Maintainability**
- Clear separation between JNI and Panama interface code
- Comprehensive error messages and diagnostics
- Extensive logging for debugging and troubleshooting
- Well-documented native APIs and data structures

## Success Criteria

**Functional Success**
- [ ] 100% WAMR 2.4.1 API coverage verified by test suite
- [ ] Both JNI and Panama interfaces pass identical test suites
- [ ] All target platforms (6 combinations) have working native libraries
- [ ] Zero JVM crashes in 10,000+ hour stress testing

**Performance Success**
- [ ] Native call overhead <10ns average on modern hardware
- [ ] Memory usage within 5% of direct WAMR usage
- [ ] WebAssembly execution performance within 2% of native WAMR
- [ ] Startup time <100ms for typical WebAssembly modules

**Quality Success**
- [ ] 100% line coverage in native Rust code
- [ ] All static analysis tools (Clippy, Miri) pass without warnings
- [ ] Comprehensive integration test suite covering edge cases
- [ ] Documentation covering every public API with examples

**Ecosystem Success**
- [ ] Successfully builds in CI/CD for all target platforms
- [ ] Integration tests pass on all supported Java versions (8, 11, 17, 21, 23+)
- [ ] Memory leak detection tools show zero leaks over 24-hour runs
- [ ] Fuzzing tests run successfully for 72+ hours without crashes

## Constraints & Assumptions

### Technical Constraints
- Must target WAMR 2.4.1 specifically (latest stable release)
- Rust toolchain required for development and compilation
- Native library size should remain <50MB per platform
- Must work with both OpenJDK and Oracle JDK implementations

### Resource Constraints
- Development can take as long as needed to ensure quality
- Cross-platform testing requires access to all target architectures
- Native library compilation adds complexity to build process
- Requires expertise in both Rust and Java native interfaces

### Assumptions
- WAMR 2.4.1 API remains stable during development period
- Target platforms maintain ABI compatibility
- Java Panama FFI APIs remain stable in Java 23+
- WAMR licensing (Apache 2.0) is compatible with project goals

## Out of Scope

The following items are explicitly excluded from this PRD:

**Build System Integration**
- Maven configuration for native compilation
- Cross-compilation toolchain setup
- Native library packaging and distribution
- CI/CD pipeline configuration for native builds

**Higher-Level APIs**
- Java-idiomatic wrapper APIs
- WebAssembly Component Model support
- WASI (WebAssembly System Interface) integration
- Custom host function implementations

**Performance Tools**
- JMH benchmarking framework
- Performance profiling and analysis tools
- Memory usage optimization beyond basic efficiency
- Specific performance tuning for particular workloads

**Development Tools**
- Debugging integration with Java IDEs
- WebAssembly module inspection utilities
- Development-time validation and linting tools
- Hot-reload or dynamic module replacement

## Dependencies

### External Dependencies
- **WAMR 2.4.1**: Core WebAssembly runtime library
- **Rust Toolchain**: For native library development and compilation
- **Target Platform SDKs**: Platform-specific development tools for cross-compilation
- **Java JDK Versions**: JDK 8, 11, 17, 21, 23+ for interface compatibility testing

### Internal Dependencies
- **wamr4j Module Structure**: Basic Maven project structure must exist
- **Static Analysis Setup**: Rust development tooling (Clippy, Miri, etc.)
- **Testing Framework**: Rust testing infrastructure for native code validation
- **Cross-Platform CI**: Infrastructure for testing on all target platforms

### Development Dependencies
- **Native Development Environment**: Rust, Cargo, cross-compilation targets
- **Java Development Environment**: Multiple JDK versions for compatibility testing
- **Platform Access**: Ability to test on Linux, Windows, macOS with both architectures
- **WAMR Source Code**: Access to WAMR repository for integration and updates

### Delivery Dependencies
- **Native Library Artifacts**: Compiled libraries for all target platforms
- **Interface Specifications**: Detailed API contracts for JNI and Panama layers
- **Integration Test Suite**: Comprehensive tests validating all functionality
- **Documentation**: Complete API documentation and integration guides

This PRD establishes the foundation for implementing the core native WAMR integration that will enable the entire wamr4j ecosystem. The success of this component is critical for achieving the project's goals of providing safe, performant, and comprehensive WebAssembly runtime bindings for Java.