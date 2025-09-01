/*
 * Copyright (c) 2024 Tegmentum AI, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.wamr4j.exception;

/**
 * Base exception class for all WebAssembly-related errors in WAMR4J.
 *
 * <p>This exception serves as the root of the WebAssembly exception hierarchy, providing common
 * functionality for error reporting and debugging. All WebAssembly-specific exceptions should
 * extend this class.
 *
 * @since 1.0.0
 */
public class WebAssemblyException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new WebAssembly exception with the specified detail message.
     *
     * @param message the detail message, saved for later retrieval by the {@link #getMessage()}
     *     method
     */
    public WebAssemblyException(final String message) {
        super(message);
    }

    /**
     * Constructs a new WebAssembly exception with the specified detail message and cause.
     *
     * @param message the detail message, saved for later retrieval by the {@link #getMessage()}
     *     method
     * @param cause the cause, saved for later retrieval by the {@link #getCause()} method (a null
     *     value is permitted, and indicates that the cause is nonexistent or unknown)
     */
    public WebAssemblyException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new WebAssembly exception with the specified cause and a detail message of
     * {@code (cause==null ? null : cause.toString())}.
     *
     * @param cause the cause, saved for later retrieval by the {@link #getCause()} method (a null
     *     value is permitted, and indicates that the cause is nonexistent or unknown)
     */
    public WebAssemblyException(final Throwable cause) {
        super(cause);
    }
}
