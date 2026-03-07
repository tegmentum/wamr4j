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
    runtime_init, runtime_get_version,
    module_compile,
    instance_create,
    function_lookup, function_call,
    memory_get, memory_grow,
    memory_page_count, memory_max_page_count, memory_is_shared,
    memory_get_by_index, memory_base_address, memory_bytes_per_page, memory_enlarge,
    module_malloc, module_free, module_dup_data,
    validate_app_addr, validate_app_str_addr, validate_native_addr,
    addr_app_to_native, addr_native_to_app,
    get_export_names_by_kind, global_get, global_set,
    module_get_export_names, module_get_import_names,
    module_get_export_function_signature,
    is_running_mode_supported, set_default_running_mode,
    set_running_mode, get_running_mode,
    set_log_level, set_bounds_checks, is_bounds_checks_enabled,
    module_set_name, module_get_name, module_register,
    module_get_hash,
    get_file_package_type, get_module_package_type,
    get_file_package_version, get_module_package_version,
    get_current_package_version, is_xip_file,
    is_underlying_binary_freeable,
    table_get, table_get_function, call_indirect,
    register_host_functions,
    instance_get_exception, instance_set_exception, instance_clear_exception,
    instance_terminate, set_instruction_count_limit,
    module_set_wasi_args, module_set_wasi_addr_pool, module_set_wasi_ns_lookup_pool,
    instance_is_wasi_mode, instance_get_wasi_exit_code,
    instance_lookup_wasi_start_function,
    instance_execute_main, instance_execute_func,
    instance_set_custom_data, instance_get_custom_data,
    instance_get_call_stack, instance_dump_call_stack,
    instance_dump_perf_profiling, instance_sum_wasm_exec_time,
    instance_get_wasm_func_exec_time, instance_dump_mem_consumption,
    init_thread_env, destroy_thread_env, is_thread_env_inited, set_max_thread_num,
    instance_create_ex, module_get_custom_section,
    get_export_global_type_info, get_export_memory_type_info,
    is_import_func_linked, is_import_global_linked,
    instance_lookup_memory,
    instance_begin_blocking_op, instance_end_blocking_op,
    instance_detect_native_stack_overflow, instance_detect_native_stack_overflow_size,
    get_mem_alloc_info,
    externref_obj2ref, externref_objdel, externref_ref2obj, externref_retain,
    create_context_key, destroy_context_key, set_context, set_context_spread, get_context,
    spawn_exec_env, destroy_spawned_exec_env,
    instance_create_ex2,
    copy_callstack,
    shared_heap_create, shared_heap_attach, shared_heap_detach,
    shared_heap_chain, shared_heap_malloc, shared_heap_free,
};
use crate::utils::{
    write_error_to_buffer, get_last_error, set_last_error, clear_last_error,
    wasm_value_from_ffi, wasm_value_to_ffi, wasm_type_to_ffi,
    WasmValueFFI,
    WASM_TYPE_I32, WASM_TYPE_I64, WASM_TYPE_F32, WASM_TYPE_F64,
};
use crate::types::{
    WamrRuntime, WamrModule, WamrInstance, WamrFunction, WamrMemory, WamrTable,
    WasmValue, WasmType, HostCallbackFn, NativeRegistration,
};
use crate::bindings::{
    WASM_IMPORT_EXPORT_KIND_FUNC,
    WASM_IMPORT_EXPORT_KIND_TABLE,
    WASM_IMPORT_EXPORT_KIND_GLOBAL,
};

// =============================================================================
// Runtime Management
// =============================================================================

/// Initialize the WAMR runtime with default configuration
#[no_mangle]
pub extern "C" fn wamr_runtime_init() -> *mut c_void {
    clear_last_error();
    match runtime_init() {
        Ok(runtime) => Box::into_raw(Box::new(runtime)) as *mut c_void,
        Err(_) => ptr::null_mut(),
    }
}

/// Destroy the WAMR runtime and clean up all resources
#[no_mangle]
pub extern "C" fn wamr_runtime_destroy(runtime: *mut c_void) {
    clear_last_error();
    if !runtime.is_null() {
        unsafe {
            let _runtime = Box::from_raw(runtime as *mut WamrRuntime);
            // Box drop will clean up the runtime
        }
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
    clear_last_error();
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

/// Destroy a WebAssembly module
#[no_mangle]
pub extern "C" fn wamr_module_destroy(module: *mut c_void) {
    clear_last_error();
    if !module.is_null() {
        unsafe {
            let _module = Box::from_raw(module as *mut WamrModule);
            // Box drop will clean up the module
        }
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
    clear_last_error();
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
    clear_last_error();
    if !instance.is_null() {
        unsafe {
            let _instance = Box::from_raw(instance as *mut WamrInstance);
            // Box drop will clean up the instance
        }
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
    clear_last_error();
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
    clear_last_error();
    if function.is_null() || arg_count < 0 || result_capacity < 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return -1;
    }

    unsafe {
        let function_ref = &*(function as *const WamrFunction);

        let arg_slice = if arg_count > 0 && !args.is_null() {
            std::slice::from_raw_parts(args, arg_count as usize)
        } else {
            &[]
        };

        let wasm_args: Vec<WasmValue> = arg_slice.iter().map(|v| wasm_value_from_ffi(v)).collect();

        match function_call(function_ref, function_ref.exec_env, &wasm_args) {
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
                let error_msg = format!("{}", e);
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
    clear_last_error();
    if function.is_null() {
        return -1;
    }

    unsafe {
        let function_ref = &*(function as *const WamrFunction);

        if !param_count.is_null() {
            *param_count = function_ref.param_types.len() as c_int;
        }
        if !result_count.is_null() {
            *result_count = function_ref.result_types.len() as c_int;
        }

        if !param_types.is_null() {
            let param_slice = std::slice::from_raw_parts_mut(
                param_types, function_ref.param_types.len());
            for (i, param_type) in function_ref.param_types.iter().enumerate() {
                param_slice[i] = wasm_type_to_ffi(param_type);
            }
        }

        if !result_types.is_null() {
            let result_slice = std::slice::from_raw_parts_mut(
                result_types, function_ref.result_types.len());
            for (i, result_type) in function_ref.result_types.iter().enumerate() {
                result_slice[i] = wasm_type_to_ffi(result_type);
            }
        }

        0
    }
}

/// Destroy a function handle allocated by wamr_function_lookup
#[no_mangle]
pub extern "C" fn wamr_function_destroy(function: *mut c_void) {
    clear_last_error();
    if !function.is_null() {
        unsafe {
            let _function = Box::from_raw(function as *mut WamrFunction);
        }
    }
}

// =============================================================================
// Memory Operations
// =============================================================================

/// Get memory from an instance
#[no_mangle]
pub extern "C" fn wamr_memory_get(instance: *mut c_void) -> *mut c_void {
    clear_last_error();
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
    clear_last_error();
    if memory.is_null() {
        return -1;
    }

    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_ref.size as c_long
    }
}

/// Get memory data pointer
#[no_mangle]
pub extern "C" fn wamr_memory_data(memory: *mut c_void) -> *mut c_void {
    clear_last_error();
    if memory.is_null() {
        return ptr::null_mut();
    }

    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_ref.data_ptr as *mut c_void
    }
}

/// Grow memory by specified pages
#[no_mangle]
pub extern "C" fn wamr_memory_grow(memory: *mut c_void, pages: c_long) -> c_int {
    clear_last_error();
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

/// Get current page count
#[no_mangle]
pub extern "C" fn wamr_memory_page_count(memory: *mut c_void) -> c_long {
    clear_last_error();
    if memory.is_null() {
        return -1;
    }
    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_page_count(memory_ref) as c_long
    }
}

/// Get maximum page count
#[no_mangle]
pub extern "C" fn wamr_memory_max_page_count(memory: *mut c_void) -> c_long {
    clear_last_error();
    if memory.is_null() {
        return -1;
    }
    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_max_page_count(memory_ref) as c_long
    }
}

/// Check if memory is shared
#[no_mangle]
pub extern "C" fn wamr_memory_is_shared(memory: *mut c_void) -> c_int {
    clear_last_error();
    if memory.is_null() {
        return 0;
    }
    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        if memory_is_shared(memory_ref) { 1 } else { 0 }
    }
}

/// Destroy a memory handle allocated by wamr_memory_get
#[no_mangle]
pub extern "C" fn wamr_memory_destroy(memory: *mut c_void) {
    clear_last_error();
    if !memory.is_null() {
        unsafe {
            let _memory = Box::from_raw(memory as *mut WamrMemory);
        }
    }
}

/// Get memory base address (native pointer)
#[no_mangle]
pub extern "C" fn wamr_memory_base_address(memory: *mut c_void) -> *mut c_void {
    clear_last_error();
    if memory.is_null() {
        return ptr::null_mut();
    }
    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_base_address(memory_ref)
    }
}

