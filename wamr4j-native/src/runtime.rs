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

//! Runtime management functions for WAMR operations
//!
//! This module provides high-level runtime management functionality,
//! including initialization, configuration, and resource management
//! for the WAMR WebAssembly runtime.

use crate::types::{
    WamrRuntime, WamrModule, WamrInstance, WamrFunction, WamrMemory, WamrTable,
    WamrError, WasmValue, WasmType, WasiStrings,
    HostFunctionContext, HostCallbackFn, NativeRegistration,
    WASM_PAGE_SIZE, ERROR_BUF_SIZE,
};
use crate::bindings;
use crate::utils::set_last_error;
use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_void};

// =============================================================================
// Signal Handler Preservation (JVM compatibility)
// =============================================================================

/// WAMR's `wasm_runtime_init()` installs signal handlers for SIGSEGV and SIGBUS
/// (when OS_ENABLE_HW_BOUND_CHECK is defined, which it is on macOS ARM64).
/// These handlers conflict with the JVM's own signal handlers, causing SIGILL.
///
/// To fix this, we save the existing signal handlers before calling
/// `wasm_runtime_init()`, then restore them afterwards. WAMR's internal data
/// structures for HW bounds checking are still initialized correctly, but the
/// JVM retains control of signal handling.
#[cfg(unix)]
mod signal_guard {
    use std::mem::MaybeUninit;

    // Signal numbers from libc
    const SIGSEGV: libc::c_int = libc::SIGSEGV;
    const SIGBUS: libc::c_int = libc::SIGBUS;

    /// Save current signal handlers for SIGSEGV and SIGBUS
    pub fn save_signal_handlers() -> Option<(libc::sigaction, libc::sigaction)> {
        unsafe {
            let mut segv_action = MaybeUninit::<libc::sigaction>::zeroed();
            let mut bus_action = MaybeUninit::<libc::sigaction>::zeroed();

            if libc::sigaction(SIGSEGV, std::ptr::null(), segv_action.as_mut_ptr()) != 0 {
                return None;
            }
            if libc::sigaction(SIGBUS, std::ptr::null(), bus_action.as_mut_ptr()) != 0 {
                return None;
            }

            Some((segv_action.assume_init(), bus_action.assume_init()))
        }
    }

    /// Restore previously saved signal handlers
    pub fn restore_signal_handlers(handlers: &(libc::sigaction, libc::sigaction)) {
        unsafe {
            libc::sigaction(SIGSEGV, &handlers.0, std::ptr::null_mut());
            libc::sigaction(SIGBUS, &handlers.1, std::ptr::null_mut());
        }
    }
}

// =============================================================================
// Runtime Management
// =============================================================================

/// Initialize WAMR runtime
pub fn runtime_init() -> Result<WamrRuntime, WamrError> {
    // Save JVM signal handlers before WAMR init overwrites them
    #[cfg(unix)]
    let saved_handlers = signal_guard::save_signal_handlers();

    let success = unsafe { bindings::wasm_runtime_init() };

    // Restore JVM signal handlers after WAMR init
    #[cfg(unix)]
    if let Some(ref handlers) = saved_handlers {
        signal_guard::restore_signal_handlers(handlers);
    }
    if !success {
        let error_msg = "Failed to initialize WAMR runtime";
        set_last_error(error_msg.to_string());
        return Err(WamrError::RuntimeCreationFailed);
    }

    // wasm_runtime_init is a singleton — the "handle" is a sentinel
    // indicating the runtime has been initialized. We use 0x1 as a
    // non-null marker since WAMR doesn't return an opaque handle.
    Ok(WamrRuntime {
        handle: 1 as *mut bindings::WasmRuntimeT,
    })
}

// =============================================================================
// Runtime Configuration
// =============================================================================

/// Check if a running mode is supported
pub fn is_running_mode_supported(mode: u32) -> bool {
    unsafe { bindings::wasm_runtime_is_running_mode_supported(mode) }
}

/// Set the default running mode for new module instances
pub fn set_default_running_mode(mode: u32) -> bool {
    unsafe { bindings::wasm_runtime_set_default_running_mode(mode) }
}

/// Set the running mode for a specific module instance
pub fn set_running_mode(instance: &WamrInstance, mode: u32) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe { bindings::wasm_runtime_set_running_mode(instance.handle, mode) }
}

/// Get the running mode for a specific module instance
pub fn get_running_mode(instance: &WamrInstance) -> u32 {
    if instance.handle.is_null() {
        return 0;
    }
    unsafe { bindings::wasm_runtime_get_running_mode(instance.handle) }
}

/// Set log verbosity level
pub fn set_log_level(level: i32) {
    unsafe { bindings::wasm_runtime_set_log_level(level as std::os::raw::c_int) }
}

/// Enable or disable bounds checks for a module instance
pub fn set_bounds_checks(instance: &WamrInstance, enable: bool) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe { bindings::wasm_runtime_set_bounds_checks(instance.handle, enable) }
}

/// Check if bounds checks are enabled for a module instance
pub fn is_bounds_checks_enabled(instance: &WamrInstance) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe { bindings::wasm_runtime_is_bounds_checks_enabled(instance.handle) }
}

// =============================================================================
// Module Naming and Registration
// =============================================================================

/// Set the name of a module
pub fn module_set_name(module: &WamrModule, name: &str) -> bool {
    if module.handle.is_null() {
        return false;
    }
    let c_name = match CString::new(name) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    unsafe {
        bindings::wasm_runtime_set_module_name(
            module.handle,
            c_name.as_ptr(),
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as u32,
        )
    }
}

/// Get the name of a module
pub fn module_get_name(module: &WamrModule) -> Option<String> {
    if module.handle.is_null() {
        return None;
    }
    unsafe {
        let name_ptr = bindings::wasm_runtime_get_module_name(module.handle);
        if name_ptr.is_null() {
            return None;
        }
        let name = CStr::from_ptr(name_ptr).to_string_lossy().into_owned();
        if name.is_empty() {
            None
        } else {
            Some(name)
        }
    }
}

/// Register a module by name for multi-module support
pub fn module_register(module: &WamrModule, name: &str) -> Result<(), WamrError> {
    if module.handle.is_null() {
        return Err(WamrError::InvalidArguments);
    }
    let c_name = match CString::new(name) {
        Ok(s) => s,
        Err(_) => return Err(WamrError::InvalidArguments),
    };
    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    let ok = unsafe {
        bindings::wasm_runtime_register_module(
            c_name.as_ptr(),
            module.handle,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as u32,
        )
    };
    if ok {
        Ok(())
    } else {
        let error_msg = unsafe {
            CStr::from_ptr(error_buf.as_ptr() as *const c_char)
                .to_string_lossy()
                .into_owned()
        };
        set_last_error(format!("Module registration failed: {}", error_msg));
        Err(WamrError::RuntimeCreationFailed)
    }
}

/// Find a previously registered module by name (returns raw handle)
pub fn module_find_registered(name: &str) -> *mut std::os::raw::c_void {
    let c_name = match CString::new(name) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    unsafe { bindings::wasm_runtime_find_module_registered(c_name.as_ptr()) }
}

// =============================================================================
// Package Type Detection
// =============================================================================

/// Get the package type from a bytecode buffer
pub fn get_file_package_type(buf: &[u8]) -> u32 {
    if buf.is_empty() {
        return bindings::PACKAGE_TYPE_UNKNOWN;
    }
    unsafe {
        bindings::wasm_runtime_get_file_package_type(buf.as_ptr(), buf.len() as u32)
    }
}

/// Get the package type from a loaded module
pub fn get_module_package_type(module: &WamrModule) -> u32 {
    if module.handle.is_null() {
        return bindings::PACKAGE_TYPE_UNKNOWN;
    }
    unsafe { bindings::wasm_runtime_get_module_package_type(module.handle) }
}

/// Get the package version from a bytecode buffer
pub fn get_file_package_version(buf: &[u8]) -> u32 {
    if buf.is_empty() {
        return 0;
    }
    unsafe {
        bindings::wasm_runtime_get_file_package_version(buf.as_ptr(), buf.len() as u32)
    }
}

/// Get the package version from a loaded module
pub fn get_module_package_version(module: &WamrModule) -> u32 {
    if module.handle.is_null() {
        return 0;
    }
    unsafe { bindings::wasm_runtime_get_module_package_version(module.handle) }
}

/// Get the currently supported version for a package type
pub fn get_current_package_version(package_type: u32) -> u32 {
    unsafe { bindings::wasm_runtime_get_current_package_version(package_type) }
}

/// Check if a buffer contains an AOT XIP file
pub fn is_xip_file(buf: &[u8]) -> bool {
    if buf.is_empty() {
        return false;
    }
    unsafe { bindings::wasm_runtime_is_xip_file(buf.as_ptr(), buf.len() as u32) }
}

/// Check if the underlying binary can be freed after loading
pub fn is_underlying_binary_freeable(module: &WamrModule) -> bool {
    if module.handle.is_null() {
        return false;
    }
    unsafe { bindings::wasm_runtime_is_underlying_binary_freeable(module.handle) }
}

/// Get WAMR runtime version as (major, minor, patch)
pub fn runtime_get_version() -> (u32, u32, u32) {
    let mut major: u32 = 0;
    let mut minor: u32 = 0;
    let mut patch: u32 = 0;
    unsafe {
        bindings::wasm_runtime_get_version(&mut major, &mut minor, &mut patch);
    }
    (major, minor, patch)
}

// =============================================================================
// Module Management
// =============================================================================

