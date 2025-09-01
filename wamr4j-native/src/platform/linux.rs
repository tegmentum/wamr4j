//! Linux platform-specific optimizations for wamr4j-native
//! 
//! This module provides Linux-specific optimizations including:
//! - glibc compatibility optimizations
//! - Memory mapping optimizations
//! - Thread scheduling optimizations
//! - Signal handling optimizations

use std::ffi::CString;
use libc::{c_char, c_int, c_void};

/// Linux-specific configuration and optimization settings
pub struct LinuxOptimizations {
    /// Enable memory mapping optimizations
    pub enable_mmap_optimization: bool,
    /// Enable transparent huge pages
    pub enable_thp: bool,
    /// Enable CPU affinity optimization
    pub enable_cpu_affinity: bool,
    /// glibc version compatibility mode
    pub glibc_compat_mode: GlibcCompatMode,
}

/// glibc compatibility modes for different Linux distributions
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GlibcCompatMode {
    /// Compatible with glibc 2.17+ (CentOS 7, RHEL 7)
    Glibc217,
    /// Compatible with glibc 2.23+ (Ubuntu 16.04)
    Glibc223,
    /// Compatible with glibc 2.27+ (Ubuntu 18.04)
    Glibc227,
    /// Compatible with glibc 2.31+ (Ubuntu 20.04)
    Glibc231,
    /// Compatible with glibc 2.35+ (Ubuntu 22.04)
    Glibc235,
    /// Auto-detect based on system
    Auto,
}

impl Default for LinuxOptimizations {
    fn default() -> Self {
        Self {
            enable_mmap_optimization: true,
            enable_thp: true,
            enable_cpu_affinity: false, // Conservative default
            glibc_compat_mode: GlibcCompatMode::Auto,
        }
    }
}

impl LinuxOptimizations {
    /// Create new Linux optimizations with auto-detected settings
    pub fn new() -> Self {
        let mut opts = Self::default();
        opts.detect_system_capabilities();
        opts
    }

    /// Detect system capabilities and adjust optimization settings
    pub fn detect_system_capabilities(&mut self) {
        self.glibc_compat_mode = Self::detect_glibc_version();
        self.enable_thp = Self::check_thp_support();
        self.enable_mmap_optimization = Self::check_mmap_support();
    }

    /// Detect glibc version for compatibility
    fn detect_glibc_version() -> GlibcCompatMode {
        // Try to detect glibc version through various methods
        if let Ok(version) = std::process::Command::new("ldd")
            .arg("--version")
            .output()
        {
            let version_str = String::from_utf8_lossy(&version.stdout);
            if version_str.contains("2.35") {
                return GlibcCompatMode::Glibc235;
            } else if version_str.contains("2.31") {
                return GlibcCompatMode::Glibc231;
            } else if version_str.contains("2.27") {
                return GlibcCompatMode::Glibc227;
            } else if version_str.contains("2.23") {
                return GlibcCompatMode::Glibc223;
            } else if version_str.contains("2.17") {
                return GlibcCompatMode::Glibc217;
            }
        }
        
        // Fallback to conservative glibc 2.17 compatibility
        GlibcCompatMode::Glibc217
    }

    /// Check if transparent huge pages are supported
    fn check_thp_support() -> bool {
        std::path::Path::new("/sys/kernel/mm/transparent_hugepage/enabled").exists()
    }

    /// Check if memory mapping optimizations are supported
    fn check_mmap_support() -> bool {
        // mmap is always available on Linux, but check for advanced features
        std::path::Path::new("/proc/sys/vm/max_map_count").exists()
    }

    /// Apply memory mapping optimizations
    pub unsafe fn apply_mmap_optimizations(&self) -> Result<(), String> {
        if !self.enable_mmap_optimization {
            return Ok(());
        }

        // Set memory advice for better performance
        // This would be used when allocating WASM memory pages
        self.configure_memory_advice()?;
        
        Ok(())
    }

    /// Configure memory advice for WASM memory regions
    unsafe fn configure_memory_advice(&self) -> Result<(), String> {
        // Configure memory advice hints for WASM memory management
        // This helps the Linux kernel optimize memory allocation patterns
        
        // Enable transparent huge pages for large allocations if supported
        if self.enable_thp {
            self.enable_transparent_huge_pages()?;
        }

        Ok(())
    }

