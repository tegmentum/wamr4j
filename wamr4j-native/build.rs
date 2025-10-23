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

//! Build script for wamr4j-native
//!
//! This build script handles:
//! 1. WAMR library compilation from source
//! 2. C header generation for FFI bindings
//! 3. Platform-specific compilation flags
//! 4. Cross-compilation support

use std::env;
use std::path::PathBuf;

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=wamr/");
    
    let target = env::var("TARGET").unwrap();
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let wamr_dir = PathBuf::from("wamr");
    
    // Check if WAMR source directory exists
    if !wamr_dir.exists() {
        println!("cargo:warning=WAMR source directory not found at {:?}", wamr_dir);
        println!("cargo:warning=Creating placeholder WAMR directory structure");
        create_placeholder_wamr(&wamr_dir);
    }
    
    // Build WAMR library
    build_wamr(&target, &out_dir, &wamr_dir);
    
    // Generate FFI bindings
    generate_bindings(&out_dir, &wamr_dir);
    
    // Configure linking
    configure_linking(&target, &out_dir);
}

/// Build WAMR library from source
fn build_wamr(target: &str, out_dir: &PathBuf, wamr_dir: &PathBuf) {
    // Check if we have a real WAMR source or placeholder
    let cmake_file = wamr_dir.join("CMakeLists.txt");
    if !cmake_file.exists() {
        println!("cargo:warning=No CMakeLists.txt found, creating placeholder build");
        create_placeholder_build(target, out_dir);
        return;
    }
    
    let build_dir = out_dir.join("wamr-build");
    std::fs::create_dir_all(&build_dir).expect("Failed to create build directory");
    
    let mut cmake = cmake::Config::new(wamr_dir);
    
    // Configure WAMR build options
    cmake
        .define("WAMR_BUILD_PLATFORM", get_wamr_platform(target))
        .define("WAMR_BUILD_TARGET", get_wamr_target(target))
        .define("WAMR_BUILD_INTERP", "1")
        .define("WAMR_BUILD_AOT", "1")
        .define("WAMR_BUILD_LIBC_WASI", "1")
        .define("WAMR_BUILD_LIBC_BUILTIN", "1")
        .define("WAMR_BUILD_FAST_INTERP", "1")
        .define("WAMR_BUILD_MULTI_MODULE", "1")
        .define("WAMR_BUILD_BULK_MEMORY", "1")
        .define("WAMR_BUILD_REF_TYPES", "1")
        .define("WAMR_BUILD_SIMD", "1")
        .define("WAMR_BUILD_JIT", "0") // Disable JIT for now
        .define("WAMR_BUILD_DUMP_CALL_STACK", "1")
        .define("WAMR_BUILD_PERF_PROFILING", "0")
        .define("WAMR_BUILD_MEMORY_PROFILING", "0")
        .define("WAMR_BUILD_CUSTOM_NAME_SECTION", "1")
        .define("CMAKE_BUILD_TYPE", "Release");
        
    // Platform-specific configurations
    match target {
        t if t.contains("linux") => {
            cmake.define("CMAKE_SYSTEM_NAME", "Linux");
        },
        t if t.contains("windows") => {
            cmake.define("CMAKE_SYSTEM_NAME", "Windows");
        },
        t if t.contains("darwin") => {
            cmake.define("CMAKE_SYSTEM_NAME", "Darwin");
        },
        _ => {}
    }
    
    // Cross-compilation toolchain setup
    if target != env::var("HOST").unwrap_or_default() {
        setup_cross_compilation(&mut cmake, target);
    }
    
    let wamr_build = cmake.build();
    
    // Link to WAMR library
    println!("cargo:rustc-link-search=native={}/lib", wamr_build.display());
    println!("cargo:rustc-link-lib=static=iwasm");
    
    // Platform-specific system libraries
    link_system_libraries(target);
}

