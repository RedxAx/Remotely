package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.config.Config;

final class BrowserRemotelySettings {
    private BrowserRemotelySettings() {
    }

    static void apply(BrowserRemotelyConfigStore store) {
        Config.wallpaper = store.getWallpaper();
        Config.customReverseProxy = store.getCustomReverseProxy();
        Config.proxyHost = store.getProxyHost();
        Config.proxyUser = store.getProxyUser();
        Config.isDev = store.isDev();
        Config.enableDebugTools = store.getEnableDebugTools();
        Config.mainMenuStyle = store.getMainMenuStyle();
        Config.redesignMainMenu = store.getRedesignMainMenu();
        Config.scanServers = store.getScanServers();
        Config.quickServerPrecreate = store.getQuickServerPrecreate();
        Config.quickServerKeepRunning = store.getQuickServerKeepRunning();
        Config.quickServerAutoRestart = store.getQuickServerAutoRestart();
        Config.quickServerMirrorMods = store.getQuickServerMirrorMods();
        Config.obfuscate = store.getObfuscate();
        Config.resyncKeyCode = store.getReSyncKeyCode();
        Config.resyncKeyModifiers = store.getReSyncKeyModifiers();
    }
}
