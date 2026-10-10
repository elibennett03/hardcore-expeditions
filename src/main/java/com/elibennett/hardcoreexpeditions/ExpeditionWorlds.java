package com.elibennett.hardcoreexpeditions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;

/**
 * The three worlds (Overworld, Nether, End) that make up one expedition. They're created while the
 * server is running with a brand-new seed, and deleted from disk when the expedition ends.
 */
public final class ExpeditionWorlds {
	public final int number;
	public final long seed;
	private final RuntimeLevelHandle overworld;
	private final RuntimeLevelHandle nether;
	private final RuntimeLevelHandle end;

	private ExpeditionWorlds(int number, long seed, RuntimeLevelHandle overworld, RuntimeLevelHandle nether, RuntimeLevelHandle end) {
		this.number = number;
		this.seed = seed;
		this.overworld = overworld;
		this.nether = nether;
		this.end = end;
	}

	/** Opens the worlds for an expedition, creating them if they don't exist yet. */
	public static ExpeditionWorlds open(MinecraftServer server, int number, long seed) {
		Fantasy fantasy = Fantasy.get(server);
		return new ExpeditionWorlds(
				number,
				seed,
				fantasy.getOrOpenPersistentLevel(id(number, "overworld"), config(server, seed, BuiltinDimensionTypes.OVERWORLD, LevelStem.OVERWORLD, true)),
				fantasy.getOrOpenPersistentLevel(id(number, "nether"), config(server, seed, BuiltinDimensionTypes.NETHER, LevelStem.NETHER, false)),
				fantasy.getOrOpenPersistentLevel(id(number, "end"), config(server, seed, BuiltinDimensionTypes.END, LevelStem.END, false))
		);
	}

	public static Identifier id(int number, String dimension) {
		return Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, "expedition_" + number + "_" + dimension);
	}

	private static RuntimeLevelConfig config(MinecraftServer server, long seed, ResourceKey<DimensionType> type, ResourceKey<LevelStem> stem, boolean overworld) {
		// Reuse the server's own generator settings (normal/amplified/etc.), just with our seed.
		ChunkGenerator generator = server.registries().compositeAccess()
				.lookupOrThrow(Registries.LEVEL_STEM)
				.getValueOrThrow(stem)
				.generator();
		return new RuntimeLevelConfig()
				.setSeed(seed)
				.setDimensionType(type)
				.setGenerator(generator)
				.setShouldTickTime(true)
				.setMirrorOverworldGameRules(true)
				.setMirrorOverworldDifficulty(true)
				.setLevelConstructor((s, key, cfg, style) -> new ExpeditionLevel(s, key, cfg, style, overworld));
	}

	public ServerLevel overworld() {
		return this.overworld.asLevel();
	}

	public ServerLevel nether() {
		return this.nether.asLevel();
	}

	public ServerLevel end() {
		return this.end.asLevel();
	}

	public boolean contains(Level level) {
		return level == overworld() || level == nether() || level == end();
	}

	/** The vanilla dimension this world stands in for, so advancements like "We Need to Go Deeper" still work. */
	public ResourceKey<Level> vanillaDimension(Level level) {
		if (level == nether()) {
			return Level.NETHER;
		}
		if (level == end()) {
			return Level.END;
		}
		return Level.OVERWORLD;
	}

	public boolean isEnd(Level level) {
		return level == end();
	}

	/** Where a Nether portal in {@code level} should lead, or null if portals shouldn't work there. */
	public ServerLevel netherPortalTarget(Level level) {
		if (level == overworld()) {
			return nether();
		}
		if (level == nether()) {
			return overworld();
		}
		return null;
	}

	public void delete() {
		this.overworld.delete();
		this.nether.delete();
		this.end.delete();
	}

	public void saveClocks(ExpeditionData data) {
		MinecraftServer server = overworld().getServer();
		data.overworldClockTicks = overworld().clockManager().getTotalTicks(server.registryAccess().getOrThrow(WorldClocks.OVERWORLD));
		data.endClockTicks = end().clockManager().getTotalTicks(server.registryAccess().getOrThrow(WorldClocks.THE_END));
	}

	public void restoreClocks(ExpeditionData data) {
		MinecraftServer server = overworld().getServer();
		setClock(overworld(), server.registryAccess().getOrThrow(WorldClocks.OVERWORLD), data.overworldClockTicks);
		setClock(end(), server.registryAccess().getOrThrow(WorldClocks.THE_END), data.endClockTicks);
	}

	private static void setClock(ServerLevel level, net.minecraft.core.Holder<WorldClock> clock, long ticks) {
		level.clockManager().setTotalTicks(clock, ticks);
	}

	/**
	 * Finds a safe surface spot to start on, the same way vanilla picks the spawn for a new world.
	 * This generates a few chunks, so it can briefly pause the server.
	 */
	public BlockPos findSpawn() {
		ServerLevel level = overworld();
		ServerChunkCache chunkSource = level.getChunkSource();
		ChunkPos spawnChunk = ChunkPos.containing(chunkSource.randomState().sampler().findSpawnPosition());
		int height = chunkSource.getGenerator().getSpawnHeight(level);
		if (height < level.getMinY()) {
			BlockPos corner = spawnChunk.getWorldPosition();
			height = level.getHeight(Heightmap.Types.WORLD_SURFACE, corner.getX() + 8, corner.getZ() + 8);
		}
		BlockPos fallback = spawnChunk.getWorldPosition().offset(8, height, 8);

		// Spiral outwards up to 5 chunks away looking for a valid surface block.
		int x = 0;
		int z = 0;
		int dx = 0;
		int dz = -1;
		for (int i = 0; i < 11 * 11; i++) {
			if (x >= -5 && x <= 5 && z >= -5 && z <= 5) {
				BlockPos found = PlayerSpawnFinder.getSpawnPosInChunk(level, new ChunkPos(spawnChunk.x() + x, spawnChunk.z() + z));
				if (found != null) {
					return found;
				}
			}
			if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
				int turn = dx;
				dx = -dz;
				dz = turn;
			}
			x += dx;
			z += dz;
		}
		return fallback;
	}
}
