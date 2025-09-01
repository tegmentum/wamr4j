#!/bin/bash
set -euo pipefail

# Cross-compilation setup script for CI environments
# Usage: ./setup-cross-compilation.sh <target-triple>

TARGET=${1:-""}

if [ -z "$TARGET" ]; then
    echo "Usage: $0 <target-triple>"
    echo "Example: $0 aarch64-unknown-linux-gnu"
    exit 1
fi

echo "Setting up cross-compilation for target: $TARGET"

case "$TARGET" in
    aarch64-unknown-linux-gnu)
        echo "Setting up ARM64 Linux cross-compilation..."
        sudo apt-get update
        sudo apt-get install -y gcc-aarch64-linux-gnu
        
        # Set environment variables for Rust/Cargo
        echo "CC_aarch64_unknown_linux_gnu=aarch64-linux-gnu-gcc" >> $GITHUB_ENV
        echo "CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc" >> $GITHUB_ENV
        
        # Set environment variables for CMake (WAMR build)
        echo "CMAKE_C_COMPILER_aarch64_unknown_linux_gnu=aarch64-linux-gnu-gcc" >> $GITHUB_ENV
        echo "CMAKE_CXX_COMPILER_aarch64_unknown_linux_gnu=aarch64-linux-gnu-g++" >> $GITHUB_ENV
        ;;
        
    armv7-unknown-linux-gnueabihf)
        echo "Setting up ARM32 Linux cross-compilation..."
        sudo apt-get update
        sudo apt-get install -y gcc-arm-linux-gnueabihf
        
        echo "CC_armv7_unknown_linux_gnueabihf=arm-linux-gnueabihf-gcc" >> $GITHUB_ENV
        echo "CARGO_TARGET_ARMV7_UNKNOWN_LINUX_GNUEABIHF_LINKER=arm-linux-gnueabihf-gcc" >> $GITHUB_ENV
        ;;
        
    x86_64-pc-windows-msvc|aarch64-pc-windows-msvc)
        echo "Windows targets are natively supported on Windows runners"
        ;;
        
    x86_64-apple-darwin|aarch64-apple-darwin)
        echo "macOS targets are natively supported on macOS runners"
        ;;
        
    *)
        echo "Unknown or unsupported target: $TARGET"
        echo "Supported targets:"
        echo "  - x86_64-unknown-linux-gnu (native)"
        echo "  - aarch64-unknown-linux-gnu"
        echo "  - armv7-unknown-linux-gnueabihf"
        echo "  - x86_64-pc-windows-msvc"
        echo "  - aarch64-pc-windows-msvc"
        echo "  - x86_64-apple-darwin"
        echo "  - aarch64-apple-darwin"
        exit 1
        ;;
esac

echo "Cross-compilation setup complete for $TARGET"