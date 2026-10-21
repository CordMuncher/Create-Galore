package dev.creategalore.fishing.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.cast.RodLift;
import dev.creategalore.fishing.net.DragAdjust;
import dev.creategalore.fishing.net.LiftInput;
import dev.creategalore.fishing.tackle.RodItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Rod controls from the keyboard and mouse.
 * <ul>
 *   <li>Lifting the rod: hold attack (left click) while your line is out. It strikes a bite, pumps a hooked fish in
 *       and twitches a lure; let go and the rod comes back down. While the line is out attack does nothing else
 *       (no punching, no mining), and it works while use (reeling) is held too.</li>
 *   <li>Drag, any time a rod is in hand: the Drag + / Drag - keys (default {@code ]} and {@code [}, rebindable under
 *       Create Galore Fishing), and the scroll wheel while a line is out, or while sneaking (otherwise the wheel still
 *       picks hotbar slots).</li>
 * </ul>
 */
public final class FishingInput {
    private static final String CATEGORY = "key.categories.create_galore_fishing";
    static final KeyMapping DRAG_UP = new KeyMapping("key.create_galore_fishing.drag_up", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    static final KeyMapping DRAG_DOWN = new KeyMapping("key.create_galore_fishing.drag_down", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);

    private FishingInput() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(DRAG_UP);
            event.register(DRAG_DOWN);
        });
        NeoForge.EVENT_BUS.addListener(FishingInput::onScroll);
        NeoForge.EVENT_BUS.addListener(FishingInput::onAttack);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onTick());
    }

    static boolean holdingRod(Player player) {
        return player.getMainHandItem().getItem() instanceof RodItem || player.getOffhandItem().getItem() instanceof RodItem;
    }

    /** Whether {@code player}'s own line is out (so attack lifts the rod instead). */
    static boolean lineOut(Player player) {
        return player.fishing instanceof CastHook hook && !hook.isRemoved() && hook.getPlayerOwner() == player
                && holdingRod(player);
    }

    /** With the line out, attack is the lift: it mustn't also punch or mine. */
    private static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !lineOut(mc.player)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** What we last told the server about lifting. */
    private static boolean sentLifting;

    private static void lift(Minecraft mc) {
        boolean lifting = mc.player != null && mc.screen == null && mc.options.keyAttack.isDown() && lineOut(mc.player);
        if (mc.player != null) RodLift.set(mc.player, lifting);
        if (lifting != sentLifting && mc.getConnection() != null) {
            sentLifting = lifting;
            PacketDistributor.sendToServer(new LiftInput(lifting));
        }
    }

    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null || !holdingRod(player)) return;
        if (!(player.fishing instanceof CastHook) && !player.isShiftKeyDown()) return;
        int steps = (int) Math.signum(event.getScrollDeltaY());
        if (steps == 0) return;
        PacketDistributor.sendToServer(new DragAdjust(steps));
        event.setCanceled(true);
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        lift(mc);
        if (mc.player == null || !holdingRod(mc.player)) {
            while (DRAG_UP.consumeClick()) { }
            while (DRAG_DOWN.consumeClick()) { }
            return;
        }
        while (DRAG_UP.consumeClick()) PacketDistributor.sendToServer(new DragAdjust(1));
        while (DRAG_DOWN.consumeClick()) PacketDistributor.sendToServer(new DragAdjust(-1));
    }
}
