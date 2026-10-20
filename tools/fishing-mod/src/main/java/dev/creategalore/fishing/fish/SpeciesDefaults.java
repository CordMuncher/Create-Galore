package dev.creategalore.fishing.fish;

import dev.creategalore.fishing.sim.Curve;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.tide.data.TideFishData;
import java.util.List;
import java.util.Map;

/**
 * Sensible values for fish that have no fight or weight data of their own, worked out from Tide's numbers and the
 * fish's name. Around 180 fish bite in the pack; this keeps hand-written data to the ones that deserve it.
 *
 * <p>Name matching uses whole words of the item path ({@code golden_dorado} has the words "golden" and "dorado"),
 * first match wins, so specific words come before general ones.
 */
public final class SpeciesDefaults {
    private SpeciesDefaults() {
    }

    /** Length-weight coefficient {@code a} (g/cm^3) by body shape; see {@link dev.creategalore.fishing.sim.SizeModel}. */
    private static final List<Map.Entry<List<String>, Double>> SHAPES = List.of(
            entry(0.0015, "eel", "lamprey", "oarfish"),
            entry(0.0025, "needlefish", "crawler", "pipefish"),
            entry(0.003, "jellyfish", "larva"),
            entry(0.004, "ray", "stingray", "mantyvern", "seahorse"),
            entry(0.005, "sturgeon", "swordfish", "sailfish", "marstilus"),
            entry(0.006, "pike", "gar", "muskellunge", "barracuda", "arapaima"),
            entry(0.007, "shark"),
            entry(0.008, "squid", "cuttlefish", "pentapus", "shrimp", "mooneye", "pollock", "ratfish"),
            entry(0.009, "catfish", "bayad", "walleye", "mackerel", "mahi", "cod", "herring", "anchovy"),
            entry(0.04, "puffer", "pufferfish", "blowfish", "boxfish", "snail", "blobfish"),
            entry(0.05, "turtle", "sunfish_ocean"),
            entry(0.03, "piranha", "tambaqui", "opah", "discus", "angelfish", "triggerfish", "dory"),
            entry(0.025, "bluegill", "sunfish", "damselfish", "surgeonfish", "cichlid", "butterflyfish"),
            entry(0.018, "carp", "koi", "goldfish", "crappie", "gourami", "parrotfish", "trevally"),
            entry(0.016, "grouper", "snapper", "rockfish", "tuna", "coelacanth", "wrasse", "stonefish"),
            entry(0.013, "bass", "perch", "flounder", "halibut", "pleco", "synodontis", "snook"),
            entry(0.011, "trout", "salmon", "char", "dorado"));

    /** Feeding habits by name, checked before the group-based fallback. */
    private static final List<Map.Entry<List<String>, Feeding>> FEEDING = List.of(
            entryF(Feeding.BOTTOM, "carp", "koi", "goldfish", "catfish", "sturgeon", "pleco", "synodontis", "bayad", "tambaqui"),
            entryF(Feeding.SEA_BOTTOM, "flounder", "halibut", "ray", "stingray", "cod", "pollock", "ratfish", "stonefish", "crawler", "snail"),
            entryF(Feeding.PANFISH, "bluegill", "crappie", "minnow", "guppy", "tetra", "danio", "barb", "betta", "gourami",
                    "cichlid", "discus", "sunfish", "perch", "boulti", "capitaine", "darter", "pupfish"),
            entryF(Feeding.SEA_PREDATOR, "tuna", "mackerel", "mahi", "sailfish", "swordfish", "shark", "trevally",
                    "barracuda", "lionfish", "moray", "grouper", "snapper", "needlefish", "anglerfish", "dragonfish",
                    "coelacanth", "marstilus", "opah", "herring", "anchovy"),
            entryF(Feeding.PREDATOR, "pike", "bass", "walleye", "trout", "salmon", "muskellunge", "gar", "arapaima",
                    "piranha", "dorado", "snook", "mooneye", "eel"));

