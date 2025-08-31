---
created: 2025-08-31T16:14:15Z
last_updated: 2025-08-31T16:14:15Z
version: 1.0
author: Claude Code PM System
---

# Project Structure

## Overview

The wamr4j project follows a **multi-runtime architecture** with a unified API layer that abstracts engine-specific implementation details. The structure supports both JNI (Java 8-22) and Panama FFI (Java 23+) implementations.

## Base Java Package
All code is organized under the base package: `ai.tegmentum.wamr4j`

## Planned Module Structure

```
wamr4j/                           # Root project directory
├── wamr4j/                       # Public API interfaces and factory
│   └── src/main/java/ai/tegmentum/wamr4j/
│       ├── Engine.java           # Core WebAssembly engine interface
│       ├── Module.java           # Compiled WebAssembly module interface
│       ├── Instance.java         # Module instance interface
│       ├── RuntimeFactory.java   # Factory for runtime selection
│       └── exceptions/           # Public API exceptions
│
├── wamr4j-jni/                   # JNI implementation (private/internal)
│   └── src/main/java/ai/tegmentum/wamr4j/jni/
│       ├── JniEngine.java        # JNI-specific engine implementation
│       ├── JniModule.java        # JNI module wrapper
│       └── JniInstance.java      # JNI instance wrapper
│
├── wamr4j-panama/                # Panama FFI implementation (private/internal)
│   └── src/main/java/ai/tegmentum/wamr4j/panama/
│       ├── PanamaEngine.java     # Panama-specific engine implementation
│       ├── PanamaModule.java     # Panama module wrapper
│       └── PanamaInstance.java   # Panama instance wrapper
│
├── wamr4j-native/                # Shared native Rust library
│   ├── Cargo.toml               # Rust project configuration
│   ├── src/
│   │   ├── lib.rs               # Main library entry point
│   │   ├── jni.rs               # JNI bindings
│   │   ├── panama.rs            # Panama FFI exports
│   │   └── wamr_wrapper.rs      # WAMR API wrapper
│   └── build.rs                 # Build script for WAMR integration
│
├── wamr4j-benchmarks/            # Performance benchmarks
│   └── src/test/java/ai/tegmentum/wamr4j/benchmarks/
│       ├── EngineBenchmark.java  # Engine performance tests
│       └── RuntimeComparison.java # JNI vs Panama comparison
│
├── wamr4j-tests/                 # Integration tests and WebAssembly test suites
│   └── src/test/java/ai/tegmentum/wamr4j/tests/
│       ├── WebAssemblyTestSuite.java    # Official WASM tests
│       ├── RuntimeSwitchingTest.java    # Runtime selection tests
│       └── resources/
│           └── wasm/             # WebAssembly test files
│
└── pom.xml                       # Root Maven configuration
```

## Current Repository Structure

Currently the repository contains Claude Code PM framework files:

```
.
├── .claude/                      # Project management framework
│   ├── agents/                   # AI agents for specialized tasks
│   ├── commands/                 # Command definitions
│   ├── context/                  # Project context files (this file)
│   ├── epics/                    # Epic management workspace
│   ├── prds/                     # Product Requirements Documents
│   ├── rules/                    # Development rules and standards
│   └── scripts/                  # Shell scripts
├── .git/                         # Git repository
├── .gitignore                    # Git ignore patterns
├── AGENTS.md                     # AI agent documentation
├── CLAUDE.md                     # Claude Code development guidance
├── COMMANDS.md                   # Command reference
├── LICENSE                       # MIT license
├── README.md                     # Project description (needs alignment)
├── install/                      # Installation scripts
└── screenshot.webp               # Documentation image
```

## Key Architectural Principles

### 1. Unified API Layer
- Users only interact with the `wamr4j` module
- Public API provides interfaces only, no implementation details
- Factory pattern for runtime selection and instantiation

### 2. Implementation Isolation
- `wamr4j-jni` and `wamr4j-panama` are private/internal modules
- No direct dependencies between public API and implementations
- Implementations loaded via factory based on runtime detection

### 3. Shared Native Library
- Single `wamr4j-native` Rust library with both JNI and Panama exports
- Eliminates code duplication between JNI and Panama implementations
- Consistent WAMR API wrapper for both binding types

### 4. Cross-Platform Support
- Native library built for Linux/Windows/macOS (x86_64 and ARM64)
- Platform-specific binaries packaged into JARs
- Runtime loading with appropriate platform detection

## File Organization Patterns

### Java Code Structure
- **Package naming**: All lowercase, following `ai.tegmentum.wamr4j` pattern
- **Class naming**: UpperCamelCase
- **Interface naming**: Same as implementation without prefix/suffix
- **Exception naming**: Descriptive with `Exception` suffix

### Rust Code Structure  
- **Module naming**: snake_case
- **Function naming**: snake_case
- **Constants**: UPPER_CASE
- **JNI exports**: Java naming conventions for compatibility

### Test Organization
- **Unit tests**: Alongside source in `src/test/java`
- **Integration tests**: Separate `wamr4j-tests` module
- **Benchmarks**: Separate `wamr4j-benchmarks` module
- **Resources**: Test WebAssembly files in `src/test/resources/wasm/`

## Build Configuration

### Maven Structure
- **Multi-module project**: Parent POM coordinates all modules
- **Dependency management**: Centralized version management
- **Plugin configuration**: Consistent across all modules

### Static Analysis Integration
- **Checkstyle**: Google Java Style Guide enforcement
- **Spotless**: Automatic code formatting
- **SpotBugs**: Bug pattern detection
- **JaCoCo**: Test coverage reporting

## Development Workflow
- **Git branching**: Feature branches from main
- **Working trees**: Created in root directory for parallel development
- **Commit format**: Conventional commits standard
- **Testing**: JUnit 5 with comprehensive test coverage

## Notes
- Structure designed for **100% API coverage** from the beginning
- **Defensive programming** prioritized for JVM crash prevention
- **No incremental implementation** - complete features only
- **Cross-compilation** handled during Maven build process