/// Get bytes per page for a memory instance
#[no_mangle]
pub extern "C" fn wamr_memory_bytes_per_page(memory: *mut c_void) -> c_long {
    clear_last_error();
    if memory.is_null() {
        return -1;
    }
    unsafe {
        let memory_ref = &*(memory as *const WamrMemory);
        memory_bytes_per_page(memory_ref) as c_long
    }
}

/// Enlarge a specific memory instance by pages
#[no_mangle]
pub extern "C" fn wamr_memory_enlarge_inst(memory: *mut c_void, inc_pages: c_long) -> c_int {
    clear_last_error();
    if memory.is_null() || inc_pages < 0 {
        return -1;
    }
    unsafe {
        let memory_ref = &mut *(memory as *mut WamrMemory);
        if memory_enlarge(memory_ref, inc_pages as u64) { 0 } else { -1 }
    }
}

/// Allocate memory within the module instance heap
/// Returns the app offset, or 0 on failure. native_addr_out receives the native pointer.
#[no_mangle]
pub extern "C" fn wamr_module_malloc(
    instance: *mut c_void,
    size: c_long,
    native_addr_out: *mut *mut c_void,
) -> c_long {
    clear_last_error();
    if instance.is_null() || size <= 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        match module_malloc(instance_ref, size as u64) {
            Ok((app_offset, native_ptr)) => {
                if !native_addr_out.is_null() {
                    *native_addr_out = native_ptr;
                }
                app_offset as c_long
            }
            Err(_) => 0,
        }
    }
}

/// Free memory previously allocated by wamr_module_malloc
#[no_mangle]
pub extern "C" fn wamr_module_free(instance: *mut c_void, ptr: c_long) {
    clear_last_error();
    if instance.is_null() || ptr <= 0 {
        return;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        module_free(instance_ref, ptr as u64);
    }
}

/// Duplicate data into the module instance memory
/// Returns the app offset, or 0 on failure.
#[no_mangle]
pub extern "C" fn wamr_module_dup_data(
    instance: *mut c_void,
    data: *const c_uchar,
    size: c_long,
) -> c_long {
    clear_last_error();
    if instance.is_null() || data.is_null() || size <= 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let slice = std::slice::from_raw_parts(data, size as usize);
        match module_dup_data(instance_ref, slice) {
            Ok(offset) => offset as c_long,
            Err(_) => 0,
        }
    }
}

/// Validate an application address range
#[no_mangle]
pub extern "C" fn wamr_validate_app_addr(
    instance: *mut c_void,
    app_offset: c_long,
    size: c_long,
) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if validate_app_addr(instance_ref, app_offset as u64, size as u64) { 1 } else { 0 }
    }
}

/// Validate an application string address
#[no_mangle]
pub extern "C" fn wamr_validate_app_str_addr(
    instance: *mut c_void,
    app_str_offset: c_long,
) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if validate_app_str_addr(instance_ref, app_str_offset as u64) { 1 } else { 0 }
    }
}

/// Validate a native address range
#[no_mangle]
pub extern "C" fn wamr_validate_native_addr(
    instance: *mut c_void,
    native_ptr: *mut c_void,
    size: c_long,
) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if validate_native_addr(instance_ref, native_ptr, size as u64) { 1 } else { 0 }
    }
}

/// Convert application offset to native pointer
#[no_mangle]
pub extern "C" fn wamr_addr_app_to_native(
    instance: *mut c_void,
    app_offset: c_long,
) -> *mut c_void {
    clear_last_error();
    if instance.is_null() {
        return ptr::null_mut();
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        addr_app_to_native(instance_ref, app_offset as u64)
    }
}

/// Convert native pointer to application offset
#[no_mangle]
pub extern "C" fn wamr_addr_native_to_app(
    instance: *mut c_void,
    native_ptr: *mut c_void,
) -> c_long {
    clear_last_error();
    if instance.is_null() {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        addr_native_to_app(instance_ref, native_ptr) as c_long
    }
}

/// Get memory instance by index
#[no_mangle]
pub extern "C" fn wamr_memory_get_by_index(
    instance: *mut c_void,
    index: c_int,
) -> *mut c_void {
    clear_last_error();
    if instance.is_null() || index < 0 {
        return ptr::null_mut();
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        match memory_get_by_index(instance_ref, index as u32) {
            Ok(memory) => Box::into_raw(Box::new(memory)) as *mut c_void,
            Err(_) => ptr::null_mut(),
        }
    }
}

/// Check if an instance has a default memory (without allocating a Box)
#[no_mangle]
pub extern "C" fn wamr_instance_has_memory(instance: *mut c_void) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return 0;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let memory_handle = crate::bindings::wasm_runtime_get_default_memory(instance_ref.handle);
        if memory_handle.is_null() { 0 } else { 1 }
    }
}

// =============================================================================
// Runtime Configuration
// =============================================================================

/// Check if a running mode is supported
#[no_mangle]
pub extern "C" fn wamr_is_running_mode_supported(mode: c_int) -> c_int {
    clear_last_error();
    if is_running_mode_supported(mode as u32) { 1 } else { 0 }
}

/// Set the default running mode
#[no_mangle]
pub extern "C" fn wamr_set_default_running_mode(mode: c_int) -> c_int {
    clear_last_error();
    if set_default_running_mode(mode as u32) { 0 } else { -1 }
}

/// Set the running mode for an instance
#[no_mangle]
pub extern "C" fn wamr_set_running_mode(instance: *mut c_void, mode: c_int) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return -1;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if set_running_mode(instance_ref, mode as u32) { 0 } else { -1 }
    }
}

/// Get the running mode for an instance
#[no_mangle]
pub extern "C" fn wamr_get_running_mode(instance: *mut c_void) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return -1;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        get_running_mode(instance_ref) as c_int
    }
}

/// Set log verbosity level
#[no_mangle]
pub extern "C" fn wamr_set_log_level(level: c_int) {
    clear_last_error();
    set_log_level(level as i32);
}

/// Enable or disable bounds checks for an instance
#[no_mangle]
pub extern "C" fn wamr_set_bounds_checks(instance: *mut c_void, enable: c_int) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return -1;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if set_bounds_checks(instance_ref, enable != 0) { 0 } else { -1 }
    }
}

/// Check if bounds checks are enabled for an instance
#[no_mangle]
pub extern "C" fn wamr_is_bounds_checks_enabled(instance: *mut c_void) -> c_int {
    clear_last_error();
    if instance.is_null() {
        return -1;
    }
    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        if is_bounds_checks_enabled(instance_ref) { 1 } else { 0 }
    }
}

// =============================================================================
// Module Management
// =============================================================================

/// Set the name of a module
#[no_mangle]
pub extern "C" fn wamr_module_set_name(
    module: *mut c_void,
    name: *const c_char,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || name.is_null() {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return -1;
    }
    let name_str = match unsafe { CStr::from_ptr(name).to_str() } {
        Ok(s) => s,
        Err(_) => {
            write_error_to_buffer("Invalid name encoding", error_buf, error_buf_size);
            return -1;
        }
    };
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        if module_set_name(module_ref, name_str) { 0 } else { -1 }
    }
}

/// Get the name of a module (returns pointer to static string, valid until module is destroyed)
#[no_mangle]
pub extern "C" fn wamr_module_get_name(
    module: *mut c_void,
    name_buf: *mut c_char,
    name_buf_size: c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || name_buf.is_null() || name_buf_size <= 0 {
        return -1;
    }
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        match module_get_name(module_ref) {
            Some(name) => {
                write_error_to_buffer(&name, name_buf, name_buf_size);
                0
            }
            None => -1,
        }
    }
}

