package redxax.oxy.remotely.host;

import restudio.rescreen.ui.core.Screen;

public interface ApplicationHost {
    void setScreen(Screen screen);
    Screen getCurrentScreen();
    void ensureTextRenderer();
    Object getFontIdentifier(String namespace, String path);
    void openParentScreen(Screen currentScreen, Object parent);
    String getGameVersion();
    void setClipboard(String text);
}