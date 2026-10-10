package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.ExpeditionData.Phase;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;

/**
 * Drag the timer line and stats panel to move them, and resize them with the sliders. Opened from
 * Mod Menu, the "Edit HUD Layout" key bind, or {@code /expeditionhud}.
 */
public final class HudEditScreen extends Screen {
	private static final float MIN_SCALE = 0.5F;
	private static final float MAX_SCALE = 2.0F;

	/** Shown when there's no live expedition (e.g. opened from the title screen). */
	private static final ExpeditionSyncPayload SAMPLE = new ExpeditionSyncPayload(
			3, Phase.RUNNING.ordinal(), 754_000, false, 9, 5_025_000, 0, "", Component.empty(),
			List.of(new ExpeditionSyncPayload.PlayerDeaths("Steve", 5), new ExpeditionSyncPayload.PlayerDeaths("Alex", 3), new ExpeditionSyncPayload.PlayerDeaths("Notch", 1)),
			12);

	private enum Dragging { NONE, LINE, STATS }

	private final Screen parent;
	private final HudConfig config = HudConfig.get();
	private Dragging dragging = Dragging.NONE;
	private double grabOffsetX;
	private double grabOffsetY;
	private Button modeButton;

	public HudEditScreen(Screen parent) {
		super(Component.literal("Edit HUD Layout"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = this.width / 2;
		int row1 = this.height - 54;
		int row2 = this.height - 30;

		addRenderableWidget(new ScaleSlider(center - 155, row1, "Timer size", this.config.lineScale, value -> this.config.lineScale = value));
		addRenderableWidget(new ScaleSlider(center + 5, row1, "Stats size", this.config.statsScale, value -> this.config.statsScale = value));

		this.modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
			this.config.statsToggle = !this.config.statsToggle;
			button.setMessage(modeLabel());
		}).bounds(center - 155, row2, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Change key..."), button ->
				this.minecraft.gui.setScreen(new KeyBindsScreen(this, this.minecraft.options))
		).bounds(center - 51, row2, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
			this.config.reset();
			this.rebuildWidgets();
		}).bounds(center + 53, row2, 49, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(center + 106, row2, 49, 20).build());
	}

	private Component modeLabel() {
		return Component.literal("Stats: " + (this.config.statsToggle ? "Toggle" : "Hold"));
	}

	private ExpeditionSyncPayload previewState() {
		ExpeditionSyncPayload live = ClientExpeditionState.get();
		return live != null ? live : SAMPLE;
	}

	private ExpeditionHud.Layout layout() {
		return ExpeditionHud.layout(this.font, previewState(), this.width, this.height);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);

		ExpeditionSyncPayload state = previewState();
		ExpeditionHud.Layout layout = layout();
		String ownName = this.minecraft.player != null ? this.minecraft.player.getScoreboardName() : "";
		ExpeditionHud.drawLine(graphics, this.font, state, layout.line());
		ExpeditionHud.drawStats(graphics, this.font, state, layout.stats(), ownName);
		outline(graphics, layout.line(), layout.line().contains(mouseX, mouseY) || this.dragging == Dragging.LINE);
		outline(graphics, layout.stats(), layout.stats().contains(mouseX, mouseY) || this.dragging == Dragging.STATS);

		int hintY = this.height / 2 - 10;
		ExpeditionHud.drawCentered(graphics, this.font, Component.literal("Drag the timer or the stats panel to move them"), this.width / 2, hintY, 1.0F, ExpeditionHud.WHITE);
		ExpeditionHud.drawCentered(graphics, this.font, Component.literal("Drag the stats panel back onto the timer to re-attach it"), this.width / 2, hintY + 12, 1.0F, ExpeditionHud.GRAY);
	}

	private static void outline(GuiGraphicsExtractor graphics, ExpeditionHud.Box box, boolean highlighted) {
		graphics.outline(box.x() - 2, box.y() - 2, box.width() + 4, box.height() + 4, highlighted ? 0xFFFFFF55 : 0x80FFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}
		ExpeditionHud.Layout layout = layout();
		// The stats panel is checked first since it's drawn on top of the line when they overlap.
		if (layout.stats().contains(event.x(), event.y())) {
			startDrag(Dragging.STATS, layout.stats(), event);
			return true;
		}
		if (layout.line().contains(event.x(), event.y())) {
			startDrag(Dragging.LINE, layout.line(), event);
			return true;
		}
		return false;
	}

	private void startDrag(Dragging target, ExpeditionHud.Box box, MouseButtonEvent event) {
		this.dragging = target;
		this.grabOffsetX = event.x() - box.x();
		this.grabOffsetY = event.y() - box.y();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging == Dragging.NONE) {
			return super.mouseDragged(event, dx, dy);
		}
		ExpeditionHud.Layout layout = layout();
		double x = event.x() - this.grabOffsetX;
		double y = event.y() - this.grabOffsetY;
		if (this.dragging == Dragging.LINE) {
			this.config.lineX = ExpeditionHud.posToFraction(x, layout.line().width(), this.width);
			this.config.lineY = ExpeditionHud.posToFraction(y, layout.line().height(), this.height);
		} else {
			this.config.statsAttached = false;
			this.config.statsX = ExpeditionHud.posToFraction(x, layout.stats().width(), this.width);
			this.config.statsY = ExpeditionHud.posToFraction(y, layout.stats().height(), this.height);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging == Dragging.STATS) {
			// Dropped on the timer line: snap back to hanging under it.
			ExpeditionHud.Layout layout = layout();
			if (layout.line().contains(event.x(), event.y())) {
				this.config.statsAttached = true;
			}
		}
		if (this.dragging != Dragging.NONE) {
			this.dragging = Dragging.NONE;
			this.config.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		this.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Slider from 50% to 200%. */
	private static final class ScaleSlider extends AbstractSliderButton {
		private final String label;
		private final Consumer<Float> onChange;

		ScaleSlider(int x, int y, String label, float scale, Consumer<Float> onChange) {
			super(x, y, 150, 20, Component.empty(), (scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE));
			this.label = label;
			this.onChange = onChange;
			updateMessage();
		}

		private float scale() {
			// Snap to 5% steps so it's easy to land on 100%.
			float raw = MIN_SCALE + (float) this.value * (MAX_SCALE - MIN_SCALE);
			return Mth.clamp(Math.round(raw * 20.0F) / 20.0F, MIN_SCALE, MAX_SCALE);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(this.label + ": " + Math.round(scale() * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			this.onChange.accept(scale());
		}
	}
}
