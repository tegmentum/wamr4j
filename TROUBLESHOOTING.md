# WAMR4J Troubleshooting Guide

This guide provides solutions to common issues encountered during WAMR4J development, setup, and usage.

## Table of Contents

- [Build Issues](#build-issues)
- [IDE Configuration Issues](#ide-configuration-issues)
- [Runtime Issues](#runtime-issues)
- [Testing Issues](#testing-issues)
- [Code Quality Issues](#code-quality-issues)
- [Performance Issues](#performance-issues)
- [Environment-Specific Issues](#environment-specific-issues)

## Build Issues

### Maven Build Fails with "Could not resolve dependencies"

**Symptoms:**
```
[ERROR] Could not resolve dependencies for project ai.tegmentum.wamr4j:wamr4j-jni:jar:1.0.0-SNAPSHOT
[ERROR] Could not find artifact ai.tegmentum.wamr4j:wamr4j:jar:1.0.0-SNAPSHOT
```

**Solutions:**

1. **Install core module first:**
   ```bash
   ./mvnw clean install -pl wamr4j
   ./mvnw clean install -pl wamr4j-native
   ```

2. **Build modules in dependency order:**
   ```bash
   ./mvnw clean install -pl wamr4j,wamr4j-native,wamr4j-jni
   ```

3. **Clean and rebuild everything:**
   ```bash
   ./mvnw clean install
   ```

### Compilation Errors in Panama Module

**Symptoms:**
```
[ERROR] ai.tegmentum.wamr4j.panama.impl.PanamaWebAssemblyModule is not abstract and does not override abstract method
```

**Solutions:**

1. **Check Java version:**
   ```bash
   java -version
   # Must be Java 23+ for Panama module
   ```

2. **Skip Panama module during development:**
   ```bash
   ./mvnw clean compile -pl '!wamr4j-panama'
   ```

3. **Use appropriate JDK:**
   ```bash
   export JAVA_HOME=/path/to/jdk-23
   ./mvnw clean compile
   ```

### Native Library Compilation Fails

**Symptoms:**
```
[ERROR] Failed to execute goal ... (default-compile) on project wamr4j-native
```

**Solutions:**

1. **Check Rust installation:**
   ```bash
   rustc --version
   # Should be 1.70+
   ```

2. **Install required targets:**
   ```bash
   rustup target add x86_64-unknown-linux-gnu
   rustup target add aarch64-unknown-linux-gnu
   ```

3. **Skip native compilation temporarily:**
   ```bash
   ./mvnw clean compile -DskipNative=true
   ```

### Maven Wrapper Issues

**Symptoms:**
```bash
./mvnw: Permission denied
```

**Solutions:**

1. **Fix permissions:**
   ```bash
   chmod +x ./mvnw
   ```

2. **Use system Maven:**
   ```bash
   mvn clean compile
   ```

3. **Re-download wrapper:**
   ```bash
   mvn wrapper:wrapper
   ```

## IDE Configuration Issues

### IntelliJ IDEA

#### Project Not Importing Correctly

**Symptoms:**
- Modules not recognized
- Dependencies not resolving
- Code style not applied

**Solutions:**

1. **Reimport Maven project:**
   ```
   File > Reload Maven Projects
   ```

2. **Invalidate caches:**
   ```
   File > Invalidate Caches and Restart
   ```

3. **Check project structure:**
   ```
   File > Project Structure > Modules
   # Verify all modules are present
   ```

#### Code Style Not Applied

**Symptoms:**
- Wrong indentation (4 spaces instead of 2)
- Incorrect import order
- Line length violations

**Solutions:**

1. **Verify code style settings:**
   ```
   Settings > Editor > Code Style > Java
   # Should show "Project" scheme
   ```

2. **Reapply code style:**
   ```
   Code > Reformat Code (Ctrl+Alt+L)
   ```

3. **Check .idea/codeStyles/ directory exists**

### Eclipse IDE

#### Import Fails

**Symptoms:**
- "No projects are found to import"
- "Build path errors"

**Solutions:**

1. **Use Maven import:**
   ```
   File > Import > Existing Maven Projects
   ```

2. **Copy Eclipse configuration:**
   ```bash
   cp eclipse/.project .
   cp eclipse/.classpath .
   ```

3. **Refresh and clean:**
   ```
   Project > Refresh
   Project > Clean > Clean all projects
   ```

#### Code Formatting Issues

**Solutions:**

1. **Install Google Java Format plugin**
2. **Configure import order** (see eclipse/ECLIPSE_SETUP.md)
3. **Set up Checkstyle plugin**

## Runtime Issues

### WebAssembly Module Loading Fails

**Symptoms:**
```java
RuntimeException: Failed to load native library
```

**Solutions:**

1. **Check Java version compatibility:**
   ```bash
   java -version
   # JNI: Java 11+, Panama: Java 23+
   ```

2. **Verify native library path:**
   ```java
   System.setProperty("java.library.path", "/path/to/natives");
   ```

3. **Check architecture compatibility:**
   ```bash
   uname -m  # Should match compiled native library
   ```

### JVM Crashes During WebAssembly Execution

**Symptoms:**
- Segmentation faults
- Unexpected JVM termination
- Native method errors

**Solutions:**

1. **Enable JVM debugging:**
   ```bash
   java -XX:+PrintGC -XX:+PrintGCDetails -XX:+ShowCodeDetailsInExceptionMessages
   ```

2. **Check input validation:**
   ```java
   // Ensure all inputs are validated before native calls
   Objects.requireNonNull(wasmBytes, "WebAssembly bytes cannot be null");
   ```

3. **Use defensive programming:**
   ```java
   try {
       return nativeCall(params);
   } catch (Exception e) {
       log.error("Native call failed", e);
       throw new RuntimeException("Safe fallback", e);
   }
   ```

### Memory Leaks

**Symptoms:**
- Increasing memory usage
- OutOfMemoryError
- GC pressure

**Solutions:**

1. **Ensure proper resource cleanup:**
   ```java
   try (WebAssemblyModule module = runtime.loadModule(bytes)) {
       // Use module
   } // Automatically closed
   ```

2. **Profile memory usage:**
   ```bash
   java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof
   ```

3. **Monitor native memory:**
   ```bash
   java -XX:NativeMemoryTracking=summary
   ```

## Testing Issues

### Tests Fail with "Native Library Not Found"

**Solutions:**

1. **Build native libraries first:**
   ```bash
   ./mvnw clean compile -pl wamr4j-native
   ```

2. **Set library path for tests:**
   ```bash
   ./mvnw test -Djava.library.path=target/natives
   ```

3. **Check test resources:**
   ```bash
   ls -la src/test/resources/
   # Verify test WASM files exist
   ```

### Integration Tests Timeout

**Solutions:**

1. **Increase timeout:**
   ```xml
   <plugin>
     <groupId>org.apache.maven.plugins</groupId>
     <artifactId>maven-surefire-plugin</artifactId>
     <configuration>
       <forkedProcessTimeoutInSeconds>300</forkedProcessTimeoutInSeconds>
     </configuration>
   </plugin>
   ```

2. **Run tests individually:**
   ```bash
   ./mvnw test -Dtest=SpecificTestClass
   ```

### Performance Tests Fail

**Solutions:**

1. **Warm up JVM:**
   ```java
   @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
   ```

2. **Check system load:**
   ```bash
   top  # Ensure system isn't overloaded
   ```

3. **Use isolated test environment**

## Code Quality Issues

### Checkstyle Violations

**Common violations and fixes:**

1. **Import order:**
   ```bash
   # Fix automatically
   ./mvnw spotless:apply
   ```

2. **Line too long (>120 chars):**
   ```java
   // Before
   public WebAssemblyInstance instantiate(final Map<String, Object> imports, final WebAssemblyRuntimeConfiguration config) {
   
   // After  
   public WebAssemblyInstance instantiate(final Map<String, Object> imports, 
                                        final WebAssemblyRuntimeConfiguration config) {
   ```

3. **Missing Javadoc:**
   ```java
   /**
    * Creates a new WebAssembly instance from the module.
    *
    * @param imports the import object bindings
    * @return new WebAssembly instance
    * @throws RuntimeException if instantiation fails
    */
   public WebAssemblyInstance instantiate(final Map<String, Object> imports) {
   ```

### SpotBugs Issues

#### Java Version Compatibility Error

**Symptoms:**
```
java.lang.IllegalArgumentException: Unsupported class file major version 67
```

**Solutions:**

1. **Use Java 11 for SpotBugs analysis:**
   ```bash
   JAVA_HOME=/path/to/jdk-11 ./mvnw spotbugs:check
   ```

2. **Skip SpotBugs temporarily:**
   ```bash
   ./mvnw checkstyle:check spotless:check
   ```

3. **Update SpotBugs plugin version** in pom.xml (if newer version available)

**Common issues:**

1. **Null pointer dereference:**
   ```java
   // Add null checks
   Objects.requireNonNull(parameter, "Parameter cannot be null");
   ```

2. **Resource not closed:**
   ```java
   // Use try-with-resources
   try (InputStream is = Files.newInputStream(path)) {
       // Use stream
   }
   ```

3. **Thread safety:**
   ```java
   // Use concurrent collections
   private final Map<String, Object> cache = new ConcurrentHashMap<>();
   ```

### Spotless Formatting Issues

**Solutions:**

1. **Auto-fix formatting:**
   ```bash
   ./mvnw spotless:apply
   ```

2. **Check what will be changed:**
   ```bash
   ./mvnw spotless:check
   ```

3. **Configure IDE auto-format** to match Spotless

## Performance Issues

### Slow WebAssembly Execution

**Solutions:**

1. **Profile execution:**
   ```bash
   java -agentpath:/path/to/profiler
   ```

2. **Check compilation mode:**
   ```java
   // Enable AOT compilation if supported
   runtime.setCompilationMode(AHEAD_OF_TIME);
   ```

3. **Optimize native calls:**
   ```java
   // Batch operations
   memory.writeBytes(offset, allBytes); // Instead of byte-by-byte
   ```

### High GC Pressure

**Solutions:**

1. **Monitor GC:**
   ```bash
   java -XX:+PrintGC -XX:+PrintGCTimeStamps
   ```

2. **Reduce object allocation:**
   ```java
   // Reuse buffers
   private final ByteBuffer reusableBuffer = ByteBuffer.allocate(1024);
   ```

3. **Use off-heap storage** for large WebAssembly modules

## Environment-Specific Issues

### macOS Issues

#### Code Signing Problems

**Symptoms:**
```
"libwamr4j.dylib" cannot be opened because the developer cannot be verified
```

**Solutions:**

1. **Allow unsigned libraries:**
   ```bash
   sudo spctl --master-disable
   ```

2. **Sign libraries manually:**
   ```bash
   codesign -s - target/natives/libwamr4j.dylib
   ```

### Linux Issues

#### Missing Dependencies

**Symptoms:**
```
error while loading shared libraries: libwamr4j.so
```

**Solutions:**

1. **Install development tools:**
   ```bash
   sudo apt-get install build-essential
   sudo yum install gcc gcc-c++
   ```

2. **Set LD_LIBRARY_PATH:**
   ```bash
   export LD_LIBRARY_PATH=target/natives:$LD_LIBRARY_PATH
   ```

### Windows Issues

#### Path Length Limitations

**Solutions:**

1. **Enable long paths:**
   ```powershell
   # Run as Administrator
   Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Value 1
   ```

2. **Use shorter paths:**
   ```bash
   # Move project closer to root
   C:\dev\wamr4j
   ```

#### Library Loading Issues

**Solutions:**

1. **Install Visual C++ Redistributable**
2. **Check PATH environment variable**
3. **Use absolute paths** for native libraries

## Getting More Help

### Diagnostic Information

When reporting issues, include:

```bash
# System information
java -version
mvn -version
uname -a  # Linux/macOS
systeminfo  # Windows

# Build information
./mvnw --version
./mvnw dependency:tree

# Runtime information
java -XX:+PrintFlagsFinal | grep -i gc
```

### Log Files

Enable verbose logging:

```java
// In test code
System.setProperty("wamr4j.logging.level", "DEBUG");
```

```bash
# Maven verbose output
./mvnw -X clean compile
```

### Community Resources

- **GitHub Issues**: Report bugs and feature requests
- **GitHub Discussions**: Ask questions and share solutions
- **Documentation**: All guides are in the repository

---

**Still need help?** Create a GitHub issue with:
1. **Environment details** (OS, Java version, etc.)
2. **Complete error messages** and stack traces
3. **Steps to reproduce** the issue
4. **Expected vs. actual behavior**