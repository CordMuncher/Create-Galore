package dev.creategalore.fishing.bait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

/**
 * Chum (groundbait) spread in the water: for {@link #DURATION_TICKS} bites come faster within {@link #RADIUS}
 * blocks, most at the spot itself and less toward the edge and as it thins out. Several spots add up, to a limit.
 * Kept in memory only: a server restart washes it away, which three minutes of chum can live with.
 */
public final class Chum {
    private Chum() {
    }

    public static final int DURATION_TICKS = 20 * 60 * 3;
    public static final double RADIUS = 8;
    /** At the centre of fresh chum bites come this much faster (1 + BOOST times). */
    static final double BOOST = 0.75;
    static final double MAX_FACTOR = 2.5;

    private record Spot(Vec3 pos, long until) {
    }

    /** The slick's colour: mashed fish, a muddy brown. */
    private static final DustParticleOptions SLICK = new DustParticleOptions(new Vector3f(0.55f, 0.36f, 0.24f), 1.4f);

    private static final Map<ResourceKey<Level>, List<Spot>> SPOTS = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) tick(level);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> SPOTS.clear());
    }

    /** Chum lands in the water at {@code pos}. */
    public static void spread(ServerLevel level, Vec3 pos) {
        SPOTS.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(new Spot(pos, level.getGameTime() + DURATION_TICKS));
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(FishingRegistry.CHUM.get())),
                pos.x, pos.y + 0.1, pos.z, 16, 0.3, 0.05, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.1, pos.z, 24, 0.4, 0.05, 0.4, 0.1);
        level.sendParticles(SLICK, pos.x, pos.y + 0.05, pos.z, 12, 0.8, 0, 0.8, 0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6f, 0.8f);
    }

    /** How many times faster bites come at {@code pos} because of chum (1 with none around). */
    public static double factor(ServerLevel level, Vec3 pos) {
        List<Spot> spots = SPOTS.get(level.dimension());
        if (spots == null) return 1;
        long now = level.getGameTime();
        double factor = 1;
        for (Spot s : spots) {
            double d = s.pos().distanceTo(pos);
            if (d >= RADIUS || s.until() <= now) continue;
            // Full strength for the first two minutes, then it thins out over the last.
            double fresh = Math.min(1, (s.until() - now) / (20.0 * 60));
            factor += BOOST * (1 - d / RADIUS) * fresh;
        }
        return Math.min(MAX_FACTOR, factor);
    }

    /** Chum spots active around {@code pos} ({@code /cgfish hook} and the self-test). */
    public static int around(ServerLevel level, Vec3 pos) {
        List<Spot> spots = SPOTS.get(level.dimension());
        if (spots == null) return 0;
        long now = level.getGameTime();
        return (int) spots.stream().filter(s -> s.until() > now && s.pos().distanceTo(pos) < RADIUS).count();
    }

    private static void tick(ServerLevel level) {
        List<Spot> spots = SPOTS.get(level.dimension());
        if (spots == null) return;
        long now = level.getGameTime();
        spots.removeIf(s -> s.until() <= now);
        // A brown slick on the surface and fish stirring under it, so players can see where it is.
        for (Spot s : spots) {
            level.sendParticles(SLICK, s.pos().x, s.pos().y + 0.05, s.pos().z, 6, 1.6, 0, 1.6, 0);
            level.sendParticles(ParticleTypes.FISHING, s.pos().x, s.pos().y + 0.1, s.pos().z, 3, 1.5, 0, 1.5, 0);
        }
    }
}
