package dev.fixtextures.generator;

import dev.fixtextures.core.*;
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
            else if (Set.of("--config", "--output", "--cache", "--targets", "--variant", "--display-name").contains(key) && i+1<args.length) options.put(key,args[++i]);
            else throw new IllegalArgumentException("Argumento desconhecido/incompleto: " + key);
        }
        if (options.containsKey("--list")) { Profiles.ALL.forEach(p -> System.out.println(p.version() + " protocolo=" + p.protocol())); return; }
        if (options.containsKey("--help") || args.length==0) {
            System.out.println("FixTextures Generator (Java 25)\n--targets 1.8.9,1.21.10,26.2 OU --all\n--config mappings.json --output packs --cache cache\n--variant <nome> --display-name <texto> (manifesto schema 2)\n--list lista os perfis exatos. --all baixa clientes de todos os perfis (~gigabytes).\nPacks gerados não equivalem a homologação visual."); return;
        }
        if (options.containsKey("--all") == options.containsKey("--targets")) throw new IllegalArgumentException("Escolha --all OU --targets.");
        MappingConfig config=Json.read(Path.of(options.getOrDefault("--config","mappings.json")),MappingConfig.class); config.validate();
        List<Profiles.Profile> targets = options.containsKey("--all") ? Profiles.ALL : Arrays.stream(options.get("--targets").split(",")).map(String::trim).map(Profiles::version).distinct().toList();
        Path output=Path.of(options.getOrDefault("--output","packs")).toAbsolutePath().normalize(); Files.createDirectories(output);
        Downloads downloads=new Downloads(Path.of(options.getOrDefault("--cache","cache")));
        AssetIndex index = new AssetIndex(downloads);
        Map<String, Set<String>> indexes = new java.util.concurrent.ConcurrentHashMap<>();
        if (options.containsKey("--all")) {
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(6)) {
                var jobs = targets.stream().map(p -> pool.submit(() -> {
                    try { indexes.put(p.version(), index.paths(p.version())); }
                    catch (Exception e) { System.err.println("Índice mcasset indisponível: " + p.version() + ": " + e.getMessage()); }
                })).toList();
                for (var job : jobs) job.get();
            }
        }
        Assets source = load("26.3", downloads, index, indexes);
        PackBuilder builder=new PackBuilder(source,config,null,null);
        Map<Integer,PackManifest.Pack> packs=new TreeMap<>(); Path manifestPath=output.resolve("manifest.json");
        if (Files.exists(manifestPath)) { PackManifest previous=Json.read(manifestPath,PackManifest.class); previous.validate(); previous.packs().forEach(p -> packs.put(p.protocol(),p)); }
        for (var profile : targets) {
            System.out.println("Gerando " + profile.version() + "...");
            var result=builder.build(profile,load(profile.version(), downloads, index, indexes),output);
            packs.put(profile.protocol(),new PackManifest.Pack(profile.version(),profile.protocol(),result.zip().getFileName().toString(),result.sha1()));
            PackManifest manifest=new PackManifest(2,"26.3",
                options.getOrDefault("--variant","default"),
                options.getOrDefault("--display-name","FixTextures padrão"),
                List.copyOf(packs.values()));
            manifest.validate();
            Path temp=Files.createTempFile(output,"manifest-",".tmp"); Json.write(temp,manifest); Files.move(temp,manifestPath,StandardCopyOption.REPLACE_EXISTING);
            System.out.println("OK: " + result.zip());
        }
        System.out.println("Publique os ZIPs via HTTP(S) e copie manifest.json para plugins/FixTextures/.");
    }
    private static Assets load(String version, Downloads downloads, AssetIndex index, Map<String,Set<String>> indexes) throws Exception {
        Set<String> paths = indexes.get(version);
        if (paths == null) try { paths = index.paths(version); }
        catch (Exception e) {
            System.err.println("mcasset indisponível para " + version + "; usando JAR local: " + e.getMessage());
            return Assets.read(downloads.officialClient(version));
        }
        return Assets.remote(version, downloads, paths, "26.3".equals(version) ? null : downloads.officialClient(version));
    }
}
