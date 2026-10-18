package dev.creategalore.bridge.mixin;

import avionics.create_dupe_fix.CreateDupeFix;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create Dupe Fix 1.2 stops drops, forces blocks to survive and skips container spills while a ship is being
 * disassembled. It decides that by looking for "sable", "aeronautics", "simulated" or "valkyrienskies" anywhere in a
 * class name on the call stack, which also matches PassableLeavesBlock (Fruits Delight), EncasableBlock (Create),
 * DispenseUsableItemBehavior (Aether) and every Aeronautics Winds class. So ripe fruit picked or knocked off Fruits
 * Delight leaves vanished, decaying fruit leaves dropped nothing, and torches, carpets and rails on blocks that Winds
 * melts or covers floated or vanished without an item. It also built a whole stack trace on every survival check,
 * worldgen included. Only count real ship code: the Sable, Simulated, Aeronautics and Valkyrien Skies packages
 * (and Simulated Coasters' trains), on the server thread.
 */
@Mixin(value = CreateDupeFix.class, remap = false)
public abstract class DupeFixShipCheckMixin {
    private static final StackWalker WALKER = StackWalker.getInstance();

    @Inject(method = "isShipDisassembly", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$onlyShipCode(CallbackInfoReturnable<Boolean> cir) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(WALKER.walk(frames -> frames.anyMatch(f -> isShipClass(f.getClassName()))));
    }

    private static boolean isShipClass(String c) {
        if (c.startsWith("dev.ryanhcode.sable.")) return !c.startsWith("dev.ryanhcode.sable.companion.");
        return c.startsWith("dev.simulated_team.simulated.") || c.startsWith("dev.eriksonn.aeronautics.")
            || c.startsWith("org.valkyrienskies.") || c.startsWith("dev.silvergold.simulatedcoasters.");
    }
}
