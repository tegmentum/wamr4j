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

//! JNI bindings for the WAMR WebAssembly runtime
//!
//! This module provides JNI function exports that correspond to the native
//! methods declared in the Java JNI implementation classes.

use jni::objects::{JClass, JObject, JByteArray, JString, JObjectArray, JValue};
use jni::sys::{jlong, jlongArray, jstring, jint, jfloat, jdouble, jboolean, jobject, jobjectArray, jbyteArray, JNI_VERSION_1_8};
use jni::{JNIEnv, JavaVM};
use once_cell::sync::OnceCell;
use std::ffi::CStr;
use std::os::raw::c_void;
use std::ptr;

use crate::bindings;
use crate::runtime;
use crate::types::{WamrRuntime, WamrModule, WamrInstance, WamrFunction, WamrMemory, WamrTable, WasmValue, WasmType, NativeRegistration};

// =============================================================================
// Global State
// =============================================================================

/// Global JavaVM reference - set during JNI_OnLoad
static JAVA_VM: OnceCell<JavaVM> = OnceCell::new();

/// JNI_OnLoad - Called when the native library is loaded
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _reserved: *mut c_void) -> jint {
    if JAVA_VM.set(vm).is_err() {
        eprintln!("ERROR: Failed to set JavaVM - already initialized");
        return 0;
    }
    JNI_VERSION_1_8
}

// =============================================================================
// JniWebAssemblyRuntime
// =============================================================================

/// Create a new WAMR runtime instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeCreateRuntime<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    crate::utils::clear_last_error();
    match runtime::runtime_init() {
        Ok(rt) => Box::into_raw(Box::new(rt)) as jlong,
        Err(_) => 0,
    }
}

/// Destroy a WAMR runtime instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeDestroyRuntime<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    runtime_handle: jlong,
) {
    if runtime_handle != 0 {
        unsafe {
            let _runtime = Box::from_raw(runtime_handle as *mut WamrRuntime);
        }
    }
}

/// Compile a WebAssembly module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeCompileModule<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    runtime_handle: jlong,
    wasm_bytes: JByteArray<'local>,
) -> jlong {
    if runtime_handle == 0 {
        return 0;
    }

    let bytes = match env.convert_byte_array(&wasm_bytes) {
        Ok(bytes) => bytes,
        Err(_) => return 0,
    };

    unsafe {
        let runtime_ref = &*(runtime_handle as *const WamrRuntime);
        match runtime::module_compile(runtime_ref, &bytes) {
            Ok(module) => Box::into_raw(Box::new(module)) as jlong,
            Err(_) => 0,
        }
    }
}

/// Get WAMR version string (from actual runtime API)
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let (major, minor, patch) = runtime::runtime_get_version();
    let version = format!("{}.{}.{}", major, minor, patch);
    match env.new_string(&version) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

/// Get WAMR major version number
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetMajorVersion<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    let (major, _, _) = runtime::runtime_get_version();
    major as jint
}

/// Get WAMR minor version number
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetMinorVersion<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    let (_, minor, _) = runtime::runtime_get_version();
    minor as jint
}

/// Get WAMR patch version number
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetPatchVersion<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    let (_, _, patch) = runtime::runtime_get_version();
    patch as jint
}

/// Check if a running mode is supported
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeIsRunningModeSupported<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    mode: jint,
) -> jboolean {
    if runtime::is_running_mode_supported(mode as u32) { 1 } else { 0 }
}

/// Set the default running mode for new module instances
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeSetDefaultRunningMode<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    mode: jint,
) -> jboolean {
    if runtime::set_default_running_mode(mode as u32) { 1 } else { 0 }
}

/// Set log verbosity level (0-5)
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeSetLogLevel<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
) {
    runtime::set_log_level(level as i32);
}

/// Initialize the thread environment for the current native thread.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeInitThreadEnv<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    if runtime::init_thread_env() { 1 } else { 0 }
}

/// Destroy the thread environment for the current native thread.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeDestroyThreadEnv<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    runtime::destroy_thread_env();
}

/// Check if the thread environment has been initialized.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeIsThreadEnvInited<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    if runtime::is_thread_env_inited() { 1 } else { 0 }
}

/// Set the maximum number of threads.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeSetMaxThreadNum<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    num: jint,
) {
    if num >= 0 {
        runtime::set_max_thread_num(num as u32);
    }
}

/// Check if an import function is linked.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeIsImportFuncLinked<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_name: JString<'local>,
    func_name: JString<'local>,
) -> jboolean {
    if module_name.is_null() || func_name.is_null() {
        return 0;
    }
    let mod_str: String = match env.get_string(&module_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let func_str: String = match env.get_string(&func_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    if runtime::is_import_func_linked(&mod_str, &func_str) { 1 } else { 0 }
}

/// Check if an import global is linked.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeIsImportGlobalLinked<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_name: JString<'local>,
    global_name: JString<'local>,
) -> jboolean {
    if module_name.is_null() || global_name.is_null() {
        return 0;
    }
    let mod_str: String = match env.get_string(&module_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let global_str: String = match env.get_string(&global_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    if runtime::is_import_global_linked(&mod_str, &global_str) { 1 } else { 0 }
}

// =============================================================================
// JniWebAssemblyModule
// =============================================================================

/// Set the name of a module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeSetModuleName<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    name: JString<'local>,
) -> jboolean {
    if module_handle == 0 || name.is_null() {
        return 0;
    }
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        if runtime::module_set_name(module_ref, &name_str) { 1 } else { 0 }
    }
}

/// Get the name of a module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetModuleName<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
) -> jstring {
    if module_handle == 0 {
        return ptr::null_mut();
    }
    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        match runtime::module_get_name(module_ref) {
            Some(name) => match env.new_string(&name) {
                Ok(s) => s.into_raw(),
                Err(_) => ptr::null_mut(),
            },
            None => ptr::null_mut(),
        }
    }
}

// Get the package type of a loaded module
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetPackageType,
    WamrModule, jint, bindings::PACKAGE_TYPE_UNKNOWN as jint, |r: &WamrModule| {
    runtime::get_module_package_type(r) as jint
});

// Get the package version of a loaded module
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetPackageVersion,
    WamrModule, jint, 0, |r: &WamrModule| {
    runtime::get_module_package_version(r) as jint
});

// Check if the underlying binary is freeable
jni_bool_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeIsUnderlyingBinaryFreeable,
    WamrModule, runtime::is_underlying_binary_freeable);

/// Get file package type from WASM bytecode
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetFilePackageType<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    wasm_bytes: JByteArray<'local>,
) -> jint {
    if wasm_bytes.is_null() {
        return bindings::PACKAGE_TYPE_UNKNOWN as jint;
    }
    let bytes = match env.convert_byte_array(&wasm_bytes) {
        Ok(b) => b,
        Err(_) => return bindings::PACKAGE_TYPE_UNKNOWN as jint,
    };
    runtime::get_file_package_type(&bytes) as jint
}

/// Get the currently supported version for a package type
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetCurrentPackageVersion<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    package_type: jint,
) -> jint {
    runtime::get_current_package_version(package_type as u32) as jint
}

/// Destroy a WebAssembly module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeDestroyModule<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
) {
    if module_handle != 0 {
        unsafe {
            let _module = Box::from_raw(module_handle as *mut WamrModule);
        }
    }
}

/// Instantiate a WebAssembly module (no imports)
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeInstantiateModule<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
) -> jlong {
    if module_handle == 0 {
        return 0;
    }

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);

        let stack_size = crate::types::DEFAULT_STACK_SIZE;
        let heap_size = crate::types::DEFAULT_HEAP_SIZE;
        match runtime::instance_create(module_ref, stack_size, heap_size) {
            Ok(instance) => Box::into_raw(Box::new(instance)) as jlong,
            Err(_) => 0,
        }
    }
}

// =============================================================================
// Host Function Registration (JNI)
// =============================================================================

/// Per-function JNI callback data. Holds a global reference to the Java
/// HostFunction object and the WASM type information needed for conversions.
struct JniCallbackData {
    /// Global reference to the Java HostFunction object
    host_function_ref: jni::objects::GlobalRef,
    /// WASM type tags for parameters (0=i32, 1=i64, 2=f32, 3=f64)
    param_types: Vec<u8>,
    /// WASM type tags for results
    result_types: Vec<u8>,
}

/// Holds both the WAMR native registration and the JNI callback data.
/// Dropping this unregisters the native functions and frees the global refs.
struct JniImportRegistration {
    #[allow(dead_code)]
    native_registration: NativeRegistration,
    /// Boxed callback data — must outlive the native_registration because
    /// the trampoline references them via user_data pointers.
    _callback_data: Vec<Box<JniCallbackData>>,
}

/// The JNI host callback function. Called by the WAMR trampoline via
/// HostFunctionContext.callback_fn. Bridges WAMR → Java HostFunction.execute().
unsafe extern "C" fn jni_host_callback(
    user_data: *mut c_void,
    args: *const u64,
    param_count: u32,
    result: *mut u64,
) -> i32 {
    if user_data.is_null() {
        return -1;
    }

    let data = &*(user_data as *const JniCallbackData);

    // Get the JavaVM and attach the current thread
    let vm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => return -1,
    };

    let mut env = match vm.attach_current_thread_as_daemon() {
        Ok(env) => env,
        Err(_) => return -1,
    };

    // Build Java Object[] from raw args based on param_types
    let obj_array = match env.new_object_array(
        param_count as i32,
        "java/lang/Object",
        &JObject::null(),
    ) {
        Ok(arr) => arr,
        Err(_) => return -1,
    };

    for i in 0..param_count as usize {
        let arg_val = *args.add(i);
        let java_obj = match data.param_types.get(i).copied().unwrap_or(0) {
            0 => box_integer(&mut env, arg_val as i32),   // I32
            1 => box_long(&mut env, arg_val as i64),      // I64
            2 => box_float(&mut env, f32::from_bits(arg_val as u32)),  // F32
            3 => box_double(&mut env, f64::from_bits(arg_val)),        // F64
            _ => box_integer(&mut env, arg_val as i32),
        };
        let obj = JObject::from_raw(java_obj);
        let _ = env.set_object_array_element(&obj_array, i as i32, &obj);
    }

    // Call HostFunction.execute(Object... args) -> Object
    let result_obj = env.call_method(
        data.host_function_ref.as_obj(),
        "execute",
        "([Ljava/lang/Object;)Ljava/lang/Object;",
        &[JValue::Object(&JObject::from(obj_array))],
    );

    let result_obj = match result_obj {
        Ok(v) => match v.l() {
            Ok(obj) => obj,
            Err(_) => return -1,
        },
        Err(_) => {
            // Check if a Java exception was thrown
            if env.exception_check().unwrap_or(false) {
                env.exception_clear().ok();
            }
            return -1;
        }
    };

    // Convert return value to uint64 based on result_types
    if !data.result_types.is_empty() && !result_obj.is_null() {
        let val: u64 = match data.result_types[0] {
            0 => unbox_integer(&mut env, &result_obj) as u32 as u64,  // I32
            1 => unbox_long(&mut env, &result_obj) as u64,            // I64
            2 => unbox_float(&mut env, &result_obj).to_bits() as u64, // F32
            3 => unbox_double(&mut env, &result_obj).to_bits(),       // F64
            _ => 0,
        };
        *result = val;
    }

    0 // success
}

