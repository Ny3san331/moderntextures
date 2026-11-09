package dev.fixtextures.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityTest {
    private static final String HASH="a".repeat(40);
    private static PackManifest.Pack pack(String version) {
        var p=Profiles.version(version); return new PackManifest.Pack(version,p.protocol(),"pack-"+version+".zip",HASH);
    }
    @Test void detectsReleaseProtocolsAndRejectsUnknownSnapshotsAndNative() {
        assertEquals("1.7.10",Profiles.protocol(5).orElseThrow().version());
        assertEquals("1.21.10",Profiles.protocol(773).orElseThrow().version());
        assertEquals("26.2",Profiles.protocol(776).orElseThrow().version());
        assertTrue(Profiles.protocol(-1).isEmpty());
        assertTrue(Profiles.protocol((1<<30)|773).isEmpty());
        assertTrue(Profiles.protocol(777).isEmpty());
        assertEquals(Profiles.ALL.size(),Profiles.ALL.stream().map(Profiles.Profile::protocol).distinct().count());
    }
    @Test void selectsOnlyExactProtocolNeverNearestPack() {
        var manifest=new PackManifest(1,"26.3",List.of(pack("1.21.10"),pack("26.2"))); manifest.validate();
        assertEquals("1.21.10",manifest.select(773).orElseThrow().clientVersion());
        assertTrue(manifest.select(774).isEmpty());
        assertTrue(manifest.select(-1).isEmpty());
    }
    @Test void rejectsDuplicateProtocolsBadHashesAndTraversal() {
        assertThrows(IllegalArgumentException.class,() -> new PackManifest(1,"26.3",List.of(pack("26.2"),pack("26.2"))).validate());
        assertThrows(IllegalArgumentException.class,() -> new PackManifest(1,"26.3",List.of(new PackManifest.Pack("26.2",776,"../x.zip",HASH))).validate());
        assertThrows(IllegalArgumentException.class,() -> new PackManifest(1,"26.3",List.of(new PackManifest.Pack("26.2",776,"x.zip","bad"))).validate());
        assertThrows(IllegalArgumentException.class,() -> PackManifest.baseUrl("file:///x"));
        assertThrows(IllegalArgumentException.class,() -> PackManifest.baseUrl("https://example.com/packs?token=x"));
        assertEquals("https://example.com/packs/a.zip",PackManifest.baseUrl("https://example.com/packs").resolve("a.zip").toString());
    }
    @Test void acceptedAndDownloadedAreNotLoadedAndLateEventsDoNotReviveFailure() {
        assertTrue(PackState.SENT.receive("ACCEPTED").pending());
        assertTrue(PackState.ACCEPTED.receive("DOWNLOADED").pending());
        assertEquals(PackState.DOWNLOADED,PackState.DOWNLOADED.receive("ACCEPTED"));
        assertEquals(PackState.LOADED,PackState.DOWNLOADED.receive("SUCCESSFULLY_LOADED"));
        assertEquals(PackState.DECLINED,PackState.SENT.receive("DECLINED").receive("SUCCESSFULLY_LOADED"));
        assertEquals(PackState.LOADED,PackState.TIMED_OUT.receive("SUCCESSFULLY_LOADED"));
        assertEquals(PackState.DISCARDED,PackState.LOADED.receive("DISCARDED"));
        for (String status:List.of("INVALID_URL","FAILED_RELOAD","FAILED_DOWNLOAD")) assertEquals(PackState.FAILED,PackState.SENT.receive(status));
    }
    @Test void mappingSelectionAndInvalidConfigurations() {
        var rule=new MappingConfig.ItemRule("minecraft:iron_spear","minecraft:iron_sword",List.of("1.21.10"),"assets/minecraft/textures/item/iron_spear.png","handheld",821,"iron_spear",0);
        var c=new MappingConfig(1,"26.3",HASH,HASH,true,Map.of(),List.of(rule)); c.validate();
        assertEquals(List.of(rule),c.select("1.21.10")); assertTrue(c.select("26.2").isEmpty());
        assertThrows(IllegalArgumentException.class,() -> new MappingConfig(1,"26.3",HASH,HASH,true,Map.of(),List.of(rule,rule)).validate());
        assertThrows(IllegalArgumentException.class,() -> new MappingConfig(1,"26.3","master",HASH,true,Map.of(),List.of()).validate());
        assertThrows(IllegalArgumentException.class,() -> MappingConfig.texture("assets/minecraft/textures/../evil.png"));
        assertThrows(IllegalArgumentException.class,() -> MappingConfig.identifier("Lança de Ferro"));
    }
}
