package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import com.elibennett.hardcoreexpeditions.ExpeditionWorlds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** End gateways only create their outer-island exit in minecraft:the_end; let them work in an expedition End too. */
@Mixin(TheEndGatewayBlockEntity.class)
public class TheEndGatewayBlockEntityMixin {
	@Redirect(
			method = "getPortalPosition",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;")
	)
	private ResourceKey<Level> hardcoreExpeditions$treatExpeditionEndAsEnd(ServerLevel level) {
		ExpeditionManager manager = ExpeditionManager.get();
		ExpeditionWorlds worlds = manager == null ? null : manager.worldsContaining(level);
		return worlds != null && worlds.isEnd(level) ? Level.END : level.dimension();
	}
}
