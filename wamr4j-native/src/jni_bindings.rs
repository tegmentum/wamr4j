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

use jni::objects::{JClass, JObject, JString, JByteArray, GlobalRef};
use jni::sys::{jlong, jbyteArray, jstring, jint, JNI_VERSION_1_8};
use jni::{JNIEnv, JavaVM};
use once_cell::sync::OnceCell;
use std::collections::HashMap;
use std::ffi::CString;
use std::os::raw::c_void;
use std::ptr;
use std::sync::Mutex;

use crate::bindings::NativeSymbol;
use crate::wamr_wrapper::*;

// =============================================================================
// Global State for Import Callbacks
// =============================================================================

/// Global JavaVM reference - set during JNI_OnLoad
static JAVA_VM: OnceCell<JavaVM> = OnceCell::new();

/// Callback registry - maps unique IDs to Java callback GlobalRefs
/// Thread-safe storage for host function callbacks
static CALLBACK_REGISTRY: OnceCell<Mutex<HashMap<usize, GlobalRef>>> = OnceCell::new();

/// Counter for generating unique callback IDs
static NEXT_CALLBACK_ID: Mutex<usize> = Mutex::new(0);

/// Initialize the callback registry
fn init_callback_registry() {
    CALLBACK_REGISTRY.get_or_init(|| Mutex::new(HashMap::new()));
}

/// Register a callback and return its unique ID
fn register_callback(callback: GlobalRef) -> usize {
    init_callback_registry();

    let mut id_counter = NEXT_CALLBACK_ID.lock().unwrap();
    let callback_id = *id_counter;
    *id_counter += 1;
    drop(id_counter);

    let mut registry = CALLBACK_REGISTRY.get().unwrap().lock().unwrap();
    registry.insert(callback_id, callback);

    callback_id
}

/// Retrieve a callback by its ID
fn get_callback(callback_id: usize) -> Option<GlobalRef> {
    let registry = CALLBACK_REGISTRY.get()?.lock().unwrap();
    registry.get(&callback_id).cloned()
}

/// JNI_OnLoad - Called when the native library is loaded
/// This is our opportunity to store the JavaVM reference for later use
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _reserved: *mut c_void) -> jint {
    // Store JavaVM for use in callbacks
    if JAVA_VM.set(vm).is_err() {
        eprintln!("ERROR: Failed to set JavaVM - already initialized");
        return 0;
    }

    // Initialize callback registry
    init_callback_registry();

    JNI_VERSION_1_8
}

// =============================================================================
// Import Data Structures
// =============================================================================

/// Represents a WebAssembly import item
#[derive(Debug, Clone)]
enum ImportItem {
    /// Function import with callback and signature
    Function {
        callback: GlobalRef,
        param_types: Vec<WasmType>,
        result_types: Vec<WasmType>,
    },
    /// Global variable import
    Global {
        value: WasmValue,
        mutable: bool,
    },
    /// Memory import (rarely used from Java)
    Memory {
        initial: u32,
        maximum: Option<u32>,
    },
    /// Table import (rarely used from Java)
    Table {
        initial: u32,
        maximum: Option<u32>,
    },
}

// =============================================================================
// JNI Helper Functions for Map Parsing
// =============================================================================

/// Check if a Java Iterator has more elements
fn has_next<'local>(env: &mut JNIEnv<'local>, iterator: &JObject<'local>) -> Result<bool, String> {
    let result = env
        .call_method(iterator, "hasNext", "()Z", &[])
        .map_err(|e| format!("Failed to call hasNext: {}", e))?;

    result
        .z()
        .map_err(|e| format!("Failed to get boolean from hasNext: {}", e))
}

/// Get next element from a Java Iterator
fn next<'local>(env: &mut JNIEnv<'local>, iterator: &JObject<'local>) -> Result<JObject<'local>, String> {
    let result = env
        .call_method(iterator, "next", "()Ljava/lang/Object;", &[])
        .map_err(|e| format!("Failed to call next: {}", e))?;

    result
        .l()
        .map_err(|e| format!("Failed to get object from next: {}", e))
}

