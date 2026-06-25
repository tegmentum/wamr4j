/*
 * Copyright (c) 2024 Tegmentum AI, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.wamr4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.tegmentum.wamr4j.spi.RuntimeProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;

class RuntimeFactoryTest {

    @Test
    void skipsProviderCompiledForNewerJavaAndKeepsTheRest() {
        // A provider compiled for a newer Java release surfaces as a raw
        // UnsupportedClassVersionError thrown from ServiceLoader iteration.
        List<RuntimeProvider> result = RuntimeFactory.collectProviders(scripted(
                stub("JNI"),
                new UnsupportedClassVersionError("Panama compiled for class file version 66.0"),
                stub("Other")));

        assertThat(names(result)).containsExactly("JNI", "Other");
    }

    @Test
    void skipsProviderWhoseConstructionFails() {
        // A provider whose construction fails surfaces as a ServiceConfigurationError.
        List<RuntimeProvider> result = RuntimeFactory.collectProviders(scripted(
                stub("JNI"),
                new ServiceConfigurationError("provider could not be instantiated")));

        assertThat(names(result)).containsExactly("JNI");
    }

    @Test
    void returnsEmptyWhenEveryProviderFailsWithoutThrowing() {
        assertThatCode(() -> {
            List<RuntimeProvider> result = RuntimeFactory.collectProviders(scripted(
                    new UnsupportedClassVersionError("too new")));
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    private static List<String> names(List<RuntimeProvider> providers) {
        List<String> names = new ArrayList<>();
        for (RuntimeProvider p : providers) {
            names.add(p.getName());
        }
        return names;
    }

    private static Iterator<RuntimeProvider> scripted(Object... entries) {
        return new ScriptedIterator(Arrays.asList(entries));
    }

    /**
     * Mimics {@link java.util.ServiceLoader}'s iterator: a poisoned entry (a
     * {@link Throwable}) is consumed and thrown from {@code hasNext()}, so the next
     * call advances to the following entry — exactly the behaviour the production
     * code relies on to keep iterating after a failed provider.
     */
    private static final class ScriptedIterator implements Iterator<RuntimeProvider> {

        private final List<Object> script;
        private int pos;

        ScriptedIterator(List<Object> script) {
            this.script = script;
        }

        @Override
        public boolean hasNext() {
            if (pos >= script.size()) {
                return false;
            }
            Object current = script.get(pos);
            if (current instanceof Throwable) {
                pos++; // consume the offending entry before throwing
                if (current instanceof Error) {
                    throw (Error) current;
                }
                throw (RuntimeException) current;
            }
            return true;
        }

        @Override
        public RuntimeProvider next() {
            return (RuntimeProvider) script.get(pos++);
        }
    }

    private static RuntimeProvider stub(String name) {
        return new RuntimeProvider() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return 100;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public ai.tegmentum.wamr4j.WebAssemblyRuntime createRuntime() {
                return null;
            }
        };
    }
}
