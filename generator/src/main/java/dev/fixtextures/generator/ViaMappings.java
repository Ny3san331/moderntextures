package dev.fixtextures.generator;

import com.google.gson.*;
import dev.fixtextures.core.MappingConfig;
import java.util.*;

/** Reads the public mapping DATA, not ViaBackwards implementation classes. */
final class ViaMappings {
    static final List<String> CHAIN = List.of("26.3", "26.2", "26.1", "1.21.11", "1.21.9", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.2", "1.21", "1.20.5", "1.20.3", "1.20.2", "1.20", "1.19.4", "1.19.3", "1.19", "1.18", "1.17", "1.16.2", "1.16", "1.15", "1.14", "1.13.2", "1.13", "1.12");
    record Translation(String substitute, String original, Integer cmd) {}
    private final Downloads downloads;
    private final String base;
    private final Map<String,JsonObject> loaded = new HashMap<>();
    private final Map<String,Set<String>> registries = new HashMap<>();
    ViaMappings(Downloads downloads, MappingConfig config) {
        this.downloads = downloads;
        base = "https://raw.githubusercontent.com/ViaVersion/Mappings/" + config.viaMappingsCommit() + "/mappings/";
    }
    JsonObject load(String file) throws Exception {
        if (!loaded.containsKey(file)) loaded.put(file, downloads.json(base + file + ".json"));
        return loaded.get(file);
    }
    Set<String> registry(String version, String kind) throws Exception {
        String key = version + ":" + kind;
        if (registries.containsKey(key)) return registries.get(key);
        JsonElement data = load("mapping-" + version).get(kind);
        if (data != null && !data.isJsonArray()) throw new IllegalArgumentException("Registro legado sem lista de identificadores: " + version + "/" + kind);
        JsonArray array = data == null ? null : data.getAsJsonArray();
        Set<String> names = new LinkedHashSet<>();
        if (array != null) array.forEach(e -> names.add(e.getAsString()));
        registries.put(key, names);
        return names;
    }
    Translation item(String source, String target) throws Exception {
        int end = CHAIN.indexOf(target);
        if (end < 0) return null;
        String current = source, original = null; Integer cmd = null;
        for (int i=0; i<end; i++) {
            String next = CHAIN.get(i+1);
            Set<String> available = registry(next, "items");
            if (available.contains(current)) continue;
            JsonObject diff = load("diff/mapping-" + CHAIN.get(i) + "to" + next);
            String replacement = resolve(current, diff, available);
            if (replacement == null) return null;
            if (original == null) {
                original = current;
                JsonObject cmds = diff.getAsJsonObject("custom_model_data");
                if (cmds != null && cmds.has(current)) cmd = cmds.get(current).getAsInt();
            }
            current = replacement;
        }
        return original == null ? null : new Translation(current, original, cmd);
    }
    static String resolve(String id, JsonObject diff, Set<String> available) {
        JsonObject items = diff.getAsJsonObject("items");
        if (items != null && items.has(id)) {
            JsonElement e = items.get(id);
            if (!e.isJsonPrimitive()) return null;
            String value = e.getAsString(); return available.contains(value) ? value : null;
        }
        JsonObject materials = diff.getAsJsonObject("materials");
        if (materials == null) return null;
        List<String> keys = new ArrayList<>(materials.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        for (String key : keys) {
            String wrapped = "_" + id + "_";
            if (!wrapped.contains("_" + key + "_")) continue;
            JsonElement e = materials.get(key);
            String to = e.isJsonObject() ? e.getAsJsonObject().get("to").getAsString() : e.getAsString();
            String candidate = wrapped.replace("_" + key + "_", "_" + to + "_");
            if (e.isJsonObject() && e.getAsJsonObject().has("words")) {
                for (var word : e.getAsJsonObject().getAsJsonObject("words").entrySet()) candidate = candidate.replace("_" + word.getKey() + "_", "_" + word.getValue().getAsString() + "_");
            }
            candidate = candidate.substring(1, candidate.length()-1);
            if (available.contains(candidate)) return candidate;
        }
        return null;
    }
}