/// Register host functions for a set of import modules.
/// Called from JniWebAssemblyModule before instantiation.
///
/// The imports parameter is a Map<String, Map<String, HostFunction>>.
/// Returns a registration handle (jlong) or 0 on failure.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeRegisterHostFunctions<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    imports: JObject<'local>,
) -> jlong {
    if imports.is_null() {
        return 0;
    }

    // Parse Map<String, Map<String, HostFunction>>
    // We'll collect all registrations (one per module name)
    let mut registrations: Vec<JniImportRegistration> = Vec::new();

    // Get the outer Map's entrySet
    let outer_entry_set = match env.call_method(&imports, "entrySet", "()Ljava/util/Set;", &[]) {
        Ok(v) => match v.l() { Ok(o) => o, Err(_) => return 0 },
        Err(_) => return 0,
    };
    let outer_iterator = match env.call_method(&outer_entry_set, "iterator", "()Ljava/util/Iterator;", &[]) {
        Ok(v) => match v.l() { Ok(o) => o, Err(_) => return 0 },
        Err(_) => return 0,
    };

    loop {
        let has_next = match env.call_method(&outer_iterator, "hasNext", "()Z", &[]) {
            Ok(v) => match v.z() { Ok(b) => b, Err(_) => break },
            Err(_) => break,
        };
        if !has_next {
            break;
        }

        let entry = match env.call_method(&outer_iterator, "next", "()Ljava/lang/Object;", &[]) {
            Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
            Err(_) => continue,
        };

        // module_name = entry.getKey()
        let module_name_obj = match env.call_method(&entry, "getKey", "()Ljava/lang/Object;", &[]) {
            Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
            Err(_) => continue,
        };
        let module_name: String = match env.get_string(&JString::from(module_name_obj)) {
            Ok(s) => s.into(),
            Err(_) => continue,
        };

        // inner_map = entry.getValue()
        let inner_map = match env.call_method(&entry, "getValue", "()Ljava/lang/Object;", &[]) {
            Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
            Err(_) => continue,
        };

        // Iterate inner Map<String, HostFunction>
        let inner_entry_set = match env.call_method(&inner_map, "entrySet", "()Ljava/util/Set;", &[]) {
            Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
            Err(_) => continue,
        };
        let inner_iterator = match env.call_method(&inner_entry_set, "iterator", "()Ljava/util/Iterator;", &[]) {
            Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
            Err(_) => continue,
        };

        let mut functions: Vec<(String, Vec<u8>, Vec<u8>, crate::types::HostCallbackFn, *mut c_void)> = Vec::new();
        let mut callback_data_list: Vec<Box<JniCallbackData>> = Vec::new();

        loop {
            let has_next = match env.call_method(&inner_iterator, "hasNext", "()Z", &[]) {
                Ok(v) => match v.z() { Ok(b) => b, Err(_) => break },
                Err(_) => break,
            };
            if !has_next {
                break;
            }

            let inner_entry = match env.call_method(&inner_iterator, "next", "()Ljava/lang/Object;", &[]) {
                Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
                Err(_) => continue,
            };

            // func_name = inner_entry.getKey()
            let func_name_obj = match env.call_method(&inner_entry, "getKey", "()Ljava/lang/Object;", &[]) {
                Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
                Err(_) => continue,
            };
            let func_name: String = match env.get_string(&JString::from(func_name_obj)) {
                Ok(s) => s.into(),
                Err(_) => continue,
            };

            // host_function = inner_entry.getValue()
            let host_func = match env.call_method(&inner_entry, "getValue", "()Ljava/lang/Object;", &[]) {
                Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
                Err(_) => continue,
            };

            // Get FunctionSignature from HostFunction.getSignature()
            let signature = match env.call_method(&host_func, "getSignature", "()Lai/tegmentum/wamr4j/FunctionSignature;", &[]) {
                Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
                Err(_) => continue,
            };

            // Get parameterTypes array from FunctionSignature
            let param_types_arr = match env.call_method(&signature, "getParameterTypes", "()[Lai/tegmentum/wamr4j/ValueType;", &[]) {
                Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
                Err(_) => continue,
            };
            let param_types = extract_value_type_array(&mut env, &param_types_arr);

            // Get returnTypes array from FunctionSignature
            let result_types_arr = match env.call_method(&signature, "getReturnTypes", "()[Lai/tegmentum/wamr4j/ValueType;", &[]) {
                Ok(v) => match v.l() { Ok(o) => o, Err(_) => continue },
                Err(_) => continue,
            };
            let result_types = extract_value_type_array(&mut env, &result_types_arr);

            // Create a global reference to the HostFunction so it survives GC
            let host_func_global = match env.new_global_ref(&host_func) {
                Ok(g) => g,
                Err(_) => continue,
            };

            let cb_data = Box::new(JniCallbackData {
                host_function_ref: host_func_global,
                param_types: param_types.clone(),
                result_types: result_types.clone(),
            });

            let user_data = &*cb_data as *const JniCallbackData as *mut c_void;

            functions.push((func_name, param_types, result_types, jni_host_callback, user_data));
            callback_data_list.push(cb_data);
        }

        if functions.is_empty() {
            continue;
        }

        // Register with WAMR
        match runtime::register_host_functions(&module_name, functions) {
            Ok(native_reg) => {
                registrations.push(JniImportRegistration {
                    native_registration: native_reg,
                    _callback_data: callback_data_list,
                });
            }
            Err(_) => {
                // Registration failed — callback_data_list will be dropped, freeing global refs
                return 0;
            }
        }
    }

    if registrations.is_empty() {
        return 0;
    }

    Box::into_raw(Box::new(registrations)) as jlong
}

/// Destroy a host function registration, unregistering all functions and freeing JNI global refs.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDestroyRegistration<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    registration_handle: jlong,
) {
    if registration_handle != 0 {
        unsafe {
            let _ = Box::from_raw(registration_handle as *mut Vec<JniImportRegistration>);
        }
    }
}

/// Extract a ValueType[] Java array into a Vec<u8> of WASM type tags.
/// ValueType enum ordinals: I32=0, I64=1, F32=2, F64=3
fn extract_value_type_array(env: &mut JNIEnv, arr: &JObject) -> Vec<u8> {
    if arr.is_null() {
        return Vec::new();
    }
    let jarray: jni::objects::JObjectArray = unsafe { JObject::from_raw(arr.as_raw()).into() };
    let len = match env.get_array_length(&jarray) {
        Ok(l) => l as usize,
        Err(_) => return Vec::new(),
    };
    let mut types = Vec::with_capacity(len);
    for i in 0..len {
        let elem = match env.get_object_array_element(&jarray, i as i32) {
            Ok(o) => o,
            Err(_) => {
                types.push(0); // fallback to I32
                continue;
            }
        };
        // Call ValueType.ordinal() to get the type tag
        let ordinal = match env.call_method(&elem, "ordinal", "()I", &[]) {
            Ok(v) => match v.i() { Ok(i) => i as u8, Err(_) => 0 },
            Err(_) => 0,
        };
        types.push(ordinal);
    }
    types
}

/// Get names of all exports from a module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetExportNames<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
) -> jobjectArray {
    if module_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        let export_count = bindings::wasm_runtime_get_export_count(module_ref.handle);
        let mut names: Vec<String> = Vec::new();

        for i in 0..export_count {
            let mut export_info: bindings::wasm_export_t = std::mem::zeroed();
            bindings::wasm_runtime_get_export_type(module_ref.handle, i as i32, &mut export_info);
            if !export_info.name.is_null() {
                if let Ok(name) = CStr::from_ptr(export_info.name).to_str() {
                    names.push(name.to_string());
                }
            }
        }

        create_string_array(&mut env, &names)
    }
}

/// Get names of all imports from a module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetImportNames<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
) -> jobjectArray {
    if module_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        let import_count = bindings::wasm_runtime_get_import_count(module_ref.handle);
        let mut names: Vec<String> = Vec::new();

        for i in 0..import_count {
            let mut import_info: bindings::wasm_import_t = std::mem::zeroed();
            bindings::wasm_runtime_get_import_type(module_ref.handle, i as i32, &mut import_info);
            if !import_info.name.is_null() {
                let name = CStr::from_ptr(import_info.name).to_string_lossy();
                if !import_info.module_name.is_null() {
                    let module_name = CStr::from_ptr(import_info.module_name).to_string_lossy();
                    names.push(format!("{}.{}", module_name, name));
                } else {
                    names.push(name.into_owned());
                }
            }
        }

        create_string_array(&mut env, &names)
    }
}

