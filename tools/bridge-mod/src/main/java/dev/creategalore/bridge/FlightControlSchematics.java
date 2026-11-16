package dev.creategalore.bridge;

import ace.flight.block.CreativeSmartVectorThrusterBlockEntity;
import ace.flight.block.SmartVectorThrusterBlockEntity;
import ace.flight.compat.blueprint.CfcBlueprintNbtTransformer;
import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * A Create schematic carries Create: Flight Control's links and settings, so a Schematicannon prints a jet or a drone
 * wired: the computer's thrusters and controllers, each thruster's tank, the Redstone Proxy's linked blocks and face
 * mappings, and every block's settings (gains, speed limits, modes, Default ON). Flight Control marks none of it safe to
 * copy, so every printed part came out blank and the player linked it all again with the Tool Gun.
 *
 * <p>Flight Control stores every link as an offset from the block that holds it, and its own blueprint code lists every
 * one ({@link CfcBlueprintNbtTransformer}); the Bridge uses that list. A schematic printed turned or mirrored turns each
 * offset with it, and each direction a part keeps (a thruster's nozzle, the stabilizer's and couplers' forward) the
 * same way; mirrored, a proxy's left and right faces trade mappings, as the receivers beside them trade sides.
 *
 * <p>The couplers' settings are kept per axis of the craft's body (Body -v.z, a max speed each way, a gain per axis),
 * and a body's axes are the world's when it is assembled, so a craft printed turned would push along its old axes:
 * measured, a jet printed a quarter turn rolled sideways at 2 blocks/s instead of taking off. Turned, those settings and
 * the proxy's actions turn with the craft. Mirrored, the craft keeps flying nose first: the craft's forward is the
 * part's own forward setting, or north (Flight Control's own default without a Forward Indicator); a mirror that flips
 * that forward turns the body settings half round, one along it leaves them.
 *
 * <p>Only settings are copied. Left out: fuel in a thruster, what a part measured or was doing when the schematic was
 * saved (Create's kinetic state, a part's last mass, attitude and signals), and each proxy's and computer's identity and
 * the claims derived from it, which Flight Control's own blueprint code strips too (a second print would share the
 * first one's); a printed proxy claims its linked blocks again by itself. A thruster's installed Nozzle prints only as a
 * material: the Schematicannon takes one from its chest. Links Flight Control keeps on other mods' blocks (flap
 * bearings, Aeronautics thrusters and swivel mounts) stay theirs to carry.
 */
public final class FlightControlSchematics {
    static final String MOD = "create_flight_control";

    private FlightControlSchematics() {
    }

