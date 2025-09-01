#![no_main]

use libfuzzer_sys::fuzz_target;
use wamr4j_native::ffi::{
    wamr_runtime_init, wamr_runtime_destroy, 
    wamr_module_compile, wamr_module_destroy,
    wamr_instance_create, wamr_instance_destroy,
    wamr_function_lookup, wamr_function_call,
    wamr_memory_get, wamr_memory_read, wamr_memory_write
};
use std::ptr;

fuzz_target!(|data: &[u8]| {
    if data.len() < 8 {
        return;
    }
    
    // Split data: first half for WASM bytecode, second half for execution inputs
    let split_point = data.len() / 2;
    let wasm_bytes = &data[..split_point];
    let execution_data = &data[split_point..];
    
    if wasm_bytes.len() < 4 || execution_data.len() < 4 {
        return;
    }
    
    // Initialize runtime
    let runtime = wamr_runtime_init();
    if runtime.is_null() {
        return;
    }
    
    // Create error buffer
    let mut error_buf = [0i8; 256];
    
    // Try to compile the WebAssembly bytecode
    let module = unsafe {
        wamr_module_compile(
            runtime,
            wasm_bytes.as_ptr(),
            wasm_bytes.len() as i64,
            error_buf.as_mut_ptr(),
            256,
        )
    };
    
    if !module.is_null() {
        // Create instance
        let instance = unsafe {
            wamr_instance_create(
                module,
                65536, // 64KB stack
                1048576, // 1MB heap
                error_buf.as_mut_ptr(),
                256,
            )
        };
        
        if !instance.is_null() {
            // Try to get memory (if exists)
            let memory = unsafe { wamr_memory_get(instance) };
            
            if !memory.is_null() && execution_data.len() >= 8 {
                // Extract offset and size from execution data
                let offset = u32::from_le_bytes([
                    execution_data[0], execution_data[1], execution_data[2], execution_data[3]
                ]) % 1024; // Limit to reasonable range
                let size = u32::from_le_bytes([
                    execution_data[4], execution_data[5], execution_data[6], execution_data[7]
                ]) % 256; // Limit to reasonable range
                
                // Try memory operations
                if execution_data.len() > 8 + size as usize {
                    let write_data = &execution_data[8..8 + size as usize];
                    unsafe {
                        // Try to write to memory
                        wamr_memory_write(memory, offset, write_data.as_ptr(), size);
                        
                        // Try to read back from memory
                        let mut read_buffer = vec![0u8; size as usize];
                        wamr_memory_read(memory, offset, read_buffer.as_mut_ptr(), size);
                    }
                }
            }
            
            // Try to lookup and call functions with fuzzer data
            if execution_data.len() >= 16 {
                // Use remaining execution data to construct function names to test
                let func_name_candidates = [
                    b"main\0".as_ptr() as *const i8,
                    b"_start\0".as_ptr() as *const i8,
                    b"test\0".as_ptr() as *const i8,
                    b"add\0".as_ptr() as *const i8,
                    b"run\0".as_ptr() as *const i8,
                ];
                
                for func_name in &func_name_candidates {
                    let function = unsafe { wamr_function_lookup(instance, *func_name) };
                    if !function.is_null() {
                        // Try to call with empty parameters (safest approach)
                        unsafe {
                            wamr_function_call(
                                function,
                                ptr::null_mut(),
                                0,
                                ptr::null_mut(),
                                0,
                                error_buf.as_mut_ptr(),
                                256,
                            );
                        }
                        break; // Only call the first function we find
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