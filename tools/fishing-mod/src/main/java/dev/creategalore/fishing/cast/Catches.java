package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.SizeClass;import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.TideCriteriaTriggers;
import dev.creategalore.fishing.tide.data.TideLootTables;
import dev.creategalore.fishing.tide.data.fishing.CatchTables;
import dev.creategalore.fishing.tide.data.fishing.CrateData;
import dev.creategalore.fishing.tide.data.fishing.FishingLootData;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.data.loot.LootTableRef;
import dev.creategalore.fishing.tide.registries.entities.misc.LootCrateEntity;
import dev.creategalore.fishing.tide.util.TideUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

/**
 * Turning a landed fight into items and rewards, the same way for players and reel drives:
 * the fish item (with its {@link CatchInfo}), vanilla's fishing advancement trigger and stat, NeoForge's
 * {@link ItemFishedEvent} (Aquaculture and other mods listen to it), and XP.
 *
 * <p>XP: a base by rarity (common 2, uncommon 4, rare 8, very rare 15, legendary 40), times the fish's size against a
 * typical one of its species (0.5-3), times 1.5 for a trophy or 2 for a unique, times the fight's difficulty
 * (0.5-2.5, see {@link Fight#difficulty}). Reel drives bank half of it.
 */
public final class Catches {
    private Catches() {
    }

    private static final ResourceLocation AQUACULTURE_WEIGHT = ResourceLocation.fromNamespaceAndPath("aquaculture", "fish_weight");

    /** The item for a landed fish. */
    public static ItemStack fishItem(Species species, BiteSampler.Bite bite, String angler, long day) {
        ItemStack fish = new ItemStack(species.item());
        CatchInfo info = CatchInfo.of(bite.size(), bite.lengthCm(), bite.kg(), angler, day);
        fish.set(FishingRegistry.CATCH.get(), info);
        if (info.exact()) fish.set(DataComponents.MAX_STACK_SIZE, 1);
        // Trophies and uniques show it in their name's colour, as rare items do.
        if (bite.size() == SizeClass.TROPHY) fish.set(DataComponents.RARITY, Rarity.RARE);
        if (bite.size() == SizeClass.UNIQUE) fish.set(DataComponents.RARITY, Rarity.EPIC);
        setAquacultureWeight(fish, species, bite.kg());
        return fish;
    }

    /** Aquaculture fish show and fillet by their own weight component; keep it in step with ours. */
    @SuppressWarnings("unchecked")
    static void setAquacultureWeight(ItemStack fish, Species species, double kg) {
        if (!species.id().getNamespace().equals("aquaculture")) return;
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(AQUACULTURE_WEIGHT);
        if (type != null) fish.set((DataComponentType<Float>) type, (float) kg);
    }

    /**
     * Vanilla XP for a fish landed by hand (before the release bonus; machines get half): a base by rarity, more for
     * a heavier fish (in absolute terms, and against its species' usual weight), trophies and unique fish extra, and
     * scaled by how hard the fight was (peak pull against the tackle's rating, and its length). A small common
     * panfish gives about 2, a 4 kg carp about 6, a big rare sea fish dozens.
     */
    public static int xp(Species species, BiteSampler.Bite bite, Fight fight) {
        double base = switch (species.rarity()) {
            case COMMON -> 3;
            case UNCOMMON -> 5;
            case RARE -> 9;
            case VERY_RARE -> 16;
            case LEGENDARY -> 40;
        };
        double heft = 1 + 0.6 * Math.log1p(bite.kg());
        double size = Math.max(0.6, Math.min(2.5, Math.sqrt(bite.kg() / Math.max(1e-4, species.size().typicalWeightKg()))));
        double klass = switch (bite.size()) {
            case TROPHY -> 1.5;
            case UNIQUE -> 2.5;
            default -> 1;
        };
        double effort = 0.6 + fight.difficulty();
        return (int) Math.max(1, Math.min(1000, Math.round(base * heft * size * klass * effort)));
    }

