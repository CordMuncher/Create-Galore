package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For testing the motion vectors: one frame's DLSS inputs saved exactly as numpy arrays (colour, motion in pixels,
 * depth; bottom row first, as OpenGL has them), so a script can check them against each other frame by frame. The
 * DLAA test kit asks for it on the frames it captures (see {@link Dlss#dumpNextFrame}).
 */
final class DebugDump {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    private DebugDump() {}

    /** Saves {@code texture} (half floats, {@code channels} of them) as {@code dir/name.npy}. */
    static void write(Path dir, String name, int texture, int width, int height, int channels) {
        int format = switch (channels) {
            case 1 -> GL11C.GL_RED;
            case 2 -> GL30C.GL_RG;
            default -> GL11C.GL_RGBA;
        };
        int bytes = width * height * channels * 2;
        ByteBuffer data = MemoryUtil.memAlloc(bytes);
        try {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
            GL45C.glGetTextureImage(texture, 0, format, GL30C.GL_HALF_FLOAT, data);
            Files.createDirectories(dir);
            try (FileChannel out = FileChannel.open(dir.resolve(name + ".npy"), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                out.write(header(height, width, channels));
                out.write(data);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not save {} for testing: {}", name, e.toString());
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    // numpy's .npy format 1.0: magic, version, header length, then a dict padded to 64 bytes.
    private static ByteBuffer header(int height, int width, int channels) {
        String dict = String.format(Locale.ROOT, "{'descr': '<f2', 'fortran_order': False, 'shape': (%d, %d, %d), }", height, width, channels);
        int length = 10 + dict.length() + 1;
        int padded = (length + 63) / 64 * 64;
        StringBuilder text = new StringBuilder(dict);
        while (10 + text.length() + 1 < padded) {
            text.append(' ');
        }
        text.append('\n');
        ByteBuffer header = ByteBuffer.allocate(padded).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 0x93).put("NUMPY".getBytes(StandardCharsets.US_ASCII)).put((byte) 1).put((byte) 0);
        header.putShort((short) text.length());
        header.put(text.toString().getBytes(StandardCharsets.US_ASCII));
        return header.flip();
    }
}
