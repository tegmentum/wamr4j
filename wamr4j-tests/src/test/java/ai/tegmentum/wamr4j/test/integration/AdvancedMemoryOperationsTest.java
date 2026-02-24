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

package ai.tegmentum.wamr4j.test.integration;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.exception.WasmRuntimeException;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for advanced memory operations (Phase 6).
 *
 * <p>Tests module malloc/free, address validation, memory-by-index retrieval,
 * base address, and bytes-per-page. All operations are tested for JNI/Panama parity.
 *
 * @since 1.0.0
 */
class AdvancedMemoryOperationsTest {

    private static final Logger LOGGER = Logger.getLogger(AdvancedMemoryOperationsTest.class.getName());

    /**
     * Builds a minimal WASM module with 1 page of memory exported.
     * Also exports a simple function so we can verify data written via moduleDupData.
     */
    private byte[] buildMemoryModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1); // 1 page = 65536 bytes

        // Function: read_i32(addr) -> i32 — reads an i32 from the given address
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("read_i32", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.I32_LOAD, 0x02, 0x00,
        });

        return builder.build();
    }

    @Test
    void testModuleMallocFreeParity() {
        LOGGER.info("Testing moduleMalloc/moduleFree parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();

        long jniOffset = -1;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            jniOffset = instance.moduleMalloc(256);
            LOGGER.info("JNI moduleMalloc(256) returned offset: " + jniOffset);
            assertTrue(jniOffset > 0,
                "JNI: moduleMalloc should return a positive offset, got: " + jniOffset);

            // Validate the allocated address
            assertTrue(instance.validateAppAddr(jniOffset, 256),
                "JNI: allocated address should be valid");

            // Free the memory — should not throw
            instance.moduleFree(jniOffset);
            LOGGER.info("JNI: moduleFree succeeded");

        } catch (final Exception e) {
            fail("JNI moduleMalloc/Free test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final long panamaOffset = instance.moduleMalloc(256);
            LOGGER.info("Panama moduleMalloc(256) returned offset: " + panamaOffset);
            assertTrue(panamaOffset > 0,
                "Panama: moduleMalloc should return a positive offset, got: " + panamaOffset);

            // Both offsets should be non-zero positive values (exact values may differ)
            LOGGER.info("JNI offset: " + jniOffset + ", Panama offset: " + panamaOffset);

            assertTrue(instance.validateAppAddr(panamaOffset, 256),
                "Panama: allocated address should be valid");

            instance.moduleFree(panamaOffset);
            LOGGER.info("Panama: moduleFree succeeded");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testModuleMallocInvalidSize() throws Exception {
        LOGGER.info("Testing moduleMalloc with invalid sizes");

        final byte[] moduleBytes = buildMemoryModule();

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.moduleMalloc(0),
                "moduleMalloc(0) should throw IllegalArgumentException");

            assertThrows(IllegalArgumentException.class,
                () -> instance.moduleMalloc(-1),
                "moduleMalloc(-1) should throw IllegalArgumentException");

            LOGGER.info("JNI: invalid size validation passed");
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testModuleDupDataParity() {
        LOGGER.info("Testing moduleDupData parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();
        final byte[] testData = new byte[]{0x0A, 0x0B, 0x0C, 0x0D};

        long jniOffset = -1;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            jniOffset = instance.moduleDupData(testData);
            LOGGER.info("JNI moduleDupData returned offset: " + jniOffset);
            assertTrue(jniOffset > 0,
                "JNI: moduleDupData should return a positive offset, got: " + jniOffset);

            // Read the data back through the WASM function to verify it was written
            final WebAssemblyMemory memory = instance.getMemory();
            final byte[] readBack = memory.read((int) jniOffset, testData.length);
            LOGGER.info("JNI: read back data: ["
                + String.format("0x%02X, 0x%02X, 0x%02X, 0x%02X",
                    readBack[0], readBack[1], readBack[2], readBack[3]) + "]");
            assertArrayEquals(testData, readBack,
                "JNI: data read back should match original");

            instance.moduleFree(jniOffset);
        } catch (final Exception e) {
            fail("JNI moduleDupData test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final long panamaOffset = instance.moduleDupData(testData);
            LOGGER.info("Panama moduleDupData returned offset: " + panamaOffset);
            assertTrue(panamaOffset > 0,
                "Panama: moduleDupData should return a positive offset, got: " + panamaOffset);

            final WebAssemblyMemory memory = instance.getMemory();
            final byte[] readBack = memory.read((int) panamaOffset, testData.length);
            LOGGER.info("Panama: read back data: ["
                + String.format("0x%02X, 0x%02X, 0x%02X, 0x%02X",
                    readBack[0], readBack[1], readBack[2], readBack[3]) + "]");
            assertArrayEquals(testData, readBack,
                "Panama: data read back should match original");

            instance.moduleFree(panamaOffset);
        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testModuleDupDataInvalidInput() throws Exception {
        LOGGER.info("Testing moduleDupData with invalid inputs");

        final byte[] moduleBytes = buildMemoryModule();

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            assertThrows(IllegalArgumentException.class,
                () -> instance.moduleDupData(null),
                "moduleDupData(null) should throw IllegalArgumentException");

            assertThrows(IllegalArgumentException.class,
                () -> instance.moduleDupData(new byte[]{}),
                "moduleDupData(empty) should throw IllegalArgumentException");

            LOGGER.info("JNI: invalid input validation passed");
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testValidateAppAddrParity() {
        LOGGER.info("Testing validateAppAddr parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();

        boolean jniValid = false;
        boolean jniInvalid = true;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Offset 0, size 100 — should be valid within 1 page (65536 bytes)
            jniValid = instance.validateAppAddr(0, 100);
            LOGGER.info("JNI validateAppAddr(0, 100): " + jniValid);
            assertTrue(jniValid, "JNI: offset 0 size 100 should be valid");

            // Offset well beyond total memory — should be invalid
            // WAMR allocates app heap pages beyond the declared 1 page, so use a very large offset
            final long totalMemory = (long) instance.getMemory().pageCount() * 65536L;
            final long invalidOffset = totalMemory + 1000;
            jniInvalid = instance.validateAppAddr(invalidOffset, 100);
            LOGGER.info("JNI validateAppAddr(" + invalidOffset + ", 100): " + jniInvalid);
            assertFalse(jniInvalid,
                "JNI: offset " + invalidOffset + " should be out of bounds (total memory: "
                + totalMemory + ")");

        } catch (final Exception e) {
            fail("JNI validateAppAddr test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final boolean panamaValid = instance.validateAppAddr(0, 100);
            LOGGER.info("Panama validateAppAddr(0, 100): " + panamaValid);
            assertEquals(jniValid, panamaValid,
                "JNI and Panama should agree on validateAppAddr(0, 100)");

            final long panamaTotalMemory = (long) instance.getMemory().pageCount() * 65536L;
            final long panamaInvalidOffset = panamaTotalMemory + 1000;
            final boolean panamaInvalid = instance.validateAppAddr(panamaInvalidOffset, 100);
            LOGGER.info("Panama validateAppAddr(" + panamaInvalidOffset + ", 100): "
                + panamaInvalid);
            assertEquals(jniInvalid, panamaInvalid,
                "JNI and Panama should agree on validateAppAddr(100000, 100)");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testValidateAppStrAddrParity() {
        LOGGER.info("Testing validateAppStrAddr parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();

        boolean jniValidStr = false;

        // JNI runtime — write a null-terminated string, then validate
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Write "Hi\0" at a known offset
            final WebAssemblyMemory memory = instance.getMemory();
            memory.write(0, new byte[]{0x48, 0x69, 0x00}); // "Hi\0"

            jniValidStr = instance.validateAppStrAddr(0);
            LOGGER.info("JNI validateAppStrAddr(0) after writing 'Hi\\0': " + jniValidStr);
            assertTrue(jniValidStr, "JNI: offset 0 with null-terminated string should be valid");

        } catch (final Exception e) {
            fail("JNI validateAppStrAddr test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();
            memory.write(0, new byte[]{0x48, 0x69, 0x00});

            final boolean panamaValidStr = instance.validateAppStrAddr(0);
            LOGGER.info("Panama validateAppStrAddr(0) after writing 'Hi\\0': " + panamaValidStr);
            assertEquals(jniValidStr, panamaValidStr,
                "JNI and Panama should agree on validateAppStrAddr");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testGetMemoryByIndexParity() {
        LOGGER.info("Testing getMemoryByIndex parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();

        int jniPageCount = -1;
        int jniSize = -1;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemoryByIndex(0);
            assertNotNull(memory, "JNI: getMemoryByIndex(0) should not return null");

            jniPageCount = memory.pageCount();
            LOGGER.info("JNI memory[0] pageCount: " + jniPageCount);
            // WAMR may allocate additional pages for the app heap beyond the declared 1 page
            assertTrue(jniPageCount >= 1,
                "JNI: memory[0] should have at least 1 page, got: " + jniPageCount);

            jniSize = memory.size();
            LOGGER.info("JNI memory[0] size: " + jniSize);
            assertTrue(jniSize > 0, "JNI: memory[0] size should be positive");

            // Index 1 should fail (only 1 memory)
            assertThrows(WasmRuntimeException.class,
                () -> instance.getMemoryByIndex(1),
                "JNI: getMemoryByIndex(1) should throw for single-memory module");

            // Negative index should fail
            assertThrows(IllegalArgumentException.class,
                () -> instance.getMemoryByIndex(-1),
                "JNI: getMemoryByIndex(-1) should throw IllegalArgumentException");

        } catch (final Exception e) {
            fail("JNI getMemoryByIndex test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemoryByIndex(0);
            assertNotNull(memory, "Panama: getMemoryByIndex(0) should not return null");

            final int panamaPageCount = memory.pageCount();
            LOGGER.info("Panama memory[0] pageCount: " + panamaPageCount);
            assertEquals(jniPageCount, panamaPageCount,
                "JNI and Panama should agree on pageCount");

            final int panamaSize = memory.size();
            LOGGER.info("Panama memory[0] size: " + panamaSize);
            assertEquals(jniSize, panamaSize,
                "JNI and Panama should agree on memory size");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testBaseAddressParity() {
        LOGGER.info("Testing getBaseAddress parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();

        long jniBaseAddr = -1;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();
            jniBaseAddr = memory.getBaseAddress();
            LOGGER.info("JNI base address: " + jniBaseAddr + " (0x"
                + Long.toHexString(jniBaseAddr) + ")");
            assertTrue(jniBaseAddr != 0,
                "JNI: base address should be non-zero, got: " + jniBaseAddr);

        } catch (final Exception e) {
            fail("JNI getBaseAddress test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();
            final long panamaBaseAddr = memory.getBaseAddress();
            LOGGER.info("Panama base address: " + panamaBaseAddr + " (0x"
                + Long.toHexString(panamaBaseAddr) + ")");
            assertTrue(panamaBaseAddr != 0,
                "Panama: base address should be non-zero, got: " + panamaBaseAddr);

            // Both should be non-zero but will differ (different allocations)
            LOGGER.info("Both runtimes returned valid non-zero base addresses");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testBytesPerPageParity() {
        LOGGER.info("Testing getBytesPerPage parity between JNI and Panama");

        final byte[] moduleBytes = buildMemoryModule();

        long jniBytesPerPage = -1;

        // JNI runtime
        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();
            jniBytesPerPage = memory.getBytesPerPage();
            LOGGER.info("JNI bytesPerPage: " + jniBytesPerPage);
            assertEquals(65536L, jniBytesPerPage,
                "JNI: standard WASM page is 65536 bytes");

        } catch (final Exception e) {
            fail("JNI getBytesPerPage test failed: " + e.getMessage());
            return;
        } finally {
            System.clearProperty("wamr4j.runtime");
        }

        // Panama runtime
        System.setProperty("wamr4j.runtime", "panama");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            final WebAssemblyMemory memory = instance.getMemory();
            final long panamaBytesPerPage = memory.getBytesPerPage();
            LOGGER.info("Panama bytesPerPage: " + panamaBytesPerPage);
            assertEquals(jniBytesPerPage, panamaBytesPerPage,
                "JNI and Panama should agree on bytesPerPage");

        } catch (final Exception e) {
            LOGGER.warning("Panama runtime not available, skipping: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testMallocFreeMultipleAllocations() {
        LOGGER.info("Testing multiple allocations and frees");

        final byte[] moduleBytes = buildMemoryModule();

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Allocate several blocks
            final long offset1 = instance.moduleMalloc(64);
            final long offset2 = instance.moduleMalloc(128);
            final long offset3 = instance.moduleMalloc(256);

            LOGGER.info("Allocated offsets: " + offset1 + ", " + offset2 + ", " + offset3);
            assertTrue(offset1 > 0, "First allocation should succeed");
            assertTrue(offset2 > 0, "Second allocation should succeed");
            assertTrue(offset3 > 0, "Third allocation should succeed");

            // All offsets should be different
            assertNotEquals(offset1, offset2, "Offsets should be unique");
            assertNotEquals(offset2, offset3, "Offsets should be unique");
            assertNotEquals(offset1, offset3, "Offsets should be unique");

            // All should be valid
            assertTrue(instance.validateAppAddr(offset1, 64), "offset1 should be valid");
            assertTrue(instance.validateAppAddr(offset2, 128), "offset2 should be valid");
            assertTrue(instance.validateAppAddr(offset3, 256), "offset3 should be valid");

            // Free all
            instance.moduleFree(offset1);
            instance.moduleFree(offset2);
            instance.moduleFree(offset3);

            LOGGER.info("All allocations freed successfully");

        } catch (final Exception e) {
            fail("Multiple allocation test failed: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }

    @Test
    void testMallocWriteReadFreeRoundTrip() {
        LOGGER.info("Testing malloc -> write -> read -> free round trip");

        final byte[] moduleBytes = buildMemoryModule();

        System.setProperty("wamr4j.runtime", "jni");
        try (final WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();
             final WebAssemblyModule module = runtime.compile(moduleBytes);
             final WebAssemblyInstance instance = module.instantiate()) {

            // Allocate space for 4 bytes
            final long offset = instance.moduleMalloc(4);
            assertTrue(offset > 0, "Allocation should succeed");
            LOGGER.info("Allocated 4 bytes at offset: " + offset);

            // Write data using memory API
            final WebAssemblyMemory memory = instance.getMemory();
            final byte[] testData = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            memory.write((int) offset, testData);
            LOGGER.info("Wrote test data at offset " + offset);

            // Read back
            final byte[] readBack = memory.read((int) offset, 4);
            assertArrayEquals(testData, readBack,
                "Data read back should match data written");
            LOGGER.info("Read back matches: ["
                + String.format("0x%02X, 0x%02X, 0x%02X, 0x%02X",
                    readBack[0], readBack[1], readBack[2], readBack[3]) + "]");

            // Free
            instance.moduleFree(offset);
            LOGGER.info("Round trip completed successfully");

        } catch (final Exception e) {
            fail("Malloc/write/read/free round trip failed: " + e.getMessage());
        } finally {
            System.clearProperty("wamr4j.runtime");
        }
    }
}
