package redxax.oxy.remotely.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render;
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

    private static float stretchPosition = 0f;
    private static float stretchVelocity = 0f;
    private static float stretchTarget = 0f;

    private static final float SPRING_STRENGTH = 15f;
    private static final float SPRING_DAMPING = 2f;
    private static final float MAX_STRETCH = 50f;
    private static final float STRETCH_INPUT_MULTIPLIER = 8f;
    private static final float MIN_WIDTH_FACTOR = 0.3f;

    private static void handleScrollInput() {
        float springForce = (stretchTarget - stretchPosition) * SPRING_STRENGTH;
        float dampingForce = stretchVelocity * SPRING_DAMPING;
        stretchVelocity += (springForce - dampingForce) * deltaTime * 3f;
        stretchPosition += stretchVelocity * deltaTime * 3f;
        stretchTarget *= (float) Math.pow(0.85f, deltaTime * 60f * 3f);
        if (Math.abs(stretchTarget) < 0.1f && Math.abs(stretchPosition) < 0.1f && Math.abs(stretchVelocity) < 0.1f) {
            stretchTarget = 0f;
            stretchPosition = 0f;
            stretchVelocity = 0f;
        }
    }

    public static void mouseScrolled(double yOffset) {
        float scrollInput = (float) yOffset * STRETCH_INPUT_MULTIPLIER;
        stretchTarget += scrollInput;

        stretchTarget = Math.max(-MAX_STRETCH, Math.min(MAX_STRETCH, stretchTarget));
    }

    public static void updateAndRender(DrawContext context, int mouseX, int mouseY) {
        if (!Config.customMouse) {
            if (initialized) {
                reset(true);
            }
            return;
        }

        handleScrollInput();

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
            stretchPosition = 0f;
            stretchVelocity = 0f;
            stretchTarget = 0f;
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

        float stretchAmount = Math.abs(stretchPosition);
        float stretchRatio = Math.min(stretchAmount / MAX_STRETCH, 1.0f);
        float widthFactor = 1.0f - (stretchRatio * (1.0f - MIN_WIDTH_FACTOR));
        float baseSize = mouseSize * mainFactor;
        int finalWidth = (int) (baseSize * widthFactor);
        int finalHeight = (int) (baseSize + stretchAmount);

        float finalY = mainY;
        if (stretchPosition < 0) {
            finalY = mainY + stretchPosition;
        }

        boolean isAnyChildHovered = false;
        if (mc.currentScreen != null) {
            for (Element child : mc.currentScreen.children()) {
                if (child.isMouseOver(mouseX, mouseY)) {
                    isAnyChildHovered = true;
                    break;
                }
            }
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1244);
        context.getMatrices().push();
        context.getMatrices().translate(mainX, finalY, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));
        context.getMatrices().translate(-mainX, -finalY, 0);
        Render.drawCustomButton(context, (int) mainX, (int) finalY, " ", mc, true, false, true, isAnyChildHovered, true, finalWidth, finalHeight, 0xFFFFFFFF, Config.accentColor, mouseX, mouseY, "");
        context.getMatrices().pop();
        Render.drawCustomButton(context, (int) tailX, (int) tailY, "  ", mc, true, false, true, false, true, (int) tailSize, (int) tailSize, 0xFFFFFFFF, Config.accentColor, mouseX, mouseY, "");
        context.getMatrices().pop();
    }

    public static void reset(boolean unhide) {
        initialized = false;
        stretchPosition = 0f;
        stretchVelocity = 0f;
        stretchTarget = 0f;
        if (mc != null && mc.getWindow() != null && unhide) {
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            cursorHidden = false;
        }
    }
}