/// Get the function signature for an exported function by name
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetExportFunctionSignature<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    function_name: JString<'local>,
) -> jobject {
    if module_handle == 0 || function_name.is_null() {
        return ptr::null_mut();
    }

    let name: String = match env.get_string(&function_name) {
        Ok(s) => s.into(),
        Err(_) => return ptr::null_mut(),
    };

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        let export_count = bindings::wasm_runtime_get_export_count(module_ref.handle);

        for i in 0..export_count {
            let mut export_info: bindings::wasm_export_t = std::mem::zeroed();
            bindings::wasm_runtime_get_export_type(module_ref.handle, i as i32, &mut export_info);

            if export_info.kind != bindings::WASM_IMPORT_EXPORT_KIND_FUNC {
                continue;
            }
            if export_info.name.is_null() {
                continue;
            }
            let export_name = match CStr::from_ptr(export_info.name).to_str() {
                Ok(s) => s,
                Err(_) => continue,
            };
            if export_name != name {
                continue;
            }

            // Found the function export — get its type info
            let func_type = export_info.type_ptr as *const bindings::WasmFuncTypeT;
            if func_type.is_null() {
                return ptr::null_mut();
            }

            let param_count = bindings::wasm_func_type_get_param_count(func_type);
            let result_count = bindings::wasm_func_type_get_result_count(func_type);

            let mut param_kinds = Vec::with_capacity(param_count as usize);
            for j in 0..param_count {
                param_kinds.push(bindings::wasm_func_type_get_param_valkind(func_type, j));
            }

            let mut result_kinds = Vec::with_capacity(result_count as usize);
            for j in 0..result_count {
                result_kinds.push(bindings::wasm_func_type_get_result_valkind(func_type, j));
            }

            return create_function_signature(&mut env, &param_kinds, &result_kinds);
        }
    }

    ptr::null_mut()
}

// =============================================================================
// JniWebAssemblyInstance
// =============================================================================

/// Destroy a WebAssembly instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDestroyInstance<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) {
    if instance_handle != 0 {
        unsafe {
            let _instance = Box::from_raw(instance_handle as *mut WamrInstance);
        }
    }
}

/// Get a function from an instance by name
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetFunction<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    function_name: JString<'local>,
) -> jlong {
    if instance_handle == 0 || function_name.is_null() {
        return 0;
    }

    let name: String = match env.get_string(&function_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::function_lookup(instance_ref, &name) {
            Ok(function) => Box::into_raw(Box::new(function)) as jlong,
            Err(_) => 0,
        }
    }
}

/// Get memory from an instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetMemory<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> jlong {
    if instance_handle == 0 {
        return 0;
    }

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::memory_get(instance_ref) {
            Ok(memory) => Box::into_raw(Box::new(memory)) as jlong,
            Err(_) => 0,
        }
    }
}

/// Get a global variable value
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetGlobal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    global_name: JString<'local>,
) -> jobject {
    if instance_handle == 0 || global_name.is_null() {
        return ptr::null_mut();
    }

    let name: String = match env.get_string(&global_name) {
        Ok(s) => s.into(),
        Err(_) => return ptr::null_mut(),
    };

    let c_name = match std::ffi::CString::new(name.clone()) {
        Ok(s) => s,
        Err(_) => return ptr::null_mut(),
    };

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::global_get(instance_ref.handle, c_name.as_ptr()) {
            Ok(wasm_val) => wasm_value_to_jobject(&mut env, &wasm_val),
            Err(e) => {
                let _ = throw_wasm_exception(&mut env, &format!("{}", e));
                ptr::null_mut()
            }
        }
    }
}

/// Set a global variable value
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetGlobal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    global_name: JString<'local>,
    value: JObject<'local>,
) {
    if instance_handle == 0 || global_name.is_null() || value.is_null() {
        return;
    }

    let name: String = match env.get_string(&global_name) {
        Ok(s) => s.into(),
        Err(_) => return,
    };

    let c_name = match std::ffi::CString::new(name.clone()) {
        Ok(s) => s,
        Err(_) => return,
    };

    // Convert Java boxed value to WasmValue
    let wasm_val = if is_instance_of(&mut env, &value, "java/lang/Integer") {
        WasmValue::I32(unbox_integer(&mut env, &value))
    } else if is_instance_of(&mut env, &value, "java/lang/Long") {
        WasmValue::I64(unbox_long(&mut env, &value))
    } else if is_instance_of(&mut env, &value, "java/lang/Float") {
        WasmValue::F32(unbox_float(&mut env, &value))
    } else if is_instance_of(&mut env, &value, "java/lang/Double") {
        WasmValue::F64(unbox_double(&mut env, &value))
    } else {
        let _ = throw_wasm_exception(&mut env, "Unsupported value type for global");
        return;
    };

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        if let Err(e) = runtime::global_set(instance_ref.handle, c_name.as_ptr(), &wasm_val) {
            let _ = throw_wasm_exception(&mut env, &format!("{}", e));
        }
    }
}

/// Get names of all exported functions from an instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetFunctionNames<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> jobjectArray {
    if instance_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        let names = runtime::get_export_names_by_kind(
            instance_ref.handle, bindings::WASM_IMPORT_EXPORT_KIND_FUNC);
        create_string_array(&mut env, &names)
    }
}

/// Get names of all exported globals from an instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetGlobalNames<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> jobjectArray {
    if instance_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        let names = runtime::get_export_names_by_kind(
            instance_ref.handle, bindings::WASM_IMPORT_EXPORT_KIND_GLOBAL);
        create_string_array(&mut env, &names)
    }
}

/// Set the running mode for an instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetRunningMode<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    mode: jint,
) -> jboolean {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        if runtime::set_running_mode(instance_ref, mode as u32) { 1 } else { 0 }
    }
}

// Get the running mode for an instance
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetRunningMode,
    WamrInstance, jint, -1, |r: &WamrInstance| {
    runtime::get_running_mode(r) as jint
});

/// Enable or disable bounds checks for an instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetBoundsChecks<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    enable: jboolean,
) -> jboolean {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        if runtime::set_bounds_checks(instance_ref, enable != 0) { 1 } else { 0 }
    }
}

// Check if bounds checks are enabled for an instance
jni_bool_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeIsBoundsChecksEnabled,
    WamrInstance, runtime::is_bounds_checks_enabled);

/// Get an exported table by name
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetTable<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    table_name: JString<'local>,
) -> jlong {
    if instance_handle == 0 || table_name.is_null() {
        return 0;
    }

    let name: String = match env.get_string(&table_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::table_get(instance_ref, &name) {
            Ok(table) => Box::into_raw(Box::new(table)) as jlong,
            Err(_) => 0,
        }
    }
}

/// Get names of all exported tables from an instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetTableNames<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> jobjectArray {
    if instance_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        let names = runtime::get_export_names_by_kind(
            instance_ref.handle, bindings::WASM_IMPORT_EXPORT_KIND_TABLE);
        create_string_array(&mut env, &names)
    }
}

/// Allocate memory within the module instance heap
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeModuleMalloc<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    size: jlong,
) -> jlong {
    if instance_handle == 0 || size <= 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::module_malloc(instance_ref, size as u64) {
            Ok((app_offset, _)) => app_offset as jlong,
            Err(_) => 0,
        }
    }
}

/// Free memory previously allocated by module_malloc
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeModuleFree<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    ptr: jlong,
) {
    if instance_handle == 0 || ptr <= 0 {
        return;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        runtime::module_free(instance_ref, ptr as u64);
    }
}

/// Duplicate data into the module instance memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeModuleDupData<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    data: JByteArray<'local>,
) -> jlong {
    if instance_handle == 0 || data.is_null() {
        return 0;
    }
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::module_dup_data(instance_ref, &bytes) {
            Ok(offset) => offset as jlong,
            Err(_) => 0,
        }
    }
}

/// Validate an application address range
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeValidateAppAddr<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    app_offset: jlong,
    size: jlong,
) -> jboolean {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        if runtime::validate_app_addr(instance_ref, app_offset as u64, size as u64) { 1 } else { 0 }
    }
}

/// Validate an application string address
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeValidateAppStrAddr<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    app_str_offset: jlong,
) -> jboolean {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        if runtime::validate_app_str_addr(instance_ref, app_str_offset as u64) { 1 } else { 0 }
    }
}

/// Get memory instance by index
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetMemoryByIndex<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    index: jint,
) -> jlong {
    if instance_handle == 0 || index < 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::memory_get_by_index(instance_ref, index as u32) {
            Ok(memory) => Box::into_raw(Box::new(memory)) as jlong,
            Err(_) => 0,
        }
    }
}

// Get memory base address as a long (native pointer)
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGetBaseAddress,
    WamrMemory, jlong, 0, |r: &WamrMemory| {
    runtime::memory_base_address(r) as jlong
});

// Get bytes per page for a memory instance
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGetBytesPerPage,
    WamrMemory, jlong, 0, |r: &WamrMemory| {
    runtime::memory_bytes_per_page(r) as jlong
});

// Check if the instance exports memory
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeHasMemory,
    WamrInstance, jni::sys::jboolean, 0, |r: &WamrInstance| {
    if crate::bindings::wasm_runtime_get_default_memory(r.handle).is_null() { 0 } else { 1 }
});

// =============================================================================
// Exception & Execution Control
// =============================================================================

/// Get the current exception on an instance. Returns null if no exception.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetException<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> jstring {
    if instance_handle == 0 {
        return ptr::null_mut();
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::instance_get_exception(instance_ref) {
            Some(msg) => {
                match env.new_string(&msg) {
                    Ok(s) => s.into_raw(),
                    Err(_) => ptr::null_mut(),
                }
            }
            None => ptr::null_mut(),
        }
    }
}

/// Set a custom exception on an instance.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetException<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    exception: JString<'local>,
) {
    if instance_handle == 0 || exception.is_null() {
        return;
    }
    let exception_str: String = match env.get_string(&exception) {
        Ok(s) => s.into(),
        Err(_) => return,
    };
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        let _ = runtime::instance_set_exception(instance_ref, &exception_str);
    }
}

// Clear the current exception on an instance.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeClearException,
    WamrInstance, runtime::instance_clear_exception);

// Terminate execution of an instance.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeTerminate,
    WamrInstance, runtime::instance_terminate);

// Set the instruction count limit for an instance's execution environment.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetInstructionCountLimit,
    WamrInstance, limit: jlong, |r: &WamrInstance, l: jlong| {
    runtime::set_instruction_count_limit(r, l as i32);
});

