package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.TabSwitchWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class AppearanceSettingsController {
    private final RemotelyConfigManager configManager;

    public AppearanceSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<>();

        Setting.Builder appearance = new Setting.Builder("Appearance");

        List<String> menuStyleOptions = Arrays.asList("Vanilla", "Minimal", "Normal", "Disable");
        int currentStyleIndex = menuStyleOptions.indexOf(configManager.getMainMenuStyle());

        TabSwitchWidget menuStyle = new TabSwitchWidget.Builder()
                .options(menuStyleOptions)
                .currentIndex(currentStyleIndex != -1 ? currentStyleIndex : 0)
                .onChange((newIndex) -> configManager.setMainMenuStyle(menuStyleOptions.get(newIndex)))
                .build();
        appearance.addRow("Menus Buttons Style", true, 20, menuStyle);

        ToggleWidget customMouse = new ToggleWidget.Builder()
                .toggled(configManager.getCustomMouse())
                .onChange(configManager::setCustomMouse)
                .build();
        appearance.addRow("Custom Mouse Cursor", false, 20, customMouse);

        ToggleWidget redesignButtons = new ToggleWidget.Builder()
                .toggled(configManager.getRedesignMainMenu())
                .onChange(configManager::setRedesignMainMenu)
                .build();
        appearance.addRow("Redesign Minecraft Buttons", false, 20, redesignButtons);

        ToggleWidget showBackground = new ToggleWidget.Builder()
                .toggled(configManager.getBackground())
                .onChange(configManager::setBackground)
                .build();
        appearance.addRow("Show Minecraft Background", false, 20, showBackground);

        ToggleWidget showWallpaper = new ToggleWidget.Builder()
                .toggled(configManager.getWallpaper())
                .onChange(configManager::setWallpaper)
                .build();
        appearance.addRow("Show Wallpaper", false, 20, showWallpaper);

        ToggleWidget textShadow = new ToggleWidget.Builder()
                .toggled(configManager.getShadow())
                .onChange(configManager::setShadow)
                .build();
        appearance.addRow("Text Shadow", false, 20, textShadow);
        settings.add(appearance.build());

        Setting.Builder animations = new Setting.Builder("Animations");
        animations.addRow("Scroll Animation Speed", true, 20, createFloatSlider(0, 60, configManager.getGlobalScrollSpeed(), configManager::setGlobalScrollSpeed));
        animations.addRow("Movement Animation Speed", true, 20, createFloatSlider(0, 60, configManager.getGlobalMovementSpeed(), configManager::setGlobalMovementSpeed));
        animations.addRow("Scale Animation Speed", true, 20, createFloatSlider(0, 60, configManager.getScaleAnimationSpeed(), configManager::setScaleAnimationSpeed));
        animations.addRow("Expand Animation Speed", true, 20, createFloatSlider(0, 30, configManager.getGlobalExpandSpeed(), configManager::setGlobalExpandSpeed));
        settings.add(animations.build());

        Setting.Builder mouse = new Setting.Builder("Mouse");
        mouse.addRow("Mouse Size", true, 20, createFloatSlider(1, 100, configManager.getMouseSize(), configManager::setMouseSize));
        mouse.addRow("Mouse Tail Size", true, 20, createFloatSlider(1, 100, configManager.getTailSize(), configManager::setTailSize));
        mouse.addRow("Mouse Tail Speed", true, 20, createFloatSlider(1, 100, configManager.getTailFollowSpeed(), configManager::setTailFollowSpeed));
        settings.add(mouse.build());

        return settings;
    }

    private DoubleSliderWidget createFloatSlider(float min, float max, float currentValue, Consumer<Float> setter) {
        AtomicReference<DoubleSliderWidget> sliderRef = new AtomicReference<>();
        DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
                .value((currentValue - min) / (max - min))
                .onChange(() -> {
                    float newValue = min + (float) (sliderRef.get().getValue() * (max - min));
                    setter.accept(newValue);
                    sliderRef.get().label = String.format("%.1f", newValue);
                })
                .label(String.format("%.1f", currentValue))
                .build();
        sliderRef.set(slider);
        return slider;
    }
}
