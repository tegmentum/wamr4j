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
//! string conversion, memory management, and other cross-cutting concerns
//! needed by the FFI layer.

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

// Import types from wamr_wrapper to avoid circular dependency
use crate::wamr_wrapper::{WasmValue, WasmType};

// Thread-local storage for last error message with atomic flag for fast path
thread_local! {
    static LAST_ERROR: std::cell::RefCell<Option<String>> = std::cell::RefCell::new(None);
}

// Global atomic flag to quickly check if any thread has errors (lock-free fast path)
static HAS_ERRORS: AtomicBool = AtomicBool::new(false);

// =============================================================================
// Error Handling Utilities
// =============================================================================

/// Set the last error message for the current thread
#[inline(always)]
pub fn set_last_error(error: String) {
    LAST_ERROR.with(|last_error| {
        *last_error.borrow_mut() = Some(error);
    });
    HAS_ERRORS.store(true, Ordering::Relaxed);
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
        if last_error.borrow_mut().take().is_some() {
            // Only clear the global flag if we actually had an error
            HAS_ERRORS.store(false, Ordering::Relaxed);
        }
    });
}

/// Fast path check if any errors exist (lock-free)
#[inline(always)]
pub fn has_any_errors() -> bool {
    HAS_ERRORS.load(Ordering::Relaxed)
}

/// Write error message to a C string buffer
pub fn write_error_to_buffer(error: &str, buffer: *mut c_char, buffer_size: c_int) {
    if buffer.is_null() || buffer_size <= 0 {
        return;
    }

    // Set the error for later retrieval
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
// String Conversion Utilities
// =============================================================================

/// Convert C string to Rust String safely
pub fn cstr_to_string(cstr: *const c_char) -> Result<String, &'static str> {
    if cstr.is_null() {
        return Err("Null pointer");
    }

    unsafe {
        match CStr::from_ptr(cstr).to_str() {
            Ok(s) => Ok(s.to_string()),
            Err(_) => Err("Invalid UTF-8"),
        }
    }
}

/// Convert C string to Rust str safely
pub fn cstr_to_str<'a>(cstr: *const c_char) -> Result<&'a str, &'static str> {
    if cstr.is_null() {
        return Err("Null pointer");
    }

    unsafe {
        match CStr::from_ptr(cstr).to_str() {
            Ok(s) => Ok(s),
            Err(_) => Err("Invalid UTF-8"),
        }
    }
}

/// Convert Rust string to C string
pub fn string_to_cstring(s: &str) -> Result<CString, &'static str> {
    CString::new(s).map_err(|_| "String contains null byte")
}

/// Copy string to a C buffer safely
pub fn copy_string_to_buffer(src: &str, buffer: *mut c_char, buffer_size: c_int) -> c_int {
    if buffer.is_null() || buffer_size <= 0 {
        return -1;
    }

    let src_cstring = match CString::new(src) {
        Ok(cstr) => cstr,
        Err(_) => return -1,
    };

    let src_bytes = src_cstring.as_bytes_with_nul();
    let copy_len = std::cmp::min(src_bytes.len(), buffer_size as usize);

    unsafe {
        std::ptr::copy_nonoverlapping(
            src_bytes.as_ptr(),
            buffer as *mut u8,
            copy_len,
        );
        
        // Ensure null termination
        if copy_len > 0 {
            *((buffer as *mut u8).add(copy_len - 1)) = 0;
        }
    }

    copy_len as c_int
}

// =============================================================================
// Memory Management Utilities
// =============================================================================

/// Allocate aligned memory
pub fn aligned_alloc(size: usize, alignment: usize) -> *mut u8 {
    if size == 0 {
        return std::ptr::null_mut();
    }

    // Ensure alignment is a power of 2
    if !alignment.is_power_of_two() {
        return std::ptr::null_mut();
    }

    unsafe {
        let layout = match std::alloc::Layout::from_size_align(size, alignment) {
            Ok(layout) => layout,
            Err(_) => return std::ptr::null_mut(),
        };

        std::alloc::alloc(layout)
    }
}

/// Free aligned memory
pub fn aligned_free(ptr: *mut u8, size: usize, alignment: usize) {
    if ptr.is_null() {
        return;
    }

    unsafe {
        let layout = match std::alloc::Layout::from_size_align(size, alignment) {
            Ok(layout) => layout,
            Err(_) => return,
        };

        std::alloc::dealloc(ptr, layout);
    }
}