/// Compile WebAssembly bytecode into a module
///
/// Uses `wasm_runtime_load_ex` with `no_resolve=true` so that import resolution
/// is deferred until instantiation. This allows host functions to be registered
/// after compilation but before instantiation.
pub fn module_compile(runtime: &WamrRuntime, wasm_bytes: &[u8]) -> Result<WamrModule, WamrError> {
    if wasm_bytes.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    if !runtime.is_valid() {
        return Err(WamrError::RuntimeCreationFailed);
    }

    // Basic WASM header validation
    if !is_valid_wasm_header(wasm_bytes) {
        return Err(WamrError::CompilationFailed);
    }

    // Clone the bytes — WAMR stores internal pointers into this buffer
    // (e.g., export name strings) so it must remain alive for the module's lifetime.
    let mut owned_bytes = wasm_bytes.to_vec();

    // Load with no_resolve=true so import symbols are NOT resolved during load.
    // They will be resolved in instance_create() after host functions are registered.
    let load_args = bindings::LoadArgs {
        name: std::ptr::null(),
        clone_wasm_binary: false,
        wasm_binary_freeable: false,
        no_resolve: true,
    };

    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    let module_handle = unsafe {
        bindings::wasm_runtime_load_ex(
            owned_bytes.as_mut_ptr(),
            owned_bytes.len() as u32,
            &load_args,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as u32,
        )
    };

    if module_handle.is_null() {
        // Extract error message from buffer
        let error_msg = unsafe {
            let cstr = std::ffi::CStr::from_ptr(error_buf.as_ptr() as *const c_char);
            cstr.to_string_lossy().into_owned()
        };
        set_last_error(format!("Module compilation failed: {}", error_msg));
        return Err(WamrError::CompilationFailed);
    }

    Ok(WamrModule {
        handle: module_handle,
        wasm_bytes: owned_bytes,
        wasi_strings: std::sync::Mutex::new(None),
    })
}

// =============================================================================
// Instance Management
// =============================================================================

/// Create an instance from a compiled module
pub fn instance_create(
    module: &WamrModule,
    stack_size: usize,
    heap_size: usize
) -> Result<WamrInstance, WamrError> {
    if stack_size == 0 || heap_size == 0 {
        return Err(WamrError::InvalidArguments);
    }

    if !module.is_valid() {
        return Err(WamrError::CompilationFailed);
    }

    // Auto-initialize thread environment for the current thread if needed.
    ensure_thread_env();

    // Resolve import symbols. Module is loaded with no_resolve=true so that
    // host functions can be registered between compile and instantiate.
    // This call links any registered natives to the module's import slots.
    let resolved = unsafe {
        bindings::wasm_runtime_resolve_symbols(module.handle)
    };
    if !resolved {
        set_last_error("Failed to resolve module import symbols".to_string());
        return Err(WamrError::InstantiationFailed);
    }

    // Instantiate WebAssembly module using WAMR C API
    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    let instance_handle = unsafe {
        bindings::wasm_runtime_instantiate(
            module.handle,
            stack_size as u32,
            heap_size as u32,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as u32,
        )
    };

    if instance_handle.is_null() {
        // Extract error message from buffer
        let error_msg = unsafe {
            let cstr = std::ffi::CStr::from_ptr(error_buf.as_ptr() as *const c_char);
            cstr.to_string_lossy().into_owned()
        };
        set_last_error(format!("Instance creation failed: {}", error_msg));
        return Err(WamrError::InstantiationFailed);
    }

    // Create execution environment for function calls
    let exec_env = unsafe {
        bindings::wasm_runtime_create_exec_env(instance_handle, stack_size as u32)
    };
    if exec_env.is_null() {
        // Clean up the instance since we can't create an exec_env
        unsafe { bindings::wasm_runtime_deinstantiate(instance_handle); }
        set_last_error("Failed to create execution environment".to_string());
        return Err(WamrError::InstantiationFailed);
    }

    Ok(WamrInstance {
        handle: instance_handle,
        exec_env,
        stack_size,
        heap_size,
    })
}

// =============================================================================
// Function Management
// =============================================================================

/// Look up a function in an instance by name
pub fn function_lookup(instance: &WamrInstance, name: &str) -> Result<WamrFunction, WamrError> {
    if name.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    // Convert function name to C string
    let c_name = match CString::new(name) {
        Ok(cstr) => cstr,
        Err(_) => {
            set_last_error("Invalid function name: contains null bytes".to_string());
            return Err(WamrError::InvalidArguments);
        }
    };
    
    // Look up function using WAMR C API
    let function_handle = unsafe {
        bindings::wasm_runtime_lookup_function(instance.handle, c_name.as_ptr())
    };
    
    if function_handle.is_null() {
        set_last_error(format!("Function '{}' not found", name));
        return Err(WamrError::FunctionNotFound);
    }
    
    // Get parameter types
    let param_count = unsafe {
        bindings::wasm_func_get_param_count(function_handle, instance.handle)
    };
    let mut raw_param_types = vec![0u8; param_count as usize];
    if param_count > 0 {
        unsafe {
            bindings::wasm_func_get_param_types(
                function_handle, instance.handle, raw_param_types.as_mut_ptr());
        }
    }
    let param_types: Vec<WasmType> = raw_param_types.iter()
        .map(|&t| wasm_valkind_to_type(t))
        .collect();

    // Get result types
    let result_count = unsafe {
        bindings::wasm_func_get_result_count(function_handle, instance.handle)
    };
    let mut raw_result_types = vec![0u8; result_count as usize];
    if result_count > 0 {
        unsafe {
            bindings::wasm_func_get_result_types(
                function_handle, instance.handle, raw_result_types.as_mut_ptr());
        }
    }
    let result_types: Vec<WasmType> = raw_result_types.iter()
        .map(|&t| wasm_valkind_to_type(t))
        .collect();

    Ok(WamrFunction {
        handle: function_handle,
        instance_handle: instance.handle,
        exec_env: instance.exec_env,
        name: name.to_string(),
        param_types,
        result_types,
    })
}

/// Convert WasmValue to WasmValT for the typed call_wasm_a API.
fn wasm_value_to_val_t(value: &WasmValue) -> bindings::WasmValT {
    match value {
        WasmValue::I32(v) => bindings::WasmValT::i32(*v),
        WasmValue::I64(v) => bindings::WasmValT::i64(*v),
        WasmValue::F32(v) => bindings::WasmValT::f32(*v),
        WasmValue::F64(v) => bindings::WasmValT::f64(*v),
    }
}

/// Convert WasmType to the kind byte for a zeroed WasmValT result slot.
pub(crate) fn wasm_type_to_kind(t: &WasmType) -> u8 {
    match t {
        WasmType::I32 => bindings::WASM_I32,
        WasmType::I64 => bindings::WASM_I64,
        WasmType::F32 => bindings::WASM_F32,
        WasmType::F64 => bindings::WASM_F64,
    }
}

/// Convert a WasmValT result back to WasmValue.
fn val_t_to_wasm_value(val: &bindings::WasmValT) -> WasmValue {
    match val.kind {
        bindings::WASM_I32 => WasmValue::I32(val.as_i32()),
        bindings::WASM_I64 => WasmValue::I64(val.as_i64()),
        bindings::WASM_F32 => WasmValue::F32(val.as_f32()),
        bindings::WASM_F64 => WasmValue::F64(val.as_f64()),
        _ => WasmValue::I32(0), // Fallback for unknown types
    }
}

/// Get the exception message from a module instance, clearing it afterward.
pub(crate) fn get_and_clear_exception(instance_handle: *mut bindings::WasmModuleInstT) -> String {
    unsafe {
        let exception_ptr = bindings::wasm_runtime_get_exception(instance_handle);
        if !exception_ptr.is_null() {
            let msg = CStr::from_ptr(exception_ptr)
                .to_string_lossy()
                .into_owned();
            bindings::wasm_runtime_clear_exception(instance_handle);
            msg
        } else {
            "Function execution failed".to_string()
        }
    }
}

/// Call a WebAssembly function with arguments using the typed call_wasm_a API.
pub fn function_call(
    function: &WamrFunction,
    exec_env: *mut bindings::WasmExecEnvT,
    args: &[WasmValue]
) -> Result<Vec<WasmValue>, WamrError> {
    if !function.is_valid() {
        return Err(WamrError::FunctionNotFound);
    }

    if exec_env.is_null() {
        return Err(WamrError::ExecutionFailed("No execution environment".to_string()));
    }

    // Auto-initialize thread environment for the current thread if needed.
    // WAMR requires each thread to have its signal handlers installed for
    // hardware bounds checking. Without this, calls from non-main threads
    // (e.g. Java ExecutorService workers) fail with "thread signal env not inited".
    ensure_thread_env();

    // Convert arguments to typed wasm_val_t array
    let mut wamr_args: Vec<bindings::WasmValT> = args.iter()
        .map(wasm_value_to_val_t)
        .collect();

    // Allocate result slots
    let mut wamr_results: Vec<bindings::WasmValT> = function.result_types.iter()
        .map(|t| bindings::WasmValT::zeroed(wasm_type_to_kind(t)))
        .collect();

    let success = unsafe {
        bindings::wasm_runtime_call_wasm_a(
            exec_env,
            function.handle,
            wamr_results.len() as u32,
            wamr_results.as_mut_ptr(),
            wamr_args.len() as u32,
            wamr_args.as_mut_ptr(),
        )
    };

    if !success {
        let error_msg = get_and_clear_exception(function.instance_handle);
        set_last_error(error_msg.clone());
        return Err(WamrError::ExecutionFailed(error_msg));
    }

    // Convert typed results back to WasmValue
    let results: Vec<WasmValue> = wamr_results.iter()
        .map(val_t_to_wasm_value)
        .collect();

    Ok(results)
}

// =============================================================================
// Memory Management
// =============================================================================

/// Get memory from an instance
pub fn memory_get(instance: &WamrInstance) -> Result<WamrMemory, WamrError> {
    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    // Get the default memory instance handle
    let memory_handle = unsafe {
        bindings::wasm_runtime_get_default_memory(instance.handle)
    };

    // Get actual memory size from WAMR
    let mut start_offset: u64 = 0;
    let mut end_offset: u64 = 0;
    let range_valid = unsafe {
        bindings::wasm_runtime_get_app_addr_range(
            instance.handle,
            0,
            &mut start_offset,
            &mut end_offset,
        )
    };
    if !range_valid {
        return Err(WamrError::MemoryAccessViolation);
    }
    let memory_size = end_offset;

    // Get memory data pointer for offset 0
    let data_ptr = unsafe {
        bindings::wasm_runtime_addr_app_to_native(instance.handle, 0)
    };

    Ok(WamrMemory {
        instance_handle: instance.handle,
        memory_handle,
        size: memory_size as usize,
        data_ptr: data_ptr as *mut u8,
    })
}

