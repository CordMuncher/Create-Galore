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
        ByteBuffer resources = resources(dll);
        if (resources == null) {
            return null;
        }
        for (int at = 0; at + 16 <= resources.limit(); at += 4) {
            if (resources.getInt(at) == FIXED_FILE_INFO_SIGNATURE && resources.getInt(at + 4) == 0x00010000) {
                int high = resources.getInt(at + 8);
                int low = resources.getInt(at + 12);
                String version = (high >>> 16) + "." + (high & 0xFFFF) + "." + (low >>> 16);
                return (low & 0xFFFF) == 0 ? version : version + "." + (low & 0xFFFF);
            }
        }
        return null;
    }

    /** True if the DLL's resources hold {@code text} (as Windows keeps its version strings, UTF-16), such as its company. */
    static boolean hasString(Path dll, String text) {
        ByteBuffer resources = resources(dll);
        if (resources == null) {
            return false;
        }
        byte[] wanted = text.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        outer:
        for (int at = 0; at + wanted.length <= resources.limit(); at++) {
            for (int i = 0; i < wanted.length; i++) {
                if (resources.get(at + i) != wanted[i]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** The DLL's resource section, or null if it isn't a DLL or has none. */
    private static ByteBuffer resources(Path dll) {
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
                if (new String(name, java.nio.charset.StandardCharsets.US_ASCII).startsWith(".rsrc")) {
                    int size = table.getInt(i * 40 + 16);
                    int offset = table.getInt(i * 40 + 20);
                    return read(file, Integer.toUnsignedLong(offset), Math.min(size, MAX_RESOURCES));
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
