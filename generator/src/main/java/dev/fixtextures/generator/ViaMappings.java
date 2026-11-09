package dev.fixtextures.generator;

import dev.fixtextures.core.MappingConfig;
import java.util.*;

/** Explicit user-authored exceptions only; never fetches Via registry or translation data. */
final class ViaMappings {
    private final MappingConfig config;
    ViaMappings(Downloads ignored, MappingConfig config) { this.config = config; }
    String alias(String targetPath) { return config.textureAliases().get(targetPath); }
    List<MappingConfig.ItemRule> exceptions(String version) { return config.select(version); }
}
