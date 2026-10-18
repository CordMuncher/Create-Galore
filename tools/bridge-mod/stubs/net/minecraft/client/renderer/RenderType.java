package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Compile-only stub of the client class. */
public abstract class RenderType {
    public void draw(MeshData mesh) { }
    public int bufferSize() { return 0; }
    public VertexFormat format() { return null; }
    public VertexFormat.Mode mode() { return null; }
    public boolean sortOnUpload() { return false; }
}
