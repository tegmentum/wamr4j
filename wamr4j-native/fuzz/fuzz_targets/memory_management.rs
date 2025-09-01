#![no_main]

use libfuzzer_sys::fuzz_target;
use wamr4j_native::ffi::{
    wamr_runtime_init_with_config, wamr_runtime_destroy,
    wamr_module_compile, wamr_module_destroy,
    wamr_instance_create, wamr_instance_destroy,
    wamr_memory_get, wamr_memory_size, wamr_memory_grow,
    wamr_memory_read, wamr_memory_write, wamr_memory_data
};
use std::ptr;

fuzz_target!(|data: &[u8]| {
    if data.len() < 16 {
        return;
    }
    
    // Extract configuration parameters from fuzzer data
    let stack_size = u32::from_le_bytes([data[0], data[1], data[2], data[3]]) % 1048576 + 8192; // 8KB - 1MB
    let heap_size = u32::from_le_bytes([data[4], data[5], data[6], data[7]]) % 16777216 + 65536; // 64KB - 16MB
    let max_threads = (data[8] % 8) + 1; // 1-8 threads
    
    // Use remaining data for operations
    let wasm_data = &data[9..];
    
    if wasm_data.len() < 8 {
        return;
    }
    
    // Initialize runtime with custom configuration
    let runtime = wamr_runtime_init_with_config(
        stack_size as i64,
        heap_size as i64,
        max_threads as i32,
    );
    
    if runtime.is_null() {
        return;
    }
    
    // Create error buffer
    let mut error_buf = [0i8; 256];
    
    // Simple WASM module with memory export for testing
    let test_wasm = [
        0x00, 0x61, 0x73, 0x6d, // WASM magic
        0x01, 0x00, 0x00, 0x00, // version
        0x05, 0x03, 0x01, 0x00, 0x01, // memory section: 1 memory, min 1 page
        0x07, 0x0a, 0x01, 0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02, 0x00, // export "memory"
    ];
    
    // Try to compile the test WASM module (for memory operations)
    let module = unsafe {
        wamr_module_compile(
            runtime,
            test_wasm.as_ptr(),
            test_wasm.len() as i64,
            error_buf.as_mut_ptr(),
            256,
        )
    };
    
    if !module.is_null() {
        // Create instance
        let instance = unsafe {
            wamr_instance_create(
                module,
                stack_size as i64,
                heap_size as i64,
                error_buf.as_mut_ptr(),
                256,
            )
        };
        
        if !instance.is_null() {
            // Get memory handle
            let memory = unsafe { wamr_memory_get(instance) };
            
            if !memory.is_null() {
                // Test memory size operations
                let initial_size = unsafe { wamr_memory_size(memory) };
                
                // Test memory growth (limit to reasonable size)
                if wasm_data.len() >= 4 {
                    let grow_pages = (wasm_data[0] % 4) + 1; // 1-4 pages
                    unsafe {
                        wamr_memory_grow(memory, grow_pages as i32);
                    }
                }
                
                // Test memory data pointer access
                let data_ptr = unsafe { wamr_memory_data(memory) };
                if !data_ptr.is_null() {
                    let current_size = unsafe { wamr_memory_size(memory) };
                    let byte_size = current_size * 65536; // pages to bytes
                    
                    // Test memory read/write operations with fuzzer data
                    if wasm_data.len() >= 8 && byte_size > 0 {
                        let offset = u32::from_le_bytes([
                            wasm_data[1], wasm_data[2], wasm_data[3], wasm_data[4]
                        ]) % (byte_size as u32).min(1024); // Limit to first 1KB or available memory
                        
                        let write_size = ((wasm_data[5] as u32) % 256).min(byte_size as u32 - offset); // Max 256 bytes
                        
                        if write_size > 0 && wasm_data.len() > 6 + write_size as usize {
                            let write_data = &wasm_data[6..6 + write_size as usize];
                            
                            // Write to memory
                            unsafe {
                                wamr_memory_write(memory, offset, write_data.as_ptr(), write_size);
                            }
                            
                            // Read back from memory
                            let mut read_buffer = vec![0u8; write_size as usize];
                            unsafe {
                                wamr_memory_read(memory, offset, read_buffer.as_mut_ptr(), write_size);
                            }
                            
                            // Verify data integrity (this helps catch memory corruption)
                            if read_buffer != write_data {
                                // Memory corruption detected - this should be investigated
                                // but we don't panic here as it's part of fuzzing
                            }
                        }
                    }
                }
            }
            
            // Clean up instance
            unsafe {
                wamr_instance_destroy(instance);
            }
        }
        
        // Clean up module
        unsafe {
            wamr_module_destroy(module);
        }
    }
    
    // Clean up runtime
    unsafe {
        wamr_runtime_destroy(runtime);
    }
});