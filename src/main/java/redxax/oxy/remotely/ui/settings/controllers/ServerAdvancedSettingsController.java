package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.List;
import java.util.Properties;

public class ServerAdvancedSettingsController {
    private final Instance instance;

    public ServerAdvancedSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder advanced = new Setting.Builder("Advanced Settings");
        Properties props = instance.getServerProperties();

        advanced.addOption(ConfigOption.<Integer>builder("Max Players")
                .description("The maximum number of players allowed on the server.")
                .range(1, 200)
                .bind(() -> Integer.parseInt(props.getProperty("max-players", "20")),
                      val -> props.setProperty("max-players", String.valueOf(val)))
                .defaultValue(20)
                .build());

        advanced.addOption(ConfigOption.<String>builder("MOTD")
                .description("Message Of The Day shown in the server list.")
                .bind(() -> props.getProperty("motd", "A Minecraft Server"),
                      val -> props.setProperty("motd", val))
                .defaultValue("A Minecraft Server")
                .build());

        advanced.addOption(ConfigOption.<String>builder("Level Seed")
                .description("The seed for the world generation.")
                .bind(() -> props.getProperty("level-seed", ""),
                      val -> props.setProperty("level-seed", val))
                .defaultValue("")
                .build());

        advanced.addOption(ConfigOption.<Integer>builder("Spawn Protection")
                .description("Radius of spawn protection in blocks.")
                .range(0, 100)
                .bind(() -> Integer.parseInt(props.getProperty("spawn-protection", "16")),
                      val -> props.setProperty("spawn-protection", String.valueOf(val)))
                .defaultValue(16)
                .build());

        advanced.addOption(ConfigOption.<String>builder("Server Port")
                .description("The port the server listens on.")
                .bind(() -> props.getProperty("server-port", "25565"),
                      val -> props.setProperty("server-port", val))
                .defaultValue("25565")
                .build());

        advanced.addOption(ConfigOption.<Boolean>builder("Online Mode")
                .description("Verify player accounts with Mojang servers.")
                .bind(() -> Boolean.parseBoolean(props.getProperty("online-mode", "true")),
                      val -> props.setProperty("online-mode", String.valueOf(val)))
                .defaultValue(true)
                .build());

        advanced.addOption(ConfigOption.<Boolean>builder("Whitelist")
                .description("Only allow players listed in the whitelist to join.")
                .bind(() -> Boolean.parseBoolean(props.getProperty("white-list", "false")),
                      val -> props.setProperty("white-list", String.valueOf(val)))
                .defaultValue(false)
                .build());

        return List.of(advanced.build());
    }
}
