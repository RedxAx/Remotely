package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ReScreenApplicationHost;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.ui.screens.auth.ReStudioLoginScreen;
import restudio.rescreen.ReStudioEntry;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.ScreenManager;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyEntry extends ReStudioEntry {
    @Override
    public void init() {
        if (RemotelyClient.INSTANCE == null) {
            RemotelyInit.initClient(new ReScreenApplicationHost());
        }

        RemotelyClient.INSTANCE.getHost().ensureTextRenderer();

        Config.applicationDir = remotelyDir;

        ReStudio.getInstance().init(remotelyDir);

        setupScreens();
    }

    @Override
    public String getIconResourcePath() {
        return "";
    }

    @Override
    protected void setupScreens() {
        if (ReStudio.getInstance().isAuthenticated()) {
            ScreenManager.getInstance().setScreen(new ServerManagerScreen(null, RemotelyClient.INSTANCE));
        } else {
            ScreenManager.getInstance().setScreen(new ReStudioLoginScreen(ScreenManager.currentScreen, () -> ScreenManager.getInstance().setScreen(new ServerManagerScreen(null, RemotelyClient.INSTANCE))));
        }
    }
}
