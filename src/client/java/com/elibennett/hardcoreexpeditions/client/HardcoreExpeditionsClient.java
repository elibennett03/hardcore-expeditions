package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class HardcoreExpeditionsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Registering this receiver is also how the server knows we have the mod: it then sends us
		// these updates instead of showing the vanilla boss bar and titles.
		ClientPlayNetworking.registerGlobalReceiver(ExpeditionSyncPayload.TYPE, (payload, context) -> ClientExpeditionState.update(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientExpeditionState.clear());

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, "expedition"), new ExpeditionHud());
	}
}
