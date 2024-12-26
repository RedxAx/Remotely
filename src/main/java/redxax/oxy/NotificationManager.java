package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    private static final List<Notification> activeNotifications = new ArrayList<>();
    private static MinecraftClient minecraftClient;

    public enum Type {
        INFO, WARN, ERROR
    }

    public static void initialize() {
        if (minecraftClient == null) {
            minecraftClient = MinecraftClient.getInstance();
        }
    }

    public static void addNotification(String message, Type type) {
        initialize();
        Notification notification = new Notification(message, type);
        activeNotifications.add(notification);
    }

    public static void updateAll(float delta) {
        Iterator<Notification> iterator = activeNotifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            notification.update(delta);
            if (notification.isFinished()) {
                iterator.remove();
            }
        }
    }

    public static void renderAll(DrawContext context) {
        initialize();
        int screenWidth = minecraftClient.getWindow().getScaledWidth();
        int screenHeight = minecraftClient.getWindow().getScaledHeight();
        int padding = 10;
        for (int i = 0; i < activeNotifications.size(); i++) {
            Notification notification = activeNotifications.get(i);
            notification.setPosition(screenWidth - notification.getWidth() - padding, screenHeight - notification.getHeight() - padding - (i * (notification.getHeight() + padding)));
            notification.render(context);
        }
    }

    private static class Notification {
        private final TextRenderer textRenderer;
        private String message;
        private Type type;
        private float x;
        private float y;
        private float targetX;
        private float opacity;
        private float animationSpeed = 300.0f;
        private float fadeOutSpeed = 100.0f;
        private float currentOpacity = 1.0f;
        private float maxOpacity = 1.0f;
        private float duration = 5.0f;
        private float elapsedTime = 0.0f;
        private boolean fadingOut = false;
        private int padding = 10;
        private int width;
        private int height;

        public Notification(String message, Type type) {
            this.textRenderer = minecraftClient.textRenderer;
            this.message = message;
            this.type = type;
            this.width = textRenderer.getWidth(message) + 2 * padding;
            this.height = textRenderer.fontHeight + 2 * padding;
            this.x = minecraftClient.getWindow().getScaledWidth();
            this.y = minecraftClient.getWindow().getScaledHeight() - height - padding;
            this.targetX = minecraftClient.getWindow().getScaledWidth() - width - padding;
            this.opacity = 1.0f;
        }

        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public void update(float delta) {
            if (x > targetX) {
                float move = animationSpeed * delta * 0.1f;
                x -= move;
                if (x < targetX) {
                    x = targetX;
                }
            } else if (!fadingOut) {
                elapsedTime += delta;
                if (elapsedTime >= duration) {
                    fadingOut = true;
                }
            }

            if (fadingOut) {
                currentOpacity -= fadeOutSpeed * delta * 0.001f;
                if (currentOpacity <= 0.0f) {
                    currentOpacity = 0.0f;
                }
            } else {
                currentOpacity = maxOpacity;
            }
        }

        public boolean isFinished() {
            return currentOpacity <= 0.0f;
        }

        public void render(DrawContext context) {
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
}
