package com.elibennett.hardcoreexpeditions;

import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreExpeditions implements ModInitializer {
	public static final String MOD_ID = "hardcore_expeditions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(ExpeditionSyncPayload.TYPE, ExpeditionSyncPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(ExpeditionManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(ExpeditionManager::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(ExpeditionManager::onServerStopped);
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			ExpeditionManager manager = ExpeditionManager.get();
			if (manager != null) {
				manager.save();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ExpeditionManager manager = ExpeditionManager.get();
			if (manager != null) {
				manager.tick();
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ExpeditionManager manager = ExpeditionManager.get();
			if (manager != null) {
				manager.onJoin(handler.player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ExpeditionManager manager = ExpeditionManager.get();
			if (manager != null) {
				manager.onLeave(handler.player);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ExpeditionCommands.register(dispatcher));
	}
}
