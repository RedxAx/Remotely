package redxax.oxy.remotely.ui.settings.controllers;

import net.minecraft.client.MinecraftClient;
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

    public ServerGeneralSettingsController(Instance instance) {
        this.instance = instance;
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
                .currentIndex(gameModes.indexOf(instance.getSettings().getProperty("gamemode", "Survival")))
                .onChange(index -> instance.getSettings().setProperty("gamemode", gameModes.get(index)))
                .build();
        general.addRow("Game Mode", true, 20, gameModeWidget);

        List<String> difficulties = Arrays.asList("Peaceful", "Easy", "Normal", "Hard");
        TabSwitchWidget difficultyWidget = new TabSwitchWidget.Builder()
                .options(difficulties)
                .currentIndex(difficulties.indexOf(instance.getSettings().getProperty("difficulty", "Normal")))
                .onChange(index -> instance.getSettings().setProperty("difficulty", difficulties.get(index)))
                .build();
        general.addRow("Difficulty", true, 20, difficultyWidget);

        ToggleWidget eulaWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getSettings().getProperty("eula", "true")))
                .onChange(val -> instance.getSettings().setProperty("eula", String.valueOf(val)))
                .build();
        general.addRow("Agree to EULA", false, 20, eulaWidget);

        ToggleWidget pvpWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getSettings().getProperty("pvp", "true")))
                .onChange(val -> instance.getSettings().setProperty("pvp", String.valueOf(val)))
                .build();
        general.addRow("PvP", false, 20, pvpWidget);

        ToggleWidget hardcoreWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getSettings().getProperty("hardcore", "false")))
                .onChange(val -> instance.getSettings().setProperty("hardcore", String.valueOf(val)))
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
                .placeholder(MinecraftClient.getInstance().getGameVersion())
                .onChange(instance::setVersionId)
                .build();
        general.addRow("Server Version", true, 20, versionWidget);

        return List.of(general.build());
    }
}