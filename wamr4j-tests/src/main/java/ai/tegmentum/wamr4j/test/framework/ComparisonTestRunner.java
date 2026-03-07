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

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Test runner that executes WebAssembly tests across multiple runtime implementations.
 *
 * <p>This runner executes the same test assertions on both JNI and Panama implementations
 * to verify behavioral consistency. It captures results, timing metrics, and errors
 * for detailed comparison.
 *
 * @since 1.0.0
 */
public final class ComparisonTestRunner {

    private static final Logger LOGGER = Logger.getLogger(ComparisonTestRunner.class.getName());

    private final byte[] moduleBytes;
    private final Map<String, Map<String, Object>> imports;
    private final List<TestAssertion> assertions;
    private final Map<String, TestResult> results;

    /**
     * Creates a new comparison test runner.
     *
     * @param moduleBytes the WebAssembly module bytecode to test
     */
    public ComparisonTestRunner(final byte[] moduleBytes) {
        this(moduleBytes, null);
    }

    /**
     * Creates a new comparison test runner with imports.
     *
     * @param moduleBytes the WebAssembly module bytecode to test
     * @param imports the imports to provide to the module
     */
    public ComparisonTestRunner(final byte[] moduleBytes, final Map<String, Map<String, Object>> imports) {
        this.moduleBytes = Objects.requireNonNull(moduleBytes, "moduleBytes must not be null").clone();
        this.imports = imports;
        this.assertions = new ArrayList<>();
        this.results = new HashMap<>();
    }

    /**
     * Adds a test assertion to be executed.
     *
     * @param assertion the test assertion
     * @return this runner for method chaining
     */
    public ComparisonTestRunner addAssertion(final TestAssertion assertion) {
        assertions.add(Objects.requireNonNull(assertion, "assertion must not be null"));
        return this;
    }

    /**
     * Adds multiple test assertions to be executed.
     *
     * @param assertions the test assertions
     * @return this runner for method chaining
     */
    public ComparisonTestRunner addAssertions(final TestAssertion... assertions) {
        for (final TestAssertion assertion : assertions) {
            addAssertion(assertion);
        }
        return this;
    }

