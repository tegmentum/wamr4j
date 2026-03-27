//! Fuzz target: memory read/write at arbitrary offsets and lengths
//!
//! Compiles a WASM module with exported memory, then performs read and
//! write operations at fuzzed offsets with fuzzed data. Tests that
//! out-of-bounds accesses are handled gracefully without crashes.

#![no_main]

use arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;
use std::sync::LazyLock;
use wamr4j_native::runtime;
use wamr4j_native::types::{WamrInstance, WamrModule, WamrRuntime};

/// A minimal WASM module with 1 page of exported memory.
///
/// WAT source:
/// ```wat
/// (module
///   (memory (export "memory") 1 4))
/// ```
static WASM_WITH_MEMORY: &[u8] = &[
    0x00, 0x61, 0x73, 0x6D, // magic
    0x01, 0x00, 0x00, 0x00, // version
    // Memory section: 1 memory, min=1, max=4
    0x05, 0x04, 0x01, 0x01, 0x01, 0x04,
    // Export section: 1 export "memory" memory 0
    0x07, 0x0A, 0x01,
    0x06, 0x6D, 0x65, 0x6D, 0x6F, 0x72, 0x79, 0x02, 0x00,
];

/// Fuzzed memory operation input.
#[derive(Arbitrary, Debug)]
struct FuzzedMemoryOp {
    /// Offset to read/write at (may be out of bounds)
    offset: u32,
    /// Data to write (for write operations)
    data: Vec<u8>,
    /// Length to read (for read operations)
    read_len: u16,
    /// Whether to do a write before the read
    do_write: bool,
    /// Whether to attempt a memory grow before access
    grow_pages: Option<u8>,
}

struct MemoryFixture {
    _runtime: WamrRuntime,
    _module: Box<WamrModule>,
    instance: Box<WamrInstance>,
}

/// Shared fixture: runtime + module with memory + instance.
/// Returns None if initialization fails (e.g. under AddressSanitizer).
static FIXTURE: LazyLock<Option<MemoryFixture>> = LazyLock::new(|| {
    let rt = match runtime::runtime_init() {
        Ok(rt) => rt,
        Err(e) => {
            eprintln!("Warning: WAMR runtime init failed: {e}");
            return None;
        }
    };
    let module = match runtime::module_compile(&rt, WASM_WITH_MEMORY) {
        Ok(m) => Box::new(m),
        Err(e) => {
            eprintln!("Warning: WASM module compile failed: {e}");
            return None;
        }
    };
    let instance = match runtime::instance_create(&module, 16 * 1024, 16 * 1024) {
        Ok(i) => Box::new(i),
        Err(e) => {
            eprintln!("Warning: WASM instance create failed: {e}");
            return None;
        }
    };
    Some(MemoryFixture {
        _runtime: rt,
        _module: module,
        instance,
    })
});

fuzz_target!(|input: FuzzedMemoryOp| {
    let fixture = match FIXTURE.as_ref() {
        Some(f) => f,
        None => return, // Fixture init failed (e.g. under ASan), skip gracefully
    };

    // Get memory handle
    let mut memory = match runtime::memory_get(&fixture.instance) {
        Ok(m) => m,
        Err(_) => return, // Memory not available
    };

    // Optionally attempt to grow memory
    if let Some(pages) = input.grow_pages {
        // Limit growth to avoid excessive memory usage in fuzzing
        let pages = (pages as u32).min(3);
        let _ = runtime::memory_grow(&mut memory, pages);
        // Re-fetch memory after growth to get updated size/pointer
        memory = match runtime::memory_get(&fixture.instance) {
            Ok(m) => m,
            Err(_) => return,
        };
    }

    let offset = input.offset as usize;

    // Optionally write data at the fuzzed offset
    if input.do_write && !input.data.is_empty() {
        // Limit write data to avoid excessive allocation
        let write_data = if input.data.len() > 4096 {
            &input.data[..4096]
        } else {
            &input.data
        };
        let _write_result = runtime::memory_write(&mut memory, offset, write_data);
        // Errors are expected for out-of-bounds offsets
    }

    // Read data at the fuzzed offset
    let read_len = (input.read_len as usize).min(4096);
    if read_len > 0 {
        let mut buf = vec![0u8; read_len];
        let _read_result = runtime::memory_read(&memory, offset, &mut buf);
        // Errors are expected for out-of-bounds offsets
    }

    wamr4j_native::utils::clear_last_error();
});
