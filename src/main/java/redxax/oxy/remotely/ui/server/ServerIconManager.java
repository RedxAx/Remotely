package redxax.oxy.remotely.ui.server;

import restudio.rescreen.platform.Async;
import restudio.rescreen.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class ServerIconManager {
    private final ServerIconProvider provider;

    public ServerIconManager(ServerIconProvider provider) {
        this.provider = provider == null ? ServerIconProvider.logical() : provider;
    }

    public void setDefaultIcons(Map<String, Identifier> icons) {
        provider.setDefaultIcons(icons);
    }

    public Identifier getIconId(Object server) {
        return provider.getIconId(server);
    }

    public Identifier getQuickIconId(Object server) {
        return provider.getQuickIconId(server);
    }

    public Identifier getLogicalIconId(String software, String loader) {
        return provider.getLogicalIconId(software, loader);
    }

    public void loadIconIdAsync(Object server, Consumer<Identifier> onLoaded) {
        provider.loadIconIdAsync(server, onLoaded);
    }

    public void loadRemoteIconAsync(Object server, Runnable onComplete) {
        provider.loadRemoteIconAsync(server, onComplete);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> resolveIconPath(Object server, boolean loadRemote, Runnable onLoaded) {
        return (Optional<T>) provider.resolveIconPath(server, loadRemote, onLoaded);
    }

    public Async<Void> customizeIcon(Object server, Object remoteHost, Identifier iconId, Runnable onComplete) {
        return provider.customizeIcon(server, remoteHost, iconId, onComplete);
    }

    public Async<Void> customizeIcon(Object server, Object remoteHost, ServerIconProvider.Customization customization, Runnable onComplete) {
        return provider.customizeIcon(server, remoteHost, customization, onComplete);
    }

    public void clearCache(Object server) {
        provider.clearCache(server);
    }

    public void clearAllRemoteTracking() {
        provider.clearAllRemoteTracking();
    }

    public List<Identifier> loadIconAssetIds() {
        return provider.loadIconAssetIds();
    }

    public List<Integer> loadIconTints() {
        return provider.loadIconTints();
    }
}
