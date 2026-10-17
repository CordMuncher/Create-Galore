package dev.creategalore.bridge;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Finds the nearest lair of one boss on the road to the Stars. Right-click: the coordinates go to chat and the
 * needle points there from then on, like a lodestone compass. Recipes live in the pack datapack.
 */
public class BossCompassItem extends Item {
    /** Same reach as /locate: 100 chunks, 1600 blocks. */
    private static final int SEARCH_CHUNKS = 100;

    private final ResourceKey<Structure> structure;
    private final ResourceKey<Level> dimension;

    public BossCompassItem(Properties properties, String structure, String dimension) {
        super(properties);
        this.structure = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structure));
        this.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
    }

    private Component structureName() {
        return Component.translatable("structure." + structure.location().getNamespace() + "." + structure.location().getPath());
    }

    private Component dimensionName() {
        return Component.translatable("dimension." + dimension.location().getNamespace() + "." + dimension.location().getPath());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        if (!server.dimension().equals(dimension)) {
            player.displayClientMessage(Component.translatable("item.create_galore_bridge.boss_compass.wrong_dimension",
                    dimensionName()).withStyle(ChatFormatting.GRAY), false);
            return InteractionResultHolder.consume(stack);
        }
        Optional<Holder.Reference<Structure>> holder = server.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(structure);
        Pair<BlockPos, Holder<Structure>> found = holder.isEmpty() ? null : server.getChunkSource().getGenerator()
                .findNearestMapStructure(server, HolderSet.direct(holder.get()), player.blockPosition(), SEARCH_CHUNKS, false);
        if (found == null) {
            player.displayClientMessage(Component.translatable("item.create_galore_bridge.boss_compass.none",
                    structureName(), SEARCH_CHUNKS * 16).withStyle(ChatFormatting.GRAY), false);
            return InteractionResultHolder.consume(stack);
        }
        BlockPos pos = found.getFirst();
        int distance = (int) Math.round(Math.sqrt(Math.pow(pos.getX() - player.getX(), 2) + Math.pow(pos.getZ() - player.getZ(), 2)));
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(dimension, pos)), false));
        player.displayClientMessage(Component.translatable("item.create_galore_bridge.boss_compass.found",
                structureName().copy().withStyle(ChatFormatting.GOLD),
                Component.literal(pos.getX() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN), distance), false);
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_galore_bridge.boss_compass.tooltip", structureName(), dimensionName())
                .withStyle(ChatFormatting.GRAY));
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null && tracker.target().isPresent()) {
            BlockPos pos = tracker.target().get().pos();
            tooltip.add(Component.translatable("item.create_galore_bridge.boss_compass.target", pos.getX() + ", " + pos.getZ())
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
