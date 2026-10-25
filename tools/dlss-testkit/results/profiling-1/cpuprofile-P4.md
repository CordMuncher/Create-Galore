## 20261025-100205: render thread samples per mode (P4.jfr)

1750 render thread samples in the recording, 196 inside the camera paths.

| mode | samples | Bridge DLAA code | its motion vector code |
| --- | --- | --- | --- |
| photon-taa | 71 | 0.0% | 0.0% |
| dlaa-default | 51 | 3.9% | 3.9% |
| dlaa-camera | 74 | 4.1% | 0.0% |

### photon-taa

| package (top frame) | share |
| --- | --- |
| java.util | 15.5% |
| native | 9.9% |
| net.minecraft.world | 8.5% |
| net.minecraft.client | 7.0% |
| java.lang | 5.6% |
| net.caffeinemc.mods | 4.2% |
| it.unimi.dsi | 4.2% |
| org.joml | 4.2% |
| com.google.common | 4.2% |
| com.mojang.blaze3d | 4.2% |

| method (top frame) | share |
| --- | --- |
| java.util.ArrayDeque.addLast | 5.6% |
| native: org.lwjgl.opengl.GL30C.glBindFramebuffer | 4.2% |
| native: org.lwjgl.opengl.GL20C.glUseProgram | 2.8% |
| org.joml.Matrix4f.mul0 | 2.8% |
| net.minecraft.world.level.BlockAndTintGetter.getBrightness | 2.8% |
| java.util.HashMap.getNode | 2.8% |
| com.google.common.collect.RegularImmutableMap.get | 2.8% |
| net.caffeinemc.mods.lithium.common.util.math.CompactSineLUT.lookup | 1.4% |
| dev.tr7zw.entityculling.EntityCullingModBase.clientTick | 1.4% |
| me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition$ConditionSupplierImpl.get | 1.4% |
| it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.get | 1.4% |
| dev.kosmx.playerAnim.api.layered.AnimationStack.isActive | 1.4% |
| net.minecraft.util.Mth.clamp | 1.4% |
| net.minecraft.core.NonNullList.size | 1.4% |
| net.irisshaders.iris.vertices.NormalHelper.computeTangent | 1.4% |

### dlaa-default

| package (top frame) | share |
| --- | --- |
| net.minecraft.client | 15.7% |
| net.minecraft.world | 11.8% |
| java.util | 11.8% |
| java.lang | 9.8% |
| native | 9.8% |
| com.mojang.blaze3d | 7.8% |
| dev.creategalore.bridge | 7.8% |
| net.irisshaders.iris | 5.9% |
| me.fzzyhmstrs.fzzy_config | 3.9% |
| com.endertech.minecraft | 2.0% |

| method (top frame) | share |
| --- | --- |
| net.minecraft.client.particle.SingleQuadParticle.getRenderBoundingBox | 7.8% |
| com.mojang.blaze3d.vertex.BufferBuilder.handler$bff000$iris$beforeNext | 5.9% |
| java.lang.String.hashCode | 5.9% |
| dev.creategalore.bridge.client.ShipAnimatedSprites.seen | 3.9% |
| dev.creategalore.bridge.dlss.EntityMotion.write | 3.9% |
| java.util.ArrayDeque.addLast | 3.9% |
| net.irisshaders.iris.vertices.NormalHelper.computeTangent | 3.9% |
| java.lang.ThreadLocal$ThreadLocalMap.getEntry | 2.0% |
| native: org.lwjgl.opengl.GL20C.glUseProgram | 2.0% |
| net.minecraft.world.level.chunk.PalettedContainer.get | 2.0% |
| native: org.lwjgl.opengl.GL30C.glBindFramebuffer | 2.0% |
| net.minecraft.world.entity.LivingEntity.getMainHandItem | 2.0% |
| net.minecraft.world.level.BlockAndTintGetter.getBrightness | 2.0% |
| net.minecraft.client.renderer.SpriteCoordinateExpander.addVertex | 2.0% |
| java.util.ArrayList$Itr.next | 2.0% |

### dlaa-camera

| package (top frame) | share |
| --- | --- |
| net.minecraft.world | 18.9% |
| java.util | 12.2% |
| net.minecraft.client | 10.8% |
| com.mojang.blaze3d | 10.8% |
| native | 6.8% |
| it.unimi.dsi | 4.1% |
| traben.entity_model_features.models | 4.1% |
| java.lang | 2.7% |
| me.fzzyhmstrs.fzzy_config | 2.7% |
| net.irisshaders.iris | 2.7% |

| method (top frame) | share |
| --- | --- |
| net.minecraft.world.level.biome.BiomeManager.getBiome | 6.8% |
| com.mojang.blaze3d.vertex.BufferBuilder.handler$bff000$iris$beforeNext | 4.1% |
| com.mojang.blaze3d.vertex.BufferBuilder.fillExtendedData | 4.1% |
| traben.entity_model_features.models.parts.EMFModelPart.renderLikeVanilla | 4.1% |
| java.util.HashMap.getNode | 2.7% |
| java.lang.ThreadLocal$ThreadLocalMap.getEntry | 2.7% |
| net.minecraft.client.model.geom.ModelPart$Cube.compile | 2.7% |
| net.minecraft.world.level.chunk.ChunkAccess.getMinBuildHeight | 2.7% |
| net.minecraft.world.level.chunk.PalettedContainer.get | 2.7% |
| me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition$ConditionSupplierImpl.get | 2.7% |
| native: org.lwjgl.system.JNI.callPPI | 2.7% |
| net.minecraft.client.multiplayer.ClientChunkCache.getChunk | 1.4% |
| net.minecraft.client.player.LocalPlayer.suffocatesAt | 1.4% |
| java.util.ArrayList.forEach | 1.4% |
| software.bernie.geckolib.renderer.GeoRenderer.createVerticesOfQuad | 1.4% |

