package dev.creategalore.bridge;

import dev.creategalore.bridge.mixin.MobGoalsAccessor;
import dev.creategalore.bridge.mixin.PathRangeInvoker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.athisworkshop.animalhusbandry.ai.MoveToTroughGoal;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlockEntity;
import org.athisworkshop.animalhusbandry.care.AnimalCareData;
import org.athisworkshop.animalhusbandry.genetics.AnimalGenetics;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;

/**
 * Animal Husbandry's tamed animals get hungry once a day and walk to a Feeding Trough for food (or water). Two things
 * left them starving beside full troughs:
 *
 * The day count runs on the world's clock, but an animal only counts while its chunk is simulated. A farm left behind
 * (a trip, another dimension, the far end of a big base) froze, and the first moment it ran again the animal counted
 * every missed day at once: days unfed, happiness down 10 for each, and the sickness roll for starving, all before it
 * could take a step to the trough beside it. Its troughs were still full. Now an animal that comes back after two
 * days or more first covers each missed day except today from the troughs it can walk to (nearest trough first, a
 * meal lasting as long as below), and is just hungry for today, so it still walks over to eat. When the troughs run
 * out partway, the days it went without still count.
 *
 * The trough goal itself looked through a 33x33 square, one block up and down, and took the first trough in that
 * scan order, not the nearest. It then needed a path, but a path only reaches 16 blocks from the animal, so a trough
 * in the far part of the square never worked, and the animal searched again every other tick. It walks to the
 * nearest trough with its food that it can reach, within 20 blocks and 4 up or down, with paths up to 24 blocks long.
 * A trough it can't get to is left alone for 30 s while it tries the next one, and it gives up a walk that takes
 * longer than 30 s, so a jostling crowd at one trough sends it to another instead of keeping it stuck.
 *
 * A meal (one item, two for a Glutton, from a trough or from the hand) keeps an animal fed for two and a half days,
 * and a drink (one of a trough's 16 water levels) keeps it watered as long: Animal Husbandry makes it hungry and
 * thirsty again every day, and a day it still has food or water in it is taken off again on the same tick. A herd
 * eats and drinks two times in five days. The days unfed that count for happiness and sickness are the days after
 * that runs out.
 */
