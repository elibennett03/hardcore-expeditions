package com.elibennett.hardcoreexpeditions;

public final class ExpeditionTime {
	private ExpeditionTime() {
	}

	/** Formats milliseconds as {@code 1:02:03} or {@code 02:03}. */
	public static String format(long ms) {
		long totalSeconds = Math.max(0, ms) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return hours > 0
				? String.format("%d:%02d:%02d", hours, minutes, seconds)
				: String.format("%02d:%02d", minutes, seconds);
	}
}
