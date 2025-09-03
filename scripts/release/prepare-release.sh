#!/bin/bash

# Release Preparation Automation Script
# Prepares a complete release with validation, version updates, and tagging

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Usage function
usage() {
    cat << EOF
Usage: $0 <release-version> [next-dev-version] [options]

Prepares a complete release with validation, version updates, and Git tagging.

Arguments:
  release-version     Version to release (e.g., 1.2.0)
  next-dev-version    Next development version (e.g., 1.3.0-SNAPSHOT)
                      If not provided, will auto-increment patch version

Options:
  --dry-run          Show what would be done without making changes
  --skip-tests       Skip running tests during preparation
  --skip-validation  Skip pre-release validation checks
  --push             Push changes and tags to remote repository
  --help, -h         Show this help message

Examples:
  $0 1.2.0                           # Prepare 1.2.0 release, next dev: 1.2.1-SNAPSHOT
  $0 1.2.0 1.3.0-SNAPSHOT           # Prepare 1.2.0 release, next dev: 1.3.0-SNAPSHOT
  $0 1.2.0 --push                   # Prepare release and push to remote
  $0 1.2.0 --dry-run                # Preview what would be done

EOF
}

# Check if working directory is clean
check_working_directory() {
    log_step "Checking working directory status..."
    
    cd "$PROJECT_ROOT"
    
    if ! git diff --quiet || ! git diff --cached --quiet; then
        log_error "Working directory is not clean. Please commit or stash changes first."
        git status --porcelain
        exit 1
    fi
    
    log_info "Working directory is clean"
}

# Get current branch
get_current_branch() {
    cd "$PROJECT_ROOT"
    git rev-parse --abbrev-ref HEAD
}

# Validate current branch
validate_branch() {
    local current_branch
    current_branch=$(get_current_branch)
    
    if [[ "$current_branch" != "main" && "$current_branch" != "epic/core-native-wamr-integration" ]]; then
        log_warn "Current branch is '$current_branch', not main or epic branch"
        log_warn "Releases are typically made from main or epic branches"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Release preparation cancelled"
            exit 0
        fi
    fi
}

# Run pre-release validation
run_validation() {
    local skip_tests="$1"
    
    log_step "Running pre-release validation..."
    
    cd "$PROJECT_ROOT"
    
    # Check if Maven wrapper exists
    if [[ ! -f "./mvnw" ]]; then
        log_error "Maven wrapper not found"
        exit 1
    fi
    
    # Run static analysis
    log_info "Running static analysis..."
    ./mvnw clean compile checkstyle:check spotbugs:check -B -q
    
    # Run tests unless skipped
    if [[ "$skip_tests" != "true" ]]; then
        log_info "Running tests..."
        ./mvnw test -B -q
    else
        log_warn "Skipping tests as requested"
    fi
    
    # Verify native library builds
    log_info "Verifying native library build..."
    ./mvnw package -P native-all-platforms -DskipTests -B -q
    
    log_info "Pre-release validation completed successfully"
}

