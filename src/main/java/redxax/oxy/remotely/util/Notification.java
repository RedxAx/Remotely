package redxax.oxy.remotely.util;

import redxax.oxy.remotely.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.Render.drawOuterBorder;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class Notification {
    private final TextRenderer textRenderer;
    private final MinecraftClient client;
    public enum Type { INFO, WARN, ERROR, SUCCESS }
    private final String message;
    private final Type type;
    private float currentX;
    private float targetX;
    private float currentY;
    private float targetY;
    private float elapsedTime = 0.0f;
    private boolean slidingOut = false;
    private boolean paused = false;
    private final int padding = 10;
    private final int width;
    private final int height;
    private static final List<Notification> activeNotifications = new ArrayList<>();
    private static final Map<Integer, Float> elevationOffsets = new HashMap<>();
    private final int notificationId;

    public Notification(String message, Type type) {
        this.message = message;
        this.type = type;
        this.notificationId = (message + System.currentTimeMillis()).hashCode();
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        this.client = minecraftClient;
        this.textRenderer = minecraftClient.textRenderer;
        this.width = textRenderer.getWidth(message) + 2 * padding;
        this.height = textRenderer.fontHeight + 2 * padding;
        assert minecraftClient.currentScreen != null;
        this.currentX = minecraftClient.currentScreen.width;
        this.targetX = minecraftClient.currentScreen.width - width - padding;
        this.currentY = calculateYPosition();
        this.targetY = this.currentY;
        elevationOffsets.put(notificationId, this.currentX);
        activeNotifications.add(this);
        if (type == Type.ERROR) {
            devPrint("[NotificationError] " + message);
            playSound(Sound.ERROR);
        } else if (type == Type.WARN) {
            playSound(Sound.WARN);
        } else if (type == Type.INFO) {
            playSound(Sound.INFO);
        } else if (type == Type.SUCCESS) {
            playSound(Sound.SUCCESS);
        }
    }

    private float calculateYPosition() {
        int count = activeNotifications.size();
        return client.currentScreen.height - height - padding - count * (height + padding);
    }

    public void update() {
        if (!paused) {
            elapsedTime += deltaTime;

            if (!slidingOut) {
                float xProgress = (targetX - currentX) * globalMovementSpeed * deltaTime;
                currentX += xProgress;

                float duration = 5.0f;
                if (elapsedTime >= duration) {
                    slidingOut = true;
                    targetX = client.currentScreen.width;
                }
            } else {
                float xProgress = (targetX - currentX) * globalMovementSpeed * deltaTime;
                currentX += xProgress;

                if (currentX >= client.currentScreen.width - 5) {
                    activeNotifications.remove(this);
                    elevationOffsets.remove(notificationId);
                }
            }
        }
        updatePositions();
    }

    private static void updatePositions() {
        int count = 0;
        for (Notification n : activeNotifications) {
            if (!n.slidingOut) {
                n.targetY = n.client.currentScreen.height - n.height - n.padding - count * (n.height + n.padding);
                float currentYOffset = n.currentY;
                float targetYOffset = n.targetY;
                float newOffset = currentYOffset + (targetYOffset - currentYOffset) * (globalMovementSpeed * 0.3f) * deltaTime;
                n.currentY = newOffset;
                count++;
            }
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!slidingOut) {
            targetX = client.currentScreen.width - width - padding;
        } else {
            targetX = client.currentScreen.width;
        }

        if (currentX >= client.currentScreen.width) return;

        boolean hovered = mouseX >= currentX && mouseX <= currentX + width &&
                mouseY >= currentY && mouseY <= currentY + height;

        paused = hovered;

        int bgColor = getElementBackgroundColor(notificationId, hovered, true, true, type == Type.ERROR, type == Type.SUCCESS, type == Type.INFO);
        int borderColor = getElementBorderColor(notificationId, hovered, true, true, type == Type.ERROR, type == Type.SUCCESS, type == Type.INFO);
        int textColor = getTextColor(notificationId, hovered, true, true, type == Type.ERROR, type == Type.SUCCESS, type == Type.INFO);
        context.fill((int) currentX, (int) currentY, (int) currentX + width, (int) currentY + height, bgColor);
        drawInnerBorder(context, (int) currentX, (int) currentY, width, height, borderColor);
        drawOuterBorder(context, (int) currentX, (int) currentY, width, height, globalOuterBorder);
        context.drawText(this.textRenderer, Text.literal(message), (int) currentX + padding, (int) currentY + padding, textColor, Config.shadow);
    }

    public static Notification[] getActiveNotifications() {
        return activeNotifications.toArray(new Notification[0]);
    }
}
