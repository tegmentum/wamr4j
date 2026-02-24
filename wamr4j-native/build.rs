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
        .define("WAMR_BUILD_JIT", "0")
        .define("WAMR_BUILD_DUMP_CALL_STACK", "1")
        .define("WAMR_BUILD_PERF_PROFILING", "1")
        .define("WAMR_BUILD_MEMORY_PROFILING", "1")
        .define("WAMR_BUILD_THREAD_MGR", "1")
        .define("WAMR_BUILD_LIB_PTHREAD", "1")
        .define("WAMR_BUILD_CUSTOM_NAME_SECTION", "1")
        .define("WAMR_BUILD_LOAD_CUSTOM_SECTION", "1")
        .define("WAMR_BUILD_INSTRUCTION_METERING", "1")
        .define("WAMR_CONFIGURABLE_BOUNDS_CHECKS", "1")
        // HW memory bounds check stays enabled (required by module loader).
        // Stack HW bounds check disabled — its sigaltstack setup conflicts with JVM.
        .define("WAMR_DISABLE_STACK_HW_BOUND_CHECK", "1")
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

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque handle types
typedef void* wasm_module_t;
typedef void* wasm_module_inst_t;
typedef void* wasm_function_inst_t;
typedef void* wasm_exec_env_t;
typedef void* wasm_memory_inst_t;
typedef void* wasm_func_type_t;
typedef uint8_t wasm_valkind_t;

// Structs used by import/export introspection and global access
typedef struct wasm_export_t { const char *name; uint32_t kind; union { void *func_type; void *table_type; void *global_type; void *memory_type; } u; } wasm_export_t;
typedef struct wasm_import_t { const char *module_name; const char *name; uint32_t kind; bool linked; union { void *func_type; void *table_type; void *global_type; void *memory_type; } u; } wasm_import_t;
typedef struct wasm_global_inst_t { wasm_valkind_t kind; bool is_mutable; void *global_data; } wasm_global_inst_t;

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
pub type WasmExecEnvT = c_void;

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
    pub fn wasm_runtime_create_exec_env(
        module_inst: *mut WasmModuleInstT,
        stack_size: c_uint,
    ) -> *mut WasmExecEnvT;
    pub fn wasm_runtime_destroy_exec_env(exec_env: *mut WasmExecEnvT);
    pub fn wasm_runtime_call_wasm(
        exec_env: *mut WasmExecEnvT,
        function: *mut WasmFunctionInstT,
        argc: c_uint,
        argv: *mut c_uint,
    ) -> c_int;
    pub fn wasm_runtime_get_exception(
        module_inst: *mut WasmModuleInstT,
    ) -> *const c_char;
    pub fn wasm_runtime_clear_exception(
        module_inst: *mut WasmModuleInstT,
    );
    pub fn wasm_runtime_addr_app_to_native(
        module_inst: *const WasmModuleInstT,
        app_offset: u64,
    ) -> *mut c_void;
    pub fn wasm_runtime_get_app_addr_range(
        module_inst: *const WasmModuleInstT,
        app_offset: u64,
        p_app_start_offset: *mut u64,
        p_app_end_offset: *mut u64,
    ) -> bool;
}
"#;

const PLACEHOLDER_WAMR_C: &str = r#"
// Placeholder WAMR implementation for development
// Only stubs for functions actually declared in bindings.rs
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

static int next_handle = 1;
static char placeholder_memory[65536];
static const char* placeholder_exception = NULL;

// === Runtime Management ===
void* wasm_runtime_init() { return (void*)(uintptr_t)next_handle++; }
void wasm_runtime_destroy() {}

// === Module Loading ===
void* wasm_runtime_load(const unsigned char* buf, unsigned int size, char* err, unsigned int err_size) {
    if (!buf || size == 0) { if (err && err_size > 0) { strncpy(err, "Invalid bytecode", err_size-1); err[err_size-1]=0; } return NULL; }
    return (void*)(uintptr_t)next_handle++;
}
void wasm_runtime_unload(void* module) {}

// === Instance Operations ===
void* wasm_runtime_instantiate(const void* module, unsigned int ss, unsigned int hs, char* err, unsigned int es) {
    if (!module) { if (err && es > 0) { strncpy(err, "Invalid module", es-1); err[es-1]=0; } return NULL; }
    return (void*)(uintptr_t)next_handle++;
}
void wasm_runtime_deinstantiate(void* inst) {}

// === Execution Environment ===
void* wasm_runtime_create_exec_env(void* inst, unsigned int ss) { return inst ? (void*)(uintptr_t)next_handle++ : NULL; }
void wasm_runtime_destroy_exec_env(void* ee) {}

