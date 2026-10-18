package com.mojang.blaze3d.pipeline;

/** Compile-only stub of the client class (the build compiles against a server, which has no client classes). */
public abstract class RenderTarget {
    public abstract int getColorTextureId();

    public void bindWrite(boolean setViewport) {
    }
}