/// Register a module by name for multi-module support
#[no_mangle]
pub extern "C" fn wamr_module_register(
    module: *mut c_void,
    name: *const c_char,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || name.is_null() {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return -1;
    }
    let name_str = match unsafe { CStr::from_ptr(name).to_str() } {
        Ok(s) => s,
        Err(_) => {
            write_error_to_buffer("Invalid name encoding", error_buf, error_buf_size);
            return -1;
        }
    };
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        match module_register(module_ref, name_str) {
            Ok(()) => 0,
            Err(_) => {
                write_error_to_buffer("Module registration failed", error_buf, error_buf_size);
                -1
            }
        }
    }
}

/// Get the module hash string
#[no_mangle]
pub extern "C" fn wamr_module_get_hash(
    module: *mut c_void,
    hash_buf: *mut c_char,
    hash_buf_size: c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || hash_buf.is_null() || hash_buf_size <= 0 {
        return -1;
    }
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        match module_get_hash(module_ref) {
            Some(hash) => {
                write_error_to_buffer(&hash, hash_buf, hash_buf_size);
                0
            }
            None => -1,
        }
    }
}

/// Get the package type of a bytecode buffer
#[no_mangle]
pub extern "C" fn wamr_get_file_package_type(
    buf: *const c_uchar,
    size: c_int,
) -> c_int {
    clear_last_error();
    if buf.is_null() || size <= 0 {
        return crate::bindings::PACKAGE_TYPE_UNKNOWN as c_int;
    }
    let bytes = unsafe { std::slice::from_raw_parts(buf, size as usize) };
    get_file_package_type(bytes) as c_int
}

/// Get the package type of a loaded module
#[no_mangle]
pub extern "C" fn wamr_get_module_package_type(module: *mut c_void) -> c_int {
    clear_last_error();
    if module.is_null() {
        return crate::bindings::PACKAGE_TYPE_UNKNOWN as c_int;
    }
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        get_module_package_type(module_ref) as c_int
    }
}

/// Get the package version of a bytecode buffer
#[no_mangle]
pub extern "C" fn wamr_get_file_package_version(
    buf: *const c_uchar,
    size: c_int,
) -> c_int {
    clear_last_error();
    if buf.is_null() || size <= 0 {
        return 0;
    }
    let bytes = unsafe { std::slice::from_raw_parts(buf, size as usize) };
    get_file_package_version(bytes) as c_int
}

/// Get the package version of a loaded module
#[no_mangle]
pub extern "C" fn wamr_get_module_package_version(module: *mut c_void) -> c_int {
    clear_last_error();
    if module.is_null() {
        return 0;
    }
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        get_module_package_version(module_ref) as c_int
    }
}

/// Get the currently supported version for a package type
#[no_mangle]
pub extern "C" fn wamr_get_current_package_version(package_type: c_int) -> c_int {
    clear_last_error();
    get_current_package_version(package_type as u32) as c_int
}

/// Check whether a buffer is an AOT XIP file
#[no_mangle]
pub extern "C" fn wamr_is_xip_file(buf: *const c_uchar, size: c_int) -> c_int {
    clear_last_error();
    if buf.is_null() || size <= 0 {
        return 0;
    }
    let bytes = unsafe { std::slice::from_raw_parts(buf, size as usize) };
    if is_xip_file(bytes) { 1 } else { 0 }
}

/// Check if the underlying binary can be freed after loading
#[no_mangle]
pub extern "C" fn wamr_is_underlying_binary_freeable(module: *mut c_void) -> c_int {
    clear_last_error();
    if module.is_null() {
        return 0;
    }
    unsafe {
        let module_ref = &*(module as *const WamrModule);
        if is_underlying_binary_freeable(module_ref) { 1 } else { 0 }
    }
}

// =============================================================================
// Table Operations
// =============================================================================

/// Get an exported table by name from an instance
#[no_mangle]
pub extern "C" fn wamr_table_get(
    instance: *mut c_void,
    name: *const c_char,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    clear_last_error();
    if instance.is_null() || name.is_null() {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let table_name = match CStr::from_ptr(name).to_str() {
            Ok(s) => s,
            Err(_) => {
                write_error_to_buffer("Invalid table name encoding", error_buf, error_buf_size);
                return ptr::null_mut();
            }
        };

        match table_get(instance_ref, table_name) {
            Ok(table) => Box::into_raw(Box::new(table)) as *mut c_void,
            Err(e) => {
                let error_msg = format!("Table get failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                ptr::null_mut()
            }
        }
    }
}

/// Destroy a table handle
#[no_mangle]
pub extern "C" fn wamr_table_destroy(table: *mut c_void) {
    clear_last_error();
    if !table.is_null() {
        unsafe {
            let _table = Box::from_raw(table as *mut WamrTable);
        }
    }
}

/// Get table current size
#[no_mangle]
pub extern "C" fn wamr_table_size(table: *mut c_void) -> c_int {
    clear_last_error();
    if table.is_null() {
        return -1;
    }
    unsafe {
        let table_ref = &*(table as *const WamrTable);
        table_ref.table_inst.cur_size as c_int
    }
}

/// Get table maximum size
#[no_mangle]
pub extern "C" fn wamr_table_max_size(table: *mut c_void) -> c_int {
    clear_last_error();
    if table.is_null() {
        return -1;
    }
    unsafe {
        let table_ref = &*(table as *const WamrTable);
        table_ref.table_inst.max_size as c_int
    }
}

/// Get table element kind (returns WASM_FUNCREF=129, WASM_EXTERNREF=128, or -1 on error)
#[no_mangle]
pub extern "C" fn wamr_table_elem_kind(table: *mut c_void) -> c_int {
    clear_last_error();
    if table.is_null() {
        return -1;
    }
    unsafe {
        let table_ref = &*(table as *const WamrTable);
        table_ref.table_inst.elem_kind as c_int
    }
}

/// Get a function from a table at the given index
#[no_mangle]
pub extern "C" fn wamr_table_get_function(
    table: *mut c_void,
    index: c_int,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    clear_last_error();
    if table.is_null() || index < 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return ptr::null_mut();
    }

    unsafe {
        let table_ref = &*(table as *const WamrTable);
        match table_get_function(table_ref, index as u32) {
            Ok(function) => Box::into_raw(Box::new(function)) as *mut c_void,
            Err(e) => {
                let error_msg = format!("Table get function failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                ptr::null_mut()
            }
        }
    }
}

/// Call a function indirectly via table index
#[no_mangle]
pub extern "C" fn wamr_call_indirect(
    instance: *mut c_void,
    element_index: c_int,
    args: *const WasmValueFFI,
    arg_count: c_int,
    results: *mut WasmValueFFI,
    result_capacity: c_int,
    result_type_tags: *const c_int,
    result_type_count: c_int,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> c_int {
    clear_last_error();
    if instance.is_null() || element_index < 0 || arg_count < 0 || result_capacity < 0 {
        write_error_to_buffer("Invalid arguments", error_buf, error_buf_size);
        return -1;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);

        let arg_slice = if arg_count > 0 && !args.is_null() {
            std::slice::from_raw_parts(args, arg_count as usize)
        } else {
            &[]
        };

        let wasm_args: Vec<WasmValue> = arg_slice.iter().map(|v| wasm_value_from_ffi(v)).collect();

        // Build result types from the tags
        let rtypes: Vec<WasmType> = if result_type_count > 0 && !result_type_tags.is_null() {
            let tags = std::slice::from_raw_parts(result_type_tags, result_type_count as usize);
            tags.iter().map(|&t| match t {
                WASM_TYPE_I32 => WasmType::I32,
                WASM_TYPE_I64 => WasmType::I64,
                WASM_TYPE_F32 => WasmType::F32,
                WASM_TYPE_F64 => WasmType::F64,
                _ => WasmType::I32,
            }).collect()
        } else {
            Vec::new()
        };

        match call_indirect(instance_ref, element_index as u32, &wasm_args, &rtypes) {
            Ok(wasm_results) => {
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
                let error_msg = format!("Indirect call failed: {:?}", e);
                write_error_to_buffer(&error_msg, error_buf, error_buf_size);
                -1
            }
        }
    }
}

/// Get names of all exported tables from an instance
#[no_mangle]
pub extern "C" fn wamr_get_table_names(
    instance: *mut c_void,
    names_buffer: *mut *mut *mut c_char,
    count: *mut c_int,
) -> c_int {
    clear_last_error();
    if instance.is_null() || names_buffer.is_null() || count.is_null() {
        set_last_error("Invalid parameters for get_table_names".to_string());
        return -1;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let names = get_export_names_by_kind(
            instance_ref.handle, WASM_IMPORT_EXPORT_KIND_TABLE);
        names_vec_to_c_array(names, names_buffer, count)
    }
}

// =============================================================================
// Utility Functions
// =============================================================================

/// Get WAMR version string (derived from actual runtime API)
#[no_mangle]
pub extern "C" fn wamr_get_version() -> *const c_char {
    // Use a thread-local to cache the formatted version string
    thread_local! {
        static VERSION_CSTR: std::cell::RefCell<Option<CString>> = std::cell::RefCell::new(None);
        static VERSION_PTR: std::cell::Cell<*const c_char> = std::cell::Cell::new(ptr::null());
    }

    VERSION_CSTR.with(|cell| {
        let mut borrow = cell.borrow_mut();
        if borrow.is_none() {
            let (major, minor, patch) = runtime_get_version();
            let version = format!("{}.{}.{}", major, minor, patch);
            if let Ok(cstr) = CString::new(version) {
                let ptr = cstr.as_ptr();
                VERSION_PTR.with(|p| p.set(ptr));
                *borrow = Some(cstr);
            }
        }
        VERSION_PTR.with(|p| p.get())
    })
}

/// Get WAMR version as separate major/minor/patch integers
#[no_mangle]
pub extern "C" fn wamr_get_version_parts(
    major_out: *mut c_int,
    minor_out: *mut c_int,
    patch_out: *mut c_int,
) {
    clear_last_error();
    let (major, minor, patch) = runtime_get_version();
    unsafe {
        if !major_out.is_null() {
            *major_out = major as c_int;
        }
        if !minor_out.is_null() {
            *minor_out = minor as c_int;
        }
        if !patch_out.is_null() {
            *patch_out = patch as c_int;
        }
    }
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

                // Ensure null termination when truncated
                if copy_len > 0 {
                    *((buffer as *mut u8).add(copy_len - 1)) = 0;
                }
            }

            copy_len as c_int
        }
        None => 0,
    }
}

