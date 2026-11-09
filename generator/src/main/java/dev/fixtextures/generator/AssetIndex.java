package dev.fixtextures.generator;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Complete minecraft resource tree, not an inference from registry names. */
final class AssetIndex {
    private static final String ROOT = "assets/minecraft/";
    private final Downloads downloads;

    AssetIndex(Downloads downloads) { this.downloads = downloads; }

    Set<String> paths(String version) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Set<String> result = new ConcurrentSkipListSet<>();
            // A bounded worker queue avoids one thread (or HTTP connection) per directory.
            LinkedBlockingQueue<String> pending = new LinkedBlockingQueue<>();
            pending.add(ROOT);
            List<Future<?>> workers = new ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger outstanding = new java.util.concurrent.atomic.AtomicInteger(1);
            java.util.concurrent.atomic.AtomicReference<Exception> failure = new java.util.concurrent.atomic.AtomicReference<>();
            for (int i = 0; i < 8; i++) workers.add(pool.submit(() -> {
                while (outstanding.get() != 0 && failure.get() == null) {
                    String directory;
                    try { directory = pending.poll(100, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
                    if (directory == null) continue;
                    try {
                        JsonObject listing = downloads.json(Downloads.assetUrl(version, directory + "_list.json"));
                        for (JsonElement file : listing.getAsJsonArray("files")) {
                            String name = file.getAsString();
                            if (valid(name)) result.add(directory + name);
                            else throw new IOException("Nome inválido no índice: " + name);
                        }
                        for (JsonElement child : listing.getAsJsonArray("directories")) {
                            String name = child.getAsString();
                            if (!valid(name)) throw new IOException("Diretório inválido no índice: " + name);
                            outstanding.incrementAndGet(); pending.add(directory + name + "/");
                        }
                    } catch (Exception e) { failure.compareAndSet(null, e); throw new CompletionException(e); }
                    finally { outstanding.decrementAndGet(); }
                }
            }));
            for (Future<?> worker : workers) worker.get();
            if (result.isEmpty()) throw new IOException("Índice vazio: " + version);
            return Collections.unmodifiableSet(result);
        } finally { pool.shutdownNow(); }
    }

    static boolean valid(String name) { return name.matches("[a-zA-Z0-9_.-]+") && !name.equals(".") && !name.contains(".."); }
}
