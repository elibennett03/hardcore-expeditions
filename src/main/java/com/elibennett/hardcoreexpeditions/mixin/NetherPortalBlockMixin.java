package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import com.elibennett.hardcoreexpeditions.ExpeditionWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla portals always lead to minecraft:the_nether; inside an expedition they lead to its own Nether. */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
	@Shadow
	private TeleportTransition getExitPortal(ServerLevel newLevel, Entity entity, BlockPos portalEntryPos, BlockPos approximateExitPos, boolean toNether, WorldBorder worldBorder) {
		throw new AssertionError();
	}

	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void hardcoreExpeditions$useExpeditionNether(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos, CallbackInfoReturnable<TeleportTransition> cir) {
		ExpeditionManager manager = ExpeditionManager.get();
		ExpeditionWorlds worlds = manager == null ? null : manager.worldsContaining(currentLevel);
		if (worlds == null) {
			return;
		}

		ServerLevel newLevel = worlds.netherPortalTarget(currentLevel);
		if (newLevel == null) {
			cir.setReturnValue(null);
			return;
		}

		// Same math as vanilla (8:1 coordinate scaling), just with the expedition's worlds.
		boolean toNether = newLevel == worlds.nether();
		WorldBorder border = newLevel.getWorldBorder();
		double scale = DimensionType.getTeleportationScale(currentLevel.dimensionType(), newLevel.dimensionType());
		BlockPos approximateExitPos = border.clampToBounds(entity.getX() * scale, entity.getY(), entity.getZ() * scale);
		cir.setReturnValue(this.getExitPortal(newLevel, entity, portalEntryPos, approximateExitPos, toNether, border));
	}
}
