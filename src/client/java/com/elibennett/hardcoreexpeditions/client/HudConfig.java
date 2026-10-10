package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Each player's own HUD layout, saved in {@code .minecraft/config/hardcore_expeditions-client.json}.
 * Positions are stored as fractions of the screen (0 = left/top edge, 1 = right/bottom edge) so the
 * layout keeps its place when the window or GUI scale changes.
 */
final class HudConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(HardcoreExpeditions.MOD_ID + "-client.json");
	private static HudConfig instance;

	float lineX = 0.0F;
	float lineY = 0.0F;
	float lineScale = 1.0F;

	/** While true the stats panel sits directly under the timer line and moves with it. */
	boolean statsAttached = true;
	float statsX = 0.0F;
	float statsY = 0.0F;
	float statsScale = 1.0F;

	/** false = stats show while the key is held, true = press once to show, again to hide. */
	boolean statsToggle = false;

	static HudConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static HudConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				HudConfig config = GSON.fromJson(reader, HudConfig.class);
				if (config != null) {
					return config;
				}
			} catch (IOException | RuntimeException e) {
				HardcoreExpeditions.LOGGER.warn("Could not read {}, using the default HUD layout", FILE, e);
			}
		}
		return new HudConfig();
	}

	void save() {
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			HardcoreExpeditions.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	void reset() {
		HudConfig defaults = new HudConfig();
		this.lineX = defaults.lineX;
		this.lineY = defaults.lineY;
		this.lineScale = defaults.lineScale;
		this.statsAttached = defaults.statsAttached;
		this.statsX = defaults.statsX;
		this.statsY = defaults.statsY;
		this.statsScale = defaults.statsScale;
		this.statsToggle = defaults.statsToggle;
	}
}
