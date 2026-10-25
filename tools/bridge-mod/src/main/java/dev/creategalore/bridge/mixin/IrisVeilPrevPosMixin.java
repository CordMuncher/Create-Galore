package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.dlss.EntityMotion;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The DLAA copy of Photon gives its entity and block entity programs one more vertex input, cg_prev_pos (where the
 * Bridge had the vertex last frame, see EntityMotion). IrisVeil builds Veil's shaders, such as Aeronautics' levitite,
 * from those programs, and its levitite transformer refuses any vertex input it doesn't know ("Unsupported Levitite
 * shaderpack vertex input: cg_prev_pos"), so levitite lost its shader under that pack. Veil's vertices aren't in Iris's
 * entity format and carry no previous position anyway: in Veil's programs (gbuffers_veil_*) the input becomes a
 * constant that says "not known" (w 0), so their motion is the camera's alone.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisVeilPrevPosMixin {
    private static final Pattern PREV_POS_INPUT = Pattern.compile(
            "layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*in\\s+vec4\\s+" + EntityMotion.ATTRIBUTE + "\\s*;");

    @WrapOperation(method = "create", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchVanilla(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;ZZLnet/irisshaders/iris/gl/state/ShaderAttributeInputs;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"))
    private static Map<?, ?> create_galore_bridge$veilWithoutPrevPos(String name, String vertex, String geometry, String tessControl,
            String tessEval, String fragment, AlphaTest alpha, boolean isLines, boolean hasChunkOffset, ShaderAttributeInputs inputs,
            Object2ObjectMap<?, ?> textureMap, Operation<Map<?, ?>> original) {
        if (name != null && name.startsWith("gbuffers_veil_") && vertex != null && vertex.contains(EntityMotion.ATTRIBUTE)) {
            vertex = PREV_POS_INPUT.matcher(vertex).replaceAll("const vec4 " + EntityMotion.ATTRIBUTE + " = vec4(0.0);");
        }
        return original.call(name, vertex, geometry, tessControl, tessEval, fragment, alpha, isLines, hasChunkOffset, inputs, textureMap);
    }
}
