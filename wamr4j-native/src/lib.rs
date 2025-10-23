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

//! Shared native library for wamr4j JNI and Panama FFI bindings

use std::os::raw::{c_char, c_int};

// Module declarations
pub mod bindings;
pub mod ffi;
pub mod jni_bindings;
pub mod runtime;
pub mod utils;
pub mod wamr_wrapper;
pub mod platform;
pub mod optimizations;

/// Test function to verify cross-compilation works
#[no_mangle]
pub extern "C" fn wamr4j_test_init() -> c_int {
    // Return success code for testing
    0
}

/// Test function to get version string
#[no_mangle]
pub extern "C" fn wamr4j_test_get_version() -> *const c_char {
    b"1.0.0-test\0".as_ptr() as *const c_char
}

/// Test function to verify cross-platform build
#[no_mangle]
pub extern "C" fn wamr4j_test_platform() -> *const c_char {
    if cfg!(target_os = "linux") {
        b"linux\0".as_ptr() as *const c_char
    } else if cfg!(target_os = "windows") {
        b"windows\0".as_ptr() as *const c_char
    } else if cfg!(target_os = "macos") {
        b"macos\0".as_ptr() as *const c_char
    } else {
        b"unknown\0".as_ptr() as *const c_char
    }
}

/// Test function to verify architecture
#[no_mangle]
pub extern "C" fn wamr4j_test_arch() -> *const c_char {
    if cfg!(target_arch = "x86_64") {
        b"x86_64\0".as_ptr() as *const c_char
    } else if cfg!(target_arch = "aarch64") {
        b"aarch64\0".as_ptr() as *const c_char
    } else {
        b"unknown\0".as_ptr() as *const c_char
    }
}

/// Initialize platform-specific optimizations
#[no_mangle]
pub extern "C" fn wamr4j_init_platform_optimizations() -> c_int {
    match platform::init_global_platform_optimizations() {
        Ok(()) => 0,  // Success
        Err(_) => -1, // Error
    }
}

/// Get platform optimization information
#[no_mangle]
pub extern "C" fn wamr4j_get_platform_info() -> *const c_char {
    let info = platform::get_platform_info();
    // Note: This is a simplified implementation
    // In production, we'd need to manage the lifetime of this string properly
    let c_string = std::ffi::CString::new(info).unwrap_or_default();
    c_string.into_raw() as *const c_char
}

/// Get optimization configuration information
#[no_mangle]
pub extern "C" fn wamr4j_get_optimization_info() -> *const c_char {
    let config = optimizations::get_optimal_config();
    let info = config.get_optimization_info();
    // Note: This is a simplified implementation
    // In production, we'd need to manage the lifetime of this string properly
    let c_string = std::ffi::CString::new(info).unwrap_or_default();
    c_string.into_raw() as *const c_char
}

/// Get optimal thread count for current platform
#[no_mangle]
pub extern "C" fn wamr4j_get_optimal_thread_count() -> c_int {
    if let Some(opts) = platform::get_platform_optimizations() {
        opts.get_optimal_thread_count() as c_int
    } else {
        std::thread::available_parallelism()
            .map(|n| n.get() as c_int)
            .unwrap_or(1)
    }
}