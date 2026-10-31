package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a skilled builder designs each kind of build, given to the book with the first model of that kind in an answer
 * instead of sitting in the instructions sent with every question. What a check enforces (posts on slabs, an engine the
 * crew can reach, a cold helm) isn't repeated here: the check's own result says it when it happens.
 */
final class DesignBriefs {
    /**
     * A model with this many building blocks (no machines, nothing with a block entity) above its bottom layer is a
     * build to copy, not a few machines explaining a point; a floor under a machine line doesn't count.
     */
    static final int SHOWPIECE = 24;

    private DesignBriefs() {
    }

    static final String BUILD = """
            A build to copy is a showpiece: build it the way a skilled Minecraft builder would, with real shape and \
            detail, at a size that gives it shape (a small sailing boat is about 12 to 20 blocks long). Use stairs, \
            slabs, trapdoors, fences, walls and buttons for curves and detail, depth and trim instead of flat walls, and \
            no plain boxes. Write it as layers, try it as a draft, look at the pictures and fix what looks blocky, \
            lopsided or off before you show it; one or two drafts are usually enough.
            Build it out of what the player has. building_materials counts the building blocks they hold by material, \
            and the trees and ground around them. Wherever blocks of the same kind would look about as good (one plank \
            wood for another, one plain stone for another), use the one they hold most of, or else what grows or lies \
            around them; what a crafting table or stonecutter makes from their materials counts as theirs. Keep each \
            part to one material, as a builder would (one wood for a whole hull), with two or three that go together. \
            Never make a build worse to use what they hold: when it needs a block they lack or more than they have, use \
            it and tell them what they still need and about how many (each model lists what it takes against what they \
            hold).
            Create builds can move, and a moving part is often what makes a build: sails or blades on a bearing, a door, \
            gate or drawbridge that swings, a turntable, a wall a piston slides aside, a lift on a rope pulley, a gantry \
            that carries a drill or harvester over a field. Where a part would work better or be more fun moving, \
            design it that way with build_model's moving, naming the block that moves it, what drives that, at what \
            speed, and where the Super Glue goes. Most builds stand still and should; make a part move only where \
            moving is its job.""";

    static final String VEHICLE = """
            Design a vehicle for the crew who will use it: where they stand to steer and work each control (on the \
            vehicle's own deck or floor), how they get aboard from the ground, and how they walk to everything they tend \
            on the way, an engine to refuel above all, along floors, steps or a ladder, in plain view. The pilot looks \
            out ahead from the helm: the controls go on a dashboard below the window sill or on the wall beside the \
            windows, never on or in front of the glass. The pilot steers with one control turned left and right, as in a \
            real cockpit: a Steering Wheel at the helm (a boat's Boat Engine tiller is one too), and sets the speed with \
            one Throttle Lever, anywhere from stopped to flat out. On an aircraft the wheel's cog turns a Swivel Bearing \
            that swings a Wooden Propeller at the stern (it needs its own small glue box), or a comparator against each \
            side of the wheel slows the propeller line on one side (Redstone Links carry it, strength and all). \
            The throttle is an Analog Transmission in each propeller line: its shaft takes the engine's turn and its \
            cogwheel passes it on toward the propeller, slowed by the redstone strength reaching it and stopped at 15, so \
            a Throttle Lever wrenched once to invert it runs from stopped to full. A transmission takes the stronger of \
            its signals, so a wheel comparator wired to it too slows that side for a turn, a small turn a little. Levers \
            are for what stays set: burners, brakes, an engine on or off. A lever per side to steer, or propellers that \
            only run flat out or stop, are only for a player who can't make a Steering Wheel, or an Analog Transmission \
            and Throttle Lever, yet; then say in the answer that you built it that way and what to upgrade to once they \
            can. A vehicle \
            balances by the shape of its hull and where its rooms and machines go, never by iron blocks or other dead \
            weight. When a player asks for a vehicle that hauls, or one to live from (a sky base, a camper, a home on \
            the move), give it the rooms that make it one: a bed, a place to cook (a smoker or Farmer's Delight stove), \
            a crafting table and real storage, a wall of barrels or chests rather than two (a container weighs the same \
            full or empty). Never tell a player a vehicle can't be built at their stage, or settle for a lesser kind, \
            until you have designed one that works: when a draft fails, change the design and try again.""";

    /**
     * Shared by every aircraft: what decides how it flies, and the honesty rule. The Field Guide's Designing Aircraft
     * entry holds the same principles with numbers, for the player.
     */
    static final String AIRCRAFT = """
            Design an aircraft from what makes one fly, the way real airship and aeroplane designers do, not from a \
            look: lift, weight, push and drag. Every block is weight to lift and drag to push, so each one earns its \
            place; a platform, deck or wall nothing uses is dead weight that the ship carries everywhere. Start from \
            what the player asked for (its size, shape, load, height, speed) and what they can make, work out what it \
            needs, then shape it. If what they asked for can't fly with what they can make yet (a shape that can't lift \
            its load, a speed their engines can't reach, a size their envelope or wings can't carry), say so plainly, \
            with the numbers, build the nearest design that does fly, and name what would make theirs possible (more \
            engines, a bigger fan, Propeller Bearings, a lighter body). The guide's Designing Aircraft notes hold the \
            numbers behind these rules.""";

