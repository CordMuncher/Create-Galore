package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.sim.SizeModel;
import dev.creategalore.fishing.sim.Tackle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Everything worked out about one rod from its parts: what it is rated for, what is missing before it can cast,
 * which part is the weak link, and the numbers the fight uses. Cheap to build; build it fresh when needed.
 *
 * <p>Worn parts are weaker: a rod at half condition takes 80% of its rated load, a frayed line breaks earlier, a
 * worn reel's drag gets jerkier. That is the "stress" side of tackle: fights wear parts, worn parts fail sooner.
 */
public final class RodStats {
    private final ItemStack stack;
    private final TacklePart.Rod rod;
    private final RodParts parts;
    private final Optional<TacklePart.Reel> reel;
    private final Optional<TacklePart.Line> line;
    private final Optional<TacklePart.Leader> leader;
    private final Optional<TacklePart.Hook> hook;
    private final Optional<TacklePart> terminal;
    private final Optional<TacklePart.Bait> bait;

    private RodStats(ItemStack stack, TacklePart.Rod rod, RodParts parts) {
        this.stack = stack;
        this.rod = rod;
        this.parts = parts;
        this.reel = TackleManager.of(parts.reel(), TacklePart.Reel.class);
        this.line = TackleManager.of(parts.line(), TacklePart.Line.class);
        this.leader = TackleManager.of(parts.leader(), TacklePart.Leader.class);
        this.hook = TackleManager.of(parts.hook(), TacklePart.Hook.class);
        this.terminal = TackleManager.of(parts.terminal()).filter(p -> p.kind().isTerminal());
        this.bait = TackleManager.of(parts.bait(), TacklePart.Bait.class);
    }

    /** Stats for a rod item; empty if the item isn't a rod with tackle data. */
    public static Optional<RodStats> of(ItemStack stack) {
        if (!(stack.getItem() instanceof RodItem)) return Optional.empty();
        return TackleManager.of(stack, TacklePart.Rod.class)
                .map(rod -> new RodStats(stack, rod, stack.getOrDefault(FishingRegistry.ROD_PARTS.get(), RodParts.EMPTY)));
    }

    public TacklePart.Rod rod() {
        return rod;
    }

    public RodParts parts() {
        return parts;
    }

    public Optional<TacklePart.Reel> reel() {
        return reel;
    }

    public Optional<TacklePart.Line> line() {
        return line;
    }

    public Optional<TacklePart.Leader> leader() {
        return leader;
    }

    public Optional<TacklePart.Hook> hook() {
        return hook;
    }

    public Optional<TacklePart> terminal() {
        return terminal;
    }

    public Optional<TacklePart.Bait> bait() {
        return bait;
    }

    public boolean isPole() {
        return !rod.reel();
    }

    public boolean isLure() {
        return terminal.map(t -> t.kind() == TacklePart.Kind.LURE).orElse(false);
    }

