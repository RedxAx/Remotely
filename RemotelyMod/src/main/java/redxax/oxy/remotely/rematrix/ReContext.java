package redxax.oxy.remotely.rematrix;

import restudio.rescreen.game.tooltip.MinecraftTooltip;

public interface ReContext {
    ReMatrixStack matrices();
    ReScissorStack scissors();
    ReTextureCache textures();
    ReTextBridge text();
    Object graphics();
    void drawItem(Object item, int x, int y, int z);
    default void drawMinecraftTooltip(MinecraftTooltip tooltip, int mouseX, int mouseY, int screenWidth, int screenHeight) {
    }
    default void drawMinecraftItemTooltip(Object item, MinecraftTooltip fallback, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        drawMinecraftTooltip(fallback, mouseX, mouseY, screenWidth, screenHeight);
    }
    default void drawItemPreview(Object item, int x, int y, int z, float scale, float rotationX, float rotationY, boolean paused) {
        drawItem(item, x, y, z);
    }
}
