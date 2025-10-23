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
 * Represents a test assertion for WebAssembly execution.
 *
 * <p>Supports different assertion types from the WebAssembly specification test suite:
 * <ul>
 *   <li>ASSERT_RETURN - Verify function returns expected value</li>
 *   <li>ASSERT_TRAP - Verify function traps with specific error</li>
 *   <li>ASSERT_INVALID - Verify module is invalid and fails validation</li>
 *   <li>ASSERT_EXHAUSTION - Verify stack exhaustion occurs</li>
 *   <li>ASSERT_UNLINKABLE - Verify module fails to link</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class TestAssertion {

    /**
     * Types of assertions supported by the test framework.
     */
    public enum Type {
        /** Verify function returns expected value. */
        ASSERT_RETURN,

        /** Verify function traps with specific error. */
        ASSERT_TRAP,

        /** Verify module is invalid and fails validation. */
        ASSERT_INVALID,

        /** Verify stack exhaustion occurs. */
        ASSERT_EXHAUSTION,

        /** Verify module fails to link. */
        ASSERT_UNLINKABLE
    }

    private final Type type;
    private final String functionName;
    private final Object[] arguments;
    private final Object[] expectedResults;
    private final String expectedError;
    private final String description;

    private TestAssertion(final Type type,
                          final String functionName,
                          final Object[] arguments,
                          final Object[] expectedResults,
                          final String expectedError,
                          final String description) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.functionName = functionName;
        this.arguments = arguments != null ? arguments.clone() : new Object[0];
        this.expectedResults = expectedResults != null ? expectedResults.clone() : new Object[0];
        this.expectedError = expectedError;
        this.description = description;
    }

    /**
     * Creates an assertion that verifies a function returns expected values.
     *
     * @param functionName the name of the function to call
     * @param arguments the arguments to pass to the function
     * @param expectedResults the expected return values
     * @return a new ASSERT_RETURN assertion
     */
    public static TestAssertion assertReturn(final String functionName,
                                            final Object[] arguments,
                                            final Object[] expectedResults) {
        return new TestAssertion(Type.ASSERT_RETURN, functionName, arguments, expectedResults, null, null);
    }

    /**
     * Creates an assertion that verifies a function returns expected values.
     *
     * @param functionName the name of the function to call
     * @param arguments the arguments to pass to the function
     * @param expectedResults the expected return values
     * @param description human-readable description of the test
     * @return a new ASSERT_RETURN assertion
     */
    public static TestAssertion assertReturn(final String functionName,
                                            final Object[] arguments,
                                            final Object[] expectedResults,
                                            final String description) {
        return new TestAssertion(Type.ASSERT_RETURN, functionName, arguments, expectedResults, null, description);
    }

    /**
     * Creates an assertion that verifies a function traps with specific error.
     *
     * @param functionName the name of the function to call
     * @param arguments the arguments to pass to the function
     * @param expectedError the expected error message or type
     * @return a new ASSERT_TRAP assertion
     */
    public static TestAssertion assertTrap(final String functionName,
                                          final Object[] arguments,
                                          final String expectedError) {
        return new TestAssertion(Type.ASSERT_TRAP, functionName, arguments, null, expectedError, null);
    }

    /**
     * Creates an assertion that verifies a function traps with specific error.
     *
     * @param functionName the name of the function to call
     * @param arguments the arguments to pass to the function
     * @param expectedError the expected error message or type
     * @param description human-readable description of the test
     * @return a new ASSERT_TRAP assertion
     */
    public static TestAssertion assertTrap(final String functionName,
                                          final Object[] arguments,
                                          final String expectedError,
                                          final String description) {
        return new TestAssertion(Type.ASSERT_TRAP, functionName, arguments, null, expectedError, description);
    }

    /**
     * Creates an assertion that verifies module validation fails.
     *
     * @param expectedError the expected validation error
     * @return a new ASSERT_INVALID assertion
     */
    public static TestAssertion assertInvalid(final String expectedError) {
        return new TestAssertion(Type.ASSERT_INVALID, null, null, null, expectedError, null);
    }

    /**
     * Creates an assertion that verifies module validation fails.
     *
     * @param expectedError the expected validation error
     * @param description human-readable description of the test
     * @return a new ASSERT_INVALID assertion
     */
    public static TestAssertion assertInvalid(final String expectedError, final String description) {
        return new TestAssertion(Type.ASSERT_INVALID, null, null, null, expectedError, description);
    }

    /**
     * Creates an assertion that verifies stack exhaustion occurs.
     *
     * @param functionName the name of the function to call
     * @param arguments the arguments to pass to the function
     * @return a new ASSERT_EXHAUSTION assertion
     */
    public static TestAssertion assertExhaustion(final String functionName,
                                                final Object[] arguments) {
        return new TestAssertion(Type.ASSERT_EXHAUSTION, functionName, arguments, null, "stack exhaustion", null);
    }

    /**
     * Creates an assertion that verifies module linking fails.
     *
     * @param expectedError the expected linking error
     * @return a new ASSERT_UNLINKABLE assertion
     */
    public static TestAssertion assertUnlinkable(final String expectedError) {
        return new TestAssertion(Type.ASSERT_UNLINKABLE, null, null, null, expectedError, null);
    }

    /**
     * Gets the assertion type.
     *
     * @return the assertion type
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the function name to call.
     *
     * @return the function name, or null for module-level assertions
     */
    public String getFunctionName() {
        return functionName;
    }

    /**
     * Gets the arguments to pass to the function.
     *
     * @return a copy of the arguments array
     */
    public Object[] getArguments() {
        return arguments.clone();
    }

    /**
     * Gets the expected return values.
     *
     * @return a copy of the expected results array
     */
    public Object[] getExpectedResults() {
        return expectedResults.clone();
    }

    /**
     * Gets the expected error message or type.
     *
     * @return the expected error, or null if not applicable
     */
    public String getExpectedError() {
        return expectedError;
    }

    /**
     * Gets the human-readable description of this assertion.
     *
     * @return the description, or null if not provided
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(type);

        if (description != null) {
            sb.append(" (").append(description).append(")");
        }

        if (functionName != null) {
            sb.append(": ").append(functionName);
            sb.append("(").append(Arrays.toString(arguments)).append(")");
        }

        if (expectedResults.length > 0) {
            sb.append(" => ").append(Arrays.toString(expectedResults));
        }

        if (expectedError != null) {
            sb.append(" error: ").append(expectedError);
        }

        return sb.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TestAssertion)) {
            return false;
        }
        final TestAssertion other = (TestAssertion) obj;
        return type == other.type
            && Objects.equals(functionName, other.functionName)
            && Arrays.equals(arguments, other.arguments)
            && Arrays.equals(expectedResults, other.expectedResults)
            && Objects.equals(expectedError, other.expectedError)
            && Objects.equals(description, other.description);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, functionName, expectedError, description);
        result = 31 * result + Arrays.hashCode(arguments);
        result = 31 * result + Arrays.hashCode(expectedResults);
        return result;
    }
}