// === Function Operations ===
void* wasm_runtime_lookup_function(const void* inst, const char* name) {
    return (!inst || !name) ? NULL : (void*)(uintptr_t)next_handle++;
}
int wasm_runtime_call_wasm(void* ee, void* func, unsigned int argc, unsigned int* argv) { return (ee && func) ? 1 : 0; }
bool wasm_runtime_call_wasm_a(void* ee, void* func, unsigned int num_results, void* results, unsigned int num_args, void* args) { return (ee && func) ? true : false; }

// === Exception Handling ===
const char* wasm_runtime_get_exception(void* inst) { return placeholder_exception; }
void wasm_runtime_clear_exception(void* inst) { placeholder_exception = NULL; }
void wasm_runtime_set_exception(void* inst, const char* exception) { (void)inst; (void)exception; }

// === Execution Control ===
void wasm_runtime_terminate(void* inst) { (void)inst; }
void wasm_runtime_set_instruction_count_limit(void* ee, int limit) { (void)ee; (void)limit; }

// === WASI Support ===
void wasm_runtime_set_wasi_args(void* m, const char** dl, unsigned int dc, const char** mdl, unsigned int mdc, const char** e, unsigned int ec, const char** a, int ac) { (void)m; (void)dl; (void)dc; (void)mdl; (void)mdc; (void)e; (void)ec; (void)a; (void)ac; }
void wasm_runtime_set_wasi_args_ex(void* m, const char** dl, unsigned int dc, const char** mdl, unsigned int mdc, const char** e, unsigned int ec, const char** a, int ac, long long si, long long so, long long se) { (void)m; (void)dl; (void)dc; (void)mdl; (void)mdc; (void)e; (void)ec; (void)a; (void)ac; (void)si; (void)so; (void)se; }
void wasm_runtime_set_wasi_addr_pool(void* m, const char** ap, unsigned int aps) { (void)m; (void)ap; (void)aps; }
void wasm_runtime_set_wasi_ns_lookup_pool(void* m, const char** np, unsigned int nps) { (void)m; (void)np; (void)nps; }
bool wasm_runtime_is_wasi_mode(void* inst) { (void)inst; return false; }
unsigned int wasm_runtime_get_wasi_exit_code(void* inst) { (void)inst; return 0; }
void* wasm_runtime_lookup_wasi_start_function(void* inst) { (void)inst; return NULL; }
bool wasm_application_execute_main(void* inst, int argc, char** argv) { (void)inst; (void)argc; (void)argv; return false; }
bool wasm_application_execute_func(void* inst, const char* name, int argc, char** argv) { (void)inst; (void)name; (void)argc; (void)argv; return false; }

// === Custom Data & Context ===
void wasm_runtime_set_custom_data(void* inst, void* data) { (void)inst; (void)data; }
void* wasm_runtime_get_custom_data(void* inst) { (void)inst; return 0; }
void wasm_runtime_set_user_data(void* env, void* data) { (void)env; (void)data; }
void* wasm_runtime_get_user_data(void* env) { (void)env; return 0; }
void* wasm_runtime_get_exec_env_singleton(void* inst) { (void)inst; return 0; }
void* wasm_runtime_get_module_inst(void* env) { (void)env; return 0; }

// === Debugging & Profiling ===
void wasm_runtime_dump_call_stack(void* env) { (void)env; }
unsigned int wasm_runtime_get_call_stack_buf_size(void* env) { (void)env; return 0; }
unsigned int wasm_runtime_dump_call_stack_to_buf(void* env, char* buf, unsigned int len) { (void)env; (void)buf; (void)len; return 0; }
void wasm_runtime_dump_perf_profiling(void* inst) { (void)inst; }
double wasm_runtime_sum_wasm_exec_time(void* inst) { (void)inst; return 0.0; }
double wasm_runtime_get_wasm_func_exec_time(void* inst, const char* name) { (void)inst; (void)name; return 0.0; }
void wasm_runtime_dump_mem_consumption(void* env) { (void)env; }

// === Threading ===
bool wasm_runtime_init_thread_env(void) { return true; }
void wasm_runtime_destroy_thread_env(void) {}
bool wasm_runtime_thread_env_inited(void) { return false; }
void wasm_runtime_set_max_thread_num(unsigned int num) { (void)num; }

// === Function Signature Introspection ===
unsigned int wasm_func_get_param_count(const void* f, const void* i) { return 0; }
unsigned int wasm_func_get_result_count(const void* f, const void* i) { return 0; }
void wasm_func_get_param_types(const void* f, const void* i, unsigned char* t) {}
void wasm_func_get_result_types(const void* f, const void* i, unsigned char* t) {}

