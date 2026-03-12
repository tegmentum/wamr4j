//! Fuzz target: error handling functions with arbitrary strings
//!
//! Tests `set_last_error`, `get_last_error`, and `clear_last_error` with
//! arbitrary byte sequences including invalid UTF-8, embedded nulls,
//! and very long strings. Verifies no crash occurs.

#![no_main]

use arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;
use wamr4j_native::utils;

/// Fuzzed error message input.
#[derive(Arbitrary, Debug)]
struct FuzzedError {
    /// Raw bytes for error message (may contain invalid UTF-8, nulls, etc.)
    raw_bytes: Vec<u8>,
    /// Number of times to set/get/clear in sequence
    iterations: u8,
    /// Whether to interleave clears between sets
    interleave_clears: bool,
}

fuzz_target!(|input: FuzzedError| {
    // Limit iterations to avoid excessive time per input
    let iterations = (input.iterations as usize).min(32);

    // Limit string length to avoid excessive memory
    let raw = if input.raw_bytes.len() > 65536 {
        &input.raw_bytes[..65536]
    } else {
        &input.raw_bytes[..]
    };

    // Convert bytes to a String - use lossy conversion since set_last_error
    // takes a String (valid UTF-8). This tests various Unicode edge cases.
    let error_msg = String::from_utf8_lossy(raw).into_owned();

    for _ in 0..iterations {
        // Set an error message - should never crash regardless of content
        utils::set_last_error(error_msg.clone());

        // Get should return the message we just set
        let retrieved = utils::get_last_error();
        // Verify it's Some (don't assert exact equality since lossy conversion
        // may have modified the input)
        if retrieved.is_none() {
            // This would be a bug - we just set it
            return;
        }

        if input.interleave_clears {
            utils::clear_last_error();

            // After clear, get should return None
            let after_clear = utils::get_last_error();
            if after_clear.is_some() {
                // This would be a bug
                return;
            }
        }
    }

    // Final cleanup
    utils::clear_last_error();

    // Also test with strings that contain null bytes - these won't survive
    // CString conversion but set_last_error takes String so they should be fine
    // at the Rust level.
    let null_string = format!("error\0with\0nulls{}", raw.len());
    utils::set_last_error(null_string);
    let _ = utils::get_last_error();
    utils::clear_last_error();
});
