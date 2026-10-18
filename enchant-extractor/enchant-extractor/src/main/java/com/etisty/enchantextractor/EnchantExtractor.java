package com.etisty.enchantextractor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class EnchantExtractor implements ModInitializer {
	public static final String MOD_ID = "enchant_extractor";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> DisenchantCommand.register(dispatcher)
		);
	}
}
