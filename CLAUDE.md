# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Think carefully and implement the most concise solution that changes as little code as possible.

## USE SUB-AGENTS FOR CONTEXT OPTIMIZATION

### 1. Always use the file-analyzer sub-agent when asked to read files.
The file-analyzer agent is an expert in extracting and summarizing critical information from files, particularly log files and verbose outputs. It provides concise, actionable summaries that preserve essential information while dramatically reducing context usage.

### 2. Always use the code-analyzer sub-agent when asked to search code, analyze code, research bugs, or trace logic flow.

The code-analyzer agent is an expert in code analysis, logic tracing, and vulnerability detection. It provides concise, actionable summaries that preserve essential information while dramatically reducing context usage.

### 3. Always use the test-runner sub-agent to run tests and analyze the test results.

Using the test-runner agent ensures:

- Full test output is captured for debugging
- Main conversation stays clean and focused
- Context usage is optimized
- All issues are properly surfaced
- No approval dialogs interrupt the workflow

## Project Overview

This project provides unified Java bindings for the WAMR (WebAssembly Micro Runtime). The core mission is to build a common interface for interacting with WAMR using both JNI and Panama bindings. The Panama bindings should target the latest stable Panama release Java 23 and JNI for older pre-23 Java releases.

The WAMR GitHub repository is located at https://github.com/bytecodealliance/wamr and should target the latest WAMR release (2.4.1). JNI bindings should use the Rust API.

The project uses Maven as the build tool with the Maven wrapper and provides both JNI and Panama Foreign Function API implementations for native runtimes, with a unified API layer that abstracts engine-specific details.

**Project Type**: Greenfield project starting from scratch

**Base Java Package**: ai.tegmentum.wamr4j

## Architecture

The project follows a multi-runtime architecture with the following structure:

**Unified API Layer:**
- `wamr4j` - Common WebAssembly interface definitions located in java package ai.tegmentum.wamr4j
- `wamr4j-jni` - JNI interface in java package ai.tegmentum.wamr4j.jni
- `wamr4j-panama` - Panama interface in java package ai.tegmentum.wamr4j.panama

**Module Structure:**
```
wamr4j/
├── wamr4j/               # Public API interfaces and factory (users only interact with this)
├── wamr4j-benchmarks     # Benchmarks for jni and panama implementations with comparisons
├── wamr4j-native/        # Shared native Rust library for JNI and Panama implementations
├── wamr4j-jni/           # JNI implementation using shared native library (private/internal)
├── wamr4j-panama/        # Panama FFI implementation using shared native library (private/internal)
└── wamr4j-tests/         # Integration tests and WebAssembly test suites
```

**Runtime Selection:**
- Automatic detection based on Java version (JNI for Java 8-22, Panama for Java 23+)
- Manual override via system properties (e.g., `-Dwamr4j.runtime=jni`)
- Graceful fallback if Panama unavailable on Java 23+ (uses JNI with warning)
- Factory-based loading with no direct dependencies between public API and implementations

## Essential Build Commands

**Test Framework:** JUnit 5 (Jupiter) with Maven Surefire Plugin

```bash
# Clean and compile
./mvnw clean compile

# Run tests (with quiet mode for cleaner output)
./mvnw test -q

# Build and package
./mvnw clean package

# Install to local repository
./mvnw clean install

# Compile and run tests with coverage
./mvnw clean test jacoco:report

# Skip tests during build (when needed)
./mvnw clean package -DskipTests

# Run specific test class
./mvnw test -Dtest=YourTestClass -q

# Run tests for specific module
./mvnw test -pl wamr4j-native -q
./mvnw test -pl wamr4j-jni -q
./mvnw test -pl wamr4j-panama -q

# Run integration tests only
./mvnw test -Dtest="*IT" -q

# Run with specific Java version
JAVA_HOME=/path/to/java ./mvnw test -q
```

## Static Analysis Commands

The project includes comprehensive static analysis tools:

