#!/bin/bash
set -euo pipefail

# Native library validation script
# Usage: ./validate-native-library.sh <platform> <architecture> <library-path>

PLATFORM=${1:-""}
ARCHITECTURE=${2:-""}
LIBRARY_PATH=${3:-""}

if [ -z "$PLATFORM" ] || [ -z "$ARCHITECTURE" ] || [ -z "$LIBRARY_PATH" ]; then
    echo "Usage: $0 <platform> <architecture> <library-path>"
    echo "Example: $0 linux x86_64 /path/to/libwamr4j_native.so"
    exit 1
fi

echo "Validating native library: $LIBRARY_PATH"
echo "Platform: $PLATFORM, Architecture: $ARCHITECTURE"

# Check if file exists
if [ ! -f "$LIBRARY_PATH" ]; then
    echo "ERROR: Library file not found: $LIBRARY_PATH"
    exit 1
fi

# Platform-specific validation
case "$PLATFORM" in
    linux)
        # Validate Linux shared library
        file "$LIBRARY_PATH"
        
        # Check if it's a valid ELF file
        if ! file "$LIBRARY_PATH" | grep -q "ELF"; then
            echo "ERROR: Not a valid ELF file"
            exit 1
        fi
        
        # Check architecture
        case "$ARCHITECTURE" in
            x86_64)
                if ! file "$LIBRARY_PATH" | grep -q "x86-64"; then
                    echo "ERROR: Library is not x86_64"
                    exit 1
                fi
                ;;
            aarch64)
                if ! file "$LIBRARY_PATH" | grep -q "aarch64"; then
                    echo "ERROR: Library is not aarch64"
                    exit 1
                fi
                ;;
            *)
                echo "WARNING: Unknown architecture: $ARCHITECTURE"
                ;;
        esac
        
        # Check for required symbols (if objdump is available)
        if command -v objdump &> /dev/null; then
            echo "Checking exported symbols..."
            if ! objdump -T "$LIBRARY_PATH" | grep -q "Java_"; then
                echo "WARNING: No JNI symbols found"
            fi
        fi
        
        # Check dependencies
        if command -v ldd &> /dev/null; then
            echo "Library dependencies:"
            ldd "$LIBRARY_PATH" || true
        fi
        ;;
        
    windows)
        # Validate Windows DLL
        file "$LIBRARY_PATH" || true
        
        # Check if it's a valid PE file
        if ! file "$LIBRARY_PATH" | grep -q "PE32"; then
            echo "ERROR: Not a valid PE32 file"
            exit 1
        fi
        
        # Check architecture
        case "$ARCHITECTURE" in
            x86_64)
                if ! file "$LIBRARY_PATH" | grep -q "x86-64"; then
                    echo "ERROR: DLL is not x86_64"
                    exit 1
                fi
                ;;
            aarch64)
                if ! file "$LIBRARY_PATH" | grep -q "Aarch64"; then
                    echo "ERROR: DLL is not ARM64"
                    exit 1
                fi
                ;;
            *)
                echo "WARNING: Unknown architecture: $ARCHITECTURE"
                ;;
        esac
        ;;
        
    macos|darwin)
        # Validate macOS dylib
        file "$LIBRARY_PATH"
        
        # Check if it's a valid Mach-O file
        if ! file "$LIBRARY_PATH" | grep -q "Mach-O"; then
            echo "ERROR: Not a valid Mach-O file"
            exit 1
        fi
        
        # Check architecture
        case "$ARCHITECTURE" in
            x86_64)
                if ! file "$LIBRARY_PATH" | grep -q "x86_64"; then
                    echo "ERROR: Library is not x86_64"
                    exit 1
                fi
                ;;
            aarch64)
                if ! file "$LIBRARY_PATH" | grep -q "arm64"; then
                    echo "ERROR: Library is not arm64"
                    exit 1
                fi
                ;;
            *)
                echo "WARNING: Unknown architecture: $ARCHITECTURE"
                ;;
        esac
        
        # Check dependencies
        if command -v otool &> /dev/null; then
            echo "Library dependencies:"
            otool -L "$LIBRARY_PATH" || true
        fi
        ;;
        
    *)
        echo "ERROR: Unknown platform: $PLATFORM"
        exit 1
        ;;
esac

# Get file size
FILE_SIZE=$(stat -c%s "$LIBRARY_PATH" 2>/dev/null || stat -f%z "$LIBRARY_PATH" 2>/dev/null || echo "unknown")
echo "Library file size: $FILE_SIZE bytes"

# Check if file is executable/readable
if [ ! -r "$LIBRARY_PATH" ]; then
    echo "ERROR: Library file is not readable"
    exit 1
fi

echo "✅ Native library validation passed"
echo "Library: $LIBRARY_PATH"
echo "Platform: $PLATFORM"
echo "Architecture: $ARCHITECTURE"
echo "Size: $FILE_SIZE bytes"