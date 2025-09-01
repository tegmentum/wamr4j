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

//! Panama FFI bindings for the WAMR WebAssembly runtime
//!
//! This module provides C ABI function exports that correspond to the
//! Panama FFI calls made from the Java Panama implementation classes.
//! These functions use the same underlying WAMR wrapper as the JNI bindings.

use std::os::raw::{c_char, c_int, c_long, c_uchar, c_void};
use std::ptr;

use crate::wamr_wrapper::*;

// Note: Panama FFI bindings use the same C ABI exports as defined in lib.rs
// This module exists for organization and can contain Panama-specific utilities

/// Panama-specific utility for handling string conversions
pub fn string_to_cstring(s: &str) -> Result<std::ffi::CString, std::ffi::NulError> {
    std::ffi::CString::new(s)
}

/// Panama-specific utility for handling byte array conversions
pub fn bytes_to_vec(ptr: *const c_uchar, len: c_long) -> Option<Vec<u8>> {
    if ptr.is_null() || len <= 0 {
        return None;
    }
    
    unsafe {
        let slice = std::slice::from_raw_parts(ptr, len as usize);
        Some(slice.to_vec())
    }
}

/// Panama-specific error handling utilities
pub fn error_to_code(error: &WamrError) -> c_int {
    match error {
        // Runtime errors
        WamrError::RuntimeCreationFailed => -1001,
        WamrError::RuntimeNotInitialized => -1002,
        WamrError::RuntimeConfigurationError(_) => -1003,
        
        // Compilation errors
        WamrError::CompilationFailed => -1010,
        WamrError::InvalidWasmBytecode => -1011,
        WamrError::UnsupportedWasmFeature(_) => -1012,
        
        // Instantiation errors
        WamrError::InstantiationFailed => -1020,
        WamrError::InsufficientMemory => -1021,
        WamrError::LinkingError(_) => -1022,
        
        // Function call errors
        WamrError::FunctionNotFound => -1030,
        WamrError::FunctionSignatureMismatch => -1031,
        WamrError::InvalidArguments => -1032,
        WamrError::ExecutionFailed(_) => -1033,
        
        // Memory errors
        WamrError::MemoryNotFound => -1040,
        WamrError::MemoryAccessViolation => -1041,
        WamrError::MemoryGrowthFailed => -1042,
        WamrError::InvalidMemoryOffset => -1043,
        
        // General errors
        WamrError::NullPointer => -1050,
        WamrError::BufferTooSmall => -1051,
        WamrError::InvalidUTF8 => -1052,
        WamrError::NativeError(_) => -1099,
    }
}

/// Panama-specific memory layout helpers
pub struct PanamaMemoryLayout;

impl PanamaMemoryLayout {
    pub const I32_SIZE: usize = 4;
    pub const I64_SIZE: usize = 8;
    pub const F32_SIZE: usize = 4;
    pub const F64_SIZE: usize = 8;
    pub const PTR_SIZE: usize = std::mem::size_of::<*const c_void>();
}

// The actual FFI exports are defined in lib.rs and are used by both
// JNI and Panama implementations. This module provides additional
// Panama-specific utilities and type conversions.