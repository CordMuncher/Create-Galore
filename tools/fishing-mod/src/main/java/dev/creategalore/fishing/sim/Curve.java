package dev.creategalore.fishing.sim;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * How hard a fish fights over the course of a fight, as a piecewise-linear curve.
 *
 * <p>The x axis is fight <em>progress</em>: 0 when the fish is hooked, 1 when it reaches the angler
 * (progress = 1 - distance / starting distance). The y axis is fight intensity, where 1 is the fish's full
 * strength. This is the cheap stand-in for real fish behaviour: an "early" fighter is strongest right after the
 * hook-set and comes in easier later, a "middle" fighter lets itself be dragged a bit and then fights halfway,
 * a "second wind" fish fights again when it nears the surface. A 50 m fight with a middle fighter peaks around
 * 25 m out, a 20 m fight around 10 m out: the curve is relative to where the fight started.
 *
 * <p>Presets cover most fish; data files may also give their own points.
 */
public final class Curve {
    private final double[] xs;
    private final double[] ys;

    /** Points must have x in [0, 1], sorted ascending; y is the intensity there. */
    public Curve(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length == 0) throw new IllegalArgumentException("curve needs matching, non-empty points");
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] < xs[i - 1]) throw new IllegalArgumentException("curve x values must be sorted");
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
    }

    /** Intensity at {@code progress}, clamped to the curve's ends. */
    public double at(double progress) {
        if (progress <= xs[0]) return ys[0];
        int last = xs.length - 1;
        if (progress >= xs[last]) return ys[last];
        int i = 1;
        while (xs[i] < progress) i++;
        double t = (progress - xs[i - 1]) / (xs[i] - xs[i - 1]);
        return ys[i - 1] + t * (ys[i] - ys[i - 1]);
    }

    public double[] xs() {
        return xs.clone();
    }

    public double[] ys() {
        return ys.clone();
    }

    /** The named shapes data files can use instead of points. */
    public static final Map<String, Curve> PRESETS = Map.of(
            // Strongest right after the hook-set, fades as it comes in (pike, many small fish).
            "early", new Curve(new double[]{0, 0.25, 0.7, 1}, new double[]{1, 0.85, 0.45, 0.35}),
            // Comes along at first, fights hardest about halfway (carp, bass).
            "middle", new Curve(new double[]{0, 0.2, 0.5, 0.8, 1}, new double[]{0.5, 0.6, 1, 0.6, 0.45}),
            // Easy at first, hardest near the angler (walleye, flatfish).
            "late", new Curve(new double[]{0, 0.4, 0.8, 1}, new double[]{0.4, 0.55, 1, 0.9}),
            // Hard start, a lull, then fights again near the surface (salmon, tuna, sharks).
            "second_wind", new Curve(new double[]{0, 0.3, 0.6, 0.85, 1}, new double[]{0.95, 0.65, 0.45, 1, 0.7}),
            // About the same the whole way.
            "steady", new Curve(new double[]{0, 1}, new double[]{0.8, 0.75}),
            // Heavy and stubborn all the way, never a sprint (catfish, sturgeon, rays).
            "sulker", new Curve(new double[]{0, 0.5, 1}, new double[]{0.85, 0.9, 0.8}));

    /** A preset by name, case-insensitive; {@code null} if there is none. */
    public static Curve preset(String name) {
        return PRESETS.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "Curve" + Arrays.toString(xs) + "->" + Arrays.toString(ys);
    }
}
