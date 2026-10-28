package dev.creategalore.bridge.dlss;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the patched Photon ({@link PhotonPatcher}) leaves raw for DLSS Ray Reconstruction, one switch per effect, from
 * the dlaaRrRaw setting (a list of the switches' names; "none" for none). Rough reflections and soft shadows are raw by
 * default, as Ray Reconstruction ships; the others are for the Photon RR research (dev builds): each takes away one of
 * Photon's own ways of hiding its noise (a history, a noise pattern, a lower resolution), so the pictures show what Ray
 * Reconstruction cleans up by itself and what it still needs help with. Each is a uniform (1: raw), so it flips on the
 * next frame without a shader reload, and only while Ray Reconstruction runs or a debug view shows its inputs. The test
 * kit flips them with its hotkeys and /dlaatest rr, and sets them for its modes ({@link #use}).
 */
public final class RrResearch {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    public enum Switch {
        REFLECTIONS("reflections", "cg_rr_raw_reflections", "Rough reflections",
                "one ray from white noise, on Photon's own rough materials too", "Photon traces none without a resource pack's specular maps"),
        REFLECTION_RAYS("reflection-rays", "cg_rr_reflection_rays", "Reflection rays",
                "raw rough reflections trace Photon's Ray Count, each from white noise", "one ray"),
        SHADOWS("shadows", "cg_rr_raw_shadows", "Soft shadows",
                "white noise each frame", "Photon's blue noise, stepped each frame"),
        SHADOW_RAYS("shadow-rays", "cg_rr_raw_shadow_rays", "Contact shadow rays",
                "screen-space shadow rays turn with white noise each frame", "a turn per pixel that never changes"),
        AO_HISTORY("ao-history", "cg_rr_raw_ao_history", "AO history",
                "ambient occlusion from this frame only", "Photon blends 10 frames"),
        AO_NOISE("ao-noise", "cg_rr_raw_ao_noise", "AO noise",
                "ambient occlusion from white noise", "Photon's blue noise, stepped each frame"),
        FOG("fog", "cg_rr_raw_fog", "Volumetric fog",
                "fog, light shafts and coloured light in the air from white noise", "Photon's blue noise, stepped each frame"),
        CLOUDS("clouds", "cg_rr_raw_clouds", "Clouds",
                "white noise and no history (with Temporal Upscaling above 1x each pixel holds its last sample)",
                "blended over frames, a few pixels of each block a frame"),
        DOF_AFTER("dof-after", "cg_rr_dof_after", "Depth of field after RR",
                "Photon's depth of field after Ray Reconstruction, as NVIDIA's guide has it (needs Depth of Field on)", "before it");

        public final String token;
        public final String uniform;
        public final String label;
        /** What raw means for this effect, and what Photon does instead. */
        public final String raw;
        public final String photon;

        Switch(String token, String uniform, String label, String raw, String photon) {
            this.token = token;
            this.uniform = uniform;
            this.label = label;
            this.raw = raw;
            this.photon = photon;
        }
    }

    /** The switches that take away one of Photon's own ways of hiding noise ("all"); the others are variants. */
    private static final Set<Switch> STRIPS = EnumSet.of(Switch.REFLECTIONS, Switch.SHADOWS, Switch.SHADOW_RAYS, Switch.AO_HISTORY,
            Switch.AO_NOISE, Switch.FOG, Switch.CLOUDS);

    /** dlaaRrRaw's default: what Ray Reconstruction ships with. */
    public static final String DEFAULT = "reflections,shadows";

    private static volatile Set<Switch> raw = parse(DEFAULT);
    /** The dlaaRrRaw value last read, so a reload keeps switches flipped in game since. */
    private static String lastSetting = DEFAULT;

    private RrResearch() {}

    public static boolean raw(Switch which) {
        return raw.contains(which);
    }

    /** From dlaaRrRaw when shaders reload: only if the setting changed since it was last read. */
    static synchronized void load(String setting) {
        String normalized = setting.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(lastSetting)) {
            lastSetting = normalized;
            raw = parse(normalized);
        }
    }

    /** For the test kit: these switches raw (dlaaRrRaw's form; null for the default), the rest Photon's own, now. */
    public static synchronized String use(String setting) {
        raw = parse(setting == null ? DEFAULT : setting);
        LOG.info("DLAA: Ray Reconstruction research: {}", describe());
        return describe();
    }

    /**
     * For the test kit's hotkeys and command: one switch (its name), or "all", raw (true), Photon's own (false) or flipped
     * (null). The message says what it is now.
     */
    public static synchronized String set(String token, Boolean wanted) {
        EnumSet<Switch> next = raw.isEmpty() ? EnumSet.noneOf(Switch.class) : EnumSet.copyOf(raw);
        if ("all".equalsIgnoreCase(token)) {
            // Every switch that takes away one of Photon's ways of hiding noise; the variants (more rays, depth of field
            // after RR) stay as they are.
            boolean on = wanted != null ? wanted : !next.containsAll(STRIPS);
            if (on) {
                next.addAll(STRIPS);
            } else {
                next.removeAll(STRIPS);
            }
            raw = next;
            LOG.info("DLAA: Ray Reconstruction research: {}", describe());
            return on ? "every effect raw" : "every effect Photon's own";
        }
        Switch which = find(token);
        if (which == null) {
            return "no switch called " + token + " (" + String.join(", ", tokens()) + ", all)";
        }
        boolean on = wanted != null ? wanted : !next.contains(which);
        if (on) {
            next.add(which);
        } else {
            next.remove(which);
        }
        raw = next;
        LOG.info("DLAA: Ray Reconstruction research: {} {}", which.token, on ? "raw" : "Photon's own");
        return state(which);
    }

    /** For the test kit: {@link #state(Switch)} by the switch's name. */
    public static String state(String token) {
        Switch which = find(token);
        return which == null ? "no switch called " + token : state(which);
    }

    /** "Label: raw (what that means)" or "Label: Photon's own (what it does)". */
    public static String state(Switch which) {
        return raw(which) ? which.label + ": raw (" + which.raw + ")" : which.label + ": Photon's own (" + which.photon + ")";
    }

    /** The switches' names, in order. */
    public static List<String> tokens() {
        List<String> names = new ArrayList<>();
        for (Switch s : Switch.values()) {
            names.add(s.token);
        }
        return names;
    }

    public static Switch find(String token) {
        for (Switch s : Switch.values()) {
            if (s.token.equalsIgnoreCase(token.trim())) {
                return s;
            }
        }
        return null;
    }

    /** dlaaRrRaw's form of the switches that are raw now ("none" for none). */
    public static String setting() {
        List<String> names = new ArrayList<>();
        for (Switch s : raw) {
            names.add(s.token);
        }
        return names.isEmpty() ? "none" : String.join(",", names);
    }

    /** "raw: a, b; Photon's own: c, d". */
    public static String describe() {
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (Switch s : Switch.values()) {
            (raw(s) ? on : off).add(s.token);
        }
        return "raw: " + (on.isEmpty() ? "nothing" : String.join(", ", on)) + "; Photon's own: " + (off.isEmpty() ? "nothing" : String.join(", ", off));
    }

    /** For the test kit: Photon's Ultra profile, the settings Ray Reconstruction starts Photon on (option, value). */
    public static Map<String, String> photonUltra() {
        return PhotonRrSettings.ULTRA;
    }

    /** True while the switches apply: Ray Reconstruction runs, or a debug view shows what it would get. */
    public static boolean applies() {
        return Dlss.rayReconstructionGuides();
    }

    private static Set<Switch> parse(String setting) {
        EnumSet<Switch> set = EnumSet.noneOf(Switch.class);
        for (String part : setting.toLowerCase(Locale.ROOT).split("[,;+ ]+")) {
            Switch s = part.isBlank() ? null : find(part);
            if (s != null) {
                set.add(s);
            } else if (!part.isBlank() && !part.equals("none")) {
                LOG.warn("DLAA: unknown Ray Reconstruction switch {} in dlaaRrRaw (known: {})", part, String.join(", ", tokens()));
            }
        }
        return set;
    }
}