/// Parse the outer imports map: Map<String moduleName, Map<String itemName, Object item>>
fn parse_imports(
    env: &JNIEnv,
    imports: JObject,
) -> Result<HashMap<String, HashMap<String, ImportItem>>, String> {
    if imports.is_null() {
        return Ok(HashMap::new());
    }

    let mut parsed_imports = HashMap::new();

    // Get Map.entrySet() method
    let entry_set = env
        .call_method(imports, "entrySet", "()Ljava/util/Set;", &[])
        .map_err(|e| format!("Failed to get entrySet: {}", e))?;

    // Get Set.iterator() method
    let iterator = env
        .call_method(entry_set.l()?, "iterator", "()Ljava/util/Iterator;", &[])
        .map_err(|e| format!("Failed to get iterator: {}", e))?;

    // Iterate over outer map (module names)
    while has_next(env, iterator.l()?)? {
        let entry = next(env, iterator.l()?)?;

        // Get module name (key)
        let key_obj = env
            .call_method(entry, "getKey", "()Ljava/lang/Object;", &[])
            .map_err(|e| format!("Failed to get key: {}", e))?;

        let module_name_jstring: JString = key_obj
            .l()
            .map_err(|e| format!("Failed to convert key to JString: {}", e))?
            .into();

        let module_name = env
            .get_string(module_name_jstring)
            .map_err(|e| format!("Failed to get module name string: {}", e))?
            .to_str()
            .map_err(|e| format!("Invalid module name UTF-8: {}", e))?
            .to_string();

        // Get inner map (value)
        let value_obj = env
            .call_method(entry, "getValue", "()Ljava/lang/Object;", &[])
            .map_err(|e| format!("Failed to get value: {}", e))?;

        // Parse inner map (item imports)
        let item_imports = parse_item_imports(env, value_obj.l()?)?;

        parsed_imports.insert(module_name, item_imports);
    }

    Ok(parsed_imports)
}

/// Parse the inner imports map: Map<String itemName, Object item>
fn parse_item_imports(
    env: &JNIEnv,
    inner_map: JObject,
) -> Result<HashMap<String, ImportItem>, String> {
    let mut items = HashMap::new();

    // Get Map.entrySet() method
    let entry_set = env
        .call_method(inner_map, "entrySet", "()Ljava/util/Set;", &[])
        .map_err(|e| format!("Failed to get inner entrySet: {}", e))?;

    // Get Set.iterator() method
    let iterator = env
        .call_method(entry_set.l()?, "iterator", "()Ljava/util/Iterator;", &[])
        .map_err(|e| format!("Failed to get inner iterator: {}", e))?;

    // Iterate over inner map (item names)
    while has_next(env, iterator.l()?)? {
        let entry = next(env, iterator.l()?)?;

        // Get item name (key)
        let key_obj = env
            .call_method(entry, "getKey", "()Ljava/lang/Object;", &[])
            .map_err(|e| format!("Failed to get item key: {}", e))?;

        let item_name_jstring: JString = key_obj
            .l()
            .map_err(|e| format!("Failed to convert item key to JString: {}", e))?
            .into();

        let item_name = env
            .get_string(item_name_jstring)
            .map_err(|e| format!("Failed to get item name string: {}", e))?
            .to_str()
            .map_err(|e| format!("Invalid item name UTF-8: {}", e))?
            .to_string();

        // Get import item (value)
        let value_obj = env
            .call_method(entry, "getValue", "()Ljava/lang/Object;", &[])
            .map_err(|e| format!("Failed to get item value: {}", e))?;

        // Determine import type and parse accordingly
        let import_item = parse_import_item(env, value_obj.l()?)?;

        items.insert(item_name, import_item);
    }

    Ok(items)
}

