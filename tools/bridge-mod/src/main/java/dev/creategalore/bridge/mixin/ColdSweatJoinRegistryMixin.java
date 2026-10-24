package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.client.event.ClientJoinSetup;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cold Sweat 2.4.4 rebuilds its temperature modifier registry on the client whenever the player joins a world: it
 * clears the static map, then fills it again. In single player the built-in server shares that same map, and it is
 * ticking the player who just joined while the client does this. If the server saves or syncs the player's modifiers
 * in that gap (Cold Sweat: Altitude does it on the player's first tick), no modifier has a registered id, and the
 * server thread crashes with "Ticking player" (NullPointerException in AbstractTempCap.serializeModifiers). Modifiers
 * loaded from the save in the gap are dropped instead. The join step also adds a second copy of every Chameleon food
 * to a shared set the server reads. The built-in server already built both when it started, in this same game, so a
 * client with a built-in server skips the rebuild. A client joining another computer's server still does it.
 */
@Mixin(value = ClientJoinSetup.class, remap = false)
public abstract class ColdSweatJoinRegistryMixin {
    @WrapWithCondition(
        method = "onJoin",
        at = @At(value = "INVOKE", target = "Lcom/momosoftworks/coldsweat/core/init/TempModifierInit;buildModifierRegistries()V"))
    private static boolean create_galore_bridge$keepServerModifiers() {
        return !Minecraft.getInstance().hasSingleplayerServer();
    }

    @WrapOperation(
        method = "onJoin",
        at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private static Event create_galore_bridge$keepServerEdibles(IEventBus bus, Event event, Operation<Event> original) {
        return Minecraft.getInstance().hasSingleplayerServer() ? event : original.call(bus, event);
    }
}
