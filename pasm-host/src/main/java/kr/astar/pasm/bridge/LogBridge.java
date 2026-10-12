package kr.astar.pasm.bridge;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.astar.pasm.wasm.Abi;
import kr.astar.pasm.wasm.WasmMemory;

/** Host function `pasm.log(level, ptr, len) -> status` that forwards guest log lines to the server logger. */
public final class LogBridge {

    private final Logger logger;
    private final String prefix;

    public LogBridge(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = prefix;
    }

    public ImportFunction hostFunction() {
        FunctionType type = FunctionType.of(
                List.of(ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32));
        return new HostFunction(Abi.IMPORT_MODULE, Abi.IMPORT_LOG, type, (instance, args) -> {
            Level level = toLevel((int) args[0]);
            if (level == null) {
                return new long[] {Abi.HOST_INVALID_ARGUMENT};
            }
            String message = new WasmMemory(instance.memory()).readString((int) args[1], (int) args[2]);
            logger.log(level, "[" + prefix + "] " + message);
            return new long[] {Abi.STATUS_OK};
        });
    }

    private static Level toLevel(int level) {
        return switch (level) {
            case Abi.LOG_DEBUG -> Level.FINE;
            case Abi.LOG_INFO -> Level.INFO;
            case Abi.LOG_WARN -> Level.WARNING;
            case Abi.LOG_ERROR -> Level.SEVERE;
            default -> null;
        };
    }
}
