---
created: 2025-08-31T16:14:15Z
last_updated: 2025-08-31T16:14:15Z
version: 1.0
author: Claude Code PM System
---

# System Patterns

## Architectural Patterns

### 1. Factory Pattern - Runtime Selection
The system uses factory pattern for runtime selection between JNI and Panama implementations:

```java
// Planned pattern
public class RuntimeFactory {
    public static Engine createEngine() {
        if (PanamaSupport.isAvailable() && !forceJNI()) {
            return new PanamaEngine();
        }
        return new JniEngine();
    }
}
```

**Benefits:**
- Clean separation between API and implementation
- Runtime detection based on Java version and availability
- Manual override capability for testing/debugging
- No direct dependencies between public API and implementations

### 2. Interface Segregation - Clean API Boundaries
Public API defines only interfaces, no implementation details:

```java
// Planned API structure
public interface Engine {
    Module compileModule(byte[] wasmBytes);
    void close();
}

public interface Module {
    Instance instantiate();
    void close();
}

public interface Instance {
    Object invokeFunction(String name, Object... args);
    void close();
}
```

**Benefits:**
- Users only interact with clean interfaces
- Implementation details are hidden
- Easy to test with mocks
- Clear contract definitions

### 3. Adapter Pattern - Native API Wrapping
Both JNI and Panama implementations wrap the same native Rust library:

```java
// JNI implementation
public class JniEngine implements Engine {
    private native long nativeCreateEngine();
    private native void nativeClose(long handle);
}

// Panama implementation  
public class PanamaEngine implements Engine {
    private final MemorySegment engineHandle;
    // Uses Panama FFI to same native functions
}
```

**Benefits:**
- Code reuse through shared native library
- Consistent behavior between implementations
- Single source of truth for WAMR integration

### 4. Resource Management Pattern - RAII-style Cleanup
All native resources implement proper cleanup patterns:

```java
// Planned pattern
public class Engine implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            nativeClose();
        }
    }
    
    @Override
    protected void finalize() {
        if (!closed.get()) {
            // Log warning and cleanup
            close();
        }
    }
}
```

**Benefits:**
- Prevents resource leaks
- Defensive programming for JVM crash prevention
- Clear ownership semantics
- Compatibility with try-with-resources

## Error Handling Patterns

### 1. Two-Tier Error Handling
- **Internal Layer**: Comprehensive native error mapping with detailed context
- **Public API**: Broader exception categories for clean user experience

```java
// Planned exception hierarchy
public class WamrException extends Exception { }
public class CompilationException extends WamrException { }
public class RuntimeException extends WamrException { }
public class ValidationException extends WamrException { }
```

### 2. Defensive Programming Pattern
All public methods validate inputs before native calls:

```java
// Planned validation pattern
public Module compileModule(final byte[] wasmBytes) {
    Objects.requireNonNull(wasmBytes, "WebAssembly bytes cannot be null");
    if (wasmBytes.length == 0) {
        throw new ValidationException("WebAssembly bytes cannot be empty");
    }
    if (wasmBytes.length > MAX_MODULE_SIZE) {
        throw new ValidationException("WebAssembly module too large");
    }
    return doCompileModule(wasmBytes);
}
```

### 3. Error Context Preservation
Native errors are translated with context preservation:

```java
// Planned error translation
private void handleNativeError(int errorCode, String operation) {
    String context = String.format("Operation: %s, Error: %d", operation, errorCode);
    switch (errorCode) {
        case WAMR_ERR_COMPILATION:
            throw new CompilationException(context + " - Module compilation failed");
        case WAMR_ERR_RUNTIME:
            throw new RuntimeException(context + " - Runtime error during execution");
        default:
            throw new WamrException(context + " - Unknown error");
    }
}
```

## Performance Patterns

### 1. Lazy Initialization Pattern
Expensive resources are initialized only when needed:

```java
// Planned lazy loading
public class RuntimeFactory {
    private static volatile Engine defaultEngine;
    
    public static Engine getDefaultEngine() {
        if (defaultEngine == null) {
            synchronized (RuntimeFactory.class) {
                if (defaultEngine == null) {
                    defaultEngine = createEngine();
                }
            }
        }
        return defaultEngine;
    }
}
```

### 2. Object Pooling Pattern
For high-frequency allocations, consider pooling:

```java
// Planned pooling for instances
public class InstancePool {
    private final Queue<Instance> availableInstances = new ConcurrentLinkedQueue<>();
    private final Module module;
    
    public Instance acquire() {
        Instance instance = availableInstances.poll();
        return instance != null ? instance : module.instantiate();
    }
    
    public void release(Instance instance) {
        if (instance.isValid()) {
            availableInstances.offer(instance);
        }
    }
}
```

### 3. Batch Operations Pattern
Minimize JNI/Panama call overhead through batching:

```java
// Planned batch pattern
public interface Instance {
    // Single call
    Object invokeFunction(String name, Object... args);
    
    // Batch call for multiple operations
    Object[] invokeFunctions(FunctionCall... calls);
}
```

