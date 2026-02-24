/*
 * Copyright (c) 2024 Tegmentum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.tegmentum.wamr4j.panama.impl;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.logging.Logger;

/**
 * Shared WebAssembly value type constants and Panama FFI utility methods.
 *
 * <p>These constants correspond to the WASM_TYPE_* values defined in
 * {@code wamr4j-native/src/utils.rs} and must stay in sync.
 *
 * @since 1.0.0
 */
final class WasmTypes {

    private static final Logger LOGGER = Logger.getLogger(WasmTypes.class.getName());

    /** WebAssembly i32 value type. */
    static final int I32 = 0;

    /** WebAssembly i64 value type. */
    static final int I64 = 1;

    /** WebAssembly f32 value type. */
    static final int F32 = 2;

    /** WebAssembly f64 value type. */
    static final int F64 = 3;

    /** Size in bytes of error message buffers passed to native functions. */
    static final int ERROR_BUF_SIZE = 1024;

    /** Maximum expected byte length for C name strings returned by native functions. */
    static final int MAX_NAME_LENGTH = 1024;

    private WasmTypes() {
        // Utility class — not instantiable
    }

    /**
     * Reads an array of C strings from a native names enumeration function.
     *
     * <p>This is a shared implementation used by both Module and Instance
     * name enumeration to avoid code duplication.
     *
     * @param nativeHandle the native handle to pass as the first argument
     * @param namesHandle the MethodHandle for the native names function
     * @param freeHandle the MethodHandle for freeing the native names array, may be null
     * @param kind a descriptive label for log messages (e.g. "export", "function")
     * @return the array of names, or empty array on failure
     */
    static String[] readNativeNames(final MemorySegment nativeHandle,
                                    final MethodHandle namesHandle,
                                    final MethodHandle freeHandle,
                                    final String kind) {
        if (namesHandle == null) {
            LOGGER.warning("wamr native " + kind + " names function not found");
            return new String[0];
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment namesBufferPtr = arena.allocate(ValueLayout.ADDRESS);
            final MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);

            final int result = (int) namesHandle.invoke(nativeHandle, namesBufferPtr, countPtr);

            if (result != 0) {
                LOGGER.warning("Failed to get " + kind + " names from native library");
                return new String[0];
            }

            final int count = countPtr.get(ValueLayout.JAVA_INT, 0);
            if (count == 0) {
                return new String[0];
            }

            final MemorySegment namesArray = namesBufferPtr.get(ValueLayout.ADDRESS, 0)
                .reinterpret((long) count * ValueLayout.ADDRESS.byteSize());
            final String[] names = new String[count];

            for (int i = 0; i < count; i++) {
                final MemorySegment namePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i);
                if (namePtr.equals(MemorySegment.NULL)) {
                    names[i] = "";
                    continue;
                }
                names[i] = namePtr.reinterpret(MAX_NAME_LENGTH).getString(0);
            }

            // Free the native array
            if (freeHandle != null) {
                freeHandle.invoke(namesArray, count);
            }

            return names;
        } catch (final Throwable e) {
            LOGGER.warning("Error getting " + kind + " names: " + e.getMessage());
            return new String[0];
        }
    }
}
