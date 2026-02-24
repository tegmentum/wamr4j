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

package ai.tegmentum.wamr4j.test.framework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating simple WebAssembly modules programmatically.
 *
 * <p>This builder provides a Java API for constructing WebAssembly modules
 * in binary format. It is primarily intended for creating test modules
 * without requiring external WASM toolchains.
 *
 * <p><b>Limitations:</b> This builder supports only basic WebAssembly features
 * needed for testing. For complex modules, use external tools like wat2wasm.
 *
 * @since 1.0.0
 */
public final class WasmModuleBuilder {

    private static final byte[] WASM_MAGIC = {0x00, 0x61, 0x73, 0x6d}; // "\0asm"
    private static final byte[] WASM_VERSION = {0x01, 0x00, 0x00, 0x00}; // version 1

    private final List<FunctionType> types;
    private final List<Import> imports;
    private final List<Integer> functions;
    private final List<Export> exports;
    private final List<byte[]> code;
    private final List<Global> globals;
    private Table table;
    private Memory memory;
    private final List<Element> elements;
    private final List<DataSegment> dataSegments;
    private Integer startFunctionIndex;

    /**
     * Creates a new WebAssembly module builder.
     */
    public WasmModuleBuilder() {
        this.types = new ArrayList<>();
        this.imports = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.exports = new ArrayList<>();
        this.code = new ArrayList<>();
        this.globals = new ArrayList<>();
        this.elements = new ArrayList<>();
        this.dataSegments = new ArrayList<>();
        this.startFunctionIndex = null;
    }

    /**
     * Adds a function type to the module.
     *
     * @param paramTypes the parameter types (using WASM value type codes)
     * @param resultTypes the result types (using WASM value type codes)
     * @return the type index
     */
    public int addType(final byte[] paramTypes, final byte[] resultTypes) {
        types.add(new FunctionType(paramTypes, resultTypes));
        return types.size() - 1;
    }

    /**
     * Adds an import to the module.
     *
     * <p>Import kinds:
     * - 0x00: Function
     * - 0x01: Table
     * - 0x02: Memory
     * - 0x03: Global
     *
     * @param moduleName the module name to import from (e.g., "env")
     * @param itemName the name of the item to import (e.g., "log")
     * @param kind the import kind (function=0x00, table=0x01, memory=0x02, global=0x03)
     * @param typeIndex the type index (for function imports) or descriptor index
     * @return the import index
     */
    public int addImport(final String moduleName, final String itemName,
                         final byte kind, final int typeIndex) {
        imports.add(new Import(moduleName, itemName, kind, typeIndex));
        return imports.size() - 1;
    }

    /**
     * Adds a function with the specified type.
     *
     * @param typeIndex the type index
     * @return the function index
     */
    public int addFunction(final int typeIndex) {
        functions.add(typeIndex);
        return functions.size() - 1;
    }

    /**
     * Adds a memory to the module.
     *
     * @param initialPages the initial memory size in pages (64KiB per page)
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addMemory(final int initialPages) {
        this.memory = new Memory(initialPages, null);
        return this;
    }

    /**
     * Adds a memory to the module with a maximum size.
     *
     * @param initialPages the initial memory size in pages (64KiB per page)
     * @param maxPages the maximum memory size in pages
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addMemory(final int initialPages, final int maxPages) {
        this.memory = new Memory(initialPages, maxPages);
        return this;
    }

    /**
     * Adds a table to the module (for indirect calls).
     *
     * @param initialSize the initial table size (number of elements)
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addTable(final int initialSize) {
        this.table = new Table(initialSize, null);
        return this;
    }

    /**
     * Adds a table to the module with a maximum size.
     *
     * @param initialSize the initial table size (number of elements)
     * @param maxSize the maximum table size
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addTable(final int initialSize, final int maxSize) {
        this.table = new Table(initialSize, maxSize);
        return this;
    }

    /**
     * Adds an element segment to initialize table entries with function references.
     *
     * @param tableIndex the table index (always 0 in MVP)
     * @param offset the offset in the table (constant expression)
     * @param functionIndices the function indices to place in the table
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addTableElement(final int tableIndex, final int offset,
                                             final int[] functionIndices) {
        this.elements.add(new Element(tableIndex, offset, functionIndices));
        return this;
    }

    /**
     * Adds a data segment to initialize memory with binary data.
     *
     * <p>Data segments are active by default and initialize memory at instantiation time.
     * The data is written to memory starting at the specified offset.
     *
     * @param memoryIndex the memory index (always 0 in MVP)
     * @param offset the offset in memory (constant i32 expression)
     * @param data the binary data to write to memory
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addDataSegment(final int memoryIndex, final int offset, final byte[] data) {
        this.dataSegments.add(new DataSegment(memoryIndex, offset, data));
        return this;
    }

    /**
     * Sets the start function for the module.
     *
     * <p>The start function is automatically executed when the module is instantiated.
     * It must have no parameters and no return values (signature: [] → []).
     * Useful for module initialization logic like setting up memory or globals.
     *
     * @param functionIndex the index of the function to execute on start
     * @return this builder for method chaining
     */
    public WasmModuleBuilder setStartFunction(final int functionIndex) {
        this.startFunctionIndex = functionIndex;
        return this;
    }

