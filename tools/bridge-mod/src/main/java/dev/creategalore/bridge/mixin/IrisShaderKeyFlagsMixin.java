package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create Galore Shaders draws several of Iris's shader keys with one program (gbuffers_entities draws mobs, name tags
 * and their backgrounds; gbuffers_block draws chests and pistons' moving blocks), and Minecraft shades each of those
 * its own way. Iris doesn't tell a program which key it draws, so this writes it into the program's source before
 * Iris patches it: the pack's line "const int cg_key_flags = 0;" becomes that key's flags, in both stages. Any other
 * pack has no such line and is left alone.
 *
 * The flags (the pack's gbuffers_kind.glsl): 1 known; 2 Minecraft's two lights on mobs and held items; 4 the lightmap;
 * 8 the lightmap filtered between levels (terrain's); 16 no texture (a name tag's background, a leash); 32 drawn over
 * something else (a charged creeper's swirl, a mob's shadow), which leaves the motion vectors under it alone.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisShaderKeyFlagsMixin {
    private static final Pattern KEY_FLAGS = Pattern.compile("const\\s+int\\s+cg_key_flags\\s*=\\s*0\\s*;");

    private static final int KNOWN = 1;
    private static final int MOB_SHADING = 2;
    private static final int LIGHTMAP = 4;
    private static final int LIGHTMAP_SMOOTH = 8;
    private static final int UNTEXTURED = 16;
    private static final int DRAWN_OVER = 32;

    // Iris's keys for Minecraft's energy swirl (charged creepers, the wither's armour), mob shadows and the boat's water
    // mask, and in block entities for glowing eyes and layers: lying on what is under them, unlit (vanilla's shaders
    // for these take neither the lightmap nor the mob lights).
    private static final Set<String> DRAWN_OVER_KEYS =
            Set.of("ENTITIES_CUTOUT", "ENTITIES_SOLID", "HAND_CUTOUT", "HAND_TRANSLUCENT", "BLOCK_ENTITY");
    // The warden's glowing layers: Iris draws them unlit, vanilla shades them with the mob lights.
    private static final Set<String> EMISSIVE_MOB_KEYS = Set.of("ENTITIES_EYES_TRANS");

    @WrapOperation(method = "create", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchVanilla(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;ZZLnet/irisshaders/iris/gl/state/ShaderAttributeInputs;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"))
    private static Map<?, ?> create_galore_bridge$keyFlags(String name, String vertex, String geometry, String tessControl,
            String tessEval, String fragment, AlphaTest alpha, boolean isLines, boolean hasChunkOffset, ShaderAttributeInputs inputs,
            Object2ObjectMap<?, ?> textureMap, Operation<Map<?, ?>> original) {
        int flags = vertex != null && vertex.contains("cg_key_flags") ? create_galore_bridge$flags(name) : 0;
        if (flags != 0) {
            String line = "const int cg_key_flags = " + flags + ";";
            vertex = KEY_FLAGS.matcher(vertex).replaceAll(line);
            if (fragment != null) {
                fragment = KEY_FLAGS.matcher(fragment).replaceAll(line);
            }
        }
        return original.call(name, vertex, geometry, tessControl, tessEval, fragment, alpha, isLines, hasChunkOffset, inputs, textureMap);
    }

    /** The flags for the key Iris names a program after (its name in lower case), or 0 for anything else. */
    private static int create_galore_bridge$flags(String name) {
        ShaderKey key;
        try {
            key = ShaderKey.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return 0;
        }
        VertexFormat format = key.getVertexFormat();
        boolean entity = format == IrisVertexFormats.ENTITY;
        int flags = KNOWN;
        if (key.hasDiffuseLighting() || EMISSIVE_MOB_KEYS.contains(key.name())) {
            flags |= MOB_SHADING;
        }
        // In Iris's entity format the lightmap comes with the mob lights or not at all, as in vanilla's shaders.
        if (format.contains(VertexFormatElement.UV2) && !key.shouldIgnoreLightmap() && (!entity || key.hasDiffuseLighting())) {
            flags |= LIGHTMAP;
            if (format == IrisVertexFormats.TERRAIN) {
                flags |= LIGHTMAP_SMOOTH;
            }
        }
        if (!format.contains(VertexFormatElement.UV0)) {
            flags |= UNTEXTURED;
        }
        if (DRAWN_OVER_KEYS.contains(key.name())) {
            flags |= DRAWN_OVER;
        }
        return flags;
    }
}