/// Parse a single import item based on its Java type
fn parse_import_item(env: &JNIEnv, value_obj: JObject) -> Result<ImportItem, String> {
    // Check if it's a primitive wrapper (Integer, Long, Float, Double) for global
    if is_instance_of(env, value_obj, "java/lang/Integer")? {
        let int_value = env
            .call_method(value_obj, "intValue", "()I", &[])
            .map_err(|e| format!("Failed to get int value: {}", e))?
            .i()
            .map_err(|e| format!("Failed to convert to i32: {}", e))?;

        return Ok(ImportItem::Global {
            value: WasmValue::I32(int_value),
            mutable: false,
        });
    }

    if is_instance_of(env, value_obj, "java/lang/Long")? {
        let long_value = env
            .call_method(value_obj, "longValue", "()J", &[])
            .map_err(|e| format!("Failed to get long value: {}", e))?
            .j()
            .map_err(|e| format!("Failed to convert to i64: {}", e))?;

        return Ok(ImportItem::Global {
            value: WasmValue::I64(long_value),
            mutable: false,
        });
    }

    if is_instance_of(env, value_obj, "java/lang/Float")? {
        let float_value = env
            .call_method(value_obj, "floatValue", "()F", &[])
            .map_err(|e| format!("Failed to get float value: {}", e))?
            .f()
            .map_err(|e| format!("Failed to convert to f32: {}", e))?;

        return Ok(ImportItem::Global {
            value: WasmValue::F32(float_value),
            mutable: false,
        });
    }

    if is_instance_of(env, value_obj, "java/lang/Double")? {
        let double_value = env
            .call_method(value_obj, "doubleValue", "()D", &[])
            .map_err(|e| format!("Failed to get double value: {}", e))?
            .d()
            .map_err(|e| format!("Failed to convert to f64: {}", e))?;

        return Ok(ImportItem::Global {
            value: WasmValue::F64(double_value),
            mutable: false,
        });
    }

    // If it's a functional interface (lambda), treat as function import
    // For now, we create a global reference to keep the callback alive
    let global_ref = env
        .new_global_ref(value_obj)
        .map_err(|e| format!("Failed to create global ref: {}", e))?;

    // TODO: Extract parameter and result types from functional interface
    // For now, use placeholder signature - this needs to be enhanced
    Ok(ImportItem::Function {
        callback: global_ref,
        param_types: vec![],
        result_types: vec![],
    })
}

/// Check if an object is an instance of a specific class
fn is_instance_of(env: &JNIEnv, obj: JObject, class_name: &str) -> Result<bool, String> {
    let class = env
        .find_class(class_name)
        .map_err(|e| format!("Failed to find class {}: {}", class_name, e))?;

    env.is_instance_of(obj, class)
        .map_err(|e| format!("Failed to check instance: {}", e))
}

// =============================================================================
// WASM Callback Wrapper Functions
// =============================================================================

/// Generic callback wrapper: () -> i32
/// WAMR signature: "()i"
/// Expects Java Supplier<Integer>
unsafe extern "C" fn callback_wrapper_void_to_i32(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> i32 {
    // Get attachment (callback ID) from execution environment
    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_void_to_i32");
        return 0;
    }

    // Convert attachment back to callback ID
    let callback_id = *(attachment as *const usize);

    // Get JavaVM and attach thread
    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0;
        }
    };

    // Retrieve callback from registry
    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0;
        }
    };

    // Call Java method (assuming Supplier<Integer> with get() method)
    let result = match env.call_method(
        callback.as_obj(),
        "get",
        "()Ljava/lang/Object;",
        &[],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0;
        }
    };

    // Convert result to i32
    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0;
        }
    };

    // Call intValue() on Integer result
    let int_val = match env.call_method(result_obj, "intValue", "()I", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get int value: {}", e);
            return 0;
        }
    };

    match int_val.i() {
        Ok(i) => i,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to i32: {}", e);
            0
        }
    }
}