    /**
     * Adds a global variable to the module.
     *
     * @param type the value type (I32, I64, F32, F64)
     * @param mutable whether the global is mutable
     * @param initValue the initial value
     * @return the global index
     */
    public int addGlobal(final byte type, final boolean mutable, final long initValue) {
        globals.add(new Global(type, mutable, initValue));
        return globals.size() - 1;
    }

    /**
     * Adds an export for a function.
     *
     * @param name the export name
     * @param functionIndex the function index
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addExport(final String name, final int functionIndex) {
        exports.add(new Export(name, (byte) 0x00, functionIndex));
        return this;
    }

    /**
     * Adds an export for any kind of WebAssembly item.
     *
     * <p>Export kinds:
     * - 0x00: Function
     * - 0x01: Table
     * - 0x02: Memory
     * - 0x03: Global
     *
     * @param name the export name
     * @param kind the export kind (function=0x00, table=0x01, memory=0x02, global=0x03)
     * @param index the item index
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addExport(final String name, final byte kind, final int index) {
        exports.add(new Export(name, kind, index));
        return this;
    }

    /**
     * Adds function code.
     *
     * @param locals the local variable types
     * @param body the function body bytecode
     * @return this builder for method chaining
     */
    public WasmModuleBuilder addCode(final byte[] locals, final byte[] body) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // Write locals count (simplified: assume no locals or single local group)
            if (locals.length == 0) {
                baos.write(0x00); // 0 local groups
            } else {
                baos.write(0x01); // 1 local group
                writeUnsignedLEB128(baos, locals.length);
                baos.write(locals);
            }

            // Write body
            baos.write(body);

            // Write end opcode
            baos.write(0x0b);

