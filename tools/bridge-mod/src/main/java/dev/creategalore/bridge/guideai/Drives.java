package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Create's parts for changing a drive line's speed, direction or on and off, and which of them the asking player can
 * make now. Checks that tell the book how to change a speed name every way this player has (a few cog steps, a
 * Rotation Speed Controller, a chain gearshift) and none they can't make yet, so the book weighs them for the build in
 * hand instead of reaching for cogs because cogs are all it was ever told about.
 */
final class Drives {
    /** A part and what it is for, in a few words. */
    record Part(String id, String job) {
        Item item() {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        }

        String name() {
            return item().getDescription().getString();
        }
    }

    static final Part GEARBOX = new Part("create:gearbox", "corners and reverses a line in one block");
    static final Part CHAIN_DRIVE = new Part("create:encased_chain_drive",
            "carries rotation sideways along a row of them, same speed");
    static final Part CLUTCH = new Part("create:clutch", "cuts the line while powered");
    static final Part GEARSHIFT = new Part("create:gearshift", "reverses the line while powered");
    static final Part CHAIN_GEARSHIFT = new Part("create:adjustable_chain_gearshift",
            "speeds the rest of its chain drive row up while powered, to twice at signal 15");
    static final Part SPEED_CONTROLLER = new Part("create:rotation_speed_controller",
            "sets the large cogwheel on top of it to any speed up to 256 RPM, whatever comes in");
    static final Part SEQUENCED = new Part("create:sequenced_gearshift",
            "runs timed turns and reverses on each redstone pulse, for doors, gantries and pistons that move in steps");
    static final List<Part> ALL = List.of(GEARBOX, CHAIN_DRIVE, CLUTCH, GEARSHIFT, CHAIN_GEARSHIFT, SPEED_CONTROLLER, SEQUENCED);

    /** The player a check is running for, so its advice names only what they can make; null in checks with no player. */
    private static final ThreadLocal<ServerPlayer> ASKING = new ThreadLocal<>();

    private Drives() {
    }

    /** The speed each Rotation Speed Controller of the model being checked is set to (build_model's speeds). */
    private static final ThreadLocal<java.util.Map<BlockPos, Float>> SET = ThreadLocal.withInitial(java.util.Map::of);

    /** Runs {@code body} with {@code player} as the one asking. */
    static <T> T asking(ServerPlayer player, java.util.function.Supplier<T> body) {
        return asking(player, SET.get(), body);
    }

    /** Runs {@code body} with {@code player} asking and the model's speed controllers set as in {@code set}. */
    static <T> T asking(ServerPlayer player, java.util.Map<BlockPos, Float> set, java.util.function.Supplier<T> body) {
        ServerPlayer before = ASKING.get();
        java.util.Map<BlockPos, Float> setBefore = SET.get();
        ASKING.set(player);
        SET.set(set);
        try {
            return body.get();
        } finally {
            ASKING.set(before);
            SET.set(setBefore);
        }
    }

    /** Sets the model's speed controller settings for the rest of the enclosing {@link #asking} call. */
    static void setModel(java.util.Map<BlockPos, Float> set) {
        SET.set(set);
    }

    /** The speed controller settings of the model being checked; empty outside a model. */
    static java.util.Map<BlockPos, Float> set() {
        return SET.get();
    }

    /** Whether the asking player can make {@code part} now; with nobody asking, only the parts every player starts with. */
    static boolean canMake(Part part) {
        ServerPlayer player = ASKING.get();
        if (player == null) return false;
        Item item = part.item();
        return item != Items.AIR && Reach.canMake(player, player.getServer(), item);
    }

    /**
     * The ways to take a line from {@code from} to {@code to} RPM (0 for either when unknown) with what the player can
     * make, for a check's advice: cog steps always, and a speed controller or chain gearshift when they can make one.
     */
    static String change(float from, float to) {
        return ways(from, to, true);
    }

    /** The ways besides cogs, for a line already geared by cogs; empty when the player can make none of them. */
    static String besidesCogs(float from, float to) {
        return ways(from, to, false);
    }

    private static String ways(float from, float to, boolean cogs) {
        float a = Math.abs(from), b = Math.abs(to);
        boolean up = a <= 0 || b <= 0 || b >= a;
        float ratio = a > 0 && b > 0 ? Math.max(a, b) / Math.min(a, b) : 0;
        int steps = ratio > 0 ? Math.max(1, (int) Math.ceil(Math.log(ratio) / Math.log(2) - 1e-6)) : 0;
        List<String> ways = new ArrayList<>();
        String one = up ? "a large cogwheel driving a small one doubles the speed" : "a small cogwheel driving a large one halves it";
        if (cogs) ways.add(steps > 1 ? steps + " cog steps (" + one + " each time" + (Math.abs(ratio - (1 << steps)) > 0.01
                ? ", so cogs give only doubles and halves" : "") + ")" : one);
        if (canMake(SPEED_CONTROLLER))
            ways.add("one Rotation Speed Controller" + (b > 0 ? " set to " + trim(b) + " RPM" : "") + " under a large cogwheel, "
                    + "any speed up to 256 in one block");
        if (canMake(CHAIN_GEARSHIFT) && up && (ratio == 0 || ratio <= 2.01))
            ways.add("an Adjustable Chain Gearshift driving an Encased Chain Drive beside it, powered by redstone, up to twice "
                    + "the speed at signal 15");
        if (ways.size() < 2) return String.join("", ways);
        return String.join("; or ", ways) + "; the player can make " + (cogs ? "each" : "these") + ", so use whichever suits the build";
    }

    /** One line for the player snapshot: the drive parts beyond shafts and cogs they can make now, and the ones not yet. */
    static String snapshot(ServerPlayer player) {
        return asking(player, () -> {
            List<String> can = new ArrayList<>(), not = new ArrayList<>();
            for (Part p : ALL) {
                if (p.item() == Items.AIR) continue;
                if (canMake(p)) can.add(p.name() + " (" + p.job() + ")");
                else not.add(p.name());
            }
            if (can.isEmpty()) return "";
            return "Besides shafts and cogwheels they can make: " + String.join("; ", can) + "."
                    + (!not.isEmpty() ? " Not yet: " + String.join(", ", not) + "." : "")
                    + " Weigh these against cog steps for each job and use whichever suits the build.";
        });
    }

    private static String trim(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.valueOf(f);
    }
}
