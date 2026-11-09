package dev.fixtextures.generator;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import dev.fixtextures.core.*;
import static org.junit.jupiter.api.Assertions.*;

class PackGenerationTest {
    @TempDir Path dir;
    private byte[] png(int w,int h) throws IOException {
        var out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB),"png",out); return out.toByteArray();
    }
    @Test void createsDeterministicZipAndValidatesReferences() throws Exception {
        Map<String,byte[]> pack=new TreeMap<>();
        PackBuilder.put(pack,"pack.mcmeta",JsonParser.parseString("{\"pack\":{\"pack_format\":46,\"description\":\"test\"}}"));
        pack.put("assets/fixtextures/textures/item/test.png",png(16,16));
        PackBuilder.put(pack,"assets/fixtextures/models/item/test.json",JsonParser.parseString("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"fixtextures:item/test\"}}"));
        PackBuilder.put(pack,"assets/minecraft/items/stone.json",JsonParser.parseString("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"fixtextures:item/test\"}}"));
        PackBuilder.validate(pack,new Assets());
        Path a=dir.resolve("a.zip"),b=dir.resolve("b.zip");PackBuilder.zip(pack,a);PackBuilder.zip(pack,b);
        assertArrayEquals(Files.readAllBytes(a),Files.readAllBytes(b));
        try(var zip=new ZipFile(a.toFile())) { assertNotNull(zip.getEntry("pack.mcmeta"));assertEquals(4,zip.size()); }
        pack.remove("assets/fixtextures/textures/item/test.png");
        assertThrows(IOException.class,() -> PackBuilder.validate(pack,new Assets()));
    }
    @Test void rejectsChangedUvsAndInvalidImages() throws Exception {
        assertTrue(PackBuilder.compatibleLayout(png(16,16),png(32,32),"x"));
        assertFalse(PackBuilder.compatibleLayout(png(64,64),png(64,32),"chest"));
        assertThrows(IOException.class,() -> PackBuilder.compatibleLayout(png(16,16),new byte[]{1},"x"));
    }
    @Test void metadataUsesTargetFormatNotSourceFormat() {
        Assets target=new Assets();
        assertEquals(1,target.packMetadata(47).getAsJsonObject("pack").get("pack_format").getAsInt());
        target.files.put("version.json","{\"pack_version\":4}".getBytes());
        assertEquals(4,target.packMetadata(477).getAsJsonObject("pack").get("pack_format").getAsInt());
        target.files.put("version.json","{\"pack_version\":{\"resource\":46}}".getBytes());
        assertEquals(46,target.packMetadata(769).getAsJsonObject("pack").get("pack_format").getAsInt());
        target.files.put("version.json","{\"pack_version\":{\"resource_major\":88,\"resource_minor\":0}}".getBytes());
        var modern=target.packMetadata(776).getAsJsonObject("pack");
        assertEquals(88,modern.getAsJsonArray("min_format").get(0).getAsInt());assertFalse(modern.has("pack_format"));
        assertThrows(IllegalArgumentException.class,() -> new Assets().packMetadata(776));
    }
    @Test void mappingRespectsExplicitRulesAndWordBoundaries() {
        var diff=JsonParser.parseString("{\"materials\":{\"spear\":\"sword\"},\"items\":{\"wooden_spear\":\"stick\"}}").getAsJsonObject();
        Set<String> available=Set.of("iron_sword","wooden_sword","stick");
        assertEquals("iron_sword",ViaMappings.resolve("iron_spear",diff,available));
        assertEquals("stick",ViaMappings.resolve("wooden_spear",diff,available));
        assertNull(ViaMappings.resolve("ironspear",diff,available));
        assertNull(ViaMappings.resolve("diamond_spear",diff,available));
    }
    @Test void validatesPathsAndBrokenModelReferences() {
        Map<String,byte[]> p=new TreeMap<>();PackBuilder.put(p,"pack.mcmeta",new JsonObject());
        PackBuilder.put(p,"assets/minecraft/items/a.json",JsonParser.parseString("{\"model\":\"fixtextures:item/missing\"}"));
        assertThrows(IOException.class,() -> PackBuilder.validate(p,new Assets()));
        p.remove("assets/minecraft/items/a.json");p.put("../evil",new byte[0]);
        assertThrows(IOException.class,() -> PackBuilder.validate(p,new Assets()));
    }
    private MappingConfig itemRule(String client, Integer cmd, String original) {
        var rule=new MappingConfig.ItemRule("minecraft:test_spear","minecraft:iron_sword",List.of(client),
            "assets/minecraft/textures/item/test_spear.png","handheld",cmd,original,0);
        var config=new MappingConfig(1,"26.3","a".repeat(40),"b".repeat(40),false,Map.of(),List.of(rule));
        config.validate(); return config;
    }
    @Test void legacyItemOverrideKeepsBaseModelAsFallback() throws Exception {
        Assets source=new Assets(),target=new Assets();
        source.files.put("assets/minecraft/textures/item/test_spear.png",png(16,16));
        target.files.put("assets/minecraft/models/item/iron_sword.json",
            "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"minecraft:item/iron_sword\"}}".getBytes());
        target.files.put("version.json","{\"pack_version\":22}".getBytes());
        var result=new PackBuilder(source,itemRule("1.20.4",1234,"test_spear"),null,null)
            .build(Profiles.version("1.20.4"),target,dir);
        assertFalse((Boolean)result.report().get("worldBlockCatalogAvailable"));
        try(var zip=new ZipFile(result.zip().toFile())) {
            assertNotNull(zip.getEntry("assets/minecraft/textures/item/fixtextures_test_spear.png"));
            var model=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/minecraft/models/item/iron_sword.json")).readAllBytes())).getAsJsonObject();
            var overrides=model.getAsJsonArray("overrides");
            assertEquals(1,overrides.size());
            assertEquals(1234,overrides.get(0).getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt());
        }
    }
    @Test void modernItemSelectPreservesOriginalFallbackAndUsesMinecraftSpritePath() throws Exception {
        Assets source=new Assets(),target=new Assets();
        source.files.put("assets/minecraft/textures/item/test_spear.png",png(16,16));
        target.files.put("assets/minecraft/items/iron_sword.json",
            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/iron_sword\"}}".getBytes());
        target.files.put("version.json","{\"pack_version\":46}".getBytes());
        var result=new PackBuilder(source,itemRule("1.21.4",null,"test_spear"),null,null)
            .build(Profiles.version("1.21.4"),target,dir);
        try(var zip=new ZipFile(result.zip().toFile())) {
            assertNotNull(zip.getEntry("assets/minecraft/textures/item/fixtextures_test_spear.png"));
            var definition=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/minecraft/items/iron_sword.json")).readAllBytes())).getAsJsonObject();
            var select=definition.getAsJsonObject("model");
            assertEquals("minecraft:select",select.get("type").getAsString());
            assertEquals("minecraft:item/iron_sword",select.getAsJsonObject("fallback").get("model").getAsString());
            assertEquals("fixtextures:item/test_spear",select.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model").get("model").getAsString());
            var layer0=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/fixtextures/models/item/test_spear.json")).readAllBytes()))
                .getAsJsonObject().getAsJsonObject("textures").get("layer0");
            assertEquals("minecraft:item/fixtextures_test_spear",layer0.getAsString());
        }
    }
    @Test void modernSpriteReferenceUsesObjectFrom261() throws Exception {
        Assets source=new Assets(),target=new Assets();
        source.files.put("assets/minecraft/textures/item/test_spear.png",png(16,16));
        target.files.put("assets/minecraft/items/iron_sword.json",
            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/iron_sword\"}}".getBytes());
        target.files.put("version.json","{\"pack_version\":{\"resource_major\":63,\"resource_minor\":0}}".getBytes());
        var result=new PackBuilder(source,itemRule("26.1.2",null,"test_spear"),null,null)
            .build(Profiles.version("26.1.2"),target,dir);
        try(var zip=new ZipFile(result.zip().toFile())) {
            var sprite=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/fixtextures/models/item/test_spear.json")).readAllBytes()))
                .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("layer0").get("sprite").getAsString();
            assertEquals("minecraft:item/fixtextures_test_spear",sprite);
        }
    }
    @Test void mapsMovedEquipmentAndEntitiesButRejectsChangedDimensions() throws Exception {
        var config = new MappingConfig(1,"26.3","a".repeat(40),"b".repeat(40),false,Map.of(),List.of());
        Assets source = new Assets(), target = new Assets();
        Map<String,String> aliases = Map.of(
            "models/armor/iron_layer_1.png", "entity/equipment/humanoid/iron.png",
            "entity/elytra.png", "entity/equipment/wings/elytra.png",
            "entity/beacon_beam.png", "entity/beacon/beacon_beam.png",
            "entity/panda/aggressive_panda.png", "entity/panda/panda_aggressive.png");
        String prefix = "assets/minecraft/textures/";
        aliases.forEach((old, recent) -> {
            try { target.files.put(prefix + old, png(64,32)); source.files.put(prefix + recent, png(64,32)); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        });
        target.files.put(prefix + "entity/cow/cow.png", png(64,32));
        source.files.put(prefix + "entity/cow/cow_temperate.png", png(64,64));
        target.files.put("version.json", "{\"pack_version\":22}".getBytes());
        var result = new PackBuilder(source,config,null,null).build(Profiles.version("1.20.4"),target,dir);
        try (var zip = new ZipFile(result.zip().toFile())) {
            for (String old : aliases.keySet()) assertNotNull(zip.getEntry(prefix + old), old);
            assertNull(zip.getEntry(prefix + "entity/cow/cow.png"));
        }
        assertEquals(1, ((List<?>)result.report().get("incompatibleUvLayout")).size());
    }

    @Test void buildsPreFlatteningPackWithoutTreatingLegacyNumericRegistryAsNames() throws Exception {
        var config=new MappingConfig(1,"26.3","a".repeat(40),"b".repeat(40),false,Map.of(),List.of());
        Assets source=new Assets(),target=new Assets();
        source.files.put("assets/minecraft/textures/block/stone.png",png(16,16));
        target.files.put("assets/minecraft/textures/blocks/stone.png",png(16,16));
        var builder=new PackBuilder(source,config,new Prismarine(Map.of(),Map.of()),null);
        var result=builder.build(Profiles.version("1.12"),target,dir);
        assertEquals(false,result.report().get("worldBlockCatalogAvailable"));
        assertEquals(false,result.report().get("conditionalItemModelsAvailable"));
        assertEquals(1,((List<?>)result.report().get("updatedTextures")).size());
        try(var zip=new ZipFile(result.zip().toFile())) {
            assertNotNull(zip.getEntry("assets/minecraft/textures/blocks/stone.png"));
            var meta=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes())).getAsJsonObject();
            assertEquals(3,meta.getAsJsonObject("pack").get("pack_format").getAsInt());
        }
    }
}