// =============================================================================
// WASI Support (JniWebAssemblyModule)
// =============================================================================

/// Configure WASI arguments on a module.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeSetWasiArgs<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    dir_list: JObjectArray<'local>,
    map_dir_list: JObjectArray<'local>,
    env_vars: JObjectArray<'local>,
    argv: JObjectArray<'local>,
) {
    if module_handle == 0 {
        return;
    }
    let dirs = jni_string_array_to_vec(&mut env, &dir_list);
    let map_dirs = jni_string_array_to_vec(&mut env, &map_dir_list);
    let envs = jni_string_array_to_vec(&mut env, &env_vars);
    let args = jni_string_array_to_vec(&mut env, &argv);

    let dir_refs: Vec<&str> = dirs.iter().map(|s| s.as_str()).collect();
    let map_dir_refs: Vec<&str> = map_dirs.iter().map(|s| s.as_str()).collect();
    let env_refs: Vec<&str> = envs.iter().map(|s| s.as_str()).collect();
    let arg_refs: Vec<&str> = args.iter().map(|s| s.as_str()).collect();

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        let _ = runtime::module_set_wasi_args(module_ref, &dir_refs, &map_dir_refs, &env_refs, &arg_refs);
    }
}

/// Configure WASI address pool on a module.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeSetWasiAddrPool<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    addr_pool: JObjectArray<'local>,
) {
    if module_handle == 0 {
        return;
    }
    let addrs = jni_string_array_to_vec(&mut env, &addr_pool);
    let addr_refs: Vec<&str> = addrs.iter().map(|s| s.as_str()).collect();

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        let _ = runtime::module_set_wasi_addr_pool(module_ref, &addr_refs);
    }
}

/// Configure WASI NS lookup pool on a module.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeSetWasiNsLookupPool<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    ns_lookup_pool: JObjectArray<'local>,
) {
    if module_handle == 0 {
        return;
    }
    let ns = jni_string_array_to_vec(&mut env, &ns_lookup_pool);
    let ns_refs: Vec<&str> = ns.iter().map(|s| s.as_str()).collect();

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        let _ = runtime::module_set_wasi_ns_lookup_pool(module_ref, &ns_refs);
    }
}

// =============================================================================
// Advanced Instantiation — JniWebAssemblyModule
// =============================================================================

/// Instantiate a module with extended arguments.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeInstantiateEx<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    default_stack_size: jint,
    host_managed_heap_size: jint,
    max_memory_pages: jint,
) -> jlong {
    if module_handle == 0 {
        let _ = env.throw_new("ai/tegmentum/wamr4j/exception/WasmRuntimeException",
            "Module handle is null");
        return 0;
    }
    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        match runtime::instance_create_ex(
            module_ref,
            default_stack_size as u32,
            host_managed_heap_size as u32,
            max_memory_pages as u32,
        ) {
            Ok(instance) => Box::into_raw(instance) as jlong,
            Err(e) => {
                let msg = format!("{}", e);
                let _ = env.throw_new("ai/tegmentum/wamr4j/exception/WasmRuntimeException", &msg);
                0
            }
        }
    }
}

/// Get a custom section from a module by name.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetCustomSection<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    name: JString<'local>,
) -> JByteArray<'local> {
    if module_handle == 0 || name.is_null() {
        return JByteArray::default();
    }
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return JByteArray::default(),
    };
    let module_ref = unsafe { &*(module_handle as *const WamrModule) };
    let data = runtime::module_get_custom_section(module_ref, &name_str);
    if data.is_empty() {
        return JByteArray::default();
    }
    match env.byte_array_from_slice(&data) {
        Ok(arr) => arr,
        Err(_) => JByteArray::default(),
    }
}

// =============================================================================
// Phase 14: Type Introspection (JniWebAssemblyModule)
// =============================================================================

/// Get global type info for an exported global.
/// Returns an int array [valkind, is_mutable] or null if not found.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetExportGlobalTypeInfo<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    name: JString<'local>,
) -> jobject {
    if module_handle == 0 || name.is_null() {
        return ptr::null_mut();
    }
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return ptr::null_mut(),
    };
    let module_ref = unsafe { &*(module_handle as *const WamrModule) };
    match runtime::get_export_global_type_info(module_ref, &name_str) {
        Some((valkind, is_mutable)) => {
            let arr = match env.new_int_array(2) {
                Ok(a) => a,
                Err(_) => return ptr::null_mut(),
            };
            let values = [valkind as jint, if is_mutable { 1 } else { 0 }];
            if env.set_int_array_region(&arr, 0, &values).is_err() {
                return ptr::null_mut();
            }
            arr.into_raw()
        }
        None => ptr::null_mut(),
    }
}

/// Get memory type info for an exported memory.
/// Returns an int array [is_shared, init_page_count, max_page_count] or null if not found.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeGetExportMemoryTypeInfo<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    name: JString<'local>,
) -> jobject {
    if module_handle == 0 || name.is_null() {
        return ptr::null_mut();
    }
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return ptr::null_mut(),
    };
    let module_ref = unsafe { &*(module_handle as *const WamrModule) };
    match runtime::get_export_memory_type_info(module_ref, &name_str) {
        Some((is_shared, init_pages, max_pages)) => {
            let arr = match env.new_int_array(3) {
                Ok(a) => a,
                Err(_) => return ptr::null_mut(),
            };
            let values = [
                if is_shared { 1 } else { 0 },
                init_pages as jint,
                max_pages as jint,
            ];
            if env.set_int_array_region(&arr, 0, &values).is_err() {
                return ptr::null_mut();
            }
            arr.into_raw()
        }
        None => ptr::null_mut(),
    }
}

// =============================================================================
// WASI Support (JniWebAssemblyInstance)
// =============================================================================

// Check if instance is in WASI mode.
jni_bool_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeIsWasiMode,
    WamrInstance, runtime::instance_is_wasi_mode);

// Get the WASI exit code.
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetWasiExitCode,
    WamrInstance, jint, 0, |r: &WamrInstance| {
    runtime::instance_get_wasi_exit_code(r) as jint
});

// Check if WASI _start function exists.
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeHasWasiStartFunction,
    WamrInstance, jni::sys::jboolean, 0, |r: &WamrInstance| {
    if runtime::instance_lookup_wasi_start_function(r).is_null() { 0 } else { 1 }
});

/// Execute the WASI _start function (execute main).
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeExecuteMain<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    argv: JObjectArray<'local>,
) -> jboolean {
    if instance_handle == 0 {
        return 0;
    }
    let args = jni_string_array_to_vec(&mut env, &argv);
    let arg_refs: Vec<&str> = args.iter().map(|s| s.as_str()).collect();

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::instance_execute_main(instance_ref, &arg_refs) {
            Ok(()) => 1,
            Err(_) => 0,
        }
    }
}

/// Execute a named function with string arguments.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeExecuteFunc<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    name: JString<'local>,
    argv: JObjectArray<'local>,
) -> jboolean {
    if instance_handle == 0 || name.is_null() {
        return 0;
    }
    let func_name: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let args = jni_string_array_to_vec(&mut env, &argv);
    let arg_refs: Vec<&str> = args.iter().map(|s| s.as_str()).collect();

    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        match runtime::instance_execute_func(instance_ref, &func_name, &arg_refs) {
            Ok(()) => 1,
            Err(_) => 0,
        }
    }
}

/// Helper: convert a JNI String[] to Vec<String>.
fn jni_string_array_to_vec(env: &mut JNIEnv, arr: &JObjectArray) -> Vec<String> {
    if arr.is_null() {
        return Vec::new();
    }
    let len = match env.get_array_length(arr) {
        Ok(l) => l as usize,
        Err(_) => return Vec::new(),
    };
    let mut result = Vec::with_capacity(len);
    for i in 0..len {
        if let Ok(obj) = env.get_object_array_element(arr, i as i32) {
            if !obj.is_null() {
                let s: String = match env.get_string(&JString::from(obj)) {
                    Ok(js) => js.into(),
                    Err(_) => continue,
                };
                result.push(s);
            }
        }
    }
    result
}

// =============================================================================
// Custom Data (JniWebAssemblyInstance)
// =============================================================================

// Set custom data on a module instance.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetCustomData,
    WamrInstance, custom_data: jlong, |r: &WamrInstance, d: jlong| {
    runtime::instance_set_custom_data(r, d as u64);
});

// Get custom data from a module instance.
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetCustomData,
    WamrInstance, jlong, 0, |r: &WamrInstance| {
    runtime::instance_get_custom_data(r) as jlong
});

// =============================================================================
// Debugging & Profiling — JniWebAssemblyInstance
// =============================================================================

/// Get the call stack as a Java string. Returns null if unavailable.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetCallStack<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> JString<'local> {
    if instance_handle == 0 {
        return JString::default();
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    let stack = runtime::instance_get_call_stack(instance_ref);
    if stack.is_empty() {
        return JString::default();
    }
    match env.new_string(&stack) {
        Ok(s) => s,
        Err(_) => JString::default(),
    }
}

// Dump call stack to stdout.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDumpCallStack,
    WamrInstance, runtime::instance_dump_call_stack);

// Dump performance profiling data to stdout.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDumpPerfProfiling,
    WamrInstance, runtime::instance_dump_perf_profiling);

// Get total WASM execution time in milliseconds.
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSumExecTime,
    WamrInstance, jdouble, 0.0, |r: &WamrInstance| {
    runtime::instance_sum_wasm_exec_time(r)
});

/// Get execution time for a specific function in milliseconds.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetFuncExecTime<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    func_name: JString<'local>,
) -> jdouble {
    if instance_handle == 0 {
        return 0.0;
    }
    let name: String = match env.get_string(&func_name) {
        Ok(s) => s.into(),
        Err(_) => return 0.0,
    };
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    runtime::instance_get_wasm_func_exec_time(instance_ref, &name)
}

// Dump memory consumption to stdout.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDumpMemConsumption,
    WamrInstance, runtime::instance_dump_mem_consumption);

// =============================================================================
// Phase 16: Memory Lookup (JniWebAssemblyInstance)
// =============================================================================

