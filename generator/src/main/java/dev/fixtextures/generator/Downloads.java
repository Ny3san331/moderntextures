package dev.fixtextures.generator;

import com.google.gson.*;
import dev.fixtextures.core.Json;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.HexFormat;

final class Downloads {
    private final Path cache;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
    Downloads(Path cache) throws IOException { this.cache = cache.toAbsolutePath().normalize(); Files.createDirectories(this.cache); }
    static String sha1(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    Path get(String url, String expectedHash) throws IOException, InterruptedException {
        String key = sha1(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path dest = cache.resolve(key);
        if (Files.isRegularFile(dest) && (expectedHash == null || sha1(Files.readAllBytes(dest)).equals(expectedHash))) return dest;
        System.out.println("Download: " + url);
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).header("User-Agent", "FixTextures/1.0").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        Path temp = Files.createTempFile(cache, "download-", ".tmp");
        try (var in = response.body(); var out = Files.newOutputStream(temp)) {
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + ": " + url);
            byte[] buffer = new byte[65536]; long total = 0; int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > 256L * 1024 * 1024) throw new IOException("Download excede 256 MiB: " + url);
                out.write(buffer, 0, n);
            }
        } catch (Exception e) { Files.deleteIfExists(temp); throw e; }
        if (expectedHash != null && !sha1(Files.readAllBytes(temp)).equals(expectedHash)) {
            Files.deleteIfExists(temp); throw new IOException("SHA-1 divergente: " + url);
        }
        Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }
    JsonObject json(String url) throws Exception { return Json.read(get(url, null), JsonObject.class); }
    JsonObject json(String url, String hash) throws Exception { return Json.read(get(url, hash), JsonObject.class); }
    Path officialClient(String version) throws Exception {
        JsonObject manifest = json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            var entry = element.getAsJsonObject();
            if (!version.equals(entry.get("id").getAsString())) continue;
            if (!"release".equals(entry.get("type").getAsString())) throw new IOException("Somente releases: " + version);
            var info = json(entry.get("url").getAsString(), entry.get("sha1").getAsString());
            var client = info.getAsJsonObject("downloads").getAsJsonObject("client");
            return get(client.get("url").getAsString(), client.get("sha1").getAsString());
        }
        throw new IOException("Versão oficial não encontrada: " + version);
    }
}
