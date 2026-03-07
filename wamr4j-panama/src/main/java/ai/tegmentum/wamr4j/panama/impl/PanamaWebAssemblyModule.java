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
import ai.tegmentum.wamr4j.PackageType;
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WasiConfiguration;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly module.
 *
 * <p>This class represents a compiled WebAssembly module using Panama FFI
 * to communicate with the native WAMR library. It provides thread-safe
 * access to module information and instance creation with better type
 * safety than JNI.
 *
 * @since 1.0.0
 */
public final class PanamaWebAssemblyModule implements WebAssemblyModule {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyModule.class.getName());

    private static final long DEFAULT_STACK_SIZE = 16L * 1024;
    private static final long DEFAULT_HEAP_SIZE = 16L * 1024 * 1024;

    // Native module handle as MemorySegment
    private volatile MemorySegment nativeHandle;

    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Maximum number of parameters or results in a function signature. */
    private static final int MAX_SIGNATURE_TYPES = 64;

    // Lazily cached MethodHandles for native calls
    private static final class Handles {
        static final MethodHandle DESTROY_MODULE;
        static final MethodHandle INSTANTIATE;
        static final MethodHandle GET_EXPORT_NAMES;
        static final MethodHandle GET_IMPORT_NAMES;
        static final MethodHandle FREE_NAMES;
        static final MethodHandle GET_EXPORT_FUNC_SIG;
        static final MethodHandle SET_MODULE_NAME;
        static final MethodHandle GET_MODULE_NAME;
        static final MethodHandle GET_MODULE_HASH;
        static final MethodHandle GET_PACKAGE_TYPE;
        static final MethodHandle GET_PACKAGE_VERSION;
        static final MethodHandle IS_UNDERLYING_BINARY_FREEABLE;
        static final MethodHandle REGISTER_HOST_FUNCTIONS;
        static final MethodHandle DESTROY_HOST_FUNCTION_REGISTRATION;
        static final MethodHandle SET_WASI_ARGS;
        static final MethodHandle SET_WASI_ADDR_POOL;
        static final MethodHandle SET_WASI_NS_LOOKUP_POOL;
        static final MethodHandle INSTANTIATE_EX;
        static final MethodHandle GET_CUSTOM_SECTION;
        static final MethodHandle FREE_NATIVE_BUFFER;
        static final MethodHandle GET_EXPORT_GLOBAL_TYPE_INFO;
        static final MethodHandle GET_EXPORT_MEMORY_TYPE_INFO;
        static final MethodHandle INSTANTIATE_EX2;
        static final MethodHandle MODULE_REGISTER;

        private static final FunctionDescriptor NAMES_DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
        private static final FunctionDescriptor FREE_NAMES_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

        static {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final Linker linker = Linker.nativeLinker();
            DESTROY_MODULE = linker.downcallHandle(
                lookup.find("wamr_module_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            INSTANTIATE = linker.downcallHandle(
                lookup.find("wamr_instance_create").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            GET_EXPORT_NAMES = resolveOptional(lookup, linker,
                "wamr_module_get_export_names", NAMES_DESC);
            GET_IMPORT_NAMES = resolveOptional(lookup, linker,
                "wamr_module_get_import_names", NAMES_DESC);
            FREE_NAMES = resolveOptional(lookup, linker, "wamr_free_names", FREE_NAMES_DESC);
            GET_EXPORT_FUNC_SIG = resolveOptional(lookup, linker,
                "wamr_module_get_export_function_signature",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            SET_MODULE_NAME = resolveOptional(lookup, linker,
                "wamr_module_set_name",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_MODULE_NAME = resolveOptional(lookup, linker,
                "wamr_module_get_name",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_MODULE_HASH = resolveOptional(lookup, linker,
                "wamr_module_get_hash",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            GET_PACKAGE_TYPE = resolveOptional(lookup, linker,
                "wamr_get_module_package_type",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GET_PACKAGE_VERSION = resolveOptional(lookup, linker,
                "wamr_get_module_package_version",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            IS_UNDERLYING_BINARY_FREEABLE = resolveOptional(lookup, linker,
                "wamr_is_underlying_binary_freeable",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            REGISTER_HOST_FUNCTIONS = resolveOptional(lookup, linker,
                "wamr_register_host_functions",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,          // module_name
                    ValueLayout.ADDRESS,          // func_names
                    ValueLayout.ADDRESS,          // param_type_arrays
                    ValueLayout.ADDRESS,          // param_counts
                    ValueLayout.ADDRESS,          // result_type_arrays
                    ValueLayout.ADDRESS,          // result_counts
                    ValueLayout.ADDRESS,          // callback_fn
                    ValueLayout.ADDRESS,          // user_data_array
                    ValueLayout.JAVA_INT));       // num_functions
            DESTROY_HOST_FUNCTION_REGISTRATION = resolveOptional(lookup, linker,
                "wamr_destroy_host_function_registration",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            SET_WASI_ARGS = resolveOptional(lookup, linker,
                "wamr_module_set_wasi_args",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // dir_list
                    ValueLayout.JAVA_INT,     // dir_count
                    ValueLayout.ADDRESS,      // map_dir_list
                    ValueLayout.JAVA_INT,     // map_dir_count
                    ValueLayout.ADDRESS,      // env_vars
                    ValueLayout.JAVA_INT,     // env_count
                    ValueLayout.ADDRESS,      // argv
                    ValueLayout.JAVA_INT));   // argc
            SET_WASI_ADDR_POOL = resolveOptional(lookup, linker,
                "wamr_module_set_wasi_addr_pool",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // addr_pool
                    ValueLayout.JAVA_INT));   // addr_pool_size
            SET_WASI_NS_LOOKUP_POOL = resolveOptional(lookup, linker,
                "wamr_module_set_wasi_ns_lookup_pool",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // ns_lookup_pool
                    ValueLayout.JAVA_INT));   // ns_lookup_pool_size
            INSTANTIATE_EX = resolveOptional(lookup, linker,
                "wamr_instance_create_ex",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.JAVA_INT,     // default_stack_size
                    ValueLayout.JAVA_INT,     // host_managed_heap_size
                    ValueLayout.JAVA_INT,     // max_memory_pages
                    ValueLayout.ADDRESS,      // error_buf
                    ValueLayout.JAVA_INT));   // error_buf_size
            GET_CUSTOM_SECTION = resolveOptional(lookup, linker,
                "wamr_module_get_custom_section",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // name
                    ValueLayout.ADDRESS));    // out_len
            FREE_NATIVE_BUFFER = resolveOptional(lookup, linker,
                "wamr_free_native_buffer",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            GET_EXPORT_GLOBAL_TYPE_INFO = resolveOptional(lookup, linker,
                "wamr_get_export_global_type_info",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // name
                    ValueLayout.ADDRESS,      // valkind_out
                    ValueLayout.ADDRESS));    // is_mutable_out
            GET_EXPORT_MEMORY_TYPE_INFO = resolveOptional(lookup, linker,
                "wamr_get_export_memory_type_info",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // name
                    ValueLayout.ADDRESS,      // is_shared_out
                    ValueLayout.ADDRESS,      // init_page_count_out
                    ValueLayout.ADDRESS));    // max_page_count_out
            INSTANTIATE_EX2 = resolveOptional(lookup, linker,
                "wamr_instance_create_ex2",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.JAVA_INT,     // default_stack_size
                    ValueLayout.JAVA_INT,     // host_managed_heap_size
                    ValueLayout.JAVA_INT,     // max_memory_pages
                    ValueLayout.ADDRESS,      // error_buf
                    ValueLayout.JAVA_INT));   // error_buf_size
            MODULE_REGISTER = resolveOptional(lookup, linker,
                "wamr_module_register",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,      // module
                    ValueLayout.ADDRESS,      // name
                    ValueLayout.ADDRESS,      // error_buf
                    ValueLayout.JAVA_INT));   // error_buf_size
        }

        private static MethodHandle resolveOptional(final SymbolLookup lookup, final Linker linker,
                final String name, final FunctionDescriptor desc) {
            final var symbol = lookup.find(name);
            return symbol.isPresent() ? linker.downcallHandle(symbol.get(), desc) : null;
        }
    }

    /**
     * Creates a new Panama WebAssembly module wrapper.
     *
     * @param nativeHandle the native module handle, must not be NULL
     */
    public PanamaWebAssemblyModule(final MemorySegment nativeHandle) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native module handle cannot be null or NULL");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created Panama WebAssembly module with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyInstance instantiate() throws WasmRuntimeException {
        return instantiate(Map.of());
    }

    @Override
    public WebAssemblyInstance instantiate(final Map<String, Map<String, Object>> imports) throws WasmRuntimeException {
        ensureNotClosed();

        final List<MemorySegment> registrationHandles = new ArrayList<>();
        // The registration arena must outlive the instance — use Arena.ofAuto()
        // so the upcall stubs and native memory live as long as needed.
        Arena registrationArena = null;

        try {
            if (imports != null && !imports.isEmpty() && Handles.REGISTER_HOST_FUNCTIONS != null) {
                registrationArena = Arena.ofShared();
                registerHostFunctions(imports, registrationHandles, registrationArena);
            }

            try (final Arena arena = Arena.ofConfined()) {
                final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

                final MemorySegment instanceHandle = (MemorySegment) Handles.INSTANTIATE.invoke(
                    nativeHandle, DEFAULT_STACK_SIZE, DEFAULT_HEAP_SIZE, errorBuf, WasmTypes.ERROR_BUF_SIZE);
                if (instanceHandle.equals(MemorySegment.NULL)) {
                    final String errorMsg = errorBuf.getString(0);
                    throw new WasmRuntimeException(
                        errorMsg.isEmpty() ? "Failed to instantiate WebAssembly module" : errorMsg);
                }

                return new PanamaWebAssemblyInstance(instanceHandle, registrationHandles, registrationArena);
            }
        } catch (final WasmRuntimeException e) {
            cleanupRegistrations(registrationHandles);
            if (registrationArena != null) {
                registrationArena.close();
            }
            throw e;
        } catch (final Throwable e) {
            cleanupRegistrations(registrationHandles);
            if (registrationArena != null) {
                registrationArena.close();
            }
            throw new WasmRuntimeException("Unexpected error during module instantiation", e);
        }
    }

    /**
     * Registers host functions from the imports map with the native WAMR runtime.
     */
    private void registerHostFunctions(
            final Map<String, Map<String, Object>> imports,
            final List<MemorySegment> registrationHandles,
            final Arena arena) throws Throwable {
        for (final Map.Entry<String, Map<String, Object>> moduleEntry : imports.entrySet()) {
            final String moduleName = moduleEntry.getKey();
            final Map<String, Object> moduleFunctions = moduleEntry.getValue();

            // Collect HostFunction entries
            final List<Map.Entry<String, HostFunction>> hostFuncs = new ArrayList<>();
            for (final Map.Entry<String, Object> entry : moduleFunctions.entrySet()) {
                if (entry.getValue() instanceof HostFunction) {
                    hostFuncs.add(Map.entry(entry.getKey(), (HostFunction) entry.getValue()));
                }
            }

            if (hostFuncs.isEmpty()) {
                continue;
            }

            final int numFunctions = hostFuncs.size();
            final MemorySegment moduleNameStr = arena.allocateFrom(moduleName);
            final MemorySegment funcNamesArr = arena.allocate(ValueLayout.ADDRESS, numFunctions);
            final MemorySegment paramCountsArr = arena.allocate(ValueLayout.JAVA_INT, numFunctions);
            final MemorySegment resultCountsArr = arena.allocate(ValueLayout.JAVA_INT, numFunctions);
            final MemorySegment userDataArr = arena.allocate(ValueLayout.ADDRESS, numFunctions);

            // Calculate total param/result type count for flattened arrays
            int totalParams = 0;
            int totalResults = 0;
            for (final Map.Entry<String, HostFunction> entry : hostFuncs) {
                final FunctionSignature sig = entry.getValue().getSignature();
                totalParams += sig.getParameterTypes().length;
                totalResults += sig.getReturnTypes().length;
            }

            final MemorySegment paramTypesFlat = arena.allocate(ValueLayout.JAVA_BYTE,
                Math.max(totalParams, 1));
            final MemorySegment resultTypesFlat = arena.allocate(ValueLayout.JAVA_BYTE,
                Math.max(totalResults, 1));

            // Create the upcall stub for the Panama callback
            final MemorySegment callbackStub = createPanamaUpcallStub(arena);

            int paramOffset = 0;
            int resultOffset = 0;
            for (int i = 0; i < numFunctions; i++) {
                final Map.Entry<String, HostFunction> entry = hostFuncs.get(i);
                final String funcName = entry.getKey();
                final HostFunction hostFunc = entry.getValue();
                final FunctionSignature sig = hostFunc.getSignature();

                // Function name
                final MemorySegment nameStr = arena.allocateFrom(funcName);
                funcNamesArr.setAtIndex(ValueLayout.ADDRESS, i, nameStr);

                // Param types
                final ValueType[] paramTypes = sig.getParameterTypes();
                paramCountsArr.setAtIndex(ValueLayout.JAVA_INT, i, paramTypes.length);
                for (final ValueType pt : paramTypes) {
                    paramTypesFlat.set(ValueLayout.JAVA_BYTE, paramOffset++, (byte) pt.ordinal());
                }

                // Result types
                final ValueType[] resultTypes = sig.getReturnTypes();
                resultCountsArr.setAtIndex(ValueLayout.JAVA_INT, i, resultTypes.length);
                for (final ValueType rt : resultTypes) {
                    resultTypesFlat.set(ValueLayout.JAVA_BYTE, resultOffset++, (byte) rt.ordinal());
                }

                // User data: store a pointer to a PanamaCallbackData struct in native memory
                final MemorySegment userData = PanamaCallbackData.allocate(arena, hostFunc, sig);
                userDataArr.setAtIndex(ValueLayout.ADDRESS, i, userData);
            }

            final MemorySegment regHandle = (MemorySegment) Handles.REGISTER_HOST_FUNCTIONS.invoke(
                moduleNameStr, funcNamesArr, paramTypesFlat,
                paramCountsArr, resultTypesFlat, resultCountsArr,
                callbackStub, userDataArr, numFunctions);

            if (!regHandle.equals(MemorySegment.NULL)) {
                registrationHandles.add(regHandle);
            } else {
                throw new WasmRuntimeException("Failed to register host functions for module: " + moduleName);
            }
        }
    }

    /**
     * Creates a Panama upcall stub for the host function callback.
     * The callback signature is: int callback(void* user_data, uint64_t* args, uint32_t param_count, uint64_t* result)
     */
    private static MemorySegment createPanamaUpcallStub(final Arena arena) {
        try {
            final MethodHandle target = MethodHandles.lookup().findStatic(
                PanamaWebAssemblyModule.class, "panamaHostCallback",
                MethodType.methodType(int.class,
                    MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class));

            final FunctionDescriptor callbackDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,      // user_data
                ValueLayout.ADDRESS,      // args
                ValueLayout.JAVA_INT,     // param_count
                ValueLayout.ADDRESS);     // result

            return Linker.nativeLinker().upcallStub(target, callbackDesc, arena);
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create upcall stub", e);
        }
    }

    /**
     * The Panama host function callback. Called from native code via the upcall stub.
     */
    @SuppressWarnings("unused") // Called via MethodHandle from native code
    private static int panamaHostCallback(
            final MemorySegment userData,
            final MemorySegment args,
            final int paramCount,
            final MemorySegment resultPtr) {
        if (userData.equals(MemorySegment.NULL)) {
            return -1;
        }

        try {
            final PanamaCallbackData cbData = PanamaCallbackData.fromPointer(userData);
            if (cbData == null) {
                return -1;
            }

            // Build Java arguments from raw uint64 args
            final MemorySegment argsSlice = args.reinterpret((long) paramCount * Long.BYTES);
            final Object[] javaArgs = new Object[paramCount];
            for (int i = 0; i < paramCount; i++) {
                final long rawArg = argsSlice.getAtIndex(ValueLayout.JAVA_LONG, i);
                final ValueType paramType = cbData.paramTypes[i];
                javaArgs[i] = switch (paramType) {
                    case I32 -> (int) rawArg;
                    case I64 -> rawArg;
                    case F32 -> Float.intBitsToFloat((int) rawArg);
                    case F64 -> Double.longBitsToDouble(rawArg);
                };
            }

            // Call the Java HostFunction
            final Object result = cbData.hostFunction.execute(javaArgs);

            // Write result if expected
            if (cbData.resultTypes.length > 0 && result != null) {
                final MemorySegment resultSlice = resultPtr.reinterpret(Long.BYTES);
                final long rawResult = switch (cbData.resultTypes[0]) {
                    case I32 -> ((Integer) result).intValue() & 0xFFFFFFFFL;
                    case I64 -> ((Long) result);
                    case F32 -> Float.floatToRawIntBits((Float) result) & 0xFFFFFFFFL;
                    case F64 -> Double.doubleToRawLongBits((Double) result);
                };
                resultSlice.set(ValueLayout.JAVA_LONG, 0, rawResult);
            }

            return 0;
        } catch (final Exception e) {
            LOGGER.warning("Host function callback error: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Cleans up native registration handles.
     */
    static void cleanupRegistrations(final List<MemorySegment> handles) {
        if (Handles.DESTROY_HOST_FUNCTION_REGISTRATION == null) {
            return;
        }
        for (final MemorySegment handle : handles) {
            if (!handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_HOST_FUNCTION_REGISTRATION.invoke(handle);
                } catch (final Throwable ignored) {
                    // Best-effort cleanup
                }
            }
        }
    }

    @Override
    public String[] getExportNames() {
        return getModuleNames(Handles.GET_EXPORT_NAMES, "export");
    }

    @Override
    public String[] getImportNames() {
        return getModuleNames(Handles.GET_IMPORT_NAMES, "import");
    }

    @Override
    public FunctionSignature getExportFunctionSignature(final String functionName) {
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }
        ensureNotClosed();

        if (Handles.GET_EXPORT_FUNC_SIG == null) {
            LOGGER.warning("wamr_module_get_export_function_signature not available");
            return null;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(functionName);
            final MemorySegment paramTypesArr = arena.allocate(ValueLayout.JAVA_INT,
                MAX_SIGNATURE_TYPES);
            final MemorySegment paramCountPtr = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment resultTypesArr = arena.allocate(ValueLayout.JAVA_INT,
                MAX_SIGNATURE_TYPES);
            final MemorySegment resultCountPtr = arena.allocate(ValueLayout.JAVA_INT);

            final int rc = (int) Handles.GET_EXPORT_FUNC_SIG.invoke(
                nativeHandle, nameStr,
                paramTypesArr, paramCountPtr,
                resultTypesArr, resultCountPtr);

            if (rc != 0) {
                return null;
            }

            final int paramCount = paramCountPtr.get(ValueLayout.JAVA_INT, 0);
            final int resultCount = resultCountPtr.get(ValueLayout.JAVA_INT, 0);

            final ValueType[] paramTypes = new ValueType[paramCount];
            for (int i = 0; i < paramCount; i++) {
                paramTypes[i] = intToValueType(paramTypesArr.getAtIndex(ValueLayout.JAVA_INT, i));
            }

            final ValueType[] resultTypes = new ValueType[resultCount];
            for (int i = 0; i < resultCount; i++) {
                resultTypes[i] = intToValueType(resultTypesArr.getAtIndex(ValueLayout.JAVA_INT, i));
            }

            return new FunctionSignature(paramTypes, resultTypes);
        } catch (final Throwable e) {
            LOGGER.fine("Failed to get function signature for '" + functionName + "': "
                + e.getMessage());
            return null;
        }
    }

    /**
     * Converts an integer WASM type constant to a {@link ValueType} enum.
     */
    private static ValueType intToValueType(final int typeId) {
        return switch (typeId) {
            case WasmTypes.I32 -> ValueType.I32;
            case WasmTypes.I64 -> ValueType.I64;
            case WasmTypes.F32 -> ValueType.F32;
            case WasmTypes.F64 -> ValueType.F64;
            default -> ValueType.I32; // fallback
        };
    }

    @Override
    public boolean setName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Module name cannot be null");
        }
        ensureNotClosed();

        if (Handles.SET_MODULE_NAME == null) {
            LOGGER.warning("wamr_module_set_name not available");
            return false;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);
            final int rc = (int) Handles.SET_MODULE_NAME.invoke(
                nativeHandle, nameStr, errorBuf, WasmTypes.ERROR_BUF_SIZE);
            return rc == 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to set module name: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getName() {
        ensureNotClosed();

        if (Handles.GET_MODULE_NAME == null) {
            LOGGER.warning("wamr_module_get_name not available");
            return "";
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameBuf = arena.allocate(WasmTypes.MAX_NAME_LENGTH);
            final int rc = (int) Handles.GET_MODULE_NAME.invoke(
                nativeHandle, nameBuf, WasmTypes.MAX_NAME_LENGTH);
            if (rc != 0) {
                return "";
            }
            return nameBuf.getString(0);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get module name: " + e.getMessage());
            return "";
        }
    }

    @Override
    public PackageType getPackageType() {
        ensureNotClosed();

        if (Handles.GET_PACKAGE_TYPE == null) {
            LOGGER.warning("wamr_get_module_package_type not available");
            return PackageType.UNKNOWN;
        }

        try {
            final int typeVal = (int) Handles.GET_PACKAGE_TYPE.invoke(nativeHandle);
            return PackageType.fromNativeValue(typeVal);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get package type: " + e.getMessage());
            return PackageType.UNKNOWN;
        }
    }

    @Override
    public int getPackageVersion() {
        ensureNotClosed();

        if (Handles.GET_PACKAGE_VERSION == null) {
            LOGGER.warning("wamr_get_module_package_version not available");
            return 0;
        }

        try {
            return (int) Handles.GET_PACKAGE_VERSION.invoke(nativeHandle);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get package version: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public String getHash() {
        ensureNotClosed();

        if (Handles.GET_MODULE_HASH == null) {
            LOGGER.warning("wamr_module_get_hash not available");
            return "";
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment hashBuf = arena.allocate(WasmTypes.MAX_NAME_LENGTH);
            final int rc = (int) Handles.GET_MODULE_HASH.invoke(
                nativeHandle, hashBuf, WasmTypes.MAX_NAME_LENGTH);
            if (rc != 0) {
                return "";
            }
            return hashBuf.getString(0);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get module hash: " + e.getMessage());
            return "";
        }
    }

    @Override
    public boolean isUnderlyingBinaryFreeable() {
        ensureNotClosed();

        if (Handles.IS_UNDERLYING_BINARY_FREEABLE == null) {
            LOGGER.warning("wamr_is_underlying_binary_freeable not available");
            return false;
        }

        try {
            final int result = (int) Handles.IS_UNDERLYING_BINARY_FREEABLE.invoke(nativeHandle);
            return result != 0;
        } catch (final Throwable e) {
            LOGGER.warning("Failed to check binary freeability: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean register(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Module name cannot be null");
        }
        ensureNotClosed();

        if (Handles.MODULE_REGISTER == null) {
            LOGGER.warning("wamr_module_register not available");
            return false;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final MemorySegment errorBuf = arena.allocate(256);
            final int result = (int) Handles.MODULE_REGISTER.invoke(
                nativeHandle, nameStr, errorBuf, 256);
            return result != 0;
        } catch (final Throwable t) {
            LOGGER.warning("Failed to register module: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void configureWasi(final WasiConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("WASI configuration cannot be null");
        }
        ensureNotClosed();

        if (Handles.SET_WASI_ARGS == null) {
            LOGGER.warning("wamr_module_set_wasi_args not available");
            return;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final String[] preopens = config.getPreopens();
            final String[] mappedDirs = config.getMappedDirs();
            final String[] envVars = config.getEnvVars();
            final String[] args = config.getArgs();

            final MemorySegment dirList = allocateStringArray(arena, preopens);
            final MemorySegment mapDirList = allocateStringArray(arena, mappedDirs);
            final MemorySegment envList = allocateStringArray(arena, envVars);
            final MemorySegment argvList = allocateStringArray(arena, args);

            Handles.SET_WASI_ARGS.invoke(nativeHandle,
                dirList, preopens.length,
                mapDirList, mappedDirs.length,
                envList, envVars.length,
                argvList, args.length);

            final String[] addrPool = config.getAddrPool();
            if (addrPool.length > 0 && Handles.SET_WASI_ADDR_POOL != null) {
                final MemorySegment addrPoolList = allocateStringArray(arena, addrPool);
                Handles.SET_WASI_ADDR_POOL.invoke(nativeHandle, addrPoolList, addrPool.length);
            }

            final String[] nsLookupPool = config.getNsLookupPool();
            if (nsLookupPool.length > 0 && Handles.SET_WASI_NS_LOOKUP_POOL != null) {
                final MemorySegment nsList = allocateStringArray(arena, nsLookupPool);
                Handles.SET_WASI_NS_LOOKUP_POOL.invoke(nativeHandle, nsList, nsLookupPool.length);
            }
        } catch (final Throwable e) {
            LOGGER.warning("Failed to configure WASI: " + e.getMessage());
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
    public WebAssemblyInstance instantiateEx(final int defaultStackSize,
            final int hostManagedHeapSize, final int maxMemoryPages) throws WasmRuntimeException {
        if (defaultStackSize < 0) {
            throw new IllegalArgumentException("defaultStackSize cannot be negative");
        }
        if (hostManagedHeapSize < 0) {
            throw new IllegalArgumentException("hostManagedHeapSize cannot be negative");
        }
        if (maxMemoryPages < 0) {
            throw new IllegalArgumentException("maxMemoryPages cannot be negative");
        }
        ensureNotClosed();

        if (Handles.INSTANTIATE_EX == null) {
            LOGGER.warning("wamr_instance_create_ex not available");
            throw new WasmRuntimeException("Extended instantiation not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);

            final MemorySegment instanceHandle = (MemorySegment) Handles.INSTANTIATE_EX.invoke(
                nativeHandle, defaultStackSize, hostManagedHeapSize, maxMemoryPages,
                errorBuf, WasmTypes.ERROR_BUF_SIZE);
            if (instanceHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Failed to instantiate WebAssembly module" : errorMsg);
            }

            return new PanamaWebAssemblyInstance(instanceHandle, List.of(), null);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException(
                "Unexpected error during extended module instantiation", e);
        }
    }

    @Override
    public byte[] getCustomSection(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Custom section name cannot be null");
        }
        ensureNotClosed();

        if (Handles.GET_CUSTOM_SECTION == null) {
            LOGGER.warning("wamr_module_get_custom_section not available");
            return null;
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final MemorySegment outLen = arena.allocate(ValueLayout.JAVA_INT);

            final MemorySegment dataPtr = (MemorySegment) Handles.GET_CUSTOM_SECTION.invoke(
                nativeHandle, nameStr, outLen);
            if (dataPtr.equals(MemorySegment.NULL)) {
                return null;
            }

            final int length = outLen.get(ValueLayout.JAVA_INT, 0);
            if (length <= 0) {
                return null;
            }

            // Copy data to Java byte array
            final MemorySegment dataSlice = dataPtr.reinterpret(length);
            final byte[] result = dataSlice.toArray(ValueLayout.JAVA_BYTE);

            // Free the native buffer
            if (Handles.FREE_NATIVE_BUFFER != null) {
                Handles.FREE_NATIVE_BUFFER.invoke(dataPtr);
            }

            return result;
        } catch (final Throwable e) {
            LOGGER.fine("Failed to get custom section '" + name + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public int[] getExportGlobalTypeInfo(final String name) {
        ensureNotClosed();
        if (name == null || Handles.GET_EXPORT_GLOBAL_TYPE_INFO == null) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final MemorySegment valkindOut = arena.allocate(ValueLayout.JAVA_BYTE);
            final MemorySegment isMutableOut = arena.allocate(ValueLayout.JAVA_INT);
            final int result = (int) Handles.GET_EXPORT_GLOBAL_TYPE_INFO.invoke(
                nativeHandle, nameStr, valkindOut, isMutableOut);
            if (result != 0) {
                return null;
            }
            return new int[] {
                Byte.toUnsignedInt(valkindOut.get(ValueLayout.JAVA_BYTE, 0)),
                isMutableOut.get(ValueLayout.JAVA_INT, 0)
            };
        } catch (final Throwable e) {
            LOGGER.fine("Failed to get export global type info for '" + name + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public int[] getExportMemoryTypeInfo(final String name) {
        ensureNotClosed();
        if (name == null || Handles.GET_EXPORT_MEMORY_TYPE_INFO == null) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateFrom(name);
            final MemorySegment isSharedOut = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment initPageCountOut = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment maxPageCountOut = arena.allocate(ValueLayout.JAVA_INT);
            final int result = (int) Handles.GET_EXPORT_MEMORY_TYPE_INFO.invoke(
                nativeHandle, nameStr, isSharedOut, initPageCountOut, maxPageCountOut);
            if (result != 0) {
                return null;
            }
            return new int[] {
                isSharedOut.get(ValueLayout.JAVA_INT, 0),
                initPageCountOut.get(ValueLayout.JAVA_INT, 0),
                maxPageCountOut.get(ValueLayout.JAVA_INT, 0)
            };
        } catch (final Throwable e) {
            LOGGER.fine("Failed to get export memory type info for '" + name + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public WebAssemblyInstance instantiateEx2(final int defaultStackSize,
            final int hostManagedHeapSize, final int maxMemoryPages) throws WasmRuntimeException {
        ensureNotClosed();
        if (defaultStackSize < 0 || hostManagedHeapSize < 0 || maxMemoryPages < 0) {
            throw new IllegalArgumentException("Parameters must be non-negative");
        }

        if (Handles.INSTANTIATE_EX2 == null) {
            throw new WasmRuntimeException("instantiateEx2 not available");
        }

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorBuf = arena.allocate(WasmTypes.ERROR_BUF_SIZE);
            final MemorySegment instanceHandle = (MemorySegment) Handles.INSTANTIATE_EX2.invoke(
                nativeHandle, defaultStackSize, hostManagedHeapSize, maxMemoryPages,
                errorBuf, WasmTypes.ERROR_BUF_SIZE);
            if (instanceHandle.equals(MemorySegment.NULL)) {
                final String errorMsg = errorBuf.getString(0);
                throw new WasmRuntimeException(
                    errorMsg.isEmpty() ? "Failed to instantiate module with ex2 API" : errorMsg);
            }
            return new PanamaWebAssemblyInstance(instanceHandle, List.of(), null);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new WasmRuntimeException("Unexpected error during ex2 instantiation", e);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;

            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    Handles.DESTROY_MODULE.invoke(handle);
                    LOGGER.fine("Destroyed Panama WebAssembly module with handle: " + handle);
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native module: " + e.getMessage());
                }
            }
        }
    }

    private String[] getModuleNames(final MethodHandle namesHandle, final String kind) {
        ensureNotClosed();
        return WasmTypes.readNativeNames(nativeHandle, namesHandle, Handles.FREE_NAMES, kind);
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly module has been closed");
        }
    }
}
