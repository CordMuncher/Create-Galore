package dev.creategalore.dlsstestkit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Standing on a ship that sails in a straight line, for the "ride" viewpoints. A small Sable ship on the open water
 * south of the lake scene (x 940..1120 is open sea at z 90..110 in the DLAA test world), open sky above it; rideShip
 * picks it: "boat", a deep hull, a deck, a rail and a short mast ahead, so the ship reaches above the player's head and
 * its middle floats about at the waterline, like many boats; "hull", a shallower hull and a stub of a mast, lower in
 * the water; "deck", just a deck and a rail, riding high. It sails along x at rideSpeedPerTick, turning back every LEG
 * ticks so it stays on the water, and with rideBobBlocks heaves up and down by that much every BOB_TICKS like a boat on
 * waves. The player stands on its deck holding a sword (the hand and the HUD show) and is never pinned: the ship
 * carries them, as when standing on a moving boat in a game. Each frame there writes a line of ride.csv in the mode's
 * folder: where the player is, their walk animation (what their model's arms and legs swing by), the light the held
 * item gets and the ship's sky light scale (Sable's measure of the daylight over the ship, which scales that light;
 * it flipped between 14 and 15 as a boat bobbed at the waterline, and the held item's light with it).
 * Built when the camera first gets there and removed when the run ends.
 */
final class RideScene {
    static final String NAME = "ride";
    private static final String SHIP = "cgrideship";
    private static final String CLEAR = "fill 958 63 97 966 68 103 air";
    // The hull goes below the waterline: put the sea back there.
    private static final String REFILL = "fill 958 59 97 966 62 103 water";
    // Every sub-level over the sea where the ship sails (Sable's x, y and z filter the sub-levels' own positions).
    private static final String REMOVE_SHIPS = "sable remove @e[x=900..1160,y=40..100,z=70..130]";
    /** Ticks the ship sails one way before it turns back. */
    private static final int LEG = 100;
    /** Ticks of one heave, up and down. */
    private static final int BOB_TICKS = 40;
    // A 7x5 deck with a rail around it, all spruce planks, so it assembles as one sub-level.
    private static final List<String> DECK = List.of(
            "fill 958 63 98 964 63 102 spruce_planks",
            "fill 958 64 98 964 64 98 spruce_planks",
            "fill 958 64 102 964 64 102 spruce_planks",
            "fill 958 64 99 958 64 101 spruce_planks",
            "fill 964 64 99 964 64 101 spruce_planks");
    // Under the deck, a hull two blocks deep (a bottom and a ring of sides, air inside), in the water.
    private static final List<String> HULL = List.of(
            "fill 958 61 98 964 61 102 spruce_planks",
            "fill 958 62 98 964 62 102 spruce_planks hollow",
            "fill 959 64 100 959 65 100 spruce_log");
    // A hull four blocks deep and a mast at the bow (ahead of the player looking west), four blocks over the deck.
    private static final List<String> BOAT = List.of(
            "fill 958 59 98 964 59 102 spruce_planks",
            "fill 958 60 98 964 62 102 spruce_planks hollow",
            "fill 959 64 100 959 67 100 spruce_log");
    /** Where the player stands: the middle of the deck. */
    static final double DECK_X = 961.5, DECK_Y = 64.0, DECK_Z = 100.5;

    private static boolean built;
    private static volatile boolean active;
    private static int ticks;
    private static volatile float speed = 0.3f;
    private static volatile float heave;
    private static Path csvFile;
    private static BufferedWriter csv;

    private RideScene() {}

    static void build(Capture.Settings settings) {
        if (!built) {
            built = true;
            String kind = "deck".equals(settings.rideShip) || "hull".equals(settings.rideShip) ? settings.rideShip : "boat";
            Capture.server(REMOVE_SHIPS);
            Capture.server(CLEAR);
            Capture.server(REFILL);
            if (kind.equals("hull")) {
                HULL.forEach(Capture::server);
            }
            DECK.forEach(Capture::server);
            if (kind.equals("boat")) {
                BOAT.forEach(Capture::server);
            }
            int bottom = switch (kind) {
                case "deck" -> 63;
                case "hull" -> 61;
                default -> 59;
            };
            Capture.server("sable assemble area 958 " + bottom + " 98 964 67 102");
            Capture.server("sable name set @l " + SHIP);
            Capture.log("built the ride scene (" + kind + ")");
        }
    }

    /** The camera arrived: build the ship the first time, put the player on its deck and let the world tick. */
    static void enter(Capture.Settings settings, float yaw, float pitch) {
        build(settings);
        speed = settings.rideSpeedPerTick;
        heave = settings.rideBobBlocks;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = false;
        // As players have it (the rest of a run turns it off): its sway follows the player's own steps.
        mc.options.bobView().set(settings.rideViewBob);
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
        }
        Capture.server("item replace entity @a weapon.mainhand with minecraft:iron_sword");
        Capture.server(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", DECK_X, DECK_Y + 0.1, DECK_Z, yaw, pitch));
        if (settings.freezeTicks) {
            Capture.server("tick unfreeze");
        }
        Capture.server("tick rate " + settings.sceneTickRate);
        ticks = 0;
        active = true;
    }

    /** The camera left: back to a frozen world, hand and HUD hidden. */
    static void leave(Capture.Settings settings) {
        if (!active) {
            return;
        }
        active = false;
        Minecraft.getInstance().options.hideGui = true;
        Minecraft.getInstance().options.bobView().set(false);
        Capture.server("item replace entity @a weapon.mainhand with air");
        Capture.server("tick rate 20");
        if (settings.freezeTicks) {
            Capture.server("tick freeze");
        }
    }

    /** The run ended: take the ship away and close the log. */
    static void remove() {
        active = false;
        closeCsv();
        if (!built) {
            return;
        }
        built = false;
        Capture.server(REMOVE_SHIPS);
        Capture.server(CLEAR);
        Capture.server(REFILL);
    }

    /** Takes the scene away even if this game didn't build it: an earlier game may have left it standing. */
    static void clear() {
        built = true;
        remove();
    }

    /** Each server tick while the camera is there: the ship sails on, turning back every LEG ticks. */
    static void serverTick(ServerTickEvent.Post event) {
        if (!active) {
            return;
        }
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || event.getServer() != server) {
            return;
        }
        double dx = (ticks / LEG) % 2 == 0 ? speed : -speed;
        double dy = heave * (Math.sin(2 * Math.PI * (ticks + 1) / BOB_TICKS) - Math.sin(2 * Math.PI * ticks / BOB_TICKS));
        ticks++;
        Capture.runNow(server, String.format(Locale.ROOT, "sable physics translation @e[name=%s] add %.4f %.4f 0 global", SHIP, dx, dy));
    }

    /** Each frame there: one line of the mode's ride.csv. */
    static void frame(Path modeDir, String segment, int local) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        try {
            Path file = modeDir.resolve("ride.csv");
            if (!file.equals(csvFile)) {
                closeCsv();
                Files.createDirectories(modeDir);
                boolean fresh = !Files.exists(file);
                csv = Files.newBufferedWriter(file, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                csvFile = file;
                if (fresh) {
                    csv.write("segment,frame,partialTick,x,y,z,dx,walkSpeed,walkPosition,sky,block,onGround,bob,walkDist,"
                            + "moveX,moveY,moveZ,camX,camY,camZ,oldMinusO,shipSkyScale,shipMinY,shipMaxY\n");
                }
            }
            float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
            int light = mc.getEntityRenderDispatcher().getPackedLightCoords(player, pt);
            net.minecraft.world.phys.Vec3 move = player.getDeltaMovement();
            net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            csv.write(String.format(Locale.ROOT, "%s,%d,%.3f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%b,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.5f",
                    segment, local, pt, player.getX(), player.getY(), player.getZ(), player.getX() - player.xo,
                    player.walkAnimation.speed(pt), player.walkAnimation.position(pt),
                    LightTexture.sky(light), LightTexture.block(light), player.onGround(),
                    net.minecraft.util.Mth.lerp(pt, player.oBob, player.bob), player.walkDist, move.x, move.y, move.z,
                    cam.x, cam.y, cam.z, player.xOld - player.xo) + ship(player) + "\n");
            csv.flush();
        } catch (IOException e) {
            Capture.log("could not write ride.csv: " + e);
            closeCsv();
        }
    }

    /** The ship the player stands on, through Sable's API by reflection (the kit doesn't build against Sable). */
    private static String ship(LocalPlayer player) {
        try {
            Object helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
            Object ship = helper.getClass().getMethod("getTrackingSubLevel", net.minecraft.world.entity.Entity.class).invoke(helper, player);
            if (ship == null) {
                return ",,,";
            }
            Object box = ship.getClass().getMethod("boundingBox").invoke(ship);
            return String.format(Locale.ROOT, ",%d,%.4f,%.4f", (Integer) ship.getClass().getMethod("getLatestSkyLightScale").invoke(ship),
                    (Double) box.getClass().getMethod("minY").invoke(box), (Double) box.getClass().getMethod("maxY").invoke(box));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ",?,?,?";
        }
    }

    private static void closeCsv() {
        if (csv != null) {
            try {
                csv.close();
            } catch (IOException ignored) {
                // nothing more to save
            }
        }
        csv = null;
        csvFile = null;
    }
}
