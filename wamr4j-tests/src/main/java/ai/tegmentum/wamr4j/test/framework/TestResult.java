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

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents the result of executing a test assertion.
 *
 * <p>Captures the actual outcome of running a WebAssembly function or
 * loading a module, including return values, errors, and execution time.
 *
 * @since 1.0.0
 */
public final class TestResult {

    private final boolean success;
    private final Object[] actualResults;
    private final Throwable error;
    private final long executionTimeNanos;
    private final String runtimeImplementation;

    private TestResult(final boolean success,
                       final Object[] actualResults,
                       final Throwable error,
                       final long executionTimeNanos,
                       final String runtimeImplementation) {
        this.success = success;
        this.actualResults = actualResults != null ? actualResults.clone() : new Object[0];
        this.error = error;
        this.executionTimeNanos = executionTimeNanos;
        this.runtimeImplementation = Objects.requireNonNull(runtimeImplementation, "runtimeImplementation must not be null");
    }

    /**
     * Creates a successful test result.
     *
     * @param actualResults the actual return values from the function
     * @param executionTimeNanos the execution time in nanoseconds
     * @param runtimeImplementation the runtime implementation used (JNI or Panama)
     * @return a new successful test result
     */
    public static TestResult success(final Object[] actualResults,
                                     final long executionTimeNanos,
                                     final String runtimeImplementation) {
        return new TestResult(true, actualResults, null, executionTimeNanos, runtimeImplementation);
    }

    /**
     * Creates a failed test result.
     *
     * @param error the error that occurred
     * @param executionTimeNanos the execution time in nanoseconds
     * @param runtimeImplementation the runtime implementation used (JNI or Panama)
     * @return a new failed test result
     */
    public static TestResult failure(final Throwable error,
                                     final long executionTimeNanos,
                                     final String runtimeImplementation) {
        return new TestResult(false, null, Objects.requireNonNull(error, "error must not be null"), executionTimeNanos, runtimeImplementation);
    }

    /**
     * Checks if the test execution was successful.
     *
     * @return true if successful, false if an error occurred
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the actual return values from the function.
     *
     * @return a copy of the actual results array, or empty array if execution failed
     */
    public Object[] getActualResults() {
        return actualResults.clone();
    }

    /**
     * Gets the error that occurred during execution.
     *
     * @return the error, or null if execution was successful
     */
    public Throwable getError() {
        return error;
    }

    /**
     * Gets the execution time in nanoseconds.
     *
     * @return the execution time
     */
    public long getExecutionTimeNanos() {
        return executionTimeNanos;
    }

    /**
     * Gets the execution time in milliseconds.
     *
     * @return the execution time in milliseconds
     */
    public double getExecutionTimeMillis() {
        return executionTimeNanos / 1_000_000.0;
    }

    /**
     * Gets the runtime implementation that produced this result.
     *
     * @return the runtime implementation (JNI or Panama)
     */
    public String getRuntimeImplementation() {
        return runtimeImplementation;
    }

    /**
     * Gets the error message if execution failed.
     *
     * @return the error message, or null if successful
     */
    public String getErrorMessage() {
        return error != null ? error.getMessage() : null;
    }

    /**
     * Gets the error type if execution failed.
     *
     * @return the error class name, or null if successful
     */
    public String getErrorType() {
        return error != null ? error.getClass().getSimpleName() : null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TestResult[").append(runtimeImplementation).append("]");

        if (success) {
            sb.append(" SUCCESS: ");
            sb.append(Arrays.toString(actualResults));
            sb.append(" (").append(String.format("%.3f", getExecutionTimeMillis())).append("ms)");
        } else {
            sb.append(" FAILURE: ");
            sb.append(getErrorType()).append(": ").append(getErrorMessage());
            sb.append(" (").append(String.format("%.3f", getExecutionTimeMillis())).append("ms)");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TestResult)) {
            return false;
        }
        final TestResult other = (TestResult) obj;
        return success == other.success
            && Arrays.equals(actualResults, other.actualResults)
            && Objects.equals(getErrorMessage(), other.getErrorMessage())
            && Objects.equals(getErrorType(), other.getErrorType())
            && Objects.equals(runtimeImplementation, other.runtimeImplementation);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(success, getErrorMessage(), getErrorType(), runtimeImplementation);
        result = 31 * result + Arrays.hashCode(actualResults);
        return result;
    }
}