/// Generic callback wrapper: (i32) -> i32
/// WAMR signature: "(i)i"
/// Expects Java Function<Integer, Integer>
unsafe extern "C" fn callback_wrapper_i32_to_i32(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> i32 {
    // Get function arguments
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0;
    }

    let arg0 = *argv as i32;

    // Get attachment (callback ID)
    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_i32_to_i32");
        return 0;
    }

    let callback_id = *(attachment as *const usize);

    // Get JavaVM and attach thread
    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0;
        }
    };

    // Retrieve callback from registry
    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0;
        }
    };

    // Create Integer object for argument
    let arg_obj = match env.new_object("java/lang/Integer", "(I)V", &[jni::objects::JValue::Int(arg0)]) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Integer object: {}", e);
            return 0;
        }
    };

    // Call Java method (assuming Function<Integer, Integer> with apply() method)
    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        &[jni::objects::JValue::Object(arg_obj)],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0;
        }
    };

    // Convert result to i32
    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0;
        }
    };

    // Call intValue() on Integer result
    let int_val = match env.call_method(result_obj, "intValue", "()I", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get int value: {}", e);
            return 0;
        }
    };

    match int_val.i() {
        Ok(i) => i,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to i32: {}", e);
            0
        }
    }
}

/// Generic callback wrapper: (i32, i32) -> i32
/// WAMR signature: "(ii)i"
/// Expects Java BiFunction<Integer, Integer, Integer>
unsafe extern "C" fn callback_wrapper_i32_i32_to_i32(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> i32 {
    // Get function arguments
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0;
    }

    let arg0 = *argv as i32;
    let arg1 = *argv.offset(1) as i32;

    // Get attachment (callback ID)
    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_i32_i32_to_i32");
        return 0;
    }

    let callback_id = *(attachment as *const usize);

    // Get JavaVM and attach thread
    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0;
        }
    };

    // Retrieve callback from registry
    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0;
        }
    };

    // Create Integer objects for arguments
    let arg0_obj = match env.new_object("java/lang/Integer", "(I)V", &[jni::objects::JValue::Int(arg0)]) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Integer object for arg0: {}", e);
            return 0;
        }
    };

    let arg1_obj = match env.new_object("java/lang/Integer", "(I)V", &[jni::objects::JValue::Int(arg1)]) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Integer object for arg1: {}", e);
            return 0;
        }
    };

    // Call Java method (assuming BiFunction<Integer, Integer, Integer> with apply() method)
    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        &[
            jni::objects::JValue::Object(arg0_obj),
            jni::objects::JValue::Object(arg1_obj),
        ],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0;
        }
    };

    // Convert result to i32
    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0;
        }
    };

    // Call intValue() on Integer result
    let int_val = match env.call_method(result_obj, "intValue", "()I", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get int value: {}", e);
            return 0;
        }
    };

    match int_val.i() {
        Ok(i) => i,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to i32: {}", e);
            0
        }
    }
}

// =============================================================================
// i64/Long Callback Wrappers
// =============================================================================

/// Callback wrapper: () -> i64
/// WAMR signature: "()I"
/// Expects Java Supplier<Long>
unsafe extern "C" fn callback_wrapper_void_to_i64(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> i64 {
    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_void_to_i64");
        return 0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0;
        }
    };

    let result = match env.call_method(callback.as_obj(), "get", "()Ljava/lang/Object;", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0;
        }
    };

    let long_val = match env.call_method(result_obj, "longValue", "()J", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get long value: {}", e);
            return 0;
        }
    };

    match long_val.j() {
        Ok(l) => l,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to i64: {}", e);
            0
        }
    }
}

/// Callback wrapper: (i64) -> i64
/// WAMR signature: "(I)I"
/// Expects Java Function<Long, Long>
unsafe extern "C" fn callback_wrapper_i64_to_i64(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> i64 {
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0;
    }

    // i64 arguments are 64-bit aligned
    let arg0 = *(argv as *const i64);

    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_i64_to_i64");
        return 0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0;
        }
    };

    let arg_obj = match env.new_object(
        "java/lang/Long",
        "(J)V",
        &[jni::objects::JValue::Long(arg0)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Long object: {}", e);
            return 0;
        }
    };

    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        &[jni::objects::JValue::Object(arg_obj)],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0;
        }
    };

    let long_val = match env.call_method(result_obj, "longValue", "()J", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get long value: {}", e);
            return 0;
        }
    };

    match long_val.j() {
        Ok(l) => l,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to i64: {}", e);
            0
        }
    }
}