// === Function Type Introspection ===
unsigned int wasm_func_type_get_param_count(const void* ft) { return 0; }
unsigned char wasm_func_type_get_param_valkind(const void* ft, unsigned int idx) { return 0; }
unsigned int wasm_func_type_get_result_count(const void* ft) { return 0; }
unsigned char wasm_func_type_get_result_valkind(const void* ft, unsigned int idx) { return 0; }

// === Import/Export Introspection ===
void* wasm_runtime_get_module(void* inst) { return inst; }
unsigned int wasm_runtime_get_export_count(const void* m) { return 0; }
void wasm_runtime_get_export_type(const void* m, int idx, void* et) { if (et) memset(et, 0, 24); }
int wasm_runtime_get_export_global_inst(const void* inst, const char* name, void* gi) { return 0; }
unsigned int wasm_runtime_get_import_count(const void* m) { return 0; }
void wasm_runtime_get_import_type(const void* m, int idx, void* it) { if (it) memset(it, 0, 32); }

// === Memory Operations ===
void* wasm_runtime_addr_app_to_native(const void* inst, uint64_t off) {
    return (off < sizeof(placeholder_memory)) ? placeholder_memory + off : NULL;
}
int wasm_runtime_get_app_addr_range(const void* inst, uint64_t off, uint64_t* s, uint64_t* e) {
    if (s) *s = 0; if (e) *e = 65536; return 1;
}
int wasm_runtime_enlarge_memory(void* inst, uint64_t pages) { return 0; }
void* wasm_runtime_get_default_memory(const void* inst) { return (void*)1; }
uint64_t wasm_memory_get_cur_page_count(const void* mi) { return 1; }
uint64_t wasm_memory_get_max_page_count(const void* mi) { return 256; }
int wasm_memory_get_shared(const void* mi) { return 0; }

// === Version ===
void wasm_runtime_get_version(unsigned int* major, unsigned int* minor, unsigned int* patch) {
    if (major) *major = 2; if (minor) *minor = 4; if (patch) *patch = 4;
}

// === Runtime Configuration ===
bool wasm_runtime_is_running_mode_supported(unsigned int mode) { return mode == 0; }
bool wasm_runtime_set_default_running_mode(unsigned int mode) { return mode == 0; }
bool wasm_runtime_set_running_mode(void* inst, unsigned int mode) { return inst && mode == 0; }
unsigned int wasm_runtime_get_running_mode(void* inst) { return 0; }
void wasm_runtime_set_log_level(int level) {}
bool wasm_runtime_set_bounds_checks(void* inst, bool enable) { return inst != NULL; }
bool wasm_runtime_is_bounds_checks_enabled(void* inst) { return inst != NULL; }

// === Module Management ===
static char module_name_buf[256] = {0};
bool wasm_runtime_set_module_name(void* module, const char* name, char* err, unsigned int err_size) {
    if (!module || !name) return false;
    strncpy(module_name_buf, name, sizeof(module_name_buf)-1);
    module_name_buf[sizeof(module_name_buf)-1] = 0;
    return true;
}
const char* wasm_runtime_get_module_name(void* module) { return module ? module_name_buf : ""; }
bool wasm_runtime_register_module(const char* name, void* module, char* err, unsigned int err_size) { return module != NULL; }
void* wasm_runtime_find_module_registered(const char* name) { return NULL; }
char* wasm_runtime_get_module_hash(void* module) { return module ? "placeholder_hash" : ""; }

// === Package Type Detection ===
#define PACKAGE_TYPE_WASM 0
#define PACKAGE_TYPE_AOT 1
#define PACKAGE_TYPE_UNKNOWN 0xFFFF
unsigned int wasm_runtime_get_file_package_type(const unsigned char* buf, unsigned int size) {
    if (!buf || size < 4) return PACKAGE_TYPE_UNKNOWN;
    if (buf[0]==0x00 && buf[1]==0x61 && buf[2]==0x73 && buf[3]==0x6D) return PACKAGE_TYPE_WASM;
    return PACKAGE_TYPE_UNKNOWN;
}
unsigned int wasm_runtime_get_module_package_type(const void* module) { return module ? PACKAGE_TYPE_WASM : PACKAGE_TYPE_UNKNOWN; }
unsigned int wasm_runtime_get_file_package_version(const unsigned char* buf, unsigned int size) { return (size >= 8) ? 1 : 0; }
unsigned int wasm_runtime_get_module_package_version(const void* module) { return module ? 1 : 0; }
unsigned int wasm_runtime_get_current_package_version(unsigned int pkg_type) { return 1; }
bool wasm_runtime_is_xip_file(const unsigned char* buf, unsigned int size) { return false; }
bool wasm_runtime_is_underlying_binary_freeable(const void* module) { return true; }
void* wasm_runtime_load_ex(unsigned char* buf, unsigned int size, const void* args, char* err, unsigned int err_size) {
    return wasm_runtime_load(buf, size, err, err_size);
}
bool wasm_runtime_resolve_symbols(void* module) { return module != NULL; }

