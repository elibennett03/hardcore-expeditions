package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dimension-change advancements (We Need to Go Deeper, The End?, the Nether/End tab roots, and
 * Subspace Bubble) check for minecraft:the_nether / minecraft:the_end by name. Expedition worlds
 * have their own names, so report them as the vanilla dimensions they stand in for.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerAdvancementsMixin {
	@Shadow
	private Vec3 enteredNetherPosition;

	@Unique
	private Vec3 hardcoreExpeditions$positionBeforeTeleport;

	@Inject(method = "teleport", at = @At("HEAD"))
	private void hardcoreExpeditions$rememberPosition(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
		this.hardcoreExpeditions$positionBeforeTeleport = ((ServerPlayer) (Object) this).position();
	}

	@Inject(method = "triggerDimensionChangeTriggers", at = @At("HEAD"))
	private void hardcoreExpeditions$trackNetherEntry(ServerLevel oldLevel, CallbackInfo ci) {
		ServerLevel newLevel = ((ServerPlayer) (Object) this).level();
		boolean expedition = ExpeditionManager.vanillaDimension(oldLevel) != oldLevel.dimension()
				|| ExpeditionManager.vanillaDimension(newLevel) != newLevel.dimension();
		// Vanilla remembers where you entered the Nether (for Subspace Bubble) only for the real Nether.
		if (expedition
				&& ExpeditionManager.vanillaDimension(oldLevel) == Level.OVERWORLD
				&& ExpeditionManager.vanillaDimension(newLevel) == Level.NETHER) {
			this.enteredNetherPosition = this.hardcoreExpeditions$positionBeforeTeleport;
		}
	}

	@Redirect(
			method = "triggerDimensionChangeTriggers",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;")
	)
	private ResourceKey<Level> hardcoreExpeditions$reportVanillaDimension(ServerLevel level) {
		return ExpeditionManager.vanillaDimension(level);
	}
}
