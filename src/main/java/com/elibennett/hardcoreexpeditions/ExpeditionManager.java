package com.elibennett.hardcoreexpeditions;

import com.elibennett.hardcoreexpeditions.ExpeditionData.Phase;
import com.elibennett.hardcoreexpeditions.ExpeditionData.PlayerStats;
import com.elibennett.hardcoreexpeditions.ExpeditionData.RunRecord;
import com.elibennett.hardcoreexpeditions.mixin.MinecraftServerAccessor;
import com.elibennett.hardcoreexpeditions.network.ExpeditionSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Runs the expedition: the timer, death tallies, the reset countdown, and swapping everyone into a
 * fresh set of worlds. One instance exists per running server (including singleplayer worlds).
 */
public final class ExpeditionManager {
	public static final int COUNTDOWN_SECONDS = 5;
	private static final String DEATHS_OBJECTIVE = "he_deaths";

	private static ExpeditionManager instance;

	private final MinecraftServer server;
	private final Path dataFile;
	private final ExpeditionData data;
	/** Shown to players who don't have the mod installed, in place of the custom HUD. */
	private final ServerBossEvent bossBar;

	private ExpeditionWorlds worlds;
	private BlockPos spawn;

	// Countdown state. Not saved: a restart mid-countdown just starts the next expedition right away.
	private ExpeditionWorlds nextWorlds;
	private BlockPos nextSpawn;
	private long countdownStartNanos;
	private int countdownShown;
	private String deadPlayer = "";
	private Component deathMessage = Component.empty();

	private long lastTickNanos;
	private long elapsedNanosRemainder;
	private int tickCounter;

	private ExpeditionManager(MinecraftServer server) {
		this.server = server;
		this.dataFile = server.getWorldPath(LevelResource.ROOT).resolve(HardcoreExpeditions.MOD_ID + ".json");
		this.data = ExpeditionData.load(this.dataFile);
		this.bossBar = new ServerBossEvent(
				UUID.nameUUIDFromBytes((HardcoreExpeditions.MOD_ID + ":timer").getBytes()),
				Component.literal("Expedition"),
				BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.PROGRESS
		);
		this.bossBar.setProgress(1.0F);
	}

	public static ExpeditionManager get() {
		return instance;
	}

	static void onServerStarted(MinecraftServer server) {
		instance = new ExpeditionManager(server);
		instance.start();
	}

	static void onServerStopping(MinecraftServer server) {
		if (instance != null) {
			instance.save();
			instance.bossBar.removeAllPlayers();
		}
	}

	static void onServerStopped(MinecraftServer server) {
		instance = null;
	}

	private void start() {
		boolean resume = this.data.expeditionNumber > 0 && this.data.phase != Phase.COUNTDOWN;
		deleteLeftoverWorlds(resume ? this.data.expeditionNumber : -1);

		if (resume) {
			this.worlds = ExpeditionWorlds.open(this.server, this.data.expeditionNumber, this.data.seed);
			this.worlds.restoreClocks(this.data);
			LevelData.RespawnData saved = this.server.getWorldData().overworldData().getRespawnData();
			this.spawn = saved.dimension().equals(this.worlds.overworld().dimension()) ? saved.pos() : this.worlds.findSpawn();
			this.server.setRespawnData(LevelData.RespawnData.of(this.worlds.overworld().dimension(), this.spawn, 0.0F, 0.0F));
			// The spawn was loaded before our worlds existed, so vanilla fell back to the lobby; recompute it.
			((MinecraftServerAccessor) this.server).hardcoreExpeditions$updateEffectiveRespawnData();
		} else {
			// First launch, or the server stopped during a countdown: go straight to a new expedition.
			ExpeditionWorlds fresh = ExpeditionWorlds.open(this.server, this.data.expeditionNumber + 1, RandomSupport.generateUniqueSeed());
			beginExpedition(fresh, fresh.findSpawn());
		}

		setUpDeathsObjective();
		this.lastTickNanos = System.nanoTime();
	}

