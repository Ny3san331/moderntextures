package dev.moderntextures.plugin;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Servidor HTTP mínimo e somente-leitura para os ZIPs listados no manifesto. */
final class PackHttpServer implements AutoCloseable {
    interface Source {
        boolean contains(String name);
        long size(String name) throws IOException;
        InputStream open(String name) throws IOException;
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final Source source;
    private final Logger logger;

    PackHttpServer(String bind, int port, Source source, Logger logger) throws IOException {
        this.source = Objects.requireNonNull(source, "source");
        this.logger = Objects.requireNonNull(logger, "logger");
        InetSocketAddress address = bind == null || bind.isBlank()
            ? new InetSocketAddress(port)
            : new InetSocketAddress(InetAddress.getByName(bind), port);
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r, "ModernTextures-HTTP");
            thread.setDaemon(true);
            return thread;
        });
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    int port() { return server.getAddress().getPort(); }

    void start() { server.start(); }

    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) { sendEmpty(exchange, 405); return; }

            String name = exchange.getRequestURI().getPath();
            if (name.startsWith("/")) name = name.substring(1);
            if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..") || !source.contains(name)) {
                sendEmpty(exchange, 404);
                return;
            }

            long size = source.size(name);
            if (size <= 0) { sendEmpty(exchange, 404); return; }
            ByteRange range = parseRange(exchange.getRequestHeaders().getFirst("Range"), size);
            if (range == null) { sendEmpty(exchange, 416); return; }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip");
            headers.set("Cache-Control", "public, max-age=31536000, immutable");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Accept-Ranges", "bytes");
            long length = range.end - range.start + 1;
            if (range.partial) headers.set("Content-Range", "bytes " + range.start + "-" + range.end + "/" + size);

            int status = range.partial ? 206 : 200;
            if ("HEAD".equals(method)) {
                headers.set("Content-Length", Long.toString(length));
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            exchange.sendResponseHeaders(status, length);

            try (InputStream in = source.open(name); OutputStream out = exchange.getResponseBody()) {
                in.skipNBytes(range.start);
                byte[] buffer = new byte[8192];
                long remaining = length;
                while (remaining > 0) {
                    int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) break;
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Falha ao servir resource pack", e);
            try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) { }
        } finally {
            exchange.close();
        }
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, -1);
    }

    private record ByteRange(long start, long end, boolean partial) { }

    private static ByteRange parseRange(String header, long size) {
        if (header == null || header.isBlank()) return new ByteRange(0, size - 1, false);
        if (!header.startsWith("bytes=")) return null;
        String spec = header.substring(6).trim();
        if (spec.isEmpty() || spec.indexOf(',') >= 0) return null;
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        String left = spec.substring(0, dash).trim();
        String right = spec.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) return null;
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? size - 1 : Long.parseLong(right);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (start < 0 || start >= size || end < start) return null;
        return new ByteRange(start, Math.min(end, size - 1), true);
    }

    static Source directory(Path root, Set<String> names) {
        Path base = root.toAbsolutePath().normalize();
        Set<String> allowed = Set.copyOf(names);
        return new Source() {
            @Override public boolean contains(String name) {
                return allowed.contains(name) && Files.isRegularFile(resolve(name));
            }
            @Override public long size(String name) throws IOException {
                return Files.size(resolve(name));
            }
            @Override public InputStream open(String name) throws IOException {
                return Files.newInputStream(resolve(name));
            }
            private Path resolve(String name) {
                Path file = base.resolve(name).normalize();
                if (!file.startsWith(base)) throw new IllegalArgumentException("Caminho inválido: " + name);
                return file;
            }
        };
    }

    static Source archive(Path archive, Set<String> names) {
        Path file = archive.toAbsolutePath().normalize();
        Set<String> allowed = Set.copyOf(names);
        return new Source() {
            @Override public boolean contains(String name) {
                if (!allowed.contains(name)) return false;
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    return zip.getEntry(name) != null;
                } catch (IOException e) {
                    return false;
                }
            }
            @Override public long size(String name) throws IOException {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    ZipEntry entry = zip.getEntry(name);
                    if (entry == null || entry.getSize() < 0) throw new FileNotFoundException(name);
                    return entry.getSize();
                }
            }
            @Override public InputStream open(String name) throws IOException {
                ZipFile zip = new ZipFile(file.toFile());
                try {
                    ZipEntry entry = zip.getEntry(name);
                    if (entry == null) throw new FileNotFoundException(name);
                    InputStream in = zip.getInputStream(entry);
                    return new FilterInputStream(in) {
                        @Override public void close() throws IOException {
                            try { super.close(); } finally { zip.close(); }
                        }
                    };
                } catch (IOException | RuntimeException e) {
                    zip.close();
                    throw e;
                }
            }
        };
    }

    static Source composite(Source... sources) {
        return new Source() {
            @Override public boolean contains(String name) {
                for (Source source : sources) if (source.contains(name)) return true;
                return false;
            }
            @Override public long size(String name) throws IOException {
                for (Source source : sources) if (source.contains(name)) return source.size(name);
                throw new FileNotFoundException(name);
            }
            @Override public InputStream open(String name) throws IOException {
                for (Source source : sources) if (source.contains(name)) return source.open(name);
                throw new FileNotFoundException(name);
            }
        };
    }
}
