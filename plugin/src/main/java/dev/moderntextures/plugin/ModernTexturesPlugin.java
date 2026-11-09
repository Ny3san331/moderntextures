package dev.moderntextures.plugin;

import com.viaversion.viaversion.api.Via;
import dev.moderntextures.core.*;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public final class ModernTexturesPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private record Settings(URI base, PackManifest manifest, String prompt, int timeout, int attempts, int interval, boolean notifyPlayer,
                            boolean embeddedEnabled, String embeddedBind, int embeddedPort) {}
    private static final class Session {
        int protocol = -1;
        int attempts;
        boolean detecting = true;
        PackManifest.Pack pack;
        PackState state;
        BukkitTask task;
        String detail = "Detectando versão";
        void cancel() { if (task != null) task.cancel(); task = null; }
    }
    private final Map<UUID,Session> sessions = new HashMap<>();
    private Settings settings;
    private PackHttpServer httpServer;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "manifest.json").exists()) saveResource("manifest.json", false);
        try { settings = readSettings(); }
        catch (Exception e) { getLogger().severe("Configuração inválida: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        for (String dependency : List.of("ViaVersion", "ViaBackwards")) {
            var plugin = getServer().getPluginManager().getPlugin(dependency);
            if (plugin == null || !plugin.getPluginMeta().getVersion().equals("5.12.2")) {
                getLogger().warning(dependency + " difere da versão 5.12.2 usada pelo catálogo. Regenere/valide os mapeamentos antes de usar em produção.");
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("moderntextures")).setExecutor(this);
        Objects.requireNonNull(getCommand("moderntextures")).setTabCompleter(this);
        startHttpServer(settings);
        getLogger().info("Perfis disponíveis: " + settings.manifest.packs().size() + ". Tradução Via deve estar instalada neste Paper.");
        if (settings.base == null || settings.manifest.packs().isEmpty()) getLogger().warning("Gere os packs, copie manifest.json e configure base-url. Use /mt reload depois.");
        for (Player player : Bukkit.getOnlinePlayers()) begin(player);
    }
    private Settings readSettings() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(new java.io.File(getDataFolder(), "config.yml"));
        PackManifest manifest = readManifest();
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
        return new Settings(base,manifest,yaml.getString("prompt","Texturas modernas"),timeout,attempts,interval,yaml.getBoolean("notify-player",true),embedded,bind,port);
    }
    private PackManifest readManifest() throws Exception {
        Path data = getDataFolder().toPath();
        Path manifestPath = data.resolve("manifest.json");
        PackManifest manifest = Files.isRegularFile(manifestPath)
            ? Json.read(manifestPath, PackManifest.class)
            : new PackManifest(1, "26.3", List.of());
        if (manifest.packs().isEmpty()) {
            Path bundle = data.resolve("packs.zip");
            if (!Files.isRegularFile(bundle)) bundle = data.resolve("ModernTextures-Packs.zip");
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
        if (manifest == null) throw new IllegalArgumentException("manifest.json ausente/inválido.");
        manifest.validate();
        return manifest;
    }
    private void startHttpServer(Settings current) {
        stopHttpServer();
        if (!current.embeddedEnabled()) return;
        Path data = getDataFolder().toPath();
        Path directory = data.resolve("packs");
        Path archive = data.resolve("packs.zip");
        if (!Files.isRegularFile(archive)) archive = data.resolve("ModernTextures-Packs.zip");
        Set<String> allowed = current.manifest().packs().stream().map(PackManifest.Pack::file).collect(Collectors.toUnmodifiableSet());
        List<PackHttpServer.Source> sources = new ArrayList<>();
        if (Files.isDirectory(directory)) {
            sources.add(PackHttpServer.directory(directory, allowed));
        }
        if (Files.isRegularFile(archive)) {
            sources.add(PackHttpServer.archive(archive, allowed));
            getLogger().info("HTTP embutido: servindo packs de " + archive.getFileName() + ".");
        }
        PackHttpServer.Source source;
        if (sources.isEmpty()) {
            source = PackHttpServer.directory(directory, allowed);
            getLogger().warning("HTTP embutido: envie os ZIPs para " + directory + " ou o bundle para " + archive + ".");
        } else if (sources.size() == 1) {
            source = sources.get(0);
        } else {
            source = PackHttpServer.composite(sources.toArray(PackHttpServer.Source[]::new));
        }
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
    private void stopHttpServer() {
        if (httpServer != null) { httpServer.close(); httpServer = null; }
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
        var selected=settings.manifest.select(session.protocol);
        if (selected.isEmpty() || settings.base==null) {
            session.detail=selected.isEmpty() ? "Sem pack gerado para protocolo " + session.protocol : "base-url não configurada";
            getLogger().info(player.getName()+": "+session.detail); return;
        }
        session.pack=selected.get(); session.state=PackState.SENT; session.detail="Pack enviado";
        String url=settings.base.resolve(session.pack.file()).toASCIIString();
        try {
            player.setResourcePack(session.pack.id(),url,HexFormat.of().parseHex(session.pack.sha1()),Component.text(settings.prompt),false);
            getLogger().info(player.getName()+": protocolo="+session.protocol+", pack="+session.pack.file()+", sha1="+session.pack.sha1());
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
        session.cancel(); getLogger().warning(player.getName()+": "+session.detail+". Mantendo aparência padrão do cliente/ViaBackwards.");
        if (settings.notifyPlayer) player.sendMessage(Component.text("[ModernTextures] Pack indisponível. Você continuará com as texturas padrão compatíveis.", NamedTextColor.YELLOW));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if (!sender.hasPermission("moderntextures.admin")) { sender.sendMessage(Component.text("Sem permissão.", NamedTextColor.RED)); return true; }
        if (args.length==1 && args[0].equalsIgnoreCase("reload")) {
            try {
                Settings next=readSettings();
                stopHttpServer();
                settings=next;
                startHttpServer(next);
                sender.sendMessage(Component.text("Configuração recarregada. Perfis: "+next.manifest.packs().size()+". Use /mt send para reenviar.", NamedTextColor.GREEN));
            }
            catch (Exception e) { sender.sendMessage(Component.text("Configuração rejeitada; anterior mantida: "+e.getMessage(), NamedTextColor.RED)); getLogger().warning(e.toString()); }
            return true;
        }
        if (args.length>=1 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("send"))) {
            Player target=args.length>=2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target==null) { sender.sendMessage(Component.text("Informe um jogador online.", NamedTextColor.RED)); return true; }
            Session session=sessions.get(target.getUniqueId());
            if (args[0].equalsIgnoreCase("send")) {
                if (session!=null && (session.detecting || session.state!=null && session.state.pending())) sender.sendMessage(Component.text("Já há detecção/carregamento em andamento.", NamedTextColor.YELLOW));
                else { begin(target); sender.sendMessage(Component.text("Envio agendado para "+target.getName(), NamedTextColor.GREEN)); }
            } else sender.sendMessage(Component.text(session==null ? "Sem sessão." : target.getName()+": protocolo="+session.protocol+", estado="+session.state+", "+session.detail, NamedTextColor.GRAY));
            return true;
        }
        return false;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if (!sender.hasPermission("moderntextures.admin")) return List.of();
        if (args.length==1) return List.of("status","send","reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length==2 && !args[0].equalsIgnoreCase("reload")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
    @Override public void onDisable() { stopHttpServer(); sessions.values().forEach(Session::cancel); sessions.clear(); }
}
