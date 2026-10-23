package dev.creategalore.bridge.wear;

import dev.creategalore.bridge.CreateGaloreBridge;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * One wear screen for every accessory mod. Curios, Accessories and The Aether each had their own accessory screen
 * with their own slots, so a new accessory meant opening each screen to find where it goes. The wear screen has one
 * set of slots for everything a person can wear (see {@link WearGroups}) and puts each item into its own mod's real
 * slot behind the scenes ({@link Worn}), so the mods work exactly as before. The three old screens and their buttons
 * are gone; anything that still opens one gets the wear screen instead.
 */
public final class Wear {
    private Wear() {
    }

    /** The old screens' menus (by class name, so nothing breaks when one of the mods is missing). */
    private static final Set<String> OLD_MENUS = Set.of(
            "top.theillusivec4.curios.common.inventory.container.CuriosContainer",
            "com.aetherteam.aether.inventory.menu.AetherAccessoriesMenu");
    private static final String ACCESSORIES_MENUS = "io.wispforest.accessories.menu.";

    public static void register(IEventBus modBus) {
        if (!Worn.HAS_CURIOS && !Worn.HAS_ACCESSORIES) return;
        WearData.register(modBus);
        WearMenu.register(modBus);
        WearRules.register();
        WearMigration.register();
        NeoForge.EVENT_BUS.addListener(Wear::onCommands);
        NeoForge.EVENT_BUS.addListener(Wear::onMenuOpen);
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1").optional();
            registrar.playToServer(Open.TYPE, Open.CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player && player.isAlive()) WearMenu.open(player);
            });
        });
    }

    private static final ThreadLocal<int[]> RECHECKING = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Curios and Accessories re-check every worn item when a player joins (and Accessories again after /reload), and
     * throw out anything their equip check refuses: into the inventory, or onto the ground when it is full. The wear
     * screen's limits must not refuse there, or items past a limit would be dropped; {@link WearMigration} hands those
     * back instead, with the stash for a full inventory. Called around those re-checks by mixins.
     */
    public static void rechecking(boolean start) {
        RECHECKING.get()[0] += start ? 1 : -1;
    }

    static boolean rechecking() {
        return RECHECKING.get()[0] > 0;
    }

    /** Client to server: open the wear screen. */
    public record Open() implements CustomPacketPayload {
        public static final Open INSTANCE = new Open();
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, "wear_open"));
        static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** One of the old screens opened anyway (a key a player bound again, another mod's button): the wear screen instead. */
    private static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String name = event.getContainer().getClass().getName();
        if (!OLD_MENUS.contains(name) && !name.startsWith(ACCESSORIES_MENUS)) return;
        player.server.execute(() -> {
            if (player.containerMenu == event.getContainer()) {
                player.closeContainer();
                WearMenu.open(player);
            }
        });
    }

    private static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wear")
                .then(Commands.literal("claim").executes(ctx -> WearMigration.claim(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("show").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            WearLayout layout = new WearLayout(player, WearLayout.shownGroups(player));
                            layout.refresh();
                            StringBuilder out = new StringBuilder();
                            for (int i = 0; i < layout.size; i++) {
                                Worn.Loc loc = layout.bound[i];
                                if (loc == null) continue;
                                out.append(layout.slotKey(i)).append(" -> ").append(loc.key()).append(' ')
                                        .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Worn.get(player, loc).getItem()))
                                        .append(Worn.shown(player, loc) ? " shown" : " hidden").append('\n');
                            }
                            for (Worn.Loc loc : layout.overflow) out.append("over ").append(loc.key()).append('\n');
                            out.append("stash ").append(WearData.stash(player).size());
                            String text = out.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        })))
                .then(Commands.literal("report").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    var src = ctx.getSource();
                    try {
                        var player = src.getPlayer() != null ? src.getPlayer() : FakePlayerFactory.getMinecraft(src.getServer().overworld());
                        var path = WearReport.write(player);
                        src.sendSuccess(() -> Component.literal("Wrote " + path), false);
                        return 1;
                    } catch (Exception e) {
                        src.sendFailure(Component.literal(e.toString()));
                        return 0;
                    }
                })));
    }
}
