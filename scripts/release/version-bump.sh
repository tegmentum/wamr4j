#!/bin/bash

# Version Management Automation Script
# Automates version updates across all Maven modules

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Usage function
usage() {
    cat << EOF
Usage: $0 <new-version> [options]

Updates the project version across all Maven modules.

Arguments:
  new-version         New version to set (e.g., 1.2.0, 2.0.0-SNAPSHOT)

Options:
  --dry-run          Show what would be changed without making changes
  --skip-validation  Skip version format validation
  --commit           Create a git commit with the version changes
  --help, -h         Show this help message

Examples:
  $0 1.2.0                     # Set version to 1.2.0
  $0 2.0.0-SNAPSHOT --commit   # Set to snapshot and commit
  $0 1.1.0 --dry-run           # Preview changes without applying

EOF
}

# Validate version format
validate_version() {
    local version="$1"
    if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9\-\.]+)?$ ]]; then
        log_error "Invalid version format: $version"
        log_error "Expected format: X.Y.Z or X.Y.Z-SUFFIX (e.g., 1.2.0, 1.2.0-SNAPSHOT)"
        exit 1
    fi
}

# Get current project version
get_current_version() {
    cd "$PROJECT_ROOT"
    ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
}

# Update project version
update_version() {
    local new_version="$1"
    local dry_run="$2"
    
    cd "$PROJECT_ROOT"
    
    if [[ "$dry_run" == "true" ]]; then
        log_info "DRY RUN: Would update version to $new_version"
        ./mvnw versions:set -DnewVersion="$new_version" -DprocessAllModules=true -DgenerateBackupPoms=false --dry-run
        return 0
    fi
    
    log_info "Updating project version to $new_version..."
    
    # Update version in all modules
    ./mvnw versions:set -DnewVersion="$new_version" -DprocessAllModules=true -DgenerateBackupPoms=false -B
    
    # Commit the changes
    ./mvnw versions:commit -B
    
    # Verify the change
    local updated_version
    updated_version=$(get_current_version)
    if [[ "$updated_version" == "$new_version" ]]; then
        log_info "Successfully updated version to $new_version"
    else
        log_error "Version update failed. Expected $new_version, got $updated_version"
        exit 1
    fi
}

# Create git commit
create_git_commit() {
    local new_version="$1"
    local current_version="$2"
    
    cd "$PROJECT_ROOT"
    
    # Check if there are changes to commit
    if ! git diff --quiet; then
        log_info "Creating git commit for version update..."
        git add pom.xml */pom.xml
        git commit -m "Issue #20 Stream C: bump version from $current_version to $new_version

- Update project version across all Maven modules
- Automated version management for release process"
        log_info "Created git commit for version update"
    else
        log_warn "No changes detected, skipping git commit"
    fi
}

# Main function
main() {
    local new_version=""
    local dry_run="false"
    local skip_validation="false"
    local create_commit="false"
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                dry_run="true"
                shift
                ;;
            --skip-validation)
                skip_validation="true"
                shift
                ;;
            --commit)
                create_commit="true"
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                if [[ -z "$new_version" ]]; then
                    new_version="$1"
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
    if [[ -z "$new_version" ]]; then
        log_error "Missing required argument: new-version"
        usage
        exit 1
    fi
    
    # Validate version format
    if [[ "$skip_validation" != "true" ]]; then
        validate_version "$new_version"
    fi
    
    # Get current version
    local current_version
    current_version=$(get_current_version)
    log_info "Current version: $current_version"
    log_info "Target version: $new_version"
    
    # Check if version is different
    if [[ "$current_version" == "$new_version" ]]; then
        log_warn "New version is the same as current version ($current_version)"
        exit 0
    fi
    
    # Update version
    update_version "$new_version" "$dry_run"
    
    # Create git commit if requested and not dry run
    if [[ "$create_commit" == "true" && "$dry_run" != "true" ]]; then
        create_git_commit "$new_version" "$current_version"
    fi
    
    log_info "Version management completed successfully"
}

# Run main function
main "$@"