package dev.moderntextures.generator;

import dev.moderntextures.core.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception e) { System.err.println("ERRO: " + e.getMessage()); e.printStackTrace(System.err); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        Map<String,String> options = new HashMap<>();
        for (int i=0; i<args.length; i++) {
            String key=args[i];
            if (Set.of("--list", "--help", "--all").contains(key)) options.put(key,"true");
            else if (Set.of("--config", "--output", "--cache", "--targets").contains(key) && i+1<args.length) options.put(key,args[++i]);
            else throw new IllegalArgumentException("Argumento desconhecido/incompleto: " + key);
        }
        if (options.containsKey("--list")) { Profiles.ALL.forEach(p -> System.out.println(p.version() + " protocolo=" + p.protocol())); return; }
        if (options.containsKey("--help") || args.length==0) {
            System.out.println("ModernTextures Generator (Java 25)\n--targets 1.8.9,1.21.10,26.2 OU --all\n--config mappings.json --output packs --cache cache\n--list lista os perfis exatos. --all baixa clientes de todos os perfis (~gigabytes).\nPacks gerados não equivalem a homologação visual."); return;
        }
        if (options.containsKey("--all") == options.containsKey("--targets")) throw new IllegalArgumentException("Escolha --all OU --targets.");
        MappingConfig config=Json.read(Path.of(options.getOrDefault("--config","mappings.json")),MappingConfig.class); config.validate();
        List<Profiles.Profile> targets = options.containsKey("--all") ? Profiles.ALL : Arrays.stream(options.get("--targets").split(",")).map(String::trim).map(Profiles::version).distinct().toList();
        Path output=Path.of(options.getOrDefault("--output","packs")).toAbsolutePath().normalize(); Files.createDirectories(output);
        Downloads downloads=new Downloads(Path.of(options.getOrDefault("--cache","cache")));
        Assets source=Assets.read(downloads.officialClient("26.3"));
        PackBuilder builder=new PackBuilder(source,config,new Prismarine(downloads,config),new ViaMappings(downloads,config));
        Map<Integer,PackManifest.Pack> packs=new TreeMap<>(); Path manifestPath=output.resolve("manifest.json");
        if (Files.exists(manifestPath)) { PackManifest previous=Json.read(manifestPath,PackManifest.class); previous.validate(); previous.packs().forEach(p -> packs.put(p.protocol(),p)); }
        for (var profile : targets) {
            System.out.println("Gerando " + profile.version() + "...");
            var result=builder.build(profile,Assets.read(downloads.officialClient(profile.version())),output);
            packs.put(profile.protocol(),new PackManifest.Pack(profile.version(),profile.protocol(),result.zip().getFileName().toString(),result.sha1()));
            PackManifest manifest=new PackManifest(1,"26.3",List.copyOf(packs.values())); manifest.validate();
            Path temp=Files.createTempFile(output,"manifest-",".tmp"); Json.write(temp,manifest); Files.move(temp,manifestPath,StandardCopyOption.REPLACE_EXISTING);
            System.out.println("OK: " + result.zip());
        }
        System.out.println("Publique os ZIPs via HTTP(S) e copie manifest.json para plugins/ModernTextures/.");
    }
}
