package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.*;

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

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public List<Setting> getSettings() {
        Setting.Builder general = new Setting.Builder("General Settings");
        Properties props = instance.getServerProperties();

        general.addOption(ConfigOption.<String>builder("Server Name")
                .description("The display name of the server instance.")
                .bind(instance::getName, instance::setName)
                .defaultValue("New Server")
                .resettable(false)
                .build());

        List<String> gameModes = Arrays.asList("Survival", "Creative", "Adventure", "Spectator");
        general.addOption(ConfigOption.<String>builder("Game Mode")
                .description("Sets the game mode for new players.")
                .options(gameModes)
                .bind(() -> capitalize(props.getProperty("gamemode", "survival")),
                      val -> props.setProperty("gamemode", val.toLowerCase()))
                .defaultValue("Survival")
                .build());

        List<String> difficulties = Arrays.asList("Peaceful", "Easy", "Normal", "Hard");
        general.addOption(ConfigOption.<String>builder("Difficulty")
                .description("Defines the difficulty level of the server.")
                .options(difficulties)
                .bind(() -> capitalize(props.getProperty("difficulty", "normal")),
                      val -> props.setProperty("difficulty", val.toLowerCase()))
                .defaultValue("Normal")
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

        general.addOption(ConfigOption.<Boolean>builder("PvP")
                .description("Enable Player vs Player combat.")
                .bind(() -> Boolean.parseBoolean(props.getProperty("pvp", "true")),
                      val -> props.setProperty("pvp", String.valueOf(val)))
                .defaultValue(true)
                .build());

        general.addOption(ConfigOption.<Boolean>builder("Hardcore")
                .description("Enable hardcore mode (perma-death).")
                .bind(() -> Boolean.parseBoolean(props.getProperty("hardcore", "false")), val -> props.setProperty("hardcore", String.valueOf(val)))
                .defaultValue(false)
                .build());

        return List.of(general.build());
    }
}