```bash
# Run Checkstyle for coding standards
./mvnw checkstyle:check

# Run Spotless for code formatting
./mvnw spotless:check
./mvnw spotless:apply  # Auto-format code

# Run SpotBugs for bug detection
./mvnw spotbugs:check

# Run all static analysis tools
./mvnw checkstyle:check spotless:check spotbugs:check
```

## Development Priorities

**CRITICAL: Priority Order for All Development Decisions**

1. **Defensive Programming (HIGHEST PRIORITY)**: Prevent JVM crashes at all costs
   - Validate all native calls and parameters before execution
   - Handle all native errors gracefully without propagating to JVM crash
   - Implement comprehensive null checks and boundary validation
   - Use defensive copying for mutable parameters
   - Never assume native code will behave correctly - always validate responses
   - Implement failsafe mechanisms and graceful degradation

2. **Performance (SECOND PRIORITY)**: Optimize for speed while maintaining safety
   - Minimize JNI/Panama call overhead through batching
   - Optimize memory allocation and garbage collection patterns
   - Cache frequently used native resources appropriately
   - Use efficient data structures and algorithms
   - Profile and benchmark critical paths

## API Implementation Priority

**IMPORTANT: Native runtime APIs are the source of truth for their respective implementations.**

When implementing features or fixing issues:
1. **Native API Fidelity**: Correctly implement native runtime APIs while adding defensive checks
2. **Tests Adapt to API**: If tests fail due to API differences, modify tests to match correct native API behavior
3. **Never Compromise Safety**: Do not remove defensive checks to make tests pass or improve performance
4. **Runtime Compatibility**: Ensure all implementations match their respective official APIs safely
5. **Unified Interface**: Provide consistent abstractions while preserving runtime-specific capabilities safely

## Coding Style

