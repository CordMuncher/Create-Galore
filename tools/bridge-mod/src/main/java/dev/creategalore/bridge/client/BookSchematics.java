package dev.creategalore.bridge.client;

import com.simibubi.create.foundation.utility.CreatePaths;
import com.simibubi.create.foundation.utility.FilesHelper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves a book model as a Create schematic in this player's own schematics folder, the one Create's Schematic and
 * Quill writes to. From there it goes the usual Create way: an Empty Schematic in a Schematic Table uploads it to the
 * server and gives back a Schematic, which shows the build as a hologram to place, turn and mirror. Each player saves
 * and uploads their own copy, so it works the same on a dedicated server with several players. While the placed
 * Schematic is in the off hand, {@link SchematicTrace} keeps the build visible to trace by hand.
 */
final class BookSchematics {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    private BookSchematics() {
    }

    /** Writes the model to schematics/<its title>.nbt (never over another file); the file's name, or null if it failed. */
    static String save(ModelView model) {
        Minecraft mc = Minecraft.getInstance();
        CompoundTag data = model.structure();
        if (data == null) return null;
        Path dir = CreatePaths.SCHEMATICS_DIR;
        String name = model.title.isBlank() ? "book build" : model.title;
        try {
            Files.createDirectories(dir);
            String file = FilesHelper.findFirstValidFilename(name, dir, "nbt");
            try (OutputStream out = Files.newOutputStream(dir.resolve(file))) {
                NbtIo.writeCompressed(data, out);
            }
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Saved the book's build as schematics/" + file + ". Put an Empty "
                        + "Schematic in a Schematic Table and pick it to get it as a Schematic. Place it with the Schematic in "
                        + "your main hand, then move it to your off hand: the build stays shown while you place blocks, and each "
                        + "block disappears from it once the right one is in its spot.").withStyle(ChatFormatting.GRAY), false);
            }
            return file;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Couldn't save a book model as a schematic", e);
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("Couldn't save the schematic: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            return null;
        }
    }
}
