package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A Windows DLL's file version, as Explorer shows it under Details: the VS_FIXEDFILEINFO in the DLL's resource
 * section, found without loading the DLL (so it works on any system, and on a DLL the game is using).
 */
final class DllVersion {
    private static final int FIXED_FILE_INFO_SIGNATURE = 0xFEEF04BD;
    private static final int MAX_RESOURCES = 16 << 20;

    private DllVersion() {}

    /** Like 310.9.16 (a last part of 0 left out), or null if the file has no version. */
    static String read(Path dll) {
        try (FileChannel file = FileChannel.open(dll, StandardOpenOption.READ)) {
            int peHeader = read(file, 0x3C, 4).getInt();
            ByteBuffer coff = read(file, peHeader, 24);
            if (coff.getInt(0) != 0x00004550) { // "PE\0\0"
                return null;
            }
            int sections = Short.toUnsignedInt(coff.getShort(6));
            int optionalHeader = Short.toUnsignedInt(coff.getShort(20));
            ByteBuffer table = read(file, peHeader + 24L + optionalHeader, sections * 40);
            for (int i = 0; i < sections; i++) {
                byte[] name = new byte[8];
                table.get(i * 40, name);
                if (!new String(name, java.nio.charset.StandardCharsets.US_ASCII).startsWith(".rsrc")) {
                    continue;
                }
                int size = table.getInt(i * 40 + 16);
                int offset = table.getInt(i * 40 + 20);
                ByteBuffer resources = read(file, Integer.toUnsignedLong(offset), Math.min(size, MAX_RESOURCES));
                for (int at = 0; at + 16 <= resources.limit(); at += 4) {
                    if (resources.getInt(at) == FIXED_FILE_INFO_SIGNATURE && resources.getInt(at + 4) == 0x00010000) {
                        int high = resources.getInt(at + 8);
                        int low = resources.getInt(at + 12);
                        String version = (high >>> 16) + "." + (high & 0xFFFF) + "." + (low >>> 16);
                        return (low & 0xFFFF) == 0 ? version : version + "." + (low & 0xFFFF);
                    }
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static ByteBuffer read(FileChannel file, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining() && file.read(buffer, position + buffer.position()) > 0) {
            // keeps reading
        }
        return buffer.flip();
    }
}