    /** Fish known for jumping or head-shaking on the line. */
    private static final List<String> JUMPERS = List.of("trout", "salmon", "bass", "sailfish", "swordfish", "mahi",
            "mooneye", "snook", "dorado", "arapaima", "flying");
    /** Teeth that cut line: needs a steel leader. */
    private static final List<String> TOOTHY = List.of("pike", "muskellunge", "gar", "piranha", "barracuda", "shark",
            "moray", "fangjaw", "anglerfish", "dragonfish", "snatcher", "eater", "lionfish", "payara", "wolf");
    /** Shy fish that notice thick or visible line. */
    private static final List<String> WARY = List.of("trout", "carp", "koi", "bonefish", "salmon", "char", "grayling");

    private static Map.Entry<List<String>, Double> entry(double a, String... words) {
        return Map.entry(List.of(words), a);
    }

    private static Map.Entry<List<String>, Feeding> entryF(Feeding f, String... words) {
        return Map.entry(List.of(words), f);
    }

    private static boolean has(List<String> words, List<String> keys) {
        for (String key : keys) {
            if (words.contains(key)) return true;
        }
        return false;
    }

    static List<String> words(String path) {
        return List.of(path.replace("raw_", "").split("_"));
    }

    /** {@code a} from the body shape, 0.011 (a trout) when the name says nothing. */
    public static double lengthWeightA(String path) {
        List<String> words = words(path);
        if (path.contains("ocean_sunfish")) return 0.05;
        for (var e : SHAPES) {
            if (has(words, e.getKey())) return e.getValue();
        }
        return 0.011;
    }

    public static Feeding feeding(String path, TideFishData tide) {
        switch (tide.profile().group()) {
            case LAVA -> {
                return Feeding.LAVA;
            }
            case VOID -> {
                return Feeding.VOID;
            }
            default -> {
            }
        }
        if ("lava".equals(tide.medium())) return Feeding.LAVA;
        if ("void".equals(tide.medium())) return Feeding.VOID;
        List<String> words = words(path);
        for (var e : FEEDING) {
            if (has(words, e.getKey())) return e.getValue();
        }
        boolean salt = tide.profile().group() == TideFishData.Group.SALTWATER;
        double typical = tide.size().map(TideFishData.Size::typicalHighCm).orElse(60.0);
        if (typical < 25) return salt ? Feeding.REEF : Feeding.PANFISH;
        if (tide.strength() >= 0.6) return salt ? Feeding.SEA_PREDATOR : Feeding.PREDATOR;
        return salt ? Feeding.REEF : Feeding.PANFISH;
    }

    /** The fight Tide's minigame numbers suggest, adjusted by what the name tells about the fish. */
    public static FishFight fight(String path, TideFishData tide) {
        List<String> words = words(path);
        double s = tide.strength();
        double v = tide.speed();
        Curve profile = Curve.preset(switch (tide.behavior()) {
            case PLATEAU -> "sulker";
            case JITTER -> "early";
            case DARTS -> "second_wind";
            case LINEAR -> "steady";
            case LINEAR_WRAP -> "late";
            case SINE -> "middle";
        });
        double runs = switch (tide.behavior()) {
            case DARTS -> 0.14;
            case PLATEAU -> 0.03;
            default -> 0.05;
        } * (0.6 + 0.4 * v);
        double jumps = has(words, JUMPERS) ? 0.12 : tide.behavior() == TideFishData.Behavior.PLATEAU ? 0.01 : 0.04;
        double charges = tide.behavior() == TideFishData.Behavior.JITTER ? 0.08 : 0.05;
        return new FishFight(0.45 + 0.8 * s, 0.7 + 0.8 * s, 0.3 + 0.9 * v, profile, runs, jumps, charges,
                has(words, TOOTHY));
    }

    public static double wary(String path) {
        return has(words(path), WARY) ? 0.6 : 0.2;
    }

    /** Fillets per kg of fish, before processing skill: deep, meaty fish give more. */
    public static double filletYield(String path) {
        List<String> words = words(path);
        if (has(words, List.of("tuna", "swordfish", "salmon", "halibut", "cod", "grouper", "mahi"))) return 0.55;
        if (has(words, List.of("jellyfish", "puffer", "pufferfish", "blowfish", "snail", "larva"))) return 0.1;
        return 0.4;
    }

    public static boolean oily(String path) {
        return has(words(path), List.of("tuna", "salmon", "mackerel", "herring", "anchovy", "sardine", "eel", "trout"));
    }
}
