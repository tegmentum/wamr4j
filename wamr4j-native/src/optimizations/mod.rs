//! Optimization modules for wamr4j-native
//! 
//! This module provides various optimization strategies for different architectures
//! and use cases to maximize WebAssembly execution performance.

pub mod simd_arm64;

use std::fmt;

/// General optimization configuration
pub struct OptimizationConfig {
    /// Enable SIMD optimizations where available
    pub enable_simd: bool,
    /// Enable branch prediction optimizations
    pub enable_branch_prediction: bool,
    /// Enable memory prefetching
    pub enable_memory_prefetch: bool,
    /// Enable loop unrolling
    pub enable_loop_unrolling: bool,
    /// Target CPU architecture
    pub target_architecture: TargetArchitecture,
}

/// Supported target architectures for optimization
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TargetArchitecture {
    X86_64,
    Aarch64,
    Unknown,
}

impl Default for OptimizationConfig {
    fn default() -> Self {
        Self {
            enable_simd: true,
            enable_branch_prediction: true,
            enable_memory_prefetch: true,
            enable_loop_unrolling: true,
            target_architecture: TargetArchitecture::detect(),
        }
    }
}

impl TargetArchitecture {
    /// Detect current target architecture
    pub fn detect() -> Self {
        match std::env::consts::ARCH {
            "x86_64" => Self::X86_64,
            "aarch64" => Self::Aarch64,
            _ => Self::Unknown,
        }
    }

    /// Get architecture name as string
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::X86_64 => "x86_64",
            Self::Aarch64 => "aarch64",
            Self::Unknown => "unknown",
        }
    }
}

impl OptimizationConfig {
    /// Create new optimization configuration with auto-detected settings
    pub fn new() -> Self {
        let mut config = Self::default();
        config.adjust_for_architecture();
        config
    }

    /// Adjust optimization settings based on target architecture
    pub fn adjust_for_architecture(&mut self) {
        match self.target_architecture {
            TargetArchitecture::X86_64 => {
                // x86_64 has good SIMD support, enable all optimizations
                self.enable_simd = true;
                self.enable_branch_prediction = true;
                self.enable_memory_prefetch = true;
                self.enable_loop_unrolling = true;
            }
            TargetArchitecture::Aarch64 => {
                // ARM64 has excellent SIMD support, enable all optimizations
                self.enable_simd = true;
                self.enable_branch_prediction = true;
                self.enable_memory_prefetch = true;
                self.enable_loop_unrolling = true;
            }
            TargetArchitecture::Unknown => {
                // Conservative settings for unknown architectures
                self.enable_simd = false;
                self.enable_branch_prediction = false;
                self.enable_memory_prefetch = false;
                self.enable_loop_unrolling = false;
            }
        }
    }

    /// Get compiler optimization flags for the target architecture
    pub fn get_compiler_flags(&self) -> Vec<String> {
        let mut flags = Vec::new();

        // Base optimization level
        flags.push("-O3".to_string());

        match self.target_architecture {
            TargetArchitecture::X86_64 => {
                flags.extend(self.get_x86_64_flags());
            }
            TargetArchitecture::Aarch64 => {
                flags.extend(self.get_aarch64_flags());
            }
            TargetArchitecture::Unknown => {
                // Generic flags only
                flags.push("-Os".to_string()); // Optimize for size
            }
        }

        flags
    }

    /// Get x86_64-specific optimization flags
    fn get_x86_64_flags(&self) -> Vec<String> {
        let mut flags = Vec::new();

        if self.enable_simd {
            flags.push("-msse4.2".to_string());
            flags.push("-mavx".to_string());
            flags.push("-mavx2".to_string());
            flags.push("-mfma".to_string());
        }

        if self.enable_branch_prediction {
            flags.push("-fno-omit-frame-pointer".to_string());
        }

        if self.enable_memory_prefetch {
            flags.push("-fprefetch-loop-arrays".to_string());
        }

        if self.enable_loop_unrolling {
            flags.push("-funroll-loops".to_string());
        }

        flags
    }

    /// Get ARM64-specific optimization flags
    fn get_aarch64_flags(&self) -> Vec<String> {
        let mut flags = Vec::new();

        if self.enable_simd {
            // Get ARM64 SIMD-specific flags
            if let Ok(arm64_opts) = simd_arm64::init_arm64_simd_optimizations() {
                let simd_flags = arm64_opts.get_simd_compiler_flags();
                for flag in simd_flags {
                    flags.push(format!("-mcpu=native+{}", flag));
                }
            } else {
                // Fallback to basic ARM64 SIMD
                flags.push("-mcpu=native+simd".to_string());
            }
        }

        if self.enable_branch_prediction {
            flags.push("-fno-omit-frame-pointer".to_string());
        }

        if self.enable_memory_prefetch {
            flags.push("-fprefetch-loop-arrays".to_string());
        }

        if self.enable_loop_unrolling {
            flags.push("-funroll-loops".to_string());
        }

        flags
    }

