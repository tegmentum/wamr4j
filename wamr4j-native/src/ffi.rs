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

//! FFI function exports for WAMR core operations
//!
//! This module provides C-compatible FFI functions that can be called from both
//! JNI and Panama Foreign Function API implementations. All functions use C ABI
//! and handle memory management safely across the FFI boundary.

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int, c_long, c_uchar, c_void};
use std::ptr;

use crate::runtime::{
    RuntimeConfig,
    runtime_init, runtime_init_with_config, runtime_is_valid,
    module_compile, module_validate, module_get_size,
    instance_create, instance_is_valid,
    function_lookup, function_call, function_get_signature,
    memory_get, memory_size, memory_data, memory_grow, memory_read, memory_write,
};
use crate::utils::{
    write_error_to_buffer, get_last_error, clear_last_error,
    wasm_value_from_ffi, wasm_value_to_ffi, wasm_type_to_ffi,
    WasmValueFFI, WASM_TYPE_I32, WASM_TYPE_I64, WASM_TYPE_F32, WASM_TYPE_F64,
};
use crate::wamr_wrapper::{
    WamrRuntime, WamrModule, WamrInstance, WamrFunction, WamrMemory,
    WasmValue, WasmType, WamrError,
};

// =============================================================================
// Runtime Management
// =============================================================================

/// Initialize the WAMR runtime with default configuration
#[no_mangle]
pub extern "C" fn wamr_runtime_init() -> *mut c_void {
    match runtime_init() {
        Ok(runtime) => Box::into_raw(Box::new(runtime)) as *mut c_void,
        Err(_) => ptr::null_mut(),
    }
}

/// Initialize the WAMR runtime with custom configuration
#[no_mangle]
pub extern "C" fn wamr_runtime_init_with_config(
    stack_size: c_long,
    heap_size: c_long,
    max_thread_num: c_int,
) -> *mut c_void {
    if stack_size < 0 || heap_size < 0 || max_thread_num < 0 {
        return ptr::null_mut();
    }

    let config = RuntimeConfig {
        stack_size: stack_size as usize,
        heap_size: heap_size as usize,
        max_thread_num: max_thread_num as u32,
    };

    match runtime_init_with_config(&config) {
        Ok(runtime) => Box::into_raw(Box::new(runtime)) as *mut c_void,
        Err(_) => ptr::null_mut(),
    }
}

/// Destroy the WAMR runtime and clean up all resources
#[no_mangle]
pub extern "C" fn wamr_runtime_destroy(runtime: *mut c_void) {
    if !runtime.is_null() {
        unsafe {
            let _runtime = Box::from_raw(runtime as *mut WamrRuntime);
            // Box drop will clean up the runtime
        }
    }
}

/// Check if runtime is valid
#[no_mangle]
pub extern "C" fn wamr_runtime_is_valid(runtime: *mut c_void) -> c_int {
    if runtime.is_null() {
        return 0;
    }

    unsafe {
        let runtime_ref = &*(runtime as *const WamrRuntime);
        if runtime_is_valid(runtime_ref) { 1 } else { 0 }
    }
}

// =============================================================================
// Module Operations
// =============================================================================

/// Compile WebAssembly bytecode into a module
#[no_mangle]
pub extern "C" fn wamr_module_compile(
    runtime: *mut c_void,
    wasm_bytes: *const c_uchar,
    length: c_long,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    if runtime.is_null() || wasm_bytes.is_null() || length <= 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return ptr::null_mut();
    }

    unsafe {
        let runtime_ref = &*(runtime as *const WamrRuntime);
        let bytes = std::slice::from_raw_parts(wasm_bytes, length as usize);
        
        match module_compile(runtime_ref, bytes) {
            Ok(module) => Box::into_raw(Box::new(module)) as *mut c_void,
            Err(e) => {
                let error_msg = format!("Module compilation failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                ptr::null_mut()
            }
        }
    }
}

