package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import com.elibennett.hardcoreexpeditions.ExpeditionWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** End portals inside an expedition lead to its own End, and the exit portal leads back to its Overworld. */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {
	/** Vanilla only rolls the credits for the exit portal in minecraft:the_end; roll them in an expedition End too. */
	@Redirect(
			method = "entityInside",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;")
	)
	private ResourceKey<Level> hardcoreExpeditions$creditsInExpeditionEnd(Level level) {
		ExpeditionManager manager = ExpeditionManager.get();
		ExpeditionWorlds worlds = manager == null ? null : manager.worldsContaining(level);
		return worlds != null && worlds.isEnd(level) ? Level.END : level.dimension();
	}

	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void hardcoreExpeditions$useExpeditionEnd(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos, CallbackInfoReturnable<TeleportTransition> cir) {
		ExpeditionManager manager = ExpeditionManager.get();
		ExpeditionWorlds worlds = manager == null ? null : manager.worldsContaining(currentLevel);
		if (worlds == null) {
			return;
		}

		TeleportTransition.PostTeleportTransition post = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
		if (!worlds.isEnd(currentLevel)) {
			// Going in: same obsidian platform and arrival point as vanilla.
			ServerLevel end = worlds.end();
			Vec3 spawnPos = Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT);
			EndPlatformFeature.createEndPlatform(end, BlockPos.containing(spawnPos).below(), true);
			if (entity instanceof ServerPlayer) {
				spawnPos = spawnPos.subtract(0.0, 1.0, 0.0);
			}
			cir.setReturnValue(new TeleportTransition(end, spawnPos, Vec3.ZERO, Direction.WEST.toYRot(), 0.0F, Relative.union(Relative.DELTA, Set.of(Relative.X_ROT)), post));
		} else if (entity instanceof ServerPlayer player) {
			// Coming out: the player's bed, or the expedition spawn (the server spawn points there).
			cir.setReturnValue(player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
		} else {
			ServerLevel overworld = worlds.overworld();
			Vec3 pos = Vec3.atBottomCenterOf(entity.adjustSpawnLocation(overworld, manager.spawn()));
			cir.setReturnValue(new TeleportTransition(overworld, pos, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), post));
		}
	}
}
