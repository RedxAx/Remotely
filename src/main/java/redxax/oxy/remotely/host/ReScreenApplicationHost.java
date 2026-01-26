package redxax.oxy.remotely.host;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rescreen.Main;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;

import static org.lwjgl.glfw.GLFW.glfwSetClipboardString;

public class ReScreenApplicationHost implements ApplicationHost {
    private final ScreenManager sm = ScreenManager.getInstance();

    @Override
    public void setScreen(Screen screen) {
        sm.setScreen(screen);
    }

    @Override
    public Screen getCurrentScreen() {
        return sm.getCurrentScreen();
    }

@Override
    public void ensureTextRenderer() {
        if (RemotelyClient.tr != null) return;
        TextRenderer.ensureLwjglRenderer();
        RemotelyClient.tr = TextRenderer.getTr();
    }

    @Override
    public Object getFontIdentifier(String namespace, String path) {
        if (namespace == null || namespace.isEmpty()) namespace = "minecraft";
        if (path == null) path = "";
        String ns = namespace.toLowerCase();
        String p = path.startsWith("/") ? path.substring(1) : path;
        p = p.toLowerCase();
        return ns + ":" + p;
    }

    @Override
    public void openParentScreen(Screen currentScreen, Object parent) {
        if (parent instanceof Screen) {
            sm.setScreen((Screen) parent);
        } else {
            sm.setScreen(null);
        }
    }

    @Override
    public String getGameVersion() {
        return "1.21.1";
    }

    @Override
    public void setClipboard(String text) {
        glfwSetClipboardString(Main.window, text);
    }

    @Override
    public String getGameUserName() {
        return null;
    }

    @Override
    public String getGameUUID() {
        return null;
    }
}
