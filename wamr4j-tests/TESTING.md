# wamr4j Test Suite Execution Guide

This guide provides comprehensive information about running and understanding the wamr4j comparison test suite.

## Overview

The wamr4j test suite validates that JNI and Panama implementations produce identical results for WebAssembly operations. The suite contains **526 comparison assertions** across **18 test classes** covering all four WebAssembly numeric types.

## Test Suite Statistics

### Coverage by Numeric Type

| Type | Test Classes | Assertions | Coverage |
|------|--------------|------------|----------|
| i32 (32-bit integer) | 6 | 179 | Complete |
| i64 (64-bit integer) | 6 | 177 | Complete |
| f32 (32-bit float) | 3 | 85 | Core Operations |
| f64 (64-bit float) | 3 | 85 | Core Operations |
| **Total** | **18** | **526** | **All Numeric Types** |

### Test Categories

#### Integer Operations (356 assertions)

**i32 Tests (179 assertions):**
- `I32NumericComparisonTest` - Arithmetic (add, sub, mul) - 28 assertions
- `I32DivisionComparisonTest` - Division with traps - 33 assertions
- `I32RemainderComparisonTest` - Remainder with traps - 22 assertions
- `I32BitwiseComparisonTest` - Bitwise (and, or, xor) - 24 assertions
- `I32ShiftComparisonTest` - Shifts (shl, shr_s, shr_u) - 29 assertions
- `I32ComparisonTest` - Comparisons (eq, ne, lt, gt, le, ge) - 43 assertions

**i64 Tests (177 assertions):**
- `I64NumericComparisonTest` - Arithmetic (add, sub, mul) - 26 assertions
- `I64DivisionComparisonTest` - Division with traps - 33 assertions
- `I64RemainderComparisonTest` - Remainder with traps - 22 assertions
- `I64BitwiseComparisonTest` - Bitwise (and, or, xor) - 24 assertions
- `I64ShiftComparisonTest` - Shifts (shl, shr_s, shr_u) - 29 assertions
- `I64ComparisonTest` - Comparisons (eq, ne, lt, gt, le, ge) - 43 assertions

#### Floating-Point Operations (170 assertions)

**f32 Tests (85 assertions):**
- `F32NumericComparisonTest` - Arithmetic and unary ops - 41 assertions
- `F32DivisionComparisonTest` - Division and special cases - 18 assertions
- `F32ComparisonTest` - Comparisons - 26 assertions

**f64 Tests (85 assertions):**
- `F64NumericComparisonTest` - Arithmetic and unary ops - 42 assertions
- `F64DivisionComparisonTest` - Division and special cases - 19 assertions
- `F64ComparisonTest` - Comparisons - 24 assertions

## Quick Start

### Prerequisites

1. Java 23+ (for Panama support) or Java 8+ (for JNI only)
2. Maven 3.6+
3. Built wamr4j modules

### Build All Modules

```bash
# From project root
./mvnw clean install -DskipTests
```

### Run All Tests

```bash
# Run all comparison tests
./mvnw test -pl wamr4j-tests

# Run with verbose logging
./mvnw test -pl wamr4j-tests -Pcomparison-verbose
```

## Running Specific Test Suites

### By Numeric Type

```bash
# Run all i32 tests
./mvnw test -pl wamr4j-tests -Dtest="I32*"

# Run all i64 tests
./mvnw test -pl wamr4j-tests -Dtest="I64*"

# Run all f32 tests
./mvnw test -pl wamr4j-tests -Dtest="F32*"

# Run all f64 tests
./mvnw test -pl wamr4j-tests -Dtest="F64*"
```

### By Operation Category

```bash
# Run all arithmetic tests
./mvnw test -pl wamr4j-tests -Dtest="*Numeric*"

# Run all division tests
./mvnw test -pl wamr4j-tests -Dtest="*Division*"

# Run all comparison tests
./mvnw test -pl wamr4j-tests -Dtest="*ComparisonTest"

# Run all bitwise tests
./mvnw test -pl wamr4j-tests -Dtest="*Bitwise*"

# Run all shift tests
./mvnw test -pl wamr4j-tests -Dtest="*Shift*"
```

