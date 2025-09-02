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

use crate::wamr_wrapper::{WamrRuntime, WamrModule, WamrInstance, WamrFunction, WamrMemory, WamrError, WasmValue, WasmType, RuntimeConfig};
use crate::bindings::{self, WasmRuntimeT, WasmModuleT, WasmModuleInstT, WasmFunctionInstT};
use crate::utils::{set_last_error};
use std::ffi::CString;
use std::ptr;

impl Default for RuntimeConfig {
    fn default() -> Self {
        Self {
            stack_size: 16 * 1024,        // 16KB stack
            heap_size: 16 * 1024 * 1024,  // 16MB heap
            max_thread_num: 4,             // 4 threads
        }
    }
}

// =============================================================================
// Runtime Management
// =============================================================================

/// Initialize WAMR runtime with default configuration
pub fn runtime_init() -> Result<WamrRuntime, WamrError> {
    runtime_init_with_config(&RuntimeConfig::default())
}

/// Initialize WAMR runtime with custom configuration
pub fn runtime_init_with_config(config: &RuntimeConfig) -> Result<WamrRuntime, WamrError> {
    // Validate configuration
    if config.stack_size == 0 {
        return Err(WamrError::InvalidArguments);
    }
    if config.heap_size == 0 {
        return Err(WamrError::InvalidArguments);
    }
    if config.max_thread_num == 0 {
        return Err(WamrError::InvalidArguments);
    }

    // Initialize actual WAMR runtime
    let runtime_handle = unsafe { bindings::wasm_runtime_init() };
    if runtime_handle.is_null() {
        let error_msg = "Failed to initialize WAMR runtime";
        set_last_error(error_msg.to_string());
        return Err(WamrError::RuntimeCreationFailed);
    }
    
    Ok(WamrRuntime { 
        handle: runtime_handle,
        config: config.clone(),
    })
}

/// Check if runtime is valid and properly initialized
pub fn runtime_is_valid(runtime: &WamrRuntime) -> bool {
    !runtime.handle.is_null()
}

/// Get runtime configuration
pub fn runtime_get_config(runtime: &WamrRuntime) -> &RuntimeConfig {
    &runtime.config
}

// =============================================================================
// Module Management
// =============================================================================

/// Compile WebAssembly bytecode into a module
pub fn module_compile(runtime: &WamrRuntime, wasm_bytes: &[u8]) -> Result<WamrModule, WamrError> {
    if wasm_bytes.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    if !runtime_is_valid(runtime) {
        return Err(WamrError::RuntimeCreationFailed);
    }

    // Basic WASM header validation
    if !is_valid_wasm_header(wasm_bytes) {
        return Err(WamrError::CompilationFailed);
    }
    
    // Compile WebAssembly module using WAMR C API
    let mut error_buf = [0u8; 1024];
    let module_handle = unsafe {
        bindings::wasm_runtime_load(
            wasm_bytes.as_ptr(),
            wasm_bytes.len() as u32,
            error_buf.as_mut_ptr() as *mut i8,
            error_buf.len() as u32,
        )
    };
    
    if module_handle.is_null() {
        // Extract error message from buffer
        let error_msg = unsafe {
            let cstr = std::ffi::CStr::from_ptr(error_buf.as_ptr() as *const i8);
            cstr.to_string_lossy().into_owned()
        };
        set_last_error(format!("Module compilation failed: {}", error_msg));
        return Err(WamrError::CompilationFailed);
    }
    
    Ok(WamrModule { 
        handle: module_handle,
        size: wasm_bytes.len(),
    })
}

/// Validate WebAssembly bytecode without compilation
pub fn module_validate(runtime: &WamrRuntime, wasm_bytes: &[u8]) -> Result<(), WamrError> {
    if wasm_bytes.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    if !runtime_is_valid(runtime) {
        return Err(WamrError::RuntimeCreationFailed);
    }

    // Basic WASM header validation
    if !is_valid_wasm_header(wasm_bytes) {
        return Err(WamrError::CompilationFailed);
    }

    // Use WAMR validation if available, otherwise try compilation and cleanup
    let mut error_buf = [0u8; 1024];
    let validation_result = unsafe {
        bindings::wasm_runtime_validate_module(
            wasm_bytes.as_ptr(),
            wasm_bytes.len() as u32,
            error_buf.as_mut_ptr() as *mut i8,
            error_buf.len() as u32,
        )
    };
    
    if validation_result == 0 {
        // Extract error message from buffer
        let error_msg = unsafe {
            let cstr = std::ffi::CStr::from_ptr(error_buf.as_ptr() as *const i8);
            cstr.to_string_lossy().into_owned()
        };
        set_last_error(format!("Module validation failed: {}", error_msg));
        return Err(WamrError::InvalidWasmBytecode);
    }
    
    Ok(())
}

