package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.compat.TideRodConversion;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * A rod blank, and with parts fitted at the Tackle Bench, a complete rod. Each tier (pole, wooden, iron, steel,
 * boat, netherite) is its own item; the fitted parts live in its {@link RodParts} component.
 *
 * <p>Not a {@code FishingRodItem} on purpose: other mods hook into vanilla rods (Hybrid Aquatic takes hooks from
 * the off hand there). It still tells NeoForge it can cast, so code that asks "is this a fishing rod" agrees, and it
 * takes fishing enchantments through the {@code minecraft:enchantable/fishing} tag.
 *
 * <p>In the inventory, right-clicking the rod with bait loads it, with a float, sinker, lure or hook swaps that
 * part, and with an empty cursor takes the bait back out.
 */
public class RodItem extends Item {
    /** Most bait one rod carries. */
    public static final int BAIT_CAPACITY = 16;

    public RodItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ability == ItemAbilities.FISHING_ROD_CAST;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    /** Fight wear, drag clicks and bait use change the stack; that shouldn't make the rod bob in the hand. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /** A rod rigged with Tide (its parts kept in Tide's components) becomes one of ours as soon as a player has it. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && TideRodConversion.needsConversion(stack)) {
            TideRodConversion.convert(stack, bait -> {
                if (!player.getInventory().add(bait)) player.drop(bait, false);
            });
        }
    }

    public static RodParts parts(ItemStack rod) {
        return rod.getOrDefault(FishingRegistry.ROD_PARTS.get(), RodParts.EMPTY);
    }

    public static void setParts(ItemStack rod, RodParts parts) {
        if (parts.isEmpty()) rod.remove(FishingRegistry.ROD_PARTS.get());
        else rod.set(FishingRegistry.ROD_PARTS.get(), parts);
    }

    /**
     * With no line out, holding use charges a cast (release to cast; a full charge takes a second). With the line
     * out, holding use reels in (and strikes when a fish bites); sneaking and using brings an idle line straight
     * back.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack rod = player.getItemInHand(hand);
        if (player instanceof net.minecraft.server.level.ServerPlayer server && CatchHolding.holding(server)) {
            // Hands are full with a fish: decide first.
            CatchHolding.offer(server);
            return InteractionResultHolder.fail(rod);
        }
        CHARGING.remove(player);
        if (player.fishing instanceof CastHook hook && !hook.isRemoved()) {
            if (player.isShiftKeyDown() && hook.phase() != CastHook.Phase.FIGHTING) {
                if (!level.isClientSide) hook.retrieve(rod);
                return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(rod);
        }
        if (player.fishing != null) {
            // Someone else's hook (a vanilla rod's): reel that one in first.
            if (!level.isClientSide) player.fishing.retrieve(rod);
            return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
        }
        RodStats stats = RodStats.of(rod).orElse(null);
        if (stats == null || !stats.canCast()) {
            if (!level.isClientSide && stats != null) {
                player.displayClientMessage(stats.missing().get(0).copy().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(rod);
        }
        CHARGING.put(player, Boolean.TRUE);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(rod);
    }

    /**
     * Players whose current use of a rod is a cast being charged (not reeling). Only those cast on release: someone
     * who reeled a fish in and lets go of use afterwards mustn't throw the line straight back out. Keyed by the
     * player object, so the client's and the integrated server's players stay apart.
     */
    private static final Map<Player, Boolean> CHARGING = Collections.synchronizedMap(new WeakHashMap<>());

    /** True while {@code player} is holding use to charge a cast. */
    public static boolean charging(Player player) {
        return player.isUsingItem() && CHARGING.containsKey(player);
    }

