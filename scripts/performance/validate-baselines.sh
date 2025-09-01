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

set -euo pipefail

# Performance baseline validation script
# Validates that performance baselines meet requirements across platforms

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Performance targets (from requirements)
FUNCTION_CALL_OVERHEAD_NS=10
MODULE_LOADING_MS=100
STARTUP_TIME_MS=50

# Platform detection
detect_platform() {
    local os=""
    local arch=""
    
    case "$(uname -s)" in
        Linux*)  os="linux" ;;
        Darwin*) os="macos" ;;
        CYGWIN*|MINGW*|MSYS*) os="windows" ;;
        *) 
            echo "Unsupported OS: $(uname -s)" >&2
            exit 1
            ;;
    esac
    
    case "$(uname -m)" in
        x86_64|amd64) arch="x86_64" ;;
        arm64|aarch64) arch="aarch64" ;;
        *)
            echo "Unsupported architecture: $(uname -m)" >&2
            exit 1
            ;;
    esac
    
    echo "${os}-${arch}"
}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

# Run native benchmarks
run_native_benchmarks() {
    log_info "Running native Rust benchmarks..."
    
    cd "${PROJECT_ROOT}/wamr4j-native"
    
    # Ensure release build exists
    if [[ ! -f "target/release/deps" ]]; then
        log_info "Building release version for benchmarks..."
        cargo build --release
    fi
    
    # Run benchmarks with JSON output
    local platform
    platform=$(detect_platform)
    
    cargo bench -- --output-format json > "../native-bench-${platform}.json" 2>/dev/null || {
        log_error "Native benchmarks failed"
        return 1
    }
    
    log_success "Native benchmarks completed"
    return 0
}

# Run JMH benchmarks
run_jmh_benchmarks() {
    log_info "Running JMH Java benchmarks..."
    
    cd "${PROJECT_ROOT}"
    
    # Build benchmarks module
    ./mvnw clean compile -pl wamr4j-benchmarks -q || {
        log_error "Failed to build benchmark module"
        return 1
    }
    
    local platform
    platform=$(detect_platform)
    
    # Run JMH benchmarks with JSON output
    timeout 300 ./mvnw exec:java -pl wamr4j-benchmarks \
        -Dexec.mainClass="ai.tegmentum.wamr4j.benchmarks.RuntimeBenchmark" \
        -Dexec.args="-rf json -rff jmh-results-${platform}.json" -q || {
        log_error "JMH benchmarks failed or timed out"
        return 1
    }
    
    log_success "JMH benchmarks completed"
    return 0
}