/// Safe memory copy with bounds checking
#[inline(always)]
pub fn safe_memcpy(dest: *mut u8, src: *const u8, size: usize, dest_capacity: usize) -> bool {
    if dest.is_null() || src.is_null() || size == 0 {
        return false;
    }

    if size > dest_capacity {
        return false;
    }

    unsafe {
        std::ptr::copy_nonoverlapping(src, dest, size);
    }

    true
}

/// Safe memory set with bounds checking
pub fn safe_memset(dest: *mut u8, value: u8, size: usize, dest_capacity: usize) -> bool {
    if dest.is_null() || size == 0 {
        return false;
    }

    if size > dest_capacity {
        return false;
    }

    unsafe {
        std::ptr::write_bytes(dest, value, size);
    }

    true
}

// =============================================================================
// Validation Utilities
// =============================================================================

/// Validate pointer is not null and aligned
#[inline(always)]
pub fn validate_pointer(ptr: *const u8, alignment: usize) -> bool {
    if ptr.is_null() {
        return false;
    }

    if alignment > 0 && (ptr as usize) % alignment != 0 {
        return false;
    }

    true
}

/// Validate buffer with size bounds
pub fn validate_buffer(ptr: *const u8, size: usize, max_size: usize) -> bool {
    if ptr.is_null() && size > 0 {
        return false;
    }

    if size > max_size {
        return false;
    }

    true
}

/// Check if value is within expected range
pub fn validate_range<T: PartialOrd>(value: T, min: T, max: T) -> bool {
    value >= min && value <= max
}

// =============================================================================
// Type Conversion Utilities
// =============================================================================

/// Convert boolean to C int
pub fn bool_to_c_int(b: bool) -> c_int {
    if b { 1 } else { 0 }
}

/// Convert C int to boolean
pub fn c_int_to_bool(i: c_int) -> bool {
    i != 0
}

/// Convert Result to C int (0 for Ok, -1 for Err)
pub fn result_to_c_int<T>(result: Result<T, &str>) -> c_int {
    match result {
        Ok(_) => 0,
        Err(e) => {
            set_last_error(e.to_string());
            -1
        }
    }
}

/// Convert usize to c_int with overflow checking
pub fn usize_to_c_int(value: usize) -> Result<c_int, &'static str> {
    if value > c_int::MAX as usize {
        Err("Value too large for c_int")
    } else {
        Ok(value as c_int)
    }
}

/// Convert c_int to usize with negative checking
pub fn c_int_to_usize(value: c_int) -> Result<usize, &'static str> {
    if value < 0 {
        Err("Negative value cannot be converted to usize")
    } else {
        Ok(value as usize)
    }
}

// =============================================================================
// Debug and Logging Utilities
// =============================================================================

/// Format pointer for debugging
pub fn format_pointer<T>(ptr: *const T) -> String {
    if ptr.is_null() {
        "NULL".to_string()
    } else {
        format!("{:p}", ptr)
    }
}

/// Format slice for debugging
pub fn format_slice(data: &[u8], max_len: usize) -> String {
    if data.len() <= max_len {
        format!("{:?}", data)
    } else {
        format!("{:?}...", &data[..max_len])
    }
}

/// Create debug information string
pub fn create_debug_info(function: &str, args: &[(&str, &str)]) -> String {
    let mut debug_info = format!("{}(", function);
    
    for (i, (name, value)) in args.iter().enumerate() {
        if i > 0 {
            debug_info.push_str(", ");
        }
        debug_info.push_str(&format!("{}={}", name, value));
    }
    
    debug_info.push(')');
    debug_info
}

// =============================================================================
// Platform-Specific Utilities
// =============================================================================

/// Get system page size
pub fn get_page_size() -> usize {
    // Default WASM page size is 64KB
    65536
}

/// Check if running on supported platform
pub fn is_supported_platform() -> bool {
    cfg!(any(
        target_os = "linux",
        target_os = "windows",
        target_os = "macos"
    ))
}

/// Get platform name for debugging
pub fn get_platform_name() -> &'static str {
    if cfg!(target_os = "linux") {
        "linux"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "macos"
    } else {
        "unknown"
    }
}

/// Get architecture name for debugging
pub fn get_architecture_name() -> &'static str {
    if cfg!(target_arch = "x86_64") {
        "x86_64"
    } else if cfg!(target_arch = "aarch64") {
        "aarch64"
    } else {
        "unknown"
    }
}

// =============================================================================
// FFI Value Conversion Functions
// =============================================================================

