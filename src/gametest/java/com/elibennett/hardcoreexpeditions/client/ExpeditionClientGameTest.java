package com.elibennett.hardcoreexpeditions.client;

import com.elibennett.hardcoreexpeditions.ExpeditionData;
import com.elibennett.hardcoreexpeditions.ExpeditionData.Phase;
import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import com.elibennett.hardcoreexpeditions.ExpeditionWorlds;
import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end test on a real dedicated server with a real client: joining, portals, advancements,
 * a death and the full reset, the dragon, and the HUD. Run with {@code ./gradlew runClientGameTest}.
 * Screenshots land in the run folder's {@code screenshots/}.
 */
public class ExpeditionClientGameTest implements FabricClientGameTest {
	private static final int TIMEOUT = 20 * 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();

			// --- Joining puts you in Expedition #1 and the HUD gets data from the server ---------------
			check(server.computeOnServer(s -> data().expeditionNumber) == 1, "server starts on Expedition #1");
			check(server.computeOnServer(s -> dimension(player(s))).equals("hardcore_expeditions:expedition_1_overworld"), "player spawns in the expedition overworld");
			context.waitFor(mc -> ClientExpeditionState.get() != null, TIMEOUT);
			check(true, "client received expedition data (mod detected over the network)");
			context.waitTicks(40);
			context.takeScreenshot("01-hud");

