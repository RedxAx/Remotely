package redxax.oxy.remotely.config;

import redxax.oxy.remotely.ui.settings.controllers.AppearanceSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.DevelopmentSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerClientSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.SoundSettingsController;
import restudio.rebase.ui.settings.Setting;
import restudio.rebase.ui.settings.SettingsScreen;
import restudio.rescreen.ui.rescreen.ReScreen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SettingsScreenFactory {

    public static SettingsScreen createGlobalSettingsScreen(ReScreen parent, RemotelyConfigManager configManager) {
        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();

        AppearanceSettingsController appearanceController = new AppearanceSettingsController(configManager);
        settingsByTab.put("Appearance", appearanceController::getSettings);

        SoundSettingsController soundController = new SoundSettingsController(configManager);
        settingsByTab.put("Sounds", soundController::getSettings);

        ServerClientSettingsController serverController = new ServerClientSettingsController(configManager);
        settingsByTab.put("Servers", serverController::getSettings);

        DevelopmentSettingsController devController = new DevelopmentSettingsController(configManager);
        settingsByTab.put("Development", devController::getSettings);

        return new SettingsScreen(parent, "Remotely Settings", settingsByTab, () -> {
            if (configManager != null) configManager.save();
        }, null);
    }
}