/*
 * Copyright (c) 2024 Tegmentum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.tegmentum.wamr4j.panama.impl;

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.exception.RuntimeException;
import ai.tegmentum.wamr4j.panama.internal.NativeLibraryLoader;
import java.lang.foreign.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Panama FFI-based implementation of WebAssembly module.
 * 
 * <p>This class represents a compiled WebAssembly module using Panama FFI
 * to communicate with the native WAMR library. It provides thread-safe
 * access to module information and instance creation with better type
 * safety than JNI.
 * 
 * @since 1.0.0
 */
public final class PanamaWebAssemblyModule implements WebAssemblyModule {

    private static final Logger LOGGER = Logger.getLogger(PanamaWebAssemblyModule.class.getName());
    
    // Native module handle as MemorySegment
    private volatile MemorySegment nativeHandle;
    
    // State tracking
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // Function descriptors for native calls
    private static final FunctionDescriptor DESTROY_MODULE_DESC = 
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
    private static final FunctionDescriptor INSTANTIATE_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor GET_EXPORTS_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor GET_IMPORTS_DESC = 
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    /**
     * Creates a new Panama WebAssembly module wrapper.
     * 
     * @param nativeHandle the native module handle, must not be NULL
     */
    public PanamaWebAssemblyModule(final MemorySegment nativeHandle) {
        if (nativeHandle == null || nativeHandle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Native module handle cannot be null or NULL");
        }
        this.nativeHandle = nativeHandle;
        LOGGER.fine("Created Panama WebAssembly module with handle: " + nativeHandle);
    }

    @Override
    public WebAssemblyInstance instantiate() throws RuntimeException {
        return instantiate(Map.of());
    }

    @Override
    public WebAssemblyInstance instantiate(final Map<String, Object> imports) throws RuntimeException {
        if (imports == null) {
            throw new IllegalArgumentException("Imports map cannot be null");
        }
        
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment instantiateFunc = lookup.find("wamr_instantiate_module")
                .orElseThrow(() -> new RuntimeException(
                    "Native function 'wamr_instantiate_module' not found"));
            
            final MethodHandle instantiate = Linker.nativeLinker()
                .downcallHandle(instantiateFunc, INSTANTIATE_DESC);
            
            final MemorySegment instanceHandle = (MemorySegment) instantiate.invoke(nativeHandle);
            if (instanceHandle.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to instantiate WebAssembly module");
            }
            
            return new PanamaWebAssemblyInstance(instanceHandle);
        } catch (final RuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Throwable e) {
            throw new RuntimeException("Unexpected error during module instantiation", e);
        }
    }

    @Override
    public String[] getExportNames() {
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getExportsFunc = lookup.find("wamr_get_exports")
                .orElse(MemorySegment.NULL);
                
            if (getExportsFunc.equals(MemorySegment.NULL)) {
                return new String[0];
            }
            
            final MethodHandle getExports = Linker.nativeLinker()
                .downcallHandle(getExportsFunc, GET_EXPORTS_DESC);
            
            final MemorySegment exportsPtr = (MemorySegment) getExports.invoke(nativeHandle);
            if (exportsPtr.equals(MemorySegment.NULL)) {
                return new String[0];
            }
            
            // Parse native string array (implementation depends on native format)
            return parseNativeStringArray(exportsPtr);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get export names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public String[] getImportNames() {
        ensureNotClosed();
        
        try {
            final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
            final MemorySegment getImportsFunc = lookup.find("wamr_get_imports")
                .orElse(MemorySegment.NULL);
                
            if (getImportsFunc.equals(MemorySegment.NULL)) {
                return new String[0];
            }
            
            final MethodHandle getImports = Linker.nativeLinker()
                .downcallHandle(getImportsFunc, GET_IMPORTS_DESC);
            
            final MemorySegment importsPtr = (MemorySegment) getImports.invoke(nativeHandle);
            if (importsPtr.equals(MemorySegment.NULL)) {
                return new String[0];
            }
            
            // Parse native string array (implementation depends on native format)
            return parseNativeStringArray(importsPtr);
        } catch (final Throwable e) {
            LOGGER.warning("Failed to get import names: " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public Map<String, FunctionSignature> getExportSignatures() {
        ensureNotClosed();
        
        try {
            // Placeholder implementation - would require native support
            return Map.of();
        } catch (final Exception e) {
            LOGGER.warning("Failed to get export signatures: " + e.getMessage());
            return Map.of();
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            final MemorySegment handle = nativeHandle;
            nativeHandle = MemorySegment.NULL;
            
            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                try {
                    final SymbolLookup lookup = NativeLibraryLoader.getSymbolLookup();
                    final MemorySegment destroyModuleFunc = lookup.find("wamr_destroy_module")
                        .orElse(MemorySegment.NULL);
                        
                    if (!destroyModuleFunc.equals(MemorySegment.NULL)) {
                        final MethodHandle destroyModule = Linker.nativeLinker()
                            .downcallHandle(destroyModuleFunc, DESTROY_MODULE_DESC);
                        destroyModule.invoke(handle);
                        LOGGER.fine("Destroyed Panama WebAssembly module with handle: " + handle);
                    }
                } catch (final Throwable e) {
                    LOGGER.warning("Error destroying native module: " + e.getMessage());
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WebAssembly module has been closed");
        }
    }
    
    private String[] parseNativeStringArray(final MemorySegment arrayPtr) {
        // Placeholder implementation - actual parsing depends on native format
        // This would typically involve iterating through null-terminated string pointers
        return new String[0];
    }
}