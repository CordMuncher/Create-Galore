package dev.creategalore.dlsstestkit;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;

/**
 * Things that change on their own without moving as a whole, for the "effects" viewpoint: built over the lake west of
 * the moving scene when a run that goes there starts and removed when the run ends, so the test world stays as it was
 * (everything sits in x 996..1016, y 63..70, z 144..151, air over the water in the DLAA test world). On a stone
 * platform, from left to right as the camera sees them:
 * - a nether portal (its swirl and its particles);
 * - fire on netherrack (animated flames, smoke);
 * - a campfire (flames, rising smoke);
 * - a lava pool fed by a lava fall (flowing lava's texture, lava pops and drips);
 * - a water fall into the lake (flowing water).
 * And while the camera is there it rains, from the first frame at full strength (rain only fades in over a few
 * seconds of ticks otherwise, so each mode would see a different amount). Leaving puts the sky back to clear at once.
 * The world ticks there as at the moving scene (sceneTickRate), so particles move and textures animate.
 */
final class EffectsScene {
    static final String NAME = "effects";
    private static final String CLEAR = "fill 996 63 144 1016 70 151 air";
    private static final List<String> BUILD = List.of(
            CLEAR,
            "fill 996 63 144 1013 63 151 stone_bricks",
            // Nether portal facing the camera: an obsidian frame 4 wide and 5 high, the portal inside.
            "fill 997 63 149 1000 67 149 obsidian",
            "fill 998 64 149 999 66 149 nether_portal[axis=x]",
            // Fire on netherrack, which keeps it burning (rain puts out fire on anything else).
            "fill 1002 63 149 1003 63 149 netherrack",
            "fill 1002 64 149 1003 64 149 fire",
            "setblock 1005 64 149 campfire[lit=true]",
            // Lava pool with a stone rim, fed by a lava fall in front of a wall at its back (placed whole, so it needs no
            // time to flow). Nothing of it touches the lake: lava flowing onto water would turn the water to stone.
            // Each fall's source sits in a stone cap, as a source over its own fall still spreads sideways, and the falls
            // would widen while the modes run, so each mode would see a different scene.
            "fill 1007 64 144 1012 64 150 stone",
            "fill 1008 64 145 1011 64 149 lava",
            "fill 1008 65 144 1011 69 144 stone",
            "fill 1008 69 145 1010 69 146 stone",
            "setblock 1009 69 145 lava",
            "fill 1009 65 145 1009 68 145 lava[level=8]",
            // Water fall into the lake, off the platform, from a stone spout at its back.
            "fill 1014 69 145 1016 69 147 stone",
            "setblock 1015 69 146 water",
            "fill 1015 63 146 1015 68 146 water[level=8]");

    private static boolean built;
    private static boolean raining;

    private EffectsScene() {}

    /** The camera arrived at the effects viewpoint: build the scene the first time, start the rain, let the world tick. */
    static void enter(Capture.Settings settings) {
        show();
        if (settings.freezeTicks) {
            Capture.server("tick unfreeze");
        }
        Capture.server("tick rate " + settings.sceneTickRate);
    }

    /** Puts the scene up, once, without the rain. */
    static void build() {
        if (!built) {
            built = true;
            BUILD.forEach(Capture::server);
            Capture.log("built the effects scene");
        }
    }

    /** Builds the scene the first time and starts the rain at full strength. */
    static void show() {
        build();
        rain(true);
    }

    /** The camera left: rain off, back to a frozen world. */
    static void leave(Capture.Settings settings) {
        if (!raining) {
            return;
        }
        rain(false);
        Capture.server("tick rate 20");
        if (settings.freezeTicks) {
            Capture.server("tick freeze");
        }
    }

    /** The run ended: take everything away again (twice, as lava and water may flow back into the first pass's air). */
    static void remove() {
        if (raining) {
            rain(false);
        }
        if (!built) {
            return;
        }
        built = false;
        Capture.server(CLEAR);
        Capture.server(CLEAR);
    }

    // Rain on or off at full strength at once, on the server and on the client (which would otherwise wait for the
    // server's next tick, and the server only changes its rain level a hundredth per tick).
    private static void rain(boolean on) {
        raining = on;
        float level = on ? 1.0f : 0.0f;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                Capture.runNow(server, on ? "weather rain" : "weather clear");
                ServerLevel overworld = server.overworld();
                overworld.setRainLevel(level);
                overworld.setThunderLevel(0.0f);
                server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(on ? ClientboundGameEventPacket.START_RAINING
                        : ClientboundGameEventPacket.STOP_RAINING, 0.0f), overworld.dimension());
                server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level),
                        overworld.dimension());
            });
        }
        if (mc.level != null) {
            mc.level.setRainLevel(level);
        }
    }
}
