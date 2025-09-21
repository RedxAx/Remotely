package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rebase.settings.Setting;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ServerAdvancedSettingsController {
    private final Instance instance;

    public ServerAdvancedSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder advanced = new Setting.Builder("Advanced Settings");

        int maxPlayers = Integer.parseInt(instance.getSettings().getProperty("max-players", "20"));
        DoubleSliderWidget maxPlayersSlider = createIntSlider(1, 200, maxPlayers, val -> instance.getSettings().setProperty("max-players", String.valueOf(val)));
        advanced.addRow("Max Players", true, 20, maxPlayersSlider);

        TextInputWidget motdWidget = new TextInputWidget.Builder()
                .text(instance.getSettings().getProperty("motd", "A Minecraft Server"))
                .onChange(val -> instance.getSettings().setProperty("motd", val))
                .build();
        advanced.addRow("MOTD", true, 20, motdWidget);

        TextInputWidget seedWidget = new TextInputWidget.Builder()
                .text(instance.getSettings().getProperty("level-seed", ""))
                .onChange(val -> instance.getSettings().setProperty("level-seed", val))
                .build();
        advanced.addRow("Seed", true, 20, seedWidget);

        int spawnProtection = Integer.parseInt(instance.getSettings().getProperty("spawn-protection", "16"));
        DoubleSliderWidget spawnProtectionSlider = createIntSlider(0, 100, spawnProtection, val -> instance.getSettings().setProperty("spawn-protection", String.valueOf(val)));
        advanced.addRow("Spawn Protection", true, 20, spawnProtectionSlider);

        TextInputWidget portWidget = new TextInputWidget.Builder()
                .text(instance.getSettings().getProperty("server-port", "25565"))
                .onChange(val -> instance.getSettings().setProperty("server-port", val))
                .build();
        advanced.addRow("Port", true, 20, portWidget);

        ToggleWidget onlineModeWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getSettings().getProperty("online-mode", "true")))
                .onChange(val -> instance.getSettings().setProperty("online-mode", String.valueOf(val)))
                .build();
        advanced.addRow("Online Mode", false, 20, onlineModeWidget);

        ToggleWidget whitelistWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getSettings().getProperty("white-list", "false")))
                .onChange(val -> instance.getSettings().setProperty("white-list", String.valueOf(val)))
                .build();
        advanced.addRow("Whitelist", false, 20, whitelistWidget);

        return List.of(advanced.build());
    }

    private DoubleSliderWidget createIntSlider(int min, int max, int currentValue, java.util.function.Consumer<Integer> setter) {
        AtomicReference<DoubleSliderWidget> sliderRef = new AtomicReference<>();
        DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
                .value((double) (currentValue - min) / (max - min))
                .onChange(() -> {
                    int newValue = min + (int) Math.round(sliderRef.get().getValue() * (max - min));
                    setter.accept(newValue);
                    sliderRef.get().label = String.valueOf(newValue);
                })
                .label(String.valueOf(currentValue))
                .build();
        sliderRef.set(slider);
        return slider;
    }
}