/// Grow memory by specified pages
pub fn memory_grow(memory: &mut WamrMemory, pages: u32) -> Result<u32, WamrError> {
    if memory.instance_handle.is_null() {
        return Err(WamrError::MemoryGrowthFailed);
    }

    // Get current page count before growing
    let old_pages = if !memory.memory_handle.is_null() {
        unsafe { bindings::wasm_memory_get_cur_page_count(memory.memory_handle) as u32 }
    } else {
        (memory.size / WASM_PAGE_SIZE) as u32
    };

    // Use wasm_runtime_enlarge_memory to grow
    let success = unsafe {
        bindings::wasm_runtime_enlarge_memory(memory.instance_handle, pages as u64)
    };

    if !success {
        return Err(WamrError::MemoryGrowthFailed);
    }

    // Update cached size and data pointer after growth
    let mut start_offset: u64 = 0;
    let mut end_offset: u64 = 0;
    let range_valid = unsafe {
        bindings::wasm_runtime_get_app_addr_range(
            memory.instance_handle, 0, &mut start_offset, &mut end_offset,
        )
    };
    if range_valid {
        memory.size = end_offset as usize;
    }
    let new_ptr = unsafe {
        bindings::wasm_runtime_addr_app_to_native(memory.instance_handle, 0)
    };
    if !new_ptr.is_null() {
        memory.data_ptr = new_ptr as *mut u8;
    }

    Ok(old_pages)
}

/// Read data from memory at specified offset
pub fn memory_read(memory: &WamrMemory, offset: usize, buffer: &mut [u8]) -> Result<usize, WamrError> {
    if offset >= memory.size {
        return Err(WamrError::InvalidMemoryOffset);
    }
    
    let available = memory.size - offset;
    let read_size = std::cmp::min(buffer.len(), available);
    
    // Get native pointer for the offset
    let src_ptr = unsafe {
        bindings::wasm_runtime_addr_app_to_native(
            memory.instance_handle,
            offset as u64,
        )
    };

    if src_ptr.is_null() {
        return Err(WamrError::MemoryAccessViolation);
    }

    // Copy data from WebAssembly memory to buffer
    unsafe {
        std::ptr::copy_nonoverlapping(
            src_ptr as *const u8,
            buffer.as_mut_ptr(),
            read_size,
        );
    }

    Ok(read_size)
}

/// Write data to memory at specified offset
pub fn memory_write(memory: &mut WamrMemory, offset: usize, data: &[u8]) -> Result<usize, WamrError> {
    if offset >= memory.size {
        return Err(WamrError::InvalidMemoryOffset);
    }
    
    let available = memory.size - offset;
    let write_size = std::cmp::min(data.len(), available);
    
    // Get native pointer for the offset
    let dst_ptr = unsafe {
        bindings::wasm_runtime_addr_app_to_native(
            memory.instance_handle,
            offset as u64,
        )
    };

    if dst_ptr.is_null() {
        return Err(WamrError::MemoryAccessViolation);
    }

    // Copy data from buffer to WebAssembly memory
    unsafe {
        std::ptr::copy_nonoverlapping(
            data.as_ptr(),
            dst_ptr as *mut u8,
            write_size,
        );
    }

    Ok(write_size)
}

/// Get current page count of memory
pub fn memory_page_count(memory: &WamrMemory) -> u64 {
    if !memory.memory_handle.is_null() {
        unsafe { bindings::wasm_memory_get_cur_page_count(memory.memory_handle) }
    } else {
        (memory.size / WASM_PAGE_SIZE) as u64
    }
}

/// Get maximum page count of memory
pub fn memory_max_page_count(memory: &WamrMemory) -> u64 {
    if !memory.memory_handle.is_null() {
        unsafe { bindings::wasm_memory_get_max_page_count(memory.memory_handle) }
    } else {
        0
    }
}

/// Check if memory is shared
pub fn memory_is_shared(memory: &WamrMemory) -> bool {
    if !memory.memory_handle.is_null() {
        unsafe { bindings::wasm_memory_get_shared(memory.memory_handle) }
    } else {
        false
    }
}

/// Allocate memory within the module instance heap.
/// Returns (app_offset, native_ptr). app_offset is 0 on failure.
pub fn module_malloc(instance: &WamrInstance, size: u64) -> Result<(u64, *mut c_void), WamrError> {
    if instance.handle.is_null() || size == 0 {
        return Err(WamrError::InvalidArguments);
    }

    let mut native_addr: *mut c_void = std::ptr::null_mut();
    let app_offset = unsafe {
        bindings::wasm_runtime_module_malloc(
            instance.handle,
            size,
            &mut native_addr,
        )
    };

    if app_offset == 0 {
        set_last_error("Module malloc failed: insufficient memory".to_string());
        return Err(WamrError::MemoryGrowthFailed);
    }

    Ok((app_offset, native_addr))
}

/// Free memory previously allocated by module_malloc
pub fn module_free(instance: &WamrInstance, ptr: u64) {
    if !instance.handle.is_null() && ptr != 0 {
        unsafe {
            bindings::wasm_runtime_module_free(instance.handle, ptr);
        }
    }
}

/// Duplicate data into the module instance memory.
/// Returns the app offset of the duplicated data, or 0 on failure.
pub fn module_dup_data(instance: &WamrInstance, data: &[u8]) -> Result<u64, WamrError> {
    if instance.handle.is_null() || data.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    let app_offset = unsafe {
        bindings::wasm_runtime_module_dup_data(
            instance.handle,
            data.as_ptr() as *const c_char,
            data.len() as u64,
        )
    };

    if app_offset == 0 {
        set_last_error("Module dup data failed: insufficient memory".to_string());
        return Err(WamrError::MemoryGrowthFailed);
    }

    Ok(app_offset)
}

/// Validate an application address range
pub fn validate_app_addr(instance: &WamrInstance, app_offset: u64, size: u64) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe {
        bindings::wasm_runtime_validate_app_addr(instance.handle, app_offset, size)
    }
}

/// Validate an application string address (null-terminated)
pub fn validate_app_str_addr(instance: &WamrInstance, app_str_offset: u64) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe {
        bindings::wasm_runtime_validate_app_str_addr(instance.handle, app_str_offset)
    }
}

/// Validate a native address range
pub fn validate_native_addr(instance: &WamrInstance, native_ptr: *mut c_void, size: u64) -> bool {
    if instance.handle.is_null() || native_ptr.is_null() {
        return false;
    }
    unsafe {
        bindings::wasm_runtime_validate_native_addr(instance.handle, native_ptr, size)
    }
}

/// Convert application offset to native pointer
pub fn addr_app_to_native(instance: &WamrInstance, app_offset: u64) -> *mut c_void {
    if instance.handle.is_null() {
        return std::ptr::null_mut();
    }
    unsafe {
        bindings::wasm_runtime_addr_app_to_native(instance.handle, app_offset)
    }
}

/// Convert native pointer to application offset
pub fn addr_native_to_app(instance: &WamrInstance, native_ptr: *mut c_void) -> u64 {
    if instance.handle.is_null() || native_ptr.is_null() {
        return 0;
    }
    unsafe {
        bindings::wasm_runtime_addr_native_to_app(instance.handle, native_ptr)
    }
}

/// Get memory instance by index (0-based)
pub fn memory_get_by_index(instance: &WamrInstance, index: u32) -> Result<WamrMemory, WamrError> {
    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    let memory_handle = unsafe {
        bindings::wasm_runtime_get_memory(instance.handle, index)
    };

    if memory_handle.is_null() {
        set_last_error(format!("Memory at index {} not found", index));
        return Err(WamrError::MemoryAccessViolation);
    }

    // Get size via app addr range (same approach as memory_get)
    let mut start_offset: u64 = 0;
    let mut end_offset: u64 = 0;
    let range_valid = unsafe {
        bindings::wasm_runtime_get_app_addr_range(
            instance.handle, 0, &mut start_offset, &mut end_offset,
        )
    };
    let memory_size = if range_valid { end_offset as usize } else { 0 };

    let data_ptr = unsafe {
        bindings::wasm_runtime_addr_app_to_native(instance.handle, 0)
    };

    Ok(WamrMemory {
        instance_handle: instance.handle,
        memory_handle,
        size: memory_size,
        data_ptr: data_ptr as *mut u8,
    })
}

/// Get the base address of a memory instance
pub fn memory_base_address(memory: &WamrMemory) -> *mut c_void {
    if memory.memory_handle.is_null() {
        return std::ptr::null_mut();
    }
    unsafe { bindings::wasm_memory_get_base_address(memory.memory_handle) }
}

/// Get bytes per page for a memory instance
pub fn memory_bytes_per_page(memory: &WamrMemory) -> u64 {
    if memory.memory_handle.is_null() {
        return WASM_PAGE_SIZE as u64;
    }
    unsafe { bindings::wasm_memory_get_bytes_per_page(memory.memory_handle) }
}

/// Enlarge a specific memory instance by pages
pub fn memory_enlarge(memory: &mut WamrMemory, inc_pages: u64) -> bool {
    if memory.memory_handle.is_null() {
        return false;
    }
    let success = unsafe {
        bindings::wasm_memory_enlarge(memory.memory_handle, inc_pages)
    };

    if success {
        // Update cached size after enlargement
        let mut start_offset: u64 = 0;
        let mut end_offset: u64 = 0;
        let range_valid = unsafe {
            bindings::wasm_runtime_get_app_addr_range(
                memory.instance_handle, 0, &mut start_offset, &mut end_offset,
            )
        };
        if range_valid {
            memory.size = end_offset as usize;
        }
        let new_ptr = unsafe {
            bindings::wasm_runtime_addr_app_to_native(memory.instance_handle, 0)
        };
        if !new_ptr.is_null() {
            memory.data_ptr = new_ptr as *mut u8;
        }
    }

    success
}

// =============================================================================
// Table Operations
// =============================================================================

