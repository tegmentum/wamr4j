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
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
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

    // Cached ByteBuffer for performance
    private volatile ByteBuffer cachedBuffer;

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle GET_SIZE;
        static final MethodHandle GET_DATA;
        static final MethodHandle GROW;

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            GET_SIZE = linker.downcallHandle(
                lookup.find("wamr_memory_size").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            GET_DATA = linker.downcallHandle(
                lookup.find("wamr_memory_data").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            final var growSymbol = lookup.find("wamr_memory_grow");
            GROW = growSymbol.isPresent()
                ? linker.downcallHandle(growSymbol.get(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG))
                : null;
        }
    }

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
            final long size = (long) Handles.GET_SIZE.invoke(nativeHandle);
            if (size < 0) {
                throw new WasmRuntimeException("Invalid memory size returned: " + size);
            }
            
            return (int) size;
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error getting memory size", e);
        }
    }

    @Override
    public ByteBuffer asByteBuffer() {
        ensureNotClosed();

        final ByteBuffer cached = cachedBuffer;
        if (cached != null) {
            return cached;
        }

        try {
            final MemorySegment dataPtr = (MemorySegment) Handles.GET_DATA.invoke(nativeHandle);
            if (dataPtr.equals(MemorySegment.NULL)) {
                throw new WasmRuntimeException("Failed to get memory data pointer");
            }

            final long size = size();
            final MemorySegment memorySegment = dataPtr.reinterpret(size);

            final ByteBuffer buffer = memorySegment.asByteBuffer();
            cachedBuffer = buffer;
            return buffer;
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error getting memory buffer", e);
        }
    }

    @Override
    public int readInt32(final int offset) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            return asByteBuffer().getInt(offset);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to read int32 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt32(final int offset, final int value) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            asByteBuffer().putInt(offset, value);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to write int32 at offset " + offset, e);
        }
    }

    @Override
    public long readInt64(final int offset) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            return asByteBuffer().getLong(offset);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to read int64 at offset " + offset, e);
        }
    }

    @Override
    public void writeInt64(final int offset, final long value) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            asByteBuffer().putLong(offset, value);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to write int64 at offset " + offset, e);
        }
    }

    @Override
    public float readFloat32(final int offset) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            return asByteBuffer().getFloat(offset);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to read float32 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat32(final int offset, final float value) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 4);
        
        try {
            asByteBuffer().putFloat(offset, value);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to write float32 at offset " + offset, e);
        }
    }

    @Override
    public double readFloat64(final int offset) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            return asByteBuffer().getDouble(offset);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to read float64 at offset " + offset, e);
        }
    }

    @Override
    public void writeFloat64(final int offset, final double value) throws WasmRuntimeException {
        ensureNotClosed();
        validateOffset(offset, 8);
        
        try {
            asByteBuffer().putDouble(offset, value);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to write float64 at offset " + offset, e);
        }
    }

    @Override
    public int grow(final int pages) {
        if (pages < 0) {
            throw new IllegalArgumentException("Page count cannot be negative: " + pages);
        }

        ensureNotClosed();

        if (Handles.GROW == null) {
            throw new UnsupportedOperationException("Memory growth is not supported");
        }

        try {
            final int result = (int) Handles.GROW.invoke(nativeHandle, (long) pages);
            if (result >= 0) {
                cachedBuffer = null; // Invalidate cached buffer after successful grow
            }
            return result; // Returns previous page count, or -1 on failure
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error growing memory", e);
        }
    }

    // Memory is owned by the instance and has no separate native destroy
    void close() {
        closed.set(true);
        cachedBuffer = null;
        nativeHandle = MemorySegment.NULL;
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly memory has been closed");
        }
    }
    
    private void validateOffset(final long offset, final int size) throws WasmRuntimeException {
        if (offset < 0) {
            throw new IllegalArgumentException("Memory offset cannot be negative: " + offset);
        }
        
        final long memorySize = size();
        if (offset + size > memorySize) {
            throw new WasmRuntimeException(
                String.format("Memory access out of bounds: offset=%d, size=%d, memorySize=%d", 
                    offset, size, memorySize));
        }
    }
    
    @Override
    public byte[] read(final int offset, final int length) throws WasmRuntimeException {
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
            throw new WasmRuntimeException("Failed to read " + length + " bytes at offset " + offset, e);
        }
    }

    @Override
    public void write(final int offset, final byte[] data) throws WasmRuntimeException {
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
            throw new WasmRuntimeException("Failed to write " + data.length + " bytes at offset " + offset, e);
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