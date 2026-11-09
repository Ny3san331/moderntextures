package dev.fixtextures.generator;

import com.google.gson.*;
import dev.fixtextures.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

final class PackBuilder {
    record Result(Path zip, String sha1, Map<String,Object> report) {}

    private static final JsonObject SPEAR_DISPLAY = JsonParser.parseString("""
        {
          "firstperson_righthand": {"rotation":[-20,90,-35],"translation":[3.13,2.0,0.13],"scale":[1.36,1.36,0.68]},
          "firstperson_lefthand": {"rotation":[-20,-90,35],"translation":[3.13,2.0,0.13],"scale":[1.36,1.36,0.68]},
          "thirdperson_righthand": {"rotation":[5,270,-40],"translation":[0,2,2],"scale":[1.7,1.7,0.85]},
          "thirdperson_lefthand": {"rotation":[5,-270,40],"translation":[0,2,2],"scale":[1.7,1.7,0.85]}
        }
        """).getAsJsonObject();

    private final Assets source;
    private final MappingConfig config;
    private final Prismarine prismarine;
    PackBuilder(Assets source, MappingConfig config, Prismarine prismarine, ViaMappings via) {
        this.source=source; this.config=config; this.prismarine=prismarine;
    }

    Result build(Profiles.Profile profile, Assets target, Path output) throws Exception {
        Set<String> targetTextures = new TreeSet<>(), sourceTextures = new TreeSet<>();
        for (String path : target.textures()) {
            if (!inScope(path) || path.contains("/entity/player/")) continue;
            String modern = modernPath(path, profile);
            if (modern == null || !source.files.containsKey(modern)) continue;
            targetTextures.add(path);
            sourceTextures.add(modern);
        }
        source.prefetch(sourceTextures);
        target.prefetch(targetTextures);
        Map<String,byte[]> pack = new TreeMap<>();
        Map<String,Object> report = new LinkedHashMap<>();
        List<Map<String,String>> assetDiff = new ArrayList<>();
        Map<String,int[]> coverage = new TreeMap<>();
        for (String category : List.of("blocks", "items", "entities", "trims", "particles")) coverage.put(category, new int[2]);
        List<String> replaced = new ArrayList<>(), unchanged = new ArrayList<>(), incompatible = new ArrayList<>(), prismFallback = new ArrayList<>();
        List<String> entityUpdated = new ArrayList<>(), entitySkipped = new ArrayList<>();
        List<String> armorUpdated = new ArrayList<>(), trimsUpdated = new ArrayList<>(), particlesUpdated = new ArrayList<>();
        List<String> blockAliases = new ArrayList<>();

        for (String path : target.textures()) {
            if (!inScope(path)) continue;
            if (path.contains("/textures/entity/player/")) continue;
            String modern = modernPath(path, profile);
            String group = reportCategory(path);
            if (group != null) coverage.get(group)[1]++;
            if (modern == null) { unchanged.add(path); continue; }
            byte[] texture = source.files.get(modern);
            if (texture == null) { texture = prismarine == null ? null : prismarine.fallback.get(modern); if (texture != null) prismFallback.add(path); }
            if (texture == null) { unchanged.add(path); continue; }
            String diffCategory = modern.equals(path) ? "updated" : "path_alias";
            if (modern.equals(path) && Arrays.equals(target.files.get(path), texture)) { unchanged.add(path); if (group != null) coverage.get(group)[0]++; continue; }

            if (isEntity(path) && profile.protocol() < 766 && path.contains("/entity/wolf/")) {
                entitySkipped.add(path + " <- " + modern + " (UV do lobo mudou em 1.20.5)");
                assetDiff.add(diff(path, modern, diffCategory, "incompatible"));
                continue;
            }
            if (!layoutCompatible(path, target.files.get(path), texture)) {
                incompatible.add(path + " <- " + modern);
                assetDiff.add(diff(path, modern, diffCategory, "incompatible")); continue;
            }

            pack.put(path, texture);
            if (isTrim(path)) {
                // Até 26.2 o cliente usa a paleta fixa trim_palette e não traz mcmeta nos trims vanilla.
                trimsUpdated.add(path);
            } else {
                JsonObject original = source.object(modern + ".mcmeta");
                if (original != null && original.has("animation")) {
                    JsonObject metadata = new JsonObject();
                    JsonObject animation = original.getAsJsonObject("animation").deepCopy();
                    if (profile.protocol() < 47) animation.remove("interpolate");
                    metadata.add("animation", animation);
                    put(pack, path + ".mcmeta", metadata);
                } else if (target.files.containsKey(path + ".mcmeta") && !isPalette(path)) {
                    put(pack, path + ".mcmeta", new JsonObject());
                }
                if (isArmor(path)) armorUpdated.add(path);
                else if (isParticle(path)) particlesUpdated.add(path);
                else if (isEntity(path)) entityUpdated.add(path);
            }
            if (!modern.equals(path) && !path.equals(modern)) {
                if (isBlock(path)) blockAliases.add(path + " <- " + modern);
            }
            replaced.add(path);
            if (group != null) coverage.get(group)[0]++;
            // Same dimensions do not prove renderer UV equivalence; no entity renderer model exists in the resource tree.
            assetDiff.add(diff(path, modern, diffCategory, "unknown"));
        }

        List<String> skippedItems = new ArrayList<>(), adaptedItems = new ArrayList<>(), heldItems = new ArrayList<>(), cubeItems = new ArrayList<>();
        Map<String,MappingConfig.ItemRule> rules = new TreeMap<>();
        // Identical geometry does not identify a protocol carrier or its CMD. Only explicit exceptions may bind a new item.
        config.select(profile.version()).forEach(r -> rules.put(r.source(), r));
        Map<String,List<MappingConfig.ItemRule>> grouped = new TreeMap<>();
        for (var rule : rules.values()) {
            if (!source.files.containsKey(rule.texture())) throw new IllegalArgumentException("Textura configurada não existe na 26.3: " + rule.texture());
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
                // 1.21.4+ only stitches item sprites from assets/minecraft/textures/item.
                String sprite = "minecraft:item/fixtextures_" + name;
                String path = "assets/minecraft/textures/item/fixtextures_" + name + ".png";
                pack.put(path, bytes);
                JsonObject meta = source.object(r.texture() + ".mcmeta");
                if (meta != null && meta.has("animation")) {
                    JsonObject adapted = new JsonObject(); adapted.add("animation", meta.get("animation")); put(pack, path + ".mcmeta", adapted);
                }

                boolean spear = "spear".equals(r.model());
                String handSource = "assets/minecraft/textures/item/" + name + "_in_hand.png";
                String handSprite = "minecraft:item/fixtextures_" + name + "_in_hand";
                String handPath = "assets/minecraft/textures/item/fixtextures_" + name + "_in_hand.png";
                boolean hasHand = spear && source.files.containsKey(handSource);
                if (hasHand) pack.put(handPath, source.files.get(handSource));

                String parent = "spear".equals(r.model()) ? "generated" : r.model();
                JsonObject normal = "generated".equals(r.model()) ? cubeItem(pack, target, name, r.texture(), profile.protocol()) : null;
                boolean cube = normal != null;
                if (cube) {
                    cubeItems.add(r.source());
                    pack.remove(path); pack.remove(path + ".mcmeta");
                }
                else normal = model(sprite, profile.protocol(), parent, false);
                if ("handheld".equals(r.model()) && "mace".equals(name)) {
                    JsonObject mace = source.object("assets/minecraft/models/item/handheld_mace.json");
                    if (mace != null && mace.has("display")) normal.add("display", mace.get("display").deepCopy());
                }
                put(pack, "assets/fixtextures/models/item/" + name + ".json", normal);
                if (spear && !hasHand) throw new IllegalArgumentException("Sprite de lança em mão ausente: " + handSource);
                if (hasHand) {
                    JsonObject held = model(handSprite, profile.protocol(), "generated", true);
                    put(pack, "assets/fixtextures/models/item/" + name + "_in_hand.json", held);
                    heldItems.add(r.source() + " -> " + r.substitute() + " (modelo de lança/held)");
                }
                adaptedItems.add(r.source() + " -> " + r.substitute() + " (" + (hasHand ? "lança em mão" : cube ? "bloco 3D" : "sprite aproximado") + ")");
                assetDiff.add(diff(r.texture(), path, "manual_exception", "unknown"));
            }
            if (profile.protocol() >= 769) modernItems(pack, target, carrier, entries);
            else legacyItems(pack, target, carrier, entries);
        }

