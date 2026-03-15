package redxax.oxy.remotely.config;

import redxax.oxy.remotely.mcassets.MinecraftAssetsManager;
import restudio.rebase.config.RebaseConfigManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemotelyConfigManager extends RebaseConfigManager {
    private static final boolean DEFAULT_MC_ASSETS_ENABLED = true;
    private static final boolean DEFAULT_MC_ASSETS_MANAGED = true;
    private static final boolean DEFAULT_MC_ASSETS_AUTO_DOWNLOAD = true;
    private static final boolean DEFAULT_MC_ASSETS_ASK_BEFORE_DOWNLOAD = true;
    private static final String DEFAULT_MC_ASSETS_VERSION_TARGET = "latest-release";

    public RemotelyConfigManager(Path applicationDir) {
        super(applicationDir);
        if (!properties.containsKey("update.projectId")) properties.setProperty("update.projectId", "remotely");
        if (!properties.containsKey("update.channel")) properties.setProperty("update.channel", "alpha");
    }

    @Override
    public void apply() {
        super.apply();
        Config.wallpaper = getWallpaper();
        restudio.rescreen.config.Config.background = getBackground();
        Config.customReverseProxy = getCustomReverseProxy();
        Config.proxyHost = getProxyHost();
        Config.proxyUser = getProxyUser();
        Config.isDev = isDev();
        Config.enableDebugTools = getEnableDebugTools();
        Config.mainMenuStyle = getMainMenuStyle();
        Config.redesignMainMenu = getRedesignMainMenu();
        Config.scanServers = getScanServers();
        Config.obfuscate = getObfuscate();
        restudio.rescreen.config.Config.consoleScrollSpeed = getConsoleScrollSpeed();
    }

    public boolean getWallpaper() { return Boolean.parseBoolean(properties.getProperty("remotely.wallpaper", "false")); }
    public void setWallpaper(boolean value) { properties.setProperty("remotely.wallpaper", String.valueOf(value)); save(); apply(); }

    public boolean getBackground() { return Boolean.parseBoolean(properties.getProperty("remotely.background", "false")); }
    public void setBackground(boolean value) { properties.setProperty("remotely.background", String.valueOf(value)); save(); apply(); }

    public boolean getCustomReverseProxy() { return Boolean.parseBoolean(properties.getProperty("remotely.customReverseProxy", "false")); }
    public void setCustomReverseProxy(boolean value) { properties.setProperty("remotely.customReverseProxy", String.valueOf(value)); save(); apply(); }

    public String getProxyHost() { return properties.getProperty("remotely.proxyHost", "RedxAx.net"); }
    public void setProxyHost(String value) { properties.setProperty("remotely.proxyHost", value); save(); apply(); }

    public String getProxyUser() { return properties.getProperty("remotely.proxyUser", "tunnel"); }
    public void setProxyUser(String value) { properties.setProperty("remotely.proxyUser", value); save(); apply(); }

    public boolean isDev() { return Boolean.parseBoolean(properties.getProperty("remotely.isDev", "false")); }
    public void setDev(boolean value) { properties.setProperty("remotely.isDev", String.valueOf(value)); save(); apply(); }

    public boolean getEnableDebugTools() { return Boolean.parseBoolean(properties.getProperty("remotely.enableDebugTools", "false")); }
    public void setEnableDebugTools(boolean value) { properties.setProperty("remotely.enableDebugTools", String.valueOf(value)); save(); apply(); }

    public String getMainMenuStyle() { return properties.getProperty("remotely.mainMenuStyle", "Minimal"); }
    public void setMainMenuStyle(String value) { properties.setProperty("remotely.mainMenuStyle", value); save(); apply(); }

    public boolean getRedesignMainMenu() { return Boolean.parseBoolean(properties.getProperty("remotely.redesignMainMenu", "false")); }
    public void setRedesignMainMenu(boolean value) { properties.setProperty("remotely.redesignMainMenu", String.valueOf(value)); save(); apply(); }

    public boolean getScanServers() { return Boolean.parseBoolean(properties.getProperty("remotely.scanServers", "true")); }
    public void setScanServers(boolean value) { properties.setProperty("remotely.scanServers", String.valueOf(value)); save(); apply(); }

    public boolean getObfuscate() { return Boolean.parseBoolean(properties.getProperty("remotely.showIp", "true")); }
    public void setObfuscate(boolean value) { properties.setProperty("remotely.showIp", String.valueOf(value)); save(); apply(); }

    public float getConsoleScrollSpeed() { return Float.parseFloat(properties.getProperty("ui.consoleScrollSpeed", "7.0")); }
    public void setConsoleScrollSpeed(float speed) { properties.setProperty("ui.consoleScrollSpeed", String.valueOf(speed)); save(); apply(); }

    public boolean isMinecraftAssetsEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ui.mcassets.enabled", String.valueOf(DEFAULT_MC_ASSETS_ENABLED)));
    }

    public void setMinecraftAssetsEnabled(boolean enabled) {
        properties.setProperty("ui.mcassets.enabled", String.valueOf(enabled));
        save();
        apply();
        MinecraftAssetsManager.notifyConfigurationChanged();
    }

    public boolean isMinecraftAssetsManagedEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ui.mcassets.managed", String.valueOf(DEFAULT_MC_ASSETS_MANAGED)));
    }

    public void setMinecraftAssetsManagedEnabled(boolean enabled) {
        properties.setProperty("ui.mcassets.managed", String.valueOf(enabled));
        save();
        apply();
        MinecraftAssetsManager.notifyConfigurationChanged();
    }

    public boolean isMinecraftAssetsAutoDownloadEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ui.mcassets.autoDownload", String.valueOf(DEFAULT_MC_ASSETS_AUTO_DOWNLOAD)));
    }

    public void setMinecraftAssetsAutoDownloadEnabled(boolean enabled) {
        properties.setProperty("ui.mcassets.autoDownload", String.valueOf(enabled));
        save();
        apply();
        MinecraftAssetsManager.notifyConfigurationChanged();
    }

    public boolean isMinecraftAssetsAskBeforeDownload() {
        return Boolean.parseBoolean(properties.getProperty("ui.mcassets.askBeforeDownload", String.valueOf(DEFAULT_MC_ASSETS_ASK_BEFORE_DOWNLOAD)));
    }

    public void setMinecraftAssetsAskBeforeDownload(boolean enabled) {
        properties.setProperty("ui.mcassets.askBeforeDownload", String.valueOf(enabled));
        save();
        apply();
        MinecraftAssetsManager.notifyConfigurationChanged();
    }

    public String getMinecraftAssetsVersionTarget() {
        return properties.getProperty("ui.mcassets.versionTarget", DEFAULT_MC_ASSETS_VERSION_TARGET);
    }

    public void setMinecraftAssetsVersionTarget(String versionTarget) {
        String normalized = versionTarget == null || versionTarget.isBlank() ? DEFAULT_MC_ASSETS_VERSION_TARGET : versionTarget.trim();
        properties.setProperty("ui.mcassets.versionTarget", normalized);
        save();
        apply();
        MinecraftAssetsManager.notifyConfigurationChanged();
    }

    public String getMinecraftAssetsResolvedVersion() {
        return properties.getProperty("ui.mcassets.resolvedVersion", "");
    }

    public void setMinecraftAssetsResolvedVersion(String resolvedVersion) {
        properties.setProperty("ui.mcassets.resolvedVersion", resolvedVersion == null ? "" : resolvedVersion.trim());
        save();
        apply();
        MinecraftAssetsManager.notifyConfigurationChanged();
    }

    public List<String> getInstanceOrder(String context) {
        String val = properties.getProperty("remotely.order." + context, "");
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split(",")));
    }

    public void setInstanceOrder(String context, List<String> order) {
        properties.setProperty("remotely.order." + context, String.join(",", order));
        save();
    }

    public List<String> getHiddenRestudioServers() {
        String val = properties.getProperty("remotely.hidden.restudio", "");
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split(",")));
    }

    public void setHiddenRestudioServers(List<String> serverIds) {
        properties.setProperty("remotely.hidden.restudio", String.join(",", serverIds));
        save();
    }

    public void hideRestudioServer(String serverId) {
        List<String> hidden = getHiddenRestudioServers();
        if (!hidden.contains(serverId)) {
            hidden.add(serverId);
            setHiddenRestudioServers(hidden);
        }
    }

    public void unhideRestudioServer(String serverId) {
        List<String> hidden = getHiddenRestudioServers();
        hidden.remove(serverId);
        setHiddenRestudioServers(hidden);
    }
}
