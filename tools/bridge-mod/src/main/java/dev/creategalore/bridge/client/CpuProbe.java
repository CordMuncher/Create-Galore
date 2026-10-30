package dev.creategalore.bridge.client;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memGetShort;

import dev.creategalore.bridge.present.Com;
import dev.creategalore.bridge.present.Ffi;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;

/**
 * For {@link FrameHitchWatch}: which kind of core the render thread runs on and how fast the CPU is allowed to run.
 * Laptop CPUs like the i7-12700H mix fast performance cores with slow efficiency cores, and slow down under heat or
 * power limits; either one makes all of the game's work take longer at once. Windows only; anything it can't read
 * stays unknown.
 */
final class CpuProbe {
    private static final int PROCESSOR_INFORMATION = 11;
    private static final int POWER_INFO_SIZE = 24;     // PROCESSOR_POWER_INFORMATION: six ULONGs

    private static long getCurrentProcessorNumber;
    private static long callNtPowerInformation;
    private static long getProcessMemoryInfo;
    private static final int MEMORY_COUNTERS_SIZE = 72;  // PROCESS_MEMORY_COUNTERS on 64-bit
    private static byte[] coreClass = new byte[0];     // efficiency class by logical processor in group 0; higher is faster
    private static byte fastest = -1;
    private static int processors;
    private static String cores = "unknown";

    private CpuProbe() {
    }

    static void init() {
        if (Platform.get() != Platform.WINDOWS) return;
        try {
            getCurrentProcessorNumber = Com.function("kernel32.dll", "GetCurrentProcessorNumber");
        } catch (Throwable ignored) {
            // core type stays unknown
        }
        try {
            readCpuSets(Com.function("kernel32.dll", "GetSystemCpuSetInformation"));
        } catch (Throwable ignored) {
            // core type stays unknown
        }
        try {
            getProcessMemoryInfo = Com.function("kernel32.dll", "K32GetProcessMemoryInfo");
        } catch (Throwable ignored) {
            // page faults stay unknown
        }
        try {
            callNtPowerInformation = Com.function("powrprof.dll", "CallNtPowerInformation");
            processors = Runtime.getRuntime().availableProcessors();
        } catch (Throwable ignored) {
            // clocks stay unknown
        }
    }

    private static void readCpuSets(long function) {
        long out = MemoryUtil.nmemAlloc(4);
        try {
            Ffi.call("PIPPI:I", function, 0L, 0, out, 0L, 0);
            int size = memGetInt(out);
            if (size <= 0) return;
            ByteBuffer buf = memAlloc(size);
            try {
                if ((int) Ffi.call("PIPPI:I", function, MemoryUtil.memAddress(buf), size, out, 0L, 0) == 0) return;
                long base = MemoryUtil.memAddress(buf);
                byte[] classes = new byte[256];
                Arrays.fill(classes, (byte) -1);
                int max = -1;
                for (int at = 0; at + 24 <= size; ) {
                    int entry = memGetInt(base + at);
                    if (entry <= 0) break;
                    // SYSTEM_CPU_SET_INFORMATION: Size, Type, then Id, Group, LogicalProcessorIndex, CoreIndex,
                    // LastLevelCacheIndex, NumaNodeIndex, EfficiencyClass
                    if (memGetInt(base + at + 4) == 0 && memGetShort(base + at + 12) == 0) {
                        int logical = memGetByte(base + at + 14) & 0xFF;
                        classes[logical] = memGetByte(base + at + 18);
                        max = Math.max(max, logical);
                    }
                    at += entry;
                }
                coreClass = Arrays.copyOf(classes, max + 1);
                byte top = -1;
                for (byte c : coreClass) top = (byte) Math.max(top, c);
                fastest = top;
                int fast = 0, slow = 0;
                for (byte c : coreClass) {
                    if (c == top) fast++;
                    else if (c >= 0) slow++;
                }
                cores = slow == 0 ? fast + " threads, all one kind" : fast + " fast-core threads + " + slow + " efficiency cores";
            } finally {
                memFree(buf);
            }
        } finally {
            MemoryUtil.nmemFree(out);
        }
    }