        List<String> missingBlocks = new ArrayList<>();
        Set<String> coveredModern = new HashSet<>();
        for (Map<String,String> entry : assetDiff) if (entry.containsKey("modern_path") && !"incompatible".equals(entry.get("uv_status")))
            coveredModern.add(entry.get("modern_path"));
        for (Map<String,String> entry : assetDiff) if ("manual_exception".equals(entry.get("diff_category"))) coveredModern.add(entry.get("path"));
        for (String path : source.files.keySet()) {
            if (!path.startsWith("assets/minecraft/textures/") || !path.endsWith(".png") || target.files.containsKey(path)) continue;
            String group = reportCategory(path);
            if (group != null) coverage.get(group)[1]++;
            if (coveredModern.contains(path)) { if (group != null) coverage.get(group)[0]++; continue; }
            if (isBlock(path)) missingBlocks.add(path);
            else if (path.startsWith("assets/minecraft/textures/item/")) skippedItems.add(path + ": sem carrier/CMD explícito");
            assetDiff.add(diff(path, null, "new_asset", "unknown"));
        }
        put(pack, "pack.mcmeta", target.packMetadata(profile.protocol()));
        report.put("sourceVersion", "26.3"); report.put("clientVersion", profile.version()); report.put("protocol", profile.protocol());
        report.put("source", source.source);
        report.put("target_source", target.source);
        report.put("asset_diff", assetDiff);
        Map<String,Double> percentages = new TreeMap<>();
        coverage.forEach((name, counts) -> percentages.put(name, counts[1] == 0 ? 100.0 : Math.round(10000.0 * counts[0] / counts[1]) / 100.0));
        report.put("coverage_percent", percentages);
        report.put("updatedTextures", replaced); report.put("unchangedNoRecentTexture", unchanged); report.put("incompatibleUvLayout", incompatible);
        report.put("prismarineFallbackNot26_3", prismFallback);
        report.put("entityUpdated", entityUpdated); report.put("entitySkipped", entitySkipped);
        report.put("armorUpdated", armorUpdated); report.put("trimsUpdated", trimsUpdated); report.put("particlesUpdated", particlesUpdated);
        report.put("blockAliases", blockAliases);
        report.put("adaptedItems", adaptedItems); report.put("unadaptedItems", skippedItems); report.put("heldItems", heldItems);
        report.put("cubeItems", cubeItems);
        report.put("unmappedWorldBlocks", missingBlocks);
        report.put("worldBlockCatalogAvailable", false);
        report.put("conditionalItemModelsAvailable", profile.protocol() >= 477);
        report.put("limitations", List.of("Não homologado visualmente em cliente real.", "Índices de assets não informam qual carrier um tradutor de protocolo usa; estados colidentes não recebem texturas individuais.",
            "Itens com componentes customizados por outros plugins podem não corresponder aos seletores gerados.",
            "Sprites de itens novos são aproximações; não reproduzem modelos especiais, tintas ou mecânicas.",
            "A animação hardcoded de uso/ataque da lança continua a do item substituto; pose e textura são corrigidas pelo modelo.",
            profile.protocol() < 477 ? "Cliente sem CustomModelData: itens novos permanecem com aparência do substituto." : "Overrides só funcionam quando o emissor fornece o CMD configurado manualmente."));
        validate(pack, target);
        Files.createDirectories(output);
        String stem = "fixtextures-26.3-to-" + profile.version();
        Path temp = Files.createTempFile(output, stem, ".tmp");
        try { zip(pack, temp); }
        catch (Exception e) { Files.deleteIfExists(temp); throw e; }
        String hash = Downloads.sha1(Files.readAllBytes(temp));
        Path zip = output.resolve(stem + "-" + hash.substring(0,12) + ".zip");
        if (Files.isRegularFile(zip)) Files.delete(temp);
        else Files.move(temp, zip);
        Path reportPath = output.resolve(stem + "-report.json");
        if (!Files.isRegularFile(reportPath) || !Json.GSON.toJson(Json.read(reportPath, JsonObject.class)).equals(Json.GSON.toJson(report)))
            Json.write(reportPath, report);
        return new Result(zip, hash, report);
    }

    private String modernPath(String old, Profiles.Profile profile) {
        if (config.textureAliases().containsKey(old)) return config.textureAliases().get(old);
        if (source.files.containsKey(old)) return old;
        String explicit = prismarine == null ? null : prismarine.aliases.get(old);
        if (explicit != null) return explicit;
        String flattened = old.replace("/textures/blocks/", "/textures/block/").replace("/textures/items/", "/textures/item/");
        if (source.files.containsKey(flattened)) return flattened;
        return null;
    }

    static boolean inScope(String path) {
        return path.matches("assets/minecraft/textures/(?:blocks?|items?)/.*")
            || (path.matches("assets/minecraft/textures/entity/.*") && !path.contains("/entity/player/"))
            || path.matches("assets/minecraft/textures/trims/.*")
            || path.matches("assets/minecraft/textures/palettes/.*")
            || path.matches("assets/minecraft/textures/models/armor/.*")
            || path.matches("assets/minecraft/textures/particle/.*");
    }

    private static boolean isEntity(String path) { return path.matches("assets/minecraft/textures/entity/.*"); }
    private static boolean isArmor(String path) { return path.matches("assets/minecraft/textures/(?:entity/equipment|models/armor)/.*"); }
    private static boolean isTrim(String path) { return path.matches("assets/minecraft/textures/(?:trims|palettes)/.*"); }
    private static boolean isParticle(String path) { return path.matches("assets/minecraft/textures/particle/.*"); }
    private static boolean isBlock(String path) { return path.matches("assets/minecraft/textures/blocks?/.*"); }
    private static boolean isPalette(String path) { return path.matches("assets/minecraft/textures/(?:palettes|trims/color_palettes)/.*"); }

    private static String reportCategory(String path) {
        if (isTrim(path)) return "trims";
        if (isParticle(path)) return "particles";
        if (isEntity(path)) return "entities";
        if (isBlock(path)) return "blocks";
        if (path.matches("assets/minecraft/textures/items?/.*")) return "items";
        return null;
    }

    private static Map<String,String> diff(String path, String modern, String category, String uv) {
        Map<String,String> entry = new LinkedHashMap<>();
        entry.put("path", path);
        if (modern != null) entry.put("modern_path", modern);
        entry.put("diff_category", category);
        entry.put("uv_status", uv);
        return entry;
    }

    private boolean layoutCompatible(String path, byte[] old, byte[] recent) throws IOException {
        if (old == null) return true;
        if (isEntity(path) || isTrim(path)) return compatibleScaled(old, recent, path);
        return compatibleLayout(old, recent, path);
    }

    static boolean compatibleLayout(byte[] old, byte[] recent, String path) throws IOException {
        var a = ImageIO.read(new ByteArrayInputStream(old)); var b = ImageIO.read(new ByteArrayInputStream(recent));
        if (a == null || b == null) throw new IOException("PNG inválido: " + path);
        // Preserve UV layouts, including entity/block renderers. A changed layout requires an explicit converter.
        return (long)a.getWidth() * b.getHeight() == (long)b.getWidth() * a.getHeight();
    }

    static boolean compatibleScaled(byte[] old, byte[] recent, String path) throws IOException {
        var a = ImageIO.read(new ByteArrayInputStream(old)); var b = ImageIO.read(new ByteArrayInputStream(recent));
        if (a == null || b == null) throw new IOException("PNG inválido: " + path);
        if (a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()) return true;
        // HD packs are safe when both axes scale by the same integer factor.
        if (a.getWidth() == 0 || a.getHeight() == 0) return false;
        if (b.getWidth() % a.getWidth() == 0 && b.getHeight() % a.getHeight() == 0) {
            return b.getWidth() / a.getWidth() == b.getHeight() / a.getHeight();
        }
        if (a.getWidth() % b.getWidth() == 0 && a.getHeight() % b.getHeight() == 0) {
            return a.getWidth() / b.getWidth() == a.getHeight() / b.getHeight();
        }
        return false;
    }

    private JsonObject cubeItem(Map<String,byte[]> pack, Assets target, String name, String texture, int protocol) {
        if (!texture.startsWith("assets/minecraft/textures/block/")) return null;
        JsonObject sourceModel = source.object("assets/minecraft/models/block/" + name + ".json");
        if (sourceModel == null || !sourceModel.has("parent") || !sourceModel.has("textures")) return null;
        String parent = sourceModel.get("parent").getAsString().replace("minecraft:", "");
        if (!simpleCubeTemplate(target, parent)) return null;
        JsonObject textures = sourceModel.getAsJsonObject("textures").deepCopy();
        Map<String,byte[]> additions = new TreeMap<>();
        for (var entry : textures.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) value = value.getAsJsonObject().get("sprite");
            if (value == null || !value.isJsonPrimitive()) return null;
            String id = value.getAsString();
            if (id.startsWith("#")) continue;
            String relative = id.replace("minecraft:", "");
            if (!relative.startsWith("block/")) return null;
            String original = "assets/minecraft/textures/" + relative + ".png";
            byte[] data = source.files.get(original);
            if (data == null) return null;
            String dest = "block/fixtextures_" + name + "_" + entry.getKey();
            String path = "assets/minecraft/textures/" + dest + ".png";
            additions.put(path, data);
            JsonObject metadata = source.object(original + ".mcmeta");
            if (metadata != null && metadata.has("animation")) {
                JsonObject animation = new JsonObject(); animation.add("animation", metadata.get("animation").deepCopy());
                put(additions, path + ".mcmeta", animation);
            }
            if (protocol >= 775) {
                JsonObject ref = new JsonObject(); ref.addProperty("sprite", "minecraft:" + dest); entry.setValue(ref);
            } else entry.setValue(new JsonPrimitive("minecraft:" + dest));
        }
        pack.putAll(additions);
        JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:" + parent); model.add("textures", textures);
        return model;
    }

    /** Resolve the actual target template instead of enumerating block names or template aliases. */
    private static boolean simpleCubeTemplate(Assets target, String parent) {
        Set<String> visited = new HashSet<>();
        String current = parent;
        while (current.startsWith("block/") && visited.add(current) && visited.size() <= 8) {
            JsonObject model = target.object("assets/minecraft/models/" + current + ".json");
            if (model == null) return false;
            if (model.has("elements")) {
                JsonArray elements = model.getAsJsonArray("elements");
                if (elements.size() != 1) return false;
                JsonObject element = elements.get(0).getAsJsonObject();
                if (!element.has("from") || !element.has("to") || element.has("rotation")) return false;
                JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
                if (from.size() != 3 || to.size() != 3) return false;
                for (int i=0; i<3; i++) if (from.get(i).getAsDouble() != 0 || to.get(i).getAsDouble() != 16) return false;
                return element.has("faces") && element.getAsJsonObject("faces").size() == 6;
            }
            if (!model.has("parent")) return false;
            current = model.get("parent").getAsString().replace("minecraft:", "");
        }
        return false;
    }

    private static JsonObject model(String sprite, int protocol, String parent, boolean held) {
        JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:item/" + parent);
        if (held) { model.addProperty("gui_light", "front"); model.add("display", SPEAR_DISPLAY.deepCopy()); }
        JsonObject textures = new JsonObject();
        if (protocol >= 775) {
            JsonObject reference = new JsonObject(); reference.addProperty("sprite", sprite); textures.add("layer0", reference);
        } else textures.addProperty("layer0", sprite);
        model.add("textures", textures);
        return model;
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
            entry.add("when", identifiers); entry.add("model", modernModelRef(r)); cases.add(entry); select.add("cases", cases); model = select;
        }
        root.add("model", model); put(pack, path, root);
    }

    private static JsonObject modernModelRef(MappingConfig.ItemRule rule) {
        String name = rule.source().replace("minecraft:", "");
        if (!"spear".equals(rule.model())) return modelRef(rule.source());
        JsonObject select = new JsonObject(); select.addProperty("type", "minecraft:select"); select.addProperty("property", "minecraft:display_context");
        JsonArray when = new JsonArray(); when.add("gui"); when.add("ground"); when.add("fixed");
        JsonObject entry = new JsonObject(); entry.add("when", when); entry.add("model", ref("fixtextures:item/" + name));
        JsonArray cases = new JsonArray(); cases.add(entry); select.add("cases", cases);
        select.add("fallback", ref("fixtextures:item/" + name + "_in_hand"));
        return select;
    }

    private static JsonObject modelRef(String source) {
        return ref("fixtextures:item/" + source.replace("minecraft:", ""));
    }

    private static JsonObject ref(String model) {
        JsonObject ref = new JsonObject(); ref.addProperty("type", "minecraft:model"); ref.addProperty("model", model); return ref;
    }

    static void legacyItems(Map<String,byte[]> pack, Assets target, String carrier, List<MappingConfig.ItemRule> rules) {
        String path = "assets/minecraft/models/item/" + carrier + ".json";
        JsonObject root = target.object(path).deepCopy();
        String fallback = "fixtextures:item/base/" + carrier;
        put(pack, "assets/fixtextures/models/item/base/" + carrier + ".json", root.deepCopy());
        JsonArray overrides = root.has("overrides") ? root.getAsJsonArray("overrides") : new JsonArray();
        rules.sort(Comparator.comparingInt(MappingConfig.ItemRule::customModelData)); Set<Integer> ids = new HashSet<>();
        for (var rule : rules) {
            int cmd = rule.customModelData(); if (!ids.add(cmd)) throw new IllegalArgumentException("CustomModelData duplicado: " + carrier + ":" + cmd);
            String name = rule.source().replace("minecraft:", "");
            String model = "fixtextures:item/" + name + ("spear".equals(rule.model()) ? "_in_hand" : "");
            // Legacy predicates mean >=, not equality. Reset after this exact integer.
            overrides.add(override(cmd, model));
            overrides.add(override(cmd + 1, fallback));
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
            if (path.contains("..") || path.startsWith("/") || path.contains("\\")) throw new IOException("Caminho inválido: " + path);
            if (path.endsWith(".png") && ImageIO.read(new ByteArrayInputStream(entry.getValue())) == null) throw new IOException("PNG inválido: " + path);
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
                    if (id.startsWith("fixtextures:")) {
                        String path = "assets/fixtextures/models/" + id.substring(id.indexOf(':')+1) + ".json";
                        if (!pack.containsKey(path)) throw new IOException("Modelo não resolvido: " + id);
                    }
                }
                if (e.getKey().equals("textures") && e.getValue().isJsonObject()) {
                    for (var texture : e.getValue().getAsJsonObject().entrySet()) {
                        JsonElement ref = texture.getValue();
                        if (ref.isJsonObject()) ref = ref.getAsJsonObject().get("sprite");
                        if (ref != null && ref.isJsonPrimitive()) {
                            String id = ref.getAsString();
                            if (id.startsWith("fixtextures:")) {
                                String path = "assets/fixtextures/textures/" + id.substring(id.indexOf(':')+1) + ".png";
                                if (!pack.containsKey(path)) throw new IOException("Textura não resolvida: " + id);
                            }
                        }
                    }
                }
                validateRefs(e.getValue(), pack, target);
            }
        }
    }
}
