package redxax.oxy.remotely.config;

import redxax.oxy.remotely.ui.settings.controllers.AppearanceSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.DevelopmentSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerClientSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.SoundSettingsController;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.settings.Setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SettingsScreen extends restudio.rescreen.ui.settings.SettingsScreen {

    public SettingsScreen(Screen parentScreen, RemotelyConfigManager configManager) {
        super(parentScreen, "Remotely Settings", createSettingsMap(configManager), () -> {
            if (configManager != null) configManager.save();
        }, null);
    }

    private static Map<String, Supplier<List<Setting>>> createSettingsMap(RemotelyConfigManager configManager) {
        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();

        AppearanceSettingsController appearanceController = new AppearanceSettingsController(configManager);
        settingsByTab.put("Appearance", appearanceController::getSettings);

        SoundSettingsController soundController = new SoundSettingsController(configManager);
        settingsByTab.put("Sounds", soundController::getSettings);

        ServerClientSettingsController serverController = new ServerClientSettingsController(configManager);
        settingsByTab.put("Servers", serverController::getSettings);

        DevelopmentSettingsController devController = new DevelopmentSettingsController(configManager);
        settingsByTab.put("Development", devController::getSettings);

        return settingsByTab;
    }

    @Override
    public int getColumns() {
        return 2;
    }
}