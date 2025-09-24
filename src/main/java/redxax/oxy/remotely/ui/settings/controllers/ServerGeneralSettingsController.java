package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.settings.Setting;
import restudio.rescreen.ui.widgets.ScrollSelectorWidget;
import restudio.rescreen.ui.widgets.TabSwitchWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ServerGeneralSettingsController {
    private final Instance instance;
    private final RemotelyClient remotelyClient;

    public ServerGeneralSettingsController(Instance instance, RemotelyClient remotelyClient) {
        this.instance = instance;
        this.remotelyClient = remotelyClient;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public List<Setting> getSettings() {
        Setting.Builder general = new Setting.Builder("General Settings");

        TextInputWidget nameWidget = new TextInputWidget.Builder()
                .text(instance.getName())
                .onChange(instance::setName)
                .build();
        general.addRow("Server Name", true, 20, nameWidget);

        List<String> gameModes = Arrays.asList("Survival", "Creative", "Adventure");
        TabSwitchWidget gameModeWidget = new TabSwitchWidget.Builder()
                .options(gameModes)
                .currentIndex(gameModes.indexOf(capitalize(instance.getServerProperties().getProperty("gamemode", "survival"))))
                .onChange(index -> instance.getServerProperties().setProperty("gamemode", gameModes.get(index).toLowerCase()))
                .build();
        general.addRow("Game Mode", true, 20, gameModeWidget);

        List<String> difficulties = Arrays.asList("Peaceful", "Easy", "Normal", "Hard");
        TabSwitchWidget difficultyWidget = new TabSwitchWidget.Builder()
                .options(difficulties)
                .currentIndex(difficulties.indexOf(capitalize(instance.getServerProperties().getProperty("difficulty", "normal"))))
                .onChange(index -> instance.getServerProperties().setProperty("difficulty", difficulties.get(index).toLowerCase()))
                .build();
        general.addRow("Difficulty", true, 20, difficultyWidget);

        ToggleWidget eulaWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getServerProperties().getProperty("eula", "true")))
                .onChange(val -> instance.getServerProperties().setProperty("eula", String.valueOf(val)))
                .build();
        general.addRow("Agree to EULA", false, 20, eulaWidget);

        ToggleWidget pvpWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getServerProperties().getProperty("pvp", "true")))
                .onChange(val -> instance.getServerProperties().setProperty("pvp", String.valueOf(val)))
                .build();
        general.addRow("PvP", false, 20, pvpWidget);

        ToggleWidget hardcoreWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getServerProperties().getProperty("hardcore", "false")))
                .onChange(val -> instance.getServerProperties().setProperty("hardcore", String.valueOf(val)))
                .build();
        general.addRow("Hardcore", false, 20, hardcoreWidget);

        List<String> serverTypes = Arrays.stream(ModLoader.values()).map(ModLoader::toString).collect(Collectors.toList());
        ScrollSelectorWidget serverTypeWidget = new ScrollSelectorWidget.Builder()
                .options(serverTypes)
                .selectedIndex(Arrays.asList(ModLoader.values()).indexOf(instance.getModLoader()))
                .onChange(index -> instance.setModLoader(ModLoader.values()[index]))
                .build();
        general.addRow("Server Type", true, 20, serverTypeWidget);

        TextInputWidget versionWidget = new TextInputWidget.Builder()
                .text(instance.getVersionId())
                .placeholder(remotelyClient.getHost().getGameVersion())
                .onChange(instance::setVersionId)
                .build();
        general.addRow("Server Version", true, 20, versionWidget);

        return List.of(general.build());
    }
}