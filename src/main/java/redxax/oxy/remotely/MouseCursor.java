package redxax.oxy.remotely;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.config.Config;

import static redxax.oxy.remotely.config.Config.deltaTime;

public class MouseCursor {
    private static float tailX = 0, tailY = 0;
    private static final float TAIL_FOLLOW_SPEED = 10f;
    private static boolean initialized = false;
    private static boolean cursorHidden = false;
    private static final float DEFAULT_MAIN_SIZE = 12f;
    private static final float DEFAULT_TAIL_SIZE = 7f;
    private static float mainFactor = 1.0f;
    private static final float ANIMATION_SPEED = 20f;
    private static MinecraftClient mc = MinecraftClient.getInstance();

    public static void updateAndRender(DrawContext context, int mouseX, int mouseY) {
        if (!Config.customMouse) {
            if (initialized) {
                reset();
            }
            return;
        }
        if (!cursorHidden && mc.getWindow() != null) {
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            cursorHidden = true;
        }
        float mainY, mainX;
        if (!initialized) {
            tailX = mouseX;
            tailY = mouseY;
            initialized = true;
        }
        mainX = mouseX + 1;
        mainY = mouseY + 3;
        float offset = 2f;
        float tailTargetX = mainX + DEFAULT_MAIN_SIZE / 2f + offset;
        float tailTargetY = mainY + DEFAULT_MAIN_SIZE / 2f + offset;
        tailX += (tailTargetX - tailX) * TAIL_FOLLOW_SPEED * deltaTime;
        tailY += (tailTargetY - tailY) * TAIL_FOLLOW_SPEED * deltaTime;
        long handle = mc.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean middleDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        float targetFactor = 1.0f;
        if (rightDown) {
            targetFactor = 1.2f;
        } else if (leftDown || middleDown) {
            targetFactor = 0.8f;
        }
        mainFactor += (targetFactor - mainFactor) * ANIMATION_SPEED * deltaTime;
        float currentMainSize = DEFAULT_MAIN_SIZE * mainFactor;
        boolean isAnyChildHovered = false;
        for (Element child : mc.currentScreen.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                isAnyChildHovered = true;
                break;
            }
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1244);
        Render.drawCustomButton(context, (int) mainX, (int) mainY, "Main", mc, true, false, true, isAnyChildHovered, true, (int) currentMainSize, (int) currentMainSize, 0xFFFFFFFF, Config.accentColor, mouseX, mouseY, "");
        Render.drawCustomButton(context, (int) tailX, (int) tailY, "Tail", mc, true, false, true, false, true, (int) DEFAULT_TAIL_SIZE, (int) DEFAULT_TAIL_SIZE, 0xFFFFFFFF, Config.accentColor, mouseX, mouseY, "");
        context.getMatrices().pop();
    }

    public static void reset() {
        initialized = false;
        cursorHidden = false;
        if (mc != null && mc.getWindow() != null) {
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }
}
