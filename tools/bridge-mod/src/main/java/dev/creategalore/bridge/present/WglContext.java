package dev.creategalore.bridge.present;

import static org.lwjgl.opengl.WGL.wglCreateContext;
import static org.lwjgl.opengl.WGL.wglDeleteContext;
import static org.lwjgl.opengl.WGL.wglMakeCurrent;
import static org.lwjgl.opengl.WGLARBCreateContext.WGL_CONTEXT_FLAGS_ARB;
import static org.lwjgl.opengl.WGLARBCreateContext.WGL_CONTEXT_FORWARD_COMPATIBLE_BIT_ARB;
import static org.lwjgl.opengl.WGLARBCreateContext.WGL_CONTEXT_MAJOR_VERSION_ARB;
import static org.lwjgl.opengl.WGLARBCreateContext.WGL_CONTEXT_MINOR_VERSION_ARB;
import static org.lwjgl.opengl.WGLARBCreateContext.wglCreateContextAttribsARB;
import static org.lwjgl.opengl.WGLARBCreateContextProfile.WGL_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB;
import static org.lwjgl.opengl.WGLARBCreateContextProfile.WGL_CONTEXT_CORE_PROFILE_BIT_ARB;
import static org.lwjgl.opengl.WGLARBCreateContextProfile.WGL_CONTEXT_PROFILE_MASK_ARB;
import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.windows.GDI32.ChoosePixelFormat;
import static org.lwjgl.system.windows.GDI32.DescribePixelFormat;
import static org.lwjgl.system.windows.GDI32.PFD_MAIN_PLANE;
import static org.lwjgl.system.windows.GDI32.PFD_SUPPORT_OPENGL;
import static org.lwjgl.system.windows.GDI32.PFD_TYPE_RGBA;
import static org.lwjgl.system.windows.GDI32.SetPixelFormat;
import static org.lwjgl.system.windows.User32.DestroyWindow;
import static org.lwjgl.system.windows.User32.GetDC;
import static org.lwjgl.system.windows.User32.ReleaseDC;
import static org.lwjgl.system.windows.User32.WS_POPUP;
import static org.lwjgl.system.windows.User32.nCreateWindowEx;
import static org.lwjgl.system.windows.WindowsUtil.windowsThrowException;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.PIXELFORMATDESCRIPTOR;

/**
 * The game's OpenGL context, on a hidden 1x1 window instead of the game window. Ported from Perfect Presentation's
 * WGLContext (MIT, sidit77). The game window has no OpenGL context at all; its picture comes from a DXGI swap chain.
 */
final class WglContext implements AutoCloseable {
    private final long hwnd;
    private final long hdc;
    private final long hglrc;

    WglContext(ContextFlags flags) {
        try (MemoryStack stack = stackPush()) {
            hwnd = check(nCreateWindowEx(0, memAddress(stack.UTF16("STATIC")), memAddress(stack.UTF16("Hidden Context Window")),
                    WS_POPUP, 0, 0, 1, 1, NULL, NULL, NULL, NULL));
            hdc = check(GetDC(hwnd));

            PIXELFORMATDESCRIPTOR pfd = PIXELFORMATDESCRIPTOR.calloc(stack)
                    .nSize((short) PIXELFORMATDESCRIPTOR.SIZEOF)
                    .nVersion((short) 1)
                    .dwFlags(PFD_SUPPORT_OPENGL)
                    .iPixelType(PFD_TYPE_RGBA)
                    .iLayerType(PFD_MAIN_PLANE)
                    .cColorBits((byte) 32)
                    .cDepthBits((byte) 24)
                    .cStencilBits((byte) 8);
            int pixelFormat = ChoosePixelFormat(hdc, pfd);
            if (pixelFormat == 0) {
                windowsThrowException("Failed to choose an OpenGL-compatible pixel format");
            }
            if (DescribePixelFormat(hdc, pixelFormat, pfd) == 0) {
                windowsThrowException("Failed to obtain pixel format information");
            }
            if (!SetPixelFormat(hdc, pixelFormat, pfd)) {
                windowsThrowException("Failed to set the pixel format");
            }

            long tempContext = check(wglCreateContext(hdc));
            wglMakeCurrent(hdc, tempContext);
            // Minecraft asks for 3.2 core, which NVIDIA answers with exactly 3.3. Normally NeoForge's early window has
            // already made a 4.6 context, and Iris, Flywheel and Distant Horizons pick faster paths on 4.x, so ask for
            // 4.6 first, like that window does, and fall back to what the game asked for.
            long context = 0L;
            if (flags.majorVersion < 4) {
                context = createContext(hdc, 4, 6, flags);
            }
            if (context == 0L) {
                context = createContext(hdc, flags.majorVersion, flags.minorVersion, flags);
            }
            hglrc = check(context);
            wglMakeCurrent(hdc, 0);
            wglDeleteContext(tempContext);
        }
    }

    private static long createContext(long hdc, int major, int minor, ContextFlags flags) {
        return wglCreateContextAttribsARB(hdc, 0, new int[] {
                WGL_CONTEXT_MAJOR_VERSION_ARB, major,
                WGL_CONTEXT_MINOR_VERSION_ARB, minor,
                WGL_CONTEXT_PROFILE_MASK_ARB, switch (flags.profile) {
                    case CORE -> WGL_CONTEXT_CORE_PROFILE_BIT_ARB;
                    case COMPAT -> WGL_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB;
                    case ANY -> 0;
                },
                WGL_CONTEXT_FLAGS_ARB, flags.forwardCompatible ? WGL_CONTEXT_FORWARD_COMPATIBLE_BIT_ARB : 0,
                0
        });
    }

    void makeCurrent() {
        wglMakeCurrent(hdc, hglrc);
    }

    @Override
    public void close() {
        wglMakeCurrent(hdc, 0);
        wglDeleteContext(hglrc);
        ReleaseDC(hwnd, hdc);
        DestroyWindow(hwnd);
    }

    /** The OpenGL version and profile the game asked GLFW for, captured from its window hints. */
    static final class ContextFlags {
        enum Profile { CORE, COMPAT, ANY }

        int majorVersion = 3;
        int minorVersion = 2;
        Profile profile = Profile.CORE;
        boolean forwardCompatible = true;
    }
}
