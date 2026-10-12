package kr.astar.pasm;

/** Unchecked error raised by the PASM host for ABI violations, invalid modules and WASM traps. */
public final class PasmException extends RuntimeException {

    public PasmException(String message) {
        super(message);
    }

    public PasmException(String message, Throwable cause) {
        super(message, cause);
    }
}
