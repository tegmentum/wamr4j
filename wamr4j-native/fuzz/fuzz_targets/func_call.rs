//! Fuzz target: function calls with arbitrary arguments
//!
//! Compiles a valid small WASM module with known functions, then calls
//! those functions with fuzzed argument types, counts, and boundary values.
//! Tests that type mismatches and invalid arguments are handled gracefully.

#![no_main]

use arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;
use std::sync::LazyLock;
use wamr4j_native::runtime;
use wamr4j_native::types::{WamrInstance, WamrModule, WamrRuntime, WasmValue};

/// A minimal WASM module (binary format) with two exported functions:
/// - "add": (i32, i32) -> i32
/// - "identity_i64": (i64) -> i64
///
/// WAT source:
/// ```wat
/// (module
///   (func (export "add") (param i32 i32) (result i32)
///     local.get 0
///     local.get 1
///     i32.add)
///   (func (export "identity_i64") (param i64) (result i64)
///     local.get 0))
/// ```
static WASM_MODULE: &[u8] = &[
    0x00, 0x61, 0x73, 0x6D, // magic
    0x01, 0x00, 0x00, 0x00, // version
    // Type section: 2 types
    0x01, 0x0B, 0x02,
    // type 0: (i32, i32) -> i32
    0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
    // type 1: (i64) -> i64
    0x60, 0x01, 0x7E, 0x01, 0x7E,
    // Function section: 2 functions
    0x03, 0x03, 0x02, 0x00, 0x01,
    // Export section: 2 exports
    0x07, 0x1B, 0x02,
    // export "add" func 0
    0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
    // export "identity_i64" func 1
    0x0D, 0x69, 0x64, 0x65, 0x6E, 0x74, 0x69, 0x74,
    0x79, 0x5F, 0x69, 0x36, 0x34, 0x00, 0x01,
    // Code section: 2 function bodies
    0x0A, 0x0F, 0x02,
    // func 0: local.get 0, local.get 1, i32.add
    0x05, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B,
    // func 1: local.get 0
    0x04, 0x00, 0x20, 0x00, 0x0B,
];

/// Fuzzed input for function calls.
#[derive(Arbitrary, Debug)]
struct FuzzedCall {
    /// Which function to call (0 = "add", 1 = "identity_i64", other = invalid name)
    func_index: u8,
    /// Arguments to pass (may have wrong count or types)
    args: Vec<FuzzedValue>,
}

/// Fuzzed WASM value - can produce any value type including mismatches.
#[derive(Arbitrary, Debug)]
enum FuzzedValue {
    I32(i32),
    I64(i64),
    F32(f32),
    F64(f64),
}

impl FuzzedValue {
    fn to_wasm_value(&self) -> WasmValue {
        match self {
            FuzzedValue::I32(v) => WasmValue::I32(*v),
            FuzzedValue::I64(v) => WasmValue::I64(*v),
            FuzzedValue::F32(v) => WasmValue::F32(*v),
            FuzzedValue::F64(v) => WasmValue::F64(*v),
        }
    }
}

struct TestFixture {
    _runtime: WamrRuntime,
    _module: Box<WamrModule>,
    instance: Box<WamrInstance>,
}

/// Shared test fixture: runtime + module + instance, initialized once.
static FIXTURE: LazyLock<TestFixture> = LazyLock::new(|| {
    let rt = runtime::runtime_init().expect("Failed to initialize WAMR runtime");
    let module = runtime::module_compile(&rt, WASM_MODULE)
        .expect("Failed to compile test WASM module");
    let module = Box::new(module);
    let instance = runtime::instance_create(&module, 16 * 1024, 16 * 1024)
        .expect("Failed to create instance");
    let instance = Box::new(instance);
    TestFixture {
        _runtime: rt,
        _module: module,
        instance,
    }
});

fuzz_target!(|input: FuzzedCall| {
    let fixture = &*FIXTURE;

    // Select function name based on fuzzed index
    let func_name = match input.func_index {
        0 => "add",
        1 => "identity_i64",
        _ => "nonexistent_func",
    };

    // Look up the function - may fail for invalid names
    let func = match runtime::function_lookup(&fixture.instance, func_name) {
        Ok(f) => f,
        Err(_) => return, // Expected for invalid function names
    };

    // Convert fuzzed values to WasmValues
    let args: Vec<WasmValue> = input.args.iter().map(|v| v.to_wasm_value()).collect();

    // Call the function with potentially wrong arg count/types.
    // This should return an error, never crash.
    let _result = runtime::function_call(&func, fixture.instance.exec_env, &args);

    wamr4j_native::utils::clear_last_error();
});
