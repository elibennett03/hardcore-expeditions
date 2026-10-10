package com.elibennett.hardcoreexpeditions.mixin;

import com.elibennett.hardcoreexpeditions.HardcoreExpeditions;
import net.minecraft.util.Util;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fantasy deletes a world's folder on the server thread, so the server freezes while an old
 * expedition is erased. A long run can leave hundreds of MB, right as everyone arrives in the new
 * world. Instead, rename the folder out of the way (instant) and delete it on a background thread.
 */
@Mixin(targets = "xyz.nucleoid.fantasy.RuntimeLevelManager")
public class FantasyLevelDeletionMixin {
	@Redirect(method = "delete", at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/FileUtils;deleteDirectory(Ljava/io/File;)V"))
	private void hardcoreExpeditions$deleteInBackground(File directory) throws IOException {
		Path source = directory.toPath();
		// The leading "." keeps it from ever matching a live expedition; leftovers are cleaned up at startup.
		Path trash = source.resolveSibling(".deleting_" + source.getFileName() + "_" + System.nanoTime());
		try {
			Files.move(source, trash);
		} catch (IOException e) {
			// Can't rename (e.g. Windows with files still open): fall back to deleting right here.
			FileUtils.deleteDirectory(directory);
			return;
		}
		Util.ioPool().execute(() -> {
			try {
				FileUtils.deleteDirectory(trash.toFile());
			} catch (IOException e) {
				HardcoreExpeditions.LOGGER.warn("Could not delete old expedition world {}; it will be removed on the next restart", trash, e);
			}
		});
	}
}
