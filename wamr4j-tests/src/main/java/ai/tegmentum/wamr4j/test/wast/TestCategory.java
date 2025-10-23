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

package ai.tegmentum.wamr4j.test.wast;

/**
 * Categories for WebAssembly test cases to enable filtering and organization.
 *
 * @since 1.0.0
 */
public enum TestCategory {
    // Official WebAssembly specification tests
    SPEC_CORE("spec-core", "WebAssembly Core Specification", "Tests for core WebAssembly features"),
    SPEC_JS_API("spec-js-api", "JavaScript API Specification", "Tests for JavaScript API integration"),
    SPEC_WEB_API("spec-web-api", "Web API Specification", "Tests for Web platform integration"),

    // WebAssembly proposals
    SPEC_BULK_MEMORY("spec-bulk-memory", "Bulk Memory Operations", "Tests for bulk memory operations proposal"),
    SPEC_REFERENCE_TYPES("spec-reference-types", "Reference Types", "Tests for reference types proposal"),
    SPEC_SIMD("spec-simd", "SIMD Operations", "Tests for SIMD operations proposal"),
    SPEC_THREADS("spec-threads", "Threading", "Tests for threading proposal"),
    SPEC_TAIL_CALLS("spec-tail-calls", "Tail Calls", "Tests for tail calls proposal"),
    SPEC_MULTI_VALUE("spec-multi-value", "Multi-Value", "Tests for multi-value proposal"),
    SPEC_MULTI_MEMORY("spec-multi-memory", "Multi-Memory", "Tests for multi-memory proposal"),
    SPEC_MEMORY64("spec-memory64", "64-bit Memory", "Tests for 64-bit memory proposal"),
    SPEC_EXCEPTION_HANDLING("spec-exception-handling", "Exception Handling", "Tests for exception handling proposal"),
    SPEC_GC("spec-gc", "Garbage Collection", "Tests for garbage collection proposal"),
    SPEC_COMPONENT_MODEL("spec-component-model", "Component Model", "Tests for component model proposal"),

    // WAMR-specific tests
    WAMR_REGRESSION("wamr-regression", "WAMR Regressions", "Regression tests for WAMR"),
    WAMR_PERFORMANCE("wamr-performance", "WAMR Performance", "Performance tests for WAMR"),
    WAMR_AOT("wamr-aot", "WAMR AOT", "Tests for WAMR Ahead-of-Time compilation"),
    WAMR_INTERP("wamr-interp", "WAMR Interpreter", "Tests for WAMR interpreter mode"),

    // Java-specific tests
    JAVA_JNI("java-jni", "Java JNI", "Java-specific tests for JNI runtime"),
    JAVA_PANAMA("java-panama", "Java Panama", "Java-specific tests for Panama runtime"),
    JAVA_INTEROP("java-interop", "Java Interop", "Java interoperability tests"),
    JAVA_MEMORY("java-memory", "Java Memory", "Java memory management tests"),
    JAVA_PERFORMANCE("java-performance", "Java Performance", "Java performance tests"),
    JAVA_CONCURRENCY("java-concurrency", "Java Concurrency", "Java concurrency tests"),

    // Custom tests
    CUSTOM("custom", "Custom Tests", "Custom test cases"),
    INTEGRATION("integration", "Integration Tests", "Integration test cases"),
    END_TO_END("end-to-end", "End-to-End Tests", "End-to-end test cases"),

    // Edge cases and stress tests
    EDGE_CASES("edge-cases", "Edge Cases", "Edge case test scenarios"),
    STRESS("stress", "Stress Tests", "Stress testing scenarios"),
    NEGATIVE("negative", "Negative Tests", "Tests that should fail or trap");

    private final String id;
    private final String displayName;
    private final String description;

    TestCategory(final String id, final String displayName, final String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the ID of this category.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the display name of this category.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this category.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets TestCategory by ID.
     *
     * @param id category ID
     * @return TestCategory or CUSTOM if not found
     */
    public static TestCategory fromId(final String id) {
        if (id == null) {
            return CUSTOM;
        }
        for (final TestCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return CUSTOM;
    }

    /**
     * Gets TestCategory from WebAssembly proposal name.
     *
     * @param proposalName proposal name
     * @return appropriate TestCategory
     */
    public static TestCategory fromProposalName(final String proposalName) {
        if (proposalName == null) {
            return CUSTOM;
        }

        final String lowerProposal = proposalName.toLowerCase().replace("-", "_").replace(" ", "_");

        return switch (lowerProposal) {
            case "bulk_memory_operations", "bulk_memory" -> SPEC_BULK_MEMORY;
            case "reference_types" -> SPEC_REFERENCE_TYPES;
            case "simd" -> SPEC_SIMD;
            case "threads", "threading" -> SPEC_THREADS;
            case "tail_call", "tail_calls" -> SPEC_TAIL_CALLS;
            case "multi_value" -> SPEC_MULTI_VALUE;
            case "multi_memory" -> SPEC_MULTI_MEMORY;
            case "memory64" -> SPEC_MEMORY64;
            case "exception_handling", "exceptions" -> SPEC_EXCEPTION_HANDLING;
            case "gc", "garbage_collection" -> SPEC_GC;
            case "component_model", "components" -> SPEC_COMPONENT_MODEL;
            default -> CUSTOM;
        };
    }

    /**
     * Checks if this category represents a WebAssembly specification test.
     *
     * @return true if this is a spec test category
     */
    public boolean isSpecTest() {
        return id.startsWith("spec-");
    }

    /**
     * Checks if this category represents a WAMR-specific test.
     *
     * @return true if this is a WAMR test category
     */
    public boolean isWamrTest() {
        return id.startsWith("wamr-");
    }

    /**
     * Checks if this category represents a Java-specific test.
     *
     * @return true if this is a Java test category
     */
    public boolean isJavaTest() {
        return id.startsWith("java-");
    }
}
