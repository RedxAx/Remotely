package redxax.oxy.remotely;

import dev.restudio.recast.bridge.LocalBridgeClient;
import redxax.oxy.remotely.recast.RemotelyRecastProvider;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ReScreenApplicationHost;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import redxax.oxy.remotely.ui.widgets.ServerPulseTitleExtension;
import restudio.rebase.Rebase;
import restudio.rebase.minecraft.assets.MinecraftAssetsManager;
import restudio.rebase.update.UpdateAvailablePopup;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.desktop.DesktopWindowFeature;
import restudio.rescreen.platform.lwjgl.DrawContextLwjgl;
import restudio.rescreen.ReStudioEntry;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.WindowTitleExtension;
import restudio.rescreen.util.Identifier;

import java.nio.file.Path;
import java.util.List;

public class RemotelyEntry extends ReStudioEntry {
    private List<WindowTitleExtension> windowTitleExtensions = List.of();
    private LocalBridgeClient recastBridge;
    private final DesktopWindowFeature recastBridgeLifecycle = new DesktopWindowFeature() {
        @Override
        public void destroyed() {
            if (recastBridge != null) {
                recastBridge.close();
            }
        }
    };

    @Override
    public void init() {
        RemotelyInit.initCommon();
        windowTitleExtensions = List.of(new ServerPulseTitleExtension());
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

        recastBridge = new LocalBridgeClient("remotely", Path.of(System.getProperty("user.home"), ".restudio", "recast", "bridge.json"),
                new RemotelyRecastProvider(RemotelyClient.INSTANCE), System.err::println);
        recastBridge.start();

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
    public String getWindowTitle() {
        return "Remotely";
    }

    @Override
    public boolean isWindowTitleStatic() {
        return true;
    }

    @Override
    public List<WindowTitleExtension> getWindowTitleExtensions() {
        return windowTitleExtensions;
    }

    @Override
    public List<DesktopWindowFeature> getDesktopWindowFeatures() {
        return List.of(recastBridgeLifecycle);
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
