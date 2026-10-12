package kr.astar.pasm.wasm;

/** Constants of the PASM WASM ABI. See docs/ABI.md. */
public final class Abi {

    public static final int VERSION = 1;

    public static final String IMPORT_MODULE = "pasm";
    public static final String IMPORT_LOG = "log";

    public static final String EXPORT_MEMORY = "memory";
    public static final String EXPORT_ABI_VERSION = "__pasm_abi_version";
    public static final String EXPORT_ALLOC = "__pasm_alloc";
    public static final String EXPORT_FREE = "__pasm_free";
    public static final String EXPORT_PLUGIN_ENABLE = "__pasm_plugin_enable";
    public static final String EXPORT_PLUGIN_DISABLE = "__pasm_plugin_disable";
    public static final String EXPORT_META_PREFIX = "__pasm_meta_";

    public static final int STATUS_OK = 0;
    public static final int HOST_INVALID_ARGUMENT = -1;

    public static final int LOG_DEBUG = 0;
    public static final int LOG_INFO = 1;
    public static final int LOG_WARN = 2;
    public static final int LOG_ERROR = 3;

    public static final int MAX_STRING_BYTES = 1 << 20;

    private Abi() {
    }
}