/// Get an exported table by name from an instance
pub fn table_get(instance: &WamrInstance, name: &str) -> Result<WamrTable, WamrError> {
    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }
    if name.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    let c_name = match CString::new(name) {
        Ok(s) => s,
        Err(_) => return Err(WamrError::InvalidArguments),
    };

    let mut table_inst: bindings::wasm_table_inst_t = unsafe { std::mem::zeroed() };

    let found = unsafe {
        bindings::wasm_runtime_get_export_table_inst(
            instance.handle,
            c_name.as_ptr(),
            &mut table_inst,
        )
    };

    if !found {
        set_last_error(format!("Table '{}' not found", name));
        return Err(WamrError::TableNotFound);
    }

    Ok(WamrTable {
        instance_handle: instance.handle,
        exec_env: instance.exec_env,
        table_inst,
        name: name.to_string(),
    })
}

/// Get a function from a table at the given index
pub fn table_get_function(
    table: &WamrTable,
    index: u32,
) -> Result<WamrFunction, WamrError> {
    if table.instance_handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    if index >= table.table_inst.cur_size {
        set_last_error(format!(
            "Table index {} out of bounds (size {})", index, table.table_inst.cur_size
        ));
        return Err(WamrError::TableIndexOutOfBounds);
    }

    let func_inst = unsafe {
        bindings::wasm_table_get_func_inst(
            table.instance_handle,
            &table.table_inst,
            index,
        )
    };

    if func_inst.is_null() {
        set_last_error(format!("No function at table index {}", index));
        return Err(WamrError::FunctionNotFound);
    }

    // Get function signature via introspection
    let param_count = unsafe {
        bindings::wasm_func_get_param_count(func_inst, table.instance_handle)
    };
    let mut raw_param_types = vec![0u8; param_count as usize];
    if param_count > 0 {
        unsafe {
            bindings::wasm_func_get_param_types(
                func_inst, table.instance_handle, raw_param_types.as_mut_ptr());
        }
    }
    let param_types: Vec<WasmType> = raw_param_types.iter()
        .map(|&t| wasm_valkind_to_type(t))
        .collect();

    let result_count = unsafe {
        bindings::wasm_func_get_result_count(func_inst, table.instance_handle)
    };
    let mut raw_result_types = vec![0u8; result_count as usize];
    if result_count > 0 {
        unsafe {
            bindings::wasm_func_get_result_types(
                func_inst, table.instance_handle, raw_result_types.as_mut_ptr());
        }
    }
    let result_types: Vec<WasmType> = raw_result_types.iter()
        .map(|&t| wasm_valkind_to_type(t))
        .collect();

    Ok(WamrFunction {
        handle: func_inst,
        instance_handle: table.instance_handle,
        exec_env: table.exec_env,
        name: format!("table[{}]", index),
        param_types,
        result_types,
    })
}

/// Pack WasmValue arguments into u32 cell vector for the argv-based WAMR APIs.
fn pack_args_to_cells(args: &[WasmValue]) -> Vec<u32> {
    let mut cells = Vec::new();
    for arg in args {
        match arg {
            WasmValue::I32(v) => cells.push(*v as u32),
            WasmValue::I64(v) => {
                cells.push(*v as u32);
                cells.push((*v >> 32) as u32);
            }
            WasmValue::F32(v) => cells.push(v.to_bits()),
            WasmValue::F64(v) => {
                let bits = v.to_bits();
                cells.push(bits as u32);
                cells.push((bits >> 32) as u32);
            }
        }
    }
    cells
}

/// Unpack u32 cell vector into WasmValue results.
fn unpack_results_from_cells(cells: &[u32], result_types: &[WasmType]) -> Vec<WasmValue> {
    let mut results = Vec::with_capacity(result_types.len());
    let mut idx = 0;
    for result_type in result_types {
        if idx >= cells.len() {
            break;
        }
        match result_type {
            WasmType::I32 => {
                results.push(WasmValue::I32(cells[idx] as i32));
                idx += 1;
            }
            WasmType::I64 => {
                let low = cells[idx];
                let high = if idx + 1 < cells.len() { cells[idx + 1] } else { 0 };
                results.push(WasmValue::I64((low as i64) | ((high as i64) << 32)));
                idx += 2;
            }
            WasmType::F32 => {
                results.push(WasmValue::F32(f32::from_bits(cells[idx])));
                idx += 1;
            }
            WasmType::F64 => {
                let low = cells[idx];
                let high = if idx + 1 < cells.len() { cells[idx + 1] } else { 0 };
                let bits = (low as u64) | ((high as u64) << 32);
                results.push(WasmValue::F64(f64::from_bits(bits)));
                idx += 2;
            }
        }
    }
    results
}

/// Call a function indirectly via table index.
/// Uses the u32 argv API since WAMR's call_indirect has no typed variant.
pub fn call_indirect(
    instance: &WamrInstance,
    element_index: u32,
    args: &[WasmValue],
    result_types: &[WasmType],
) -> Result<Vec<WasmValue>, WamrError> {
    if instance.handle.is_null() || instance.exec_env.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    let result_cells: usize = result_types.iter().map(|t| match t {
        WasmType::I32 | WasmType::F32 => 1,
        WasmType::I64 | WasmType::F64 => 2,
    }).sum();

    let mut wamr_args = pack_args_to_cells(args);
    let argc = wamr_args.len();
    if wamr_args.len() < result_cells {
        wamr_args.resize(result_cells, 0);
    }

    let success = unsafe {
        bindings::wasm_runtime_call_indirect(
            instance.exec_env,
            element_index,
            argc as u32,
            wamr_args.as_mut_ptr(),
        )
    };

    if !success {
        let error_msg = get_and_clear_exception(instance.handle);
        set_last_error(error_msg.clone());
        return Err(WamrError::ExecutionFailed(error_msg));
    }

    Ok(unpack_results_from_cells(&wamr_args, result_types))
}

// =============================================================================
// Export Enumeration
// =============================================================================

/// Get names of exports filtered by kind from a module instance
pub fn get_export_names_by_kind(
    instance_handle: *mut bindings::WasmModuleInstT,
    kind: u32,
) -> Vec<String> {
    let module = unsafe { bindings::wasm_runtime_get_module(instance_handle) };
    if module.is_null() {
        return Vec::new();
    }

    let export_count = unsafe { bindings::wasm_runtime_get_export_count(module) };
    let mut names = Vec::new();

    for i in 0..export_count {
        let mut export_info: bindings::wasm_export_t = unsafe { std::mem::zeroed() };
        unsafe { bindings::wasm_runtime_get_export_type(module, i as i32, &mut export_info) };
        if export_info.kind == kind && !export_info.name.is_null() {
            if let Ok(name) = unsafe { CStr::from_ptr(export_info.name).to_str() } {
                names.push(name.to_string());
            }
        }
    }

    names
}

// =============================================================================
// Global Variable Operations
// =============================================================================

/// Get a global variable value from a module instance
pub fn global_get(
    instance_handle: *mut bindings::WasmModuleInstT,
    name: *const c_char,
) -> Result<WasmValue, WamrError> {
    let mut global_inst: bindings::wasm_global_inst_t = unsafe { std::mem::zeroed() };

    let found = unsafe {
        bindings::wasm_runtime_get_export_global_inst(
            instance_handle, name, &mut global_inst,
        )
    };

    if !found {
        return Err(WamrError::FunctionNotFound);
    }

    if global_inst.global_data.is_null() {
        return Err(WamrError::MemoryAccessViolation);
    }

    unsafe {
        match global_inst.kind {
            bindings::WASM_I32 => Ok(WasmValue::I32(*(global_inst.global_data as *const i32))),
            bindings::WASM_I64 => Ok(WasmValue::I64(*(global_inst.global_data as *const i64))),
            bindings::WASM_F32 => Ok(WasmValue::F32(*(global_inst.global_data as *const f32))),
            bindings::WASM_F64 => Ok(WasmValue::F64(*(global_inst.global_data as *const f64))),
            _ => Err(WamrError::InvalidArguments),
        }
    }
}

/// Set a global variable value on a module instance
pub fn global_set(
    instance_handle: *mut bindings::WasmModuleInstT,
    name: *const c_char,
    value: &WasmValue,
) -> Result<(), WamrError> {
    let mut global_inst: bindings::wasm_global_inst_t = unsafe { std::mem::zeroed() };

    let found = unsafe {
        bindings::wasm_runtime_get_export_global_inst(
            instance_handle, name, &mut global_inst,
        )
    };

    if !found {
        return Err(WamrError::FunctionNotFound);
    }

    if !global_inst.is_mutable {
        return Err(WamrError::InvalidArguments);
    }

    if global_inst.global_data.is_null() {
        return Err(WamrError::MemoryAccessViolation);
    }

    unsafe {
        match value {
            WasmValue::I32(v) => *(global_inst.global_data as *mut i32) = *v,
            WasmValue::I64(v) => *(global_inst.global_data as *mut i64) = *v,
            WasmValue::F32(v) => *(global_inst.global_data as *mut f32) = *v,
            WasmValue::F64(v) => *(global_inst.global_data as *mut f64) = *v,
        }
    }

    Ok(())
}

// =============================================================================
// Module-Level Export/Import Enumeration
// =============================================================================

/// Get all export names from a compiled module (no instance needed)
pub fn module_get_export_names(
    module_handle: *mut bindings::WasmModuleT,
) -> Vec<String> {
    if module_handle.is_null() {
        return Vec::new();
    }

    let export_count = unsafe { bindings::wasm_runtime_get_export_count(module_handle) };
    let mut names = Vec::new();

    for i in 0..export_count {
        let mut export_info: bindings::wasm_export_t = unsafe { std::mem::zeroed() };
        unsafe { bindings::wasm_runtime_get_export_type(module_handle, i as i32, &mut export_info) };
        if !export_info.name.is_null() {
            if let Ok(name) = unsafe { CStr::from_ptr(export_info.name).to_str() } {
                names.push(name.to_string());
            }
        }
    }

    names
}

