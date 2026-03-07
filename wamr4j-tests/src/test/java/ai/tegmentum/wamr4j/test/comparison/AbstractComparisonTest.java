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

package ai.tegmentum.wamr4j.test.comparison;

import ai.tegmentum.wamr4j.test.framework.ComparisonTestRunner;
import ai.tegmentum.wamr4j.test.framework.TestResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for comparison tests.
 *
 * <p>This class provides utilities for running the same tests across
 * JNI and Panama implementations and comparing results for consistency.
 *
 * @since 1.0.0
 */
public abstract class AbstractComparisonTest {

    protected static final Logger LOGGER = Logger.getLogger(AbstractComparisonTest.class.getName());

    private static final double FLOAT_EPSILON = 1e-6;
    private static final double DOUBLE_EPSILON = 1e-12;

    protected ComparisonTestRunner runner;

    @AfterEach
    void cleanupRunner(final TestInfo testInfo) {
        if (runner != null) {
            runner.clear();
            LOGGER.info("Completed test: " + testInfo.getDisplayName());
        }
    }

    /**
     * Runs assertions on both JNI and Panama and verifies they produce identical results.
     *
     * @param moduleBytes the WebAssembly module to test
     */
    protected void runAndCompare(final byte[] moduleBytes) {
        runAndCompare(moduleBytes, null);
    }

    /**
     * Runs assertions on both JNI and Panama with imports and verifies they produce identical results.
     *
     * @param moduleBytes the WebAssembly module to test
     * @param imports the imports to provide to the module
     */
    protected void runAndCompare(final byte[] moduleBytes, final Map<String, Map<String, Object>> imports) {
        if (runner == null) {
            runner = new ComparisonTestRunner(moduleBytes, imports);
        }

        final Map<String, List<TestResult>> allResults = runner.runOnBoth();

        final List<TestResult> jniResults = allResults.get("JNI");
        final List<TestResult> panamaResults = allResults.get("Panama");

        assertNotNull(jniResults, "JNI results should not be null");

        if (panamaResults == null) {
            LOGGER.warning("Panama runtime not available, skipping comparison");
            return;
        }

        assertEquals(jniResults.size(), panamaResults.size(),
            "JNI and Panama should execute the same number of assertions");

        // Compare each result
        for (int i = 0; i < jniResults.size(); i++) {
            final TestResult jniResult = jniResults.get(i);
            final TestResult panamaResult = panamaResults.get(i);

            compareResults(jniResult, panamaResult, i);
        }

        logPerformanceComparison(jniResults, panamaResults);
    }

    /**
     * Compares two test results for equality.
     *
     * @param jniResult the JNI test result
     * @param panamaResult the Panama test result
     * @param assertionIndex the assertion index for error reporting
     */
    protected void compareResults(final TestResult jniResult,
                                  final TestResult panamaResult,
                                  final int assertionIndex) {
        LOGGER.fine("Comparing assertion " + assertionIndex);
        LOGGER.fine("  JNI:    " + jniResult);
        LOGGER.fine("  Panama: " + panamaResult);

        // Both should have the same success/failure status
        assertEquals(jniResult.isSuccess(), panamaResult.isSuccess(),
            "Assertion " + assertionIndex + ": JNI and Panama should have same success status");

        if (jniResult.isSuccess()) {
            // Compare return values
            compareReturnValues(
                jniResult.getActualResults(),
                panamaResult.getActualResults(),
                assertionIndex
            );
        } else {
            // Compare error types
            assertEquals(jniResult.getErrorType(), panamaResult.getErrorType(),
                "Assertion " + assertionIndex + ": JNI and Panama should throw same error type");
        }
    }

    /**
     * Compares return values with appropriate handling for floating-point values.
     *
     * @param jniValues the JNI return values
     * @param panamaValues the Panama return values
     * @param assertionIndex the assertion index for error reporting
     */
    protected void compareReturnValues(final Object[] jniValues,
                                       final Object[] panamaValues,
                                       final int assertionIndex) {
        assertEquals(jniValues.length, panamaValues.length,
            "Assertion " + assertionIndex + ": Should have same number of return values");

        for (int i = 0; i < jniValues.length; i++) {
            final Object jniValue = jniValues[i];
            final Object panamaValue = panamaValues[i];

            if (jniValue instanceof Float && panamaValue instanceof Float) {
                assertEquals((Float) jniValue, (Float) panamaValue, FLOAT_EPSILON,
                    "Assertion " + assertionIndex + ", result " + i + ": Float values should match");
            } else if (jniValue instanceof Double && panamaValue instanceof Double) {
                assertEquals((Double) jniValue, (Double) panamaValue, DOUBLE_EPSILON,
                    "Assertion " + assertionIndex + ", result " + i + ": Double values should match");
            } else {
                assertEquals(jniValue, panamaValue,
                    "Assertion " + assertionIndex + ", result " + i + ": Values should match exactly");
            }
        }
    }

    /**
     * Logs performance comparison between JNI and Panama implementations.
     *
     * @param jniResults the JNI test results
     * @param panamaResults the Panama test results
     */
    protected void logPerformanceComparison(final List<TestResult> jniResults,
                                           final List<TestResult> panamaResults) {
        if (jniResults.isEmpty() || panamaResults.isEmpty()) {
            return;
        }

        final long jniTotalNanos = jniResults.stream()
            .mapToLong(TestResult::getExecutionTimeNanos)
            .sum();

        final long panamaTotalNanos = panamaResults.stream()
            .mapToLong(TestResult::getExecutionTimeNanos)
            .sum();

        final double jniAvgMillis = jniTotalNanos / 1_000_000.0 / jniResults.size();
        final double panamaAvgMillis = panamaTotalNanos / 1_000_000.0 / panamaResults.size();

        LOGGER.info(String.format("Performance comparison (%d assertions):", jniResults.size()));
        LOGGER.info(String.format("  JNI:    %.3f ms total, %.3f ms avg", jniTotalNanos / 1_000_000.0, jniAvgMillis));
        LOGGER.info(String.format("  Panama: %.3f ms total, %.3f ms avg", panamaTotalNanos / 1_000_000.0, panamaAvgMillis));

        if (panamaAvgMillis > 0) {
            final double speedup = jniAvgMillis / panamaAvgMillis;
            LOGGER.info(String.format("  Speedup: %.2fx %s", Math.abs(speedup),
                speedup > 1.0 ? "(Panama faster)" : "(JNI faster)"));
        }
    }

    /**
     * Loads a test WebAssembly module from resources.
     *
     * @param resourcePath the path to the WASM file in test resources
     * @return the WebAssembly module bytes
     */
    protected byte[] loadTestModule(final String resourcePath) {
        try {
            final var inputStream = getClass().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                throw new RuntimeException("Test resource not found: " + resourcePath);
            }

            return inputStream.readAllBytes();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to load test module: " + resourcePath, e);
        }
    }
}
