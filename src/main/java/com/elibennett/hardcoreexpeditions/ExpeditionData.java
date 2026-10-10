package com.elibennett.hardcoreexpeditions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the mod remembers between server restarts. Saved as readable JSON in the world folder
 * ({@code world/hardcore_expeditions.json}) so it's easy to inspect or hand-edit.
 */
public final class ExpeditionData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Phase {
		/** The run is live and the timer is counting. */
		RUNNING,
		/** Someone died; everyone is waiting for the reset countdown. */
		COUNTDOWN,
		/** The dragon was killed; the timer is stopped at the final time. */
		COMPLETED
	}

	/** Current expedition number (1, 2, 3...). 0 means no expedition has been started yet. */
	public int expeditionNumber = 0;
	public long seed;
	/**
	 * Numbers the world folders (dimensions/hardcore_expeditions/expedition_<worldId>_...). Separate
	 * from expeditionNumber and never goes down, so restarting the numbering at #1 can't collide with
	 * an existing folder.
	 */
	public int worldId;
	/** Set by /expedition reset: the next expedition is #1 again. */
	public boolean restartNumbering;
	public Phase phase = Phase.RUNNING;
	/** How long the current expedition has been running, counted only while at least one player is online. */
	public long elapsedMs;

	public int totalDeaths;
	/** Keyed by player UUID so stats survive name changes. */
	public Map<String, PlayerStats> players = new LinkedHashMap<>();
	public Long bestTimeMs;
	public List<RunRecord> history = new ArrayList<>();

	/** Players who have already been given a fresh start in the current expedition. */
	public Set<String> joinedCurrent = new HashSet<>();

	/** Time of day in the expedition worlds, so a restart doesn't snap the sun back to morning. */
	public long overworldClockTicks;
	public long endClockTicks;

	public static final class PlayerStats {
		public String name;
		public int deaths;
	}

	public static final class RunRecord {
		public int expedition;
		public long seed;
		public long durationMs;
		public boolean completed;
		/** Who died (or who started a new expedition manually). Null when the dragon was killed. */
		public String endedBy;
		public String reason;
		public String endedAt;
	}

	public static ExpeditionData load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				ExpeditionData data = GSON.fromJson(reader, ExpeditionData.class);
				if (data != null) {
					return data;
				}
			} catch (IOException | RuntimeException e) {
				HardcoreExpeditions.LOGGER.error("Could not read {}, starting with fresh stats", file, e);
			}
		}
		return new ExpeditionData();
	}

	public void save(Path file) {
		// Write to a temp file then move, so a crash mid-write can't corrupt the stats.
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			HardcoreExpeditions.LOGGER.error("Could not save {}", file, e);
			return;
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			HardcoreExpeditions.LOGGER.error("Could not save {}", file, e);
		}
	}
}
