# wamr4j-tests

Comprehensive test suite for wamr4j WebAssembly runtime implementations.

## Quick Links

- **[Test Execution Guide](TESTING.md)** - Comprehensive guide to running tests
- **[Coverage Summary](TEST_COVERAGE_SUMMARY.md)** - Detailed statistics and metrics
- **[Comparison Tests](#comparison-tests)** - Overview of test architecture
- **[Running Tests](#running-tests)** - Quick start commands

## Overview

This module provides a comprehensive test framework validating the wamr4j API through multiple test categories:
- **Comparison tests** validate behavioral consistency between JNI and Panama implementations
- **Integration tests** verify complex real-world WebAssembly programs
- **WAMR engine tests** validate correct execution through Java bindings

**Current Status:** 167 tests across 23 test classes - all passing ✅

**Test Breakdown:**
- 134 comparison tests (JNI/Panama parity validation)
- 4 integration tests (complex MVP scenarios)
- 29 WAMR engine tests (bindings validation)

See [`TEST_COVERAGE_REPORT.md`](TEST_COVERAGE_REPORT.md) for detailed coverage analysis.

## Architecture

### Test Framework Components

#### Core Framework (`src/main/java/ai/tegmentum/wamr4j/test/framework/`)

- **`TestAssertion`**: Represents different types of WebAssembly test assertions
  - `ASSERT_RETURN` - Verify function returns expected value
  - `ASSERT_TRAP` - Verify function traps with specific error
  - `ASSERT_INVALID` - Verify module fails validation
  - `ASSERT_EXHAUSTION` - Verify stack exhaustion occurs
  - `ASSERT_UNLINKABLE` - Verify module fails to link

- **`TestResult`**: Captures execution results including return values, errors, and timing metrics

- **`ComparisonTestRunner`**: Executes tests across multiple runtime implementations
  - Runs same assertions on both JNI and Panama
  - Captures timing metrics for performance comparison
  - Provides detailed comparison reporting

- **`WasmModuleBuilder`**: Programmatic WebAssembly module creation
  - Build simple test modules without external toolchains
  - Support for i32 operations (add, sub, mul, div_s, div_u)
  - Pre-built module factories for common operations
  - Useful for hand-crafted test cases

- **`SpecTestAdapter`**: Converts spec tests to executable assertions
  - Bridges WebAssemblyTestCase and TestAssertion
  - Extracts function names, arguments, and expected results
  - Handles all assertion types (return, trap, invalid, etc.)
  - Groups test cases by module for efficient execution

- **`SpecTestRunner`**: Orchestrates spec test execution
  - Loads and parses spec test JSON files
  - Converts test cases to assertions
  - Runs tests on JNI, Panama, or both
  - Collects and analyzes results
  - Fluent API for test configuration

#### WAST Test Parser (`src/main/java/ai/tegmentum/wamr4j/test/wast/`)

- **`WebAssemblySpecTestParser`**: Parser for official WebAssembly spec test files
  - Parses JSON-formatted spec test files
  - Extracts individual test cases with metadata
  - Supports all standard assertion types

- **`WebAssemblyTestCase`**: Comprehensive test case representation
  - Test ID, name, category, and description
  - Expected results (PASS, FAIL, TRAP, etc.)
  - Tags and metadata for filtering
  - Complexity tracking for scheduling

- **`TestCategory`**: Categorization for test organization
  - Spec test categories (core, proposals)
  - WAMR-specific tests (regression, performance, AOT, interpreter)
  - Java-specific tests (JNI, Panama, interop)
  - Custom and integration tests

- **`TestExpectedResult`**: Expected execution outcomes
  - PASS, FAIL, TRAP, TIMEOUT, SKIP, UNKNOWN

- **`TestComplexity`**: Complexity levels for resource allocation
  - SIMPLE, MODERATE, COMPLEX
  - Default timeouts and resource multipliers

- **`TestSuiteException`**: Exception for test suite errors

#### Test Base Classes (`src/test/java/ai/tegmentum/wamr4j/test/comparison/`)

- **`AbstractComparisonTest`**: Base class for comparison tests
  - Automatic result comparison between JNI and Panama
  - Floating-point comparison with appropriate epsilon values
  - Performance metrics logging

### Test Categories

#### Comparison Tests (134 tests)
Tests that run on both JNI and Panama implementations to verify identical behavior:

**Integer Operations (i32 & i64) - 90 tests**
- `I32NumericComparisonTest` - Addition, subtraction, multiplication
- `I32DivisionComparisonTest` - Signed/unsigned division with edge cases
- `I32RemainderComparisonTest` - Signed/unsigned remainder operations
- `I32BitwiseComparisonTest` - AND, OR, XOR operations
- `I32ShiftComparisonTest` - Left shift, right shift (signed/unsigned)
- `I32ComparisonTest` - All comparison operators
- `I64NumericComparisonTest` - 64-bit arithmetic operations
- `I64DivisionComparisonTest` - 64-bit division operations
- `I64RemainderComparisonTest` - 64-bit remainder operations
- `I64BitwiseComparisonTest` - 64-bit bitwise operations
- `I64ShiftComparisonTest` - 64-bit shift operations
- `I64ComparisonTest` - 64-bit comparison operators

**Floating-Point Operations (f32 & f64) - 44 tests**
- `F32NumericComparisonTest` - 32-bit float arithmetic
- `F32DivisionComparisonTest` - 32-bit float division
- `F32ComparisonTest` - 32-bit float comparisons
- `F64NumericComparisonTest` - 64-bit float arithmetic
- `F64DivisionComparisonTest` - 64-bit float division
- `F64ComparisonTest` - 64-bit float comparisons

#### Integration Tests (4 tests)
Complex real-world WebAssembly programs combining multiple features:
- `MVPIntegrationTest.testFibonacciWithMemoization` - Memory + globals + recursion
- `MVPIntegrationTest.testBufferWithGlobals` - Circular buffer implementation
- `MVPIntegrationTest.testCalculatorWithDispatchTable` - Tables + indirect calls
- `MVPIntegrationTest.testArraySum` - Memory + loops + locals

#### WAMR Engine Tests (29 tests)
Validate WAMR engine execution through Java bindings:

**Control Flow (10 tests) - `ControlFlowSpecTest`**
- Blocks, loops, conditionals (if/else)
- Branches (br, br_if, br_table)
- Function calls (direct and indirect)
- Return statements, unreachable instruction

**Memory Operations (7 tests) - `MemorySpecTest`**
- Load/store operations
- Memory addressing and alignment
- Memory grow/size operations
- Data segments, bounds checking

**Type Conversions (7 tests) - `ConversionsSpecTest`**
- Integer wrap/extend operations
- Float to int truncation
- Int to float conversion
- Float promotion/demotion
- Reinterpret operations

**Table Operations (5 tests) - `TableSpecTest`**
- Indirect calls via function tables
- Multiple function signatures
- Out-of-bounds handling
- Recursive calls through tables

## Running Tests

### Prerequisites

```bash
# Build all wamr4j modules first
./mvnw clean install -DskipTests
```

### Test Execution Commands

```bash
# Run all comparison tests (default)
./mvnw test -pl wamr4j-tests

# Run comparison tests with specific profile
./mvnw test -pl wamr4j-tests -Pcomparison

# Run with verbose logging for debugging
./mvnw test -pl wamr4j-tests -Pcomparison-verbose

# Run JNI-only tests
./mvnw test -pl wamr4j-tests -Pjni-only

# Run Panama-only tests (requires Java 23+)
./mvnw test -pl wamr4j-tests -Ppanama-only

# Run spec tests (when available)
./mvnw test -pl wamr4j-tests -Pspec-tests
```

### Test Output

Comparison tests produce detailed output showing:
- Number of assertions executed
- Success/failure status for each assertion
- Return value comparisons between JNI and Panama
- Performance metrics (execution time, speedup factor)
- Any discrepancies or errors

Example output:
```
Performance comparison (5 assertions):
  JNI:    2.456 ms total, 0.491 ms avg
  Panama: 1.823 ms total, 0.365 ms avg
  Speedup: 1.35x (Panama faster)
```

## Writing Comparison Tests

### Basic Example

```java
@Test
void testI32Add() {
    // Create a WebAssembly module
    final byte[] module = WasmModuleBuilder.createI32AddModule();
    runner = new ComparisonTestRunner(module);

    // Add test assertions
    runner.addAssertion(TestAssertion.assertReturn("add",
        new Object[]{1, 2},
        new Object[]{3},
        "1 + 2 = 3"));

    runner.addAssertion(TestAssertion.assertReturn("add",
        new Object[]{-1, 1},
        new Object[]{0},
        "-1 + 1 = 0"));

    // Run on both JNI and Panama, compare results
    runAndCompare(module);
}
```

### Testing Error Conditions

```java
@Test
void testDivideByZero() {
    final byte[] module = createDivModule();
    runner = new ComparisonTestRunner(module);

    // Verify that both implementations trap on division by zero
    runner.addAssertion(TestAssertion.assertTrap("div",
        new Object[]{10, 0},
        "integer divide by zero"));

    runAndCompare(module);
}
```

### Testing Invalid Modules

```java
@Test
void testInvalidModule() {
    // Module with invalid bytecode
    final byte[] invalidModule = createInvalidModule();
    runner = new ComparisonTestRunner(invalidModule);

    // Verify that both implementations reject the module
    runner.addAssertion(TestAssertion.assertInvalid(
        "invalid module"));

    runAndCompare(invalidModule);
}
```

## Test Coverage Goals

### Phase 1: MVP (Completed)
- [x] Test framework infrastructure
- [x] ComparisonTestRunner
- [x] WasmModuleBuilder for simple modules
- [x] i32 arithmetic operations (add, sub, mul)
- [x] Basic comparison test suite
- [x] Maven profiles for test execution
- [x] WAST/JSON spec test parser
- [x] Test case metadata classes

### Phase 2: Extended Numeric Operations
- [x] i32 division operations (div_s, div_u)
- [x] Division by zero trap testing
- [x] i32 remainder operations (rem_s, rem_u) - 22 assertions
- [x] i32 bitwise operations (and, or, xor) - 24 assertions
- [x] i32 shift operations (shl, shr_s, shr_u) - 29 assertions
- [x] Remainder by zero trap testing
- [x] i32 comparison operations (eq, ne, lt, gt, le, ge) - 43 assertions
- [x] i64 operations (all categories) - 177 assertions
  - [x] i64 arithmetic (add, sub, mul) - 26 assertions
  - [x] i64 division and remainder operations - 55 assertions
  - [x] i64 bitwise operations - 24 assertions
  - [x] i64 shift operations - 29 assertions
  - [x] i64 comparison operations - 43 assertions
- [x] f32 operations (core operations) - 85 assertions
  - [x] f32 arithmetic (add, sub, mul) - 15 assertions
  - [x] f32 division and special cases - 18 assertions
  - [x] f32 unary operations (sqrt, abs, neg, ceil, floor) - 20 assertions
  - [x] f32 min/max operations - 6 assertions
  - [x] f32 comparison operations - 26 assertions
- [x] f64 operations (core operations) - 85 assertions
  - [x] f64 arithmetic (add, sub, mul) - 16 assertions
  - [x] f64 division and special cases - 19 assertions
  - [x] f64 unary operations (sqrt, abs, neg, ceil, floor) - 20 assertions
  - [x] f64 min/max operations - 6 assertions
  - [x] f64 comparison operations - 24 assertions
- [ ] Type conversion operations

### Phase 3: WebAssembly Spec Test Integration
- [ ] Download official WebAssembly testsuite as git submodule
- [x] JSON spec test parser (WebAssemblySpecTestParser)
- [x] Integration layer (SpecTestAdapter + SpecTestRunner)
- [x] End-to-end spec test execution framework
- [ ] Port numeric operation tests (~2000 tests)
- [ ] Port memory operation tests (~500 tests)
- [ ] Port control flow tests (~400 tests)

### Phase 4: Complete Coverage
- [ ] Table operations
- [ ] Global variables
- [ ] Import/export validation
- [ ] Memory growth and limits
- [ ] Custom sections
- [ ] Full spec compliance (10,000+ assertions)

## Integration with Official WASM Tests

### Planned Structure

```
wamr4j-tests/
└── src/test/resources/
    ├── wasm/
    │   ├── spec/          # Official spec test binaries
    │   │   ├── i32.wasm
    │   │   ├── i64.wasm
    │   │   ├── memory.wasm
    │   │   └── ... (100+ files)
    │   └── simple/        # Hand-crafted test modules
    └── wast/              # WAST test specifications
        ├── i32.wast
        └── ... (spec test files)
```

### Downloading the Official Test Suite

The official WebAssembly specification test suite contains thousands of tests for validating WebAssembly implementations. Use the provided script to download and set up the test suite:

```bash
# From the wamr4j-tests directory
./download-testsuite.sh
```

This script will:
- Clone the official WebAssembly testsuite repository
- Set it up in `src/test/resources/testsuite/`
- Display a summary of available test files
- Provide next steps for running the tests

The test suite includes:
- **WAST files**: WebAssembly text format with embedded test assertions
- **WASM files**: Pre-compiled WebAssembly binary modules
- **JSON files**: Machine-readable test specifications

To update an existing test suite installation, run the script again and choose to update when prompted.

### Using Official Spec Tests

The complete integration layer is now available for running official WebAssembly spec tests:

```java
@Test
void testI32SpecSuite() throws TestSuiteException {
    // Create spec test runner
    final SpecTestRunner runner = new SpecTestRunner();

    // Load WebAssembly module
    final byte[] moduleBytes = loadResource("/wasm/spec/i32.wasm");

    // Load and run spec tests from JSON file
    final SpecTestRunner.Results results = runner
            .loadSpecTestFile(Path.of("src/test/resources/spec/i32.json"))
            .withModuleBytes(moduleBytes)
            .runComparison();  // Runs on both JNI and Panama

    // Check results
    System.out.println(results);  // Prints summary for each runtime
    assertTrue(results.allPassed("JNI"), "All JNI tests should pass");

    // If Panama available, verify consistency
    if (results.getRuntimes().contains("Panama")) {
        assertEquals(
            results.getPassedCount("JNI"),
            results.getPassedCount("Panama"),
            "JNI and Panama should have identical results"
        );
    }
}
```

#### Running Tests on Specific Runtime

```java
@Test
void testOnJniOnly() throws TestSuiteException {
    final SpecTestRunner runner = new SpecTestRunner();

    final SpecTestRunner.Results results = runner
            .loadSpecTestFile(Path.of("spec/memory.json"))
            .withModuleBytes(moduleBytes)
            .runOn("jni");  // Run only on JNI

    LOGGER.info("JNI results: " + results);
}
```

#### Manual Test Case Creation

```java
@Test
void testCustomTestCases() {
    // Create test cases manually (useful for custom tests)
    final List<WebAssemblyTestCase> testCases = List.of(
        WebAssemblyTestCase.builder()
            .testId("custom_add_test")
            .testName("add_basic")
            .category(TestCategory.CUSTOM)
            .expected(TestExpectedResult.PASS)
            .metadata(Map.of(
                "function", "add",
                "arguments", List.of(5, 3),
                "expected", List.of(8)
            ))
            .build()
    );

    final SpecTestRunner.Results results = new SpecTestRunner()
            .addTestCases(testCases)
            .withModuleBytes(moduleBytes)
            .runComparison();

    assertTrue(results.getTotalCount("JNI") > 0);
}
```

**Spec Test JSON Format:**

The official WebAssembly spec tests use JSON format with these command types:
- `module` - Load/register a WebAssembly module
- `assert_return` - Function should return expected value
- `assert_trap` - Function should trap with specific error
- `assert_invalid` - Module should fail validation
- `assert_malformed` - Module should fail parsing
- `assert_uninstantiable` - Module should fail instantiation
- `invoke` - Call a function (without checking result)
- `register` - Register module with a name

## Logging Configuration

### Standard Logging (`logging.properties`)
- INFO level for normal test execution
- Shows test progress and summary results
- Minimal output for CI/CD environments

### Verbose Logging (`logging-verbose.properties`)
- FINE level for detailed debugging
- Shows individual assertion execution
- Detailed comparison output
- Performance breakdowns

Activate with `-Pcomparison-verbose` profile.

## Performance Metrics

The comparison framework automatically tracks:
- Individual assertion execution time
- Total execution time per runtime
- Average execution time per assertion
- Performance speedup factor (JNI vs Panama)

Metrics help identify:
- Performance regressions
- JNI vs Panama overhead differences
- Optimization opportunities

## Continuous Integration

Recommended CI workflow:
1. Build all modules: `./mvnw clean install -DskipTests`
2. Run comparison tests: `./mvnw test -pl wamr4j-tests -Pcomparison`
3. Run spec tests: `./mvnw test -pl wamr4j-tests -Pspec-tests`
4. Verify no discrepancies between JNI and Panama
5. Check performance metrics for regressions

## Contributing

When adding new comparison tests:
1. Extend `AbstractComparisonTest`
2. Use `WasmModuleBuilder` for simple modules or load from resources
3. Add assertions using `TestAssertion` factory methods
4. Call `runAndCompare()` to execute on both runtimes
5. Ensure tests are verbose for debugging (add descriptive messages)
6. Test boundary conditions and edge cases
7. Follow Google Java Style Guide

## Future Enhancements

### Official Testsuite Integration
Download and integrate the official WebAssembly testsuite:
- Add testsuite as git submodule
- Automated test discovery from testsuite directory
- Support for all spec test categories
- Continuous sync with upstream testsuite updates

### Advanced Test Execution
Enhanced test execution features:
- Parallel test execution for performance
- Test result caching and incremental testing
- Test case filtering by category, tags, or complexity
- Selective test execution based on code changes

### Native WAMR Comparison
Optional comparison against native WAMR binary:
- Execute same tests on native WAMR runtime
- Verify wamr4j results match native behavior
- Requires native WAMR installation
- Activate with `-Pnative-baseline` profile

### Test Result Database
Store test results for historical analysis:
- Track performance trends over time
- Identify regressions automatically
- Generate comparison reports
- Export metrics for visualization

## Resources

- [WebAssembly Specification](https://webassembly.github.io/spec/)
- [Official WebAssembly Test Suite](https://github.com/WebAssembly/testsuite)
- [WAMR GitHub Repository](https://github.com/bytecodealliance/wamr)
- [wamr4j Project Documentation](../README.md)
- [Test Execution Guide](TESTING.md)
- [Coverage Summary](TEST_COVERAGE_SUMMARY.md)

## Project Status

### ✅ Completed

**Phase 1: MVP (Complete)**
- Test framework infrastructure
- ComparisonTestRunner
- WasmModuleBuilder for programmatic module creation
- WAST/JSON spec test parser
- Maven profiles for test execution
- Comprehensive documentation

**Phase 2: Extended Numeric Operations (Complete)**
- i32 operations (all categories) - 179 assertions
- i64 operations (all categories) - 177 assertions
- f32 operations (core operations) - 85 assertions
- f64 operations (core operations) - 85 assertions
- **Total: 526 comparison assertions**

**Infrastructure**
- 18 comparison test classes
- 13 framework/parser classes
- 94 WebAssembly opcodes supported
- 35 factory methods for module creation
- Complete documentation (README, TESTING, COVERAGE_SUMMARY)

### 🔄 In Progress

**Phase 3: WebAssembly Spec Test Integration**
- Download script available (`download-testsuite.sh`)
- Integration framework complete
- Ready for official spec test execution

### 📋 Planned

**Phase 4: Complete Coverage**
- Type conversion operations
- Memory operations
- Control flow operations
- Table and global operations
- Full spec compliance (~10,000 assertions)