            code.add(baos.toByteArray());
        } catch (final IOException e) {
            throw new RuntimeException("Failed to build function code", e);
        }
        return this;
    }

    /**
     * Builds the WebAssembly module.
     *
     * @return the module bytecode
     */
    public byte[] build() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // Write magic and version
            baos.write(WASM_MAGIC);
            baos.write(WASM_VERSION);

            // Write type section (section 1)
            if (!types.isEmpty()) {
                writeTypeSection(baos);
            }

            // Write import section (section 2)
            if (!imports.isEmpty()) {
                writeImportSection(baos);
            }

            // Write function section (section 3)
            if (!functions.isEmpty()) {
                writeFunctionSection(baos);
            }

            // Write table section
            if (table != null) {
                writeTableSection(baos);
            }

            // Write memory section
            if (memory != null) {
                writeMemorySection(baos);
            }

            // Write global section
            if (!globals.isEmpty()) {
                writeGlobalSection(baos);
            }

            // Write export section
            if (!exports.isEmpty()) {
                writeExportSection(baos);
            }

            // Write start section
            if (startFunctionIndex != null) {
                writeStartSection(baos);
            }

            // Write element section
            if (!elements.isEmpty()) {
                writeElementSection(baos);
            }

            // Write code section
            if (!code.isEmpty()) {
                writeCodeSection(baos);
            }

            // Write data section
            if (!dataSegments.isEmpty()) {
                writeDataSection(baos);
            }

            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to build WASM module", e);
        }
    }

    private void writeTypeSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, types.size());

        for (final FunctionType type : types) {
            content.write(0x60); // func type
            writeUnsignedLEB128(content, type.paramTypes.length);
            content.write(type.paramTypes);
            writeUnsignedLEB128(content, type.resultTypes.length);
            content.write(type.resultTypes);
        }

        writeSection(baos, 1, content.toByteArray());
    }

    private void writeImportSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, imports.size());

        for (final Import imp : imports) {
            // Write module name
            final byte[] moduleNameBytes = imp.moduleName.getBytes(StandardCharsets.UTF_8);
            writeUnsignedLEB128(content, moduleNameBytes.length);
            content.write(moduleNameBytes);

            // Write item name
            final byte[] itemNameBytes = imp.itemName.getBytes(StandardCharsets.UTF_8);
            writeUnsignedLEB128(content, itemNameBytes.length);
            content.write(itemNameBytes);

            // Write import kind
            content.write(imp.kind);

            // Write type descriptor (for functions, this is the type index)
            if (imp.kind == IMPORT_FUNC) {
                writeUnsignedLEB128(content, imp.typeIndex);
            }
            // TODO: Add support for table, memory, global imports if needed
        }

        writeSection(baos, 2, content.toByteArray());
    }

    private void writeFunctionSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, functions.size());

        for (final Integer typeIndex : functions) {
            writeUnsignedLEB128(content, typeIndex);
        }

        writeSection(baos, 3, content.toByteArray());
    }

    private void writeTableSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, 1); // Number of tables (always 1 in MVP)

        // Write element type (0x70 = funcref)
        content.write(0x70);

        // Write limits
        if (table.maxSize != null) {
            content.write(0x01); // Has maximum
            writeUnsignedLEB128(content, table.initialSize);
            writeUnsignedLEB128(content, table.maxSize);
        } else {
            content.write(0x00); // No maximum
            writeUnsignedLEB128(content, table.initialSize);
        }

        writeSection(baos, 4, content.toByteArray());
    }

    private void writeMemorySection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, 1); // Number of memories (always 1)

        // Write limits
        if (memory.maxPages != null) {
            content.write(0x01); // Has maximum
            writeUnsignedLEB128(content, memory.initialPages);
            writeUnsignedLEB128(content, memory.maxPages);
        } else {
            content.write(0x00); // No maximum
            writeUnsignedLEB128(content, memory.initialPages);
        }

        writeSection(baos, 5, content.toByteArray());
    }

    private void writeGlobalSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, globals.size());

        for (final Global global : globals) {
            // Write value type
            content.write(global.type);

            // Write mutability (0x00 = immutable, 0x01 = mutable)
            content.write(global.mutable ? 0x01 : 0x00);

            // Write initializer expression based on type
            if (global.type == I32) {
                content.write(I32_CONST);
                writeSignedLEB128(content, (int) global.initValue);
            } else if (global.type == I64) {
                content.write(I64_CONST);
                writeSignedLEB128(content, global.initValue);
            } else if (global.type == F32) {
                content.write(F32_CONST);
                final int bits = Float.floatToRawIntBits((float) Double.longBitsToDouble(global.initValue));
                content.write(bits & 0xFF);
                content.write((bits >> 8) & 0xFF);
                content.write((bits >> 16) & 0xFF);
                content.write((bits >> 24) & 0xFF);
            } else if (global.type == F64) {
                content.write(F64_CONST);
                final long bits = global.initValue;
                for (int i = 0; i < 8; i++) {
                    content.write((int) ((bits >> (i * 8)) & 0xFF));
                }
            }

            // Write end of initializer expression
            content.write(END);
        }

        writeSection(baos, 6, content.toByteArray());
    }

    private void writeExportSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, exports.size());

        for (final Export export : exports) {
            final byte[] nameBytes = export.name.getBytes(StandardCharsets.UTF_8);
            writeUnsignedLEB128(content, nameBytes.length);
            content.write(nameBytes);
            content.write(export.kind);
            writeUnsignedLEB128(content, export.index);
        }

        writeSection(baos, 7, content.toByteArray());
    }

    private void writeStartSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, startFunctionIndex);
        writeSection(baos, 8, content.toByteArray());
    }

    private void writeElementSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, elements.size());

        for (final Element element : elements) {
            writeUnsignedLEB128(content, element.tableIndex);

            // Write offset expression (i32.const offset, end)
            content.write(WasmModuleBuilder.I32_CONST);
            writeSignedLEB128(content, element.offset);
            content.write(WasmModuleBuilder.END);

            // Write function indices
            writeUnsignedLEB128(content, element.functionIndices.length);
            for (final int funcIndex : element.functionIndices) {
                writeUnsignedLEB128(content, funcIndex);
            }
        }

        writeSection(baos, 9, content.toByteArray());
    }

    private void writeCodeSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, code.size());

        for (final byte[] funcCode : code) {
            writeUnsignedLEB128(content, funcCode.length);
            content.write(funcCode);
        }

        writeSection(baos, 10, content.toByteArray());
    }

    private void writeDataSection(final ByteArrayOutputStream baos) throws IOException {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeUnsignedLEB128(content, dataSegments.size());

        for (final DataSegment segment : dataSegments) {
            // Write memory index (0 for active data segment with explicit index)
            writeUnsignedLEB128(content, segment.memoryIndex);

            // Write offset expression (i32.const <offset> end)
            content.write(I32_CONST);
            writeSignedLEB128(content, segment.offset);
            content.write(END);

            // Write data size and bytes
            writeUnsignedLEB128(content, segment.data.length);
            content.write(segment.data);
        }

        writeSection(baos, 11, content.toByteArray());
    }

    private void writeSection(final ByteArrayOutputStream baos, final int sectionId, final byte[] content) throws IOException {
        baos.write(sectionId);
        writeUnsignedLEB128(baos, content.length);
        baos.write(content);
    }

    private void writeUnsignedLEB128(final ByteArrayOutputStream baos, final int value) throws IOException {
        int remaining = value;
        do {
            byte b = (byte) (remaining & 0x7f);
            remaining >>>= 7;
            if (remaining != 0) {
                b |= (byte) 0x80;
            }
            baos.write(b);
        } while (remaining != 0);
    }

    private void writeSignedLEB128(final ByteArrayOutputStream baos, final int value) throws IOException {
        int remaining = value;
        boolean more = true;
        while (more) {
            byte b = (byte) (remaining & 0x7f);
            remaining >>= 7;
            if ((remaining == 0 && (b & 0x40) == 0) || (remaining == -1 && (b & 0x40) != 0)) {
                more = false;
            } else {
                b |= (byte) 0x80;
            }
            baos.write(b);
        }
    }

    private void writeSignedLEB128(final ByteArrayOutputStream baos, final long value) throws IOException {
        long remaining = value;
        boolean more = true;
        while (more) {
            byte b = (byte) (remaining & 0x7f);
            remaining >>= 7;
            if ((remaining == 0 && (b & 0x40) == 0) || (remaining == -1 && (b & 0x40) != 0)) {
                more = false;
            } else {
                b |= (byte) 0x80;
            }
            baos.write(b);
        }
    }

    private static final class FunctionType {
        final byte[] paramTypes;
        final byte[] resultTypes;

        FunctionType(final byte[] paramTypes, final byte[] resultTypes) {
            this.paramTypes = paramTypes;
            this.resultTypes = resultTypes;
        }
    }

    private static final class Export {
        final String name;
        final byte kind;
        final int index;

        Export(final String name, final byte kind, final int index) {
            this.name = name;
            this.kind = kind;
            this.index = index;
        }
    }

    private static final class Table {
        final int initialSize;
        final Integer maxSize;

        Table(final int initialSize, final Integer maxSize) {
            this.initialSize = initialSize;
            this.maxSize = maxSize;
        }
    }

    private static final class Memory {
        final int initialPages;
        final Integer maxPages;

        Memory(final int initialPages, final Integer maxPages) {
            this.initialPages = initialPages;
            this.maxPages = maxPages;
        }
    }

    private static final class Global {
        final byte type;
        final boolean mutable;
        final long initValue;

        Global(final byte type, final boolean mutable, final long initValue) {
            this.type = type;
            this.mutable = mutable;
            this.initValue = initValue;
        }
    }

    private static final class Element {
        final int tableIndex;
        final int offset;
        final int[] functionIndices;

        Element(final int tableIndex, final int offset, final int[] functionIndices) {
            this.tableIndex = tableIndex;
            this.offset = offset;
            this.functionIndices = functionIndices;
        }
    }

    private static final class DataSegment {
        final int memoryIndex;
        final int offset;
        final byte[] data;

        DataSegment(final int memoryIndex, final int offset, final byte[] data) {
            this.memoryIndex = memoryIndex;
            this.offset = offset;
            this.data = data;
        }
    }

    private static final class Import {
        final String moduleName;
        final String itemName;
        final byte kind;
        final int typeIndex;

        Import(final String moduleName, final String itemName, final byte kind, final int typeIndex) {
            this.moduleName = moduleName;
            this.itemName = itemName;
            this.kind = kind;
            this.typeIndex = typeIndex;
        }
    }

    // Common WASM value types
    public static final byte I32 = 0x7f;
    public static final byte I64 = 0x7e;
    public static final byte F32 = 0x7d;
    public static final byte F64 = 0x7c;
    public static final byte VOID_TYPE = 0x40;

    // Import/Export kinds
    public static final byte IMPORT_FUNC = 0x00;
    public static final byte IMPORT_TABLE = 0x01;
    public static final byte IMPORT_MEM = 0x02;
    public static final byte IMPORT_GLOBAL = 0x03;

    // Control flow opcodes
    public static final byte UNREACHABLE = 0x00;
    public static final byte NOP = 0x01;
    public static final byte BLOCK = 0x02;
    public static final byte LOOP = 0x03;
    public static final byte IF = 0x04;
    public static final byte ELSE = 0x05;
    public static final byte END = 0x0b;
    public static final byte BR = 0x0c;
    public static final byte BR_IF = 0x0d;
    public static final byte BR_TABLE = 0x0e;
    public static final byte RETURN = 0x0f;
    public static final byte CALL = 0x10;
    public static final byte CALL_INDIRECT = 0x11;

    // Stack opcodes
    public static final byte DROP = 0x1a;
    public static final byte SELECT = 0x1b;

    // Local variable opcodes
    public static final byte LOCAL_GET = 0x20;
    public static final byte LOCAL_SET = 0x21;
    public static final byte LOCAL_TEE = 0x22;

    // Global variable opcodes
    public static final byte GLOBAL_GET = 0x23;
    public static final byte GLOBAL_SET = 0x24;

    // Constant opcodes
    public static final byte I32_CONST = 0x41;
    public static final byte I64_CONST = 0x42;
    public static final byte F32_CONST = 0x43;
    public static final byte F64_CONST = 0x44;

    // Memory opcodes
    public static final byte MEMORY_SIZE = 0x3f;
    public static final byte MEMORY_GROW = 0x40;

    // i32 memory load operations
    public static final byte I32_LOAD = 0x28;
    public static final byte I32_LOAD8_S = 0x2c;
    public static final byte I32_LOAD8_U = 0x2d;
    public static final byte I32_LOAD16_S = 0x2e;
    public static final byte I32_LOAD16_U = 0x2f;

    // i64 memory load operations
    public static final byte I64_LOAD = 0x29;
    public static final byte I64_LOAD8_S = 0x30;
    public static final byte I64_LOAD8_U = 0x31;
    public static final byte I64_LOAD16_S = 0x32;
    public static final byte I64_LOAD16_U = 0x33;
    public static final byte I64_LOAD32_S = 0x34;
    public static final byte I64_LOAD32_U = 0x35;

    // f32 memory load operations
    public static final byte F32_LOAD = 0x2a;

    // f64 memory load operations
    public static final byte F64_LOAD = 0x2b;

    // i32 memory store operations
    public static final byte I32_STORE = 0x36;
    public static final byte I32_STORE8 = 0x3a;
    public static final byte I32_STORE16 = 0x3b;

    // i64 memory store operations
    public static final byte I64_STORE = 0x37;
    public static final byte I64_STORE8 = 0x3c;
    public static final byte I64_STORE16 = 0x3d;
    public static final byte I64_STORE32 = 0x3e;

    // f32 memory store operations
    public static final byte F32_STORE = 0x38;

    // f64 memory store operations
    public static final byte F64_STORE = 0x39;

    // i32 test operations
    public static final byte I32_EQZ = 0x45;

    // i32 comparison operations
    public static final byte I32_EQ = 0x46;
    public static final byte I32_NE = 0x47;
    public static final byte I32_LT_S = 0x48;
    public static final byte I32_LT_U = 0x49;
    public static final byte I32_GT_S = 0x4a;
    public static final byte I32_GT_U = 0x4b;
    public static final byte I32_LE_S = 0x4c;
    public static final byte I32_LE_U = 0x4d;
    public static final byte I32_GE_S = 0x4e;
    public static final byte I32_GE_U = 0x4f;

    // i32 unary operations
    public static final byte I32_CLZ = 0x67;
    public static final byte I32_CTZ = 0x68;
    public static final byte I32_POPCNT = 0x69;

    // i32 arithmetic operations
    public static final byte I32_ADD = 0x6a;
    public static final byte I32_SUB = 0x6b;
    public static final byte I32_MUL = 0x6c;
    public static final byte I32_DIV_S = 0x6d;
    public static final byte I32_DIV_U = 0x6e;
    public static final byte I32_REM_S = 0x6f;
    public static final byte I32_REM_U = 0x70;

    // i32 bitwise operations
    public static final byte I32_AND = 0x71;
    public static final byte I32_OR = 0x72;
    public static final byte I32_XOR = 0x73;
    public static final byte I32_SHL = 0x74;
    public static final byte I32_SHR_S = 0x75;
    public static final byte I32_SHR_U = 0x76;
    public static final byte I32_ROTL = 0x77;
    public static final byte I32_ROTR = 0x78;

    // i64 comparison operations
    public static final byte I64_EQZ = 0x50;
    public static final byte I64_EQ = 0x51;
    public static final byte I64_NE = 0x52;
    public static final byte I64_LT_S = 0x53;
    public static final byte I64_LT_U = 0x54;
    public static final byte I64_GT_S = 0x55;
    public static final byte I64_GT_U = 0x56;
    public static final byte I64_LE_S = 0x57;
    public static final byte I64_LE_U = 0x58;
    public static final byte I64_GE_S = 0x59;
    public static final byte I64_GE_U = 0x5a;

    // i64 unary operations
    public static final byte I64_CLZ = 0x79;
    public static final byte I64_CTZ = 0x7a;
    public static final byte I64_POPCNT = 0x7b;

    // i64 arithmetic operations
    public static final byte I64_ADD = 0x7c;
    public static final byte I64_SUB = 0x7d;
    public static final byte I64_MUL = 0x7e;
    public static final byte I64_DIV_S = 0x7f;
    public static final byte I64_DIV_U = (byte) 0x80;
    public static final byte I64_REM_S = (byte) 0x81;
    public static final byte I64_REM_U = (byte) 0x82;

    // i64 bitwise operations
    public static final byte I64_AND = (byte) 0x83;
    public static final byte I64_OR = (byte) 0x84;
    public static final byte I64_XOR = (byte) 0x85;
    public static final byte I64_SHL = (byte) 0x86;
    public static final byte I64_SHR_S = (byte) 0x87;
    public static final byte I64_SHR_U = (byte) 0x88;
    public static final byte I64_ROTL = (byte) 0x89;
    public static final byte I64_ROTR = (byte) 0x8a;

    // f32 comparison operations
    public static final byte F32_EQ = 0x5b;
    public static final byte F32_NE = 0x5c;
    public static final byte F32_LT = 0x5d;
    public static final byte F32_GT = 0x5e;
    public static final byte F32_LE = 0x5f;
    public static final byte F32_GE = 0x60;

    // f32 unary operations
    public static final byte F32_ABS = (byte) 0x8b;
    public static final byte F32_NEG = (byte) 0x8c;
    public static final byte F32_CEIL = (byte) 0x8d;
    public static final byte F32_FLOOR = (byte) 0x8e;
    public static final byte F32_TRUNC = (byte) 0x8f;
    public static final byte F32_NEAREST = (byte) 0x90;
    public static final byte F32_SQRT = (byte) 0x91;

    // f32 binary operations
    public static final byte F32_ADD = (byte) 0x92;
    public static final byte F32_SUB = (byte) 0x93;
    public static final byte F32_MUL = (byte) 0x94;
    public static final byte F32_DIV = (byte) 0x95;
    public static final byte F32_MIN = (byte) 0x96;
    public static final byte F32_MAX = (byte) 0x97;
    public static final byte F32_COPYSIGN = (byte) 0x98;

    // f64 comparison operations
    public static final byte F64_EQ = 0x61;
    public static final byte F64_NE = 0x62;
    public static final byte F64_LT = 0x63;
    public static final byte F64_GT = 0x64;
    public static final byte F64_LE = 0x65;
    public static final byte F64_GE = 0x66;

    // f64 unary operations
    public static final byte F64_ABS = (byte) 0x99;
    public static final byte F64_NEG = (byte) 0x9a;
    public static final byte F64_CEIL = (byte) 0x9b;
    public static final byte F64_FLOOR = (byte) 0x9c;
    public static final byte F64_TRUNC = (byte) 0x9d;
    public static final byte F64_NEAREST = (byte) 0x9e;
    public static final byte F64_SQRT = (byte) 0x9f;

    // f64 binary operations
    public static final byte F64_ADD = (byte) 0xa0;
    public static final byte F64_SUB = (byte) 0xa1;
    public static final byte F64_MUL = (byte) 0xa2;
    public static final byte F64_DIV = (byte) 0xa3;
    public static final byte F64_MIN = (byte) 0xa4;
    public static final byte F64_MAX = (byte) 0xa5;
    public static final byte F64_COPYSIGN = (byte) 0xa6;

    // Type conversion operations
    public static final byte I32_WRAP_I64 = (byte) 0xa7;
    public static final byte I32_TRUNC_F32_S = (byte) 0xa8;
    public static final byte I32_TRUNC_F32_U = (byte) 0xa9;
    public static final byte I32_TRUNC_F64_S = (byte) 0xaa;
    public static final byte I32_TRUNC_F64_U = (byte) 0xab;
    public static final byte I64_EXTEND_I32_S = (byte) 0xac;
    public static final byte I64_EXTEND_I32_U = (byte) 0xad;
    public static final byte I64_TRUNC_F32_S = (byte) 0xae;
    public static final byte I64_TRUNC_F32_U = (byte) 0xaf;
    public static final byte I64_TRUNC_F64_S = (byte) 0xb0;
    public static final byte I64_TRUNC_F64_U = (byte) 0xb1;
    public static final byte F32_CONVERT_I32_S = (byte) 0xb2;
    public static final byte F32_CONVERT_I32_U = (byte) 0xb3;
    public static final byte F32_CONVERT_I64_S = (byte) 0xb4;
    public static final byte F32_CONVERT_I64_U = (byte) 0xb5;
    public static final byte F32_DEMOTE_F64 = (byte) 0xb6;
    public static final byte F64_CONVERT_I32_S = (byte) 0xb7;
    public static final byte F64_CONVERT_I32_U = (byte) 0xb8;
    public static final byte F64_CONVERT_I64_S = (byte) 0xb9;
    public static final byte F64_CONVERT_I64_U = (byte) 0xba;
    public static final byte F64_PROMOTE_F32 = (byte) 0xbb;
    public static final byte I32_REINTERPRET_F32 = (byte) 0xbc;
    public static final byte I64_REINTERPRET_F64 = (byte) 0xbd;
    public static final byte F32_REINTERPRET_I32 = (byte) 0xbe;
    public static final byte F64_REINTERPRET_I64 = (byte) 0xbf;

    /**
     * Creates a module with a single binary operation: (T, T) -> T.
     *
     * @param exportName the exported function name
     * @param opcode the WASM binary opcode
     * @param valueType the WASM value type for both parameters and result
     * @return the module bytecode
     */
    public static byte[] createBinaryOpModule(final String exportName, final byte opcode,
                                              final byte valueType) {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        final int typeIndex = builder.addType(new byte[]{valueType, valueType}, new byte[]{valueType});
        final int funcIndex = builder.addFunction(typeIndex);
        builder.addExport(exportName, funcIndex);
        builder.addCode(new byte[]{}, new byte[]{LOCAL_GET, 0x00, LOCAL_GET, 0x01, opcode});
        return builder.build();
    }

    /**
     * Creates a module with a single unary operation: (T) -> T.
     *
     * @param exportName the exported function name
     * @param opcode the WASM unary opcode
     * @param valueType the WASM value type for the parameter and result
     * @return the module bytecode
     */
    public static byte[] createUnaryOpModule(final String exportName, final byte opcode,
                                             final byte valueType) {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        final int typeIndex = builder.addType(new byte[]{valueType}, new byte[]{valueType});
        final int funcIndex = builder.addFunction(typeIndex);
        builder.addExport(exportName, funcIndex);
        builder.addCode(new byte[]{}, new byte[]{LOCAL_GET, 0x00, opcode});
        return builder.build();
    }

    // --- i32 binary operation modules ---

    /** @return module: (i32, i32) -> i32 exporting "add" */
    public static byte[] createI32AddModule() { return createBinaryOpModule("add", I32_ADD, I32); }

    /** @return module: (i32, i32) -> i32 exporting "sub" */
    public static byte[] createI32SubModule() { return createBinaryOpModule("sub", I32_SUB, I32); }

    /** @return module: (i32, i32) -> i32 exporting "mul" */
    public static byte[] createI32MulModule() { return createBinaryOpModule("mul", I32_MUL, I32); }

    /** @return module: (i32, i32) -> i32 exporting "div_s" */
    public static byte[] createI32DivSModule() { return createBinaryOpModule("div_s", I32_DIV_S, I32); }

    /** @return module: (i32, i32) -> i32 exporting "div_u" */
    public static byte[] createI32DivUModule() { return createBinaryOpModule("div_u", I32_DIV_U, I32); }

    /** @return module: (i32, i32) -> i32 exporting "rem_s" */
    public static byte[] createI32RemSModule() { return createBinaryOpModule("rem_s", I32_REM_S, I32); }

    /** @return module: (i32, i32) -> i32 exporting "rem_u" */
    public static byte[] createI32RemUModule() { return createBinaryOpModule("rem_u", I32_REM_U, I32); }

    /** @return module: (i32, i32) -> i32 exporting "and" */
    public static byte[] createI32AndModule() { return createBinaryOpModule("and", I32_AND, I32); }

    /** @return module: (i32, i32) -> i32 exporting "or" */
    public static byte[] createI32OrModule() { return createBinaryOpModule("or", I32_OR, I32); }

    /** @return module: (i32, i32) -> i32 exporting "xor" */
    public static byte[] createI32XorModule() { return createBinaryOpModule("xor", I32_XOR, I32); }

    /** @return module: (i32, i32) -> i32 exporting "shl" */
    public static byte[] createI32ShlModule() { return createBinaryOpModule("shl", I32_SHL, I32); }

    /** @return module: (i32, i32) -> i32 exporting "shr_s" */
    public static byte[] createI32ShrSModule() { return createBinaryOpModule("shr_s", I32_SHR_S, I32); }

    /** @return module: (i32, i32) -> i32 exporting "shr_u" */
    public static byte[] createI32ShrUModule() { return createBinaryOpModule("shr_u", I32_SHR_U, I32); }

    /**
     * Creates a module with multiple i32 operations exported.
     *
     * @return the module bytecode
     */
    public static byte[] createI32AllOpsModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        final int typeIndex = builder.addType(new byte[]{I32, I32}, new byte[]{I32});
        final String[] names = {"add", "sub", "mul", "div_s", "div_u", "rem_s", "rem_u",
            "and", "or", "xor", "shl", "shr_s", "shr_u"};
        final byte[] opcodes = {I32_ADD, I32_SUB, I32_MUL, I32_DIV_S, I32_DIV_U, I32_REM_S,
            I32_REM_U, I32_AND, I32_OR, I32_XOR, I32_SHL, I32_SHR_S, I32_SHR_U};
        for (int i = 0; i < names.length; i++) {
            builder.addFunction(typeIndex);
            builder.addExport(names[i], i);
            builder.addCode(new byte[]{}, new byte[]{LOCAL_GET, 0x00, LOCAL_GET, 0x01, opcodes[i]});
        }
        return builder.build();
    }

    // --- i64 binary operation modules ---

    /** @return module: (i64, i64) -> i64 exporting "add" */
    public static byte[] createI64AddModule() { return createBinaryOpModule("add", I64_ADD, I64); }

    /** @return module: (i64, i64) -> i64 exporting "sub" */
    public static byte[] createI64SubModule() { return createBinaryOpModule("sub", I64_SUB, I64); }

    /** @return module: (i64, i64) -> i64 exporting "mul" */
    public static byte[] createI64MulModule() { return createBinaryOpModule("mul", I64_MUL, I64); }

    /** @return module: (i64, i64) -> i64 exporting "div_s" */
    public static byte[] createI64DivSModule() { return createBinaryOpModule("div_s", I64_DIV_S, I64); }

    /** @return module: (i64, i64) -> i64 exporting "div_u" */
    public static byte[] createI64DivUModule() { return createBinaryOpModule("div_u", I64_DIV_U, I64); }

    /** @return module: (i64, i64) -> i64 exporting "rem_s" */
    public static byte[] createI64RemSModule() { return createBinaryOpModule("rem_s", I64_REM_S, I64); }

    /** @return module: (i64, i64) -> i64 exporting "rem_u" */
    public static byte[] createI64RemUModule() { return createBinaryOpModule("rem_u", I64_REM_U, I64); }

    /** @return module: (i64, i64) -> i64 exporting "and" */
    public static byte[] createI64AndModule() { return createBinaryOpModule("and", I64_AND, I64); }

    /** @return module: (i64, i64) -> i64 exporting "or" */
    public static byte[] createI64OrModule() { return createBinaryOpModule("or", I64_OR, I64); }

    /** @return module: (i64, i64) -> i64 exporting "xor" */
    public static byte[] createI64XorModule() { return createBinaryOpModule("xor", I64_XOR, I64); }

    /** @return module: (i64, i64) -> i64 exporting "shl" */
    public static byte[] createI64ShlModule() { return createBinaryOpModule("shl", I64_SHL, I64); }

    /** @return module: (i64, i64) -> i64 exporting "shr_s" */
    public static byte[] createI64ShrSModule() { return createBinaryOpModule("shr_s", I64_SHR_S, I64); }

    /** @return module: (i64, i64) -> i64 exporting "shr_u" */
    public static byte[] createI64ShrUModule() { return createBinaryOpModule("shr_u", I64_SHR_U, I64); }

    // --- f32 binary operation modules ---

    /** @return module: (f32, f32) -> f32 exporting "add" */
    public static byte[] createF32AddModule() { return createBinaryOpModule("add", F32_ADD, F32); }

    /** @return module: (f32, f32) -> f32 exporting "sub" */
    public static byte[] createF32SubModule() { return createBinaryOpModule("sub", F32_SUB, F32); }

    /** @return module: (f32, f32) -> f32 exporting "mul" */
    public static byte[] createF32MulModule() { return createBinaryOpModule("mul", F32_MUL, F32); }

    /** @return module: (f32, f32) -> f32 exporting "div" */
    public static byte[] createF32DivModule() { return createBinaryOpModule("div", F32_DIV, F32); }

    /** @return module: (f32, f32) -> f32 exporting "min" */
    public static byte[] createF32MinModule() { return createBinaryOpModule("min", F32_MIN, F32); }

    /** @return module: (f32, f32) -> f32 exporting "max" */
    public static byte[] createF32MaxModule() { return createBinaryOpModule("max", F32_MAX, F32); }

    // --- f32 unary operation modules ---

    /** @return module: (f32) -> f32 exporting "sqrt" */
    public static byte[] createF32SqrtModule() { return createUnaryOpModule("sqrt", F32_SQRT, F32); }

    /** @return module: (f32) -> f32 exporting "abs" */
    public static byte[] createF32AbsModule() { return createUnaryOpModule("abs", F32_ABS, F32); }

    /** @return module: (f32) -> f32 exporting "neg" */
    public static byte[] createF32NegModule() { return createUnaryOpModule("neg", F32_NEG, F32); }

    /** @return module: (f32) -> f32 exporting "ceil" */
    public static byte[] createF32CeilModule() { return createUnaryOpModule("ceil", F32_CEIL, F32); }

    /** @return module: (f32) -> f32 exporting "floor" */
    public static byte[] createF32FloorModule() { return createUnaryOpModule("floor", F32_FLOOR, F32); }

    // --- f64 binary operation modules ---

    /** @return module: (f64, f64) -> f64 exporting "add" */
    public static byte[] createF64AddModule() { return createBinaryOpModule("add", F64_ADD, F64); }

    /** @return module: (f64, f64) -> f64 exporting "sub" */
    public static byte[] createF64SubModule() { return createBinaryOpModule("sub", F64_SUB, F64); }

    /** @return module: (f64, f64) -> f64 exporting "mul" */
    public static byte[] createF64MulModule() { return createBinaryOpModule("mul", F64_MUL, F64); }

    /** @return module: (f64, f64) -> f64 exporting "div" */
    public static byte[] createF64DivModule() { return createBinaryOpModule("div", F64_DIV, F64); }

    /** @return module: (f64, f64) -> f64 exporting "min" */
    public static byte[] createF64MinModule() { return createBinaryOpModule("min", F64_MIN, F64); }

    /** @return module: (f64, f64) -> f64 exporting "max" */
    public static byte[] createF64MaxModule() { return createBinaryOpModule("max", F64_MAX, F64); }

    // --- f64 unary operation modules ---

    /** @return module: (f64) -> f64 exporting "sqrt" */
    public static byte[] createF64SqrtModule() { return createUnaryOpModule("sqrt", F64_SQRT, F64); }

    /** @return module: (f64) -> f64 exporting "abs" */
    public static byte[] createF64AbsModule() { return createUnaryOpModule("abs", F64_ABS, F64); }

    /** @return module: (f64) -> f64 exporting "neg" */
    public static byte[] createF64NegModule() { return createUnaryOpModule("neg", F64_NEG, F64); }

    /** @return module: (f64) -> f64 exporting "ceil" */
    public static byte[] createF64CeilModule() { return createUnaryOpModule("ceil", F64_CEIL, F64); }

    /** @return module: (f64) -> f64 exporting "floor" */
    public static byte[] createF64FloorModule() { return createUnaryOpModule("floor", F64_FLOOR, F64); }
}
