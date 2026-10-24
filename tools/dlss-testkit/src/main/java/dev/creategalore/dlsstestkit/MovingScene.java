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
 * - A second motor turning a mechanical bearing with a glued cross of planks and wool on it (a contraption).
 * - A small Sable ship on the water, turned and swayed a little every server tick (a sub-level), slowly enough by
 *   default to stand on it.
 * While the camera is there, the world ticks at sceneTickRate (the rest of the run is frozen): on slow software
 * rendering (about a frame a second) 1 tick a second keeps each frame's movement close to a normal game's.
 * /dlaatest scene build puts it up outside a run too, at the game's own tick rate, to look at and ride.
 */
final class MovingScene {
    static final String NAME = "moving";
    private static final String SHIP = "cgtestship";
    private static final String CLEAR = "fill 1023 63 143 1036 70 157 air";
    // Every sub-level around the scene (Sable's x, y and z filter the sub-levels' own positions), so nothing a run made
    // stays behind, even a piece that came loose.
    private static final String REMOVE_SHIPS = "sable remove @e[x=1013..1049,y=50..85,z=127..173]";
    private static final List<String> BUILD = List.of(
            REMOVE_SHIPS,
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
            "create glue 1031 63 153 1035 67 153",
            // The ship: a 5x5 deck, a rail around it with a window, a mast standing on the deck with a sail, a lantern.
            // All of it touches, so it assembles as one sub-level (a part that touches nothing becomes a sub-level of
            // its own).
            "fill 1027 63 143 1031 63 147 spruce_planks",
            "fill 1027 64 143 1031 64 143 spruce_planks",
            "fill 1027 64 147 1031 64 147 spruce_planks",
            "fill 1027 64 144 1027 64 146 spruce_planks",
            "fill 1031 64 144 1031 64 146 spruce_planks",
            "setblock 1029 64 147 glass",
            "fill 1029 64 145 1029 67 145 spruce_log",
            "fill 1028 66 146 1030 67 146 white_wool",
            "setblock 1027 65 143 lantern",
            "sable assemble area 1027 63 143 1031 67 147",
            "sable name set @l " + SHIP);

    private static boolean built;
    private static volatile boolean active;
    private static int ticks;
    private static volatile float turn = 0.75f;
    private static volatile float sway = 0.03f;

    private MovingScene() {}

    /** The camera arrived at the moving viewpoint: build the scene the first time and let the world tick. */
    static void enter(Capture.Settings settings) {
        show(settings);
        if (settings.freezeTicks) {
            Capture.server("tick unfreeze");
        }
        Capture.server("tick rate " + settings.sceneTickRate);
    }

    /** Builds the scene the first time and sets the ship going (at whatever rate the world ticks). */
    static void show(Capture.Settings settings) {
        if (!built) {
            built = true;
            BUILD.forEach(Capture::server);
            Capture.log("built the moving scene");
        }
        turn = settings.shipTurnDegreesPerTick;
        sway = settings.shipSwayPerTick;
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
        Capture.server(REMOVE_SHIPS);
        Capture.server("kill @e[type=create:stationary_contraption,x=1033,y=65,z=153,distance=..8]");
        Capture.server("kill @e[type=create:super_glue,x=1033,y=65,z=153,distance=..8]");
        Capture.server(CLEAR);
        Capture.server(CLEAR);
        Capture.server("kill @e[type=item,x=1029,y=65,z=150,distance=..16]");
    }

    /** Each server tick while the scene is shown: the ship turns and sways along x (shipTurnDegreesPerTick, shipSwayPerTick). */
    static void serverTick(ServerTickEvent.Post event) {
        if (!active) {
            return;
        }
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || event.getServer() != server) {
            return;
        }
        double dx = sway * Math.cos(2 * Math.PI * ticks / 80.0);
        ticks++;
        Capture.runNow(server, String.format(java.util.Locale.ROOT, "sable physics rotation @e[name=%s] add axis 0 1 0 %.4f global", SHIP, turn));
        Capture.runNow(server, String.format(java.util.Locale.ROOT, "sable physics translation @e[name=%s] add %.4f 0 0 global", SHIP, dx));
    }
}