/// Get all import names from a compiled module (no instance needed)
pub fn module_get_import_names(
    module_handle: *mut bindings::WasmModuleT,
) -> Vec<String> {
    if module_handle.is_null() {
        return Vec::new();
    }

    let import_count = unsafe { bindings::wasm_runtime_get_import_count(module_handle) };
    let mut names = Vec::new();

    for i in 0..import_count {
        let mut import_info: bindings::wasm_import_t = unsafe { std::mem::zeroed() };
        unsafe { bindings::wasm_runtime_get_import_type(module_handle, i as i32, &mut import_info) };
        if !import_info.name.is_null() {
            if let Ok(name) = unsafe { CStr::from_ptr(import_info.name).to_str() } {
                if !import_info.module_name.is_null() {
                    if let Ok(module_name) = unsafe { CStr::from_ptr(import_info.module_name).to_str() } {
                        names.push(format!("{}.{}", module_name, name));
                        continue;
                    }
                }
                names.push(name.to_string());
            }
        }
    }

    names
}

/// Get the function signature for a named export from a compiled module (no instance needed)
pub fn module_get_export_function_signature(
    module_handle: *mut bindings::WasmModuleT,
    name: &str,
) -> Option<(Vec<WasmType>, Vec<WasmType>)> {
    if module_handle.is_null() {
        return None;
    }

    let export_count = unsafe { bindings::wasm_runtime_get_export_count(module_handle) };

    for i in 0..export_count {
        let mut export_info: bindings::wasm_export_t = unsafe { std::mem::zeroed() };
        unsafe { bindings::wasm_runtime_get_export_type(module_handle, i as i32, &mut export_info) };

        if export_info.kind != bindings::WASM_IMPORT_EXPORT_KIND_FUNC {
            continue;
        }
        if export_info.name.is_null() {
            continue;
        }

        let export_name = match unsafe { CStr::from_ptr(export_info.name).to_str() } {
            Ok(s) => s,
            Err(_) => continue,
        };
        if export_name != name {
            continue;
        }

        let func_type = export_info.type_ptr as *const bindings::WasmFuncTypeT;
        if func_type.is_null() {
            return None;
        }

        let param_count = unsafe { bindings::wasm_func_type_get_param_count(func_type) };
        let result_count = unsafe { bindings::wasm_func_type_get_result_count(func_type) };

        let param_types: Vec<WasmType> = (0..param_count)
            .map(|j| wasm_valkind_to_type(unsafe { bindings::wasm_func_type_get_param_valkind(func_type, j) }))
            .collect();

        let result_types: Vec<WasmType> = (0..result_count)
            .map(|j| wasm_valkind_to_type(unsafe { bindings::wasm_func_type_get_result_valkind(func_type, j) }))
            .collect();

        return Some((param_types, result_types));
    }

    None
}

// =============================================================================
// Helper Functions
// =============================================================================

/// Convert WAMR value kind byte to WasmType
fn wasm_valkind_to_type(kind: u8) -> WasmType {
    match kind {
        bindings::WASM_I32 => WasmType::I32,
        bindings::WASM_I64 => WasmType::I64,
        bindings::WASM_F32 => WasmType::F32,
        bindings::WASM_F64 => WasmType::F64,
        _ => WasmType::I32, // fallback for unknown types
    }
}

// =============================================================================
// Host Function Registration
// =============================================================================

/// The single raw trampoline function registered with WAMR for all host functions.
/// WAMR calls this with (exec_env, args). We retrieve the HostFunctionContext
/// from the attachment and delegate to the stored callback.
unsafe extern "C" fn host_function_trampoline(
    exec_env: *mut bindings::WasmExecEnvT,
    args: *mut u64,
) {
    let attachment = bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        return;
    }

    let ctx = &*(attachment as *const HostFunctionContext);
    let mut result: u64 = 0;

    let rc = (ctx.callback_fn)(
        ctx.user_data,
        args as *const u64,
        ctx.param_count,
        &mut result,
    );

    if rc == 0 && ctx.result_count > 0 {
        // Write result back to args[0] (WAMR raw convention)
        *args = result;
    }
}

/// Build a WAMR signature string from param/result type arrays.
/// Format: "(paramtypes)resulttypes" where i=i32, I=i64, f=f32, F=f64.
fn build_wamr_signature(param_types: &[u8], result_types: &[u8]) -> String {
    let mut sig = String::with_capacity(param_types.len() + result_types.len() + 3);
    sig.push('(');
    for &t in param_types {
        sig.push(match t {
            0 => 'i', // I32
            1 => 'I', // I64
            2 => 'f', // F32
            3 => 'F', // F64
            _ => 'i',
        });
    }
    sig.push(')');
    for &t in result_types {
        sig.push(match t {
            0 => 'i',
            1 => 'I',
            2 => 'f',
            3 => 'F',
            _ => 'i',
        });
    }
    sig
}

/// Register a batch of host functions for a module name.
///
/// Each function is described by: name, param_types, result_types, callback_fn, user_data.
/// Returns a NativeRegistration that unregisters on drop.
pub fn register_host_functions(
    module_name: &str,
    functions: Vec<(String, Vec<u8>, Vec<u8>, HostCallbackFn, *mut c_void)>,
) -> Result<NativeRegistration, WamrError> {
    if module_name.is_empty() || functions.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    let c_module_name = CString::new(module_name)
        .map_err(|_| WamrError::InvalidArguments)?;

    let mut symbol_names = Vec::with_capacity(functions.len());
    let mut signatures = Vec::with_capacity(functions.len());
    let mut contexts = Vec::with_capacity(functions.len());
    let mut symbols = Vec::with_capacity(functions.len());

    for (name, param_types, result_types, callback_fn, user_data) in &functions {
        let c_name = CString::new(name.as_str())
            .map_err(|_| WamrError::InvalidArguments)?;
        let sig_str = build_wamr_signature(param_types, result_types);
        let c_sig = CString::new(sig_str)
            .map_err(|_| WamrError::InvalidArguments)?;

        let ctx = Box::new(HostFunctionContext {
            param_count: param_types.len() as u32,
            result_count: result_types.len() as u32,
            callback_fn: *callback_fn,
            user_data: *user_data,
        });

        let ctx_ptr = &*ctx as *const HostFunctionContext as *mut c_void;

        symbols.push(bindings::NativeSymbol {
            symbol: c_name.as_ptr(),
            func_ptr: host_function_trampoline as *mut c_void,
            signature: c_sig.as_ptr(),
            attachment: ctx_ptr,
        });

        symbol_names.push(c_name);
        signatures.push(c_sig);
        contexts.push(ctx);
    }

    let ok = unsafe {
        bindings::wasm_runtime_register_natives_raw(
            c_module_name.as_ptr(),
            symbols.as_mut_ptr(),
            symbols.len() as u32,
        )
    };

    if !ok {
        set_last_error("Failed to register native functions".to_string());
        return Err(WamrError::RuntimeCreationFailed);
    }

    Ok(NativeRegistration {
        module_name: c_module_name,
        symbols,
        symbol_names,
        signatures,
        contexts,
    })
}

// =============================================================================
// Exception & Execution Control
// =============================================================================

/// Get the current exception string on a module instance, if any.
pub fn instance_get_exception(instance: &WamrInstance) -> Option<String> {
    if instance.handle.is_null() {
        return None;
    }
    unsafe {
        let exception_ptr = bindings::wasm_runtime_get_exception(instance.handle);
        if exception_ptr.is_null() {
            None
        } else {
            Some(CStr::from_ptr(exception_ptr).to_string_lossy().into_owned())
        }
    }
}

/// Set a custom exception on a module instance.
pub fn instance_set_exception(instance: &WamrInstance, exception: &str) -> Result<(), WamrError> {
    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }
    let c_exception = CString::new(exception)
        .map_err(|_| WamrError::InvalidArguments)?;
    unsafe {
        bindings::wasm_runtime_set_exception(instance.handle, c_exception.as_ptr());
    }
    Ok(())
}

/// Clear the current exception on a module instance.
pub fn instance_clear_exception(instance: &WamrInstance) {
    if instance.handle.is_null() {
        return;
    }
    unsafe {
        bindings::wasm_runtime_clear_exception(instance.handle);
    }
}

/// Terminate execution of a module instance.
/// Can be called from another thread to interrupt long-running execution.
pub fn instance_terminate(instance: &WamrInstance) {
    if instance.handle.is_null() {
        return;
    }
    unsafe {
        bindings::wasm_runtime_terminate(instance.handle);
    }
}

/// Set the instruction count limit for an execution environment.
/// When the limit is reached, execution will trap.
/// Pass -1 to remove the limit.
pub fn set_instruction_count_limit(instance: &WamrInstance, limit: i32) {
    if instance.exec_env.is_null() {
        return;
    }
    unsafe {
        bindings::wasm_runtime_set_instruction_count_limit(instance.exec_env, limit);
    }
}

// =============================================================================
// WASI Support
// =============================================================================

/// Configure WASI arguments on a module before instantiation.
pub fn module_set_wasi_args(
    module: &WamrModule,
    dir_list: &[&str],
    map_dir_list: &[&str],
    env_vars: &[&str],
    argv: &[&str],
) -> Result<(), WamrError> {
    if !module.is_valid() {
        return Err(WamrError::CompilationFailed);
    }

    // WAMR's wasm_runtime_set_wasi_args stores raw pointers without copying.
    // We must keep the CStrings alive for the lifetime of the module.
    let c_dirs: Vec<CString> = dir_list.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let c_map_dirs: Vec<CString> = map_dir_list.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let c_envs: Vec<CString> = env_vars.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let c_argv: Vec<CString> = argv.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();

    let dir_ptrs: Vec<*const c_char> = c_dirs.iter().map(|s| s.as_ptr()).collect();
    let map_dir_ptrs: Vec<*const c_char> = c_map_dirs.iter().map(|s| s.as_ptr()).collect();
    let env_ptrs: Vec<*const c_char> = c_envs.iter().map(|s| s.as_ptr()).collect();
    let argv_ptrs: Vec<*const c_char> = c_argv.iter().map(|s| s.as_ptr()).collect();

    unsafe {
        bindings::wasm_runtime_set_wasi_args(
            module.handle,
            if dir_ptrs.is_empty() { std::ptr::null() } else { dir_ptrs.as_ptr() },
            dir_ptrs.len() as u32,
            if map_dir_ptrs.is_empty() { std::ptr::null() } else { map_dir_ptrs.as_ptr() },
            map_dir_ptrs.len() as u32,
            if env_ptrs.is_empty() { std::ptr::null() } else { env_ptrs.as_ptr() },
            env_ptrs.len() as u32,
            if argv_ptrs.is_empty() { std::ptr::null() } else { argv_ptrs.as_ptr() },
            argv_ptrs.len() as i32,
        );
    }

    // Store all CStrings and pointer arrays in the module so they outlive
    // the WAMR pointers. Pointer arrays must also be kept alive since WAMR
    // stores the array pointer itself (not the individual string pointers).
    let mut all_strings = Vec::new();
    all_strings.extend(c_dirs);
    all_strings.extend(c_map_dirs);
    all_strings.extend(c_envs);
    all_strings.extend(c_argv);

    let wasi_strings = WasiStrings {
        strings: all_strings,
        ptr_arrays: vec![dir_ptrs, map_dir_ptrs, env_ptrs, argv_ptrs],
    };

    if let Ok(mut guard) = module.wasi_strings.lock() {
        *guard = Some(wasi_strings);
    }

    Ok(())
}

