package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.ExpeditionData;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.minecraft.util.Util;

/** The latest expedition update from the server. Null when not connected to a server running the mod. */
final class ClientExpeditionState {
	private static ExpeditionSyncPayload latest;
	private static long receivedAtMs;

	private ClientExpeditionState() {
	}

	static void update(ExpeditionSyncPayload payload) {
		latest = payload;
		receivedAtMs = Util.getMillis();
	}

	static void clear() {
		latest = null;
	}

	static ExpeditionSyncPayload get() {
		return latest;
	}

	static ExpeditionData.Phase phase() {
		return ExpeditionData.Phase.values()[latest.phase()];
	}

	/** Server only updates once a second; count up locally in between so the timer ticks smoothly. */
	static long elapsedMs() {
		if (latest == null) {
			return 0;
		}
		return latest.timerRunning() ? latest.elapsedMs() + (Util.getMillis() - receivedAtMs) : latest.elapsedMs();
	}
}