    /**
     * Fires the events for {@code drops} landed by {@code hook} and returns what is left to hand out (empty if a
     * listener cancelled the catch). {@code species} may be null for junk and treasure.
     */
    public static List<ItemStack> fire(CastHook hook, Player angler, ItemStack rod, List<ItemStack> drops, Species species,
                                       double kg) {
        List<ItemStack> out = new ArrayList<>(drops);
        ItemFishedEvent event = new ItemFishedEvent(out, 1, hook);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return List.of();
        // Aquaculture's own listener gives its fish a random weight; ours is the real one.
        if (species != null) out.forEach(stack -> {
            if (stack.getItem() == species.item()) setAquacultureWeight(stack, species, kg);
        });
        // A machine's fake angler earns no advancements or stats (they would make a player file for it).
        if (angler instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, out);
            for (ItemStack stack : out) {
                if (stack.is(ItemTags.FISHES)) player.awardStat(Stats.FISH_CAUGHT, 1);
            }
        }
        return out;
    }

    /** Hands {@code items} and {@code xp} to the angler, with the catch sound. */
    public static void give(Player angler, List<ItemStack> items, int xp) {
        ServerLevel level = (ServerLevel) angler.level();
        for (ItemStack stack : items) {
            if (!angler.getInventory().add(stack)) angler.drop(stack, false);
        }
        if (xp > 0) ExperienceOrb.award(level, angler.position().add(0, 0.5, 0), xp);
        level.playSound(null, angler.getX(), angler.getY(), angler.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    /**
     * Junk or treasure, as vanilla rolls it, from the table Tide's {@code fishing/loot} data picks for the spot (lava
     * and cave junk, Nether and ocean monument treasure, ...), else from the vanilla fishing tables (other mods add
     * to those).
     */
    public static List<ItemStack> lootFrom(CastHook hook, ItemStack rod, FishingContext context, boolean treasure,
                                           int luck) {
        ServerLevel level = (ServerLevel) hook.level();
        ResourceKey<LootTable> key = (context == null ? Optional.<FishingLootData>empty()
                : CatchTables.pickLoot(context, hook.getRandom(), treasure))
                .map(FishingLootData::lootTable)
                .orElse(treasure ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING_JUNK);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        return table.getRandomItems(params);
    }

    /**
     * A loot crate reeled in: it flies out of the water toward the angler as Tide's crates do, and fills itself from
     * its loot table where it lands. Fires {@code tide:pull_crate}.
     */
    public static void pullCrate(CastHook hook, Player angler, ItemStack rod, CrateData crate, int luck) {
        ServerLevel level = (ServerLevel) hook.level();
        BlockPos pos = hook.blockPosition();
        BlockState water = level.getBlockState(pos);
        BlockState block = crate.blockProvider().getState(hook.getRandom(), pos);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.THIS_ENTITY, angler)
                .withLuck(luck)
                .create(LootContextParamSets.CHEST);
        double dx = angler.getX() - pos.getX();
        double dy = angler.getY() - pos.getY();
        double dz = angler.getZ() - pos.getZ();
        LootCrateEntity.create(level, pos, block, dx * 0.0666,
                dy * 0.0666 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.082 + 0.27, dz * 0.0666,
                params, crate.lootTable().map(LootTableRef::of).orElse(null));
        // The crate leaves the water (or lava) it was pulled from as it was.
        level.setBlock(pos, water, 3);
        if (angler instanceof ServerPlayer player && !(player instanceof FakePlayer)) TideCriteriaTriggers.PULLED_CRATE.trigger(player);
        level.playSound(null, angler.getX(), angler.getY(), angler.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    /**
     * The Midas and Village rods' extra catch, as in Tide: now and then a gold item or a bit of village loot comes
     * up with the fish (never in lava).
     */
    public static Optional<ItemStack> perkBonus(CastHook hook, Player angler, ItemStack rod, RodStats stats,
                                                String medium, int luck) {
        if (medium.equals("lava")) return Optional.empty();
        LootTableRef table = stats.rod().has("midas") ? TideLootTables.Fishing.BONUS_GOLD
                : stats.rod().has("village") ? TideLootTables.Fishing.BONUS_VILLAGE : null;
        if (table == null) return Optional.empty();
        ServerLevel level = (ServerLevel) hook.level();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        return table.getTable(level.getServer()).getRandomItems(params).stream().filter(s -> !s.isEmpty()).findFirst();
    }

    /**
     * Logs a landed fish in the angler's Fishing Journal (Tide's), with its length, kept or released: the journal
     * unlocks the species and keeps the angler's records.
     */
    public static void logInJournal(ServerPlayer angler, ItemStack fish, BiteSampler.Bite bite) {
        ItemStack entry = fish.copy();
        TideItemData.FISH_LENGTH.set(entry, bite.lengthCm());
        TideUtils.tryLogCatch(entry, angler, bite.kg());
    }

    static Component describe(Species species, BiteSampler.Bite bite) {
        return Component.translatable("message.create_galore_fishing.caught." + bite.size().key(),
                species.item().getDescription(), CatchInfo.weightText(bite.kg()),
                String.format(java.util.Locale.ROOT, "%.0f", bite.lengthCm()));
    }
}
