package dev.creategalore.dlsstestkit;

import java.util.List;

/**
 * A dark room lit by one lantern, for the "dark" viewpoint (the Photon RR research): where Photon's noise shows most,
 * since nothing bright covers it. Closed deepslate walls (no sky light inside), a polished andesite floor, a lantern on a
 * chain from the ceiling, in view, in front of iron bars a block before the back wall (the bars' shadows on the wall,
 * the light through the chain, coloured light in the air with Photon's Ultra), and in the corners things with crevices
 * and contact shadows (barrels, an anvil, a crafting table), rough metal for rough reflections (iron, copper, gold) and
 * a cauldron of water. Built in the air over
 * the open sea (x 1055..1065, y 63..69, z 113..123, empty in the DLAA test world) when the camera first gets there, and
 * removed when the run ends. The camera stands inside by the south wall, looking north into the room.
 */
final class DarkScene {
    static final String NAME = "dark";
    private static final String CLEAR = "fill 1055 63 113 1065 69 123 air";
    private static final List<String> BUILD = List.of(
            CLEAR,
            "fill 1055 63 113 1065 69 123 deepslate_bricks hollow",
            "fill 1056 63 114 1064 63 122 polished_andesite",
            // The one light: a lantern on a chain from the ceiling, at eye height, two blocks before the bars.
            "fill 1060 67 117 1060 68 117 chain",
            "setblock 1060 66 117 lantern[hanging=true]",
            // Back left corner: a stack of barrels and an anvil.
            "fill 1056 64 114 1057 64 114 barrel[facing=up]",
            "setblock 1056 65 114 barrel[facing=up]",
            "setblock 1057 64 116 anvil[facing=east]",
            // Back right: rough metal beside a crafting table; iron bars a block before the back wall, behind the lantern.
            "setblock 1063 64 114 iron_block",
            "setblock 1064 64 114 copper_block",
            "setblock 1064 64 115 gold_block",
            "setblock 1062 64 114 crafting_table",
            "fill 1058 64 115 1061 66 115 iron_bars",
            // Front right, in view: a cauldron of water.
            "setblock 1062 64 119 water_cauldron[level=3]");
    /** The camera: inside by the south wall, eye height 65.62, looking north and a little down (the lantern in view). */
    static final double X = 1060.5, Y = 64.0, Z = 121.5;

    private static boolean built;

    private DarkScene() {}

    static void build() {
        if (!built) {
            built = true;
            BUILD.forEach(Capture::server);
            Capture.log("built the dark scene");
        }
    }

    static void remove() {
        if (built) {
            built = false;
            Capture.server(CLEAR);
        }
    }

    /** Takes the scene away even if this game didn't build it: an earlier game may have left it standing. */
    static void clear() {
        built = true;
        remove();
    }
}
