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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Parser for official WebAssembly specification test files.
 *
 * <p>Handles JSON-formatted spec test files and extracts individual test cases.
 *
 * @since 1.0.0
 */
public final class WebAssemblySpecTestParser {

    private static final Logger LOGGER = Logger.getLogger(WebAssemblySpecTestParser.class.getName());

    private final ObjectMapper objectMapper;

    /**
     * Creates a new WebAssembly spec test parser.
     */
    public WebAssemblySpecTestParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parses a WebAssembly specification test file and extracts test cases.
     *
     * @param specTestFile path to the spec test JSON file
     * @return list of parsed test cases
     * @throws TestSuiteException if parsing fails
     */
    public List<WebAssemblyTestCase> parseSpecTestFile(final Path specTestFile)
            throws TestSuiteException {
        if (specTestFile == null || !Files.exists(specTestFile)) {
            throw new TestSuiteException("Spec test file does not exist: " + specTestFile);
        }

        try {
            LOGGER.fine("Parsing spec test file: " + specTestFile);

            final String content = Files.readString(specTestFile);
            final JsonNode rootNode = objectMapper.readTree(content);

            if (!rootNode.has("commands")) {
                throw new TestSuiteException("Invalid spec test file format: missing 'commands' field");
            }

            final JsonNode commandsNode = rootNode.get("commands");
            if (!commandsNode.isArray()) {
                throw new TestSuiteException("Invalid spec test file format: 'commands' is not an array");
            }

            final List<WebAssemblyTestCase> testCases = new ArrayList<>();
            final String baseTestId = generateBaseTestId(specTestFile);

            int commandIndex = 0;
            for (final JsonNode commandNode : commandsNode) {
                try {
                    final WebAssemblyTestCase testCase =
                            parseSpecCommand(commandNode, baseTestId, commandIndex, specTestFile);
                    if (testCase != null) {
                        testCases.add(testCase);
                    }
                } catch (final Exception e) {
                    LOGGER.warning(
                            "Failed to parse command "
                                    + commandIndex
                                    + " in "
                                    + specTestFile
                                    + ": "
                                    + e.getMessage());
                }
                commandIndex++;
            }

            LOGGER.fine("Parsed " + testCases.size() + " test cases from " + specTestFile);
            return testCases;

        } catch (final IOException e) {
            throw new TestSuiteException("Failed to read spec test file: " + specTestFile, e);
        } catch (final Exception e) {
            throw new TestSuiteException("Failed to parse spec test file: " + specTestFile, e);
        }
    }

    private WebAssemblyTestCase parseSpecCommand(
            final JsonNode commandNode,
            final String baseTestId,
            final int commandIndex,
            final Path specTestFile)
            throws TestSuiteException {

        if (!commandNode.has("type")) {
            return null; // Skip commands without type
        }

        final String commandType = commandNode.get("type").asText();
        final String testId = baseTestId + "_" + commandIndex + "_" + commandType;

        switch (commandType) {
            case "module":
                return parseModuleCommand(commandNode, testId, specTestFile);

            case "assert_return":
                return parseAssertReturnCommand(commandNode, testId, specTestFile);

            case "assert_trap":
                return parseAssertTrapCommand(commandNode, testId, specTestFile);

            case "assert_invalid":
                return parseAssertInvalidCommand(commandNode, testId, specTestFile);

            case "assert_malformed":
                return parseAssertMalformedCommand(commandNode, testId, specTestFile);

            case "assert_uninstantiable":
                return parseAssertUninstantiableCommand(commandNode, testId, specTestFile);

            case "invoke":
                return parseInvokeCommand(commandNode, testId, specTestFile);

            case "register":
                return parseRegisterCommand(commandNode, testId, specTestFile);

            default:
                LOGGER.fine("Skipping unsupported command type: " + commandType);
                return null;
        }
    }

    private WebAssemblyTestCase parseModuleCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        final WebAssemblyTestCase.Builder builder = WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("module_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("WebAssembly module instantiation test")
                .expected(TestExpectedResult.PASS)
                .tags(List.of("spec", "module", "instantiation"));

        if (commandNode.has("filename")) {
            builder.metadata("module.filename", commandNode.get("filename").asText());
        }
        if (commandNode.has("name")) {
            builder.metadata("module.name", commandNode.get("name").asText());
        }

        return builder.build();
    }

    private WebAssemblyTestCase parseAssertReturnCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        final WebAssemblyTestCase.Builder builder = WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("assert_return_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Function invocation should return expected value")
                .expected(TestExpectedResult.PASS)
                .tags(List.of("spec", "assert_return", "function_call"));

