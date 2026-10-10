package com.elibennett.hardcoreexpeditions.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess hardcoreExpeditions$getStorageSource();

	@Invoker("updateEffectiveRespawnData")
	void hardcoreExpeditions$updateEffectiveRespawnData();
}