/// Setup cross-compilation configuration
fn setup_cross_compilation(cmake: &mut cmake::Config, target: &str) {
    match target {
        "x86_64-unknown-linux-gnu" => {
            cmake.define("CMAKE_C_COMPILER", "x86_64-linux-gnu-gcc");
            cmake.define("CMAKE_CXX_COMPILER", "x86_64-linux-gnu-g++");
        },
        "aarch64-unknown-linux-gnu" => {
            cmake.define("CMAKE_C_COMPILER", "aarch64-linux-gnu-gcc");
            cmake.define("CMAKE_CXX_COMPILER", "aarch64-linux-gnu-g++");
        },
        "x86_64-pc-windows-gnu" => {
            cmake.define("CMAKE_C_COMPILER", "x86_64-w64-mingw32-gcc");
            cmake.define("CMAKE_CXX_COMPILER", "x86_64-w64-mingw32-g++");
        },
        "x86_64-apple-darwin" | "aarch64-apple-darwin" => {
            // Use system clang for macOS targets
            cmake.define("CMAKE_C_COMPILER", "clang");
            cmake.define("CMAKE_CXX_COMPILER", "clang++");
        },
        _ => {
            println!("cargo:warning=Unsupported cross-compilation target: {}", target);
        }
    }
}

/// Get WAMR platform string
fn get_wamr_platform(target: &str) -> &'static str {
    if target.contains("linux") {
        "linux"
    } else if target.contains("windows") {
        "windows"
    } else if target.contains("darwin") {
        "darwin"
    } else {
        "linux" // Default fallback
    }
}

/// Get WAMR target architecture
fn get_wamr_target(target: &str) -> &'static str {
    if target.contains("x86_64") {
        "X86_64"
    } else if target.contains("aarch64") {
        "AARCH64"
    } else if target.contains("arm") {
        "ARM"
    } else {
        "X86_64" // Default fallback
    }
}

/// Link platform-specific system libraries
fn link_system_libraries(target: &str) {
    if target.contains("linux") {
        println!("cargo:rustc-link-lib=pthread");
        println!("cargo:rustc-link-lib=dl");
        println!("cargo:rustc-link-lib=m");
    } else if target.contains("windows") {
        println!("cargo:rustc-link-lib=ws2_32");
        println!("cargo:rustc-link-lib=advapi32");
        println!("cargo:rustc-link-lib=bcrypt");
    } else if target.contains("darwin") {
        println!("cargo:rustc-link-lib=framework=Security");
        println!("cargo:rustc-link-lib=framework=CoreFoundation");
    }
}

/// Generate FFI bindings from WAMR headers
fn generate_bindings(out_dir: &PathBuf, wamr_dir: &PathBuf) {
    let include_dir = wamr_dir.join("core").join("iwasm").join("include");
    
    if !include_dir.exists() {
        println!("cargo:warning=WAMR include directory not found, creating placeholder bindings");
        create_placeholder_bindings(out_dir);
        return;
    }
    
    // Use bindgen to generate Rust bindings from WAMR headers
    #[cfg(feature = "bindgen")]
    {
        let bindings = bindgen::Builder::default()
            .header(include_dir.join("wasm_export.h").to_string_lossy())
            .header(include_dir.join("wasm_c_api.h").to_string_lossy())
            .allowlist_function("wasm_.*")
            .allowlist_type("wasm_.*")
            .allowlist_var("wasm_.*")
            .derive_debug(true)
            .derive_default(true)
            .generate()
            .expect("Unable to generate bindings");
        
        let out_path = out_dir.join("wamr_bindings.rs");
        bindings
            .write_to_file(out_path)
            .expect("Couldn't write bindings!");
    }
    
    #[cfg(not(feature = "bindgen"))]
    {
        create_placeholder_bindings(out_dir);
    }
}

/// Configure linking for different targets
fn configure_linking(target: &str, _out_dir: &PathBuf) {
    // Enable link-time optimization for release builds
    if env::var("PROFILE").unwrap_or_default() == "release" {
        if target.contains("windows") {
            println!("cargo:rustc-link-arg=/LTCG");
        } else {
            println!("cargo:rustc-link-arg=-flto");
        }
    }
    
    // Platform-specific linking flags
    match target {
        t if t.contains("linux") => {
            println!("cargo:rustc-link-arg=-Wl,--gc-sections");
        },
        t if t.contains("darwin") => {
            println!("cargo:rustc-link-arg=-Wl,-dead_strip");
        },
        _ => {}
    }
}

