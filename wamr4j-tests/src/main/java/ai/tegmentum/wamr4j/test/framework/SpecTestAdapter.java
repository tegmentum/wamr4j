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

import ai.tegmentum.wamr4j.test.wast.TestExpectedResult;
import ai.tegmentum.wamr4j.test.wast.WebAssemblyTestCase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Adapter that converts WebAssembly spec test cases into executable test assertions.
 *
 * <p>This class bridges the gap between the parsed spec test format
 * (WebAssemblyTestCase) and the executable test format (TestAssertion).
 * It handles extracting function calls, arguments, and expected results
 * from spec test metadata.
 *
 * @since 1.0.0
 */
public final class SpecTestAdapter {

    private static final Logger LOGGER = Logger.getLogger(SpecTestAdapter.class.getName());

    /**
     * Converts a WebAssembly test case to a test assertion.
     *
     * <p>This method extracts the necessary information from the test case
     * metadata and creates an appropriate TestAssertion based on the
     * expected result type.
     *
     * @param testCase the spec test case to convert
     * @return the corresponding test assertion, or null if conversion is not possible
     */
    public TestAssertion convertToAssertion(final WebAssemblyTestCase testCase) {
        Objects.requireNonNull(testCase, "testCase must not be null");

        // Extract metadata
        final String functionName = extractFunctionName(testCase);
        final Object[] arguments = extractArguments(testCase);
        final Object[] expectedResults = extractExpectedResults(testCase);
        final String expectedError = extractExpectedError(testCase);

        // Convert based on expected result type
        switch (testCase.getExpected()) {
            case PASS:
                // PASS can be either assert_return or invoke
                if (expectedResults != null && expectedResults.length > 0) {
                    return TestAssertion.assertReturn(
                        functionName,
                        arguments,
                        expectedResults,
                        testCase.getDescription()
                    );
                } else {
                    // Simple invoke without checking result
                    LOGGER.fine("PASS test without expected results: " + testCase.getTestId());
                    return null;
                }

            case TRAP:
                return TestAssertion.assertTrap(
                    functionName,
                    arguments,
                    expectedError != null ? expectedError : "trap",
                    testCase.getDescription()
                );

            case FAIL:
                // FAIL could be assert_invalid, assert_malformed, or assert_uninstantiable
                if (testCase.hasTag("validation") || testCase.hasTag("assert_invalid")) {
                    return TestAssertion.assertInvalid(
                        expectedError != null ? expectedError : "invalid module",
                        testCase.getDescription()
                    );
                } else if (testCase.hasTag("parsing") || testCase.hasTag("assert_malformed")) {
                    return TestAssertion.assertInvalid(
                        expectedError != null ? expectedError : "malformed module",
                        testCase.getDescription()
                    );
                } else if (testCase.hasTag("instantiation") || testCase.hasTag("assert_uninstantiable")) {
                    return TestAssertion.assertUnlinkable(
                        expectedError != null ? expectedError : "uninstantiable module"
                    );
                } else {
                    return TestAssertion.assertInvalid(
                        expectedError != null ? expectedError : "module error",
                        testCase.getDescription()
                    );
                }

            case SKIP:
            case UNKNOWN:
            case TIMEOUT:
                LOGGER.fine("Skipping test with expected result: " + testCase.getExpected());
                return null;

            default:
                LOGGER.warning("Unsupported expected result: " + testCase.getExpected());
                return null;
        }
    }

    /**
     * Converts a list of WebAssembly test cases to test assertions.
     *
     * @param testCases the spec test cases to convert
     * @return list of test assertions (excluding null conversions)
     */
    public List<TestAssertion> convertToAssertions(final List<WebAssemblyTestCase> testCases) {
        Objects.requireNonNull(testCases, "testCases must not be null");

        final List<TestAssertion> assertions = new ArrayList<>();
        for (final WebAssemblyTestCase testCase : testCases) {
            final TestAssertion assertion = convertToAssertion(testCase);
            if (assertion != null) {
                assertions.add(assertion);
            }
        }

        LOGGER.info("Converted " + assertions.size() + " assertions from " + testCases.size() + " test cases");
        return assertions;
    }

    /**
     * Groups test cases by module for sequential execution.
     *
     * <p>Spec tests often have multiple test cases that operate on the same
     * module. This method groups them together for efficient execution.
     *
     * @param testCases the spec test cases to group
     * @return map of module ID to list of test cases
     */
    public Map<String, List<WebAssemblyTestCase>> groupByModule(final List<WebAssemblyTestCase> testCases) {
        Objects.requireNonNull(testCases, "testCases must not be null");

        final Map<String, List<WebAssemblyTestCase>> grouped = new HashMap<>();
        String currentModule = "default";

        for (final WebAssemblyTestCase testCase : testCases) {
            // Check if this is a module command
            if (testCase.hasTag("module")) {
                currentModule = testCase.getTestId();
                grouped.putIfAbsent(currentModule, new ArrayList<>());
            } else {
                // Add to current module's test cases
                grouped.computeIfAbsent(currentModule, k -> new ArrayList<>()).add(testCase);
            }
        }

        LOGGER.info("Grouped " + testCases.size() + " test cases into " + grouped.size() + " modules");
        return grouped;
    }

    private String extractFunctionName(final WebAssemblyTestCase testCase) {
        // Try to get from metadata first
        final String metadataFunction = testCase.getMetadataValue("function");
        if (metadataFunction != null) {
            return metadataFunction;
        }

        // Try to extract from test name
        final String testName = testCase.getTestName();
        if (testName.contains("invoke_")) {
            final String[] parts = testName.split("_");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        // Try to extract from description
        final String description = testCase.getDescription();
        if (description != null && description.contains("Invoke function:")) {
            return description.substring(description.indexOf("Invoke function:") + 16).trim();
        }

        // Default
        return "test";
    }

    private Object[] extractArguments(final WebAssemblyTestCase testCase) {
        // Try to get from metadata
        final List<?> metadataArgs = testCase.getMetadataValue("arguments");
        if (metadataArgs != null) {
            return metadataArgs.toArray();
        }

        // Default to empty arguments
        return new Object[0];
    }

    private Object[] extractExpectedResults(final WebAssemblyTestCase testCase) {
        // Try to get from metadata
        final List<?> metadataResults = testCase.getMetadataValue("expected");
        if (metadataResults != null) {
            return metadataResults.toArray();
        }

        // For PASS tests without explicit results, return empty array
        if (testCase.getExpected() == TestExpectedResult.PASS) {
            return new Object[0];
        }

        return null;
    }

    private String extractExpectedError(final WebAssemblyTestCase testCase) {
        // Try to get from metadata first
        final String metadataError = testCase.getMetadataValue("error");
        if (metadataError != null) {
            return metadataError;
        }

        // Try to extract from description
        final String description = testCase.getDescription();
        if (description != null) {
            if (description.contains("should trap:")) {
                return description.substring(description.indexOf("should trap:") + 12).trim();
            }
            if (description.contains("should be invalid:")) {
                return description.substring(description.indexOf("should be invalid:") + 18).trim();
            }
            if (description.contains("should be malformed:")) {
                return description.substring(description.indexOf("should be malformed:") + 20).trim();
            }
            if (description.contains("should be uninstantiable:")) {
                return description.substring(description.indexOf("should be uninstantiable:") + 25).trim();
            }
        }

        return null;
    }
}
