package redxax.oxy.remotely.host;

import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.ui.core.Screen;

public interface ApplicationHost {
    void setScreen(Screen screen);
    Screen getCurrentScreen();
    void ensureTextRenderer();
    MinecraftGameAssets getGameAssets();
    Object getFontIdentifier(String namespace, String path);
    void openParentScreen(Screen currentScreen, Object parent);
    void setClipboard(String text);
    boolean shouldCloseRootScreen();
    default boolean supportsDesktopIntegrations() {
        return true;
    }

    default boolean managesPrimaryScreen() {
        return true;
    }


    String getGameVersion();
    String getGameUserName();
    String getGameUUID();
}
