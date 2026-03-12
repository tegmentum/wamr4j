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
package ai.tegmentum.wamr4j.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.CompilationException;
import ai.tegmentum.wamr4j.exception.ValidationException;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Fuzz tests for WebAssembly memory operations.
 *
 * <p>These tests exercise memory read, write, and growth operations with
 * fuzzed offsets, lengths, and data to ensure no JVM crashes occur on
 * out-of-bounds or malformed memory access.
 *
 * @since 1.0.0
 */
class MemoryFuzzer {

    /**
     * A valid WASM module that exports 1 page of memory (64KB, max 4 pages).
     *
     * <p>Built from WAT:
     * <pre>
     * (module
     *   (memory (export "memory") 1 4)
     * )
     * </pre>
     */
    private static final byte[] MEMORY_MODULE = {
        // WASM header
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
        // Memory section: 1 memory, flags=1 (has max), initial=1, max=4
        0x05, 0x04, 0x01, 0x01, 0x01, 0x04,
        // Export section: export "memory" as memory index 0
        0x07, 0x0a, 0x01, 0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02, 0x00
    };

    /**
     * Reads and writes individual typed values at fuzzed offsets.
     *
     * <p>Exercises readInt32/writeInt32, readInt64/writeInt64, readFloat32/writeFloat32,
     * and readFloat64/writeFloat64 at arbitrary offsets to ensure bounds checking
     * works correctly and never causes a JVM crash.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzMemoryByteAccess(final FuzzedDataProvider data) {
        final int offset = data.consumeInt();
        final int typeChoice = data.consumeInt(0, 3);

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(MEMORY_MODULE);
                WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();

            switch (typeChoice) {
                case 0:
                    final int intVal = data.consumeInt();
                    memory.writeInt32(offset, intVal);
                    memory.readInt32(offset);
                    break;
                case 1:
                    final long longVal = data.consumeLong();
                    memory.writeInt64(offset, longVal);
                    memory.readInt64(offset);
                    break;
                case 2:
                    final float floatVal = data.consumeFloat();
                    memory.writeFloat32(offset, floatVal);
                    memory.readFloat32(offset);
                    break;
                case 3:
                    final double doubleVal = data.consumeDouble();
                    memory.writeFloat64(offset, doubleVal);
                    memory.readFloat64(offset);
                    break;
                default:
                    break;
            }

        } catch (final CompilationException | ValidationException e) {
            // Expected for module-level errors
        } catch (final WasmRuntimeException e) {
            // Expected for out-of-bounds memory access
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for negative offsets or closed resources
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during memory byte access fuzz", e);
        }
    }

    /**
     * Performs bulk read and write operations with fuzzed byte arrays and offsets.
     *
     * <p>Exercises the read(offset, length) and write(offset, data) methods with
     * arbitrary offsets, lengths, and data content to verify robust bounds checking.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzMemoryBulkAccess(final FuzzedDataProvider data) {
        final int readOffset = data.consumeInt();
        final int readLength = data.consumeInt();
        final int writeOffset = data.consumeInt();
        final byte[] writeData = data.consumeRemainingAsBytes();

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(MEMORY_MODULE);
                WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();

            // Exercise size/page inspection
            memory.size();
            memory.pageCount();
            memory.maxPageCount();
            memory.isShared();
            memory.getBaseAddress();
            memory.getBytesPerPage();

            // Attempt bulk write then read
            if (writeData.length > 0) {
                memory.write(writeOffset, writeData);
            }
            memory.read(readOffset, readLength);

        } catch (final CompilationException | ValidationException e) {
            // Expected for module-level errors
        } catch (final WasmRuntimeException e) {
            // Expected for out-of-bounds memory access
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for negative offsets, zero/negative lengths, or closed resources
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during memory bulk access fuzz", e);
        }
    }

    /**
     * Grows memory by fuzzed page counts and verifies no crash on excessive growth.
     *
     * <p>Exercises both the grow(int) and enlarge(long) methods with arbitrary
     * page count values including negative, zero, and very large values.
     *
     * @param data the fuzz input provider
     */
    @FuzzTest
    void fuzzMemoryGrowth(final FuzzedDataProvider data) {
        final int growPages = data.consumeInt();
        final long enlargePages = data.consumeLong();

        try (WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
                WebAssemblyModule module = runtime.compile(MEMORY_MODULE);
                WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();

            // Record initial state
            memory.pageCount();
            memory.maxPageCount();

            // Attempt grow with fuzzed pages
            memory.grow(growPages);

            // Attempt enlarge with fuzzed pages
            memory.enlarge(enlargePages);

            // Verify memory is still accessible after growth attempts
            memory.size();
            memory.pageCount();

        } catch (final CompilationException | ValidationException e) {
            // Expected for module-level errors
        } catch (final WasmRuntimeException e) {
            // Expected for failed growth or runtime errors
        } catch (final IllegalArgumentException | IllegalStateException e) {
            // Expected for negative page counts or closed resources
        } catch (final Exception e) {
            throw new AssertionError("Unexpected exception during memory growth fuzz", e);
        }
    }
}
