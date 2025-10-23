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
 * Complexity levels for WebAssembly test cases.
 *
 * <p>Used for test scheduling, timeout determination, and resource allocation.
 *
 * @since 1.0.0
 */
public enum TestComplexity {
    /** Simple test case with minimal resource requirements. */
    SIMPLE("simple", "Simple", "Basic test with minimal complexity", 1000, 1),

    /** Moderate test case with average resource requirements. */
    MODERATE("moderate", "Moderate", "Test with moderate complexity", 5000, 2),

    /** Complex test case with high resource requirements. */
    COMPLEX("complex", "Complex", "Test with high complexity", 30000, 4);

    private final String id;
    private final String displayName;
    private final String description;
    private final long defaultTimeoutMs;
    private final int resourceMultiplier;

    TestComplexity(
            final String id,
            final String displayName,
            final String description,
            final long defaultTimeoutMs,
            final int resourceMultiplier) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.resourceMultiplier = resourceMultiplier;
    }

    /**
     * Gets the ID of this complexity level.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the display name of this complexity level.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this complexity level.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the default timeout in milliseconds for this complexity level.
     *
     * @return the default timeout
     */
    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    /**
     * Gets the resource multiplier for this complexity level.
     *
     * @return the resource multiplier
     */
    public int getResourceMultiplier() {
        return resourceMultiplier;
    }

    /**
     * Gets TestComplexity by ID.
     *
     * @param id complexity ID
     * @return TestComplexity or SIMPLE if not found
     */
    public static TestComplexity fromId(final String id) {
        if (id == null) {
            return SIMPLE;
        }
        for (final TestComplexity complexity : values()) {
            if (complexity.id.equalsIgnoreCase(id)) {
                return complexity;
            }
        }
        return SIMPLE;
    }
}
