package dev.creategalore.bridge.client;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.lopymine.patpat.client.config.PatPatClientConfig;
import net.lopymine.patpat.client.config.resourcepack.PlayerConfig;
import net.lopymine.patpat.client.keybinding.KeybindingCombination;
import net.lopymine.patpat.client.keybinding.PatPatClientKeybindingManager;
import net.lopymine.patpat.client.keybinding.PatPatKeybinding;
import net.lopymine.patpat.client.manager.PatPatClientManager;
import net.lopymine.patpat.client.render.PatPatClientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Who gets a Shift + right-click on a mob: PatPat or the mob's own interaction.
 *
 * <p>PatPat (default keys Shift + right mouse) swallows the use key whenever an empty-handed player looks at a
 * living entity, so nothing else ever sees that click. The pack's rule is the other way round: when a mob does
 * something of its own on Shift + right-click (a tamed horse, donkey or llama opens its inventory, and any modded mob
 * whose interaction checks for sneaking), that wins for that mob; every other mob is patted.
 *
 * <p>So while the pat keys include the use key, the use key works as usual and the pat is decided where the game
 * handles the click ({@code Minecraft.startUseItem}): a mob whose interaction code checks whether the player sneaks
 * gets its interaction first and is patted only if that did nothing (with either hand); any other mob is patted
 * and its plain right-click (sitting a pet down, trading, riding) is skipped, as PatPat did before. With the pat
 * keys moved off the use key, PatPat works on its own as it always has.
 *
 * <p>Everything that touches PatPat's classes is only reached when PatPat is loaded.
 */
public final class PatPriority {
    private static Boolean loaded;
    /** Entity class -> its interaction code checks whether the player sneaks. */
    private static final Map<Class<?>, Boolean> SNEAK_INTERACTION = new ConcurrentHashMap<>();
    private static final Set<String> SNEAK_CHECKS =
            Set.of("isShiftKeyDown", "isSecondaryUseActive", "isCrouching", "isSteppingCarefully", "isDiscrete", "hasShiftDown");
    private static final Set<String> INTERACTIONS = Set.of("interact", "interactAt", "mobInteract");

    /** The mob patted once neither hand's interaction did anything this click. */
    private static LivingEntity pending;

    private PatPriority() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("patpat");
        return loaded;
    }

    /** The pat keys include the use key (PatPat's default, Shift + right mouse). */
    public static boolean sharesUseKey() {
        if (!loaded()) return false;
        try {
            KeybindingCombination keys = PatPatClientConfig.getInstance().getMainConfig().getPatCombination();
            return keys != null && keys.getKeys().contains(Minecraft.getInstance().options.keyUse.getKey());
        } catch (Throwable t) {
            return false;
        }
    }

    /** The click starts: nothing pending from the last one. */
    public static void clickStart() {
        pending = null;
    }

    /** The living entity this click would pat, or null (pat keys not held, cooldown, item in hand, ignored mob...). */
    public static LivingEntity patTarget(Entity target) {
        if (!sharesUseKey()) return null;
        if (!PatPatClientConfig.getInstance().getMainConfig().isModEnabled()) return null;
        PatPatKeybinding keys = PatPatClientKeybindingManager.getPatKeybinding();
        if (keys == null || !keys.isDown() || PatPatClientManager.getPatCooldown() != 0) return null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isDeadOrDying()) return null;
        LivingEntity patted = PatPatClientManager.getPatEntityFromHitResult();
        return patted == target ? patted : null;
    }

    public static void setPending(LivingEntity patted) {
        pending = patted;
    }

    /** One hand's interaction did something: no pat this click. */
    public static void interactionTaken() {
        pending = null;
    }

    /** The click ended with nothing done: pat the mob that was waiting for it. */
    public static void clickEnd() {
        LivingEntity patted = pending;
        pending = null;
        if (patted != null && patted.isAlive()) pat(patted);
    }

    public static void pat(LivingEntity patted) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        PatPatClientRenderer.registerClientPacket(
                new PatPatClientRenderer.PacketPat(patted, PlayerConfig.currentSession(), player, false));
        PatPatClientManager.setPatCooldown(4);
    }

    /**
     * Whether this mob does something of its own on Shift + right-click: its interaction methods (or the methods of
     * its own class they call) check whether the player sneaks. Read once per class from the class files, from the
     * mob's class up to (not including) {@link Mob}, whose own interaction never looks at sneaking.
     */
    public static boolean hasSneakInteraction(Entity entity) {
        return SNEAK_INTERACTION.computeIfAbsent(entity.getClass(), PatPriority::scan);
    }

    private static boolean scan(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != Mob.class && c != LivingEntity.class && c != Entity.class
                && c != Object.class; c = c.getSuperclass()) {
            if (classChecksSneak(c)) return true;
        }
        return false;
    }

    private static boolean classChecksSneak(Class<?> c) {
        String internal = c.getName().replace('.', '/');
        ClassLoader loader = c.getClassLoader() != null ? c.getClassLoader() : ClassLoader.getSystemClassLoader();
        byte[] bytes;
        try (InputStream in = loader.getResourceAsStream(internal + ".class")) {
            if (in == null) return false;
            bytes = in.readAllBytes();
        } catch (Exception e) {
            return false;
        }
        // Per method of this class: does it check sneaking, and which of this class's own methods does it call.
        Map<String, Set<String>> calls = new java.util.HashMap<>();
        Set<String> sneaky = new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                String self = name + desc;
                Set<String> out = calls.computeIfAbsent(self, k -> new HashSet<>());
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String mName, String mDesc, boolean itf) {
                        if (SNEAK_CHECKS.contains(mName) && mDesc.equals("()Z")) sneaky.add(self);
                        else if (owner.equals(internal)) out.add(mName + mDesc);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        ArrayDeque<String> todo = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (String m : calls.keySet()) {
            if (INTERACTIONS.contains(m.substring(0, m.indexOf('(')))) todo.add(m);
        }
        while (!todo.isEmpty()) {
            String m = todo.poll();
            if (!seen.add(m)) continue;
            if (sneaky.contains(m)) return true;
            todo.addAll(calls.getOrDefault(m, Set.of()));
        }
        return false;
    }
}
