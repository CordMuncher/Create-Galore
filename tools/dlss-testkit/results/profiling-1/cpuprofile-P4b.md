## 20261025-100622: render thread samples per mode (P4b.jfr)

15293 render thread samples in the recording, 4862 inside the camera paths.

| mode | samples | Bridge DLAA code | its motion vector code |
| --- | --- | --- | --- |
| photon-taa | 1661 | 0.3% | 0.2% |
| dlaa-default | 1658 | 3.3% | 1.6% |
| dlaa-camera | 1543 | 1.6% | 0.3% |

### photon-taa

| package (top frame) | share |
| --- | --- |
| java.util | 13.5% |
| net.minecraft.client | 11.9% |
| native | 10.1% |
| it.unimi.dsi | 6.8% |
| net.minecraft.world | 6.7% |
| com.mojang.blaze3d | 5.7% |
| net.caffeinemc.mods | 4.6% |
| net.irisshaders.iris | 4.1% |
| java.lang | 3.0% |
| org.joml | 2.9% |

| method (top frame) | share |
| --- | --- |
| java.util.ArrayDeque.addLast | 3.4% |
| java.util.HashMap.getNode | 3.2% |
| com.mojang.blaze3d.vertex.BufferBuilder.handler$bff000$iris$beforeNext | 2.8% |
| native: org.lwjgl.opengl.GL30C.glBindFramebuffer | 2.7% |
| net.minecraft.client.particle.SingleQuadParticle.getRenderBoundingBox | 2.4% |
| native: org.lwjgl.opengl.GL11C.nglDrawElements | 2.0% |
| net.neoforged.bus.EventBus.post | 1.7% |
| native: org.lwjgl.opengl.GL20C.glUseProgram | 1.2% |
| dev.creategalore.bridge.client.ShipAnimatedSprites.seen | 1.1% |
| it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap.get | 1.1% |
| java.lang.ThreadLocal$ThreadLocalMap.getEntry | 1.1% |
| net.minecraft.client.renderer.SpriteCoordinateExpander.addVertex | 1.1% |
| com.google.common.collect.RegularImmutableMap.get | 1.0% |
| net.minecraft.world.level.BlockAndTintGetter.getBrightness | 1.0% |
| net.minecraft.client.multiplayer.ClientChunkCache.handler$laa000$sable$getChunk | 0.9% |

### dlaa-default

| package (top frame) | share |
| --- | --- |
| native | 14.4% |
| java.util | 13.9% |
| net.minecraft.client | 10.6% |
| net.minecraft.world | 9.9% |
| it.unimi.dsi | 5.8% |
| com.mojang.blaze3d | 5.0% |
| net.irisshaders.iris | 4.3% |
| net.caffeinemc.mods | 2.8% |
| org.joml | 2.7% |
| java.lang | 2.6% |

| method (top frame) | share |
| --- | --- |
| java.util.ArrayDeque.addLast | 3.2% |
| java.util.HashMap.getNode | 3.1% |
| native: org.lwjgl.opengl.GL30C.glBindFramebuffer | 2.7% |
| native: org.lwjgl.system.JNI.invokePV | 2.4% |
| net.minecraft.client.particle.SingleQuadParticle.getRenderBoundingBox | 2.2% |
| native: org.lwjgl.opengl.GL11C.nglDrawElements | 1.7% |
| com.mojang.blaze3d.vertex.BufferBuilder.handler$bff000$iris$beforeNext | 1.7% |
| net.minecraft.world.level.biome.BiomeManager.getBiome | 1.6% |
| net.minecraft.world.level.chunk.ChunkAccess.getMinBuildHeight | 1.3% |
| dev.creategalore.bridge.dlss.EntityMotion.write | 1.3% |
| native: org.lwjgl.system.JNI.callPPI | 1.2% |
| xaero.map.region.texture.LeafRegionTexture.postUpload | 1.1% |
| java.lang.ThreadLocal$ThreadLocalMap.getEntry | 1.1% |
| net.minecraft.client.multiplayer.ClientChunkCache.handler$laa000$sable$getChunk | 1.1% |
| net.minecraft.client.renderer.SpriteCoordinateExpander.addVertex | 1.0% |

### dlaa-camera

| package (top frame) | share |
| --- | --- |
| native | 16.7% |
| java.util | 13.0% |
| net.minecraft.client | 10.9% |
| net.minecraft.world | 8.7% |
| com.mojang.blaze3d | 7.3% |
| it.unimi.dsi | 5.3% |
| net.irisshaders.iris | 5.1% |
| net.caffeinemc.mods | 2.6% |
| java.lang | 2.3% |
| org.joml | 2.1% |

| method (top frame) | share |
| --- | --- |
| native: org.lwjgl.system.JNI.invokePV | 4.2% |
| com.mojang.blaze3d.vertex.BufferBuilder.handler$bff000$iris$beforeNext | 3.7% |
| java.util.ArrayDeque.addLast | 3.4% |
| java.util.HashMap.getNode | 3.0% |
| native: org.lwjgl.opengl.GL30C.glBindFramebuffer | 2.7% |
| native: org.lwjgl.opengl.GL11C.nglDrawElements | 2.5% |
| dev.creategalore.bridge.client.ShipAnimatedSprites.seen | 1.4% |
| net.minecraft.client.multiplayer.ClientChunkCache.handler$laa000$sable$getChunk | 1.4% |
| com.google.common.collect.RegularImmutableMap.get | 1.3% |
| net.neoforged.bus.EventBus.post | 1.3% |
| net.minecraft.client.renderer.SpriteCoordinateExpander.addVertex | 1.3% |
| net.minecraft.client.particle.SingleQuadParticle.getRenderBoundingBox | 1.2% |
| net.minecraft.client.model.geom.ModelPart$Cube.compile | 1.2% |
| native: org.lwjgl.opengl.GL20C.glUseProgram | 1.0% |
| net.irisshaders.iris.vertices.NormalHelper.computeFaceNormalManual | 1.0% |

