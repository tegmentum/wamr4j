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
import java.nio.ByteOrder;
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

    // Parent instance for lifecycle tracking
    private final PanamaWebAssemblyInstance parentInstance;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Cached ByteBuffer for performance
    private volatile ByteBuffer cachedBuffer;

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle GET_SIZE;
        static final MethodHandle GET_DATA;
        static final MethodHandle GROW;
        static final MethodHandle PAGE_COUNT;
        static final MethodHandle MAX_PAGE_COUNT;
        static final MethodHandle IS_SHARED;
        static final MethodHandle DESTROY_MEMORY;
        static final MethodHandle BASE_ADDRESS;
        static final MethodHandle BYTES_PER_PAGE;

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
            PAGE_COUNT = linker.downcallHandle(
                lookup.find("wamr_memory_page_count").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            MAX_PAGE_COUNT = linker.downcallHandle(
                lookup.find("wamr_memory_max_page_count").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            IS_SHARED = linker.downcallHandle(
                lookup.find("wamr_memory_is_shared").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            DESTROY_MEMORY = linker.downcallHandle(
                lookup.find("wamr_memory_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            final var baseAddrSymbol = lookup.find("wamr_memory_base_address");
            BASE_ADDRESS = baseAddrSymbol.isPresent()
                ? linker.downcallHandle(baseAddrSymbol.get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                : null;
            final var bytesPerPageSymbol = lookup.find("wamr_memory_bytes_per_page");
            BYTES_PER_PAGE = bytesPerPageSymbol.isPresent()
                ? linker.downcallHandle(bytesPerPageSymbol.get(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS))
                : null;
        }
    }

    /**
     * Creates a new Panama WebAssembly memory wrapper.
     *
     * @param nativeHandle the native memory handle, must not be NULL
     * @param parentInstance the parent instance that owns this memory
     */
    public PanamaWebAssemblyMemory(final MemorySegment nativeHandle,
            final PanamaWebAssemblyInstance parentInstance) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native memory handle cannot be null or NULL");
        }
        if (parentInstance == null) {
            throw new IllegalArgumentException("Parent instance cannot be null");
        }
        this.nativeHandle = nativeHandle;
        this.parentInstance = parentInstance;
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

            final ByteBuffer buffer = memorySegment.asByteBuffer()
                .order(ByteOrder.LITTLE_ENDIAN);
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

    void close() {
        if (closed.compareAndSet(false, true)) {
            cachedBuffer = null;
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;
            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_MEMORY.invoke(handle);
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native memory: " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get() || parentInstance.isClosed()) {
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
            buffer.get(offset, result, 0, length);
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
            buffer.put(offset, data, 0, data.length);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to write " + data.length + " bytes at offset " + offset, e);
        }
    }

    @Override
    public int pageCount() {
        ensureNotClosed();
        try {
            final long pages = (long) Handles.PAGE_COUNT.invoke(nativeHandle);
            return (int) pages;
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error getting page count", e);
        }
    }

    @Override
    public int maxPageCount() {
        ensureNotClosed();
        try {
            final long maxPages = (long) Handles.MAX_PAGE_COUNT.invoke(nativeHandle);
            return (int) maxPages;
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error getting max page count", e);
        }
    }

    @Override
    public boolean isShared() {
        ensureNotClosed();
        try {
            final int shared = (int) Handles.IS_SHARED.invoke(nativeHandle);
            return shared != 0;
        } catch (final Throwable e) {
            throw new IllegalStateException("Unexpected error checking shared status", e);
        }
    }

    @Override
    public long getBaseAddress() {
        ensureNotClosed();

        if (Handles.BASE_ADDRESS == null) {
            return 0L;
        }

        try {
            final MemorySegment addr = (MemorySegment) Handles.BASE_ADDRESS.invoke(nativeHandle);
            if (addr.equals(MemorySegment.NULL)) {
                return 0L;
            }
            return addr.address();
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get base address: " + e.getMessage());
            return 0L;
        }
    }

    @Override
    public long getBytesPerPage() {
        ensureNotClosed();

        if (Handles.BYTES_PER_PAGE == null) {
            return 65536L;
        }

        try {
            return (long) Handles.BYTES_PER_PAGE.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get bytes per page: " + e.getMessage());
            return 65536L;
        }
    }

    @Override
    public boolean isValid() {
        return !parentInstance.isClosed() && !closed.get()
            && nativeHandle != null && !nativeHandle.equals(MemorySegment.NULL);
    }
}