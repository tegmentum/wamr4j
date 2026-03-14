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

import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Represents a callable WebAssembly function.
 *
 * <p>WebAssembly functions are strongly typed with specific parameter and return types. This
 * interface provides methods to invoke functions and inspect their signatures.
 *
 * <p>Functions maintain a reference to their parent instance and become invalid when the instance
 * is closed. Functions are not thread-safe - concurrent invocation of the same function should be
 * synchronized.
 *
 * <p>Parameter and return types are mapped to Java types as follows:
 *
 * <ul>
 *   <li>i32 → int or Integer
 *   <li>i64 → long or Long
 *   <li>f32 → float or Float
 *   <li>f64 → double or Double
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WebAssemblyFunction add = instance.getFunction("add");
 * FunctionSignature signature = add.getSignature();
 *
 * // Invoke with appropriate arguments
 * Object result = add.invoke(42, 24);
 * int sum = (Integer) result;
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WebAssemblyFunction {

    /**
     * Invokes this WebAssembly function with the given arguments.
     *
     * <p>The arguments must match the function's parameter types and count exactly. Type conversion
     * is performed automatically for compatible numeric types (e.g., int to long).
     *
     * @param args the arguments to pass to the function, must match signature
     * @return the function result, or null for void functions
     * @throws WasmRuntimeException if execution fails or arguments are invalid
     * @throws IllegalArgumentException if the argument count or types don't match
     * @throws IllegalStateException if the parent instance has been closed
     */
    Object invoke(Object... args) throws WasmRuntimeException;

    /**
     * Invokes this function multiple times with different argument sets in a single batch.
     *
     * <p>This amortizes JNI/FFI crossing overhead by performing all invocations in one native call.
     * On error, the first failing invocation throws and remaining calls are skipped (fail-fast).
     *
     * @param argSets the argument arrays for each invocation
     * @return an array of results, one per invocation (null entries for void functions)
     * @throws WasmRuntimeException if any invocation fails
     * @throws IllegalArgumentException if argSets is null
     * @throws IllegalStateException if the parent instance has been closed
     */
    default Object[] invokeMultiple(final Object[]... argSets) throws WasmRuntimeException {
        if (argSets == null) {
            throw new IllegalArgumentException("Argument sets array cannot be null");
        }
        final Object[] results = new Object[argSets.length];
        for (int i = 0; i < argSets.length; i++) {
            results[i] = invoke(argSets[i]);
        }
        return results;
    }

    /**
     * Returns the signature of this function.
     *
     * <p>The signature describes the parameter types and return type of the function, which is
     * useful for validation and reflection.
     *
     * @return the function signature, never null
     * @throws IllegalStateException if the parent instance has been closed
     */
    FunctionSignature getSignature();

    /**
     * Returns the name of this function.
     *
     * @return the function name, never null
     * @throws IllegalStateException if the parent instance has been closed
     */
    String getName();

    /**
     * Checks if the parent instance has been closed.
     *
     * <p>Functions become invalid when their parent instance is closed. This method can be used to
     * check the validity of the function.
     *
     * @return true if the parent instance has been closed, false otherwise
     */
    boolean isValid();
}
