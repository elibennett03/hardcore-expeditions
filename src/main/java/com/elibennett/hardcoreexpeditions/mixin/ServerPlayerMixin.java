package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces player death inside an expedition. die() only runs after totems of undying have had
 * their chance, so totems still work normally.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@Inject(method = "die", at = @At("HEAD"), cancellable = true)
	private void hardcoreExpeditions$onDeath(DamageSource source, CallbackInfo ci) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager != null && manager.handleDeath((ServerPlayer) (Object) this, source)) {
			ci.cancel();
		}
	}

	/** Nobody takes damage while the countdown to the next expedition is running. */
	@Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
	private void hardcoreExpeditions$invulnerableDuringCountdown(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		ExpeditionManager manager = ExpeditionManager.get();
		if (manager != null && manager.protectsDuringCountdown((ServerPlayer) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
