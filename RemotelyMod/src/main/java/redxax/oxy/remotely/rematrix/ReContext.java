package redxax.oxy.remotely.rematrix;

import restudio.rescreen.game.tooltip.MinecraftTooltip;

public interface ReContext {
    ReMatrixStack matrices();
    ReScissorStack scissors();
    ReTextureCache textures();
    ReTextBridge text();
    Object graphics();
    void drawItem(Object item, int x, int y, int z);
    default void drawEntity(Object entity, int x, int y, int z, int size) {
    }
    default void drawPlayer(Object player, int x, int y, int z, int size) {
        drawEntity(player, x, y, z, size);
    }
    default void drawMinecraftTooltip(MinecraftTooltip tooltip, int mouseX, int mouseY, int screenWidth, int screenHeight) {
    }
    default void drawMinecraftItemTooltip(Object item, MinecraftTooltip fallback, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        drawMinecraftTooltip(fallback, mouseX, mouseY, screenWidth, screenHeight);
    }
    default void drawItemPreview(Object item, int x, int y, int z, float scale, float rotationX, float rotationY, boolean paused) {
        drawItem(item, x, y, z);
    }
    default void drawEntityPreview(Object entity, int x, int y, int z, int size, float yaw, float pitch, boolean paused) {
        drawEntity(entity, x, y, z, size);
    }
    default void drawPlayerPreview(Object player, int x, int y, int z, int size, float yaw, float pitch, boolean paused) {
        drawEntityPreview(player, x, y, z, size, yaw, pitch, paused);
    }
    default void drawEntityMousePreview(Object entity, int x, int y, int z, int size, int mouseX, int mouseY, boolean paused) {
        drawEntityRelativeMousePreview(entity, x, y, z, size, mouseX - (x + size / 2.0f), mouseY - (y + size / 2.0f), paused);
    }
    default void drawPlayerMousePreview(Object player, int x, int y, int z, int size, int mouseX, int mouseY, boolean paused) {
        drawPlayerRelativeMousePreview(player, x, y, z, size, mouseX - (x + size / 2.0f), mouseY - (y + size / 2.0f), paused);
    }
    default void drawEntityRelativeMousePreview(Object entity, int x, int y, int z, int size, float relativeMouseX, float relativeMouseY, boolean paused) {
        float yaw = (float) Math.atan(relativeMouseX / 40.0f) * 20.0f;
        float pitch = (float) Math.atan(relativeMouseY / 40.0f) * 20.0f;
        drawEntityPreview(entity, x, y, z, size, yaw, pitch, paused);
    }
    default void drawPlayerRelativeMousePreview(Object player, int x, int y, int z, int size, float relativeMouseX, float relativeMouseY, boolean paused) {
        drawEntityRelativeMousePreview(player, x, y, z, size, relativeMouseX, relativeMouseY + playerPreviewHeadMouseYOffset(size), paused);
    }
    private static float playerPreviewHeadMouseYOffset(int size) {
        return size * 0.32f;
    }
}
