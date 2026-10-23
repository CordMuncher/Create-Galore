package dev.creategalore.bridge.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.SectionPos;

/**
 * Sodium only advances an animated texture (fire, lava, sea lanterns, prismarine...) in a tick where something marked
 * it as seen. Its own terrain does that every frame for the sections it draws. Sable draws ships and other sub-levels
 * with vanilla's section compiler instead, which marks the textures once, while it builds the mesh, and never again:
 * a campfire on a ship froze on one flame frame and only moved while something else drew the same texture, such as
 * Jade's tooltip icon with the crosshair on it. This remembers the animated textures of each section vanilla's
 * compiler builds and marks them again every frame the sub-level draws.
 */
public final class ShipAnimatedSprites {
    private static final TextureAtlasSprite[] NONE = new TextureAtlasSprite[0];
    private static final Map<Long, TextureAtlasSprite[]> BY_SECTION = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<TextureAtlasSprite>> COMPILING = new ThreadLocal<>();
    // Sodium's SpriteUtil, reached by reflection because the server jars the Bridge is built against have no Sodium.
    private static final MethodHandle MARK_ACTIVE;
    private static final MethodHandle HAS_ANIMATION;

    static {
        MethodHandle mark = null, has = null;
        try {
            Class<?> api = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            Object instance = api.getField("INSTANCE").get(null);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            mark = lookup.findVirtual(api, "markSpriteActive", MethodType.methodType(void.class, TextureAtlasSprite.class)).bindTo(instance);
            has = lookup.findVirtual(api, "hasAnimation", MethodType.methodType(boolean.class, TextureAtlasSprite.class)).bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            // No Sodium: vanilla animates every texture anyway.
        }
        MARK_ACTIVE = mark;
        HAS_ANIMATION = has;
    }

    private ShipAnimatedSprites() {}

    /** A section compile starts on this thread: collect what it marks. */
    public static void startCompile() {
        if (MARK_ACTIVE != null) COMPILING.set(new HashSet<>());
    }

    /** Sodium was asked to mark this sprite; remember it when a section compile on this thread asked. */
    public static void seen(TextureAtlasSprite sprite) {
        Set<TextureAtlasSprite> compiling = COMPILING.get();
        if (compiling == null) return;
        try {
            if ((boolean) HAS_ANIMATION.invokeExact(sprite)) compiling.add(sprite);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /** The compile of this section finished. */
    public static void endCompile(SectionPos pos) {
        Set<TextureAtlasSprite> compiling = COMPILING.get();
        COMPILING.remove();
        if (compiling == null) return;
        if (compiling.isEmpty()) BY_SECTION.remove(pos.asLong());
        else BY_SECTION.put(pos.asLong(), compiling.toArray(NONE));
    }

    /** A sub-level is drawing this section: its animated textures should move. */
    public static void markDrawn(long sectionPos) {
        TextureAtlasSprite[] sprites = BY_SECTION.get(sectionPos);
        if (sprites == null) return;
        try {
            for (TextureAtlasSprite sprite : sprites) MARK_ACTIVE.invokeExact(sprite);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /** A new world was joined: sections of the old one are gone. */
    public static void clear() {
        BY_SECTION.clear();
    }
}
