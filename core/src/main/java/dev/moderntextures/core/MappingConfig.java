package dev.moderntextures.core;

import java.util.*;

public record MappingConfig(int schema, String sourceVersion, String prismarineCommit, String viaMappingsCommit,
                            boolean autoItems, Map<String,String> textureAliases, List<ItemRule> items) {
    public record ItemRule(String source, String substitute, List<String> clients, String texture,
                           String model, Integer customModelData, String originalIdentifier, int stringIndex) {}
    public void validate() {
        if (schema != 1 || !"26.3".equals(sourceVersion)) throw new IllegalArgumentException("mappings.json: schema deve ser 1 e sourceVersion deve ser 26.3.");
        if (prismarineCommit == null || !prismarineCommit.matches("[a-f0-9]{40}") || viaMappingsCommit == null || !viaMappingsCommit.matches("[a-f0-9]{40}")) throw new IllegalArgumentException("Fixe commits SHA-1 de Prismarine e Via mappings.");
        if (textureAliases == null || items == null) throw new IllegalArgumentException("textureAliases e items são obrigatórios.");
        textureAliases.forEach((k,v) -> { texture(k); texture(v); });
        Set<String> used = new HashSet<>();
        for (ItemRule r : items) {
            if (r == null) throw new IllegalArgumentException("Regra nula no array items.");
            identifier(r.source); identifier(r.substitute); texture(r.texture);
            if (r.model == null || !Set.of("generated", "handheld").contains(r.model)) throw new IllegalArgumentException("Modelo deve ser generated ou handheld: " + r.source);
            if (r.clients == null || r.clients.isEmpty() || r.stringIndex < 0 || r.stringIndex > 63) throw new IllegalArgumentException("clients/stringIndex inválido: " + r.source);
            for (String version : r.clients) {
                var p = Profiles.version(version);
                if (p.protocol() < 477) throw new IllegalArgumentException("Itens condicionais exigem cliente >=1.14: " + version);
                if (p.protocol() < 769 && (r.customModelData == null || r.customModelData < 1 || r.customModelData > 16777215)) throw new IllegalArgumentException("customModelData inteiro exato obrigatório para " + version);
                if (p.protocol() >= 769 && (r.originalIdentifier == null || !r.originalIdentifier.matches("(?:minecraft:)?[a-z0-9_./-]+"))) throw new IllegalArgumentException("originalIdentifier obrigatório: " + r.source);
                if (!used.add(version + ":" + r.source)) throw new IllegalArgumentException("Regra duplicada: " + version + ":" + r.source);
            }
        }
    }
    public List<ItemRule> select(String client) { return items.stream().filter(r -> r.clients.contains(client)).toList(); }
    public static void identifier(String id) {
        if (id == null || !id.matches("minecraft:[a-z0-9_./-]+") || id.contains("..")) throw new IllegalArgumentException("Identificador vanilla inválido: " + id);
    }
    public static void texture(String path) {
        if (path == null || !path.matches("assets/minecraft/textures/[a-z0-9_./-]+\\.png") || path.contains("..")) throw new IllegalArgumentException("Caminho de textura inválido: " + path);
    }
}
