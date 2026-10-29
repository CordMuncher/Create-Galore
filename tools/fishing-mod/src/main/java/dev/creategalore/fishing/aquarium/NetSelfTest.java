package dev.creategalore.fishing.aquarium;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /cgfish selftest net}: Hybrid Aquatic's Creature Net catches starfish, crabs and fish of every kind (the
 * fishing mod's own too), and a tank takes what it holds: a starfish or crab as itself (colours and all, back out the
 * same with an empty net), a fishing fish as its fish item with its length; a mob that can't live in water stays in
 * the net. Lists any water mob in the game the net still can't catch.
 */
public final class NetSelfTest {
    private NetSelfTest() {
    }

    public static void register() {
        SelfTest.suite("net", NetSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        Item net = BuiltInRegistries.ITEM.get(AquariumCreatures.NET);
        if (net == Items.AIR) {
            r.info("Hybrid Aquatic's Creature Net isn't loaded, skipped");
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos pool = new BlockPos(20, level.getMaxBuildHeight() - 12, 20);
        BlockPos tankAt = pool.offset(4, 0, 0);
        List<BlockPos> placed = new ArrayList<>();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack held = player.getMainHandItem();
        try {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                set(level, pool.offset(x, -1, z), Blocks.GLASS, placed);
                set(level, pool.offset(x, 0, z), Blocks.WATER, placed);
            }
            uncaught(level, r);
            ItemStack starfish = netted(level, player, net, pool, "hybrid_aquatic:starfish", r);
            ItemStack crab = netted(level, player, net, pool, "hybrid_aquatic:fiddler_crab", r);
            ItemStack bass = netted(level, player, net, pool, "tide:largemouth_bass", r);
            ItemStack clownfish = netted(level, player, net, pool, "hybrid_aquatic:clownfish", r);
            ItemStack otter = netted(level, player, net, pool, "hybrid_aquatic:otter", r);

            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                level.setBlock(tankAt.offset(x, 0, z), FishingRegistry.AQUARIUM_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
                placed.add(tankAt.offset(x, 0, z));
                AquariumTanks.form(level, tankAt.offset(x, 0, z));
            }
            AquariumBlockEntity tank = level.getBlockEntity(tankAt) instanceof AquariumBlockEntity be ? be.tank() : null;
            if (!r.check(tank != null && tank.volume() == 6, "six tank blocks are a 3x1x2 tank")) return;
            tank.fill(6000, false);

            if (!starfish.isEmpty()) {
                int colour = AquariumCreatures.stored(starfish).orElseThrow().getInt("Starfish_Color");
                use(tank, player, starfish);
                ItemStack back = player.getMainHandItem();
                r.check(tank.fish().size() == 1 && AquariumCreatures.isCreature(tank.fish().get(0))
                                && AquariumCreatures.type(tank.fish().get(0)).map(t -> t == type("hybrid_aquatic:starfish")).orElse(false),
                        "the netted starfish goes into the tank as a starfish");
                r.check(AquariumCreatures.isNet(back) && !AquariumCreatures.isCreature(back), "and the net comes back empty");
                r.check(AquariumCreatures.isCreature(tank.fish().get(0)) && tank.fish().get(0).get(TideDataComponents.FISH_LENGTH) == null,
                        "the creature's net isn't marked as a fish");
                r.info("starfish takes " + String.format(java.util.Locale.ROOT, "%.0f cm", AquariumBlockEntity.cm(tank.fish().get(0))));

                CompoundTag saved = tank.saveWithFullMetadata(level.registryAccess());
                var loaded = BlockEntity.loadStatic(tankAt, level.getBlockState(tankAt), saved, level.registryAccess());
                r.check(loaded instanceof AquariumBlockEntity copy && copy.fish().size() == 1 && AquariumCreatures.isCreature(copy.fish().get(0)),
                        "the starfish survives saving and loading the tank");

                use(tank, player, back);
                ItemStack out = player.getMainHandItem();
                r.check(tank.fish().isEmpty() && AquariumCreatures.stored(out).map(m -> m.getInt("Starfish_Color") == colour).orElse(false),
                        "an empty net takes the same starfish back out, colours and all");
                use(tank, player, out);
            }
            if (!crab.isEmpty()) {
                use(tank, player, crab);
                r.check(tank.fish().stream().anyMatch(f -> AquariumCreatures.type(f).map(t -> t == type("hybrid_aquatic:fiddler_crab")).orElse(false)),
                        "a netted crab goes in too");
            }
            if (!clownfish.isEmpty()) {
                use(tank, player, clownfish);
                r.check(tank.fish().stream().anyMatch(f -> AquariumCreatures.type(f).map(t -> t == type("hybrid_aquatic:clownfish")).orElse(false)),
                        "and a netted clownfish, as itself (it keeps its kind and colours)");
            }
            if (!bass.isEmpty()) {
                double cm = AquariumCreatures.stored(bass).orElseThrow().getDouble("FishLength");
                use(tank, player, bass);
                Item bassItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tide:largemouth_bass"));
                r.check(tank.fish().stream().anyMatch(f -> f.is(bassItem) && Math.abs(AquariumBlockEntity.cm(f) - cm) < 0.01),
                        "a netted fishing fish goes in as its fish item, its length kept (" + String.format(java.util.Locale.ROOT, "%.1f cm", cm) + ")");
            }
            if (!otter.isEmpty()) {
                int before = tank.fish().size();
                use(tank, player, otter);
                r.check(tank.fish().size() == before && AquariumCreatures.isCreature(player.getMainHandItem()),
                        "an otter can't live in a tank: it stays in the net");
            }

            int inside = tank.fish().size();
            BlockPos wall = tankAt.offset(1, 0, 0);
            level.removeBlock(wall, false);
            placed.remove(wall);
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(wall).inflate(1.5));
            long nets = dropped.stream().filter(e -> AquariumCreatures.isCreature(e.getItem())).count();
            r.check(inside > 0 && dropped.size() == inside && nets == inside - (bass.isEmpty() ? 0 : 1),
                    "breaking the tank spills the creatures in their nets (" + nets + " nets, " + dropped.size() + " items)");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            for (BlockPos p : placed) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.getEntitiesOfClass(Entity.class, new AABB(pool).inflate(8), e -> !(e instanceof FakePlayer)).forEach(Entity::discard);
        }
    }

