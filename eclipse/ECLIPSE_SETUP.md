# Eclipse Setup Guide for WAMR4J

This guide helps you set up Eclipse IDE for WAMR4J development.

## Prerequisites

- Eclipse IDE 2023-09 or later
- Java 11+ for development (Java 23+ for Panama modules)
- Maven integration (m2e plugin)

## Import Instructions

### Method 1: Import as Maven Project (Recommended)

1. **Open Eclipse**
2. **File > Import...**
3. **Select "Existing Maven Projects"**
4. **Browse to** the `wamr4j` root directory
5. **Select all modules** that appear
6. **Click Finish**

### Method 2: Import as Existing Project

1. **Open Eclipse**
2. **File > Import...**
3. **Select "Existing Projects into Workspace"**
4. **Copy files from** `/eclipse/` directory to project root:
   ```bash
   cp eclipse/.project .
   cp eclipse/.classpath .
   ```
5. **Browse to** the project directory
6. **Click Finish**

## Required Plugins

Install these Eclipse plugins for optimal development experience:

### Essential Plugins
- **Maven Integration (m2e)** - Usually pre-installed
- **Checkstyle Plugin** - `https://checkstyle.org/eclipse`
- **SpotBugs Plugin** - `https://spotbugs.readthedocs.io/en/latest/eclipse.html`

### Installation Steps
1. **Help > Eclipse Marketplace**
2. **Search for** "Checkstyle" and "SpotBugs"
3. **Install both plugins**
4. **Restart Eclipse**

## Code Style Configuration

### Apply Google Java Style
1. **Window > Preferences** (Eclipse > Preferences on macOS)
2. **Java > Code Style > Formatter**
3. **Import...** the formatter config:
   - Download: https://github.com/google/styleguide/blob/gh-pages/eclipse-java-google-style.xml
   - Save as `google-java-format.xml`
   - Import this file
4. **Set as active formatter**

### Configure Import Order
1. **Java > Code Style > Organize Imports**
2. **Set import order**:
   ```
   java
   javax
   [blank line]
   org
   [blank line]  
   com
   [blank line]
   ai.tegmentum
   [blank line]
   [all other imports]
   [blank line]
   [static imports]
   ```

## Build Configuration

### Configure Maven
1. **Window > Preferences**
2. **Maven > User Settings**
3. **Verify** Maven installation path
4. **Apply and Close**

### JDK Configuration
1. **Window > Preferences**
2. **Java > Installed JREs**
3. **Add** JDK 11 (minimum)
4. **Add** JDK 23+ (for Panama module)
5. **Set JDK 11** as default

## Project Structure

After successful import, you should see these modules:

```
wamr4j/
├── wamr4j                # Core API interfaces
├── wamr4j-jni           # JNI implementation  
├── wamr4j-panama        # Panama implementation
├── wamr4j-native        # Native Rust library
├── wamr4j-benchmarks    # Performance benchmarks
└── wamr4j-tests         # Integration tests
```

## Common Tasks

### Build Project
- **Right-click project root** → **Run As** → **Maven build...**
- **Goals**: `clean compile`

### Run Tests  
- **Right-click project root** → **Run As** → **Maven build...**
- **Goals**: `clean test`

### Static Analysis
- **Right-click project root** → **Run As** → **Maven build...**
- **Goals**: `checkstyle:check spotbugs:check spotless:check`

## Troubleshooting

### Common Issues

**Maven dependencies not resolving:**
1. **Right-click project** → **Maven** → **Reload Projects**
2. **Project** → **Clean...** → **Clean all projects**

**Code style warnings:**
1. **Verify formatter** is set to Google Java Style
2. **Run Spotless**: `mvn spotless:apply`

**JDK version conflicts:**
1. **Verify project-specific JDK** settings
2. **Properties** → **Java Build Path** → **Libraries**
3. **Remove and re-add** JRE System Library

### Need Help?
See [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) for additional solutions.