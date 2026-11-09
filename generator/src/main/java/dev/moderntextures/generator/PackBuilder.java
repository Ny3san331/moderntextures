package dev.moderntextures.generator;

import com.google.gson.*;
import dev.moderntextures.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

final class PackBuilder {
    record Result(Path zip, String sha1, Map<String,Object> report) {}
    private final Assets source;
    private final MappingConfig config;
    private final Prismarine prismarine;
    private final ViaMappings via;
    PackBuilder(Assets source, MappingConfig config, Prismarine prismarine, ViaMappings via) {
        this.source=source; this.config=config; this.prismarine=prismarine; this.via=via;
    }
    Result build(Profiles.Profile profile, Assets target, Path output) throws Exception {
        Map<String,byte[]> pack = new TreeMap<>();
        Map<String,Object> report = new LinkedHashMap<>();
        List<String> replaced = new ArrayList<>(), unchanged = new ArrayList<>(), incompatible = new ArrayList<>(), prismFallback = new ArrayList<>();
        for (String path : target.textures()) {
            if (!inScope(path)) continue;
            String modern = modernPath(path);
            byte[] texture = source.files.get(modern);
            if (texture == null) { texture = prismarine.fallback.get(modern); if (texture != null) prismFallback.add(path); }
            if (texture == null) { unchanged.add(path); continue; }
            if (!compatibleLayout(target.files.get(path), texture, path)) { incompatible.add(path + " <- " + modern); continue; }
            pack.put(path, texture);
            JsonObject metadata = new JsonObject();
            JsonObject original = source.object(modern + ".mcmeta");
            if (original != null && original.has("animation")) {
                JsonObject animation = original.getAsJsonObject("animation").deepCopy();
                if (profile.protocol() < 47) animation.remove("interpolate");
                metadata.add("animation", animation);
            }
            if (original != null || target.files.containsKey(path + ".mcmeta")) put(pack, path + ".mcmeta", metadata);
            replaced.add(path);
        }
        List<String> skippedItems = new ArrayList<>(), adaptedItems = new ArrayList<>();
        Map<String,MappingConfig.ItemRule> rules = new TreeMap<>();
        if (config.autoItems() && profile.protocol() >= 477) {
            Set<String> available = via.registry(profile.registry(), "items");
            for (String item : via.registry("26.3", "items")) {
                if (available.contains(item)) continue;
                ViaMappings.Translation mapped = via.item(item, profile.registry());
                String texture = source.files.containsKey("assets/minecraft/textures/item/" + item + ".png")
                    ? "assets/minecraft/textures/item/" + item + ".png" : "assets/minecraft/textures/block/" + item + ".png";
                if (mapped == null || !source.files.containsKey(texture) || (profile.protocol() < 769 && mapped.cmd() == null)) {
                    skippedItems.add(item + ": sem traduÃ§Ã£o verificÃ¡vel ou sprite simples"); continue;
                }
                rules.put("minecraft:" + item, new MappingConfig.ItemRule("minecraft:" + item, "minecraft:" + mapped.substitute(),
                    List.of(profile.version()), texture, "generated", mapped.cmd(), mapped.original(), 0));
            }
        }
        config.select(profile.version()).forEach(r -> rules.put(r.source(), r));
        Map<String,List<MappingConfig.ItemRule>> grouped = new TreeMap<>();
        for (var rule : rules.values()) {
            if (!source.files.containsKey(rule.texture())) throw new IllegalArgumentException("Textura configurada nÃ£o existe na 26.3: " + rule.texture());
            String carrier = rule.substitute().substring("minecraft:".length());
            String definition = "assets/minecraft/" + (profile.protocol() >= 769 ? "items/" : "models/item/") + carrier + ".json";
            if (!target.files.containsKey(definition)) {
                if (config.select(profile.version()).contains(rule)) throw new IllegalArgumentException("Modelo do substituto ausente: " + definition);
                skippedItems.add(rule.source() + ": modelo do substituto ausente"); continue;
            }
            grouped.computeIfAbsent(carrier, k -> new ArrayList<>()).add(rule);
        }
        for (var group : grouped.entrySet()) {
            String carrier = group.getKey(); List<MappingConfig.ItemRule> entries = group.getValue();
            for (var r : entries) {
                String name = r.source().substring("minecraft:".length());
                byte[] bytes = source.files.get(r.texture());
                // 1.21.4+ stitches item sprites from assets/minecraft/textures/item only. A custom
                // namespace would need an extra atlas entry, so use a unique minecraft item path.
                String sprite = "minecraft:item/moderntextures_" + name;
                String path = "assets/minecraft/textures/item/moderntextures_" + name + ".png";
                pack.put(path, bytes);
                JsonObject meta = source.object(r.texture() + ".mcmeta");
                if (meta != null && meta.has("animation")) {
                    JsonObject adapted = new JsonObject(); adapted.add("animation", meta.get("animation")); put(pack, path + ".mcmeta", adapted);
                }
                JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:item/" + r.model());
                JsonObject textures = new JsonObject();
                if (profile.protocol() >= 775) {
                    // 26.1+ also supports explicit sprite-reference objects.
                    JsonObject reference = new JsonObject(); reference.addProperty("sprite", sprite); textures.add("layer0", reference);
                } else textures.addProperty("layer0", sprite);
                model.add("textures", textures); put(pack, "assets/moderntextures/models/item/" + name + ".json", model);
                adaptedItems.add(r.source() + " -> " + r.substitute() + " (sprite aproximado)");
            }
            if (profile.protocol() >= 769) modernItems(pack, target, carrier, entries);
            else legacyItems(pack, target, carrier, entries);
        }
        List<String> missingBlocks = new ArrayList<>();
        boolean namedBlockCatalog = via != null && profile.protocol() >= 393 && ViaMappings.CHAIN.contains(profile.registry());
        if (namedBlockCatalog) {
            Set<String> blocks = via.registry(profile.registry(), "blocks");
            for (String block : via.registry("26.3", "blocks")) if (!blocks.contains(block)) missingBlocks.add(block);
        }
        put(pack, "pack.mcmeta", target.packMetadata(profile.protocol()));
        report.put("sourceVersion", "26.3"); report.put("clientVersion", profile.version()); report.put("protocol", profile.protocol());
        report.put("prismarineCommit", config.prismarineCommit()); report.put("viaMappingsCommit", config.viaMappingsCommit());
        report.put("updatedTextures", replaced); report.put("unchangedNoRecentTexture", unchanged); report.put("incompatibleUvLayout", incompatible);
        report.put("prismarine26_1FallbackNot26_3", prismFallback); report.put("adaptedItems", adaptedItems); report.put("unadaptedItems", skippedItems);
        report.put("newWorldBlocksUsingViaFallback", missingBlocks);
        report.put("worldBlockCatalogAvailable", namedBlockCatalog);
        report.put("conditionalItemModelsAvailable", profile.protocol() >= 477);
        report.put("limitations", List.of("NÃ£o homologado visualmente em cliente real.", "Blocos novos usam traduÃ§Ã£o Via; estados colidentes nÃ£o recebem texturas individuais.",
            "Itens com componentes customizados por outros plugins podem nÃ£o corresponder aos seletores gerados.",
            "Sprites de itens novos sÃ£o aproximaÃ§Ãµes; nÃ£o reproduzem modelos especiais, tintas ou mecÃ¢nicas.",
            profile.protocol() < 477 ? "Cliente sem CustomModelData: itens novos permanecem com aparÃªncia do substituto." : "Requer pass-original-item-name-to-resource-packs=true no ViaBackwards 5.12.2."));
        validate(pack, target);
        Files.createDirectories(output);
        String stem = "moderntextures-26.3-to-" + profile.version();
        Path temp = Files.createTempFile(output, stem, ".tmp");
        try { zip(pack, temp); }
        catch (Exception e) { Files.deleteIfExists(temp); throw e; }
        String hash = Downloads.sha1(Files.readAllBytes(temp));
        Path zip = output.resolve(stem + "-" + hash.substring(0,12) + ".zip");
        Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING);
        Json.write(output.resolve(stem + "-report.json"), report);
        return new Result(zip, hash, report);
    }
    private String modernPath(String old) {
        if (config.textureAliases().containsKey(old)) return config.textureAliases().get(old);
        if (source.files.containsKey(old)) return old;
        String explicit = prismarine.aliases.get(old);
        if (explicit != null) return explicit;
        return old.replace("/textures/blocks/", "/textures/block/").replace("/textures/items/", "/textures/item/");
    }
    static boolean inScope(String path) {
        return path.matches("assets/minecraft/textures/(?:blocks?|items?)/.*")
            || path.matches("assets/minecraft/textures/entity/(?:chest|bed|signs|shulker|banner|conduit|decorated_pot)(?:/|\\.).*")
            || path.equals("assets/minecraft/textures/entity/enchanting_table_book.png");
    }
    static boolean compatibleLayout(byte[] old, byte[] recent, String path) throws IOException {
        var a = ImageIO.read(new ByteArrayInputStream(old)); var b = ImageIO.read(new ByteArrayInputStream(recent));
        if (a == null || b == null) throw new IOException("PNG invÃ¡lido: " + path);
        // Preserve UV layouts, including entity/block renderers. A changed layout requires an explicit converter.
        return (long)a.getWidth() * b.getHeight() == (long)b.getWidth() * a.getHeight();
    }
    static void modernItems(Map<String,byte[]> pack, Assets target, String carrier, List<MappingConfig.ItemRule> rules) {
        String path = "assets/minecraft/items/" + carrier + ".json";
        JsonObject root = target.object(path).deepCopy(); JsonElement model = root.get("model");
        Set<String> selectors = new HashSet<>();
        for (var r : rules) {
            if (!selectors.add(r.stringIndex() + ":" + r.originalIdentifier())) throw new IllegalArgumentException("Seletores de item duplicados: " + carrier);
            JsonObject select = new JsonObject(); select.addProperty("type", "minecraft:select"); select.addProperty("property", "minecraft:custom_model_data"); select.addProperty("index", r.stringIndex());
            select.add("fallback", model); JsonArray cases = new JsonArray(); JsonObject entry = new JsonObject();
            JsonArray identifiers = new JsonArray(); String bare = r.originalIdentifier().replace("minecraft:", ""); identifiers.add(bare); identifiers.add("minecraft:" + bare);
            entry.add("when", identifiers); entry.add("model", modelRef(r.source())); cases.add(entry); select.add("cases", cases); model = select;
        }
        root.add("model", model); put(pack, path, root);
    }
    private static JsonObject modelRef(String source) {
        JsonObject ref = new JsonObject(); ref.addProperty("type", "minecraft:model"); ref.addProperty("model", "moderntextures:item/" + source.replace("minecraft:", "")); return ref;
    }
    static void legacyItems(Map<String,byte[]> pack, Assets target, String carrier, List<MappingConfig.ItemRule> rules) {
        String path = "assets/minecraft/models/item/" + carrier + ".json";
        JsonObject root = target.object(path).deepCopy();
        JsonArray overrides = root.has("overrides") ? root.getAsJsonArray("overrides") : new JsonArray();
        rules.sort(Comparator.comparingInt(MappingConfig.ItemRule::customModelData)); Set<Integer> ids = new HashSet<>();
        for (var rule : rules) {
            int cmd = rule.customModelData(); if (!ids.add(cmd)) throw new IllegalArgumentException("CustomModelData duplicado: " + carrier + ":" + cmd);
            // The unchanged base model is already the fallback for every unmatched value.
            overrides.add(override(cmd, "moderntextures:item/" + rule.source().replace("minecraft:", "")));
        }
        root.add("overrides", overrides); put(pack, path, root);
    }
    private static JsonObject override(int value, String model) {
        JsonObject o = new JsonObject(), predicate = new JsonObject(); predicate.addProperty("custom_model_data", value); o.add("predicate", predicate); o.addProperty("model", model); return o;
    }
    static void put(Map<String,byte[]> files, String path, JsonElement object) { files.put(path, Json.GSON.toJson(object).getBytes(StandardCharsets.UTF_8)); }
    static void zip(Map<String,byte[]> files, Path path) throws IOException {
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var e : new TreeMap<>(files).entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey()); entry.setTime(315532800000L); out.putNextEntry(entry); out.write(e.getValue()); out.closeEntry();
            }
        }
    }
    static void validate(Map<String,byte[]> pack, Assets target) throws IOException {
        if (!pack.containsKey("pack.mcmeta")) throw new IOException("pack.mcmeta ausente");
        for (var entry : pack.entrySet()) {
            String path = entry.getKey();
            if (path.contains("..") || path.startsWith("/") || path.contains("\\")) throw new IOException("Caminho invÃ¡lido: " + path);
            if (path.endsWith(".png") && ImageIO.read(new ByteArrayInputStream(entry.getValue())) == null) throw new IOException("PNG invÃ¡lido: " + path);
            if (path.endsWith(".json") || path.endsWith(".mcmeta")) {
                JsonElement json = JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8));
                if (!json.isJsonObject()) throw new IOException("Objeto JSON esperado: " + path);
                validateRefs(json, pack, target);
            }
        }
    }
    private static void validateRefs(JsonElement json, Map<String,byte[]> pack, Assets target) throws IOException {
        if (json.isJsonArray()) { for (var e : json.getAsJsonArray()) validateRefs(e, pack, target); }
        else if (json.isJsonObject()) {
            for (var e : json.getAsJsonObject().entrySet()) {
                if ((e.getKey().equals("model") || e.getKey().equals("parent")) && e.getValue().isJsonPrimitive()) {
                    String id = e.getValue().getAsString();
                    if (id.startsWith("moderntextures:")) {
                        String path = "assets/moderntextures/models/" + id.substring(id.indexOf(':')+1) + ".json";
                        if (!pack.containsKey(path)) throw new IOException("Modelo nÃ£o resolvido: " + id);
                    }
                }
                if (e.getKey().equals("textures") && e.getValue().isJsonObject()) {
                    for (var texture : e.getValue().getAsJsonObject().entrySet()) {
                        JsonElement ref = texture.getValue();
                        if (ref.isJsonObject()) ref = ref.getAsJsonObject().get("sprite");
                        if (ref != null && ref.isJsonPrimitive()) {
                            String id = ref.getAsString();
                            if (id.startsWith("moderntextures:")) {
                                String path = "assets/moderntextures/textures/" + id.substring(id.indexOf(':')+1) + ".png";
                                if (!pack.containsKey(path)) throw new IOException("Textura nÃ£o resolvida: " + id);
                            }
                        }
                    }
                }
                validateRefs(e.getValue(), pack, target);
            }
        }
    }
}
