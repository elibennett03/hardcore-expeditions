package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Killing the dragon in the expedition's End completes the run and stops the timer. */
@Mixin(EnderDragonFight.class)
public class EnderDragonFightMixin {
	@Inject(method = "setDragonKilled", at = @At("TAIL"))
	private void hardcoreExpeditions$onDragonKilled(EnderDragon dragon, CallbackInfo ci) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager != null) {
			manager.onDragonKilled(dragon.level());
		}
	}
}
