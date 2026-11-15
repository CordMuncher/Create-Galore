package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a model is wired up, carried into its schematic so the Schematicannon prints it set: each Redstone Link's two
 * frequency items, each Rotation Speed Controller's speed, and each Wheel Mount's suspension and Tire. Create's
 * Schematicannon keeps what a block entity marks as safe to copy: a link's frequency and a block's value panel (written
 * as {@code FrequencyFirst}/{@code FrequencyLast} and {@code ScrollValue}), and a mount's Tire, which the Bridge marks
 * (WheelMountSchematics; the cannon takes a Tire from its chest for it). Anything else on a block (a Control Stand's
 * modules, fuel) the player sets by hand.
 *
 * <p>An entry is {@code {x, y, z, link: [first item, last item]}} or {@code {x, y, z, value: n}}, a mount's with
 * {@code tire: item} too; the model JSON carries them as {@code wiring}, the client turns each into the block's data in
 * the schematic it saves.
 */
public final class Wiring {
    private static final String LINK = "create:redstone_link";
    private static final String SPEED_CONTROLLER = "create:rotation_speed_controller";
    private static final String WHEEL_MOUNT = "offroad:wheel_mount";

    private Wiring() {
    }

    /** A link entry: the receiver or transmitter at {@code at} on the frequency of these two items. */
    static JsonObject link(BlockPos at, String first, String last) {
        JsonObject o = cell(at);
        JsonArray items = new JsonArray();
        items.add(first);
        items.add(last);
        o.add("link", items);
        return o;
    }

    /** A value-panel entry: the setting on the block at {@code at} (a speed controller's speed). */
    static JsonObject value(BlockPos at, int value) {
        JsonObject o = cell(at);
        o.addProperty("value", value);
        return o;
    }

    /** A Wheel Mount's entry: its suspension and the Tire in it. */
    static JsonObject mount(BlockPos at, int suspension, String tire) {
        JsonObject o = value(at, suspension);
        o.addProperty("tire", tire);
        return o;
    }

    /** The Tire a Wheel Mount prints with when nothing names another: Offroad's standard one. */
    static final String TIRE = "offroad:tire";

    /**
     * Gives every Wheel Mount in {@code cells} a Tire in {@code wiring}, the standard one where its entry names none, so a
     * schematic never prints a bare mount (a mount without a Tire carries nothing).
     */
    static void fitTires(JsonArray wiring, Map<BlockPos, BlockState> cells) {
        Map<BlockPos, JsonObject> at = new java.util.HashMap<>();
        for (JsonElement e : wiring) if (e.isJsonObject()) at.putIfAbsent(at(e.getAsJsonObject()), e.getAsJsonObject());
        cells.forEach((p, st) -> {
            if (!BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals(WHEEL_MOUNT)) return;
            JsonObject o = at.get(p);
            if (o == null) {
                o = cell(p);
                wiring.add(o);
            }
            if (!o.has("tire")) o.addProperty("tire", TIRE);
        });
    }

    private static JsonObject cell(BlockPos at) {
        JsonObject o = new JsonObject();
        o.addProperty("x", at.getX());
        o.addProperty("y", at.getY());
        o.addProperty("z", at.getZ());
        return o;
    }

    static BlockPos at(JsonObject o) {
        return new BlockPos(Diagrams.intOr(o, "x", 0), Diagrams.intOr(o, "y", 0), Diagrams.intOr(o, "z", 0));
    }

    /** The same entry moved to {@code to}. */
    static JsonObject moved(JsonObject o, BlockPos to) {
        JsonObject n = o.deepCopy();
        n.addProperty("x", to.getX());
        n.addProperty("y", to.getY());
        n.addProperty("z", to.getZ());
        return n;
    }

