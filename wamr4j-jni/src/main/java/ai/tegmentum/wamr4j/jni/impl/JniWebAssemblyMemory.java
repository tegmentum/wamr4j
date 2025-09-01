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
import ai.tegmentum.wamr4j.exception.RuntimeException;
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
    private final long nativeHandle;
    
    // Parent instance reference
    private final JniWebAssemblyInstance parentInstance;

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
    public byte[] read(final int offset, final int length) throws RuntimeException {
        // Defensive programming - validate inputs
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive: " + length);
        }
        
        ensureValid();
        
        try {
            return nativeReadMemory(nativeHandle, offset, length);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error reading memory at offset " + offset, e);
        }
    }

    @Override
    public void write(final int offset, final byte[] data) throws RuntimeException {
        // Defensive programming - validate inputs
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        ensureValid();
        
        try {
            nativeWriteMemory(nativeHandle, offset, data);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error writing memory at offset " + offset, e);
        }
    }

    @Override
    public int readInt32(final int offset) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            return nativeReadInt32(nativeHandle, offset);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error reading int32 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt32(final int offset, final int value) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            nativeWriteInt32(nativeHandle, offset, value);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error writing int32 at offset " + offset, e);
        }
    }

    @Override
    public long readInt64(final int offset) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            return nativeReadInt64(nativeHandle, offset);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error reading int64 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt64(final int offset, final long value) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            nativeWriteInt64(nativeHandle, offset, value);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error writing int64 at offset " + offset, e);
        }
    }

    @Override
    public float readFloat32(final int offset) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            return nativeReadFloat32(nativeHandle, offset);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error reading float32 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat32(final int offset, final float value) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            nativeWriteFloat32(nativeHandle, offset, value);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error writing float32 at offset " + offset, e);
        }
    }

    @Override
    public double readFloat64(final int offset) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            return nativeReadFloat64(nativeHandle, offset);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error reading float64 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat64(final int offset, final double value) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        
        ensureValid();
        
        try {
            nativeWriteFloat64(nativeHandle, offset, value);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected error writing float64 at offset " + offset, e);
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
        return size() / PAGE_SIZE;
    }

    @Override
    public int grow(final int pages) {
        if (pages < 0) {
            throw new IllegalArgumentException("Pages cannot be negative: " + pages);
        }
        
        ensureValid();
        
        try {
            return nativeGrowMemory(nativeHandle, pages);
        } catch (final Exception e) {
            LOGGER.fine("Memory growth failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public ByteBuffer asByteBuffer() {
        ensureValid();
        
        try {
            final ByteBuffer buffer = nativeGetMemoryBuffer(nativeHandle);
            if (buffer != null) {
                // WebAssembly uses little-endian byte order
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            return buffer;
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to get memory buffer", e);
        }
    }

    @Override
    public boolean isValid() {
        return parentInstance.isValid();
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
     * @throws RuntimeException if the read is out of bounds
     */
    private static native byte[] nativeReadMemory(long memoryHandle, int offset, int length) throws RuntimeException;

    /**
     * Writes bytes to WebAssembly memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset to write to
     * @param data the bytes to write
     * @throws RuntimeException if the write is out of bounds
     */
    private static native void nativeWriteMemory(long memoryHandle, int offset, byte[] data) throws RuntimeException;

    /**
     * Reads a 32-bit integer from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the integer value
     * @throws RuntimeException if the read is out of bounds
     */
    private static native int nativeReadInt32(long memoryHandle, int offset) throws RuntimeException;

    /**
     * Writes a 32-bit integer to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the integer value
     * @throws RuntimeException if the write is out of bounds
     */
    private static native void nativeWriteInt32(long memoryHandle, int offset, int value) throws RuntimeException;

    /**
     * Reads a 64-bit integer from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the integer value
     * @throws RuntimeException if the read is out of bounds
     */
    private static native long nativeReadInt64(long memoryHandle, int offset) throws RuntimeException;

    /**
     * Writes a 64-bit integer to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the integer value
     * @throws RuntimeException if the write is out of bounds
     */
    private static native void nativeWriteInt64(long memoryHandle, int offset, long value) throws RuntimeException;

    /**
     * Reads a 32-bit float from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the float value
     * @throws RuntimeException if the read is out of bounds
     */
    private static native float nativeReadFloat32(long memoryHandle, int offset) throws RuntimeException;

    /**
     * Writes a 32-bit float to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the float value
     * @throws RuntimeException if the write is out of bounds
     */
    private static native void nativeWriteFloat32(long memoryHandle, int offset, float value) throws RuntimeException;

    /**
     * Reads a 64-bit double from memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @return the double value
     * @throws RuntimeException if the read is out of bounds
     */
    private static native double nativeReadFloat64(long memoryHandle, int offset) throws RuntimeException;

    /**
     * Writes a 64-bit double to memory.
     * 
     * @param memoryHandle the native memory handle
     * @param offset the byte offset
     * @param value the double value
     * @throws RuntimeException if the write is out of bounds
     */
    private static native void nativeWriteFloat64(long memoryHandle, int offset, double value) throws RuntimeException;

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
}