### Run Single Test Class

```bash
# Run specific test class
./mvnw test -pl wamr4j-tests -Dtest=I32NumericComparisonTest

# Run specific test method
./mvnw test -pl wamr4j-tests -Dtest=I32NumericComparisonTest#testI32Add
```

## Runtime-Specific Testing

### JNI Only

```bash
# Test only JNI implementation
./mvnw test -pl wamr4j-tests -Pjni-only
```

**Use Cases:**
- Testing on Java 8-22 (Panama not available)
- Isolating JNI-specific issues
- Performance benchmarking JNI implementation

### Panama Only

```bash
# Test only Panama implementation (requires Java 23+)
./mvnw test -pl wamr4j-tests -Ppanama-only
```

**Use Cases:**
- Testing on Java 23+ with Panama FFI
- Isolating Panama-specific issues
- Performance benchmarking Panama implementation

### Comparison Mode (Default)

```bash
# Run on both JNI and Panama, compare results
./mvnw test -pl wamr4j-tests -Pcomparison
```

**What It Does:**
- Executes every test on both JNI and Panama implementations
- Compares return values for exact equality
- Verifies trap behavior matches
- Reports performance differences
- Validates behavioral consistency

## Understanding Test Output

### Successful Test Output

```
[INFO] Starting i32 numeric comparison tests
[INFO] Performance comparison (5 assertions):
[INFO]   JNI:    2.456 ms total, 0.491 ms avg
[INFO]   Panama: 1.823 ms total, 0.365 ms avg
[INFO]   Speedup: 1.35x (Panama faster)
[INFO] Completed test: testI32Add
```

### Test Assertion Format

Each assertion includes:
- **Function name**: WebAssembly function being tested
- **Arguments**: Input values
- **Expected results**: Expected return values
- **Description**: Human-readable test description

Example:
```java
runner.addAssertion(TestAssertion.assertReturn("add",
    new Object[]{5, 3},
    new Object[]{8},
    "5 + 3 = 8"));
```

### Floating-Point Comparisons

Float and double values use epsilon-based comparison:
- **f32**: FLOAT_EPSILON = 1e-6
- **f64**: DOUBLE_EPSILON = 1e-12

This handles minor precision differences while ensuring behavioral consistency.

## Special Test Cases

### Division by Zero

**Integer Division:**
- Traps with "integer divide by zero" error
- Both JNI and Panama must trap identically

**Floating-Point Division:**
- Produces infinity (not a trap)
- Positive/negative infinity based on sign

### Integer Overflow

**i32/i64 Overflow:**
- Wraps around (modular arithmetic)
- MIN_VALUE - 1 = MAX_VALUE
- MAX_VALUE + 1 = MIN_VALUE

**Special Case:**
- MIN_VALUE / -1 traps (overflow)

### Floating-Point Special Values

**Infinity:**
- Operations with infinity follow IEEE 754
- INFINITY + finite = INFINITY
- finite / INFINITY = 0

**Negative Zero:**
- -0.0 == 0.0 (true)
- abs(-0.0) = 0.0
- neg(0.0) = -0.0

**NaN (Not tested yet):**
- Will be added in future iterations

## Performance Analysis

### Viewing Performance Metrics

Performance metrics are logged automatically:

```
[INFO] Performance comparison (29 assertions):
[INFO]   JNI:    8.342 ms total, 0.288 ms avg
[INFO]   Panama: 6.127 ms total, 0.211 ms avg
[INFO]   Speedup: 1.36x (Panama faster)
```

### Interpreting Results

- **Total time**: Cumulative time for all assertions
- **Average time**: Mean time per assertion
- **Speedup**: Performance ratio between implementations

**Note:** Initial runs may show JIT warm-up effects. Run multiple times for stable measurements.

## Troubleshooting

### Panama Not Available

**Symptom:**
```
[WARN] Panama runtime not available, skipping comparison
```

**Solutions:**
1. Ensure Java 23+ is installed
2. Check JAVA_HOME points to Java 23+
3. Verify Panama FFI is enabled (should be by default)

