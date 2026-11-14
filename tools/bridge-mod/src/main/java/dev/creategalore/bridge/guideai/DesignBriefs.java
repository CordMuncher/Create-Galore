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
            one Throttle Lever, anywhere from stopped to flat out. An aircraft is flown from an Aeroworks Control Stand or \
            Control Desk instead, never a ship's wheel: its Yoke Module sends Turn Left and Turn Right, and its Throttle \
            Quadrant's levers the throttle and the reverse, each on its own Redstone Link frequency (strength and all), \
            so a turn slows the propeller line on one side; tell the player which frequency each control and receiver \
            gets. Where they can't make a console yet, the wheel's cog turns a Swivel Bearing that swings a Wooden \
            Propeller at the stern, or a comparator against each side of the wheel slows one side's line. \
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
            Design every small plane new through build_model's plane, choosing its shape for this player: \
            seats (one or two), wood and colour, the engine, then the wings. engine prop is Portable Engines and \
            propellers; engine jet trades them and the gyroscope for two Smart Vector Thrusters (Create: Flight Control) \
            either side of the tail and a Flight Control Computer that swings and throttles them, fed by a Smart \
            Attitude Stabilizer (Level, Medium preset: keeps it level), a Velocity Coupler (the throttle: holds the speed \
            set in it, 30 blocks/s against a propeller plane's 16) and an Angular Velocity Coupler (the turns), all \
            flown from the same Control Stand through a Redstone Proxy. Lava or TFMG fuel lasts long at cruise. A jet \
            needs Control Units (sequenced assembly) and every link made by hand with the Flight Control Tool Gun once \
            it is built, so give one to a player who asks for a jet or thrusters, or can make those parts and wants \
            speed or height. To judge a player's jet: its thrusters go far from the centre of mass (the computer pitches \
            it by swinging them; a block or two from it, holding level took so much thrust that the plane ran away past \
            its set speed and climbed off), its wing balances it fore and aft by itself, it needs no gyroscope, and it \
            is assembled with the controllers' engine cold (assembled turning, controllers went missing). Lift grows with speed and every block is weight, so \
            the wing block sets the plane's size: Copycat Wings (8m) (iron sheets: a Wing cut on a saw, two stacked per \
            block) lift half again as much as a Create sail for the same weight, and only they get a plane off the ground \
            on wheels, so they are the default; sails can't (wheels drag until it lifts, and a sail wing big enough adds \
            more weight and drag than lift). A Copycat biplane is about 29 across, a monoplane about 31. \
            Pick wings 1 (sleek, wide), 2 (compact, classic) or 0 to let the server choose; span 0 unless the player \
            named a size (wider lifts more and climbs higher, narrower fits a hangar); tips round for a softer, real \
            plane look, square for the most lift. Choose from what they can make and what they asked for (small, \
            cheap, high, sleek, a look) and say why in your answer. The server builds every part joined, geared and \
            balanced, fills in what you left to it and checks it, or says why the choice can't fly and what would; \
            then change it with from_draft only where the player asked for something it doesn't cover (paint, \
            trim), keeping its engines, gear and wing in place. A Copycat Wing shows as a bare frame until a block is used \
            on it; tell the player to give the wings a look (white wool or concrete for a classic wing). Every part must visibly join the next \
            and no sky may show through its body: a sail is a thin panel across the middle of its block and joins \
            only blocks beside it. One the player built stays theirs: copy it with from_world and improve it in its own \
            shape by the rules below, never by swapping in a laid-out plane. The rules, to explain the design and to \
            judge one the player built: a plane reads as an aircraft, a body narrowing back to a tail with a flat \
            stabiliser and an upright fin, wings set over the weight's centre, and it flies only if it is light, so \
            wings and tail are fabric or copycat (Create sails lift like Wing blocks at a quarter of the weight; Symmetric \
            Sails steady a tail without lifting) and the body is light wood, stairs rather than planks, closed at the \
            sides. The pilot sits up front with \
            every control in reach and nothing but a low nose and a window ahead of their eyes: engines and \
            propellers go behind the pilot or out on the wings (propellers on the wings' leading edge, pushing at the \
            height of the centre of mass), never in front of the windscreen, and a wing in front of the pilot's eyes \
            blinds them. Portable Engines geared up to 256 RPM turn Wooden Propellers from the start, and a Gyroscope \
            keeps the wings level, in proportion to its RPM: only at 256 (two engines' worth, 16 stress units per RPM) does it hold a plane \
            level; at 128 it flies a few degrees banked, at a bare 32 it leans hard. It stands on real wheels: three Offroad Wheel Mounts (one under the nose, two \
            just behind the centre of mass), each facing sideways with a Tire in it, never Grindstones or bare blocks. A \
            wheel drags 1.5 x its suspension setting per block/s until the plane leaves the ground, so three are set to \
            5, their lowest (at the default 10 the run is far longer); tell the player. The pilot flies from an \
            Aeroworks Control Stand (Yoke and Throttle Quadrant on link frequencies), and a Gearshift in each propeller \
            line, reversed from the throttle quadrant, stops it in a few blocks after landing. The \
            check gives the take-off and landing runs; quote them. When a draft fails, change the design.""";

    static final String AIRSHIP = """
            Start every airship or blimp you design new from build_model's airship: say what the player wants (closed cabin or \
            open deck, beds, chests, speed, ceiling, wood, colour, style) and the server lays it out by these rules and \
            balances it, so its first version works; then change it with from_draft only where the player asked for \
            something the wishes don't cover, and keep its heavy parts in place. One the player built stays theirs: \
            copy it with from_world and improve it in its own shape and look, by the rules below; when a draft of it has \
            faults, fix them on their copy (a smaller change if one broke their drive), never with a laid-out airship \
            ("my build but better" is still theirs). An open deck is only \
            for a player who asked for one; above y 192 they need Windproof Lining on their armour. Pick the style from how the \
            player describes it: sleek is long and slim with long fins (more cloth, so it drags more for its engines \
            and turns slower), classic the all-rounder, cargo fat with a boxy cabin for big loads; each gets a trim \
            colour on its fins, a stripe along its top and its propeller blades. When a style can't do what was asked \
            the layout says so: offer another style. The rules, to explain the \
            design and to judge one the player built: an airship is an envelope with a small gondola under it. The \
            envelope is a streamlined body, not a box or a ball: round in every cross-section, blunt at the nose, \
            fullest about two fifths back, tapering to a point at the tail, about two to three times as long as it \
            is wide. Size it to the load with room to climb, since every envelope block drags as much as several \
            blocks of weight: a fatter envelope holds more air per block of skin. The gondola hangs under the belly, \
            compact, with the helm at the bow and the engine room behind it. Every engine sits inside that \
            engine room, where the crew walks to refuel it; shafts carry the power up through the belly and along \
            the envelope's axis (envelope-encased where they pass the skin), never engines on platforms, pods or \
            decks outside. Push through the middle of the drag, which the gondola's own drag pulls a shade under a round \
            envelope's axis (a tenth of a block off pitches a compact airship a degree or two at full speed; the \
            airship layout runs a ridge of envelope along the top, which drags like the rest, or makes the envelope a \
            row taller than wide, to put that middle on the shaft): a Propeller Bearing fan on that line ahead of the \
            nose or behind the tail (or \
            both), or a pair at the widest point on short shaft arms; never propellers low under the gondola. Big slow fans push most for their engines (each Portable \
            Engine gives 2048 stress; a fan draws 2 per sail per RPM). Steer with the wheel's shafts swinging a \
            Swivel Bearing under the tail that carries a powered Wooden Propeller; throttle the drive line. A \
            balloon hangs with its weight straight under the middle of its air, and on a compact airship that air \
            sits only a few blocks above its weight, so one heavy part moved a few blocks tips it: the envelope's \
            thin tail is mostly skin, the fan and swivel weigh on their end, and the gondola sits where it balances \
            them. It should cruise at about 8 blocks a second or better.
            A hot air balloon is the simple early aircraft: a round envelope about as wide as it is tall, a basket or \
            small gondola hung under it on posts from its corners, burners under the open bottom; it climbs, sinks \
            and drifts. Build one when the player asks for a balloon, or can't make an airship's drive yet, and say \
            what it can't do (it goes where the wind and a small propeller take it).""";

    static final String HELICOPTER = """
            Design every helicopter new through build_model's helicopter: say the seats (one to three), wood and colour, \
            and the server sizes its rotor and engines, balances it and checks it, so its first version flies; change \
            it with from_draft only where the player asked for something it doesn't cover (paint, trim, a window), keeping \
            its engines, mast, rotor and tail in place. One the player built stays theirs: copy it with from_world and \
            improve it in its own shape by the rules below. The rules, to explain the design and to judge one the player \
            built: a helicopter here lifts on one Gyroscopic Propeller Bearing facing up on a mast above the cabin, \
            turning a rotor of Create sails. That bearing keeps its push pointing straight up (up to 12 degrees off its \
            own axis) whatever the cabin does, so with the rotor well above the weight the cabin hangs under it like a \
            pendulum and rights itself; a plain Propeller Bearing or upward Wooden Propellers push the way the cabin \
            points, and the first gust rolls it over (only an Aeroworks Gyroscope at 256 RPM would hold that level). The \
            rotor must sit over the centre of mass: the bearing can lean its push only 12 degrees. The rotor pushes 0.2 x \
            sails^1.5 x RPM for 2 stress units per sail per RPM, so on the same engines a bigger, slower rotor lifts \
            more; its push thins with the air, which sets the ceiling, and below that the collective (an Analog \
            Transmission in the rotor's line on a Throttle Lever) climbs, hovers or sinks it. Rotors don't twist the \
            cabin here, so it needs no anti-torque tail rotor, and since the rotor stays level, tipping the cabin doesn't \
            drive it along as on a real helicopter: a Wooden Propeller at the tail, geared to 256 RPM and pushing level \
            with the centre of mass, drives it forward on its own throttle, hung under a Swivel Bearing the Steering \
            Wheel swings to turn it. Nothing grips the air sideways, so it yaws on the spot: an upright fin of several \
            sails at the tail damps that (without one it spins up at full wheel and coasts half a turn after), and it \
            slides on the way it was going for a few seconds after a turn. Nothing may stand in the rotor's disc or \
            touch the blades: they strike it, and the ship can come apart there when they spin up. The bearing's value \
            panel sets which way the rotor pushes for the way it turns: set it so it lifts. The gearbox and propeller \
            under the swivel get their own glue box, apart from the ship's. Engines sit in the cabin where the crew \
            refuels them; shafts carry the power up the mast and back along the boom. Helicopters need brass (the bearing's Gyroscopic Mechanism); before \
            that, offer a hot air balloon and say why.""";

    static final String BOAT = """
            A boat has a real hull: a keel, a bow that narrows, sides that curve with stairs and slabs, a \
            deck with a rail and a cabin or wheelhouse, sized to what it carries. The helmsman stands on the deck at the \
            helm, the engine and its fuel are reached from inside the hull, and it floats level and upright because of \
            where its hold, rooms and machines go.""";

    static final String SUBMARINE = """
            Design every submarine new through build_model's submarine: say who goes aboard (crew), beds, barrels, hull \
            (copper to 85 m, iron to 101 m) and whether it has a bow window (Pressurized Glass: plain glass gives way \
            at 28 m), and the server \
            lays it out by Deep Seas' rules and checks it, so its first version dives, surfaces and stays dry; then \
            change it with from_draft only where the player asked for something those don't cover, keeping its \
            machinery, tanks and propellers in place. One the player built stays theirs: copy it with from_world and \
            improve it in its own shape by the rules below. The rules, to explain the design and to judge one the \
            player built: it is a submarine only while an Oxygen Diffuser runs (a lever beside it, oxygen piped in \
            from an Electrolyzer that has a Shaft in its module slot, turns, and is switched on, fed sea water by a \
            pump from a Ballast Vent); otherwise Deep Seas treats it as a boat, which floods inside and tilts under \
            water. Nothing goes on top of an Electrolyzer or a Diffuser: they break themselves. Ballast Tanks push \
            through the centre of mass toward a climb or sink rate, each driving about 75 mass, so it needs its mass \
            over 75 of them and no more, since every tank is 8000 mB to pump before it dives; surfaced they ride at the \
            waterline, so they go in the roof and the hull and propellers stay under. A Pump Controller on its own \
            engine, piped to the tanks and a Ballast Vent, fills and empties them, and an Onboard Computer at the helm \
            runs it to hold the depth the pilot sets. Submarine Propellers in a 2x2 or 3x3 square push as one from \
            any one driven block (16 or 81 times a single one), along their axis by the way they spin: a wrench \
            reverses them. Steer with a square each side slowed one side at a time by comparators at the Steering \
            Wheel, through Analog Transmissions with the Throttle Lever; a Winds & Weather Rudder spins and tips a \
            submarine at the surface. Full blocks, machines and shut trapdoors seal; a slab, stair, pane or fence in \
            the hull lets the sea in. The hatch is an iron trapdoor on the roof, above the waterline when surfaced, \
            with a ladder down; every engine is reached from inside.""";

    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> WINDMILL_SAILS =
            net.minecraft.tags.BlockTags.create(net.minecraft.resources.ResourceLocation.parse("create:windmill_sails"));

    /** The briefs for the kinds of build this model is, not counting those already given in this answer. */
    static List<String> forModel(Map<BlockPos, BlockState> cells, Set<String> given) {
        boolean envelope = false, wing = false, prop = false, water = false, wheels = false, sub = false, rotor = false;
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
            // A rotor: a propeller bearing or propeller pushing up (its sails are its blades, not wings).
            if ((id.endsWith("propeller_bearing") || id.endsWith("propeller")) && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                    && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).getAxis().isVertical()) rotor = true;
            if (id.startsWith("create_high_seas:") || id.startsWith("create_submarine:")) water = true;
            if (id.equals("create_submarine:ballast_tank") || id.equals("create_submarine:oxygene_diffuser")
                    || id.equals("create_submarine:submarine_propeller") || id.equals("create_submarine:command_sub")) sub = true;
            if (id.equals("offroad:wheel_mount")) wheels = true;
        }
        List<String> kinds = new ArrayList<>();
        if (building >= SHOWPIECE) kinds.add("build");
        if (envelope || prop || water || wheels) kinds.add("vehicle");
        if (envelope || rotor || wing && prop) kinds.add("aircraft");
        if (envelope) kinds.add("airship");
        else if (rotor) kinds.add("helicopter");
        else if (wing && prop) kinds.add("plane");
        if (sub) kinds.add("submarine");
        else if (water) kinds.add("boat");
        List<String> out = new ArrayList<>();
        for (String k : kinds) {
            if (!given.add(k)) continue;
            out.add(switch (k) {
                case "build" -> BUILD;
                case "vehicle" -> VEHICLE;
                case "aircraft" -> AIRCRAFT;
                case "airship" -> AIRSHIP;
                case "plane" -> PLANE;
                case "submarine" -> SUBMARINE;
                case "helicopter" -> HELICOPTER;
                default -> BOAT;
            });
        }
        return out;
    }
}
