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

/**
 * Configuration for WASI (WebAssembly System Interface) module parameters.
 *
 * <p>WASI allows WebAssembly modules to interact with the host operating system
 * through a standardized interface. This configuration must be applied to a module
 * before instantiation using {@link WebAssemblyModule#configureWasi(WasiConfiguration)}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WasiConfiguration config = new WasiConfiguration()
 *     .setArgs("program", "--flag", "value")
 *     .setEnvVars("HOME=/tmp", "PATH=/usr/bin")
 *     .setPreopens("/tmp");
 * module.configureWasi(config);
 * WebAssemblyInstance instance = module.instantiate();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class WasiConfiguration {

    private String[] args = new String[0];
    private String[] envVars = new String[0];
    private String[] preopens = new String[0];
    private String[] mappedDirs = new String[0];
    private String[] addrPool = new String[0];
    private String[] nsLookupPool = new String[0];

    /**
     * Sets the command-line arguments for the WASI module.
     *
     * <p>The first argument is conventionally the program name.
     *
     * @param args the command-line arguments
     * @return this configuration for chaining
     */
    public WasiConfiguration setArgs(final String... args) {
        this.args = args != null ? args.clone() : new String[0];
        return this;
    }

    /**
     * Sets the environment variables for the WASI module.
     *
     * <p>Each entry should be in "KEY=VALUE" format.
     *
     * @param envVars the environment variables
     * @return this configuration for chaining
     */
    public WasiConfiguration setEnvVars(final String... envVars) {
        this.envVars = envVars != null ? envVars.clone() : new String[0];
        return this;
    }

    /**
     * Sets the preopened directories for the WASI module.
     *
     * <p>These are real host paths that will be accessible to the WASI module.
     *
     * @param preopens the host paths to preopen
     * @return this configuration for chaining
     */
    public WasiConfiguration setPreopens(final String... preopens) {
        this.preopens = preopens != null ? preopens.clone() : new String[0];
        return this;
    }

    /**
     * Sets the mapped directories for the WASI module.
     *
     * <p>Each entry should be in "guest-path::host-path" format.
     *
     * @param mappedDirs the mapped directory entries
     * @return this configuration for chaining
     */
    public WasiConfiguration setMappedDirs(final String... mappedDirs) {
        this.mappedDirs = mappedDirs != null ? mappedDirs.clone() : new String[0];
        return this;
    }

    /**
     * Sets the address pool for WASI network operations.
     *
     * <p>Restricts which IP addresses the WASI module can access.
     *
     * @param addrPool the allowed IP addresses
     * @return this configuration for chaining
     */
    public WasiConfiguration setAddrPool(final String... addrPool) {
        this.addrPool = addrPool != null ? addrPool.clone() : new String[0];
        return this;
    }

    /**
     * Sets the name server lookup pool for WASI DNS resolution.
     *
     * @param nsLookupPool the allowed name servers
     * @return this configuration for chaining
     */
    public WasiConfiguration setNsLookupPool(final String... nsLookupPool) {
        this.nsLookupPool = nsLookupPool != null ? nsLookupPool.clone() : new String[0];
        return this;
    }

    /**
     * Returns the command-line arguments.
     *
     * @return the args array, never null
     */
    public String[] getArgs() {
        return args.clone();
    }

    /**
     * Returns the environment variables.
     *
     * @return the env vars array, never null
     */
    public String[] getEnvVars() {
        return envVars.clone();
    }

    /**
     * Returns the preopened directories.
     *
     * @return the preopens array, never null
     */
    public String[] getPreopens() {
        return preopens.clone();
    }

    /**
     * Returns the mapped directories.
     *
     * @return the mapped dirs array, never null
     */
    public String[] getMappedDirs() {
        return mappedDirs.clone();
    }

    /**
     * Returns the address pool.
     *
     * @return the addr pool array, never null
     */
    public String[] getAddrPool() {
        return addrPool.clone();
    }

    /**
     * Returns the name server lookup pool.
     *
     * @return the ns lookup pool array, never null
     */
    public String[] getNsLookupPool() {
        return nsLookupPool.clone();
    }
}
