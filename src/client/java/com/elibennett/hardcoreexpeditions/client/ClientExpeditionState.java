package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.ExpeditionData.Phase;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.minecraft.util.Util;

/** The latest expedition update from the server. Null when not connected to a server running the mod. */
final class ClientExpeditionState {
	private static final Phase[] PHASES = Phase.values();

	private static ExpeditionSyncPayload latest;
	private static long receivedAtMs;
	/** When the death overlay appeared, for its fade-in. */
	private static long countdownStartedAtMs;
	/** When the countdown number last changed, for its pulse and the draining bar. */
	private static long countdownTickedAtMs;

	private ClientExpeditionState() {
	}

	static void update(ExpeditionSyncPayload payload) {
		long now = Util.getMillis();
		boolean wasCountdown = latest != null && phase(latest) == Phase.COUNTDOWN;
		if (phase(payload) == Phase.COUNTDOWN) {
			if (!wasCountdown) {
				countdownStartedAtMs = now;
				countdownTickedAtMs = now;
			} else if (payload.countdown() != latest.countdown()) {
				countdownTickedAtMs = now;
			}
		}
		latest = payload;
		receivedAtMs = now;
	}

	static void clear() {
		latest = null;
	}

	static ExpeditionSyncPayload get() {
		return latest;
	}

	static Phase phase(ExpeditionSyncPayload state) {
		return PHASES[state.phase()];
	}

	/** Server only updates once a second; count up locally in between so the timer ticks smoothly. */
	static long elapsedMs(ExpeditionSyncPayload state) {
		if (state != latest || !state.timerRunning()) {
			return state.elapsedMs();
		}
		return state.elapsedMs() + (Util.getMillis() - receivedAtMs);
	}

	static long msSinceCountdownStarted() {
		return Util.getMillis() - countdownStartedAtMs;
	}

	static long msSinceCountdownTicked() {
		return Util.getMillis() - countdownTickedAtMs;
	}
}
