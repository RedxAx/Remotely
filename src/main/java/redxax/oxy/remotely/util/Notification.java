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

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class Notification {
    private final TextRenderer textRenderer;
    private final MinecraftClient client;
    private int originalWidth;

    public enum Type { INFO, WARN, ERROR, SUCCESS }

    private String message;
    private String description;
    private Type type;
    private Runnable action;
    public boolean loading;
    private boolean hovered;
    private boolean ignoreHover;
    private boolean rescaling;
    private float currentX;
    private float targetX;
    private float currentY;
    private float targetY;
    private float elapsedTime = 0.0f;
    private boolean slidingOut = false;
    private boolean paused = false;
    public boolean autoSlideOut = true;
    private final int padding = 10;
    private final int widthPadding = 3;
    private int width;
    private int height;
    private static final List<Notification> activeNotifications = new ArrayList<>();
    private final int notificationId;
    private boolean justAdded;

    public Notification(String message, String description, Type type, Runnable action) {
        this.message = message;
        this.description = description;
        this.type = type;
        this.notificationId = (message + System.currentTimeMillis()).hashCode();
        this.action = action;
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        this.client = minecraftClient;
        this.textRenderer = minecraftClient.textRenderer;
        this.width = originalWidth = Math.max(textRenderer.getWidth(message) + 2 * widthPadding, textRenderer.getWidth(description) + 2 * widthPadding);
        this.height = textRenderer.fontHeight + 2 * padding;
        assert minecraftClient.currentScreen != null;
        this.currentX = minecraftClient.currentScreen.width;
        this.targetX = minecraftClient.currentScreen.width - width - widthPadding;
        this.currentY = calculateYPosition();
        this.targetY = this.currentY;
        this.ignoreHover = false;
        activeNotifications.add(this);
        if (type == Type.ERROR) {
            devPrint("[NotificationError] " + message + "\n" + description);
            playSound(Sound.ERROR);
        } else if (type == Type.WARN) {
            playSound(Sound.WARN);
        } else if (type == Type.INFO) {
            playSound(Sound.INFO);
        } else if (type == Type.SUCCESS) {
            playSound(Sound.SUCCESS);
        }
        this.justAdded = true;
    }

    public Notification(String message, Type type) {
        this(message, "", type, null);
        paused = true;
    }

    public Notification(String message, String description, Type type) {
        this(message, description, type, null);
    }

    public void change(String message, String description, Type type, Runnable action) {
        this.message = message;
        this.description = description;
        this.type = type;
        this.action = action;
        this.width = originalWidth = Math.max(textRenderer.getWidth(message) + 2 * widthPadding, textRenderer.getWidth(description) + 2 * widthPadding);
        this.height = textRenderer.fontHeight + 2 * padding;
        this.currentX = client.currentScreen.width;
        this.targetX = client.currentScreen.width - width - widthPadding;
        this.currentY = calculateYPosition();
        this.justAdded = true;
        this.loading = false;
        this.autoSlideOut = true;
        if (type == Type.ERROR) {
            devPrint("[NotificationError] " + message + "\n" + description);
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
        return client.currentScreen.height - height - padding - (count - 1) * (height + padding);
    }

    public void update() {
        if (justAdded) {
            this.currentX = client.currentScreen.width;
            this.targetX = client.currentScreen.width - width - widthPadding;
            this.currentY = calculateYPosition();
            this.targetY = this.currentY;
            justAdded = false;
        }
        if (!paused) {
            elapsedTime += deltaTime;

            if (!slidingOut) {
                float xProgress = (targetX - currentX) * globalMovementSpeed * deltaTime;
                currentX += xProgress;

                float duration = 8.0f;
                if (autoSlideOut && elapsedTime >= duration) {
                    slidingOut = true;
                    targetX = client.currentScreen.width;
                }
            } else {
                float xProgress = (targetX - currentX) * globalMovementSpeed * deltaTime;
                currentX += xProgress;

                if (currentX >= client.currentScreen.width - 5) {
                    activeNotifications.remove(this);
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
                n.currentY = !n.rescaling ? (currentYOffset + (targetYOffset - currentYOffset) * (globalMovementSpeed * 0.3f) * deltaTime) : targetYOffset;
                count++;
            }
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        int loadingAnimSize = 24;
        int loadingPadding = 3;
        int contentOffsetX = 0;

        if (loading) {
            contentOffsetX = loadingAnimSize + loadingPadding;
            if (width < originalWidth + contentOffsetX) {
                width = originalWidth + contentOffsetX;
            }
        } else {
            width = originalWidth;
        }

        if (!slidingOut) {
            targetX = client.currentScreen.width - width - widthPadding;
        } else {
            targetX = client.currentScreen.width;
        }
        rescaling = targetScaleFactor != animScaleFactor;
        hovered = mouseX >= currentX && mouseX <= currentX + width && mouseY >= currentY && mouseY <= currentY + height;
        paused = hovered && !ignoreHover;
        int bgColor = getElementBackgroundColor(notificationId, hovered, true, true, type == Type.ERROR, type == Type.SUCCESS, type == Type.INFO);
        int borderColor = getElementBorderColor(notificationId, hovered, true, true, type == Type.ERROR, type == Type.SUCCESS, type == Type.INFO);
        int textColor = getTextColor(notificationId, hovered, true, true, type == Type.ERROR, type == Type.SUCCESS, type == Type.INFO);
        context.fill((int) currentX, (int) currentY, (int) currentX + width, (int) currentY + height, bgColor);
        drawInnerBorder(context, (int) currentX, (int) currentY, width, height, borderColor);
        drawOuterBorder(context, (int) currentX, (int) currentY, width, height, backgroundColor);
        if (loading) {
            int animCenterY = (int) currentY + (height - loadingAnimSize) / 2;
            drawSnakeLoading(context, (int) currentX + widthPadding, animCenterY, loadingAnimSize, loadingAnimSize);
        }
        int textX = (int) currentX + widthPadding + contentOffsetX;
        int textY = (int) currentY + padding;
        if (description.isEmpty()) {
            context.drawText(this.textRenderer, Text.literal(message), textX, textY, textColor, Config.shadow);
        } else {
            context.drawText(this.textRenderer, Text.literal(message), textX, (int) currentY + 6, textColor, Config.shadow);
            float scale = (float) ((float) client.getWindow().getScaleFactor() * (1f / client.getWindow().getScaleFactor()));
            int descriptionY = (int) (currentY + textRenderer.fontHeight);
            context.getMatrices().push();
            context.getMatrices().scale(scale, scale, 1.0f);
            float scaledX = Math.round((textX) / scale);
            float scaledY = Math.round((descriptionY + padding) / scale);
            context.drawText(this.textRenderer, Text.literal(description), (int) scaledX, (int) scaledY, globalDarkTextColor, Config.shadow);
            context.getMatrices().pop();
        }
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hovered) {
            if (action != null && button == 0) {
                action.run();
                paused = false;
                ignoreHover = true;
                slidingOut = true;
            } else if (button == 1 || button == 2) {
                paused = false;
                ignoreHover = true;
                slidingOut = true;
            }
            return true;
        }
        return false;
    }

    public static Notification[] getActiveNotifications() {
        return activeNotifications.toArray(new Notification[0]);
    }
}