    /** 1 for an undamaged item, down to 0 when about to break. */
    static double condition(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem()) return 1;
        return 1 - s.getDamageValue() / (double) s.getMaxDamage();
    }

    public double rodCondition() {
        return condition(stack);
    }

    public double reelCondition() {
        return condition(parts.reel());
    }

    /** Fraying of the line, 0..1. */
    public double lineWear() {
        return parts.line().getOrDefault(FishingRegistry.LINE_WEAR.get(), 0f);
    }

    /** Metres of line on the rod (a pole's fixed length). */
    public int lineMeters() {
        if (line.isEmpty()) return 0;
        int left = LineItem.meters(parts.line());
        if (isPole()) return (int) Math.min(left, rod.fixedLine());
        return (int) Math.min(left, reel.map(TacklePart.Reel::capacity).orElse(0.0));
    }

    public int dragSteps() {
        return reel.map(TacklePart.Reel::dragSteps).orElse(0);
    }

    /** Current drag knob position; a new rod starts at a third of the way. */
    public int dragStep() {
        int steps = dragSteps();
        return Math.max(0, Math.min(steps, stack.getOrDefault(FishingRegistry.DRAG_STEP.get(), steps / 3)));
    }

    /** Current drag in kgf, or infinity for a pole (no drag at all). */
    public double drag() {
        return reel.map(r -> r.dragAt(dragStep())).orElse(Double.POSITIVE_INFINITY);
    }

    public double rodLoad() {
        return rod.maxLoad() * (0.6 + 0.4 * rodCondition());
    }

    public double lineTest() {
        return line.map(l -> l.test() * (1 - 0.5 * lineWear())).orElse(0.0);
    }

    /** What's missing before the rod can be cast; empty when it is ready. */
    public List<Component> missing() {
        List<Component> out = new ArrayList<>();
        if (!isPole() && reel.isEmpty()) out.add(Component.translatable("tooltip.create_galore_fishing.needs.reel"));
        if (line.isEmpty()) out.add(Component.translatable("tooltip.create_galore_fishing.needs.line"));
        else if (lineMeters() < 5) out.add(Component.translatable("tooltip.create_galore_fishing.needs.more_line"));
        if (!isLure() && hook.isEmpty()) out.add(Component.translatable("tooltip.create_galore_fishing.needs.hook"));
        return out;
    }

    public boolean canCast() {
        return missing().isEmpty();
    }

    /** Warnings about the weak link and settings that will cost the angler; empty when the rig is balanced. */
    public List<Component> warnings() {
        List<Component> out = new ArrayList<>();
        double test = breakingLoad();
        if (reel.isPresent() && line.isPresent() && drag() > test) {
            out.add(Component.translatable("tooltip.create_galore_fishing.warn.drag_over_line", kg(drag()), kg(test)));
        }
        if (reel.isPresent() && drag() > rodLoad()) {
            out.add(Component.translatable("tooltip.create_galore_fishing.warn.drag_over_rod", kg(drag()), kg(rodLoad())));
        }
        if (isPole() && line.isPresent() && test > rodLoad()) {
            out.add(Component.translatable("tooltip.create_galore_fishing.warn.pole_line", kg(rodLoad())));
        }
        double weight = terminalWeight();
        if (weight > 0 && (weight < rod.lureMin() * 0.7 || weight > rod.lureMax() * 1.3)) {
            out.add(Component.translatable("tooltip.create_galore_fishing.warn.lure_weight",
                    (int) weight, (int) rod.lureMin(), (int) rod.lureMax()));
        }
        if (rodCondition() < 0.25) out.add(Component.translatable("tooltip.create_galore_fishing.warn.worn_rod"));
        if (reel.isPresent() && reelCondition() < 0.25) out.add(Component.translatable("tooltip.create_galore_fishing.warn.worn_reel"));
        if (lineWear() > 0.6) out.add(Component.translatable("tooltip.create_galore_fishing.warn.frayed_line"));
        return out;
    }

    /** Grams of the float, sinker or lure (plus bait, roughly). */
    public double terminalWeight() {
        double w = terminal.map(t -> switch (t) {
            case TacklePart.Bobber f -> f.weight();
            case TacklePart.Sinker s -> s.weight();
            case TacklePart.Lure l -> l.weight();
            default -> 0.0;
        }).orElse(0.0);
        return w + (bait.isPresent() ? 3 : 0);
    }

    /** The rig breaks at the weaker of line and leader. */
    public double breakingLoad() {
        double test = lineTest();
        if (leader.isPresent()) test = Math.min(test, leader.get().test());
        return test;
    }

    /** The heaviest steady pull the rig holds without anything failing: the weakest of rod, line and drag. */
    public double ratedLoad() {
        double load = Math.min(rodLoad(), breakingLoad());
        return reel.map(r -> Math.min(load, r.maxDrag())).orElse(load);
    }

    /**
     * Roughly the heaviest average-fighting fish this rig can land with a sensible drag: the weight whose peak pull
     * equals the rated load.
     */
    public double ratedFishKg() {
        double effective = ratedLoad() / FishFight.AVERAGE.fightPerKg();
        return effective <= 20 ? effective : 20 * Math.exp((effective - 20) / 12);
    }

    /** Farthest cast in metres at full charge: longer rods cast further, a terminal outside the rod's range less. */
    public double maxCast() {
        double fit = 1;
        double w = terminalWeight();
        if (w < rod.lureMin()) fit = Math.max(0.4, w / rod.lureMin());
        else if (w > rod.lureMax()) fit = Math.max(0.4, rod.lureMax() / w);
        double distance = (8 + 7 * rod.length()) * fit;
        return isPole() ? Math.min(distance, rod.fixedLine()) : Math.min(distance, lineMeters());
    }

    /** The fight's view of this rig, for a fish whose mouth gives {@code hookHold}. */
    public Tackle tackle(double hookHold) {
        double hold = Math.min(1.2, hookHold * hook.map(TacklePart.Hook::hold).orElse(1.0));
        if (isPole()) {
            TacklePart.Line l = line.orElseThrow();
            return new Tackle(rodLoad(), rod.action().absorb(), false, 0, 0.9, rodLoad(), 0, rod.fixedLine(),
                    lineTest(), l.stretch(), leader.map(TacklePart.Leader::test).orElse(Double.NaN),
                    leader.map(TacklePart.Leader::steel).orElse(false), hold);
        }
        TacklePart.Reel r = reel.orElseThrow();
        TacklePart.Line l = line.orElseThrow();
        double reelCondition = reelCondition();
        return new Tackle(rodLoad(), rod.action().absorb(), true, r.maxDrag(), r.retrieve(),
                r.power() * (0.7 + 0.3 * reelCondition), r.smooth() * (0.5 + 0.5 * reelCondition), lineMeters(),
                lineTest(), l.stretch(), leader.map(TacklePart.Leader::test).orElse(Double.NaN),
                leader.map(TacklePart.Leader::steel).orElse(false), hold);
    }

    /** Hook size index, or the size that suits a lure's own hooks. */
    public int hookSize() {
        if (hook.isPresent()) return hook.get().size();
        return terminal.filter(t -> t instanceof TacklePart.Lure).map(t -> 2 + 2 * ((TacklePart.Lure) t).size()).orElse(4);
    }

    /** Hold of the hook in a fish this long. */
    public double hookHold(double fishLengthCm) {
        return SizeModel.hookHold(hookSize(), fishLengthCm);
    }

    /** "4.5 kg" with one decimal under 10 kg. */
    public static String kg(double kg) {
        if (Double.isInfinite(kg)) return "-";
        return kg < 10 ? String.format(java.util.Locale.ROOT, "%.1f kg", kg) : String.format(java.util.Locale.ROOT, "%.0f kg", kg);
    }
}