			// --- Lighting an obsidian frame opens a Nether portal in the expedition's worlds -----------
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ExpeditionWorlds w = worlds(p);
				BlockPos origin = p.blockPosition().offset(6, 30, 0);
				check(lightPortalFrame(w.overworld(), origin), "lighting an obsidian frame in the expedition overworld creates a nether portal");
				check(lightPortalFrame(w.nether(), new BlockPos(origin.getX() / 8, 64, origin.getZ() / 8)), "lighting an obsidian frame in the expedition nether creates a nether portal");
			});

			// --- The allow_entering_nether_using_portals game rule applies to the expedition Nether ---
			server.runCommand("gamerule allow_entering_nether_using_portals false");
			check(server.computeOnServer(s -> !worlds(player(s)).overworld().isAllowedToEnterPortal(worlds(player(s)).nether())), "turning off allow_entering_nether_using_portals blocks the expedition nether");
			server.runCommand("gamerule allow_entering_nether_using_portals true");
			check(server.computeOnServer(s -> worlds(player(s)).overworld().isAllowedToEnterPortal(worlds(player(s)).nether())), "allow_entering_nether_using_portals on lets you into the expedition nether");

			// --- Portals lead to this expedition's Nether and End, and their advancements trigger -----
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ExpeditionWorlds w = worlds(p);
				TeleportTransition toNether = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(w.overworld(), p, p.blockPosition());
				check(toNether != null && toNether.newLevel() == w.nether(), "nether portal in expedition overworld leads to expedition nether");
				p.teleport(toNether);
			});
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ExpeditionWorlds w = worlds(p);
				check(dimension(p).equals("hardcore_expeditions:expedition_1_nether"), "player is now in the expedition nether");
				check(hasAdvancement(s, p, "minecraft:story/enter_the_nether"), "earned 'We Need to Go Deeper'");
				check(hasAdvancement(s, p, "minecraft:nether/root"), "Nether advancement tab unlocked");
				TeleportTransition back = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(w.nether(), p, p.blockPosition());
				check(back != null && back.newLevel() == w.overworld(), "nether portal in expedition nether leads back to expedition overworld");

				TeleportTransition toEnd = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(w.overworld(), p, p.blockPosition());
				check(toEnd != null && toEnd.newLevel() == w.end(), "end portal leads to expedition end");
				p.teleport(toEnd);
			});
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ExpeditionWorlds w = worlds(p);
				check(hasAdvancement(s, p, "minecraft:story/enter_the_end"), "earned 'The End?'");
				check(hasAdvancement(s, p, "minecraft:end/root"), "End advancement tab unlocked");
				TeleportTransition exit = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(w.end(), p, p.blockPosition());
				check(exit != null && exit.newLevel() == w.overworld(), "end exit portal leads back to expedition overworld");
				p.teleport(exit);
			});
			context.waitTicks(20);

			// --- Earn a pile of progress, then die ------------------------------------------------------
			server.runCommand("advancement grant @a everything");
			server.runCommand("give @a minecraft:diamond 32");
			server.runCommand("xp add @a 30 levels");
			server.runCommand("item replace entity @a enderchest.0 with minecraft:netherite_ingot");
			context.waitTicks(10);
			int earned = server.computeOnServer(s -> completedAdvancements(s, player(s)).size());
			check(earned > 100, "player has lots of advancements before dying (" + earned + ")");
			check(server.computeOnServer(s -> player(s).experienceLevel >= 30), "player has 30+ XP levels before dying");
			// Five nights without sleep, well past the three phantoms need.
			server.runOnServer(s -> player(s).awardStat(Stats.TIME_SINCE_REST, 5 * 24000));
			check(server.computeOnServer(s -> sinceRest(player(s)) >= 5 * 24000), "player has gone 5 days without sleep before dying");

			context.getInput().holdKey(InputConstants.KEY_TAB);
			context.waitTicks(5);
			context.takeScreenshot("02-stats-panel");
			context.getInput().releaseKey(InputConstants.KEY_TAB);

			server.runCommand("scoreboard objectives add test_deaths deathCount");
			server.runCommand("kill @a");
			context.waitTicks(15);
			check(server.computeOnServer(s -> data().phase == Phase.COUNTDOWN), "death starts the countdown");
			check(server.computeOnServer(s -> player(s).getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)) == 1), "death shows in the vanilla Statistics screen");
			check(server.computeOnServer(s -> s.getScoreboard().getPlayerScoreInfo(player(s), s.getScoreboard().getObjective("test_deaths")).value() == 1), "death counts on deathCount scoreboards");
			server.runCommand("scoreboard objectives remove test_deaths");
			check(server.computeOnServer(s -> player(s).gameMode() == GameType.SPECTATOR), "dead player is a spectator during the countdown");
			check(server.computeOnServer(s -> data().totalDeaths == 1), "death was tallied");
			check(context.computeOnClient(mc -> ClientExpeditionState.get().phase() == Phase.COUNTDOWN.ordinal()), "client shows the death overlay");
			context.takeScreenshot("03-death-overlay");

			// --- The reset ---------------------------------------------------------------------------
			server.waitFor(s -> data().expeditionNumber == 2 && data().phase == Phase.RUNNING, TIMEOUT);
			context.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(dimension(p).equals("hardcore_expeditions:expedition_2_overworld"), "player moved to Expedition #2");
				check(data().seed != 0, "new expedition has a seed");
				check(p.gameMode() == GameType.SURVIVAL, "player is back in survival");
				check(p.getInventory().isEmpty(), "inventory wiped");
				check(p.getEnderChestInventory().isEmpty(), "ender chest wiped");
				check(p.experienceLevel == 0, "XP wiped");
				check(sinceRest(p) < 24000, "time since rest reset, so phantoms don't come the first night (" + sinceRest(p) + " ticks)");
				check(p.getHealth() == p.getMaxHealth(), "full health");
				// Vanilla re-grants a few advancements on the very first tick (e.g. the crafting table
				// recipe), exactly like for a brand-new player, so those don't count as left over.
				List<String> left = completedAdvancements(s, p).stream().filter(id -> !grantedAutomatically(s, id)).toList();
				check(left.isEmpty(), "all advancements wiped" + (left.isEmpty() ? "" : " (still have: " + left + ")"));
				check(s.getLevel(levelKey("expedition_1_overworld")) == null, "Expedition #1 worlds unloaded");
				check(data().history.size() == 1 && data().history.getFirst().durationMs > 0, "Expedition #1 recorded with its time");
			});
			context.waitTicks(40);
			check(server.computeOnServer(s -> !Files.exists(expeditionFolder(s, "expedition_1_overworld"))), "Expedition #1 world folder deleted from disk");
			check(context.computeOnClient(mc -> ClientExpeditionState.get().expedition() == 2 && ClientExpeditionState.get().longestRunMs() > 0), "client HUD shows Expedition #2 and a longest run");
			context.getInput().holdKey(InputConstants.KEY_TAB);
			context.waitTicks(5);
			context.takeScreenshot("04-after-reset-stats");
			context.getInput().releaseKey(InputConstants.KEY_TAB);

			// --- The dragon completes the run --------------------------------------------------------
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleport(((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(worlds(p).overworld(), p, p.blockPosition()));
			});
			int waited = server.waitFor(s -> !worlds(player(s)).end().getDragons().isEmpty(), TIMEOUT);
			log("dragon spawned after " + waited + " ticks");
			server.runCommand("kill @e[type=minecraft:ender_dragon]");
			server.waitFor(s -> data().phase == Phase.COMPLETED, TIMEOUT);
			check(server.computeOnServer(s -> data().bestTimeMs != null), "dragon kill completes the expedition and records the time");
			context.waitTicks(20);
			context.takeScreenshot("05-completed");

			// --- Walking into the exit portal rolls the credits, then returns you to the overworld ----
			server.runOnServer(s -> player(s).level().setBlockAndUpdate(player(s).blockPosition(), Blocks.END_PORTAL.defaultBlockState()));
			context.waitFor(mc -> mc.gui.screen() instanceof WinScreen, TIMEOUT);
			check(server.computeOnServer(s -> player(s).seenCredits), "exit portal in the expedition end rolls the credits");
			context.takeScreenshot("06-credits");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			server.waitFor(s -> dimension(player(s)).equals("hardcore_expeditions:expedition_2_overworld"), TIMEOUT);
			check(server.computeOnServer(s -> player(s).gameMode() == GameType.SURVIVAL), "after the credits you're back in the expedition overworld in survival");

			// --- Dying after the win still resets everyone --------------------------------------------
			// Vanilla makes a player invulnerable until their client has loaded in after the credits.
			server.waitFor(s -> player(s).connection.hasClientLoaded(), TIMEOUT);
			int deathsBefore = server.computeOnServer(s -> data().totalDeaths);
			int runsBefore = server.computeOnServer(s -> data().history.size());
			server.runCommand("kill @a");
			context.waitTicks(15);
			check(server.computeOnServer(s -> data().phase == Phase.COUNTDOWN), "dying after the dragon starts the countdown");
			check(server.computeOnServer(s -> data().totalDeaths == deathsBefore + 1), "a death after the dragon is counted");
			check(server.computeOnServer(s -> data().history.size() == runsBefore && data().history.getLast().completed), "the won run stays recorded as a win");
			server.waitFor(s -> data().expeditionNumber == 3 && data().phase == Phase.RUNNING, TIMEOUT);
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(dimension(p).equals("hardcore_expeditions:expedition_3_overworld"), "a death after the win starts Expedition #3");
				check(p.gameMode() == GameType.SURVIVAL, "player is back in survival after the post-win death");
				check(!p.seenCredits, "a new expedition rolls the credits again next time");
			});

			// --- Nobody can die during the countdown, and a death then doesn't count ------------------
			server.waitFor(s -> player(s).connection.hasClientLoaded(), TIMEOUT);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.hurtServer(p.level(), p.damageSources().generic(), 4.0F);
				check(p.getHealth() < p.getMaxHealth(), "players take damage normally during a run");
				p.setHealth(p.getMaxHealth());
			});
			server.runCommand("expedition new");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(data().phase == Phase.COUNTDOWN, "/expedition new starts the countdown");
				int deaths = data().totalDeaths;
				p.hurtServer(p.level(), p.damageSources().generic(), 4.0F);
				p.kill(p.level());
				check(p.getHealth() == p.getMaxHealth() && p.gameMode() == GameType.SURVIVAL, "players are invulnerable during the countdown (even to /kill)");
				p.die(p.damageSources().generic());
				check(data().totalDeaths == deaths && p.gameMode() == GameType.SPECTATOR, "a death during the countdown isn't counted");
			});
			server.waitFor(s -> data().expeditionNumber == 4 && data().phase == Phase.RUNNING, TIMEOUT);
			check(server.computeOnServer(s -> dimension(player(s))).equals("hardcore_expeditions:expedition_4_overworld"), "/expedition new starts Expedition #4");

			// --- /expedition reset wipes all stats and starts over at #1, in fresh world folders -----
			check(server.computeOnServer(s -> s.getScoreboard().getObjective("he_deaths") == null), "no death numbers in the vanilla Tab list");
			server.runCommand("expedition reset confirm");
			server.waitFor(s -> data().expeditionNumber == 1 && data().phase == Phase.RUNNING, TIMEOUT);
			context.waitTicks(20);
			server.runOnServer(s -> {
				check(data().totalDeaths == 0 && data().players.isEmpty(), "reset cleared all deaths");
				check(data().history.isEmpty() && data().bestTimeMs == null, "reset cleared run history and best time");
				check(dimension(player(s)).equals("hardcore_expeditions:expedition_5_overworld"), "Expedition #1 after a reset uses new world folders");
				check(player(s).getInventory().isEmpty(), "reset gave everyone a fresh start");
			});
			check(context.computeOnClient(mc -> ClientExpeditionState.get().expedition() == 1 && ClientExpeditionState.get().totalDeaths() == 0), "HUD shows Expedition #1 with 0 deaths after reset");

			// --- HUD layout editor -------------------------------------------------------------------
			context.setScreen(() -> new HudEditScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("07-layout-editor");
			context.setScreen(() -> null);
		}
		log("ALL CHECKS PASSED");
	}

	private static ExpeditionData data() {
		return ExpeditionManager.get().data();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static ExpeditionWorlds worlds(ServerPlayer player) {
		return ExpeditionManager.get().worldsContaining(player.level());
	}

	private static String dimension(ServerPlayer player) {
		return player.level().dimension().identifier().toString();
	}

	private static ResourceKey<Level> levelKey(String path) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(HardcoreExpeditions.MOD_ID, path));
	}

	private static Path expeditionFolder(MinecraftServer server, String path) {
		return DimensionType.getStorageFolder(levelKey(path), server.getWorldPath(LevelResource.ROOT));
	}

	private static boolean hasAdvancement(MinecraftServer server, ServerPlayer player, String id) {
		AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	private static List<String> completedAdvancements(MinecraftServer server, ServerPlayer player) {
		List<String> done = new ArrayList<>();
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
				done.add(holder.id().toString());
			}
		}
		return done;
	}

	/** True for advancements vanilla gives every player automatically (they have a "minecraft:tick" criterion). */
	private static boolean grantedAutomatically(MinecraftServer server, String id) {
		AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
		return holder != null && holder.value().criteria().values().stream().anyMatch(criterion -> criterion.trigger() == CriteriaTriggers.TICK);
	}

	/** Builds a 4x5 obsidian frame at {@code origin} (open along X), lights it like flint and steel would, and reports whether it became a portal. */
	private static boolean lightPortalFrame(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 5; y++) {
				boolean edge = x == 0 || x == 3 || y == 0 || y == 4;
				level.setBlockAndUpdate(origin.offset(x, y, 0), edge ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
		BlockPos inside = origin.offset(1, 1, 0);
		level.setBlockAndUpdate(inside, BaseFireBlock.getState(level, inside));
		return level.getBlockState(inside).is(Blocks.NETHER_PORTAL) && level.getBlockState(origin.offset(2, 3, 0)).is(Blocks.NETHER_PORTAL);
	}

	private static int sinceRest(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			log("FAIL: " + what);
			throw new AssertionError(what);
		}
		log("PASS: " + what);
	}

	private static void log(String message) {
		HardcoreExpeditions.LOGGER.info("[TEST] {}", message);
	}
}
