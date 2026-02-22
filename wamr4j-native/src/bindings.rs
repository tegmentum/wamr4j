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
//! This module provides low-level unsafe bindings to the WAMR C API.
//! These bindings are used internally by the safe wrapper functions
//! in the runtime and wamr_wrapper modules.

use std::os::raw::{c_char, c_int, c_uint, c_uchar, c_void};

// =============================================================================
// WAMR Type Definitions
// =============================================================================

/// Opaque handle to WAMR runtime
pub type WasmRuntimeT = c_void;

/// Opaque handle to compiled WASM module
pub type WasmModuleT = c_void;

/// Opaque handle to WASM module instance
pub type WasmModuleInstT = c_void;

/// Opaque handle to WASM function instance
pub type WasmFunctionInstT = c_void;

// =============================================================================
// WAMR C API Function Bindings
// =============================================================================

extern "C" {
    // Runtime Management
    /// Initialize the WAMR runtime
    /// Returns: pointer to runtime instance, or NULL on failure
    pub fn wasm_runtime_init() -> *mut WasmRuntimeT;

    /// Destroy the WAMR runtime and cleanup all resources
    pub fn wasm_runtime_destroy();

    // Module Operations
    /// Load (compile) WebAssembly bytecode into a module
    /// 
    /// # Parameters
    /// - `buf`: WebAssembly bytecode buffer
    /// - `size`: Size of the bytecode buffer
    /// - `error_buf`: Buffer to store error messages
    /// - `error_buf_size`: Size of the error buffer
    /// 
    /// # Returns
    /// Pointer to compiled module, or NULL on failure
    pub fn wasm_runtime_load(
        buf: *const c_uchar,
        size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleT;

    /// Unload a WebAssembly module and free its resources
    pub fn wasm_runtime_unload(module: *mut WasmModuleT);

    // Instance Operations
    /// Instantiate a WebAssembly module
    /// 
    /// # Parameters
    /// - `module`: Compiled WebAssembly module
    /// - `stack_size`: Stack size for the instance
    /// - `heap_size`: Heap size for the instance
    /// - `error_buf`: Buffer to store error messages
    /// - `error_buf_size`: Size of the error buffer
    /// 
    /// # Returns
    /// Pointer to module instance, or NULL on failure
    pub fn wasm_runtime_instantiate(
        module: *const WasmModuleT,
        stack_size: c_uint,
        heap_size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleInstT;

    /// Destroy a WebAssembly module instance and free its resources
    pub fn wasm_runtime_deinstantiate(module_inst: *mut WasmModuleInstT);

    // Function Operations
    /// Look up a function in a module instance by name
    /// 
    /// # Parameters
    /// - `module_inst`: WebAssembly module instance
    /// - `name`: Function name (null-terminated C string)
    /// 
    /// # Returns
    /// Pointer to function instance, or NULL if not found
    pub fn wasm_runtime_lookup_function(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
    ) -> *mut WasmFunctionInstT;

    /// Call a WebAssembly function
    /// 
    /// # Parameters
    /// - `function`: Function instance to call
    /// - `argc`: Number of arguments
    /// - `argv`: Array of argument values (32-bit integers)
    /// 
    /// # Returns
    /// true (nonzero) on success, false (0) on failure
    pub fn wasm_runtime_call_wasm(
        function: *mut WasmFunctionInstT,
        argc: c_uint,
        argv: *mut c_uint,
    ) -> c_int;

    // Function Signature Introspection
    /// Get the number of parameters for a function
    pub fn wasm_func_get_param_count(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
    ) -> u32;

    /// Get the number of results for a function
    pub fn wasm_func_get_result_count(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
    ) -> u32;

    /// Get the parameter types for a function
    pub fn wasm_func_get_param_types(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
        param_types: *mut c_uchar,
    );

    /// Get the result types for a function
    pub fn wasm_func_get_result_types(
        func_inst: *const WasmFunctionInstT,
        module_inst: *const WasmModuleInstT,
        result_types: *mut c_uchar,
    );

    // Memory Operations
    /// Convert WebAssembly application address to native pointer
    /// 
    /// # Parameters
    /// - `module_inst`: WebAssembly module instance
    /// - `app_offset`: WebAssembly memory offset
    /// 
    /// # Returns
    /// Native pointer, or NULL if invalid offset
    pub fn wasm_runtime_addr_app_to_native(
        module_inst: *const WasmModuleInstT,
        app_offset: c_uint,
    ) -> *mut c_void;

    /// Convert native pointer to WebAssembly application address
    /// 
    /// # Parameters
    /// - `module_inst`: WebAssembly module instance
    /// - `native_ptr`: Native memory pointer
    /// 
    /// # Returns
    /// WebAssembly memory offset, or 0 if invalid pointer
    pub fn wasm_runtime_addr_native_to_app(
        module_inst: *const WasmModuleInstT,
        native_ptr: *mut c_void,
    ) -> c_uint;

    /// Get WebAssembly memory address range information
    /// 
    /// # Parameters
    /// - `module_inst`: WebAssembly module instance
    /// - `app_offset`: WebAssembly memory offset to query
    /// - `p_app_start_offset`: Output pointer for start offset
    /// - `p_app_end_offset`: Output pointer for end offset
    /// 
    /// # Returns
    /// Size of the valid address range, or 0 if invalid
    pub fn wasm_runtime_get_app_addr_range(
        module_inst: *const WasmModuleInstT,
        app_offset: c_uint,
        p_app_start_offset: *mut c_uint,
        p_app_end_offset: *mut c_uint,
    ) -> c_uint;
}


// =============================================================================
// Export/Import Kind Constants
// =============================================================================

/// WebAssembly export/import kind: Function
pub const WASM_IMPORT_EXPORT_KIND_FUNC: u32 = 0;

/// WebAssembly export/import kind: Global
pub const WASM_IMPORT_EXPORT_KIND_GLOBAL: u32 = 3;

// =============================================================================
// Value Type Constants
// =============================================================================

/// WebAssembly value type: i32
pub const WASM_I32: u32 = 0;

/// WebAssembly value type: i64
pub const WASM_I64: u32 = 1;

/// WebAssembly value type: f32
pub const WASM_F32: u32 = 2;

/// WebAssembly value type: f64
pub const WASM_F64: u32 = 3;

// =============================================================================
// Export and Global Type Structures
// =============================================================================

/// WebAssembly export information
#[repr(C)]
pub struct wasm_export_t {
    /// Export name
    pub name: *const c_char,
    /// Export kind (function, table, memory, global)
    pub kind: u32,
    /// Type information (union - use based on kind)
    pub type_data: [u8; 8],
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

extern "C" {
    /// Set a global variable value in a module instance
    ///
    /// # Parameters
    /// - `module_inst`: WebAssembly module instance
    /// - `name`: Global variable name
    /// - `value`: Pointer to value buffer
    ///
    /// # Returns
    /// true on success, false on failure
    pub fn wasm_runtime_set_global(
        module_inst: *mut WasmModuleInstT,
        name: *const c_char,
        value: *const c_void,
    ) -> bool;

    /// Get a global variable value from a module instance
    ///
    /// # Parameters
    /// - `module_inst`: WebAssembly module instance
    /// - `name`: Global variable name
    /// - `value`: Pointer to value buffer (output)
    ///
    /// # Returns
    /// true on success, false on failure
    pub fn wasm_runtime_get_global(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
        value: *mut c_void,
    ) -> bool;

    /// Get module from module instance
    ///
    /// # Parameters
    /// - `module_inst`: Module instance pointer
    ///
    /// # Returns
    /// Module pointer
    pub fn wasm_runtime_get_module(module_inst: *mut WasmModuleInstT) -> *mut WasmModuleT;

    /// Get export count from module
    ///
    /// # Parameters
    /// - `module`: Module pointer
    ///
    /// # Returns
    /// Number of exports
    pub fn wasm_runtime_get_export_count(module: *const WasmModuleT) -> u32;

    /// Get export info by index
    ///
    /// # Parameters
    /// - `module`: Module pointer
    /// - `export_index`: Index of export
    /// - `export_type`: Pointer to export info structure to fill
    ///
    /// # Returns
    /// true if successful, false otherwise
    pub fn wasm_runtime_get_export_type(
        module: *const WasmModuleT,
        export_index: i32,
        export_type: *mut wasm_export_t,
    ) -> bool;

    /// Get exported global instance by name
    ///
    /// # Parameters
    /// - `module_inst`: Module instance pointer
    /// - `name`: Global variable name
    /// - `global_inst`: Pointer to global instance structure to fill
    ///
    /// # Returns
    /// true if successful, false otherwise
    pub fn wasm_runtime_get_export_global_inst(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
        global_inst: *mut wasm_global_inst_t,
    ) -> bool;
}