    @Override
    public void releaseUsing(ItemStack rod, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || CHARGING.remove(player) == null) return;
        if (level.isClientSide || player.fishing != null) return;
        double charge = Math.min(1, (getUseDuration(rod, entity) - timeLeft) / (double) FULL_CHARGE_TICKS);
        RodStats.of(rod).filter(RodStats::canCast)
                .ifPresent(stats -> CastHook.cast(player, entity.getUsedItemHand(), stats, charge));
    }

    /** Ticks of holding use for a full-strength cast. */
    public static final int FULL_CHARGE_TICKS = 20;

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack rod, ItemStack cursor, Slot slot, ClickAction action, Player player,
                                            SlotAccess cursorAccess) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        RodParts parts = parts(rod);
        if (cursor.isEmpty()) {
            ItemStack bait = parts.bait();
            if (bait.isEmpty()) return false;
            cursorAccess.set(bait);
            setParts(rod, parts.with(RodParts.Slot.BAIT, ItemStack.EMPTY));
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1f);
            return true;
        }
        TacklePart part = TackleManager.of(cursor).orElse(null);
        if (part == null) return false;
        RodParts.Slot target = switch (part.kind()) {
            case BAIT -> RodParts.Slot.BAIT;
            case HOOK -> RodParts.Slot.HOOK;
            case FLOAT, SINKER, LURE -> RodParts.Slot.TERMINAL;
            default -> null;
        };
        if (target == null) return false;
        ItemStack fitted = parts.get(target);
        if (target == RodParts.Slot.BAIT && ItemStack.isSameItemSameComponents(fitted, cursor)) {
            int room = Math.min(BAIT_CAPACITY, fitted.getMaxStackSize()) - fitted.getCount();
            if (room <= 0) return false;
            int moved = Math.min(room, cursor.getCount());
            fitted.grow(moved);
            cursor.shrink(moved);
            setParts(rod, parts.with(target, fitted));
        } else {
            int take = target == RodParts.Slot.BAIT ? Math.min(BAIT_CAPACITY, cursor.getCount()) : 1;
            ItemStack placed = cursor.split(take);
            setParts(rod, parts.with(target, placed));
            if (!fitted.isEmpty()) {
                if (cursor.isEmpty()) cursorAccess.set(fitted);
                else if (!player.getInventory().add(fitted)) player.drop(fitted, false);
            }
        }
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 1f);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RodStats.of(stack).ifPresent(stats -> describe(stats, stack, tooltip));
    }

    private static final String P = "tooltip.create_galore_fishing.";

    static void describe(RodStats stats, ItemStack stack, List<Component> out) {
        TackleTooltips.describe(stats.rod(), stack, out);
        RodParts parts = stats.parts();
        part(out, "reel", parts.reel());
        part(out, "line", parts.line());
        part(out, "leader", parts.leader());
        part(out, "hook", parts.hook());
        part(out, "terminal", parts.terminal());
        if (!parts.bait().isEmpty()) {
            out.add(Component.translatable(P + "part.bait", parts.bait().getHoverName(), parts.bait().getCount())
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        if (stats.reel().isPresent()) {
            out.add(Component.translatable(P + "drag", RodStats.kg(stats.drag()), RodStats.kg(stats.reel().get().maxDrag()))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (stats.canCast()) {
            out.add(Component.translatable(P + "rated", RodStats.kg(stats.ratedLoad()), RodStats.kg(stats.ratedFishKg()))
                    .withStyle(ChatFormatting.BLUE));
            out.add(Component.translatable(P + "casts", TackleTooltips.num(Math.round(stats.maxCast()))).withStyle(ChatFormatting.BLUE));
        } else {
            stats.missing().forEach(c -> out.add(c.copy().withStyle(ChatFormatting.RED)));
        }
        stats.warnings().forEach(c -> out.add(c.copy().withStyle(ChatFormatting.GOLD)));
    }

    private static void part(List<Component> out, String key, ItemStack part) {
        if (part.isEmpty()) return;
        out.add(Component.translatable(P + "part." + key, part.getHoverName()).withStyle(ChatFormatting.DARK_GREEN));
    }
}
