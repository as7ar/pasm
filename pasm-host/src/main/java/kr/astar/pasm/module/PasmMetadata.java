package kr.astar.pasm.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import kr.astar.pasm.PasmException;
import kr.astar.pasm.wasm.Abi;

/** Plugin metadata discovered from the `__pasm_meta_*` exports of a module. */
public record PasmMetadata(String name, List<String> authors, String version) {

    /** Merges metadata fragments; requires exactly one `plugin` fragment and ignores unknown kinds. */
    public static PasmMetadata parse(String label, List<String> fragments) {
        JsonObject plugin = null;
        for (String fragment : fragments) {
            JsonObject object;
            try {
                object = JsonParser.parseString(fragment).getAsJsonObject();
            } catch (RuntimeException e) {
                throw new PasmException(label + " exposes malformed metadata: " + fragment, e);
            }
            if (!"plugin".equals(string(object, "kind"))) {
                continue;
            }
            if (plugin != null) {
                throw new PasmException(label + " declares more than one plugin");
            }
            plugin = object;
        }
        if (plugin == null) {
            throw new PasmException(label + " does not declare a plugin");
        }
        if (!plugin.has("abi") || plugin.get("abi").getAsInt() != Abi.VERSION) {
            throw new PasmException(label + " plugin metadata has an unsupported ABI version");
        }

        List<String> authors = new ArrayList<>();
        JsonArray array = plugin.has("authors") ? plugin.getAsJsonArray("authors") : new JsonArray();
        for (JsonElement author : array) {
            authors.add(author.getAsString());
        }
        String name = string(plugin, "name");
        String version = string(plugin, "version");
        if (name == null || name.isBlank() || version == null) {
            throw new PasmException(label + " plugin metadata is missing name or version");
        }
        return new PasmMetadata(name, List.copyOf(authors), version);
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }
}
