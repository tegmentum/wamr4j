/*
 * Copyright (c) 2024 Tegmentum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.tegmentum.wamr4j.test.framework;

import ai.tegmentum.wamr4j.test.wast.TestSuiteException;
import ai.tegmentum.wamr4j.test.wast.WebAssemblySpecTestParser;
import ai.tegmentum.wamr4j.test.wast.WebAssemblyTestCase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Runner for executing WebAssembly specification tests.
 *
 * <p>This class orchestrates the execution of official WebAssembly spec tests
 * by parsing spec test files, converting them to executable assertions,
 * and running them through the comparison test framework.
 *
 * <p>Example usage:
 * <pre>{@code
 * SpecTestRunner runner = new SpecTestRunner();
 * SpecTestRunner.Results results = runner
 *     .loadSpecTestFile(Path.of("spec/i32.json"))
 *     .withModuleBytes(wasmModuleBytes)
 *     .runComparison();
 *
 * System.out.println("Passed: " + results.getPassedCount());
 * System.out.println("Failed: " + results.getFailedCount());
 * }</pre>
 *
 * @since 1.0.0
 */
public final class SpecTestRunner {

    private static final Logger LOGGER = Logger.getLogger(SpecTestRunner.class.getName());

    private final WebAssemblySpecTestParser parser;
    private final SpecTestAdapter adapter;
    private List<WebAssemblyTestCase> testCases;
    private byte[] moduleBytes;

    /**
     * Creates a new spec test runner.
     */
    public SpecTestRunner() {
        this.parser = new WebAssemblySpecTestParser();
        this.adapter = new SpecTestAdapter();
        this.testCases = new ArrayList<>();
    }

    /**
     * Loads spec test cases from a JSON file.
     *
     * @param specTestFile path to the spec test JSON file
     * @return this runner for method chaining
     * @throws TestSuiteException if loading fails
     */
    public SpecTestRunner loadSpecTestFile(final Path specTestFile) throws TestSuiteException {
        Objects.requireNonNull(specTestFile, "specTestFile must not be null");

        LOGGER.info("Loading spec test file: " + specTestFile);
        this.testCases = parser.parseSpecTestFile(specTestFile);
        LOGGER.info("Loaded " + testCases.size() + " test cases");

        return this;
    }

    /**
     * Adds individual test cases.
     *
     * @param testCases the test cases to add
     * @return this runner for method chaining
     */
    public SpecTestRunner addTestCases(final List<WebAssemblyTestCase> testCases) {
        Objects.requireNonNull(testCases, "testCases must not be null");
        this.testCases.addAll(testCases);
        return this;
    }

    /**
     * Sets the WebAssembly module bytes to test.
     *
     * @param moduleBytes the module bytecode
     * @return this runner for method chaining
     */
    public SpecTestRunner withModuleBytes(final byte[] moduleBytes) {
        this.moduleBytes = Objects.requireNonNull(moduleBytes, "moduleBytes must not be null").clone();
        return this;
    }

    /**
     * Runs the spec tests on both JNI and Panama implementations.
     *
     * @return test results
     * @throws IllegalStateException if module bytes are not set
     */
    public Results runComparison() {
        if (moduleBytes == null) {
            throw new IllegalStateException("Module bytes must be set before running tests");
        }

        LOGGER.info("Running " + testCases.size() + " spec test cases in comparison mode");

        // Convert test cases to assertions
        final List<TestAssertion> assertions = adapter.convertToAssertions(testCases);
        LOGGER.info("Converted to " + assertions.size() + " executable assertions");

        if (assertions.isEmpty()) {
            LOGGER.warning("No executable assertions generated from test cases");
            return new Results();
        }

        // Create comparison test runner
        final ComparisonTestRunner runner = new ComparisonTestRunner(moduleBytes);
        for (final TestAssertion assertion : assertions) {
            runner.addAssertion(assertion);
        }

        // Run on both implementations
        final Map<String, List<TestResult>> allResults = runner.runOnBoth();

        // Analyze results
        return analyzeResults(allResults, assertions);
    }

