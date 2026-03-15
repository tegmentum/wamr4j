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

import java.nio.ByteBuffer;

import ai.tegmentum.wamr4j.exception.WasmRuntimeException;

/**
 * Represents WebAssembly linear memory.
 *
 * <p>WebAssembly memory is a linear array of bytes that can be dynamically resized. Memory is
 * zero-indexed and provides methods for reading and writing data at specific offsets.
 *
 * <p>Memory operations are bounds-checked to prevent buffer overflows and security vulnerabilities.
 * Invalid memory access will result in a {@link WasmRuntimeException}.
 *
 * <p>Memory objects maintain a reference to their parent instance and become invalid when the
 * instance is closed. Memory is not thread-safe - concurrent access should be synchronized by the
 * caller.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WebAssemblyMemory memory = instance.getMemory();
 *
 * // Write data to memory
 * byte[] data = "Hello, WebAssembly!".getBytes();
 * memory.write(0, data);
 *
 * // Read data back
 * byte[] result = memory.read(0, data.length);
 *
 * // Access as ByteBuffer for advanced operations
 * ByteBuffer buffer = memory.asByteBuffer();
 * }</pre>
 *
 * @since 1.0.0
 */
public interface WebAssemblyMemory {

    /**
     * Reads bytes from memory at the specified offset.
     *
     * <p>This method performs bounds checking to ensure the read operation is within the valid
     * memory range.
     *
     * @param offset the byte offset to read from, must be {@code >= 0}
     * @param length the number of bytes to read, must be {@code > 0}
     * @return a new byte array containing the read data
     * @throws WasmRuntimeException if the read operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0} or {@code length <= 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    byte[] read(int offset, int length) throws WasmRuntimeException;

    /**
     * Writes bytes to memory at the specified offset.
     *
     * <p>This method performs bounds checking to ensure the write operation is within the valid
     * memory range.
     *
     * @param offset the byte offset to write to, must be {@code >= 0}
     * @param data the data to write, must not be null
     * @throws WasmRuntimeException if the write operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0} or data is null
     * @throws IllegalStateException if the parent instance has been closed
     */
    void write(int offset, byte[] data) throws WasmRuntimeException;

    /**
     * Reads a signed 32-bit integer from memory at the specified offset.
     *
     * <p>The integer is read in little-endian byte order, which is the WebAssembly standard.
     *
     * @param offset the byte offset to read from, must be {@code >= 0}
     * @return the 32-bit integer value
     * @throws WasmRuntimeException if the read operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    int readInt32(int offset) throws WasmRuntimeException;

    /**
     * Writes a signed 32-bit integer to memory at the specified offset.
     *
     * <p>The integer is written in little-endian byte order, which is the WebAssembly standard.
     *
     * @param offset the byte offset to write to, must be {@code >= 0}
     * @param value the 32-bit integer value to write
     * @throws WasmRuntimeException if the write operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    void writeInt32(int offset, int value) throws WasmRuntimeException;

    /**
     * Reads a signed 64-bit integer from memory at the specified offset.
     *
     * <p>The integer is read in little-endian byte order, which is the WebAssembly standard.
     *
     * @param offset the byte offset to read from, must be {@code >= 0}
     * @return the 64-bit integer value
     * @throws WasmRuntimeException if the read operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    long readInt64(int offset) throws WasmRuntimeException;

    /**
     * Writes a signed 64-bit integer to memory at the specified offset.
     *
     * <p>The integer is written in little-endian byte order, which is the WebAssembly standard.
     *
     * @param offset the byte offset to write to, must be {@code >= 0}
     * @param value the 64-bit integer value to write
     * @throws WasmRuntimeException if the write operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    void writeInt64(int offset, long value) throws WasmRuntimeException;

    /**
     * Reads a 32-bit floating-point number from memory at the specified offset.
     *
     * @param offset the byte offset to read from, must be {@code >= 0}
     * @return the floating-point value
     * @throws WasmRuntimeException if the read operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    float readFloat32(int offset) throws WasmRuntimeException;

    /**
     * Writes a 32-bit floating-point number to memory at the specified offset.
     *
     * @param offset the byte offset to write to, must be {@code >= 0}
     * @param value the floating-point value to write
     * @throws WasmRuntimeException if the write operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    void writeFloat32(int offset, float value) throws WasmRuntimeException;

    /**
     * Reads a 64-bit floating-point number from memory at the specified offset.
     *
     * @param offset the byte offset to read from, must be {@code >= 0}
     * @return the floating-point value
     * @throws WasmRuntimeException if the read operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    double readFloat64(int offset) throws WasmRuntimeException;

    /**
     * Writes a 64-bit floating-point number to memory at the specified offset.
     *
     * @param offset the byte offset to write to, must be {@code >= 0}
     * @param value the floating-point value to write
     * @throws WasmRuntimeException if the write operation is out of bounds
     * @throws IllegalArgumentException if {@code offset < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    void writeFloat64(int offset, double value) throws WasmRuntimeException;

    /**
     * Returns the current size of memory in bytes.
     *
     * @return the memory size in bytes, always {@code >= 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    int size();

    /**
     * Returns the current size of memory in WebAssembly pages.
     *
     * <p>WebAssembly memory is measured in pages of 64KB each.
     *
     * @return the memory size in pages, always {@code >= 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    int pageCount();

    /**
     * Returns the maximum size of memory in WebAssembly pages.
     *
     * <p>This is the upper bound on the number of pages the memory can grow to.
     *
     * @return the maximum memory size in pages
     * @throws IllegalStateException if the parent instance has been closed
     */
    int maxPageCount();

