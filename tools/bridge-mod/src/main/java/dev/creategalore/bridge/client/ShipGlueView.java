package dev.creategalore.bridge.client;

import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import dev.creategalore.bridge.ShipGlueCheck;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueItem;
import java.util.List;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Glue check, client side. While Super Glue or Honey Glue is in hand (or Engineer's Goggles are on and the crosshair
 * is on a Physics Assembler), the nearest Physics Assembler's ship is outlined: blue around every block that will go
 * aboard, red with a checkered face on every block inside the ship's glue that will stay behind. The server works both
 * out with the assembler's own search (see {@link ShipGlueCheck}) about once a second, so new glue shows up quickly.
 */
public final class ShipGlueView {
    private static final Object JOINED = new Object(), LOOSE = new Object(), PROBLEM = new Object();
    private static final int BLUE = 0x5AA9E6, RED = 0xE5484D, ORANGE = 0xF0A030;
    private static final int ASK_EVERY = 20, SAY_EVERY = 100;

    private static BlockPos target;
    private static ShipGlueCheck.Result shown;
    private static int ticks, saidAt = -SAY_EVERY;
    private static String lastSaid = "";

    private ShipGlueView() {
    }

    public static void onResult(ShipGlueCheck.Result result) {
        Minecraft.getInstance().execute(() -> {
            if (target == null || !target.equals(result.assembler())) return;
            Outliner outliner = Outliner.getInstance();
            if (shown == null || !shown.joined().equals(result.joined())) {
                if (result.joined().isEmpty()) outliner.remove(JOINED);
                else outliner.showCluster(JOINED, result.joined()).colored(BLUE).disableLineNormals().lineWidth(1 / 32f);
            }
            if (shown == null || !shown.loose().equals(result.loose())) {
                if (result.loose().isEmpty()) outliner.remove(LOOSE);
                else outliner.showCluster(LOOSE, result.loose()).colored(RED)
                        .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.CHECKERED).disableLineNormals().lineWidth(1 / 16f);
            }
            if (result.hasProblemPos()) {
                outliner.showCluster(PROBLEM, List.of(result.problemPos())).colored(ORANGE)
                        .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.CHECKERED).disableLineNormals().lineWidth(1 / 16f);
            } else {
                outliner.remove(PROBLEM);
            }
            shown = result;
            say();
        });
    }

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clear();
            return;
        }
        BlockPos looked = lookedAtAssembler(mc);
        boolean glue = isGlue(player.getMainHandItem()) || isGlue(player.getOffhandItem());
        if (!glue && !(looked != null && GogglesItem.isWearingGoggles(player))) {
            clear();
            return;
        }
        BlockPos next = looked != null ? looked : stillValid(mc, target) ? target : nearestAssembler(mc);
        if (next == null) {
            clear();
            return;
        }
        if (!next.equals(target)) {
            clear();
            target = next;
            ticks = 0;
        }
        if (ticks++ % ASK_EVERY == 0) PacketDistributor.sendToServer(new ShipGlueCheck.Request(target));
        if (shown != null) {
            Outliner outliner = Outliner.getInstance();
            outliner.keep(JOINED);
            outliner.keep(LOOSE);
            outliner.keep(PROBLEM);
            say();
        }
    }

    /** A short line above the hotbar: how many blocks go aboard and how many stay behind. */
    private static void say() {
        Minecraft mc = Minecraft.getInstance();
        if (shown == null || mc.player == null) return;
        Component line;
        if (!shown.problem().getString().isEmpty()) {
            line = Component.translatable("create_galore_bridge.glue_check.problem", shown.problem()).withColor(ORANGE);
        } else if (!shown.loose().isEmpty()) {
            line = Component.translatable("create_galore_bridge.glue_check.loose", count(shown), shown.loose().size()).withColor(RED);
        } else {
            line = Component.translatable("create_galore_bridge.glue_check.all", count(shown)).withColor(BLUE);
        }
        String text = line.getString();
        // Said when it changes, then again every few seconds while something stays behind or is wrong.
        boolean worry = !shown.loose().isEmpty() || !shown.problem().getString().isEmpty();
        if (text.equals(lastSaid) && !(worry && ticks - saidAt >= SAY_EVERY)) return;
        mc.player.displayClientMessage(line, true);
        lastSaid = text;
        saidAt = ticks;
    }

    private static Component count(ShipGlueCheck.Result result) {
        return result.joined().isEmpty() ? Component.literal(ShipGlueCheck.MAX_SENT + "+") : Component.literal(String.valueOf(result.joined().size()));
    }

    private static void clear() {
        if (target == null && shown == null) return;
        Outliner outliner = Outliner.getInstance();
        outliner.remove(JOINED);
        outliner.remove(LOOSE);
        outliner.remove(PROBLEM);
        target = null;
        shown = null;
        lastSaid = "";
    }

    private static boolean isGlue(ItemStack stack) {
        return stack.getItem() instanceof SuperGlueItem || stack.getItem() instanceof HoneyGlueItem;
    }

    private static BlockPos lookedAtAssembler(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        return mc.level.getBlockState(block.getBlockPos()).getBlock() instanceof PhysicsAssemblerBlock ? block.getBlockPos() : null;
    }

    private static boolean stillValid(Minecraft mc, BlockPos pos) {
        return pos != null && mc.level.getBlockState(pos).getBlock() instanceof PhysicsAssemblerBlock
                && mc.player.distanceToSqr(pos.getCenter()) <= 32 * 32;
    }

    /** The closest Physics Assembler still standing in the world (one on a ship lives elsewhere) within 32 blocks. */
    private static BlockPos nearestAssembler(Minecraft mc) {
        BlockPos best = null;
        double bestDistance = 32 * 32;
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof PhysicsAssemblerBlockEntity)) continue;
                    double d = mc.player.distanceToSqr(be.getBlockPos().getCenter());
                    if (d <= bestDistance) {
                        bestDistance = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        return best;
    }
}
