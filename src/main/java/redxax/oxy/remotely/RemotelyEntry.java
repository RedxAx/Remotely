package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ReScreenApplicationHost;
import redxax.oxy.remotely.mcassets.MinecraftAssetsManager;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import restudio.rebase.restudio.ReStudio;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.DrawContextLwjgl;
import restudio.rebase.ui.screens.auth.ReStudioLoginScreen;
import restudio.rescreen.ReStudioEntry;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
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
    public IDrawContext getDrawContext() {
        return new DrawContextLwjgl(MinecraftAssetsManager.get(getRemotelyConfigManager()).getAssetSource());
    }

    @Override
    protected void setupScreens() {
        ScreenManager screenManager = ScreenManager.getInstance();
        ServerManagerScreen superScreen = new ServerManagerScreen(null, RemotelyClient.INSTANCE);

        if (Config.desktopMode && !ReStudio.getInstance().isAuthenticated()) {
            screenManager.setScreen(new ReStudioLoginScreen(null, () -> screenManager.setScreen(superScreen)));
            requestMinecraftAssetsStartupProvision();
            return;
        }
        if (ReStudio.getInstance().isAuthenticated()) {
            screenManager.setScreen(superScreen);
        } else {
            screenManager.setScreen(new ReStudioLoginScreen(ScreenManager.currentScreen, () -> screenManager.setScreen(superScreen)));
        }
        requestMinecraftAssetsStartupProvision();
    }

    private void requestMinecraftAssetsStartupProvision() {
        Screen current = ScreenManager.getInstance().getCurrentScreen();
        ReScreen screen = current instanceof ReScreen reScreen ? reScreen : null;
        MinecraftAssetsManager.get(getRemotelyConfigManager()).requestStartupProvision(screen);
    }

    private RemotelyConfigManager getRemotelyConfigManager() {
        if (Config.configManager instanceof RemotelyConfigManager remotelyConfigManager) {
            return remotelyConfigManager;
        }
        throw new IllegalStateException("Remotely config manager not initialized");
    }
}
