package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.ExpeditionData.Phase;
import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import com.elibennett.hardcoreexpeditions.ExpeditionTime;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the expedition line and the stats panel while its key is active, plus (as a separate
 * element) the full-screen "<player> has died" overlay with the countdown to the new world. Where the line and
 * panel go comes from {@link HudConfig}, which players change in {@link HudEditScreen}.
 */
final class ExpeditionHud implements HudElement {
	static final int GOLD = 0xFFFFAA00;
	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFAAAAAA;
	static final int RED = 0xFFFF5555;
	static final int GREEN = 0xFF55FF55;
	static final int YELLOW = 0xFFFFFF55;

	/** Gap kept between the HUD and the edge of the screen. */
	private static final int MARGIN = 4;
	private static final int PANEL_PADDING = 5;
	private static final int ROW_GAP = 16;


	/** Where something is drawn on screen; width and height already include the scale. */
	record Box(int x, int y, int width, int height, float scale) {
		boolean contains(double px, double py) {
			return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
		}
	}

	record Layout(Box line, Box stats) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ExpeditionSyncPayload state = ClientExpeditionState.get();
		Minecraft minecraft = Minecraft.getInstance();
		// The layout editor draws its own preview.
		if (state == null || minecraft.gui.screen() instanceof HudEditScreen) {
			return;
		}

