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

package ai.tegmentum.wamr4j.jni.impl;

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.ValueType;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import java.util.logging.Logger;

/**
 * JNI-based implementation of WebAssembly function.
 * 
 * <p>This class represents a callable WebAssembly function using JNI
 * to communicate with the native WAMR library. It provides type-safe
 * function invocation with proper error handling.
 * 
 * @since 1.0.0
 */
public final class JniWebAssemblyFunction implements WebAssemblyFunction {

    private static final Logger LOGGER = Logger.getLogger(JniWebAssemblyFunction.class.getName());

    // Native function handle
    private volatile long nativeHandle;

    // Function metadata
    private final String functionName;
    private final JniWebAssemblyInstance parentInstance;

    // Cached signature (loaded lazily)
    private volatile FunctionSignature cachedSignature;

    // Cached fast path for optimized invocation dispatch
    private volatile FastPath fastPath;

    /**
     * Creates a new JNI WebAssembly function wrapper.
     * 
     * @param nativeHandle the native function handle, must not be 0
     * @param functionName the name of the function, must not be null
     * @param parentInstance the parent instance, must not be null
     */
    public JniWebAssemblyFunction(final long nativeHandle, final String functionName, 
                                  final JniWebAssemblyInstance parentInstance) {
        if (nativeHandle == 0L) {
            throw new IllegalArgumentException("Native function handle cannot be 0");
        }
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null");
        }
        if (parentInstance == null) {
            throw new IllegalArgumentException("Parent instance cannot be null");
        }
        
        this.nativeHandle = nativeHandle;
        this.functionName = functionName;
        this.parentInstance = parentInstance;
        
