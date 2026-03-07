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
 * Represents WAMR execution running modes.
 *
 * <p>WAMR supports multiple execution modes with different performance and compatibility
 * characteristics. The available modes depend on how the WAMR runtime was compiled.
 *
 * @since 1.0.0
 */
public enum RunningMode {

    /** Classic interpreter mode. Always available. */
    INTERP(1),

    /** Fast JIT compilation mode. May not be available in all builds. */
    FAST_JIT(2),

    /** LLVM-based JIT compilation mode. May not be available in all builds. */
    LLVM_JIT(3),

    /** Multi-tier JIT mode combining fast JIT and LLVM JIT. May not be available in all builds. */
    MULTI_TIER_JIT(4);

    private final int nativeValue;

    RunningMode(final int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /**
     * Returns the native integer value for this running mode.
     *
     * @return the native mode constant
     */
    public int getNativeValue() {
        return nativeValue;
    }

    /**
     * Converts a native integer value to a {@link RunningMode} enum constant.
     *
     * @param nativeValue the native mode constant
     * @return the corresponding RunningMode, or null if the value is unknown
     */
    public static RunningMode fromNativeValue(final int nativeValue) {
        for (final RunningMode mode : values()) {
            if (mode.nativeValue == nativeValue) {
                return mode;
            }
        }
        return null;
    }
}
