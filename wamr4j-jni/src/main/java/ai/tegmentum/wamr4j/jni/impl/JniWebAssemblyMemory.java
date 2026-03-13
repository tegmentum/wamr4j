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

package ai.tegmentum.wamr4j.jni.impl;

import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly memory.
 * 
 * <p>This class represents WebAssembly linear memory using JNI
 * to communicate with the native WAMR library. It provides safe
 * memory access with bounds checking and proper error handling.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyMemory implements WebAssemblyMemory {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyMemory.class.getName());
    
    // WebAssembly page size (64KB)
    private static final int PAGE_SIZE = 65536;
    
    // Native memory handle
    private volatile long nativeHandle;

    // Parent instance reference
    private final JniWebAssemblyInstance parentInstance;

    // Cached ByteBuffer for typed read/write operations (avoids per-call JNI crossing)
    private volatile ByteBuffer cachedBuffer;

    /**
     * Creates a new JNI WebAssembly memory wrapper.
     * 
     * @param nativeHandle the native memory handle, must not be 0
     * @param parentInstance the parent instance, must not be null
     */
    public JniWebAssemblyMemory(final long nativeHandle, final JniWebAssemblyInstance parentInstance) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native memory handle cannot be 0");
        }
        if (parentInstance == null) {
            throw new IllegalArgumentException("Parent instance cannot be null");
        }
        
        this.nativeHandle = nativeHandle;
        this.parentInstance = parentInstance;
        
        LOGGER.fine("Created JNI WebAssembly memory with handle: " + nativeHandle);
    }

    @Override
    public byte[] read(final int offset, final int length) throws WasmRuntimeException {
        // Defensive programming - validate inputs
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive: " + length);
        }

        ensureValid();

        try {
            final byte[] result = new byte[length];
            // Use a duplicate of the cached buffer for thread-safe absolute positioning
            final ByteBuffer view = ensureBuffer().duplicate();
            view.position(offset);
            view.get(result, 0, length);
            return result;
        } catch (final IndexOutOfBoundsException | java.nio.BufferUnderflowException e) {
            throw new WasmRuntimeException("Memory access out of bounds at offset " + offset
                + ", length " + length, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error reading memory at offset " + offset, e);
        }
    }

    @Override
    public void write(final int offset, final byte[] data) throws WasmRuntimeException {
        // Defensive programming - validate inputs
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        ensureValid();

        try {
            final ByteBuffer view = ensureBuffer().duplicate();
            view.position(offset);
            view.put(data, 0, data.length);
        } catch (final IndexOutOfBoundsException | java.nio.BufferOverflowException e) {
            throw new WasmRuntimeException("Memory access out of bounds at offset " + offset
                + ", length " + data.length, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error writing memory at offset " + offset, e);
        }
    }

    @Override
    public int readInt32(final int offset) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            return ensureBuffer().getInt(offset);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds reading int32 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error reading int32 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt32(final int offset, final int value) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            ensureBuffer().putInt(offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds writing int32 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error writing int32 at offset " + offset, e);
        }
    }

    @Override
    public long readInt64(final int offset) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            return ensureBuffer().getLong(offset);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds reading int64 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error reading int64 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt64(final int offset, final long value) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            ensureBuffer().putLong(offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds writing int64 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error writing int64 at offset " + offset, e);
        }
    }

    @Override
    public float readFloat32(final int offset) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            return ensureBuffer().getFloat(offset);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds reading float32 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error reading float32 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat32(final int offset, final float value) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            ensureBuffer().putFloat(offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds writing float32 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error writing float32 at offset " + offset, e);
        }
    }

    @Override
    public double readFloat64(final int offset) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            return ensureBuffer().getDouble(offset);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds reading float64 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error reading float64 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat64(final int offset, final double value) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        ensureValid();

        try {
            ensureBuffer().putDouble(offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw new WasmRuntimeException("Memory access out of bounds writing float64 at offset " + offset, e);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error writing float64 at offset " + offset, e);
        }
    }

    @Override
    public int size() {
        ensureValid();
        
        try {
            return nativeGetMemorySize(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get memory size: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public int pageCount() {
        ensureValid();
        try {
            return nativeGetPageCount(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get page count: " + e.getMessage());
            return size() / PAGE_SIZE;
        }
    }

    @Override
    public int maxPageCount() {
        ensureValid();
        try {
            return nativeGetMaxPageCount(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get max page count: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isShared() {
        ensureValid();
        try {
            return nativeIsShared(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to check shared status: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int grow(final int pages) {
        if (pages < 0) {
            throw new IllegalArgumentException("Pages cannot be negative: " + pages);
        }

        ensureValid();

        try {
            final int result = nativeGrowMemory(nativeHandle, pages);
            if (result >= 0) {
                cachedBuffer = null; // Invalidate cached buffer after successful grow
            }
            return result;
        } catch (final Exception e) {
            LOGGER.fine("Memory growth failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public ByteBuffer asByteBuffer() {
        ensureValid();

        final ByteBuffer cached = cachedBuffer;
        if (cached != null) {
            return cached;
        }

        try {
            final ByteBuffer buffer = nativeGetMemoryBuffer(nativeHandle);
            if (buffer != null) {
                // WebAssembly uses little-endian byte order
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                cachedBuffer = buffer;
            }
            return buffer;
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to get memory buffer", e);
        }
    }

    /**
     * Returns the cached ByteBuffer, creating it on first access.
     * Used by typed read/write methods to avoid per-call JNI crossings.
     *
     * @return the cached DirectByteBuffer with little-endian byte order
     */
    private ByteBuffer ensureBuffer() {
        final ByteBuffer cached = cachedBuffer;
        if (cached != null) {
            return cached;
        }
        return asByteBuffer();
    }

    @Override
    public long getBaseAddress() {
        ensureValid();

        try {
            return nativeGetBaseAddress(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get base address: " + e.getMessage());
            return 0L;
        }
    }

    @Override
    public long getBytesPerPage() {
        ensureValid();

        try {
            return nativeGetBytesPerPage(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get bytes per page: " + e.getMessage());
            return PAGE_SIZE;
        }
    }

    @Override
    public boolean enlarge(final long incPages) {
        if (incPages < 0) {
            throw new IllegalArgumentException("Pages cannot be negative: " + incPages);
        }
        ensureValid();
        final boolean result = nativeEnlarge(nativeHandle, incPages);
        if (result) {
            cachedBuffer = null; // Invalidate cached buffer after successful enlarge
        }
        return result;
    }

    @Override
    public boolean isValid() {
        return nativeHandle != 0L && parentInstance.isValid();
    }

    /**
     * Destroys the native memory handle, freeing the Rust Box wrapper.
     * Called by the parent instance during cleanup.
     */
    void close() {
        cachedBuffer = null;
        final long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L) {
            try {
                nativeDestroyMemory(handle);
            } catch (final Exception e) {
                LOGGER.warning("Error destroying native memory: " + e.getMessage());
            }
        }
    }

    private void ensureValid() {
        if (!isValid()) {
            throw new IllegalStateException("Memory is no longer valid - parent instance has been closed");
        }
    }

    // Native method declarations
    
    /**
     * Reads bytes from WebAssembly memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset to read from
     * @param length the number of bytes to read
     * @return the read bytes
     * @throws WasmRuntimeException if the read is out of bounds
     */
    private static native byte[] nativeReadMemory(long memoryHandle, int offset, int length) throws WasmRuntimeException;

    /**
     * Writes bytes to WebAssembly memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset to write to
     * @param data the bytes to write
     * @throws WasmRuntimeException if the write is out of bounds
     */
    private static native void nativeWriteMemory(long memoryHandle, int offset, byte[] data) throws WasmRuntimeException;

    /**
     * Reads a 32-bit integer from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the integer value
     * @throws WasmRuntimeException if the read is out of bounds
     */
    private static native int nativeReadInt32(long memoryHandle, int offset) throws WasmRuntimeException;

    /**
     * Writes a 32-bit integer to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the integer value
     * @throws WasmRuntimeException if the write is out of bounds
     */
    private static native void nativeWriteInt32(long memoryHandle, int offset, int value) throws WasmRuntimeException;

    /**
     * Reads a 64-bit integer from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the integer value
     * @throws WasmRuntimeException if the read is out of bounds
     */
    private static native long nativeReadInt64(long memoryHandle, int offset) throws WasmRuntimeException;

    /**
     * Writes a 64-bit integer to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the integer value
     * @throws WasmRuntimeException if the write is out of bounds
     */
    private static native void nativeWriteInt64(long memoryHandle, int offset, long value) throws WasmRuntimeException;

    /**
     * Reads a 32-bit float from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the float value
     * @throws WasmRuntimeException if the read is out of bounds
     */
    private static native float nativeReadFloat32(long memoryHandle, int offset) throws WasmRuntimeException;

    /**
     * Writes a 32-bit float to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the float value
     * @throws WasmRuntimeException if the write is out of bounds
     */
    private static native void nativeWriteFloat32(long memoryHandle, int offset, float value) throws WasmRuntimeException;

    /**
     * Reads a 64-bit double from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the double value
     * @throws WasmRuntimeException if the read is out of bounds
     */
    private static native double nativeReadFloat64(long memoryHandle, int offset) throws WasmRuntimeException;

    /**
     * Writes a 64-bit double to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the double value
     * @throws WasmRuntimeException if the write is out of bounds
     */
    private static native void nativeWriteFloat64(long memoryHandle, int offset, double value) throws WasmRuntimeException;

    /**
     * Gets the current size of memory in bytes.
     * 
     * @param memoryHandle the native memory handle
     * @return the memory size in bytes
     */
    private static native int nativeGetMemorySize(long memoryHandle);

    /**
     * Grows memory by the specified number of pages.
     * 
     * @param memoryHandle the native memory handle
     * @param pages the number of pages to grow by
     * @return the previous size in pages, or -1 if growth failed
     */
    private static native int nativeGrowMemory(long memoryHandle, int pages);

    /**
     * Gets a ByteBuffer view of the memory.
     *
     * @param memoryHandle the native memory handle
     * @return a ByteBuffer view of the memory
     */
    private static native ByteBuffer nativeGetMemoryBuffer(long memoryHandle);

    /**
     * Gets the current page count.
     *
     * @param memoryHandle the native memory handle
     * @return the page count
     */
    private static native int nativeGetPageCount(long memoryHandle);

    /**
     * Gets the maximum page count.
     *
     * @param memoryHandle the native memory handle
     * @return the maximum page count
     */
    private static native int nativeGetMaxPageCount(long memoryHandle);

    /**
     * Checks if the memory is shared.
     *
     * @param memoryHandle the native memory handle
     * @return true if shared
     */
    private static native boolean nativeIsShared(long memoryHandle);

    /**
     * Gets the base address of the memory as a native pointer value.
     *
     * @param memoryHandle the native memory handle
     * @return the base address as a long
     */
    private static native long nativeGetBaseAddress(long memoryHandle);

    /**
     * Gets the number of bytes per page for this memory.
     *
     * @param memoryHandle the native memory handle
     * @return the bytes per page
     */
    private static native long nativeGetBytesPerPage(long memoryHandle);

    /**
     * Destroys a native WebAssembly memory handle.
     *
     * @param memoryHandle the native memory handle
     */
    private static native void nativeDestroyMemory(long memoryHandle);

    /**
     * Enlarges the memory by the specified number of pages.
     *
     * @param memoryHandle the native memory handle
     * @param incPages the number of pages to add
     * @return true if the enlargement succeeded
     */
    private static native boolean nativeEnlarge(long memoryHandle, long incPages);
}