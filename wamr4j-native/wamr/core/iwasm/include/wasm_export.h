
#ifndef WASM_EXPORT_H
#define WASM_EXPORT_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct wasm_runtime_t wasm_runtime_t;
typedef struct wasm_module_t wasm_module_t;
typedef struct wasm_module_inst_t wasm_module_inst_t;
typedef struct wasm_function_inst_t wasm_function_inst_t;

// Runtime management
wasm_runtime_t* wasm_runtime_init(void);
void wasm_runtime_destroy(void);

// Module operations
wasm_module_t* wasm_runtime_load(const unsigned char* buf, unsigned int size, char* error_buf, unsigned int error_buf_size);
void wasm_runtime_unload(wasm_module_t* module);

// Instance operations
wasm_module_inst_t* wasm_runtime_instantiate(const wasm_module_t* module, unsigned int stack_size, unsigned int heap_size, char* error_buf, unsigned int error_buf_size);
void wasm_runtime_deinstantiate(wasm_module_inst_t* module_inst);

// Function operations
wasm_function_inst_t* wasm_runtime_lookup_function(const wasm_module_inst_t* module_inst, const char* name);
int wasm_runtime_call_wasm(wasm_function_inst_t* function, unsigned int argc, unsigned int* argv);

// Memory operations
void* wasm_runtime_addr_app_to_native(const wasm_module_inst_t* module_inst, unsigned int app_offset);
unsigned int wasm_runtime_addr_native_to_app(const wasm_module_inst_t* module_inst, void* native_ptr);
unsigned int wasm_runtime_get_app_addr_range(const wasm_module_inst_t* module_inst, unsigned int app_offset, unsigned int* p_app_start_offset, unsigned int* p_app_end_offset);

#ifdef __cplusplus
}
#endif

#endif /* WASM_EXPORT_H */
