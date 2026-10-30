package dev.creategalore.bridge.client;

import dev.creategalore.bridge.mixin.IrisPipelineDirectivesAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.neoforged.fml.ModList;

/**
 * Where a shader pack asked for the world's particles to be drawn ({@code particles.ordering} in its shaders.properties).
 * Iris on NeoForge reads the setting but never acts on it: its hooks on the two particle draws are left out of its
 * NeoForge build, so every pack gets NeoForge's split. Opaque particles (flames, lava drips, sparks) are drawn with the
 * solid world, before the lighting and before your hand, and see-through ones after it. A pack that lights particles
 * itself after its lighting pass (AFTER) then gets the flames in the middle of building its G-buffer: they land on
 * whatever is there at that moment and your hand, drawn next, can't cover them, so fire shows over your hand.
 * <p>
 * Only an ordering the pack names is followed here, the way Iris does it elsewhere: AFTER draws every particle after
 * the lighting, BEFORE every particle with the solid world. A pack that names none (Photon) or MIXED (Complementary,
 * Solas, Rethinking Voxels) keeps NeoForge's split, as before.
 */
public final class ParticleOrdering {
    private static final boolean IRIS = ModList.get().isLoaded("iris");

    public static final int KEEP = 0;
    public static final int BEFORE = 1;
    public static final int AFTER = 2;

    private ParticleOrdering() {
    }

    /** {@link #KEEP}, {@link #BEFORE} or {@link #AFTER}, for the shader pack drawing this dimension right now. */
    public static int asked() {
        return IRIS ? fromIris() : KEEP;
    }

    private static int fromIris() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof IrisRenderingPipeline)) return KEEP;
        ParticleRenderingSettings asked = ((IrisPipelineDirectivesAccessor) pipeline).create_galore_bridge$packDirectives().getParticleRenderingSettings();
        return asked == ParticleRenderingSettings.AFTER ? AFTER : asked == ParticleRenderingSettings.BEFORE ? BEFORE : KEEP;
    }
}
