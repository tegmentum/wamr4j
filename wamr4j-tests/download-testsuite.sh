#!/bin/bash
#
# Copyright (c) 2024 Tegmentum
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# Download and setup the official WebAssembly specification test suite
#
# This script downloads the official WebAssembly test suite from
# https://github.com/WebAssembly/testsuite and sets it up for use
# with the wamr4j comparison test framework.
#

set -euo pipefail

# Configuration
TESTSUITE_REPO="https://github.com/WebAssembly/testsuite.git"
TESTSUITE_DIR="src/test/resources/testsuite"
TESTSUITE_TAG="main"  # Can be changed to specific tag/branch

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if git is installed
if ! command -v git &> /dev/null; then
    log_error "git is not installed. Please install git and try again."
    exit 1
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

log_info "WebAssembly Test Suite Downloader"
log_info "=================================="
log_info ""
log_info "Target directory: $TESTSUITE_DIR"
log_info "Repository: $TESTSUITE_REPO"
log_info "Tag/Branch: $TESTSUITE_TAG"
log_info ""

# Check if testsuite already exists
if [ -d "$TESTSUITE_DIR" ]; then
    log_warn "Test suite directory already exists: $TESTSUITE_DIR"
    read -p "Do you want to update the existing test suite? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Updating existing test suite..."
        cd "$TESTSUITE_DIR"
        git fetch origin
        git checkout "$TESTSUITE_TAG"
        git pull origin "$TESTSUITE_TAG"
        cd "$SCRIPT_DIR"
        log_info "Test suite updated successfully!"
    else
        log_info "Skipping update."
    fi
else
    log_info "Cloning WebAssembly test suite..."

    # Create parent directories if they don't exist
    mkdir -p "$(dirname "$TESTSUITE_DIR")"

    # Clone the repository
    git clone --depth 1 --branch "$TESTSUITE_TAG" "$TESTSUITE_REPO" "$TESTSUITE_DIR"

    if [ $? -eq 0 ]; then
        log_info "Test suite cloned successfully!"
    else
        log_error "Failed to clone test suite."
        exit 1
    fi
fi

log_info ""
log_info "Test suite structure:"
log_info "===================="

# Display directory structure
if command -v tree &> /dev/null; then
    tree -L 2 -d "$TESTSUITE_DIR"
else
    find "$TESTSUITE_DIR" -maxdepth 2 -type d
fi

log_info ""
log_info "Test files summary:"
log_info "==================="
log_info "WAST files: $(find "$TESTSUITE_DIR" -name "*.wast" | wc -l)"
log_info "WASM files: $(find "$TESTSUITE_DIR" -name "*.wasm" | wc -l)"
log_info "JSON files: $(find "$TESTSUITE_DIR" -name "*.json" | wc -l)"

log_info ""
log_info "Setup complete!"
log_info ""
log_info "Next steps:"
log_info "1. Convert WAST files to JSON format (if needed)"
log_info "2. Run comparison tests: ./mvnw test -pl wamr4j-tests -Pspec-tests"
log_info "3. View test results in target/surefire-reports/"