/// Callback wrapper: (i64, i64) -> i64
/// WAMR signature: "(II)I"
/// Expects Java BiFunction<Long, Long, Long>
unsafe extern "C" fn callback_wrapper_i64_i64_to_i64(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> i64 {
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0;
    }

    let arg0 = *(argv as *const i64);
    let arg1 = *(argv as *const i64).offset(1);

    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_i64_i64_to_i64");
        return 0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0;
        }
    };

    let arg0_obj = match env.new_object(
        "java/lang/Long",
        "(J)V",
        &[jni::objects::JValue::Long(arg0)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Long object for arg0: {}", e);
            return 0;
        }
    };

    let arg1_obj = match env.new_object(
        "java/lang/Long",
        "(J)V",
        &[jni::objects::JValue::Long(arg1)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Long object for arg1: {}", e);
            return 0;
        }
    };

    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        &[
            jni::objects::JValue::Object(arg0_obj),
            jni::objects::JValue::Object(arg1_obj),
        ],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0;
        }
    };

    let long_val = match env.call_method(result_obj, "longValue", "()J", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get long value: {}", e);
            return 0;
        }
    };

    match long_val.j() {
        Ok(l) => l,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to i64: {}", e);
            0
        }
    }
}

// =============================================================================
// f32/Float Callback Wrappers
// =============================================================================

/// Callback wrapper: () -> f32
/// WAMR signature: "()f"
/// Expects Java Supplier<Float>
unsafe extern "C" fn callback_wrapper_void_to_f32(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> f32 {
    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_void_to_f32");
        return 0.0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0.0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0.0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0.0;
        }
    };

    let result = match env.call_method(callback.as_obj(), "get", "()Ljava/lang/Object;", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0.0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0.0;
        }
    };

    let float_val = match env.call_method(result_obj, "floatValue", "()F", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get float value: {}", e);
            return 0.0;
        }
    };

    match float_val.f() {
        Ok(f) => f,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to f32: {}", e);
            0.0
        }
    }
}

/// Callback wrapper: (f32) -> f32
/// WAMR signature: "(f)f"
/// Expects Java Function<Float, Float>
unsafe extern "C" fn callback_wrapper_f32_to_f32(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> f32 {
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0.0;
    }

    let arg0 = f32::from_bits(*argv);

    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_f32_to_f32");
        return 0.0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0.0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0.0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0.0;
        }
    };

    let arg_obj = match env.new_object(
        "java/lang/Float",
        "(F)V",
        &[jni::objects::JValue::Float(arg0)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Float object: {}", e);
            return 0.0;
        }
    };

    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        &[jni::objects::JValue::Object(arg_obj)],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0.0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0.0;
        }
    };

    let float_val = match env.call_method(result_obj, "floatValue", "()F", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get float value: {}", e);
            return 0.0;
        }
    };

    match float_val.f() {
        Ok(f) => f,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to f32: {}", e);
            0.0
        }
    }
}

/// Callback wrapper: (f32, f32) -> f32
/// WAMR signature: "(ff)f"
/// Expects Java BiFunction<Float, Float, Float>
unsafe extern "C" fn callback_wrapper_f32_f32_to_f32(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> f32 {
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0.0;
    }

    let arg0 = f32::from_bits(*argv);
    let arg1 = f32::from_bits(*argv.offset(1));

    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_f32_f32_to_f32");
        return 0.0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0.0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0.0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0.0;
        }
    };

    let arg0_obj = match env.new_object(
        "java/lang/Float",
        "(F)V",
        &[jni::objects::JValue::Float(arg0)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Float object for arg0: {}", e);
            return 0.0;
        }
    };

    let arg1_obj = match env.new_object(
        "java/lang/Float",
        "(F)V",
        &[jni::objects::JValue::Float(arg1)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Float object for arg1: {}", e);
            return 0.0;
        }
    };

    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        &[
            jni::objects::JValue::Object(arg0_obj),
            jni::objects::JValue::Object(arg1_obj),
        ],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0.0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0.0;
        }
    };

    let float_val = match env.call_method(result_obj, "floatValue", "()F", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get float value: {}", e);
            return 0.0;
        }
    };

    match float_val.f() {
        Ok(f) => f,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to f32: {}", e);
            0.0
        }
    }
}

