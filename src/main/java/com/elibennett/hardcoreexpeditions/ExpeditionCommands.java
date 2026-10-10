package com.elibennett.hardcoreexpeditions;

import com.elibennett.hardcoreexpeditions.ExpeditionData.PlayerStats;
import com.elibennett.hardcoreexpeditions.ExpeditionData.RunRecord;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /expedition}        current expedition info (anyone)
 * {@code /expedition stats}  all-time deaths per player, best time, recent runs (anyone)
 * {@code /expedition new}    abandon the current expedition and start a new one (operators)
 */
final class ExpeditionCommands {
	private ExpeditionCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("expedition")
				.executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource())))
				.then(Commands.literal("new")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> newExpedition(ctx.getSource()))));
	}

	private static int info(CommandSourceStack source) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager == null) {
			return 0;
		}
		ExpeditionData data = manager.data();
		String status = switch (data.phase) {
			case RUNNING -> "In progress";
			case COUNTDOWN -> "Resetting";
			case COMPLETED -> "Complete";
		};
		source.sendSuccess(() -> Component.literal("Expedition #" + data.expeditionNumber).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Status: " + status + "   Time: " + ExpeditionTime.format(data.elapsedMs)), false);
		source.sendSuccess(() -> Component.literal("Total deaths (all expeditions): " + data.totalDeaths), false);
		source.sendSuccess(() -> Component.literal("Seed: " + data.seed).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int stats(CommandSourceStack source) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager == null) {
			return 0;
		}
		ExpeditionData data = manager.data();
		source.sendSuccess(() -> Component.literal("Expedition stats").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Total deaths: " + data.totalDeaths), false);

		List<PlayerStats> players = data.players.values().stream()
				.sorted(Comparator.comparingInt((PlayerStats p) -> p.deaths).reversed())
				.toList();
		for (PlayerStats player : players) {
			source.sendSuccess(() -> Component.literal("  " + player.name + ": " + player.deaths).withStyle(ChatFormatting.RED), false);
		}

		long completed = data.history.stream().filter(run -> run.completed).count();
		String best = data.bestTimeMs == null ? "none yet" : ExpeditionTime.format(data.bestTimeMs);
		source.sendSuccess(() -> Component.literal("Expeditions finished: " + data.history.size() + " (" + completed + " beat the dragon)   Best time: " + best), false);

		List<RunRecord> recent = data.history.subList(Math.max(0, data.history.size() - 5), data.history.size());
		for (RunRecord run : recent.reversed()) {
			String outcome = run.completed ? "Dragon defeated" : run.reason;
			source.sendSuccess(() -> Component.literal("  #" + run.expedition + "  " + ExpeditionTime.format(run.durationMs) + "  " + outcome).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int newExpedition(CommandSourceStack source) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager == null || !manager.requestNewExpedition(source.getTextName())) {
			source.sendFailure(Component.literal("A new expedition is already starting."));
			return 0;
		}
		return 1;
	}
}
