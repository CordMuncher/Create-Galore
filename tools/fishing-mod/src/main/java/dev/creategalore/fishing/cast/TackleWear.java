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
 * What a fight costs the tackle. Every fight wears the rod (more the longer it was loaded near its rating), the reel
 * (more by cranking while its drag slipped, with the square of the drag) and the leader, hook and float, sinker or
 * lure a little. A line only gets weaker by fraying, which teeth do (Emre, 22 Oct): load and long fights never
 * weaken it, it holds or it snaps. Nothing lasts for ever and nothing caps it: a careless angler can
 * wear a reel or rod through in one fight, and a rod, reel or line that gives out mid-fight loses the fish (the fight
 * checks what each has left, see {@link Fight#wearLimits}). A leader, hook or terminal that wears out goes, but a
 * fish already on stays on. A line that snaps, is bitten through, runs
 * out or parts on a seized reel loses what was out plus a few metres, and the hook, rig, leader and bait go with it.
 * A rod that breaks hands its parts back.
 */
public final class TackleWear {
    private TackleWear() {
    }

    /** Wear one fight puts on a leader, hook, float, sinker or lure: one wears out after 200 fights. */
    public static final float PART_WEAR_PER_FIGHT = 0.005f;
    /** Fraying one fight puts on a line: only what teeth did. */
    public static double lineFray(Fight fight) {
        return fight.teethFray();
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
     * Tells {@code fight} how much the reel and rod on {@code rod} have left beyond the fight's base wear, and how much
     * fraying the line can still take, so
     * one worn through mid-fight breaks there and then.
     */
    public static void setLimits(Fight fight, ItemStack rod) {
        ItemStack reel = RodItem.parts(rod).reel();
        double reelLeft = !reel.isEmpty() && reel.isDamageableItem()
                ? reel.getMaxDamage() - reel.getDamageValue() - 1 : Double.POSITIVE_INFINITY;
        double rodLeft = rod.isDamageableItem() ? rod.getMaxDamage() - rod.getDamageValue() - 1 : Double.POSITIVE_INFINITY;
        ItemStack line = RodItem.parts(rod).line();
        double lineLeft = line.isEmpty() ? Double.POSITIVE_INFINITY
                : 1 - line.getOrDefault(FishingRegistry.LINE_WEAR.get(), 0f);
        fight.wearLimits(Math.max(0, reelLeft), Math.max(0, rodLeft), Math.max(0, lineLeft));
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
                // What was on the end goes: leader, hook, float or lure, and the one bait on the hook. Bait still
                // waiting in the rod stays.
                ItemStack bait = parts.bait();
                bait.shrink(1);
                parts = parts.with(RodParts.Slot.LEADER, ItemStack.EMPTY).with(RodParts.Slot.HOOK, ItemStack.EMPTY)
                        .with(RodParts.Slot.TERMINAL, ItemStack.EMPTY).with(RodParts.Slot.BAIT, bait);
            } else {
                double fray = lineFray(fight);
                float wear = line.getOrDefault(FishingRegistry.LINE_WEAR.get(), 0f) + (float) fray;
                if (fray <= 0) {
                    // No teeth, no fraying: the line is as it was.
                } else if (wear >= 1) {
                    angler.level().playSound(null, angler.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
                    tell(angler, Component.translatable("message.create_galore_fishing.worn_out", line.getHoverName()));
                    line = ItemStack.EMPTY;
                } else {
                    line.set(FishingRegistry.LINE_WEAR.get(), wear);
                }
                for (RodParts.Slot s : new RodParts.Slot[]{RodParts.Slot.LEADER, RodParts.Slot.HOOK, RodParts.Slot.TERMINAL}) {
                    parts = parts.with(s, wearPart(angler, parts.get(s)));
                }
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

    /** One fight's wear on a leader, hook or terminal; returns it, or empty once it has worn out. */
    static ItemStack wearPart(LivingEntity angler, ItemStack part) {
        if (part.isEmpty()) return part;
        // Hooks with durability of their own (Hybrid Aquatic's) spend it on their effects; fights wear them here too.
        float wear = part.getOrDefault(FishingRegistry.PART_WEAR.get(), 0f) + PART_WEAR_PER_FIGHT;
        boolean worn = wear >= 1 - 1e-4f;
        part.set(FishingRegistry.PART_WEAR.get(), wear);
        if (!worn) return part;
        angler.level().playSound(null, angler.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
        tell(angler, Component.translatable("message.create_galore_fishing.worn_out", part.getHoverName()));
        return ItemStack.EMPTY;
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