/// Create placeholder WAMR directory structure for development
fn create_placeholder_wamr(wamr_dir: &PathBuf) {
    let core_dir = wamr_dir.join("core");
    let include_dir = core_dir.join("iwasm").join("include");
    std::fs::create_dir_all(&include_dir).expect("Failed to create placeholder directories");
    
    // Create placeholder header files
    let wasm_export_h = include_dir.join("wasm_export.h");
    std::fs::write(&wasm_export_h, PLACEHOLDER_WASM_EXPORT_H)
        .expect("Failed to create placeholder header");
        
    let wasm_c_api_h = include_dir.join("wasm_c_api.h");
    std::fs::write(&wasm_c_api_h, PLACEHOLDER_WASM_C_API_H)
        .expect("Failed to create placeholder header");
    
    println!("cargo:warning=Created placeholder WAMR headers for development");
}

/// Create placeholder FFI bindings
fn create_placeholder_bindings(out_dir: &PathBuf) {
    let bindings_path = out_dir.join("wamr_bindings.rs");
    std::fs::write(&bindings_path, PLACEHOLDER_BINDINGS)
        .expect("Failed to create placeholder bindings");
        
    println!("cargo:warning=Created placeholder WAMR bindings for development");
}

/// Create a placeholder build for development without WAMR source
fn create_placeholder_build(target: &str, out_dir: &PathBuf) {
    println!("cargo:warning=Building with placeholder WAMR implementation");
    
    // Create a simple static library with placeholder functions
    let lib_dir = out_dir.join("lib");
    std::fs::create_dir_all(&lib_dir).expect("Failed to create lib directory");
    
    let placeholder_c = out_dir.join("placeholder_wamr.c");
    std::fs::write(&placeholder_c, PLACEHOLDER_WAMR_C)
        .expect("Failed to create placeholder C file");
    
    // Compile the placeholder library
    let mut builder = cc::Build::new();
    builder
        .file(&placeholder_c)
        .flag("-fPIC")
        .include(out_dir.join("..").join("..").join("..").join("wamr").join("core").join("iwasm").join("include"))
        .compile("vmlib");
    
    println!("cargo:rustc-link-lib=static=vmlib");
    
    // Platform-specific system libraries
    link_system_libraries(target);
}

// Placeholder header content for development without WAMR source
const PLACEHOLDER_WASM_EXPORT_H: &str = r#"
#ifndef WASM_EXPORT_H
#define WASM_EXPORT_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct wasm_runtime_t wasm_runtime_t;
typedef struct wasm_module_t wasm_module_t;
typedef struct wasm_module_inst_t wasm_module_inst_t;
typedef struct wasm_function_inst_t wasm_function_inst_t;

// Runtime management
wasm_runtime_t* wasm_runtime_init(void);
void wasm_runtime_destroy(void);

// Module operations
wasm_module_t* wasm_runtime_load(const unsigned char* buf, unsigned int size, char* error_buf, unsigned int error_buf_size);
void wasm_runtime_unload(wasm_module_t* module);

// Instance operations
wasm_module_inst_t* wasm_runtime_instantiate(const wasm_module_t* module, unsigned int stack_size, unsigned int heap_size, char* error_buf, unsigned int error_buf_size);
void wasm_runtime_deinstantiate(wasm_module_inst_t* module_inst);

// Function operations
wasm_function_inst_t* wasm_runtime_lookup_function(const wasm_module_inst_t* module_inst, const char* name);
int wasm_runtime_call_wasm(wasm_function_inst_t* function, unsigned int argc, unsigned int* argv);

