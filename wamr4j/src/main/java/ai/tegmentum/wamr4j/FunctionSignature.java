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

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents the type signature of a WebAssembly function.
 *
 * <p>A function signature describes the parameter types and return type of a WebAssembly function.
 * This information is used for type checking and validation during function invocation.
 *
 * <p>WebAssembly supports the following value types:
 *
 * <ul>
 *   <li>{@link ValueType#I32} - 32-bit integer
 *   <li>{@link ValueType#I64} - 64-bit integer
 *   <li>{@link ValueType#F32} - 32-bit floating point
 *   <li>{@link ValueType#F64} - 64-bit floating point
 * </ul>
 *
 * <p>This class is immutable and thread-safe.
 *
 * @since 1.0.0
 */
public final class FunctionSignature {

    private final ValueType[] parameterTypes;
    private final ValueType[] returnTypes;

    /**
     * Creates a new function signature.
     *
     * @param parameterTypes the parameter types, may be null or empty
     * @param returnTypes the return types, may be null or empty (for void functions)
     */
    public FunctionSignature(final ValueType[] parameterTypes, final ValueType[] returnTypes) {
        this.parameterTypes = parameterTypes != null ? parameterTypes.clone() : new ValueType[0];
        this.returnTypes = returnTypes != null ? returnTypes.clone() : new ValueType[0];
    }

    /**
     * Returns the parameter types of this function.
     *
     * @return a copy of the parameter types array, never null
     */
    public ValueType[] getParameterTypes() {
        return parameterTypes.clone();
    }

    /**
     * Returns the return types of this function.
     *
     * @return a copy of the return types array, never null
     */
    public ValueType[] getReturnTypes() {
        return returnTypes.clone();
    }

    /**
     * Returns the number of parameters this function accepts.
     *
     * @return the parameter count, always >= 0
     */
    public int getParameterCount() {
        return parameterTypes.length;
    }

    /**
     * Returns the number of return values this function produces.
     *
     * @return the return count, always >= 0
     */
    public int getReturnCount() {
        return returnTypes.length;
    }

    /**
     * Checks if this function has a void return type.
     *
     * @return true if the function returns no values, false otherwise
     */
    public boolean isVoid() {
        return returnTypes.length == 0;
    }

    /**
     * Validates that the provided arguments match this function's signature.
     *
     * @param args the arguments to validate, may be null or empty
     * @return true if the arguments are compatible, false otherwise
     */
    public boolean isCompatible(final Object[] args) {
        final Object[] arguments = args != null ? args : new Object[0];

        if (arguments.length != parameterTypes.length) {
            return false;
        }

        for (int i = 0; i < arguments.length; i++) {
            if (!parameterTypes[i].isCompatible(arguments[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FunctionSignature)) {
            return false;
        }
        final FunctionSignature other = (FunctionSignature) obj;
        return Arrays.equals(parameterTypes, other.parameterTypes)
                && Arrays.equals(returnTypes, other.returnTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(parameterTypes), Arrays.hashCode(returnTypes));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes[i]);
        }
        sb.append(") -> ");
        if (returnTypes.length == 0) {
            sb.append("void");
        } else if (returnTypes.length == 1) {
            sb.append(returnTypes[0]);
        } else {
            sb.append("(");
            for (int i = 0; i < returnTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(returnTypes[i]);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
