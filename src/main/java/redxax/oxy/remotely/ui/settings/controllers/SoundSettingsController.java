package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Sound;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SoundSettingsController {

    private final RemotelyConfigManager configManager;

    public SoundSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<>();
        Setting.Builder sounds = new Setting.Builder("Sounds");

        sounds.addRow("Sound Volume", true, 20, createIntSlider(0, 200, configManager.getSoundVolume(), configManager::setSoundVolume));
        sounds.addRow("Pitch Variation", true, 20, createIntSlider(0, 200, configManager.getPitchVariation(), configManager::setPitchVariation));

        ToggleWidget soundEffects = new ToggleWidget.Builder()
                .toggled(configManager.isSfxEnabled())
                .onChange(configManager::setSfxEnabled)
                .build();
        sounds.addRow("Sound Effects", false, 20, soundEffects);

        settings.add(sounds.build());

        Setting.Builder soundToggles = new Setting.Builder("Sound Toggles");
        for (Sound sound : Sound.values()) {
            String displayName = formatSoundEnumName(sound.name());
            ToggleWidget toggle = new ToggleWidget.Builder()
                    .toggled(configManager.isSoundEnabled(sound))
                    .onChange(enabled -> configManager.setSoundEnabled(sound, enabled))
                    .build();
            soundToggles.addRow(displayName, false, 20, toggle);
        }

        settings.add(soundToggles.build());

        return settings;
    }

    private DoubleSliderWidget createIntSlider(int min, int max, int currentValue, Consumer<Integer> setter) {
        AtomicReference<DoubleSliderWidget> sliderRef = new AtomicReference<>();
        DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
                .value((double) (currentValue - min) / (max - min))
                .onChange(() -> {
                    int newValue = min + (int) Math.round(sliderRef.get().getValue() * (max - min));
                    setter.accept(newValue);
                    sliderRef.get().label = String.format("%d", newValue);
                })
                .label(String.valueOf(currentValue))
                .build();
        sliderRef.set(slider);
        return slider;
    }

    private String formatSoundEnumName(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "";
        String lower = enumName.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}