# Parse native benchmark results
parse_native_results() {
    local platform
    platform=$(detect_platform)
    local file="${PROJECT_ROOT}/native-bench-${platform}.json"
    
    if [[ ! -f "$file" ]]; then
        log_error "Native benchmark results not found: $file"
        return 1
    fi
    
    log_info "Parsing native benchmark results..."
    
    # Extract function call overhead
    local call_overhead_ns
    call_overhead_ns=$(python3 -c "
import json
import sys

try:
    with open('${file}') as f:
        for line in f:
            line = line.strip()
            if line and line.startswith('{'):
                data = json.loads(line)
                if (data.get('reason') == 'benchmark-complete' and
                    'basic_ffi/test_function' in data.get('id', '')):
                    print(f\"{data['mean']['estimate']:.2f}\")
                    sys.exit(0)
    print('0')
except Exception as e:
    print('0')
    " 2>/dev/null || echo "0")
    
    if (( $(echo "$call_overhead_ns > $FUNCTION_CALL_OVERHEAD_NS" | bc -l) )); then
        log_error "Function call overhead: ${call_overhead_ns}ns > ${FUNCTION_CALL_OVERHEAD_NS}ns target"
        return 1
    else
        log_success "Function call overhead: ${call_overhead_ns}ns ≤ ${FUNCTION_CALL_OVERHEAD_NS}ns target"
    fi
    
    return 0
}

# Parse JMH results
parse_jmh_results() {
    local platform
    platform=$(detect_platform)
    local file="${PROJECT_ROOT}/jmh-results-${platform}.json"
    
    if [[ ! -f "$file" ]]; then
        log_error "JMH benchmark results not found: $file"
        return 1
    fi
    
    log_info "Parsing JMH benchmark results..."
    
    local violations=0
    
    # Check runtime creation time
    local startup_time_ms
    startup_time_ms=$(python3 -c "
import json
try:
    with open('${file}') as f:
        data = json.load(f)
        for benchmark in data:
            if 'RuntimeCreation' in benchmark.get('benchmark', ''):
                mean_us = benchmark.get('primaryMetric', {}).get('score', 0)
                print(f\"{mean_us / 1000.0:.2f}\")
                break
        else:
            print('0')
except:
    print('0')
" 2>/dev/null || echo "0")
    
    if (( $(echo "$startup_time_ms > $STARTUP_TIME_MS" | bc -l) )); then
        log_error "Startup time: ${startup_time_ms}ms > ${STARTUP_TIME_MS}ms target"
        violations=$((violations + 1))
    else
        log_success "Startup time: ${startup_time_ms}ms ≤ ${STARTUP_TIME_MS}ms target"
    fi
    
    # Check module compilation time
    local module_loading_ms
    module_loading_ms=$(python3 -c "
import json
try:
    with open('${file}') as f:
        data = json.load(f)
        for benchmark in data:
            if 'ModuleCompilation' in benchmark.get('benchmark', ''):
                mean_us = benchmark.get('primaryMetric', {}).get('score', 0)
                print(f\"{mean_us / 1000.0:.2f}\")
                break
        else:
            print('0')
except:
    print('0')
" 2>/dev/null || echo "0")
    
    if (( $(echo "$module_loading_ms > $MODULE_LOADING_MS" | bc -l) )); then
        log_error "Module loading: ${module_loading_ms}ms > ${MODULE_LOADING_MS}ms target"
        violations=$((violations + 1))
    else
        log_success "Module loading: ${module_loading_ms}ms ≤ ${MODULE_LOADING_MS}ms target"
    fi
    
    return $violations
}

# Generate baseline report
generate_baseline_report() {
    local platform
    platform=$(detect_platform)
    
    log_info "Generating baseline validation report for platform: $platform"
    
    cat > "${PROJECT_ROOT}/baseline-validation-${platform}.md" << EOF
# Performance Baseline Validation Report

**Platform**: $platform  
**Generated**: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

## Performance Targets

| Target | Threshold | Status |
|--------|-----------|---------|
| Function Call Overhead | ${FUNCTION_CALL_OVERHEAD_NS}ns | ✅/❌ |
| Module Loading | ${MODULE_LOADING_MS}ms | ✅/❌ |
| Startup Time | ${STARTUP_TIME_MS}ms | ✅/❌ |

## Validation Results

See individual benchmark outputs for detailed timing measurements.

## Platform Information

- **OS**: $(uname -s) $(uname -r)
- **Architecture**: $(uname -m)
- **Java Version**: $(java -version 2>&1 | head -1)
- **Rust Version**: $(rustc --version)

## Next Steps

If all targets are met, baselines are validated for this platform.
If targets are not met, optimization work is required before proceeding.

EOF

    log_success "Baseline report generated: baseline-validation-${platform}.md"
}

# Main validation function
main() {
    log_info "Starting performance baseline validation..."
    log_info "Platform: $(detect_platform)"
    
    # Check prerequisites
    if ! command -v cargo >/dev/null 2>&1; then
        log_error "Cargo not found - Rust toolchain required"
        exit 1
    fi
    
    if ! command -v java >/dev/null 2>&1; then
        log_error "Java not found - Java runtime required"
        exit 1
    fi
    
    if ! command -v python3 >/dev/null 2>&1; then
        log_error "Python 3 not found - required for result parsing"
        exit 1
    fi
    
    if ! command -v bc >/dev/null 2>&1; then
        log_error "bc calculator not found - required for numeric comparisons"
        exit 1
    fi
    
    local total_violations=0
    
    # Run benchmarks
    run_native_benchmarks || {
        log_error "Native benchmark execution failed"
        exit 1
    }
    
    run_jmh_benchmarks || {
        log_error "JMH benchmark execution failed"  
        exit 1
    }
    
    # Parse results and check targets
    parse_native_results || total_violations=$((total_violations + 1))
    
    local jmh_violations
    parse_jmh_results
    jmh_violations=$?
    total_violations=$((total_violations + jmh_violations))
    
    # Generate report
    generate_baseline_report
    
    # Final status
    if [[ $total_violations -eq 0 ]]; then
        log_success "All performance baselines validated successfully!"
        exit 0
    else
        log_error "Performance baseline validation failed with $total_violations violation(s)"
        exit 1
    fi
}

# Handle script interruption
trap 'log_error "Baseline validation interrupted"; exit 130' INT TERM

# Run if executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi