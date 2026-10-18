package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Compile-only stub of the client class: the name tag code draws with the game's matrices. */
public final class RenderSystem {
    public static Matrix4f getModelViewMatrix() { return null; }
    public static Matrix4f getProjectionMatrix() { return null; }
    public static Matrix4fStack getModelViewStack() { return null; }
    public static void applyModelViewMatrix() { }
    public static void backupProjectionMatrix() { }
    public static void restoreProjectionMatrix() { }
    public static void setProjectionMatrix(Matrix4f matrix, VertexSorting sorting) { }
    public static VertexSorting getVertexSorting() { return null; }
}
