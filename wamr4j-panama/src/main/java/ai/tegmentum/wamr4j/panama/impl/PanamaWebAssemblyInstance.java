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

import ai.tegmentum.wamr4j.RunningMode;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.WebAssemblyTable;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly instance.
 * 
 * <p>This class represents an instantiated WebAssembly module using Panama FFI
 * to communicate with the native WAMR library. It provides access to exported
 * functions and memory with enhanced type safety.
 * 
 * @since 1.0.0
 */
public final class PanamaWebAssemblyInstance implements WebAssemblyInstance {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyInstance.class.getName());
    
    // Native instance handle as MemorySegment
    private volatile MemorySegment nativeHandle;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Child resources that need cleanup on close
    private final List<PanamaWebAssemblyMemory> childMemories = new ArrayList<>();
    private final List<PanamaWebAssemblyFunction> childFunctions = new ArrayList<>();
    private final List<PanamaWebAssemblyTable> childTables = new ArrayList<>();

    // Host function registration handles and their arena (null if no imports)
    private final List<MemorySegment> registrationHandles;
    private final Arena registrationArena;

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle DESTROY_INSTANCE;
        static final MethodHandle GET_FUNCTION;
        static final MethodHandle GET_MEMORY;
        static final MethodHandle HAS_MEMORY;
        static final MethodHandle GET_FUNCTION_NAMES;
        static final MethodHandle GET_GLOBAL_NAMES;
        static final MethodHandle FREE_NAMES;
        static final MethodHandle GET_GLOBAL;
        static final MethodHandle SET_GLOBAL;
        static final MethodHandle SET_RUNNING_MODE;
        static final MethodHandle GET_RUNNING_MODE;
        static final MethodHandle SET_BOUNDS_CHECKS;
        static final MethodHandle IS_BOUNDS_CHECKS_ENABLED;
        static final MethodHandle GET_TABLE;
        static final MethodHandle GET_TABLE_NAMES;
        static final MethodHandle MODULE_MALLOC;
        static final MethodHandle MODULE_FREE;
        static final MethodHandle MODULE_DUP_DATA;
        static final MethodHandle VALIDATE_APP_ADDR;
        static final MethodHandle VALIDATE_APP_STR_ADDR;
        static final MethodHandle GET_MEMORY_BY_INDEX;
        static final MethodHandle GET_EXCEPTION;
        static final MethodHandle SET_EXCEPTION;
        static final MethodHandle CLEAR_EXCEPTION;
        static final MethodHandle TERMINATE;
        static final MethodHandle SET_INSTRUCTION_COUNT_LIMIT;
        static final MethodHandle IS_WASI_MODE;
        static final MethodHandle GET_WASI_EXIT_CODE;
        static final MethodHandle HAS_WASI_START_FUNCTION;
        static final MethodHandle EXECUTE_MAIN;
        static final MethodHandle EXECUTE_FUNC;
        static final MethodHandle SET_CUSTOM_DATA;
        static final MethodHandle GET_CUSTOM_DATA;
        static final MethodHandle GET_CALL_STACK;
        static final MethodHandle DUMP_CALL_STACK;
        static final MethodHandle DUMP_PERF_PROFILING;
        static final MethodHandle SUM_EXEC_TIME;
        static final MethodHandle GET_FUNC_EXEC_TIME;
        static final MethodHandle DUMP_MEM_CONSUMPTION;
        static final MethodHandle LOOKUP_MEMORY;
        static final MethodHandle BEGIN_BLOCKING_OP;
        static final MethodHandle END_BLOCKING_OP;
        static final MethodHandle DETECT_NATIVE_STACK_OVERFLOW;
        static final MethodHandle DETECT_NATIVE_STACK_OVERFLOW_SIZE;
        static final MethodHandle SET_CONTEXT;
        static final MethodHandle GET_CONTEXT;

        private static final FunctionDescriptor NAMES_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
        private static final FunctionDescriptor FREE_NAMES_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
        private static final FunctionDescriptor GET_GLOBAL_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
        private static final FunctionDescriptor SET_GLOBAL_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            DESTROY_INSTANCE = linker.downcallHandle(
                lookup.find("wamr_instance_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            GET_FUNCTION = linker.downcallHandle(
                lookup.find("wamr_function_lookup").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_MEMORY = linker.downcallHandle(
                lookup.find("wamr_memory_get").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            HAS_MEMORY = linker.downcallHandle(
                lookup.find("wamr_instance_has_memory").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_FUNCTION_NAMES = resolveOptional(lookup, linker, "wamr_get_function_names", NAMES_DESC);
            GET_GLOBAL_NAMES = resolveOptional(lookup, linker, "wamr_get_global_names", NAMES_DESC);
            FREE_NAMES = resolveOptional(lookup, linker, "wamr_free_names", FREE_NAMES_DESC);
            GET_GLOBAL = resolveOptional(lookup, linker, "wamr_get_global", GET_GLOBAL_DESC);
            SET_GLOBAL = resolveOptional(lookup, linker, "wamr_set_global", SET_GLOBAL_DESC);
            SET_RUNNING_MODE = linker.downcallHandle(
                lookup.find("wamr_set_running_mode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_RUNNING_MODE = linker.downcallHandle(
                lookup.find("wamr_get_running_mode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            SET_BOUNDS_CHECKS = linker.downcallHandle(
                lookup.find("wamr_set_bounds_checks").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            IS_BOUNDS_CHECKS_ENABLED = linker.downcallHandle(
                lookup.find("wamr_is_bounds_checks_enabled").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_TABLE = resolveOptional(lookup, linker, "wamr_table_get",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_TABLE_NAMES = resolveOptional(lookup, linker, "wamr_get_table_names", NAMES_DESC);
            MODULE_MALLOC = resolveOptional(lookup, linker, "wamr_module_malloc",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            MODULE_FREE = resolveOptional(lookup, linker, "wamr_module_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            MODULE_DUP_DATA = resolveOptional(lookup, linker, "wamr_module_dup_data",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            VALIDATE_APP_ADDR = resolveOptional(lookup, linker, "wamr_validate_app_addr",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
            VALIDATE_APP_STR_ADDR = resolveOptional(lookup, linker, "wamr_validate_app_str_addr",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG));
            GET_MEMORY_BY_INDEX = resolveOptional(lookup, linker, "wamr_memory_get_by_index",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            GET_EXCEPTION = resolveOptional(lookup, linker, "wamr_instance_get_exception",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            SET_EXCEPTION = resolveOptional(lookup, linker, "wamr_instance_set_exception",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
            CLEAR_EXCEPTION = resolveOptional(lookup, linker, "wamr_instance_clear_exception",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            TERMINATE = resolveOptional(lookup, linker, "wamr_instance_terminate",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            SET_INSTRUCTION_COUNT_LIMIT = resolveOptional(lookup, linker,
                "wamr_instance_set_instruction_count_limit",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            IS_WASI_MODE = resolveOptional(lookup, linker,
                "wamr_instance_is_wasi_mode",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_WASI_EXIT_CODE = resolveOptional(lookup, linker,
                "wamr_instance_get_wasi_exit_code",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            HAS_WASI_START_FUNCTION = resolveOptional(lookup, linker,
                "wamr_instance_has_wasi_start_function",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            EXECUTE_MAIN = resolveOptional(lookup, linker,
                "wamr_instance_execute_main",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // instance
                    ValueLayout.ADDRESS,      // argv
                    ValueLayout.JAVA_INT));   // argc
            EXECUTE_FUNC = resolveOptional(lookup, linker,
                "wamr_instance_execute_func",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // instance
                    ValueLayout.ADDRESS,      // name
                    ValueLayout.ADDRESS,      // argv
                    ValueLayout.JAVA_INT));   // argc
            SET_CUSTOM_DATA = resolveOptional(lookup, linker,
                "wamr_instance_set_custom_data",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            GET_CUSTOM_DATA = resolveOptional(lookup, linker,
                "wamr_instance_get_custom_data",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            GET_CALL_STACK = resolveOptional(lookup, linker,
                "wamr_instance_get_call_stack",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            DUMP_CALL_STACK = resolveOptional(lookup, linker,
                "wamr_instance_dump_call_stack",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            DUMP_PERF_PROFILING = resolveOptional(lookup, linker,
                "wamr_instance_dump_perf_profiling",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            SUM_EXEC_TIME = resolveOptional(lookup, linker,
                "wamr_instance_sum_exec_time",
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
            GET_FUNC_EXEC_TIME = resolveOptional(lookup, linker,
                "wamr_instance_get_func_exec_time",
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            DUMP_MEM_CONSUMPTION = resolveOptional(lookup, linker,
                "wamr_instance_dump_mem_consumption",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            LOOKUP_MEMORY = resolveOptional(lookup, linker,
                "wamr_instance_lookup_memory",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            BEGIN_BLOCKING_OP = resolveOptional(lookup, linker,
                "wamr_instance_begin_blocking_op",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            END_BLOCKING_OP = resolveOptional(lookup, linker,
                "wamr_instance_end_blocking_op",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            DETECT_NATIVE_STACK_OVERFLOW = resolveOptional(lookup, linker,
                "wamr_instance_detect_native_stack_overflow",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            DETECT_NATIVE_STACK_OVERFLOW_SIZE = resolveOptional(lookup, linker,
                "wamr_instance_detect_native_stack_overflow_size",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            SET_CONTEXT = resolveOptional(lookup, linker,
                "wamr_set_context",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            GET_CONTEXT = resolveOptional(lookup, linker,
                "wamr_get_context",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        }

        private static MethodHandle resolveOptional(final SymbolLookup lookup, final Linker linker,
                final String name, final FunctionDescriptor desc) {
            final var symbol = lookup.find(name);
            return symbol.isPresent() ? linker.downcallHandle(symbol.get(), desc) : null;
        }
    }

    /**
     * Creates a new Panama WebAssembly instance wrapper without imports.
     *
     * @param nativeHandle the native instance handle, must not be NULL
     */
    public PanamaWebAssemblyInstance(final MemorySegment nativeHandle) {
        this(nativeHandle, Collections.emptyList(), null);
    }

    /**
     * Creates a new Panama WebAssembly instance wrapper with optional import registrations.
     *
     * @param nativeHandle the native instance handle, must not be NULL
     * @param registrationHandles native registration handles for host functions, may be empty
     * @param registrationArena the arena managing registration memory, may be null if no imports
     */
    PanamaWebAssemblyInstance(final MemorySegment nativeHandle,
            final List<MemorySegment> registrationHandles, final Arena registrationArena) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native instance handle cannot be null or NULL");
        }
        this.nativeHandle = nativeHandle;
        this.registrationHandles = new ArrayList<>(registrationHandles);
        this.registrationArena = registrationArena;
        LOGGER.fine("Created Panama WebAssembly instance with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyFunction getFunction(final String name) throws WasmRuntimeException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        
        ensureNotClosed();
        
        try (final Arena arena = Arena.ofConfined()) {
            // Allocate memory for function name and error buffer
            final MemorySegment nameBuffer = arena.allocateFrom(name);
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            final MemorySegment functionHandle = (MemorySegment) Handles.GET_FUNCTION.invoke(
                nativeHandle, nameBuffer, errorBuf, WasmTypes.ERROR_BUF_SIZE);

            if (functionHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Function not found: " + name : errorMsg);
            }
            
            final PanamaWebAssemblyFunction function =
                new PanamaWebAssemblyFunction(functionHandle, name, this);
            synchronized (childFunctions) {
                childFunctions.add(function);
            }
            return function;
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting function: " + name, e);
        }
    }

    @Override
    public WebAssemblyMemory getMemory() throws WasmRuntimeException {
        ensureNotClosed();

        try {
            final MemorySegment memoryHandle = (MemorySegment) Handles.GET_MEMORY.invoke(nativeHandle);
            if (memoryHandle.equals(MemorySegment.NULL)) {
                throw new WasmRuntimeException("No memory exported by WebAssembly module");
            }
            
            final PanamaWebAssemblyMemory memory =
                new PanamaWebAssemblyMemory(memoryHandle, this);
            synchronized (childMemories) {
                childMemories.add(memory);
            }
            return memory;
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting memory", e);
        }
    }

    @Override
    public String[] getFunctionNames() {
        return getExportedNames(Handles.GET_FUNCTION_NAMES, "function");
    }

    @Override
    public String[] getGlobalNames() {
        return getExportedNames(Handles.GET_GLOBAL_NAMES, "global");
    }

    @Override
    public Object getGlobal(final String globalName) throws WasmRuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }

        ensureNotClosed();

        if (Handles.GET_GLOBAL == null) {
            throw new WasmRuntimeException("Native function 'wamr_get_global' not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuffer = arena.allocateFrom(globalName);
            final MemorySegment valueTypePtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment valueBuffer = arena.allocate(8); // 8 bytes for i64/f64
            final int valueSize = 8;

            final int result = (int) Handles.GET_GLOBAL.invoke(
                nativeHandle, nameBuffer, valueTypePtr, valueBuffer, valueSize);

            if (result != 0) {
                throw new WasmRuntimeException("Failed to get global variable: " + globalName);
            }

            final int valueType = valueTypePtr.get(ValueLayout.JAVA_INT, 0);

            switch (valueType) {
                case WasmTypes.I32:
                    return valueBuffer.get(ValueLayout.JAVA_INT, 0);
                case WasmTypes.I64:
                    return valueBuffer.get(ValueLayout.JAVA_LONG, 0);
                case WasmTypes.F32:
                    return valueBuffer.get(ValueLayout.JAVA_FLOAT, 0);
                case WasmTypes.F64:
                    return valueBuffer.get(ValueLayout.JAVA_DOUBLE, 0);
                default:
                    throw new WasmRuntimeException("Unsupported global type: " + valueType);
            }
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting global: " + globalName, e);
        }
    }

    @Override
    public void setGlobal(final String globalName, final Object value) throws WasmRuntimeException {
        if (globalName == null || globalName.trim().isEmpty()) {
            throw new IllegalArgumentException("Global name cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Global value cannot be null");
        }

        ensureNotClosed();

        if (Handles.SET_GLOBAL == null) {
            throw new WasmRuntimeException("Native function 'wamr_set_global' not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuffer = arena.allocateFrom(globalName);
            final MemorySegment valueBuffer = arena.allocate(8); // 8 bytes for i64/f64

            // Determine type and write value
            final int valueType;
            if (value instanceof Integer) {
                valueType = WasmTypes.I32;
                valueBuffer.set(ValueLayout.JAVA_INT, 0, (Integer) value);
            } else if (value instanceof Long) {
                valueType = WasmTypes.I64;
                valueBuffer.set(ValueLayout.JAVA_LONG, 0, (Long) value);
            } else if (value instanceof Float) {
                valueType = WasmTypes.F32;
                valueBuffer.set(ValueLayout.JAVA_FLOAT, 0, (Float) value);
            } else if (value instanceof Double) {
                valueType = WasmTypes.F64;
                valueBuffer.set(ValueLayout.JAVA_DOUBLE, 0, (Double) value);
            } else {
                throw new IllegalArgumentException(
                    "Unsupported global value type: " + value.getClass().getName() +
                    ". Expected Integer, Long, Float, or Double.");
            }

            final int result = (int) Handles.SET_GLOBAL.invoke(
                nativeHandle, nameBuffer, valueType, valueBuffer);

            if (result != 0) {
                throw new WasmRuntimeException("Failed to set global variable: " + globalName);
            }
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error setting global: " + globalName, e);
        }
    }

    @Override
    public WebAssemblyTable getTable(final String tableName) throws WasmRuntimeException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        ensureNotClosed();

        if (Handles.GET_TABLE == null) {
            throw new WasmRuntimeException("Native function 'wamr_table_get' not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuffer = arena.allocateFrom(tableName);
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            final MemorySegment tableHandle = (MemorySegment) Handles.GET_TABLE.invoke(
                nativeHandle, nameBuffer, errorBuf, WasmTypes.ERROR_BUF_SIZE);

            if (tableHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Table not found: " + tableName : errorMsg);
            }

            final PanamaWebAssemblyTable table =
                new PanamaWebAssemblyTable(tableHandle, tableName, this);
            synchronized (childTables) {
                childTables.add(table);
            }
            return table;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error getting table: " + tableName, e);
        }
    }

    @Override
    public String[] getTableNames() {
        return getExportedNames(Handles.GET_TABLE_NAMES, "table");
    }

    @Override
    public String getException() {
        ensureNotClosed();

        if (Handles.GET_EXCEPTION == null) {
            return null;
        }

        try {
            final MemorySegment result = (MemorySegment) Handles.GET_EXCEPTION.invoke(nativeHandle);
            if (result.equals(MemorySegment.NULL)) {
                return null;
            }
            // The returned string is malloc'd by Rust; read it and free it
            final String msg = result.reinterpret(Long.MAX_VALUE).getString(0);
            // Free the malloc'd string using libc free via Linker
            freeNativeString(result);
            return msg;
        } catch (final Throwable e) {
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

        if (Handles.SET_EXCEPTION == null) {
            LOGGER.warning("Native function 'wamr_instance_set_exception' not available");
            return;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment exceptionBuffer = arena.allocateFrom(exception);
            Handles.SET_EXCEPTION.invoke(nativeHandle, exceptionBuffer);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set exception: " + e.getMessage());
        }
    }

    @Override
    public void clearException() {
        ensureNotClosed();

        if (Handles.CLEAR_EXCEPTION == null) {
            LOGGER.warning("Native function 'wamr_instance_clear_exception' not available");
            return;
        }

        try {
            Handles.CLEAR_EXCEPTION.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to clear exception: " + e.getMessage());
        }
    }

    @Override
    public void terminate() {
        ensureNotClosed();

        if (Handles.TERMINATE == null) {
            LOGGER.warning("Native function 'wamr_instance_terminate' not available");
            return;
        }

        try {
            Handles.TERMINATE.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to terminate instance: " + e.getMessage());
        }
    }

    @Override
    public void setInstructionCountLimit(final long limit) {
        ensureNotClosed();

        if (Handles.SET_INSTRUCTION_COUNT_LIMIT == null) {
            LOGGER.warning(
                "Native function 'wamr_instance_set_instruction_count_limit' not available");
            return;
        }

        try {
            Handles.SET_INSTRUCTION_COUNT_LIMIT.invoke(nativeHandle, (int) limit);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set instruction count limit: " + e.getMessage());
        }
    }

    @Override
    public boolean isWasiMode() {
        ensureNotClosed();

        if (Handles.IS_WASI_MODE == null) {
            return false;
        }

        try {
            final int result = (int) Handles.IS_WASI_MODE.invoke(nativeHandle);
            return result != 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to check WASI mode: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getWasiExitCode() {
        ensureNotClosed();

        if (Handles.GET_WASI_EXIT_CODE == null) {
            return 0;
        }

        try {
            return (int) Handles.GET_WASI_EXIT_CODE.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get WASI exit code: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean hasWasiStartFunction() {
        ensureNotClosed();

        if (Handles.HAS_WASI_START_FUNCTION == null) {
            return false;
        }

        try {
            final int result = (int) Handles.HAS_WASI_START_FUNCTION.invoke(nativeHandle);
            return result != 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to check WASI start function: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean executeMain(final String[] argv) {
        ensureNotClosed();

        if (Handles.EXECUTE_MAIN == null) {
            LOGGER.warning("Native function 'wamr_instance_execute_main' not available");
            return false;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final String[] args = argv != null ? argv : new String[0];
            final MemorySegment argvList = allocateStringArray(arena, args);
            final int result = (int) Handles.EXECUTE_MAIN.invoke(
                nativeHandle, argvList, args.length);
            return result == 0;
        } catch (final Throwable e) {
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

        if (Handles.EXECUTE_FUNC == null) {
            LOGGER.warning("Native function 'wamr_instance_execute_func' not available");
            return false;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final String[] args = argv != null ? argv : new String[0];
            final MemorySegment argvList = allocateStringArray(arena, args);
            final int result = (int) Handles.EXECUTE_FUNC.invoke(
                nativeHandle, nameStr, argvList, args.length);
            return result == 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to execute function '" + name + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Allocates a native array of C strings from a Java String array.
     */
    private static MemorySegment allocateStringArray(final Arena arena, final String[] strings) {
        if (strings.length == 0) {
            return MemorySegment.NULL;
        }
        final MemorySegment array = arena.allocate(ValueLayout.ADDRESS, strings.length);
        for (int i = 0; i < strings.length; i++) {
            array.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(strings[i]));
        }
        return array;
    }

    @Override
    public void setCustomData(final long customData) {
        ensureNotClosed();

        if (Handles.SET_CUSTOM_DATA == null) {
            LOGGER.warning("Native function 'wamr_instance_set_custom_data' not available");
            return;
        }

        try {
            Handles.SET_CUSTOM_DATA.invoke(nativeHandle, customData);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set custom data: " + e.getMessage());
        }
    }

    @Override
    public long getCustomData() {
        ensureNotClosed();

        if (Handles.GET_CUSTOM_DATA == null) {
            LOGGER.warning("Native function 'wamr_instance_get_custom_data' not available");
            return 0;
        }

        try {
            return (long) Handles.GET_CUSTOM_DATA.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get custom data: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public String getCallStack() {
        ensureNotClosed();

        if (Handles.GET_CALL_STACK == null) {
            return null;
        }

        try {
            final MemorySegment result = (MemorySegment) Handles.GET_CALL_STACK.invoke(nativeHandle);
            if (result.equals(MemorySegment.NULL)) {
                return null;
            }
            final String stack = result.reinterpret(Long.MAX_VALUE).getString(0);
            freeNativeString(result);
            return stack;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get call stack: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void dumpCallStack() {
        ensureNotClosed();

        if (Handles.DUMP_CALL_STACK == null) {
            LOGGER.warning("Native function 'wamr_instance_dump_call_stack' not available");
            return;
        }

        try {
            Handles.DUMP_CALL_STACK.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to dump call stack: " + e.getMessage());
        }
    }

    @Override
    public void dumpPerfProfiling() {
        ensureNotClosed();

        if (Handles.DUMP_PERF_PROFILING == null) {
            LOGGER.warning("Native function 'wamr_instance_dump_perf_profiling' not available");
            return;
        }

        try {
            Handles.DUMP_PERF_PROFILING.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to dump perf profiling: " + e.getMessage());
        }
    }

    @Override
    public double sumWasmExecTime() {
        ensureNotClosed();

        if (Handles.SUM_EXEC_TIME == null) {
            return 0.0;
        }

        try {
            return (double) Handles.SUM_EXEC_TIME.invoke(nativeHandle);
        } catch (final Throwable e) {
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

        if (Handles.GET_FUNC_EXEC_TIME == null) {
            return 0.0;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuffer = arena.allocateFrom(funcName);
            return (double) Handles.GET_FUNC_EXEC_TIME.invoke(nativeHandle, nameBuffer);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get func exec time: " + e.getMessage());
            return 0.0;
        }
    }

    @Override
    public void dumpMemConsumption() {
        ensureNotClosed();

        if (Handles.DUMP_MEM_CONSUMPTION == null) {
            LOGGER.warning("Native function 'wamr_instance_dump_mem_consumption' not available");
            return;
        }

        try {
            Handles.DUMP_MEM_CONSUMPTION.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to dump mem consumption: " + e.getMessage());
        }
    }

    @Override
    public boolean lookupMemory(final String name) {
        ensureNotClosed();
        if (name == null || Handles.LOOKUP_MEMORY == null) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final MemorySegment result = (MemorySegment) Handles.LOOKUP_MEMORY.invoke(
                nativeHandle, nameStr);
            return !result.equals(MemorySegment.NULL);
        } catch (final Throwable e) {
            LOGGER.fine("Failed to lookup memory '" + name + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean beginBlockingOp() {
        ensureNotClosed();
        if (Handles.BEGIN_BLOCKING_OP == null) {
            return false;
        }
        try {
            return ((int) Handles.BEGIN_BLOCKING_OP.invoke(nativeHandle)) != 0;
        } catch (final Throwable e) {
            LOGGER.fine("Failed to begin blocking op: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void endBlockingOp() {
        ensureNotClosed();
        if (Handles.END_BLOCKING_OP == null) {
            return;
        }
        try {
            Handles.END_BLOCKING_OP.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.fine("Failed to end blocking op: " + e.getMessage());
        }
    }

    @Override
    public boolean detectNativeStackOverflow() {
        ensureNotClosed();
        if (Handles.DETECT_NATIVE_STACK_OVERFLOW == null) {
            return true;
        }
        try {
            return ((int) Handles.DETECT_NATIVE_STACK_OVERFLOW.invoke(nativeHandle)) != 0;
        } catch (final Throwable e) {
            LOGGER.fine("Failed to detect native stack overflow: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean detectNativeStackOverflowSize(final int requiredSize) {
        ensureNotClosed();
        if (Handles.DETECT_NATIVE_STACK_OVERFLOW_SIZE == null) {
            return true;
        }
        try {
            return ((int) Handles.DETECT_NATIVE_STACK_OVERFLOW_SIZE.invoke(
                nativeHandle, requiredSize)) != 0;
        } catch (final Throwable e) {
            LOGGER.fine("Failed to detect native stack overflow size: " + e.getMessage());
            return true;
        }
    }

    @Override
    public void setContext(final long key, final long ctx) {
        ensureNotClosed();
        if (Handles.SET_CONTEXT == null) {
            LOGGER.warning("wamr_set_context not available");
            return;
        }
        try {
            Handles.SET_CONTEXT.invoke(nativeHandle, MemorySegment.ofAddress(key), ctx);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set context: " + e.getMessage());
        }
    }

    @Override
    public long getContext(final long key) {
        ensureNotClosed();
        if (Handles.GET_CONTEXT == null) {
            return 0;
        }
        try {
            return (long) Handles.GET_CONTEXT.invoke(nativeHandle, MemorySegment.ofAddress(key));
        } catch (final Throwable e) {
            LOGGER.fine("Failed to get context: " + e.getMessage());
            return 0;
        }
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
                for (final PanamaWebAssemblyMemory memory : childMemories) {
                    try {
                        memory.close();
                    } catch (final Exception e) {
                        LOGGER.warning("Error closing child memory: " + e.getMessage());
                    }
                }
                childMemories.clear();
            }

            synchronized (childFunctions) {
                for (final PanamaWebAssemblyFunction function : childFunctions) {
                    try {
                        function.close();
                    } catch (final Exception e) {
                        LOGGER.warning("Error closing child function: " + e.getMessage());
                    }
                }
                childFunctions.clear();
            }

            synchronized (childTables) {
                for (final PanamaWebAssemblyTable table : childTables) {
                    try {
                        table.close();
                    } catch (final Exception e) {
                        LOGGER.warning("Error closing child table: " + e.getMessage());
                    }
                }
                childTables.clear();
            }

            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;

            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_INSTANCE.invoke(handle);
                    LOGGER.fine("Destroyed Panama WebAssembly instance with handle: " + handle);
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native instance: " + e.getMessage());
                }
            }

            // Destroy host function registrations AFTER instance (host functions may still
            // be referenced during instance destruction)
            PanamaWebAssemblyModule.cleanupRegistrations(registrationHandles);
            if (registrationArena != null) {
                try {
                    registrationArena.close();
                } catch (final Exception e) {
                    LOGGER.warning("Error closing registration arena: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Returns the native instance handle for use by child resources (e.g., call_indirect).
     * Package-private access only.
     *
     * @return the native instance handle
     */
    MemorySegment getNativeHandle() {
        ensureNotClosed();
        return nativeHandle;
    }

    private String[] getExportedNames(final MethodHandle namesHandle, final String kind) {
        ensureNotClosed();
        return WasmTypes.readNativeNames(nativeHandle, namesHandle, Handles.FREE_NAMES, kind);
    }

    private static void freeNativeString(final MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            final MethodHandle freeString = linker.downcallHandle(
                lookup.find("wamr_free_string").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            freeString.invoke(ptr);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to free native string: " + e.getMessage());
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly instance has been closed");
        }
    }
    
    @Override
    public boolean hasMemory() {
        ensureNotClosed();
        try {
            final int result = (int) Handles.HAS_MEMORY.invoke(nativeHandle);
            return result != 0;
        } catch (final Throwable e) {
            return false;
        }
    }

    @Override
    public boolean setRunningMode(final RunningMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Running mode cannot be null");
        }
        ensureNotClosed();
        try {
            final int result = (int) Handles.SET_RUNNING_MODE.invoke(nativeHandle, mode.getNativeValue());
            return result == 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set running mode: " + e.getMessage());
            return false;
        }
    }

    @Override
    public RunningMode getRunningMode() {
        ensureNotClosed();
        try {
            final int modeValue = (int) Handles.GET_RUNNING_MODE.invoke(nativeHandle);
            return RunningMode.fromNativeValue(modeValue);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get running mode: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setBoundsChecks(final boolean enable) {
        ensureNotClosed();
        try {
            final int result = (int) Handles.SET_BOUNDS_CHECKS.invoke(nativeHandle, enable ? 1 : 0);
            return result == 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set bounds checks: " + e.getMessage());
            return false;
        }
    }

    @Override
    public long moduleMalloc(final long size) throws WasmRuntimeException {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }

        ensureNotClosed();

        if (Handles.MODULE_MALLOC == null) {
            throw new WasmRuntimeException("Native function 'wamr_module_malloc' not available");
        }

        try {
            final long offset = (long) Handles.MODULE_MALLOC.invoke(
                nativeHandle, size, MemorySegment.NULL);
            if (offset == 0L) {
                throw new WasmRuntimeException("Module malloc failed for size: " + size);
            }
            return offset;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error in moduleMalloc", e);
        }
    }

    @Override
    public void moduleFree(final long offset) {
        ensureNotClosed();

        if (Handles.MODULE_FREE == null) {
            LOGGER.warning("Native function 'wamr_module_free' not available");
            return;
        }

        try {
            Handles.MODULE_FREE.invoke(nativeHandle, offset);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to free module memory at offset " + offset + ": " + e.getMessage());
        }
    }

    @Override
    public long moduleDupData(final byte[] data) throws WasmRuntimeException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        ensureNotClosed();

        if (Handles.MODULE_DUP_DATA == null) {
            throw new WasmRuntimeException("Native function 'wamr_module_dup_data' not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment dataSegment = arena.allocate(data.length);
            dataSegment.copyFrom(MemorySegment.ofArray(data));

            final long offset = (long) Handles.MODULE_DUP_DATA.invoke(
                nativeHandle, dataSegment, (long) data.length);
            if (offset == 0L) {
                throw new WasmRuntimeException(
                    "Module dup data failed for " + data.length + " bytes");
            }
            return offset;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error in moduleDupData", e);
        }
    }

    @Override
    public boolean validateAppAddr(final long appOffset, final long size) {
        ensureNotClosed();

        if (Handles.VALIDATE_APP_ADDR == null) {
            return false;
        }

        try {
            final int result = (int) Handles.VALIDATE_APP_ADDR.invoke(
                nativeHandle, appOffset, size);
            return result != 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to validate app addr: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean validateAppStrAddr(final long appStrOffset) {
        ensureNotClosed();

        if (Handles.VALIDATE_APP_STR_ADDR == null) {
            return false;
        }

        try {
            final int result = (int) Handles.VALIDATE_APP_STR_ADDR.invoke(
                nativeHandle, appStrOffset);
            return result != 0;
        } catch (final Throwable e) {
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

        if (Handles.GET_MEMORY_BY_INDEX == null) {
            throw new WasmRuntimeException(
                "Native function 'wamr_memory_get_by_index' not available");
        }

        try {
            final MemorySegment memoryHandle = (MemorySegment) Handles.GET_MEMORY_BY_INDEX.invoke(
                nativeHandle, index);
            if (memoryHandle.equals(MemorySegment.NULL)) {
                throw new WasmRuntimeException("No memory at index: " + index);
            }

            final PanamaWebAssemblyMemory memory =
                new PanamaWebAssemblyMemory(memoryHandle, this);
            synchronized (childMemories) {
                childMemories.add(memory);
            }
            return memory;
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException(
                "Unexpected error getting memory at index: " + index, e);
        }
    }

    @Override
    public boolean isBoundsChecksEnabled() {
        ensureNotClosed();
        try {
            final int result = (int) Handles.IS_BOUNDS_CHECKS_ENABLED.invoke(nativeHandle);
            return result != 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to check bounds checks: " + e.getMessage());
            return false;
        }
    }
}