package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.ExpeditionData.Phase;
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
 * Draws the expedition line, the stats panel while its key is active, and, after a death, the
 * full-screen "<player> has died" overlay with the countdown to the new world. Where the line and
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
		// F1 hides the line and stats, but never the death overlay.
		if (!minecraft.gameRenderer.gameRenderState().guiRenderState.isHudHidden) {
			Layout layout = layout(font, state, graphics.guiWidth(), graphics.guiHeight());
			drawLine(graphics, font, state, layout.line());
			if (ExpeditionKeys.statsVisible() && minecraft.player != null) {
				drawStats(graphics, font, state, layout.stats(), minecraft.player.getScoreboardName());
			}
		}
		if (phase(state) == Phase.COUNTDOWN) {
			drawDeathOverlay(graphics, font, state);
		}
	}

	private static Phase phase(ExpeditionSyncPayload state) {
		return Phase.values()[state.phase()];
	}

	// ---------------------------------------------------------------------------------------------
	// Layout

	static Layout layout(Font font, ExpeditionSyncPayload state, int screenWidth, int screenHeight) {
		HudConfig config = HudConfig.get();
		int lineWidth = scaled(lineWidth(font, state), config.lineScale);
		int lineHeight = scaled(font.lineHeight, config.lineScale);
		Box line = new Box(
				fractionToPos(config.lineX, lineWidth, screenWidth),
				fractionToPos(config.lineY, lineHeight, screenHeight),
				lineWidth, lineHeight, config.lineScale);

		int[] panel = statsSize(font, state);
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

	private static String title(ExpeditionSyncPayload state) {
		return "Expedition #" + state.expedition();
	}

	private static String time(ExpeditionSyncPayload state) {
		return ExpeditionTime.format(ClientExpeditionState.elapsedMs(state));
	}

	private static String deaths(ExpeditionSyncPayload state) {
		return state.totalDeaths() == 1 ? "1 death" : state.totalDeaths() + " deaths";
	}

	private static int lineWidth(Font font, ExpeditionSyncPayload state) {
		return font.width(title(state)) + 6 + font.width(time(state)) + 6 + font.width(deaths(state));
	}

	static void drawLine(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state, Box box) {
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
		graphics.text(font, title(state), x, 0, GOLD, true);
		x += font.width(title(state)) + 6;
		graphics.text(font, time(state), x, 0, timerColor, true);
		x += font.width(time(state)) + 6;
		graphics.text(font, deaths(state), x, 0, GRAY, true);
		graphics.pose().popMatrix();
	}

	// ---------------------------------------------------------------------------------------------
	// The stats panel: who has died the most, plus all-time stats

	private static List<String[]> leaderboardRows(ExpeditionSyncPayload state) {
		List<String[]> rows = new ArrayList<>();
		for (ExpeditionSyncPayload.PlayerDeaths entry : state.leaderboard()) {
			rows.add(new String[] {(rows.size() + 1) + ". " + entry.name(), String.valueOf(entry.deaths())});
		}
		return rows;
	}

	private static String[][] totalRows(ExpeditionSyncPayload state) {
		return new String[][] {
				{"Total deaths", String.valueOf(state.totalDeaths())},
				{"Expeditions", String.valueOf(state.expeditionsPlayed())},
				{"Best time", state.bestTimeMs() >= 0 ? ExpeditionTime.format(state.bestTimeMs()) : "-"},
		};
	}

	private static int rowHeight(Font font) {
		return font.lineHeight + 3;
	}

	/** Unscaled {width, height} of the stats panel, including its padding. */
	private static int[] statsSize(Font font, ExpeditionSyncPayload state) {
		List<String[]> rows = leaderboardRows(state);
		String[][] totals = totalRows(state);
		int width = font.width("Most deaths");
		for (String[] row : rows) {
			width = Math.max(width, font.width(row[0]) + ROW_GAP + font.width(row[1]));
		}
		for (String[] row : totals) {
			width = Math.max(width, font.width(row[0]) + ROW_GAP + font.width(row[1]));
		}
		int height = rowHeight(font) * (1 + Math.max(rows.size(), 1) + totals.length) + 4;
		return new int[] {width + PANEL_PADDING * 2, height + PANEL_PADDING * 2};
	}

	static void drawStats(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state, Box box, String ownName) {
		List<String[]> rows = leaderboardRows(state);
		int[] size = statsSize(font, state);
		int rowHeight = rowHeight(font);

		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		graphics.fill(0, 0, size[0], size[1], 0x80000000);

		int x = PANEL_PADDING;
		int right = size[0] - PANEL_PADDING;
		int y = PANEL_PADDING;
		graphics.text(font, "Most deaths", x, y, GOLD, true);
		y += rowHeight;

		if (rows.isEmpty()) {
			graphics.text(font, "No deaths yet", x, y, GRAY, true);
			y += rowHeight;
		}
		for (int i = 0; i < rows.size(); i++) {
			String[] row = rows.get(i);
			boolean you = state.leaderboard().get(i).name().equals(ownName);
			graphics.text(font, row[0], x, y, you ? YELLOW : WHITE, true);
			graphics.text(font, row[1], right - font.width(row[1]), y, RED, true);
			y += rowHeight;
		}

		// Thin divider between the leaderboard and the totals.
		graphics.fill(x, y, right, y + 1, 0x40FFFFFF);
		y += 4;
		for (String[] row : totalRows(state)) {
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

		graphics.fill(0, 0, width, height, 0x70400000);

		String headline = state.deadPlayer().isEmpty() ? "New expedition!" : state.deadPlayer() + " has died";
		drawCentered(graphics, font, Component.literal(headline), centerX, centerY - 60, 3.0F, state.deadPlayer().isEmpty() ? GOLD : RED);

		if (!state.deathMessage().getString().isEmpty()) {
			drawCentered(graphics, font, state.deathMessage(), centerX, centerY - 28, 1.0F, WHITE);
		}

		drawCentered(graphics, font, Component.literal("New world in"), centerX, centerY - 10, 1.0F, GRAY);
		drawCentered(graphics, font, Component.literal(String.valueOf(state.countdown())), centerX, centerY + 4, 5.0F, WHITE);
	}

	static void drawCentered(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, -font.width(text) / 2, 0, color, true);
		graphics.pose().popMatrix();
	}
}
