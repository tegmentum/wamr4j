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
 * Represents the element kind of a WebAssembly table.
 *
 * <p>WebAssembly tables store references of a specific kind. Currently the specification
 * defines two reference types: function references ({@code funcref}) and external references
 * ({@code externref}).
 *
 * @since 1.0.0
 */
public enum ElementKind {

    /** External reference type (externref). */
    EXTERNREF(128),

    /** Function reference type (funcref). */
    FUNCREF(129);

    private final int nativeValue;

    ElementKind(final int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /**
     * Returns the native integer value for this element kind.
     *
     * @return the native element kind constant
     */
    public int getNativeValue() {
        return nativeValue;
    }

    /**
     * Converts a native integer value to an {@link ElementKind} enum constant.
     *
     * @param nativeValue the native element kind constant
     * @return the corresponding ElementKind, or null if the value is unknown
     */
    public static ElementKind fromNativeValue(final int nativeValue) {
        for (final ElementKind kind : values()) {
            if (kind.nativeValue == nativeValue) {
                return kind;
            }
        }
        return null;
    }
}
