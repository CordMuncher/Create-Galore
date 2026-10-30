package dev.creategalore.bridge;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Pre-glue on the Schematicannon (a switch next to its placement settings): every block it prints is Super Glued to
 * each printed block it touches (by a face glue can hold, or by an edge, as ships count), as if glued by hand. The glue comes from the
 * cannon's input chests like any other material: one use per glue box the cannon makes. A box grows over the build as
 * the blocks land and never takes in a block that isn't part of the print (the ground, a tree, the cannon), so a build
 * on the ground comes away clean. Without glue the cannon pauses on Super Glue like on a missing block, even with Skip
 * Missing on, since a half-glued build would fall apart when it moves.
 * <p>
 * Boxes stay at most {@link #MAX_SIDE} a side: Create finds a glue box by where it stands (its bottom centre), up to
 * about 18 blocks from the pair it glues. A pair across two boxes gets a thin box of its own along that seam, which grows
 * like the others, so pistons, bearings and the Physics Assembler all carry the whole build.
 */
public final class SchematicannonGlue {
    public static final int MAX_SIDE = 16;

    private SchematicannonGlue() {
    }

    /** What the cannon mixin keeps: the switch, the glue paid for and not yet used, and this print's boxes. */
    public interface Cannon {
        boolean create_galore_bridge$preGlue();

        void create_galore_bridge$setPreGlue(boolean on);

        /** Uses taken from the chests and not yet spent on boxes; below zero when boxes were made in one go. */
        int create_galore_bridge$glueCharges();

        void create_galore_bridge$spendGlue();

        /** The build being printed, kept until the last shot lands (Create forgets it when the last shot leaves). */
        SchematicLevel create_galore_bridge$glueSchematic();

        List<Box> create_galore_bridge$glueBoxes();
    }

    /** One of this print's glue boxes, by its entity and its last known box. */
    public record Box(UUID id, AABB box) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            SuperGlueEntity.writeBoundingBox(tag, box);
            return tag;
        }

        static Box load(CompoundTag tag) {
            return new Box(tag.getUUID("Id"), SuperGlueEntity.readBoundingBox(tag));
        }
    }

    public static ListTag saveBoxes(List<Box> boxes) {
        ListTag list = new ListTag();
        for (Box box : boxes) list.add(box.save());
        return list;
    }

    public static void loadBoxes(ListTag list, List<Box> into) {
        into.clear();
        for (int i = 0; i < list.size(); i++) into.add(Box.load(list.getCompound(i)));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a client without the Bridge just has no switch.
            event.registrar("1").optional().playToServer(Toggle.TYPE, Toggle.CODEC, SchematicannonGlue::onToggle);
        });
    }

    private static void onToggle(Toggle payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // Only from the cannon's own screen, as Create checks its other settings.
        if (!(player.containerMenu instanceof SchematicannonMenu menu)) return;
        SchematicannonBlockEntity cannon = menu.contentHolder;
        if (cannon == null || !cannon.getBlockPos().equals(payload.pos()) || !(cannon instanceof Cannon glue)) return;
        glue.create_galore_bridge$setPreGlue(payload.on());
        cannon.setChanged();
        cannon.sendUpdate = true;
    }

    /** A shot landed: glue what it placed to the printed blocks around it. */
    public static void landed(SchematicannonBlockEntity cannon, LaunchedItem shot) {
        if (!(cannon.getLevel() instanceof ServerLevel level) || !(cannon instanceof Cannon glue)) return;
        SchematicLevel schematic = glue.create_galore_bridge$glueSchematic();
        if (schematic == null) return;
        if (shot instanceof LaunchedItem.ForBelt belt) {
            // A belt lands whole: glue each of its blocks.
            boolean start = belt.state.getValue(BeltBlock.PART) == BeltPart.START;
            BlockPos step = BeltBlock.nextSegmentPosition(belt.state, BlockPos.ZERO, start);
            for (int i = 0; i < belt.length; i++) glueAround(level, glue, schematic, belt.target.offset(step.multiply(i)));
        } else if (shot instanceof LaunchedItem.ForBlockState block && !block.state.isAir()) {
            glueAround(level, glue, schematic, block.target);
        }
    }

    private static void glueAround(ServerLevel level, Cannon glue, SchematicLevel schematic, BlockPos pos) {
        if (!printed(level, schematic, pos)) return;
        List<BlockPos> partners = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos other = pos.relative(d);
            if (!printed(level, schematic, other)) continue;
            // Create's own test for a face glue can hold, both ways, as its glue tool does.
            if (!SuperGlueEntity.isValidFace(level, pos, d) || !SuperGlueEntity.isValidFace(level, other, d.getOpposite())) continue;
            if (!glued(level, pos, other)) partners.add(other);
        }
        // Blocks touching at an edge: a ship takes those too (pistons and bearings don't, and ignore the extra glue).
        for (BlockPos other : edgeNeighbours(pos)) {
            if (printed(level, schematic, other) && !glued(level, pos, other)) partners.add(other);
        }
        if (partners.isEmpty()) return;
        // One box round the block and all its partners when that takes in nothing else, else a box per pair.
        AABB around = new AABB(pos);
        for (BlockPos other : partners) around = around.minmax(new AABB(other));
        if (clean(level, schematic, around, null)) {
            cover(level, glue, schematic, around);
            return;
        }
        for (BlockPos other : partners) {
            // A box made for an earlier pair may hold this one too.
            if (glued(level, pos, other)) continue;
            AABB pair = new AABB(pos).minmax(new AABB(other));
            // An edge pair's box also takes in the two blocks beside the edge: only when they are the build's or empty.
            if (clean(level, schematic, pair, null)) cover(level, glue, schematic, pair);
        }
    }

    private static List<BlockPos> edgeNeighbours(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(12);
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                list.add(pos.offset(a, b, 0));
                list.add(pos.offset(a, 0, b));
                list.add(pos.offset(0, a, b));
            }
        }
        return list;
    }

    /** One glue box holds both blocks, as Create needs for a pair to move together. */
    private static boolean glued(ServerLevel level, BlockPos a, BlockPos b) {
        Vec3 ca = Vec3.atCenterOf(a), cb = Vec3.atCenterOf(b);
        for (SuperGlueEntity glue : level.getEntitiesOfClass(SuperGlueEntity.class, new AABB(a).minmax(new AABB(b)).inflate(MAX_SIDE))) {
            if (glue.getBoundingBox().contains(ca) && glue.getBoundingBox().contains(cb)) return true;
        }
        return false;
    }

    /** Puts {@code need} inside one of this print's boxes, growing the one that grows least; else makes a box for one use. */
    private static void cover(ServerLevel level, Cannon glue, SchematicLevel schematic, AABB need) {
        List<Box> boxes = glue.create_galore_bridge$glueBoxes();
        Box best = null;
        AABB bestBox = null;
        double bestGrowth = Double.MAX_VALUE;
        for (Box box : boxes) {
            AABB grown = box.box().minmax(need);
            if (grown.getXsize() > MAX_SIDE || grown.getYsize() > MAX_SIDE || grown.getZsize() > MAX_SIDE) continue;
            double growth = volume(grown) - volume(box.box());
            if (growth >= bestGrowth) continue;
            if (!clean(level, schematic, grown, box.box())) continue;
            best = box;
            bestBox = grown;
            bestGrowth = growth;
            if (growth == 0) break;
        }
        if (best != null) {
            if (bestGrowth == 0) return;
            // A glue box can't be resized where players see it, so the grown box replaces it.
            if (level.getEntity(best.id()) instanceof SuperGlueEntity old && old.isAlive()) {
                old.discard();
                SuperGlueEntity grown = new SuperGlueEntity(level, bestBox);
                level.addFreshEntity(grown);
                boxes.set(boxes.indexOf(best), new Box(grown.getUUID(), bestBox));
                return;
            }
            // Gone (a player took it off, or its chunk is away): forget it and make a box instead.
            boxes.remove(best);
        }
        SuperGlueEntity made = new SuperGlueEntity(level, need);
        level.addFreshEntity(made);
        boxes.add(new Box(made.getUUID(), need));
        glue.create_galore_bridge$spendGlue();
    }

    /**
     * True when glue over {@code box} would hold only the print: every block whose centre is inside (and outside
     * {@code skip}, already known) is air, a fluid (glue never takes those), or the build's own block.
     */
    private static boolean clean(ServerLevel level, SchematicLevel schematic, AABB box, AABB skip) {
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        int x0 = (int) Math.floor(box.minX), y0 = (int) Math.floor(box.minY), z0 = (int) Math.floor(box.minZ);
        int x1 = (int) Math.ceil(box.maxX), y1 = (int) Math.ceil(box.maxY), z1 = (int) Math.ceil(box.maxZ);
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    cell.set(x, y, z);
                    if (skip != null && skip.contains(Vec3.atCenterOf(cell))) continue;
                    BlockState here = level.getBlockState(cell);
                    if (here.isAir() || here.getBlock() instanceof LiquidBlock) continue;
                    if (!printed(level, schematic, cell)) return false;
                }
            }
        }
        return true;
    }

    /** A block of the build stands there: the world has the schematic's block (shape and facing may have settled).
     * The schematic's bounds are its own, from its anchor; it reads the layer just under them as dirt, so ground there
     * must not count. */
    private static boolean printed(ServerLevel level, SchematicLevel schematic, BlockPos pos) {
        if (!schematic.getBounds().isInside(pos.subtract(schematic.anchor))) return false;
        BlockState here = level.getBlockState(pos);
        if (here.isAir() || here.getBlock() instanceof LiquidBlock) return false;
        BlockState wanted = schematic.getBlockState(pos);
        return !wanted.isAir() && wanted.getBlock() != Blocks.STRUCTURE_VOID && wanted.getBlock() == here.getBlock();
    }

    private static double volume(AABB box) {
        return box.getXsize() * box.getYsize() * box.getZsize();
    }

    /** Client to server: turn pre-glue on or off for the cannon whose screen is open. */
    public record Toggle(BlockPos pos, boolean on) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, "schematicannon_glue"));
        static final StreamCodec<FriendlyByteBuf, Toggle> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Toggle::pos, ByteBufCodecs.BOOL, Toggle::on, Toggle::new);

        @Override
        public Type<Toggle> type() {
            return TYPE;
        }
    }
}