    static void register(IEventBus modBus) {
        if (!ModList.get().isLoaded(MOD)) return;
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(Cfc::install));
    }

    /**
     * A Flight Control block's data as a new one of {@code state} writes it with {@code set} merged in, cut to what a
     * schematic carries: how the book's own schematics carry a craft's links and settings. Empty without Flight Control.
     */
    public static CompoundTag data(BlockState state, CompoundTag set) {
        return ModList.get().isLoaded(MOD) ? Cfc.data(state, set) : new CompoundTag();
    }

    /** Loaded only when Flight Control is installed. */
    static final class Cfc {
        /** What a part was doing or measured, not how it is set: never copied (with the patterns in runtime()). */
        private static final Set<String> RUNTIME = Set.of(
                // fuel inside a thruster: printing it would hand fuel out for free
                "fluidAmountMb", "storedFluidId",
                // what a part was doing when saved
                "active", "working", "hasMass", "hasDisplayedAttitude", "hasRenderTargetOrientation",
                "rsThrust", "rsStrength", "rsLeft", "rsRight", "rsUp", "rsDown", "msSigHigh",
                // legacy copies of a controller's proxy claims, rebuilt from the proxy (positions where it was saved)
                "hasLP1", "lp1Idx", "lp1x", "lp1y", "lp1z", "hasLP2", "lp2Idx", "lp2x", "lp2y", "lp2z", "hasLinkedProxy");
        private static final Pattern SIGNAL = Pattern.compile("[vw][xyz]Signal(Neg|Pos)");
        /** Directions a part keeps, as names. */
        private static final List<String> FACINGS = List.of("forwardIndicatorFacing", "bodyForwardFacing");
        /** Body-axis settings: a max speed each way, a gain or a disable per axis, a proxy action on one. */
        private static final Pattern SIGNED = Pattern.compile("max([VW])([xyz])(Neg|Pos)");
        private static final Pattern PER_AXIS = Pattern.compile("(kp|kd|velocityKp|velocityKd|angularKp|angularKd)([XYZ])");
        private static final Pattern DISABLE = Pattern.compile("([vw])([xyz])Disable");
        private static final Pattern ACTION = Pattern.compile("(.*body_)([vw])([xyz])_(pos|neg)");
        private static final Pattern ACTION_DISABLE = Pattern.compile("(.*disable_)([vw])([xyz])");
        private static final ResourceLocation NOZZLE = ResourceLocation.fromNamespaceAndPath(MOD, "smart_vector_thruster_nozzle");

        static void install() {
            for (Map.Entry<ResourceKey<BlockEntityType<?>>, BlockEntityType<?>> e : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet()) {
                if (!e.getKey().location().getNamespace().equals(MOD)) continue;
                BlockEntityType<?> type = e.getValue();
                if (SafeNbtWriterRegistry.REGISTRY.get(type) == null)
                    SafeNbtWriterRegistry.REGISTRY.register(type, (be, tag, registries) -> tag.merge(safe(be, registries)));
                if (MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.get(type) == null)
                    MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(type, Cfc::transform);
                if (SchematicRequirementRegistries.BLOCK_ENTITIES.get(type) == null)
                    SchematicRequirementRegistries.BLOCK_ENTITIES.register(type, (be, state) -> nozzle(be));
            }
        }

        /** The part's settings and links, as a schematic should carry them. */
        static CompoundTag safe(BlockEntity be, HolderLookup.Provider registries) {
            CompoundTag t = be.saveWithoutMetadata(registries);
            strip(t);
            return t;
        }

        static CompoundTag data(BlockState state, CompoundTag set) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            HolderLookup.Provider registries = server != null ? server.registryAccess()
                    : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            BlockEntity be = state.getBlock() instanceof EntityBlock eb ? eb.newBlockEntity(BlockPos.ZERO, state) : null;
            CompoundTag t = be == null ? new CompoundTag() : be.saveWithoutMetadata(registries);
            t.merge(set);
            strip(t);
            return t;
        }

        private static void strip(CompoundTag t) {
            CfcBlueprintNbtTransformer.stripCreateKineticRuntime(t);
            CfcBlueprintNbtTransformer.stripBlueprintProxyIdentityAndDerivedState(t);
            for (String k : List.copyOf(t.getAllKeys())) if (runtime(k)) t.remove(k);
        }

        static boolean runtime(String k) {
            return RUNTIME.contains(k) || k.length() > 4 && k.startsWith("last") && Character.isUpperCase(k.charAt(4))
                    || k.endsWith("SignalHigh") || k.endsWith("SwitchHigh") || SIGNAL.matcher(k).matches()
                    || k.startsWith("displayed") || k.startsWith("currentOrientation") || k.startsWith("renderTarget");
        }

        /** A thruster with its Nozzle in prints with one from the chest; a creative thruster needs none. */
        private static ItemRequirement nozzle(BlockEntity be) {
            if (!(be instanceof SmartVectorThrusterBlockEntity thruster) || be instanceof CreativeSmartVectorThrusterBlockEntity
                    || !thruster.hasNozzle) return ItemRequirement.NONE;
            Item item = BuiltInRegistries.ITEM.get(NOZZLE);
            return item == Items.AIR ? ItemRequirement.NONE : new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(item));
        }

        /**
         * Turns the part's links, directions and body-axis settings with the blocks round it: Create calls this for each
         * block entity of a schematic it prints (and of a contraption it puts back down turned).
         */
        static void transform(BlockEntity be, StructureTransform transform) {
            boolean mirrored = transform.mirror != null && transform.mirror != Mirror.NONE;
            if (!mirrored && (transform.rotation == null || transform.rotation == Rotation.NONE) && transform.angle % 360 == 0) return;
            if (be.getLevel() == null) return;
            HolderLookup.Provider registries = be.getLevel().registryAccess();
            CompoundTag t = be.saveWithoutMetadata(registries);
            BlockPos at = be.getBlockPos();
            // Every link Flight Control keeps, as its blueprint code lists them: an offset from this block, turned.
            CfcBlueprintNbtTransformer.resolveReferences(t, at, at, (tag, key, kind, target) ->
                    kind == CfcBlueprintNbtTransformer.ReferenceKind.RELATIVE
                            ? CfcBlueprintNbtTransformer.ReferenceResolution.mapped(at.offset(transform.applyWithoutOffset(target.subtract(at))))
                            : CfcBlueprintNbtTransformer.ReferenceResolution.absent());
            turnVector(t, "tcx", "tcy", "tcz", transform);
            if (t.contains("linkedThrusters", Tag.TAG_LIST)) {
                ListTag list = t.getList("linkedThrusters", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    if (entry.contains("dir", Tag.TAG_ANY_NUMERIC))
                        entry.putByte("dir", (byte) turn(Direction.from3DDataValue(entry.getByte("dir")), transform).get3DDataValue());
                    turnVector(entry, "tcx", "tcy", "tcz", transform);
                }
            }
            // The body's axes: turned with the craft, and half round when a mirror flips its forward.
            Direction forward = forward(t);
            boolean flipsNose = mirrored && transform.mirrorFacing(forward) != forward;
            turnBody(t, d -> transform.rotateFacing(flipsNose && d.getAxis().isHorizontal() ? d.getOpposite() : d));
            for (String k : FACINGS) {
                Direction d = t.contains(k, Tag.TAG_STRING) ? Direction.byName(t.getString(k)) : null;
                if (d != null) t.putString(k, turn(d, transform).getSerializedName());
            }
            if (mirrored && t.contains("faceAssignments", Tag.TAG_LIST)) swapSides(t.getList("faceAssignments", Tag.TAG_COMPOUND));
            be.loadWithComponents(t, registries);
            be.setChanged();
        }

        private static Direction turn(Direction d, StructureTransform transform) {
            return transform.rotateFacing(transform.mirrorFacing(d));
        }

        /** The craft's forward as this part has it set, else north, as Flight Control takes it without a Forward Indicator. */
        private static Direction forward(CompoundTag t) {
            for (String k : FACINGS) {
                Direction d = t.contains(k, Tag.TAG_STRING) ? Direction.byName(t.getString(k)) : null;
                if (d != null && d.getAxis().isHorizontal()) return d;
            }
            return Direction.NORTH;
        }

        /** Moves every body-axis setting and proxy action to the axis (and way along it) the body turns it to. */
        static void turnBody(CompoundTag t, UnaryOperator<Direction> body) {
            List<String> old = new ArrayList<>();
            CompoundTag moved = new CompoundTag();
            for (String k : t.getAllKeys()) {
                Matcher m;
                if ((m = SIGNED.matcher(k)).matches()) {
                    Direction d = body.apply(direction(m.group(2), m.group(3).equals("Pos")));
                    moved.put("max" + m.group(1) + axis(d) + (positive(d) ? "Pos" : "Neg"), t.get(k).copy());
                } else if ((m = PER_AXIS.matcher(k)).matches()) {
                    Direction d = body.apply(direction(m.group(2).toLowerCase(Locale.ROOT), true));
                    moved.put(m.group(1) + axis(d).toUpperCase(Locale.ROOT), t.get(k).copy());
                } else if ((m = DISABLE.matcher(k)).matches()) {
                    moved.put(m.group(1) + axis(body.apply(direction(m.group(2), true))) + "Disable", t.get(k).copy());
                } else continue;
                old.add(k);
            }
            old.forEach(t::remove);
            t.merge(moved);
            if (t.contains("faceAssignments", Tag.TAG_LIST)) {
                ListTag faces = t.getList("faceAssignments", Tag.TAG_COMPOUND);
                for (int i = 0; i < faces.size(); i++) {
                    CompoundTag f = faces.getCompound(i);
                    if (f.contains("actionId", Tag.TAG_STRING)) f.putString("actionId", action(f.getString("actionId"), body));
                }
            }
        }

        static String action(String id, UnaryOperator<Direction> body) {
            Matcher m = ACTION.matcher(id);
            if (m.matches()) {
                Direction d = body.apply(direction(m.group(3), m.group(4).equals("pos")));
                return m.group(1) + m.group(2) + axis(d) + (positive(d) ? "_pos" : "_neg");
            }
            m = ACTION_DISABLE.matcher(id);
            if (m.matches()) return m.group(1) + m.group(2) + axis(body.apply(direction(m.group(3), true)));
            return id;
        }

        private static Direction direction(String axis, boolean positive) {
            return Direction.fromAxisAndDirection(Direction.Axis.byName(axis),
                    positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        }

        private static String axis(Direction d) {
            return d.getAxis().getName();
        }

        private static boolean positive(Direction d) {
            return d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        }

        /** A vector the part keeps (where its thrust acts, from it), turned with it. */
        private static void turnVector(CompoundTag t, String kx, String ky, String kz, StructureTransform transform) {
            if (!t.contains(kx, Tag.TAG_ANY_NUMERIC) || !t.contains(ky, Tag.TAG_ANY_NUMERIC) || !t.contains(kz, Tag.TAG_ANY_NUMERIC)) return;
            Vec3 v = transform.applyWithoutOffsetUncentered(new Vec3(t.getFloat(kx), t.getFloat(ky), t.getFloat(kz)));
            t.putFloat(kx, clean(v.x));
            t.putFloat(ky, clean(v.y));
            t.putFloat(kz, clean(v.z));
        }

        /** Drops the rounding a quarter turn leaves (1.2E-16 for 0). */
        private static float clean(double v) {
            return (float) (Math.round(v * 1e5) / 1e5);
        }

        /**
         * Mirrored, a proxy's left and right faces trade what they map to: the receiver that drove its left face now stands
         * on its right. Faces are named relative to the proxy (front, back, left, right, top).
         */
        private static void swapSides(ListTag faces) {
            for (int i = 0; i < faces.size(); i++) {
                CompoundTag f = faces.getCompound(i);
                if (!f.contains("face", Tag.TAG_STRING)) continue;
                String face = f.getString("face");
                if (face.equalsIgnoreCase("left")) f.putString("face", match(face, "right"));
                else if (face.equalsIgnoreCase("right")) f.putString("face", match(face, "left"));
            }
        }

        private static String match(String like, String word) {
            return Character.isUpperCase(like.charAt(0)) ? word.toUpperCase(Locale.ROOT) : word;
        }
    }
}
