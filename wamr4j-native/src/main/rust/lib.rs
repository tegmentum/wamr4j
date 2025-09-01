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

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int, c_long, c_uchar, c_void};
use std::ptr;

// Re-export modules
pub mod jni_bindings;
pub mod panama_bindings;
pub mod wamr_wrapper;

// Use the WAMR wrapper for common functionality
use wamr_wrapper::*;

/// Initialize the WAMR runtime
#[no_mangle]
pub extern "C" fn wamr_create_runtime() -> *mut c_void {
    match create_runtime() {
        Ok(runtime) => Box::into_raw(Box::new(runtime)) as *mut c_void,
        Err(_) => ptr::null_mut(),
    }
}

/// Destroy the WAMR runtime
#[no_mangle]
pub extern "C" fn wamr_destroy_runtime(runtime: *mut c_void) {
    if !runtime.is_null() {
        unsafe {
            let _runtime = Box::from_raw(runtime as *mut WamrRuntime);
            // Box drop will clean up the runtime
        }
    }
}

/// Compile a WebAssembly module
#[no_mangle]
pub extern "C" fn wamr_compile_module(
    runtime: *mut c_void,
    wasm_bytes: *const c_uchar,
    length: c_long,
) -> *mut c_void {
    if runtime.is_null() || wasm_bytes.is_null() || length <= 0 {
        return ptr::null_mut();
    }

    unsafe {
        let runtime_ref = &*(runtime as *const WamrRuntime);
        let bytes = std::slice::from_raw_parts(wasm_bytes, length as usize);
        
        match compile_module(runtime_ref, bytes) {
            Ok(module) => Box::into_raw(Box::new(module)) as *mut c_void,
            Err(_) => ptr::null_mut(),
        }
    }
}

/// Destroy a WebAssembly module
#[no_mangle]
pub extern "C" fn wamr_destroy_module(module: *mut c_void) {
    if !module.is_null() {
        unsafe {
            let _module = Box::from_raw(module as *mut WamrModule);
            // Box drop will clean up the module
        }
    }
}

/// Instantiate a WebAssembly module
#[no_mangle]
pub extern "C" fn wamr_instantiate_module(module: *mut c_void) -> *mut c_void {
    if module.is_null() {
        return ptr::null_mut();
    }

    unsafe {
        let module_ref = &*(module as *const WamrModule);
        match instantiate_module(module_ref) {
            Ok(instance) => Box::into_raw(Box::new(instance)) as *mut c_void,
            Err(_) => ptr::null_mut(),
        }
    }
}

/// Destroy a WebAssembly instance
#[no_mangle]
pub extern "C" fn wamr_destroy_instance(instance: *mut c_void) {
    if !instance.is_null() {
        unsafe {
            let _instance = Box::from_raw(instance as *mut WamrInstance);
            // Box drop will clean up the instance
        }
    }
}

/// Get a function from an instance
#[no_mangle]
pub extern "C" fn wamr_get_function(
    instance: *mut c_void,
    name: *const c_char,
) -> *mut c_void {
    if instance.is_null() || name.is_null() {
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let function_name = match CStr::from_ptr(name).to_str() {
            Ok(s) => s,
            Err(_) => return ptr::null_mut(),
        };
        
        match get_function(instance_ref, function_name) {
            Ok(function) => Box::into_raw(Box::new(function)) as *mut c_void,
            Err(_) => ptr::null_mut(),
        }
    }
}

/// Call a WebAssembly function
#[no_mangle]
pub extern "C" fn wamr_call_function(
    function: *mut c_void,
    args: *const c_void,
    arg_count: c_int,
    results: *mut c_void,
    result_capacity: c_int,
) -> c_int {
    if function.is_null() {
        return -1;
    }

    unsafe {
        let function_ref = &*(function as *const WamrFunction);
        // Placeholder implementation - actual argument conversion needed
        match call_function(function_ref, &[]) {
            Ok(_) => 0,
            Err(_) => -1,
        }
    }
}

/// Get memory from an instance
#[no_mangle]
pub extern "C" fn wamr_get_memory(instance: *mut c_void) -> *mut c_void {
    if instance.is_null() {
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        match get_memory(instance_ref) {
            Ok(memory) => Box::into_raw(Box::new(memory)) as *mut c_void,
            Err(_) => ptr::null_mut(),
        }
    }
}

/// Get memory size
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

/// Get WAMR version
#[no_mangle]
pub extern "C" fn wamr_get_version() -> *const c_char {
    static VERSION: &str = "2.4.1\0";
    VERSION.as_ptr() as *const c_char
}