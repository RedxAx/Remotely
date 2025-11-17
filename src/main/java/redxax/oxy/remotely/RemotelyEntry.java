package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ReScreenApplicationHost;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
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

        if ("standalone".equals(RemotelyInit.PLATFORM.getModloader())) {
            RemotelyClient.INSTANCE.getHost().ensureTextRenderer();
        }

        Config.applicationDir = remotelyDir;
        Config.setConfigManager(new RemotelyConfigManager(remotelyDir));
        setupScreens();
    }

    @Override
    public String getIconResourcePath() {
        return "";
    }

    @Override
    protected void setupScreens() {
        ScreenManager.getInstance().setScreen(new ServerManagerScreen(null, RemotelyClient.INSTANCE));
    }
}