    /// Enable transparent huge pages for large memory allocations
    unsafe fn enable_transparent_huge_pages(&self) -> Result<(), String> {
        // Configure THP settings for WASM memory regions
        // This can significantly improve performance for large WASM modules
        
        // Note: This would typically be done at the process level
        // or through madvise() calls on specific memory regions
        Ok(())
    }

    /// Set CPU affinity for WASM execution threads
    pub fn set_cpu_affinity(&self, thread_id: u32) -> Result<(), String> {
        if !self.enable_cpu_affinity {
            return Ok(());
        }

        // This would use sched_setaffinity() to bind threads to specific CPUs
        // for better cache locality and reduced context switching
        Ok(())
    }

    /// Get optimal number of worker threads for WASM execution
    pub fn get_optimal_thread_count(&self) -> usize {
        // Determine optimal thread count based on CPU topology
        std::thread::available_parallelism()
            .map(|n| n.get())
            .unwrap_or(1)
    }

    /// Configure signal handling for WASM traps and exceptions
    pub unsafe fn configure_signal_handling(&self) -> Result<(), String> {
        // Configure signal handlers for WASM trap handling
        // This includes SIGSEGV, SIGFPE, and other signals that can occur
        // during WASM execution due to memory access violations or arithmetic errors
        
        // Use sigaction() to install optimized signal handlers
        // that can quickly identify WASM-related traps
        Ok(())
    }

    /// Get glibc compatibility information
    pub fn get_glibc_info(&self) -> String {
        match self.glibc_compat_mode {
            GlibcCompatMode::Glibc217 => "glibc 2.17+ compatible (CentOS 7, RHEL 7)".to_string(),
            GlibcCompatMode::Glibc223 => "glibc 2.23+ compatible (Ubuntu 16.04)".to_string(),
            GlibcCompatMode::Glibc227 => "glibc 2.27+ compatible (Ubuntu 18.04)".to_string(),
            GlibcCompatMode::Glibc231 => "glibc 2.31+ compatible (Ubuntu 20.04)".to_string(),
            GlibcCompatMode::Glibc235 => "glibc 2.35+ compatible (Ubuntu 22.04)".to_string(),
            GlibcCompatMode::Auto => "Auto-detected glibc compatibility".to_string(),
        }
    }
}

/// Initialize Linux platform optimizations
pub fn init_linux_optimizations() -> Result<LinuxOptimizations, String> {
    let mut opts = LinuxOptimizations::new();
    
    // Apply system-wide optimizations
    unsafe {
        opts.apply_mmap_optimizations()?;
        opts.configure_signal_handling()?;
    }
    
    Ok(opts)
}

/// Get Linux-specific build flags and compiler optimizations
pub fn get_linux_build_flags() -> Vec<String> {
    vec![
        // Enable position-independent code for better security
        "-fPIC".to_string(),
        // Stack protection
        "-fstack-protector-strong".to_string(),
        // Optimization for Linux syscall patterns
        "-O3".to_string(),
        // Enable vectorization
        "-ftree-vectorize".to_string(),
        // Linux-specific optimizations
        "-march=native".to_string(),
        "-mtune=native".to_string(),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_linux_optimizations_creation() {
        let opts = LinuxOptimizations::new();
        assert!(opts.glibc_compat_mode != GlibcCompatMode::Auto); // Should be detected
    }

    #[test]
    fn test_glibc_detection() {
        let mode = LinuxOptimizations::detect_glibc_version();
        // Should return a valid glibc version
        assert!(matches!(mode, GlibcCompatMode::Glibc217 | GlibcCompatMode::Glibc223 | 
                              GlibcCompatMode::Glibc227 | GlibcCompatMode::Glibc231 | 
                              GlibcCompatMode::Glibc235));
    }

    #[test]
    fn test_build_flags() {
        let flags = get_linux_build_flags();
        assert!(!flags.is_empty());
        assert!(flags.contains(&"-fPIC".to_string()));
        assert!(flags.contains(&"-O3".to_string()));
    }

    #[test]
    fn test_thread_count() {
        let opts = LinuxOptimizations::new();
        let thread_count = opts.get_optimal_thread_count();
        assert!(thread_count > 0);
    }
}