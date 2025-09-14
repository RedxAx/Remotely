package redxax.oxy.remotely.adapters;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.platform.UiHost;
import restudio.rescreen.ui.core.Widget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import java.util.List;
import restudio.rescreen.ui.widgets.AnimatedWidget;

public class MinecraftUiHost implements UiHost {
    private final Screen screen;
    private final MinecraftClient client = MinecraftClient.getInstance();

    public MinecraftUiHost(Screen screen) { this.screen = screen; }

    @Override
    public <T extends Widget> T addDrawableChild(T widget) {
        // library expects to manage widgets itself; just forward to ScreenManager if needed
        // if library expects to render via host, return widget and let library manage it.
        return widget;
    }

    @Override public void remove(Widget widget) { /* no-op: library will remove from its lists */ }
    @Override public void setFocusedWidget(Widget widget) { /* map to Minecraft focus if needed */ }
    @Override public Widget getFocusedWidget() { return null; }

    @Override public boolean hasShiftDown() {
        return InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override public boolean hasControlDown() {
        return InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @Override public boolean hasAltDown() {
        return InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT) || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @Override public int getWidth() { return screen.width; }
    @Override public int getHeight() { return screen.height; }
    @Override public int getInitialWidth() { return screen.width; }
    @Override public int getInitialHeight() { return screen.height; }

    @Override public void updateRenderOrder(List<AnimatedWidget> widgets) { /* optionally re-sort */ }

    @Override public restudio.rescreen.ui.core.Screen asScreen() {
        return null; // the library Screen and Minecraft Screen cannot be the same; only used if needed
    }

    @Override public void setHitBottom(boolean hitBottom) { /* forward if needed */ }
}
