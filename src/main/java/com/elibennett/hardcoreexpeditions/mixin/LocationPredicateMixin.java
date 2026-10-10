package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.ExpeditionManager;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Location checks like "killed a ghast in the Overworld" (Uneasy Alliance) or "rode a strider in Overworld lava" (Feels Like Home) treat expedition worlds as the vanilla ones. */
@Mixin(LocationPredicate.class)
public class LocationPredicateMixin {
	@Redirect(
			method = "matches",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;")
	)
	private ResourceKey<Level> hardcoreExpeditions$reportVanillaDimension(ServerLevel level) {
		return ExpeditionManager.vanillaDimension(level);
	}
}
