# Import Section Implementation Guide

**Status:** Signature Updated, Implementation Pending
**Date:** 2025-10-22

## Overview

This document provides a comprehensive guide for implementing WebAssembly import section support in the wamr4j native Rust bridge.

## Current Status

### Completed ✅
- Public Java API designed: `WebAssemblyModule.instantiate(Map<String, Map<String, Object>> imports)`
- JNI method declared in Java: `nativeInstantiateModule(long moduleHandle, Map<String, Map<String, Object>> imports)`
- Rust JNI binding signature updated to accept `imports: JObject` parameter
- Specification tests created in `ImportSpecTest.java` (currently disabled)

### Pending ❌
- Java Map parsing in Rust
- Host function registration with WAMR
- Callback mechanism from WASM to Java
- Global/memory/table import support
- Integration tests

## Implementation Steps

### 1. Parse Java Map Structure in Rust

The imports parameter is a nested Map structure:
```java
Map<String moduleName, Map<String itemName, Object item>>
```

**Rust Implementation (in `jni_bindings.rs`):**

```rust
use jni::objects::{JMap, JValue};
use std::collections::HashMap;

fn parse_imports(env: &JNIEnv, imports: JObject) -> Result<HashMap<String, HashMap<String, ImportItem>>, String> {
    if imports.is_null() {
        return Ok(HashMap::new());
    }

    let mut parsed_imports = HashMap::new();

    // Get Map.entrySet() method
    let entry_set = env.call_method(imports, "entrySet", "()Ljava/util/Set;", &[])
        .map_err(|e| format!("Failed to get entrySet: {}", e))?;

    // Get Set.iterator() method
    let iterator = env.call_method(entry_set.l()?, "iterator", "()Ljava/util/Iterator;", &[])
        .map_err(|e| format!("Failed to get iterator: {}", e))?;

    // Iterate over outer map (module names)
    while has_next(env, iterator.l()?)? {
        let entry = next(env, iterator.l()?)?;

        // Get module name (key)
        let key_obj = env.call_method(entry, "getKey", "()Ljava/lang/Object;", &[])?;
        let module_name = env.get_string(key_obj.l()?.into())?
            .to_str()
            .map_err(|e| format!("Invalid module name: {}", e))?
            .to_string();

        // Get inner map (value)
        let value_obj = env.call_method(entry, "getValue", "()Ljava/lang/Object;", &[])?;

        // Parse inner map (item imports)
        let item_imports = parse_item_imports(env, value_obj.l()?)?;

        parsed_imports.insert(module_name, item_imports);
    }

    Ok(parsed_imports)
}

fn parse_item_imports(env: &JNIEnv, inner_map: JObject) -> Result<HashMap<String, ImportItem>, String> {
    let mut items = HashMap::new();

    // Similar iteration logic for inner map
    // Extract item name and determine type from Object value
    // ...

    Ok(items)
}
```

### 2. Define Import Types

```rust
#[derive(Debug, Clone)]
enum ImportItem {
    Function {
        callback: GlobalRef,  // Java function reference
        param_types: Vec<WasmType>,
        result_types: Vec<WasmType>,
    },
    Global {
        value: WasmValue,
        mutable: bool,
    },
    Memory {
        initial: u32,
        maximum: Option<u32>,
    },
    Table {
        initial: u32,
        maximum: Option<u32>,
    },
}
```

### 3. Register Host Functions with WAMR

WAMR provides `wasm_runtime_register_natives()` for host function registration.

**Add to `bindings.rs`:**

```rust
extern "C" {
    pub fn wasm_runtime_register_natives(
        module_name: *const c_char,
        native_symbols: *const NativeSymbol,
        n_native_symbols: u32,
    ) -> bool;
}

#[repr(C)]
pub struct NativeSymbol {
    pub symbol: *const c_char,
    pub func_ptr: *mut c_void,
    pub signature: *const c_char,
    pub attachment: *mut c_void,
}
```

**Register functions:**