// =============================================================================
// f64/Double Callback Wrappers
// =============================================================================

/// Callback wrapper: () -> f64
/// WAMR signature: "()F"
/// Expects Java Supplier<Double>
unsafe extern "C" fn callback_wrapper_void_to_f64(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> f64 {
    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_void_to_f64");
        return 0.0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0.0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0.0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0.0;
        }
    };

    let result = match env.call_method(callback.as_obj(), "get", "()Ljava/lang/Object;", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0.0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0.0;
        }
    };

    let double_val = match env.call_method(result_obj, "doubleValue", "()D", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get double value: {}", e);
            return 0.0;
        }
    };

    match double_val.d() {
        Ok(d) => d,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to f64: {}", e);
            0.0
        }
    }
}

/// Callback wrapper: (f64) -> f64
/// WAMR signature: "(F)F"
/// Expects Java Function<Double, Double>
unsafe extern "C" fn callback_wrapper_f64_to_f64(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> f64 {
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0.0;
    }

    let arg0 = f64::from_bits(*(argv as *const u64));

    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_f64_to_f64");
        return 0.0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0.0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0.0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0.0;
        }
    };

    let arg_obj = match env.new_object(
        "java/lang/Double",
        "(D)V",
        &[jni::objects::JValue::Double(arg0)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Double object: {}", e);
            return 0.0;
        }
    };

    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        &[jni::objects::JValue::Object(arg_obj)],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0.0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0.0;
        }
    };

    let double_val = match env.call_method(result_obj, "doubleValue", "()D", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get double value: {}", e);
            return 0.0;
        }
    };

    match double_val.d() {
        Ok(d) => d,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to f64: {}", e);
            0.0
        }
    }
}

/// Callback wrapper: (f64, f64) -> f64
/// WAMR signature: "(FF)F"
/// Expects Java BiFunction<Double, Double, Double>
unsafe extern "C" fn callback_wrapper_f64_f64_to_f64(
    exec_env: *mut crate::bindings::WasmExecEnv,
) -> f64 {
    let argv = crate::bindings::wasm_runtime_get_function_argv(exec_env);
    if argv.is_null() {
        eprintln!("ERROR: Failed to get function arguments");
        return 0.0;
    }

    let arg0 = f64::from_bits(*(argv as *const u64));
    let arg1 = f64::from_bits(*(argv as *const u64).offset(1));

    let attachment = crate::bindings::wasm_runtime_get_function_attachment(exec_env);
    if attachment.is_null() {
        eprintln!("ERROR: No attachment data in callback_wrapper_f64_f64_to_f64");
        return 0.0;
    }

    let callback_id = *(attachment as *const usize);

    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => {
            eprintln!("ERROR: JavaVM not initialized");
            return 0.0;
        }
    };

    let env = match jvm.attach_current_thread() {
        Ok(env) => env,
        Err(e) => {
            eprintln!("ERROR: Failed to attach thread: {}", e);
            return 0.0;
        }
    };

    let callback = match get_callback(callback_id) {
        Some(cb) => cb,
        None => {
            eprintln!("ERROR: Callback {} not found in registry", callback_id);
            return 0.0;
        }
    };

    let arg0_obj = match env.new_object(
        "java/lang/Double",
        "(D)V",
        &[jni::objects::JValue::Double(arg0)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Double object for arg0: {}", e);
            return 0.0;
        }
    };

    let arg1_obj = match env.new_object(
        "java/lang/Double",
        "(D)V",
        &[jni::objects::JValue::Double(arg1)],
    ) {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to create Double object for arg1: {}", e);
            return 0.0;
        }
    };

    let result = match env.call_method(
        callback.as_obj(),
        "apply",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        &[
            jni::objects::JValue::Object(arg0_obj),
            jni::objects::JValue::Object(arg1_obj),
        ],
    ) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to call Java callback: {}", e);
            return 0.0;
        }
    };

    let result_obj = match result.l() {
        Ok(obj) => obj,
        Err(e) => {
            eprintln!("ERROR: Failed to get result object: {}", e);
            return 0.0;
        }
    };

    let double_val = match env.call_method(result_obj, "doubleValue", "()D", &[]) {
        Ok(val) => val,
        Err(e) => {
            eprintln!("ERROR: Failed to get double value: {}", e);
            return 0.0;
        }
    };

    match double_val.d() {
        Ok(d) => d,
        Err(e) => {
            eprintln!("ERROR: Failed to convert to f64: {}", e);
            0.0
        }
    }
}

