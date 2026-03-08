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

//! Macros for reducing boilerplate in FFI and JNI binding functions.

/// FFI function that takes a handle, null-checks, casts to a type ref, calls a
/// runtime function returning bool, and returns `c_int` (1 for true, 0 for false).
///
/// Usage: `ffi_bool_fn!(export_name, HandleType, runtime_fn, null_default);`
macro_rules! ffi_bool_fn {
    ($name:ident, $handle_type:ty, $runtime_fn:expr, $null_default:expr) => {
        #[no_mangle]
        pub extern "C" fn $name(handle: *mut std::os::raw::c_void) -> std::os::raw::c_int {
            crate::utils::clear_last_error();
            if handle.is_null() {
                return $null_default;
            }
            unsafe {
                let ref_ = &*(handle as *const $handle_type);
                if ($runtime_fn)(ref_) { 1 } else { 0 }
            }
        }
    };
}

/// FFI function that takes a handle, null-checks, casts to a type ref, evaluates
/// an expression to produce a return value.
///
/// Usage: `ffi_getter_fn!(export_name, HandleType, ReturnType, null_default, |ref_| expr);`
macro_rules! ffi_getter_fn {
    ($name:ident, $handle_type:ty, $ret:ty, $null_default:expr, $body:expr) => {
        #[no_mangle]
        pub extern "C" fn $name(handle: *mut std::os::raw::c_void) -> $ret {
            crate::utils::clear_last_error();
            if handle.is_null() {
                return $null_default;
            }
            unsafe {
                let ref_ = &*(handle as *const $handle_type);
                ($body)(ref_)
            }
        }
    };
}

/// FFI function that takes a handle and one extra argument, null-checks, casts,
/// and calls a void runtime function.
///
/// Usage: `ffi_void_fn!(export_name, HandleType, runtime_fn);`
/// Usage with arg: `ffi_void_fn!(export_name, HandleType, ArgType, |ref_, arg| expr);`
macro_rules! ffi_void_fn {
    ($name:ident, $handle_type:ty, $runtime_fn:expr) => {
        #[no_mangle]
        pub extern "C" fn $name(handle: *mut std::os::raw::c_void) {
            crate::utils::clear_last_error();
            if handle.is_null() {
                return;
            }
            let ref_ = unsafe { &*(handle as *const $handle_type) };
            ($runtime_fn)(ref_);
        }
    };
    ($name:ident, $handle_type:ty, $arg_name:ident : $arg_type:ty, $body:expr) => {
        #[no_mangle]
        pub extern "C" fn $name(handle: *mut std::os::raw::c_void, $arg_name: $arg_type) {
            crate::utils::clear_last_error();
            if handle.is_null() {
                return;
            }
            let ref_ = unsafe { &*(handle as *const $handle_type) };
            ($body)(ref_, $arg_name);
        }
    };
}

/// JNI function that takes a handle jlong, null-checks, casts to a type ref,
/// calls a runtime function returning bool, and returns `jboolean`.
///
/// Usage: `jni_bool_fn!(JavaClassName, methodName, HandleType, runtime_fn);`
macro_rules! jni_bool_fn {
    ($java_name:ident, $handle_type:ty, $runtime_fn:expr) => {
        #[no_mangle]
        pub extern "system" fn $java_name<'local>(
            _env: jni::JNIEnv<'local>,
            _class: jni::objects::JClass<'local>,
            handle: jni::sys::jlong,
        ) -> jni::sys::jboolean {
            if handle == 0 {
                return 0;
            }
            unsafe {
                let ref_ = &*(handle as *const $handle_type);
                if ($runtime_fn)(ref_) { 1 } else { 0 }
            }
        }
    };
}

/// JNI function that takes a handle jlong, null-checks, casts, evaluates an
/// expression to produce a return value.
///
/// Usage: `jni_getter_fn!(JavaClassName, HandleType, ReturnType, default, |ref_| expr);`
macro_rules! jni_getter_fn {
    ($java_name:ident, $handle_type:ty, $ret:ty, $default:expr, $body:expr) => {
        #[no_mangle]
        pub extern "system" fn $java_name<'local>(
            _env: jni::JNIEnv<'local>,
            _class: jni::objects::JClass<'local>,
            handle: jni::sys::jlong,
        ) -> $ret {
            if handle == 0 {
                return $default;
            }
            unsafe {
                let ref_ = &*(handle as *const $handle_type);
                ($body)(ref_)
            }
        }
    };
}

/// JNI function that takes a handle jlong and calls a void runtime function.
///
/// Usage: `jni_void_fn!(JavaClassName, HandleType, runtime_fn);`
macro_rules! jni_void_fn {
    ($java_name:ident, $handle_type:ty, $runtime_fn:expr) => {
        #[no_mangle]
        pub extern "system" fn $java_name<'local>(
            _env: jni::JNIEnv<'local>,
            _class: jni::objects::JClass<'local>,
            handle: jni::sys::jlong,
        ) {
            if handle == 0 {
                return;
            }
            let ref_ = unsafe { &*(handle as *const $handle_type) };
            ($runtime_fn)(ref_);
        }
    };
    ($java_name:ident, $handle_type:ty, $arg_name:ident : $arg_type:ty, $body:expr) => {
        #[no_mangle]
        pub extern "system" fn $java_name<'local>(
            _env: jni::JNIEnv<'local>,
            _class: jni::objects::JClass<'local>,
            handle: jni::sys::jlong,
            $arg_name: $arg_type,
        ) {
            if handle == 0 {
                return;
            }
            let ref_ = unsafe { &*(handle as *const $handle_type) };
            ($body)(ref_, $arg_name);
        }
    };
}
