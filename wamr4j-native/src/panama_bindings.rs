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
        WamrError::RuntimeCreationFailed => -1001,
        WamrError::CompilationFailed => -1002,
        WamrError::InstantiationFailed => -1003,
        WamrError::FunctionNotFound => -1004,
        WamrError::MemoryNotFound => -1005,
        WamrError::InvalidArguments => -1006,
        WamrError::NativeError(_) => -1999,
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