/// Select appropriate callback wrapper based on signature
fn get_callback_wrapper(
    param_types: &[WasmType],
    result_types: &[WasmType],
) -> Option<*mut c_void> {
    // Match on (param_types, result_types) to select wrapper function
    match (param_types, result_types) {
        // i32 variants
        ([], [WasmType::I32]) => Some(callback_wrapper_void_to_i32 as *mut c_void),
        ([WasmType::I32], [WasmType::I32]) => Some(callback_wrapper_i32_to_i32 as *mut c_void),
        ([WasmType::I32, WasmType::I32], [WasmType::I32]) => {
            Some(callback_wrapper_i32_i32_to_i32 as *mut c_void)
        }

        // i64 variants
        ([], [WasmType::I64]) => Some(callback_wrapper_void_to_i64 as *mut c_void),
        ([WasmType::I64], [WasmType::I64]) => Some(callback_wrapper_i64_to_i64 as *mut c_void),
        ([WasmType::I64, WasmType::I64], [WasmType::I64]) => {
            Some(callback_wrapper_i64_i64_to_i64 as *mut c_void)
        }

        // f32 variants
        ([], [WasmType::F32]) => Some(callback_wrapper_void_to_f32 as *mut c_void),
        ([WasmType::F32], [WasmType::F32]) => Some(callback_wrapper_f32_to_f32 as *mut c_void),
        ([WasmType::F32, WasmType::F32], [WasmType::F32]) => {
            Some(callback_wrapper_f32_f32_to_f32 as *mut c_void)
        }

        // f64 variants
        ([], [WasmType::F64]) => Some(callback_wrapper_void_to_f64 as *mut c_void),
        ([WasmType::F64], [WasmType::F64]) => Some(callback_wrapper_f64_to_f64 as *mut c_void),
        ([WasmType::F64, WasmType::F64], [WasmType::F64]) => {
            Some(callback_wrapper_f64_f64_to_f64 as *mut c_void)
        }

        // Unsupported signature
        _ => {
            eprintln!(
                "WARNING: Unsupported callback signature: {:?} -> {:?}",
                param_types, result_types
            );
            None
        }
    }
}

// =============================================================================
// WAMR Host Function Registration
// =============================================================================

/// Generate WAMR signature string from parameter and result types
///
/// Examples:
/// - (i32, i32) -> i32 becomes "(ii)i"
/// - () -> i32 becomes "()i"
/// - (i64, f32) -> f64 becomes "(If)F"
fn generate_wamr_signature(
    param_types: &[WasmType],
    result_types: &[WasmType],
) -> Result<CString, String> {
    let mut sig = String::from("(");

    for param in param_types {
        sig.push(match param {
            WasmType::I32 => 'i',
            WasmType::I64 => 'I',
            WasmType::F32 => 'f',
            WasmType::F64 => 'F',
        });
    }

    sig.push(')');

    for result in result_types {
        sig.push(match result {
            WasmType::I32 => 'i',
            WasmType::I64 => 'I',
            WasmType::F32 => 'f',
            WasmType::F64 => 'F',
        });
    }

    CString::new(sig).map_err(|e| format!("Invalid signature string: {}", e))
}

