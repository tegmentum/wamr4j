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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single WebAssembly test case with comprehensive metadata.
 *
 * @since 1.0.0
 */
public final class WebAssemblyTestCase {

    private final String testId;
    private final String testName;
    private final TestCategory category;
    private final Path testFilePath;
    private final String description;
    private final TestExpectedResult expected;
    private final List<String> tags;
    private final Map<String, Object> metadata;

    private WebAssemblyTestCase(final Builder builder) {
        this.testId = builder.testId;
        this.testName = builder.testName;
        this.category = builder.category;
        this.testFilePath = builder.testFilePath;
        this.description = builder.description;
        this.expected = builder.expected;
        this.tags = List.copyOf(builder.tags);
        this.metadata = Map.copyOf(builder.metadata);
    }

    /**
     * Gets the test ID.
     *
     * @return the test ID
     */
    public String getTestId() {
        return testId;
    }

    /**
     * Gets the test name.
     *
     * @return the test name
     */
    public String getTestName() {
        return testName;
    }

    /**
     * Gets the test category.
     *
     * @return the test category
     */
    public TestCategory getCategory() {
        return category;
    }

    /**
     * Gets the test file path.
     *
     * @return the test file path
     */
    public Path getTestFilePath() {
        return testFilePath;
    }

    /**
     * Gets the test description.
     *
     * @return the test description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the expected result.
     *
     * @return the expected result
     */
    public TestExpectedResult getExpected() {
        return expected;
    }

    /**
     * Gets the test tags.
     *
     * @return the test tags
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Gets the test metadata.
     *
     * @return the test metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Checks if this test case has the specified tag.
     *
     * @param tag tag to check
     * @return true if tag exists
     */
    public boolean hasTag(final String tag) {
        return tags.contains(tag);
    }

    /**
     * Gets metadata value by key.
     *
     * @param key metadata key
     * @param <T> expected value type
     * @return metadata value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(final String key) {
        return (T) metadata.get(key);
    }

    /**
     * Creates a new builder for WebAssemblyTestCase.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WebAssemblyTestCase that = (WebAssemblyTestCase) o;
        return Objects.equals(testId, that.testId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testId);
    }

    @Override
    public String toString() {
        return "WebAssemblyTestCase{"
                + "testId='" + testId + '\''
                + ", testName='" + testName + '\''
                + ", category=" + category
                + ", expected=" + expected
                + ", tags=" + tags.size()
                + '}';
    }

    /**
     * Builder for WebAssemblyTestCase.
     *
     * @since 1.0.0
     */
    public static final class Builder {
        private String testId;
        private String testName;
        private TestCategory category = TestCategory.CUSTOM;
        private Path testFilePath;
        private String description = "";
        private TestExpectedResult expected = TestExpectedResult.PASS;
        private List<String> tags = List.of();
        private Map<String, Object> metadata = new HashMap<>();

        /**
         * Sets the test ID.
         *
         * @param testId test ID
         * @return this builder
         */
        public Builder testId(final String testId) {
            if (testId == null || testId.trim().isEmpty()) {
                throw new IllegalArgumentException("Test ID cannot be null or empty");
            }
            this.testId = testId.trim();
            return this;
        }

        /**
         * Sets the test name.
         *
         * @param testName test name
         * @return this builder
         */
        public Builder testName(final String testName) {
            if (testName == null || testName.trim().isEmpty()) {
                throw new IllegalArgumentException("Test name cannot be null or empty");
            }
            this.testName = testName.trim();
            return this;
        }

        /**
         * Sets the test category.
         *
         * @param category test category
         * @return this builder
         */
        public Builder category(final TestCategory category) {
            if (category == null) {
                throw new IllegalArgumentException("Category cannot be null");
            }
            this.category = category;
            return this;
        }

        /**
         * Sets the test file path.
         *
         * @param testFilePath test file path
         * @return this builder
         */
        public Builder testFilePath(final Path testFilePath) {
            this.testFilePath = testFilePath;
            return this;
        }

        /**
         * Sets the test description.
         *
         * @param description test description
         * @return this builder
         */
        public Builder description(final String description) {
            this.description = description != null ? description : "";
            return this;
        }

        /**
         * Sets the expected test result.
         *
         * @param expected expected result
         * @return this builder
         */
        public Builder expected(final TestExpectedResult expected) {
            if (expected == null) {
                throw new IllegalArgumentException("Expected result cannot be null");
            }
            this.expected = expected;
            return this;
        }

        /**
         * Sets the test tags.
         *
         * @param tags test tags
         * @return this builder
         */
        public Builder tags(final List<String> tags) {
            this.tags = tags != null ? List.copyOf(tags) : List.of();
            return this;
        }

        /**
         * Sets the test metadata.
         *
         * @param metadata test metadata
         * @return this builder
         */
        public Builder metadata(final Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        /**
         * Adds a single metadata entry.
         *
         * @param key metadata key
         * @param value metadata value
         * @return this builder
         */
        public Builder metadata(final String key, final Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Builds the WebAssembly test case.
         *
         * @return WebAssembly test case
         */
        public WebAssemblyTestCase build() {
            if (testId == null || testId.trim().isEmpty()) {
                throw new IllegalStateException("Test ID must be set");
            }
            if (testName == null || testName.trim().isEmpty()) {
                throw new IllegalStateException("Test name must be set");
            }

            return new WebAssemblyTestCase(this);
        }
    }
}