// === Table Operations ===
typedef struct { unsigned char elem_kind; unsigned char _pad[3]; unsigned int cur_size; unsigned int max_size; void* elems; } wasm_table_inst_t;
bool wasm_runtime_get_export_table_inst(const void* inst, const char* name, wasm_table_inst_t* ti) { return false; }
void* wasm_table_get_func_inst(const void* inst, const wasm_table_inst_t* ti, unsigned int idx) { return NULL; }
bool wasm_runtime_call_indirect(void* ee, unsigned int ei, unsigned int argc, unsigned int* argv) { return false; }
unsigned char wasm_table_type_get_elem_kind(const void* tt) { return 129; }
bool wasm_table_type_get_shared(const void* tt) { return false; }
unsigned int wasm_table_type_get_init_size(const void* tt) { return 0; }
unsigned int wasm_table_type_get_max_size(const void* tt) { return 0; }

// === Host Function Registration ===
typedef struct { const char* symbol; void* func_ptr; const char* signature; void* attachment; } NativeSymbol;
bool wasm_runtime_register_natives_raw(const char* mn, NativeSymbol* ns, unsigned int n) { return true; }
bool wasm_runtime_unregister_natives(const char* mn, NativeSymbol* ns) { return true; }
void* wasm_runtime_get_function_attachment(void* ee) { return NULL; }

// === Advanced Memory Operations ===
uint64_t wasm_runtime_module_malloc(void* inst, uint64_t size, void** p_native) {
    if (p_native) *p_native = NULL;
    return 0;
}
void wasm_runtime_module_free(void* inst, uint64_t ptr) {}
uint64_t wasm_runtime_module_dup_data(void* inst, const char* src, uint64_t size) { return 0; }
bool wasm_runtime_validate_app_addr(const void* inst, uint64_t off, uint64_t size) { return off + size <= 65536; }
bool wasm_runtime_validate_app_str_addr(const void* inst, uint64_t off) { return off < 65536; }
bool wasm_runtime_validate_native_addr(const void* inst, void* ptr, uint64_t size) { return ptr != NULL; }
uint64_t wasm_runtime_addr_native_to_app(const void* inst, void* ptr) { return 0; }
bool wasm_runtime_get_native_addr_range(const void* inst, uint8_t* ptr, uint8_t** start, uint8_t** end) {
    if (start) *start = NULL;
    if (end) *end = NULL;
    return false;
}
void* wasm_runtime_get_memory(const void* inst, unsigned int idx) { return idx == 0 ? (void*)1 : NULL; }
void* wasm_memory_get_base_address(const void* mi) { return (void*)placeholder_memory; }
uint64_t wasm_memory_get_bytes_per_page(const void* mi) { return 65536; }
bool wasm_memory_enlarge(void* mi, uint64_t pages) { return false; }

// === Advanced Instantiation & Miscellaneous ===
typedef struct { unsigned int default_stack_size; unsigned int host_managed_heap_size; unsigned int max_memory_pages; } InstantiationArgs;
void* wasm_runtime_instantiate_ex(const void* module, const InstantiationArgs* args, char* err, unsigned int es) {
    if (!module || !args) { if (err && es > 0) { strncpy(err, "Invalid args", es-1); err[es-1]=0; } return NULL; }
    return wasm_runtime_instantiate(module, args->default_stack_size, args->host_managed_heap_size, err, es);
}
const unsigned char* wasm_runtime_get_custom_section(const void* module, const char* name, unsigned int* len) {
    (void)module; (void)name; if (len) *len = 0; return NULL;
}
void* wasm_runtime_malloc(unsigned int size) { return malloc(size); }
void* wasm_runtime_realloc(void* ptr, unsigned int size) { return realloc(ptr, size); }
void wasm_runtime_free(void* ptr) { free(ptr); }

// === Has Memory Check (no Box allocation) ===
// wamr_instance_has_memory is implemented in Rust FFI, not here
"#;