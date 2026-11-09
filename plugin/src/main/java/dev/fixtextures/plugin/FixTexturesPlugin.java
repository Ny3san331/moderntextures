package dev.fixtextures.plugin;

import com.viaversion.viaversion.api.Via;
import dev.fixtextures.core.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public final class FixTexturesPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private record Variant(String name, String displayName, PackManifest manifest, URI base) {}

    private record Settings(URI base, Map<String,Variant> variants, String defaultVariant, boolean allowChoice,
                            String prompt, int timeout, int attempts, int interval, boolean notifyPlayer,
                            boolean embeddedEnabled, String embeddedBind, int embeddedPort) {
        Variant variant(String name) {
            Variant found = variants.get(name);
            return found != null ? found : variants.get(defaultVariant);
        }
    }

    private static final class Session {
        int protocol = -1;
        int attempts;
        boolean detecting = true;
        String variant;
        PackManifest.Pack pack;
        PackState state;
        BukkitTask task;
        String detail = "Detectando versão";
        void cancel() { if (task != null) task.cancel(); task = null; }
    }

    private final Map<UUID,Session> sessions = new HashMap<>();
    private final Map<UUID,String> preferences = new HashMap<>();
    private Settings settings;
    private PackHttpServer httpServer;

    @Override public void onEnable() {
        migrateLegacyFolder();
        saveDefaultConfig();
        saveResourceIfMissing("manifest.json");
        saveResourceIfMissing("TUTORIAL.md");
        loadPreferences();
        try { settings = readSettings(); }
        catch (Exception e) { getLogger().severe("Configuração inválida: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        for (String dependency : List.of("ViaVersion", "ViaBackwards")) {
            var plugin = getServer().getPluginManager().getPlugin(dependency);
            if (plugin == null || !plugin.getPluginMeta().getVersion().equals("5.12.2")) {
                getLogger().warning(dependency + " difere da versão 5.12.2 usada pelo catálogo. Regenere/valide os mapeamentos antes de usar em produção.");
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("fixtextures")).setExecutor(this);
        Objects.requireNonNull(getCommand("fixtextures")).setTabCompleter(this);
        startHttpServer(settings);
        if (!Bukkit.getServer().getResourcePack().isBlank())
            getLogger().warning("server.properties também envia um resource pack. Clientes até 1.20.2 não empilham packs: use um único pack mesclado para evitar substituição e recargas.");
        int profiles = settings.variants().values().stream().mapToInt(v -> v.manifest().packs().size()).sum();
        getLogger().info("Variantes: " + settings.variants().size() + "; perfis: " + profiles + ". Tradução Via deve estar instalada neste Paper.");
        if (settings.base() == null || profiles == 0) getLogger().warning("Gere os packs, copie manifest.json e configure base-url. Use /ft reload depois.");
        for (Player player : Bukkit.getOnlinePlayers()) begin(player);
    }

    private void saveResourceIfMissing(String name) {
        if (!new java.io.File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    private void migrateLegacyFolder() {
        java.io.File old = new java.io.File(getDataFolder().getParentFile(), "ModernTextures");
        if (!old.isDirectory() || old.equals(getDataFolder())) return;
        try {
            Files.createDirectories(getDataFolder().toPath());
            int copied = 0;
            for (String name : List.of("config.yml", "manifest.json", "players.yml", "packs.zip", "ModernTextures-Packs.zip", "FixTextures-Packs.zip")) {
                Path from = old.toPath().resolve(name), to = getDataFolder().toPath().resolve(name);
                if (Files.isRegularFile(from) && !Files.exists(to)) { Files.copy(from, to); copied++; }
            }
            for (String name : List.of("packs", "variants")) {
                Path from = old.toPath().resolve(name), to = getDataFolder().toPath().resolve(name);
                if (Files.isDirectory(from) && !Files.exists(to)) copied += copyTree(from, to);
            }
            if (copied > 0) getLogger().info("Migrados " + copied + " arquivo(s) de plugins/ModernTextures para plugins/FixTextures (a pasta antiga não foi apagada).");
        } catch (IOException e) {
            getLogger().warning("Falha ao migrar plugins/ModernTextures: " + e.getMessage());
        }
    }

    private static int copyTree(Path from, Path to) throws IOException {
        int count = 0;
        try (var walk = Files.walk(from)) {
            for (Path source : walk.toList()) {
                Path destination = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(destination);
                else if (Files.isRegularFile(source) && !Files.exists(destination)) { Files.createDirectories(destination.getParent()); Files.copy(source, destination); count++; }
            }
        }
        return count;
    }

    private Settings readSettings() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(new java.io.File(getDataFolder(), "config.yml"));
        String url = yaml.getString("base-url", "").trim();
        int timeout=yaml.getInt("timeout-seconds",120), attempts=yaml.getInt("detect-attempts",20), interval=yaml.getInt("detect-interval-ticks",5);
        if (timeout<10 || timeout>1800 || attempts<1 || attempts>100 || interval<1 || interval>100) throw new IllegalArgumentException("timeout 10..1800, attempts 1..100, interval 1..100.");
        boolean embedded = yaml.getBoolean("embedded-server.enabled", false);
        String bind = yaml.getString("embedded-server.bind", "0.0.0.0").trim();
        int port = yaml.getInt("embedded-server.port", 8080);
        String publicUrl = yaml.getString("embedded-server.public-url", "").trim();
        if (port < 1 || port > 65535) throw new IllegalArgumentException("embedded-server.port deve ser 1..65535.");
        if (embedded && bind.isEmpty()) bind = "0.0.0.0";
        URI external = url.isEmpty() ? null : PackManifest.baseUrl(url);
        URI embeddedBase = publicUrl.isEmpty() ? null : PackManifest.baseUrl(publicUrl);
        URI base = external != null ? external : embeddedBase;
        if (base == null) getLogger().warning(embedded
            ? "embedded-server habilitado, mas embedded-server.public-url/base-url não configurada. Os packs não serão enviados."
            : "base-url não configurada. Os packs não serão enviados.");

        Map<String,Variant> variants = readVariants(yaml, base);
        String defaultVariant = yaml.getString("variants.default", "default").trim();
        if (!variants.containsKey(defaultVariant)) {
            if (variants.isEmpty()) variants.put("default", new Variant("default", "FixTextures padrão", new PackManifest(1, "26.3", List.of()), base));
            defaultVariant = variants.keySet().iterator().next();
            getLogger().warning("variants.default '" + yaml.getString("variants.default", "default") + "' não existe; usando '" + defaultVariant + "'.");
        }
        return new Settings(base, variants, defaultVariant, yaml.getBoolean("variants.allow-player-choice", true),
            yaml.getString("prompt","Texturas modernas"), timeout, attempts, interval, yaml.getBoolean("notify-player",true),
            embedded, bind, port);
    }

    private Map<String,Variant> readVariants(YamlConfiguration yaml, URI base) throws Exception {
        Map<String,Variant> variants = new LinkedHashMap<>();
        Path root = getDataFolder().toPath();
        Path rootManifest = root.resolve("manifest.json");
        if (Files.isRegularFile(rootManifest)) {
            PackManifest manifest = readManifest(root);
            String name = manifest.schema() == 2 ? manifest.variantName() : "default";
            variants.put(name, new Variant(name, manifest.displayNameOrDefault(), manifest, variantBase(yaml, name, base)));
        }
        Path variantsDir = root.resolve("variants");
        if (Files.isDirectory(variantsDir)) {
            try (var list = Files.list(variantsDir)) {
                for (Path dir : list.filter(Files::isDirectory).sorted().toList()) {
                    String name = dir.getFileName().toString();
                    if (!name.matches("[a-z0-9_-]{1,32}")) { getLogger().warning("Variante ignorada (nome inválido): " + name); continue; }
                    if (!Files.isRegularFile(dir.resolve("manifest.json"))) { getLogger().warning("Variante ignorada (manifest.json ausente): " + name); continue; }
                    PackManifest manifest = readManifest(dir);
                    if (manifest.schema() == 2 && !manifest.variantName().equals(name)) {
                        getLogger().warning("Manifesto da variante '" + name + "' declara '" + manifest.variantName() + "'; usando o nome da pasta.");
                    }
                    variants.put(name, new Variant(name, manifest.displayNameOrDefault(), manifest, variantBase(yaml, name, base)));
                }
            }
        }
        return variants;
    }

    private URI variantBase(YamlConfiguration yaml, String name, URI base) {
        String override = yaml.getString("variants." + name + ".base-url", "").trim();
        if (!override.isEmpty()) return PackManifest.baseUrl(override);
        if (base == null || "default".equals(name)) return base;
        return base.resolve("variants/" + name + "/");
    }

    private PackManifest readManifest(Path directory) throws Exception {
        Path manifestPath = directory.resolve("manifest.json");
        PackManifest manifest = Files.isRegularFile(manifestPath)
            ? Json.read(manifestPath, PackManifest.class)
            : new PackManifest(1, "26.3", List.of());
        if (manifest == null) throw new IllegalArgumentException("Manifesto inválido em " + directory);
        manifest.validate();
        if (manifest.packs().isEmpty()) {
            Path bundle = directory.resolve("packs.zip");
            if (!Files.isRegularFile(bundle)) bundle = directory.resolve("FixTextures-Packs.zip");
            if (!Files.isRegularFile(bundle)) bundle = directory.resolve("ModernTextures-Packs.zip");
            if (Files.isRegularFile(bundle)) {
                try (ZipFile zip = new ZipFile(bundle.toFile())) {
                    var entry = zip.getEntry("manifest.json");
                    if (entry != null) {
                        try (var in = zip.getInputStream(entry)) {
                            manifest = Json.GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), PackManifest.class);
                        }
                    }
                }
            }
        }
        if (manifest == null) throw new IllegalArgumentException("manifest.json ausente/inválido em " + directory + ".");
        manifest.validate();
        return manifest;
    }

    private void startHttpServer(Settings current) {
        stopHttpServer();
        if (!current.embeddedEnabled()) return;
        Map<String,PackHttpServer.Source> routes = new LinkedHashMap<>();
        boolean any = false;
        for (Variant variant : current.variants().values()) {
            Set<String> allowed = variant.manifest().packs().stream().map(PackManifest.Pack::file).collect(Collectors.toUnmodifiableSet());
            PackHttpServer.Source source = variantSource(variant.name(), allowed);
            if (source == null) continue;
            any = true;
            if (variant.name().equals(current.defaultVariant())) routes.put("", source);
            routes.put("variants/" + variant.name() + "/", source);
            routes.put("packs/" + variant.name() + "/", source);
        }
        if (!any) {
            String name = current.defaultVariant();
            Set<String> allowed = current.variant(name).manifest().packs().stream().map(PackManifest.Pack::file).collect(Collectors.toUnmodifiableSet());
            PackHttpServer.Source fallback = PackHttpServer.directory(getDataFolder().toPath().resolve("packs"), allowed);
            routes.put("", fallback);
            getLogger().warning("HTTP embutido: envie os ZIPs para " + getDataFolder().toPath().resolve("packs") + " ou o bundle packs.zip.");
        }
        PackHttpServer.Source source = PackHttpServer.routed(routes);
        try {
            httpServer = new PackHttpServer(current.embeddedBind(), current.embeddedPort(), source, getLogger());
            httpServer.start();
            getLogger().info("HTTP embutido ativo em " + current.embeddedBind() + ":" + httpServer.port()
                + ". URL dos packs: " + (current.base() != null ? current.base() : "configure embedded-server.public-url"));
        } catch (Exception e) {
            httpServer = null;
            getLogger().severe("Falha ao iniciar HTTP embutido: " + e.getMessage());
        }
    }

    private PackHttpServer.Source variantSource(String name, Set<String> allowed) {
        Path directory = "default".equals(name) ? getDataFolder().toPath() : getDataFolder().toPath().resolve("variants").resolve(name);
        Path packs = directory.resolve("packs");
        List<PackHttpServer.Source> sources = new ArrayList<>();
        if (Files.isDirectory(packs)) sources.add(PackHttpServer.directory(packs, allowed));
        for (String bundleName : List.of("packs.zip", "FixTextures-Packs.zip", "ModernTextures-Packs.zip")) {
            Path archive = directory.resolve(bundleName);
            if (Files.isRegularFile(archive)) { sources.add(PackHttpServer.archive(archive, allowed)); getLogger().info("HTTP embutido: servindo " + name + " de " + archive.getFileName() + "."); }
        }
        if (sources.isEmpty()) return null;
        return sources.size() == 1 ? sources.get(0) : PackHttpServer.composite(sources.toArray(PackHttpServer.Source[]::new));
    }

    private void stopHttpServer() {
        if (httpServer != null) { httpServer.close(); httpServer = null; }
    }

    private void loadPreferences() {
        Path path = getDataFolder().toPath().resolve("players.yml");
        if (!Files.isRegularFile(path)) return;
        try {
            var yaml = new YamlConfiguration(); yaml.load(path.toFile());
            for (String key : yaml.getKeys(false)) {
                try { preferences.put(UUID.fromString(key), yaml.getString(key)); } catch (IllegalArgumentException ignored) { }
            }
        } catch (Exception e) { getLogger().warning("players.yml inválido: " + e.getMessage()); }
    }

    private void savePreferences() {
        try {
            var yaml = new YamlConfiguration();
            preferences.forEach((id, variant) -> yaml.set(id.toString(), variant));
            yaml.save(getDataFolder().toPath().resolve("players.yml").toFile());
        } catch (IOException e) { getLogger().warning("Falha ao salvar players.yml: " + e.getMessage()); }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { begin(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { Session session=sessions.remove(event.getPlayer().getUniqueId()); if (session!=null) session.cancel(); }

    private void begin(Player player) {
        UUID id=player.getUniqueId(); Session old=sessions.get(id); if (old!=null) old.cancel();
        Session session=new Session(); sessions.put(id,session);
        session.task=Bukkit.getScheduler().runTaskLater(this,() -> detect(player,session),settings.interval);
    }

    private void detect(Player player, Session session) {
        if (!current(player,session)) return;
        var version=Via.getAPI().getPlayerProtocolVersion(player.getUniqueId());
        if (!version.isKnown()) {
            if (++session.attempts < settings.attempts) session.task=Bukkit.getScheduler().runTaskLater(this,() -> detect(player,session),settings.interval);
            else { session.detecting=false; session.detail="Protocolo desconhecido; fallback padrão"; getLogger().warning(player.getName()+": " + session.detail); }
            return;
        }
        session.detecting=false; session.protocol=version.getVersion(); session.cancel();
        if (session.protocol==777) { session.detail="Cliente nativo 26.3; pack desnecessário"; return; }
        String chosen = preferences.getOrDefault(player.getUniqueId(), settings.defaultVariant());
        Variant variant = settings.variant(chosen);
        session.variant = variant.name();
        var selected=variant.manifest().select(session.protocol);
        if (selected.isEmpty() || variant.base()==null) {
            session.detail=selected.isEmpty() ? "Sem pack gerado para protocolo " + session.protocol : "base-url não configurada";
            getLogger().info(player.getName()+": variante="+variant.name()+", "+session.detail); return;
        }
        session.pack=selected.get(); session.state=PackState.SENT; session.detail="Pack enviado (" + variant.name() + ")";
        String url=variant.base().resolve(session.pack.file()).toASCIIString();
        try {
            // setResourcePack first removes all packs, causing an extra reload on legacy clients.
            // Their protocol already replaces the active pack when a new one is sent.
            if (session.protocol < 765)
                player.addResourcePack(session.pack.id(),url,HexFormat.of().parseHex(session.pack.sha1()),settings.prompt,false);
            else player.setResourcePack(session.pack.id(),url,HexFormat.of().parseHex(session.pack.sha1()),Component.text(settings.prompt),false);
            getLogger().info(player.getName()+": variante="+variant.name()+", protocolo="+session.protocol+", pack="+session.pack.file()+", sha1="+session.pack.sha1());
            session.task=Bukkit.getScheduler().runTaskLater(this,() -> {
                if (current(player,session) && session.state.pending()) {
                    session.state=PackState.TIMED_OUT; session.detail="Tempo de carregamento excedido"; fallback(player,session);
                }
            },settings.timeout*20L);
        } catch (RuntimeException e) { session.state=PackState.FAILED; session.detail=e.getMessage(); fallback(player,session); }
    }

    private boolean current(Player player,Session session) { return player.isOnline() && sessions.get(player.getUniqueId())==session; }

    @EventHandler public void onStatus(PlayerResourcePackStatusEvent event) {
        Session session=sessions.get(event.getPlayer().getUniqueId());
        if (session==null || session.pack==null || session.state==null) return;
        // Pre-1.20.3 protocols do not carry a pack UUID. There must be a single pack sender in that case.
        if (session.protocol>=765 && !session.pack.id().equals(event.getID())) return;
        PackState before=session.state;
        session.state=session.state.receive(event.getStatus().name());
        if (before==session.state) return;
        session.detail=event.getStatus().name();
        getLogger().info(event.getPlayer().getName()+": pack="+session.pack.file()+", estado="+session.state);
        if (!session.state.pending()) {
            session.cancel();
            if (session.state!=PackState.LOADED) fallback(event.getPlayer(),session);
        }
    }

    private void fallback(Player player,Session session) {
        session.cancel(); getLogger().warning(player.getName()+": "+session.detail+". Aplicação do FixTextures não confirmada."
            + (session.protocol < 765 ? " Cliente legado: respostas não identificam o pack; confira outro emissor de packs e server.properties." : ""));
        if (settings.notifyPlayer) player.sendMessage(Component.text("[FixTextures] Aplicação do pack não confirmada. Consulte /ft status ou avise a administração.", NamedTextColor.YELLOW));
    }

    private boolean admin(CommandSender sender) {
        return sender.hasPermission("fixtextures.admin") || sender.hasPermission("moderntextures.admin");
    }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if (sender instanceof Player && args.length >= 1 && args[0].equalsIgnoreCase("texture") && args.length <= 2)
            return textureCommand(sender, args);
        if (!admin(sender)) { sender.sendMessage(Component.text("Sem permissão.", NamedTextColor.RED)); return true; }
        if (args.length==1 && args[0].equalsIgnoreCase("reload")) {
            try {
                Settings next=readSettings();
                stopHttpServer();
                settings=next;
                startHttpServer(next);
                sender.sendMessage(Component.text("Configuração recarregada. Variantes: "+next.variants().size()+". Use /ft send para reenviar.", NamedTextColor.GREEN));
            }
            catch (Exception e) { sender.sendMessage(Component.text("Configuração rejeitada; anterior mantida: "+e.getMessage(), NamedTextColor.RED)); getLogger().warning(e.toString()); }
            return true;
        }
        if (args.length>=1 && args[0].equalsIgnoreCase("texture")) {
            return textureCommand(sender, args);
        }
        if (args.length>=1 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("send"))) {
            Player target=args.length>=2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target==null) { sender.sendMessage(Component.text("Informe um jogador online.", NamedTextColor.RED)); return true; }
            Session session=sessions.get(target.getUniqueId());
            if (args[0].equalsIgnoreCase("send")) {
                if (session!=null && (session.detecting || session.state!=null && session.state.pending())) sender.sendMessage(Component.text("Já há detecção/carregamento em andamento.", NamedTextColor.YELLOW));
                else { begin(target); sender.sendMessage(Component.text("Envio agendado para "+target.getName(), NamedTextColor.GREEN)); }
            } else {
                sender.sendMessage(Component.text(session==null ? "Sem sessão." : target.getName()+": protocolo="+session.protocol+", variante="+(session.variant==null?settings.defaultVariant():session.variant)+", estado="+session.state+", "+session.detail, NamedTextColor.GRAY));
                if (session != null && session.pack != null)
                    sender.sendMessage(Component.text("Pack=" + session.pack.file() + ", SHA-1=" + session.pack.sha1(), NamedTextColor.GRAY));
                if (session != null && session.protocol <= 754) {
                    sender.sendMessage(Component.text("Cliente até 1.16.5: altura vanilla Y=0..255; Y atual=" + target.getLocation().getBlockY() + ". Resource pack não amplia a altura.", NamedTextColor.YELLOW));
                }
            }
            return true;
        }
        sender.sendMessage(Component.text("Uso: /ft <status [jogador]|send [jogador]|reload|texture list|texture <nome>|texture <jogador> <nome>>", NamedTextColor.GRAY));
        return true;
    }

    private boolean textureCommand(CommandSender sender, String[] args) {
        if (args.length==2 && args[1].equalsIgnoreCase("list")) {
            String current = sender instanceof Player p ? preferences.getOrDefault(p.getUniqueId(), settings.defaultVariant()) : settings.defaultVariant();
            sender.sendMessage(Component.text("Variantes de textura:", NamedTextColor.GOLD));
            for (Variant variant : settings.variants().values()) {
                String mark = variant.name().equals(current) ? " *" : "";
                sender.sendMessage(Component.text(" - " + variant.name() + mark + " (" + variant.manifest().packs().size() + " perfis) - " + variant.displayName(), NamedTextColor.GRAY));
            }
            return true;
        }
        if (!settings.allowChoice()) { sender.sendMessage(Component.text("Seleção desabilitada no config.yml.", NamedTextColor.RED)); return true; }
        Player target = sender instanceof Player self ? self : null;
        String variantName;
        if (args.length==2) variantName = args[1];
        else if (args.length==3) { target = Bukkit.getPlayerExact(args[1]); variantName = args[2]; }
        else { sender.sendMessage(Component.text("Uso: /ft texture list | /ft texture <nome> | /ft texture <jogador> <nome>", NamedTextColor.GRAY)); return true; }
        if (target == null) { sender.sendMessage(Component.text("Jogador online não encontrado.", NamedTextColor.RED)); return true; }
        if (!settings.variants().containsKey(variantName)) {
            sender.sendMessage(Component.text("Variante inexistente: " + variantName + ". Use /ft texture list.", NamedTextColor.RED)); return true;
        }
        Session active = sessions.get(target.getUniqueId());
        if (active != null && (active.detecting || active.state != null && active.state.pending())) {
            sender.sendMessage(Component.text("Aguarde o carregamento atual antes de trocar de variante.", NamedTextColor.YELLOW)); return true;
        }
        if (active != null && variantName.equals(active.variant) && active.state == PackState.LOADED) {
            sender.sendMessage(Component.text("Essa variante já está carregada. Para reenviar, use /ft send.", NamedTextColor.GRAY)); return true;
        }
        if (!settings.allowChoice() && !target.equals(sender)) { sender.sendMessage(Component.text("Seleção desabilitada no config.yml.", NamedTextColor.RED)); return true; }
        preferences.put(target.getUniqueId(), variantName); savePreferences();
        sender.sendMessage(Component.text("Variante de " + target.getName() + ": " + variantName + ". Reenviando pack...", NamedTextColor.GREEN));
        begin(target);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if (!admin(sender)) {
            if (args.length == 1) return List.of("texture");
            if (args.length == 2 && args[0].equalsIgnoreCase("texture")) {
                List<String> choices = new ArrayList<>(settings.variants().keySet()); choices.add("list");
                return choices.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            return List.of();
        }
        if (args.length==1) return List.of("status","send","reload","texture").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length==2 && args[0].equalsIgnoreCase("texture")) {
            List<String> names = new ArrayList<>(); names.add("list"); names.addAll(settings.variants().keySet());
            return names.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length==2 && !args[0].equalsIgnoreCase("reload")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length==3 && args[0].equalsIgnoreCase("texture")) return settings.variants().keySet().stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    @Override public void onDisable() { stopHttpServer(); sessions.values().forEach(Session::cancel); sessions.clear(); }
}
