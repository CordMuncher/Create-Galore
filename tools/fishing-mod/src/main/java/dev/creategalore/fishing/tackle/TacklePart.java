package dev.creategalore.fishing.tackle;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.bait.BaitKind;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * What one tackle item is and its ratings, from {@code data/<ns>/fishing/tackle/**.json}:
 * {@code {"item": "create_galore_fishing:iron_reel", "kind": "reel", "max_drag": 6, ...}}. Any mod's item can be
 * tackle this way (Aquaculture's worm is bait, Hybrid Aquatic's hooks are hooks).
 *
 * <p>Forces are kgf, lengths metres, lure and float weights grams.
 */
public sealed interface TacklePart {
    Kind kind();

    enum Kind implements StringRepresentable {
        ROD, REEL, LINE, LEADER, HOOK, FLOAT, SINKER, LURE, BAIT;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Floats, sinkers and lures share the rod's one terminal slot. */
        public boolean isTerminal() {
            return this == FLOAT || this == SINKER || this == LURE;
        }

        MapCodec<? extends TacklePart> codec() {
            return switch (this) {
                case ROD -> Rod.CODEC;
                case REEL -> Reel.CODEC;
                case LINE -> Line.CODEC;
                case LEADER -> Leader.CODEC;
                case HOOK -> Hook.CODEC;
                case FLOAT -> Bobber.CODEC;
                case SINKER -> Sinker.CODEC;
                case LURE -> Lure.CODEC;
                case BAIT -> Bait.CODEC;
            };
        }
    }

    Codec<TacklePart> CODEC = Kind.CODEC.dispatch("kind", TacklePart::kind, Kind::codec);

    /** Where a rod bends and how much shock it soaks. */
    enum Action implements StringRepresentable {
        /** Bends near the tip; stiff, little cushion. */
        FAST(0.4),
        MODERATE(0.6),
        /** Bends through the whole blank; soft, lots of cushion. */
        SLOW(0.8);

        public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);
        private final double absorb;

        Action(double absorb) {
            this.absorb = absorb;
        }

        public double absorb() {
            return absorb;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Reel families; they look different on the rod and suit different rods. */
    enum ReelStyle implements StringRepresentable {
        SPINNING, BAITCASTER, TROLLING;

        public static final Codec<ReelStyle> CODEC = StringRepresentable.fromEnum(ReelStyle::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A rod blank.
     *
     * @param length     metres; longer casts further
     * @param maxLoad    rated load before the blank is overloaded
     * @param sections   blank sections (drawn on the 3D rod; joined by ferrules, or nested when telescopic)
     * @param lureMin    lightest terminal weight (g) it casts well
     * @param lureMax    heaviest terminal weight (g) it casts well
     * @param reel       false for a pole: no reel seat, a fixed line, no drag
     * @param fixedLine  line length of a pole, metres
     * @param lava       can fish in lava (the line and rig survive it)
     * @param color      blank colour for the 3D model, #rrggbb
     * @param grip       handle colour for the 3D model (cork, EVA foam, wood), #rrggbb
     * @param telescopic the sections nest inside each other and slide out on a cast
     * @param gripStyle  what the handle is made of, for the 3D model: cork, foam, wood or bamboo
     * @param finish     the blank's surface for the 3D model: paint, wood or bamboo
     * @param perk       a special rod's bonus, from Tide's special rods: echo (names the fish on a bite), midas
     *                   (+1 luck, bonus gold), prismarine (bites faster and +1 luck in rain), sunflower (+1 luck in
     *                   sunlight), village (bonus loot) or blazing (Nether fish in any lava)
     */
    record Rod(double length, double maxLoad, Action action, int sections, double lureMin, double lureMax,
               boolean reel, double fixedLine, boolean lava, String color, String grip, boolean telescopic,
               String gripStyle, String finish, Optional<String> perk)
            implements TacklePart {
        static final MapCodec<Rod> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("length").forGetter(Rod::length),
                Codec.DOUBLE.fieldOf("max_load").forGetter(Rod::maxLoad),
                Action.CODEC.optionalFieldOf("action", Action.MODERATE).forGetter(Rod::action),
                Codec.INT.optionalFieldOf("sections", 2).forGetter(Rod::sections),
                Codec.DOUBLE.optionalFieldOf("lure_min", 2.0).forGetter(Rod::lureMin),
                Codec.DOUBLE.optionalFieldOf("lure_max", 30.0).forGetter(Rod::lureMax),
                Codec.BOOL.optionalFieldOf("reel", true).forGetter(Rod::reel),
                Codec.DOUBLE.optionalFieldOf("fixed_line", 0.0).forGetter(Rod::fixedLine),
                Codec.BOOL.optionalFieldOf("lava", false).forGetter(Rod::lava),
                Codec.STRING.optionalFieldOf("color", "#8a6a45").forGetter(Rod::color),
                Codec.STRING.optionalFieldOf("grip", "#c9a26b").forGetter(Rod::grip),
                Codec.BOOL.optionalFieldOf("telescopic", false).forGetter(Rod::telescopic),
                Codec.STRING.optionalFieldOf("grip_style", "cork").forGetter(Rod::gripStyle),
                Codec.STRING.optionalFieldOf("finish", "paint").forGetter(Rod::finish),
                Codec.STRING.optionalFieldOf("perk").forGetter(Rod::perk)
        ).apply(i, Rod::new));

        public boolean has(String perk) {
            return this.perk.map(perk::equals).orElse(false);
        }

        @Override
        public Kind kind() {
            return Kind.ROD;
        }
    }

    /**
     * A reel.
     *
     * @param maxDrag   strongest drag setting
     * @param dragSteps clicks on the drag knob from 0 to max (each scroll is one step)
     * @param retrieve  metres of line wound per second at full speed with no load
     * @param power     pull the crank can still wind against
     * @param smooth    0..1; a smooth drag catches spikes before they reach the line
     * @param capacity  metres of line the spool holds
     */
    record Reel(double maxDrag, int dragSteps, double retrieve, double power, double smooth, double capacity,
                ReelStyle style, String color) implements TacklePart {
        static final MapCodec<Reel> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("max_drag").forGetter(Reel::maxDrag),
                Codec.INT.optionalFieldOf("drag_steps", 10).forGetter(Reel::dragSteps),
                Codec.DOUBLE.fieldOf("retrieve").forGetter(Reel::retrieve),
                Codec.DOUBLE.fieldOf("power").forGetter(Reel::power),
                Codec.DOUBLE.optionalFieldOf("smooth", 0.5).forGetter(Reel::smooth),
                Codec.DOUBLE.fieldOf("capacity").forGetter(Reel::capacity),
                ReelStyle.CODEC.optionalFieldOf("style", ReelStyle.SPINNING).forGetter(Reel::style),
                Codec.STRING.optionalFieldOf("color", "#9a9a9a").forGetter(Reel::color)
        ).apply(i, Reel::new));

        @Override
        public Kind kind() {
            return Kind.REEL;
        }

        /** Drag in kgf at knob position {@code step}. */
        public double dragAt(int step) {
            return maxDrag * Math.max(0, Math.min(step, dragSteps)) / dragSteps;
        }
    }

    /**
     * A spool of main line.
     *
     * @param test       breaking load
     * @param stretch    0..1; stretchy line soaks shocks
     * @param visibility 0..1; wary fish see thick or coloured line
     * @param meters     metres on a new spool
     */
    record Line(double test, double stretch, double visibility, int meters, String color) implements TacklePart {
        static final MapCodec<Line> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("test").forGetter(Line::test),
                Codec.DOUBLE.optionalFieldOf("stretch", 0.5).forGetter(Line::stretch),
                Codec.DOUBLE.optionalFieldOf("visibility", 0.5).forGetter(Line::visibility),
                Codec.INT.optionalFieldOf("meters", 100).forGetter(Line::meters),
                Codec.STRING.optionalFieldOf("color", "#e0e0e0").forGetter(Line::color)
        ).apply(i, Line::new));

        @Override
        public Kind kind() {
            return Kind.LINE;
        }
    }

    /** The short length of line at the hook: a clear one hides the rig, a steel one stops teeth. */
    record Leader(double test, double visibility, boolean steel, String color) implements TacklePart {
        static final MapCodec<Leader> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("test").forGetter(Leader::test),
                Codec.DOUBLE.optionalFieldOf("visibility", 0.3).forGetter(Leader::visibility),
                Codec.BOOL.optionalFieldOf("steel", false).forGetter(Leader::steel),
                Codec.STRING.optionalFieldOf("color", "#c8d8e0").forGetter(Leader::color)
        ).apply(i, Leader::new));

        @Override
        public Kind kind() {
            return Kind.LEADER;
        }
    }

    /**
     * A hook.
     *
     * @param size   index into {@link dev.creategalore.fishing.sim.SizeModel#HOOK_SIZES} (#12 = 0 ... 8/0 = 11)
     * @param hold   multiplier on how well it holds (barbed hooks hold better)
     * @param luck   added to the angler's luck while it's on the rod
     * @param effect special behaviour: lava, fiery, permafrost, twilight, void, glowing, magnetic, barbed,
     *               creeper_magnet, ominous, manglerfish (see the fishing code for what each does)
     */
    record Hook(int size, double hold, int luck, Optional<String> effect) implements TacklePart {
        static final MapCodec<Hook> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("size").forGetter(Hook::size),
                Codec.DOUBLE.optionalFieldOf("hold", 1.0).forGetter(Hook::hold),
                Codec.INT.optionalFieldOf("luck", 0).forGetter(Hook::luck),
                Codec.STRING.optionalFieldOf("effect").forGetter(Hook::effect)
        ).apply(i, Hook::new));

        @Override
        public Kind kind() {
            return Kind.HOOK;
        }

        public boolean is(String name) {
            return effect.map(name::equals).orElse(false);
        }
    }

    /** A float (bobber): holds bait at the set depth and shows bites by dipping. JSON kind "float". */
    record Bobber(double weight, String color) implements TacklePart {
        static final MapCodec<Bobber> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("weight", 4.0).forGetter(Bobber::weight),
                Codec.STRING.optionalFieldOf("color", "#d03030").forGetter(Bobber::color)
        ).apply(i, Bobber::new));

        @Override
        public Kind kind() {
            return Kind.FLOAT;
        }
    }

    /** A sinker: takes bait to the bottom; bites show as a twitch of the rod tip. */
    record Sinker(double weight) implements TacklePart {
        static final MapCodec<Sinker> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("weight", 15.0).forGetter(Sinker::weight)
        ).apply(i, Sinker::new));

        @Override
        public Kind kind() {
            return Kind.SINKER;
        }
    }

    /**
     * A lure: needs no bait, is worked by reeling, is kept after a catch (but lost with the line).
     *
     * @param dive     metres it dives on the retrieve or when trolled
     * @param speedMin slowest retrieve or trolling speed (m/s) at which it works
     * @param speedMax fastest
     * @param size     0 small, 1 medium, 2 large: picks fish size like a bait does
     */
    record Lure(BaitKind bait, double weight, double dive, double speedMin, double speedMax, int size, String color)
            implements TacklePart {
        static final MapCodec<Lure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BaitKind.CODEC.fieldOf("bait").forGetter(Lure::bait),
                Codec.DOUBLE.optionalFieldOf("weight", 10.0).forGetter(Lure::weight),
                Codec.DOUBLE.optionalFieldOf("dive", 0.5).forGetter(Lure::dive),
                Codec.DOUBLE.optionalFieldOf("speed_min", 0.3).forGetter(Lure::speedMin),
                Codec.DOUBLE.optionalFieldOf("speed_max", 2.0).forGetter(Lure::speedMax),
                Codec.INT.optionalFieldOf("size", 1).forGetter(Lure::size),
                Codec.STRING.optionalFieldOf("color", "#c0c0c0").forGetter(Lure::color)
        ).apply(i, Lure::new));

        @Override
        public Kind kind() {
            return Kind.LURE;
        }
    }

    /**
     * A bait item. One is used up per fish landed.
     *
     * @param size 0 small, 1 medium, 2 large
     * @param live a live bait (from the Live-Bait Bucket): predators like it much better
     * @param luck   added to the angler's luck (Tide's Lucky Bait)
     * @param effect {@code magnetic}: crates and treasure come up more often (Tide's Magnetic Bait)
     */
    record Bait(BaitKind bait, int size, boolean live, int luck, Optional<String> effect) implements TacklePart {
        static final MapCodec<Bait> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BaitKind.CODEC.fieldOf("bait").forGetter(Bait::bait),
                Codec.INT.optionalFieldOf("size", 0).forGetter(Bait::size),
                Codec.BOOL.optionalFieldOf("live", false).forGetter(Bait::live),
                Codec.INT.optionalFieldOf("luck", 0).forGetter(Bait::luck),
                Codec.STRING.optionalFieldOf("effect").forGetter(Bait::effect)
        ).apply(i, Bait::new));

        public boolean is(String effect) {
            return this.effect.map(effect::equals).orElse(false);
        }

        /** The same bait kept alive in a Live-Bait Bucket: a baitfish swims (live fish, not cut bait). */
        public Bait alive() {
            return new Bait(bait == BaitKind.CUT_BAIT ? BaitKind.LIVE_FISH : bait, size, true, luck, effect);
        }

        @Override
        public Kind kind() {
            return Kind.BAIT;
        }
    }
}
