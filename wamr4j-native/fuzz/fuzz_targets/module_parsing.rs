#![no_main]

use libfuzzer_sys::fuzz_target;
use wamr4j_native::{wamr4j_test_init};

fuzz_target!(|data: &[u8]| {
    // Simple test to verify the dependency resolution
    unsafe {
        wamr4j_test_init();
    }
    
    // Just ignore the fuzzer data for now
    let _ = data;
});