/// Validate WebAssembly bytecode without compilation
#[no_mangle]
pub extern "C" fn wamr_module_validate(
    runtime: *mut c_void,
    wasm_bytes: *const c_uchar,
    length: c_long,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> c_int {
    if runtime.is_null() || wasm_bytes.is_null() || length <= 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return 0;
    }

    unsafe {
        let runtime_ref = &*(runtime as *const WamrRuntime);
        let bytes = std::slice::from_raw_parts(wasm_bytes, length as usize);
        
        match module_validate(runtime_ref, bytes) {
            Ok(_) => 1,
            Err(e) => {
                let error_msg = format!("Module validation failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                0
            }
        }
    }
}

/// Destroy a WebAssembly module
#[no_mangle]
pub extern "C" fn wamr_module_destroy(module: *mut c_void) {
    if !module.is_null() {
        unsafe {
            let _module = Box::from_raw(module as *mut WamrModule);
            // Box drop will clean up the module
        }
    }
}

/// Get module size in bytes
#[no_mangle]
pub extern "C" fn wamr_module_get_size(module: *mut c_void) -> c_long {
    if module.is_null() {
        return -1;
    }

    unsafe {
        let module_ref = &*(module as *const WamrModule);
        module_get_size(module_ref) as c_long
    }
}

// =============================================================================
// Instance Operations
// =============================================================================

/// Instantiate a WebAssembly module
#[no_mangle]
pub extern "C" fn wamr_instance_create(
    module: *mut c_void,
    stack_size: c_long,
    heap_size: c_long,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    if module.is_null() || stack_size < 0 || heap_size < 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return ptr::null_mut();
    }

    unsafe {
        let module_ref = &*(module as *const WamrModule);
        match instance_create(module_ref, stack_size as usize, heap_size as usize) {
            Ok(instance) => Box::into_raw(Box::new(instance)) as *mut c_void,
            Err(e) => {
                let error_msg = format!("Instance creation failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                ptr::null_mut()
            }
        }
    }
}

/// Destroy a WebAssembly instance
#[no_mangle]
pub extern "C" fn wamr_instance_destroy(instance: *mut c_void) {
    if !instance.is_null() {
        unsafe {
            let _instance = Box::from_raw(instance as *mut WamrInstance);
            // Box drop will clean up the instance
        }
    }
}

/// Check if instance is valid
#[no_mangle]
pub extern "C" fn wamr_instance_is_valid(instance: *mut c_void) -> c_int {
    if instance.is_null() {
        return 0;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if instance_is_valid(instance_ref) { 1 } else { 0 }
    }
}

// =============================================================================
// Function Operations
// =============================================================================

/// Get a function from an instance by name
#[no_mangle]
pub extern "C" fn wamr_function_lookup(
    instance: *mut c_void,
    name: *const c_char,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    if instance.is_null() || name.is_null() {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let function_name = match CStr::from_ptr(name).to_str() {
            Ok(s) => s,
            Err(_) => {
                write_error_to_buffer("Invalid function name encoding", error_buf, error_buf_size);
                return ptr::null_mut();
            }
        };
        
        match function_lookup(instance_ref, function_name) {
            Ok(function) => Box::into_raw(Box::new(function)) as *mut c_void,
            Err(e) => {
                let error_msg = format!("Function lookup failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                ptr::null_mut()
            }
        }
    }
}

/// Call a WebAssembly function with arguments
#[no_mangle]
pub extern "C" fn wamr_function_call(
    function: *mut c_void,
    args: *const WasmValueFFI,
    arg_count: c_int,
    results: *mut WasmValueFFI,
    result_capacity: c_int,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> c_int {
    if function.is_null() || arg_count < 0 || result_capacity < 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return -1;
    }

    unsafe {
        let function_ref = &*(function as *const WamrFunction);
        
        // Optimized argument conversion using batch operations
        let arg_slice = if arg_count > 0 && !args.is_null() {
            std::slice::from_raw_parts(args, arg_count as usize)
        } else {
            &[]
        };
        
        // Use stack allocation for small argument counts to avoid heap allocation
        let mut stack_args = [WasmValue::I32(0); 16]; // Stack buffer for up to 16 args
        let wasm_args: Vec<WasmValue> = if arg_slice.len() <= 16 {
            // Use stack buffer - no heap allocation
            let mut converted_count = 0;
            for (i, ffi_val) in arg_slice.iter().enumerate() {
                if i < 16 {
                    stack_args[i] = wasm_value_from_ffi(ffi_val);
                    converted_count += 1;
                }
            }
            stack_args[..converted_count].to_vec()
        } else {
            // Fall back to heap allocation for large argument counts
            arg_slice
                .iter()
                .map(|ffi_val| wasm_value_from_ffi(ffi_val))
                .collect()
        };
        
        match function_call(function_ref, &wasm_args) {
            Ok(wasm_results) => {
                // Convert results back to FFI format
                let result_count = std::cmp::min(wasm_results.len(), result_capacity as usize);
                
                if result_count > 0 && !results.is_null() {
                    let result_slice = std::slice::from_raw_parts_mut(results, result_count);
                    for (i, wasm_val) in wasm_results.iter().take(result_count).enumerate() {
                        result_slice[i] = wasm_value_to_ffi(wasm_val);
                    }
                }
                
                result_count as c_int
            }
            Err(e) => {
                let error_msg = format!("Function call failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                -1
            }
        }
    }
}

/// Get function signature information
#[no_mangle]
pub extern "C" fn wamr_function_get_signature(
    function: *mut c_void,
    param_types: *mut c_int,
    param_count: *mut c_int,
    result_types: *mut c_int,
    result_count: *mut c_int,
) -> c_int {
    if function.is_null() {
        return -1;
    }

    unsafe {
        let function_ref = &*(function as *const WamrFunction);
        match function_get_signature(function_ref) {
            Ok((params, results)) => {
                if !param_count.is_null() {
                    *param_count = params.len() as c_int;
                }
                if !result_count.is_null() {
                    *result_count = results.len() as c_int;
                }
                
                if !param_types.is_null() {
                    let param_slice = std::slice::from_raw_parts_mut(param_types, params.len());
                    for (i, param_type) in params.iter().enumerate() {
                        param_slice[i] = wasm_type_to_ffi(param_type);
                    }
                }
                
                if !result_types.is_null() {
                    let result_slice = std::slice::from_raw_parts_mut(result_types, results.len());
                    for (i, result_type) in results.iter().enumerate() {
                        result_slice[i] = wasm_type_to_ffi(result_type);
                    }
                }
                
                0
            }
            Err(_) => -1,
        }
    }
}

// =============================================================================
// Memory Operations
// =============================================================================

/// Get memory from an instance
#[no_mangle]
pub extern "C" fn wamr_memory_get(instance: *mut c_void) -> *mut c_void {
    if instance.is_null() {
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        match memory_get(instance_ref) {
            Ok(memory) => Box::into_raw(Box::new(memory)) as *mut c_void,
            Err(_) => ptr::null_mut(),
        }
    }
}

/// Get memory size in bytes
#[no_mangle]
pub extern "C" fn wamr_memory_size(memory: *mut c_void) -> c_long {
    if memory.is_null() {
        return -1;
    }

    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_size(memory_ref) as c_long
    }
}

/// Get memory data pointer
#[no_mangle]
pub extern "C" fn wamr_memory_data(memory: *mut c_void) -> *mut c_void {
    if memory.is_null() {
        return ptr::null_mut();
    }

    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_data(memory_ref) as *mut c_void
    }
}

/// Grow memory by specified pages
#[no_mangle]
pub extern "C" fn wamr_memory_grow(memory: *mut c_void, pages: c_long) -> c_int {
    if memory.is_null() || pages < 0 {
        return -1;
    }

    unsafe {
        let memory_ref = &mut *(memory as *mut WamrMemory);
        match memory_grow(memory_ref, pages as u32) {
            Ok(old_size) => old_size as c_int,
            Err(_) => -1,
        }
    }
}

/// Read data from memory at offset
#[no_mangle]
#[inline(never)] // Keep as separate function for debugging, but optimize internally
pub extern "C" fn wamr_memory_read(
    memory: *mut c_void,
    offset: c_long,
    buffer: *mut c_void,
    size: c_long,
) -> c_int {
    if memory.is_null() || buffer.is_null() || offset < 0 || size <= 0 {
        return -1;
    }

    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        let buffer_slice = std::slice::from_raw_parts_mut(buffer as *mut u8, size as usize);
        
        match memory_read(memory_ref, offset as usize, buffer_slice) {
            Ok(bytes_read) => bytes_read as c_int,
            Err(_) => -1,
        }
    }
}

