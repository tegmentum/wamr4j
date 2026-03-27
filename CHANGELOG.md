# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Version format: `{WAMR version}-{wamr4j version}` (e.g., `2.4.4-1.0.1`).

## [2.4.4-1.0.1] - 2026-03-27

### Fixed

- Fix fuzz target compile errors: add `Sync` impl for `WamrInstance` and use
  `RuntimeHandle` wrapper in `ffi_roundtrip` fuzz target
- Fix fuzz target panics under AddressSanitizer: convert `expect()` calls in
  `func_call` and `memory_access` fixture init to graceful `Option<T>` returns
- Fix dependency update workflow: set `working-directory` for cargo commands,
  correct `Cargo.lock` path, and add cmake dependency
- Fix fuzz testing workflow: build native library in Java fuzz smoke test
- Fix fuzz testing workflow startup failures

## [2.4.4-1.0.0] - 2026-03-15

### Added

- Unified Java API for WebAssembly Micro Runtime (WAMR) with JNI and Panama
  Foreign Function API implementations
- Automatic runtime selection based on Java version (JNI for Java 8-22,
  Panama for Java 23+) with manual override via `-Dwamr4j.runtime=jni|panama`
- 157/160 WAMR C API functions bound in Rust (98.1% coverage), with 5 excluded
  functions documented with justification
- Shared native Rust library (`wamr4j-native`) with both JNI and Panama FFI
  exports, eliminating code duplication
- Cross-platform native library builds for Linux, macOS, and Windows on both
  x86_64 and ARM64 architectures
- Batch API for amortized JNI/FFI crossing overhead
- Typed primitive fast paths for function invocation and global get/set
  operations in both JNI and Panama
- Pre-allocated buffer caching for Panama global operations and JNI memory
  buffer/class/method ID caching
- Shared heap support and module reader bindings
- Comprehensive test suite with 286 passing tests covering both JNI and Panama
  implementations
- Fuzz testing infrastructure with 5 Rust fuzz targets (module_parse,
  func_call, memory_access, error_message, ffi_roundtrip) and Java fuzz tests
- Mutation testing infrastructure
- JMH performance benchmarks for JNI vs Panama comparison
- CI/CD pipelines with multi-platform build, release automation, security
  scanning, fuzz testing, and dependency update workflows
- Quality gate in release workflow with static analysis (Checkstyle, Spotless,
  SpotBugs)

### Performance

- Typed fast-path FFI dispatch for Panama function calls
- Typed primitive fast paths for JNI function invocation
- Cached pre-allocated buffers for Panama global get/set operations
- Cached JNI memory buffer and class/method IDs to eliminate native crossings
- Removed per-call FFI validation from Panama typed memory operations

[2.4.4-1.0.1]: https://github.com/tegmentum/wamr4j/compare/v2.4.4-1.0.0...v2.4.4-1.0.1
[2.4.4-1.0.0]: https://github.com/tegmentum/wamr4j/releases/tag/v2.4.4-1.0.0
