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

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.HostFunction;
import ai.tegmentum.wamr4j.ValueType;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores per-host-function callback data for the Panama FFI path.
 *
 * <p>Since Panama cannot store Java object references directly in native memory,
 * this class uses a global registry keyed by a unique token. The token is stored
 * as a native long value and passed as the {@code user_data} pointer through WAMR.
 * When the callback fires, the token is read back and used to look up the
 * associated Java objects.
 *
 * @since 1.0.0
 */
final class PanamaCallbackData {

    /** Global registry mapping native token values to callback data instances. */
    private static final ConcurrentHashMap<Long, PanamaCallbackData> REGISTRY =
        new ConcurrentHashMap<>();

    /** Monotonically increasing token generator. */
    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1);

    /** The host function to invoke when the callback fires. */
    final HostFunction hostFunction;

    /** The parameter types from the function signature. */
    final ValueType[] paramTypes;

    /** The result types from the function signature. */
    final ValueType[] resultTypes;

    /** The unique token for this callback data in the registry. */
    private final long token;

    private PanamaCallbackData(final HostFunction hostFunction, final FunctionSignature signature,
            final long token) {
        this.hostFunction = hostFunction;
        this.paramTypes = signature.getParameterTypes();
        this.resultTypes = signature.getReturnTypes();
        this.token = token;
    }

    /**
     * Allocates a new callback data entry and returns a native memory segment
     * containing the lookup token. The token is stored as a {@code long} value
     * in a single-element native buffer so it can be passed as {@code user_data}.
     *
     * @param arena the arena to allocate the native token buffer in
     * @param hostFunction the host function callback
     * @param signature the function's type signature
     * @return a native memory segment pointing to the token value
     */
    static MemorySegment allocate(final Arena arena, final HostFunction hostFunction,
            final FunctionSignature signature) {
        final long token = NEXT_TOKEN.getAndIncrement();
        final PanamaCallbackData data = new PanamaCallbackData(hostFunction, signature, token);
        REGISTRY.put(token, data);

        // Store the token as a pointer-sized value in native memory.
        // The Rust side receives this as *mut c_void (user_data).
        final MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
        segment.set(ValueLayout.JAVA_LONG, 0, token);
        return segment;
    }

    /**
     * Looks up a callback data instance from a native {@code user_data} pointer.
     * The pointer is expected to contain a long token value.
     *
     * @param userData the native memory segment containing the token
     * @return the associated callback data, or null if not found
     */
    static PanamaCallbackData fromPointer(final MemorySegment userData) {
        if (userData.equals(MemorySegment.NULL)) {
            return null;
        }
        // The user_data pointer IS the token value (cast from long to pointer on the Rust side).
        // We stored the MemorySegment address of a long, so Rust passes that address back.
        // Read the token from the address.
        final long token = userData.reinterpret(Long.BYTES).get(ValueLayout.JAVA_LONG, 0);
        return REGISTRY.get(token);
    }

    /**
     * Removes this callback data from the global registry.
     * Should be called during cleanup to prevent memory leaks.
     */
    void unregister() {
        REGISTRY.remove(token);
    }

    /**
     * Removes a callback data entry by its native token pointer.
     *
     * @param userData the native memory segment containing the token
     */
    static void unregisterByPointer(final MemorySegment userData) {
        final PanamaCallbackData data = fromPointer(userData);
        if (data != null) {
            data.unregister();
        }
    }
}