/// Get module size in bytes
pub fn module_get_size(module: &WamrModule) -> usize {
    module.size
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

    // Instantiate WebAssembly module using WAMR C API
    let mut error_buf = [0u8; 1024];
    let instance_handle = unsafe {
        bindings::wasm_runtime_instantiate(
            module.handle,
            stack_size as u32,
            heap_size as u32,
            error_buf.as_mut_ptr() as *mut i8,
            error_buf.len() as u32,
        )
    };
    
    if instance_handle.is_null() {
        // Extract error message from buffer
        let error_msg = unsafe {
            let cstr = std::ffi::CStr::from_ptr(error_buf.as_ptr() as *const i8);
            cstr.to_string_lossy().into_owned()
        };
        set_last_error(format!("Instance creation failed: {}", error_msg));
        return Err(WamrError::InstantiationFailed);
    }
    
    Ok(WamrInstance { 
        handle: instance_handle,
        stack_size,
        heap_size,
    })
}

/// Check if instance is valid and ready for execution
pub fn instance_is_valid(instance: &WamrInstance) -> bool {
    !instance.handle.is_null()
}

/// Get instance stack size
pub fn instance_get_stack_size(instance: &WamrInstance) -> usize {
    instance.stack_size
}

/// Get instance heap size
pub fn instance_get_heap_size(instance: &WamrInstance) -> usize {
    instance.heap_size
}

// =============================================================================
// Function Management
// =============================================================================

/// Look up a function in an instance by name
pub fn function_lookup(instance: &WamrInstance, name: &str) -> Result<WamrFunction, WamrError> {
    if name.is_empty() {
        return Err(WamrError::InvalidArguments);
    }

    if !instance_is_valid(instance) {
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
    
    // Get function signature information
    let mut param_count = 0u32;
    let mut result_count = 0u32;
    let sig_result = unsafe {
        bindings::wasm_runtime_get_function_signature(
            function_handle,
            &mut param_count,
            &mut result_count,
        )
    };
    
    // For now, default to I32 types if signature unavailable
    // TODO: Parse actual signature information from WAMR
    let param_types = if sig_result == 0 {
        vec![WasmType::I32; param_count as usize]
    } else {
        vec![] // No parameters if signature unavailable
    };
    
    let result_types = if sig_result == 0 {
        vec![WasmType::I32; result_count as usize]
    } else {
        vec![] // No results if signature unavailable
    };
    
    Ok(WamrFunction {
        handle: function_handle,
        name: name.to_string(),
        param_types,
        result_types,
    })
}

/// Call a WebAssembly function with arguments
pub fn function_call(
    function: &WamrFunction, 
    args: &[WasmValue]
) -> Result<Vec<WasmValue>, WamrError> {
    // Basic argument validation
    if args.len() != function.param_types.len() {
        return Err(WamrError::FunctionSignatureMismatch);
    }

    // Type checking
    for (arg, expected_type) in args.iter().zip(function.param_types.iter()) {
        if !is_compatible_type(arg, expected_type) {
            return Err(WamrError::FunctionSignatureMismatch);
        }
    }

    if !function.is_valid() {
        return Err(WamrError::FunctionNotFound);
    }

    // Convert arguments to WAMR format (32-bit values)
    let mut wamr_args: Vec<u32> = Vec::with_capacity(args.len());
    for arg in args {
        match arg {
            WasmValue::I32(v) => wamr_args.push(*v as u32),
            WasmValue::I64(v) => {
                // I64 needs to be passed as two 32-bit values
                let bytes = v.to_le_bytes();
                let low = u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]);
                let high = u32::from_le_bytes([bytes[4], bytes[5], bytes[6], bytes[7]]);
                wamr_args.push(low);
                wamr_args.push(high);
            }
            WasmValue::F32(v) => wamr_args.push(v.to_bits()),
            WasmValue::F64(v) => {
                // F64 needs to be passed as two 32-bit values
                let bits = v.to_bits();
                let bytes = bits.to_le_bytes();
                let low = u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]);
                let high = u32::from_le_bytes([bytes[4], bytes[5], bytes[6], bytes[7]]);
                wamr_args.push(low);
                wamr_args.push(high);
            }
        }
    }

    // Call function using WAMR C API
    let call_result = unsafe {
        bindings::wasm_runtime_call_wasm(
            function.handle,
            wamr_args.len() as u32,
            wamr_args.as_mut_ptr(),
        )
    };
    
    if call_result != 0 {
        let error_msg = bindings::get_wamr_error()
            .unwrap_or_else(|| "Function execution failed".to_string());
        set_last_error(error_msg.clone());
        return Err(WamrError::ExecutionFailed(error_msg));
    }

    // Extract results from the argument array
    // WAMR returns results in the same argv array
    let mut results = Vec::with_capacity(function.result_types.len());
    let mut arg_idx = 0;
    
    for result_type in &function.result_types {
        if arg_idx >= wamr_args.len() {
            break;
        }
        
        match result_type {
            WasmType::I32 => {
                results.push(WasmValue::I32(wamr_args[arg_idx] as i32));
                arg_idx += 1;
            }
            WasmType::I64 => {
                if arg_idx + 1 < wamr_args.len() {
                    let low = wamr_args[arg_idx];
                    let high = wamr_args[arg_idx + 1];
                    let bytes = [
                        (low & 0xFF) as u8,
                        ((low >> 8) & 0xFF) as u8,
                        ((low >> 16) & 0xFF) as u8,
                        ((low >> 24) & 0xFF) as u8,
                        (high & 0xFF) as u8,
                        ((high >> 8) & 0xFF) as u8,
                        ((high >> 16) & 0xFF) as u8,
                        ((high >> 24) & 0xFF) as u8,
                    ];
                    let value = i64::from_le_bytes(bytes);
                    results.push(WasmValue::I64(value));
                    arg_idx += 2;
                } else {
                    results.push(WasmValue::I64(0));
                    arg_idx += 1;
                }
            }
            WasmType::F32 => {
                results.push(WasmValue::F32(f32::from_bits(wamr_args[arg_idx])));
                arg_idx += 1;
            }
            WasmType::F64 => {
                if arg_idx + 1 < wamr_args.len() {
                    let low = wamr_args[arg_idx];
                    let high = wamr_args[arg_idx + 1];
                    let bytes = [
                        (low & 0xFF) as u8,
                        ((low >> 8) & 0xFF) as u8,
                        ((low >> 16) & 0xFF) as u8,
                        ((low >> 24) & 0xFF) as u8,
                        (high & 0xFF) as u8,
                        ((high >> 8) & 0xFF) as u8,
                        ((high >> 16) & 0xFF) as u8,
                        ((high >> 24) & 0xFF) as u8,
                    ];
                    let bits = u64::from_le_bytes(bytes);
                    let value = f64::from_bits(bits);
                    results.push(WasmValue::F64(value));
                    arg_idx += 2;
                } else {
                    results.push(WasmValue::F64(0.0));
                    arg_idx += 1;
                }
            }
        }
    }

    Ok(results)
}