/// Lookup a memory instance by export name. Returns true if found.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeLookupMemory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    name: JString<'local>,
) -> jboolean {
    if instance_handle == 0 || name.is_null() {
        return 0;
    }
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    let result = runtime::instance_lookup_memory(instance_ref, &name_str);
    if result.is_null() { 0 } else { 1 }
}

// =============================================================================
// Phase 17: Blocking Ops & Stack Overflow (JniWebAssemblyInstance)
// =============================================================================

// Begin a blocking operation.
jni_bool_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeBeginBlockingOp,
    WamrInstance, runtime::instance_begin_blocking_op);

// End a blocking operation.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeEndBlockingOp,
    WamrInstance, runtime::instance_end_blocking_op);

/// Detect native stack overflow.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDetectNativeStackOverflow<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
) -> jboolean {
    if instance_handle == 0 {
        return 1;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    if runtime::instance_detect_native_stack_overflow(instance_ref) { 1 } else { 0 }
}

/// Detect native stack overflow with required size.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDetectNativeStackOverflowSize<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    required_size: jint,
) -> jboolean {
    if instance_handle == 0 {
        return 1;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    if runtime::instance_detect_native_stack_overflow_size(instance_ref, required_size as u32) { 1 } else { 0 }
}

// =============================================================================
// Phase 18: Runtime Mem Info (JniWebAssemblyRuntime)
// =============================================================================

/// Get memory allocation info.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetMemAllocInfo<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jobject {
    match runtime::get_mem_alloc_info() {
        Some((total, free, highmark)) => {
            let arr = match env.new_int_array(3) {
                Ok(a) => a,
                Err(_) => return ptr::null_mut(),
            };
            let values = [total as jint, free as jint, highmark as jint];
            if env.set_int_array_region(&arr, 0, &values).is_err() {
                return ptr::null_mut();
            }
            arr.into_raw()
        }
        None => ptr::null_mut(),
    }
}

// =============================================================================
// Phase 22: Context Keys (JniWebAssemblyRuntime)
// =============================================================================

/// Create a context key.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeCreateContextKey<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    runtime::create_context_key() as jlong
}

/// Destroy a context key.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeDestroyContextKey<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    key: jlong,
) {
    if key != 0 {
        runtime::destroy_context_key(key as *mut std::os::raw::c_void);
    }
}

// =============================================================================
// Phase 22: Context (JniWebAssemblyInstance)
// =============================================================================

/// Set context on an instance.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetContext<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    key: jlong,
    ctx: jlong,
) {
    if instance_handle == 0 || key == 0 {
        return;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    runtime::set_context(
        instance_ref,
        key as *mut std::os::raw::c_void,
        ctx as *mut std::os::raw::c_void,
    );
}

/// Get context from an instance.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetContext<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    key: jlong,
) -> jlong {
    if instance_handle == 0 || key == 0 {
        return 0;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    runtime::get_context(instance_ref, key as *mut std::os::raw::c_void) as jlong
}

// =============================================================================
// Phase 25: InstantiationArgs2 (JniWebAssemblyModule)
// =============================================================================

/// Instantiate a module using InstantiationArgs2.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeInstantiateEx2<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    default_stack_size: jint,
    host_managed_heap_size: jint,
    max_memory_pages: jint,
) -> jlong {
    if module_handle == 0 {
        let _ = env.throw_new("ai/tegmentum/wamr4j/exception/WasmRuntimeException",
            "Module handle is null");
        return 0;
    }
    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        match runtime::instance_create_ex2(
            module_ref,
            default_stack_size as u32,
            host_managed_heap_size as u32,
            max_memory_pages as u32,
        ) {
            Ok(instance) => Box::into_raw(instance) as jlong,
            Err(e) => {
                let msg = format!("{}", e);
                let _ = env.throw_new("ai/tegmentum/wamr4j/exception/WasmRuntimeException", &msg);
                0
            }
        }
    }
}

// =============================================================================
// JniWebAssemblyFunction
// =============================================================================

/// Destroy a WebAssembly function handle (frees the Rust Box wrapper)
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeDestroyFunction<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
) {
    if function_handle != 0 {
        unsafe {
            let _function = Box::from_raw(function_handle as *mut WamrFunction);
        }
    }
}

/// Invoke a WebAssembly function with arguments
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeFunction<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    args: JObjectArray<'local>,
) -> jobject {
    if function_handle == 0 {
        let _ = throw_wasm_exception(&mut env, "Invalid function handle");
        return ptr::null_mut();
    }

    unsafe {
        let function_ref = &*(function_handle as *const WamrFunction);

        // Convert Java Object[] to Vec<WasmValue>
        let wasm_args = match convert_args_to_wasm(&mut env, &args, &function_ref.param_types) {
            Ok(a) => a,
            Err(msg) => {
                let _ = throw_wasm_exception(&mut env, &msg);
                return ptr::null_mut();
            }
        };

        // Call the function
        match runtime::function_call(function_ref, function_ref.exec_env, &wasm_args) {
            Ok(results) => {
                if results.is_empty() {
                    return ptr::null_mut(); // void function
                }
                // Return single result as boxed value, or first result if multiple
                wasm_value_to_jobject(&mut env, &results[0])
            }
            Err(e) => {
                let _ = throw_wasm_exception(&mut env, &format!("{}", e));
                ptr::null_mut()
            }
        }
    }
}

// =============================================================================
// Typed Fast-Path Invocation — 1 JNI crossing, 0 heap allocations
// =============================================================================
//
// These native methods accept and return primitives directly, bypassing the
// Object[] marshalling that the generic nativeInvokeFunction uses. Each call
// uses stack-allocated WasmValT arrays and calls wasm_runtime_call_wasm_a
// directly, avoiding Vec allocations and JNI round-trips for boxing/unboxing.

/// Helper: call WAMR with stack-allocated args/results and throw on failure.
/// Returns false if an exception was thrown (caller should return default).
unsafe fn fast_call(
    env: &mut JNIEnv,
    function_handle: jlong,
    args: &mut [bindings::WasmValT],
    results: &mut [bindings::WasmValT],
) -> bool {
    if function_handle == 0 {
        let _ = throw_wasm_exception(env, "Invalid function handle");
        return false;
    }

    let function_ref = &*(function_handle as *const WamrFunction);
    if !function_ref.is_valid() {
        let _ = throw_wasm_exception(env, "Function is no longer valid");
        return false;
    }
    if function_ref.exec_env.is_null() {
        let _ = throw_wasm_exception(env, "No execution environment");
        return false;
    }

    // Auto-initialize thread environment for the current thread if needed.
    runtime::ensure_thread_env();

    let success = bindings::wasm_runtime_call_wasm_a(
        function_ref.exec_env,
        function_ref.handle,
        results.len() as u32,
        results.as_mut_ptr(),
        args.len() as u32,
        args.as_mut_ptr(),
    );

    if !success {
        let error_msg = runtime::get_and_clear_exception(function_ref.instance_handle);
        let wrapped = format!("Function execution failed: {}", error_msg);
        let _ = throw_wasm_exception(env, &wrapped);
        return false;
    }

    true
}

/// () -> void
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvoke_1V<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
) {
    unsafe {
        fast_call(&mut env, function_handle, &mut [], &mut []);
    }
}

/// () -> i32
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvoke_1I<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
) -> jint {
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_I32)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut [], &mut results) {
            return 0;
        }
        results[0].as_i32()
    }
}

/// (i32) -> void
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeI_1V<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jint,
) {
    let mut args = [bindings::WasmValT::i32(arg0)];
    unsafe {
        fast_call(&mut env, function_handle, &mut args, &mut []);
    }
}

/// (i32) -> i32
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeI_1I<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jint,
) -> jint {
    let mut args = [bindings::WasmValT::i32(arg0)];
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_I32)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut args, &mut results) {
            return 0;
        }
        results[0].as_i32()
    }
}

/// (i32, i32) -> void
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeII_1V<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jint,
    arg1: jint,
) {
    let mut args = [bindings::WasmValT::i32(arg0), bindings::WasmValT::i32(arg1)];
    unsafe {
        fast_call(&mut env, function_handle, &mut args, &mut []);
    }
}

/// (i32, i32) -> i32
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeII_1I<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jint,
    arg1: jint,
) -> jint {
    let mut args = [bindings::WasmValT::i32(arg0), bindings::WasmValT::i32(arg1)];
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_I32)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut args, &mut results) {
            return 0;
        }
        results[0].as_i32()
    }
}

/// (i32, i32, i32) -> i32
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeIII_1I<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jint,
    arg1: jint,
    arg2: jint,
) -> jint {
    let mut args = [
        bindings::WasmValT::i32(arg0),
        bindings::WasmValT::i32(arg1),
        bindings::WasmValT::i32(arg2),
    ];
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_I32)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut args, &mut results) {
            return 0;
        }
        results[0].as_i32()
    }
}

/// (i64) -> i64
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeJ_1J<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jlong,
) -> jlong {
    let mut args = [bindings::WasmValT::i64(arg0)];
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_I64)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut args, &mut results) {
            return 0;
        }
        results[0].as_i64()
    }
}

/// (i64, i64) -> i64
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeJJ_1J<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jlong,
    arg1: jlong,
) -> jlong {
    let mut args = [bindings::WasmValT::i64(arg0), bindings::WasmValT::i64(arg1)];
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_I64)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut args, &mut results) {
            return 0;
        }
        results[0].as_i64()
    }
}

/// (f64, f64) -> f64
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeDD_1D<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
    arg0: jdouble,
    arg1: jdouble,
) -> jdouble {
    let mut args = [bindings::WasmValT::f64(arg0), bindings::WasmValT::f64(arg1)];
    let mut results = [bindings::WasmValT::zeroed(bindings::WASM_F64)];
    unsafe {
        if !fast_call(&mut env, function_handle, &mut args, &mut results) {
            return 0.0;
        }
        results[0].as_f64()
    }
}

/// Get the function signature
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeGetFunctionSignature<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    function_handle: jlong,
) -> jobject {
    if function_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let function_ref = &*(function_handle as *const WamrFunction);
        let param_kinds: Vec<u8> = function_ref.param_types.iter().map(|t| wasm_type_to_valkind(t)).collect();
        let result_kinds: Vec<u8> = function_ref.result_types.iter().map(|t| wasm_type_to_valkind(t)).collect();
        create_function_signature(&mut env, &param_kinds, &result_kinds)
    }
}