// Memory operations
void* wasm_runtime_addr_app_to_native(const wasm_module_inst_t* module_inst, unsigned int app_offset);
unsigned int wasm_runtime_addr_native_to_app(const wasm_module_inst_t* module_inst, void* native_ptr);
unsigned int wasm_runtime_get_app_addr_range(const wasm_module_inst_t* module_inst, unsigned int app_offset, unsigned int* p_app_start_offset, unsigned int* p_app_end_offset);

#ifdef __cplusplus
}
#endif

#endif /* WASM_EXPORT_H */
"#;

const PLACEHOLDER_WASM_C_API_H: &str = r#"
#ifndef WASM_C_API_H
#define WASM_C_API_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Basic types
typedef uint8_t byte_t;
typedef float float32_t;
typedef double float64_t;

// Forward declarations
typedef struct wasm_byte_vec_t wasm_byte_vec_t;
typedef struct wasm_name_t wasm_name_t;

// Byte vector
struct wasm_byte_vec_t {
    size_t size;
    byte_t* data;
};

// Name type
struct wasm_name_t {
    wasm_byte_vec_t data;
};

#ifdef __cplusplus
}
#endif

#endif /* WASM_C_API_H */
"#;

const PLACEHOLDER_BINDINGS: &str = r#"
// Placeholder WAMR bindings for development without actual WAMR source

use std::os::raw::{c_char, c_int, c_uint, c_uchar, c_void};

// Type definitions
pub type WasmRuntimeT = c_void;
pub type WasmModuleT = c_void;
pub type WasmModuleInstT = c_void;
pub type WasmFunctionInstT = c_void;

