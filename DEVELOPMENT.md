# WAMR4J Development Guide

This guide provides everything needed to set up a complete WAMR4J development environment and start contributing to the project.

## Quick Start (5 Minutes)

```bash
# 1. Clone the repository
git clone <repository-url>
cd wamr4j

# 2. Verify prerequisites
java -version    # Should be 11+
mvn -version     # Or use ./mvnw

# 3. Build the project
./mvnw clean compile

# 4. Run tests
./mvnw test -q

# 5. Open in your IDE
# IntelliJ IDEA: File > Open > Select wamr4j directory
# Eclipse: Follow eclipse/ECLIPSE_SETUP.md
```

## Prerequisites

### Required
- **Java 11+** for core development
- **Git** for version control
- **Maven 3.8+** (or use included Maven wrapper `./mvnw`)

### Optional (for specific modules)
- **Java 23+** for Panama Foreign Function API development
- **Rust 1.70+** for native library development
- **Docker** for cross-platform testing

### Verification
```bash
# Check Java version
java -version
# Expected: openjdk 11.0.X or later

# Check Maven
mvn -version
# Expected: Apache Maven 3.8.X or later

# Test basic build
./mvnw clean compile
# Expected: BUILD SUCCESS
```

## Project Architecture

WAMR4J follows a multi-runtime architecture:

```
wamr4j/
├── wamr4j/               # 🎯 Public API (users only interact with this)
│   ├── src/main/java/    # Common WebAssembly interfaces
│   └── pom.xml           # Java 11+ compatibility
├── wamr4j-jni/           # 🔧 JNI implementation (internal)
│   ├── src/main/java/    # JNI-specific code
│   └── pom.xml           # Java 11+ compatibility  
├── wamr4j-panama/        # 🔧 Panama implementation (internal)
│   ├── src/main/java/    # Panama FFI code
│   └── pom.xml           # Java 23+ required
├── wamr4j-native/        # ⚙️  Shared native Rust library
│   ├── src/lib.rs        # Rust code for JNI/Panama
│   └── pom.xml           # Cross-compilation setup
├── wamr4j-benchmarks/    # 📊 Performance testing
├── wamr4j-tests/         # 🧪 Integration tests
└── pom.xml               # Root Maven configuration
```

### Key Architectural Principles
- **Unified API**: Users only depend on `wamr4j` module
- **Runtime Selection**: Automatic JNI/Panama selection based on Java version
- **Shared Native**: Single Rust library for both implementations
- **Defensive Programming**: JVM crash prevention is the top priority

## Essential Commands

### Building
```bash
# Clean and compile all modules
./mvnw clean compile

# Compile specific module
./mvnw clean compile -pl wamr4j
./mvnw clean compile -pl wamr4j-jni

# Skip problematic modules during development
./mvnw clean compile -pl '!wamr4j-panama'

# Install to local repository
./mvnw clean install
```

### Testing
```bash
# Run all tests (with quiet output)
./mvnw test -q

# Run tests for specific module
./mvnw test -pl wamr4j -q
./mvnw test -pl wamr4j-jni -q

# Run specific test class
./mvnw test -Dtest=WebAssemblyRuntimeTest -q

# Run tests with coverage report
./mvnw clean test jacoco:report
```

### Code Quality
```bash
# Check code style (Checkstyle + Google Java Style)
./mvnw checkstyle:check

# Auto-format code (Spotless)
./mvnw spotless:apply

# Check for bugs (SpotBugs)
./mvnw spotbugs:check

# Run all static analysis
./mvnw checkstyle:check spotless:check spotbugs:check
```

### Packaging
```bash
# Build and package all artifacts
./mvnw clean package

# Skip tests during packaging (when needed)
./mvnw clean package -DskipTests

# Create distribution with all dependencies
./mvnw clean package -P release
```

## IDE Setup

### IntelliJ IDEA (Recommended)

The project includes comprehensive IntelliJ IDEA configuration:

1. **Open Project**
   ```
   File > Open > Select wamr4j directory
   ```

2. **Trust Project** when prompted

3. **Automatic Configuration**
   - Code style: Google Java Style (pre-configured)
   - Compiler settings: Multi-module aware
   - Run configurations: Build, Test, Static Analysis
   - Inspection profiles: Project-specific quality rules

4. **Available Run Configurations**
   - **Build All**: `./mvnw clean compile`
   - **Test All**: `./mvnw clean test`
   - **Static Analysis**: Code quality checks
   - **Package All**: `./mvnw clean package`

### Eclipse IDE

Follow the detailed setup guide:
```bash
# See complete instructions
cat eclipse/ECLIPSE_SETUP.md
```

**Quick import:**
1. File > Import > Existing Maven Projects
2. Browse to wamr4j directory
3. Select all modules > Finish

## Development Workflow

### 1. Before Starting Work

```bash
# Update your local repository
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name

# Verify everything builds
./mvnw clean compile
```

### 2. During Development

```bash
# Auto-format code before committing
./mvnw spotless:apply

# Run tests frequently
./mvnw test -q

# Check code quality
./mvnw checkstyle:check spotbugs:check
```

### 3. Before Committing

```bash
# Complete quality check
./mvnw clean test checkstyle:check spotbugs:check

# Verify formatting
./mvnw spotless:check

# Create commit with conventional format
git commit -m "feat: add WebAssembly module validation"
```

## Coding Standards

The project strictly follows **Google Java Style Guide** with these key requirements:

