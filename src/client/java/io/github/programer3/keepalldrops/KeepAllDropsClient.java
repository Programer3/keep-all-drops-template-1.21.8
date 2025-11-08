package io.github.programer3.keepalldrops;

import io.github.programer3.keepalldrops.config.ConfigManager;
import net.fabricmc.api.ClientModInitializer;

public class KeepAllDropsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ConfigManager.init();
        KeepAllDrops.LOGGER.info("KeepAllDrops (client) initialized. Config loaded: {}", ConfigManager.getConfig());
	}
}