package dev.creategalore.bridge.mixin;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound Physics: Aeronautics froze the game for good on the way through a portal. Before it deletes a reflection's
 * OpenAL source it asks whether any other sound has since been given the same source number, and it asks with the
 * dying sound's lock held, taking every other sound's lock in turn. Leaving a world tears down every sound on the game
 * thread while the sound thread keeps updating its own; each thread then held one sound and waited for the other's,
 * and neither ever let go (seen in a player's freeze log: "Render thread" and "Sound engine" deadlocked in
 * liveAllocationUsesId, from onLevelUnload and from a Doppler pitch update).
 *
 * <p>A thread that already holds a sound's lock now never waits for another: it reads that sound's reflections as
 * they stand, without the lock, and tries again if they change under it. A thread holding no sound's lock asks the
 * same way the mod does, since it cannot be part of the loop. The answer only guards against deleting a source
 * number that was handed out again, which takes a source to be freed and reused in that same instant.
 */
@Mixin(targets = "com.sonicether.soundphysics.multipath.MultipathAudioRenderer", remap = false)
public abstract class SoundPhysicsSourceCheckMixin {
    @Unique
    private static final Logger create_galore_bridge$LOG = LogUtils.getLogger();
    @Unique
    private static final Object create_galore_bridge$SET_UP = new Object();
    @Unique
    private static volatile boolean create_galore_bridge$ready;
    @Unique
    private static volatile boolean create_galore_bridge$broken;
    @Unique
    private static Field create_galore_bridge$backend;
    @Unique
    private static MethodHandle create_galore_bridge$contextCurrent;
    @Unique
    private static VarHandle create_galore_bridge$routes;
    @Unique
    private static VarHandle create_galore_bridge$contextEpoch;
    @Unique
    private static VarHandle create_galore_bridge$sourceId;
    @Unique
    private static VarHandle create_galore_bridge$allocationEpoch;

    @Shadow
    private static Stream<?> allParents() {
        throw new AssertionError();
    }

    @Inject(method = "liveAllocationUsesId(IJ)Z", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$noWaitWhileHolding(int sourceId, long excludedAllocationEpoch, CallbackInfoReturnable<Boolean> cir) {
        List<?> parents = allParents().toList();
        boolean holding = false;
        for (Object parent : parents) {
            if (Thread.holdsLock(parent)) {
                holding = true;
                break;
            }
        }
        if (!holding || !create_galore_bridge$setUp()) return;
        try {
            Object backend = create_galore_bridge$backend.get(null);
            for (Object parent : parents) {
                if (!(boolean) create_galore_bridge$contextCurrent.invoke(backend, (long) create_galore_bridge$contextEpoch.get(parent))) continue;
                for (Object auxiliary : create_galore_bridge$routesOf(parent)) {
                    if (auxiliary != null && (int) create_galore_bridge$sourceId.getVolatile(auxiliary) == sourceId
                            && (long) create_galore_bridge$allocationEpoch.getVolatile(auxiliary) != excludedAllocationEpoch) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
            cir.setReturnValue(false);
        } catch (Throwable e) {
            create_galore_bridge$broken = true;
            create_galore_bridge$LOG.warn("Create Galore: Sound Physics source check fell back to the mod's own (it can freeze the game)", e);
        }
    }

    /** The sound's reflections as they stand. Another thread may be changing them, so a read that breaks is tried again. */
    @Unique
    private static Object[] create_galore_bridge$routesOf(Object parent) {
        Map<?, ?> routes = (Map<?, ?>) create_galore_bridge$routes.get(parent);
        if (Thread.holdsLock(parent)) return routes.values().toArray();
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                return routes.values().toArray();
            } catch (RuntimeException changing) {
                Thread.onSpinWait();
            }
        }
        return new Object[0];
    }

    @Unique
    private static boolean create_galore_bridge$setUp() {
        if (create_galore_bridge$ready) return true;
        if (create_galore_bridge$broken) return false;
        synchronized (create_galore_bridge$SET_UP) {
            if (create_galore_bridge$ready) return true;
            try {
                Class<?> renderer = Class.forName("com.sonicether.soundphysics.multipath.MultipathAudioRenderer");
                Class<?> parent = Class.forName(renderer.getName() + "$ParentState", false, renderer.getClassLoader());
                Class<?> auxiliary = Class.forName(renderer.getName() + "$AuxiliaryState", false, renderer.getClassLoader());
                Class<?> backendType = Class.forName(renderer.getName() + "$AudioBackend", false, renderer.getClassLoader());
                Field backend = renderer.getDeclaredField("audioBackend");
                backend.setAccessible(true);
                Method contextCurrent = backendType.getMethod("contextCurrent", long.class);
                contextCurrent.setAccessible(true);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                create_galore_bridge$routes = create_galore_bridge$field(lookup, parent, "routes", Map.class);
                create_galore_bridge$contextEpoch = create_galore_bridge$field(lookup, parent, "contextEpoch", long.class);
                create_galore_bridge$sourceId = create_galore_bridge$field(lookup, auxiliary, "sourceId", int.class);
                create_galore_bridge$allocationEpoch = create_galore_bridge$field(lookup, auxiliary, "allocationEpoch", long.class);
                create_galore_bridge$contextCurrent = lookup.unreflect(contextCurrent);
                create_galore_bridge$backend = backend;
                create_galore_bridge$ready = true;
                create_galore_bridge$LOG.info("Create Galore: Sound Physics no longer waits on one sound while holding another (portal freeze)");
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                create_galore_bridge$broken = true;
                create_galore_bridge$LOG.warn("Create Galore: Sound Physics changed; its portal freeze fix is off", e);
                return false;
            }
        }
    }

    @Unique
    private static VarHandle create_galore_bridge$field(MethodHandles.Lookup lookup, Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type) throw new NoSuchFieldException(owner.getName() + "." + name + " is " + field.getType());
        field.setAccessible(true);
        return lookup.unreflectVarHandle(field);
    }
}
