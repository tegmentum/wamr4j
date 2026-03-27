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

//! Core type definitions shared between JNI and Panama FFI bindings
//!
//! This module provides the fundamental types for WebAssembly runtime
//! operations including runtime handles, module handles, error types,
//! and value representations.

use std::ffi::CString;
use std::fmt;
use std::os::raw::c_char;
use std::ptr;
use std::sync::Mutex;
use crate::bindings::{self, WasmRuntimeT, WasmModuleT, WasmModuleInstT, WasmFunctionInstT, WasmExecEnvT, WasmMemoryInstT, wasm_table_inst_t};

// =============================================================================
// Constants
// =============================================================================

/// WebAssembly page size in bytes (64KB)
pub const WASM_PAGE_SIZE: usize = 65536;

/// Default error buffer size for WAMR C API calls
pub const ERROR_BUF_SIZE: usize = 1024;

/// Default stack size for WebAssembly instances (16KB)
pub const DEFAULT_STACK_SIZE: usize = 16 * 1024;

/// Default heap size for WebAssembly instances (16MB)
pub const DEFAULT_HEAP_SIZE: usize = 16 * 1024 * 1024;

// =============================================================================
// Core Type Definitions
// =============================================================================

/// WebAssembly value types
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum WasmValue {
    I32(i32),
    I64(i64),
    F32(f32),
    F64(f64),
}

/// WebAssembly type enumeration
#[derive(Debug, Clone, PartialEq)]
pub enum WasmType {
    I32,
    I64,
    F32,
    F64,
}

/// Comprehensive error type for WAMR operations
#[derive(Debug, Clone)]
pub enum WamrError {
    RuntimeCreationFailed,
    CompilationFailed,
    InstantiationFailed,
    FunctionNotFound,
    InvalidArguments,
    ExecutionFailed(String),
    MemoryAccessViolation,
    MemoryGrowthFailed,
    InvalidMemoryOffset,
    TableNotFound,
    TableIndexOutOfBounds,
}

impl fmt::Display for WamrError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            WamrError::RuntimeCreationFailed => write!(f, "Failed to create WAMR runtime"),
            WamrError::CompilationFailed => write!(f, "WebAssembly compilation failed"),
            WamrError::InstantiationFailed => write!(f, "WebAssembly instantiation failed"),
            WamrError::FunctionNotFound => write!(f, "WebAssembly function not found"),
            WamrError::InvalidArguments => write!(f, "Invalid function arguments"),
            WamrError::ExecutionFailed(msg) => write!(f, "Function execution failed: {}", msg),
            WamrError::MemoryAccessViolation => write!(f, "Memory access violation"),
            WamrError::MemoryGrowthFailed => write!(f, "Memory growth failed"),
            WamrError::InvalidMemoryOffset => write!(f, "Invalid memory offset"),
            WamrError::TableNotFound => write!(f, "Table not found"),
            WamrError::TableIndexOutOfBounds => write!(f, "Table index out of bounds"),
        }
    }
}

impl std::error::Error for WamrError {}

// =============================================================================
// Handle Structures
// =============================================================================

/// WebAssembly runtime handle
pub struct WamrRuntime {
    /// Handle to actual WAMR runtime instance
    pub handle: *mut WasmRuntimeT,
}

// Safety: WAMR runtime is thread-safe for read operations
unsafe impl Send for WamrRuntime {}
unsafe impl Sync for WamrRuntime {}

/// Holds WASI string data that must outlive the module.
/// WAMR's `wasm_runtime_set_wasi_args` stores raw pointers without copying,
/// so we must keep the CStrings alive until the module is destroyed.
pub struct WasiStrings {
    /// Owned CStrings (kept alive so pointers remain valid)
    #[allow(dead_code)]
    pub strings: Vec<CString>,
    /// Pointer arrays passed to WAMR (point into `strings`)
    #[allow(dead_code)]
    pub ptr_arrays: Vec<Vec<*const c_char>>,
}