    /**
     * Runs the spec tests on a specific runtime implementation.
     *
     * @param runtimeType the runtime type ("jni" or "panama")
     * @return test results
     * @throws IllegalStateException if module bytes are not set
     */
    public Results runOn(final String runtimeType) {
        if (moduleBytes == null) {
            throw new IllegalStateException("Module bytes must be set before running tests");
        }

        Objects.requireNonNull(runtimeType, "runtimeType must not be null");

        LOGGER.info("Running " + testCases.size() + " spec test cases on " + runtimeType);

        // Convert test cases to assertions
        final List<TestAssertion> assertions = adapter.convertToAssertions(testCases);
        LOGGER.info("Converted to " + assertions.size() + " executable assertions");

        if (assertions.isEmpty()) {
            LOGGER.warning("No executable assertions generated from test cases");
            return new Results();
        }

        // Create comparison test runner
        final ComparisonTestRunner runner = new ComparisonTestRunner(moduleBytes);
        for (final TestAssertion assertion : assertions) {
            runner.addAssertion(assertion);
        }

        // Run on specified implementation
        final List<TestResult> results = runner.runOn(runtimeType);

        // Analyze results
        final Map<String, List<TestResult>> resultsMap = new HashMap<>();
        resultsMap.put(runtimeType, results);
        return analyzeResults(resultsMap, assertions);
    }

    /**
     * Gets the loaded test cases.
     *
     * @return list of test cases
     */
    public List<WebAssemblyTestCase> getTestCases() {
        return new ArrayList<>(testCases);
    }

    /**
     * Clears all loaded test cases.
     *
     * @return this runner for method chaining
     */
    public SpecTestRunner clear() {
        testCases.clear();
        moduleBytes = null;
        return this;
    }

    private Results analyzeResults(
            final Map<String, List<TestResult>> allResults,
            final List<TestAssertion> assertions) {

        final Results results = new Results();

        for (final Map.Entry<String, List<TestResult>> entry : allResults.entrySet()) {
            final String runtime = entry.getKey();
            final List<TestResult> testResults = entry.getValue();

            for (int i = 0; i < testResults.size() && i < assertions.size(); i++) {
                final TestResult result = testResults.get(i);
                final TestAssertion assertion = assertions.get(i);

                results.addResult(runtime, assertion, result);

                if (result.isSuccess()) {
                    results.incrementPassed(runtime);
                } else {
                    results.incrementFailed(runtime);
                }
            }
        }

        return results;
    }

    /**
     * Results from executing spec tests.
     *
     * @since 1.0.0
     */
    public static final class Results {
        private final Map<String, Integer> passedCounts;
        private final Map<String, Integer> failedCounts;
        private final Map<String, List<TestResult>> allResults;
        private final Map<String, List<TestAssertion>> allAssertions;

        Results() {
            this.passedCounts = new HashMap<>();
            this.failedCounts = new HashMap<>();
            this.allResults = new HashMap<>();
            this.allAssertions = new HashMap<>();
        }

        void incrementPassed(final String runtime) {
            passedCounts.merge(runtime, 1, Integer::sum);
        }

        void incrementFailed(final String runtime) {
            failedCounts.merge(runtime, 1, Integer::sum);
        }

        void addResult(final String runtime, final TestAssertion assertion, final TestResult result) {
            allResults.computeIfAbsent(runtime, k -> new ArrayList<>()).add(result);
            allAssertions.computeIfAbsent(runtime, k -> new ArrayList<>()).add(assertion);
        }

        /**
         * Gets the number of passed tests for a runtime.
         *
         * @param runtime the runtime name
         * @return the passed count
         */
        public int getPassedCount(final String runtime) {
            return passedCounts.getOrDefault(runtime, 0);
        }

        /**
         * Gets the number of failed tests for a runtime.
         *
         * @param runtime the runtime name
         * @return the failed count
         */
        public int getFailedCount(final String runtime) {
            return failedCounts.getOrDefault(runtime, 0);
        }

        /**
         * Gets the total number of tests for a runtime.
         *
         * @param runtime the runtime name
         * @return the total count
         */
        public int getTotalCount(final String runtime) {
            return getPassedCount(runtime) + getFailedCount(runtime);
        }

        /**
         * Gets all test results for a runtime.
         *
         * @param runtime the runtime name
         * @return list of test results
         */
        public List<TestResult> getResults(final String runtime) {
            return allResults.getOrDefault(runtime, List.of());
        }

        /**
         * Gets all tested runtimes.
         *
         * @return set of runtime names
         */
        public java.util.Set<String> getRuntimes() {
            return passedCounts.keySet();
        }

        /**
         * Checks if all tests passed for a runtime.
         *
         * @param runtime the runtime name
         * @return true if all tests passed
         */
        public boolean allPassed(final String runtime) {
            return getFailedCount(runtime) == 0 && getTotalCount(runtime) > 0;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SpecTest Results:\n");

            for (final String runtime : getRuntimes()) {
                sb.append("  ").append(runtime).append(": ");
                sb.append(getPassedCount(runtime)).append(" passed, ");
                sb.append(getFailedCount(runtime)).append(" failed, ");
                sb.append(getTotalCount(runtime)).append(" total\n");
            }

            return sb.toString();
        }
    }
}
