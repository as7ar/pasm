package kr.astar.pasm.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import kr.astar.pasm.PasmException;

/** Discovers `.wasm` files in a directory and owns the lifecycle of the modules loaded from them. */
public final class PasmModuleLoader {

    private final Path directory;
    private final Logger logger;
    private final List<PasmModule> modules = new ArrayList<>();

    public PasmModuleLoader(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    /** Loads and enables every module in alphabetical file order. A broken module never blocks the others. */
    public void loadAndEnableAll() {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new PasmException("cannot create " + directory, e);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".wasm"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new PasmException("cannot list " + directory, e);
        }

        for (Path file : files) {
            loadOne(file);
        }
        logger.info("Loaded " + modules.size() + " of " + files.size() + " PASM module(s)");
    }

    private void loadOne(Path file) {
        PasmModule module;
        try {
            module = PasmModule.load(file, logger);
            module.enable();
        } catch (PasmException e) {
            logger.log(Level.SEVERE, "Failed to start " + file.getFileName() + ": " + e.getMessage(), e);
            return;
        }
        modules.add(module);
        PasmMetadata metadata = module.metadata();
        logger.info("Enabled " + metadata.name() + " v" + metadata.version()
                + " by " + String.join(", ", metadata.authors()) + " (" + file.getFileName() + ")");
    }

    public List<PasmModule> modules() {
        return List.copyOf(modules);
    }

    /** Disables modules in reverse load order and forgets them. */
    public void disableAll() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            PasmModule module = modules.get(i);
            try {
                module.disable();
            } catch (PasmException e) {
                logger.log(Level.SEVERE, "Failed to disable " + module.metadata().name() + ": " + e.getMessage(), e);
            }
        }
        modules.clear();
    }
}
