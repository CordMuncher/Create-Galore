package dev.creategalore.bridge.present;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.lwjgl.system.windows.WinBase;

/**
 * The Bridge's own native helpers (cg_ngx.dll, cg_present.dll), which ship inside the mod jar. Each is unpacked under a
 * name that carries its hash, so a running game never has its copy replaced, and loaded from there.
 */
public final class NativeHelper {
    private final String name;
    private final long module;

    private NativeHelper(String name, long module) {
        this.name = name;
        this.module = module;
    }

    /** Unpacks {@code natives/windows-x64/<name>.dll} into {@code folder} (if that exact build is not there yet) and loads it. */
    public static NativeHelper load(String name, Path folder) throws IOException {
        String resource = "/natives/windows-x64/" + name + ".dll";
        byte[] bytes;
        try (InputStream in = NativeHelper.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("The mod jar has no " + resource);
            }
            bytes = in.readAllBytes();
        }
        Path dll = folder.resolve(name + "-" + sha256(bytes).substring(0, 12) + ".dll");
        if (!Files.exists(dll) || Files.size(dll) != bytes.length) {
            Files.createDirectories(folder);
            Path temp = Files.createTempFile(folder, name, ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, dll, StandardCopyOption.REPLACE_EXISTING);
        }
        long module = WinBase.LoadLibrary(dll.toString());
        if (module == 0L) {
            throw new IllegalStateException("Could not load " + dll + " (error " + WinBase.getLastError() + ")");
        }
        return new NativeHelper(name, module);
    }

    /** The address of an exported function. */
    public long symbol(String function) {
        long address = WinBase.GetProcAddress(module, function);
        if (address == 0L) {
            throw new IllegalStateException(name + ".dll has no " + function);
        }
        return address;
    }

    /** Throws unless the helper's {@code <name>_abi()} is {@code abi}, the version this Bridge was written for. */
    public void checkAbi(int abi) {
        int found = (int) Ffi.call(":I", symbol(name + "_abi"));
        if (found != abi) {
            throw new IllegalStateException(name + ".dll is helper version " + found + ", the Bridge needs " + abi);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
