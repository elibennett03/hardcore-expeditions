package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The allow_entering_nether_using_portals game rule only checks minecraft:the_nether; apply it to an expedition's Nether too. */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {
	@Redirect(
			method = "isAllowedToEnterPortal",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;")
	)
	private ResourceKey<Level> hardcoreExpeditions$reportVanillaDimension(Level toLevel) {
		return toLevel instanceof ServerLevel level ? ExpeditionManager.vanillaDimension(level) : toLevel.dimension();
	}
}
