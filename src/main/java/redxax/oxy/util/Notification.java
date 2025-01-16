package redxax.oxy.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.config.Config;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.Render.drawInnerBorder;

public class Notification {
    private final TextRenderer textRenderer;



    public enum Type { INFO, WARN, ERROR }
    private String message;
    private Type type;
    private float x;
    private float y;
    private float targetX;
    private float opacity;
    private float animationSpeed = 30.0f;
    private float fadeOutSpeed = 100.0f;
    private float currentOpacity = 0.0f;
    private float maxOpacity = 1.0f;
    private float duration = 50.0f;
    private float elapsedTime = 0.0f;
    private boolean fadingOut = false;
    private int padding = 10;
    private int width;
    private int height;
    private static final List<Notification> activeNotifications = new ArrayList<>();

    public Notification(String message, Type type, MinecraftClient minecraftClient) {
        this.message = message;
        this.type = type;
        this.textRenderer = minecraftClient.textRenderer;
        this.width = textRenderer.getWidth(message) + 2 * padding;
        this.height = textRenderer.fontHeight + 2 * padding;
        assert minecraftClient.currentScreen != null;
        this.x = minecraftClient.currentScreen.width;
        this.y = minecraftClient.currentScreen.height - height - padding - (activeNotifications.size() * (height + padding));
        this.targetX = minecraftClient.currentScreen.width - width - padding;
        this.opacity = 1.0f;
        this.currentOpacity = 1.0f;
        activeNotifications.add(this);
    }

    public void update(float delta) {
        if (x > targetX) {
            float move = animationSpeed * delta;
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
            currentOpacity -= fadeOutSpeed * delta / 1000.0f;
            if (currentOpacity <= 0.0f) {
                currentOpacity = 0.0f;
                activeNotifications.remove(this);
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
            case ERROR -> color = blendColor(0xFFFF5555, currentOpacity);
            case WARN -> color = blendColor(0xFFFFAA55, currentOpacity);
            default -> color = blendColor(0xFF5555FF, currentOpacity);
        }
        context.fill((int) x, (int) y, (int) x + width, (int) y + height, color);
        drawInnerBorder(context, (int) x, (int) y, width, height, blendColor(0xFF000000, currentOpacity));
        context.drawText(this.textRenderer, Text.literal(message), (int) x + padding, (int) y + padding, blendColor(0xFFFFFFFF, currentOpacity), Config.shadow);
    }

    private int blendColor(int color, float opacity) {
        int a = (int) ((color >> 24 & 0xFF) * opacity);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8 & 0xFF);
        int b = (color & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static Notification[] getActiveNotifications() {
        return activeNotifications.toArray(new Notification[0]);
    }

}