```rust
fn register_function_imports(
    module_name: &str,
    functions: &HashMap<String, ImportItem>,
) -> Result<(), String> {
    let mut native_symbols = Vec::new();

    for (name, import) in functions {
        if let ImportItem::Function { callback, param_types, result_types } = import {
            // Create native symbol entry
            let c_name = CString::new(name.as_str())
                .map_err(|e| format!("Invalid function name: {}", e))?;

            // Generate WAMR signature string (e.g., "(ii)i" for (i32, i32) -> i32)
            let signature = generate_wamr_signature(param_types, result_types)?;

            // Create wrapper function pointer
            let func_ptr = create_host_function_wrapper(callback.clone(), param_types, result_types)?;

            native_symbols.push(NativeSymbol {
                symbol: c_name.as_ptr(),
                func_ptr: func_ptr as *mut c_void,
                signature: signature.as_ptr(),
                attachment: ptr::null_mut(),
            });

            // Store c_name and signature to prevent deallocation
        }
    }

    let c_module_name = CString::new(module_name)
        .map_err(|e| format!("Invalid module name: {}", e))?;

    let success = unsafe {
        bindings::wasm_runtime_register_natives(
            c_module_name.as_ptr(),
            native_symbols.as_ptr(),
            native_symbols.len() as u32,
        )
    };

    if !success {
        return Err(format!("Failed to register native functions for module '{}'", module_name));
    }

    Ok(())
}
```

### 4. Create Host Function Wrapper

This is the most complex part - creating a Rust function that WAMR can call, which then calls back to Java.

```rust
fn create_host_function_wrapper(
    java_callback: GlobalRef,
    param_types: &[WasmType],
    result_types: &[WasmType],
) -> Result<*const c_void, String> {
    // Create a closure that captures the Java callback
    // This is tricky because:
    // 1. The function pointer must be 'static
    // 2. We need to store java_callback somewhere accessible
    // 3. We need JNIEnv to call Java, but it's thread-local

    // Possible approaches:
    // A) Use a global registry of callbacks with unique IDs
    // B) Store GlobalRef in attachment field of NativeSymbol
    // C) Use JavaVM to get JNIEnv in callback

    // Simplified example (approach C):
    unsafe extern "C" fn host_function_stub(exec_env: *mut WasmExecEnv) -> i32 {
        // Get function arguments from WAMR execution environment
        let argv = wasm_runtime_get_function_argv(exec_env);

        // Get JavaVM and attach current thread
        let jvm = get_java_vm(); // Stored during JNI_OnLoad
        let env = jvm.attach_current_thread().unwrap();

        // Get Java callback from attachment or registry
        let callback = get_callback_for_current_function();

        // Call Java method
        // Convert WASM arguments to Java types
        // Invoke callback
        // Convert result back to WASM type

        // Return result
        0 // Placeholder
    }

    Ok(host_function_stub as *const c_void)
}
```

### 5. Generate WAMR Signature String

WAMR uses signature strings like `"(ii)i"` for `(i32, i32) -> i32`.

```rust
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

    CString::new(sig)
        .map_err(|e| format!("Invalid signature string: {}", e))
}
```

### 6. Handle Global Imports

Globals are simpler than functions - just extract the value and create a WAMR global.

```rust
fn register_global_imports(
    module: &WamrModule,
    module_name: &str,
    globals: &HashMap<String, ImportItem>,
) -> Result<(), String> {
    for (name, import) in globals {
        if let ImportItem::Global { value, mutable } = import {
            // WAMR might not have a direct API for this
            // May need to modify module before compilation or
            // use alternative approach

            // TODO: Research WAMR API for global imports
        }
    }

    Ok(())
}
```

## Key Challenges

### 1. Thread Safety
- JNIEnv is thread-local
- WASM can be called from any thread
- Need JavaVM.attach_current_thread() in callbacks

### 2. Lifetime Management
- Java callback references must remain valid
- Use GlobalRef for callbacks
- Store in static registry or instance field

### 3. Type Conversion
- WASM i32/i64/f32/f64 ↔ Java Integer/Long/Float/Double
- Handle boxing/unboxing
- Error handling for type mismatches

### 4. Error Propagation
- WAMR C API uses return codes
- Need to propagate Java exceptions to WASM traps
- Vice versa: WASM traps to Java exceptions

## Testing Strategy

1. **Unit Tests (Rust):**
   - Test Map parsing logic
   - Test signature generation
   - Test type conversion

2. **Integration Tests (Java):**
   - Enable tests in `ImportSpecTest.java`
   - Test simple function imports
   - Test multi-parameter functions
   - Test global imports
   - Test error cases

3. **Validation:**
   - Compare behavior with other WASM runtimes (wasmtime, wasmer)
   - Verify against official WASM test suite

