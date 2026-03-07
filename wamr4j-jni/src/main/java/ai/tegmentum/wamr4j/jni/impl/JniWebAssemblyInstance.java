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

import ai.tegmentum.wamr4j.RunningMode;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.WebAssemblyTable;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly instance.
 * 
 * <p>This class represents an instantiated WebAssembly module using JNI
 * to communicate with the native WAMR library. It provides access to
 * functions, memory, and global variables.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyInstance implements WebAssemblyInstance {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyInstance.class.getName());
    
    // Native instance handle
    private volatile long nativeHandle;

    // Native registration handle for host function imports (0 if no imports)
    private volatile long registrationHandle;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Child resources that need cleanup on close
    private final List<JniWebAssemblyFunction> childFunctions = new ArrayList<>();
    private final List<JniWebAssemblyMemory> childMemories = new ArrayList<>();
    private final List<JniWebAssemblyTable> childTables = new ArrayList<>();

    /**
     * Creates a new JNI WebAssembly instance wrapper without imports.
     *
     * @param nativeHandle the native instance handle, must not be 0
     */
    public JniWebAssemblyInstance(final long nativeHandle) {
        this(nativeHandle, 0L);
    }

    /**
     * Creates a new JNI WebAssembly instance wrapper with optional import registration.
     *
     * @param nativeHandle the native instance handle, must not be 0
     * @param registrationHandle the native registration handle, 0 if no imports
     */
    public JniWebAssemblyInstance(final long nativeHandle, final long registrationHandle) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native instance handle cannot be 0");
        }
        this.nativeHandle = nativeHandle;
        this.registrationHandle = registrationHandle;
        LOGGER.fine("Created JNI WebAssembly instance with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyFunction getFunction(final String functionName) throws WasmRuntimeException {
        if (functionName == null || functionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        
        ensureNotClosed();
        
        try {
            final long functionHandle = nativeGetFunction(nativeHandle, functionName);
            if (functionHandle == 0L) {
                throw new WasmRuntimeException("Function not found: " + functionName);
            }

            final JniWebAssemblyFunction function =
                new JniWebAssemblyFunction(functionHandle, functionName, this);
            synchronized (childFunctions) {
                childFunctions.add(function);
            }
            return function;
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error getting function: " + functionName, e);
        }
    }

    @Override
    public WebAssemblyMemory getMemory() throws WasmRuntimeException {
        ensureNotClosed();
        
        try {
            final long memoryHandle = nativeGetMemory(nativeHandle);
            if (memoryHandle == 0L) {
                throw new WasmRuntimeException("Memory not exported by instance");
            }

            final JniWebAssemblyMemory memory = new JniWebAssemblyMemory(memoryHandle, this);
            synchronized (childMemories) {
                childMemories.add(memory);
            }
            return memory;
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error getting memory", e);
        }
    }

    @Override
    public Object getGlobal(final String globalName) throws WasmRuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }
        
        ensureNotClosed();
        
        try {
            return nativeGetGlobal(nativeHandle, globalName);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to get global variable: " + globalName, e);
        }
    }

    @Override
    public void setGlobal(final String globalName, final Object value) throws WasmRuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }
        
        ensureNotClosed();
        
        try {
            nativeSetGlobal(nativeHandle, globalName, value);
        } catch (final Exception e) {
            throw new WasmRuntimeException("Failed to set global variable: " + globalName, e);
        }
    }

    @Override
    public String[] getFunctionNames() {
        ensureNotClosed();
        
        try {
            final String[] functions = nativeGetFunctionNames(nativeHandle);
            return functions != null ? functions : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get function names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public String[] getGlobalNames() {
        ensureNotClosed();
        
        try {
            final String[] globals = nativeGetGlobalNames(nativeHandle);
            return globals != null ? globals : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get global names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public boolean hasMemory() {
        ensureNotClosed();

        try {
            return nativeHasMemory(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to check memory availability: " + e.getMessage());
            return false;
        }
    }

    @Override
    public WebAssemblyTable getTable(final String tableName) throws WasmRuntimeException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        ensureNotClosed();

        try {
            final long tableHandle = nativeGetTable(nativeHandle, tableName);
            if (tableHandle == 0L) {
                throw new WasmRuntimeException("Table not found: " + tableName);
            }

            final JniWebAssemblyTable table =
                new JniWebAssemblyTable(tableHandle, tableName, this);
            synchronized (childTables) {
                childTables.add(table);
            }
            return table;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error getting table: " + tableName, e);
        }
    }

    @Override
    public String[] getTableNames() {
        ensureNotClosed();

        try {
            final String[] tables = nativeGetTableNames(nativeHandle);
            return tables != null ? tables : new String[0];
        } catch (final Exception e) {
            LOGGER.warning("Failed to get table names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public boolean setRunningMode(final RunningMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Running mode cannot be null");
        }
        ensureNotClosed();
        return nativeSetRunningMode(nativeHandle, mode.getNativeValue());
    }

    @Override
    public RunningMode getRunningMode() {
        ensureNotClosed();
        final int modeValue = nativeGetRunningMode(nativeHandle);
        return RunningMode.fromNativeValue(modeValue);
    }

    @Override
    public boolean setBoundsChecks(final boolean enable) {
        ensureNotClosed();
        return nativeSetBoundsChecks(nativeHandle, enable);
    }

    @Override
    public boolean isBoundsChecksEnabled() {
        ensureNotClosed();
        return nativeIsBoundsChecksEnabled(nativeHandle);
    }

    @Override
    public long moduleMalloc(final long size) throws WasmRuntimeException {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }

        ensureNotClosed();

        try {
            final long offset = nativeModuleMalloc(nativeHandle, size);
            if (offset == 0L) {
                throw new WasmRuntimeException("Module malloc failed for size: " + size);
            }
            return offset;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error in moduleMalloc", e);
        }
    }

    @Override
    public void moduleFree(final long offset) {
        ensureNotClosed();

        try {
            nativeModuleFree(nativeHandle, offset);
        } catch (final Exception e) {
            LOGGER.warning("Failed to free module memory at offset " + offset + ": " + e.getMessage());
        }
    }

    @Override
    public long moduleDupData(final byte[] data) throws WasmRuntimeException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        ensureNotClosed();

        try {
            final long offset = nativeModuleDupData(nativeHandle, data);
            if (offset == 0L) {
                throw new WasmRuntimeException("Module dup data failed for " + data.length + " bytes");
            }
            return offset;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error in moduleDupData", e);
        }
    }

    @Override
    public boolean validateAppAddr(final long appOffset, final long size) {
        ensureNotClosed();

        try {
            return nativeValidateAppAddr(nativeHandle, appOffset, size);
        } catch (final Exception e) {
            LOGGER.warning("Failed to validate app addr: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean validateAppStrAddr(final long appStrOffset) {
        ensureNotClosed();

        try {
            return nativeValidateAppStrAddr(nativeHandle, appStrOffset);
        } catch (final Exception e) {
            LOGGER.warning("Failed to validate app str addr: " + e.getMessage());
            return false;
        }
    }

    @Override
    public WebAssemblyMemory getMemoryByIndex(final int index) throws WasmRuntimeException {
        if (index < 0) {
            throw new IllegalArgumentException("Memory index cannot be negative: " + index);
        }

        ensureNotClosed();

        try {
            final long memoryHandle = nativeGetMemoryByIndex(nativeHandle, index);
            if (memoryHandle == 0L) {
                throw new WasmRuntimeException("No memory at index: " + index);
            }

            final JniWebAssemblyMemory memory = new JniWebAssemblyMemory(memoryHandle, this);
            synchronized (childMemories) {
                childMemories.add(memory);
            }
            return memory;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error getting memory at index: " + index, e);
        }
    }

    @Override
    public String getException() {
        ensureNotClosed();

        try {
            return nativeGetException(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get exception: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void setException(final String exception) {
        if (exception == null) {
            throw new IllegalArgumentException("Exception message cannot be null");
        }

        ensureNotClosed();

        try {
            nativeSetException(nativeHandle, exception);
        } catch (final Exception e) {
            LOGGER.warning("Failed to set exception: " + e.getMessage());
        }
    }

    @Override
    public void clearException() {
        ensureNotClosed();

        try {
            nativeClearException(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to clear exception: " + e.getMessage());
        }
    }

    @Override
    public void terminate() {
        ensureNotClosed();

        try {
            nativeTerminate(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to terminate instance: " + e.getMessage());
        }
    }

    @Override
    public void setInstructionCountLimit(final long limit) {
        ensureNotClosed();

        try {
            nativeSetInstructionCountLimit(nativeHandle, limit);
        } catch (final Exception e) {
            LOGGER.warning("Failed to set instruction count limit: " + e.getMessage());
        }
    }

    @Override
    public boolean isWasiMode() {
        ensureNotClosed();

        try {
            return nativeIsWasiMode(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to check WASI mode: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getWasiExitCode() {
        ensureNotClosed();

        try {
            return nativeGetWasiExitCode(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get WASI exit code: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean hasWasiStartFunction() {
        ensureNotClosed();

        try {
            return nativeHasWasiStartFunction(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to check WASI start function: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean executeMain(final String[] argv) {
        ensureNotClosed();

        try {
            return nativeExecuteMain(nativeHandle, argv != null ? argv : new String[0]);
        } catch (final Exception e) {
            LOGGER.warning("Failed to execute main: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean executeFunc(final String name, final String[] argv) {
        if (name == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }

        ensureNotClosed();

        try {
            return nativeExecuteFunc(nativeHandle, name, argv != null ? argv : new String[0]);
        } catch (final Exception e) {
            LOGGER.warning("Failed to execute function '" + name + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setCustomData(final long customData) {
        ensureNotClosed();

        try {
            nativeSetCustomData(nativeHandle, customData);
        } catch (final Exception e) {
            LOGGER.warning("Failed to set custom data: " + e.getMessage());
        }
    }

    @Override
    public long getCustomData() {
        ensureNotClosed();

        try {
            return nativeGetCustomData(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get custom data: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public String getCallStack() {
        ensureNotClosed();

        try {
            return nativeGetCallStack(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get call stack: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void dumpCallStack() {
        ensureNotClosed();

        try {
            nativeDumpCallStack(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to dump call stack: " + e.getMessage());
        }
    }

    @Override
    public void dumpPerfProfiling() {
        ensureNotClosed();

        try {
            nativeDumpPerfProfiling(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to dump perf profiling: " + e.getMessage());
        }
    }

    @Override
    public double sumWasmExecTime() {
        ensureNotClosed();

        try {
            return nativeSumExecTime(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get sum exec time: " + e.getMessage());
            return 0.0;
        }
    }

    @Override
    public double getWasmFuncExecTime(final String funcName) {
        if (funcName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }

        ensureNotClosed();

        try {
            return nativeGetFuncExecTime(nativeHandle, funcName);
        } catch (final Exception e) {
            LOGGER.warning("Failed to get func exec time: " + e.getMessage());
            return 0.0;
        }
    }

    @Override
    public void dumpMemConsumption() {
        ensureNotClosed();

        try {
            nativeDumpMemConsumption(nativeHandle);
        } catch (final Exception e) {
            LOGGER.warning("Failed to dump mem consumption: " + e.getMessage());
        }
    }

    @Override
    public void setContext(final long key, final long ctx) {
        ensureNotClosed();
        if (key != 0) {
            nativeSetContext(nativeHandle, key, ctx);
        }
    }

    @Override
    public long getContext(final long key) {
        ensureNotClosed();
        if (key == 0) {
            return 0;
        }
        return nativeGetContext(nativeHandle, key);
    }

    @Override
    public boolean lookupMemory(final String name) {
        ensureNotClosed();
        if (name == null) {
            return false;
        }
        return nativeLookupMemory(nativeHandle, name);
    }

    @Override
    public boolean beginBlockingOp() {
        ensureNotClosed();
        return nativeBeginBlockingOp(nativeHandle);
    }

    @Override
    public void endBlockingOp() {
        ensureNotClosed();
        nativeEndBlockingOp(nativeHandle);
    }

    @Override
    public boolean detectNativeStackOverflow() {
        ensureNotClosed();
        return nativeDetectNativeStackOverflow(nativeHandle);
    }

    @Override
    public boolean detectNativeStackOverflowSize(final int requiredSize) {
        ensureNotClosed();
        return nativeDetectNativeStackOverflowSize(nativeHandle, requiredSize);
    }

    @Override
    public boolean validateNativeAddr(final long nativeAddr, final long size) {
        ensureNotClosed();
        try {
            return nativeValidateNativeAddr(nativeHandle, nativeAddr, size);
        } catch (final Exception e) {
            LOGGER.warning("Failed to validate native addr: " + e.getMessage());
            return false;
        }
    }

    @Override
    public long addrAppToNative(final long appOffset) {
        ensureNotClosed();
        return nativeAddrAppToNative(nativeHandle, appOffset);
    }

    @Override
    public long addrNativeToApp(final long nativeAddr) {
        ensureNotClosed();
        return nativeAddrNativeToApp(nativeHandle, nativeAddr);
    }

    @Override
    public void setContextSpread(final long key, final long ctx) {
        ensureNotClosed();
        if (key != 0) {
            nativeSetContextSpread(nativeHandle, key, ctx);
        }
    }

    @Override
    public long spawnExecEnv() {
        ensureNotClosed();
        return nativeSpawnExecEnv(nativeHandle);
    }

    @Override
    public void destroySpawnedExecEnv(final long execEnv) {
        ensureNotClosed();
        if (execEnv != 0) {
            nativeDestroySpawnedExecEnv(execEnv);
        }
    }

    @Override
    public int[][] copyCallstack(final int maxFrames, final int skip) {
        if (maxFrames <= 0) {
            throw new IllegalArgumentException("maxFrames must be positive: " + maxFrames);
        }
        ensureNotClosed();
        try {
            return nativeCopyCallstack(nativeHandle, maxFrames, skip);
        } catch (final Exception e) {
            LOGGER.warning("Failed to copy callstack: " + e.getMessage());
            return null;
        }
    }

    @Override
    public int externrefObj2Ref(final long externObj) {
        ensureNotClosed();
        return nativeExternrefObj2Ref(nativeHandle, externObj);
    }

    @Override
    public void externrefObjDel(final long externObj) {
        ensureNotClosed();
        nativeExternrefObjDel(nativeHandle, externObj);
    }

    @Override
    public boolean attachSharedHeap(final long heapHandle) {
        ensureNotClosed();
        return nativeAttachSharedHeap(nativeHandle, heapHandle);
    }

    @Override
    public void detachSharedHeap() {
        ensureNotClosed();
        nativeDetachSharedHeap(nativeHandle);
    }

    @Override
    public long sharedHeapMalloc(final long size) {
        ensureNotClosed();
        return nativeSharedHeapMalloc(nativeHandle, size);
    }

    @Override
    public void sharedHeapFree(final long ptr) {
        ensureNotClosed();
        nativeSharedHeapFree(nativeHandle, ptr);
    }

    @Override
    public long[] getNativeAddrRange(final long nativePtr) {
        ensureNotClosed();
        return nativeGetNativeAddrRange(nativeHandle, nativePtr);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // Close child resources before destroying the instance
            synchronized (childMemories) {
                for (final JniWebAssemblyMemory memory : childMemories) {
                    try {
                        memory.close();
                    } catch (final Exception e) {
                        LOGGER.warning("Error closing child memory: " + e.getMessage());
                    }
                }
                childMemories.clear();
            }

            synchronized (childFunctions) {
                for (final JniWebAssemblyFunction function : childFunctions) {
                    try {
                        function.close();
                    } catch (final Exception e) {
                        LOGGER.warning("Error closing child function: " + e.getMessage());
                    }
                }
                childFunctions.clear();
            }

            synchronized (childTables) {
                for (final JniWebAssemblyTable table : childTables) {
                    try {
                        table.close();
                    } catch (final Exception e) {
                        LOGGER.warning("Error closing child table: " + e.getMessage());
                    }
                }
                childTables.clear();
            }

            final long handle = nativeHandle;
            final long regHandle = registrationHandle;
            nativeHandle = 0L;
            registrationHandle = 0L;

            if (handle != 0L) {
                try {
                    nativeDestroyInstance(handle);
                    LOGGER.fine("Destroyed JNI WebAssembly instance with handle: " + handle);
                } catch (final Exception e) {
                    LOGGER.warning("Error destroying native instance: " + e.getMessage());
                }
            }

            // Destroy import registration AFTER instance (host functions may still be referenced)
            if (regHandle != 0L) {
                try {
                    nativeDestroyRegistration(regHandle);
                    LOGGER.fine("Destroyed host function registration with handle: " + regHandle);
                } catch (final Exception e) {
                    LOGGER.warning("Error destroying host function registration: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Internal method to check if the instance is valid.
     * Used by Function and Memory objects.
     * 
     * @return true if the instance is valid, false otherwise
     */
    boolean isValid() {
        return !closed.get() && nativeHandle != 0L;
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly instance has been closed");
        }
    }

    // Native method declarations
    
    /**
     * Destroys a native WebAssembly instance.
     * 
     * @param instanceHandle the native instance handle
     */
    private static native void nativeDestroyInstance(long instanceHandle);

    /**
     * Gets a function from the instance.
     * 
     * @param instanceHandle the native instance handle
     * @param functionName the name of the function
     * @return the native function handle, or 0 if not found
     */
    private static native long nativeGetFunction(long instanceHandle, String functionName);

    /**
     * Gets the memory from the instance.
     * 
     * @param instanceHandle the native instance handle
     * @return the native memory handle, or 0 if not available
     */
    private static native long nativeGetMemory(long instanceHandle);

    /**
     * Gets a global variable value.
     * 
     * @param instanceHandle the native instance handle
     * @param globalName the name of the global variable
     * @return the global variable value
     * @throws WasmRuntimeException if the global is not found
     */
    private static native Object nativeGetGlobal(long instanceHandle, String globalName) throws WasmRuntimeException;

    /**
     * Sets a global variable value.
     * 
     * @param instanceHandle the native instance handle
     * @param globalName the name of the global variable
     * @param value the new value for the global variable
     * @throws WasmRuntimeException if the global is not found or cannot be set
     */
    private static native void nativeSetGlobal(long instanceHandle, String globalName, Object value) 
        throws WasmRuntimeException;

    /**
     * Gets the names of all exported functions.
     * 
     * @param instanceHandle the native instance handle
     * @return array of function names, or null on failure
     */
    private static native String[] nativeGetFunctionNames(long instanceHandle);

    /**
     * Gets the names of all exported global variables.
     * 
     * @param instanceHandle the native instance handle
     * @return array of global names, or null on failure
     */
    private static native String[] nativeGetGlobalNames(long instanceHandle);

    /**
     * Checks if the instance exports memory.
     *
     * @param instanceHandle the native instance handle
     * @return true if memory is available, false otherwise
     */
    private static native boolean nativeHasMemory(long instanceHandle);

    /**
     * Gets an exported table by name.
     *
     * @param instanceHandle the native instance handle
     * @param tableName the name of the table
     * @return the native table handle, or 0 if not found
     */
    private static native long nativeGetTable(long instanceHandle, String tableName);

    /**
     * Gets the names of all exported tables.
     *
     * @param instanceHandle the native instance handle
     * @return array of table names, or null on failure
     */
    private static native String[] nativeGetTableNames(long instanceHandle);

    /**
     * Sets the running mode for the instance.
     *
     * @param instanceHandle the native instance handle
     * @param mode the native running mode constant
     * @return true if set successfully
     */
    private static native boolean nativeSetRunningMode(long instanceHandle, int mode);

    /**
     * Gets the running mode for the instance.
     *
     * @param instanceHandle the native instance handle
     * @return the native running mode constant
     */
    private static native int nativeGetRunningMode(long instanceHandle);

    /**
     * Enables or disables bounds checks for the instance.
     *
     * @param instanceHandle the native instance handle
     * @param enable true to enable, false to disable
     * @return true if applied successfully
     */
    private static native boolean nativeSetBoundsChecks(long instanceHandle, boolean enable);

    /**
     * Checks if bounds checks are enabled for the instance.
     *
     * @param instanceHandle the native instance handle
     * @return true if bounds checks are enabled
     */
    private static native boolean nativeIsBoundsChecksEnabled(long instanceHandle);

    /**
     * Allocates memory from the module instance's internal heap.
     *
     * @param instanceHandle the native instance handle
     * @param size the number of bytes to allocate
     * @return the application offset, or 0 on failure
     */
    private static native long nativeModuleMalloc(long instanceHandle, long size);

    /**
     * Frees memory previously allocated by nativeModuleMalloc.
     *
     * @param instanceHandle the native instance handle
     * @param offset the application offset to free
     */
    private static native void nativeModuleFree(long instanceHandle, long offset);

    /**
     * Copies data into the module instance's memory.
     *
     * @param instanceHandle the native instance handle
     * @param data the data to copy
     * @return the application offset, or 0 on failure
     */
    private static native long nativeModuleDupData(long instanceHandle, byte[] data);

    /**
     * Validates an application address range.
     *
     * @param instanceHandle the native instance handle
     * @param appOffset the application offset
     * @param size the size of the range
     * @return true if valid
     */
    private static native boolean nativeValidateAppAddr(long instanceHandle, long appOffset, long size);

    /**
     * Validates an application string address.
     *
     * @param instanceHandle the native instance handle
     * @param appStrOffset the application string offset
     * @return true if valid
     */
    private static native boolean nativeValidateAppStrAddr(long instanceHandle, long appStrOffset);

    /**
     * Gets a memory instance by index.
     *
     * @param instanceHandle the native instance handle
     * @param index the zero-based memory index
     * @return the native memory handle, or 0 if not found
     */
    private static native long nativeGetMemoryByIndex(long instanceHandle, int index);

    /**
     * Gets the current exception on the instance.
     *
     * @param instanceHandle the native instance handle
     * @return the exception message, or null if no exception
     */
    private static native String nativeGetException(long instanceHandle);

    /**
     * Sets a custom exception on the instance.
     *
     * @param instanceHandle the native instance handle
     * @param exception the exception message
     */
    private static native void nativeSetException(long instanceHandle, String exception);

    /**
     * Clears the current exception on the instance.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeClearException(long instanceHandle);

    /**
     * Terminates execution of the instance.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeTerminate(long instanceHandle);

    /**
     * Sets the instruction count limit for the instance's execution environment.
     *
     * @param instanceHandle the native instance handle
     * @param limit the instruction count limit, or -1 to remove
     */
    private static native void nativeSetInstructionCountLimit(long instanceHandle, long limit);

    /**
     * Checks if the instance is in WASI mode.
     *
     * @param instanceHandle the native instance handle
     * @return true if in WASI mode
     */
    private static native boolean nativeIsWasiMode(long instanceHandle);

    /**
     * Gets the WASI exit code.
     *
     * @param instanceHandle the native instance handle
     * @return the WASI exit code
     */
    private static native int nativeGetWasiExitCode(long instanceHandle);

    /**
     * Checks if a WASI _start function exists.
     *
     * @param instanceHandle the native instance handle
     * @return true if _start function exists
     */
    private static native boolean nativeHasWasiStartFunction(long instanceHandle);

    /**
     * Executes the WASI _start function.
     *
     * @param instanceHandle the native instance handle
     * @param argv command-line arguments
     * @return true if execution succeeded
     */
    private static native boolean nativeExecuteMain(long instanceHandle, String[] argv);

    /**
     * Executes a named function with string arguments.
     *
     * @param instanceHandle the native instance handle
     * @param name the function name
     * @param argv string arguments
     * @return true if execution succeeded
     */
    private static native boolean nativeExecuteFunc(long instanceHandle, String name, String[] argv);

    /**
     * Sets custom data on the instance.
     *
     * @param instanceHandle the native instance handle
     * @param customData the custom data value
     */
    private static native void nativeSetCustomData(long instanceHandle, long customData);

    /**
     * Gets custom data from the instance.
     *
     * @param instanceHandle the native instance handle
     * @return the custom data value
     */
    private static native long nativeGetCustomData(long instanceHandle);

    /**
     * Gets the call stack as a string.
     *
     * @param instanceHandle the native instance handle
     * @return the call stack string, or null if unavailable
     */
    private static native String nativeGetCallStack(long instanceHandle);

    /**
     * Dumps call stack to stdout.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeDumpCallStack(long instanceHandle);

    /**
     * Dumps performance profiling data to stdout.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeDumpPerfProfiling(long instanceHandle);

    /**
     * Gets total WASM execution time in milliseconds.
     *
     * @param instanceHandle the native instance handle
     * @return the total execution time
     */
    private static native double nativeSumExecTime(long instanceHandle);

    /**
     * Gets execution time for a specific function in milliseconds.
     *
     * @param instanceHandle the native instance handle
     * @param funcName the function name
     * @return the execution time
     */
    private static native double nativeGetFuncExecTime(long instanceHandle, String funcName);

    /**
     * Dumps memory consumption to stdout.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeDumpMemConsumption(long instanceHandle);

    /**
     * Destroys a host function registration.
     *
     * @param registrationHandle the native registration handle
     */
    static native void nativeDestroyRegistration(long registrationHandle);

    /**
     * Lookup a memory instance by export name.
     *
     * @param instanceHandle the native instance handle
     * @param name the export name
     * @return true if found
     */
    private static native boolean nativeLookupMemory(long instanceHandle, String name);

    /**
     * Begin a blocking operation.
     *
     * @param instanceHandle the native instance handle
     * @return true on success
     */
    private static native boolean nativeBeginBlockingOp(long instanceHandle);

    /**
     * End a blocking operation.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeEndBlockingOp(long instanceHandle);

    /**
     * Detect native stack overflow.
     *
     * @param instanceHandle the native instance handle
     * @return true if overflow detected
     */
    private static native boolean nativeDetectNativeStackOverflow(long instanceHandle);

    /**
     * Detect native stack overflow with required size.
     *
     * @param instanceHandle the native instance handle
     * @param requiredSize the required stack size
     * @return true if overflow detected
     */
    private static native boolean nativeDetectNativeStackOverflowSize(long instanceHandle, int requiredSize);

    /**
     * Set context on an instance.
     *
     * @param instanceHandle the native instance handle
     * @param key the context key handle
     * @param ctx the context value
     */
    private static native void nativeSetContext(long instanceHandle, long key, long ctx);

    /**
     * Get context from an instance.
     *
     * @param instanceHandle the native instance handle
     * @param key the context key handle
     * @return the context value
     */
    private static native long nativeGetContext(long instanceHandle, long key);

    /**
     * Validates a native address range.
     *
     * @param instanceHandle the native instance handle
     * @param nativeAddr the native address
     * @param size the size of the range
     * @return true if valid
     */
    private static native boolean nativeValidateNativeAddr(long instanceHandle, long nativeAddr, long size);

    /**
     * Converts an application offset to a native address.
     *
     * @param instanceHandle the native instance handle
     * @param appOffset the application offset
     * @return the native address
     */
    private static native long nativeAddrAppToNative(long instanceHandle, long appOffset);

    /**
     * Converts a native address to an application offset.
     *
     * @param instanceHandle the native instance handle
     * @param nativeAddr the native address
     * @return the application offset
     */
    private static native long nativeAddrNativeToApp(long instanceHandle, long nativeAddr);

    /**
     * Sets context on an instance with spread semantics.
     *
     * @param instanceHandle the native instance handle
     * @param key the context key handle
     * @param ctx the context value
     */
    private static native void nativeSetContextSpread(long instanceHandle, long key, long ctx);

    /**
     * Spawns a new execution environment from the instance.
     *
     * @param instanceHandle the native instance handle
     * @return the spawned exec env handle, or 0 on failure
     */
    private static native long nativeSpawnExecEnv(long instanceHandle);

    /**
     * Destroys a spawned execution environment.
     *
     * @param execEnv the exec env handle
     */
    private static native void nativeDestroySpawnedExecEnv(long execEnv);

    /**
     * Copies the current call stack frames.
     *
     * @param instanceHandle the native instance handle
     * @param maxFrames the maximum number of frames to capture
     * @param skip the number of frames to skip
     * @return array of frames, each as [funcIndex, moduleNameIdx], or null
     */
    private static native int[][] nativeCopyCallstack(long instanceHandle, int maxFrames, int skip);

    /**
     * Converts a native object pointer to an externref index.
     *
     * @param instanceHandle the native instance handle
     * @param externObj the native object pointer
     * @return the externref index
     */
    private static native int nativeExternrefObj2Ref(long instanceHandle, long externObj);

    /**
     * Deletes a native object from the externref map.
     *
     * @param instanceHandle the native instance handle
     * @param externObj the native object pointer
     */
    private static native void nativeExternrefObjDel(long instanceHandle, long externObj);

    /**
     * Attaches a shared heap to the instance.
     *
     * @param instanceHandle the native instance handle
     * @param heapHandle the shared heap handle
     * @return true if attached successfully
     */
    private static native boolean nativeAttachSharedHeap(long instanceHandle, long heapHandle);

    /**
     * Detaches the shared heap from the instance.
     *
     * @param instanceHandle the native instance handle
     */
    private static native void nativeDetachSharedHeap(long instanceHandle);

    /**
     * Allocates memory from the shared heap.
     *
     * @param instanceHandle the native instance handle
     * @param size the number of bytes to allocate
     * @return the allocated address, or 0 on failure
     */
    private static native long nativeSharedHeapMalloc(long instanceHandle, long size);

    /**
     * Frees memory previously allocated from the shared heap.
     *
     * @param instanceHandle the native instance handle
     * @param ptr the address to free
     */
    private static native void nativeSharedHeapFree(long instanceHandle, long ptr);

    /**
     * Gets the native address range containing a pointer.
     *
     * @param instanceHandle the native instance handle
     * @param nativePtr the native pointer to query
     * @return a two-element long array [start, end], or null if not found
     */
    private static native long[] nativeGetNativeAddrRange(long instanceHandle, long nativePtr);

    /**
     * Static helper for destroying a registration handle from JniWebAssemblyModule
     * (used for cleanup on instantiation failure).
     *
     * @param registrationHandle the native registration handle
     */
    static void destroyRegistration(final long registrationHandle) {
        if (registrationHandle != 0L) {
            nativeDestroyRegistration(registrationHandle);
        }
    }
}