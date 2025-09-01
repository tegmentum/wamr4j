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

//! Shared native library for wamr4j JNI and Panama FFI bindings
//!
//! This library provides a unified interface to the WAMR WebAssembly runtime
//! that can be accessed from both JNI (Java 8+) and Panama FFI (Java 23+).
//! All functions are exported with C ABI for maximum compatibility.
//!
//! # Architecture
//!
//! The library is structured as follows:
//! - `ffi` - C-compatible FFI functions for external consumption
//! - `runtime` - High-level runtime management functions
//! - `wamr_wrapper` - Core WAMR abstractions and types
//! - `utils` - Utility functions for error handling and conversions
//! - `jni_bindings` - JNI-specific bindings
//! - `panama_bindings` - Panama FFI-specific bindings
//!
//! # Safety
//!
//! All FFI functions perform comprehensive validation and error handling
//! to prevent JVM crashes or undefined behavior. Memory management is
//! handled safely using Rust's RAII patterns.

use std::os::raw::{c_char, c_int, c_long, c_uchar, c_void};

// Core modules
pub mod ffi;
pub mod runtime;
pub mod wamr_wrapper;
pub mod utils;

// Platform-specific bindings
pub mod jni_bindings;
pub mod panama_bindings;

// Re-export core types for easier access
pub use wamr_wrapper::{
    WamrRuntime, WamrModule, WamrInstance, WamrFunction, WamrMemory,
    WasmValue, WasmType, WamrError,
};

pub use runtime::RuntimeConfig;

// Re-export FFI functions for direct C linkage
pub use ffi::{
    // Runtime management
    wamr_runtime_init,
    wamr_runtime_init_with_config,
    wamr_runtime_destroy,
    wamr_runtime_is_valid,
    
    // Module operations
    wamr_module_compile,
    wamr_module_validate,
    wamr_module_destroy,
    wamr_module_get_size,
    
    // Instance operations
    wamr_instance_create,
    wamr_instance_destroy,
    wamr_instance_is_valid,
    
    // Function operations
    wamr_function_lookup,
    wamr_function_call,
    wamr_function_get_signature,
    
    // Memory operations
    wamr_memory_get,
    wamr_memory_size,
    wamr_memory_data,
    wamr_memory_grow,
    wamr_memory_read,
    wamr_memory_write,
    
    // Utility functions
    wamr_get_version,
    wamr_get_last_error,
    wamr_clear_last_error,
    
    // FFI types
    WasmValueFFI,
    WASM_TYPE_I32, WASM_TYPE_I64, WASM_TYPE_F32, WASM_TYPE_F64,
};

// =============================================================================
// Legacy FFI Functions (for backward compatibility)
// =============================================================================

/// Legacy function - Initialize the WAMR runtime
/// Use wamr_runtime_init instead
#[no_mangle]
pub extern "C" fn wamr_create_runtime() -> *mut c_void {
    ffi::wamr_runtime_init()
}

/// Legacy function - Destroy the WAMR runtime
/// Use wamr_runtime_destroy instead
#[no_mangle]
pub extern "C" fn wamr_destroy_runtime(runtime: *mut c_void) {
    ffi::wamr_runtime_destroy(runtime)
}

/// Legacy function - Compile a WebAssembly module
/// Use wamr_module_compile instead
#[no_mangle]
pub extern "C" fn wamr_compile_module(
    runtime: *mut c_void,
    wasm_bytes: *const c_uchar,
    length: c_long,
) -> *mut c_void {
    let mut error_buf = [0i8; 256];
    ffi::wamr_module_compile(
        runtime,
        wasm_bytes,
        length,
        error_buf.as_mut_ptr(),
        error_buf.len() as c_int,
    )
}

/// Legacy function - Destroy a WebAssembly module
/// Use wamr_module_destroy instead
#[no_mangle]
pub extern "C" fn wamr_destroy_module(module: *mut c_void) {
    ffi::wamr_module_destroy(module)
}

/// Legacy function - Instantiate a WebAssembly module
/// Use wamr_instance_create instead
#[no_mangle]
pub extern "C" fn wamr_instantiate_module(module: *mut c_void) -> *mut c_void {
    let mut error_buf = [0i8; 256];
    ffi::wamr_instance_create(
        module,
        16 * 1024,      // 16KB stack
        16 * 1024 * 1024, // 16MB heap
        error_buf.as_mut_ptr(),
        error_buf.len() as c_int,
    )
}

/// Legacy function - Destroy a WebAssembly instance
/// Use wamr_instance_destroy instead
#[no_mangle]
pub extern "C" fn wamr_destroy_instance(instance: *mut c_void) {
    ffi::wamr_instance_destroy(instance)
}

/// Legacy function - Get a function from an instance
/// Use wamr_function_lookup instead
#[no_mangle]
pub extern "C" fn wamr_get_function(
    instance: *mut c_void,
    name: *const c_char,
) -> *mut c_void {
    let mut error_buf = [0i8; 256];
    ffi::wamr_function_lookup(
        instance,
        name,
        error_buf.as_mut_ptr(),
        error_buf.len() as c_int,
    )
}

/// Legacy function - Call a WebAssembly function
/// Use wamr_function_call instead
#[no_mangle]
pub extern "C" fn wamr_call_function(
    function: *mut c_void,
    args: *const c_void,
    arg_count: c_int,
    results: *mut c_void,
    result_capacity: c_int,
) -> c_int {
    let mut error_buf = [0i8; 256];
    
    // Convert legacy args format to new FFI format
    // For now, use empty args since the legacy format is not well-defined
    ffi::wamr_function_call(
        function,
        std::ptr::null(), // No args in legacy format
        0,
        results as *mut ffi::WasmValueFFI,
        result_capacity,
        error_buf.as_mut_ptr(),
        error_buf.len() as c_int,
    )
}

