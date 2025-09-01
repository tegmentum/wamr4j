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

package ai.tegmentum.wamr4j.test;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import java.util.logging.Logger;

/**
 * Abstract base class for WebAssembly runtime tests.
 * 
 * <p>This class provides common test infrastructure for testing WebAssembly
 * operations across different runtime implementations (JNI and Panama).
 * It handles runtime lifecycle management and provides utilities for
 * loading test WebAssembly modules.
 * 
 * @since 1.0.0
 */
public abstract class AbstractRuntimeTest {

    protected static final Logger LOGGER = Logger.getLogger(AbstractRuntimeTest.class.getName());
    
    protected WebAssemblyRuntime runtime;

    @BeforeEach
    void setUp(final TestInfo testInfo) {
        LOGGER.info("Starting test: " + testInfo.getDisplayName());
        
        try {
            runtime = RuntimeFactory.createRuntime();
            LOGGER.info("Created runtime: " + runtime.getImplementation());
        } catch (final Exception e) {
            LOGGER.severe("Failed to create runtime for test: " + e.getMessage());
            throw new RuntimeException("Test setup failed", e);
        }
    }

    @AfterEach
    void tearDown(final TestInfo testInfo) {
        if (runtime != null && !runtime.isClosed()) {
            try {
                runtime.close();
                LOGGER.info("Closed runtime for test: " + testInfo.getDisplayName());
            } catch (final Exception e) {
                LOGGER.warning("Error closing runtime: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the current runtime implementation name for test verification.
     * 
     * @return the runtime implementation name (JNI or Panama)
     */
    protected String getRuntimeImplementation() {
        return runtime != null ? runtime.getImplementation() : "unknown";
    }

    /**
     * Loads a test WebAssembly module from resources.
     * 
     * @param resourcePath the path to the WASM file in test resources
     * @return the WebAssembly module bytes
     * @throws RuntimeException if the resource cannot be loaded
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

    /**
     * Creates a simple test WebAssembly module that adds two integers.
     * 
     * @return WebAssembly bytecode for a simple add function
     */
    protected byte[] createSimpleAddModule() {
        // WebAssembly binary format for: (module (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add))
        return new byte[] {
            0x00, 0x61, 0x73, 0x6d, // WASM magic number
            0x01, 0x00, 0x00, 0x00, // WASM version
            0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f, // Type section: (i32, i32) -> i32
            0x03, 0x02, 0x01, 0x00, // Function section: 1 function of type 0
            0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00, // Export section: export "add" as function 0
            0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b // Code section: local.get 0, local.get 1, i32.add
        };
    }
}