package dev.creategalore.fishing.client;
import dev.creategalore.fishing.aquarium.AquariumBlockEntity;
import dev.creategalore.fishing.aquarium.AquariumCreatures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Where each fish in a tank is swimming, worked out on the client only (the server just keeps the list of fish):
 * every fish wanders toward a random spot in the water, turning gently, slower the bigger it is, and picks a new
 * spot when it gets there or after a while. Creatures that crawl (starfish, crabs) keep to the sand floor and go
 * slowly. Kept on the tank's block entity ({@code clientView}).
 */
final class AquariumSwim {
    static final class Fish {
        final ItemStack stack;
        final float pace;
        final float phase;
        /** The mob drawn for a creature (a netted one, {@link CreatureView}); null for a fish item. */
        Entity creature;
        /** Keeps to the floor; creeps unless it's {@code quick}. */
        final boolean crawler, quick;
        double x, y, z, px, py, pz, vx, vy, vz, tx, ty, tz;
        int retarget;

        Fish(ItemStack stack, RandomSource rng, Level level) {
            this.stack = stack;
            EntityType<?> type = AquariumCreatures.type(stack).orElse(null);
            if (type != null) creature = CreatureView.create(stack, level).orElse(null);
            crawler = type != null && type.is(AquariumCreatures.CRAWLERS);
            quick = type != null && type.is(AquariumCreatures.QUICK_CRAWLERS);
            this.pace = 0.7f + rng.nextFloat() * 0.6f;
            this.phase = rng.nextFloat() * 100;
        }
    }

    final List<Fish> fish = new ArrayList<>();
    private final RandomSource rng = RandomSource.create();

    static AquariumSwim of(AquariumBlockEntity tank) {
        if (tank.clientView instanceof AquariumSwim swim) return swim;
        AquariumSwim swim = new AquariumSwim();
        tank.clientView = swim;
        return swim;
    }

    /** The fish's length as drawn, in blocks: true length, but never longer than the tank allows. */
    static float length(AquariumBlockEntity tank, ItemStack stack) {
        float longest = Math.max(tank.sizeX(), tank.sizeZ());
        return Mth.clamp((float) AquariumBlockEntity.cm(stack) / 100, 0.06f, longest - 0.15f);
    }

    /** The top of the sand floor, where crawlers walk. */
    static final float FLOOR = 1 / 16f;

    /** Height of the water surface above the tank's floor block bottom, in blocks. */
    static float waterLevel(AquariumBlockEntity tank) {
        return tank.capacity() <= 0 ? 0 : tank.sizeY() * tank.water() / (float) tank.capacity();
    }

    static void tick(Level level, AquariumBlockEntity tank) {
        AquariumSwim swim = of(tank);
        swim.sync(tank);
        for (Fish f : swim.fish) swim.move(tank, f);
    }

    /** Matches the swimmers to the tank's fish list, keeping the ones that are still there where they are. */
    private void sync(AquariumBlockEntity tank) {
        List<ItemStack> now = tank.fish();
        boolean same = now.size() == fish.size();
        for (int i = 0; same && i < now.size(); i++) same = now.get(i) == fish.get(i).stack;
        if (same) return;
        List<Fish> old = new ArrayList<>(fish);
        fish.clear();
        for (ItemStack stack : now) {
            Fish kept = null;
            for (Fish o : old) {
                if (ItemStack.isSameItemSameComponents(o.stack, stack)) {
                    kept = o;
                    break;
                }
            }
            Fish f = new Fish(stack, rng, tank.getLevel());
            if (kept != null) {
                old.remove(kept);
                f.x = f.px = kept.x;
                f.y = f.py = kept.y;
                f.z = f.pz = kept.z;
                f.vx = kept.vx;
                f.vz = kept.vz;
                if (kept.creature != null) f.creature = kept.creature;
            } else {
                retarget(tank, f);
                f.x = f.px = f.tx;
                f.y = f.py = f.ty;
                f.z = f.pz = f.tz;
            }
            fish.add(f);
        }
    }

    private void move(AquariumBlockEntity tank, Fish f) {
        f.px = f.x;
        f.py = f.y;
        f.pz = f.z;
        double dx = f.tx - f.x, dy = f.ty - f.y, dz = f.tz - f.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.25 || --f.retarget <= 0) {
            retarget(tank, f);
            return;
        }
        float len = length(tank, f.stack);
        // About two body lengths a second for small fish, less for big ones; crabs scuttle, starfish creep.
        double speed = f.crawler ? (f.quick ? 0.02 : 0.004) * f.pace
                : Math.min(0.06, 0.012 + 0.05 * len) * f.pace / (1 + len);
        double k = 0.06;
        f.vx += (dx / dist * speed - f.vx) * k;
        f.vy += (dy / dist * speed * 0.5 - f.vy) * k;
        f.vz += (dz / dist * speed - f.vz) * k;
        f.x += f.vx;
        f.y += f.vy;
        f.z += f.vz;
        clamp(tank, f);
        if (f.creature != null) CreatureView.step(f.creature, f.vx, f.vz, Math.sqrt(f.vx * f.vx + f.vz * f.vz));
    }

    private void retarget(AquariumBlockEntity tank, Fish f) {
        float half = length(tank, f.stack) / 2;
        float mx = Math.min(tank.sizeX() / 2f, half + 0.08f), mz = Math.min(tank.sizeZ() / 2f, half + 0.08f);
        float top = Math.max(0.2f, waterLevel(tank) - 0.15f);
        f.tx = mx + rng.nextFloat() * Math.max(0, tank.sizeX() - 2 * mx);
        f.tz = mz + rng.nextFloat() * Math.max(0, tank.sizeZ() - 2 * mz);
        f.ty = f.crawler ? FLOOR : 0.2f + rng.nextFloat() * Math.max(0, top - 0.2f);
        f.retarget = 60 + rng.nextInt(160);
    }

    private static void clamp(AquariumBlockEntity tank, Fish f) {
        f.x = Mth.clamp(f.x, 0.05, tank.sizeX() - 0.05);
        f.z = Mth.clamp(f.z, 0.05, tank.sizeZ() - 0.05);
        f.y = f.crawler ? FLOOR : Mth.clamp(f.y, 0.15, Math.max(0.15, waterLevel(tank) - 0.08));
    }
}