/// Legacy function - Get memory from an instance
/// Use wamr_memory_get instead
#[no_mangle]
pub extern "C" fn wamr_get_memory(instance: *mut c_void) -> *mut c_void {
    ffi::wamr_memory_get(instance)
}

/// Legacy function - Get memory size
/// Use wamr_memory_size instead
#[no_mangle]
pub extern "C" fn wamr_memory_size(memory: *mut c_void) -> c_long {
    ffi::wamr_memory_size(memory)
}

/// Legacy function - Get memory data pointer
/// Use wamr_memory_data instead
#[no_mangle]
pub extern "C" fn wamr_memory_data(memory: *mut c_void) -> *mut c_void {
    ffi::wamr_memory_data(memory)
}

/// Legacy function - Grow memory by specified pages
/// Use wamr_memory_grow instead
#[no_mangle]
pub extern "C" fn wamr_memory_grow(memory: *mut c_void, pages: c_long) -> c_int {
    ffi::wamr_memory_grow(memory, pages)
}

/// Legacy function - Get WAMR version
/// Use wamr_get_version instead
#[no_mangle]
pub extern "C" fn wamr_get_version() -> *const c_char {
    ffi::wamr_get_version()
}

// =============================================================================
// Library Information
// =============================================================================

/// Get library name
#[no_mangle]
pub extern "C" fn wamr4j_get_library_name() -> *const c_char {
    b"wamr4j-native\0".as_ptr() as *const c_char
}

/// Get library version
#[no_mangle]
pub extern "C" fn wamr4j_get_library_version() -> *const c_char {
    b"1.0.0\0".as_ptr() as *const c_char
}

/// Get supported WAMR version
#[no_mangle]
pub extern "C" fn wamr4j_get_wamr_version() -> *const c_char {
    ffi::wamr_get_version()
}

/// Check if library is compiled with debug information
#[no_mangle]
pub extern "C" fn wamr4j_is_debug_build() -> c_int {
    if cfg!(debug_assertions) { 1 } else { 0 }
}

/// Get platform information
#[no_mangle]
pub extern "C" fn wamr4j_get_platform_info(
    platform_buf: *mut c_char,
    platform_buf_size: c_int,
    arch_buf: *mut c_char,
    arch_buf_size: c_int,
) -> c_int {
    use utils::{copy_string_to_buffer, get_platform_name, get_architecture_name};
    
    let platform = get_platform_name();
    let arch = get_architecture_name();
    
    let platform_result = copy_string_to_buffer(platform, platform_buf, platform_buf_size);
    let arch_result = copy_string_to_buffer(arch, arch_buf, arch_buf_size);
    
    if platform_result > 0 && arch_result > 0 { 0 } else { -1 }
}

// =============================================================================
// Test and Debug Functions
// =============================================================================

/// Test function to verify library loading
#[no_mangle]
pub extern "C" fn wamr4j_test_function() -> c_int {
    42 // Return a known value to confirm library is loaded
}

/// Initialize logging (placeholder for future logging integration)
#[no_mangle]
pub extern "C" fn wamr4j_init_logging(level: c_int) -> c_int {
    // TODO: Initialize logging based on level
    // 0 = ERROR, 1 = WARN, 2 = INFO, 3 = DEBUG, 4 = TRACE
    0 // Success
}

// =============================================================================
// Module Documentation
// =============================================================================

//! # Example Usage (C/JNI)
//!
//! ```c
//! // Initialize runtime
//! void* runtime = wamr_runtime_init();
//! if (!runtime) {
//!     // Handle error
//!     return -1;
//! }
//!
//! // Compile module
//! char error_buf[256];
//! void* module = wamr_module_compile(runtime, wasm_bytes, wasm_size, 
//!                                    error_buf, sizeof(error_buf));
//! if (!module) {
//!     printf("Compilation failed: %s\n", error_buf);
//!     wamr_runtime_destroy(runtime);
//!     return -1;
//! }
//!
//! // Create instance
//! void* instance = wamr_instance_create(module, 16384, 16777216, 
//!                                       error_buf, sizeof(error_buf));
//! if (!instance) {
//!     printf("Instantiation failed: %s\n", error_buf);
//!     wamr_module_destroy(module);
//!     wamr_runtime_destroy(runtime);
//!     return -1;
//! }
//!
//! // Look up function
//! void* function = wamr_function_lookup(instance, "exported_function",
//!                                       error_buf, sizeof(error_buf));
//! if (!function) {
//!     printf("Function lookup failed: %s\n", error_buf);
//! } else {
//!     // Call function
//!     WasmValueFFI args[] = {{WASM_TYPE_I32, {42, 0, 0, 0, 0, 0, 0, 0}}};
//!     WasmValueFFI results[1];
//!     int result_count = wamr_function_call(function, args, 1, results, 1,
//!                                           error_buf, sizeof(error_buf));
//!     if (result_count >= 0) {
//!         printf("Function returned: %d\n", *(int*)results[0].data);
//!     }
//! }
//!
//! // Cleanup
//! wamr_instance_destroy(instance);
//! wamr_module_destroy(module);
//! wamr_runtime_destroy(runtime);
//! ```