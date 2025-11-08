package io.github.programer3.keepalldrops.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.programer3.keepalldrops.KeepAllDrops;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static KeepExplosionsConfig config;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("keepalldrops.json");

    public static void init() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(r, KeepExplosionsConfig.class);
                    if (config == null) config = new KeepExplosionsConfig();
                }
            } else {
                config = new KeepExplosionsConfig();
                save();
            }
        } catch (Throwable t) {
            KeepAllDrops.LOGGER.warn("Failed to load keepalldrops config, using defaults.", t);
            config = new KeepExplosionsConfig();
        }
    }

    public static KeepExplosionsConfig getConfig() {
        if (config == null) init();
        return config;
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(getConfig(), w);
            }
        } catch (IOException e) {
            KeepAllDrops.LOGGER.error("Failed to save keepalldrops config to {}", CONFIG_PATH, e);
        }
    }
}
