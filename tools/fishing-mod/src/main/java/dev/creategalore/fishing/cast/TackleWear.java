package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.tackle.LineItem;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a fight costs the tackle. Every fight wears the rod (more the longer it was loaded near its rating), the
 * reel (by cranking while its drag slipped, with the square of the drag) and the line (it frays). Nothing caps it: a
 * careless angler can wear a reel or rod through in one fight, and a part that gives out mid-fight loses the fish
 * (the fight checks the durability left, see {@link Fight#wearLimits}). A line that snaps, is bitten through, runs
 * out or parts on a seized reel loses what was out plus a few metres, and the hook, rig, leader and bait go with it.
 * A rod that breaks hands its parts back.
 */
public final class TackleWear {
    private TackleWear() {
    }

    /** Durability one fight takes from a reel: 1 for the fight, plus its grinding. */
    public static int reelDamage(double reelWear) {
        return 1 + (int) Math.round(reelWear * Fight.REEL_DURABILITY_PER_WEAR);
    }

    /** Durability one fight takes from a rod: 1 for the fight, plus its overloading. */
    public static int rodDamage(double rodWear) {
        return 1 + (int) Math.round(rodWear * Fight.ROD_DURABILITY_PER_WEAR);
    }

    /**
     * Tells {@code fight} how much the reel and rod on {@code rod} have left beyond the fight's base wear, so a part
     * worn through mid-fight breaks there and then.
     */
    public static void setLimits(Fight fight, ItemStack rod) {
        ItemStack reel = RodItem.parts(rod).reel();
        double reelLeft = !reel.isEmpty() && reel.isDamageableItem()
                ? reel.getMaxDamage() - reel.getDamageValue() - 1 : Double.POSITIVE_INFINITY;
        double rodLeft = rod.isDamageableItem() ? rod.getMaxDamage() - rod.getDamageValue() - 1 : Double.POSITIVE_INFINITY;
        fight.wearLimits(Math.max(0, reelLeft), Math.max(0, rodLeft));
    }

    /**
     * Applies the wear of {@code fight} to {@code rod} (held by {@code angler} in {@code slot}, or in a holder when
     * {@code angler} is a fake player) and returns the rod (the same stack, or empty if it broke).
     */
    public static ItemStack apply(LivingEntity angler, EquipmentSlot slot, ItemStack rod, Fight fight, Outcome outcome) {
        RodParts parts = RodItem.parts(rod);

        ItemStack reel = parts.reel();
        if (!reel.isEmpty() && reel.isDamageableItem()) {
            int damage = outcome == Outcome.REEL_BROKE ? reel.getMaxDamage() : reelDamage(fight.reelWear());
            reel.setDamageValue(reel.getDamageValue() + damage);
            if (reel.getDamageValue() >= reel.getMaxDamage()) {
                angler.level().playSound(null, angler.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
                // A reel that seized mid-fight already said so ("Your reel seized..."); the action bar shows one line.
                if (outcome != Outcome.REEL_BROKE) {
                    tell(angler, Component.translatable("message.create_galore_fishing.reel_broke", reel.getHoverName()));
                }
                reel = ItemStack.EMPTY;
            }
            parts = parts.with(RodParts.Slot.REEL, reel);
        }

        ItemStack line = parts.line();
        if (!line.isEmpty()) {
            if (outcome.losesTackle()) {
                int left = LineItem.meters(line) - (int) Math.ceil(fight.distance()) - 3;
                if (left < 5) {
                    line = ItemStack.EMPTY;
                } else {
                    line.set(FishingRegistry.LINE_METERS.get(), left);
                    line.remove(FishingRegistry.LINE_WEAR.get());
                }
                parts = parts.with(RodParts.Slot.LEADER, ItemStack.EMPTY).with(RodParts.Slot.HOOK, ItemStack.EMPTY)
                        .with(RodParts.Slot.TERMINAL, ItemStack.EMPTY).with(RodParts.Slot.BAIT, ItemStack.EMPTY);
            } else {
                float wear = line.getOrDefault(FishingRegistry.LINE_WEAR.get(), 0f) + (float) (0.01 + fight.lineWear() * 0.5);
                line.set(FishingRegistry.LINE_WEAR.get(), Math.min(1f, wear));
            }
            parts = parts.with(RodParts.Slot.LINE, line);
        }
        RodItem.setParts(rod, parts);

        int damage = outcome == Outcome.ROD_BROKE ? rod.getMaxDamage() : rodDamage(fight.rodWear());
        if (rod.isDamageableItem() && rod.getDamageValue() + damage >= rod.getMaxDamage()) {
            // The blank is gone; the reel and the rest come back.
            giveParts(angler, parts);
            RodItem.setParts(rod, RodParts.EMPTY);
            tell(angler, Component.translatable("message.create_galore_fishing.rod_broke"));
        }
        if (angler instanceof Player) {
            rod.hurtAndBreak(damage, angler, slot);
        } else {
            rod.setDamageValue(rod.getDamageValue() + damage);
            if (rod.getDamageValue() >= rod.getMaxDamage()) rod.setCount(0);
        }
        return rod;
    }

    static void giveParts(LivingEntity angler, RodParts parts) {
        for (RodParts.Slot s : RodParts.Slot.values()) {
            ItemStack part = parts.get(s);
            if (part.isEmpty()) continue;
            if (angler instanceof Player player) {
                if (!player.getInventory().add(part)) player.drop(part, false);
            } else {
                angler.spawnAtLocation(part);
            }
        }
    }

    static void tell(LivingEntity angler, Component message) {
        if (angler instanceof ServerPlayer player) player.displayClientMessage(message, true);
    }
}
