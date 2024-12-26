package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class Notification {
    private final TextRenderer textRenderer;

    public enum Type {
        INFO, WARN, ERROR
    }

    private String message;
    private Type type;
    private float x;
    private float y;
    private float targetX;
    private float opacity;
    private float animationSpeed = 300.0f;
    private float fadeOutSpeed = 100.0f;
    private float currentOpacity = 0.0f;
    private float maxOpacity = 1.0f;
    private float duration = 5.0f;
    private float elapsedTime = 0.0f;
    private boolean fadingOut = false;
    private int padding = 10;
    private int width;
    private int height;

    private static final List<Notification> activeNotifications = new ArrayList<>();
    private static MinecraftClient minecraftClient;

    private Notification(Builder builder) {
        if (minecraftClient == null) {
            minecraftClient = MinecraftClient.getInstance();
        }
        this.textRenderer = minecraftClient.textRenderer;
        this.message = builder.message;
        this.type = builder.type;
        this.animationSpeed = builder.animationSpeed;
        this.fadeOutSpeed = builder.fadeOutSpeed;
        this.maxOpacity = builder.maxOpacity;
        this.duration = builder.duration;
        this.padding = builder.padding;
        this.width = textRenderer.getWidth(message) + 2 * padding;
        this.height = textRenderer.fontHeight + 2 * padding;
        int screenWidth = minecraftClient.getWindow().getScaledWidth();
        int screenHeight = minecraftClient.getWindow().getScaledHeight();
        this.x = screenWidth;
        this.y = screenHeight - height - padding - (activeNotifications.size() * (height + padding));
        this.targetX = screenWidth - width - padding;
        this.opacity = 1.0f;
        activeNotifications.add(this);
    }

    public static class Builder {
        private String message;
        private Type type;
        private float animationSpeed = 300.0f;
        private float fadeOutSpeed = 100.0f;
        private float maxOpacity = 1.0f;
        private float duration = 5.0f;
        private int padding = 10;

        public Builder(String message, Type type) {
            this.message = message;
            this.type = type;
        }

        public Builder animationSpeed(float animationSpeed) {
            this.animationSpeed = animationSpeed;
            return this;
        }

        public Builder fadeOutSpeed(float fadeOutSpeed) {
            this.fadeOutSpeed = fadeOutSpeed;
            return this;
        }

        public Builder maxOpacity(float maxOpacity) {
            this.maxOpacity = maxOpacity;
            return this;
        }

        public Builder duration(float duration) {
            this.duration = duration;
            return this;
        }

        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }

        public Notification build() {
            return new Notification(this);
        }
    }

    public static void updateAll(float delta) {
        List<Notification> toRemove = new ArrayList<>();
        for (Notification notification : new ArrayList<>(activeNotifications)) {
            notification.update(delta);
            if (notification.isFinished()) {
                toRemove.add(notification);
            }
        }
        activeNotifications.removeAll(toRemove);
    }

    public static void renderAll(DrawContext context) {
        for (Notification notification : activeNotifications) {
            notification.render(context);
        }
    }

    private void update(float delta) {
        if (x > targetX) {
            float move = animationSpeed * delta;
            x -= move;
            if (x < targetX) {
                x = targetX;
                currentOpacity = maxOpacity;
            }
        } else if (!fadingOut) {
            elapsedTime += delta;
            if (elapsedTime >= duration) {
                fadingOut = true;
            }
        }

        if (fadingOut) {
            currentOpacity -= fadeOutSpeed * delta / 1000.0f;
            if (currentOpacity <= 0.0f) {
                currentOpacity = 0.0f;
            }
        }
    }

    private boolean isFinished() {
        return currentOpacity <= 0.0f;
    }

    private void render(DrawContext context) {
        if (currentOpacity <= 0.0f) return;
        int color;
        switch (type) {
            case ERROR:
                color = blendColor(0xFFFF5555, currentOpacity);
                break;
            case WARN:
                color = blendColor(0xFFFFAA55, currentOpacity);
                break;
            case INFO:
            default:
                color = blendColor(0xFF5555FF, currentOpacity);
                break;
        }
        context.fill((int) x, (int) y, (int) x + width, (int) y + height, color);
        drawInnerBorder(context, (int) x, (int) y, width, height, blendColor(0xFF000000, currentOpacity));
        context.drawText(this.textRenderer, Text.literal(message), (int) x + padding, (int) y + padding, blendColor(0xFFFFFFFF, currentOpacity), false);
    }

    private int blendColor(int color, float opacity) {
        int a = (int) ((color >> 24 & 0xFF) * opacity);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8 & 0xFF);
        int b = (color & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}
