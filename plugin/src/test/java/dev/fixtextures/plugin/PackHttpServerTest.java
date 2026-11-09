package dev.fixtextures.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PackHttpServerTest {
    @TempDir Path dir;
    private PackHttpServer server;

    @AfterEach void stop() { if (server != null) server.close(); }

    @Test void servesOnlyManifestFilesWithHeadAndRange() throws Exception {
        byte[] content = {1, 2, 3, 4, 5};
        Files.write(dir.resolve("pack.zip"), content);
        server = new PackHttpServer("127.0.0.1", 0, PackHttpServer.directory(dir, Set.of("pack.zip")), Logger.getLogger("test"));
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://127.0.0.1:" + server.port() + "/pack.zip");

        HttpResponse<byte[]> get = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, get.statusCode());
        assertArrayEquals(content, get.body());
        assertEquals("application/zip", get.headers().firstValue("Content-Type").orElse(""));

        HttpResponse<Void> head = client.send(HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(200, head.statusCode());
        assertEquals("5", head.headers().firstValue("Content-Length").orElse(""));

        HttpResponse<byte[]> range = client.send(HttpRequest.newBuilder(uri).header("Range", "bytes=1-3").build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, range.statusCode());
        assertArrayEquals(new byte[]{2, 3, 4}, range.body());

        assertEquals(404, client.send(HttpRequest.newBuilder(uri.resolve("/other.zip")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(405, client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    @Test void servesFromBundleArchive() throws Exception {
        Path bundle = dir.resolve("packs.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(bundle))) {
            out.putNextEntry(new ZipEntry("pack.zip"));
            out.write(new byte[]{9, 8, 7});
            out.closeEntry();
            out.putNextEntry(new ZipEntry("private.txt"));
            out.write(new byte[]{1});
            out.closeEntry();
        }
        server = new PackHttpServer("127.0.0.1", 0, PackHttpServer.archive(bundle, Set.of("pack.zip")), Logger.getLogger("test"));
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        URI base = URI.create("http://127.0.0.1:" + server.port());

        HttpResponse<byte[]> get = client.send(HttpRequest.newBuilder(base.resolve("/pack.zip")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, get.statusCode());
        assertArrayEquals(new byte[]{9, 8, 7}, get.body());
        assertEquals(404, client.send(HttpRequest.newBuilder(base.resolve("/private.txt")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
    }
}