// =============================================================================
// Export Enumeration Functions
// =============================================================================

/// Get names of all exported functions from an instance
#[no_mangle]
pub extern "C" fn wamr_get_function_names(
    instance: *mut c_void,
    names_buffer: *mut *mut *mut c_char,
    count: *mut c_int,
) -> c_int {
    clear_last_error();
    if instance.is_null() || names_buffer.is_null() || count.is_null() {
        set_last_error("Invalid parameters for get_function_names".to_string());
        return -1;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let names = get_export_names_by_kind(
            instance_ref.handle, WASM_IMPORT_EXPORT_KIND_FUNC);
        names_vec_to_c_array(names, names_buffer, count)
    }
}

/// Get names of all exported globals from an instance
#[no_mangle]
pub extern "C" fn wamr_get_global_names(
    instance: *mut c_void,
    names_buffer: *mut *mut *mut c_char,
    count: *mut c_int,
) -> c_int {
    clear_last_error();
    if instance.is_null() || names_buffer.is_null() || count.is_null() {
        set_last_error("Invalid parameters for get_global_names".to_string());
        return -1;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let names = get_export_names_by_kind(
            instance_ref.handle, WASM_IMPORT_EXPORT_KIND_GLOBAL);
        names_vec_to_c_array(names, names_buffer, count)
    }
}

/// Get names of all exports from a compiled module (no instance needed)
#[no_mangle]
pub extern "C" fn wamr_module_get_export_names(
    module: *mut c_void,
    names_buffer: *mut *mut *mut c_char,
    count: *mut c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || names_buffer.is_null() || count.is_null() {
        set_last_error("Invalid parameters for module_get_export_names".to_string());
        return -1;
    }

    unsafe {
        let module_ref = &*(module as *const WamrModule);
        let names = module_get_export_names(module_ref.handle);
        names_vec_to_c_array(names, names_buffer, count)
    }
}

/// Get names of all imports from a compiled module (no instance needed)
#[no_mangle]
pub extern "C" fn wamr_module_get_import_names(
    module: *mut c_void,
    names_buffer: *mut *mut *mut c_char,
    count: *mut c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || names_buffer.is_null() || count.is_null() {
        set_last_error("Invalid parameters for module_get_import_names".to_string());
        return -1;
    }

    unsafe {
        let module_ref = &*(module as *const WamrModule);
        let names = module_get_import_names(module_ref.handle);
        names_vec_to_c_array(names, names_buffer, count)
    }
}

/// Get function signature for a named export from a compiled module (no instance needed)
#[no_mangle]
pub extern "C" fn wamr_module_get_export_function_signature(
    module: *mut c_void,
    name: *const c_char,
    param_types: *mut c_int,
    param_count: *mut c_int,
    result_types: *mut c_int,
    result_count: *mut c_int,
) -> c_int {
    clear_last_error();
    if module.is_null() || name.is_null() {
        set_last_error("Invalid parameters for module_get_export_function_signature".to_string());
        return -1;
    }

    let func_name = match unsafe { CStr::from_ptr(name).to_str() } {
        Ok(s) => s,
        Err(_) => {
            set_last_error("Invalid function name encoding".to_string());
            return -1;
        }
    };

    unsafe {
        let module_ref = &*(module as *const WamrModule);
        match module_get_export_function_signature(module_ref.handle, func_name) {
            Some((params, results)) => {
                if !param_count.is_null() {
                    *param_count = params.len() as c_int;
                }
                if !result_count.is_null() {
                    *result_count = results.len() as c_int;
                }
                if !param_types.is_null() {
                    let slice = std::slice::from_raw_parts_mut(param_types, params.len());
                    for (i, pt) in params.iter().enumerate() {
                        slice[i] = wasm_type_to_ffi(pt);
                    }
                }
                if !result_types.is_null() {
                    let slice = std::slice::from_raw_parts_mut(result_types, results.len());
                    for (i, rt) in results.iter().enumerate() {
                        slice[i] = wasm_type_to_ffi(rt);
                    }
                }
                0
            }
            None => {
                set_last_error(format!("Function '{}' not found in module exports", func_name));
                -1
            }
        }
    }
}

/// Free array of names allocated by get_function_names or get_global_names
#[no_mangle]
pub extern "C" fn wamr_free_names(names: *mut *mut c_char, count: c_int) {
    if names.is_null() || count <= 0 {
        return;
    }

    unsafe {
        // Free each string
        for i in 0..count {
            let name = *names.add(i as usize);
            if !name.is_null() {
                libc::free(name as *mut c_void);
            }
        }
        // Free the array itself
        libc::free(names as *mut c_void);
    }
}

/// Free a string allocated by Rust's CString::into_raw (e.g., from wamr_instance_get_exception).
#[no_mangle]
pub extern "C" fn wamr_free_string(s: *mut c_char) {
    if !s.is_null() {
        unsafe {
            let _ = CString::from_raw(s);
        }
    }
}

/// Get a global variable value
#[no_mangle]
pub extern "C" fn wamr_get_global(
    instance: *mut c_void,
    name: *const c_char,
    value_type: *mut c_int,
    value: *mut c_void,
    value_size: c_int,
) -> c_int {
    clear_last_error();
    if instance.is_null() || name.is_null() || value_type.is_null() || value.is_null() {
        set_last_error("Invalid parameters for get_global".to_string());
        return -1;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        match global_get(instance_ref.handle, name) {
            Ok(wasm_val) => {
                let (kind, copy_size): (c_int, c_int) = match &wasm_val {
                    WasmValue::I32(_) => (WASM_TYPE_I32, 4),
                    WasmValue::I64(_) => (WASM_TYPE_I64, 8),
                    WasmValue::F32(_) => (WASM_TYPE_F32, 4),
                    WasmValue::F64(_) => (WASM_TYPE_F64, 8),
                };

                if value_size < copy_size {
                    set_last_error("Value buffer too small".to_string());
                    return -1;
                }

                *value_type = kind;
                match wasm_val {
                    WasmValue::I32(v) => *(value as *mut i32) = v,
                    WasmValue::I64(v) => *(value as *mut i64) = v,
                    WasmValue::F32(v) => *(value as *mut f32) = v,
                    WasmValue::F64(v) => *(value as *mut f64) = v,
                }
                0
            }
            Err(e) => {
                set_last_error(format!("{}", e));
                -1
            }
        }
    }
}