// =============================================================================
// JniWebAssemblyMemory
// =============================================================================

/// Destroy a WebAssembly memory handle (frees the Rust Box wrapper)
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeDestroyMemory<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
) {
    if memory_handle != 0 {
        unsafe {
            let _memory = Box::from_raw(memory_handle as *mut WamrMemory);
        }
    }
}

/// Read raw bytes from memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeReadMemory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
    length: jint,
) -> jbyteArray {
    if memory_handle == 0 || offset < 0 || length < 0 {
        return ptr::null_mut();
    }

    unsafe {
        let memory_ref = &*(memory_handle as *const WamrMemory);
        let mut buffer = vec![0u8; length as usize];
        match runtime::memory_read(memory_ref, offset as usize, &mut buffer) {
            Ok(bytes_read) => {
                match env.new_byte_array(bytes_read as i32) {
                    Ok(array) => {
                        let signed: Vec<i8> = buffer[..bytes_read].iter().map(|&b| b as i8).collect();
                        let _ = env.set_byte_array_region(&array, 0, &signed);
                        array.into_raw()
                    }
                    Err(_) => ptr::null_mut(),
                }
            }
            Err(e) => {
                let _ = throw_wasm_exception(&mut env, &format!("{}", e));
                ptr::null_mut()
            }
        }
    }
}

/// Write raw bytes to memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeWriteMemory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
    data: JByteArray<'local>,
) {
    if memory_handle == 0 || offset < 0 || data.is_null() {
        return;
    }

    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return,
    };

    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        if let Err(e) = runtime::memory_write(memory_ref, offset as usize, &bytes) {
            let _ = throw_wasm_exception(&mut env, &format!("{}", e));
        }
    }
}

/// Read an i32 from memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeReadInt32<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
) -> jint {
    if memory_handle == 0 || offset < 0 {
        return 0;
    }

    unsafe {
        let memory_ref = &*(memory_handle as *const WamrMemory);
        let mut buffer = [0u8; 4];
        match runtime::memory_read(memory_ref, offset as usize, &mut buffer) {
            Ok(4) => i32::from_le_bytes(buffer),
            _ => {
                let _ = throw_wasm_exception(&mut env, "Failed to read i32 from memory");
                0
            }
        }
    }
}

/// Write an i32 to memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeWriteInt32<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
    value: jint,
) {
    if memory_handle == 0 || offset < 0 {
        return;
    }

    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        let bytes = value.to_le_bytes();
        if let Err(e) = runtime::memory_write(memory_ref, offset as usize, &bytes) {
            let _ = throw_wasm_exception(&mut env, &format!("{}", e));
        }
    }
}

/// Read an i64 from memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeReadInt64<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
) -> jlong {
    if memory_handle == 0 || offset < 0 {
        return 0;
    }

    unsafe {
        let memory_ref = &*(memory_handle as *const WamrMemory);
        let mut buffer = [0u8; 8];
        match runtime::memory_read(memory_ref, offset as usize, &mut buffer) {
            Ok(8) => i64::from_le_bytes(buffer),
            _ => {
                let _ = throw_wasm_exception(&mut env, "Failed to read i64 from memory");
                0
            }
        }
    }
}

/// Write an i64 to memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeWriteInt64<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
    value: jlong,
) {
    if memory_handle == 0 || offset < 0 {
        return;
    }

    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        let bytes = value.to_le_bytes();
        if let Err(e) = runtime::memory_write(memory_ref, offset as usize, &bytes) {
            let _ = throw_wasm_exception(&mut env, &format!("{}", e));
        }
    }
}

/// Read a f32 from memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeReadFloat32<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
) -> jfloat {
    if memory_handle == 0 || offset < 0 {
        return 0.0;
    }

    unsafe {
        let memory_ref = &*(memory_handle as *const WamrMemory);
        let mut buffer = [0u8; 4];
        match runtime::memory_read(memory_ref, offset as usize, &mut buffer) {
            Ok(4) => f32::from_le_bytes(buffer),
            _ => {
                let _ = throw_wasm_exception(&mut env, "Failed to read f32 from memory");
                0.0
            }
        }
    }
}

/// Write a f32 to memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeWriteFloat32<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
    value: jfloat,
) {
    if memory_handle == 0 || offset < 0 {
        return;
    }

    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        let bytes = value.to_le_bytes();
        if let Err(e) = runtime::memory_write(memory_ref, offset as usize, &bytes) {
            let _ = throw_wasm_exception(&mut env, &format!("{}", e));
        }
    }
}

/// Read a f64 from memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeReadFloat64<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
) -> jdouble {
    if memory_handle == 0 || offset < 0 {
        return 0.0;
    }

    unsafe {
        let memory_ref = &*(memory_handle as *const WamrMemory);
        let mut buffer = [0u8; 8];
        match runtime::memory_read(memory_ref, offset as usize, &mut buffer) {
            Ok(8) => f64::from_le_bytes(buffer),
            _ => {
                let _ = throw_wasm_exception(&mut env, "Failed to read f64 from memory");
                0.0
            }
        }
    }
}

/// Write a f64 to memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeWriteFloat64<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    offset: jint,
    value: jdouble,
) {
    if memory_handle == 0 || offset < 0 {
        return;
    }

    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        let bytes = value.to_le_bytes();
        if let Err(e) = runtime::memory_write(memory_ref, offset as usize, &bytes) {
            let _ = throw_wasm_exception(&mut env, &format!("{}", e));
        }
    }
}

// Get memory size in bytes
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGetMemorySize,
    WamrMemory, jint, -1, |r: &WamrMemory| r.size as jint);

/// Grow memory by specified pages
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGrowMemory<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    pages: jint,
) -> jint {
    if memory_handle == 0 || pages < 0 {
        return -1;
    }

    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        match runtime::memory_grow(memory_ref, pages as u32) {
            Ok(old_pages) => old_pages as jint,
            Err(_) => -1,
        }
    }
}

/// Get a direct ByteBuffer wrapping the WebAssembly memory
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGetMemoryBuffer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
) -> jobject {
    if memory_handle == 0 {
        return ptr::null_mut();
    }

    unsafe {
        let memory_ref = &*(memory_handle as *const WamrMemory);
        let data_ptr = memory_ref.data_ptr;
        let size = memory_ref.size;

        if data_ptr.is_null() || size == 0 {
            return ptr::null_mut();
        }

        match env.new_direct_byte_buffer(data_ptr, size) {
            Ok(buf) => buf.into_raw(),
            Err(_) => ptr::null_mut(),
        }
    }
}

// Get current page count
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGetPageCount,
    WamrMemory, jint, 0, |r: &WamrMemory| runtime::memory_page_count(r) as jint);

// Get maximum page count
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeGetMaxPageCount,
    WamrMemory, jint, 0, |r: &WamrMemory| runtime::memory_max_page_count(r) as jint);

// Check if memory is shared
jni_bool_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeIsShared,
    WamrMemory, runtime::memory_is_shared);

// =============================================================================
// JniWebAssemblyTable
// =============================================================================

/// Destroy a table handle
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyTable_nativeDestroyTable<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    table_handle: jlong,
) {
    if table_handle != 0 {
        unsafe {
            let _table = Box::from_raw(table_handle as *mut WamrTable);
        }
    }
}

// Get table current size
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyTable_nativeGetSize,
    WamrTable, jint, -1, |r: &WamrTable| r.table_inst.cur_size as jint);

// Get table maximum size
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyTable_nativeGetMaxSize,
    WamrTable, jint, -1, |r: &WamrTable| r.table_inst.max_size as jint);

// Get table element kind
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyTable_nativeGetElementKind,
    WamrTable, jint, -1, |r: &WamrTable| r.table_inst.elem_kind as jint);

/// Get a function from a table at the given index
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyTable_nativeGetFunction<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    table_handle: jlong,
    index: jint,
) -> jlong {
    if table_handle == 0 || index < 0 {
        return 0;
    }
    unsafe {
        let table_ref = &*(table_handle as *const WamrTable);
        match runtime::table_get_function(table_ref, index as u32) {
            Ok(function) => Box::into_raw(Box::new(function)) as jlong,
            Err(_) => 0,
        }
    }
}

/// Call a function indirectly via table index
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyTable_nativeCallIndirect<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    table_handle: jlong,
    element_index: jint,
    args: JObjectArray<'local>,
    result_type_ordinals: JObject<'local>,
) -> jobject {
    if table_handle == 0 || element_index < 0 {
        let _ = throw_wasm_exception(&mut env, "Invalid table handle or element index");
        return ptr::null_mut();
    }

    unsafe {
        let table_ref = &*(table_handle as *const WamrTable);

        // Get the instance from the table
        let instance = WamrInstance {
            handle: table_ref.instance_handle,
            exec_env: table_ref.exec_env,
            stack_size: 0,
            heap_size: 0,
        };

        // Convert result type ordinals from int[] to Vec<WasmType>
        let result_types = if !result_type_ordinals.is_null() {
            let int_array: jni::objects::JIntArray = JObject::into(result_type_ordinals);
            let len = env.get_array_length(&int_array).unwrap_or(0) as usize;
            let mut buf = vec![0i32; len];
            let _ = env.get_int_array_region(&int_array, 0, &mut buf);
            buf.iter().map(|&t| match t {
                0 => WasmType::I32,
                1 => WasmType::I64,
                2 => WasmType::F32,
                3 => WasmType::F64,
                _ => WasmType::I32,
            }).collect::<Vec<_>>()
        } else {
            Vec::new()
        };

        // Convert Java args to WasmValue
        let wasm_args = if !args.is_null() {
            match convert_args_with_autodetect(&mut env, &args) {
                Ok(a) => a,
                Err(msg) => {
                    let _ = throw_wasm_exception(&mut env, &msg);
                    // Prevent Drop from destroying the instance we don't own
                    std::mem::forget(instance);
                    return ptr::null_mut();
                }
            }
        } else {
            Vec::new()
        };

        match runtime::call_indirect(&instance, element_index as u32, &wasm_args, &result_types) {
            Ok(results) => {
                // Prevent Drop from destroying the instance we don't own
                std::mem::forget(instance);
                if results.is_empty() {
                    return ptr::null_mut();
                }
                wasm_value_to_jobject(&mut env, &results[0])
            }
            Err(e) => {
                // Prevent Drop from destroying the instance we don't own
                std::mem::forget(instance);
                let _ = throw_wasm_exception(&mut env, &format!("{}", e));
                ptr::null_mut()
            }
        }
    }
}

