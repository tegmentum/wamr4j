#!/bin/bash

# Hotfix Creation Automation Script
# Creates a hotfix branch from a specific version for emergency fixes

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
Usage: $0 <base-version> <hotfix-version> <description> [options]

Creates a hotfix branch from a specific release version for emergency fixes.

Arguments:
  base-version        Base version to create hotfix from (e.g., 1.2.0)
  hotfix-version      New hotfix version (e.g., 1.2.1)  
  description         Brief description of the hotfix (used in branch name)

Options:
  --emergency         Skip non-essential validation for critical issues
  --security          Mark as security-related hotfix
  --help, -h          Show this help message

Examples:
  $0 1.2.0 1.2.1 "critical-memory-leak"
  $0 1.2.0 1.2.1 "security-vulnerability" --security
  $0 1.2.0 1.2.1 "urgent-compatibility-fix" --emergency

EOF
}

# Validate version format
validate_version() {
    local version="$1"
    if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9\-\.]+)?$ ]]; then
        log_error "Invalid version format: $version"
        log_error "Expected format: X.Y.Z or X.Y.Z-SUFFIX (e.g., 1.2.0, 1.2.1-hotfix)"
        exit 1
    fi
}

# Check if tag exists
check_tag_exists() {
    local version="$1"
    local tag="v$version"
    
    cd "$PROJECT_ROOT"
    
    if ! git rev-parse --verify "refs/tags/$tag" >/dev/null 2>&1; then
        log_error "Base version tag $tag does not exist"
        log_error "Available tags:"
        git tag -l "v*" | sort -V | tail -10
        exit 1
    fi
}

# Check if hotfix version already exists
check_hotfix_version_available() {
    local version="$1"
    local tag="v$version"
    
    cd "$PROJECT_ROOT"
    
    if git rev-parse --verify "refs/tags/$tag" >/dev/null 2>&1; then
        log_error "Hotfix version $version already exists (tag $tag found)"
        exit 1
    fi
}

# Sanitize branch name description
sanitize_description() {
    local description="$1"
    # Convert to lowercase, replace spaces with hyphens, remove special characters
    echo "$description" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | sed 's/[^a-z0-9\-]//g'
}

# Create hotfix branch
create_hotfix_branch() {
    local base_version="$1"
    local hotfix_version="$2"
    local description="$3"
    local is_security="$4"
    
    cd "$PROJECT_ROOT"
    
    local base_tag="v$base_version"
    local sanitized_desc
    sanitized_desc=$(sanitize_description "$description")
    local branch_name="hotfix/v$hotfix_version-$sanitized_desc"
    
    log_step "Creating hotfix branch from $base_tag..."
    
    # Checkout base version
    git checkout "$base_tag"
    
    # Create hotfix branch
    git checkout -b "$branch_name"
    
    log_info "Created hotfix branch: $branch_name"
    log_info "Base version: $base_version ($base_tag)"
    log_info "Target hotfix version: $hotfix_version"
    
    # Create initial commit message template
    local commit_template_file="$PROJECT_ROOT/.git/COMMIT_EDITMSG_HOTFIX"
    cat > "$commit_template_file" << EOF
hotfix: $description

Brief description of the fix implemented.

$(if [[ "$is_security" == "true" ]]; then
    echo "SECURITY UPDATE - Details:"
    echo "- CVE/Security ID: [if applicable]"
    echo "- Affected versions: [version range]"
    echo "- Severity: [Critical/High/Medium]"
    echo ""
fi)Technical changes:
- [List specific changes made]
- [Include any API changes]
- [Note any breaking changes]

Testing:
- [Describe testing performed]
- [Include any limitations due to emergency nature]

$(if [[ "$is_security" == "true" ]]; then
    echo "Security-Level: [Critical/High/Medium]"
fi)Affects: [version range or specific versions]
Fixes: [GitHub issue number or external reference]
EOF
    
    echo
    log_info "Hotfix branch created successfully!"
    echo
    log_warn "Next steps:"
    echo "1. Apply your hotfix changes to the code"
    echo "2. Test the changes (use emergency validation for critical issues)"
    echo "3. Commit your changes:"
    echo "   git add ."
    echo "   git commit -F $commit_template_file"
    echo "   # Edit the commit message as needed"
    echo "4. Update version and create tag:"
    echo "   ./scripts/release/version-bump.sh $hotfix_version"
    echo "   git tag -a v$hotfix_version -m 'Hotfix $hotfix_version: $description'"
    echo "5. Push the hotfix:"
    echo "   git push origin $branch_name"
    echo "   git push origin v$hotfix_version"
    echo
    
    if [[ "$is_security" == "true" ]]; then
        log_warn "SECURITY HOTFIX REMINDER:"
        echo "- Create GitHub Security Advisory if needed"
        echo "- Coordinate with security team"
        echo "- Plan coordinated disclosure timeline"
        echo "- Prepare security-focused release notes"
    fi
}

# Run emergency validation
run_emergency_validation() {
    local emergency="$1"
    
    if [[ "$emergency" != "true" ]]; then
        log_step "Running standard hotfix validation..."
        cd "$PROJECT_ROOT"
        
        # Basic compilation check
        ./mvnw compile -B -q
        
        # Critical tests only
        ./mvnw test -Dtest="*Integration*,*Native*,*Security*" -B -q || {
            log_warn "Some tests failed - review before proceeding with hotfix"
        }
        
        log_info "Standard validation completed"
    else
        log_step "Skipping non-essential validation (emergency mode)"
        cd "$PROJECT_ROOT"
        
        # Minimal validation for emergency
        ./mvnw compile -B -q
        log_info "Emergency validation completed"
    fi
}

# Main function
main() {
    local base_version=""
    local hotfix_version=""
    local description=""
    local emergency="false"
    local is_security="false"
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --emergency)
                emergency="true"
                shift
                ;;
            --security)
                is_security="true"
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                if [[ -z "$base_version" ]]; then
                    base_version="$1"
                elif [[ -z "$hotfix_version" ]]; then
                    hotfix_version="$1"
                elif [[ -z "$description" ]]; then
                    description="$1"
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
    if [[ -z "$base_version" || -z "$hotfix_version" || -z "$description" ]]; then
        log_error "Missing required arguments"
        usage
        exit 1
    fi
    
    # Validate versions
    validate_version "$base_version"
    validate_version "$hotfix_version"
    
    # Check that hotfix version is greater than base version
    if [[ "$(printf '%s\n' "$base_version" "$hotfix_version" | sort -V | head -n1)" != "$base_version" ]]; then
        log_error "Hotfix version ($hotfix_version) must be greater than base version ($base_version)"
        exit 1
    fi
    
    log_info "Creating hotfix for critical issue..."
    log_info "Base version: $base_version"
    log_info "Hotfix version: $hotfix_version"
    log_info "Description: $description"
    log_info "Emergency mode: $emergency"
    log_info "Security hotfix: $is_security"
    
    # Validate preconditions
    check_tag_exists "$base_version"
    check_hotfix_version_available "$hotfix_version"
    
    # Run validation
    run_emergency_validation "$emergency"
    
    # Create hotfix branch
    create_hotfix_branch "$base_version" "$hotfix_version" "$description" "$is_security"
    
    log_info "Hotfix branch creation completed successfully!"
}

# Run main function
main "$@"