## Testing Patterns

### 1. Test Category Pattern
Different types of tests serve different purposes:

```java
// Unit tests - test individual components
@Test
@Category(UnitTest.class)
void testModuleCompilation() { }

// Integration tests - test cross-module compatibility
@Test  
@Category(IntegrationTest.class)
void testRuntimeSwitching() { }

// Performance tests - benchmark critical paths
@BenchmarkMode(Mode.Throughput)
@Test
void benchmarkFunctionInvocation() { }
```

### 2. Test Data Builder Pattern
Complex test objects built through fluent interface:

```java
// Planned test builder
public class TestModuleBuilder {
    public static TestModuleBuilder wasmModule() {
        return new TestModuleBuilder();
    }
    
    public TestModuleBuilder withFunction(String name, String... params) {
        // Build WASM function definition
        return this;
    }
    
    public byte[] build() {
        // Generate valid WASM bytes
    }
}
```

### 3. Parameterized Testing Pattern
Same tests run against both JNI and Panama implementations:

```java
// Planned parameterized tests
@ParameterizedTest
@MethodSource("runtimeImplementations")
void testFunctionInvocation(RuntimeType runtime) {
    Engine engine = RuntimeFactory.createEngine(runtime);
    // Test logic runs against both implementations
}

static Stream<RuntimeType> runtimeImplementations() {
    return Stream.of(RuntimeType.JNI, RuntimeType.PANAMA);
}
```

## Logging Patterns

### 1. Structured Logging Pattern
Consistent log format across all modules:

```java
// Planned logging structure
private static final Logger logger = Logger.getLogger(Engine.class.getName());

public void logOperation(String operation, long duration, boolean success) {
    String message = String.format("op=%s duration=%dms success=%b", 
                                  operation, duration, success);
    if (success) {
        logger.info(message);
    } else {
        logger.warning(message);
    }
}
```

### 2. Context-Rich Logging Pattern
Log entries include sufficient context for debugging:

```java
// Planned context logging
public void logNativeError(String operation, int errorCode, long resourceId) {
    logger.severe(String.format(
        "Native operation failed: op=%s error=%d resource=%d thread=%s", 
        operation, errorCode, resourceId, Thread.currentThread().getName()));
}
```

## Concurrency Patterns

### 1. Thread-Safe Resource Management
Native resources protected from concurrent access:

```java
// Planned thread safety
public class ThreadSafeEngine implements Engine {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean closed = false;
    
    public Module compileModule(byte[] wasmBytes) {
        lock.readLock().lock();
        try {
            if (closed) throw new IllegalStateException("Engine closed");
            return doCompileModule(wasmBytes);
        } finally {
            lock.readLock().unlock();
        }
    }
}
```

### 2. Copy-on-Write Pattern
For thread-safe access to collections:

```java
// Planned COW pattern for module cache
private final CopyOnWriteArrayList<CachedModule> moduleCache = new CopyOnWriteArrayList<>();
```

## Build Integration Patterns

### 1. Multi-Stage Build Pattern
Maven coordinates complex build process:

1. **Rust Native Build**: Compile WAMR and Rust wrapper
2. **Java Compilation**: Compile Java interfaces and implementations  
3. **Native Library Packaging**: Bundle platform-specific binaries
4. **Testing**: Run tests against all implementations
5. **Static Analysis**: Run code quality checks

### 2. Cross-Platform Build Pattern
Single build produces artifacts for multiple platforms:

```xml
<!-- Planned Maven profile pattern -->
<profiles>
    <profile>
        <id>native-linux-x86_64</id>
        <!-- Linux x86_64 build configuration -->
    </profile>
    <profile>
        <id>native-macos-aarch64</id>
        <!-- macOS ARM64 build configuration -->
    </profile>
</profiles>
```

## Integration Patterns

### 1. Version Detection Pattern
Runtime capabilities detected at startup:

```java
// Planned capability detection
public class RuntimeCapabilities {
    public static boolean isPanamaAvailable() {
        try {
            Class.forName("java.lang.foreign.MemorySegment");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

### 2. Graceful Degradation Pattern
Fallback behavior when preferred runtime unavailable:

```java
// Planned fallback pattern
public static Engine createEngine() {
    if (isPanamaPreferred() && isPanamaAvailable()) {
        try {
            return new PanamaEngine();
        } catch (Exception e) {
            logger.warning("Panama runtime failed, falling back to JNI: " + e.getMessage());
        }
    }
    return new JniEngine();
}
```

## Design Principles Applied

1. **Defensive Programming**: Input validation, resource cleanup, error handling
2. **Interface Segregation**: Clean API boundaries, no implementation leakage
3. **Single Responsibility**: Each class/module has focused purpose
4. **Open/Closed Principle**: Extensible design for new runtime implementations
5. **Dependency Inversion**: Depend on abstractions, not concretions