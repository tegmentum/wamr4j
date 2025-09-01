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
 * Represents WebAssembly value types.
 *
 * <p>WebAssembly defines four basic value types that correspond to different numeric types. These
 * types are used in function signatures, global variables, and local variables.
 *
 * <p>The mapping to Java types is as follows:
 *
 * <ul>
 *   <li>{@link #I32} - 32-bit signed integer (int/Integer)
 *   <li>{@link #I64} - 64-bit signed integer (long/Long)
 *   <li>{@link #F32} - 32-bit floating point (float/Float)
 *   <li>{@link #F64} - 64-bit floating point (double/Double)
 * </ul>
 *
 * @since 1.0.0
 */
public enum ValueType {

    /** 32-bit signed integer type. */
    I32("i32", int.class, Integer.class),

    /** 64-bit signed integer type. */
    I64("i64", long.class, Long.class),

    /** 32-bit floating point type. */
    F32("f32", float.class, Float.class),

    /** 64-bit floating point type. */
    F64("f64", double.class, Double.class);

    private final String wasmName;
    private final Class<?> primitiveType;
    private final Class<?> wrapperType;

    ValueType(final String wasmName, final Class<?> primitiveType, final Class<?> wrapperType) {
        this.wasmName = wasmName;
        this.primitiveType = primitiveType;
        this.wrapperType = wrapperType;
    }

    /**
     * Returns the WebAssembly name for this value type.
     *
     * @return the WebAssembly type name (e.g., "i32", "f64")
     */
    public String getWasmName() {
        return wasmName;
    }

    /**
     * Returns the Java primitive type corresponding to this value type.
     *
     * @return the primitive Java class (e.g., int.class, double.class)
     */
    public Class<?> getPrimitiveType() {
        return primitiveType;
    }

    /**
     * Returns the Java wrapper type corresponding to this value type.
     *
     * @return the wrapper Java class (e.g., Integer.class, Double.class)
     */
    public Class<?> getWrapperType() {
        return wrapperType;
    }

    /**
     * Checks if the given Java object is compatible with this value type.
     *
     * <p>This method performs type checking to determine if a Java object can be used as a
     * parameter or return value for this WebAssembly type. It accepts both primitive and wrapper
     * types, and performs reasonable numeric conversions.
     *
     * @param value the Java object to check, may be null
     * @return true if the value is compatible, false otherwise
     */
    public boolean isCompatible(final Object value) {
        if (value == null) {
            return false;
        }

        final Class<?> valueClass = value.getClass();

        // Direct type matches
        if (primitiveType.equals(valueClass) || wrapperType.equals(valueClass)) {
            return true;
        }

        // Numeric conversions
        switch (this) {
            case I32:
                // Allow byte, short, and int
                return value instanceof Byte || value instanceof Short || value instanceof Integer;

            case I64:
                // Allow all integer types up to long
                return value instanceof Byte
                        || value instanceof Short
                        || value instanceof Integer
                        || value instanceof Long;

            case F32:
                // Allow float and numeric types that can be converted to float
                return value instanceof Float
                        || value instanceof Number && !(value instanceof Double);

            case F64:
                // Allow all numeric types
                return value instanceof Number;

            default:
                return false;
        }
    }

    /**
     * Converts a Java object to the appropriate type for this value type.
     *
     * <p>This method performs the actual conversion from Java objects to the expected WebAssembly
     * types. It should only be called after {@link #isCompatible(Object)} returns true.
     *
     * @param value the Java object to convert, must be compatible
     * @return the converted value in the appropriate Java type
     * @throws IllegalArgumentException if the value is not compatible
     */
    public Object convert(final Object value) {
        if (!isCompatible(value)) {
            throw new IllegalArgumentException(
                    "Value " + value + " is not compatible with type " + this);
        }

        if (value == null) {
            throw new IllegalArgumentException("Cannot convert null value");
        }

        switch (this) {
            case I32:
                return ((Number) value).intValue();
            case I64:
                return ((Number) value).longValue();
            case F32:
                return ((Number) value).floatValue();
            case F64:
                return ((Number) value).doubleValue();
            default:
                throw new IllegalArgumentException("Unknown value type: " + this);
        }
    }

    /**
     * Returns the value type corresponding to the given WebAssembly type name.
     *
     * @param wasmName the WebAssembly type name (e.g., "i32", "f64")
     * @return the corresponding ValueType, or null if not found
     */
    public static ValueType fromWasmName(final String wasmName) {
        if (wasmName == null) {
            return null;
        }

        for (final ValueType type : values()) {
            if (type.wasmName.equals(wasmName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Returns the value type corresponding to the given Java class.
     *
     * @param javaClass the Java class to map
     * @return the corresponding ValueType, or null if not mappable
     */
    public static ValueType fromJavaClass(final Class<?> javaClass) {
        if (javaClass == null) {
            return null;
        }

        for (final ValueType type : values()) {
            if (type.primitiveType.equals(javaClass) || type.wrapperType.equals(javaClass)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return wasmName;
    }
}
