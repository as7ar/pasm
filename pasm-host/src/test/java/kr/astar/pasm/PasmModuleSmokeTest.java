package kr.astar.pasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import kr.astar.pasm.module.PasmModule;
import kr.astar.pasm.module.PasmModuleLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PasmModuleSmokeTest {

    private Path wasm;
    private Logger logger;
    private List<String> lines;

    @BeforeEach
    void setUp() {
        String property = System.getProperty("pasm.testWasm", "");
        Assumptions.assumeTrue(!property.isBlank(), "pass -PpasmTestWasm=<path to hello_plugin.wasm>");
        wasm = Path.of(property);
        lines = new ArrayList<>();
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getLevel() + " " + record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    void loadsMetadataAndRunsLifecycle() {
        PasmModule module = PasmModule.load(wasm, logger);
        assertEquals("HelloPlugin", module.metadata().name());
        assertEquals("0.1.0", module.metadata().version());
        assertEquals(List.of("AS7AR"), module.metadata().authors());

        module.enable();
        assertEquals(PasmModule.State.ENABLED, module.state());
        assertTrue(lines.contains("INFO [hello_plugin] HelloPlugin enabled (count = 1)"));
        assertTrue(lines.contains("WARNING [hello_plugin] this is a warning from Rust"));
        assertTrue(lines.contains("SEVERE [hello_plugin] this is an error from Rust"));

        module.disable();
        assertEquals(PasmModule.State.DISABLED, module.state());
        assertTrue(lines.contains("INFO [hello_plugin] HelloPlugin disabled (count = 1)"));
        assertThrows(PasmException.class, module::enable);
    }

    @Test
    void loaderSkipsBrokenModulesAndEnablesTheRest(@TempDir Path directory) throws Exception {
        Files.copy(wasm, directory.resolve("a.wasm"));
        Files.write(directory.resolve("b.wasm"), new byte[] {1, 2, 3});
        Files.writeString(directory.resolve("ignored.txt"), "x");

        PasmModuleLoader loader = new PasmModuleLoader(directory, logger);
        loader.loadAndEnableAll();
        assertEquals(1, loader.modules().size());
        loader.disableAll();
        assertEquals(0, loader.modules().size());
    }
}