/// Register function imports with WAMR for a given module
fn register_function_imports(
    module_name: &str,
    functions: &HashMap<String, ImportItem>,
) -> Result<(), String> {
    if functions.is_empty() {
        return Ok(());
    }

    let mut native_symbols = Vec::new();
    let mut c_names = Vec::new();
    let mut c_signatures = Vec::new();
    let mut callback_ids = Vec::new();

    for (name, import) in functions {
        if let ImportItem::Function {
            callback,
            param_types,
            result_types,
        } = import
        {
            // Create C string for function name
            let c_name = CString::new(name.as_str())
                .map_err(|e| format!("Invalid function name '{}': {}", name, e))?;

            // Generate WAMR signature
            let signature = generate_wamr_signature(param_types, result_types)?;

            // Get appropriate wrapper function based on signature
            let func_ptr = match get_callback_wrapper(param_types, result_types) {
                Some(ptr) => ptr,
                None => {
                    // Unsupported signature - log warning and skip
                    eprintln!(
                        "WARNING: Skipping function '{}' with unsupported signature {:?} -> {:?}",
                        name, param_types, result_types
                    );
                    continue;
                }
            };

            // Register callback in global registry and get unique ID
            let callback_id = register_callback(callback.clone());

            // Store callback ID as attachment (WAMR will pass this to the wrapper)
            // Convert usize to pointer for attachment field
            let attachment = Box::into_raw(Box::new(callback_id)) as *mut c_void;

            // Create native symbol entry
            native_symbols.push(NativeSymbol {
                symbol: c_name.as_ptr(),
                func_ptr,
                signature: signature.as_ptr(),
                attachment,
            });

            // Store strings to prevent deallocation
            c_names.push(c_name);
            c_signatures.push(signature);
            callback_ids.push(callback_id);
        }
    }

    if native_symbols.is_empty() {
        return Ok(());
    }

    let c_module_name =
        CString::new(module_name).map_err(|e| format!("Invalid module name: {}", e))?;

    let success = unsafe {
        crate::bindings::wasm_runtime_register_natives(
            c_module_name.as_ptr(),
            native_symbols.as_ptr(),
            native_symbols.len() as u32,
        )
    };

    if !success {
        return Err(format!(
            "Failed to register native functions for module '{}'",
            module_name
        ));
    }

    Ok(())
}

/// Register all imports with WAMR
fn register_imports(
    parsed_imports: &HashMap<String, HashMap<String, ImportItem>>,
) -> Result<(), String> {
    for (module_name, items) in parsed_imports {
        // Filter function imports
        let functions: HashMap<String, ImportItem> = items
            .iter()
            .filter(|(_, item)| matches!(item, ImportItem::Function { .. }))
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();

        // Register function imports
        if !functions.is_empty() {
            register_function_imports(module_name, &functions)?;
        }

        // TODO: Register global imports
        // TODO: Register memory imports
        // TODO: Register table imports
    }

    Ok(())
}

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

/// Instantiate a WebAssembly module with optional imports
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyModule_nativeInstantiateModule(
    env: JNIEnv,
    _class: JClass,
    module_handle: jlong,
    imports: JObject,
) -> jlong {
    if module_handle == 0 {
        return 0;
    }

    unsafe {
        let module_ref = &*(module_handle as *const WamrModule);

        // Parse and register imports if provided
        if !imports.is_null() {
            match parse_imports(&env, imports) {
                Ok(parsed_imports) => {
                    if !parsed_imports.is_empty() {
                        // Register imports with WAMR before instantiation
                        if let Err(e) = register_imports(&parsed_imports) {
                            eprintln!("ERROR: Failed to register imports: {}", e);
                            return 0;
                        }
                    }
                }
                Err(e) => {
                    eprintln!("ERROR: Failed to parse imports: {}", e);
                    return 0;
                }
            }
        }

        // Instantiate module (with or without imports)
        match instantiate_module(module_ref) {
            Ok(instance) => Box::into_raw(Box::new(instance)) as jlong,
            Err(_) => 0,
        }
    }
}

// Additional JNI bindings for other WebAssembly operations would be implemented here
// following the same pattern...