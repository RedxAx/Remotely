package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ReScreenApplicationHost;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import restudio.rebase.Rebase;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.minecraft.assets.MinecraftAssetsManager;
import restudio.rebase.update.UpdateAvailablePopup;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.DrawContextLwjgl;
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
            if (Rebase.get().getConfigManager().isUpdateCheckOnStartup()) {
                if (Rebase.get().getConfigManager().getUpdateChannel().equalsIgnoreCase("alpha"))  {
                    Rebase.get().getConfigManager().setUpdateChannel("stable");
                    Rebase.get().getConfigManager().save();
                    Rebase.get().getConfigManager().apply();
                }
                Rebase.get().getApplicationUpdateManager().checkForUpdates().thenAccept(updateOpt -> updateOpt.ifPresent(releaseInfo -> ScreenManager.getInstance().execute(() -> UpdateAvailablePopup.show(releaseInfo, Rebase.get().getApplicationUpdateManager()))));
            }
        }

        RemotelyClient.INSTANCE.getHost().ensureTextRenderer();

        setupScreens();
    }

    @Override
    public String getIconResourcePath() {
        return "/assets/restudio/logos/Reemotely.png";
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

        screenManager.setScreen(superScreen);
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
