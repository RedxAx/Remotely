package redxax.oxy.remotely.config;

import redxax.oxy.remotely.ui.settings.controllers.ServerClientSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.PackContentSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ReProxySettingsController;
import restudio.rebase.settings.controllers.AppearanceSettingsController;
import restudio.rebase.settings.controllers.BackupSettingsController;
import restudio.rebase.settings.controllers.ExplorerSettingsController;
import restudio.rebase.settings.controllers.JavaManagerController;
import restudio.rebase.settings.controllers.LspSettingsController;
import restudio.rebase.settings.controllers.MinecraftAssetsSettingsController;
import restudio.rebase.settings.controllers.PresetSettingsController;
import restudio.rebase.settings.controllers.ReStudioAccountSettingsController;
import restudio.rebase.settings.controllers.SoundSettingsController;
import restudio.rebase.settings.controllers.ThemeController;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.controllers.DevelopmentSettingsController;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SettingsScreenFactory {

    public static SettingsScreen createGlobalSettingsScreen(ReScreen parent, RemotelyConfigManager configManager) {
        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();

        AppearanceSettingsController appearanceController = new AppearanceSettingsController(configManager);
        settingsByTab.put("Appearance", appearanceController::getSettings);

        ThemeController themeController = new ThemeController();
        settingsByTab.put("Theme", () -> {
            List<Setting> allThemeSettings = new ArrayList<>();
            allThemeSettings.addAll(themeController.getThemeSettings());
            allThemeSettings.addAll(themeController.getAccentSettings());
            return allThemeSettings;
        });

        SoundSettingsController soundController = new SoundSettingsController(configManager);
        settingsByTab.put("Sounds", soundController::getSettings);

        ServerClientSettingsController serverController = new ServerClientSettingsController(configManager);
        settingsByTab.put("Servers", serverController::getSettings);

        ReProxySettingsController reProxyController = new ReProxySettingsController();
        settingsByTab.put("ReProxy", reProxyController::getSettings);

        PackContentSettingsController packContentController = new PackContentSettingsController(configManager);
        settingsByTab.put("Pack Content", packContentController::getSettings);

        JavaManagerController javaController = new JavaManagerController();
        settingsByTab.put("Java", javaController::getSettings);

        LspSettingsController lspController = new LspSettingsController(configManager);
        settingsByTab.put("LSP", lspController::getSettings);

        MinecraftAssetsSettingsController minecraftAssetsController = new MinecraftAssetsSettingsController(configManager);
        settingsByTab.put("Minecraft Assets", minecraftAssetsController::getSettings);

        ExplorerSettingsController explorerController = new ExplorerSettingsController(configManager);
        settingsByTab.put("File Explorer", explorerController::getSettings);

        BackupSettingsController backupSettings = new BackupSettingsController(parent, null);
        settingsByTab.put("Backups", backupSettings::getSettings);

        PresetSettingsController presetSettings = new PresetSettingsController(parent);
        settingsByTab.put("Presets", presetSettings::getSettings);

        ReStudioAccountSettingsController accountSettings = new ReStudioAccountSettingsController();
        settingsByTab.put("About", accountSettings::getSettings);

        DevelopmentSettingsController devController = new DevelopmentSettingsController(configManager);
        settingsByTab.put("Development", devController::getSettings);

        return new SettingsScreen(parent, "Remotely Settings", settingsByTab, () -> {
            if (configManager != null) {
                configManager.save();
                configManager.apply();
            }
        }, backupSettings::cleanup) {
            public String getDesktopAppId() {
                return "global-settings";
            }

            public String getDesktopAppTitle() {
                return "Settings";
            }

            public String getDesktopAppIconPath() {
                return "remotely.png";
            }

            @Override
            public DesktopWindowBehavior getDesktopWindowBehavior() {
                return DesktopWindowBehaviorProvider.DesktopWindowBehavior.SINGLETON;
            }
        };
    }
}
