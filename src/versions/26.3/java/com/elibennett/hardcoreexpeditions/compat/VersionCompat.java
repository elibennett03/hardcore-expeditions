package com.elibennett.hardcoreexpeditions.compat;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.ChunkPos;

/** The few calls that differ between Minecraft versions. This copy is compiled for 26.3. */
public final class VersionCompat {
	private VersionCompat() {
	}

	public static long clockTicks(ServerLevel level, Holder<WorldClock> clock) {
		return level.clockManager().getInstance(clock).totalTicks();
	}

	/** The chunk vanilla would pick as the spawn area for a brand-new world. */
	public static ChunkPos spawnChunk(ServerChunkCache chunkSource) {
		return chunkSource.getGenerator().getOrigin(chunkSource.randomState());
	}
}
