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

/**
 * Draws the expedition panel (top-left) and, after a death, the full-screen
 * "<player> has died" overlay with the countdown to the new world.
 */
final class ExpeditionHud implements HudElement {
	private static final int GOLD = 0xFFFFAA00;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF5555;
	private static final int GREEN = 0xFF55FF55;

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
		}
		if (ClientExpeditionState.phase() == Phase.COUNTDOWN) {
			drawDeathOverlay(graphics, font, state);
		}
	}

	private static void drawPanel(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state) {
		Phase phase = ClientExpeditionState.phase();
		String title = "Expedition #" + state.expedition();
		String time = ExpeditionTime.format(ClientExpeditionState.elapsedMs());
		String deaths = "Deaths: " + state.totalDeaths();
		String status = switch (phase) {
			case RUNNING -> null;
			case COUNTDOWN -> "Failed";
			case COMPLETED -> "Dragon defeated!";
		};
		String best = state.bestTimeMs() >= 0 ? "Best: " + ExpeditionTime.format(state.bestTimeMs()) : null;

		int x = 6;
		int y = 6;
		int lineHeight = font.lineHeight + 2;
		int width = Math.max(Math.max(font.width(title), font.width(time) * 2), font.width(deaths));
		if (status != null) {
			width = Math.max(width, font.width(status));
		}
		if (best != null) {
			width = Math.max(width, font.width(best));
		}
		int lines = 2 + (status != null ? 1 : 0) + (best != null ? 1 : 0);
		int height = lineHeight * lines + font.lineHeight * 2 + 6;

		graphics.fill(x - 4, y - 4, x + width + 4, y + height, 0x90000000);

		graphics.text(font, title, x, y, GOLD, true);
		y += lineHeight;

		// Timer drawn at double size so it's readable at a glance.
		int timerColor = switch (phase) {
			case RUNNING -> WHITE;
			case COUNTDOWN -> RED;
			case COMPLETED -> GREEN;
		};
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(font, time, 0, 0, timerColor, true);
		graphics.pose().popMatrix();
		y += font.lineHeight * 2 + 4;

		graphics.text(font, deaths, x, y, RED, true);
		y += lineHeight;
		if (status != null) {
			graphics.text(font, status, x, y, phase == Phase.COMPLETED ? GREEN : RED, true);
			y += lineHeight;
		}
		if (best != null) {
			graphics.text(font, best, x, y, GRAY, true);
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