    /**
     * Runs all assertions on the specified runtime implementation.
     *
     * @param runtimeType the runtime type to use ("jni" or "panama")
     * @return a list of test results
     */
    public List<TestResult> runOn(final String runtimeType) {
        Objects.requireNonNull(runtimeType, "runtimeType must not be null");

        LOGGER.info("Running " + assertions.size() + " assertions on " + runtimeType + " runtime");

        final List<TestResult> testResults = new ArrayList<>();

        // Set system property to force specific runtime
        final String originalProperty = System.getProperty("wamr4j.runtime");
        try {
            System.setProperty("wamr4j.runtime", runtimeType.toLowerCase());

            try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
                final String actualImplementation = runtime.getImplementation();
                LOGGER.info("Using runtime implementation: " + actualImplementation);

                // Compile module once and close when done
                try (final WebAssemblyModule module = runtime.compile(moduleBytes)) {
                    // Execute each assertion
                    for (final TestAssertion assertion : assertions) {
                        final TestResult result =
                            executeAssertion(runtime, module, assertion, actualImplementation);
                        testResults.add(result);

                        final String key = runtimeType + ":" + assertions.indexOf(assertion);
                        results.put(key, result);
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.severe("Failed to create runtime: " + e.getMessage());
            throw new RuntimeException("Runtime creation failed for " + runtimeType, e);
        } finally {
            // Restore original property
            if (originalProperty != null) {
                System.setProperty("wamr4j.runtime", originalProperty);
            } else {
                System.clearProperty("wamr4j.runtime");
            }
        }

        return testResults;
    }

    /**
     * Runs all assertions on both JNI and Panama implementations.
     *
     * @return a map of runtime type to list of test results
     */
    public Map<String, List<TestResult>> runOnBoth() {
        final Map<String, List<TestResult>> allResults = new HashMap<>();

        // Run on JNI
        allResults.put("JNI", runOn("jni"));

        // Run on Panama (if Java 23+)
        try {
            allResults.put("Panama", runOn("panama"));
        } catch (final Exception e) {
            LOGGER.warning("Panama tests skipped or failed: " + e.getMessage());
        }

        return allResults;
    }

    /**
     * Gets all collected test results.
     *
     * @return a map of result keys to test results
     */
    public Map<String, TestResult> getResults() {
        return new HashMap<>(results);
    }

    /**
     * Clears all assertions and results.
     */
    public void clear() {
        assertions.clear();
        results.clear();
    }

    private TestResult executeAssertion(final WebAssemblyRuntime runtime,
                                       final WebAssemblyModule module,
                                       final TestAssertion assertion,
                                       final String runtimeImplementation) {
        LOGGER.fine("Executing assertion: " + assertion);

        final long startTime = System.nanoTime();

        try {
            switch (assertion.getType()) {
                case ASSERT_RETURN:
                    return executeAssertReturn(runtime, module, assertion, runtimeImplementation, startTime);

                case ASSERT_TRAP:
                    return executeAssertTrap(runtime, module, assertion, runtimeImplementation, startTime);

                case ASSERT_INVALID:
                    return executeAssertInvalid(runtime, assertion, runtimeImplementation, startTime);

                case ASSERT_EXHAUSTION:
                    return executeAssertExhaustion(runtime, module, assertion, runtimeImplementation, startTime);

                case ASSERT_UNLINKABLE:
                    return executeAssertUnlinkable(runtime, module, assertion, runtimeImplementation, startTime);

                default:
                    throw new IllegalArgumentException("Unsupported assertion type: " + assertion.getType());
            }
        } catch (final Exception e) {
            final long duration = System.nanoTime() - startTime;
            LOGGER.warning("Assertion execution failed: " + e.getMessage());
            return TestResult.failure(e, duration, runtimeImplementation);
        }
    }

    private TestResult executeAssertReturn(final WebAssemblyRuntime runtime,
                                          final WebAssemblyModule module,
                                          final TestAssertion assertion,
                                          final String runtimeImplementation,
                                          final long startTime) throws Exception {
        try (final WebAssemblyInstance instance =
                imports != null ? module.instantiate(imports) : module.instantiate()) {
            final WebAssemblyFunction function = instance.getFunction(assertion.getFunctionName());

            if (function == null) {
                throw new IllegalArgumentException("Function not found: " + assertion.getFunctionName());
            }

            final Object result = function.invoke(assertion.getArguments());
            final Object[] results = (result == null) ? new Object[0] :
                (result.getClass().isArray() ? (Object[]) result : new Object[]{result});
            final long duration = System.nanoTime() - startTime;

            return TestResult.success(results, duration, runtimeImplementation);
        }
    }

    private TestResult executeAssertTrap(final WebAssemblyRuntime runtime,
                                        final WebAssemblyModule module,
                                        final TestAssertion assertion,
                                        final String runtimeImplementation,
                                        final long startTime) throws Exception {
        try (final WebAssemblyInstance instance =
                imports != null ? module.instantiate(imports) : module.instantiate()) {
            final WebAssemblyFunction function = instance.getFunction(assertion.getFunctionName());

            if (function == null) {
                throw new IllegalArgumentException("Function not found: " + assertion.getFunctionName());
            }

            // This should trap
            function.invoke(assertion.getArguments());

            // If we get here, the trap didn't occur
            final long duration = System.nanoTime() - startTime;
            return TestResult.failure(
                new AssertionError("Expected trap but function returned normally"),
                duration,
                runtimeImplementation
            );
        } catch (final Exception e) {
            // Expected trap occurred — this is a success
            final long duration = System.nanoTime() - startTime;
            return TestResult.success(
                new Object[]{e.getClass().getSimpleName() + ": " + e.getMessage()},
                duration, runtimeImplementation);
        }
    }

    private TestResult executeAssertInvalid(final WebAssemblyRuntime runtime,
                                           final TestAssertion assertion,
                                           final String runtimeImplementation,
                                           final long startTime) {
        try {
            // This should fail validation
            runtime.compile(moduleBytes);

            // If we get here, validation didn't fail as expected
            final long duration = System.nanoTime() - startTime;
            return TestResult.failure(
                new AssertionError("Expected invalid module but compilation succeeded"),
                duration,
                runtimeImplementation
            );
        } catch (final Exception e) {
            // Expected validation failure — this is a success
            final long duration = System.nanoTime() - startTime;
            return TestResult.success(
                new Object[]{e.getClass().getSimpleName() + ": " + e.getMessage()},
                duration, runtimeImplementation);
        }
    }

    private TestResult executeAssertExhaustion(final WebAssemblyRuntime runtime,
                                              final WebAssemblyModule module,
                                              final TestAssertion assertion,
                                              final String runtimeImplementation,
                                              final long startTime) throws Exception {
        try (final WebAssemblyInstance instance =
                imports != null ? module.instantiate(imports) : module.instantiate()) {
            final WebAssemblyFunction function = instance.getFunction(assertion.getFunctionName());

            if (function == null) {
                throw new IllegalArgumentException("Function not found: " + assertion.getFunctionName());
            }

            // This should exhaust the stack
            function.invoke(assertion.getArguments());

            // If we get here, exhaustion didn't occur
            final long duration = System.nanoTime() - startTime;
            return TestResult.failure(
                new AssertionError("Expected stack exhaustion but function returned normally"),
                duration,
                runtimeImplementation
            );
        } catch (final Exception e) {
            // Expected exhaustion occurred — this is a success
            final long duration = System.nanoTime() - startTime;
            return TestResult.success(
                new Object[]{e.getClass().getSimpleName() + ": " + e.getMessage()},
                duration, runtimeImplementation);
        }
    }

    @SuppressWarnings("try")
    private TestResult executeAssertUnlinkable(final WebAssemblyRuntime runtime,
                                              final WebAssemblyModule module,
                                              final TestAssertion assertion,
                                              final String runtimeImplementation,
                                              final long startTime) {
        try {
            // This should fail to instantiate (link) — instance only used for auto-close
            try (final WebAssemblyInstance instance =
                    imports != null ? module.instantiate(imports) : module.instantiate()) {
                // If we get here, linking didn't fail as expected
                final long duration = System.nanoTime() - startTime;
                return TestResult.failure(
                    new AssertionError("Expected unlinkable module but instantiation succeeded"),
                    duration,
                    runtimeImplementation
                );
            }
        } catch (final Exception e) {
            // Expected linking failure — this is a success
            final long duration = System.nanoTime() - startTime;
            return TestResult.success(
                new Object[]{e.getClass().getSimpleName() + ": " + e.getMessage()},
                duration, runtimeImplementation);
        }
    }
}