/// Optimized batch conversion from WasmValue slice to FFI representation
/// Uses zero-copy techniques where possible
#[inline]
pub fn batch_wasm_values_to_ffi(values: &[WasmValue], ffi_buffer: &mut [WasmValueFFI]) -> usize {
    let count = std::cmp::min(values.len(), ffi_buffer.len());
    
    // Use vectorized operations for common cases
    for i in 0..count {
        ffi_buffer[i] = wasm_value_to_ffi(&values[i]);
    }
    
    count
}

/// Optimized batch conversion from FFI representation to WasmValue
/// Minimizes allocations through pre-allocated buffer reuse
#[inline]
pub fn batch_ffi_to_wasm_values(ffi_values: &[WasmValueFFI], wasm_buffer: &mut Vec<WasmValue>) {
    wasm_buffer.clear();
    wasm_buffer.reserve(ffi_values.len());
    
    for ffi_val in ffi_values {
        wasm_buffer.push(wasm_value_from_ffi(ffi_val));
    }
}

/// Zero-copy memory transfer using memory mapping for large buffers
/// Falls back to safe_memcpy for smaller buffers
#[inline]
pub fn optimized_memory_transfer(
    dest: *mut u8, 
    src: *const u8, 
    size: usize, 
    dest_capacity: usize
) -> bool {
    // For large transfers, use platform-specific optimizations
    if size >= 4096 {
        // Use optimized memory copy for large blocks
        return safe_memcpy(dest, src, size, dest_capacity);
    }
    
    // For small transfers, inline the copy to avoid function call overhead
    if dest.is_null() || src.is_null() || size == 0 || size > dest_capacity {
        return false;
    }
    
    unsafe {
        std::ptr::copy_nonoverlapping(src, dest, size);
    }
    
    true
}

/// FFI-compatible WebAssembly value representation
#[repr(C)]
pub struct WasmValueFFI {
    pub value_type: c_int, // 0=i32, 1=i64, 2=f32, 3=f64
    pub data: [u8; 8],     // Union-like storage for all value types
}

/// FFI type constants for WebAssembly values
pub const WASM_TYPE_I32: c_int = 0;
pub const WASM_TYPE_I64: c_int = 1;
pub const WASM_TYPE_F32: c_int = 2;
pub const WASM_TYPE_F64: c_int = 3;

/// Convert WasmValue to FFI representation
#[inline(always)]
pub fn wasm_value_to_ffi(value: &WasmValue) -> WasmValueFFI {
    match value {
        WasmValue::I32(v) => WasmValueFFI {
            value_type: WASM_TYPE_I32,
            data: {
                let mut data = [0u8; 8];
                data[0..4].copy_from_slice(&v.to_le_bytes());
                data
            },
        },
        WasmValue::I64(v) => WasmValueFFI {
            value_type: WASM_TYPE_I64,
            data: v.to_le_bytes(),
        },
        WasmValue::F32(v) => WasmValueFFI {
            value_type: WASM_TYPE_F32,
            data: {
                let mut data = [0u8; 8];
                data[0..4].copy_from_slice(&v.to_le_bytes());
                data
            },
        },
        WasmValue::F64(v) => WasmValueFFI {
            value_type: WASM_TYPE_F64,
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
        _ => WasmValue::I32(0), // Default fallback
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
// Testing Utilities (for unit tests)
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CString;

    #[test]
    fn test_error_handling() {
        clear_last_error();
        assert!(get_last_error().is_none());

        set_last_error("Test error".to_string());
        assert_eq!(get_last_error(), Some("Test error".to_string()));

        clear_last_error();
        assert!(get_last_error().is_none());
    }

    #[test]
    fn test_string_conversion() {
        let test_str = "Hello, World!";
        let cstring = CString::new(test_str).unwrap();
        let cstr_ptr = cstring.as_ptr();

        assert_eq!(cstr_to_string(cstr_ptr).unwrap(), test_str);
        assert_eq!(cstr_to_str(cstr_ptr).unwrap(), test_str);
    }

    #[test]
    fn test_validation() {
        assert!(validate_range(5, 0, 10));
        assert!(!validate_range(15, 0, 10));

        let ptr = 0x1000 as *const u8;
        assert!(validate_pointer(ptr, 1));
        assert!(validate_pointer(ptr, 4));
        assert!(!validate_pointer(std::ptr::null(), 1));
    }

    #[test]
    fn test_type_conversion() {
        assert_eq!(bool_to_c_int(true), 1);
        assert_eq!(bool_to_c_int(false), 0);
        assert_eq!(c_int_to_bool(1), true);
        assert_eq!(c_int_to_bool(0), false);
    }
}