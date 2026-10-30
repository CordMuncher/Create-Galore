package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.ClassTreeIdRegistry;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Every entity class numbers its synced data fields (SynchedEntityData.defineId) in its static initializer, and the
 * numbers live in one shared ClassTreeIdRegistry backed by a plain fastutil map. NeoForge builds mods on several threads
 * at once, and mods that create their entity types while being built (Hybrid Aquatic does, for every fish and shark)
 * initialize entity classes on those threads. Two threads adding to the map together can corrupt it while it grows: the
 * game then stops during mod loading with "ArrayIndexOutOfBoundsException ... Object2IntOpenHashMap.rehash", or two
 * fields quietly share a number. It happens rarely and at random, so a second start usually works. Reads and writes now
 * take turns. Neither one runs other code while holding the lock (the lookup only walks superclasses, which are always
 * initialized already), so it can't deadlock, and the lock is free whenever the game runs on one thread.
 */
@Mixin(ClassTreeIdRegistry.class)
public abstract class EntityDataIdsLockMixin {
    @WrapMethod(method = "getLastIdFor")
    private int create_galore_bridge$lockedLookup(Class<?> type, Operation<Integer> original) {
        synchronized (this) {
            return original.call(type);
        }
    }

    @WrapMethod(method = "define")
    private int create_galore_bridge$lockedDefine(Class<?> type, Operation<Integer> original) {
        synchronized (this) {
            return original.call(type);
        }
    }
}