/// Set a global variable value
#[no_mangle]
pub extern "C" fn wamr_set_global(
    instance: *mut c_void,
    name: *const c_char,
    value_type: c_int,
    value: *const c_void,
) -> c_int {
    clear_last_error();
    if instance.is_null() || name.is_null() || value.is_null() {
        set_last_error("Invalid parameters for set_global".to_string());
        return -1;
    }

    unsafe {
        let instance_ref = &*(instance as *const WamrInstance);
        let wasm_val = match value_type {
            WASM_TYPE_I32 => WasmValue::I32(*(value as *const i32)),
            WASM_TYPE_I64 => WasmValue::I64(*(value as *const i64)),
            WASM_TYPE_F32 => WasmValue::F32(*(value as *const f32)),
            WASM_TYPE_F64 => WasmValue::F64(*(value as *const f64)),
            _ => {
                set_last_error("Unsupported global type".to_string());
                return -1;
            }
        };

        match global_set(instance_ref.handle, name, &wasm_val) {
            Ok(()) => 0,
            Err(e) => {
                set_last_error(format!("{}", e));
                -1
            }
        }
    }
}

/// Convert a Vec<String> to a C-allocated array of C strings
unsafe fn names_vec_to_c_array(
    names: Vec<String>,
    names_buffer: *mut *mut *mut c_char,
    count: *mut c_int,
) -> c_int {
    *count = names.len() as c_int;

    if names.is_empty() {
        *names_buffer = std::ptr::null_mut();
        return 0;
    }

    let array = libc::malloc(names.len() * std::mem::size_of::<*mut c_char>()) as *mut *mut c_char;
    if array.is_null() {
        set_last_error("Failed to allocate memory for names array".to_string());
        return -1;
    }

    for (i, name) in names.iter().enumerate() {
        let c_name = libc::malloc(name.len() + 1) as *mut c_char;
        if !c_name.is_null() {
            std::ptr::copy_nonoverlapping(name.as_ptr(), c_name as *mut u8, name.len());
            *(c_name as *mut u8).add(name.len()) = 0;
            *array.add(i) = c_name;
        } else {
            *array.add(i) = std::ptr::null_mut();
        }
    }

    *names_buffer = array;
    0
}

// =============================================================================
// Host Function Registration (Panama FFI)
// =============================================================================

/// Register a batch of host functions for a given module name.
///
/// Parameters:
/// - module_name: the WASM import module name (e.g. "env")
/// - func_names: array of function name C strings
/// - param_type_arrays: flattened param types (each func's params concatenated)
/// - param_counts: array of per-function param counts
/// - result_type_arrays: flattened result types
/// - result_counts: array of per-function result counts
/// - callback_fn: the callback function pointer (same for all functions)
/// - user_data_array: array of per-function opaque user_data pointers
/// - num_functions: number of functions to register
///
/// Returns: opaque registration handle (freed with wamr_unregister_host_functions), or null on error
#[no_mangle]
pub extern "C" fn wamr_register_host_functions(
    module_name: *const c_char,
    func_names: *const *const c_char,
    param_type_arrays: *const u8,
    param_counts: *const u32,
    result_type_arrays: *const u8,
    result_counts: *const u32,
    callback_fn: HostCallbackFn,
    user_data_array: *const *mut c_void,
    num_functions: u32,
) -> *mut c_void {
    clear_last_error();

    if module_name.is_null() || func_names.is_null() || num_functions == 0 {
        set_last_error("Invalid parameters for register_host_functions".to_string());
        return std::ptr::null_mut();
    }

    let mod_name = match unsafe { CStr::from_ptr(module_name).to_str() } {
        Ok(s) => s,
        Err(_) => {
            set_last_error("Invalid module name encoding".to_string());
            return std::ptr::null_mut();
        }
    };

    let mut functions = Vec::with_capacity(num_functions as usize);
    let mut param_offset: usize = 0;
    let mut result_offset: usize = 0;

    for i in 0..num_functions as usize {
        let name = match unsafe { CStr::from_ptr(*func_names.add(i)).to_str() } {
            Ok(s) => s.to_string(),
            Err(_) => {
                set_last_error(format!("Invalid function name at index {}", i));
                return std::ptr::null_mut();
            }
        };

        let pc = unsafe { *param_counts.add(i) } as usize;
        let rc = unsafe { *result_counts.add(i) } as usize;

        let params: Vec<u8> = (0..pc)
            .map(|j| unsafe { *param_type_arrays.add(param_offset + j) })
            .collect();
        let results: Vec<u8> = (0..rc)
            .map(|j| unsafe { *result_type_arrays.add(result_offset + j) })
            .collect();

        let ud = unsafe { *user_data_array.add(i) };

        functions.push((name, params, results, callback_fn, ud));
        param_offset += pc;
        result_offset += rc;
    }

    match register_host_functions(mod_name, functions) {
        Ok(reg) => Box::into_raw(Box::new(reg)) as *mut c_void,
        Err(_) => std::ptr::null_mut(),
    }
}

/// Destroy a host function registration, unregistering all functions.
#[no_mangle]
pub extern "C" fn wamr_destroy_host_function_registration(registration: *mut c_void) {
    if !registration.is_null() {
        unsafe {
            let _ = Box::from_raw(registration as *mut NativeRegistration);
        }
    }
}

// =============================================================================
// Exception & Execution Control
// =============================================================================

/// Get the current exception on an instance. Returns null if no exception.
/// The returned string is allocated with malloc and must be freed by the caller.
#[no_mangle]
pub extern "C" fn wamr_instance_get_exception(instance: *mut c_void) -> *mut c_char {
    if instance.is_null() {
        return ptr::null_mut();
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    match instance_get_exception(instance_ref) {
        Some(msg) => {
            match CString::new(msg) {
                Ok(c) => c.into_raw(),
                Err(_) => ptr::null_mut(),
            }
        }
        None => ptr::null_mut(),
    }
}

/// Set a custom exception on an instance. Returns 0 on success, -1 on failure.
#[no_mangle]
pub extern "C" fn wamr_instance_set_exception(instance: *mut c_void, exception: *const c_char) -> c_int {
    if instance.is_null() || exception.is_null() {
        return -1;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    let exception_str = match unsafe { CStr::from_ptr(exception).to_str() } {
        Ok(s) => s,
        Err(_) => return -1,
    };
    match instance_set_exception(instance_ref, exception_str) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

/// Clear the current exception on an instance.
#[no_mangle]
pub extern "C" fn wamr_instance_clear_exception(instance: *mut c_void) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_clear_exception(instance_ref);
}

/// Terminate execution of an instance.
#[no_mangle]
pub extern "C" fn wamr_instance_terminate(instance: *mut c_void) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_terminate(instance_ref);
}

/// Set the instruction count limit for an instance's execution environment.
/// Pass -1 to remove the limit.
#[no_mangle]
pub extern "C" fn wamr_instance_set_instruction_count_limit(instance: *mut c_void, limit: c_int) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    set_instruction_count_limit(instance_ref, limit);
}

// =============================================================================
// WASI Support
// =============================================================================

