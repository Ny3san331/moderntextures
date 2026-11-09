package dev.fixtextures.generator;

import com.google.gson.*;
import dev.fixtextures.core.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

final class Assets {
    final Map<String, byte[]> files;
    final String source;
    Assets() { files = new TreeMap<>(); source = "jar_local"; }
    private Assets(Map<String,byte[]> files, String source) { this.files = files; this.source = source; }
    static Assets remote(String version, Downloads downloads, Set<String> index, Path jar) throws Exception {
        Assets result = new Assets(new RemoteFiles(version, downloads, index), "mcasset");
        // mcasset's version.json is a site manifest, not the client JAR's pack_version.
        if (jar != null) try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("version.json");
            if (entry != null) try (var in = zip.getInputStream(entry)) { result.files.put("version.json", in.readAllBytes()); }
        }
        return result;
    }
    private static final class RemoteFiles extends AbstractMap<String,byte[]> {
        private final String version;
        private final Downloads downloads;
        private final Set<String> index;
        private final Map<String,byte[]> cached = new java.util.concurrent.ConcurrentHashMap<>();
        RemoteFiles(String version, Downloads downloads, Set<String> index) {
            this.version = version; this.downloads = downloads; this.index = index;
        }
        @Override public byte[] get(Object key) {
            if (!(key instanceof String path) || !index.contains(path) && !cached.containsKey(path)) return null;
            try { return cached.computeIfAbsent(path, p -> {
                try { return downloads.asset(version, p); }
                catch (IOException e) { throw new UncheckedIOException(e); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }); } catch (UncheckedIOException e) { throw e; }
        }
        @Override public boolean containsKey(Object key) { return index.contains(key) || cached.containsKey(key); }
        @Override public byte[] put(String key, byte[] value) { return cached.put(key, value); }
        @Override public Set<String> keySet() { Set<String> keys = new TreeSet<>(index); keys.addAll(cached.keySet()); return keys; }
        @Override public Set<Entry<String,byte[]>> entrySet() {
            Set<Entry<String,byte[]>> entries = new LinkedHashSet<>();
            for (String key : keySet()) entries.add(Map.entry(key, get(key)));
            return entries;
        }
    }
    void prefetch(Collection<String> paths) throws Exception {
        if (!(files instanceof RemoteFiles)) return;
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
            for (String path : paths) if (files.containsKey(path)) jobs.add(pool.submit(() -> files.get(path)));
            for (var job : jobs) job.get();
        }
    }
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
        pack.addProperty("description", "FixTextures 26.3 - texturas adaptadas");
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
