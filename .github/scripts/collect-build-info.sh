#!/bin/bash
set -euo pipefail

# Build information collection script
# Usage: ./collect-build-info.sh <output-file>

OUTPUT_FILE=${1:-"build-info.json"}

echo "Collecting build information..."

# Get current timestamp
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Get Git information
GIT_COMMIT=$(git rev-parse HEAD)
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
GIT_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "none")
GIT_DIRTY=$(git diff --quiet && echo "false" || echo "true")

# Get system information
OS_NAME=$(uname -s)
OS_VERSION=$(uname -r)
ARCH=$(uname -m)

# Get Java information
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
JAVA_HOME_VAL=${JAVA_HOME:-"not set"}

# Get Rust information
RUST_VERSION=$(rustc --version)
CARGO_VERSION=$(cargo --version)

# Get Maven information
MAVEN_VERSION=$(./mvnw --version | head -1)

# Get project version from POM
PROJECT_VERSION=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)

# Create JSON output
cat > "$OUTPUT_FILE" << EOF
{
  "build_info": {
    "timestamp": "$TIMESTAMP",
    "project_version": "$PROJECT_VERSION",
    "git": {
      "commit": "$GIT_COMMIT",
      "branch": "$GIT_BRANCH",
      "tag": "$GIT_TAG",
      "dirty": $GIT_DIRTY
    },
    "system": {
      "os": "$OS_NAME",
      "version": "$OS_VERSION",
      "architecture": "$ARCH"
    },
    "tools": {
      "java": {
        "version": "$JAVA_VERSION",
        "home": "$JAVA_HOME_VAL"
      },
      "rust": {
        "rustc": "$RUST_VERSION",
        "cargo": "$CARGO_VERSION"
      },
      "maven": "$MAVEN_VERSION"
    },
    "environment": {
      "ci": "${CI:-false}",
      "github_actions": "${GITHUB_ACTIONS:-false}",
      "runner_os": "${RUNNER_OS:-unknown}"
    }
  }
}
EOF

echo "Build information collected in: $OUTPUT_FILE"

# Also output key information to console
echo ""
echo "=== Build Information Summary ==="
echo "Project Version: $PROJECT_VERSION"
echo "Git Commit: $GIT_COMMIT"
echo "Git Branch: $GIT_BRANCH"
echo "System: $OS_NAME $OS_VERSION ($ARCH)"
echo "Java: $JAVA_VERSION"
echo "Rust: $RUST_VERSION"
echo "================================="