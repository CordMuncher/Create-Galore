package dev.creategalore.fishing.bait;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/** Chum: thrown into the water like a snowball (or out of a dispenser) to draw fish to a spot for a few minutes. */
public class ChumItem extends Item implements ProjectileItem {
    public ChumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                0.5f, 0.6f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        if (!level.isClientSide) {
            ThrownChum chum = new ThrownChum(level, player);
            chum.setItem(stack);
            chum.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.0f, 1.0f);
            level.addFreshEntity(chum);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 10);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        ThrownChum chum = new ThrownChum(level, pos.x(), pos.y(), pos.z());
        chum.setItem(stack);
        return chum;
    }
}