// Safety: WasiStrings contains only owned data.
unsafe impl Send for WasiStrings {}
unsafe impl Sync for WasiStrings {}

/// WebAssembly module handle
pub struct WamrModule {
    /// Handle to actual WAMR compiled module
    pub handle: *mut WasmModuleT,
    /// Keeps the WASM binary alive — WAMR stores internal pointers into this buffer
    /// (e.g., export name strings). Must outlive the module handle.
    #[allow(dead_code)]
    pub wasm_bytes: Vec<u8>,
    /// WASI string data kept alive for the lifetime of the module.
    /// Protected by Mutex for interior mutability (configureWasi can be called after creation).
    pub wasi_strings: Mutex<Option<WasiStrings>>,
}

// Safety: WAMR modules are immutable after compilation
unsafe impl Send for WamrModule {}
unsafe impl Sync for WamrModule {}

/// WebAssembly instance handle with configuration
pub struct WamrInstance {
    /// Handle to actual WAMR module instance
    pub handle: *mut WasmModuleInstT,
    /// Execution environment for function calls
    pub exec_env: *mut WasmExecEnvT,
    pub stack_size: usize,
    pub heap_size: usize,
}

// Safety: WAMR instances are isolated and thread-safe within their execution context
unsafe impl Send for WamrInstance {}
unsafe impl Sync for WamrInstance {}

/// WebAssembly function handle with signature information
pub struct WamrFunction {
    /// Handle to actual WAMR function instance
    pub handle: *mut WasmFunctionInstT,
    /// Handle to the module instance (needed for signature introspection and exception APIs)
    pub instance_handle: *mut WasmModuleInstT,
    /// Handle to the execution environment (needed for function calls)
    pub exec_env: *mut WasmExecEnvT,
    pub name: String,
    pub param_types: Vec<WasmType>,
    pub result_types: Vec<WasmType>,
}

// Safety: WAMR functions can be transferred across threads (ownership transfer).
// Sync is NOT implemented because exec_env is per-thread in WAMR.
unsafe impl Send for WamrFunction {}

/// WebAssembly memory handle with access tracking
pub struct WamrMemory {
    /// Handle to the module instance that owns this memory
    pub instance_handle: *mut WasmModuleInstT,
    /// Handle to the WAMR memory instance (for page count, shared, etc.)
    pub memory_handle: *mut WasmMemoryInstT,
    pub size: usize,
    pub data_ptr: *mut u8,
}

// SAFETY: WamrMemory can be sent between threads but is not safe to share
// concurrently due to the mutable data_ptr.
unsafe impl Send for WamrMemory {}

/// WebAssembly table handle with metadata
pub struct WamrTable {
    /// Handle to the module instance that owns this table
    pub instance_handle: *mut WasmModuleInstT,
    /// Execution environment for indirect calls
    pub exec_env: *mut WasmExecEnvT,
    /// Inline copy of the table instance data from WAMR
    pub table_inst: wasm_table_inst_t,
    /// Table name
    pub name: String,
}

// Safety: WamrTable holds read-only metadata; the instance_handle is not
// modified through the table.
unsafe impl Send for WamrTable {}

// =============================================================================
// Safety and Validation
// =============================================================================

impl WamrRuntime {
    /// Validate that the runtime is properly initialized
    pub fn is_valid(&self) -> bool {
        !self.handle.is_null()
    }
}

impl Drop for WamrRuntime {
    fn drop(&mut self) {
        if !self.handle.is_null() {
            unsafe {
                bindings::wasm_runtime_destroy();
            }
            self.handle = ptr::null_mut();
        }
    }
}

impl WamrModule {
    /// Validate that the module is properly compiled
    pub fn is_valid(&self) -> bool {
        !self.handle.is_null()
    }
}

impl Drop for WamrModule {
    fn drop(&mut self) {
        if !self.handle.is_null() {
            unsafe {
                bindings::wasm_runtime_unload(self.handle);
            }
            self.handle = ptr::null_mut();
        }
    }
}

