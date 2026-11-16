package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a skilled builder designs each kind of build, given to the book with the first model of that kind in an answer, or
 * when it asks for one (design_guide) to advise on a vehicle without a model, instead of sitting in the instructions
 * sent with every question. What a check enforces (posts on slabs, an engine the crew can reach, a cold helm) isn't
 * repeated here: the check's own result says it when it happens.
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
            one Throttle Lever, anywhere from stopped to flat out. The wheel's cog turns a Swivel Bearing that swings a \
            Wooden Propeller at the stern, or a comparator against each side of the wheel slows one side's line. A plane \
            is the exception: it turns by banking and is flown from an Aeroworks Control Stand (a stick for its ailerons \
            and elevator, a throttle lever, brake pedals, each on its own Redstone Link frequency: design_guide plane \
            has it); tell the player which frequency each control and receiver gets. \
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

    /**
     * The plane design guide: every part this world has for a plane and how each works, from a sweep of the mods' own code
     * and the Book's laid-out biplane flown in a test world. Long, so it comes only when a plane does: through
     * design_guide, or with the first plane model of an answer. The pilot flies it (ailerons, elevator); a gyroscope is an
     * add-on only, since it stops the rolls and loops a plane is for.
     */
    static final String PLANE = """
            PLANE DESIGN GUIDE: the parts this world has for a plane and how each works, to design one, explain it and \
            judge one the player built.
            Start: design every new small plane through build_model's plane: seats (one or two), wood and colour, engine \
            prop or jet, then wings 1 (one wide wing), 2 (a biplane) or 0, wing_block copycat_wing (the default) or sail, \
            span 0 unless the player named a size, tips round (a softer look) or square (the most lift). The server lays \
            it out joined, geared, wired and balanced, checks it and says what it does and how to build and fly it; say \
            why you chose what you did, and quote its take-off and landing runs. Change it with from_draft only where the \
            player asked for something it doesn't cover (paint, trim, a window), keeping its engines, gear, wing and tail \
            in place. One the player built stays theirs: copy it with from_world and improve it in its own shape by the \
            rules below, never by swapping in a laid-out plane. When a draft fails, change the design.
            How it flies: only lift blocks facing up lift it, more the faster it goes and less as the air thins with \
            height, so it needs a run to rise and has a ceiling. Lift for weight per block: Copycat Wing (8m) 0.71 for \
            0.25, the best there is (cut a Wing on a Mechanical Saw or stonecutter for a Copycat Wing, place one and use a \
            second on it); Copycat Wing (4m) and a Create sail 0.48 for 0.25; Copycat Wing (12m) 0.95 for 0.5; a Wing or \
            Tempered Wing 0.48 for a whole 1. A Copycat Wing takes the look of any block used on it (white wool or \
            concrete for a classic wing) at the same weight, and shows a bare frame until then. Copycat Wings make the \
            smallest plane: a two-seater flies on a biplane 13 wide and 4 deep of them, but needs one 17 wide and 6 deep \
            of sails, and one deck of sails can't lift it at all. Symmetric \
            Sails lift nothing and drag when angled to the air: fins, the tail's stabiliser, control surfaces, propeller \
            blades. Weight per block: wool, wooden stairs and slabs, trapdoors, fences, panes, ladders, sails and \
            Redstone Links 0.25; planks, logs, shafts, cogwheels and seats 0.5; most machines and full blocks 1; a Wheel \
            Mount and stone 2; metal blocks and flywheels 4.
            Shape: it reads as an aircraft: a slim body of light wood (stairs rather than planks, closed at the sides, no \
            sky through it) narrowing back to a tail with a flat stabiliser and an upright fin, the wings over the centre \
            of mass, every part visibly joined to the next (a sail is a thin panel across the middle of its block and joins \
            only blocks beside it). The pilot sits with every control in reach, a low nose and a window ahead of their \
            eyes, and no engine, machine or wing in front of the windscreen. A nose propeller must leave the way ahead \
            open, and spinning it sweeps a full disc: give it two slim blades with open sky between them, never a solid \
            disc of sails at the pilot's eye line (the check spins it and faults one that hides more than a quarter of \
            the middle of the view: from the back seat two blades five wide hide about a sixth, a five-wide disc half). \
            Keep its hub about level with the centre of mass: a propeller set low pulls below it and noses the plane up \
            with power. Its lowest tip clears the ground by half a block or more, parked on its wheels. A tandem cockpit \
            seats the pilot in the back, as in an open-cockpit biplane, and a passenger in front.
            Flying it level: nothing holds a plane level by itself, and that is what lets it bank, roll and loop: the \
            pilot flies it, as a real one. Ailerons near the wingtips roll it, an elevator behind the tail's stabiliser \
            pitches it: each a strip of Symmetric Sails glued out from the face of a Swivel Bearing (each strip its own \
            small glue box), the bearing's cog turned by a Tilt Adapter on a turning shaft. A Tilt Adapter swings its \
            output shaft to (right signal minus left signal)/15 of 45 degrees and holds it there (an Advanced Tilt \
            Adapter, with a Precision Mechanism, goes to 90), so Redstone Links on its two sides, on the stick's \
            frequencies, are its control wires. A Swivel Bearing turns at its cog's speed, up to 96 RPM; a Portable \
            Engine's 32 RPM is plenty. Other ways to swing a surface: an Aeroworks Mechanical Servo (signal strength sets \
            an angle between two set ones) or Stepper Servo (steps either way by strength), brass tier, 4 stress units \
            per RPM; or a Torsion Spring, which springs back to centre, fed through a Directional Gearshift. Banked, its \
            wings' tipped lift curves it round while the fin swings the nose after it; a little back stick holds the \
            height in a turn.
            The Aeroworks Gyroscope is an add-on, never the default: turned from below (full strength at 256 RPM, 16 \
            stress units per RPM: 4096, two Portable Engines of its own) it pulls the plane level and damps every roll, \
            pitch and turn, so it fights banking and stops rolls and loops. Give one only to a player who asks for a \
            plane that flies itself or who can't fly one by hand, and say what it costs them; redstone into its east or \
            west side weakens it about one axis and into its north or south side the other (15 switches that axis off), \
            so a lever there frees it for aerobatics. A jet's Smart Attitude Stabilizer levels it the same way, and its \
            Master Switch turns that off.
            Push: a propeller pushes only up to its airflow speed, so spin it at 256 RPM, the most a shaft turns. A \
            Propeller Bearing turning a glued disc of sails (sails, wool and Symmetric Sails all count as blades) pushes \
            0.2 x sails^1.5 x RPM for 2 stress units per sail per RPM, with an airflow of 0.05 x the square root of its \
            sails x RPM: twelve sails at 256 RPM push about 2100 for 6144 stress, three Portable Engines, and a bigger \
            slower disc pushes more for the same engines. A Wooden or Andesite Propeller pushes 1 x RPM for 4 stress per \
            RPM (airflow a tenth of its RPM). Push at the height of the centre of mass, or the nose pitches as the power \
            changes. Reaching 256 RPM needs no tower of cogwheels: a Rotation Speed Controller (brass casing and a \
            Precision Mechanism) takes any turning shaft through it and turns the Large Cogwheel on top at the speed on \
            its value panel, whatever drives it (it starts at 16, so set it; below zero turns the other way). Or a source \
            that runs at 256 itself: an Electric Motor (Crafts & Additions; 16384 stress at 256, on Forge energy from an \
            Accumulator), a Stirling Engine (Create Propulsion; 4096 at 256, over a Solid or Liquid Burner) or a TFMG \
            engine (up to 256 on its throttle signal, with cylinders, fuel and an exhaust). A Portable Engine (any furnace \
            fuel in its one slot; a stack of Coal Blocks lasts hours) turns 32 RPM with 2048 stress, 64 RPM and 4096 on \
            Blaze Cake; every machine costs its stress per RPM x its own RPM, so a line stepped up from 32 to 256 turns an \
            eighth as much per engine. Portable Engines on one line add up, and one lit while the line already turns sets \
            its own direction to match. The throttle is an Analog Transmission in the line (shaft in, cogwheel on toward \
            the propeller): no signal passes full speed, a stronger one slows it, 15 stops it.
            Wheels: three Offroad Wheel Mounts, each facing sideways with a Tire on its front, the face it faces, which \
            must be open (a Tire goes on only there, by right-clicking that face; Small, Tire, Large or Monstrous differ \
            only in size): one under the nose, two just behind the centre of mass. A mount's suspension \
            (its value panel, 5 to 180, starting at 10) sets how firmly it holds, and a wheel drags 1.5 x that per \
            block/s until the plane lifts off, so a light plane runs them at 5, their lowest. Redstone into a mount's top \
            brakes it by strength; into its sides it steers (up to 30 degrees). They don't retract; never Grindstones or \
            bare blocks as gear. A plane that sits back on its main wheels and tail, nose wheel off the ground, steers on \
            the ground by braking one main wheel, as light planes do with toe brakes: a Pedal per wheel.
            Cockpit: the pilot flies from an Aeroworks Control Stand (or Control Desk), never a ship's wheel. It has two \
            small sockets, one large (taking the place of both) and three pedal sockets; each direction of each module \
            sends its own Redstone Link frequency (two items) at strength 0 to 15, set with Shift + right-click, where \
            Idle Power, Full Power, Swap Idle and Full Power and Range (Full or one way only) shape it. The stick is a \
            Joystick Module: Left and Right for the ailerons, Forward and Back for the elevator, on the mouse, springing \
            back. The throttle is a Lever Module (it stays where left): Forward on the throttle's frequency, Range \
            Forward Only, Swap Idle and Full Power pressed, so back sends 15 (stopped) and forward 0 (full). Pedal \
            Modules spring back: brakes. A Yoke Module (large socket) also pitches and rolls; a Throttle Quadrant has \
            four levers. The lever and pedals start on the W and S keys, so give each its own. Right-click the stand \
            with an empty hand to fly, Esc to let go. build_model writes each receiving Redstone Link's frequency \
            (links), each Rotation Speed Controller's speed, each Wheel Mount's suspension and a Tire in each mount \
            into the model, so a Schematicannon prints them set (Tires come from its chest like blocks); the Control \
            Stand's modules and their frequencies the player fits by hand: tell them.
            Building it: on open flat ground or a runway pointed into the open, every engine cold. Glue it with Super \
            Glue in boxes covering all of it but each control surface's strip, and give each strip a small box of its \
            own; use the Physics Assembler; then right-click each Swivel Bearing with an empty hand to give it its strip; \
            only then fuel the engines (a propeller turning when it is assembled loses its blades, and controllers spun \
            up during assembly drop off). It lands to refuel.
            Flying: at full power it lifts off by itself and keeps nosing up, so once the wheels are off the pilot brings \
            the throttle back and holds a gentle climb; loops and rolls want height to spare; it lands at low power with \
            the nose a little down, then brakes. The layout says the numbers for its own design.
            Jets: engine jet trades the propeller drive for two Smart Vector Thrusters (Create: Flight Control) either \
            side of the tail and a Flight Control Computer that swings and throttles them, fed by a Smart Attitude \
            Stabilizer (Level, Medium preset: keeps it level), a Velocity Coupler (the throttle: holds the speed set in \
            it, 30 blocks/s) and an Angular Velocity Coupler (the turns), all flown from the same Control Stand through \
            a Redstone Proxy. Lava or TFMG fuel lasts long at cruise. A jet needs Control Units (sequenced assembly; \
            diamonds); its schematic prints every Flight Control link and setting, turned or mirrored with it (built by \
            hand, the Flight Control Tool Gun links it), so give one to a \
            player who asks for a jet or thrusters, or can make those parts and wants speed or height. To judge a \
            player's jet: its thrusters go far from the centre of mass (the computer pitches it by swinging them; a \
            block or two from it, holding level took so much thrust that the plane ran away past its set speed and \
            climbed off), its wing balances it fore and aft by itself, it needs no gyroscope, and it is assembled with \
            the controllers' engine cold. Copycat Integrated Sails (Flight Control) lift only once aerodynamics is \
            switched on in their screen, and the computer can work them as flaps.
            What each part takes: a Tilt Adapter and an Andesite Propeller are andesite; a Portable Engine needs iron \
            plate, an Engine Assembly and a blast furnace; a Swivel Bearing an industrial iron block; a Propeller Bearing, \
            an Analog Transmission and a Rotation Speed Controller brass (the controller a Precision Mechanism too); the \
            Control Stand copper casing, a Precision Mechanism and an Electron Tube, and its modules brass casing and a \
            transmitter. When the player can't make a part yet, say what it is, build the nearest plane they can, and \
            name what to make next.""";

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

    /** The kinds design_guide hands over. */
    static final List<String> KINDS = List.of("plane", "airship", "helicopter", "submarine", "boat");

    /** The design_guide tool: one kind's guide, asked for when a question is about one (it is long, so not sent always). */
    static com.google.gson.JsonObject definition() {
        com.google.gson.JsonObject props = new com.google.gson.JsonObject();
        com.google.gson.JsonObject kind = WorldTools.prop("string", "Kind.");
        com.google.gson.JsonArray kinds = new com.google.gson.JsonArray();
        KINDS.forEach(kinds::add);
        kind.add("enum", kinds);
        props.add("kind", kind);
        return WorldTools.tool("design_guide", "How to design one kind of vehicle here: its parts, the numbers that decide "
                + "how it flies, its controls, what each part takes. Read it before you design, judge or advise on a plane, "
                + "airship, helicopter or submarine.", props);
    }

    /** design_guide: the briefs for a kind not yet given in this answer, marking them given. */
    static String guide(String kind, Set<String> given) {
        String k = kind == null ? "" : kind.strip().toLowerCase(java.util.Locale.ROOT);
        if (!KINDS.contains(k)) return "No design guide for '" + kind + "': kind is one of " + String.join(", ", KINDS) + ".";
        List<String> kinds = new ArrayList<>(List.of("vehicle"));
        if (!k.equals("submarine") && !k.equals("boat")) kinds.add("aircraft");
        kinds.add(k);
        List<String> out = new ArrayList<>();
        for (String one : kinds) if (given.add(one)) out.add(brief(one));
        if (out.isEmpty()) return "The " + k + " design guide is already above in this answer.";
        return "Design guide: " + k + " (for you: follow it, and tell the player what matters to them in plain words)\n"
                + String.join("\n", out);
    }

    private static String brief(String kind) {
        return switch (kind) {
            case "build" -> BUILD;
            case "vehicle" -> VEHICLE;
            case "aircraft" -> AIRCRAFT;
            case "airship" -> AIRSHIP;
            case "plane" -> PLANE;
            case "submarine" -> SUBMARINE;
            case "helicopter" -> HELICOPTER;
            default -> BOAT;
        };
    }

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
        for (String k : kinds) if (given.add(k)) out.add(brief(k));
        return out;
    }
}
