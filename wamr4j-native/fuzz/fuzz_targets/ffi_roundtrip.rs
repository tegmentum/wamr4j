//! Fuzz target: FFI layer module compilation with arbitrary bytes
//!
//! Calls `wamr_module_compile()` directly through the C ABI interface
//! with arbitrary bytes. This tests the FFI boundary code including
//! null checks, error buffer handling, and memory safety at the C/Rust
//! boundary.

#![no_main]

use libfuzzer_sys::fuzz_target;
use std::os::raw::{c_char, c_void};
use std::sync::LazyLock;

// FFI function declarations matching the exports from wamr4j_native::ffi
extern "C" {
    fn wamr_runtime_init() -> *mut c_void;
    fn wamr_runtime_destroy(runtime: *mut c_void);
    fn wamr_module_compile(
        runtime: *mut c_void,
        wasm_bytes: *const u8,
        length: i64,
        error_buf: *mut c_char,
        error_buf_size: i32,
    ) -> *mut c_void;
    fn wamr_module_destroy(module: *mut c_void);
}

/// Shared runtime handle initialized once.
static RUNTIME: LazyLock<*mut c_void> = LazyLock::new(|| {
    let rt = unsafe { wamr_runtime_init() };
    assert!(!rt.is_null(), "Failed to initialize WAMR runtime via FFI");
    rt
});

// SAFETY: The runtime pointer is read-only after initialization and WAMR
// runtime is thread-safe for module compilation operations.
unsafe impl Send for RuntimeHandle {}
unsafe impl Sync for RuntimeHandle {}
struct RuntimeHandle(*mut c_void);

fuzz_target!(|data: &[u8]| {
    let runtime = *RUNTIME;

    // Skip empty input
    if data.is_empty() {
        return;
    }

    let mut error_buf = [0u8; 1024];

    // Test 1: Normal compilation with arbitrary bytes through FFI
    let module = unsafe {
        wamr_module_compile(
            runtime,
            data.as_ptr(),
            data.len() as i64,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as i32,
        )
    };

    if !module.is_null() {
        // Unexpectedly valid module - clean it up
        unsafe { wamr_module_destroy(module) };
    }

    // Test 2: Null wasm_bytes pointer (should return null, not crash)
    let module_null = unsafe {
        wamr_module_compile(
            runtime,
            std::ptr::null(),
            data.len() as i64,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as i32,
        )
    };
    assert!(module_null.is_null());

    // Test 3: Zero length (should return null, not crash)
    let module_zero = unsafe {
        wamr_module_compile(
            runtime,
            data.as_ptr(),
            0,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as i32,
        )
    };
    assert!(module_zero.is_null());

    // Test 4: Negative length (should return null, not crash)
    let module_neg = unsafe {
        wamr_module_compile(
            runtime,
            data.as_ptr(),
            -1,
            error_buf.as_mut_ptr() as *mut c_char,
            error_buf.len() as i32,
        )
    };
    assert!(module_neg.is_null());

    // Test 5: Null error buffer (should not crash)
    let module_no_err = unsafe {
        wamr_module_compile(
            runtime,
            data.as_ptr(),
            data.len() as i64,
            std::ptr::null_mut(),
            0,
        )
    };
    if !module_no_err.is_null() {
        unsafe { wamr_module_destroy(module_no_err) };
    }

    // Test 6: Very small error buffer (should truncate, not crash)
    let mut tiny_buf = [0u8; 1];
    let module_tiny = unsafe {
        wamr_module_compile(
            runtime,
            data.as_ptr(),
            data.len() as i64,
            tiny_buf.as_mut_ptr() as *mut c_char,
            tiny_buf.len() as i32,
        )
    };
    if !module_tiny.is_null() {
        unsafe { wamr_module_destroy(module_tiny) };
    }
});
