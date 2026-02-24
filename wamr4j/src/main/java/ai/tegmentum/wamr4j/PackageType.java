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
 * Represents the type of a WebAssembly binary package.
 *
 * <p>WAMR supports both standard WebAssembly bytecode and pre-compiled
 * AOT (Ahead-Of-Time) binaries. This enum identifies the package format.
 *
 * @since 1.0.0
 */
public enum PackageType {

    /** Standard WebAssembly bytecode (.wasm). */
    WASM(0),

    /** WAMR AOT (Ahead-Of-Time) compiled binary. */
    AOT(1),

    /** Unknown or unrecognized package format. */
    UNKNOWN(0xFFFF);

    private final int nativeValue;

    PackageType(final int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /**
     * Returns the native integer value for this package type.
     *
     * @return the native package type constant
     */
    public int getNativeValue() {
        return nativeValue;
    }

    /**
     * Converts a native integer value to a {@link PackageType} enum constant.
     *
     * @param nativeValue the native package type constant
     * @return the corresponding PackageType, or {@link #UNKNOWN} if the value is unrecognized
     */
    public static PackageType fromNativeValue(final int nativeValue) {
        for (final PackageType type : values()) {
            if (type.nativeValue == nativeValue) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
