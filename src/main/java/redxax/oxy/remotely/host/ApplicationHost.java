package redxax.oxy.remotely.host;

import restudio.rescreen.ui.core.Screen;

public interface ApplicationHost {
    void setScreen(Screen screen);
    Screen getCurrentScreen();
    void ensureTextRenderer();
    Object getFontIdentifier(String namespace, String path);
    void openParentScreen(Screen currentScreen, Object parent);
    void setClipboard(String text);


    String getGameVersion();
    String getGameUserName();
    String getGameUUID();
}
