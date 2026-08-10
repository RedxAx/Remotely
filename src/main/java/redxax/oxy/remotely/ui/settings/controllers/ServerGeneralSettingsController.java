package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.List;

public class ServerGeneralSettingsController {
    private final Instance instance;
    private final boolean editMode;

    public ServerGeneralSettingsController(Instance instance) {
        this(instance, false);
    }

    public ServerGeneralSettingsController(Instance instance, boolean editMode) {
        this.instance = instance;
        this.editMode = editMode;
    }

    public List<Setting> getSettings() {
        Setting.Builder general = new Setting.Builder("Server");

        general.addOption(ConfigOption.<String>builder("Server Name")
                .description("The name used for this server in Remotely.")
                .bind(instance::getName, instance::setName)
                .defaultValue("New Server")
                .resettable(false)
                .build());

        if (!editMode && RemotelyClient.INSTANCE.getHost().getGameUserName() != null) {
            general.addOption(ConfigOption.<Boolean>builder("Op Me")
                .description("Grant " + RemotelyClient.INSTANCE.getHost().getGameUserName() + " Operator.")
                .bind(() -> Boolean.parseBoolean(instance.getSettings().getProperty("op-me", "true")),
                    val -> instance.getSettings().setProperty("op-me", String.valueOf(val)))
                .defaultValue(true)
                .resettable(false)
                .build()
            );
        }

        return List.of(general.build());
    }
}
