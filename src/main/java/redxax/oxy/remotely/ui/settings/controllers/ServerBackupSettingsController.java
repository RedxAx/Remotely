package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.settings.controllers.BackupSettingsController;
import restudio.rebase.settings.controllers.BackupSettingsProvider;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;

import java.util.List;

public final class ServerBackupSettingsController {
    private final BackupSettingsController controller;

    public ServerBackupSettingsController(ReScreen parentScreen, BackupSettingsProvider provider) {
        controller = new BackupSettingsController(parentScreen, provider);
    }

    public List<Setting> getSettings() {
        return controller.getSettings();
    }

    public void cleanup() {
        controller.cleanup();
    }
}