        LOGGER.fine("Created JNI WebAssembly function '" + functionName + "' with handle: " + nativeHandle);
    }

    @Override
    public Object invoke(final Object... args) throws WasmRuntimeException {
        // Defensive programming - validate state
        if (!isValid()) {
            throw new IllegalStateException("Function is no longer valid - parent instance has been closed");
        }

        try {
            return invokeFastPath(args);
        } catch (final WasmRuntimeException e) {
            throw e; // Re-throw WebAssembly exceptions as-is
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error invoking function: " + functionName, e);
        }
    }

    /**
     * Dispatches to a typed native fast path when the function signature matches
     * a known pattern, bypassing Object[] marshalling overhead entirely.
     * Falls back to the generic path for unrecognized signatures.
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private Object invokeFastPath(final Object... args) throws WasmRuntimeException {
        final FastPath path = resolveFastPath();
        final long handle = nativeHandle;

        switch (path) {
            case V_V:
                nativeInvoke_V(handle);
                return null;
            case V_I:
                return nativeInvoke_I(handle);
            case I_V:
                nativeInvokeI_V(handle, ((Number) args[0]).intValue());
                return null;
            case I_I:
                return nativeInvokeI_I(handle, ((Number) args[0]).intValue());
            case II_V:
                nativeInvokeII_V(handle,
                        ((Number) args[0]).intValue(), ((Number) args[1]).intValue());
                return null;
            case II_I:
                return nativeInvokeII_I(handle,
                        ((Number) args[0]).intValue(), ((Number) args[1]).intValue());
            case III_I:
                return nativeInvokeIII_I(handle,
                        ((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
                        ((Number) args[2]).intValue());
            case J_J:
                return nativeInvokeJ_J(handle, ((Number) args[0]).longValue());
            case JJ_J:
                return nativeInvokeJJ_J(handle,
                        ((Number) args[0]).longValue(), ((Number) args[1]).longValue());
            case DD_D:
                return nativeInvokeDD_D(handle,
                        ((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue());
            default:
                return nativeInvokeFunction(handle, args);
        }
    }

    /**
     * Resolves and caches the fast path enum for this function's signature.
     */
    private FastPath resolveFastPath() {
        FastPath path = fastPath;
        if (path == null) {
            path = FastPath.resolve(getSignature());
            fastPath = path;
        }
        return path;
    }

    @Override
    public FunctionSignature getSignature() {
        if (!isValid()) {
            throw new IllegalStateException("Function is no longer valid - parent instance has been closed");
        }
        
        FunctionSignature signature = cachedSignature;
        if (signature == null) {
            synchronized (this) {
                signature = cachedSignature;
                if (signature == null) {
                    try {
                        signature = nativeGetFunctionSignature(nativeHandle);
                        if (signature == null) {
                            throw new IllegalStateException("Failed to get function signature");
                        }
                        cachedSignature = signature;
                    } catch (final Exception e) {
                        throw new IllegalStateException("Failed to get function signature for: " + functionName, e);
                    }
                }
            }
        }
        return signature;
    }

    @Override
    public String getName() {
        return functionName;
    }

    @Override
    public boolean isValid() {
        return nativeHandle != 0L && parentInstance.isValid();
    }

    @Override
    public Object[] invokeMultiple(final Object[]... argSets) throws WasmRuntimeException {
        if (argSets == null) {
            throw new IllegalArgumentException("Argument sets array cannot be null");
        }
        if (!isValid()) {
            throw new IllegalStateException("Function is no longer valid - parent instance has been closed");
        }
        if (argSets.length == 0) {
            return new Object[0];
        }

        try {
            return nativeInvokeMultiple(nativeHandle, argSets);
        } catch (final WasmRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new WasmRuntimeException("Unexpected error in batch invoke: " + functionName, e);
        }
    }

    /**
     * Destroys the native function handle, freeing the Rust Box wrapper.
     * Called by the parent instance during cleanup.
     */
    void close() {
        final long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L) {
            try {
                nativeDestroyFunction(handle);
            } catch (final Exception e) {
                LOGGER.warning("Error destroying native function '" + functionName + "': " + e.getMessage());
            }
        }
    }

    // Native method declarations

    /**
     * Destroys a native WebAssembly function handle.
     *
     * @param functionHandle the native function handle
     */
    private static native void nativeDestroyFunction(long functionHandle);

    /**
     * Invokes a WebAssembly function with the given arguments.
     *
     * @param functionHandle the native function handle
     * @param args the function arguments
     * @return the function result, or null for void functions
     * @throws WasmRuntimeException if execution fails
     */
    private static native Object nativeInvokeFunction(long functionHandle, Object[] args) throws WasmRuntimeException;

    /**
     * Gets the signature of a WebAssembly function.
     *
     * @param functionHandle the native function handle
     * @return the function signature
     */
    private static native FunctionSignature nativeGetFunctionSignature(long functionHandle);

    /**
     * Batch invokes a function multiple times with different argument sets in a single JNI crossing.
     *
     * @param functionHandle the native function handle
     * @param argSets array of argument arrays, one per invocation
     * @return array of results, one per invocation
     * @throws WasmRuntimeException if any invocation fails (fail-fast)
     */
    private static native Object[] nativeInvokeMultiple(long functionHandle, Object[][] argSets)
        throws WasmRuntimeException;

    // =========================================================================
    // Typed primitive fast-path native methods — 1 JNI crossing, 0 allocations
    // =========================================================================

    private static native void nativeInvoke_V(long functionHandle) throws WasmRuntimeException;

    private static native int nativeInvoke_I(long functionHandle) throws WasmRuntimeException;

    private static native void nativeInvokeI_V(long functionHandle,
            int arg0) throws WasmRuntimeException;

    private static native int nativeInvokeI_I(long functionHandle,
            int arg0) throws WasmRuntimeException;

    private static native void nativeInvokeII_V(long functionHandle,
            int arg0, int arg1) throws WasmRuntimeException;

    private static native int nativeInvokeII_I(long functionHandle,
            int arg0, int arg1) throws WasmRuntimeException;

    private static native int nativeInvokeIII_I(long functionHandle,
            int arg0, int arg1, int arg2) throws WasmRuntimeException;

    private static native long nativeInvokeJ_J(long functionHandle,
            long arg0) throws WasmRuntimeException;

    private static native long nativeInvokeJJ_J(long functionHandle,
            long arg0, long arg1) throws WasmRuntimeException;

    private static native double nativeInvokeDD_D(long functionHandle,
            double arg0, double arg1) throws WasmRuntimeException;

    // =========================================================================
    // Fast path signature matching
    // =========================================================================

    /**
     * Enum of recognized function signatures for typed fast-path dispatch.
     * Each variant corresponds to a typed native method that bypasses Object[]
     * marshalling, reducing JNI crossings from ~6 to 1 per call.
     */
    @SuppressWarnings("checkstyle:JavadocVariable")
    enum FastPath {
        V_V,     // () -> void
        V_I,     // () -> i32
        I_V,     // (i32) -> void
        I_I,     // (i32) -> i32
        II_V,    // (i32, i32) -> void
        II_I,    // (i32, i32) -> i32
        III_I,   // (i32, i32, i32) -> i32
        J_J,     // (i64) -> i64
        JJ_J,    // (i64, i64) -> i64
        DD_D,    // (f64, f64) -> f64
        GENERIC; // fallback to Object[] path

        /**
         * Resolves the fast path for a given function signature.
         *
         * @param signature the function signature
         * @return the matching fast path, or GENERIC if no fast path applies
         */
        @SuppressWarnings("checkstyle:CyclomaticComplexity")
        static FastPath resolve(final FunctionSignature signature) {
            final ValueType[] params = signature.getParameterTypes();
            final ValueType[] results = signature.getReturnTypes();
            final int pc = params.length;
            final int rc = results.length;

            if (rc == 0) {
                // void return
                if (pc == 0) {
                    return V_V;
                }
                if (pc == 1 && params[0] == ValueType.I32) {
                    return I_V;
                }
                if (pc == 2 && params[0] == ValueType.I32 && params[1] == ValueType.I32) {
                    return II_V;
                }
            } else if (rc == 1) {
                final ValueType rt = results[0];
                if (rt == ValueType.I32) {
                    if (pc == 0) {
                        return V_I;
                    }
                    if (pc == 1 && params[0] == ValueType.I32) {
                        return I_I;
                    }
                    if (pc == 2 && params[0] == ValueType.I32 && params[1] == ValueType.I32) {
                        return II_I;
                    }
                    if (pc == 3 && params[0] == ValueType.I32
                            && params[1] == ValueType.I32 && params[2] == ValueType.I32) {
                        return III_I;
                    }
                } else if (rt == ValueType.I64) {
                    if (pc == 1 && params[0] == ValueType.I64) {
                        return J_J;
                    }
                    if (pc == 2 && params[0] == ValueType.I64 && params[1] == ValueType.I64) {
                        return JJ_J;
                    }
                } else if (rt == ValueType.F64) {
                    if (pc == 2 && params[0] == ValueType.F64 && params[1] == ValueType.F64) {
                        return DD_D;
                    }
                }
            }

            return GENERIC;
        }
    }
}