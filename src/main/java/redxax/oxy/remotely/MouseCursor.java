package redxax.oxy.remotely;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.config.Config;
import static redxax.oxy.remotely.config.Config.*;

public class MouseCursor {
    private static float tailX = 0, tailY = 0;
    private static boolean initialized = false;
    private static boolean cursorHidden = false;
    private static float mainFactor = 1.0f;
    private static final float ANIMATION_SPEED = 30f;
    private static MinecraftClient mc = MinecraftClient.getInstance();

    private static float lastMouseX = 0, lastMouseY = 0;
    private static float rotationAngle = 0;
    private static final float ROTATION_MULTIPLIER = 6f;

    public static void updateAndRender(DrawContext context, int mouseX, int mouseY) {
        if (!Config.customMouse) {
            if (initialized) {
                reset(true);
            }
            return;
        }
        if (!cursorHidden && mc.getWindow() != null) {
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            cursorHidden = true;
        }
        if (!initialized) {
            tailX = mouseX;
            tailY = mouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            rotationAngle = 0;
            initialized = true;
        }
        float dx = mouseX - lastMouseX;
        float dy = mouseY - lastMouseY;
        float velocity = (float) Math.sqrt(dx * dx + dy * dy);
        float targetRotation = velocity * ROTATION_MULTIPLIER * Math.signum(dx);
        rotationAngle += (targetRotation - rotationAngle) * globalMovementSpeed * deltaTime;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        float mainX = mouseX + 1;
        float mainY = mouseY + 3;
        float offset = 2f;
        float tailTargetX = mainX + mouseSize / 2f + offset;
        float tailTargetY = mainY + mouseSize / 2f + offset;
        tailX += (tailTargetX - tailX) * tailFollowSpeed * deltaTime;
        tailY += (tailTargetY - tailY) * tailFollowSpeed * deltaTime;
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
        float currentMainSize = mouseSize * mainFactor;
        boolean isAnyChildHovered = false;
        for (Element child : mc.currentScreen.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                isAnyChildHovered = true;
                break;
            }
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1244);
        context.getMatrices().push();
        context.getMatrices().translate(mainX, mainY, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));
        context.getMatrices().translate(-mainX, -mainY, 0);
        Render.drawCustomButton(context, (int) mainX, (int) mainY, "Main", mc, true, false, true, isAnyChildHovered, true, (int) currentMainSize, (int) currentMainSize, 0xFFFFFFFF, Config.accentColor, mouseX, mouseY, "");
        context.getMatrices().pop();
        Render.drawCustomButton(context, (int) tailX, (int) tailY, "Tail", mc, true, false, true, false, true, (int) tailSize, (int) tailSize, 0xFFFFFFFF, Config.accentColor, mouseX, mouseY, "");
        context.getMatrices().pop();
    }

    public static void reset(boolean unhide) {
        initialized = false;
        if (mc != null && mc.getWindow() != null && unhide) {
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            cursorHidden = false;
        }
    }
}
