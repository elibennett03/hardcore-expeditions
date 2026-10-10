package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every expedition starts at the server spawn, so vanilla spawn protection would stop non-op players
 * from breaking blocks right where the run begins. Turn it off inside expeditions.
 */
@Mixin(DedicatedServer.class)
public class DedicatedServerMixin {
	@Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true)
	private void hardcoreExpeditions$noSpawnProtection(ServerLevel level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager != null && manager.worldsContaining(level) != null) {
			cir.setReturnValue(false);
		}
	}
}
