package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.TackleWear;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.sim.Outcome;
import io.netty.buffer.Unpooled;
import java.util.Locale;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The {@code tackle} self-test: every tackle item has data, rods build valid rigs, components survive saving. */
public final class TackleSelfTest {
    private TackleSelfTest() {
    }

    public static void register() {
        SelfTest.suite("tackle", (server, r) -> {
            r.check(TackleManager.problems().isEmpty(), "no tackle data problems (" + TackleManager.problems().size() + ")");
            TackleManager.problems().forEach(p -> r.info("problem: " + p));
            for (var rod : FishingRegistry.RODS) {
                r.check(TackleManager.of(rod.get()).filter(p -> p.kind() == TacklePart.Kind.ROD).isPresent(),
                        "rod data for " + rod.getId());
            }
            for (var part : FishingRegistry.PARTS) {
                r.check(TackleManager.of(part.get()).isPresent(), "tackle data for " + part.getId());
            }

            // A full iron rig: parts go in, stats come out, and everything survives disk and network round trips.
            ItemStack rod = rig(FishingRegistry.IRON_ROD.get(), FishingRegistry.IRON_REEL.get(),
                    FishingRegistry.MONO_LINE.get(), FishingRegistry.MEDIUM_HOOK.get(), FishingRegistry.FLOAT.get());
            RodStats stats = RodStats.of(rod).orElseThrow();
            r.check(stats.canCast(), "iron rig can cast (missing: " + stats.missing() + ")");
            r.info(String.format(Locale.ROOT, "iron rig: rated %.1f kg, fish ~%.1f kg, cast %.0f m, drag %.1f kg, warnings %s",
                    stats.ratedLoad(), stats.ratedFishKg(), stats.maxCast(), stats.drag(), stats.warnings()));
            r.check(stats.ratedLoad() > 1 && stats.ratedLoad() <= 9, "iron rig rated between 1 and 9 kg");

            RegistryAccess access = server.registryAccess();
            Tag saved = rod.save(access);
            ItemStack loaded = ItemStack.parse(access, saved).orElse(ItemStack.EMPTY);
            r.check(ItemStack.matches(rod, loaded), "rod with parts survives save/load");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, rod);
            ItemStack received = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            r.check(ItemStack.matches(rod, received), "rod with parts survives the network");

            // A pole has no reel slot and casts its fixed line.
            ItemStack pole = new ItemStack(FishingRegistry.BAMBOO_POLE.get());
            RodItem.setParts(pole, RodParts.EMPTY.with(RodParts.Slot.LINE, new ItemStack(FishingRegistry.STRING_LINE.get()))
                    .with(RodParts.Slot.HOOK, new ItemStack(FishingRegistry.SMALL_HOOK.get())));
            RodStats poleStats = RodStats.of(pole).orElseThrow();
            r.check(poleStats.isPole() && poleStats.canCast(), "pole with line and hook can cast");
            r.check(!poleStats.tackle(1).hasReel(), "pole has no drag");

            // A drag set above the line's test is flagged (iron reel up to 6 kg on 4 kg string line).
            ItemStack weak = rig(FishingRegistry.IRON_ROD.get(), FishingRegistry.IRON_REEL.get(),
                    FishingRegistry.STRING_LINE.get(), FishingRegistry.MEDIUM_HOOK.get(), FishingRegistry.FLOAT.get());
            r.check(RodStats.of(weak).orElseThrow().warnings().isEmpty(), "string line at the default drag has no warning");
            weak.set(FishingRegistry.DRAG_STEP.get(), 10);
            r.check(!RodStats.of(weak).orElseThrow().warnings().isEmpty(), "drag over the line test gives a warning");

            // Every part wears a little each fight, even a gentle landed one, and wears out in the end.
            ArmorStand angler = EntityType.ARMOR_STAND.create(server.overworld());
            ItemStack used = rig(FishingRegistry.IRON_ROD.get(), FishingRegistry.IRON_REEL.get(),
                    FishingRegistry.MONO_LINE.get(), FishingRegistry.MEDIUM_HOOK.get(), FishingRegistry.FLOAT.get());
            RodItem.setParts(used, RodItem.parts(used).with(RodParts.Slot.LEADER, new ItemStack(FishingRegistry.CLEAR_LEADER.get())));
            Fight gentle = new Fight(FishFight.AVERAGE, RodStats.of(used).orElseThrow().tackle(1), 1, 5);
            TackleWear.apply(angler, EquipmentSlot.MAINHAND, used, gentle, Outcome.LANDED);
            RodParts after = RodItem.parts(used);
            r.check(used.getDamageValue() == 1 && after.reel().getDamageValue() == 1,
                    "a gentle fight costs the rod " + used.getDamageValue() + " and the reel " + after.reel().getDamageValue() + " (want 1 each)");
            r.check(Math.abs(after.line().getOrDefault(FishingRegistry.LINE_WEAR.get(), 0f) - TackleWear.LINE_WEAR_PER_FIGHT) < 1e-6,
                    "a gentle fight frays the line " + after.line().get(FishingRegistry.LINE_WEAR.get()));
            for (RodParts.Slot slot : new RodParts.Slot[]{RodParts.Slot.LEADER, RodParts.Slot.HOOK, RodParts.Slot.TERMINAL}) {
                r.check(after.get(slot).getOrDefault(FishingRegistry.PART_WEAR.get(), 0f) == TackleWear.PART_WEAR_PER_FIGHT,
                        "a gentle fight wears the " + slot + " " + after.get(slot).get(FishingRegistry.PART_WEAR.get()));
            }
            // A rod, reel or line worn through breaks mid-fight and the fish is gone: a line nearly frayed through parts
            // as soon as the fish pulls.
            ItemStack frayedRig = used.copy();
            ItemStack frayedLine = after.line();
            frayedLine.set(FishingRegistry.LINE_WEAR.get(), 0.995f);
            RodItem.setParts(frayedRig, after.with(RodParts.Slot.LINE, frayedLine));
            Fight onFrayed = new Fight(FishFight.AVERAGE, RodStats.of(frayedRig).orElseThrow().tackle(1), 1, 20);
            TackleWear.setLimits(onFrayed, frayedRig);
            Controls hold = new Controls();
            hold.drag = RodStats.of(frayedRig).orElseThrow().drag();
            java.util.SplittableRandom random = new java.util.SplittableRandom(1);
            while (!onFrayed.outcome().over() && onFrayed.ticks() < 200) onFrayed.tick(hold, random);
            r.check(onFrayed.outcome() == Outcome.LINE_SNAPPED, "a line frayed through parts mid-fight (" + onFrayed.outcome() + ")");

            ItemStack nearlyHook = after.hook(), nearlyLine = after.line();
            nearlyHook.set(FishingRegistry.PART_WEAR.get(), 0.996f);
            nearlyLine.set(FishingRegistry.LINE_WEAR.get(), 0.995f);
            RodItem.setParts(used, after.with(RodParts.Slot.HOOK, nearlyHook).with(RodParts.Slot.LINE, nearlyLine));
            TackleWear.apply(angler, EquipmentSlot.MAINHAND, used, gentle, Outcome.LANDED);
            RodParts worn = RodItem.parts(used);
            r.check(worn.hook().isEmpty() && worn.line().isEmpty() && !worn.terminal().isEmpty() && !worn.reel().isEmpty(),
                    "a hook and a line worn through are gone after the fight; the rest stays");

            // Emre's snap (0.4.12): a String Line (4 kg test) frayed 22.5% holds 3.55 kg, so a 3.6 kg drag is over it.
            // The rod's tooltip, the drag readouts and the fight must all use that same number.
            ItemStack frayed = rig(FishingRegistry.IRON_ROD.get(), FishingRegistry.IRON_REEL.get(),
                    FishingRegistry.STRING_LINE.get(), FishingRegistry.MEDIUM_HOOK.get(), FishingRegistry.SPOON.get());
            RodParts fp = RodItem.parts(frayed);
            ItemStack snapLine = fp.line();
            snapLine.set(FishingRegistry.LINE_WEAR.get(), 0.225f);
            RodItem.setParts(frayed, fp.with(RodParts.Slot.LINE, snapLine));
            frayed.set(FishingRegistry.DRAG_STEP.get(), 6);
            RodStats fs = RodStats.of(frayed).orElseThrow();
            r.check(Math.abs(fs.breakingLoad() - 3.55) < 1e-6 && fs.tackle(1).breakingLoad() == fs.breakingLoad(),
                    String.format(Locale.ROOT, "a frayed line holds the same in the rod's stats and in the fight (%.3f / %.3f kg)",
                            fs.breakingLoad(), fs.tackle(1).breakingLoad()));
            r.check(Math.abs(fs.drag() - 3.6) < 1e-6 && fs.dragOverLine() && RodStats.kgHolds(fs.breakingLoad()).equals("3.5 kg")
                            && fs.warnings().stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                            && t.getKey().endsWith("warn.drag_over_line")),
                    "a 3.6 kg drag on it shows as over the line (holds shown as " + RodStats.kgHolds(fs.breakingLoad()) + ", warned)");
            // Any drag the readouts show as safe holds through hard fights, frayed line or not.
            int snapped = 0;
            for (int step = 0; step <= fs.dragSteps(); step++) {
                frayed.set(FishingRegistry.DRAG_STEP.get(), step);
                RodStats at = RodStats.of(frayed).orElseThrow();
                if (at.dragOverLine()) continue;
                java.util.SplittableRandom rng = new java.util.SplittableRandom(step);
                for (int f = 0; f < 40; f++) {
                    Fight fight = new Fight(new dev.creategalore.fishing.sim.FishFight(1.2, 2, 1.6,
                            new dev.creategalore.fishing.sim.Curve(new double[]{0}, new double[]{1}), 0.3, 0.3, 0.05, false),
                            at.tackle(1), 10, 30);
                    dev.creategalore.fishing.sim.Controls c = new dev.creategalore.fishing.sim.Controls();
                    c.drag = at.drag();
                    while (!fight.outcome().over() && fight.ticks() < 1200) {
                        c.reeling = fight.ticks() / 40 % 2 == 0;
                        fight.tick(c, rng);
                    }
                    if (fight.outcome() == Outcome.LINE_SNAPPED) snapped++;
                }
            }
            r.check(snapped == 0, "no drag shown as safe snaps the frayed line in hard fights (" + snapped + " snapped)");
        });
    }

    static ItemStack rig(Item rod, Item reel, Item line, Item hook, Item terminal) {
        ItemStack stack = new ItemStack(rod);
        RodItem.setParts(stack, RodParts.EMPTY.with(RodParts.Slot.REEL, new ItemStack(reel))
                .with(RodParts.Slot.LINE, new ItemStack(line)).with(RodParts.Slot.HOOK, new ItemStack(hook))
                .with(RodParts.Slot.TERMINAL, new ItemStack(terminal)));
        return stack;
    }
}