/// Get function signature (parameter and result types)
pub fn function_get_signature(function: &WamrFunction) -> Result<(Vec<WasmType>, Vec<WasmType>), WamrError> {
    Ok((function.param_types.clone(), function.result_types.clone()))
}

/// Get function name
pub fn function_get_name(function: &WamrFunction) -> &str {
    &function.name
}

// =============================================================================
// Memory Management
// =============================================================================

/// Get memory from an instance
pub fn memory_get(instance: &WamrInstance) -> Result<WamrMemory, WamrError> {
    if !instance_is_valid(instance) {
        return Err(WamrError::InstantiationFailed);
    }

    // Get memory size from WAMR
    let memory_size = unsafe {
        bindings::wasm_runtime_get_app_heap_size(instance.handle)
    };
    
    if memory_size == 0 {
        return Err(WamrError::MemoryNotFound);
    }
    
    // Get memory data pointer for offset 0
    let data_ptr = unsafe {
        bindings::wasm_runtime_addr_app_to_native(instance.handle, 0)
    };
    
    Ok(WamrMemory {
        instance_handle: instance.handle,
        size: memory_size as usize,
        data_ptr: data_ptr as *mut u8,
    })
}

/// Get memory size in bytes
pub fn memory_size(memory: &WamrMemory) -> usize {
    memory.size
}

/// Get memory data pointer
pub fn memory_data(memory: &WamrMemory) -> *mut u8 {
    memory.data_ptr
}

/// Grow memory by specified pages
pub fn memory_grow(memory: &mut WamrMemory, pages: u32) -> Result<u32, WamrError> {
    let old_pages = memory.size / 65536;
    
    // WAMR doesn't provide direct memory growth API in the basic export
    // This would typically be handled by the WebAssembly runtime itself
    // For now, we simulate the growth by checking if it's valid
    let new_size = memory.size + (pages as usize * 65536);
    
    // Check for overflow or excessive size
    if new_size < memory.size || new_size > (1024 * 1024 * 1024) { // 1GB max
        return Err(WamrError::MemoryGrowthFailed);
    }
    
    // Update size tracking
    memory.size = new_size;
    Ok(old_pages as u32)
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
            offset as u32,
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
            offset as u32,
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

// =============================================================================
// Helper Functions
// =============================================================================

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

/// Check if a WasmValue is compatible with expected type
fn is_compatible_type(value: &WasmValue, expected: &WasmType) -> bool {
    match (value, expected) {
        (WasmValue::I32(_), WasmType::I32) => true,
        (WasmValue::I64(_), WasmType::I64) => true,
        (WasmValue::F32(_), WasmType::F32) => true,
        (WasmValue::F64(_), WasmType::F64) => true,
        _ => false,
    }
}

/// Create a default value for a given type
fn create_default_value(wasm_type: &WasmType) -> WasmValue {
    match wasm_type {
        WasmType::I32 => WasmValue::I32(0),
        WasmType::I64 => WasmValue::I64(0),
        WasmType::F32 => WasmValue::F32(0.0),
        WasmType::F64 => WasmValue::F64(0.0),
    }
}