package redxax.oxy.remotely.config;

import redxax.oxy.remotely.ui.settings.controllers.ServerClientSettingsController;
import restudio.rebase.settings.controllers.*;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.options.ConfigOption;

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

        JavaManagerController javaController = new JavaManagerController();
        settingsByTab.put("Java", javaController::getSettings);

        ExplorerSettingsController explorerController = new ExplorerSettingsController(configManager);
        settingsByTab.put("File Explorer", explorerController::getSettings);

        BackupSettingsController backupSettings = new BackupSettingsController(parent, null);
        settingsByTab.put("Backups", backupSettings::getSettings);

        PresetSettingsController presetSettings = new PresetSettingsController(parent);
        settingsByTab.put("Presets", presetSettings::getSettings);

        DevelopmentSettingsController devController = new DevelopmentSettingsController(configManager);
        settingsByTab.put("Development", () -> {
            List<Setting> devSettings = new ArrayList<>(devController.getSettings());

            Setting.Builder extraBuilder = new Setting.Builder("Network Display");
            extraBuilder.addOption(ConfigOption.<Boolean>builder("Show IP Address")
                .description("Don't Obfuscate IP Addresses (§kBurger§r).")
                .bind(configManager::getShowIp, configManager::setShowIp)
                .defaultValue(true)
                .build());
            devSettings.add(extraBuilder.build());

            return devSettings;
        });

        return new SettingsScreen(parent, "Remotely Settings", settingsByTab, () -> {
            if (configManager != null) configManager.save();
        }, null);
    }
}