The project strictly follows the Google Java Style Guide (https://google.github.io/styleguide/javaguide.html). Key requirements:

### General Guidelines
- **Character encoding**: UTF-8
- **Line length**: Maximum 120 characters (ignoring package/import statements and URLs)
- **File length**: Maximum 2000 lines
- **Indentation**: Spaces only, no tabs

### Naming Conventions
- **Classes**: UpperCamelCase
- **Methods**: lowerCamelCase
- **Variables**: lowerCamelCase
- **Constants**: UPPER_CASE_WITH_UNDERSCORES
- **Packages**: lowercase

### Code Organization
- **Method length**: Maximum 150 lines
- **Parameters**: Maximum 7 parameters per method
- **Imports**: No wildcard imports (e.g., `import java.util.*`)
- **Modifier order**: Follow standard Java conventions

### Best Practices
- **Final parameters**: Method parameters should be declared final
- **Boolean simplification**: Avoid unnecessary boolean complexity
- **Switch statements**: Always include a default case
- **Equals and HashCode**: Override both or neither
- **Visibility**: Keep fields private/protected, provide getters/setters as needed

### Documentation
- **Javadoc**: Required for all public classes, methods, and fields
- **TODO comments**: Use TODO format for tracking future work

## Key Implementation Details

- **Native Library Management**: Cross-compiles WAMR libraries from source during Maven build for all supported architectures, packages them into JARs
- **Shared Native Library**: `wamr4j-native` provides a single, consolidated Rust library with both JNI and Panama FFI exports
- **Cross-Compilation**: Maven build process compiles WAMR for multiple target architectures (Linux/Windows/macOS x86_64 and ARM64)
- **API Architecture**: Public `wamr4j` module contains only interfaces and factories, private implementation modules handle JNI/Panama specifics using shared native library
- **Code Consolidation**: All native Rust code consolidated in `wamr4j-native` to eliminate duplication and ensure consistency
- **Error Handling**: Comprehensive native error mapping internally, broader category exceptions (CompilationException, RuntimeException, ValidationException) in public API

## Testing Strategy

**Test Categories:**
- **Unit Tests**: Individual component testing for API methods
- **Integration Tests**: Cross-module compatibility and runtime switching
- **WebAssembly Tests**: Combination of official WASM test suite, WAMR tests, and custom Java-specific tests
- **Native Library Tests**: Platform-specific native code validation
- **Performance Tests**: JMH benchmarks for detailed performance analysis

**Test Requirements:**
- All tests must pass on both JNI and Panama implementations
- Tests should not depend on specific runtime behavior differences
- Native library loading tests must work across all supported platforms
- WebAssembly test files should match official WAMR test specifications
- **Native Method Implementation**: All native methods should be fully implemented, not stubbed

## Logging Strategy

**Logging Framework:** Native `java.util.logging` (JUL) to minimize external dependencies

**Logging Guidelines:**
- Use `java.util.logging.Logger` for all logging needs
- Configure logging levels appropriately (SEVERE, WARNING, INFO, FINE, etc.)
- Avoid external logging frameworks (Log4j, SLF4J, Logback) to keep dependencies minimal
- Log important events: native library loading, WebAssembly module operations, errors, and performance metrics

## Git and Development Workflow

- Working trees should be created in the root working directory
- Do not initialize a new git repo in the root working directory
- Share as much code between wamr4j-panama and wamr4j-jni
- Use conventional commits format when writing commit messages https://conventionalcommits.org/en/v1.0.0
- You should not refer to Claude or Anthropic in commit messages or use emojis
- Plan for 100% API coverage from the beginning. This will not be implemented incrementally

## DateTime Handling

When any command requires the current date/time (for frontmatter, timestamps, or logs), you MUST obtain the REAL current date/time from the system rather than estimating or using placeholder values.

### Getting Current DateTime
```bash
# Get current datetime in ISO 8601 format (works on Linux/Mac)
date -u +"%Y-%m-%dT%H:%M:%SZ"
```

### Required Format
All dates in frontmatter MUST use ISO 8601 format with UTC timezone:
- Format: `YYYY-MM-DDTHH:MM:SSZ`
- Example: `2025-08-31T16:08:04Z`

## Problem Solving Approach

- Fix systemic problems systematically one file at a time. Do not build scripts to fix all problems at once. This is dangerous and has the potential to cause numerous compilation build errors that are difficult to fix.

## Philosophy

### Error Handling
- **Fail fast** for critical configuration (missing text model)
- **Log and continue** for optional features (extraction model)
- **Graceful degradation** when external services unavailable
- **User-friendly messages** through resilience layer

### Testing
- Always use the test-runner agent to execute tests.
- Do not use mock services for anything ever.
- Do not move on to the next test until the current test is complete.
- If the test fails, consider checking if the test is structured correctly before deciding we need to refactor the codebase.
- Tests to be verbose so we can use them for debugging.

## Tone and Behavior

- Criticism is welcome. Please tell me when I am wrong or mistaken, or even when you think I might be wrong or mistaken.
- Please tell me if there is a better approach than the one I am taking.
- Please tell me if there is a relevant standard or convention that I appear to be unaware of.
- Be skeptical.
- Be concise.
- Short summaries are OK, but don't give an extended breakdown unless we are working through the details of a plan.
- Do not flatter, and do not give compliments unless I am specifically asking for your judgement.
- Occasional pleasantries are fine.
- Feel free to ask many questions. If you are in doubt of my intent, don't guess. Ask.

## ABSOLUTE RULES:

- NO PARTIAL IMPLEMENTATION
- NO SIMPLIFICATION : no "//This is simplified stuff for now, complete implementation would blablabla"
- NO CODE DUPLICATION : check existing codebase to reuse functions and constants Read files before writing new functions. Use common sense function name to find them easily.
- NO DEAD CODE : either use or delete from codebase completely
- IMPLEMENT TEST FOR EVERY FUNCTIONS
- NO CHEATER TESTS : test must be accurate, reflect real usage and be designed to reveal flaws. No useless tests! Design tests to be verbose so we can use them for debugging.
- NO INCONSISTENT NAMING - read existing codebase naming patterns.
- NO OVER-ENGINEERING - Don't add unnecessary abstractions, factory patterns, or middleware when simple functions would work. Don't think "enterprise" when you need "working"
- NO MIXED CONCERNS - Don't put validation logic inside API handlers, database queries inside UI components, etc. instead of proper separation
- NO RESOURCE LEAKS - Don't forget to close database connections, clear timeouts, remove event listeners, or clean up file handles

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.