	void tick() {
		long now = System.nanoTime();
		long delta = now - this.lastTickNanos;
		this.lastTickNanos = now;

		// The timer only runs while at least one player is online.
		if (this.data.phase == Phase.RUNNING && !this.server.getPlayerList().getPlayers().isEmpty()) {
			this.elapsedNanosRemainder += delta;
			this.data.elapsedMs += this.elapsedNanosRemainder / 1_000_000;
			this.elapsedNanosRemainder %= 1_000_000;
		}

		if (this.data.phase == Phase.COUNTDOWN) {
			tickCountdown();
		}

		if (++this.tickCounter % 20 == 0) {
			if (this.data.phase != Phase.COUNTDOWN) {
				bringStraysIntoExpedition();
			}
			syncAll();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Deaths and the reset countdown

	/**
	 * Called instead of vanilla's death handling. Returns true if the death was handled here (the
	 * player is put into spectator instead of dying).
	 */
	public boolean handleDeath(ServerPlayer player, DamageSource source) {
		if (this.worlds == null || !this.worlds.contains(player.level())) {
			return false;
		}

		Component message = player.getCombatTracker().getDeathMessage();
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.removeAllEffects();
		player.setGameMode(GameType.SPECTATOR);
		this.server.getPlayerList().broadcastSystemMessage(message, false);

		if (this.data.phase == Phase.COMPLETED) {
			// Victory lap after the dragon: deaths don't count anymore.
			return true;
		}

		recordDeath(player);
		if (this.data.phase == Phase.RUNNING) {
			endRun(player.getScoreboardName(), message.getString(), false);
			startCountdown(player.getScoreboardName(), message);
		}
		save();
		syncAll();
		return true;
	}

	/** Starts a new expedition on request (the {@code /expedition new} command). */
	public boolean requestNewExpedition(String requestedBy) {
		if (this.data.phase == Phase.COUNTDOWN) {
			return false;
		}
		if (this.data.phase == Phase.RUNNING) {
			endRun(requestedBy, "Started a new expedition", false);
		}
		startCountdown("", Component.literal(requestedBy + " started a new expedition"));
		save();
		syncAll();
		return true;
	}

	private void recordDeath(ServerPlayer player) {
		this.data.totalDeaths++;
		PlayerStats stats = this.data.players.computeIfAbsent(player.getUUID().toString(), uuid -> new PlayerStats());
		stats.name = player.getScoreboardName();
		stats.deaths++;
		setDeathsScore(stats);
	}

	private void endRun(String endedBy, String reason, boolean completed) {
		RunRecord run = new RunRecord();
		run.expedition = this.data.expeditionNumber;
		run.seed = this.data.seed;
		run.durationMs = this.data.elapsedMs;
		run.completed = completed;
		run.endedBy = endedBy;
		run.reason = reason;
		run.endedAt = Instant.now().toString();
		this.data.history.add(run);
	}

	private void startCountdown(String deadPlayer, Component message) {
		this.data.phase = Phase.COUNTDOWN;
		this.deadPlayer = deadPlayer;
		this.deathMessage = message;
		this.nextWorlds = null;
		showCountdown(COUNTDOWN_SECONDS);
		// The new worlds are created at the end of this server tick (see tickCountdown), never in the
		// middle of a world tick, which is where deaths happen.
	}

	private void tickCountdown() {
		if (this.nextWorlds == null) {
			// Generating the new world pauses the server for a moment, so the countdown only starts
			// once it's ready. Everyone keeps seeing "5" while it's being made.
			this.nextWorlds = ExpeditionWorlds.open(this.server, this.data.expeditionNumber + 1, RandomSupport.generateUniqueSeed());
			this.nextSpawn = this.nextWorlds.findSpawn();
			this.countdownStartNanos = System.nanoTime();
			return;
		}

		long elapsedMs = (System.nanoTime() - this.countdownStartNanos) / 1_000_000;
		int remaining = COUNTDOWN_SECONDS - (int) (elapsedMs / 1000);
		if (remaining <= 0) {
			beginExpedition(this.nextWorlds, this.nextSpawn);
		} else if (remaining != this.countdownShown) {
			showCountdown(remaining);
		}
	}

	private void showCountdown(int remaining) {
		this.countdownShown = remaining;
		Component title = this.deadPlayer.isEmpty()
				? Component.literal("New expedition!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				: Component.literal(this.deadPlayer + " has died").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		Component subtitle = Component.literal("New world in " + remaining + "...").withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			if (!hasMod(player)) {
				sendTitle(player, title, subtitle, 30);
			}
		}
		syncAll();
	}

	private void beginExpedition(ExpeditionWorlds newWorlds, BlockPos newSpawn) {
		ExpeditionWorlds old = this.worlds;
		this.worlds = newWorlds;
		this.spawn = newSpawn;
		this.nextWorlds = null;
		this.nextSpawn = null;
		this.deadPlayer = "";
		this.deathMessage = Component.empty();

		this.data.expeditionNumber = newWorlds.number;
		this.data.seed = newWorlds.seed;
		this.data.phase = Phase.RUNNING;
		this.data.elapsedMs = 0;
		this.elapsedNanosRemainder = 0;
		this.data.joinedCurrent.clear();
		this.data.overworldClockTicks = 0;
		this.data.endClockTicks = 0;

		// Making the new overworld the server's spawn means new players, compasses and the End exit
		// portal all point into the expedition instead of the vanilla world.
		this.server.setRespawnData(LevelData.RespawnData.of(this.worlds.overworld().dimension(), this.spawn, 0.0F, 0.0F));

		for (ServerPlayer player : List.copyOf(this.server.getPlayerList().getPlayers())) {
			giveFreshStart(player);
			player.connection.send(new ClientboundClearTitlesPacket(true));
		}

		if (old != null) {
			// Everyone has already left; this removes the old worlds from disk too.
			old.delete();
		}

		save();
		this.server.getPlayerList().broadcastSystemMessage(
				Component.literal("Expedition #" + this.data.expeditionNumber + " has begun. Good luck!").withStyle(ChatFormatting.GOLD), false);
		syncAll();
	}

	// ---------------------------------------------------------------------------------------------
	// Players

	void onJoin(ServerPlayer player) {
		if (this.data.phase != Phase.COUNTDOWN && !this.data.joinedCurrent.contains(player.getUUID().toString())) {
			giveFreshStart(player);
		}
		syncAll();
	}

	void onLeave(ServerPlayer player) {
		this.bossBar.removePlayer(player);
	}

	/**
	 * Anyone outside the current expedition (new players, people who were offline during a reset, or
	 * someone who left through the End exit portal) gets brought in.
	 */
	private void bringStraysIntoExpedition() {
		for (ServerPlayer player : List.copyOf(this.server.getPlayerList().getPlayers())) {
			if (this.worlds.contains(player.level())) {
				continue;
			}
			if (this.data.joinedCurrent.contains(player.getUUID().toString())) {
				teleportToSpawn(player);
			} else {
				giveFreshStart(player);
			}
		}
	}

	private void teleportToSpawn(ServerPlayer player) {
		player.teleport(new TeleportTransition(this.worlds.overworld(), Vec3.atBottomCenterOf(this.spawn), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
	}

	/** Sends the player to the expedition spawn with an empty inventory, full health and no progress. */
	private void giveFreshStart(ServerPlayer player) {
		player.closeContainer();
		teleportToSpawn(player);
		player.setGameMode(GameType.SURVIVAL);

		player.getInventory().clearContent();
		player.getEnderChestInventory().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);

		player.removeAllEffects();
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.setExperienceLevels(0);
		player.setExperiencePoints(0);
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.resetFallDistance();
		player.setRespawnPosition(null, false);

		PlayerAdvancements advancements = player.getAdvancements();
		for (AdvancementHolder advancement : this.server.getAdvancements().getAllAdvancements()) {
			AdvancementProgress progress = advancements.getOrStartProgress(advancement);
			List<String> completed = new ArrayList<>();
			progress.getCompletedCriteria().forEach(completed::add);
			for (String criterion : completed) {
				advancements.revoke(advancement, criterion);
			}
		}
		player.resetRecipes(this.server.getRecipeManager().getRecipes());

		this.data.joinedCurrent.add(player.getUUID().toString());
	}

	// ---------------------------------------------------------------------------------------------
	// Ender Dragon = expedition complete

	public void onDragonKilled(Level level) {
		if (this.worlds == null || !this.worlds.isEnd(level) || this.data.phase != Phase.RUNNING) {
			return;
		}

		this.data.phase = Phase.COMPLETED;
		endRun(null, "Ender Dragon defeated", true);
		boolean personalBest = this.data.bestTimeMs == null || this.data.elapsedMs < this.data.bestTimeMs;
		if (personalBest) {
			this.data.bestTimeMs = this.data.elapsedMs;
		}

		String time = ExpeditionTime.format(this.data.elapsedMs);
		this.server.getPlayerList().broadcastSystemMessage(
				Component.literal("Expedition #" + this.data.expeditionNumber + " complete in " + time + "!" + (personalBest ? " New best time!" : ""))
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			sendTitle(player,
					Component.literal("Expedition complete!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.literal(time + (personalBest ? "  (new best!)" : "")).withStyle(ChatFormatting.YELLOW),
					100);
		}
		save();
		syncAll();
	}

	// ---------------------------------------------------------------------------------------------
	// UI: custom HUD for modded players, boss bar + titles for everyone else

	private static boolean hasMod(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, ExpeditionSyncPayload.TYPE);
	}

	private static void sendTitle(ServerPlayer player, Component title, Component subtitle, int stayTicks) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, stayTicks, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	private ExpeditionSyncPayload snapshot() {
		return new ExpeditionSyncPayload(
				this.data.expeditionNumber,
				this.data.phase.ordinal(),
				this.data.elapsedMs,
				this.data.phase == Phase.RUNNING,
				this.data.totalDeaths,
				this.data.bestTimeMs == null ? -1 : this.data.bestTimeMs,
				this.countdownShown,
				this.deadPlayer,
				this.deathMessage
		);
	}

	private void syncAll() {
		ExpeditionSyncPayload payload = snapshot();
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			boolean modded = hasMod(player);
			if (modded) {
				ServerPlayNetworking.send(player, payload);
			}
			boolean showingBar = this.bossBar.getPlayers().contains(player);
			if (modded && showingBar) {
				this.bossBar.removePlayer(player);
			} else if (!modded && !showingBar) {
				this.bossBar.addPlayer(player);
			}
		}
		updateBossBar();
	}

	private void updateBossBar() {
		String time = ExpeditionTime.format(this.data.elapsedMs);
		switch (this.data.phase) {
			case RUNNING -> {
				this.bossBar.setName(Component.literal("Expedition #" + this.data.expeditionNumber + "  |  " + time + "  |  Deaths: " + this.data.totalDeaths));
				this.bossBar.setColor(BossEvent.BossBarColor.YELLOW);
			}
			case COUNTDOWN -> {
				String who = this.deadPlayer.isEmpty() ? "New expedition" : this.deadPlayer + " died";
				this.bossBar.setName(Component.literal(who + "  |  New world in " + this.countdownShown));
				this.bossBar.setColor(BossEvent.BossBarColor.RED);
			}
			case COMPLETED -> {
				this.bossBar.setName(Component.literal("Expedition #" + this.data.expeditionNumber + " complete!  |  " + time));
				this.bossBar.setColor(BossEvent.BossBarColor.GREEN);
			}
		}
	}

	/** Shows each player's all-time death count next to their name in the Tab player list. */
	private void setUpDeathsObjective() {
		ServerScoreboard scoreboard = this.server.getScoreboard();
		Objective objective = scoreboard.getObjective(DEATHS_OBJECTIVE);
		if (objective == null) {
			objective = scoreboard.addObjective(DEATHS_OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal("Deaths"), ObjectiveCriteria.RenderType.INTEGER, false, null);
		}
		scoreboard.setDisplayObjective(DisplaySlot.LIST, objective);
		for (PlayerStats stats : this.data.players.values()) {
			setDeathsScore(stats);
		}
	}

	private void setDeathsScore(PlayerStats stats) {
		ServerScoreboard scoreboard = this.server.getScoreboard();
		Objective objective = scoreboard.getObjective(DEATHS_OBJECTIVE);
		if (objective != null && stats.name != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(stats.name), objective).set(stats.deaths);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Persistence and queries

	void save() {
		if (this.worlds != null && this.data.phase != Phase.COUNTDOWN) {
			this.worlds.saveClocks(this.data);
		}
		this.data.save(this.dataFile);
	}

	/** Deletes expedition world folders left behind by a crash or a restart during a countdown. */
	private void deleteLeftoverWorlds(int keepExpedition) {
		Path dir = this.server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(HardcoreExpeditions.MOD_ID);
		if (!Files.isDirectory(dir)) {
			return;
		}
		String keepPrefix = "expedition_" + keepExpedition + "_";
		try (Stream<Path> children = Files.list(dir)) {
			for (Path child : children.toList()) {
				if (!child.getFileName().toString().startsWith(keepPrefix)) {
					FileUtils.deleteDirectory(child.toFile());
				}
			}
		} catch (IOException e) {
			HardcoreExpeditions.LOGGER.warn("Could not clean up old expedition worlds in {}", dir, e);
		}
	}

	public ExpeditionData data() {
		return this.data;
	}

	public BlockPos spawn() {
		return this.spawn;
	}

	/** The expedition (current, or the one being prepared during a countdown) that owns this world. */
	public ExpeditionWorlds worldsContaining(Level level) {
		if (this.worlds != null && this.worlds.contains(level)) {
			return this.worlds;
		}
		if (this.nextWorlds != null && this.nextWorlds.contains(level)) {
			return this.nextWorlds;
		}
		return null;
	}
}
