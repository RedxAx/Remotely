package redxax.oxy.remotely.host;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.minecraft.assets.MinecraftAssetsManager;
import restudio.rescreen.Main;
import restudio.rescreen.config.Config;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.game.SourceMinecraftGameAssets;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;

import static org.lwjgl.glfw.GLFW.glfwSetClipboardString;

public class ReScreenApplicationHost implements ApplicationHost {
    private final ScreenManager sm = ScreenManager.getInstance();
    private volatile MinecraftGameAssets gameAssets;

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
    public MinecraftGameAssets getGameAssets() {
        MinecraftGameAssets current = gameAssets;
        if (Config.configManager instanceof RemotelyConfigManager remotelyConfigManager) {
            if (!(current instanceof SourceMinecraftGameAssets)) {
                current = new SourceMinecraftGameAssets(MinecraftAssetsManager.get(remotelyConfigManager).getAssetSource());
                gameAssets = current;
            }
            return current;
        }
        if (current == null) {
            current = MinecraftGameAssets.EMPTY;
            gameAssets = current;
        }
        return current;
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
        }
    }

    @Override
    public boolean shouldCloseRootScreen() {
        return false;
    }

    @Override
    public String getGameVersion() {
        return null;
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
