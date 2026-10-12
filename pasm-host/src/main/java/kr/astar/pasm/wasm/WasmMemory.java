package kr.astar.pasm.wasm;

import com.dylibso.chicory.runtime.Memory;
import kr.astar.pasm.PasmException;

/** Bounds-checked view over the linear memory of a guest instance. */
public final class WasmMemory {

    private final Memory memory;

    public WasmMemory(Memory memory) {
        this.memory = memory;
    }

    /** Reads a UTF-8 string, rejecting negative, oversized or out-of-bounds ranges. */
    public String readString(int ptr, int len) {
        if (ptr < 0 || len < 0) {
            throw new PasmException("invalid guest range ptr=" + ptr + " len=" + len);
        }
        if (len > Abi.MAX_STRING_BYTES) {
            throw new PasmException("guest string too large: " + len + " bytes");
        }
        long end = (long) ptr + len;
        if (end > (long) memory.pages() * Memory.PAGE_SIZE) {
            throw new PasmException("guest range out of bounds ptr=" + ptr + " len=" + len);
        }
        return memory.readString(ptr, len);
    }
}
