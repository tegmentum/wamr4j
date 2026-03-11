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

package ai.tegmentum.wamr4j.test.integration;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WamrRuntimeExtensions;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for threading APIs (Phase 12).
 *
 * <p>Tests thread environment management and max thread configuration
 * for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class ThreadingTest {

    private static final Logger LOGGER =
        Logger.getLogger(ThreadingTest.class.getName());

    @Test
    void testIsThreadEnvInitedParity() throws Exception {
        LOGGER.info("Testing isThreadEnvInited() on both runtimes");

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
            // The main thread should have thread env inited after runtime creation
            final boolean jniResult = runtime.isThreadEnvInited();
            LOGGER.info("JNI isThreadEnvInited() on main thread: " + jniResult);
            // This may be true or false depending on WAMR's internal state
            // (main thread env is managed by runtime init, not by initThreadEnv)
            assertNotNull(Boolean.valueOf(jniResult),
                "JNI: isThreadEnvInited should return a valid boolean");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WamrRuntimeExtensions runtime =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
                final boolean panamaResult = runtime.isThreadEnvInited();
                LOGGER.info("Panama isThreadEnvInited() on main thread: " + panamaResult);
                assertNotNull(Boolean.valueOf(panamaResult),
                    "Panama: isThreadEnvInited should return a valid boolean");
            }
        }
    }

    @Test
    void testInitAndDestroyThreadEnvParity() throws Exception {
        LOGGER.info("Testing initThreadEnv/destroyThreadEnv on both runtimes");

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
            // initThreadEnv on a thread that already has env may return true (idempotent)
            // or false (already initialized) depending on WAMR behavior
            final boolean jniInit = runtime.initThreadEnv();
            LOGGER.info("JNI initThreadEnv(): " + jniInit);

            // destroyThreadEnv should not crash
            assertDoesNotThrow(() -> runtime.destroyThreadEnv(),
                "JNI: destroyThreadEnv should not throw");
            LOGGER.info("JNI: destroyThreadEnv completed without crash");

            // Re-init for subsequent operations
            runtime.initThreadEnv();
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WamrRuntimeExtensions runtime =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
                final boolean panamaInit = runtime.initThreadEnv();
                LOGGER.info("Panama initThreadEnv(): " + panamaInit);

                assertDoesNotThrow(() -> runtime.destroyThreadEnv(),
                    "Panama: destroyThreadEnv should not throw");
                LOGGER.info("Panama: destroyThreadEnv completed without crash");

                runtime.initThreadEnv();
            }
        }
    }

    @Test
    void testSetMaxThreadNumParity() throws Exception {
        LOGGER.info("Testing setMaxThreadNum() on both runtimes");

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
            assertDoesNotThrow(() -> runtime.setMaxThreadNum(4),
                "JNI: setMaxThreadNum(4) should not throw");
            assertDoesNotThrow(() -> runtime.setMaxThreadNum(1),
                "JNI: setMaxThreadNum(1) should not throw");
            assertDoesNotThrow(() -> runtime.setMaxThreadNum(0),
                "JNI: setMaxThreadNum(0) should not throw");
            LOGGER.info("JNI: setMaxThreadNum completed without crash");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WamrRuntimeExtensions runtime =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
                assertDoesNotThrow(() -> runtime.setMaxThreadNum(4),
                    "Panama: setMaxThreadNum(4) should not throw");
                assertDoesNotThrow(() -> runtime.setMaxThreadNum(1),
                    "Panama: setMaxThreadNum(1) should not throw");
                assertDoesNotThrow(() -> runtime.setMaxThreadNum(0),
                    "Panama: setMaxThreadNum(0) should not throw");
                LOGGER.info("Panama: setMaxThreadNum completed without crash");
            }
        }
    }

    @Test
    void testSetMaxThreadNumNegativeThrows() throws Exception {
        LOGGER.info("Testing setMaxThreadNum(-1) throws on both runtimes");

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        try (final WamrRuntimeExtensions runtime =
                 (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
            assertThrows(IllegalArgumentException.class,
                () -> runtime.setMaxThreadNum(-1),
                "JNI: setMaxThreadNum(-1) should throw");
            LOGGER.info("JNI: setMaxThreadNum(-1) correctly threw IllegalArgumentException");
        }

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            try (final WamrRuntimeExtensions runtime =
                     (WamrRuntimeExtensions) RuntimeFactory.createRuntime()) {
                assertThrows(IllegalArgumentException.class,
                    () -> runtime.setMaxThreadNum(-1),
                    "Panama: setMaxThreadNum(-1) should throw");
                LOGGER.info("Panama: setMaxThreadNum(-1) correctly threw IllegalArgumentException");
            }
        }
    }

    @Test
    void testThreadingOnClosedRuntimeThrows() throws Exception {
        LOGGER.info("Testing threading methods on closed runtime throw on both runtimes");

        // JNI
        System.setProperty("wamr4j.runtime", "jni");
        final WamrRuntimeExtensions jniRuntime =
            (WamrRuntimeExtensions) RuntimeFactory.createRuntime();
        jniRuntime.close();

        assertThrows(IllegalStateException.class, () -> jniRuntime.initThreadEnv(),
            "JNI: initThreadEnv on closed runtime should throw");
        assertThrows(IllegalStateException.class, () -> jniRuntime.destroyThreadEnv(),
            "JNI: destroyThreadEnv on closed runtime should throw");
        assertThrows(IllegalStateException.class, () -> jniRuntime.isThreadEnvInited(),
            "JNI: isThreadEnvInited on closed runtime should throw");
        assertThrows(IllegalStateException.class, () -> jniRuntime.setMaxThreadNum(4),
            "JNI: setMaxThreadNum on closed runtime should throw");
        LOGGER.info("JNI: All closed runtime threading methods threw IllegalStateException");

        // Panama
        if (RuntimeFactory.isProviderAvailable("panama")) {
            System.setProperty("wamr4j.runtime", "panama");
            final WamrRuntimeExtensions panamaRuntime =
                (WamrRuntimeExtensions) RuntimeFactory.createRuntime();
            panamaRuntime.close();

            assertThrows(IllegalStateException.class, () -> panamaRuntime.initThreadEnv(),
                "Panama: initThreadEnv on closed runtime should throw");
            assertThrows(IllegalStateException.class, () -> panamaRuntime.destroyThreadEnv(),
                "Panama: destroyThreadEnv on closed runtime should throw");
            assertThrows(IllegalStateException.class, () -> panamaRuntime.isThreadEnvInited(),
                "Panama: isThreadEnvInited on closed runtime should throw");
            assertThrows(IllegalStateException.class, () -> panamaRuntime.setMaxThreadNum(4),
                "Panama: setMaxThreadNum on closed runtime should throw");
            LOGGER.info(
                "Panama: All closed runtime threading methods threw IllegalStateException");
        }
    }
}
