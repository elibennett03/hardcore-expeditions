package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Key binds, changeable in Options > Controls > Key Binds under "Hardcore Expeditions". */
final class ExpeditionKeys {
	private static KeyMapping stats;
	private static KeyMapping editHud;
	private static boolean statsToggledOn;

	private ExpeditionKeys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, "main"));
		// Defaults to Tab, same as the player list; both still work when they share a key.
		stats = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hardcore_expeditions.stats", InputConstants.KEY_TAB, category));
		editHud = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hardcore_expeditions.edit_hud", InputConstants.UNKNOWN.getValue(), category));
	}

	static void tick(Minecraft minecraft) {
		while (stats.consumeClick()) {
			statsToggledOn = !statsToggledOn;
		}
		while (editHud.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new HudEditScreen(null));
			}
		}
	}

	static boolean statsVisible() {
		return HudConfig.get().statsToggle ? statsToggledOn : stats.isDown();
	}
}
