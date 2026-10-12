package kr.astar.pasm.module;

import com.dylibso.chicory.runtime.ImportValues;
import java.nio.file.Path;
import java.util.logging.Logger;
import kr.astar.pasm.PasmException;
import kr.astar.pasm.bridge.LogBridge;
import kr.astar.pasm.wasm.Abi;
import kr.astar.pasm.wasm.WasmRuntime;

/** A loaded `.wasm` plugin: owns its runtime instance and drives its enable/disable lifecycle. */
public final class PasmModule {

    public enum State { LOADED, ENABLED, DISABLED, FAILED }

    private final Path file;
    private final PasmMetadata metadata;
    private final WasmRuntime runtime;
    private final Logger logger;
    private State state = State.LOADED;

    private PasmModule(Path file, PasmMetadata metadata, WasmRuntime runtime, Logger logger) {
        this.file = file;
        this.metadata = metadata;
        this.runtime = runtime;
        this.logger = logger;
    }

    /** Instantiates the module with host imports and reads its metadata. */
    public static PasmModule load(Path file, Logger logger) {
        String fileName = file.getFileName().toString();
        String prefix = fileName.endsWith(".wasm") ? fileName.substring(0, fileName.length() - 5) : fileName;
        ImportValues imports = ImportValues.builder()
                .addFunction(new LogBridge(logger, prefix).hostFunction())
                .build();
        WasmRuntime runtime = WasmRuntime.load(file, imports);
        PasmMetadata metadata = PasmMetadata.parse(runtime.label(), runtime.readMetadataFragments());
        return new PasmModule(file, metadata, runtime, logger);
    }

    public Path file() {
        return file;
    }

    public PasmMetadata metadata() {
        return metadata;
    }

    public synchronized State state() {
        return state;
    }

    /** Runs the guest `on_enabled`. A failure marks the module as FAILED and rethrows. */
    public synchronized void enable() {
        if (state != State.LOADED) {
            throw new PasmException(metadata.name() + " cannot be enabled from state " + state);
        }
        runLifecycle(Abi.EXPORT_PLUGIN_ENABLE, State.ENABLED);
    }

    /** Runs the guest `on_disabled`. Disabling a module that is not enabled is a no-op. */
    public synchronized void disable() {
        if (state != State.ENABLED) {
            return;
        }
        runLifecycle(Abi.EXPORT_PLUGIN_DISABLE, State.DISABLED);
    }

    private void runLifecycle(String export, State target) {
        int status;
        try {
            status = runtime.callStatus(export);
        } catch (PasmException e) {
            state = State.FAILED;
            throw e;
        }
        if (status != Abi.STATUS_OK) {
            state = State.FAILED;
            throw new PasmException(metadata.name() + " returned status " + status + " from " + export);
        }
        state = target;
        logger.fine(metadata.name() + " -> " + target);
    }
}