    private static EntityType<?> type(String id) {
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
    }

    private static void set(ServerLevel level, BlockPos pos, Block block, List<BlockPos> placed) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        placed.add(pos);
    }

    /** Right-clicks the tank with {@code stack}; what's in the hand afterwards is the result. */
    private static void use(AquariumBlockEntity tank, FakePlayer player, ItemStack stack) {
        // A copy: using an item uses up the stack in the hand.
        ItemStack hand = stack.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        AquariumUse.useItem(tank, hand, player, InteractionHand.MAIN_HAND);
    }

    /** Spawns {@code id} in the pool and right-clicks it with an empty net; returns the full net (empty if not caught). */
    private static ItemStack netted(ServerLevel level, FakePlayer player, Item net, BlockPos pool, String id, SelfTest.Report r) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id));
        if (type.isEmpty()) {
            r.info(id + " isn't loaded, skipped");
            return ItemStack.EMPTY;
        }
        Entity mob = type.get().create(level);
        mob.moveTo(pool.getX() + 0.5, pool.getY() + 0.1, pool.getZ() + 0.5, 0, 0);
        level.addFreshEntity(mob);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net));
        player.interactOn(mob, InteractionHand.MAIN_HAND);
        ItemStack out = player.getMainHandItem();
        boolean caught = AquariumCreatures.type(out).map(t -> t == type.get()).orElse(false) && mob.isRemoved();
        r.check(caught, "the Creature Net catches " + id);
        if (!mob.isRemoved()) mob.discard();
        return caught ? out.copy() : ItemStack.EMPTY;
    }

    /** Water mobs the net can't catch (information: the pack decides which mobs a net takes). */
    private static void uncaught(ServerLevel level, SelfTest.Report r) {
        List<String> missing = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            MobCategory c = t.getCategory();
            boolean water = c == MobCategory.WATER_AMBIENT || c == MobCategory.WATER_CREATURE || c == MobCategory.UNDERGROUND_WATER_CREATURE
                    || c == MobCategory.AXOLOTLS;
            if (water && !t.is(AquariumCreatures.NET_CATCHABLE)) missing.add(EntityType.getKey(t).toString());
        }
        r.info("water mobs the net can't catch (" + missing.size() + "): " + String.join(", ", missing));
    }
}
