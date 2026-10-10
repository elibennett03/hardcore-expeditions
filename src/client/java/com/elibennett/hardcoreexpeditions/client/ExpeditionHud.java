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

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the expedition line (top-left), the stats panel while Tab is held, and, after a death,
 * the full-screen "<player> has died" overlay with the countdown to the new world.
 */
final class ExpeditionHud implements HudElement {
	private static final int GOLD = 0xFFFFAA00;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF5555;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ExpeditionSyncPayload state = ClientExpeditionState.get();
		Minecraft minecraft = Minecraft.getInstance();
		if (state == null) {
			return;
		}

		Font font = minecraft.font;
		// F1 hides the panel, but never the death overlay.
		if (!minecraft.gameRenderer.gameRenderState().guiRenderState.isHudHidden) {
			drawPanel(graphics, font, state);
			if (minecraft.options.keyPlayerList.isDown() && minecraft.player != null) {
				drawStats(graphics, font, state, minecraft.player.getScoreboardName());
			}
		}
		if (ClientExpeditionState.phase() == Phase.COUNTDOWN) {
			drawDeathOverlay(graphics, font, state);
		}
	}

	/** One small line in the top-left: "Expedition #3  12:34  7 deaths". No background box. */
	private static void drawPanel(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state) {
		String title = "Expedition #" + state.expedition();
		String time = ExpeditionTime.format(ClientExpeditionState.elapsedMs());
		String deaths = state.totalDeaths() == 1 ? "1 death" : state.totalDeaths() + " deaths";
		// The timer's color shows the state: white while running, red after a death, green once the dragon is dead.
		int timerColor = switch (ClientExpeditionState.phase()) {
			case RUNNING -> WHITE;
			case COUNTDOWN -> RED;
			case COMPLETED -> GREEN;
		};

		int x = 4;
		int y = 4;
		graphics.text(font, title, x, y, GOLD, true);
		x += font.width(title) + 6;
		graphics.text(font, time, x, y, timerColor, true);
		x += font.width(time) + 6;
		graphics.text(font, deaths, x, y, GRAY, true);
	}

	/** Shown under the top line while Tab is held: who has died the most, plus all-time stats. */
	private static void drawStats(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state, String ownName) {
		int padding = 5;
		int rowHeight = font.lineHeight + 3;
		int gap = 16;

		List<String[]> rows = new ArrayList<>();
		for (ExpeditionSyncPayload.PlayerDeaths entry : state.leaderboard()) {
			rows.add(new String[] {(rows.size() + 1) + ". " + entry.name(), String.valueOf(entry.deaths())});
		}
		String[][] totals = {
				{"Total deaths", String.valueOf(state.totalDeaths())},
				{"Expeditions", String.valueOf(state.expeditionsPlayed())},
				{"Best time", state.bestTimeMs() >= 0 ? ExpeditionTime.format(state.bestTimeMs()) : "-"},
		};

		String header = "Most deaths";
		int width = font.width(header);
		for (String[] row : rows) {
			width = Math.max(width, font.width(row[0]) + gap + font.width(row[1]));
		}
		for (String[] row : totals) {
			width = Math.max(width, font.width(row[0]) + gap + font.width(row[1]));
		}
		int listRows = Math.max(rows.size(), 1);
		int height = rowHeight * (1 + listRows + totals.length) + 4;

		int left = 4;
		int top = 4 + font.lineHeight + 5;
		int right = left + width + padding * 2;
		graphics.fill(left, top, right, top + height + padding * 2, 0x80000000);

		int x = left + padding;
		int y = top + padding;
		graphics.text(font, header, x, y, GOLD, true);
		y += rowHeight;

		if (rows.isEmpty()) {
			graphics.text(font, "No deaths yet", x, y, GRAY, true);
			y += rowHeight;
		}
		for (int i = 0; i < rows.size(); i++) {
			String[] row = rows.get(i);
			boolean you = state.leaderboard().get(i).name().equals(ownName);
			graphics.text(font, row[0], x, y, you ? YELLOW : WHITE, true);
			graphics.text(font, row[1], right - padding - font.width(row[1]), y, RED, true);
			y += rowHeight;
		}

		// Thin divider between the leaderboard and the totals.
		graphics.fill(x, y, right - padding, y + 1, 0x40FFFFFF);
		y += 4;
		for (String[] row : totals) {
			graphics.text(font, row[0], x, y, GRAY, true);
			graphics.text(font, row[1], right - padding - font.width(row[1]), y, WHITE, true);
			y += rowHeight;
		}
	}

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

	private static void drawCentered(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, -font.width(text) / 2, 0, color, true);
		graphics.pose().popMatrix();
	}
}