impl WamrInstance {
    /// Validate that the instance is properly instantiated
    pub fn is_valid(&self) -> bool {
        !self.handle.is_null()
    }
}

impl Drop for WamrInstance {
    fn drop(&mut self) {
        // Destroy exec_env before deinstantiating the module instance
        if !self.exec_env.is_null() {
            unsafe {
                bindings::wasm_runtime_destroy_exec_env(self.exec_env);
            }
            self.exec_env = ptr::null_mut();
        }
        if !self.handle.is_null() {
            unsafe {
                bindings::wasm_runtime_deinstantiate(self.handle);
            }
            self.handle = ptr::null_mut();
        }
    }
}

impl WamrFunction {
    /// Validate that the function is properly resolved
    pub fn is_valid(&self) -> bool {
        !self.handle.is_null() && !self.name.is_empty()
    }
}

// Note: WamrFunction doesn't need Drop trait since function instances
// are owned by the module instance and cleaned up automatically

impl WamrMemory {
    /// Validate that the memory is accessible
    pub fn is_valid(&self) -> bool {
        !self.instance_handle.is_null() && self.size > 0
    }
}

// Note: WamrMemory doesn't need Drop trait since memory is owned by
// the module instance and cleaned up automatically

impl WamrTable {
    /// Validate that the table is accessible
    pub fn is_valid(&self) -> bool {
        !self.instance_handle.is_null() && !self.name.is_empty()
    }
}

// Note: WamrTable doesn't need Drop trait since tables are owned by
// the module instance and cleaned up automatically

// =============================================================================
// Host Function Registration Types
// =============================================================================

/// Callback function type for host functions.
/// Called by the WAMR trampoline when a registered host function is invoked.
///
/// Parameters:
/// - `user_data`: opaque pointer to caller's context
/// - `args`: raw uint64 arguments from WAMR (one per parameter)
/// - `param_count`: number of parameters
/// - `result`: pointer to write result value (as uint64)
///
/// Returns: 0 on success, non-zero on error
pub type HostCallbackFn = unsafe extern "C" fn(
    user_data: *mut std::os::raw::c_void,
    args: *const u64,
    param_count: u32,
    result: *mut u64,
) -> i32;

/// Per-function context stored as the NativeSymbol attachment.
/// Retrieved by the trampoline via wasm_runtime_get_function_attachment.
pub struct HostFunctionContext {
    /// Number of WASM parameters
    pub param_count: u32,
    /// Number of WASM results (0 or 1)
    pub result_count: u32,
    /// External callback function pointer
    pub callback_fn: HostCallbackFn,
    /// Opaque user data passed to callback_fn
    pub user_data: *mut std::os::raw::c_void,
}

/// Manages the lifetime of a batch of registered native symbols.
/// Holds all allocated CStrings and NativeSymbol array to prevent premature freeing.
/// Drop unregisters from WAMR and frees all resources.
pub struct NativeRegistration {
    /// Module name this registration is for
    pub module_name: std::ffi::CString,
    /// The NativeSymbol array passed to WAMR (must remain stable for unregister)
    pub symbols: Vec<bindings::NativeSymbol>,
    /// Owned CStrings for symbol names (keep alive while registered)
    pub symbol_names: Vec<std::ffi::CString>,
    /// Owned CStrings for signatures (keep alive while registered)
    pub signatures: Vec<std::ffi::CString>,
    /// Boxed contexts for each function (keep alive while registered)
    pub contexts: Vec<Box<HostFunctionContext>>,
}

impl Drop for NativeRegistration {
    fn drop(&mut self) {
        if !self.symbols.is_empty() {
            unsafe {
                bindings::wasm_runtime_unregister_natives(
                    self.module_name.as_ptr(),
                    self.symbols.as_mut_ptr(),
                );
            }
        }
        // contexts, symbol_names, signatures are dropped automatically
    }
}

// Safety: NativeRegistration contains owned data that doesn't reference
// thread-local state.
unsafe impl Send for NativeRegistration {}
