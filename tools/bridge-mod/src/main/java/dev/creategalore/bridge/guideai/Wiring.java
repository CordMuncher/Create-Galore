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
 * frequency items, each Rotation Speed Controller's speed and each Wheel Mount's suspension. Create's Schematicannon
 * keeps what a block entity marks as safe to copy, and those three are (a link's frequency and a block's value panel,
 * written as {@code FrequencyFirst}/{@code FrequencyLast} and {@code ScrollValue}); anything else on a block (a Control
 * Stand's modules, a Tire in a mount, fuel) the player sets by hand.
 *
 * <p>An entry is {@code {x, y, z, link: [first item, last item]}} or {@code {x, y, z, value: n}}; the model JSON carries
 * them as {@code wiring}, the client turns each into the block's data in the schematic it saves.
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

    /** A value-panel entry: the setting on the block at {@code at} (a Wheel Mount's suspension). */
    static JsonObject value(BlockPos at, int value) {
        JsonObject o = cell(at);
        o.addProperty("value", value);
        return o;
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
        if (o.has("value")) return id.equals(WHEEL_MOUNT) || id.equals(SPEED_CONTROLLER) ? null
                : "a setting at " + where + ", which holds " + id + " (only Wheel Mounts and speed controllers keep one)";
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
        if (o.has("value") && o.get("value").isJsonPrimitive()) {
            tag.putInt("ScrollValue", o.get("value").getAsInt());
            return tag;
        }
        return null;
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
        int links = 0, values = 0;
        java.util.Set<BlockPos> linked = new java.util.HashSet<>();
        for (JsonElement e : wiring) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("link")) {
                links++;
                linked.add(at(o));
            } else values++;
        }
        List<String> blank = new java.util.ArrayList<>();
        cells.forEach((p, st) -> {
            if (!linked.contains(p) && BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals(LINK))
                blank.add(p.getX() + "," + p.getY() + "," + p.getZ());
        });
        if (links + values + speeds.size() == 0 && blank.isEmpty()) return null;
        List<String> set = new java.util.ArrayList<>();
        if (links > 0) set.add(links + " Redstone Link frequenc" + (links == 1 ? "y" : "ies"));
        if (!speeds.isEmpty()) set.add(speeds.size() + " speed controller setting" + (speeds.size() == 1 ? "" : "s"));
        if (values > 0) set.add(values + " Wheel Mount suspension" + (values == 1 ? "" : "s"));
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
        if (ids.contains(WHEEL_MOUNT)) byHand.add("a Tire in each Wheel Mount");
        if (!byHand.isEmpty()) s.append(". Not carried, so the player adds them: ").append(String.join(", ", byHand));
        return s.append('.').toString();
    }

    /** The cell an entry is at, for the client. */
    public static int[] cellOf(JsonObject o) {
        BlockPos p = at(o);
        return new int[] {p.getX(), p.getY(), p.getZ()};
    }
}
