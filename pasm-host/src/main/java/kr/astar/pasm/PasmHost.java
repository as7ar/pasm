package kr.astar.pasm;

import java.nio.file.Path;
import kr.astar.pasm.module.PasmModuleLoader;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper plugin entry point that loads WASM plugins from `plugins/Pasm/plugins`. */
public final class PasmHost extends JavaPlugin {

    private PasmModuleLoader loader;

    @Override
    public void onEnable() {
        Path directory = getDataFolder().toPath().resolve("plugins");
        loader = new PasmModuleLoader(directory, getLogger());
        try {
            loader.loadAndEnableAll();
        } catch (PasmException e) {
            getLogger().severe("PASM could not start: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (loader == null) {
            return;
        }
        loader.disableAll();
        loader = null;
    }
}
