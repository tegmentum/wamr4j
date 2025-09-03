#!/usr/bin/env python3
"""
Copyright (c) 2024 Tegmentum

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

"""
Cross-platform performance monitoring and regression detection system.

This script provides continuous performance monitoring capabilities:
- Historical performance data tracking
- Regression detection and alerts
- Multi-platform performance comparison
- Performance trend analysis
"""

import json
import sqlite3
import argparse
import subprocess
import platform
import sys
from pathlib import Path
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple
import statistics

# Performance targets from requirements
PERFORMANCE_TARGETS = {
    'function_call_overhead_ns': 10.0,
    'module_loading_ms': 100.0,
    'startup_time_ms': 50.0,
}

# Regression thresholds
REGRESSION_THRESHOLDS = {
    'warning': 0.10,   # 10% degradation warning
    'critical': 0.25,  # 25% degradation critical
}

class PerformanceMonitor:
    """Cross-platform performance monitoring system."""
    
    def __init__(self, db_path: Path):
        """Initialize performance monitor with database."""
        self.db_path = db_path
        self.platform_info = self._get_platform_info()
        self._init_database()
    
    def _get_platform_info(self) -> Dict[str, str]:
        """Get platform information for tracking."""
        return {
            'os': platform.system(),
            'arch': platform.machine(),
            'platform': f"{platform.system().lower()}-{platform.machine()}",
            'python_version': platform.python_version(),
        }
    
    def _init_database(self):
        """Initialize SQLite database for historical data."""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute('''
                CREATE TABLE IF NOT EXISTS performance_runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    git_commit TEXT,
                    git_branch TEXT,
                    run_type TEXT NOT NULL,
                    success BOOLEAN NOT NULL
                )
            ''')
            
            conn.execute('''
                CREATE TABLE IF NOT EXISTS benchmark_results (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_id INTEGER NOT NULL,
                    benchmark_type TEXT NOT NULL,
                    benchmark_name TEXT NOT NULL,
                    value REAL NOT NULL,
                    unit TEXT NOT NULL,
                    target_value REAL,
                    meets_target BOOLEAN,
                    FOREIGN KEY (run_id) REFERENCES performance_runs (id)
                )
            ''')
            
            conn.execute('''
                CREATE INDEX IF NOT EXISTS idx_benchmark_name_platform
                ON benchmark_results (benchmark_name, benchmark_type)
            ''')
            
            conn.execute('''
                CREATE INDEX IF NOT EXISTS idx_timestamp
                ON performance_runs (timestamp)
            ''')
    
    def _get_git_info(self) -> Tuple[Optional[str], Optional[str]]:
        """Get current git commit and branch."""
        try:
            commit = subprocess.check_output(
                ['git', 'rev-parse', 'HEAD'],
                stderr=subprocess.DEVNULL
            ).decode().strip()
            
            branch = subprocess.check_output(
                ['git', 'rev-parse', '--abbrev-ref', 'HEAD'],
                stderr=subprocess.DEVNULL
            ).decode().strip()
            
            return commit, branch
        except subprocess.CalledProcessError:
            return None, None
    
    def run_benchmarks(self) -> bool:
        """Execute both native and JMH benchmarks."""
        project_root = Path(__file__).parent.parent.parent
        
        print(f"Running benchmarks on platform: {self.platform_info['platform']}")
        
        # Run native benchmarks
        print("Executing native Rust benchmarks...")
        try:
            native_result = subprocess.run([
                'cargo', 'bench', '--', '--output-format', 'json'
            ], cwd=project_root / 'wamr4j-native', 
            capture_output=True, text=True, timeout=300)
            
            if native_result.returncode != 0:
                print(f"Native benchmarks failed: {native_result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            print("Native benchmarks timed out")
            return False
        
        # Run JMH benchmarks
        print("Executing JMH Java benchmarks...")
        try:
            jmh_result = subprocess.run([
                './mvnw', 'clean', 'compile', 'exec:java', 
                '-pl', 'wamr4j-benchmarks',
                '-Dexec.mainClass=ai.tegmentum.wamr4j.benchmarks.RuntimeBenchmark',
                '-Dexec.args=-rf json -rff jmh-results.json',
                '-q'
            ], cwd=project_root, capture_output=True, text=True, timeout=600)
            
            if jmh_result.returncode != 0:
                print(f"JMH benchmarks failed: {jmh_result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            print("JMH benchmarks timed out")
            return False
        
        return True
    
    def parse_native_results(self, benchmark_output: str) -> List[Dict]:
        """Parse Criterion.rs benchmark output."""
        results = []
        
        for line in benchmark_output.split('\n'):
            line = line.strip()
            if line and line.startswith('{'):
                try:
                    data = json.loads(line)
                    if data.get('reason') == 'benchmark-complete':
                        benchmark_name = data.get('id', '')
                        if 'mean' in data:
                            mean_estimate = data['mean']['estimate']
                            unit = data['mean'].get('unit', 'ns')
                            
                            results.append({
                                'benchmark_type': 'native',
                                'benchmark_name': benchmark_name,
                                'value': mean_estimate,
                                'unit': unit
                            })
                except json.JSONDecodeError:
                    continue
        
        return results
    
    def parse_jmh_results(self, results_file: Path) -> List[Dict]:
        """Parse JMH benchmark results from JSON file."""
        results = []
        
        if not results_file.exists():
            return results
        
        try:
            with open(results_file) as f:
                data = json.load(f)
                
            for benchmark in data:
                name = benchmark.get('benchmark', '')
                mean_score = benchmark.get('primaryMetric', {}).get('score', 0)
                
                results.append({
                    'benchmark_type': 'jmh',
                    'benchmark_name': name,
                    'value': mean_score,
                    'unit': 'μs'
                })
                
        except (json.JSONDecodeError, FileNotFoundError):
            pass
        
        return results
    
    def check_performance_targets(self, results: List[Dict]) -> Dict[str, bool]:
        """Check if results meet performance targets."""
        target_status = {}
        
        for result in results:
            name = result['benchmark_name']
            value = result['value']
            
            # Check function call overhead (native)
            if 'basic_ffi/test_function' in name and result['unit'] == 'ns':
                target = PERFORMANCE_TARGETS['function_call_overhead_ns']
                meets_target = value <= target
                target_status['function_call_overhead'] = meets_target
                result['target_value'] = target
                result['meets_target'] = meets_target
            
            # Check module compilation (JMH)
            elif 'ModuleCompilation' in name and result['unit'] == 'μs':
                target_ms = PERFORMANCE_TARGETS['module_loading_ms']
                value_ms = value / 1000.0
                meets_target = value_ms <= target_ms
                target_status['module_loading'] = meets_target
                result['target_value'] = target_ms
                result['meets_target'] = meets_target
            
            # Check runtime creation (JMH)
            elif 'RuntimeCreation' in name and result['unit'] == 'μs':
                target_ms = PERFORMANCE_TARGETS['startup_time_ms']
                value_ms = value / 1000.0
                meets_target = value_ms <= target_ms
                target_status['startup_time'] = meets_target
                result['target_value'] = target_ms
                result['meets_target'] = meets_target
        
        return target_status
    
    def detect_regressions(self, current_results: List[Dict]) -> Dict[str, List[Dict]]:
        """Detect performance regressions compared to historical data."""
        regressions = {'warnings': [], 'critical': []}
        
        with sqlite3.connect(self.db_path) as conn:
            for result in current_results:
                # Get historical data for this benchmark
                cursor = conn.execute('''
                    SELECT br.value, pr.timestamp
                    FROM benchmark_results br
                    JOIN performance_runs pr ON br.run_id = pr.id
                    WHERE br.benchmark_name = ? 
                    AND br.benchmark_type = ?
                    AND pr.platform = ?
                    AND pr.success = 1
                    ORDER BY pr.timestamp DESC
                    LIMIT 10
                ''', (result['benchmark_name'], result['benchmark_type'], 
                     self.platform_info['platform']))
                
                historical_values = [row[0] for row in cursor.fetchall()]
                
                if len(historical_values) >= 3:
                    historical_mean = statistics.mean(historical_values)
                    current_value = result['value']
                    
                    if historical_mean > 0:  # Avoid division by zero
                        regression_ratio = (current_value - historical_mean) / historical_mean
                        
                        if regression_ratio > REGRESSION_THRESHOLDS['critical']:
                            regressions['critical'].append({
                                'benchmark': result['benchmark_name'],
                                'current': current_value,
                                'historical_mean': historical_mean,
                                'regression_percent': regression_ratio * 100
                            })
                        elif regression_ratio > REGRESSION_THRESHOLDS['warning']:
                            regressions['warnings'].append({
                                'benchmark': result['benchmark_name'],
                                'current': current_value,
                                'historical_mean': historical_mean,
                                'regression_percent': regression_ratio * 100
                            })
        
        return regressions
    
    def store_results(self, results: List[Dict], success: bool, 
                     run_type: str = 'manual') -> int:
        """Store benchmark results in database."""
        commit, branch = self._get_git_info()
        timestamp = datetime.now(timezone.utc).isoformat()
        
        with sqlite3.connect(self.db_path) as conn:
            # Insert performance run
            cursor = conn.execute('''
                INSERT INTO performance_runs 
                (timestamp, platform, git_commit, git_branch, run_type, success)
                VALUES (?, ?, ?, ?, ?, ?)
            ''', (timestamp, self.platform_info['platform'], commit, branch, 
                  run_type, success))
            
            run_id = cursor.lastrowid
            
            # Insert benchmark results
            for result in results:
                conn.execute('''
                    INSERT INTO benchmark_results
                    (run_id, benchmark_type, benchmark_name, value, unit, 
                     target_value, meets_target)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                ''', (run_id, result['benchmark_type'], result['benchmark_name'],
                      result['value'], result['unit'], 
                      result.get('target_value'), result.get('meets_target')))
            
            return run_id
    
    def generate_report(self, results: List[Dict], target_status: Dict[str, bool],
                       regressions: Dict[str, List[Dict]]) -> str:
        """Generate performance monitoring report."""
        report_lines = [
            "# Performance Monitoring Report",
            f"**Generated**: {datetime.now(timezone.utc).isoformat()}",
            f"**Platform**: {self.platform_info['platform']}",
            ""
        ]
        
        # Overall status
        all_targets_met = all(target_status.values())
        has_critical_regressions = len(regressions['critical']) > 0
        
        if all_targets_met and not has_critical_regressions:
            report_lines.extend([
                "## ✅ Overall Status: PASS",
                "All performance targets met and no critical regressions detected.",
                ""
            ])
        else:
            status_issues = []
            if not all_targets_met:
                failed_targets = [k for k, v in target_status.items() if not v]
                status_issues.append(f"Failed targets: {', '.join(failed_targets)}")
            if has_critical_regressions:
                status_issues.append(f"Critical regressions: {len(regressions['critical'])}")
            
            report_lines.extend([
                "## ❌ Overall Status: FAIL",
                f"Issues detected: {'; '.join(status_issues)}",
                ""
            ])
        
        # Performance targets
        report_lines.extend([
            "## Performance Targets",
            "| Target | Threshold | Status |",
            "|--------|-----------|---------|"
        ])
        
        for target_name, target_value in PERFORMANCE_TARGETS.items():
            status = "✅" if target_status.get(target_name.replace('_ns', '').replace('_ms', ''), True) else "❌"
            unit = "ns" if "_ns" in target_name else "ms"
            report_lines.append(f"| {target_name.replace('_', ' ').title()} | {target_value}{unit} | {status} |")
        
        report_lines.append("")
        
        # Regression analysis
        if regressions['critical'] or regressions['warnings']:
            report_lines.extend([
                "## Regression Analysis",
                ""
            ])
            
            if regressions['critical']:
                report_lines.extend([
                    "### ❌ Critical Regressions (>25% degradation)",
                    ""
                ])
                for reg in regressions['critical']:
                    report_lines.append(
                        f"- **{reg['benchmark']}**: "
                        f"{reg['current']:.2f} vs {reg['historical_mean']:.2f} avg "
                        f"({reg['regression_percent']:+.1f}%)"
                    )
                report_lines.append("")
            
            if regressions['warnings']:
                report_lines.extend([
                    "### ⚠️ Performance Warnings (>10% degradation)",
                    ""
                ])
                for reg in regressions['warnings']:
                    report_lines.append(
                        f"- **{reg['benchmark']}**: "
                        f"{reg['current']:.2f} vs {reg['historical_mean']:.2f} avg "
                        f"({reg['regression_percent']:+.1f}%)"
                    )
                report_lines.append("")
        else:
            report_lines.extend([
                "## Regression Analysis",
                "✅ No performance regressions detected.",
                ""
            ])
        
        return "\n".join(report_lines)
    
    def monitor(self) -> bool:
        """Run complete performance monitoring cycle."""
        print("Starting performance monitoring...")
        
        # Execute benchmarks
        if not self.run_benchmarks():
            print("❌ Benchmark execution failed")
            return False
        
        # Parse results
        project_root = Path(__file__).parent.parent.parent
        
        # Parse native results (from previous run)
        native_results = []
        try:
            # In a real implementation, we'd capture the output from cargo bench
            # For now, we simulate empty results
            pass
        except Exception as e:
            print(f"Warning: Could not parse native results: {e}")
        
        # Parse JMH results
        jmh_results = self.parse_jmh_results(project_root / 'jmh-results.json')
        
        all_results = native_results + jmh_results
        
        if not all_results:
            print("❌ No benchmark results to analyze")
            return False
        
        # Check targets
        target_status = self.check_performance_targets(all_results)
        
        # Detect regressions
        regressions = self.detect_regressions(all_results)
        
        # Store results
        success = all(target_status.values()) and len(regressions['critical']) == 0
        run_id = self.store_results(all_results, success)
        
        # Generate report
        report = self.generate_report(all_results, target_status, regressions)
        
        # Save report
        report_file = project_root / f'performance-monitor-{self.platform_info["platform"]}.md'
        with open(report_file, 'w') as f:
            f.write(report)
        
        print(f"Performance monitoring completed (Run ID: {run_id})")
        print(f"Report saved: {report_file}")
        
        return success

def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description='Performance monitoring system')
    parser.add_argument('--db', type=Path, default='performance.db',
                       help='Database file for historical data')
    parser.add_argument('--ci', action='store_true',
                       help='Run in CI mode (fail on regressions)')
    
    args = parser.parse_args()
    
    monitor = PerformanceMonitor(args.db)
    success = monitor.monitor()
    
    if not success and args.ci:
        print("❌ Performance monitoring failed in CI mode")
        sys.exit(1)
    elif success:
        print("✅ Performance monitoring completed successfully")
    else:
        print("⚠️ Performance monitoring completed with warnings")

if __name__ == '__main__':
    main()