/// Configure WASI address pool on a module.
pub fn module_set_wasi_addr_pool(
    module: &WamrModule,
    addr_pool: &[&str],
) -> Result<(), WamrError> {
    if !module.is_valid() {
        return Err(WamrError::CompilationFailed);
    }

    let c_addrs: Vec<CString> = addr_pool.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let addr_ptrs: Vec<*const c_char> = c_addrs.iter().map(|s| s.as_ptr()).collect();

    unsafe {
        bindings::wasm_runtime_set_wasi_addr_pool(
            module.handle,
            if addr_ptrs.is_empty() { std::ptr::null() } else { addr_ptrs.as_ptr() },
            addr_ptrs.len() as u32,
        );
    }

    Ok(())
}

/// Configure WASI NS lookup pool on a module.
pub fn module_set_wasi_ns_lookup_pool(
    module: &WamrModule,
    ns_lookup_pool: &[&str],
) -> Result<(), WamrError> {
    if !module.is_valid() {
        return Err(WamrError::CompilationFailed);
    }

    let c_ns: Vec<CString> = ns_lookup_pool.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let ns_ptrs: Vec<*const c_char> = c_ns.iter().map(|s| s.as_ptr()).collect();

    unsafe {
        bindings::wasm_runtime_set_wasi_ns_lookup_pool(
            module.handle,
            if ns_ptrs.is_empty() { std::ptr::null() } else { ns_ptrs.as_ptr() },
            ns_ptrs.len() as u32,
        );
    }

    Ok(())
}

/// Check if a module instance is running in WASI mode.
pub fn instance_is_wasi_mode(instance: &WamrInstance) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe { bindings::wasm_runtime_is_wasi_mode(instance.handle) }
}

/// Get the WASI exit code from a module instance.
pub fn instance_get_wasi_exit_code(instance: &WamrInstance) -> u32 {
    if instance.handle.is_null() {
        return 0;
    }
    unsafe { bindings::wasm_runtime_get_wasi_exit_code(instance.handle) }
}

/// Lookup the WASI _start function in a module instance.
pub fn instance_lookup_wasi_start_function(
    instance: &WamrInstance,
) -> *mut bindings::WasmFunctionInstT {
    if instance.handle.is_null() {
        return std::ptr::null_mut();
    }
    unsafe { bindings::wasm_runtime_lookup_wasi_start_function(instance.handle) }
}

/// Execute the WASI _start function.
pub fn instance_execute_main(
    instance: &WamrInstance,
    argv: &[&str],
) -> Result<(), WamrError> {
    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    let mut c_argv: Vec<CString> = argv.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let mut argv_ptrs: Vec<*mut c_char> = c_argv.iter_mut()
        .map(|s| s.as_ptr() as *mut c_char)
        .collect();

    let success = unsafe {
        bindings::wasm_application_execute_main(
            instance.handle,
            argv_ptrs.len() as i32,
            if argv_ptrs.is_empty() { std::ptr::null_mut() } else { argv_ptrs.as_mut_ptr() },
        )
    };

    if !success {
        let msg = get_and_clear_exception(instance.handle);
        set_last_error(msg.clone());
        return Err(WamrError::ExecutionFailed(msg));
    }

    Ok(())
}

/// Execute a named function with string arguments (WASI-style).
pub fn instance_execute_func(
    instance: &WamrInstance,
    name: &str,
    argv: &[&str],
) -> Result<(), WamrError> {
    if instance.handle.is_null() {
        return Err(WamrError::InstantiationFailed);
    }

    let c_name = CString::new(name)
        .map_err(|_| WamrError::InvalidArguments)?;

    let mut c_argv: Vec<CString> = argv.iter()
        .filter_map(|s| CString::new(*s).ok())
        .collect();
    let mut argv_ptrs: Vec<*mut c_char> = c_argv.iter_mut()
        .map(|s| s.as_ptr() as *mut c_char)
        .collect();

    let success = unsafe {
        bindings::wasm_application_execute_func(
            instance.handle,
            c_name.as_ptr(),
            argv_ptrs.len() as i32,
            if argv_ptrs.is_empty() { std::ptr::null_mut() } else { argv_ptrs.as_mut_ptr() },
        )
    };

    if !success {
        let msg = get_and_clear_exception(instance.handle);
        set_last_error(msg.clone());
        return Err(WamrError::ExecutionFailed(msg));
    }

    Ok(())
}

// =============================================================================
// Custom Data
// =============================================================================

/// Set custom data on a module instance.
/// The custom data is an opaque pointer (u64) managed by the caller.
pub fn instance_set_custom_data(instance: &WamrInstance, custom_data: u64) {
    if instance.handle.is_null() {
        return;
    }
    unsafe {
        bindings::wasm_runtime_set_custom_data(
            instance.handle,
            custom_data as *mut c_void,
        );
    }
}

/// Get custom data from a module instance.
/// Returns 0 if no custom data is set or the instance is invalid.
pub fn instance_get_custom_data(instance: &WamrInstance) -> u64 {
    if instance.handle.is_null() {
        return 0;
    }
    unsafe {
        bindings::wasm_runtime_get_custom_data(instance.handle) as u64
    }
}

// =============================================================================
// Debugging & Profiling
// =============================================================================

/// Get the call stack as a string. Returns empty string if unavailable.
pub fn instance_get_call_stack(instance: &WamrInstance) -> String {
    if instance.handle.is_null() {
        return String::new();
    }
    unsafe {
        let exec_env = bindings::wasm_runtime_get_exec_env_singleton(instance.handle);
        if exec_env.is_null() {
            return String::new();
        }
        let buf_size = bindings::wasm_runtime_get_call_stack_buf_size(exec_env);
        if buf_size == 0 {
            return String::new();
        }
        let mut buf = vec![0u8; buf_size as usize];
        let written = bindings::wasm_runtime_dump_call_stack_to_buf(
            exec_env,
            buf.as_mut_ptr() as *mut c_char,
            buf_size,
        );
        if written == 0 {
            return String::new();
        }
        let len = std::cmp::min(written as usize, buf.len());
        String::from_utf8_lossy(&buf[..len]).trim_end_matches('\0').to_string()
    }
}

/// Dump call stack to stdout.
pub fn instance_dump_call_stack(instance: &WamrInstance) {
    if instance.handle.is_null() {
        return;
    }
    unsafe {
        let exec_env = bindings::wasm_runtime_get_exec_env_singleton(instance.handle);
        if !exec_env.is_null() {
            bindings::wasm_runtime_dump_call_stack(exec_env);
        }
    }
}

/// Dump performance profiling data to stdout.
pub fn instance_dump_perf_profiling(instance: &WamrInstance) {
    if instance.handle.is_null() {
        return;
    }
    unsafe {
        bindings::wasm_runtime_dump_perf_profiling(instance.handle);
    }
}

/// Get total WASM execution time in milliseconds.
pub fn instance_sum_wasm_exec_time(instance: &WamrInstance) -> f64 {
    if instance.handle.is_null() {
        return 0.0;
    }
    unsafe {
        bindings::wasm_runtime_sum_wasm_exec_time(instance.handle)
    }
}

/// Get execution time for a specific function in milliseconds.
pub fn instance_get_wasm_func_exec_time(instance: &WamrInstance, func_name: &str) -> f64 {
    if instance.handle.is_null() {
        return 0.0;
    }
    let c_name = match std::ffi::CString::new(func_name) {
        Ok(s) => s,
        Err(_) => return 0.0,
    };
    unsafe {
        bindings::wasm_runtime_get_wasm_func_exec_time(instance.handle, c_name.as_ptr())
    }
}

/// Dump memory consumption to stdout.
pub fn instance_dump_mem_consumption(instance: &WamrInstance) {
    if instance.handle.is_null() {
        return;
    }
    unsafe {
        let exec_env = bindings::wasm_runtime_get_exec_env_singleton(instance.handle);
        if !exec_env.is_null() {
            bindings::wasm_runtime_dump_mem_consumption(exec_env);
        }
    }
}

// =============================================================================
// Threading
// =============================================================================

/// Initialize the thread environment for the current native thread.
pub fn init_thread_env() -> bool {
    unsafe { bindings::wasm_runtime_init_thread_env() }
}

/// Ensure the thread environment is initialized for the current thread.
/// This is a no-op if already initialized, making it safe to call on every invocation.
pub fn ensure_thread_env() {
    if !is_thread_env_inited() {
        init_thread_env();
    }
}

/// Destroy the thread environment for the current native thread.
pub fn destroy_thread_env() {
    unsafe { bindings::wasm_runtime_destroy_thread_env() }
}

/// Check if the thread environment has been initialized.
pub fn is_thread_env_inited() -> bool {
    unsafe { bindings::wasm_runtime_thread_env_inited() }
}

/// Set the maximum number of threads.
pub fn set_max_thread_num(num: u32) {
    unsafe { bindings::wasm_runtime_set_max_thread_num(num) }
}