    /** Why the entry can't sit on {@code state} (the block at its cell, or null for air); null if it can. */
    static String problem(JsonObject o, BlockState state) {
        BlockPos p = at(o);
        String where = "cell " + p.getX() + "," + p.getY() + "," + p.getZ();
        String id = state == null ? "air" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (o.has("link")) {
            if (!id.equals(LINK)) return "links names " + where + ", which holds " + id + ", not a Redstone Link";
            List<String> items = items(o);
            if (items == null) return "links at " + where + " needs two item ids";
            for (String item : items) {
                ResourceLocation rl = ResourceLocation.tryParse(item);
                if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl) || rl.getPath().equals("air"))
                    return "links at " + where + " names " + item + ", which isn't an item";
            }
            return null;
        }
        if (o.has("tire")) {
            if (!id.equals(WHEEL_MOUNT)) return "a Tire at " + where + ", which holds " + id + ", not a Wheel Mount";
            if (!o.get("tire").isJsonPrimitive() || !dev.creategalore.bridge.WheelMountSchematics.isTire(ResourceLocation.tryParse(id(o.get("tire").getAsString()))))
                return "the Tire at " + where + " names " + o.get("tire") + ", which isn't one of Offroad's Tires";
        }
        if (o.has("value")) return id.equals(WHEEL_MOUNT) || id.equals(SPEED_CONTROLLER) ? null
                : "a setting at " + where + ", which holds " + id + " (only Wheel Mounts and speed controllers keep one)";
        if (o.has("tire")) return null;
        return "a wiring entry at " + where + " with nothing to set";
    }

    /** Keeps the entries whose cells still hold a block they fit (a later version may have changed or removed it). */
    static JsonArray fitting(JsonArray wiring, Map<BlockPos, BlockState> cells) {
        JsonArray out = new JsonArray();
        for (JsonElement e : wiring)
            if (e.isJsonObject() && problem(e.getAsJsonObject(), cells.get(at(e.getAsJsonObject()))) == null) out.add(e);
        return out;
    }

    private static List<String> items(JsonObject o) {
        if (!o.get("link").isJsonArray() || o.getAsJsonArray("link").size() != 2) return null;
        JsonArray a = o.getAsJsonArray("link");
        if (!a.get(0).isJsonPrimitive() || !a.get(1).isJsonPrimitive()) return null;
        return List.of(id(a.get(0).getAsString()), id(a.get(1).getAsString()));
    }

    /** An item id with its namespace (stick: minecraft:stick). */
    static String id(String item) {
        String s = item.strip().toLowerCase(java.util.Locale.ROOT);
        return s.contains(":") ? s : "minecraft:" + s;
    }

    /**
     * The block's data the entry sets, as its block entity writes it (what a schematic's block carries as {@code nbt});
     * null if the entry sets nothing. Used by the client when it saves a model as a schematic.
     */
    public static CompoundTag nbt(JsonObject o) {
        CompoundTag tag = new CompoundTag();
        if (o.has("link") && o.get("link").isJsonArray()) {
            List<String> items = items(o);
            if (items == null) return null;
            tag.put("FrequencyFirst", stack(items.get(0)));
            tag.put("FrequencyLast", stack(items.get(1)));
            return tag;
        }
        if (o.has("value") && o.get("value").isJsonPrimitive()) tag.putInt("ScrollValue", o.get("value").getAsInt());
        if (o.has("tire") && o.get("tire").isJsonPrimitive()) {
            CompoundTag tire = new CompoundTag();
            tire.putString("id", id(o.get("tire").getAsString()));
            tire = dev.creategalore.bridge.WheelMountSchematics.tire(tire);
            if (tire != null) tag.put("CurrentStack", tire);
        }
        return tag.isEmpty() ? null : tag;
    }

    /** One of the item, as Create writes a frequency slot's stack. */
    private static CompoundTag stack(String item) {
        CompoundTag t = new CompoundTag();
        t.putString("id", item);
        t.putInt("count", 1);
        return t;
    }

    /**
     * What the book is told about the model's wiring: what its schematic sets, and which Redstone Links it leaves blank
     * (the player sets those by hand); null for a model with neither.
     */
    static String report(JsonArray wiring, Map<BlockPos, Float> speeds, Map<BlockPos, BlockState> cells) {
        int links = 0, values = 0, tires = 0;
        java.util.Set<BlockPos> linked = new java.util.HashSet<>();
        for (JsonElement e : wiring) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("link")) {
                links++;
                linked.add(at(o));
            } else if (o.has("value") && cells.get(at(o)) != null
                    && BuiltInRegistries.BLOCK.getKey(cells.get(at(o)).getBlock()).toString().equals(WHEEL_MOUNT)) values++;
            if (o.has("tire")) tires++;
        }
        List<String> blank = new java.util.ArrayList<>();
        cells.forEach((p, st) -> {
            if (!linked.contains(p) && BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals(LINK))
                blank.add(p.getX() + "," + p.getY() + "," + p.getZ());
        });
        if (links + values + tires + speeds.size() == 0 && blank.isEmpty()) return null;
        List<String> set = new java.util.ArrayList<>();
        if (links > 0) set.add(links + " Redstone Link frequenc" + (links == 1 ? "y" : "ies"));
        if (!speeds.isEmpty()) set.add(speeds.size() + " speed controller setting" + (speeds.size() == 1 ? "" : "s"));
        if (values > 0) set.add(values + " Wheel Mount suspension" + (values == 1 ? "" : "s"));
        if (tires > 0) set.add(tires + " Tire" + (tires == 1 ? "" : "s") + " in Wheel Mounts (the Schematicannon takes each "
                + "from its chest like a block; built by hand, a Tire goes on with a right-click on the mount's front, the face "
                + "it faces, where the wheel hangs)");
        StringBuilder s = new StringBuilder("Wiring: ");
        if (!set.isEmpty()) s.append("its schematic sets ").append(String.join(", ", set)).append(", so the Schematicannon "
                + "prints them ready (tell the player; built by hand, they set each as you say)");
        if (!blank.isEmpty()) {
            java.util.Collections.sort(blank);
            s.append(set.isEmpty() ? "" : "; ").append(blank.size()).append(" Redstone Link").append(blank.size() == 1 ? " has" : "s have")
                    .append(" no frequency in links (").append(String.join(" ", blank.subList(0, Math.min(8, blank.size()))))
                    .append(blank.size() > 8 ? " ..." : "").append("): give each its two items in links so the schematic carries them");
        }
        // What a schematic can't carry, for the parts this model has.
        java.util.Set<String> ids = new java.util.HashSet<>();
        cells.values().forEach(st -> ids.add(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString()));
        List<String> byHand = new java.util.ArrayList<>();
        if (ids.stream().anyMatch(id -> id.startsWith("aeroworks:") && id.endsWith("control_stand")))
            byHand.add("the Control Stand's modules and their frequencies");
        long mounts = cells.values().stream().filter(st -> BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals(WHEEL_MOUNT)).count();
        if (mounts > tires) byHand.add("a Tire in each Wheel Mount without one in the schematic (right-click the mount's front, "
                + "the face it faces, with it)");
        if (!byHand.isEmpty()) s.append(". Not carried, so the player adds them: ").append(String.join(", ", byHand));
        return s.append('.').toString();
    }

    /** The cell an entry is at, for the client. */
    public static int[] cellOf(JsonObject o) {
        BlockPos p = at(o);
        return new int[] {p.getX(), p.getY(), p.getZ()};
    }
}
