package com.elibennett.hardcoreexpeditions;

public final class ExpeditionTime {
	private ExpeditionTime() {
	}

	/** Formats milliseconds as {@code 1:02:03} or {@code 02:03}. */
	public static String format(long ms) {
		long totalSeconds = Math.max(0, ms) / 1000;
		long hours = totalSeconds / 3600;
		StringBuilder out = new StringBuilder(8);
		if (hours > 0) {
			out.append(hours).append(':');
		}
		appendTwoDigits(out, (totalSeconds % 3600) / 60);
		out.append(':');
		appendTwoDigits(out, totalSeconds % 60);
		return out.toString();
	}

	private static void appendTwoDigits(StringBuilder out, long value) {
		if (value < 10) {
			out.append('0');
		}
		out.append(value);
	}
}
