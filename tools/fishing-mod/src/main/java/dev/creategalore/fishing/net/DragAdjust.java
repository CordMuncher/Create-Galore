package dev.creategalore.fishing.net;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: turn the drag knob of the rod in hand by {@code steps} clicks (one scroll notch or key press is
 * one click). Works any time the rod is held: before a cast, while waiting, mid-fight. The server clamps the knob to
 * the reel's range and ignores more than ten changes a second.
 */
public record DragAdjust(int steps) implements CustomPacketPayload {
    public static final Type<DragAdjust> TYPE = new Type<>(CreateGaloreFishing.id("drag"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DragAdjust> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, DragAdjust::steps, DragAdjust::new);

    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    @Override
    public Type<DragAdjust> type() {
        return TYPE;
    }

    public static void handle(DragAdjust payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < 2) return;
        LAST.put(player.getUUID(), now);
        int steps = Math.max(-4, Math.min(4, payload.steps()));
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack rod = player.getItemInHand(hand);
            if (!(rod.getItem() instanceof RodItem)) continue;
            RodStats stats = RodStats.of(rod).orElse(null);
            if (stats == null || stats.reel().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.create_galore_fishing.no_drag")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            int step = Math.max(0, Math.min(stats.dragSteps(), stats.dragStep() + steps));
            rod.set(FishingRegistry.DRAG_STEP.get(), step);
            RodStats after = RodStats.of(rod).orElseThrow();
            boolean over = after.drag() > after.breakingLoad();
            player.displayClientMessage(Component.translatable("message.create_galore_fishing.drag",
                    RodStats.kg(after.drag()), RodStats.kg(after.reel().get().maxDrag()), step, after.dragSteps())
                    .withStyle(over ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS, 0.2f, 1.8f + 0.02f * step);
            return;
        }
    }
}
