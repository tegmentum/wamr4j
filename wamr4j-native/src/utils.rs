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

//! Utility functions for WAMR FFI operations
//!
//! This module provides common utility functions for error handling,
//! string conversion, and FFI value conversion needed by the FFI layer.

use std::ffi::CString;
use std::os::raw::{c_char, c_int};

use crate::types::{WasmValue, WasmType};

// Thread-local storage for last error message
thread_local! {
    static LAST_ERROR: std::cell::RefCell<Option<String>> = std::cell::RefCell::new(None);
}

// =============================================================================
// Error Handling Utilities
// =============================================================================

/// Set the last error message for the current thread
#[inline(always)]
pub fn set_last_error(error: String) {
    LAST_ERROR.with(|last_error| {
        *last_error.borrow_mut() = Some(error);
    });
}

/// Get the last error message for the current thread
pub fn get_last_error() -> Option<String> {
    LAST_ERROR.with(|last_error| {
        last_error.borrow().clone()
    })
}

/// Clear the last error message for the current thread
#[inline(always)]
pub fn clear_last_error() {
    LAST_ERROR.with(|last_error| {
        last_error.borrow_mut().take();
    });
}

/// Write a string to a C string buffer without setting the last error
pub fn write_string_to_buffer(s: &str, buffer: *mut c_char, buffer_size: c_int) {
    if buffer.is_null() || buffer_size <= 0 {
        return;
    }

    let cstring = match CString::new(s) {
        Ok(cstr) => cstr,
        Err(_) => return,
    };

    let bytes = cstring.as_bytes_with_nul();
    let copy_len = std::cmp::min(bytes.len(), buffer_size as usize);

    unsafe {
        std::ptr::copy_nonoverlapping(
            bytes.as_ptr(),
            buffer as *mut u8,
            copy_len,
        );

        // Ensure null termination
        if copy_len > 0 {
            *((buffer as *mut u8).add(copy_len - 1)) = 0;
        }
    }
}

/// Write error message to a C string buffer
pub fn write_error_to_buffer(error: &str, buffer: *mut c_char, buffer_size: c_int) {
    if buffer.is_null() || buffer_size <= 0 {
        return;
    }

    set_last_error(error.to_string());

    let error_cstring = match CString::new(error) {
        Ok(cstr) => cstr,
        Err(_) => return,
    };

    let error_bytes = error_cstring.as_bytes_with_nul();
    let copy_len = std::cmp::min(error_bytes.len(), buffer_size as usize);

    unsafe {
        std::ptr::copy_nonoverlapping(
            error_bytes.as_ptr(),
            buffer as *mut u8,
            copy_len,
        );

        // Ensure null termination
        if copy_len > 0 {
            *((buffer as *mut u8).add(copy_len - 1)) = 0;
        }
    }
}

// =============================================================================
// FFI Value Types and Constants
// =============================================================================

/// FFI-compatible WebAssembly value representation.
/// Layout: value_type (i32) at offset 0, padding at offset 4, data at offset 8.
/// Total size: 16 bytes, matching the Java Panama struct layout.
#[repr(C)]
pub struct WasmValueFFI {
    pub value_type: c_int, // 0=i32, 1=i64, 2=f32, 3=f64
    _padding: c_int,       // explicit padding for 8-byte aligned data
    pub data: [u8; 8],     // Union-like storage for all value types
}

/// FFI type constants for WebAssembly values
pub const WASM_TYPE_I32: c_int = 0;
pub const WASM_TYPE_I64: c_int = 1;
pub const WASM_TYPE_F32: c_int = 2;
pub const WASM_TYPE_F64: c_int = 3;

// =============================================================================
// FFI Value Conversion Functions
// =============================================================================

/// Convert WasmValue to FFI representation
#[inline(always)]
pub fn wasm_value_to_ffi(value: &WasmValue) -> WasmValueFFI {
    match value {
        WasmValue::I32(v) => WasmValueFFI {
            value_type: WASM_TYPE_I32,
            _padding: 0,
            data: {
                let mut data = [0u8; 8];
                data[0..4].copy_from_slice(&v.to_le_bytes());
                data
            },
        },
        WasmValue::I64(v) => WasmValueFFI {
            value_type: WASM_TYPE_I64,
            _padding: 0,
            data: v.to_le_bytes(),
        },
        WasmValue::F32(v) => WasmValueFFI {
            value_type: WASM_TYPE_F32,
            _padding: 0,
            data: {
                let mut data = [0u8; 8];
                data[0..4].copy_from_slice(&v.to_le_bytes());
                data
            },
        },
        WasmValue::F64(v) => WasmValueFFI {
            value_type: WASM_TYPE_F64,
            _padding: 0,
            data: v.to_le_bytes(),
        },
    }
}

/// Convert FFI representation to WasmValue
#[inline(always)]
pub fn wasm_value_from_ffi(ffi_value: &WasmValueFFI) -> WasmValue {
    match ffi_value.value_type {
        WASM_TYPE_I32 => {
            let bytes = [ffi_value.data[0], ffi_value.data[1], ffi_value.data[2], ffi_value.data[3]];
            WasmValue::I32(i32::from_le_bytes(bytes))
        }
        WASM_TYPE_I64 => {
            WasmValue::I64(i64::from_le_bytes(ffi_value.data))
        }
        WASM_TYPE_F32 => {
            let bytes = [ffi_value.data[0], ffi_value.data[1], ffi_value.data[2], ffi_value.data[3]];
            WasmValue::F32(f32::from_le_bytes(bytes))
        }
        WASM_TYPE_F64 => {
            WasmValue::F64(f64::from_le_bytes(ffi_value.data))
        }
        _ => {
            set_last_error(format!("Unknown WASM value type: {}", ffi_value.value_type));
            WasmValue::I32(0)
        }
    }
}

/// Convert WasmType to FFI representation
#[inline(always)]
pub fn wasm_type_to_ffi(wasm_type: &WasmType) -> c_int {
    match wasm_type {
        WasmType::I32 => WASM_TYPE_I32,
        WasmType::I64 => WASM_TYPE_I64,
        WasmType::F32 => WASM_TYPE_F32,
        WasmType::F64 => WASM_TYPE_F64,
    }
}

// =============================================================================
// Testing Utilities
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_handling() {
        clear_last_error();
        assert!(get_last_error().is_none());

        set_last_error("Test error".to_string());
        assert_eq!(get_last_error(), Some("Test error".to_string()));

        clear_last_error();
        assert!(get_last_error().is_none());
    }
}
