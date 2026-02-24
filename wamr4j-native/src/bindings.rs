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

//! Raw WAMR C API bindings
//!
//! This module provides low-level unsafe bindings to the WAMR C API
//! from wasm_export.h (WAMR 2.4.4). Only bindings actually used by
//! the runtime, ffi, and jni_bindings modules are included.

use std::os::raw::{c_char, c_int, c_uint, c_uchar, c_void};

// =============================================================================
// Opaque Handle Type Definitions
// =============================================================================

/// Opaque handle to WAMR runtime
pub type WasmRuntimeT = c_void;

/// Opaque handle to compiled WASM module (WASMModuleCommon*)
pub type WasmModuleT = c_void;

/// Opaque handle to WASM module instance (WASMModuleInstanceCommon*)
pub type WasmModuleInstT = c_void;

/// Opaque handle to WASM function instance (WASMFunctionInstanceCommon*)
pub type WasmFunctionInstT = c_void;

/// Opaque handle to WASM execution environment (WASMExecEnv*)
pub type WasmExecEnvT = c_void;

/// Opaque handle to WASM memory instance (WASMMemoryInstance*)
pub type WasmMemoryInstT = c_void;

/// Opaque handle to WASM function type (WASMFuncType*)
pub type WasmFuncTypeT = c_void;

/// Opaque handle to WASM table type (for introspection via export/import type)
pub type WasmTableTypeT = c_void;

// =============================================================================
// Constants
// =============================================================================

/// WebAssembly export/import kind: Function
pub const WASM_IMPORT_EXPORT_KIND_FUNC: u32 = 0;

/// WebAssembly export/import kind: Table
pub const WASM_IMPORT_EXPORT_KIND_TABLE: u32 = 1;

/// WebAssembly export/import kind: Memory
pub const WASM_IMPORT_EXPORT_KIND_MEMORY: u32 = 2;

/// WebAssembly export/import kind: Global
pub const WASM_IMPORT_EXPORT_KIND_GLOBAL: u32 = 3;

/// Value kind: i32
pub const WASM_I32: u8 = 0;

/// Value kind: i64
pub const WASM_I64: u8 = 1;

/// Value kind: f32
pub const WASM_F32: u8 = 2;

/// Value kind: f64
pub const WASM_F64: u8 = 3;

/// Value kind: externref
pub const WASM_EXTERNREF: u8 = 128;

/// Value kind: funcref
pub const WASM_FUNCREF: u8 = 129;

// =============================================================================
// Struct Definitions
// =============================================================================

/// Typed value for the call_wasm_a API.
/// Layout: 1-byte kind + 7-byte padding + 8-byte union = 16 bytes total.
#[repr(C)]
pub struct WasmValT {
    /// Value kind (WASM_I32, WASM_I64, WASM_F32, WASM_F64)
    pub kind: u8,
    /// Padding for alignment
    pub _paddings: [u8; 7],
    /// Value data (8 bytes, interpreted according to kind)
    pub data: [u8; 8],
}

impl WasmValT {
    /// Create an i32 value.
    pub fn i32(v: i32) -> Self {
        let mut data = [0u8; 8];
        data[..4].copy_from_slice(&v.to_le_bytes());
        Self { kind: WASM_I32, _paddings: [0; 7], data }
    }

    /// Create an i64 value.
    pub fn i64(v: i64) -> Self {
        let mut data = [0u8; 8];
        data.copy_from_slice(&v.to_le_bytes());
        Self { kind: WASM_I64, _paddings: [0; 7], data }
    }

    /// Create an f32 value.
    pub fn f32(v: f32) -> Self {
        let mut data = [0u8; 8];
        data[..4].copy_from_slice(&v.to_le_bytes());
        Self { kind: WASM_F32, _paddings: [0; 7], data }
    }

    /// Create an f64 value.
    pub fn f64(v: f64) -> Self {
        let mut data = [0u8; 8];
        data.copy_from_slice(&v.to_le_bytes());
        Self { kind: WASM_F64, _paddings: [0; 7], data }
    }

