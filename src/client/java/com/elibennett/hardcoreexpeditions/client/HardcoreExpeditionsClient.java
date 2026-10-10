package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class HardcoreExpeditionsClient implements ClientModInitializer {
	/** Set by /expeditionhud; opened on the next tick, after the chat screen has closed. */
	private static boolean openEditorNextTick;

	@Override
	public void onInitializeClient() {
		// Registering this receiver is also how the server knows we have the mod: it then sends us
		// these updates instead of showing the vanilla boss bar and titles.
		ClientPlayNetworking.registerGlobalReceiver(ExpeditionSyncPayload.TYPE, (payload, context) -> ClientExpeditionState.update(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientExpeditionState.clear());

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, "expedition"), new ExpeditionHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST, Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, "death_overlay"), ExpeditionHud.DEATH_OVERLAY);
		// Chat text always draws on top of HUD elements, so hide it for the few seconds the death
		// overlay is up (the overlay shows the death message itself). Typing with T still works.
		HudElementRegistry.replaceElement(VanillaHudElements.CHAT, chat -> (graphics, deltaTracker) -> {
			if (!ExpeditionHud.deathOverlayShowing()) {
				chat.extractRenderState(graphics, deltaTracker);
			}
		});

		ExpeditionKeys.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ExpeditionKeys.tick(client);
			if (openEditorNextTick && client.gui.screen() == null) {
				openEditorNextTick = false;
				client.gui.setScreen(new HudEditScreen(null));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				ClientCommands.literal("expeditionhud").executes(ctx -> {
					openEditorNextTick = true;
					return 1;
				})));
	}
}
