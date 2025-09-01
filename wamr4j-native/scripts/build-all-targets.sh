#!/usr/bin/env bash

# Cross-compilation build script for wamr4j-native
# Builds native libraries for all supported target platforms

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Target platform configurations
declare -A TARGETS
TARGETS[x86_64-unknown-linux-gnu]="Linux x86_64"
TARGETS[aarch64-unknown-linux-gnu]="Linux ARM64"
TARGETS[x86_64-pc-windows-msvc]="Windows x86_64"
TARGETS[aarch64-pc-windows-msvc]="Windows ARM64"
TARGETS[x86_64-apple-darwin]="macOS x86_64"
TARGETS[aarch64-apple-darwin]="macOS ARM64"

# Priority targets for Day 1 (most common development platforms)
PRIORITY_TARGETS=(
    "aarch64-apple-darwin"
    "x86_64-unknown-linux-gnu"
)

# All targets
ALL_TARGETS=(
    "${!TARGETS[@]}"
)

# Function to build for a specific target
build_target() {
    local target=$1
    local description=${TARGETS[$target]}
    
    log_info "Building for $description ($target)"
    
    # Choose build tool based on target
    local build_cmd
    if [[ "$target" == *"linux"* ]] || [[ "$target" == *"windows"* ]]; then
        # Use cargo-cross for non-native targets
        if command -v cross >/dev/null 2>&1; then
            build_cmd="cross"
        else
            log_warning "cargo-cross not available, falling back to cargo (may fail)"
            build_cmd="cargo"
        fi
    else
        # Use regular cargo for native targets
        build_cmd="cargo"
    fi
    
    # Build in both debug and release modes
    for profile in debug release; do
        local profile_flag=""
        if [[ "$profile" == "release" ]]; then
            profile_flag="--release"
        fi
        
        log_info "  Building $profile profile..."
        
        if $build_cmd build --target "$target" $profile_flag; then
            log_success "  $profile build completed"
            
            # List the generated artifacts
            local target_dir="$PROJECT_DIR/target/$target/$profile"
            if [[ -d "$target_dir" ]]; then
                log_info "  Generated artifacts:"
                find "$target_dir" -name "*wamr4j_native*" -type f | head -5 | while read -r file; do
                    local size=$(du -h "$file" | cut -f1)
                    echo "    $(basename "$file") ($size)"
                done
            fi
        else
            log_error "  $profile build failed for $target"
            return 1
        fi
    done
    
    return 0
}

# Function to validate build results
validate_build() {
    local target=$1
    local success=true
    
    for profile in debug release; do
        local target_dir="$PROJECT_DIR/target/$target/$profile"
        local lib_file=""
        
        # Find the library file based on platform
        if [[ "$target" == *"windows"* ]]; then
            lib_file=$(find "$target_dir" -name "wamr4j_native.dll" 2>/dev/null | head -1)
        elif [[ "$target" == *"apple"* ]]; then
            lib_file=$(find "$target_dir" -name "libwamr4j_native.dylib" 2>/dev/null | head -1)
        else
            lib_file=$(find "$target_dir" -name "libwamr4j_native.so" 2>/dev/null | head -1)
        fi
        
        if [[ -f "$lib_file" ]]; then
            log_success "  Found $profile library: $(basename "$lib_file")"
        else
            log_error "  Missing $profile library for $target"
            success=false
        fi
    done
    
    return $([ "$success" = true ] && echo 0 || echo 1)
}

# Main execution
main() {
    log_info "Starting cross-platform build process"
    log_info "Project directory: $PROJECT_DIR"
    
    cd "$PROJECT_DIR"
    
    # Parse command line arguments
    local build_mode="priority"
    local targets_to_build=()
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --all)
                build_mode="all"
                targets_to_build=("${ALL_TARGETS[@]}")
                shift
                ;;
            --priority)
                build_mode="priority"
                targets_to_build=("${PRIORITY_TARGETS[@]}")
                shift
                ;;
            --target=*)
                build_mode="specific"
                local target="${1#*=}"
                if [[ -n "${TARGETS[$target]:-}" ]]; then
                    targets_to_build+=("$target")
                else
                    log_error "Unknown target: $target"
                    exit 1
                fi
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --all             Build for all supported targets"
                echo "  --priority        Build for priority targets only (default)"
                echo "  --target=TARGET   Build for specific target only"
                echo "  --help, -h        Show this help message"
                echo ""
                echo "Supported targets:"
                for target in "${!TARGETS[@]}"; do
                    echo "  $target - ${TARGETS[$target]}"
                done
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Default to priority targets if no targets specified
    if [[ ${#targets_to_build[@]} -eq 0 ]]; then
        targets_to_build=("${PRIORITY_TARGETS[@]}")
    fi
    
    log_info "Build mode: $build_mode"
    log_info "Targets to build: ${targets_to_build[*]}"
    
    # Check prerequisites
    if ! command -v rustc >/dev/null 2>&1; then
        log_error "Rust compiler not found. Please install Rust."
        exit 1
    fi
    
    if ! command -v cargo >/dev/null 2>&1; then
        log_error "Cargo not found. Please install Rust."
        exit 1
    fi
    
    # Install required targets
    log_info "Installing required Rust targets..."
    for target in "${targets_to_build[@]}"; do
        if rustup target add "$target" 2>/dev/null; then
            log_success "Target $target installed/updated"
        else
            log_warning "Failed to install target $target (may already be installed)"
        fi
    done
    
    # Clean previous builds
    log_info "Cleaning previous builds..."
    cargo clean
    
    # Build all targets
    local build_results=()
    local failed_builds=()
    
    for target in "${targets_to_build[@]}"; do
        echo ""
        log_info "========================================="
        log_info "Building ${TARGETS[$target]} ($target)"
        log_info "========================================="
        
        if build_target "$target"; then
            if validate_build "$target"; then
                build_results+=("$target:SUCCESS")
                log_success "Build completed successfully for $target"
            else
                build_results+=("$target:VALIDATION_FAILED")
                failed_builds+=("$target")
                log_error "Build validation failed for $target"
            fi
        else
            build_results+=("$target:FAILED")
            failed_builds+=("$target")
            log_error "Build failed for $target"
        fi
    done
    
    # Summary
    echo ""
    log_info "========================================="
    log_info "BUILD SUMMARY"
    log_info "========================================="
    
    for result in "${build_results[@]}"; do
        local target="${result%:*}"
        local status="${result#*:}"
        local description="${TARGETS[$target]}"
        
        case "$status" in
            SUCCESS)
                log_success "$description ($target): ✓"
                ;;
            VALIDATION_FAILED)
                log_warning "$description ($target): ⚠ Built but validation failed"
                ;;
            FAILED)
                log_error "$description ($target): ✗ Failed"
                ;;
        esac
    done
    
    # Exit with error if any builds failed
    if [[ ${#failed_builds[@]} -gt 0 ]]; then
        echo ""
        log_error "${#failed_builds[@]} target(s) failed to build successfully"
        exit 1
    else
        echo ""
        log_success "All targets built successfully!"
        exit 0
    fi
}

# Execute main function with all arguments
main "$@"