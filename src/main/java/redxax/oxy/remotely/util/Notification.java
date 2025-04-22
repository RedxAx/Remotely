package redxax.oxy.remotely.util;

import redxax.oxy.remotely.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.Render.drawOuterBorder;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class Notification {
    private final Font textRenderer;
    private final Minecraft client;
    public enum Type { INFO, WARN, ERROR }
    private String message;
    private Type type;
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
        Minecraft minecraftClient = Minecraft.getInstance();
        this.client = minecraftClient;
        this.textRenderer = minecraftClient.font;
        this.width = textRenderer.width(message) + 2 * padding;
        this.height = textRenderer.lineHeight + 2 * padding;
        assert minecraftClient.screen != null;
        this.currentX = minecraftClient.screen.width;
        this.targetX = minecraftClient.screen.width - width - padding;
        this.currentY = calculateYPosition();
        this.targetY = this.currentY;
        elevationOffsets.put(notificationId, this.currentX);
        activeNotifications.add(this);
        if (type == Type.ERROR) devPrint("[NotificationError] " + message);
    }

    private float calculateYPosition() {
        int count = activeNotifications.size();
        return client.screen.height - height - padding - count * (height + padding);
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
                    targetX = client.screen.width;
                }
            } else {
                float xProgress = (targetX - currentX) * globalMovementSpeed * deltaTime;
                currentX += xProgress;

                if (currentX >= client.screen.width - 5) {
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
                n.targetY = n.client.screen.height - n.height - n.padding - count * (n.height + n.padding);
                float currentYOffset = n.currentY;
                float targetYOffset = n.targetY;
                float newOffset = currentYOffset + (targetYOffset - currentYOffset) * (globalMovementSpeed * 0.3f) * deltaTime;
                n.currentY = newOffset;
                count++;
            }
        }
    }

    public void render(GuiGraphics context, int mouseX, int mouseY) {
        if (!slidingOut) {
            targetX = client.screen.width - width - padding;
        } else {
            targetX = client.screen.width;
        }

        if (currentX >= client.screen.width) return;

        boolean hovered = mouseX >= currentX && mouseX <= currentX + width &&
                mouseY >= currentY && mouseY <= currentY + height;

        paused = hovered;

        int bgColor = getElementBackgroundColor(notificationId, hovered, true, true, type == Type.ERROR, false, type == Type.INFO);
        int borderColor = getElementBorderColor(notificationId, hovered, true, true, type == Type.ERROR, false, type == Type.INFO);
        int textColor = getTextColor(hovered, paused);
        context.fill((int) currentX, (int) currentY, (int) currentX + width, (int) currentY + height, bgColor);
        drawInnerBorder(context, (int) currentX, (int) currentY, width, height, borderColor);
        drawOuterBorder(context, (int) currentX, (int) currentY, width, height, globalOuterBorder);
        context.drawString(this.textRenderer, Component.literal(message), (int) currentX + padding, (int) currentY + padding, textColor, Config.shadow);
    }

    public static Notification[] getActiveNotifications() {
        return activeNotifications.toArray(new Notification[0]);
    }
}
