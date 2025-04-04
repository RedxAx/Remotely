package redxax.oxy.common.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.common.config.Config;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.common.Render.drawInnerBorder;
import static redxax.oxy.common.Render.drawOuterBorder;
import static redxax.oxy.common.config.Config.*;

public class Notification {
    private final TextRenderer textRenderer;
    private final MinecraftClient client;
    public enum Type { INFO, WARN, ERROR }
    private String message;
    private Type type;
    private float x;
    private float y;
    private float targetX;
    private float animationSpeed = 30.0f;
    private float duration = 50.0f;
    private float elapsedTime = 0.0f;
    private boolean slidingOut = false;
    private boolean paused = false;
    private int padding = 10;
    private int width;
    private int height;
    private static final List<Notification> activeNotifications = new ArrayList<>();

    public Notification(String message, Type type) {
        this.message = message;
        this.type = type;
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        this.client = minecraftClient;
        this.textRenderer = minecraftClient.textRenderer;
        this.width = textRenderer.getWidth(message) + 2 * padding;
        this.height = textRenderer.fontHeight + 2 * padding;
        assert minecraftClient.currentScreen != null;
        this.x = minecraftClient.currentScreen.width;
        this.y = minecraftClient.currentScreen.height - height - padding - (activeNotifications.size() * (height + padding));
        this.targetX = minecraftClient.currentScreen.width - width - padding;
        activeNotifications.add(this);
    }

    public void update(float delta) {
        if (!paused) {
            if (!slidingOut) {
                if (x > targetX) {
                    float move = animationSpeed * delta;
                    x -= move;
                    if (x < targetX) {
                        x = targetX;
                    }
                } else {
                    elapsedTime += delta;
                    if (elapsedTime >= duration) {
                        slidingOut = true;
                    }
                }
            } else {
                float slideSpeed = animationSpeed * 2 * delta;
                x += slideSpeed;
                if (x >= client.currentScreen.width) {
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
                n.y = n.client.currentScreen.height - n.height - n.padding - count * (n.height + n.padding);
                count++;
            }
        }
    }

    public boolean isFinished() {
        return x >= client.currentScreen.width;
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (x >= client.currentScreen.width) return;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int bgColor = getElementBackgroundColor(message.hashCode(), hovered, type.equals(Type.WARN), type.equals(Type.ERROR), false, type.equals(Type.INFO));
        int borderColor = getElementBorderColor(message.hashCode(), hovered, type.equals(Type.WARN), type.equals(Type.ERROR), false, type.equals(Type.INFO));
        int textColor = getTextColor(false, paused);
        context.fill((int) x, (int) y, (int) x + width, (int) y + height, bgColor);
        drawInnerBorder(context, (int) x, (int) y, width, height, borderColor);
        drawOuterBorder(context, (int) x, (int) y, width, height, globalOuterBorder);
        context.drawText(this.textRenderer, Text.literal(message), (int) x + padding, (int) y + padding, textColor, Config.shadow);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            paused = !paused;
            return true;
        }
        return false;
    }

    public static Notification[] getActiveNotifications() {
        return activeNotifications.toArray(new Notification[0]);
    }
}
