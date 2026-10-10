package com.elibennett.hardcoreexpeditions;

import com.elibennett.hardcoreexpeditions.mixin.MinecraftServerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import xyz.nucleoid.fantasy.RuntimeLevel;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelData;

import java.util.List;

/**
 * A Fantasy runtime world that behaves like a real survival world. Fantasy's default worlds skip the
 * overworld "custom spawners", which would mean no phantoms, pillager patrols, cats, zombie sieges or
 * wandering traders. That isn't hardcore, so the expedition overworld gets them back.
 */
public class ExpeditionLevel extends RuntimeLevel {
	public ExpeditionLevel(MinecraftServer server, ResourceKey<Level> dimension, RuntimeLevelConfig config, Style style, boolean overworld) {
		super(
				server,
				Util.backgroundExecutor(),
				((MinecraftServerAccessor) server).hardcoreExpeditions$getStorageSource(),
				new RuntimeLevelData(server.getWorldData(), config),
				dimension,
				config.createDimensionOptions(server),
				false,
				BiomeManager.obfuscateSeed(config.getSeed()),
				overworld ? overworldSpawners(server) : List.of(),
				config.shouldTickTime(),
				style,
				// null = use the server's game rules, so /gamerule works in expedition worlds too.
				null,
				config.getClockManager(server, server.getGameRules())
		);
	}

	private static List<CustomSpawner> overworldSpawners(MinecraftServer server) {
		return List.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(server.getDataStorage()));
	}
}
