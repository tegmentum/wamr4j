---
created: 2025-08-31T16:14:15Z
last_updated: 2025-08-31T16:14:15Z
version: 1.0
author: Claude Code PM System
---

# Project Style Guide

## Code Style Standards

### Primary Standard: Google Java Style Guide
The project strictly follows the **Google Java Style Guide** (https://google.github.io/styleguide/javaguide.html) with specific modifications for JNI and native code integration.

### Fundamental Requirements

#### Character Encoding and File Format
- **Encoding**: UTF-8 exclusively for all source files
- **Line Endings**: Unix-style LF (\n) line endings
- **File Length**: Maximum 2000 lines per file
- **Line Length**: Maximum 120 characters (ignoring package/import statements and URLs)

#### Indentation and Whitespace
- **Indentation**: Spaces only, never tabs
- **Indentation Size**: 2 spaces for all indentation levels
- **Trailing Whitespace**: Not permitted
- **Empty Lines**: Single empty line to separate logical sections

### Naming Conventions

#### Package Naming
- **Format**: All lowercase
- **Pattern**: `ai.tegmentum.wamr4j[.subpackage]`
- **Examples**: 
  - `ai.tegmentum.wamr4j`
  - `ai.tegmentum.wamr4j.jni`
  - `ai.tegmentum.wamr4j.panama`
  - `ai.tegmentum.wamr4j.exceptions`

#### Class Naming
- **Format**: UpperCamelCase
- **Interfaces**: No prefix or suffix (e.g., `Engine`, not `IEngine`)
- **Implementations**: Descriptive suffix (e.g., `JniEngine`, `PanamaEngine`)
- **Exceptions**: Descriptive name with `Exception` suffix
- **Examples**:
  - `Engine` (interface)
  - `JniEngine` (JNI implementation)
  - `RuntimeFactory` (factory class)
  - `CompilationException` (exception class)

#### Method and Variable Naming
- **Format**: lowerCamelCase
- **Methods**: Verb phrases describing actions
- **Variables**: Noun phrases describing content
- **Boolean Variables**: Predicate form (e.g., `isValid`, `hasError`)
- **Examples**:
  - `compileModule(byte[] wasmBytes)`
  - `getInstance(String name)`
  - `boolean isModuleValid`
  - `int maxMemorySize`

#### Constant Naming
- **Format**: UPPER_CASE_WITH_UNDERSCORES
- **Scope**: Static final fields with primitive or immutable types
- **Examples**:
  - `public static final int MAX_MODULE_SIZE = 1024 * 1024;`
  - `private static final String DEFAULT_ENGINE_NAME = "wamr";`

#### Native Method Naming
- **Java Methods**: Standard lowerCamelCase
- **JNI C Functions**: Java_[package_path]_[class]_[method] format
- **Examples**:
  - Java: `private native long nativeCreateEngine();`
  - JNI: `Java_ai_tegmentum_wamr4j_jni_JniEngine_nativeCreateEngine`

### Code Organization

#### Import Statements
- **No Wildcard Imports**: Always use specific imports (e.g., `import java.util.List;` not `import java.util.*;`)
- **Import Order**: 
  1. Java standard library imports
  2. Third-party library imports
  3. Internal project imports
- **Import Grouping**: Single blank line between groups

#### Method Organization
- **Length Limit**: Maximum 150 lines per method
- **Parameter Limit**: Maximum 7 parameters per method
- **Constructor Order**: Default constructor first, then by increasing parameter count
- **Method Order**: Public, protected, package-private, private

#### Class Member Organization
```java
// 1. Static constants
public static final int MAX_SIZE = 1024;

// 2. Static variables
private static volatile Engine defaultEngine;

// 3. Instance fields
private final long nativeHandle;
private final AtomicBoolean closed = new AtomicBoolean(false);

// 4. Constructors
public Engine() { ... }

// 5. Public methods
public Module compileModule(final byte[] wasmBytes) { ... }

// 6. Protected/package methods
protected void validateState() { ... }

// 7. Private methods  
private native long nativeMethod();

// 8. Static nested classes
public static final class Builder { ... }
```

### Java Language Features

#### Method Parameters
- **Final Parameters**: All method parameters should be declared final
- **Validation**: Validate all public method parameters before use
- **Null Checking**: Use `Objects.requireNonNull()` for null validation

#### Exception Handling
- **Checked Exceptions**: Use for recoverable errors
- **Unchecked Exceptions**: Use for programming errors
- **Exception Chaining**: Preserve original exception context
- **Resource Management**: Use try-with-resources for AutoCloseable resources

#### Generics and Collections
- **Type Parameters**: Single uppercase letter (T, E, K, V)
- **Wildcards**: Use bounded wildcards for flexibility
- **Collections**: Prefer interface types (List, Set, Map) for declarations

#### Boolean Logic
- **Simplification**: Avoid complex boolean expressions
- **Early Return**: Use early returns to reduce nesting
- **Switch Statements**: Always include default case

### Documentation Standards

#### Javadoc Requirements
- **Public API**: All public classes, methods, and fields must have Javadoc
- **Package Documentation**: Package-info.java files for all packages
- **Format**: Standard Javadoc tags (@param, @return, @throws, etc.)

#### Javadoc Example
```java
/**
 * Compiles WebAssembly bytecode into an executable module.
 *
 * <p>This method validates the input bytecode and creates a compiled module
 * that can be instantiated for execution. The compilation process includes
 * validation of the WebAssembly format and optimization for the target
 * runtime.
 *
 * @param wasmBytes the WebAssembly bytecode to compile, must not be null
 * @return a compiled module ready for instantiation
 * @throws ValidationException if the WebAssembly bytecode is invalid
 * @throws CompilationException if compilation fails for any reason
 * @throws IllegalArgumentException if wasmBytes is null or empty
 * @since 1.0
 */
public Module compileModule(final byte[] wasmBytes) throws WamrException {
    // Implementation
}
```

#### Comment Style
- **Line Comments**: Use `//` for single-line comments
- **Block Comments**: Use `/* */` for multi-line explanations
- **TODO Comments**: Format as `// TODO: description`
- **Implementation Notes**: Document complex algorithms and native integration

### Native Code Integration

#### JNI Method Declarations
```java
// Correct JNI method declaration
private native long nativeCreateEngine();
private native void nativeCloseEngine(long handle);
private native byte[] nativeCompileModule(long engineHandle, byte[] wasmBytes);
```

#### Native Resource Management
```java
public final class Engine implements AutoCloseable {
    private final long nativeHandle;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            nativeClose(nativeHandle);
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed.get()) {
                // Log warning about manual cleanup
                close();
            }
        } finally {
            super.finalize();
        }
    }
}
```

### Rust Code Style (Native Library)

#### Naming Conventions
- **Functions**: snake_case
- **Constants**: UPPER_CASE
- **Types**: PascalCase
- **Modules**: snake_case

#### JNI Exports
```rust
// JNI function naming follows Java package structure
#[no_mangle]
pub extern "C" fn Java_ai_tegmentum_wamr4j_jni_JniEngine_nativeCreateEngine(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    // Implementation
}
```

#### Panama FFI Exports
```rust
// Panama exports use C-compatible naming
#[no_mangle]
pub extern "C" fn wamr_create_engine() -> *mut Engine {
    // Implementation
}
```

### Testing Style

#### Test Method Naming
- **Format**: test_[scenario]_[expected_result]
- **Examples**:
  - `test_compileValidModule_returnsModule()`
  - `test_compileInvalidModule_throwsValidationException()`
  - `test_closeEngine_preventsSubsequentOperations()`

#### Test Organization
```java
@Test
@DisplayName("Module compilation with valid WebAssembly bytecode should succeed")
void test_compileValidModule_returnsModule() throws Exception {
    // Given
    final byte[] validWasm = TestUtils.loadWasmFile("valid_module.wasm");
    final Engine engine = RuntimeFactory.createEngine();
    
    // When
    final Module module = engine.compileModule(validWasm);
    
    // Then
    assertThat(module).isNotNull();
    assertThat(module.isValid()).isTrue();
    
    // Cleanup
    module.close();
    engine.close();
}
```

### Static Analysis Integration

#### Checkstyle Configuration
- **Google Java Style**: Enforced via Checkstyle plugin
- **Custom Rules**: Additional rules for JNI method validation
- **Suppressions**: Minimal use, document all suppressions

#### Spotless Configuration
- **Automatic Formatting**: Google Java Format integration
- **Import Organization**: Automatic import sorting
- **License Headers**: Automatic license header maintenance

#### SpotBugs Configuration
- **Bug Detection**: Enhanced with FindSecBugs plugin
- **Native Code**: Custom rules for JNI resource management
- **Security**: Focus on input validation and resource leaks

### Build Integration

#### Maven Plugin Configuration
```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.17.0</version>
                <style>GOOGLE</style>
            </googleJavaFormat>
            <removeUnusedImports />
            <importOrder>
                <order>java,javax,org,com,ai.tegmentum</order>
            </importOrder>
        </java>
    </configuration>
</plugin>
```

### Performance Considerations

#### JNI Performance Patterns
- **Minimize JNI Calls**: Batch operations when possible
- **Array Handling**: Use direct byte buffers for large data transfers
- **String Conversion**: Minimize Java string to native string conversions
- **Exception Handling**: Use return codes for frequent error conditions

#### Memory Management
- **Native Resources**: Always pair allocation with deallocation
- **Weak References**: Use for caches to allow garbage collection
- **DirectByteBuffer**: Use for memory shared with native code

### Error Handling Patterns

#### Input Validation
```java
public Module compileModule(final byte[] wasmBytes) throws WamrException {
    Objects.requireNonNull(wasmBytes, "WebAssembly bytes cannot be null");
    
    if (wasmBytes.length == 0) {
        throw new ValidationException("WebAssembly bytes cannot be empty");
    }
    
    if (wasmBytes.length > MAX_MODULE_SIZE) {
        throw new ValidationException(
            String.format("Module size %d exceeds maximum %d", 
                         wasmBytes.length, MAX_MODULE_SIZE));
    }
    
    return doCompileModule(wasmBytes);
}
```

#### Exception Translation
```java
private Module doCompileModule(final byte[] wasmBytes) throws WamrException {
    final long moduleHandle = nativeCompileModule(nativeHandle, wasmBytes);
    
    if (moduleHandle == 0) {
        final int errorCode = nativeGetLastError(nativeHandle);
        throw translateNativeError(errorCode, "module compilation");
    }
    
    return new ModuleImpl(moduleHandle);
}
```

This style guide ensures consistency across all code in the wamr4j project while maintaining the highest standards for safety, performance, and maintainability.