    static final String PLANE = """
            A plane should read as an aircraft at a glance, not a platform with parts set on it: a fuselage that is a \
            body (the engine in the nose or in nacelles on the wings, the propellers out front, a cockpit, a body \
            narrowing back to the tail; a streamlined shape draws one), long slim wings (about five or six times as \
            wide as they are deep, a biplane's two stacked on struts) set over the weight's centre, a tail far back \
            with a flat stabiliser and an upright fin, and landing gear under it. The pilot sits in the cockpit with \
            every control in front of them, grouped like a dashboard, steers with the Steering Wheel and sets the \
            speed with a Throttle Lever. Planes fly only if they are light, so wings and tail are fabric (Create sails \
            lift like Wing blocks at a quarter of the weight; Symmetric Sails steady a tail without lifting) and the \
            body is slabs, trapdoors and fences, with solid blocks only where they matter. Propeller planes work from \
            the start: Portable Engines geared up to 256 RPM turning Wooden Propellers, on sail wings, with a \
            Gyroscope. When a draft fails, change the design (more wing, less weight, faster propellers, a biplane).""";

    static final String AIRSHIP = """
            An airship or blimp is an envelope with a small gondola under it. The envelope is a streamlined body, not a \
            box or a ball: round in every cross-section, blunt at the nose, fullest about two fifths back, tapering to \
            a point at the tail, about two to three times as long as it is wide (build_model's streamlined_shell draws \
            one; paint stripes or panels on it). Size it to the load with room to climb, since every envelope block \
            drags as much as several blocks of weight: a fatter envelope holds more air per block of skin, a longer \
            one only reads better. The gondola hangs close under the belly, a little forward of the envelope's middle \
            (the tail's propeller and fins weigh on the stern), compact, with the helm at the bow behind windows and \
            the engine room behind it. Every engine sits inside that engine room, where the crew walks to refuel it \
            in the warm; shafts carry the power out (up through the belly, along inside the envelope, out through \
            the skin with envelope-encased shafts), never engines on platforms, pods or decks outside. Push in line \
            with the envelope's middle, which is where its drag is: one big Propeller Bearing fan on the envelope's \
            axis behind the tail, or a pair at its widest point on short shaft arms through its sides; never \
            propellers low under the gondola. Big slow fans push most for their engines (each Portable Engine gives \
            2048 stress; a fan draws 2 per sail per RPM). Fins of Symmetric Sails at the tail keep it pointing \
            ahead. Steer with the wheel's shafts swinging a Swivel Bearing under the tail that carries a powered \
            Wooden Propeller, or by slowing one side of a pair; throttle the drive line. It should cruise at about 8 \
            blocks a second or better; a balloon at walking pace is not a finished design.
            A hot air balloon is the simple early aircraft: a round envelope about as wide as it is tall, a basket or \
            small gondola hung under it on posts from its corners, burners under the open bottom; it climbs, sinks \
            and drifts. Build one when the player asks for a balloon, or can't make an airship's drive yet, and say \
            what it can't do (it goes where the wind and a small propeller take it).""";

    static final String BOAT = """
            A boat or submarine has a real hull: a keel, a bow that narrows, sides that curve with stairs and slabs, a \
            deck with a rail and a cabin or wheelhouse, sized to what it carries. The helmsman stands on the deck at the \
            helm, the engine and its fuel are reached from inside the hull, and it floats level and upright because of \
            where its hold, rooms and machines go.""";

    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> WINDMILL_SAILS =
            net.minecraft.tags.BlockTags.create(net.minecraft.resources.ResourceLocation.parse("create:windmill_sails"));

    /** The briefs for the kinds of build this model is, not counting those already given in this answer. */
    static List<String> forModel(Map<BlockPos, BlockState> cells, Set<String> given) {
        boolean envelope = false, wing = false, prop = false, water = false, wheels = false;
        int bottom = Integer.MAX_VALUE, building = 0;
        for (BlockPos p : cells.keySet()) bottom = Math.min(bottom, p.getY());
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockState b = e.getValue();
            if (e.getKey().getY() > bottom && !b.isAir() && !b.hasBlockEntity()
                    && !(b.getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate)) building++;
        }
        for (BlockState s : cells.values()) {
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
            if (id.endsWith("_envelope") || id.equals("aeronautics:adjustable_burner") || id.contains("levitite")) envelope = true;
            if (s.is(WINDMILL_SAILS) || id.contains("wing") || id.endsWith("_symmetric_sail") || id.equals("aeroworks:gyroscope")) wing = true;
            if (id.endsWith("propeller") || id.startsWith("createpropulsion:") || id.endsWith("propeller_bearing")) prop = true;
            if (id.startsWith("create_high_seas:") || id.startsWith("create_submarine:")) water = true;
            if (id.equals("offroad:wheel_mount")) wheels = true;
        }
        List<String> kinds = new ArrayList<>();
        if (building >= SHOWPIECE) kinds.add("build");
        if (envelope || prop || water || wheels) kinds.add("vehicle");
        if (envelope || wing && prop) kinds.add("aircraft");
        if (envelope) kinds.add("airship");
        else if (wing && prop) kinds.add("plane");
        if (water) kinds.add("boat");
        List<String> out = new ArrayList<>();
        for (String k : kinds) {
            if (!given.add(k)) continue;
            out.add(switch (k) {
                case "build" -> BUILD;
                case "vehicle" -> VEHICLE;
                case "aircraft" -> AIRCRAFT;
                case "airship" -> AIRSHIP;
                case "plane" -> PLANE;
                default -> BOAT;
            });
        }
        return out;
    }
}