        // Extract action details (function name, arguments)
        if (commandNode.has("action")) {
            final JsonNode action = commandNode.get("action");
            builder.metadata("action.type", action.has("type") ? action.get("type").asText() : "invoke");
            builder.metadata("action.field", action.has("field") ? action.get("field").asText() : "");
            if (action.has("args")) {
                builder.metadata("action.args", action.get("args").toString());
            }
        }

        // Extract expected return values
        if (commandNode.has("expected")) {
            builder.metadata("expected.values", commandNode.get("expected").toString());
        }

        return builder.build();
    }

    private WebAssemblyTestCase parseAssertTrapCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        String trapMessage = "unknown trap";
        if (commandNode.has("text")) {
            trapMessage = commandNode.get("text").asText();
        }

        final WebAssemblyTestCase.Builder builder = WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("assert_trap_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Function invocation should trap: " + trapMessage)
                .expected(TestExpectedResult.TRAP)
                .tags(List.of("spec", "assert_trap", "trap", "negative"));

        builder.metadata("trap.message", trapMessage);
        if (commandNode.has("action")) {
            final JsonNode action = commandNode.get("action");
            builder.metadata("action.type", action.has("type") ? action.get("type").asText() : "invoke");
            builder.metadata("action.field", action.has("field") ? action.get("field").asText() : "");
            if (action.has("args")) {
                builder.metadata("action.args", action.get("args").toString());
            }
        }

        return builder.build();
    }

    private WebAssemblyTestCase parseAssertInvalidCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        String errorMessage = "invalid module";
        if (commandNode.has("text")) {
            errorMessage = commandNode.get("text").asText();
        }

        return WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("assert_invalid_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Module should be invalid: " + errorMessage)
                .expected(TestExpectedResult.FAIL)
                .tags(List.of("spec", "assert_invalid", "validation", "negative"))
                .metadata("error.message", errorMessage)
                .build();
    }

    private WebAssemblyTestCase parseAssertMalformedCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        String errorMessage = "malformed module";
        if (commandNode.has("text")) {
            errorMessage = commandNode.get("text").asText();
        }

        return WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("assert_malformed_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Module should be malformed: " + errorMessage)
                .expected(TestExpectedResult.FAIL)
                .tags(List.of("spec", "assert_malformed", "parsing", "negative"))
                .metadata("error.message", errorMessage)
                .build();
    }

    private WebAssemblyTestCase parseAssertUninstantiableCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        String errorMessage = "uninstantiable module";
        if (commandNode.has("text")) {
            errorMessage = commandNode.get("text").asText();
        }

        return WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("assert_uninstantiable_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Module should be uninstantiable: " + errorMessage)
                .expected(TestExpectedResult.FAIL)
                .tags(List.of("spec", "assert_uninstantiable", "instantiation", "negative"))
                .metadata("error.message", errorMessage)
                .build();
    }

    private WebAssemblyTestCase parseInvokeCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        String functionName = "unknown";
        if (commandNode.has("field")) {
            functionName = commandNode.get("field").asText();
        }

        final WebAssemblyTestCase.Builder builder = WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("invoke_" + functionName + "_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Invoke function: " + functionName)
                .expected(TestExpectedResult.PASS)
                .tags(List.of("spec", "invoke", "function_call"));

        builder.metadata("action.type", "invoke");
        builder.metadata("action.field", functionName);
        if (commandNode.has("args")) {
            builder.metadata("action.args", commandNode.get("args").toString());
        }

        return builder.build();
    }

    private WebAssemblyTestCase parseRegisterCommand(
            final JsonNode commandNode, final String testId, final Path specTestFile) {
        String moduleName = "unknown";
        if (commandNode.has("name")) {
            moduleName = commandNode.get("name").asText();
        }

        return WebAssemblyTestCase.builder()
                .testId(testId)
                .testName("register_" + moduleName + "_" + testId.substring(testId.lastIndexOf('_') + 1))
                .category(TestCategory.SPEC_CORE)
                .testFilePath(specTestFile)
                .description("Register module: " + moduleName)
                .expected(TestExpectedResult.PASS)
                .tags(List.of("spec", "register", "module"))
                .metadata("module.name", moduleName)
                .build();
    }

    private String generateBaseTestId(final Path specTestFile) {
        final String fileName = specTestFile.getFileName().toString();
        return fileName.replaceAll("\\.json$", "").replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