## Implementation Checklist

- [x] Update Rust JNI signature to accept imports parameter
- [x] Implement Java Map parsing
- [x] Define ImportItem data structures
- [x] Implement function import registration (structure only)
- [ ] Create host function wrapper mechanism (CRITICAL - see below)
- [ ] Implement callback from WASM to Java (CRITICAL - see below)
- [x] Implement global import support (parsing only, registration pending)
- [x] Add error handling and validation
- [ ] Enable and run ImportSpecTest
- [ ] Add integration tests
- [x] Update documentation

## Implementation Status (2025-10-22)

### Completed ✅

1. **JNI Signature Updated** (`jni_bindings.rs:493`)
   - Function now accepts `imports: JObject` parameter
   - Signature matches Java API

2. **Java Map Parsing** (`jni_bindings.rs:90-265`)
   - `parse_imports()` - Parses outer Map<String, Map<String, Object>>
   - `parse_item_imports()` - Parses inner Map<String, Object>
   - `parse_import_item()` - Determines import type from Java object
   - `has_next()`, `next()`, `is_instance_of()` - JNI helper functions
   - Global imports fully parsed (Integer, Long, Float, Double)
   - Function imports parsed (callback stored as GlobalRef)

3. **Import Data Structures** (`jni_bindings.rs:38-61`)
   - `ImportItem` enum with Function, Global, Memory, Table variants
   - Full type information storage

4. **WAMR Signature Generation** (`jni_bindings.rs:287-314`)
   - `generate_wamr_signature()` converts WasmType arrays to WAMR format
   - Examples: (i32, i32) -> i32 becomes "(ii)i"

5. **Host Function Registration Structure** (`jni_bindings.rs:316-407`)
   - `register_function_imports()` creates NativeSymbol array
   - `register_imports()` orchestrates all import registration
   - WAMR C API bindings added (`bindings.rs:298-362`)

6. **Integration** (`jni_bindings.rs:506-523`)
   - `nativeInstantiateModule` now calls import parsing and registration
   - Error handling for parse and registration failures

### Pending Implementation ❌

**CRITICAL: Host Function Callback Mechanism**

The most complex remaining work is creating the actual C function pointers that WAMR can call, which then call back to Java. Currently `func_ptr` is set to `ptr::null_mut()` (line 349).

**Required Implementation:**

```rust
// TODO: Replace null func_ptr with actual wrapper
// Current code (jni_bindings.rs:346-350):
native_symbols.push(NativeSymbol {
    symbol: c_name.as_ptr(),
    func_ptr: ptr::null_mut(), // ❌ NULL - WAMR will crash if called
    signature: signature.as_ptr(),
    attachment: ptr::null_mut(),
});
```

**Solution Needed:**
1. Create Rust extern "C" wrapper functions for each imported function
2. Store Java callback GlobalRef in thread-safe storage (e.g., static HashMap)
3. Wrapper retrieves JNIEnv via JavaVM.attach_current_thread()
4. Convert WASM arguments to JValues
5. Call Java method via JNI
6. Convert Java result back to WASM type
7. Handle exceptions gracefully

**Complexity:**
- Thread-local JNIEnv management
- Type conversion matrix (4 WASM types × various Java types)
- Exception marshaling (Java → WASM trap)
- Memory safety (GlobalRef lifecycle, JavaVM storage)

**Estimated Effort:** 2-3 days for complete callback implementation

## Resources

- [WAMR Native API Documentation](https://github.com/bytecodealliance/wasm-micro-runtime/blob/main/doc/export_native_api.md)
- [JNI Specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/jniTOC.html)
- [WebAssembly Imports Specification](https://webassembly.github.io/spec/core/syntax/modules.html#imports)

## Estimated Effort

- Experienced developer: 3-5 days
- Includes: Implementation, testing, debugging, documentation
- Most time spent on callback mechanism and error handling

## Conclusion

This is the final 2% to achieve 100% WebAssembly MVP coverage. The signature is now correct, and this document provides a clear implementation path. The Java API is ready, and the tests document expected behavior.

When implementing, start with simple function imports (no parameters, i32 result), then progressively add:
1. Parameters
2. Multiple return values
3. Different types (i64, f32, f64)
4. Global imports
5. Error handling
6. Memory/table imports (if needed)
