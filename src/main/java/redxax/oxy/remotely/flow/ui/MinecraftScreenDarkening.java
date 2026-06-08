package redxax.oxy.remotely.flow.ui;

import restudio.rescreen.platform.IDrawContext;

final class MinecraftScreenDarkening {
    private static final int CONTAINER_TOP = 0xC0101010;
    private static final int CONTAINER_BOTTOM = 0xD0101010;
    private static final int LIGHT_OVERLAY = 0x40101010;

    private MinecraftScreenDarkening() {
    }

    static void renderContainer(IDrawContext context, int width, int height) {
        context.fillGradient(0, 0, width, height, CONTAINER_TOP, CONTAINER_BOTTOM);
    }

    static void renderLight(IDrawContext context, int width, int height) {
        context.fill(0, 0, width, height, LIGHT_OVERLAY);
    }
}
