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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import ai.tegmentum.wamr4j.exception.RuntimeException;
import ai.tegmentum.wamr4j.spi.RuntimeProvider;

/**
 * Factory for creating WebAssembly runtime instances.
 *
 * <p>This factory provides the main entry point for creating WebAssembly runtimes. It automatically
 * discovers available runtime implementations and selects the most appropriate one based on the
 * current environment.
 *
 * <p>Runtime selection follows this priority order:
 *
 * <ol>
 *   <li>Explicit runtime specified via system property
 *   <li>Highest priority available runtime provider
 *   <li>Fallback to any available runtime
 * </ol>
 *
 * <p>System properties for runtime control:
 *
 * <ul>
 *   <li>{@code wamr4j.runtime} - Force specific runtime ("jni", "panama")
 *   <li>{@code wamr4j.runtime.debug} - Enable debug logging
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Automatic runtime selection
 * try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
 *     // Use runtime...
 * }
 *
 * // Force specific runtime
 * System.setProperty("wamr4j.runtime", "jni");
 * try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime()) {
 *     // Use JNI runtime...
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class RuntimeFactory {

    private static final Logger LOGGER = Logger.getLogger(RuntimeFactory.class.getName());

    private static final String RUNTIME_PROPERTY = "wamr4j.runtime";
    private static final String DEBUG_PROPERTY = "wamr4j.runtime.debug";

    // Cached providers list to avoid repeated ServiceLoader calls
    private static volatile List<RuntimeProvider> cachedProviders;

    // Current Java major version
    private static final int JAVA_VERSION = getJavaMajorVersion();

    // Private constructor to prevent instantiation
    private RuntimeFactory() {
        throw new UnsupportedOperationException("RuntimeFactory cannot be instantiated");
    }

    /**
     * Creates a new WebAssembly runtime using automatic provider selection.
     *
     * <p>This method discovers available runtime providers and selects the most appropriate one
     * based on system properties, provider priority, and environment compatibility.
     *
     * @return a new WebAssembly runtime instance, never null
     * @throws RuntimeException if no suitable runtime provider is available
     * @throws UnsupportedOperationException if WebAssembly is not supported in the current
     *     environment
     */
    public static WebAssemblyRuntime createRuntime() throws RuntimeException {
        final List<RuntimeProvider> providers = getAvailableProviders();

        if (providers.isEmpty()) {
            throw new RuntimeException(
                    "No WebAssembly runtime providers found. Ensure wamr4j-jni or wamr4j-panama "
                            + "is on the classpath.");
        }

        logDebug("Found {} runtime provider(s)", providers.size());

        // Check for explicit runtime specification
        final String requestedRuntime = System.getProperty(RUNTIME_PROPERTY);
        if (requestedRuntime != null) {
            return createSpecificRuntime(providers, requestedRuntime);
        }

        // Use highest priority available provider
        final RuntimeProvider provider = providers.get(0); // Already sorted by priority
        logInfo(
                "Selected runtime provider: {} (priority: {})",
                provider.getName(),
                provider.getPriority());

        try {
            return provider.createRuntime();
        } catch (final Exception e) {
            throw new RuntimeException(
                    "Failed to create runtime using provider: " + provider.getName(), e);
        }
    }

    /**
     * Creates a new WebAssembly runtime using the specified provider name.
     *
     * <p>This method allows explicit selection of a runtime provider by name. Provider names are
     * case-insensitive and typically include "jni" and "panama".
     *
     * @param providerName the name of the runtime provider to use
     * @return a new WebAssembly runtime instance, never null
     * @throws RuntimeException if the specified provider is not available
     * @throws IllegalArgumentException if providerName is null or empty
     */
    public static WebAssemblyRuntime createRuntime(final String providerName)
            throws RuntimeException {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }

        final List<RuntimeProvider> providers = getAvailableProviders();
        return createSpecificRuntime(providers, providerName.trim());
    }

    /**
     * Returns the names of all available runtime providers.
     *
     * <p>This method can be used to discover what runtime implementations are available in the
     * current environment.
     *
     * @return a list of provider names, never null but may be empty
     */
    public static List<String> getAvailableRuntimeNames() {
        final List<RuntimeProvider> providers = getAvailableProviders();
        final List<String> names = new ArrayList<>(providers.size());
        for (final RuntimeProvider provider : providers) {
            names.add(provider.getName());
        }
        return names;
    }

    /**
     * Returns detailed information about all discovered runtime providers.
     *
     * <p>This method provides diagnostic information about all providers, including those that are
     * not available in the current environment.
     *
     * @return a list of provider information strings
     */
    public static List<String> getProviderInformation() {
        final List<String> information = new ArrayList<>();

        for (final RuntimeProvider provider : discoverAllProviders()) {
            final StringBuilder info = new StringBuilder();
            info.append("Provider: ").append(provider.getName());
            info.append(", Priority: ").append(provider.getPriority());
            info.append(", Available: ").append(provider.isAvailable());
            info.append(", Min Java: ").append(provider.getMinimumJavaVersion());

            final String description = provider.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                info.append(", Description: ").append(description.trim());
            }

            information.add(info.toString());
        }

        return information;
    }

    private static List<RuntimeProvider> getAvailableProviders() {
        List<RuntimeProvider> providers = cachedProviders;
        if (providers == null) {
            synchronized (RuntimeFactory.class) {
                providers = cachedProviders;
                if (providers == null) {
                    providers = loadAndFilterProviders();
                    cachedProviders = providers;
                }
            }
        }
        return providers;
    }

    private static List<RuntimeProvider> loadAndFilterProviders() {
        final List<RuntimeProvider> availableProviders = new ArrayList<>();

        for (final RuntimeProvider provider : discoverAllProviders()) {
            logDebug(
                    "Evaluating provider: {} (priority: {}, min Java: {})",
                    provider.getName(),
                    provider.getPriority(),
                    provider.getMinimumJavaVersion());

            // Check Java version compatibility
            if (provider.getMinimumJavaVersion() > JAVA_VERSION) {
                logDebug(
                        "Skipping provider {} - requires Java {}, current is Java {}",
                        provider.getName(),
                        provider.getMinimumJavaVersion(),
                        JAVA_VERSION);
                continue;
            }

            // Check availability
            try {
                if (provider.isAvailable()) {
                    availableProviders.add(provider);
                    logDebug("Provider {} is available", provider.getName());
                } else {
                    logDebug("Provider {} is not available", provider.getName());
                }
            } catch (final Exception e) {
                logDebug(
                        "Provider {} availability check failed: {}",
                        provider.getName(),
                        e.getMessage());
            }
        }

        // Sort by priority (highest first)
        availableProviders.sort(Comparator.comparingInt(RuntimeProvider::getPriority).reversed());

        return Collections.unmodifiableList(availableProviders);
    }

    private static List<RuntimeProvider> discoverAllProviders() {
        final List<RuntimeProvider> allProviders = new ArrayList<>();

        try {
            final ServiceLoader<RuntimeProvider> loader = ServiceLoader.load(RuntimeProvider.class);
            for (final RuntimeProvider provider : loader) {
                allProviders.add(provider);
            }
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "Failed to discover runtime providers", e);
        }

        return allProviders;
    }

    private static WebAssemblyRuntime createSpecificRuntime(
            final List<RuntimeProvider> providers, final String requestedName)
            throws RuntimeException {

        for (final RuntimeProvider provider : providers) {
            if (provider.getName().equalsIgnoreCase(requestedName)) {
                logInfo("Creating requested runtime: {}", provider.getName());
                try {
                    return provider.createRuntime();
                } catch (final Exception e) {
                    throw new RuntimeException(
                            "Failed to create requested runtime: " + requestedName, e);
                }
            }
        }

        throw new RuntimeException(
                "Requested runtime provider '"
                        + requestedName
                        + "' is not available. "
                        + "Available providers: "
                        + getAvailableRuntimeNames());
    }

    private static int getJavaMajorVersion() {
        try {
            final String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                // Java 8 and below use 1.x format
                final int legacyVersionIndex = 3;
                return Integer.parseInt(version.substring(2, legacyVersionIndex));
            } else {
                // Java 9+ use x.y.z format
                final int dotIndex = version.indexOf('.');
                final int endIndex;
                if (dotIndex > 0) {
                    endIndex = dotIndex;
                } else {
                    endIndex = version.length();
                }
                return Integer.parseInt(version.substring(0, endIndex));
            }
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "Failed to determine Java version, assuming Java 8", e);
            return 8;
        }
    }

    private static void logInfo(final String message, final Object... args) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(formatMessage(message, args));
        }
    }

    private static void logDebug(final String message, final Object... args) {
        if (isDebugEnabled() && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(formatMessage(message, args));
        }
    }

    private static boolean isDebugEnabled() {
        return Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
    }

    private static String formatMessage(final String message, final Object... args) {
        if (args.length == 0) {
            return message;
        }

        String formatted = message;
        for (final Object arg : args) {
            formatted = formatted.replaceFirst("\\{\\}", String.valueOf(arg));
        }
        return formatted;
    }
}
