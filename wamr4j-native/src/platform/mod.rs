//! Platform-specific optimizations module for wamr4j-native
//! 
//! This module provides platform-specific optimizations for Linux, Windows, and macOS
//! to maximize performance and compatibility across different operating systems.

pub mod linux;
pub mod windows;
pub mod macos;

use std::fmt;

/// Platform-specific optimization configuration
pub enum PlatformOptimizations {
    Linux(linux::LinuxOptimizations),
    Windows(windows::WindowsOptimizations),
    MacOS(macos::MacosOptimizations),
}

/// Platform detection and initialization
pub struct Platform;

impl Platform {
    /// Detect current platform and initialize optimizations
    pub fn detect_and_init() -> Result<PlatformOptimizations, String> {
        #[cfg(target_os = "linux")]
        {
            let opts = linux::init_linux_optimizations()?;
            Ok(PlatformOptimizations::Linux(opts))
        }
        
        #[cfg(target_os = "windows")]
        {
            let opts = windows::init_windows_optimizations()?;
            Ok(PlatformOptimizations::Windows(opts))
        }
        
        #[cfg(target_os = "macos")]
        {
            let opts = macos::init_macos_optimizations()?;
            Ok(PlatformOptimizations::MacOS(opts))
        }
        
        #[cfg(not(any(target_os = "linux", target_os = "windows", target_os = "macos")))]
        {
            Err("Unsupported platform".to_string())
        }
    }

    /// Get current platform name
    pub fn get_platform_name() -> &'static str {
        std::env::consts::OS
    }

    /// Get current architecture
    pub fn get_architecture() -> &'static str {
        std::env::consts::ARCH
    }

    /// Get platform-specific build flags
    pub fn get_build_flags() -> Vec<String> {
        #[cfg(target_os = "linux")]
        return linux::get_linux_build_flags();
        
        #[cfg(target_os = "windows")]
        return windows::get_windows_build_flags();
        
        #[cfg(target_os = "macos")]
        {
            match std::env::consts::ARCH {
                "x86_64" => macos::get_macos_x86_64_build_flags(),
                "aarch64" => macos::get_macos_arm64_build_flags(),
                _ => macos::get_universal_binary_build_flags(),
            }
        }
        
        #[cfg(not(any(target_os = "linux", target_os = "windows", target_os = "macos")))]
        vec![]
    }

    /// Get optimal thread count for current platform
    pub fn get_optimal_thread_count(opts: &PlatformOptimizations) -> usize {
        match opts {
            PlatformOptimizations::Linux(linux_opts) => linux_opts.get_optimal_thread_count(),
            PlatformOptimizations::Windows(windows_opts) => windows_opts.get_optimal_thread_count(),
            PlatformOptimizations::MacOS(macos_opts) => macos_opts.get_optimal_thread_count(),
        }
    }
}

impl PlatformOptimizations {
    /// Get platform information as a string
    pub fn get_platform_info(&self) -> String {
        match self {
            PlatformOptimizations::Linux(opts) => {
                format!("Linux: {}", opts.get_glibc_info())
            }
            PlatformOptimizations::Windows(opts) => {
                format!("Windows: {}", opts.get_msvc_info())
            }
            PlatformOptimizations::MacOS(opts) => {
                format!("macOS: {} on {}", opts.get_macos_info(), opts.get_cpu_architecture_info())
            }
        }
    }

    /// Apply platform-specific memory optimizations
    pub unsafe fn apply_memory_optimizations(&mut self) -> Result<(), String> {
        match self {
            PlatformOptimizations::Linux(opts) => opts.apply_mmap_optimizations(),
            PlatformOptimizations::Windows(opts) => opts.apply_memory_optimizations(),
            PlatformOptimizations::MacOS(opts) => opts.apply_memory_optimizations(),
        }
    }

    /// Get optimal thread count
    pub fn get_optimal_thread_count(&self) -> usize {
        match self {
            PlatformOptimizations::Linux(opts) => opts.get_optimal_thread_count(),
            PlatformOptimizations::Windows(opts) => opts.get_optimal_thread_count(),
            PlatformOptimizations::MacOS(opts) => opts.get_optimal_thread_count(),
        }
    }
}

impl fmt::Display for PlatformOptimizations {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.get_platform_info())
    }
}

/// Global platform optimizations instance
static mut PLATFORM_OPTIMIZATIONS: Option<PlatformOptimizations> = None;
static PLATFORM_INIT: std::sync::Once = std::sync::Once::new();

/// Initialize global platform optimizations (thread-safe)
pub fn init_global_platform_optimizations() -> Result<(), String> {
    PLATFORM_INIT.call_once(|| {
        match Platform::detect_and_init() {
            Ok(mut opts) => {
                // Apply memory optimizations during initialization
                unsafe {
                    if let Err(e) = opts.apply_memory_optimizations() {
                        eprintln!("Warning: Failed to apply memory optimizations: {}", e);
                    }
                }
                
                unsafe {
                    PLATFORM_OPTIMIZATIONS = Some(opts);
                }
            }
            Err(e) => {
                eprintln!("Error: Failed to initialize platform optimizations: {}", e);
            }
        }
    });

    unsafe {
        if PLATFORM_OPTIMIZATIONS.is_some() {
            Ok(())
        } else {
            Err("Failed to initialize platform optimizations".to_string())
        }
    }
}

/// Get reference to global platform optimizations
pub fn get_platform_optimizations() -> Option<&'static PlatformOptimizations> {
    unsafe { PLATFORM_OPTIMIZATIONS.as_ref() }
}

/// Get platform information string
pub fn get_platform_info() -> String {
    if let Some(opts) = get_platform_optimizations() {
        opts.get_platform_info()
    } else {
        format!("{} {} (not optimized)", Platform::get_platform_name(), Platform::get_architecture())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_platform_detection() {
        let platform_name = Platform::get_platform_name();
        let architecture = Platform::get_architecture();
        
        assert!(!platform_name.is_empty());
        assert!(!architecture.is_empty());
        
        // Should be one of the supported platforms
        assert!(matches!(platform_name, "linux" | "windows" | "macos"));
        // Should be one of the supported architectures
        assert!(matches!(architecture, "x86_64" | "aarch64"));
    }

    #[test]
    fn test_platform_initialization() {
        let result = Platform::detect_and_init();
        assert!(result.is_ok());
        
        let opts = result.unwrap();
        let info = opts.get_platform_info();
        assert!(!info.is_empty());
    }

    #[test]
    fn test_build_flags() {
        let flags = Platform::get_build_flags();
        assert!(!flags.is_empty());
    }

    #[test]
    fn test_thread_count() {
        let opts = Platform::detect_and_init().unwrap();
        let thread_count = Platform::get_optimal_thread_count(&opts);
        assert!(thread_count > 0);
    }

    #[test]
    fn test_global_initialization() {
        let result = init_global_platform_optimizations();
        assert!(result.is_ok());
        
        let opts = get_platform_optimizations();
        assert!(opts.is_some());
        
        let info = get_platform_info();
        assert!(!info.is_empty());
    }
}