/// Configure WASI arguments on a module.
/// String arrays are passed as null-terminated C strings with counts.
#[no_mangle]
pub extern "C" fn wamr_module_set_wasi_args(
    module: *mut c_void,
    dir_list: *const *const c_char,
    dir_count: c_int,
    map_dir_list: *const *const c_char,
    map_dir_count: c_int,
    env_vars: *const *const c_char,
    env_count: c_int,
    argv: *const *const c_char,
    argc: c_int,
) -> c_int {
    if module.is_null() {
        return -1;
    }

    let module_ref = unsafe { &*(module as *const crate::types::WamrModule) };

    let dirs = read_string_array(dir_list, dir_count);
    let map_dirs = read_string_array(map_dir_list, map_dir_count);
    let envs = read_string_array(env_vars, env_count);
    let args = read_string_array(argv, argc);

    let dir_refs: Vec<&str> = dirs.iter().map(|s| s.as_str()).collect();
    let map_dir_refs: Vec<&str> = map_dirs.iter().map(|s| s.as_str()).collect();
    let env_refs: Vec<&str> = envs.iter().map(|s| s.as_str()).collect();
    let arg_refs: Vec<&str> = args.iter().map(|s| s.as_str()).collect();

    match module_set_wasi_args(module_ref, &dir_refs, &map_dir_refs, &env_refs, &arg_refs) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

/// Configure WASI address pool on a module.
#[no_mangle]
pub extern "C" fn wamr_module_set_wasi_addr_pool(
    module: *mut c_void,
    addr_pool: *const *const c_char,
    addr_pool_size: c_int,
) -> c_int {
    if module.is_null() {
        return -1;
    }

    let module_ref = unsafe { &*(module as *const crate::types::WamrModule) };
    let addrs = read_string_array(addr_pool, addr_pool_size);
    let addr_refs: Vec<&str> = addrs.iter().map(|s| s.as_str()).collect();

    match module_set_wasi_addr_pool(module_ref, &addr_refs) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

/// Configure WASI NS lookup pool on a module.
#[no_mangle]
pub extern "C" fn wamr_module_set_wasi_ns_lookup_pool(
    module: *mut c_void,
    ns_lookup_pool: *const *const c_char,
    ns_lookup_pool_size: c_int,
) -> c_int {
    if module.is_null() {
        return -1;
    }

    let module_ref = unsafe { &*(module as *const crate::types::WamrModule) };
    let ns = read_string_array(ns_lookup_pool, ns_lookup_pool_size);
    let ns_refs: Vec<&str> = ns.iter().map(|s| s.as_str()).collect();

    match module_set_wasi_ns_lookup_pool(module_ref, &ns_refs) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

/// Check if a module instance is in WASI mode.
#[no_mangle]
pub extern "C" fn wamr_instance_is_wasi_mode(instance: *mut c_void) -> c_int {
    if instance.is_null() {
        return 0;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    if instance_is_wasi_mode(instance_ref) { 1 } else { 0 }
}

/// Get the WASI exit code from a module instance.
#[no_mangle]
pub extern "C" fn wamr_instance_get_wasi_exit_code(instance: *mut c_void) -> c_int {
    if instance.is_null() {
        return 0;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_get_wasi_exit_code(instance_ref) as c_int
}

/// Check if WASI _start function exists in the module instance.
#[no_mangle]
pub extern "C" fn wamr_instance_has_wasi_start_function(instance: *mut c_void) -> c_int {
    if instance.is_null() {
        return 0;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    let func = instance_lookup_wasi_start_function(instance_ref);
    if func.is_null() { 0 } else { 1 }
}

/// Execute the WASI _start function.
/// Returns 0 on success, -1 on failure.
#[no_mangle]
pub extern "C" fn wamr_instance_execute_main(
    instance: *mut c_void,
    argv: *const *const c_char,
    argc: c_int,
) -> c_int {
    if instance.is_null() {
        return -1;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    let args = read_string_array(argv, argc);
    let arg_refs: Vec<&str> = args.iter().map(|s| s.as_str()).collect();

    match instance_execute_main(instance_ref, &arg_refs) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

/// Execute a named function with string arguments (WASI-style).
/// Returns 0 on success, -1 on failure.
#[no_mangle]
pub extern "C" fn wamr_instance_execute_func(
    instance: *mut c_void,
    name: *const c_char,
    argv: *const *const c_char,
    argc: c_int,
) -> c_int {
    if instance.is_null() || name.is_null() {
        return -1;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    let func_name = match unsafe { CStr::from_ptr(name).to_str() } {
        Ok(s) => s,
        Err(_) => return -1,
    };
    let args = read_string_array(argv, argc);
    let arg_refs: Vec<&str> = args.iter().map(|s| s.as_str()).collect();

    match instance_execute_func(instance_ref, func_name, &arg_refs) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

/// Helper: read a C string array into a Vec<String>.
// =============================================================================
// Custom Data (Panama FFI)
// =============================================================================

/// Set custom data on a module instance.
#[no_mangle]
pub extern "C" fn wamr_instance_set_custom_data(instance: *mut c_void, custom_data: u64) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_set_custom_data(instance_ref, custom_data);
}

/// Get custom data from a module instance.
#[no_mangle]
pub extern "C" fn wamr_instance_get_custom_data(instance: *mut c_void) -> u64 {
    if instance.is_null() {
        return 0;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_get_custom_data(instance_ref)
}

// =============================================================================
// Debugging & Profiling
// =============================================================================

/// Get the call stack as a malloc'd C string. Caller must free with wamr_free_string.
/// Returns null if unavailable.
#[no_mangle]
pub extern "C" fn wamr_instance_get_call_stack(instance: *mut c_void) -> *mut c_char {
    if instance.is_null() {
        return std::ptr::null_mut();
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    let stack = instance_get_call_stack(instance_ref);
    if stack.is_empty() {
        return std::ptr::null_mut();
    }
    match std::ffi::CString::new(stack) {
        Ok(cs) => cs.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Dump call stack to stdout.
#[no_mangle]
pub extern "C" fn wamr_instance_dump_call_stack(instance: *mut c_void) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_dump_call_stack(instance_ref);
}

/// Dump performance profiling data to stdout.
#[no_mangle]
pub extern "C" fn wamr_instance_dump_perf_profiling(instance: *mut c_void) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_dump_perf_profiling(instance_ref);
}

/// Get total WASM execution time in milliseconds.
#[no_mangle]
pub extern "C" fn wamr_instance_sum_exec_time(instance: *mut c_void) -> f64 {
    if instance.is_null() {
        return 0.0;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_sum_wasm_exec_time(instance_ref)
}

/// Get execution time for a specific function in milliseconds.
#[no_mangle]
pub extern "C" fn wamr_instance_get_func_exec_time(
    instance: *mut c_void,
    func_name: *const c_char,
) -> f64 {
    if instance.is_null() || func_name.is_null() {
        return 0.0;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    let name = unsafe { std::ffi::CStr::from_ptr(func_name) };
    let name_str = match name.to_str() {
        Ok(s) => s,
        Err(_) => return 0.0,
    };
    instance_get_wasm_func_exec_time(instance_ref, name_str)
}

/// Dump memory consumption to stdout.
#[no_mangle]
pub extern "C" fn wamr_instance_dump_mem_consumption(instance: *mut c_void) {
    if instance.is_null() {
        return;
    }
    let instance_ref = unsafe { &*(instance as *const crate::types::WamrInstance) };
    instance_dump_mem_consumption(instance_ref);
}

// =============================================================================
// Threading
// =============================================================================

/// Initialize the thread environment for the current native thread.
#[no_mangle]
pub extern "C" fn wamr_init_thread_env() -> c_int {
    if init_thread_env() { 0 } else { -1 }
}

/// Destroy the thread environment for the current native thread.
#[no_mangle]
pub extern "C" fn wamr_destroy_thread_env() {
    destroy_thread_env();
}

/// Check if the thread environment has been initialized.
#[no_mangle]
pub extern "C" fn wamr_is_thread_env_inited() -> c_int {
    if is_thread_env_inited() { 1 } else { 0 }
}

/// Set the maximum number of threads.
#[no_mangle]
pub extern "C" fn wamr_set_max_thread_num(num: c_int) {
    if num >= 0 {
        set_max_thread_num(num as u32);
    }
}

// =============================================================================
// Advanced Instantiation & Miscellaneous
// =============================================================================

/// Instantiate a module with extended arguments.
#[no_mangle]
pub extern "C" fn wamr_instance_create_ex(
    module: *mut c_void,
    default_stack_size: c_int,
    host_managed_heap_size: c_int,
    max_memory_pages: c_int,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    if module.is_null() {
        write_error_to_buffer("Module handle is null", error_buf, error_buf_size);
        return std::ptr::null_mut();
    }
    let module_ref = unsafe { &*(module as *const crate::types::WamrModule) };
    match instance_create_ex(
        module_ref,
        default_stack_size as u32,
        host_managed_heap_size as u32,
        max_memory_pages as u32,
    ) {
        Ok(instance) => Box::into_raw(instance) as *mut c_void,
        Err(e) => {
            let msg = format!("{}", e);
            write_error_to_buffer(&msg, error_buf, error_buf_size);
            std::ptr::null_mut()
        }
    }
}

/// Get a custom section from a module. Returns a malloc'd copy of the section data.
/// The caller must free the returned buffer with wamr_free_string.
/// Returns null if the section doesn't exist. Sets *out_len to the length.
#[no_mangle]
pub extern "C" fn wamr_module_get_custom_section(
    module: *mut c_void,
    name: *const c_char,
    out_len: *mut c_int,
) -> *mut u8 {
    if module.is_null() || name.is_null() {
        if !out_len.is_null() {
            unsafe { *out_len = 0; }
        }
        return std::ptr::null_mut();
    }
    let name_str = unsafe { std::ffi::CStr::from_ptr(name) };
    let name_str = match name_str.to_str() {
        Ok(s) => s,
        Err(_) => {
            if !out_len.is_null() {
                unsafe { *out_len = 0; }
            }
            return std::ptr::null_mut();
        }
    };
    let module_ref = unsafe { &*(module as *const crate::types::WamrModule) };
    let data = module_get_custom_section(module_ref, name_str);
    if data.is_empty() {
        if !out_len.is_null() {
            unsafe { *out_len = 0; }
        }
        return std::ptr::null_mut();
    }
    // Allocate and copy data for caller to own (using WAMR's allocator)
    let len = data.len();
    let buf = unsafe { crate::bindings::wasm_runtime_malloc(len as u32) as *mut u8 };
    if buf.is_null() {
        if !out_len.is_null() {
            unsafe { *out_len = 0; }
        }
        return std::ptr::null_mut();
    }
    unsafe {
        std::ptr::copy_nonoverlapping(data.as_ptr(), buf, len);
        if !out_len.is_null() {
            *out_len = len as c_int;
        }
    }
    buf
}

/// Free a buffer that was allocated with wasm_runtime_malloc (used by get_custom_section).
#[no_mangle]
pub extern "C" fn wamr_free_native_buffer(ptr: *mut u8) {
    if !ptr.is_null() {
        unsafe { crate::bindings::wasm_runtime_free(ptr as *mut c_void); }
    }
}

// =============================================================================
// Phase 14: Type Introspection
// =============================================================================

/// Get global type info (valkind, is_mutable) for an exported global by name.
/// Returns 0 on success, -1 if not found.
#[no_mangle]
pub extern "C" fn wamr_get_export_global_type_info(
    module_handle: *mut c_void,
    name: *const c_char,
    valkind_out: *mut u8,
    is_mutable_out: *mut c_int,
) -> c_int {
    clear_last_error();
    if module_handle.is_null() || name.is_null() {
        set_last_error("Module handle or name is null".to_string());
        return -1;
    }
    let module = unsafe { &*(module_handle as *const WamrModule) };
    let name_str = match unsafe { CStr::from_ptr(name) }.to_str() {
        Ok(s) => s,
        Err(_) => {
            set_last_error("Invalid UTF-8 in global name".to_string());
            return -1;
        }
    };
    match get_export_global_type_info(module, name_str) {
        Some((valkind, is_mutable)) => {
            if !valkind_out.is_null() {
                unsafe { *valkind_out = valkind; }
            }
            if !is_mutable_out.is_null() {
                unsafe { *is_mutable_out = if is_mutable { 1 } else { 0 }; }
            }
            0
        }
        None => {
            set_last_error("Export global not found".to_string());
            -1
        }
    }
}

/// Get memory type info (is_shared, init_page_count, max_page_count) for an exported memory by name.
/// Returns 0 on success, -1 if not found.
#[no_mangle]
pub extern "C" fn wamr_get_export_memory_type_info(
    module_handle: *mut c_void,
    name: *const c_char,
    is_shared_out: *mut c_int,
    init_page_count_out: *mut c_int,
    max_page_count_out: *mut c_int,
) -> c_int {
    clear_last_error();
    if module_handle.is_null() || name.is_null() {
        set_last_error("Module handle or name is null".to_string());
        return -1;
    }
    let module = unsafe { &*(module_handle as *const WamrModule) };
    let name_str = match unsafe { CStr::from_ptr(name) }.to_str() {
        Ok(s) => s,
        Err(_) => {
            set_last_error("Invalid UTF-8 in memory name".to_string());
            return -1;
        }
    };
    match get_export_memory_type_info(module, name_str) {
        Some((is_shared, init_pages, max_pages)) => {
            if !is_shared_out.is_null() {
                unsafe { *is_shared_out = if is_shared { 1 } else { 0 }; }
            }
            if !init_page_count_out.is_null() {
                unsafe { *init_page_count_out = init_pages as c_int; }
            }
            if !max_page_count_out.is_null() {
                unsafe { *max_page_count_out = max_pages as c_int; }
            }
            0
        }
        None => {
            set_last_error("Export memory not found".to_string());
            -1
        }
    }
}

// =============================================================================
// Phase 15: Import Link Checking
// =============================================================================

/// Check if an import function is linked (has a registered host implementation).
/// Returns 1 if linked, 0 if not.
#[no_mangle]
pub extern "C" fn wamr_is_import_func_linked(
    module_name: *const c_char,
    func_name: *const c_char,
) -> c_int {
    clear_last_error();
    if module_name.is_null() || func_name.is_null() {
        return 0;
    }
    let mod_str = match unsafe { CStr::from_ptr(module_name) }.to_str() {
        Ok(s) => s,
        Err(_) => return 0,
    };
    let func_str = match unsafe { CStr::from_ptr(func_name) }.to_str() {
        Ok(s) => s,
        Err(_) => return 0,
    };
    if is_import_func_linked(mod_str, func_str) { 1 } else { 0 }
}

/// Check if an import global is linked (has a registered host implementation).
/// Returns 1 if linked, 0 if not.
#[no_mangle]
pub extern "C" fn wamr_is_import_global_linked(
    module_name: *const c_char,
    global_name: *const c_char,
) -> c_int {
    clear_last_error();
    if module_name.is_null() || global_name.is_null() {
        return 0;
    }
    let mod_str = match unsafe { CStr::from_ptr(module_name) }.to_str() {
        Ok(s) => s,
        Err(_) => return 0,
    };
    let global_str = match unsafe { CStr::from_ptr(global_name) }.to_str() {
        Ok(s) => s,
        Err(_) => return 0,
    };
    if is_import_global_linked(mod_str, global_str) { 1 } else { 0 }
}

// =============================================================================
// Phase 16: Exec Env & Memory Lookup
// =============================================================================

/// Lookup a memory instance by export name.
/// Returns a pointer to the memory instance, or null if not found.
#[no_mangle]
pub extern "C" fn wamr_instance_lookup_memory(
    instance_handle: *mut c_void,
    name: *const c_char,
) -> *mut c_void {
    clear_last_error();
    if instance_handle.is_null() || name.is_null() {
        return ptr::null_mut();
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    let name_str = match unsafe { CStr::from_ptr(name) }.to_str() {
        Ok(s) => s,
        Err(_) => return ptr::null_mut(),
    };
    instance_lookup_memory(instance, name_str) as *mut c_void
}

// =============================================================================
// Phase 17: Blocking Operations & Stack Overflow Detection
// =============================================================================

/// Begin a blocking operation on an instance's exec env.
/// Returns 1 on success, 0 on failure.
#[no_mangle]
pub extern "C" fn wamr_instance_begin_blocking_op(
    instance_handle: *mut c_void,
) -> c_int {
    clear_last_error();
    if instance_handle.is_null() {
        return 0;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    if instance_begin_blocking_op(instance) { 1 } else { 0 }
}

/// End a blocking operation on an instance's exec env.
#[no_mangle]
pub extern "C" fn wamr_instance_end_blocking_op(
    instance_handle: *mut c_void,
) {
    if instance_handle.is_null() {
        return;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    instance_end_blocking_op(instance);
}

/// Detect native stack overflow.
/// Returns 1 if stack overflow detected, 0 if safe.
#[no_mangle]
pub extern "C" fn wamr_instance_detect_native_stack_overflow(
    instance_handle: *mut c_void,
) -> c_int {
    if instance_handle.is_null() {
        return 1;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    if instance_detect_native_stack_overflow(instance) { 1 } else { 0 }
}

/// Detect native stack overflow with required size.
/// Returns 1 if stack overflow detected, 0 if safe.
#[no_mangle]
pub extern "C" fn wamr_instance_detect_native_stack_overflow_size(
    instance_handle: *mut c_void,
    required_size: c_int,
) -> c_int {
    if instance_handle.is_null() {
        return 1;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    if instance_detect_native_stack_overflow_size(instance, required_size as u32) { 1 } else { 0 }
}

// =============================================================================
// Phase 18: Runtime Mem Info
// =============================================================================

/// Get memory allocation info. Returns 0 on success, -1 on failure.
#[no_mangle]
pub extern "C" fn wamr_get_mem_alloc_info(
    total_size_out: *mut c_int,
    total_free_size_out: *mut c_int,
    highmark_size_out: *mut c_int,
) -> c_int {
    clear_last_error();
    match get_mem_alloc_info() {
        Some((total, free, highmark)) => {
            if !total_size_out.is_null() {
                unsafe { *total_size_out = total as c_int; }
            }
            if !total_free_size_out.is_null() {
                unsafe { *total_free_size_out = free as c_int; }
            }
            if !highmark_size_out.is_null() {
                unsafe { *highmark_size_out = highmark as c_int; }
            }
            0
        }
        None => -1,
    }
}

// =============================================================================
// Phase 21: Externref
// =============================================================================

/// Map an external object to an externref index.
#[no_mangle]
pub extern "C" fn wamr_externref_obj2ref(
    instance_handle: *mut c_void,
    extern_obj: *mut c_void,
    p_idx: *mut c_int,
) -> c_int {
    if instance_handle.is_null() || p_idx.is_null() {
        return 0;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    match externref_obj2ref(instance, extern_obj) {
        Some(idx) => {
            unsafe { *p_idx = idx as c_int; }
            1
        }
        None => 0,
    }
}

/// Delete an externref mapping.
#[no_mangle]
pub extern "C" fn wamr_externref_objdel(
    instance_handle: *mut c_void,
    extern_obj: *mut c_void,
) -> c_int {
    if instance_handle.is_null() {
        return 0;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    if externref_objdel(instance, extern_obj) { 1 } else { 0 }
}

/// Get external object from externref index.
#[no_mangle]
pub extern "C" fn wamr_externref_ref2obj(
    externref_idx: c_int,
    p_obj: *mut *mut c_void,
) -> c_int {
    if p_obj.is_null() {
        return 0;
    }
    match externref_ref2obj(externref_idx as u32) {
        Some(obj) => {
            unsafe { *p_obj = obj; }
            1
        }
        None => 0,
    }
}

/// Retain an externref.
#[no_mangle]
pub extern "C" fn wamr_externref_retain(externref_idx: c_int) -> c_int {
    if externref_retain(externref_idx as u32) { 1 } else { 0 }
}

// =============================================================================
// Phase 22: Module Instance Context
// =============================================================================

/// Create a context key.
#[no_mangle]
pub extern "C" fn wamr_create_context_key() -> *mut c_void {
    create_context_key()
}

/// Destroy a context key.
#[no_mangle]
pub extern "C" fn wamr_destroy_context_key(key: *mut c_void) {
    destroy_context_key(key);
}

/// Set context on an instance.
#[no_mangle]
pub extern "C" fn wamr_set_context(
    instance_handle: *mut c_void,
    key: *mut c_void,
    ctx: *mut c_void,
) {
    if instance_handle.is_null() {
        return;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    set_context(instance, key, ctx);
}

/// Set context and spread to spawned threads.
#[no_mangle]
pub extern "C" fn wamr_set_context_spread(
    instance_handle: *mut c_void,
    key: *mut c_void,
    ctx: *mut c_void,
) {
    if instance_handle.is_null() {
        return;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    set_context_spread(instance, key, ctx);
}

/// Get context from an instance.
#[no_mangle]
pub extern "C" fn wamr_get_context(
    instance_handle: *mut c_void,
    key: *mut c_void,
) -> *mut c_void {
    if instance_handle.is_null() {
        return ptr::null_mut();
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    get_context(instance, key)
}

// =============================================================================
// Phase 23: Thread Spawning
// =============================================================================

/// Spawn a new exec_env for parallel execution.
#[no_mangle]
pub extern "C" fn wamr_spawn_exec_env(
    instance_handle: *mut c_void,
) -> *mut c_void {
    if instance_handle.is_null() {
        return ptr::null_mut();
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    spawn_exec_env(instance) as *mut c_void
}

/// Destroy a spawned exec_env.
#[no_mangle]
pub extern "C" fn wamr_destroy_spawned_exec_env(
    exec_env: *mut c_void,
) {
    destroy_spawned_exec_env(exec_env as *mut _);
}

// =============================================================================
// Phase 25: InstantiationArgs2 API
// =============================================================================

/// Instantiate a module using the opaque InstantiationArgs2 API.
#[no_mangle]
pub extern "C" fn wamr_instance_create_ex2(
    module_handle: *mut c_void,
    default_stack_size: c_int,
    host_managed_heap_size: c_int,
    max_memory_pages: c_int,
    error_buf: *mut c_char,
    error_buf_size: c_int,
) -> *mut c_void {
    clear_last_error();
    if module_handle.is_null() {
        write_error_to_buffer("Module handle is null", error_buf, error_buf_size);
        return ptr::null_mut();
    }
    let module = unsafe { &*(module_handle as *const WamrModule) };
    match instance_create_ex2(
        module,
        default_stack_size as u32,
        host_managed_heap_size as u32,
        max_memory_pages as u32,
    ) {
        Ok(instance) => Box::into_raw(instance) as *mut c_void,
        Err(e) => {
            let msg = format!("{}", e);
            write_error_to_buffer(&msg, error_buf, error_buf_size);
            ptr::null_mut()
        }
    }
}

// =============================================================================
// Phase 26: Callstack Frames
// =============================================================================

/// Get the number of callstack frames.
#[no_mangle]
pub extern "C" fn wamr_copy_callstack(
    instance_handle: *mut c_void,
    func_indices_out: *mut c_int,
    func_offsets_out: *mut c_int,
    max_frames: c_int,
    skip: c_int,
) -> c_int {
    if instance_handle.is_null() || max_frames <= 0 {
        return 0;
    }
    let instance = unsafe { &*(instance_handle as *const WamrInstance) };
    let frames = copy_callstack(instance, skip as u32);
    let count = std::cmp::min(frames.len(), max_frames as usize);
    for i in 0..count {
        if !func_indices_out.is_null() {
            unsafe { *func_indices_out.add(i) = frames[i].1 as c_int; }
        }
        if !func_offsets_out.is_null() {
            unsafe { *func_offsets_out.add(i) = frames[i].2 as c_int; }
        }
    }
    count as c_int
}

// =============================================================================
// Helpers
// =============================================================================

// =============================================================================
// Shared Heap FFI
// =============================================================================

#[no_mangle]
pub extern "C" fn wamr_shared_heap_create(size: u32) -> *mut c_void {
    shared_heap_create(size)
}

#[no_mangle]
pub extern "C" fn wamr_shared_heap_attach(instance: *mut c_void, heap: *mut c_void) -> c_int {
    if instance.is_null() || heap.is_null() {
        return -1;
    }
    let inst = unsafe { &*(instance as *const WamrInstance) };
    if shared_heap_attach(inst, heap) { 0 } else { -1 }
}

#[no_mangle]
pub extern "C" fn wamr_shared_heap_detach(instance: *mut c_void) {
    if instance.is_null() {
        return;
    }
    let inst = unsafe { &*(instance as *const WamrInstance) };
    shared_heap_detach(inst);
}

#[no_mangle]
pub extern "C" fn wamr_shared_heap_chain(head: *mut c_void, body: *mut c_void) -> *mut c_void {
    shared_heap_chain(head, body)
}

#[no_mangle]
pub extern "C" fn wamr_shared_heap_malloc(instance: *mut c_void, size: u64) -> u64 {
    if instance.is_null() {
        return 0;
    }
    let inst = unsafe { &*(instance as *const WamrInstance) };
    shared_heap_malloc(inst, size)
}

#[no_mangle]
pub extern "C" fn wamr_shared_heap_free(instance: *mut c_void, ptr: u64) {
    if instance.is_null() {
        return;
    }
    let inst = unsafe { &*(instance as *const WamrInstance) };
    shared_heap_free(inst, ptr);
}

fn read_string_array(arr: *const *const c_char, count: c_int) -> Vec<String> {
    if arr.is_null() || count <= 0 {
        return Vec::new();
    }
    (0..count as usize)
        .filter_map(|i| {
            let ptr = unsafe { *arr.add(i) };
            if ptr.is_null() {
                None
            } else {
                unsafe { CStr::from_ptr(ptr).to_str().ok().map(|s| s.to_string()) }
            }
        })
        .collect()
}

