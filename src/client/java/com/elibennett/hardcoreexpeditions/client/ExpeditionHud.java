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

	/** One small line in the top-left: "Expedition #3  12:34". No background box. */
	private static void drawPanel(GuiGraphicsExtractor graphics, Font font, ExpeditionSyncPayload state) {
		String title = "Expedition #" + state.expedition();
		String time = ExpeditionTime.format(ClientExpeditionState.elapsedMs());
		// The timer's color shows the state: white while running, red after a death, green once the dragon is dead.
		int timerColor = switch (ClientExpeditionState.phase()) {
			case RUNNING -> WHITE;
			case COUNTDOWN -> RED;
			case COMPLETED -> GREEN;
		};

		int x = 4;
		int y = 4;
		graphics.text(font, title, x, y, GOLD, true);
		graphics.text(font, time, x + font.width(title) + 6, y, timerColor, true);
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
