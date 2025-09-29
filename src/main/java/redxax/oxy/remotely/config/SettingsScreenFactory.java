package redxax.oxy.remotely.config;

import redxax.oxy.remotely.ui.settings.controllers.AppearanceSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.DevelopmentSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerClientSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.SoundSettingsController;
import restudio.rebase.settings.Setting;
import restudio.rebase.settings.SettingsScreen;
import restudio.rebase.settings.controllers.JavaManagerController;
import restudio.rebase.settings.controllers.ThemeController;
import restudio.rescreen.ui.rescreen.ReScreen;

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

        DevelopmentSettingsController devController = new DevelopmentSettingsController(configManager);
        settingsByTab.put("Development", devController::getSettings);

        return new SettingsScreen(parent, "Remotely Settings", settingsByTab, () -> {
            if (configManager != null) configManager.save();
        }, null);
    }
}