package io.github.programer3.keepalldrops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.fabricmc.api.ModInitializer;

public class KeepAllDrops implements ModInitializer {
    public static final String MOD_ID = "keepalldrops";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("KeepAllDrops initialized.");
    }
}
