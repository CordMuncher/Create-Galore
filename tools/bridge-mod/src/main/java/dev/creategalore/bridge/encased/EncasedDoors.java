package dev.creategalore.bridge.encased;

import dev.creategalore.bridge.CreateGaloreBridge;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Doors and trapdoors cased in Create casing. Right-click any door or trapdoor (every kind, any mod) with Andesite,
 * Brass or Copper Casing and it becomes a cased one: shut, it stops smog like a wall; open, it lets air through like
 * any other door. A cased door only opens with redstone (buttons, levers, plates, circuits), never by hand, and mobs
 * and villagers can't open or break it. A wrench takes the casing back off and gives the old door back; sneaking with
 * the wrench picks the whole thing up. Breaking it drops both the casing and the door it was made from.
 *
 * The cased block is its own block that remembers the door it covers (block entity), so no other mod's door changes.
 */
public final class EncasedDoors {
    private EncasedDoors() {}

    public enum Casing {
        ANDESITE("andesite", MapColor.STONE, SoundType.WOOD),
        BRASS("brass", MapColor.TERRACOTTA_BROWN, SoundType.WOOD),
        COPPER("copper", MapColor.COLOR_LIGHT_GRAY, SoundType.COPPER);

        public final String id;
        final MapColor color;
        final SoundType sound;

        Casing(String id, MapColor color, SoundType sound) {
            this.id = id;
            this.color = color;
            this.sound = sound;
        }

