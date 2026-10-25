package dev.creategalore.bridge.latency;

import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.system.MemoryUtil.nmemCalloc;

import java.util.concurrent.locks.LockSupport;

import dev.creategalore.bridge.present.Com;
import dev.creategalore.bridge.present.Ffi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits until a System.nanoTime deadline to within a few tens of microseconds, for the GPU pacer and the frame cap
 * that waits before input ({@link FrameLatency}). Java's own sleeps on Windows only wake on the system timer's tick
 * (up to 15.6 ms unless something raised its resolution), so there it sleeps on a high resolution waitable timer
 * (Windows 10 1803 and newer) and spins only the last bit. Elsewhere LockSupport.parkNanos is fine-grained enough.
 */
final class PreciseWait {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    // CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, TIMER_ALL_ACCESS
    private static final int HIGH_RESOLUTION = 0x2;
    private static final int TIMER_ALL_ACCESS = 0x1F0003;
    private static final int INFINITE = 0xFFFFFFFF;

    private static boolean started;
    private static long timer;
    private static long setTimer;
    private static long waitFor;
    private static long dueTime;
    /** How close to the deadline the sleep stops and the spin starts. */
    private static long spinNanos = 1_500_000L;

    private PreciseWait() {}

    /** Returns at {@code deadline} (System.nanoTime) or right away if it has passed. */
    static void until(long deadline) {
        if (!started) {
            start();
        }
        long left;
        while ((left = deadline - System.nanoTime()) > spinNanos) {
            sleep(left - spinNanos);
        }
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static void sleep(long nanos) {
        if (timer != 0L) {
            memPutLong(dueTime, -Math.max(1L, nanos / 100L)); // relative, in 100 ns units
            if ((int) Ffi.call("PPIPPI:I", setTimer, timer, dueTime, 0, 0L, 0L, 0) != 0) {
                Ffi.call("PI:I", waitFor, timer, INFINITE);
                return;
            }
        }
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            Thread.yield(); // no timer to sleep on: spin
        } else {
            LockSupport.parkNanos(nanos);
        }
    }

    private static void start() {
        started = true;
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            spinNanos = 150_000L;
            return;
        }
        try {
            long create = Com.function("kernel32.dll", "CreateWaitableTimerExW");
            setTimer = Com.function("kernel32.dll", "SetWaitableTimer");
            waitFor = Com.function("kernel32.dll", "WaitForSingleObject");
            timer = Ffi.call("PPII:P", create, 0L, 0L, HIGH_RESOLUTION, TIMER_ALL_ACCESS);
            if (timer != 0L) {
                dueTime = nmemCalloc(1, 8);
                spinNanos = 500_000L;
                LOG.info("Latency: waits use a high resolution timer");
            } else {
                LOG.warn("Latency: no high resolution timer on this Windows (older than 10 version 1803?), waits spin");
            }
        } catch (RuntimeException | LinkageError e) {
            timer = 0L;
            LOG.warn("Latency: no high resolution timer, waits spin: {}", e.toString());
        }
    }
}
