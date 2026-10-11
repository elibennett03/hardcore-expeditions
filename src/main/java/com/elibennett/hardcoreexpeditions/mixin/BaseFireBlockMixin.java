package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla only lets fire light a Nether portal in minecraft:overworld or minecraft:the_nether; expedition Overworlds and Nethers count too. */
@Mixin(BaseFireBlock.class)
public class BaseFireBlockMixin {
	@Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
	private static void hardcoreExpeditions$allowExpeditionPortals(Level level, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel serverLevel) {
			ResourceKey<Level> dimension = ExpeditionManager.vanillaDimension(serverLevel);
			cir.setReturnValue(dimension == Level.OVERWORLD || dimension == Level.NETHER);
		}
	}
}
