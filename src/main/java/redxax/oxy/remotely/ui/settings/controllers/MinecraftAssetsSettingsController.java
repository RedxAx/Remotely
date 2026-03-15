package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.mcassets.MinecraftAssetsManager;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;

import java.util.List;

public class MinecraftAssetsSettingsController {
    private final RemotelyConfigManager configManager;
    private final MinecraftAssetsManager assetsManager;

    public MinecraftAssetsSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
        this.assetsManager = MinecraftAssetsManager.get(configManager);
    }

    public List<Setting> getSettings() {
        Setting.Builder generalBuilder = new Setting.Builder("Minecraft Assets");

        ConfigOption<Boolean> enabled = ConfigOption.<Boolean>builder("Enable Minecraft Assets")
                .description("Enable standalone Minecraft item assets in Remotely.")
                .bind(configManager::isMinecraftAssetsEnabled, configManager::setMinecraftAssetsEnabled)
                .defaultValue(true)
                .build();
        generalBuilder.addOption(enabled);

        ConfigOption<Boolean> managed = ConfigOption.<Boolean>builder("Managed Assets")
                .description("Allow Remotely to download and manage Minecraft assets.")
                .bind(configManager::isMinecraftAssetsManagedEnabled, configManager::setMinecraftAssetsManagedEnabled)
                .defaultValue(true)
                .dependsOn(enabled)
                .build();
        generalBuilder.addOption(managed);

        generalBuilder.addOption(ConfigOption.<Boolean>builder("Auto Download")
                .description("Download managed Minecraft assets automatically on startup.")
                .bind(configManager::isMinecraftAssetsAutoDownloadEnabled, configManager::setMinecraftAssetsAutoDownloadEnabled)
                .defaultValue(true)
                .dependsOn(managed)
                .build());

        generalBuilder.addOption(ConfigOption.<Boolean>builder("Ask Before Download")
                .description("Prompt before downloading managed Minecraft assets.")
                .bind(configManager::isMinecraftAssetsAskBeforeDownload, configManager::setMinecraftAssetsAskBeforeDownload)
                .defaultValue(true)
                .dependsOn(managed)
                .build());

        generalBuilder.addOption(ConfigOption.<String>builder("Version Target")
                .description("Use latest-release, latest-snapshot, or a specific Minecraft version.")
                .bind(configManager::getMinecraftAssetsVersionTarget, configManager::setMinecraftAssetsVersionTarget)
                .defaultValue("latest-release")
                .dependsOn(enabled)
                .build());

        Setting.Builder managedBuilder = new Setting.Builder("Managed Assets");
        managedBuilder.addRow("", true, false, 30, createManagedAssetsWidget());

        return List.of(generalBuilder.build(), managedBuilder.build());
    }

    private MountableButtonWidget createManagedAssetsWidget() {
        boolean installed = assetsManager.getActiveAssetsDir() != null;

        SquareButtonWidget installButton = new SquareButtonWidget.Builder()
                .imagePath(installed ? "reload.png" : "download.png")
                .hint(installed ? "Update Assets" : "Download Assets")
                .onClick(this::installAssets)
                .accentType(ThemeManager.getAccent("nice"))
                .build();

        SquareButtonWidget openButton = new SquareButtonWidget.Builder()
                .imagePath("explorer.png")
                .hint("Open Assets Dir")
                .onClick(assetsManager::openAssetsDir)
                .build();

        SquareButtonWidget removeButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .hint("Remove Assets")
                .onClick(this::removeAssets)
                .accentType(ThemeManager.getAccent("danger"))
                .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder("Managed Minecraft Assets")
                .hiddenText(assetsManager.getStatusBadge())
                .description(assetsManager.getStatusDescription())
                .onClick(installed ? assetsManager::openAssetsDir : this::installAssets)
                .addButton(installButton)
                .addButton(openButton)
                .addButton(removeButton)
                .build();
        widget.accentType = installed ? ThemeManager.getAccent("nice") : ThemeManager.getDefaultAccent();
        return widget;
    }

    private void installAssets() {
        Screen current = ScreenManager.getInstance().getCurrentScreen();
        ReScreen screen = current instanceof ReScreen reScreen ? reScreen : null;
        assetsManager.requestProvision(screen, true, false);
        refreshSettings();
    }

    private void removeAssets() {
        assetsManager.removeManagedAssets();
        refreshSettings();
    }

    private void refreshSettings() {
        Screen current = ScreenManager.getInstance().getCurrentScreen();
        if (current instanceof SettingsScreen settingsScreen) {
            settingsScreen.refreshTab("Minecraft Assets");
        }
    }
}
