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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for runtime provider discovery and loading.
 * 
 * <p>These tests verify that the RuntimeFactory correctly discovers and
 * loads the appropriate WebAssembly runtime implementation based on
 * the Java version and available providers.
 * 
 * @since 1.0.0
 */
class RuntimeProviderTest extends AbstractRuntimeTest {

    @Test
    void testRuntimeCreation() {
        assertNotNull(runtime, "Runtime should be created successfully");
        assertFalse(runtime.isClosed(), "Runtime should not be closed initially");
        
        final String implementation = runtime.getImplementation();
        assertTrue(implementation.equals("JNI") || implementation.equals("Panama"),
            "Runtime implementation should be either JNI or Panama, but was: " + implementation);
    }

    @Test
    void testRuntimeVersion() {
        final String version = runtime.getVersion();
        assertNotNull(version, "Runtime version should not be null");
        assertFalse(version.trim().isEmpty(), "Runtime version should not be empty");
        
        LOGGER.info("Runtime version: " + version);
    }

    @Test
    @EnabledOnJre({JRE.JAVA_8, JRE.JAVA_11, JRE.JAVA_17, JRE.JAVA_21})
    void testJniRuntimeOnOlderJava() {
        assertEquals("JNI", runtime.getImplementation(),
            "Should use JNI implementation on Java versions < 23");
    }

    @Test
    @EnabledOnJre(JRE.JAVA_23)
    void testPanamaRuntimeOnJava23Plus() {
        // Panama should be preferred on Java 23+ if available
        final String implementation = runtime.getImplementation();
        assertTrue(implementation.equals("Panama") || implementation.equals("JNI"),
            "Should prefer Panama on Java 23+ but JNI fallback is acceptable");
        
        if (implementation.equals("JNI")) {
            LOGGER.warning("Panama runtime not available, using JNI fallback on Java 23+");
        }
    }

    @Test
    void testRuntimeFactoryReturnsWorkingRuntime() {
        // Test that we can create a runtime multiple times
        try (final WebAssemblyRuntime runtime1 = RuntimeFactory.createRuntime();
             final WebAssemblyRuntime runtime2 = RuntimeFactory.createRuntime()) {
            
            assertNotNull(runtime1);
            assertNotNull(runtime2);
            assertFalse(runtime1.isClosed());
            assertFalse(runtime2.isClosed());
            
            // Both should use the same implementation
            assertEquals(runtime1.getImplementation(), runtime2.getImplementation());
        }
    }

    @Test
    void testRuntimeCloseIdempotent() {
        assertFalse(runtime.isClosed());
        
        runtime.close();
        assertTrue(runtime.isClosed());
        
        // Closing again should be safe
        runtime.close();
        assertTrue(runtime.isClosed());
    }

    @Test
    void testClosedRuntimeThrowsException() {
        runtime.close();
        assertTrue(runtime.isClosed());
        
        assertThrows(IllegalStateException.class, () -> {
            runtime.compile(createSimpleAddModule());
        }, "Should throw IllegalStateException when using closed runtime");
    }
}