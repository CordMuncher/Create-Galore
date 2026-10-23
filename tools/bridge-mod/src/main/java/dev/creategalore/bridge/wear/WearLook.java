package dev.creategalore.bridge.wear;

import java.util.List;
import java.util.Map;

/**
 * Where everything sits on the wear screen (shared by the menu, which places the slots, and the screen, which draws
 * them). The groups are laid out head to feet beside the player: head and neck on top, then back and hands, rings and
 * waist, feet and pockets, with the inventory below.
 */
public final class WearLook {
    private WearLook() {
    }

    public static final int WIDTH = 264, HEIGHT = 233;
    public static final int ARMOR_X = 8, ARMOR_Y = 8, OFFHAND_X = 8, OFFHAND_Y = 84;
    public static final int MODEL_X0 = 28, MODEL_Y0 = 8, MODEL_X1 = 84, MODEL_Y1 = 102;
    public static final int INV_X = 51, INV_LABEL_Y = 142, INV_Y = 152, HOTBAR_Y = 210;

    /** Group id -> {x, y, columns} of its first slot. */
    private static final Map<String, int[]> SPOTS = Map.ofEntries(
            Map.entry("hat", new int[] {90, 16, 1}), Map.entry("eyes", new int[] {108, 16, 1}), Map.entry("mask", new int[] {126, 16, 1}),
            Map.entry("necklace", new int[] {154, 16, 3}),
            Map.entry("cape", new int[] {90, 45, 1}), Map.entry("back", new int[] {108, 45, 1}), Map.entry("body", new int[] {126, 45, 1}),
            Map.entry("hands", new int[] {154, 45, 2}), Map.entry("shield", new int[] {196, 45, 1}),
            Map.entry("rings", new int[] {90, 74, 5}), Map.entry("belt", new int[] {188, 74, 1}), Map.entry("quiver", new int[] {206, 74, 1}),
            Map.entry("spellbook", new int[] {232, 74, 1}),
            Map.entry("feet", new int[] {90, 122, 1}), Map.entry("charm", new int[] {116, 122, 3}), Map.entry("gadget", new int[] {178, 122, 3}));

    /** Section labels: translation key suffix, x, y. */
    public record Label(String key, int x, int y) {
    }

    public static final List<Label> LABELS = List.of(
            new Label("head", 90, 6), new Label("neck", 154, 6),
            new Label("back", 90, 35), new Label("hands", 154, 35), new Label("shield", 196, 35),
            new Label("rings", 90, 64), new Label("waist", 188, 64), new Label("spellbook", 232, 64),
            new Label("feet", 90, 112), new Label("charm", 116, 112), new Label("gadget", 178, 112));

    /** Label key -> the groups it names (a label is drawn only when one of them is on the screen). */
    public static final Map<String, List<String>> LABEL_GROUPS = Map.ofEntries(
            Map.entry("head", List.of("hat", "eyes", "mask")), Map.entry("neck", List.of("necklace")),
            Map.entry("back", List.of("cape", "back", "body")), Map.entry("hands", List.of("hands")), Map.entry("shield", List.of("shield")),
            Map.entry("rings", List.of("rings")), Map.entry("waist", List.of("belt", "quiver")), Map.entry("spellbook", List.of("spellbook")),
            Map.entry("feet", List.of("feet")), Map.entry("charm", List.of("charm")), Map.entry("gadget", List.of("gadget")));

    public static int[] slotPos(WearLayout layout, int slot) {
        WearGroups.Group g = layout.group(slot);
        int k = slot - layout.first(g);
        int[] spot = SPOTS.getOrDefault(g.id(), new int[] {90, 16, 8});
        return new int[] {spot[0] + (k % spot[2]) * 18, spot[1] + (k / spot[2]) * 18};
    }
}