    /** Called on the render thread: 1 = on a fast core, 0 = on an efficiency core, -1 = unknown. */
    static int onFastCore() {
        if (getCurrentProcessorNumber == 0L || fastest < 0) return -1;
        try {
            int cpu = (int) Ffi.call(":I", getCurrentProcessorNumber);
            if (cpu < 0 || cpu >= coreClass.length || coreClass[cpu] < 0) return -1;
            return coreClass[cpu] == fastest ? 1 : 0;
        } catch (Throwable e) {
            getCurrentProcessorNumber = 0L;
            return -1;
        }
    }

    /**
     * The game's page faults so far and its working set (the part of its memory that is in RAM right now), or null.
     * Windows counts a fault each time the game touches memory it had moved out of the game's RAM, also when it only
     * has to fetch it back from its compressed store or standby list, which costs CPU but no disk reads.
     */
    static long[] memory() {
        if (getProcessMemoryInfo == 0L) return null;
        long buf = MemoryUtil.nmemAlloc(MEMORY_COUNTERS_SIZE);
        try {
            MemoryUtil.memPutInt(buf, MEMORY_COUNTERS_SIZE);
            if ((int) Ffi.call("PPI:I", getProcessMemoryInfo, -1L, buf, MEMORY_COUNTERS_SIZE) == 0) return null;
            return new long[] {memGetInt(buf + 4) & 0xFFFFFFFFL, MemoryUtil.memGetLong(buf + 16)};
        } catch (Throwable e) {
            getProcessMemoryInfo = 0L;
            return null;
        } finally {
            MemoryUtil.nmemFree(buf);
        }
    }

    static String cores() {
        return cores;
    }

    /**
     * The CPU's clock right now: the average current MHz and the lowest limit Windows reports, split by core kind, e.g.
     * "fast cores 2700 MHz (limit 4700), efficiency cores 2100 MHz (limit 3500)". Empty if unknown.
     */
    static String clocks() {
        if (callNtPowerInformation == 0L || processors <= 0) return "";
        ByteBuffer buf = memAlloc(processors * POWER_INFO_SIZE);
        try {
            long base = MemoryUtil.memAddress(buf);
            int status = (int) Ffi.call("IPIPI:I", callNtPowerInformation, PROCESSOR_INFORMATION, 0L, 0, base, processors * POWER_INFO_SIZE);
            if (status != 0) return "";
            long[] mhz = new long[2], limit = {Long.MAX_VALUE, Long.MAX_VALUE}, max = new long[2];
            int[] n = new int[2];
            for (int i = 0; i < processors; i++) {
                long at = base + (long) i * POWER_INFO_SIZE;
                int number = memGetInt(at);
                int kind = number < coreClass.length && fastest >= 0 && coreClass[number] >= 0 && coreClass[number] != fastest ? 1 : 0;
                n[kind]++;
                max[kind] = Math.max(max[kind], memGetInt(at + 4));
                mhz[kind] += memGetInt(at + 8);
                limit[kind] = Math.min(limit[kind], memGetInt(at + 12));
            }
            StringBuilder out = new StringBuilder();
            String[] names = {fastest >= 0 && cores.contains("efficiency") ? "fast cores" : "cores", "efficiency cores"};
            for (int k = 0; k < 2; k++) {
                if (n[k] == 0) continue;
                if (out.length() > 0) out.append(", ");
                out.append(String.format(Locale.ROOT, "%s %d MHz (limit %d of %d)", names[k], mhz[k] / n[k], limit[k], max[k]));
            }
            return out.toString();
        } catch (Throwable e) {
            callNtPowerInformation = 0L;
            return "";
        } finally {
            memFree(buf);
        }
    }
}
