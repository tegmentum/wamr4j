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

/// Opaque handle to WASM execution environment (used in callbacks)
pub type WasmExecEnv = c_void;

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
    /// 0 on success, -1 on failure
    pub fn wasm_runtime_call_wasm(
        function: *mut WasmFunctionInstT,
        argc: c_uint,
        argv: *mut c_uint,
    ) -> c_int;

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
// Additional WAMR API Functions (Extended API)
// =============================================================================

// TODO: These functions don't exist in WAMR 2.4.1 API.
// Need to replace with correct API calls:
// - wasm_runtime_get_exception(module_inst) instead of get_last_error()
// - wasm_func_get_param_count/result_count instead of get_function_signature
// - wasm_runtime_get_memory + wasm_memory_get_cur_page_count for heap size
// - Validation happens during wasm_runtime_load, no separate validate function
//
// Temporarily commented out to get build working. These are not used by
// the import callback implementation.

/*
extern "C" {
    /// Get the size of WebAssembly linear memory in bytes
    pub fn wasm_runtime_get_app_heap_size(module_inst: *const WasmModuleInstT) -> c_uint;

    /// Validate WebAssembly bytecode without compilation
    pub fn wasm_runtime_validate_module(
        buf: *const c_uchar,
        size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> c_int;

    /// Get function signature information
    pub fn wasm_runtime_get_function_signature(
        function: *const WasmFunctionInstT,
        param_count: *mut c_uint,
        result_count: *mut c_uint,
    ) -> c_int;

    /// Get the last error message from WAMR
    pub fn wasm_runtime_get_last_error() -> *const c_char;

    /// Clear the last error message
    pub fn wasm_runtime_clear_last_error();
}
*/

// =============================================================================
// Helper Functions for Safe Wrapper
// =============================================================================

/// Convert pointer to handle for storage
pub fn ptr_to_handle<T>(ptr: *mut T) -> usize {
    ptr as usize
}

/// Convert handle back to pointer
pub fn handle_to_ptr<T>(handle: usize) -> *mut T {
    handle as *mut T
}

// =============================================================================
// Safety and Validation Helpers
// =============================================================================

/// Check if a WAMR handle pointer is valid (non-null)
#[inline(always)]
pub fn is_valid_handle<T>(ptr: *const T) -> bool {
    !ptr.is_null()
}

/// Validate a buffer pointer and size
#[inline(always)]
pub fn is_valid_buffer(ptr: *const c_uchar, size: c_uint) -> bool {
    !ptr.is_null() && size > 0
}

/// Validate a C string pointer
#[inline(always)]
pub fn is_valid_cstr(ptr: *const c_char) -> bool {
    !ptr.is_null()
}

/// Convert a C string to Rust string safely
pub fn cstr_to_rust_string(ptr: *const c_char) -> Result<String, &'static str> {
    if ptr.is_null() {
        return Err("Null pointer");
    }

    unsafe {
        let cstr = std::ffi::CStr::from_ptr(ptr);
        match cstr.to_str() {
            Ok(s) => Ok(s.to_string()),
            Err(_) => Err("Invalid UTF-8"),
        }
    }
}

// TODO: Update to use wasm_runtime_get_exception(module_inst) instead
/*
/// Get WAMR error message safely
pub fn get_wamr_error() -> Option<String> {
    unsafe {
        let error_ptr = wasm_runtime_get_last_error();
        if is_valid_cstr(error_ptr) {
            cstr_to_rust_string(error_ptr).ok()
        } else {
            None
        }
    }
}

/// Clear WAMR error state
pub fn clear_wamr_error() {
    unsafe {
        wasm_runtime_clear_last_error();
    }
}
*/

// =============================================================================
// Constants and Limits
// =============================================================================

/// Default stack size for WebAssembly instances (16KB)
pub const DEFAULT_STACK_SIZE: c_uint = 16 * 1024;

/// Default heap size for WebAssembly instances (16MB)
pub const DEFAULT_HEAP_SIZE: c_uint = 16 * 1024 * 1024;

/// Maximum error buffer size
pub const MAX_ERROR_BUF_SIZE: c_uint = 1024;

/// WebAssembly page size (64KB)
pub const WASM_PAGE_SIZE: c_uint = 65536;

/// Maximum number of function arguments supported
pub const MAX_FUNCTION_ARGS: usize = 32;

/// Maximum number of function results supported
pub const MAX_FUNCTION_RESULTS: usize = 8;

// =============================================================================
// Host Function Registration (Import Support)
// =============================================================================

/// Native symbol entry for host function registration
#[repr(C)]
pub struct NativeSymbol {
    /// Function name (null-terminated C string)
    pub symbol: *const c_char,
    /// Function pointer to native implementation
    pub func_ptr: *mut c_void,
    /// Function signature string (e.g., "(ii)i" for (i32, i32) -> i32)
    pub signature: *const c_char,
    /// Optional attachment data
    pub attachment: *mut c_void,
}

extern "C" {
    /// Register native functions (host functions) with WAMR
    ///
    /// # Parameters
    /// - `module_name`: Module name for the imports (e.g., "env")
    /// - `native_symbols`: Array of native symbol definitions
    /// - `n_native_symbols`: Number of symbols in the array
    ///
    /// # Returns
    /// true on success, false on failure
    pub fn wasm_runtime_register_natives(
        module_name: *const c_char,
        native_symbols: *const NativeSymbol,
        n_native_symbols: c_uint,
    ) -> bool;

    /// Unregister native functions for a module
    ///
    /// # Parameters
    /// - `module_name`: Module name to unregister
    pub fn wasm_runtime_unregister_natives(module_name: *const c_char);

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

    /// Get function arguments from execution environment (for use in native callbacks)
    ///
    /// # Parameters
    /// - `exec_env`: Execution environment passed to native function
    ///
    /// # Returns
    /// Pointer to argument array (u32 values for i32/f32, u64 for i64/f64)
    pub fn wasm_runtime_get_function_argv(exec_env: *mut WasmExecEnv) -> *mut u32;

    /// Get module instance from execution environment
    ///
    /// # Parameters
    /// - `exec_env`: Execution environment passed to native function
    ///
    /// # Returns
    /// Pointer to module instance
    pub fn wasm_runtime_get_module_inst(exec_env: *mut WasmExecEnv) -> *mut WasmModuleInstT;

    /// Get user data (attachment) from native function
    ///
    /// # Parameters
    /// - `exec_env`: Execution environment passed to native function
    ///
    /// # Returns
    /// Pointer to attachment data (void*)
    pub fn wasm_runtime_get_function_attachment(exec_env: *mut WasmExecEnv) -> *mut c_void;
}