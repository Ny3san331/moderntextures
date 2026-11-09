package dev.fixtextures.core;

import java.net.URI;
import java.util.*;

public record PackManifest(int schema, String sourceVersion, String variant, String displayName, List<Pack> packs) {
    public record Pack(String clientVersion, int protocol, String file, String sha1) {
        public UUID id() { return UUID.nameUUIDFromBytes((protocol + ":" + sha1).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    /** Construtor retrocompatível com manifestos schema 1. */
    public PackManifest(int schema, String sourceVersion, List<Pack> packs) {
        this(schema, sourceVersion, null, null, packs);
    }
    public void validate() {
        if ((schema != 1 && schema != 2) || !"26.3".equals(sourceVersion) || packs == null) throw new IllegalArgumentException("Manifesto inválido (schema=1 ou 2, sourceVersion=26.3).");
        if (schema == 2) {
            if (variant == null || !variant.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("variant inválida no manifesto schema 2.");
            if (displayName != null && displayName.isBlank()) throw new IllegalArgumentException("displayName não pode ser vazio.");
        }
        Set<Integer> ids = new HashSet<>();
        for (Pack p : packs) {
            if (p == null || Profiles.protocol(p.protocol).isEmpty() || !ids.add(p.protocol)) throw new IllegalArgumentException("Protocolo inválido/duplicado no manifesto.");
            if (!Profiles.version(p.clientVersion).equals(Profiles.protocol(p.protocol).orElseThrow())) throw new IllegalArgumentException("Versão/protocolo divergentes: " + p.clientVersion);
            if (p.file == null || !p.file.matches("[a-zA-Z0-9._-]+\\.zip") || p.sha1 == null || !p.sha1.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Arquivo/hash inválido: " + p.file);
        }
    }
    public Optional<Pack> select(int protocol) { return packs.stream().filter(p -> p.protocol == protocol).findFirst(); }
    public String variantName() { return variant == null || variant.isBlank() ? "default" : variant; }
    public String displayNameOrDefault() { return displayName == null || displayName.isBlank() ? "FixTextures " + variantName() : displayName; }
    public static URI baseUrl(String value) {
        URI uri = URI.create(value.endsWith("/") ? value : value + "/");
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) throw new IllegalArgumentException("base-url deve ser uma URL HTTP(S) sem query, credenciais ou fragmento.");
        return uri;
    }
}