    /// Read the value as i32 (caller must check kind).
    pub fn as_i32(&self) -> i32 {
        i32::from_le_bytes(self.data[..4].try_into().unwrap())
    }

    /// Read the value as i64 (caller must check kind).
    pub fn as_i64(&self) -> i64 {
        i64::from_le_bytes(self.data.try_into().unwrap())
    }

    /// Read the value as f32 (caller must check kind).
    pub fn as_f32(&self) -> f32 {
        f32::from_le_bytes(self.data[..4].try_into().unwrap())
    }

    /// Read the value as f64 (caller must check kind).
    pub fn as_f64(&self) -> f64 {
        f64::from_le_bytes(self.data.try_into().unwrap())
    }

    /// Create a zeroed value with the given kind (for result slots).
    pub fn zeroed(kind: u8) -> Self {
        Self { kind, _paddings: [0; 7], data: [0; 8] }
    }
}

/// WebAssembly export information
#[repr(C)]
pub struct wasm_export_t {
    /// Export name
    pub name: *const c_char,
    /// Export kind (function, table, memory, global)
    pub kind: u32,
    /// Padding to align union to pointer size
    _padding: u32,
    /// Union of type pointers (func_type, table_type, global_type, memory_type)
    pub type_ptr: *const c_void,
}

/// WebAssembly import information
#[repr(C)]
pub struct wasm_import_t {
    /// Import module name
    pub module_name: *const c_char,
    /// Import name
    pub name: *const c_char,
    /// Import kind (function, table, memory, global)
    pub kind: u32,
    /// Whether the import is linked
    pub linked: u8,
    /// Padding to align union to pointer size
    _padding: [u8; 3],
    /// Union of type pointers (func_type, table_type, global_type, memory_type)
    pub type_ptr: *const c_void,
}

/// WebAssembly global instance
#[repr(C)]
pub struct wasm_global_inst_t {
    /// Value type
    pub kind: u8,
    /// Is mutable
    pub is_mutable: bool,
    /// Pointer to global data
    pub global_data: *mut c_void,
}

/// WebAssembly table instance
#[repr(C)]
pub struct wasm_table_inst_t {
    /// Element kind (WASM_FUNCREF=129, WASM_EXTERNREF=128)
    pub elem_kind: u8,
    /// Padding for alignment
    _padding: [u8; 3],
    /// Current number of elements
    pub cur_size: u32,
    /// Maximum number of elements
    pub max_size: u32,
    /// Internal element array (opaque, for internal use only)
    pub elems: *mut c_void,
}

/// Native symbol for host function registration (from lib_export.h)
#[repr(C)]
pub struct NativeSymbol {
    /// Function name
    pub symbol: *const c_char,
    /// Function pointer (for raw mode: void fn(wasm_exec_env_t, uint64_t*))
    pub func_ptr: *mut c_void,
    /// Signature string (e.g., "(ii)i" for (i32,i32)->i32)
    pub signature: *const c_char,
    /// Attachment data retrievable via wasm_runtime_get_function_attachment
    pub attachment: *mut c_void,
}

// =============================================================================
// WAMR C API Function Bindings — Runtime Management
// =============================================================================

extern "C" {
    /// Initialize the WAMR runtime with default settings
    pub fn wasm_runtime_init() -> bool;

    /// Destroy the WAMR runtime and cleanup all resources
    pub fn wasm_runtime_destroy();
}

// =============================================================================
// WAMR C API Function Bindings — Module Loading
// =============================================================================