// =============================================================================
// Advanced Instantiation & Miscellaneous
// =============================================================================

/// Instantiate a module with extended arguments (custom stack/heap sizes).
pub fn instance_create_ex(
    module: &WamrModule,
    default_stack_size: u32,
    host_managed_heap_size: u32,
    max_memory_pages: u32,
) -> Result<Box<WamrInstance>, WamrError> {
    if module.handle.is_null() || !module.is_valid() {
        return Err(WamrError::CompilationFailed);
    }

    // Auto-initialize thread environment for the current thread if needed.
    ensure_thread_env();

    // Resolve import symbols
    let resolved = unsafe { bindings::wasm_runtime_resolve_symbols(module.handle) };
    if !resolved {
        set_last_error("Failed to resolve module import symbols".to_string());
        return Err(WamrError::InstantiationFailed);
    }

    let args = bindings::InstantiationArgs {
        default_stack_size,
        host_managed_heap_size,
        max_memory_pages,
    };
    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    let instance_handle = unsafe {
        bindings::wasm_runtime_instantiate_ex(
            module.handle as *const _,
            &args,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as u32,
        )
    };
    if instance_handle.is_null() {
        let error_msg = unsafe {
            std::ffi::CStr::from_ptr(error_buf.as_ptr() as *const c_char)
                .to_string_lossy().into_owned()
        };
        set_last_error(format!("Instance creation failed: {}", error_msg));
        return Err(WamrError::InstantiationFailed);
    }

    // Create execution environment
    let exec_env = unsafe {
        bindings::wasm_runtime_create_exec_env(instance_handle, default_stack_size)
    };
    if exec_env.is_null() {
        unsafe { bindings::wasm_runtime_deinstantiate(instance_handle); }
        set_last_error("Failed to create execution environment".to_string());
        return Err(WamrError::InstantiationFailed);
    }

    Ok(Box::new(WamrInstance {
        handle: instance_handle,
        exec_env,
        stack_size: default_stack_size as usize,
        heap_size: host_managed_heap_size as usize,
    }))
}

/// Get a custom section from a module by name. Returns the section bytes or empty vec.
pub fn module_get_custom_section(module: &WamrModule, name: &str) -> Vec<u8> {
    if module.handle.is_null() {
        return Vec::new();
    }
    let c_name = match std::ffi::CString::new(name) {
        Ok(s) => s,
        Err(_) => return Vec::new(),
    };
    let mut len: u32 = 0;
    let ptr = unsafe {
        bindings::wasm_runtime_get_custom_section(
            module.handle as *const _,
            c_name.as_ptr(),
            &mut len,
        )
    };
    if ptr.is_null() || len == 0 {
        return Vec::new();
    }
    unsafe { std::slice::from_raw_parts(ptr, len as usize).to_vec() }
}

// =============================================================================
// Phase 14: Type Introspection (Global/Memory types)
// =============================================================================

/// Get the value kind of an exported global by name.
/// Returns (valkind, is_mutable) or None if not found.
pub fn get_export_global_type_info(
    module: &WamrModule,
    name: &str,
) -> Option<(u8, bool)> {
    if module.handle.is_null() {
        return None;
    }
    let module_handle = module.handle as *const bindings::WasmModuleT;
    let export_count = unsafe { bindings::wasm_runtime_get_export_count(module_handle) };
    let c_name = CString::new(name).ok()?;
    let name_cstr = c_name.as_c_str();

    for i in 0..export_count {
        let mut export_info: bindings::wasm_export_t = unsafe { std::mem::zeroed() };
        unsafe { bindings::wasm_runtime_get_export_type(module_handle, i as i32, &mut export_info) };
        if export_info.kind != bindings::WASM_IMPORT_EXPORT_KIND_GLOBAL {
            continue;
        }
        if export_info.name.is_null() {
            continue;
        }
        let export_name = unsafe { CStr::from_ptr(export_info.name) };
        if export_name != name_cstr {
            continue;
        }
        let global_type = export_info.type_ptr as *const bindings::WasmGlobalTypeT;
        if global_type.is_null() {
            return None;
        }
        let valkind = unsafe { bindings::wasm_global_type_get_valkind(global_type) };
        let is_mutable = unsafe { bindings::wasm_global_type_get_mutable(global_type) };
        return Some((valkind, is_mutable));
    }
    None
}

/// Get memory type info for an exported memory by name.
/// Returns (is_shared, init_page_count, max_page_count) or None if not found.
pub fn get_export_memory_type_info(
    module: &WamrModule,
    name: &str,
) -> Option<(bool, u32, u32)> {
    if module.handle.is_null() {
        return None;
    }
    let module_handle = module.handle as *const bindings::WasmModuleT;
    let export_count = unsafe { bindings::wasm_runtime_get_export_count(module_handle) };
    let c_name = CString::new(name).ok()?;
    let name_cstr = c_name.as_c_str();

    for i in 0..export_count {
        let mut export_info: bindings::wasm_export_t = unsafe { std::mem::zeroed() };
        unsafe { bindings::wasm_runtime_get_export_type(module_handle, i as i32, &mut export_info) };
        if export_info.kind != bindings::WASM_IMPORT_EXPORT_KIND_MEMORY {
            continue;
        }
        if export_info.name.is_null() {
            continue;
        }
        let export_name = unsafe { CStr::from_ptr(export_info.name) };
        if export_name != name_cstr {
            continue;
        }
        let memory_type = export_info.type_ptr as *const bindings::WasmMemoryTypeT;
        if memory_type.is_null() {
            return None;
        }
        let is_shared = unsafe { bindings::wasm_memory_type_get_shared(memory_type) };
        let init_pages = unsafe { bindings::wasm_memory_type_get_init_page_count(memory_type) };
        let max_pages = unsafe { bindings::wasm_memory_type_get_max_page_count(memory_type) };
        return Some((is_shared, init_pages, max_pages));
    }
    None
}

// =============================================================================
// Phase 15: Import Link Checking
// =============================================================================

/// Check if an import function is linked.
pub fn is_import_func_linked(module_name: &str, func_name: &str) -> bool {
    let c_module = match CString::new(module_name) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let c_func = match CString::new(func_name) {
        Ok(s) => s,
        Err(_) => return false,
    };
    unsafe {
        bindings::wasm_runtime_is_import_func_linked(c_module.as_ptr(), c_func.as_ptr())
    }
}

/// Check if an import global is linked.
pub fn is_import_global_linked(module_name: &str, global_name: &str) -> bool {
    let c_module = match CString::new(module_name) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let c_global = match CString::new(global_name) {
        Ok(s) => s,
        Err(_) => return false,
    };
    unsafe {
        bindings::wasm_runtime_is_import_global_linked(c_module.as_ptr(), c_global.as_ptr())
    }
}

// =============================================================================
// Phase 16: Exec Env & Memory Lookup
// =============================================================================

/// Lookup a memory instance by export name.
pub fn instance_lookup_memory(
    instance: &WamrInstance,
    name: &str,
) -> *mut bindings::WasmMemoryInstT {
    if instance.handle.is_null() {
        return std::ptr::null_mut();
    }
    let c_name = match CString::new(name) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    unsafe {
        bindings::wasm_runtime_lookup_memory(instance.handle as *const _, c_name.as_ptr())
    }
}

// =============================================================================
// Phase 17: Blocking Operations & Stack Overflow Detection
// =============================================================================

/// Begin a blocking operation.
pub fn instance_begin_blocking_op(instance: &WamrInstance) -> bool {
    if instance.exec_env.is_null() {
        return false;
    }
    unsafe { bindings::wasm_runtime_begin_blocking_op(instance.exec_env) }
}

/// End a blocking operation.
pub fn instance_end_blocking_op(instance: &WamrInstance) {
    if instance.exec_env.is_null() {
        return;
    }
    unsafe { bindings::wasm_runtime_end_blocking_op(instance.exec_env) }
}

/// Detect native stack overflow.
pub fn instance_detect_native_stack_overflow(instance: &WamrInstance) -> bool {
    if instance.exec_env.is_null() {
        return true; // Defensive: report overflow if no env
    }
    unsafe { bindings::wasm_runtime_detect_native_stack_overflow(instance.exec_env) }
}

/// Detect native stack overflow with required size.
pub fn instance_detect_native_stack_overflow_size(
    instance: &WamrInstance,
    required_size: u32,
) -> bool {
    if instance.exec_env.is_null() {
        return true;
    }
    unsafe {
        bindings::wasm_runtime_detect_native_stack_overflow_size(instance.exec_env, required_size)
    }
}

/// Check if WebAssembly header is valid
fn is_valid_wasm_header(bytes: &[u8]) -> bool {
    if bytes.len() < 8 {
        return false;
    }
    
    // Check WASM magic number (0x00, 0x61, 0x73, 0x6D)
    if &bytes[0..4] != &[0x00, 0x61, 0x73, 0x6D] {
        return false;
    }
    
    // Check version (0x01, 0x00, 0x00, 0x00 for version 1)
    if &bytes[4..8] != &[0x01, 0x00, 0x00, 0x00] {
        return false;
    }

    true
}

// =============================================================================
// Phase 18: Runtime Init & Mem Info
// =============================================================================

/// Get memory allocation info from the runtime.
pub fn get_mem_alloc_info() -> Option<(u32, u32, u32)> {
    let mut info: bindings::MemAllocInfo = Default::default();
    let ok = unsafe { bindings::wasm_runtime_get_mem_alloc_info(&mut info) };
    if ok {
        Some((info.total_size, info.total_free_size, info.highmark_size))
    } else {
        None
    }
}

// =============================================================================
// Phase 21: Externref
// =============================================================================

/// Map an external object to an externref index.
pub fn externref_obj2ref(
    instance: &WamrInstance,
    extern_obj: *mut std::os::raw::c_void,
) -> Option<u32> {
    if instance.handle.is_null() {
        return None;
    }
    let mut idx: u32 = 0;
    let ok = unsafe {
        bindings::wasm_externref_obj2ref(instance.handle as *const _, extern_obj, &mut idx)
    };
    if ok { Some(idx) } else { None }
}

