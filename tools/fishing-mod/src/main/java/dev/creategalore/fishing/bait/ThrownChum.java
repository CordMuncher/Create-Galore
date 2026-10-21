package dev.creategalore.fishing.bait;

import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A ball of chum in flight: spreads where it lands in water ({@link Chum#spread}), and is wasted on land. */
public class ThrownChum extends ThrowableItemProjectile {
    public ThrownChum(EntityType<? extends ThrownChum> type, Level level) {
        super(type, level);
    }

    public ThrownChum(Level level, LivingEntity thrower) {
        super(FishingRegistry.THROWN_CHUM.get(), thrower, level);
    }

    public ThrownChum(Level level, double x, double y, double z) {
        super(FishingRegistry.THROWN_CHUM.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return FishingRegistry.CHUM.get();
    }

    @Override
    public void tick() {
        super.tick();
        // Projectiles fly through water rather than hit it: spread on touching it.
        if (!level().isClientSide && !isRemoved() && isInWater()) {
            Chum.spread((ServerLevel) level(), position());
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level && !isRemoved()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 8,
                    0.1, 0.1, 0.1, 0.05);
            discard();
        }
    }
}