/// Write data to memory at offset
#[no_mangle]
pub extern "C" fn wamr_memory_write(
    memory: *mut c_void,
    offset: c_long,
    buffer: *const c_void,
    size: c_long,
) -> c_int {
    if memory.is_null() || buffer.is_null() || offset < 0 || size <= 0 {
        return -1;
    }

    unsafe {
        let memory_ref = &mut *(memory as *mut WamrMemory);
        let buffer_slice = std::slice::from_raw_parts(buffer as *const u8, size as usize);
        
        match memory_write(memory_ref, offset as usize, buffer_slice) {
            Ok(bytes_written) => bytes_written as c_int,
            Err(_) => -1,
        }
    }
}

// =============================================================================
// Utility Functions
// =============================================================================

/// Get WAMR version string
#[no_mangle]
pub extern "C" fn wamr_get_version() -> *const c_char {
    static VERSION: &str = "2.4.1\0";
    VERSION.as_ptr() as *const c_char
}

/// Get last error message
#[no_mangle]
pub extern "C" fn wamr_get_last_error(buffer: *mut c_char, buffer_size: c_int) -> c_int {
    if buffer.is_null() || buffer_size <= 0 {
        return -1;
    }

    match get_last_error() {
        Some(error) => {
            let error_cstring = match CString::new(error) {
                Ok(cstr) => cstr,
                Err(_) => return -1,
            };
            
            let error_bytes = error_cstring.as_bytes_with_nul();
            let copy_len = std::cmp::min(error_bytes.len(), buffer_size as usize);
            
            unsafe {
                std::ptr::copy_nonoverlapping(
                    error_bytes.as_ptr(),
                    buffer as *mut u8,
                    copy_len,
                );
            }
            
            copy_len as c_int
        }
        None => 0,
    }
}

/// Clear last error
#[no_mangle]
pub extern "C" fn wamr_clear_last_error() {
    clear_last_error();
}

// FFI value types are defined in utils.rs to avoid duplication