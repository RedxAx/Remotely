package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ReScreenApplicationHost;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.ui.screens.auth.ReStudioLoginScreen;
import restudio.rescreen.ReStudioEntry;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Identifier;

public class RemotelyEntry extends ReStudioEntry {
    @Override
    public void init() {
        if (RemotelyClient.INSTANCE == null) {
            RemotelyInit.initClient(new ReScreenApplicationHost());
        }

        RemotelyClient.INSTANCE.getHost().ensureTextRenderer();

        setupScreens();
    }

    @Override
    public String getIconResourcePath() {
        return "assets/restudio/logos/Reemotely.png";
    }

    @Override
    public Identifier getIconIdentifier() {
        return Identifier.of("restudio", "logos/Reemotely.png");
    }

    @Override
    protected void setupScreens() {
        ScreenManager screenManager = ScreenManager.getInstance();
        ServerManagerScreen superScreen = new ServerManagerScreen(null, RemotelyClient.INSTANCE);

        if (Config.desktopMode && !ReStudio.getInstance().isAuthenticated()) {
            screenManager.clearDesktopWindows();
            screenManager.setScreen(new ReStudioLoginScreen(null, () -> {
                screenManager.setDesktopSuperScreen(superScreen);
                screenManager.setScreen(superScreen);
            }));
            return;
        } else if (Config.desktopMode) {
            screenManager.setDesktopSuperScreen(superScreen);
            screenManager.setScreen(superScreen);
            return;
        }

        if (ReStudio.getInstance().isAuthenticated()) {
            screenManager.setScreen(superScreen);
        } else {
            screenManager.setScreen(new ReStudioLoginScreen(ScreenManager.currentScreen, () -> screenManager.setScreen(superScreen)));
        }
    }
}