    /// Get Rust-specific target features
    pub fn get_rust_target_features(&self) -> Vec<String> {
        let mut features = Vec::new();

        match self.target_architecture {
            TargetArchitecture::X86_64 => {
                if self.enable_simd {
                    features.extend(vec![
                        "sse4.2".to_string(),
                        "avx".to_string(),
                        "avx2".to_string(),
                        "fma".to_string(),
                    ]);
                }
            }
            TargetArchitecture::Aarch64 => {
                if self.enable_simd {
                    if let Ok(arm64_opts) = simd_arm64::init_arm64_simd_optimizations() {
                        features.extend(arm64_opts.get_rust_target_features());
                    } else {
                        features.push("neon".to_string());
                    }
                }
            }
            TargetArchitecture::Unknown => {
                // No specific features for unknown architectures
            }
        }

        features
    }

    /// Get optimization information as string
    pub fn get_optimization_info(&self) -> String {
        let mut info = Vec::new();

        info.push(format!("Target: {}", self.target_architecture.as_str()));

        if self.enable_simd {
            info.push("SIMD: enabled".to_string());
        }
        if self.enable_branch_prediction {
            info.push("Branch prediction: enabled".to_string());
        }
        if self.enable_memory_prefetch {
            info.push("Memory prefetch: enabled".to_string());
        }
        if self.enable_loop_unrolling {
            info.push("Loop unrolling: enabled".to_string());
        }

        format!("Optimizations: {}", info.join(", "))
    }

    /// Check if any optimizations are enabled
    pub fn has_optimizations(&self) -> bool {
        self.enable_simd || self.enable_branch_prediction || 
        self.enable_memory_prefetch || self.enable_loop_unrolling
    }
}

impl fmt::Display for OptimizationConfig {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.get_optimization_info())
    }
}

/// Initialize optimizations for current platform
pub fn init_optimizations() -> Result<OptimizationConfig, String> {
    let config = OptimizationConfig::new();
    
    if !config.has_optimizations() {
        return Err("No optimizations available for current platform".to_string());
    }
    
    Ok(config)
}

/// Get optimal configuration for current platform and architecture
pub fn get_optimal_config() -> OptimizationConfig {
    init_optimizations().unwrap_or_else(|_| {
        // Fallback to minimal configuration
        OptimizationConfig {
            enable_simd: false,
            enable_branch_prediction: false,
            enable_memory_prefetch: false,
            enable_loop_unrolling: false,
            target_architecture: TargetArchitecture::detect(),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_target_architecture_detection() {
        let arch = TargetArchitecture::detect();
        
        // Should detect a valid architecture
        assert!(matches!(arch, TargetArchitecture::X86_64 | 
                              TargetArchitecture::Aarch64 | 
                              TargetArchitecture::Unknown));
        
        // Architecture string should be valid
        let arch_str = arch.as_str();
        assert!(!arch_str.is_empty());
        assert!(matches!(arch_str, "x86_64" | "aarch64" | "unknown"));
    }

    #[test]
    fn test_optimization_config_creation() {
        let config = OptimizationConfig::new();
        
        // Should have detected target architecture
        assert!(config.target_architecture != TargetArchitecture::Unknown || 
                std::env::consts::ARCH == "unknown");
    }

    #[test]
    fn test_compiler_flags() {
        let config = OptimizationConfig::new();
        let flags = config.get_compiler_flags();
        
        // Should have at least base optimization
        assert!(!flags.is_empty());
        assert!(flags.contains(&"-O3".to_string()) || flags.contains(&"-Os".to_string()));
    }

    #[test]
    fn test_rust_target_features() {
        let config = OptimizationConfig::new();
        let features = config.get_rust_target_features();
        
        // Features should be architecture-appropriate
        match config.target_architecture {
            TargetArchitecture::X86_64 => {
                if config.enable_simd {
                    assert!(features.len() > 0);
                }
            }
            TargetArchitecture::Aarch64 => {
                if config.enable_simd {
                    assert!(features.len() > 0);
                }
            }
            TargetArchitecture::Unknown => {
                assert!(features.is_empty());
            }
        }
    }

    #[test]
    fn test_optimization_info() {
        let config = OptimizationConfig::new();
        let info = config.get_optimization_info();
        
        assert!(!info.is_empty());
        assert!(info.contains("Target:"));
    }

    #[test]
    fn test_has_optimizations() {
        let config = OptimizationConfig::new();
        
        // Most platforms should have some optimizations available
        if config.target_architecture != TargetArchitecture::Unknown {
            assert!(config.has_optimizations());
        }
    }

    #[test]
    fn test_initialization() {
        let result = init_optimizations();
        
        // Should succeed on known architectures
        match TargetArchitecture::detect() {
            TargetArchitecture::Unknown => {
                // May fail on unknown architectures
            }
            _ => {
                assert!(result.is_ok());
            }
        }
    }

    #[test]
    fn test_optimal_config() {
        let config = get_optimal_config();
        
        // Should always return a valid configuration
        assert!(config.target_architecture == TargetArchitecture::detect());
    }
}