public final class AnimalTroughs {
    public static final int REACH = 20;
    private static final int REACH_Y = 4;
    private static final float PATH_RANGE = 24.0f;
    private static final float PATH_NODES = 4.0f;
    private static final int PATHS_PER_SEARCH = 6;
    private static final int SKIP_TICKS = 600;
    private static final int WALK_TICKS = 600;
    private static final double EAT_DISTANCE_SQ = 6.0;
    private static final double SPEED = 1.2;
    private static final long DAY = 24000L;
    /** What each kind may find in a trough; the trough only takes some of these. */
    /** How long a meal or a drink lasts, in tenths of a day. */
    public static final int MEAL_TENTHS = 25;
    private static final int DAY_TENTHS = 10;
    private static final String FOOD_LEFT = "create_galore_bridge:food_left";
    private static final String WATER_LEFT = "create_galore_bridge:water_left";
    private static final List<ItemStack> FOODS = List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.CARROT),
        new ItemStack(Items.POTATO), new ItemStack(Items.WHEAT_SEEDS));

    private AnimalTroughs() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("animalhusbandry")) return;
        NeoForge.EVENT_BUS.addListener(AnimalTroughs::onJoin);
        NeoForge.EVENT_BUS.addListener(AnimalTroughs::onTick);
        NeoForge.EVENT_BUS.addListener(AnimalTroughs::afterTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AnimalTroughs::onHandFeed);
    }

    /** The animals Animal Husbandry looks after (the same list as its own). */
    private static boolean managed(Animal animal) {
        EntityType<?> type = animal.getType();
        return "animalhusbandry".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace()) || type == EntityType.CHICKEN
            || type == EntityType.COW || type == EntityType.GOAT || type == EntityType.PIG || type == EntityType.SHEEP;
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Animal animal)) return;
        GoalSelector goals = ((MobGoalsAccessor) animal).create_galore_bridge$goals();
        List<WrappedGoal> old = new ArrayList<>();
        for (WrappedGoal wrapped : goals.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof MoveToTroughGoal) old.add(wrapped);
        }
        for (WrappedGoal wrapped : old) {
            goals.removeGoal(wrapped.getGoal());
            goals.addGoal(wrapped.getPriority(), new TroughGoal(animal));
        }
    }

    /**
     * Runs on the tick Animal Husbandry counts the days, just before it does: an animal that missed two days or more
     * eats from its troughs for the days the farm stood still, except today.
     */
    private static void onTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Animal animal) || !(animal.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 100L != 0L || !managed(animal)) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (care == null || !care.isDomesticated) return;
        long days = (level.getDayTime() - care.lastTickTime) / DAY;
        if (days < 2) return;
        int fedDays = eatMissedMeals(animal, (int) days - 1);
        if (fedDays == 0) return;
        // Animal Husbandry adds the elapsed days next; this leaves it the days since the food of these meals ran out.
        care.daysSinceFed = -fedDays;
        ModAttachments.setCareData(animal, care);
    }

    /**
     * Runs just after Animal Husbandry counted the days: a day the animal still has food or water in it from its last
     * meal or drink doesn't count as hungry or thirsty.
     */
    private static void afterTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal) || animal.level().isClientSide) return;
        if (animal.level().getGameTime() % 100L != 0L || !managed(animal)) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (care == null || !care.isDomesticated) return;
        CompoundTag data = animal.getPersistentData();
        int food = data.getInt(FOOD_LEFT), water = data.getInt(WATER_LEFT);
        boolean changed = false;
        while (care.daysSinceFed > 0 && food >= DAY_TENTHS) {
            care.daysSinceFed--;
            food -= DAY_TENTHS;
            changed = true;
        }
        while (care.daysDehydrated > 0 && water >= DAY_TENTHS) {
            care.daysDehydrated--;
            water -= DAY_TENTHS;
            changed = true;
        }
        if (!changed) return;
        data.putInt(FOOD_LEFT, food);
        data.putInt(WATER_LEFT, water);
        ModAttachments.setCareData(animal, care);
    }

    /** Animal Husbandry fills a hungry tamed animal from the hand; that meal lasts as long as one from a trough. */
    private static void onHandFeed(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || !(event.getTarget() instanceof Animal animal) || !managed(animal)) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (care == null || !care.isDomesticated || care.daysSinceFed <= 0 || !animal.isFood(event.getItemStack())) return;
        ate(animal);
    }

    /** A meal: today is fed, and the rest of the meal is kept for the days after. */
    private static void ate(Animal animal) {
        CompoundTag data = animal.getPersistentData();
        data.putInt(FOOD_LEFT, data.getInt(FOOD_LEFT) % DAY_TENTHS + MEAL_TENTHS - DAY_TENTHS);
    }

    private static void drank(Animal animal) {
        CompoundTag data = animal.getPersistentData();
        data.putInt(WATER_LEFT, data.getInt(WATER_LEFT) % DAY_TENTHS + MEAL_TENTHS - DAY_TENTHS);
    }

    /** Whole days the animal's last meal (food) or drink still lasts after today. */
    public static int daysLeft(Animal animal, boolean food) {
        return animal.getPersistentData().getInt(food ? FOOD_LEFT : WATER_LEFT) / DAY_TENTHS;
    }

    /** Covers missed days, first with what the animal still has in it, then with meals from the troughs it can walk to. */
    private static int eatMissedMeals(Animal animal, int wanted) {
        CompoundTag data = animal.getPersistentData();
        int left = data.getInt(FOOD_LEFT);
        int bite = bite(animal);
        int days = 0;
        while (days < wanted && left >= DAY_TENTHS) {
            left -= DAY_TENTHS;
            days++;
        }
        int paths = 0;
        for (FeedingTroughBlockEntity trough : troughs(animal, true, false)) {
            if (days >= wanted || paths >= PATHS_PER_SEARCH) break;
            paths++;
            Path path = pathTo(animal, trough.getBlockPos());
            if (path == null || !path.canReach()) continue;
            while (days < wanted && hasFood(animal, trough)) {
                trough.removeItem(0, Math.min(bite, trough.getItem(0).getCount()));
                left += MEAL_TENTHS;
                while (days < wanted && left >= DAY_TENTHS) {
                    left -= DAY_TENTHS;
                    days++;
                }
            }
        }
        data.putInt(FOOD_LEFT, left);
        return days;
    }

    /** True when the animal can walk to a trough holding its food. */
    public static boolean foodInReach(Animal animal) {
        int paths = 0;
        for (FeedingTroughBlockEntity trough : troughs(animal, true, false)) {
            if (paths++ >= PATHS_PER_SEARCH) break;
            Path path = pathTo(animal, trough.getBlockPos());
            if (path != null && path.canReach()) return true;
        }
        return false;
    }

    /** The trough foods this kind eats, by name ("Wheat", "Carrot, Potato"). */
    public static String foodNames(Animal animal) {
        StringJoiner names = new StringJoiner(", ");
        for (ItemStack food : FOODS) {
            if (eats(animal, food)) names.add(food.getHoverName().getString());
        }
        return names.toString();
    }

    /** Animal Husbandry's own rule: what the animal breeds on, and leaves and flowers for an Iron Stomach goat. */
    static boolean eats(Animal animal, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (animal.isFood(stack)) return true;
        if (!(animal instanceof Goat)) return false;
        AnimalGenetics genetics = ModAttachments.getGenetics(animal);
        return genetics != null && "iron_stomach".equals(genetics.trait) && (stack.is(ItemTags.LEAVES) || stack.is(ItemTags.FLOWERS)
            || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.TALL_FLOWERS));
    }

    private static boolean hasFood(Animal animal, FeedingTroughBlockEntity trough) {
        return trough.waterLevel == 0 && eats(animal, trough.getItem(0));
    }

    private static int bite(Animal animal) {
        AnimalGenetics genetics = ModAttachments.getGenetics(animal);
        return genetics != null && "glutton".equals(genetics.trait) ? 2 : 1;
    }

    /** Troughs in reach holding the animal's food (food) or water (water), nearest first. Only chunks already loaded. */
    private static List<FeedingTroughBlockEntity> troughs(Animal animal, boolean food, boolean water) {
        List<FeedingTroughBlockEntity> found = new ArrayList<>();
        Level level = animal.level();
        BlockPos at = animal.blockPosition();
        for (int cx = (at.getX() - REACH) >> 4; cx <= (at.getX() + REACH) >> 4; cx++) {
            for (int cz = (at.getZ() - REACH) >> 4; cz <= (at.getZ() + REACH) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof FeedingTroughBlockEntity trough)) continue;
                    BlockPos pos = trough.getBlockPos();
                    int dx = pos.getX() - at.getX(), dz = pos.getZ() - at.getZ();
                    if (Math.abs(pos.getY() - at.getY()) > REACH_Y || dx * dx + dz * dz > REACH * REACH) continue;
                    if (food && hasFood(animal, trough) || water && trough.waterLevel > 0) found.add(trough);
                }
            }
        }
        found.sort(Comparator.comparingDouble(t -> t.getBlockPos().distToCenterSqr(animal.position())));
        return found;
    }

    /** A path to the trough or a block beside it (or on it), up to 24 blocks long. */
    private static Path pathTo(Animal animal, BlockPos trough) {
        PathNavigation navigation = animal.getNavigation();
        // A path is only planned from the ground; one hop or a step down shouldn't make a trough look out of reach.
        boolean onGround = animal.onGround();
        animal.setOnGround(true);
        navigation.setMaxVisitedNodesMultiplier(PATH_NODES);
        try {
            return ((PathRangeInvoker) navigation).create_galore_bridge$createPath(Set.of(trough), 8, false, 1, PATH_RANGE);
        } finally {
            navigation.resetMaxVisitedNodesMultiplier();
            animal.setOnGround(onGround);
        }
    }

    /** Animal Husbandry's trough goal, walking to the nearest trough it can reach. */
    static final class TroughGoal extends Goal {
        private final Animal animal;
        private final Map<BlockPos, Integer> skipped = new HashMap<>();
        private BlockPos target;
        private boolean forFood;
        private Path path;
        private int nextSearch;
        private int walked;
        private int replans;

        TroughGoal(Animal animal) {
            this.animal = animal;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            AnimalCareData care = ModAttachments.getCareData(animal);
            if (care == null) return false;
            boolean hungry = care.daysSinceFed > 0, thirsty = care.daysDehydrated > 0;
            if (!hungry && !thirsty || animal.tickCount < nextSearch) return false;
            nextSearch = animal.tickCount + 20 + animal.getRandom().nextInt(20);
            skipped.values().removeIf(until -> until <= animal.tickCount);
            int paths = 0;
            for (FeedingTroughBlockEntity trough : troughs(animal, hungry, thirsty)) {
                BlockPos pos = trough.getBlockPos();
                if (skipped.containsKey(pos)) continue;
                if (paths++ >= PATHS_PER_SEARCH) break;
                Path found = pathTo(animal, pos);
                if (found == null || !found.canReach()) {
                    skip(pos);
                    continue;
                }
                target = pos.immutable();
                forFood = hungry && hasFood(animal, trough);
                path = found;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null || walked > WALK_TICKS) return false;
            AnimalCareData care = ModAttachments.getCareData(animal);
            if (care == null || (forFood ? care.daysSinceFed <= 0 : care.daysDehydrated <= 0)) return false;
            return animal.level().getBlockEntity(target) instanceof FeedingTroughBlockEntity trough
                && (forFood ? hasFood(animal, trough) : trough.waterLevel > 0);
        }

        @Override
        public void start() {
            walked = 0;
            replans = 0;
            animal.getNavigation().moveTo(path, SPEED);
        }

        @Override
        public void stop() {
            if (target != null && walked > WALK_TICKS) skip(target);
            target = null;
            path = null;
            animal.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (target == null) return;
            walked++;
            animal.getLookControl().setLookAt(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 10.0f, animal.getMaxHeadXRot());
            if (animal.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= EAT_DISTANCE_SQ) {
                if (animal.level().getBlockEntity(target) instanceof FeedingTroughBlockEntity trough) consume(trough);
                target = null;
                return;
            }
            if (animal.getNavigation().isInProgress()) return;
            Path again = replans++ < 3 ? pathTo(animal, target) : null;
            if (again == null || !again.canReach()) {
                skip(target);
                target = null;
                return;
            }
            animal.getNavigation().moveTo(again, SPEED);
        }

        /** Eats or drinks the way Animal Husbandry does. */
        private void consume(FeedingTroughBlockEntity trough) {
            AnimalCareData care = ModAttachments.getCareData(animal);
            if (care == null) return;
            if (forFood && care.daysSinceFed > 0 && hasFood(animal, trough)) {
                trough.removeItem(0, Math.min(bite(animal), trough.getItem(0).getCount()));
                care.daysSinceFed = 0;
                ate(animal);
                animal.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
            } else if (!forFood && care.daysDehydrated > 0 && trough.waterLevel > 0) {
                trough.waterLevel--;
                care.daysDehydrated = 0;
                drank(animal);
                animal.playSound(SoundEvents.GENERIC_DRINK, 1.0f, 1.0f);
            } else {
                return;
            }
            ModAttachments.setCareData(animal, care);
            trough.setChanged();
            trough.updateVisualState();
            ((ServerLevel) animal.level()).sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + 1.0, animal.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
        }

        private void skip(BlockPos pos) {
            skipped.put(pos.immutable(), animal.tickCount + SKIP_TICKS);
        }
    }
}