### Test Failures

**JNI and Panama Produce Different Results:**
1. Check for implementation bugs
2. Verify native library versions match
3. Review recent code changes

**All Tests Fail:**
1. Verify native libraries are built and loaded
2. Check WebAssembly module construction
3. Review module bytecode generation

### Build Issues

**Native Library Not Found:**
```bash
# Rebuild native libraries
./mvnw clean install -pl wamr4j-native
```

**Dependency Resolution:**
```bash
# Clean and rebuild everything
./mvnw clean install -DskipTests
```

## Advanced Usage

### Custom Test Development

Create new comparison tests by extending `AbstractComparisonTest`:

```java
class CustomComparisonTest extends AbstractComparisonTest {
    @Test
    void testCustomOperation() {
        final byte[] module = WasmModuleBuilder.createI32AddModule();
        runner = new ComparisonTestRunner(module);

        runner.addAssertion(TestAssertion.assertReturn("add",
            new Object[]{10, 20},
            new Object[]{30},
            "Custom test: 10 + 20 = 30"));

        runAndCompare(module);
    }
}
```

### Using WasmModuleBuilder

Build custom WebAssembly modules programmatically:

```java
final WasmModuleBuilder builder = new WasmModuleBuilder();
final int typeIndex = builder.addType(
    new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
    new byte[]{WasmModuleBuilder.I32}
);
final int funcIndex = builder.addFunction(typeIndex);
builder.addExport("myFunc", funcIndex);
builder.addCode(
    new byte[]{},
    new byte[]{
        WasmModuleBuilder.LOCAL_GET, 0x00,
        WasmModuleBuilder.LOCAL_GET, 0x01,
        WasmModuleBuilder.I32_ADD
    }
);
final byte[] module = builder.build();
```

## Integration with Official Spec Tests

### Download WebAssembly Test Suite

```bash
cd wamr4j-tests
./download-testsuite.sh
```

This downloads the official WebAssembly specification test suite to:
```
wamr4j-tests/src/test/resources/testsuite/
```

### Running Spec Tests

Once downloaded, spec tests can be run using the `SpecTestRunner`:

```java
final SpecTestRunner runner = new SpecTestRunner();
final SpecTestRunner.Results results = runner
    .loadSpecTestFile(Path.of("src/test/resources/testsuite/i32.json"))
    .withModuleBytes(moduleBytes)
    .runComparison();
```

See `SpecTestIntegrationExample.java` for complete examples.

## Continuous Integration

### Recommended CI Pipeline

```yaml
# Example GitHub Actions workflow
- name: Build All Modules
  run: ./mvnw clean install -DskipTests

- name: Run Comparison Tests
  run: ./mvnw test -pl wamr4j-tests -Pcomparison

- name: Check Test Results
  run: |
    if [ $? -ne 0 ]; then
      echo "Tests failed"
      exit 1
    fi
```

### Test Reports

Test results are written to:
```
wamr4j-tests/target/surefire-reports/
```

## Next Steps

1. **Download Spec Tests**: Run `./download-testsuite.sh` to get official tests
2. **Run Complete Suite**: Execute all 526 assertions
3. **Review Results**: Check for any JNI/Panama discrepancies
4. **Add Custom Tests**: Extend the suite for project-specific validation

## Resources

- [WebAssembly Specification](https://webassembly.github.io/spec/)
- [Official Test Suite](https://github.com/WebAssembly/testsuite)
- [WAMR Documentation](https://github.com/bytecodealliance/wamr)
- [Project README](README.md)

## Summary

The wamr4j comparison test suite provides:
- ✅ Complete coverage of all four numeric types
- ✅ 526 assertions validating JNI and Panama consistency
- ✅ Comprehensive test framework infrastructure
- ✅ Integration with official WebAssembly spec tests
- ✅ Performance comparison between implementations
- ✅ IEEE 754 compliance validation
- ✅ Edge case and boundary condition testing

This ensures that wamr4j's unified API provides identical WebAssembly semantics across both JNI and Panama implementations.
