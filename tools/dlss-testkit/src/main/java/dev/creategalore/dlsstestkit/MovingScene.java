package dev.creategalore.dlsstestkit;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Things that move by themselves, for the "moving" viewpoint: built over the lake when a run that goes there starts
 * (nothing moves until the camera gets there) and removed when the run ends, so the test world itself stays as it was
 * (everything sits in air above the water, in x 1023..1036, y 63..70, z 143..157, which is empty in the DLAA test
 * world).
 * - A creative motor turning shafts, a cogwheel, a large cogwheel and an encased fan (Flywheel instances).
 * - A second motor turning a mechanical bearing with a glued cross of planks and wool on it (a contraption).
 * - A small Sable ship on the water, turned and swayed a little every server tick (a sub-level), slowly enough by
 *   default to stand on it.
 * - Entities: a villager, an armor stand in iron armor holding a sword (the test world is peaceful, so no hostile mob
 *   stays) and a red panda (a GeckoLib model, from Critters and Companions) go round in circles in front (moved every
 *   server tick, so the mobs' legs swing), a dropped diamond sword spins in the air, a bell keeps ringing and a chest
 *   keeps opening and closing.
 * With showHand, the hand shows too (and the HUD with it) holding a sword, and swings every few ticks.
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
    private static final String TAG = "cgtest";
    private static final String MOB = "{NoAI:1b,NoGravity:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"" + TAG + "\",\"%s\"]%s}";
    private static final String BELL_POWER = "1036 64 156";
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
            "sable name set @l " + SHIP,
            // Entities: three walkers (moved in serverTick), a spinning dropped sword, a bell, a chest.
            "kill @e[tag=" + TAG + "]",
            "summon villager 1028 64 157 " + String.format(MOB, "cgwalker1",
                    ",VillagerData:{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"}"),
            "summon armor_stand 1033 64 157 " + String.format(MOB, "cgwalker2", ",ShowArms:1b,ArmorItems:[{id:\"minecraft:iron_boots\",count:1},"
                    + "{id:\"minecraft:iron_leggings\",count:1},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1}],"
                    + "HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]"),
            "summon crittersandcompanions:red_panda 1025 64 159.5 " + String.format(MOB, "cgwalker3", ""),
            "summon item 1030.5 65.5 156.5 {Item:{id:\"minecraft:diamond_sword\",count:1},NoGravity:1b,PickupDelay:32767,Age:0,Tags:[\"" + TAG + "\"]}",
            "setblock 1035 64 156 bell[attachment=floor,facing=south]",
            "setblock 1024 64 156 chest[facing=south]");
    private static final net.minecraft.core.BlockPos CHEST = new net.minecraft.core.BlockPos(1024, 64, 156);

    private static boolean built;
    private static volatile boolean active;
    private static int ticks;
    private static volatile float turn = 0.75f;
    private static volatile float sway = 0.03f;
    private static volatile boolean hand;
    private static boolean handGiven;

    private MovingScene() {}

    /** The camera arrived at the moving viewpoint: build the scene the first time and let the world tick. */
    static void enter(Capture.Settings settings) {
        show(settings);
        if (settings.freezeTicks) {
            Capture.server("tick unfreeze");
        }
        Capture.server("tick rate " + settings.sceneTickRate);
    }

    /** Puts the scene up, once; nothing moves yet. */
    static void build() {
        if (!built) {
            built = true;
            BUILD.forEach(Capture::server);
            Capture.log("built the moving scene");
        }
    }

    /** Builds the scene the first time and sets the ship going (at whatever rate the world ticks). */
    static void show(Capture.Settings settings) {
        build();
        turn = settings.shipTurnDegreesPerTick;
        sway = settings.shipSwayPerTick;
        hand = settings.showHand;
        if (hand) {
            Minecraft.getInstance().options.hideGui = false;
            Capture.server("item replace entity @a weapon.mainhand with minecraft:iron_sword");
            handGiven = true;
        }
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
        Capture.server("kill @e[tag=" + TAG + "]");
        if (handGiven) {
            handGiven = false;
            Capture.server("item replace entity @a weapon.mainhand with air");
        }
        Capture.server("kill @e[type=create:stationary_contraption,x=1033,y=65,z=153,distance=..8]");
        Capture.server("kill @e[type=create:super_glue,x=1033,y=65,z=153,distance=..8]");
        Capture.server(CLEAR);
        Capture.server(CLEAR);
        Capture.server("kill @e[type=item,x=1029,y=65,z=150,distance=..16]");
    }

    /**
     * Each server tick while the scene is shown: the ship turns and sways along x (shipTurnDegreesPerTick,
     * shipSwayPerTick), the walkers take a step around their circles, the bell's power flips every 10 ticks (it rings
     * when powered), the chest opens or closes every 12 and the hand swings every 8.
     */
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
        walk(server, "cgwalker1", 1028.0, 157.0, ticks);
        walk(server, "cgwalker2", 1033.0, 157.0, -ticks);
        walk(server, "cgwalker3", 1025.0, 159.5, ticks);
        if (ticks % 10 == 0) {
            Capture.runNow(server, "setblock " + BELL_POWER + (ticks % 20 == 0 ? " redstone_block" : " air"));
        }
        if (ticks % 12 == 0) {
            // What a player opening it would send: the lid's open count. The lid takes 10 ticks to open or close.
            server.overworld().blockEvent(CHEST, net.minecraft.world.level.block.Blocks.CHEST, 1, ticks % 24 == 0 ? 0 : 1);
        }
        if (hand && ticks % 8 == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            });
        }
    }

    // A circle of 1.5 blocks in 120 ticks, facing along it (tick < 0 goes the other way round).
    private static void walk(IntegratedServer server, String tag, double x, double z, int tick) {
        double a = 2 * Math.PI * tick / 120.0;
        double px = x + 1.5 * Math.cos(a);
        double pz = z + 1.5 * Math.sin(a);
        // Facing the direction of travel: minecraft yaw 0 looks along +z, 90 along -x.
        double vx = -Math.sin(a) * Math.signum(tick == 0 ? 1 : tick);
        double vz = Math.cos(a) * Math.signum(tick == 0 ? 1 : tick);
        float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        Capture.runNow(server, String.format(java.util.Locale.ROOT, "tp @e[tag=%s,limit=1] %.3f 64 %.3f %.1f 0", tag, px, pz, yaw));
    }
}
