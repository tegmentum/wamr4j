//! Fuzz target: FFI layer module compilation with arbitrary bytes
//!
//! Calls `wamr_module_compile()` through the FFI interface with arbitrary
//! bytes. This tests the FFI boundary code including null checks, error
//! buffer handling, and memory safety at the C/Rust boundary.

#![no_main]

use libfuzzer_sys::fuzz_target;
use std::os::raw::{c_char, c_void};
use std::sync::LazyLock;
use wamr4j_native::ffi;

// SAFETY: The runtime pointer is read-only after initialization and WAMR
// runtime is thread-safe for module compilation operations.
struct RuntimeHandle(*mut c_void);
unsafe impl Send for RuntimeHandle {}
unsafe impl Sync for RuntimeHandle {}

/// Shared runtime handle initialized once.
static RUNTIME: LazyLock<RuntimeHandle> = LazyLock::new(|| {
    let rt = ffi::wamr_runtime_init();
    assert!(!rt.is_null(), "Failed to initialize WAMR runtime via FFI");
    RuntimeHandle(rt)
});

fuzz_target!(|data: &[u8]| {
    let runtime = RUNTIME.0;

    // Skip empty input
    if data.is_empty() {
        return;
    }

    let mut error_buf = [0u8; 1024];

    // Test 1: Normal compilation with arbitrary bytes through FFI
    let module = ffi::wamr_module_compile(
        runtime,
        data.as_ptr(),
        data.len() as i64,
        error_buf.as_mut_ptr() as *mut c_char,
        error_buf.len() as i32,
    );

    if !module.is_null() {
        // Unexpectedly valid module - clean it up
        ffi::wamr_module_destroy(module);
    }

    // Test 2: Null wasm_bytes pointer (should return null, not crash)
    let module_null = ffi::wamr_module_compile(
        runtime,
        std::ptr::null(),
        data.len() as i64,
        error_buf.as_mut_ptr() as *mut c_char,
        error_buf.len() as i32,
    );
    assert!(module_null.is_null());

    // Test 3: Zero length (should return null, not crash)
    let module_zero = ffi::wamr_module_compile(
        runtime,
        data.as_ptr(),
        0,
        error_buf.as_mut_ptr() as *mut c_char,
        error_buf.len() as i32,
    );
    assert!(module_zero.is_null());

    // Test 4: Negative length (should return null, not crash)
    let module_neg = ffi::wamr_module_compile(
        runtime,
        data.as_ptr(),
        -1,
        error_buf.as_mut_ptr() as *mut c_char,
        error_buf.len() as i32,
    );
    assert!(module_neg.is_null());

    // Test 5: Null error buffer (should not crash)
    let module_no_err = ffi::wamr_module_compile(
        runtime,
        data.as_ptr(),
        data.len() as i64,
        std::ptr::null_mut(),
        0,
    );
    if !module_no_err.is_null() {
        ffi::wamr_module_destroy(module_no_err);
    }

    // Test 6: Very small error buffer (should truncate, not crash)
    let mut tiny_buf = [0u8; 1];
    let module_tiny = ffi::wamr_module_compile(
        runtime,
        data.as_ptr(),
        data.len() as i64,
        tiny_buf.as_mut_ptr() as *mut c_char,
        tiny_buf.len() as i32,
    );
    if !module_tiny.is_null() {
        ffi::wamr_module_destroy(module_tiny);
    }
});
