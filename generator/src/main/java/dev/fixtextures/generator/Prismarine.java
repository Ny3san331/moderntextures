package dev.fixtextures.generator;

import com.google.gson.*;
import dev.fixtextures.core.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class Prismarine {
    final Map<String,String> aliases = new TreeMap<>(); // target path -> recent path
    final Map<String,byte[]> fallback = new HashMap<>();
    Prismarine(Map<String,String> aliases, Map<String,byte[]> fallback) {
        this.aliases.putAll(aliases); this.fallback.putAll(fallback);
    }
    Prismarine(Downloads downloads, MappingConfig config) throws Exception {
        String base = "https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/" + config.prismarineCommit() + "/data/";
        for (String file : List.of("legacy_texturepack_mappings.json", "pre_flattening_texturepack_mappings.json")) {
            JsonObject map = downloads.json(base + "common/" + file);
            for (var e : map.entrySet()) {
                if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString()) continue;
                String target = "assets/minecraft/" + e.getValue().getAsString() + ".png";
                String source = "assets/minecraft/" + e.getKey() + ".png";
                aliases.putIfAbsent(target, source);
            }
        }
        var catalog = Json.GSON.fromJson(java.nio.file.Files.readString(downloads.get(base + "26.1/items_textures.json", null)), JsonArray.class);
        var content = Json.GSON.fromJson(java.nio.file.Files.readString(downloads.get(base + "26.1/texture_content.json", null)), JsonArray.class);
        Map<String,String> paths = new HashMap<>();
        for (var element : catalog) {
            var obj = element.getAsJsonObject();
            if (!obj.has("texture") || obj.get("texture").isJsonNull()) continue;
            String texture = obj.get("texture").getAsString().replace("minecraft:", "").replace("items/", "item/");
            paths.put(obj.get("name").getAsString(), "assets/minecraft/textures/" + texture + ".png");
        }
        for (var element : content) {
            var obj = element.getAsJsonObject(); String path = paths.get(obj.get("name").getAsString());
            if (path == null || !obj.has("texture") || obj.get("texture").isJsonNull()) continue;
            String data = obj.get("texture").getAsString();
            if (data.startsWith("data:image/png;base64,")) fallback.putIfAbsent(path, Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1)));
        }
        aliases.putAll(config.textureAliases());
    }
}