		Font font = minecraft.font;
		// F1 (hide HUD) and F3 (debug screen, which uses the same corner) hide the line and stats.
		boolean hudHidden = minecraft.gameRenderer.gameRenderState().guiRenderState.isHudHidden;
		if (!hudHidden && !minecraft.getDebugOverlay().showDebugScreen()) {
			boolean showStats = ExpeditionKeys.statsVisible() && minecraft.player != null;
			Layout layout = layout(font, state, graphics.guiWidth(), graphics.guiHeight(), showStats);
			drawLine(graphics, font, state, layout.line());
			if (showStats) {
				drawStats(graphics, font, state, layout.stats(), minecraft.player.getScoreboardName());
			}
		}
	}

	/**
	 * The death overlay is its own HUD element, attached after chat and the player list so it
	 * covers them (see {@link HardcoreExpeditionsClient}).
	 */
	static final HudElement DEATH_OVERLAY = (graphics, deltaTracker) -> {
		if (deathOverlayShowing()) {
			drawDeathOverlay(graphics, Minecraft.getInstance().font, ClientExpeditionState.get());
		}
	};

	static boolean deathOverlayShowing() {
		ExpeditionSyncPayload state = ClientExpeditionState.get();
		return state != null && phase(state) == Phase.COUNTDOWN;
	}

	private static Phase phase(ExpeditionSyncPayload state) {
		return ClientExpeditionState.phase(state);
	}

	// ---------------------------------------------------------------------------------------------
	// Layout

	/** @param includeStats false skips measuring the stats panel (its box is then null) */
	static Layout layout(Font font, ExpeditionSyncPayload state, int screenWidth, int screenHeight, boolean includeStats) {
		HudConfig config = HudConfig.get();
		int lineWidth = scaled(lineText(font, state).width(), config.lineScale);
		int lineHeight = scaled(font.lineHeight, config.lineScale);
		Box line = new Box(
				fractionToPos(config.lineX, lineWidth, screenWidth),
				fractionToPos(config.lineY, lineHeight, screenHeight),
				lineWidth, lineHeight, config.lineScale);
		if (!includeStats) {
			return new Layout(line, null);
		}

		int[] panel = statsPanel(font, state).size();
		int statsWidth = scaled(panel[0], config.statsScale);
		int statsHeight = scaled(panel[1], config.statsScale);
		int statsX;
		int statsY;
		if (config.statsAttached) {
			// Hang under the line, or above it if there's no room below.
			statsX = clamp(line.x(), statsWidth, screenWidth);
			statsY = line.y() + line.height() + 5;
			if (statsY + statsHeight > screenHeight - MARGIN) {
				statsY = line.y() - statsHeight - 5;
			}
			statsY = clamp(statsY, statsHeight, screenHeight);
		} else {
			statsX = fractionToPos(config.statsX, statsWidth, screenWidth);
			statsY = fractionToPos(config.statsY, statsHeight, screenHeight);
		}
		return new Layout(line, new Box(statsX, statsY, statsWidth, statsHeight, config.statsScale));
	}

	/** Converts a 0-1 screen fraction to a pixel position that keeps the whole element on screen. */
	static int fractionToPos(float fraction, int size, int screenSize) {
		int space = Math.max(0, screenSize - 2 * MARGIN - size);
		return MARGIN + Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * space);
	}

	/** The inverse of {@link #fractionToPos}, used while dragging. */
	static float posToFraction(double pos, int size, int screenSize) {
		int space = screenSize - 2 * MARGIN - size;
		return space <= 0 ? 0.0F : Mth.clamp((float) ((pos - MARGIN) / space), 0.0F, 1.0F);
	}

	private static int clamp(int pos, int size, int screenSize) {
		return Mth.clamp(pos, MARGIN, Math.max(MARGIN, screenSize - MARGIN - size));
	}

	private static int scaled(int size, float scale) {
		return Math.round(size * scale);
	}

	// ---------------------------------------------------------------------------------------------
	// The "Expedition #3  12:34  7 deaths" line

	private static final String SEPARATOR = "\u00B7";
	private static final int SPACE = 4;
	private static final int DARK_GRAY = 0xFF555555;

	/** The strings for the line, rebuilt only when the shown time or the server data changes. */
	private record LineText(ExpeditionSyncPayload state, String title, String time, String deaths, int width) {
	}

	private static LineText lineCache;

	private static LineText lineText(Font font, ExpeditionSyncPayload state) {
		String time = ExpeditionTime.format(ClientExpeditionState.elapsedMs(state));
		LineText cached = lineCache;
		if (cached != null && cached.state() == state && cached.time().equals(time)) {
			return cached;
		}
		String title = "Expedition #" + state.expedition();
		String deaths = state.totalDeaths() == 1 ? "1 death" : state.totalDeaths() + " deaths";
		int separator = SPACE + font.width(SEPARATOR) + SPACE;
		int width = font.width(title) + separator + font.width(time) + separator + font.width(deaths);
		lineCache = new LineText(state, title, time, deaths, width);
		return lineCache;
	}

	static void drawLine(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state, Box box) {
		LineText text = lineText(font, state);
		// The timer's color shows the state: white while running, red after a death, green once the dragon is dead.
		int timerColor = switch (phase(state)) {
			case RUNNING -> WHITE;
			case COUNTDOWN -> RED;
			case COMPLETED -> GREEN;
		};

		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		int x = 0;
		x = drawPart(graphics, font, text.title(), x, GOLD);
		x = drawPart(graphics, font, SEPARATOR, x + SPACE, DARK_GRAY) + SPACE;
		x = drawPart(graphics, font, text.time(), x, timerColor);
		x = drawPart(graphics, font, SEPARATOR, x + SPACE, DARK_GRAY) + SPACE;
		drawPart(graphics, font, text.deaths(), x, GRAY);
		graphics.pose().popMatrix();
	}

	/** Draws text at x and returns where the next piece should start. */
	private static int drawPart(GuiGraphicsExtractor graphics, Font font, String text, int x, int color) {
		graphics.text(font, text, x, 0, color, true);
		return x + font.width(text);
	}

	// ---------------------------------------------------------------------------------------------
	// The stats panel: who has died the most, plus all-time stats

	/** Rows and size of the stats panel, rebuilt only when new data arrives from the server. */
	private record StatsPanel(ExpeditionSyncPayload state, List<String[]> rows, String[][] totals, int[] size) {
	}

	private static StatsPanel statsCache;

	private static int rowHeight(Font font) {
		return font.lineHeight + 3;
	}

	private static StatsPanel statsPanel(Font font, ExpeditionSyncPayload state) {
		StatsPanel cached = statsCache;
		if (cached != null && cached.state() == state) {
			return cached;
		}
		List<String[]> rows = new ArrayList<>();
		for (ExpeditionSyncPayload.PlayerDeaths entry : state.leaderboard()) {
			rows.add(new String[] {(rows.size() + 1) + ". " + entry.name(), String.valueOf(entry.deaths())});
		}
		String[][] totals = {
				{"Total deaths", String.valueOf(state.totalDeaths())},
				{"Expeditions", String.valueOf(state.expeditionsPlayed())},
				{"Longest run", state.longestRunMs() >= 0 ? ExpeditionTime.format(state.longestRunMs()) : "-"},
				// Only expeditions where the dragon was killed count here.
				{"Fastest win", state.bestTimeMs() >= 0 ? ExpeditionTime.format(state.bestTimeMs()) : "-"},
		};

		int width = font.width("Most deaths");
		for (String[] row : rows) {
			width = Math.max(width, font.width(row[0]) + ROW_GAP + font.width(row[1]));
		}
		for (String[] row : totals) {
			width = Math.max(width, font.width(row[0]) + ROW_GAP + font.width(row[1]));
		}
		int height = rowHeight(font) * (1 + Math.max(rows.size(), 1) + totals.length) + 4;
		statsCache = new StatsPanel(state, rows, totals, new int[] {width + PANEL_PADDING * 2, height + PANEL_PADDING * 2 + 1});
		return statsCache;
	}

	static void drawStats(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state, Box box, String ownName) {
		StatsPanel panel = statsPanel(font, state);
		int[] size = panel.size();
		int rowHeight = rowHeight(font);

		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		graphics.fill(0, 0, size[0], size[1], 0x90000000);
		// Thin gold accent along the top, matching the expedition title color.
		graphics.fill(0, 0, size[0], 1, 0xC0FFAA00);

		int x = PANEL_PADDING;
		int right = size[0] - PANEL_PADDING;
		int y = PANEL_PADDING + 1;
		graphics.text(font, "Most deaths", x, y, GOLD, true);
		y += rowHeight;

		if (panel.rows().isEmpty()) {
			graphics.text(font, "No deaths yet", x, y, GRAY, true);
			y += rowHeight;
		}
		for (int i = 0; i < panel.rows().size(); i++) {
			String[] row = panel.rows().get(i);
			boolean you = state.leaderboard().get(i).name().equals(ownName);
			graphics.text(font, row[0], x, y, you ? YELLOW : WHITE, true);
			graphics.text(font, row[1], right - font.width(row[1]), y, RED, true);
			y += rowHeight;
		}

		// Thin divider between the leaderboard and the totals.
		graphics.fill(x, y, right, y + 1, 0x40FFFFFF);
		y += 4;
		for (String[] row : panel.totals()) {
			graphics.text(font, row[0], x, y, GRAY, true);
			graphics.text(font, row[1], right - font.width(row[1]), y, WHITE, true);
			y += rowHeight;
		}
		graphics.pose().popMatrix();
	}

	// ---------------------------------------------------------------------------------------------
	// Death overlay

	private static void drawDeathOverlay(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int centerX = width / 2;
		int centerY = height / 2;

		// Start a new drawing layer so the overlay covers chat instead of sitting underneath it.
		graphics.nextStratum();

		// Fade everything in over a quarter second instead of snapping on.
		float fade = Mth.clamp(ClientExpeditionState.msSinceCountdownStarted() / 250.0F, 0.0F, 1.0F);

		// Dark red that's heavier at the top and bottom edges, so the middle stays readable.
		graphics.fillGradient(0, 0, width, centerY, withAlpha(0x500000, 0xB0 * fade), withAlpha(0x400000, 0x50 * fade));
		graphics.fillGradient(0, centerY, width, height, withAlpha(0x400000, 0x50 * fade), withAlpha(0x500000, 0xB0 * fade));

		String headline = state.deadPlayer().isEmpty() ? "New expedition!" : state.deadPlayer() + " has died";
		int headlineColor = state.deadPlayer().isEmpty() ? GOLD : RED;
		// Shrink long names so the headline always fits on screen.
		float headlineScale = Math.min(3.0F, (width - 20) / (float) font.width(headline));
		drawCentered(graphics, font, Component.literal(headline), centerX, centerY - 64, headlineScale, withAlpha(headlineColor, 0xFF * fade));

		if (!state.deathMessage().getString().isEmpty()) {
			drawCentered(graphics, font, state.deathMessage(), centerX, centerY - 32, 1.0F, withAlpha(WHITE, 0xFF * fade));
		}

		drawCentered(graphics, font, Component.literal("New world in"), centerX, centerY - 14, 1.0F, withAlpha(GRAY, 0xFF * fade));

		// The number pops slightly larger each time it changes, then settles.
		long sinceTick = ClientExpeditionState.msSinceCountdownTicked();
		float pop = 1.0F - Mth.clamp(sinceTick / 200.0F, 0.0F, 1.0F);
		float numberScale = 5.0F + pop * 1.5F;
		int numberY = centerY + 2 - Math.round(pop * 3);
		drawCentered(graphics, font, Component.literal(String.valueOf(state.countdown())), centerX, numberY, numberScale, withAlpha(WHITE, 0xFF * fade));

		// Bar under the number that drains smoothly over the countdown.
		int total = ExpeditionManager.COUNTDOWN_SECONDS;
		float secondProgress = Mth.clamp(sinceTick / 1000.0F, 0.0F, 1.0F);
		float remaining = Mth.clamp((state.countdown() - secondProgress) / total, 0.0F, 1.0F);
		int barWidth = 120;
		int barLeft = centerX - barWidth / 2;
		int barTop = centerY + 48;
		graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 3, withAlpha(0x000000, 0x80 * fade));
		graphics.fill(barLeft, barTop, barLeft + Math.round(barWidth * remaining), barTop + 3, withAlpha(RED, 0xFF * fade));
	}

	/** Replaces the alpha (opacity) of a color; alpha is 0-255. */
	private static int withAlpha(int color, float alpha) {
		return ((int) Mth.clamp(alpha, 0.0F, 255.0F) << 24) | (color & 0xFFFFFF);
	}

	static void drawCentered(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, -font.width(text) / 2, 0, color, true);
		graphics.pose().popMatrix();
	}
}