# Auto-increment version
auto_increment_version() {
    local version="$1"
    
    # Extract version parts
    if [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        local major="${BASH_REMATCH[1]}"
        local minor="${BASH_REMATCH[2]}"
        local patch="${BASH_REMATCH[3]}"
        
        # Increment patch version
        ((patch++))
        echo "${major}.${minor}.${patch}-SNAPSHOT"
    else
        log_error "Cannot auto-increment version: $version"
        exit 1
    fi
}

# Create release tag
create_release_tag() {
    local version="$1"
    local dry_run="$2"
    
    log_step "Creating release tag v$version..."
    
    cd "$PROJECT_ROOT"
    
    if [[ "$dry_run" == "true" ]]; then
        log_info "DRY RUN: Would create tag v$version"
        return 0
    fi
    
    # Create annotated tag
    git tag -a "v$version" -m "Release $version

Automated release preparation for WAMR4J version $version.
Contains native libraries for all supported platforms and comprehensive test suite.

Release prepared by: $(git config user.name)
Release date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
    
    log_info "Created release tag v$version"
}

# Update changelog
update_changelog() {
    local version="$1"
    local dry_run="$2"
    
    log_step "Updating CHANGELOG.md..."
    
    cd "$PROJECT_ROOT"
    
    if [[ ! -f "CHANGELOG.md" ]]; then
        if [[ "$dry_run" == "true" ]]; then
            log_info "DRY RUN: Would create CHANGELOG.md"
            return 0
        fi
        
        log_info "Creating initial CHANGELOG.md"
        cat > CHANGELOG.md << EOF
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [$version] - $(date +%Y-%m-%d)

### Added
- Initial release of WAMR4J unified Java bindings
- Cross-platform native libraries for Linux, Windows, and macOS
- Support for both x86_64 and ARM64 architectures
- JNI and Panama Foreign Function API implementations
- Comprehensive test suite and benchmarks
- Maven Central publishing automation

### Security
- GPG signing of all release artifacts
- Artifact integrity validation in CI/CD pipeline

EOF
    else
        if [[ "$dry_run" == "true" ]]; then
            log_info "DRY RUN: Would update CHANGELOG.md with version $version"
            return 0
        fi
        
        # Check if version already exists in changelog
        if grep -q "## \[$version\]" CHANGELOG.md; then
            log_info "Version $version already exists in CHANGELOG.md"
        else
            log_warn "CHANGELOG.md exists but version $version not found"
            log_warn "Please manually update CHANGELOG.md with release notes for $version"
        fi
    fi
}

# Push changes
push_changes() {
    local dry_run="$1"
    local push_remote="$2"
    
    if [[ "$push_remote" != "true" ]]; then
        return 0
    fi
    
    log_step "Pushing changes to remote repository..."
    
    cd "$PROJECT_ROOT"
    
    if [[ "$dry_run" == "true" ]]; then
        log_info "DRY RUN: Would push changes and tags to origin"
        return 0
    fi
    
    # Push commits and tags
    git push origin
    git push origin --tags
    
    log_info "Changes and tags pushed to remote repository"
}

# Main function
main() {
    local release_version=""
    local next_dev_version=""
    local dry_run="false"
    local skip_tests="false"
    local skip_validation="false"
    local push_remote="false"
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                dry_run="true"
                shift
                ;;
            --skip-tests)
                skip_tests="true"
                shift
                ;;
            --skip-validation)
                skip_validation="true"
                shift
                ;;
            --push)
                push_remote="true"
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                if [[ -z "$release_version" ]]; then
                    release_version="$1"
                elif [[ -z "$next_dev_version" ]]; then
                    next_dev_version="$1"
                else
                    log_error "Unknown option: $1"
                    usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate required arguments
    if [[ -z "$release_version" ]]; then
        log_error "Missing required argument: release-version"
        usage
        exit 1
    fi
    
    # Auto-generate next development version if not provided
    if [[ -z "$next_dev_version" ]]; then
        next_dev_version=$(auto_increment_version "$release_version")
        log_info "Auto-generated next development version: $next_dev_version"
    fi
    
    log_info "Release preparation starting..."
    log_info "Release version: $release_version"
    log_info "Next development version: $next_dev_version"
    log_info "Dry run: $dry_run"
    
    # Pre-flight checks
    if [[ "$skip_validation" != "true" ]]; then
        check_working_directory
        validate_branch
        run_validation "$skip_tests"
    else
        log_warn "Skipping validation as requested"
    fi
    
    # Get current version for reference
    cd "$PROJECT_ROOT"
    local current_version
    current_version=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
    log_info "Current version: $current_version"
    
    # Update to release version
    log_step "Updating to release version..."
    "$SCRIPT_DIR/version-bump.sh" "$release_version" $([ "$dry_run" == "true" ] && echo "--dry-run")
    
    # Update changelog
    update_changelog "$release_version" "$dry_run"
    
    # Create release tag
    create_release_tag "$release_version" "$dry_run"
    
    # Update to next development version
    log_step "Updating to next development version..."
    "$SCRIPT_DIR/version-bump.sh" "$next_dev_version" $([ "$dry_run" == "true" ] && echo "--dry-run")
    
    # Push changes if requested
    push_changes "$dry_run" "$push_remote"
    
    log_info "Release preparation completed successfully!"
    log_info ""
    log_info "Next steps:"
    log_info "1. Review the changes and test locally if needed"
    log_info "2. Push the release tag: git push origin v$release_version"
    log_info "3. Create a GitHub release from the tag"
    log_info "4. The release workflow will automatically publish to Maven Central"
}

# Run main function
main "$@"