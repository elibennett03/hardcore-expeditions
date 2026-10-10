package com.elibennett.hardcoreexpeditions.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds a config button for this mod in Mod Menu that opens the HUD layout editor. Only loaded if Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HudEditScreen::new;
	}
}
