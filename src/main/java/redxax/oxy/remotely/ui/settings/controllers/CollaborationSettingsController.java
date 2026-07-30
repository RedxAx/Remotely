package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.ColorFieldWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;

import java.util.List;

public final class CollaborationSettingsController {
    private final RemotelyConfigManager config;

    public CollaborationSettingsController(RemotelyConfigManager config) {
        this.config = config;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Collaboration");
        builder.addOption(ConfigOption.<Boolean>builder("Custom Color")
            .description("Use your chosen color for cursors, activity, and messages. Turn this off to use your avatar color.")
            .bind(config::getCollaborationColorOverrideEnabled, config::setCollaborationColorOverrideEnabled)
            .defaultValue(false)
            .build());

        ColorFieldWidget color = new ColorFieldWidget(0, 0, 140, 20, String.format("#%08X", config.getCollaborationColor()));
        color.onChange = () -> config.setCollaborationColor(parseColor(color.getColor()));
        builder.addRow("", new MountableButtonWidget.Builder("Color")
            .description("Choose the color other collaborators see.")
            .addWidget(color)
            .build());
        return List.of(builder.build());
    }

    private int parseColor(String value) {
        if (value == null) {
            return config.getCollaborationColor();
        }
        String hex = value.trim().replace("#", "");
        try {
            if (hex.length() == 8) {
                hex = hex.substring(2);
            }
            return hex.length() == 6 ? 0xFF000000 | Integer.parseUnsignedInt(hex, 16) : config.getCollaborationColor();
        } catch (NumberFormatException ignored) {
            return config.getCollaborationColor();
        }
    }
}
