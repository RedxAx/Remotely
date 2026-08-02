package redxax.oxy.remotely.host;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.platform.CursorHandler;
import restudio.rescreen.platform.input.ReMouseButton;

public final class MinecraftCursorHandler implements CursorHandler {
    private final Minecraft minecraft;

    public MinecraftCursorHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public boolean windowActive() {
        long window = windowHandle();
        return window != 0
            && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) != GLFW.GLFW_TRUE
            && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != GLFW.GLFW_FALSE;
    }

    @Override
    public boolean pointerAvailable() {
        long window = windowHandle();
        return window != 0
            && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) != GLFW.GLFW_TRUE
            && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != GLFW.GLFW_FALSE
            && GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED;
    }

    @Override
    public boolean isButtonDown(ReMouseButton button) {
        int nativeButton = switch (button) {
            case LEFT -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case RIGHT -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case MIDDLE -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            case BACK -> GLFW.GLFW_MOUSE_BUTTON_4;
            case FORWARD -> GLFW.GLFW_MOUSE_BUTTON_5;
            default -> -1;
        };
        return nativeButton >= 0 && GLFW.glfwGetMouseButton(windowHandle(), nativeButton) == GLFW.GLFW_PRESS;
    }

    @Override
    public void hideNativeCursor() {
        long window = windowHandle();
        if (window != 0 && GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_HIDDEN) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    @Override
    public boolean isNativeCursorHidden() {
        long window = windowHandle();
        return window != 0 && GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_HIDDEN;
    }

    @Override
    public void showNativeCursor() {
        long window = windowHandle();
        if (window != 0 && GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_HIDDEN) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    private long windowHandle() {
        //#if NEOFORGE && MC < 1.21.10
        //$$ return minecraft.getWindow().getWindow();
        //#elseif MC >= 1.21.6 || MC >= 26.1
        return minecraft.getWindow().handle();
        //#else
        //$$ return minecraft.getWindow().getWindow();
        //#endif
    }
}
