package dev.creategalore.fishing.fish;

import dev.creategalore.fishing.cast.TackleWear;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.Curve;
import dev.creategalore.fishing.sim.MonteCarlo;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.sim.TacklePresets;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Self-test suites for the species data: {@code species} (data sanity) and {@code fight} (balance bands). */
public final class SpeciesSelfTest {
    private SpeciesSelfTest() {
    }

    public static void register() {
        SelfTest.suite("species", (server, r) -> {
            List<Species> all = SpeciesManager.all();
            r.check(all.size() >= 100, "at least 100 species loaded (" + all.size() + ")");
            r.check(SpeciesManager.problems().isEmpty(), "no fishing data files failed ("
                    + SpeciesManager.problems().size() + ")");
            SpeciesManager.problems().forEach(p -> r.info("problem: " + p));
            for (String id : List.of("minecraft:cod", "minecraft:salmon", "aquaculture:smallmouth_bass",
                    "aquaculture:tuna", "hybrid_aquatic:mackerel", "hybrid_aquatic:coelacanth")) {
                ResourceLocation rl = ResourceLocation.parse(id);
                if (!ModList.get().isLoaded(rl.getNamespace()) && !rl.getNamespace().equals("minecraft")) continue;
                r.check(SpeciesManager.byId(rl).isPresent(), "species " + id + " is loaded");
            }
            // Merged duplicates resolve to their canonical species (the pack sends Tide's carp to Aquaculture's).
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("tide:carp")).ifPresent(item ->
                    r.check(SpeciesManager.of(item).map(s -> s.id().toString()).orElse("").equals("aquaculture:carp"),
                            "tide:carp counts as aquaculture:carp"));
            int bad = 0;
            for (Species s : all) {
                double record = s.size().weightKg(s.size().record());
                boolean ok = s.fight().fightPerKg() > 0 && s.fight().stamina() > 0 && s.fight().speed() > 0
                        && Double.isFinite(record) && record > 0 && s.size().typicalLow() > 0;
                if (!ok) {
                    bad++;
                    r.fail("species " + s.id() + " has bad numbers: " + s.fight() + " " + s.size());
                }
            }
            r.check(bad == 0, "every species has positive, finite fight and size numbers");
            for (Species s : all) {
                r.info(String.format(Locale.ROOT, "%-40s %-10s %-12s %6.1f-%6.1f cm  %8.2f-%8.2f kg (record %9.1f kg)  per_kg %.2f",
                        s.id(), s.medium(), s.feeding().getSerializedName(), s.size().typicalLow(), s.size().typicalHigh(),
                        s.size().weightKg(s.size().typicalLow()), s.size().weightKg(s.size().typicalHigh()),
                        s.size().weightKg(s.size().record()), s.fight().fightPerKg()));
            }
        });

        SelfTest.suite("fight", (server, r) -> {
            // Fixed seeds: the bands below are for the shipped data and presets.
            for (Curve c : Curve.PRESETS.values()) r.check(c.at(0) > 0 && c.at(1) > 0, "preset curve positive: " + c);
            check(r, "minecraft:cod", "starter", 0.80, 1.0);
            check(r, "aquaculture:smallmouth_bass", "starter", 0.85, 1.0);
            check(r, "aquaculture:tuna", "starter", 0.0, 0.35);
            check(r, "aquaculture:tuna", "boat", 0.6, 1.0);
            reelWear(r, "minecraft:cod", "starter", 300);
            reelWear(r, "minecraft:salmon", "starter", 300);
        });
    }

    /**
     * A player who never touches the drag and holds use the whole fight cranks through a slipping drag: the reel
     * grinds, but one fight takes at most a tenth of it ({@link TackleWear#MAX_REEL_WEAR_PER_FIGHT}).
     */
    private static void reelWear(SelfTest.Report r, String id, String rig, int reelDurability) {
        var species = SpeciesManager.byId(ResourceLocation.parse(id));
        if (species.isEmpty()) {
            r.info("skipped " + id + " (not loaded)");
            return;
        }
        Species s = species.get();
        dev.creategalore.fishing.sim.Angler holdsUse = new dev.creategalore.fishing.sim.Angler() {
            @Override
            public void act(dev.creategalore.fishing.sim.Fight fight, dev.creategalore.fishing.sim.Controls c) {
                c.drag = fight.tackle().reelMaxDrag() / 3;
                c.reeling = true;
                c.crankThroughSlip = true;
                c.rodAngle = Math.toRadians(40);
            }

            @Override
            public String name() {
                return "holds_use";
            }
        };
        MonteCarlo.Result res = MonteCarlo.run(new MonteCarlo.Species(id, s.fight(), s.size()),
                TacklePresets.ALL.get(rig), () -> holdsUse, 200, 7);
        int raw = 1 + (int) Math.round(res.avgReelWear() * 3);
        int damage = TackleWear.reelDamage(res.avgReelWear(), reelDurability);
        r.check(damage <= Math.ceil(reelDurability * TackleWear.MAX_REEL_WEAR_PER_FIGHT), String.format(Locale.ROOT,
                "%s on %s, drag untouched and use held: a fight takes %d of the reel's %d (uncapped %d), landed %.0f%%",
                id, rig, damage, reelDurability, raw, res.rate(Outcome.LANDED) * 100));
    }

    private static void check(SelfTest.Report r, String id, String rig, double minLand, double maxLand) {
        var species = SpeciesManager.byId(ResourceLocation.parse(id));
        if (species.isEmpty()) {
            r.info("skipped " + id + " (not loaded)");
            return;
        }
        Species s = species.get();
        MonteCarlo.Result res = MonteCarlo.run(new MonteCarlo.Species(id, s.fight(), s.size()),
                TacklePresets.ALL.get(rig), dev.creategalore.fishing.sim.Angler::careful, 300, 7);
        double land = res.rate(Outcome.LANDED);
        r.check(land >= minLand && land <= maxLand, String.format(Locale.ROOT,
                "%s on %s lands %.0f%% (want %.0f-%.0f%%), %.0f s average", id, rig, land * 100, minLand * 100,
                maxLand * 100, res.avgSeconds()));
    }
}
