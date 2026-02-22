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

use std::fmt;
use std::ptr;
use crate::bindings::{self, WasmRuntimeT, WasmModuleT, WasmModuleInstT, WasmFunctionInstT};

/// Configuration for WAMR runtime initialization
#[derive(Debug, Clone)]
pub struct RuntimeConfig {
    pub stack_size: usize,
    pub heap_size: usize,
    pub max_thread_num: u32,
}

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
        }
    }
}

impl std::error::Error for WamrError {}

// =============================================================================
// Handle Structures
// =============================================================================

/// WebAssembly runtime handle with enhanced configuration
pub struct WamrRuntime {
    /// Handle to actual WAMR runtime instance
    pub handle: *mut WasmRuntimeT,
    pub config: RuntimeConfig,
}

// Safety: WAMR runtime is thread-safe for read operations
unsafe impl Send for WamrRuntime {}
unsafe impl Sync for WamrRuntime {}

/// WebAssembly module handle with metadata
pub struct WamrModule {
    /// Handle to actual WAMR compiled module
    pub handle: *mut WasmModuleT,
    pub size: usize,
}

// Safety: WAMR modules are immutable after compilation
unsafe impl Send for WamrModule {}
unsafe impl Sync for WamrModule {}

/// WebAssembly instance handle with configuration
pub struct WamrInstance {
    /// Handle to actual WAMR module instance
    pub handle: *mut WasmModuleInstT,
    pub stack_size: usize,
    pub heap_size: usize,
}

// Safety: WAMR instances are isolated and thread-safe within their execution context
unsafe impl Send for WamrInstance {}

/// WebAssembly function handle with signature information
pub struct WamrFunction {
    /// Handle to actual WAMR function instance
    pub handle: *mut WasmFunctionInstT,
    /// Handle to the module instance (needed for signature introspection APIs)
    pub instance_handle: *mut WasmModuleInstT,
    pub name: String,
    pub param_types: Vec<WasmType>,
    pub result_types: Vec<WasmType>,
}

// Safety: WAMR functions are read-only after lookup
unsafe impl Send for WamrFunction {}
unsafe impl Sync for WamrFunction {}

/// WebAssembly memory handle with access tracking
pub struct WamrMemory {
    /// Handle to the module instance that owns this memory
    pub instance_handle: *mut WasmModuleInstT,
    pub size: usize,
    pub data_ptr: *mut u8,
}

// Implement Send and Sync for memory handle (with caution)
unsafe impl Send for WamrMemory {}
unsafe impl Sync for WamrMemory {}

// =============================================================================
// Value Conversion Utilities
// =============================================================================

impl WasmValue {
    /// Get the type of this value
    pub fn get_type(&self) -> WasmType {
        match self {
            WasmValue::I32(_) => WasmType::I32,
            WasmValue::I64(_) => WasmType::I64,
            WasmValue::F32(_) => WasmType::F32,
            WasmValue::F64(_) => WasmType::F64,
        }
    }
}

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

    /// Get function name
    pub fn name(&self) -> &str {
        &self.name
    }

    /// Get parameter types
    pub fn param_types(&self) -> &[WasmType] {
        &self.param_types
    }

    /// Get result types
    pub fn result_types(&self) -> &[WasmType] {
        &self.result_types
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
