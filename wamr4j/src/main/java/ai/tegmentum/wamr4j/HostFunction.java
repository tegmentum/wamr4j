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
 * Represents a host function that can be imported by a WebAssembly module.
 *
 * <p>Host functions allow Java code to provide callable functions to WebAssembly modules.
 * When a WASM module declares an import, the host can satisfy it by registering a
 * {@code HostFunction} with a matching signature.
 *
 * <p>The callback receives arguments as boxed Java objects matching the WASM types:
 * <ul>
 *   <li>i32 → {@link Integer}
 *   <li>i64 → {@link Long}
 *   <li>f32 → {@link Float}
 *   <li>f64 → {@link Double}
 * </ul>
 *
 * <p>The callback must return the appropriate type for the function's return type,
 * or {@code null} for void functions.
 *
 * <p>Example usage:
 * <pre>{@code
 * HostFunction logFunc = new HostFunction(
 *     new FunctionSignature(new ValueType[]{ValueType.I32}, new ValueType[]{}),
 *     args -> {
 *         System.out.println("WASM says: " + args[0]);
 *         return null;
 *     }
 * );
 *
 * Map<String, Map<String, Object>> imports = new HashMap<>();
 * Map<String, Object> envImports = new HashMap<>();
 * envImports.put("log", logFunc);
 * imports.put("env", envImports);
 *
 * WebAssemblyInstance instance = module.instantiate(imports);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class HostFunction {

    private final FunctionSignature signature;
    private final Callback callback;

    /**
     * Callback interface for host function invocations.
     */
    @FunctionalInterface
    public interface Callback {

        /**
         * Executes the host function with the given arguments.
         *
         * @param args the arguments from the WASM caller, matching the function signature
         * @return the return value (Integer, Long, Float, or Double), or null for void functions
         */
        Object execute(Object... args);
    }

    /**
     * Creates a new host function with the given signature and callback.
     *
     * @param signature the function's type signature, must not be null
     * @param callback the callback to invoke when the function is called, must not be null
     */
    public HostFunction(final FunctionSignature signature, final Callback callback) {
        if (signature == null) {
            throw new IllegalArgumentException("Signature cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }
        this.signature = signature;
        this.callback = callback;
    }

    /**
     * Returns the type signature of this host function.
     *
     * @return the function signature, never null
     */
    public FunctionSignature getSignature() {
        return signature;
    }

    /**
     * Invokes this host function with the given arguments.
     *
     * @param args the arguments from the WASM caller
     * @return the return value, or null for void functions
     */
    public Object execute(final Object... args) {
        return callback.execute(args);
    }
}
