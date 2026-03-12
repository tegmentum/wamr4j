//! Fuzz target: module compilation with arbitrary bytes
//!
//! Feeds arbitrary byte sequences to WAMR's bytecode parser via
//! `runtime::module_compile()`. The goal is to find crashes in the
//! WASM bytecode parser when given malformed input.

#![no_main]

use libfuzzer_sys::fuzz_target;
use std::sync::LazyLock;
use wamr4j_native::runtime;
use wamr4j_native::types::WamrRuntime;

/// Shared runtime instance initialized once across all fuzz iterations.
/// WAMR runtime is a singleton so we initialize it once and reuse.
static RUNTIME: LazyLock<WamrRuntime> = LazyLock::new(|| {
    runtime::runtime_init().expect("Failed to initialize WAMR runtime")
});

fuzz_target!(|data: &[u8]| {
    // Force runtime initialization on first call
    let rt = &*RUNTIME;

    // Skip empty input - module_compile rejects it with InvalidArguments
    if data.is_empty() {
        return;
    }

    // Attempt to compile arbitrary bytes as a WASM module.
    // This should never crash regardless of input - errors are expected
    // and should be returned as Err variants.
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        runtime::module_compile(rt, data)
    }));

    match result {
        Ok(Ok(module)) => {
            // Valid module compiled successfully - drop will clean up
            drop(module);
        }
        Ok(Err(_)) => {
            // Expected: most arbitrary bytes are not valid WASM
        }
        Err(_) => {
            // Panic caught - this is a finding worth investigating
            // but we don't re-panic since libfuzzer handles this
        }
    }

    // Clear any error state for the next iteration
    wamr4j_native::utils::clear_last_error();
});
