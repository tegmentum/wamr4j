/*
 * Simple test program to validate cross-compiled library loading
 * Compile with: gcc -o test-load test-load.c -ldl
 */

#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>

int main(int argc, char *argv[]) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <library_path>\n", argv[0]);
        return 1;
    }
    
    const char *lib_path = argv[1];
    printf("Testing library loading: %s\n", lib_path);
    
    // Load the library
    void *handle = dlopen(lib_path, RTLD_LAZY);
    if (!handle) {
        fprintf(stderr, "Error loading library: %s\n", dlerror());
        return 1;
    }
    
    printf("✓ Library loaded successfully\n");
    
    // Test function pointers
    typedef int (*test_init_func)();
    typedef const char* (*test_version_func)();
    typedef const char* (*test_platform_func)();
    typedef const char* (*test_arch_func)();
    
    // Load test functions
    test_init_func test_init = (test_init_func) dlsym(handle, "wamr4j_test_init");
    test_version_func test_version = (test_version_func) dlsym(handle, "wamr4j_test_get_version");
    test_platform_func test_platform = (test_platform_func) dlsym(handle, "wamr4j_test_platform");
    test_arch_func test_arch = (test_arch_func) dlsym(handle, "wamr4j_test_arch");
    
    if (!test_init || !test_version || !test_platform || !test_arch) {
        fprintf(stderr, "Error: Could not find test functions in library\n");
        dlclose(handle);
        return 1;
    }
    
    printf("✓ Test functions found\n");
    
    // Call test functions
    int init_result = test_init();
    const char *version = test_version();
    const char *platform = test_platform();
    const char *arch = test_arch();
    
    printf("✓ Functions executed successfully\n");
    printf("  Init result: %d\n", init_result);
    printf("  Version: %s\n", version);
    printf("  Platform: %s\n", platform);
    printf("  Architecture: %s\n", arch);
    
    // Clean up
    dlclose(handle);
    
    printf("✓ Library unloaded successfully\n");
    printf("✓ All tests passed!\n");
    
    return 0;
}