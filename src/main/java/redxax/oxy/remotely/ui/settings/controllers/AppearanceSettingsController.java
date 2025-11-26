package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppearanceSettingsController {
    private final RemotelyConfigManager configManager;

    public AppearanceSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<>();

        Setting.Builder appearance = new Setting.Builder("Appearance");

        List<String> menuStyles = Arrays.asList("Vanilla", "Minimal", "Normal", "Disable");
        appearance.addOption(ConfigOption.<String>builder("Menu Buttons Style")
            .description("Controls the visual style of the main menu buttons.")
            .options(menuStyles)
            .bind(configManager::getMainMenuStyle, configManager::setMainMenuStyle)
            .defaultValue("Minimal")
            .build());

        ConfigOption<Boolean> customMouse = ConfigOption.<Boolean>builder("Custom Mouse Cursor")
            .description("Enable the custom rendered mouse cursor.")
            .bind(configManager::getCustomMouse, configManager::setCustomMouse)
            .defaultValue(false)
            .build();
        appearance.addOption(customMouse);

        appearance.addOption(ConfigOption.<Boolean>builder("Redesign Minecraft Buttons")
            .description("Apply custom styling to standard Minecraft buttons.")
            .bind(configManager::getRedesignMainMenu, configManager::setRedesignMainMenu)
            .defaultValue(false)
            .build());

        appearance.addOption(ConfigOption.<Boolean>builder("Show Minecraft Background")
            .description("Render the panorama background.")
            .bind(configManager::getBackground, configManager::setBackground)
            .defaultValue(false)
            .build());

        appearance.addOption(ConfigOption.<Boolean>builder("Show Wallpaper")
            .description("Use a custom wallpaper if available.")
            .bind(configManager::getWallpaper, configManager::setWallpaper)
            .defaultValue(false)
            .build());

        appearance.addOption(ConfigOption.<Boolean>builder("Text Shadow")
            .description("Render shadows behind text for better contrast.")
            .bind(configManager::getShadow, configManager::setShadow)
            .defaultValue(true)
            .build());

        settings.add(appearance.build());

        Setting.Builder animations = new Setting.Builder("Animations");

        animations.addOption(ConfigOption.<Float>builder("Scroll Animation Speed")
            .description("Controls how fast lists and containers scroll.")
            .range(0f, 60f)
            .bind(configManager::getGlobalScrollSpeed, configManager::setGlobalScrollSpeed)
            .defaultValue(7.0f)
            .build());

        animations.addOption(ConfigOption.<Float>builder("Movement Animation Speed")
            .description("Speed of element layout transitions.")
            .range(0f, 60f)
            .bind(configManager::getGlobalMovementSpeed, configManager::setGlobalMovementSpeed)
            .defaultValue(7.0f)
            .build());

        animations.addOption(ConfigOption.<Float>builder("Scale Animation Speed")
            .description("Speed of hover scale effects.")
            .range(0f, 60f)
            .bind(configManager::getScaleAnimationSpeed, configManager::setScaleAnimationSpeed)
            .defaultValue(10.0f)
            .build());

        animations.addOption(ConfigOption.<Float>builder("Expand Animation Speed")
            .description("Speed of expanding/collapsing elements.")
            .range(0f, 30f)
            .bind(configManager::getGlobalExpandSpeed, configManager::setGlobalExpandSpeed)
            .defaultValue(10.0f)
            .build());

        settings.add(animations.build());

        Setting.Builder mouse = new Setting.Builder("Mouse Customization");

        mouse.addOption(ConfigOption.<Float>builder("Mouse Size")
            .description("Diameter of the custom cursor in pixels.")
            .range(1f, 100f)
            .bind(configManager::getMouseSize, configManager::setMouseSize)
            .defaultValue(12.0f)
            .dependsOn(customMouse)
            .build());

        mouse.addOption(ConfigOption.<Float>builder("Tail Size")
            .description("Length of the cursor trail.")
            .range(1f, 100f)
            .bind(configManager::getTailSize, configManager::setTailSize)
            .defaultValue(7.0f)
            .dependsOn(customMouse)
            .build());

        mouse.addOption(ConfigOption.<Float>builder("Tail Follow Speed")
            .description("How tightly the trail follows the cursor.")
            .range(1f, 100f)
            .bind(configManager::getTailFollowSpeed, configManager::setTailFollowSpeed)
            .defaultValue(10.0f)
            .dependsOn(customMouse)
            .build());

        settings.add(mouse.build());

        return settings;
    }
}
