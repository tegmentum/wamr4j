# Static Analysis Setup

This document describes the static analysis configuration for the WAMR4J project.

## Overview

The project uses comprehensive static analysis tools to enforce the Google Java Style Guide and detect potential security vulnerabilities. All tools are integrated into the Maven build lifecycle and can be run individually or together.

## Tools Configured

### 1. Checkstyle - Code Style Enforcement

**Configuration**: `checkstyle.xml` (custom Google Java Style Guide)
**Suppressions**: `checkstyle-suppressions.xml`

```bash
# Check code style violations
./mvnw checkstyle:check

# Generate detailed report
./mvnw checkstyle:checkstyle
```

**Key Features**:
- Google Java Style Guide compliance (120 character line length)
- 4-space indentation with AOSP style
- Javadoc requirements for public APIs
- Import organization rules
- Custom rules for JNI/Panama methods

### 2. Spotless - Automatic Code Formatting  

**Configuration**: Integrated in parent POM
**License Header**: `spotless-license-header.txt`

```bash
# Check formatting compliance
./mvnw spotless:check

# Apply automatic formatting
./mvnw spotless:apply
```

**Key Features**:
- Google Java Format with AOSP style
- Automatic license header management
- Import organization and cleanup
- Trailing whitespace removal
- Consistent line endings

### 3. SpotBugs + FindSecBugs - Security Analysis

**Include Filter**: `spotbugs-include.xml`
**Exclude Filter**: `spotbugs-exclude.xml`

```bash
# Run security analysis
./mvnw spotbugs:check

# Generate detailed report
./mvnw spotbugs:spotbugs
```

**Key Features**:
- Core SpotBugs rules for code quality
- FindSecBugs plugin for security vulnerabilities  
- JNI-specific security patterns
- Custom exclusions for generated code

## Build Integration

### Quality Profile
Run all static analysis tools together:

```bash
# Full quality check
./mvnw verify -Pquality

# Quality check on specific module
./mvnw verify -Pquality -pl wamr4j
```

### Individual Tool Commands

```bash
# Checkstyle only
./mvnw checkstyle:check

# Spotless format check only  
./mvnw spotless:check

# SpotBugs analysis only
./mvnw spotbugs:check
```

### Development Workflow

1. **Before Committing**: Run `./mvnw spotless:apply` to fix formatting
2. **Quality Check**: Run `./mvnw verify -Pquality` to validate all rules
3. **CI Integration**: Quality profile runs automatically in CI pipeline

## IDE Integration

### IntelliJ IDEA
- Code style configuration: `.idea/codeStyles/Project.xml`
- Settings applied automatically when project is imported
- Reformat Code (Ctrl+Alt+L) follows Google Java Style Guide

### Eclipse  
- Formatter configuration: `.settings/org.eclipse.jdt.core.prefs`
- Import project settings for consistent formatting
- Code style preferences applied automatically

### EditorConfig
- Universal configuration: `.editorconfig`
- Supported by most modern IDEs
- Ensures consistent basic formatting

## Configuration Files

| File | Purpose |
|------|---------|
| `checkstyle.xml` | Custom Checkstyle rules based on Google Java Style Guide |
| `checkstyle-suppressions.xml` | Legitimate rule exceptions |
| `spotless-license-header.txt` | License header template |
| `spotbugs-include.xml` | Security rules to enforce |
| `spotbugs-exclude.xml` | Known false positives to ignore |
| `.editorconfig` | Basic IDE formatting rules |
| `.idea/codeStyles/Project.xml` | IntelliJ IDEA code style |
| `.settings/org.eclipse.jdt.core.prefs` | Eclipse formatter settings |

## Customization

### Adding Suppressions
To suppress a Checkstyle rule for specific cases, edit `checkstyle-suppressions.xml`:

```xml
<suppress checks="MagicNumber" files=".*Version.*\.java"/>
```

### Excluding SpotBugs Rules
To exclude false positives, edit `spotbugs-exclude.xml`:

```xml
<Match>
    <Class name="com.example.GeneratedClass"/>
    <Bug pattern="ALL"/>
</Match>
```

## Troubleshooting

### Common Issues

**Build fails with "You have X Checkstyle violations"**:
- Run `./mvnw checkstyle:check` to see specific violations
- Fix violations manually or add suppressions if legitimate

**Spotless formatting conflicts**:
- Run `./mvnw spotless:apply` to auto-fix formatting
- Commit the formatted changes

**SpotBugs false positives**:
- Add specific exclusions to `spotbugs-exclude.xml` 
- Document why the exclusion is justified

### IDE Plugin Recommendations

**IntelliJ IDEA**:
- Google Java Format plugin
- Checkstyle-IDEA plugin  
- SpotBugs plugin

**Eclipse**:
- Checkstyle plugin
- SpotBugs plugin
- EditorConfig plugin

**VS Code**:
- Checkstyle for Java extension
- EditorConfig extension
- Language Support for Java

## Quality Gates

The static analysis tools enforce these quality gates:

1. **Zero Checkstyle Violations**: Build fails on any style violations
2. **Consistent Formatting**: Spotless ensures all code follows Google Java Style
3. **Security Analysis**: SpotBugs prevents common security vulnerabilities
4. **Documentation**: Javadoc required for all public APIs
5. **Import Organization**: Consistent import ordering enforced

All quality gates must pass before code can be merged to main branches.