// Function declarations (placeholders)
extern "C" {
    pub fn wasm_runtime_init() -> *mut WasmRuntimeT;
    pub fn wasm_runtime_destroy();
    pub fn wasm_runtime_load(
        buf: *const c_uchar,
        size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleT;
    pub fn wasm_runtime_unload(module: *mut WasmModuleT);
    pub fn wasm_runtime_instantiate(
        module: *const WasmModuleT,
        stack_size: c_uint,
        heap_size: c_uint,
        error_buf: *mut c_char,
        error_buf_size: c_uint,
    ) -> *mut WasmModuleInstT;
    pub fn wasm_runtime_deinstantiate(module_inst: *mut WasmModuleInstT);
    pub fn wasm_runtime_lookup_function(
        module_inst: *const WasmModuleInstT,
        name: *const c_char,
    ) -> *mut WasmFunctionInstT;
    pub fn wasm_runtime_call_wasm(
        function: *mut WasmFunctionInstT,
        argc: c_uint,
        argv: *mut c_uint,
    ) -> c_int;
    pub fn wasm_runtime_addr_app_to_native(
        module_inst: *const WasmModuleInstT,
        app_offset: c_uint,
    ) -> *mut c_void;
    pub fn wasm_runtime_addr_native_to_app(
        module_inst: *const WasmModuleInstT,
        native_ptr: *mut c_void,
    ) -> c_uint;
    pub fn wasm_runtime_get_app_addr_range(
        module_inst: *const WasmModuleInstT,
        app_offset: c_uint,
        p_app_start_offset: *mut c_uint,
        p_app_end_offset: *mut c_uint,
    ) -> c_uint;
}
"#;

const PLACEHOLDER_WAMR_C: &str = r#"
// Placeholder WAMR implementation for development
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

// Placeholder runtime state
static int runtime_initialized = 0;
static int next_handle = 1;

// Placeholder runtime init
void* wasm_runtime_init() {
    runtime_initialized = 1;
    return (void*)(uintptr_t)next_handle++;
}

// Placeholder runtime destroy
void wasm_runtime_destroy() {
    runtime_initialized = 0;
}

// Placeholder module load
void* wasm_runtime_load(const unsigned char* buf, unsigned int size, 
                        char* error_buf, unsigned int error_buf_size) {
    if (!buf || size == 0) {
        if (error_buf && error_buf_size > 0) {
            strncpy(error_buf, "Invalid WebAssembly bytecode", error_buf_size - 1);
            error_buf[error_buf_size - 1] = '\0';
        }
        return NULL;
    }
    return (void*)(uintptr_t)next_handle++;
}

// Placeholder module unload
void wasm_runtime_unload(void* module) {
    // No-op for placeholder
}

// Placeholder module instantiate
void* wasm_runtime_instantiate(const void* module, unsigned int stack_size, 
                               unsigned int heap_size, char* error_buf, 
                               unsigned int error_buf_size) {
    if (!module) {
        if (error_buf && error_buf_size > 0) {
            strncpy(error_buf, "Invalid module", error_buf_size - 1);
            error_buf[error_buf_size - 1] = '\0';
        }
        return NULL;
    }
    return (void*)(uintptr_t)next_handle++;
}

// Placeholder instance destroy
void wasm_runtime_deinstantiate(void* module_inst) {
    // No-op for placeholder
}

// Placeholder function lookup
void* wasm_runtime_lookup_function(const void* module_inst, const char* name) {
    if (!module_inst || !name) {
        return NULL;
    }
    return (void*)(uintptr_t)next_handle++;
}

// Placeholder function call
int wasm_runtime_call_wasm(void* function, unsigned int argc, unsigned int* argv) {
    if (!function) {
        return -1;
    }
    // Return success for placeholder
    return 0;
}

// Placeholder memory access
void* wasm_runtime_addr_app_to_native(const void* module_inst, unsigned int app_offset) {
    static char placeholder_memory[65536]; // 1 page
    if (app_offset < sizeof(placeholder_memory)) {
        return placeholder_memory + app_offset;
    }
    return NULL;
}

// Placeholder native to app address
unsigned int wasm_runtime_addr_native_to_app(const void* module_inst, void* native_ptr) {
    static char placeholder_memory[65536]; // 1 page
    if (native_ptr >= (void*)placeholder_memory && 
        native_ptr < (void*)(placeholder_memory + sizeof(placeholder_memory))) {
        return (unsigned int)((char*)native_ptr - placeholder_memory);
    }
    return 0;
}

// Placeholder address range
unsigned int wasm_runtime_get_app_addr_range(const void* module_inst, 
                                             unsigned int app_offset,
                                             unsigned int* p_app_start_offset, 
                                             unsigned int* p_app_end_offset) {
    if (p_app_start_offset) *p_app_start_offset = 0;
    if (p_app_end_offset) *p_app_end_offset = 65536; // 1 page
    return 65536;
}

// Additional API functions for extended functionality
unsigned int wasm_runtime_get_app_heap_size(const void* module_inst) {
    return 65536; // 1 page default
}

int wasm_runtime_validate_module(const unsigned char* buf, unsigned int size, 
                                 char* error_buf, unsigned int error_buf_size) {
    if (!buf || size < 8) {
        if (error_buf && error_buf_size > 0) {
            strncpy(error_buf, "Invalid WebAssembly bytecode", error_buf_size - 1);
            error_buf[error_buf_size - 1] = '\0';
        }
        return 0; // Validation failed
    }
    // Check WASM magic number
    if (buf[0] != 0x00 || buf[1] != 0x61 || buf[2] != 0x73 || buf[3] != 0x6D) {
        if (error_buf && error_buf_size > 0) {
            strncpy(error_buf, "Invalid WASM magic number", error_buf_size - 1);
            error_buf[error_buf_size - 1] = '\0';
        }
        return 0; // Validation failed
    }
    return 1; // Validation succeeded
}

int wasm_runtime_get_function_signature(const void* function, 
                                        unsigned int* param_count, 
                                        unsigned int* result_count) {
    if (!function) return -1;
    
    // Return placeholder signature information
    if (param_count) *param_count = 1;  // 1 parameter
    if (result_count) *result_count = 1; // 1 result
    return 0; // Success
}

static char last_error_buf[1024] = {0};

const char* wasm_runtime_get_last_error() {
    if (last_error_buf[0] == 0) {
        return NULL;
    }
    return last_error_buf;
}

void wasm_runtime_clear_last_error() {
    last_error_buf[0] = 0;
}
"#;