        /** Create's casing block of this kind ({@code create:andesite_casing}...). */
        public Block block() {
            return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id + "_casing"));
        }

        public ItemStack stack() {
            return new ItemStack(block().asItem());
        }

        static Casing of(ItemStack stack) {
            if (stack.isEmpty()) return null;
            Item item = stack.getItem();
            for (Casing casing : values()) {
                Block block = casing.block();
                if (block != Blocks.AIR && block.asItem() == item) return casing;
            }
            return null;
        }
    }

    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateGaloreBridge.ID);

    public static final Map<Casing, DeferredBlock<EncasedDoorBlock>> DOORS = new EnumMap<>(Casing.class);
    public static final Map<Casing, DeferredBlock<EncasedTrapdoorBlock>> TRAPDOORS = new EnumMap<>(Casing.class);

    static {
        for (Casing casing : Casing.values()) {
            DOORS.put(casing, CreateGaloreBridge.BLOCKS.register(casing.id + "_encased_door",
                    () -> new EncasedDoorBlock(casing, props(casing).pushReaction(PushReaction.DESTROY))));
            TRAPDOORS.put(casing, CreateGaloreBridge.BLOCKS.register(casing.id + "_encased_trapdoor",
                    () -> new EncasedTrapdoorBlock(casing, props(casing).isValidSpawn((state, level, pos, type) -> false))));
        }
    }

    public static final Supplier<BlockEntityType<EncasedDoorBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("encased_door",
            () -> BlockEntityType.Builder.of(EncasedDoorBlockEntity::new, java.util.stream.Stream.concat(
                    DOORS.values().stream(), TRAPDOORS.values().stream()).map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    private static BlockBehaviour.Properties props(Casing casing) {
        return BlockBehaviour.Properties.of().mapColor(casing.color).strength(3.0F, 6.0F).sound(casing.sound).noOcclusion();
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(EncasedDoors::onRightClick);
    }

    /** A cased door or trapdoor that is shut: smog can't get through it from any side. */
    public static boolean isSealed(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof EncasedDoorBlock) return !state.getValue(DoorBlock.OPEN);
        if (block instanceof EncasedTrapdoorBlock) return !state.getValue(TrapDoorBlock.OPEN);
        return false;
    }

    static boolean isEncased(Block block) {
        return block instanceof EncasedDoorBlock || block instanceof EncasedTrapdoorBlock;
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        // Sneaking places the casing as a block, as usual.
        if (player.isSecondaryUseActive() || !player.mayBuild()) return;
        Casing casing = Casing.of(event.getItemStack());
        if (casing == null) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (isEncased(block) || !(block instanceof DoorBlock || block instanceof TrapDoorBlock)) return;
        if (!level.mayInteract(player, pos)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;
        boolean done = block instanceof DoorBlock ? encaseDoor(level, pos, state, casing) : encaseTrapdoor(level, pos, state, casing);
        if (!done) return;
        if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);
        SoundType sound = casing.block().defaultBlockState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    private static boolean encaseDoor(Level level, BlockPos pos, BlockState state, Casing casing) {
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upper = lower.above();
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(state.getBlock()) || !level.getBlockState(upper).is(state.getBlock())) return false;
        // A cased door follows redstone only, so it starts the way its signal says (shut when there is none).
        boolean powered = level.hasNeighborSignal(lower) || level.hasNeighborSignal(upper);
        BlockState cased = DOORS.get(casing).get().defaultBlockState();
        cased = copy(lowerState, cased, DoorBlock.FACING);
        cased = copy(lowerState, cased, DoorBlock.HINGE);
        cased = cased.setValue(DoorBlock.OPEN, powered).setValue(DoorBlock.POWERED, powered);
        BlockState original = lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        swapDoor(level, lower, cased, original);
        return true;
    }

    private static boolean encaseTrapdoor(Level level, BlockPos pos, BlockState state, Casing casing) {
        boolean powered = level.hasNeighborSignal(pos);
        BlockState cased = TRAPDOORS.get(casing).get().defaultBlockState();
        cased = copy(state, cased, TrapDoorBlock.FACING);
        cased = copy(state, cased, TrapDoorBlock.HALF);
        cased = copy(state, cased, TrapDoorBlock.WATERLOGGED);
        cased = cased.setValue(TrapDoorBlock.OPEN, powered).setValue(TrapDoorBlock.POWERED, powered);
        swapTrapdoor(level, pos, cased, state);
        return true;
    }

    /** Takes the casing off: the door it was made from comes back the way the cased one stands now. */
    static void uncaseDoor(Level level, BlockPos pos, BlockState state) {
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lowerState = level.getBlockState(lower);
        if (!(lowerState.getBlock() instanceof EncasedDoorBlock)) return;
        BlockState door = original(level, lower, Blocks.OAK_DOOR.defaultBlockState());
        door = copy(lowerState, door, DoorBlock.FACING);
        door = copy(lowerState, door, DoorBlock.HINGE);
        door = copy(lowerState, door, DoorBlock.OPEN);
        door = copy(lowerState, door, DoorBlock.POWERED);
        swapDoor(level, lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), null);
    }

    static void uncaseTrapdoor(Level level, BlockPos pos, BlockState state) {
        BlockState trapdoor = original(level, pos, Blocks.OAK_TRAPDOOR.defaultBlockState());
        trapdoor = copy(state, trapdoor, TrapDoorBlock.FACING);
        trapdoor = copy(state, trapdoor, TrapDoorBlock.HALF);
        trapdoor = copy(state, trapdoor, TrapDoorBlock.OPEN);
        trapdoor = copy(state, trapdoor, TrapDoorBlock.POWERED);
        trapdoor = copy(state, trapdoor, TrapDoorBlock.WATERLOGGED);
        swapTrapdoor(level, pos, trapdoor, null);
    }

    /** The door a cased block was made from, or {@code fallback} when it was never recorded (placed by command). */
    static BlockState original(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState fallback) {
        BlockEntity be = level.getBlockEntity(pos);
        BlockState original = be instanceof EncasedDoorBlockEntity cased ? cased.original() : null;
        return original == null || original.isAir() ? fallback : original;
    }

    // Both halves are swapped without shape updates in between, or the half left behind would see a stranger next to
    // it and break off (dropping a door).
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static void swapDoor(Level level, BlockPos lower, BlockState lowerState, BlockState original) {
        BlockPos upper = lower.above();
        level.setBlock(lower, lowerState, QUIET);
        level.setBlock(upper, lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), QUIET);
        if (original != null) {
            remember(level, lower, original);
            remember(level, upper, original);
        }
        level.blockUpdated(lower, lowerState.getBlock());
        level.blockUpdated(upper, lowerState.getBlock());
    }

    private static void swapTrapdoor(Level level, BlockPos pos, BlockState state, BlockState original) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (original != null) remember(level, pos, original);
    }

    private static void remember(Level level, BlockPos pos, BlockState original) {
        if (level.getBlockEntity(pos) instanceof EncasedDoorBlockEntity cased) cased.setOriginal(original);
    }

    static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return from.hasProperty(property) && to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }
}
