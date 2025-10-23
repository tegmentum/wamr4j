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

import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.exception.RuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly linear memory.
 * 
 * <p>This class provides access to WebAssembly linear memory using Panama FFI
 * for direct memory operations. It provides safe, bounds-checked access to
 * the WebAssembly memory space with automatic cleanup.
 * 
 * @since 1.0.0
 */
public final class PanamaWebAssemblyMemory implements WebAssemblyMemory {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyMemory.class.getName());
    
    // Native memory handle as MemorySegment
    private volatile MemorySegment nativeHandle;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // Function descriptors for native calls
    private static final FunctionDescriptor DESTROY_MEMORY_DESC = 
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
    private static final FunctionDescriptor GET_SIZE_DESC = 
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
    private static final FunctionDescriptor GET_DATA_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor GROW_DESC = 
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

    /**
     * Creates a new Panama WebAssembly memory wrapper.
     * 
     * @param nativeHandle the native memory handle, must not be NULL
     */
    public PanamaWebAssemblyMemory(final MemorySegment nativeHandle) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native memory handle cannot be null or NULL");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created Panama WebAssembly memory with handle: " + nativeHandle);
    }

    @Override
    public int size() {
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getSizeFunc = lookup.find("wamr_memory_size")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_memory_size' not found"));
            
            final MethodHandle getSize = Linker.nativeLinker()
                .downcallHandle(getSizeFunc, GET_SIZE_DESC);
            
            final long size = (long) getSize.invoke(nativeHandle);
            if (size < 0) {
                throw new RuntimeException("Invalid memory size returned: " + size);
            }
            
            return (int) size;
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error getting memory size", e);
        }
    }

    @Override
    public ByteBuffer asByteBuffer() {
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getDataFunc = lookup.find("wamr_memory_data")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_memory_data' not found"));
            
            final MethodHandle getData = Linker.nativeLinker()
                .downcallHandle(getDataFunc, GET_DATA_DESC);
            
            final MemorySegment dataPtr = (MemorySegment) getData.invoke(nativeHandle);
            if (dataPtr.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to get memory data pointer");
            }
            
            final long size = size();
            final MemorySegment memorySegment = dataPtr.reinterpret(size);
            
            return memorySegment.asByteBuffer();
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error getting memory buffer", e);
        }
    }

    // Helper method - not part of public API
    byte readByte(final int offset) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 1);
        
        try {
            return asByteBuffer().get((int) offset);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read byte at offset " + offset, e);
        }
    }

    // Helper method - not part of public API
    void writeByte(final int offset, final byte value) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 1);
        
        try {
            asByteBuffer().put((int) offset, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to write byte at offset " + offset, e);
        }
    }

    @Override
    public int readInt32(final int offset) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            return asByteBuffer().getInt((int) offset);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read int32 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt32(final int offset, final int value) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            asByteBuffer().putInt((int) offset, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to write int32 at offset " + offset, e);
        }
    }

    @Override
    public long readInt64(final int offset) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            return asByteBuffer().getLong((int) offset);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read int64 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt64(final int offset, final long value) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            asByteBuffer().putLong((int) offset, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to write int64 at offset " + offset, e);
        }
    }

    @Override
    public float readFloat32(final int offset) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            return asByteBuffer().getFloat((int) offset);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read float32 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat32(final int offset, final float value) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            asByteBuffer().putFloat((int) offset, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to write float32 at offset " + offset, e);
        }
    }

    @Override
    public double readFloat64(final int offset) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            return asByteBuffer().getDouble((int) offset);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read float64 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat64(final int offset, final double value) throws RuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            asByteBuffer().putDouble((int) offset, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to write float64 at offset " + offset, e);
        }
    }

    @Override
    public int grow(final int pages) {
        if (pages < 0) {
            throw new IllegalArgumentException("Page count cannot be negative: " + pages);
        }

        ensureNotClosed();

        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment growFunc = lookup.find("wamr_memory_grow")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_memory_grow' not found"));

            final MethodHandle grow = Linker.nativeLinker()
                .downcallHandle(growFunc, GROW_DESC);

            final int result = (int) grow.invoke(nativeHandle, (long) pages);
            return result; // Returns previous page count, or -1 on failure
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error growing memory", e);
        }
    }

    // Helper method for cleanup - not part of public API
    void close() {
        if (closed.compareAndSet(false, true)) {
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;
            
            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
                    final MemorySegment destroyMemoryFunc = lookup.find("wamr_destroy_memory")
                        .orElse(MemorySegment.NULL);
                        
                    if (!destroyMemoryFunc.equals(MemorySegment.NULL)) {
                        final MethodHandle destroyMemory = Linker.nativeLinker()
                            .downcallHandle(destroyMemoryFunc, DESTROY_MEMORY_DESC);
                        destroyMemory.invoke(handle);
                        LOGGER.fine("Destroyed Panama WebAssembly memory with handle: " + handle);
                    }
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native memory: " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly memory has been closed");
        }
    }
    
    private void validateOffset(final long offset, final int size) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Memory offset cannot be negative: " + offset);
        }
        
        final long memorySize = size();
        if (offset + size > memorySize) {
            throw new RuntimeException(
                String.format("Memory access out of bounds: offset=%d, size=%d, memorySize=%d", 
                    offset, size, memorySize));
        }
    }
    
    @Override
    public byte[] read(final int offset, final int length) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive: " + length);
        }

        ensureNotClosed();
        validateOffset(offset, length);

        final byte[] result = new byte[length];
        try {
            final ByteBuffer buffer = asByteBuffer();
            buffer.position(offset);
            buffer.get(result);
            return result;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read " + length + " bytes at offset " + offset, e);
        }
    }

    @Override
    public void write(final int offset, final byte[] data) throws RuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        ensureNotClosed();
        validateOffset(offset, data.length);

        try {
            final ByteBuffer buffer = asByteBuffer();
            buffer.position(offset);
            buffer.put(data);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to write " + data.length + " bytes at offset " + offset, e);
        }
    }

    @Override
    public int pageCount() {
        ensureNotClosed();

        // WebAssembly page size is 64KB
        final int pageSize = 65536;
        final int totalSize = size();
        return totalSize / pageSize;
    }

    @Override
    public boolean isValid() {
        return !closed.get() && nativeHandle != null && !nativeHandle.equals(MemorySegment.NULL);
    }
}