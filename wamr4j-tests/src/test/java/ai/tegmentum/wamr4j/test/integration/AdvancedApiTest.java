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
import ai.tegmentum.wamr4j.WamrInstanceExtensions;
import ai.tegmentum.wamr4j.WamrRuntimeExtensions;
import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.wamr4j.test.framework.WasmModuleBuilder;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phases 18-26: Memory Alloc Info, Context Keys,
 * InstantiateEx2, and JNI/Panama parity.
 *
 * @since 1.0.0
 */
class AdvancedApiTest {

    private static final Logger LOGGER = Logger.getLogger(AdvancedApiTest.class.getName());

    private byte[] buildSimpleModule() {
        final WasmModuleBuilder builder = new WasmModuleBuilder();
        builder.addMemory(1, 10);
        final int globalIdx = builder.addGlobal(WasmModuleBuilder.I32, true, 42);
        builder.addExport("memory", (byte) 0x02, 0);
        builder.addExport("counter", (byte) 0x03, globalIdx);
        final int type = builder.addType(
            new byte[]{WasmModuleBuilder.I32, WasmModuleBuilder.I32},
            new byte[]{WasmModuleBuilder.I32}
        );
        final int func = builder.addFunction(type);
        builder.addExport("add", func);
        builder.addCode(new byte[]{}, new byte[]{
            WasmModuleBuilder.LOCAL_GET, 0x00,
            WasmModuleBuilder.LOCAL_GET, 0x01,
            WasmModuleBuilder.I32_ADD,
        });
        return builder.build();
    }

