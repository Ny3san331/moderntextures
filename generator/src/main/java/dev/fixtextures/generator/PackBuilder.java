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

    private static final Set<String> ENTITY_SAFE_EXCLUSIONS = Set.of("player");
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
    private final ViaMappings via;
    private final Map<String,List<String>> entityByBasename = new HashMap<>();

    PackBuilder(Assets source, MappingConfig config, Prismarine prismarine, ViaMappings via) {
        this.source=source; this.config=config; this.prismarine=prismarine; this.via=via;
        for (String path : source.files.keySet()) {
            if (!path.startsWith("assets/minecraft/textures/entity/") || !path.endsWith(".png") || path.contains("/player/")) continue;
            String base = path.substring(path.lastIndexOf('/') + 1);
            entityByBasename.computeIfAbsent(base, k -> new ArrayList<>()).add(path);
        }
    }

    Result build(Profiles.Profile profile, Assets target, Path output) throws Exception {
        Map<String,byte[]> pack = new TreeMap<>();
        Map<String,Object> report = new LinkedHashMap<>();
        List<String> replaced = new ArrayList<>(), unchanged = new ArrayList<>(), incompatible = new ArrayList<>(), prismFallback = new ArrayList<>();
        List<String> entityUpdated = new ArrayList<>(), entitySkipped = new ArrayList<>();
        List<String> armorUpdated = new ArrayList<>(), trimsUpdated = new ArrayList<>(), particlesUpdated = new ArrayList<>();
        List<String> blockAliases = new ArrayList<>();

        for (String path : target.textures()) {
            if (!inScope(path)) continue;
            if (path.contains("/textures/entity/player/")) continue;
            String modern = modernPath(path, profile);
            if (modern == null) { unchanged.add(path); continue; }
            byte[] texture = source.files.get(modern);
            if (texture == null) { texture = prismarine == null ? null : prismarine.fallback.get(modern); if (texture != null) prismFallback.add(path); }
            if (texture == null) { unchanged.add(path); continue; }

            String category = category(path);
            if (isEntity(path) && profile.protocol() < 766 && path.contains("/entity/wolf/")) {
                entitySkipped.add(path + " <- " + modern + " (UV do lobo mudou em 1.20.5)");
                continue;
            }
            if (!layoutCompatible(path, target.files.get(path), texture)) { incompatible.add(path + " <- " + modern); continue; }

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
        }

        List<String> skippedItems = new ArrayList<>(), adaptedItems = new ArrayList<>(), heldItems = new ArrayList<>(), cubeItems = new ArrayList<>();
        Map<String,MappingConfig.ItemRule> rules = new TreeMap<>();
        if (config.autoItems() && profile.protocol() >= 477 && via != null) {
            Set<String> available = via.registry(profile.registry(), "items");
            for (String item : via.registry("26.3", "items")) {
                if (available.contains(item)) continue;
                ViaMappings.Translation mapped = via.item(item, profile.registry());
                String sprite = "assets/minecraft/textures/item/" + item + ".png";
                String texture = source.files.containsKey(sprite) ? sprite : "assets/minecraft/textures/block/" + item + ".png";
                boolean spear = source.files.containsKey("assets/minecraft/textures/item/" + item + "_in_hand.png");
                JsonObject originalModel = source.object("assets/minecraft/models/item/" + item + ".json");
                String originalParent = originalModel != null && originalModel.has("parent") ? originalModel.get("parent").getAsString().replace("minecraft:", "") : "";
                boolean handheld = Set.of("item/handheld", "item/handheld_mace").contains(originalParent);
                if (mapped == null || !source.files.containsKey(texture) || (profile.protocol() < 769 && mapped.cmd() == null)) {
                    skippedItems.add(item + ": sem tradução verificável ou sprite simples"); continue;
                }
                rules.put("minecraft:" + item, new MappingConfig.ItemRule("minecraft:" + item, "minecraft:" + mapped.substitute(),
                    List.of(profile.version()), texture, spear ? "spear" : handheld ? "handheld" : "generated", mapped.cmd(), mapped.original(), 0));
            }
        }
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
        report.put("prismarineFallbackNot26_3", prismFallback);
        report.put("entityUpdated", entityUpdated); report.put("entitySkipped", entitySkipped);
        report.put("armorUpdated", armorUpdated); report.put("trimsUpdated", trimsUpdated); report.put("particlesUpdated", particlesUpdated);
        report.put("blockAliases", blockAliases);
        report.put("adaptedItems", adaptedItems); report.put("unadaptedItems", skippedItems); report.put("heldItems", heldItems);
        report.put("cubeItems", cubeItems);
        report.put("newWorldBlocksUsingViaFallback", missingBlocks);
        report.put("worldBlockCatalogAvailable", namedBlockCatalog);
        report.put("conditionalItemModelsAvailable", profile.protocol() >= 477);
        report.put("limitations", List.of("Não homologado visualmente em cliente real.", "Blocos novos usam tradução Via; estados colidentes não recebem texturas individuais.",
            "Itens com componentes customizados por outros plugins podem não corresponder aos seletores gerados.",
            "Sprites de itens novos são aproximações; não reproduzem modelos especiais, tintas ou mecânicas.",
            "A animação hardcoded de uso/ataque da lança continua a do item substituto; pose e textura são corrigidas pelo modelo.",
            profile.protocol() < 477 ? "Cliente sem CustomModelData: itens novos permanecem com aparência do substituto." : "Requer pass-original-item-name-to-resource-packs=true no ViaBackwards 5.12.2."));
        validate(pack, target);
        Files.createDirectories(output);
        String stem = "fixtextures-26.3-to-" + profile.version();
        Path temp = Files.createTempFile(output, stem, ".tmp");
        try { zip(pack, temp); }
        catch (Exception e) { Files.deleteIfExists(temp); throw e; }
        String hash = Downloads.sha1(Files.readAllBytes(temp));
        Path zip = output.resolve(stem + "-" + hash.substring(0,12) + ".zip");
        Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING);
        Json.write(output.resolve(stem + "-report.json"), report);
        return new Result(zip, hash, report);
    }

    private String modernPath(String old, Profiles.Profile profile) {
        if (config.textureAliases().containsKey(old)) return config.textureAliases().get(old);
        String armor = armorAlias(old);
        if (armor != null) return armor;
        String equipment = equipmentAlias(old);
        if (equipment != null && source.files.containsKey(equipment)) return equipment;
        String trim = trimAlias(old);
        if (trim != null && source.files.containsKey(trim)) return trim;
        String entity = entityAlias(old);
        if (entity != null && source.files.containsKey(entity)) return entity;
        String block = blockAlias(old);
        if (block != null && source.files.containsKey(block)) return block;
        if (source.files.containsKey(old)) return old;
        String explicit = prismarine == null ? null : prismarine.aliases.get(old);
        if (explicit != null) return explicit;
        String flattened = old.replace("/textures/blocks/", "/textures/block/").replace("/textures/items/", "/textures/item/");
        if (source.files.containsKey(flattened)) return flattened;
        String generic = genericEntityAlias(old);
        if (generic != null && source.files.containsKey(generic)) return generic;
        return flattened.equals(old) ? null : flattened;
    }

    private static String armorAlias(String old) {
        var m = java.util.regex.Pattern.compile("assets/minecraft/textures/models/armor/([a-z0-9_]+)_layer_([12])(_overlay)?\\.png").matcher(old);
        if (!m.matches()) return null;
        String material = m.group(1);
        String layer = m.group(2);
        boolean overlay = m.group(3) != null;
        if (overlay) material = "leather_overlay";
        else if (material.equals("turtle")) material = "turtle_scute";
        return "assets/minecraft/textures/entity/equipment/" + ("1".equals(layer) ? "humanoid" : "humanoid_leggings") + "/" + material + ".png";
    }

    private static String equipmentAlias(String old) {
        String prefix = "assets/minecraft/textures/entity/";
        if (!old.startsWith(prefix)) return null;
        String rel = old.substring(prefix.length());
        if (rel.startsWith("horse/armor/horse_armor_"))
            return prefix + "equipment/horse_body/" + rel.substring("horse/armor/horse_armor_".length());
        if (rel.startsWith("llama/decor/"))
            return prefix + "equipment/llama_body/" + rel.substring("llama/decor/".length());
        return switch (rel) {
            case "elytra.png" -> prefix + "equipment/wings/elytra.png";
            case "pig/pig_saddle.png" -> prefix + "equipment/pig_saddle/saddle.png";
            case "strider/strider_saddle.png" -> prefix + "equipment/strider_saddle/saddle.png";
            default -> null;
        };
    }

    private static String trimAlias(String old) {
        if (old.startsWith("assets/minecraft/textures/trims/models/armor/")) {
            String name = old.substring(old.lastIndexOf('/') + 1).replace(".png", "");
            boolean leggings = name.endsWith("_leggings");
            if (leggings) name = name.substring(0, name.length() - "_leggings".length());
            return "assets/minecraft/textures/trims/entity/" + (leggings ? "humanoid_leggings" : "humanoid") + "/" + name + ".png";
        }
        if (old.startsWith("assets/minecraft/textures/trims/color_palettes/")) {
            String name = old.substring(old.lastIndexOf('/') + 1);
            return "assets/minecraft/textures/palettes/" + (name.equals("trim_palette.png") ? "trim_base.png" : "trim/" + name);
        }
        return null;
    }

    private static String entityAlias(String old) {
        if (!old.startsWith("assets/minecraft/textures/entity/")) return null;
        if (old.contains("/player/")) return null;
        String rel = old.substring("assets/minecraft/textures/entity/".length());
        return switch (rel) {
            case "armorstand/wood.png" -> "assets/minecraft/textures/entity/armorstand/armorstand.png";
            case "chicken.png" -> "assets/minecraft/textures/entity/chicken/chicken_temperate.png";
            case "cow/red_mooshroom.png" -> "assets/minecraft/textures/entity/mooshroom/mooshroom_red.png";
            case "cow/brown_mooshroom.png" -> "assets/minecraft/textures/entity/mooshroom/mooshroom_brown.png";
            case "fox/snow_fox.png" -> "assets/minecraft/textures/entity/fox/fox_snow.png";
            case "fox/snow_fox_sleep.png" -> "assets/minecraft/textures/entity/fox/fox_snow_sleep.png";
            case "sheep/sheep_fur.png" -> "assets/minecraft/textures/entity/sheep/sheep_wool.png";
            case "cow/cow.png" -> "assets/minecraft/textures/entity/cow/cow_temperate.png";
            case "cow/cow_baby.png" -> "assets/minecraft/textures/entity/cow/cow_temperate_baby.png";
            case "pig/pig.png" -> "assets/minecraft/textures/entity/pig/pig_temperate.png";
            case "pig/pig_baby.png" -> "assets/minecraft/textures/entity/pig/pig_temperate_baby.png";
            case "chicken/chicken.png" -> "assets/minecraft/textures/entity/chicken/chicken_temperate.png";
            case "chicken/chicken_baby.png" -> "assets/minecraft/textures/entity/chicken/chicken_temperate_baby.png";
            default -> null;
        };
    }

    private String genericEntityAlias(String old) {
        if (!old.startsWith("assets/minecraft/textures/entity/")) return null;
        if (old.contains("/player/")) return null;
        String basename = old.substring(old.lastIndexOf('/') + 1);
        List<String> candidates = entityByBasename.getOrDefault(basename, List.of());
        if (candidates.size() == 1) return candidates.getFirst();
        if (old.contains("/panda/") && basename.endsWith("_panda.png"))
            return "assets/minecraft/textures/entity/panda/panda_" + basename.replace("_panda.png", ".png");
        String folderPath = old.substring(0, old.lastIndexOf('/'));
        String base = old.substring(old.lastIndexOf('/') + 1, old.length() - 4);
        String folder = folderPath.substring(folderPath.lastIndexOf('/') + 1);
        if (base.startsWith(folder + "_")) return null;
        return folderPath + "/" + folder + "_" + base + ".png";
    }

    private static String blockAlias(String old) {
        if (!old.contains("/textures/block")) return null;
        return switch (old.substring(old.lastIndexOf('/') + 1)) {
            case "grass_path_side.png" -> old.replace("grass_path_side.png", "dirt_path_side.png");
            case "grass_path_top.png" -> old.replace("grass_path_top.png", "dirt_path_top.png");
            case "chain.png" -> old.replace("chain.png", "iron_chain.png");
            case "purpur_pillar.png" -> old.replace("purpur_pillar.png", "purpur_pillar_side.png");
            case "quartz_pillar.png" -> old.replace("quartz_pillar.png", "quartz_pillar_side.png");
            default -> null;
        };
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

    private static String category(String path) {
        if (isTrim(path)) return "trim";
        if (isArmor(path)) return "armor";
        if (isParticle(path)) return "particle";
        if (isEntity(path)) return "entity";
        return "block/item";
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
        if (!Set.of("block/cube_all", "block/cube_column", "block/cube_bottom_top").contains(parent)
            || !target.files.containsKey("assets/minecraft/models/" + parent + ".json")) return null;
        JsonObject textures = sourceModel.getAsJsonObject("textures").deepCopy();
        Map<String,byte[]> additions = new TreeMap<>();
        for (var entry : textures.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) return null;
            String id = entry.getValue().getAsString();
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