// =============================================================================
// Gap Coverage: WebAssemblyRuntime additions
// =============================================================================

/// Get the last error message.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetLastError<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    match crate::utils::get_last_error() {
        Some(msg) => env.new_string(&msg).unwrap_or_else(|_| JString::default()),
        None => JString::default(),
    }
}

/// Check if a binary buffer is an XIP file.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeIsXipFile<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    wasm_bytes: JByteArray<'local>,
) -> jboolean {
    let bytes = match env.convert_byte_array(&wasm_bytes) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    if runtime::is_xip_file(&bytes) { 1 } else { 0 }
}

/// Get the file package version from a binary buffer.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetFilePackageVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    wasm_bytes: JByteArray<'local>,
) -> jint {
    let bytes = match env.convert_byte_array(&wasm_bytes) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    runtime::get_file_package_version(&bytes) as jint
}

/// Get host object from externref index.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeExternrefRef2Obj<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    externref_idx: jint,
) -> jlong {
    match runtime::externref_ref2obj(externref_idx as u32) {
        Some(ptr) => ptr as jlong,
        None => 0,
    }
}

/// Retain an externref.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeExternrefRetain<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    externref_idx: jint,
) -> jboolean {
    if runtime::externref_retain(externref_idx as u32) { 1 } else { 0 }
}

/// Create a shared heap.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeCreateSharedHeap<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    size: jint,
) -> jlong {
    runtime::shared_heap_create(size as u32) as jlong
}

/// Chain two shared heaps.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeChainSharedHeaps<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    head: jlong,
    body: jlong,
) -> jlong {
    runtime::shared_heap_chain(
        head as *mut std::os::raw::c_void,
        body as *mut std::os::raw::c_void,
    ) as jlong
}

// =============================================================================
// Gap Coverage: WebAssemblyModule additions
// =============================================================================

/// Register a module under a name.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeRegister<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    name: JString<'local>,
) -> jboolean {
    if module_handle == 0 {
        return 0;
    }
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let module_ref = unsafe { &*(module_handle as *const WamrModule) };
    match runtime::module_register(module_ref, &name_str) {
        Ok(()) => 1,
        Err(_) => 0,
    }
}

// =============================================================================
// Gap Coverage: WebAssemblyInstance additions
// =============================================================================

/// Validate a native address.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeValidateNativeAddr<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    native_addr: jlong,
    size: jlong,
) -> jboolean {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        if runtime::validate_native_addr(instance_ref, native_addr as *mut std::os::raw::c_void, size as u64) { 1 } else { 0 }
    }
}

/// Convert app offset to native address.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeAddrAppToNative<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    app_offset: jlong,
) -> jlong {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        runtime::addr_app_to_native(instance_ref, app_offset as u64) as jlong
    }
}

/// Convert native address to app offset.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeAddrNativeToApp<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    native_addr: jlong,
) -> jlong {
    if instance_handle == 0 {
        return 0;
    }
    unsafe {
        let instance_ref = &*(instance_handle as *const WamrInstance);
        runtime::addr_native_to_app(instance_ref, native_addr as *mut std::os::raw::c_void) as jlong
    }
}

/// Set context with spread.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSetContextSpread<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    key: jlong,
    ctx: jlong,
) {
    if instance_handle == 0 || key == 0 {
        return;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    runtime::set_context_spread(
        instance_ref,
        key as *mut std::os::raw::c_void,
        ctx as *mut std::os::raw::c_void,
    );
}

// Spawn a new execution environment.
jni_getter_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSpawnExecEnv,
    WamrInstance, jlong, 0, |r: &WamrInstance| runtime::spawn_exec_env(r) as jlong);

/// Destroy a spawned execution environment.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDestroySpawnedExecEnv<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    exec_env: jlong,
) {
    if exec_env == 0 {
        return;
    }
    runtime::destroy_spawned_exec_env(exec_env as *mut crate::bindings::WasmExecEnvT);
}

/// Copy the call stack into arrays.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeCopyCallstack<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    max_frames: jint,
    skip: jint,
) -> JObjectArray<'local> {
    if instance_handle == 0 || max_frames <= 0 {
        return JObjectArray::default();
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    let frames = runtime::copy_callstack(instance_ref, skip as u32);
    let count = std::cmp::min(frames.len(), max_frames as usize);
    if count == 0 {
        return JObjectArray::default();
    }
    let func_indices: Vec<i32> = frames[..count].iter().map(|f| f.1 as i32).collect();
    let func_offsets: Vec<i32> = frames[..count].iter().map(|f| f.2 as i32).collect();
    let int_array_class = match env.find_class("[I") {
        Ok(c) => c,
        Err(_) => return JObjectArray::default(),
    };
    let outer = match env.new_object_array(2, &int_array_class, &JObject::null()) {
        Ok(a) => a,
        Err(_) => return JObjectArray::default(),
    };
    let indices_arr = match env.new_int_array(count as i32) {
        Ok(a) => a,
        Err(_) => return JObjectArray::default(),
    };
    let offsets_arr = match env.new_int_array(count as i32) {
        Ok(a) => a,
        Err(_) => return JObjectArray::default(),
    };
    let _ = env.set_int_array_region(&indices_arr, 0, &func_indices);
    let _ = env.set_int_array_region(&offsets_arr, 0, &func_offsets);
    let _ = env.set_object_array_element(&outer, 0, &indices_arr);
    let _ = env.set_object_array_element(&outer, 1, &offsets_arr);
    outer
}

/// Map host object to externref.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeExternrefObj2Ref<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    extern_obj: jlong,
) -> jint {
    if instance_handle == 0 {
        return -1;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    match runtime::externref_obj2ref(instance_ref, extern_obj as *mut std::os::raw::c_void) {
        Some(idx) => idx as jint,
        None => -1,
    }
}

// Delete host object from externref table.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeExternrefObjDel,
    WamrInstance, extern_obj: jlong, |r: &WamrInstance, obj: jlong| {
    runtime::externref_objdel(r, obj as *mut std::os::raw::c_void);
});

/// Attach shared heap to instance.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeAttachSharedHeap<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    heap_handle: jlong,
) -> jboolean {
    if instance_handle == 0 || heap_handle == 0 {
        return 0;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    if runtime::shared_heap_attach(instance_ref, heap_handle as *mut std::os::raw::c_void) { 1 } else { 0 }
}

// Detach shared heap from instance.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeDetachSharedHeap,
    WamrInstance, runtime::shared_heap_detach);

/// Allocate from shared heap.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSharedHeapMalloc<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    size: jlong,
) -> jlong {
    if instance_handle == 0 {
        return 0;
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    runtime::shared_heap_malloc(instance_ref, size as u64) as jlong
}

// Free shared heap memory.
jni_void_fn!(Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeSharedHeapFree,
    WamrInstance, ptr: jlong, |r: &WamrInstance, p: jlong| {
    runtime::shared_heap_free(r, p as u64);
});

// =============================================================================
// Gap Coverage: WebAssemblyMemory additions
// =============================================================================

/// Enlarge memory by pages.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyMemory_nativeEnlarge<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_handle: jlong,
    inc_pages: jlong,
) -> jboolean {
    if memory_handle == 0 {
        return 0;
    }
    unsafe {
        let memory_ref = &mut *(memory_handle as *mut WamrMemory);
        if runtime::memory_enlarge(memory_ref, inc_pages as u64) { 1 } else { 0 }
    }
}

// =============================================================================
// Gap Coverage: Runtime - findRegisteredModule
// =============================================================================

/// Find a previously registered module by name.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeFindRegisteredModule<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    name: JString<'local>,
) -> jlong {
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    runtime::module_find_registered(&name_str) as jlong
}

// =============================================================================
// Gap Coverage: Module - setWasiArgsEx
// =============================================================================

/// Set WASI args with explicit stdio file descriptors.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeSetWasiArgsEx<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    module_handle: jlong,
    dir_list: JObjectArray<'local>,
    map_dir_list: JObjectArray<'local>,
    env_vars: JObjectArray<'local>,
    argv: JObjectArray<'local>,
    stdinfd: jlong,
    stdoutfd: jlong,
    stderrfd: jlong,
) {
    if module_handle == 0 {
        return;
    }
    let dirs = jni_string_array_to_vec(&mut env, &dir_list);
    let map_dirs = jni_string_array_to_vec(&mut env, &map_dir_list);
    let envs = jni_string_array_to_vec(&mut env, &env_vars);
    let args = jni_string_array_to_vec(&mut env, &argv);

    let dir_cstrings: Vec<std::ffi::CString> = dirs.iter().filter_map(|s| std::ffi::CString::new(s.as_str()).ok()).collect();
    let map_dir_cstrings: Vec<std::ffi::CString> = map_dirs.iter().filter_map(|s| std::ffi::CString::new(s.as_str()).ok()).collect();
    let env_cstrings: Vec<std::ffi::CString> = envs.iter().filter_map(|s| std::ffi::CString::new(s.as_str()).ok()).collect();
    let arg_cstrings: Vec<std::ffi::CString> = args.iter().filter_map(|s| std::ffi::CString::new(s.as_str()).ok()).collect();

    let dir_ptrs: Vec<*const std::os::raw::c_char> = dir_cstrings.iter().map(|s| s.as_ptr()).collect();
    let map_dir_ptrs: Vec<*const std::os::raw::c_char> = map_dir_cstrings.iter().map(|s| s.as_ptr()).collect();
    let env_ptrs: Vec<*const std::os::raw::c_char> = env_cstrings.iter().map(|s| s.as_ptr()).collect();
    let arg_ptrs: Vec<*const std::os::raw::c_char> = arg_cstrings.iter().map(|s| s.as_ptr()).collect();

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        runtime::module_set_wasi_args_ex(
            module_ref.handle,
            &dir_ptrs,
            &map_dir_ptrs,
            &env_ptrs,
            &arg_ptrs,
            stdinfd as i64,
            stdoutfd as i64,
            stderrfd as i64,
        );
    }
}