/// Delete an externref mapping.
pub fn externref_objdel(
    instance: &WamrInstance,
    extern_obj: *mut std::os::raw::c_void,
) -> bool {
    if instance.handle.is_null() {
        return false;
    }
    unsafe { bindings::wasm_externref_objdel(instance.handle as *const _, extern_obj) }
}

/// Get the external object from an externref index.
pub fn externref_ref2obj(externref_idx: u32) -> Option<*mut std::os::raw::c_void> {
    let mut obj: *mut std::os::raw::c_void = std::ptr::null_mut();
    let ok = unsafe { bindings::wasm_externref_ref2obj(externref_idx, &mut obj) };
    if ok { Some(obj) } else { None }
}

/// Retain an externref to prevent cleanup.
pub fn externref_retain(externref_idx: u32) -> bool {
    unsafe { bindings::wasm_externref_retain(externref_idx) }
}

// =============================================================================
// Phase 22: Module Instance Context
// =============================================================================

/// Create a context key (without destructor).
pub fn create_context_key() -> *mut std::os::raw::c_void {
    unsafe { bindings::wasm_runtime_create_context_key(None) }
}

/// Destroy a context key.
pub fn destroy_context_key(key: *mut std::os::raw::c_void) {
    if !key.is_null() {
        unsafe { bindings::wasm_runtime_destroy_context_key(key) }
    }
}

/// Set context on an instance.
pub fn set_context(
    instance: &WamrInstance,
    key: *mut std::os::raw::c_void,
    ctx: *mut std::os::raw::c_void,
) {
    if !instance.handle.is_null() && !key.is_null() {
        unsafe {
            bindings::wasm_runtime_set_context(instance.handle as *const _, key, ctx)
        }
    }
}

/// Set context and spread to spawned threads.
pub fn set_context_spread(
    instance: &WamrInstance,
    key: *mut std::os::raw::c_void,
    ctx: *mut std::os::raw::c_void,
) {
    if !instance.handle.is_null() && !key.is_null() {
        unsafe {
            bindings::wasm_runtime_set_context_spread(instance.handle as *const _, key, ctx)
        }
    }
}

/// Get context from an instance.
pub fn get_context(
    instance: &WamrInstance,
    key: *mut std::os::raw::c_void,
) -> *mut std::os::raw::c_void {
    if instance.handle.is_null() || key.is_null() {
        return std::ptr::null_mut();
    }
    unsafe { bindings::wasm_runtime_get_context(instance.handle as *const _, key) }
}

// =============================================================================
// Phase 23: Thread Spawning
// =============================================================================

/// Spawn a new exec_env for parallel execution.
pub fn spawn_exec_env(instance: &WamrInstance) -> *mut bindings::WasmExecEnvT {
    if instance.exec_env.is_null() {
        return std::ptr::null_mut();
    }
    unsafe { bindings::wasm_runtime_spawn_exec_env(instance.exec_env) }
}

/// Destroy a spawned exec_env.
pub fn destroy_spawned_exec_env(exec_env: *mut bindings::WasmExecEnvT) {
    if !exec_env.is_null() {
        unsafe { bindings::wasm_runtime_destroy_spawned_exec_env(exec_env) }
    }
}

// =============================================================================
// Phase 25: InstantiationArgs2 API
// =============================================================================

/// Create and instantiate using the opaque InstantiationArgs2 API.
pub fn instance_create_ex2(
    module: &WamrModule,
    default_stack_size: u32,
    host_managed_heap_size: u32,
    max_memory_pages: u32,
) -> Result<Box<WamrInstance>, WamrError> {
    if module.handle.is_null() {
        return Err(WamrError::CompilationFailed);
    }

    // Auto-initialize thread environment for the current thread if needed.
    ensure_thread_env();

    let mut args: *mut bindings::InstantiationArgs2 = std::ptr::null_mut();
    let created = unsafe { bindings::wasm_runtime_instantiation_args_create(&mut args) };
    if !created || args.is_null() {
        set_last_error("Failed to create InstantiationArgs2".to_string());
        return Err(WamrError::InstantiationFailed);
    }

    unsafe {
        bindings::wasm_runtime_instantiation_args_set_default_stack_size(args, default_stack_size);
        bindings::wasm_runtime_instantiation_args_set_host_managed_heap_size(args, host_managed_heap_size);
        bindings::wasm_runtime_instantiation_args_set_max_memory_pages(args, max_memory_pages);
    }

    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    let module_inst = unsafe {
        bindings::wasm_runtime_instantiate_ex2(
            module.handle as *const _,
            args as *const _,
            error_buf.as_mut_ptr() as *mut c_char,
            ERROR_BUF_SIZE as u32,
        )
    };

    unsafe { bindings::wasm_runtime_instantiation_args_destroy(args) };

    if module_inst.is_null() {
        let error_msg = unsafe { CStr::from_ptr(error_buf.as_ptr() as *const c_char) }
            .to_str()
            .unwrap_or("Unknown error")
            .to_string();
        set_last_error(error_msg);
        return Err(WamrError::InstantiationFailed);
    }

    let exec_env = unsafe {
        bindings::wasm_runtime_create_exec_env(
            module_inst,
            default_stack_size,
        )
    };

    if exec_env.is_null() {
        unsafe { bindings::wasm_runtime_deinstantiate(module_inst) };
        set_last_error("Failed to create exec env for ex2 instance".to_string());
        return Err(WamrError::InstantiationFailed);
    }

    Ok(Box::new(WamrInstance {
        handle: module_inst as *mut std::os::raw::c_void,
        exec_env,
        stack_size: default_stack_size as usize,
        heap_size: host_managed_heap_size as usize,
    }))
}

// =============================================================================
// Phase 26: Callstack Frames
// =============================================================================

/// Copy structured callstack frames.
pub fn copy_callstack(
    instance: &WamrInstance,
    skip: u32,
) -> Vec<(u32, u32, u32, String)> {
    if instance.exec_env.is_null() {
        return Vec::new();
    }
    // Allocate space for up to 32 frames
    let mut frames: Vec<bindings::WasmCApiFrame> = Vec::with_capacity(32);
    for _ in 0..32 {
        frames.push(unsafe { std::mem::zeroed() });
    }
    let mut error_buf = [0u8; ERROR_BUF_SIZE];
    let count = unsafe {
        bindings::wasm_copy_callstack(
            instance.exec_env as *const _,
            frames.as_mut_ptr(),
            32,
            skip,
            error_buf.as_mut_ptr() as *mut c_char,
            ERROR_BUF_SIZE as u32,
        )
    };
    let mut result = Vec::new();
    for i in 0..count as usize {
        let frame = &frames[i];
        let name = if frame.func_name_wp.is_null() {
            String::new()
        } else {
            unsafe { CStr::from_ptr(frame.func_name_wp) }
                .to_str()
                .unwrap_or("")
                .to_string()
        };
        result.push((frame.module_offset, frame.func_index, frame.func_offset, name));
    }
    result
}

// =============================================================================
// Shared Heap Operations
// =============================================================================

/// Creates a shared heap with the given initial size.
pub fn shared_heap_create(size: u32) -> *mut std::os::raw::c_void {
    let mut args: bindings::SharedHeapInitArgs = unsafe { std::mem::zeroed() };
    args.size = size;
    unsafe { bindings::wasm_runtime_create_shared_heap(&mut args) }
}

/// Attaches a shared heap to a module instance.
pub fn shared_heap_attach(instance: &WamrInstance, heap: *mut std::os::raw::c_void) -> bool {
    unsafe {
        bindings::wasm_runtime_attach_shared_heap(instance.handle, heap)
    }
}

/// Detaches the shared heap from a module instance.
pub fn shared_heap_detach(instance: &WamrInstance) {
    unsafe {
        bindings::wasm_runtime_detach_shared_heap(instance.handle);
    }
}

/// Chain two shared heaps together.
pub fn shared_heap_chain(head: *mut std::os::raw::c_void, body: *mut std::os::raw::c_void) -> *mut std::os::raw::c_void {
    unsafe { bindings::wasm_runtime_chain_shared_heaps(head, body) }
}

/// Allocates memory from a shared heap.
pub fn shared_heap_malloc(instance: &WamrInstance, size: u64) -> u64 {
    unsafe {
        bindings::wasm_runtime_shared_heap_malloc(
            instance.handle,
            size,
            std::ptr::null_mut(),
        )
    }
}

/// Frees memory from a shared heap.
pub fn shared_heap_free(instance: &WamrInstance, ptr: u64) {
    unsafe {
        bindings::wasm_runtime_shared_heap_free(instance.handle, ptr);
    }
}

// =============================================================================
// Extended WASI
// =============================================================================

/// Sets WASI args with explicit stdio file descriptors.
pub fn module_set_wasi_args_ex(
    module: *mut bindings::WasmModuleT,
    dir_list: &[*const std::os::raw::c_char],
    map_dir_list: &[*const std::os::raw::c_char],
    env: &[*const std::os::raw::c_char],
    argv: &[*const std::os::raw::c_char],
    stdinfd: i64,
    stdoutfd: i64,
    stderrfd: i64,
) {
    unsafe {
        bindings::wasm_runtime_set_wasi_args_ex(
            module,
            dir_list.as_ptr(),
            dir_list.len() as u32,
            map_dir_list.as_ptr(),
            map_dir_list.len() as u32,
            env.as_ptr(),
            env.len() as u32,
            argv.as_ptr(),
            argv.len() as i32,
            stdinfd,
            stdoutfd,
            stderrfd,
        );
    }
}

/// Gets the native address range for a pointer.
pub fn get_native_addr_range(
    instance: &WamrInstance,
    native_ptr: *mut u8,
) -> Option<(*mut u8, *mut u8)> {
    let mut start: *mut u8 = std::ptr::null_mut();
    let mut end: *mut u8 = std::ptr::null_mut();
    let ok = unsafe {
        bindings::wasm_runtime_get_native_addr_range(
            instance.handle,
            native_ptr,
            &mut start,
            &mut end,
        )
    };
    if ok { Some((start, end)) } else { None }
}


