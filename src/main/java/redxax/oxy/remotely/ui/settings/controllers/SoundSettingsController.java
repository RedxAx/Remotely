package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.util.Sound;

import java.util.ArrayList;
import java.util.List;

public class SoundSettingsController {

    private final RemotelyConfigManager configManager;

    public SoundSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<>();

        Setting.Builder master = new Setting.Builder("Master Audio");

        ConfigOption<Boolean> sfxEnabled = ConfigOption.<Boolean>builder("Sound Effects")
                .description("Master switch for all application sound effects.")
                .bind(configManager::isSfxEnabled, configManager::setSfxEnabled)
                .defaultValue(true)
                .build();
        master.addOption(sfxEnabled);

        master.addOption(ConfigOption.<Integer>builder("Master Volume")
                .description("Global volume level for all sounds.")
                .range(0, 100)
                .bind(configManager::getSoundVolume, configManager::setSoundVolume)
                .defaultValue(50)
                .dependsOn(sfxEnabled)
                .build());

        master.addOption(ConfigOption.<Integer>builder("Pitch Variation")
                .description("Randomness in sound pitch for variety.")
                .range(0, 100)
                .bind(configManager::getPitchVariation, configManager::setPitchVariation)
                .defaultValue(20)
                .dependsOn(sfxEnabled)
                .build());

        settings.add(master.build());

        Setting.Builder individual = new Setting.Builder("Individual Sounds");

        for (Sound sound : Sound.values()) {
            String displayName = formatSoundEnumName(sound.name());
            individual.addOption(ConfigOption.<Boolean>builder(displayName)
                    .description("Enable or disable this specific sound.")
                    .bind(() -> configManager.isSoundEnabled(sound), val -> configManager.setSoundEnabled(sound, val))
                    .defaultValue(true)
                    .dependsOn(sfxEnabled)
                    .build());
        }

        settings.add(individual.build());

        return settings;
    }

    private String formatSoundEnumName(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "";
        String lower = enumName.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