    /**
     * Returns whether this memory is shared.
     *
     * <p>Shared memories can be accessed concurrently by multiple threads using
     * atomic operations.
     *
     * @return true if the memory is shared, false otherwise
     * @throws IllegalStateException if the parent instance has been closed
     */
    boolean isShared();

    /**
     * Attempts to grow memory by the specified number of pages.
     *
     * <p>Memory growth may fail if there is insufficient system memory or if the maximum memory
     * limit would be exceeded.
     *
     * @param pages the number of pages to grow by, must be {@code >= 0}
     * @return the previous size in pages, or -1 if growth failed
     * @throws IllegalArgumentException if {@code pages < 0}
     * @throws IllegalStateException if the parent instance has been closed
     */
    int grow(int pages);

    /**
     * Returns a ByteBuffer view of the memory.
     *
     * <p>The returned buffer is a view of the actual memory and modifications are reflected in both
     * directions. The buffer remains valid as long as the memory doesn't grow - growth may
     * invalidate the buffer.
     *
     * <p>The buffer uses little-endian byte order, which is the WebAssembly standard.
     *
     * @return a ByteBuffer view of the memory
     * @throws IllegalStateException if the parent instance has been closed
     */
    ByteBuffer asByteBuffer();

    /**
     * Returns the base address of this memory as a native pointer value.
     *
     * <p>The returned value is the host-side address of the first byte of the WebAssembly linear
     * memory. This is primarily useful for advanced interop scenarios where native code needs a
     * direct pointer to the memory region.
     *
     * <p>The address may change after memory growth operations. Callers should not cache this
     * value across grow calls.
     *
     * @return the base address as a long, or 0 if the address cannot be determined
     * @throws IllegalStateException if the parent instance has been closed
     */
    long getBaseAddress();

    /**
     * Returns the number of bytes per WebAssembly page for this memory.
     *
     * <p>The standard WebAssembly page size is 65536 bytes (64KB), but WAMR may report this
     * value from the underlying implementation.
     *
     * @return the number of bytes per page
     * @throws IllegalStateException if the parent instance has been closed
     */
    long getBytesPerPage();

    /**
     * Enlarges this memory by the specified number of pages.
     *
     * <p>This is an alternative to {@link #grow(int)} that uses a different
     * WAMR internal code path. It operates directly on the memory instance
     * rather than through the module instance.
     *
     * @param incPages the number of pages to add, must be non-negative
     * @return true if the enlargement succeeded, false otherwise
     * @throws IllegalArgumentException if incPages is negative
     * @throws IllegalStateException if the parent instance has been closed
     */
    boolean enlarge(long incPages);

    /**
     * Checks if the parent instance has been closed.
     *
     * <p>Memory objects become invalid when their parent instance is closed. This method can be
     * used to check the validity of the memory.
     *
     * @return true if the parent instance has been closed, false otherwise
     */
    boolean isValid();
}
