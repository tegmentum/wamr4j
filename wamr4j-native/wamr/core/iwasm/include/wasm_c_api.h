
#ifndef WASM_C_API_H
#define WASM_C_API_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Basic types
typedef uint8_t byte_t;
typedef float float32_t;
typedef double float64_t;

// Forward declarations
typedef struct wasm_byte_vec_t wasm_byte_vec_t;
typedef struct wasm_name_t wasm_name_t;

// Byte vector
struct wasm_byte_vec_t {
    size_t size;
    byte_t* data;
};

// Name type
struct wasm_name_t {
    wasm_byte_vec_t data;
};

#ifdef __cplusplus
}
#endif

#endif /* WASM_C_API_H */