// =============================================================================
// Gap Coverage: Instance - getNativeAddrRange
// =============================================================================

/// Get native address range for a pointer.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyInstance_nativeGetNativeAddrRange<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    instance_handle: jlong,
    native_ptr: jlong,
) -> jlongArray {
    if instance_handle == 0 {
        return ptr::null_mut();
    }
    let instance_ref = unsafe { &*(instance_handle as *const WamrInstance) };
    match runtime::get_native_addr_range(instance_ref, native_ptr as *mut u8) {
        Some((start, end)) => {
            let arr = [start as jlong, end as jlong];
            match env.new_long_array(2) {
                Ok(result) => {
                    let _ = env.set_long_array_region(&result, 0, &arr);
                    result.into_raw()
                }
                Err(_) => ptr::null_mut(),
            }
        }
        None => ptr::null_mut(),
    }
}

// =============================================================================
// Helper Functions
// =============================================================================

/// Create a Java String[] array from a Vec<String>
fn create_string_array(env: &mut JNIEnv, names: &[String]) -> jobjectArray {
    let string_class = match env.find_class("java/lang/String") {
        Ok(c) => c,
        Err(_) => return ptr::null_mut(),
    };

    let empty = match env.new_string("") {
        Ok(s) => s,
        Err(_) => return ptr::null_mut(),
    };

    let array = match env.new_object_array(names.len() as i32, &string_class, &empty) {
        Ok(a) => a,
        Err(_) => return ptr::null_mut(),
    };

    for (i, name) in names.iter().enumerate() {
        let jstr = match env.new_string(name) {
            Ok(s) => s,
            Err(_) => continue,
        };
        let _ = env.set_object_array_element(&array, i as i32, &jstr);
    }

    array.into_raw()
}

/// Create a FunctionSignature Java object from parameter and result value kinds
fn create_function_signature(env: &mut JNIEnv, param_kinds: &[u8], result_kinds: &[u8]) -> jobject {
    // Find ValueType enum class
    let vt_class = match env.find_class("ai/tegmentum/wamr4j/ValueType") {
        Ok(c) => c,
        Err(_) => return ptr::null_mut(),
    };

    // Create ValueType arrays
    let param_array = match env.new_object_array(param_kinds.len() as i32, &vt_class, &JObject::null()) {
        Ok(a) => a,
        Err(_) => return ptr::null_mut(),
    };

    for (i, &kind) in param_kinds.iter().enumerate() {
        let vt = match valkind_to_valuetype_jobject(env, &vt_class, kind) {
            Some(v) => v,
            None => continue,
        };
        let _ = env.set_object_array_element(&param_array, i as i32, &vt);
    }

    let result_array = match env.new_object_array(result_kinds.len() as i32, &vt_class, &JObject::null()) {
        Ok(a) => a,
        Err(_) => return ptr::null_mut(),
    };

    for (i, &kind) in result_kinds.iter().enumerate() {
        let vt = match valkind_to_valuetype_jobject(env, &vt_class, kind) {
            Some(v) => v,
            None => continue,
        };
        let _ = env.set_object_array_element(&result_array, i as i32, &vt);
    }

    // Find FunctionSignature class and constructor
    let sig_class = match env.find_class("ai/tegmentum/wamr4j/FunctionSignature") {
        Ok(c) => c,
        Err(_) => return ptr::null_mut(),
    };

    match env.new_object(
        &sig_class,
        "([Lai/tegmentum/wamr4j/ValueType;[Lai/tegmentum/wamr4j/ValueType;)V",
        &[JValue::Object(&param_array.into()), JValue::Object(&result_array.into())],
    ) {
        Ok(obj) => obj.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

/// Convert WAMR valkind byte to Java ValueType enum constant
fn valkind_to_valuetype_jobject<'local>(
    env: &mut JNIEnv<'local>,
    vt_class: &JClass<'local>,
    kind: u8,
) -> Option<JObject<'local>> {
    let field_name = match kind {
        bindings::WASM_I32 => "I32",
        bindings::WASM_I64 => "I64",
        bindings::WASM_F32 => "F32",
        bindings::WASM_F64 => "F64",
        _ => return None,
    };

    env.get_static_field(vt_class, field_name, "Lai/tegmentum/wamr4j/ValueType;")
        .ok()
        .and_then(|v| v.l().ok())
}

/// Convert WasmType to WAMR valkind byte
fn wasm_type_to_valkind(t: &WasmType) -> u8 {
    match t {
        WasmType::I32 => bindings::WASM_I32,
        WasmType::I64 => bindings::WASM_I64,
        WasmType::F32 => bindings::WASM_F32,
        WasmType::F64 => bindings::WASM_F64,
    }
}

/// Convert Java Object[] arguments to Vec<WasmValue>
fn convert_args_to_wasm(
    env: &mut JNIEnv,
    args: &JObjectArray,
    param_types: &[WasmType],
) -> Result<Vec<WasmValue>, String> {
    if args.is_null() {
        return Ok(Vec::new());
    }

    let arg_count = env.get_array_length(args).map_err(|e| format!("Failed to get args length: {}", e))? as usize;

    if arg_count != param_types.len() {
        return Err(format!(
            "Argument count mismatch: expected {}, got {}",
            param_types.len(), arg_count
        ));
    }

    let mut wasm_args = Vec::with_capacity(arg_count);

    for i in 0..arg_count {
        let arg = env.get_object_array_element(args, i as i32)
            .map_err(|e| format!("Failed to get arg {}: {}", i, e))?;

        let wasm_val = match &param_types[i] {
            WasmType::I32 => WasmValue::I32(unbox_integer(env, &arg)),
            WasmType::I64 => WasmValue::I64(unbox_long(env, &arg)),
            WasmType::F32 => WasmValue::F32(unbox_float(env, &arg)),
            WasmType::F64 => WasmValue::F64(unbox_double(env, &arg)),
        };

        wasm_args.push(wasm_val);
    }

    Ok(wasm_args)
}

/// Convert Java Object[] arguments to Vec<WasmValue> with auto-detected types
fn convert_args_with_autodetect(
    env: &mut JNIEnv,
    args: &JObjectArray,
) -> Result<Vec<WasmValue>, String> {
    let arg_count = env.get_array_length(args).map_err(|e| format!("Failed to get args length: {}", e))? as usize;
    let mut wasm_args = Vec::with_capacity(arg_count);

    for i in 0..arg_count {
        let arg = env.get_object_array_element(args, i as i32)
            .map_err(|e| format!("Failed to get arg {}: {}", i, e))?;

        let wasm_val = if is_instance_of(env, &arg, "java/lang/Integer") {
            WasmValue::I32(unbox_integer(env, &arg))
        } else if is_instance_of(env, &arg, "java/lang/Long") {
            WasmValue::I64(unbox_long(env, &arg))
        } else if is_instance_of(env, &arg, "java/lang/Float") {
            WasmValue::F32(unbox_float(env, &arg))
        } else if is_instance_of(env, &arg, "java/lang/Double") {
            WasmValue::F64(unbox_double(env, &arg))
        } else {
            return Err(format!("Unsupported argument type at index {}", i));
        };

        wasm_args.push(wasm_val);
    }

    Ok(wasm_args)
}

/// Convert WasmValue to a boxed Java object
fn wasm_value_to_jobject(env: &mut JNIEnv, value: &WasmValue) -> jobject {
    match value {
        WasmValue::I32(v) => box_integer(env, *v),
        WasmValue::I64(v) => box_long(env, *v),
        WasmValue::F32(v) => box_float(env, *v),
        WasmValue::F64(v) => box_double(env, *v),
    }
}

/// Throw a WasmRuntimeException
fn throw_wasm_exception(env: &mut JNIEnv, msg: &str) -> Result<(), jni::errors::Error> {
    env.throw_new("ai/tegmentum/wamr4j/exception/WasmRuntimeException", msg)
}

/// Check if a JObject is an instance of a class
fn is_instance_of(env: &mut JNIEnv, obj: &JObject, class_name: &str) -> bool {
    env.find_class(class_name)
        .and_then(|cls| env.is_instance_of(obj, &cls))
        .unwrap_or(false)
}

// =============================================================================
// Boxing/Unboxing Helpers
// =============================================================================

fn box_integer(env: &mut JNIEnv, val: i32) -> jobject {
    env.new_object("java/lang/Integer", "(I)V", &[JValue::Int(val)])
        .map(|o| o.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn box_long(env: &mut JNIEnv, val: i64) -> jobject {
    env.new_object("java/lang/Long", "(J)V", &[JValue::Long(val)])
        .map(|o| o.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn box_float(env: &mut JNIEnv, val: f32) -> jobject {
    env.new_object("java/lang/Float", "(F)V", &[JValue::Float(val)])
        .map(|o| o.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn box_double(env: &mut JNIEnv, val: f64) -> jobject {
    env.new_object("java/lang/Double", "(D)V", &[JValue::Double(val)])
        .map(|o| o.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn unbox_integer(env: &mut JNIEnv, obj: &JObject) -> i32 {
    env.call_method(obj, "intValue", "()I", &[])
        .and_then(|v| v.i())
        .unwrap_or(0)
}

fn unbox_long(env: &mut JNIEnv, obj: &JObject) -> i64 {
    env.call_method(obj, "longValue", "()J", &[])
        .and_then(|v| v.j())
        .unwrap_or(0)
}

fn unbox_float(env: &mut JNIEnv, obj: &JObject) -> f32 {
    env.call_method(obj, "floatValue", "()F", &[])
        .and_then(|v| v.f())
        .unwrap_or(0.0)
}

fn unbox_double(env: &mut JNIEnv, obj: &JObject) -> f64 {
    env.call_method(obj, "doubleValue", "()D", &[])
        .and_then(|v| v.d())
        .unwrap_or(0.0)
}