    @Test
    void testGetMemAllocInfoParity() {
        LOGGER.info("Testing getMemAllocInfo on both runtimes");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                final int[] info = ext.getMemAllocInfo();
                LOGGER.info(runtime.toUpperCase() + ": getMemAllocInfo() = "
                    + (info != null ? "[" + info[0] + ", " + info[1] + ", " + info[2] + "]" : "null"));
            } catch (final Exception e) {
                LOGGER.info(runtime.toUpperCase() + ": getMemAllocInfo threw " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testContextKeyCreateDestroyParity() {
        LOGGER.info("Testing createContextKey/destroyContextKey on both runtimes");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                final long key = ext.createContextKey();
                LOGGER.info(runtime.toUpperCase() + ": createContextKey() = " + key);
                if (key != 0) {
                    ext.destroyContextKey(key);
                    LOGGER.info(runtime.toUpperCase() + ": destroyContextKey succeeded");
                }
            } catch (final Exception e) {
                fail(runtime.toUpperCase() + ": context key ops threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testSetGetContextParity() {
        LOGGER.info("Testing setContext/getContext on both runtimes");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                final long key = ext.createContextKey();
                LOGGER.info(runtime.toUpperCase() + ": created context key = " + key);

                if (key == 0) {
                    LOGGER.info(runtime.toUpperCase() + ": skipping (context key API not available)");
                    continue;
                }

                try (final WebAssemblyModule module = rt.compile(wasm);
                     final WamrInstanceExtensions instance =
                         (WamrInstanceExtensions) module.instantiate()) {

                    instance.setContext(key, 12345L);
                    final long ctx = instance.getContext(key);
                    LOGGER.info(runtime.toUpperCase()
                        + ": setContext(key, 12345) -> getContext(key) = " + ctx);
                    assertEquals(12345L, ctx,
                        runtime.toUpperCase() + ": context value mismatch");
                }

                ext.destroyContextKey(key);
            } catch (final Exception e) {
                fail(runtime.toUpperCase() + ": setContext/getContext threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInstantiateExParity() {
        LOGGER.info("Testing instantiateEx on both runtimes");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(wasm)) {

                try (final WebAssemblyInstance instance =
                         module.instantiateEx(16384, 16 * 1024 * 1024, 65536)) {
                    assertNotNull(instance,
                        runtime.toUpperCase() + ": instantiateEx returned null");
                    assertFalse(instance.isClosed(),
                        runtime.toUpperCase() + ": instance should not be closed");

                    final var func = instance.getFunction("add");
                    final Object result = func.invoke(3, 4);
                    LOGGER.info(runtime.toUpperCase()
                        + ": instantiateEx -> add(3,4) = " + result);
                    assertEquals(7, ((Number) result).intValue(),
                        runtime.toUpperCase() + ": add(3,4) should be 7");
                }
            } catch (final Exception e) {
                fail(runtime.toUpperCase() + ": instantiateEx threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testInstantiateExNegativeArgs() {
        LOGGER.info("Testing instantiateEx with negative arguments");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(wasm)) {

                assertThrows(IllegalArgumentException.class, () ->
                    module.instantiateEx(-1, 1024, 1024),
                    runtime.toUpperCase() + ": should reject negative stackSize");
                LOGGER.info(runtime.toUpperCase() + ": negative args correctly rejected");
            } catch (final Exception e) {
                fail(runtime.toUpperCase() + ": threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testSpawnDestroyExecEnvParity() {
        LOGGER.info("Testing spawnExecEnv/destroySpawnedExecEnv on both runtimes");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(wasm);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final long execEnv = instance.spawnExecEnv();
                LOGGER.info(runtime.toUpperCase() + ": spawnExecEnv() = " + execEnv);
                if (execEnv == 0) {
                    LOGGER.info(runtime.toUpperCase()
                        + ": spawnExecEnv returned 0 (aux stack not available), skipping destroy");
                } else {
                    instance.destroySpawnedExecEnv(execEnv);
                    LOGGER.info(runtime.toUpperCase() + ": destroySpawnedExecEnv succeeded");
                }
                // Verify destroySpawnedExecEnv(0) is a safe no-op
                instance.destroySpawnedExecEnv(0);
                LOGGER.info(runtime.toUpperCase() + ": destroySpawnedExecEnv(0) is safe no-op");
            } catch (final Exception e) {
                fail(runtime.toUpperCase()
                    + ": spawnExecEnv/destroySpawnedExecEnv threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testSetContextSpreadParity() {
        LOGGER.info("Testing setContextSpread/getContext on both runtimes");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                final long key = ext.createContextKey();
                LOGGER.info(runtime.toUpperCase() + ": created context key = " + key);

                if (key == 0) {
                    LOGGER.info(runtime.toUpperCase()
                        + ": skipping (context key API not available)");
                    continue;
                }

                try (final WebAssemblyModule module = rt.compile(wasm);
                     final WamrInstanceExtensions instance =
                         (WamrInstanceExtensions) module.instantiate()) {

                    instance.setContextSpread(key, 99999L);
                    final long ctx = instance.getContext(key);
                    LOGGER.info(runtime.toUpperCase()
                        + ": setContextSpread(key, 99999) -> getContext(key) = " + ctx);
                    assertEquals(99999L, ctx,
                        runtime.toUpperCase() + ": context value mismatch after setContextSpread");
                }

                ext.destroyContextKey(key);
            } catch (final Exception e) {
                fail(runtime.toUpperCase()
                    + ": setContextSpread/getContext threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testFindRegisteredModuleParity() {
        LOGGER.info("Testing findRegisteredModule on both runtimes");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                final long handle = ext.findRegisteredModule("never_registered_module");
                LOGGER.info(runtime.toUpperCase()
                    + ": findRegisteredModule('never_registered_module') = " + handle);
                assertEquals(0L, handle,
                    runtime.toUpperCase()
                        + ": unregistered module should return 0 handle");
            } catch (final Exception e) {
                fail(runtime.toUpperCase()
                    + ": findRegisteredModule threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testGetNativeAddrRangeParity() {
        LOGGER.info("Testing getNativeAddrRange on both runtimes");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(wasm);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final long[] range = instance.getNativeAddrRange(0);
                LOGGER.info(runtime.toUpperCase() + ": getNativeAddrRange(0) = "
                    + (range != null ? "[" + range[0] + ", " + range[1] + "]" : "null"));
                // The result may be null if the native pointer 0 is not within any memory.
                // If non-null, it should have start and size.
                if (range != null) {
                    assertEquals(2, range.length,
                        runtime.toUpperCase() + ": range should have 2 elements (start, size)");
                    LOGGER.info(runtime.toUpperCase()
                        + ": range start=" + range[0] + " size=" + range[1]);
                } else {
                    LOGGER.info(runtime.toUpperCase()
                        + ": getNativeAddrRange(0) returned null (expected for ptr 0)");
                }
            } catch (final Exception e) {
                fail(runtime.toUpperCase()
                    + ": getNativeAddrRange threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testExternrefOperationsParity() {
        LOGGER.info("Testing externrefObj2Ref and externrefObjDel on both runtimes");
        final byte[] wasm = buildSimpleModule();
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime();
                 final WebAssemblyModule module = rt.compile(wasm);
                 final WamrInstanceExtensions instance =
                     (WamrInstanceExtensions) module.instantiate()) {

                final int ref = instance.externrefObj2Ref(42L);
                LOGGER.info(runtime.toUpperCase()
                    + ": externrefObj2Ref(42) = " + ref
                    + " (may be -1 if externref not supported without ref-types module)");

                // Should not crash regardless of return value
                instance.externrefObjDel(42L);
                LOGGER.info(runtime.toUpperCase()
                    + ": externrefObjDel(42) completed without crash");
            } catch (final Exception e) {
                LOGGER.info(runtime.toUpperCase()
                    + ": externref operations threw (may be expected): " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }

    @Test
    void testContextKeyZeroIsNoOp() {
        LOGGER.info("Testing destroyContextKey(0) is a no-op");
        for (final String runtime : new String[]{"jni", "panama"}) {
            System.setProperty("wamr4j.runtime", runtime);
            if (!RuntimeFactory.isProviderAvailable(runtime)) {
                System.clearProperty("wamr4j.runtime");
                continue;
            }
            try (final WebAssemblyRuntime rt = RuntimeFactory.createRuntime()) {
                final WamrRuntimeExtensions ext = (WamrRuntimeExtensions) rt;
                ext.destroyContextKey(0);
                LOGGER.info(runtime.toUpperCase()
                    + ": destroyContextKey(0) succeeded (no-op)");
            } catch (final Exception e) {
                fail(runtime.toUpperCase()
                    + ": destroyContextKey(0) threw: " + e.getMessage());
            } finally {
                System.clearProperty("wamr4j.runtime");
            }
        }
    }
}