extern "C" {
    /// Load WebAssembly bytecode into a module
    pub fn wasm_runtime_load(
        buf: *const c_uchar,
        size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleT;

    /// Unload a WebAssembly module and free its resources
    pub fn wasm_runtime_unload(module: *mut WasmModuleT);
}

// =============================================================================
// WAMR C API Function Bindings — Instance Operations
// =============================================================================

extern "C" {
    /// Instantiate a WebAssembly module
    pub fn wasm_runtime_instantiate(
        module: *const WasmModuleT,
        stack_size: c_uint,
        heap_size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleInstT;

    /// Destroy a WebAssembly module instance
    pub fn wasm_runtime_deinstantiate(module_inst: *mut WasmModuleInstT);
}

// =============================================================================
// WAMR C API Function Bindings — Execution Environment
// =============================================================================

extern "C" {
    /// Create an execution environment for a module instance
    pub fn wasm_runtime_create_exec_env(
        module_inst: *mut WasmModuleInstT,
        stack_size: c_uint,
    ) -> *mut WasmExecEnvT;

    /// Destroy an execution environment
    pub fn wasm_runtime_destroy_exec_env(exec_env: *mut WasmExecEnvT);
}

// =============================================================================
// WAMR C API Function Bindings — Function Operations
// =============================================================================

extern "C" {
    /// Look up a function in a module instance by name
    pub fn wasm_runtime_lookup_function(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
    ) -> *mut WasmFunctionInstT;

    /// Call a WebAssembly function (argv-based, 32-bit cells)
    pub fn wasm_runtime_call_wasm(
        exec_env: *mut WasmExecEnvT,
        function: *mut WasmFunctionInstT,
        argc: c_uint,
        argv: *mut c_uint,
    ) -> c_int;

    /// Call a WASM function with typed wasm_val_t args and results.
    /// Returns true on success, false on failure.
    pub fn wasm_runtime_call_wasm_a(
        exec_env: *mut WasmExecEnvT,
        function: *mut WasmFunctionInstT,
        num_results: c_uint,
        results: *mut WasmValT,
        num_args: c_uint,
        args: *mut WasmValT,
    ) -> bool;
}

// =============================================================================
// WAMR C API Function Bindings — Exception Handling
// =============================================================================

extern "C" {
    /// Get the exception string from a module instance
    pub fn wasm_runtime_get_exception(
        module_inst: *mut WasmModuleInstT,
    ) -> *const c_char;

    /// Clear the exception on a module instance
    pub fn wasm_runtime_clear_exception(
        module_inst: *mut WasmModuleInstT,
    );

    /// Set a custom exception on a module instance.
    /// Pass null to clear the exception.
    pub fn wasm_runtime_set_exception(
        module_inst: *mut WasmModuleInstT,
        exception: *const c_char,
    );

    /// Terminate the execution of a module instance.
    /// Can be called from another thread to interrupt long-running execution.
    pub fn wasm_runtime_terminate(
        module_inst: *mut WasmModuleInstT,
    );
}

// =============================================================================
// WAMR C API Function Bindings — Execution Control
// =============================================================================

extern "C" {
    /// Set the instruction count limit for an execution environment.
    /// When the limit is reached, execution will trap.
    /// Pass -1 to remove the limit.
    pub fn wasm_runtime_set_instruction_count_limit(
        exec_env: *mut WasmExecEnvT,
        limit: c_int,
    );
}

// =============================================================================
// WAMR C API Function Bindings — WASI Support
// =============================================================================

extern "C" {
    /// Set WASI parameters on a module (with default stdio).
    pub fn wasm_runtime_set_wasi_args(
        module: *mut WasmModuleT,
        dir_list: *const *const c_char,
        dir_count: c_uint,
        map_dir_list: *const *const c_char,
        map_dir_count: c_uint,
        env: *const *const c_char,
        env_count: c_uint,
        argv: *const *const c_char,
        argc: c_int,
    );

    /// Set WASI parameters on a module (with custom stdio file descriptors).
    pub fn wasm_runtime_set_wasi_args_ex(
        module: *mut WasmModuleT,
        dir_list: *const *const c_char,
        dir_count: c_uint,
        map_dir_list: *const *const c_char,
        map_dir_count: c_uint,
        env: *const *const c_char,
        env_count: c_uint,
        argv: *const *const c_char,
        argc: c_int,
        stdinfd: i64,
        stdoutfd: i64,
        stderrfd: i64,
    );

    /// Set WASI address pool for network operations.
    pub fn wasm_runtime_set_wasi_addr_pool(
        module: *mut WasmModuleT,
        addr_pool: *const *const c_char,
        addr_pool_size: c_uint,
    );

    /// Set WASI name server lookup pool for DNS resolution.
    pub fn wasm_runtime_set_wasi_ns_lookup_pool(
        module: *mut WasmModuleT,
        ns_lookup_pool: *const *const c_char,
        ns_lookup_pool_size: c_uint,
    );

    /// Check if a module instance is running in WASI mode.
    pub fn wasm_runtime_is_wasi_mode(
        module_inst: *mut WasmModuleInstT,
    ) -> bool;

    /// Get the WASI exit code from a module instance.
    pub fn wasm_runtime_get_wasi_exit_code(
        module_inst: *mut WasmModuleInstT,
    ) -> c_uint;

    /// Lookup the WASI _start function in a module instance.
    pub fn wasm_runtime_lookup_wasi_start_function(
        module_inst: *mut WasmModuleInstT,
    ) -> *mut WasmFunctionInstT;

    /// Execute the _start function (WASI entry point).
    pub fn wasm_application_execute_main(
        module_inst: *mut WasmModuleInstT,
        argc: c_int,
        argv: *mut *mut c_char,
    ) -> bool;

    /// Execute a named function with string arguments.
    pub fn wasm_application_execute_func(
        module_inst: *mut WasmModuleInstT,
        name: *const c_char,
        argc: c_int,
        argv: *mut *mut c_char,
    ) -> bool;
}

// =============================================================================
// WAMR C API Function Bindings — Function Signature Introspection
// =============================================================================

extern "C" {
    /// Get the number of parameters for a function instance
    pub fn wasm_func_get_param_count(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
    ) -> u32;

    /// Get the number of results for a function instance
    pub fn wasm_func_get_result_count(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
    ) -> u32;

    /// Get the parameter types for a function instance
    pub fn wasm_func_get_param_types(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
        param_types: *mut c_uchar,
    );

    /// Get the result types for a function instance
    pub fn wasm_func_get_result_types(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
        result_types: *mut c_uchar,
    );
}

// =============================================================================
// WAMR C API Function Bindings — Function Type Introspection
// =============================================================================

extern "C" {
    /// Get the number of parameters for a function type
    pub fn wasm_func_type_get_param_count(func_type: *const WasmFuncTypeT) -> u32;

    /// Get the value kind of a parameter at given index
    pub fn wasm_func_type_get_param_valkind(
        func_type: *const WasmFuncTypeT,
        param_index: u32,
    ) -> u8;

    /// Get the number of results for a function type
    pub fn wasm_func_type_get_result_count(func_type: *const WasmFuncTypeT) -> u32;

    /// Get the value kind of a result at given index
    pub fn wasm_func_type_get_result_valkind(
        func_type: *const WasmFuncTypeT,
        result_index: u32,
    ) -> u8;
}

// =============================================================================
// WAMR C API Function Bindings — Import/Export Introspection
// =============================================================================

extern "C" {
    /// Get module from module instance
    pub fn wasm_runtime_get_module(
        module_inst: *mut WasmModuleInstT,
    ) -> *mut WasmModuleT;

    /// Get the number of exports for a module
    pub fn wasm_runtime_get_export_count(module: *const WasmModuleT) -> u32;

    /// Get export info by index
    pub fn wasm_runtime_get_export_type(
        module: *const WasmModuleT,
        export_index: i32,
        export_type: *mut wasm_export_t,
    );

    /// Get exported global instance by name
    pub fn wasm_runtime_get_export_global_inst(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
        global_inst: *mut wasm_global_inst_t,
    ) -> bool;

    /// Get the number of imports for a module
    pub fn wasm_runtime_get_import_count(module: *const WasmModuleT) -> u32;

    /// Get import info by index
    pub fn wasm_runtime_get_import_type(
        module: *const WasmModuleT,
        import_index: i32,
        import_type: *mut wasm_import_t,
    );
}

// =============================================================================
// WAMR C API Function Bindings — Version
// =============================================================================

extern "C" {
    /// Get WAMR runtime version (major, minor, patch)
    pub fn wasm_runtime_get_version(
        major: *mut u32,
        minor: *mut u32,
        patch: *mut u32,
    );
}

// =============================================================================
// Constants — Running Modes
// =============================================================================

/// Running mode: interpreter
pub const RUNNING_MODE_INTERP: u32 = 0;
/// Running mode: fast JIT
pub const RUNNING_MODE_FAST_JIT: u32 = 1;
/// Running mode: LLVM JIT
pub const RUNNING_MODE_LLVM_JIT: u32 = 2;
/// Running mode: multi-tier JIT
pub const RUNNING_MODE_MULTI_TIER_JIT: u32 = 3;

// =============================================================================
// WAMR C API Function Bindings — Runtime Configuration
// =============================================================================

extern "C" {
    /// Check if a running mode is supported
    pub fn wasm_runtime_is_running_mode_supported(
        mode: u32,
    ) -> bool;

    /// Set the default running mode for new module instances
    pub fn wasm_runtime_set_default_running_mode(
        mode: u32,
    ) -> bool;

    /// Set the running mode for a specific module instance
    pub fn wasm_runtime_set_running_mode(
        module_inst: *mut WasmModuleInstT,
        mode: u32,
    ) -> bool;

    /// Get the running mode for a specific module instance
    pub fn wasm_runtime_get_running_mode(
        module_inst: *mut WasmModuleInstT,
    ) -> u32;

    /// Set log verbosity level (0-5)
    pub fn wasm_runtime_set_log_level(
        level: c_int,
    );

    /// Enable or disable bounds checks for a module instance
    pub fn wasm_runtime_set_bounds_checks(
        module_inst: *mut WasmModuleInstT,
        enable: bool,
    ) -> bool;

    /// Check if bounds checks are enabled for a module instance
    pub fn wasm_runtime_is_bounds_checks_enabled(
        module_inst: *mut WasmModuleInstT,
    ) -> bool;
}

// =============================================================================
// Constants — Package Types
// =============================================================================

/// Package type: WASM bytecode
pub const PACKAGE_TYPE_WASM: u32 = 0;
/// Package type: AOT compiled
pub const PACKAGE_TYPE_AOT: u32 = 1;
/// Package type: Unknown
pub const PACKAGE_TYPE_UNKNOWN: u32 = 0xFFFF;

// =============================================================================
// Struct Definitions — LoadArgs
// =============================================================================

/// Arguments for wasm_runtime_load_ex
#[repr(C)]
pub struct LoadArgs {
    /// Module name
    pub name: *const c_char,
    /// Used by Wasm C API only
    pub clone_wasm_binary: bool,
    /// If true, loader creates copies of const strings so the binary can be freed
    pub wasm_binary_freeable: bool,
    /// If true, don't resolve symbols yet (requires wasm_runtime_resolve_symbols call)
    pub no_resolve: bool,
}

// =============================================================================
// WAMR C API Function Bindings — Module Management
// =============================================================================

extern "C" {
    /// Set the name of a module
    pub fn wasm_runtime_set_module_name(
        module: *mut WasmModuleT,
        name: *const c_char,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> bool;

    /// Get the name of a module
    pub fn wasm_runtime_get_module_name(
        module: *mut WasmModuleT,
    ) -> *const c_char;

    /// Register a named module for multi-module support
    pub fn wasm_runtime_register_module(
        module_name: *const c_char,
        module: *mut WasmModuleT,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> bool;

    /// Find a previously registered module by name
    pub fn wasm_runtime_find_module_registered(
        module_name: *const c_char,
    ) -> *mut WasmModuleT;

    // Note: wasm_runtime_get_module_hash is only available on linux-sgx
    // with remote attestation enabled. Not bound for standard builds.

    /// Get package type from a buffer
    pub fn wasm_runtime_get_file_package_type(
        buf: *const c_uchar,
        size: c_uint,
    ) -> u32;

    /// Get package type from a loaded module
    pub fn wasm_runtime_get_module_package_type(
        module: *const WasmModuleT,
    ) -> u32;

    /// Get package version from a buffer
    pub fn wasm_runtime_get_file_package_version(
        buf: *const c_uchar,
        size: c_uint,
    ) -> u32;

    /// Get package version from a loaded module
    pub fn wasm_runtime_get_module_package_version(
        module: *const WasmModuleT,
    ) -> u32;

    /// Get the currently supported version for a package type
    pub fn wasm_runtime_get_current_package_version(
        package_type: u32,
    ) -> u32;

    /// Check whether a file is an AOT XIP file
    pub fn wasm_runtime_is_xip_file(
        buf: *const c_uchar,
        size: c_uint,
    ) -> bool;

    /// Check if the underlying binary can be freed after loading
    pub fn wasm_runtime_is_underlying_binary_freeable(
        module: *const WasmModuleT,
    ) -> bool;

    /// Load a module with extended arguments
    pub fn wasm_runtime_load_ex(
        buf: *mut c_uchar,
        size: c_uint,
        args: *const LoadArgs,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleT;

    /// Resolve symbols for a previously loaded module
    pub fn wasm_runtime_resolve_symbols(
        module: *mut WasmModuleT,
    ) -> bool;
}

// =============================================================================
// WAMR C API Function Bindings — Table Operations
// =============================================================================

extern "C" {
    /// Get an exported table instance by name
    pub fn wasm_runtime_get_export_table_inst(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
        table_inst: *mut wasm_table_inst_t,
    ) -> bool;

    /// Get a function instance from a table at the given index
    pub fn wasm_table_get_func_inst(
        module_inst: *const WasmModuleInstT,
        table_inst: *const wasm_table_inst_t,
        idx: c_uint,
    ) -> *mut WasmFunctionInstT;

    /// Call a function indirectly via table index (uses default table 0)
    pub fn wasm_runtime_call_indirect(
        exec_env: *mut WasmExecEnvT,
        element_index: c_uint,
        argc: c_uint,
        argv: *mut c_uint,
    ) -> bool;
}

// =============================================================================
// WAMR C API Function Bindings — Table Type Introspection
// =============================================================================

extern "C" {
    /// Get element kind from a table type
    pub fn wasm_table_type_get_elem_kind(table_type: *const WasmTableTypeT) -> u8;

    /// Check if table type is shared
    pub fn wasm_table_type_get_shared(table_type: *const WasmTableTypeT) -> bool;

    /// Get initial size from a table type
    pub fn wasm_table_type_get_init_size(table_type: *const WasmTableTypeT) -> c_uint;

    /// Get maximum size from a table type
    pub fn wasm_table_type_get_max_size(table_type: *const WasmTableTypeT) -> c_uint;
}

// =============================================================================
// WAMR C API Function Bindings — Memory Operations
// =============================================================================

extern "C" {
    /// Convert WebAssembly application address to native pointer
    pub fn wasm_runtime_addr_app_to_native(
        module_inst: *const WasmModuleInstT,
        app_offset: u64,
    ) -> *mut c_void;

    /// Get WebAssembly memory address range information
    pub fn wasm_runtime_get_app_addr_range(
        module_inst: *const WasmModuleInstT,
        app_offset: u64,
        p_app_start_offset: *mut u64,
        p_app_end_offset: *mut u64,
    ) -> bool;

    /// Enlarge module instance memory by pages
    pub fn wasm_runtime_enlarge_memory(
        module_inst: *mut WasmModuleInstT,
        inc_page_count: u64,
    ) -> bool;

    /// Get the default memory instance
    pub fn wasm_runtime_get_default_memory(
        module_inst: *const WasmModuleInstT,
    ) -> *mut WasmMemoryInstT;

    /// Get current page count for a memory instance
    pub fn wasm_memory_get_cur_page_count(
        memory_inst: *const WasmMemoryInstT,
    ) -> u64;

    /// Get maximum page count for a memory instance
    pub fn wasm_memory_get_max_page_count(
        memory_inst: *const WasmMemoryInstT,
    ) -> u64;

    /// Get shared status for a memory instance
    pub fn wasm_memory_get_shared(
        memory_inst: *const WasmMemoryInstT,
    ) -> bool;

    /// Allocate memory from the module instance's heap
    pub fn wasm_runtime_module_malloc(
        module_inst: *mut WasmModuleInstT,
        size: u64,
        p_native_addr: *mut *mut c_void,
    ) -> u64;

    /// Free memory allocated by wasm_runtime_module_malloc
    pub fn wasm_runtime_module_free(
        module_inst: *mut WasmModuleInstT,
        ptr: u64,
    );

    /// Duplicate data into module instance memory
    pub fn wasm_runtime_module_dup_data(
        module_inst: *mut WasmModuleInstT,
        src: *const c_char,
        size: u64,
    ) -> u64;

    /// Validate an application address range
    pub fn wasm_runtime_validate_app_addr(
        module_inst: *const WasmModuleInstT,
        app_offset: u64,
        size: u64,
    ) -> bool;

    /// Validate an application string address (null-terminated)
    pub fn wasm_runtime_validate_app_str_addr(
        module_inst: *const WasmModuleInstT,
        app_str_offset: u64,
    ) -> bool;

    /// Validate a native address range
    pub fn wasm_runtime_validate_native_addr(
        module_inst: *const WasmModuleInstT,
        native_ptr: *mut c_void,
        size: u64,
    ) -> bool;

    /// Convert native pointer to application offset
    pub fn wasm_runtime_addr_native_to_app(
        module_inst: *const WasmModuleInstT,
        native_ptr: *mut c_void,
    ) -> u64;

    /// Get native address range information
    pub fn wasm_runtime_get_native_addr_range(
        module_inst: *const WasmModuleInstT,
        native_ptr: *mut u8,
        p_native_start_addr: *mut *mut u8,
        p_native_end_addr: *mut *mut u8,
    ) -> bool;

    /// Get memory instance by index
    pub fn wasm_runtime_get_memory(
        module_inst: *const WasmModuleInstT,
        index: u32,
    ) -> *mut WasmMemoryInstT;

    /// Get the base address of a memory instance
    pub fn wasm_memory_get_base_address(
        memory_inst: *const WasmMemoryInstT,
    ) -> *mut c_void;

    /// Get bytes per page for a memory instance
    pub fn wasm_memory_get_bytes_per_page(
        memory_inst: *const WasmMemoryInstT,
    ) -> u64;

    /// Enlarge a specific memory instance by pages
    pub fn wasm_memory_enlarge(
        memory_inst: *mut WasmMemoryInstT,
        inc_page_count: u64,
    ) -> bool;
}

// =============================================================================
// WAMR C API Function Bindings — Host Function Registration
// =============================================================================

extern "C" {
    /// Register native functions with raw argument passing.
    /// Native functions receive (wasm_exec_env_t, uint64_t* args).
    pub fn wasm_runtime_register_natives_raw(
        module_name: *const c_char,
        native_symbols: *mut NativeSymbol,
        n_native_symbols: u32,
    ) -> bool;

    /// Unregister previously registered native functions.
    /// The native_symbols pointer must be the same pointer passed to register.
    pub fn wasm_runtime_unregister_natives(
        module_name: *const c_char,
        native_symbols: *mut NativeSymbol,
    ) -> bool;

    /// Get the attachment for the current native function from the execution environment.
    pub fn wasm_runtime_get_function_attachment(
        exec_env: *mut WasmExecEnvT,
    ) -> *mut c_void;
}

// =============================================================================
// WAMR C API Function Bindings — Custom Data & Context
// =============================================================================

extern "C" {
    /// Set custom data on a module instance.
    pub fn wasm_runtime_set_custom_data(
        module_inst: *mut WasmModuleInstT,
        custom_data: *mut c_void,
    );

    /// Get custom data from a module instance.
    pub fn wasm_runtime_get_custom_data(
        module_inst: *mut WasmModuleInstT,
    ) -> *mut c_void;

    /// Set user data on an execution environment.
    pub fn wasm_runtime_set_user_data(
        exec_env: *mut WasmExecEnvT,
        user_data: *mut c_void,
    );

    /// Get user data from an execution environment.
    pub fn wasm_runtime_get_user_data(
        exec_env: *mut WasmExecEnvT,
    ) -> *mut c_void;

    /// Get the singleton execution environment for a module instance.
    pub fn wasm_runtime_get_exec_env_singleton(
        module_inst: *mut WasmModuleInstT,
    ) -> *mut WasmExecEnvT;

    /// Get the module instance associated with an execution environment.
    pub fn wasm_runtime_get_module_inst(
        exec_env: *mut WasmExecEnvT,
    ) -> *mut WasmModuleInstT;
}

// =============================================================================
// WAMR C API Function Bindings — Debugging & Profiling
// =============================================================================

extern "C" {
    /// Dump call stack to stdout.
    pub fn wasm_runtime_dump_call_stack(exec_env: *mut WasmExecEnvT);

    /// Get the required buffer size for call stack dump.
    pub fn wasm_runtime_get_call_stack_buf_size(
        exec_env: *mut WasmExecEnvT,
    ) -> c_uint;

    /// Dump call stack to a buffer.
    pub fn wasm_runtime_dump_call_stack_to_buf(
        exec_env: *mut WasmExecEnvT,
        buf: *mut c_char,
        len: c_uint,
    ) -> c_uint;

    /// Dump performance profiling data to stdout.
    pub fn wasm_runtime_dump_perf_profiling(
        module_inst: *mut WasmModuleInstT,
    );

    /// Get total WASM execution time in milliseconds.
    pub fn wasm_runtime_sum_wasm_exec_time(
        module_inst: *mut WasmModuleInstT,
    ) -> f64;

    /// Get execution time for a specific function in milliseconds.
    pub fn wasm_runtime_get_wasm_func_exec_time(
        module_inst: *mut WasmModuleInstT,
        func_name: *const c_char,
    ) -> f64;

    /// Dump memory consumption info to stdout.
    pub fn wasm_runtime_dump_mem_consumption(
        exec_env: *mut WasmExecEnvT,
    );
}

// =============================================================================
// WAMR C API Function Bindings — Threading
// =============================================================================

extern "C" {
    /// Initialize the thread environment for the current native thread.
    pub fn wasm_runtime_init_thread_env() -> bool;

    /// Destroy the thread environment for the current native thread.
    pub fn wasm_runtime_destroy_thread_env();

    /// Check if the thread environment has been initialized.
    pub fn wasm_runtime_thread_env_inited() -> bool;

    /// Set the maximum number of threads for WASM thread management.
    pub fn wasm_runtime_set_max_thread_num(num: c_uint);
}

// =============================================================================
// WAMR C API Function Bindings — Advanced Instantiation & Miscellaneous
// =============================================================================

/// InstantiationArgs struct for extended instantiation.
#[repr(C)]
pub struct InstantiationArgs {
    pub default_stack_size: c_uint,
    pub host_managed_heap_size: c_uint,
    pub max_memory_pages: c_uint,
}

extern "C" {
    /// Instantiate a module with extended arguments.
    pub fn wasm_runtime_instantiate_ex(
        module: *const WasmModuleT,
        args: *const InstantiationArgs,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleInstT;

    /// Get a custom section from a module.
    pub fn wasm_runtime_get_custom_section(
        module: *const WasmModuleT,
        name: *const c_char,
        len: *mut c_uint,
    ) -> *const u8;

    /// Allocate memory from the WAMR runtime allocator.
    pub fn wasm_runtime_malloc(size: c_uint) -> *mut c_void;

    /// Reallocate memory from the WAMR runtime allocator.
    pub fn wasm_runtime_realloc(ptr: *mut c_void, size: c_uint) -> *mut c_void;

    /// Free memory allocated by the WAMR runtime allocator.
    pub fn wasm_runtime_free(ptr: *mut c_void);
}
