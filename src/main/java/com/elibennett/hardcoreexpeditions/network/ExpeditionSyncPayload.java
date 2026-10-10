package com.elibennett.hardcoreexpeditions.network;

import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client snapshot of the expedition, sent every second and whenever something changes.
 * Only players with the mod installed receive it; everyone else gets the boss bar and titles instead.
 *
 * @param phase           ordinal of {@link com.elibennett.hardcoreexpeditions.ExpeditionData.Phase}
 * @param timerRunning    whether the client should keep counting the timer up between updates
 * @param countdown       seconds until the new world, during the COUNTDOWN phase
 * @param deadPlayer      who died (empty when the reset was started with a command)
 * @param deathMessage    vanilla death message, e.g. "Steve was slain by Zombie"
 * @param leaderboard     all-time deaths per player, most deaths first (shown while holding Tab)
 * @param expeditionsPlayed how many expeditions have ended so far
 */
public record ExpeditionSyncPayload(
		int expedition,
		int phase,
		long elapsedMs,
		boolean timerRunning,
		int totalDeaths,
		long bestTimeMs,
		int countdown,
		String deadPlayer,
		Component deathMessage,
		List<PlayerDeaths> leaderboard,
		int expeditionsPlayed
) implements CustomPacketPayload {
	public record PlayerDeaths(String name, int deaths) {
	}

	public static final Type<ExpeditionSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, "sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExpeditionSyncPayload> CODEC = StreamCodec.of(ExpeditionSyncPayload::write, ExpeditionSyncPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, ExpeditionSyncPayload payload) {
		buf.writeVarInt(payload.expedition);
		buf.writeVarInt(payload.phase);
		buf.writeVarLong(payload.elapsedMs);
		buf.writeBoolean(payload.timerRunning);
		buf.writeVarInt(payload.totalDeaths);
		buf.writeVarLong(payload.bestTimeMs);
		buf.writeVarInt(payload.countdown);
		buf.writeUtf(payload.deadPlayer);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.deathMessage);
		buf.writeVarInt(payload.leaderboard.size());
		for (PlayerDeaths entry : payload.leaderboard) {
			buf.writeUtf(entry.name);
			buf.writeVarInt(entry.deaths);
		}
		buf.writeVarInt(payload.expeditionsPlayed);
	}

	private static ExpeditionSyncPayload read(RegistryFriendlyByteBuf buf) {
		int expedition = buf.readVarInt();
		int phase = buf.readVarInt();
		long elapsedMs = buf.readVarLong();
		boolean timerRunning = buf.readBoolean();
		int totalDeaths = buf.readVarInt();
		long bestTimeMs = buf.readVarLong();
		int countdown = buf.readVarInt();
		String deadPlayer = buf.readUtf();
		Component deathMessage = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		int count = buf.readVarInt();
		List<PlayerDeaths> leaderboard = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			leaderboard.add(new PlayerDeaths(buf.readUtf(), buf.readVarInt()));
		}
		int expeditionsPlayed = buf.readVarInt();
		return new ExpeditionSyncPayload(expedition, phase, elapsedMs, timerRunning, totalDeaths, bestTimeMs, countdown, deadPlayer, deathMessage, leaderboard, expeditionsPlayed);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
