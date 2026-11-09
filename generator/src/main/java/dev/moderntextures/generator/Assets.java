package dev.moderntextures.generator;

import com.google.gson.*;
import dev.moderntextures.core.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

final class Assets {
    final Map<String, byte[]> files = new TreeMap<>();
    static Assets read(Path jar) throws IOException {
        Assets a = new Assets();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            long total = 0;
            while (entries.hasMoreElements()) {
                var e = entries.nextElement(); String name = e.getName();
                if (e.isDirectory() || !(name.equals("version.json") || name.equals("pack.mcmeta") || name.startsWith("assets/minecraft/"))) continue;
                if (name.contains("..") || e.getSize() > 16 * 1024 * 1024) throw new IOException("Entrada inválida: " + name);
                try (var in = zip.getInputStream(e)) {
                    byte[] bytes = in.readNBytes(16 * 1024 * 1024 + 1);
                    total += bytes.length;
                    if (bytes.length > 16 * 1024 * 1024 || total > 256L * 1024 * 1024) throw new IOException("Assets excedem limites: " + name);
                    a.files.put(name, bytes);
                }
            }
        }
        return a;
    }
    JsonObject object(String path) {
        byte[] data = files.get(path);
        return data == null ? null : Json.GSON.fromJson(new String(data, StandardCharsets.UTF_8), JsonObject.class);
    }
    Set<String> textures() {
        Set<String> names = new TreeSet<>();
        for (String p : files.keySet()) if (p.startsWith("assets/minecraft/textures/") && p.endsWith(".png")) names.add(p);
        return names;
    }
    JsonObject packMetadata(int protocol) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "ModernTextures 26.3 - texturas adaptadas");
        JsonObject version = object("version.json");
        JsonElement format = null;
        if (version != null && version.has("pack_version")) {
            JsonElement versionsElement = version.get("pack_version");
            if (versionsElement.isJsonPrimitive()) format = versionsElement;
            else {
              JsonObject versions = versionsElement.getAsJsonObject();
              if (versions.has("resource_major")) {
                JsonArray v = new JsonArray(); v.add(versions.get("resource_major").getAsInt()); v.add(versions.get("resource_minor").getAsInt());
                pack.add("min_format", v); pack.add("max_format", v.deepCopy());
                JsonObject result = new JsonObject(); result.add("pack", pack); return result;
              }
              format = versions.get("resource");
            }
        }
        if (format == null) {
            if (protocol > 404) throw new IllegalArgumentException("version.json não informa formato de pack para protocolo " + protocol);
            int legacy = protocol < 107 ? 1 : protocol <= 210 ? 2 : protocol <= 340 ? 3 : 4;
            pack.addProperty("pack_format", legacy);
        } else {
            int f = format.getAsInt();
            if (f >= 65) { JsonArray v = new JsonArray(); v.add(f); v.add(0); pack.add("min_format", v); pack.add("max_format", v.deepCopy()); }
            else pack.addProperty("pack_format", f);
        }
        JsonObject result = new JsonObject(); result.add("pack", pack); return result;
    }
}