### Formatting
- **Indentation**: 2 spaces (no tabs)
- **Line length**: Maximum 120 characters
- **Encoding**: UTF-8
- **Imports**: No wildcards (e.g., `java.util.*`)

### Naming Conventions
- **Classes**: `UpperCamelCase`
- **Methods**: `lowerCamelCase`
- **Variables**: `lowerCamelCase`
- **Constants**: `UPPER_CASE_WITH_UNDERSCORES`
- **Packages**: `lowercase`

### Documentation
- **Javadoc required** for all public classes, methods, and fields
- **Include `@param`, `@return`, `@throws`** for public methods
- **Explain WHY, not just WHAT** in comments

### Code Quality
- **Method length**: Maximum 150 lines
- **Parameters**: Maximum 7 parameters per method
- **Final parameters**: Declare method parameters as `final`
- **Switch statements**: Always include `default` case
- **Error handling**: Comprehensive exception handling

## Module-Specific Development

### Core API Module (`wamr4j`)
- **Purpose**: Public interfaces and factory classes
- **Constraints**: Java 11+ compatibility
- **Focus**: Clean, stable API design

```java
// Example: Public interface design
public interface WebAssemblyModule extends AutoCloseable {
    WebAssemblyInstance instantiate(Map<String, Object> imports);
    void validateImports(Map<String, Map<String, Object>> requiredImports);
    // ... other methods
}
```

### JNI Implementation (`wamr4j-jni`)
- **Purpose**: Java Native Interface implementation
- **Constraints**: Java 11+ compatibility
- **Focus**: Memory management and JVM crash prevention

### Panama Implementation (`wamr4j-panama`)
- **Purpose**: Foreign Function API implementation
- **Constraints**: Java 23+ required
- **Focus**: Type-safe native interop

### Native Library (`wamr4j-native`)
- **Purpose**: Shared Rust library for JNI and Panama
- **Language**: Rust 1.70+
- **Focus**: WAMR integration and safety

## Testing Strategy

### Test Categories
1. **Unit Tests**: Individual component testing
2. **Integration Tests**: Cross-module compatibility
3. **WebAssembly Tests**: Official WASM test suite
4. **Performance Tests**: JMH benchmarks

### Test Requirements
- **All tests** must pass on both JNI and Panama
- **No runtime-specific** test dependencies
- **Comprehensive coverage** of error conditions
- **Performance validation** for critical paths

### Writing Tests
```java
@Test
@DisplayName("Should validate module imports correctly")
void shouldValidateModuleImports() {
    // Arrange
    final WebAssemblyModule module = loadTestModule();
    final Map<String, Map<String, Object>> imports = createTestImports();
    
    // Act & Assert
    assertDoesNotThrow(() -> module.validateImports(imports));
}
```

## Performance Optimization

### Priority Order
1. **Safety First**: Never compromise JVM stability for performance
2. **Minimize Native Calls**: Batch operations when possible
3. **Memory Efficiency**: Optimize GC patterns
4. **Cache Strategically**: Cache frequently used native resources

### Benchmarking
```bash
# Run performance benchmarks
./mvnw test -pl wamr4j-benchmarks

# Profile specific operations
./mvnw test -Dtest=WebAssemblyBenchmark -pl wamr4j-benchmarks
```

## Debugging

### Common Debug Scenarios

**Build failures:**
```bash
# Clean everything and rebuild
./mvnw clean compile

# Check for dependency conflicts
./mvnw dependency:tree
```

**Test failures:**
```bash
# Run specific test with verbose output
./mvnw test -Dtest=YourTestClass

# Debug with JVM options
./mvnw test -Dmaven.surefire.debug
```

**Native library issues:**
```bash
# Check native library loading
java -Djava.library.path=./target/natives -cp target/classes YourTestClass
```

## Documentation

### Javadoc Generation
```bash
# Generate API documentation
./mvnw javadoc:javadoc

# Generate aggregate documentation
./mvnw javadoc:aggregate
```

### Documentation Standards
- **API docs**: Must be comprehensive and accurate
- **Code comments**: Explain complex algorithms and business logic
- **README files**: Keep up-to-date with examples

## Contributing

### Pull Request Checklist
- [ ] Code follows Google Java Style Guide
- [ ] All tests pass (`./mvnw test`)
- [ ] Static analysis passes (`./mvnw checkstyle:check spotbugs:check`)
- [ ] Code is formatted (`./mvnw spotless:check`)
- [ ] Javadoc is complete for public APIs
- [ ] Tests cover new functionality
- [ ] No performance regressions

### Commit Message Format
Follow [Conventional Commits](https://conventionalcommits.org/):

```
feat: add WebAssembly module validation
fix: resolve memory leak in native cleanup
docs: update API documentation for new features
test: add integration tests for Panama runtime
```

## Troubleshooting

For comprehensive troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

### Quick Solutions

**"Cannot resolve dependencies":**
```bash
./mvnw clean install -pl wamr4j
```

**"Code style violations":**
```bash
./mvnw spotless:apply
```

**"Tests fail with native errors":**
```bash
# Check Java version and native library compatibility
java -version
./mvnw clean compile -pl wamr4j-native
```

## Getting Help

- **Issues**: Create GitHub issues for bugs and feature requests
- **Discussions**: Use GitHub Discussions for questions
- **Documentation**: All docs are in the repository
- **Code Examples**: See `wamr4j-tests/` for usage examples

---

**Ready to contribute?** Start with `./mvnw clean compile` and dive in!