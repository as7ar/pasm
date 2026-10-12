package kr.astar.pasm.wasm;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.Export;
import com.dylibso.chicory.wasm.types.ExternalType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.astar.pasm.PasmException;

/** One instantiated guest module and the ABI-level calls the host performs on it. */
public final class WasmRuntime {

    private final String label;
    private final Instance instance;
    private final WasmMemory memory;
    private final List<String> metaExports;

    private WasmRuntime(String label, Instance instance, WasmMemory memory, List<String> metaExports) {
        this.label = label;
        this.instance = instance;
        this.memory = memory;
        this.metaExports = metaExports;
    }

    /** Parses and instantiates a module, then verifies the exported ABI version. */
    public static WasmRuntime load(Path file, ImportValues imports) {
        String label = file.getFileName().toString();
        WasmModule module;
        Instance instance;
        try {
            module = Parser.parse(file);
            instance = Instance.builder(module).withImportValues(imports).build();
        } catch (RuntimeException e) {
            throw new PasmException("failed to load " + label + ": " + e.getMessage(), e);
        }

        List<String> metaExports = new ArrayList<>();
        for (int i = 0; i < module.exportSection().exportCount(); i++) {
            Export export = module.exportSection().getExport(i);
            if (export.exportType() == ExternalType.FUNCTION && export.name().startsWith(Abi.EXPORT_META_PREFIX)) {
                metaExports.add(export.name());
            }
        }
        metaExports.sort(String::compareTo);

        WasmMemory memory;
        try {
            memory = new WasmMemory(instance.exports().memory(Abi.EXPORT_MEMORY));
        } catch (RuntimeException e) {
            throw new PasmException(label + " does not export '" + Abi.EXPORT_MEMORY + "'", e);
        }

        WasmRuntime runtime = new WasmRuntime(label, instance, memory, List.copyOf(metaExports));
        int abi = (int) runtime.call(Abi.EXPORT_ABI_VERSION)[0];
        if (abi != Abi.VERSION) {
            throw new PasmException(label + " targets ABI " + abi + " but the host supports ABI " + Abi.VERSION);
        }
        return runtime;
    }

    public String label() {
        return label;
    }

    /** Calls a lifecycle export and returns its status code. */
    public int callStatus(String export) {
        return (int) call(export)[0];
    }

    /** Calls every `__pasm_meta_*` export and returns the JSON fragments they describe. */
    public List<String> readMetadataFragments() {
        List<String> fragments = new ArrayList<>();
        for (String export : metaExports) {
            long packed = call(export)[0];
            int ptr = (int) (packed >>> 32);
            int len = (int) (packed & 0xFFFFFFFFL);
            fragments.add(memory.readString(ptr, len));
        }
        return fragments;
    }

    private long[] call(String export) {
        ExportFunction function;
        try {
            function = instance.exports().function(export);
        } catch (RuntimeException e) {
            throw new PasmException(label + " is missing required export '" + export + "'", e);
        }
        try {
            return function.apply();
        } catch (RuntimeException e) {
            throw new PasmException(label + " trapped in '" + export + "': " + e.getMessage(), e);
        }
    }
}
