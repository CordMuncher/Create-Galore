package dev.creategalore.dlsstestkit;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Things that move by themselves, for the "moving" viewpoint: built over the lake when a run first gets there and
 * removed when the run ends, so the test world itself stays as it was (everything sits in air above the water, in
 * x 1023..1036, y 63..70, z 143..157, which is empty in the DLAA test world).
 * - A creative motor turning shafts, a cogwheel, a large cogwheel and an encased fan (Flywheel instances).
 * - A second motor turning a mechanical bearing with a cross of planks and wool on it (a contraption).
 * - A small Sable ship on the water, turned and swayed a little every server tick (a sub-level).
 * While the camera is there, the world ticks at sceneTickRate (the rest of the run is frozen): on slow software
 * rendering (about a frame a second) 1 tick a second keeps each frame's movement close to a normal game's.
 */
final class MovingScene {
    static final String NAME = "moving";
    private static final String SHIP = "cgtestship";
    private static final String CLEAR = "fill 1023 63 143 1036 70 157 air";
    private static final List<String> BUILD = List.of(
            "sable remove @e[name=" + SHIP + "]",
            CLEAR,
            // Machines, facing the camera: motor, shaft, cogwheel (with a large one meshed above it), shaft, fan.
            "setblock 1025 65 151 create:creative_motor[facing=south]{ScrollValue:32}",
            "setblock 1025 65 152 create:shaft[axis=z]",
            "setblock 1025 65 153 create:cogwheel[axis=z]",
            "setblock 1026 66 153 create:large_cogwheel[axis=z]",
            "setblock 1025 65 154 create:shaft[axis=z]",
            "setblock 1025 65 155 create:encased_fan[facing=south]",
            // Bearing with a cross on its face: it assembles as soon as it turns.
            "setblock 1033 65 150 create:creative_motor[facing=south]{ScrollValue:16}",
            "setblock 1033 65 151 create:shaft[axis=z]",
            "setblock 1033 65 152 create:mechanical_bearing[facing=south]",
            "fill 1031 65 153 1035 65 153 white_wool",
            "fill 1033 63 153 1033 67 153 red_wool",
            "setblock 1033 65 153 oak_planks",
            // The ship: a 5x5 deck, a rail around it with a window, a mast and a sail, a lantern.
            "fill 1027 63 143 1031 63 147 spruce_planks",
            "fill 1027 64 143 1031 64 143 spruce_planks",
            "fill 1027 64 147 1031 64 147 spruce_planks",
            "fill 1027 64 144 1027 64 146 spruce_planks",
            "fill 1031 64 144 1031 64 146 spruce_planks",
            "setblock 1029 64 147 glass",
            "fill 1029 65 145 1029 67 145 spruce_log",
            "fill 1028 66 146 1030 67 146 white_wool",
            "setblock 1027 65 143 lantern",
            "sable assemble area 1027 63 143 1031 67 147",
            "sable name set @l " + SHIP);

    private static boolean built;
    private static volatile boolean active;
    private static int ticks;

    private MovingScene() {}

    /** The camera arrived at the moving viewpoint: build the scene the first time and let the world tick. */
    static void enter(Capture.Settings settings) {
        if (!built) {
            built = true;
            BUILD.forEach(Capture::server);
            Capture.log("built the moving scene");
        }
        if (settings.freezeTicks) {
            Capture.server("tick unfreeze");
        }
        Capture.server("tick rate " + settings.sceneTickRate);
        ticks = 0;
        active = true;
    }

    /** The camera left: back to a frozen world. */
    static void leave(Capture.Settings settings) {
        if (!active) {
            return;
        }
        active = false;
        Capture.server("tick rate 20");
        if (settings.freezeTicks) {
            Capture.server("tick freeze");
        }
    }

    /** The run ended: take everything away again (twice: the bearing puts its blocks back down when it goes). */
    static void remove() {
        active = false;
        if (!built) {
            return;
        }
        built = false;
        Capture.server("sable remove @e[name=" + SHIP + "]");
        Capture.server("kill @e[type=create:stationary_contraption,x=1033,y=65,z=153,distance=..8]");
        Capture.server(CLEAR);
        Capture.server(CLEAR);
        Capture.server("kill @e[type=item,x=1029,y=65,z=150,distance=..16]");
    }

    /** Each server tick while the camera is there: the ship turns 4 degrees and sways along x (about 2 blocks each way). */
    static void serverTick(ServerTickEvent.Post event) {
        if (!active) {
            return;
        }
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || event.getServer() != server) {
            return;
        }
        double sway = 0.15 * Math.cos(2 * Math.PI * ticks / 80.0);
        ticks++;
        Capture.runNow(server, "sable physics rotation @e[name=" + SHIP + "] add axis 0 1 0 4 global");
        Capture.runNow(server, String.format(java.util.Locale.ROOT, "sable physics translation @e[name=%s] add %.4f 0 0 global", SHIP, sway));
    }
}
