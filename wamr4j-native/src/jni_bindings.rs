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

use jni::objects::{JClass, JObject, JString, JByteArray};
use jni::sys::{jlong, jbyteArray, jstring, jint};
use jni::JNIEnv;
use std::ptr;

use crate::wamr_wrapper::*;

/// Create a new WAMR runtime instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeCreateRuntime(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    match create_runtime() {
        Ok(runtime) => Box::into_raw(Box::new(runtime)) as jlong,
        Err(_) => 0,
    }
}

/// Destroy a WAMR runtime instance
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeDestroyRuntime(
    _env: JNIEnv,
    _class: JClass,
    runtime_handle: jlong,
) {
    if runtime_handle != 0 {
        unsafe {
            let _runtime = Box::from_raw(runtime_handle as *mut WamrRuntime);
            // Box drop will clean up the runtime
        }
    }
}

/// Compile a WebAssembly module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeCompileModule(
    env: JNIEnv,
    _class: JClass,
    runtime_handle: jlong,
    wasm_bytes: jbyteArray,
) -> jlong {
    if runtime_handle == 0 {
        return 0;
    }

    let byte_array = unsafe { JByteArray::from_raw(wasm_bytes) };
    let bytes = match env.convert_byte_array(byte_array) {
        Ok(bytes) => bytes,
        Err(_) => return 0,
    };

    unsafe {
        let runtime_ref = &*(runtime_handle as *const WamrRuntime);
        match compile_module(runtime_ref, &bytes) {
            Ok(module) => Box::into_raw(Box::new(module)) as jlong,
            Err(_) => 0,
        }
    }
}

/// Get WAMR version
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyRuntime_nativeGetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let version = "2.4.1";
    match env.new_string(version) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

/// Destroy a WebAssembly module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeDestroyModule(
    _env: JNIEnv,
    _class: JClass,
    module_handle: jlong,
) {
    if module_handle != 0 {
        unsafe {
            let _module = Box::from_raw(module_handle as *mut WamrModule);
            // Box drop will clean up the module
        }
    }
}

/// Instantiate a WebAssembly module
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeInstantiateModule(
    _env: JNIEnv,
    _class: JClass,
    module_handle: jlong,
) -> jlong {
    if module_handle == 0 {
        return 0;
    }

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);
        match instantiate_module(module_ref) {
            Ok(instance) => Box::into_raw(Box::new(instance)) as jlong,
            Err(_) => 0,
        }
    }
}

// Additional JNI bindings for other WebAssembly operations would be implemented here
// following the same pattern...