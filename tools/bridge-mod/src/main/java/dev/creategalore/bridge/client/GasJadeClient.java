package dev.creategalore.bridge.client;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import dev.creategalore.bridge.Exhaust;
import dev.creategalore.bridge.FilteredExhaustBlock;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade names the smog and filtered exhaust you look at, with how thick it is. Gas has no outline, so the game's own
 * aim passes straight through it (and keeps doing so: clicks still reach the block behind); only Jade's look-up
 * stops at the first gas cell in front of the block you aim at. Crouch to look through it.
 */
final class GasJadeClient {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "gas");

    private GasJadeClient() {}

    static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Details.INSTANCE, Pollutant.class);
        registration.registerBlockComponent(Details.INSTANCE, FilteredExhaustBlock.class);
        registration.addRayTraceCallback((hit, accessor, original) -> aimAtGas(registration, hit, accessor));
    }

    private static Accessor<?> aimAtGas(IWailaClientRegistration registration, HitResult hit, Accessor<?> accessor) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        // Crouching looks through the gas, so a machine in a smoggy room can still be read.
        if (player == null || level == null || hit == null || player.isSpectator() || player.isCrouching()) return accessor;
        Vec3 eye = player.getEyePosition();
        Vec3 end = hit.getType() == HitResult.Type.MISS
            ? eye.add(player.getViewVector(1f).scale(player.blockInteractionRange()))
            : hit.getLocation();
        BlockPos target = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
        BlockPos head = BlockPos.containing(eye);
        // The first gas cell in front of what you aim at, not counting the one your head is in.
        BlockPos gas = BlockGetter.traverseBlocks(eye, end, level, (lvl, pos) -> {
            if (pos.equals(target)) return Optional.<BlockPos>empty();
            return !pos.equals(head) && isGas(lvl.getBlockState(pos)) ? Optional.of(pos.immutable()) : null;
        }, lvl -> Optional.<BlockPos>empty()).orElse(null);
        if (gas == null) return accessor;
        Vec3 point = new AABB(gas).clip(eye, end).orElse(Vec3.atCenterOf(gas));
        Direction face = Direction.getNearest(eye.x - point.x, eye.y - point.y, eye.z - point.z);
        BlockAccessor.Builder builder = registration.blockAccessor()
            .level(level)
            .player(player)
            .hit(new BlockHitResult(point, face, gas, false))
            .blockState(level.getBlockState(gas));
        if (accessor != null) builder.serverConnected(accessor.isServerConnected()).showDetails(accessor.showDetails());
        return builder.build();
    }

    private static boolean isGas(BlockState state) {
        return state.getBlock() instanceof Pollutant || Exhaust.isExhaust(state);
    }

    private enum Details implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (state.getBlock() instanceof Pollutant<?> pollutant) {
                int units = pollutant.getCarriedPollutionAmount(state);
                String density = switch (units) {
                    case 1 -> "light";
                    case 2 -> "medium";
                    default -> "heavy";
                };
                tooltip.add(Component.translatable("create_galore_bridge.jade.gas." + density, units).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("create_galore_bridge.jade.exhaust").withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
