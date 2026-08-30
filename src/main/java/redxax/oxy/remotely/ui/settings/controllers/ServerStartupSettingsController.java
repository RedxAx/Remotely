package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.ArrayList;
import java.util.List;

public final class ServerStartupSettingsController {
    private final ServerStartupSettingsProvider provider;

    public ServerStartupSettingsController(ServerStartupSettingsProvider provider) {
        this.provider = provider;
    }

    public List<Setting> getSettings() {
        Setting.Builder setting = new Setting.Builder("Startup Options");
        int rows = 0;
        rows += select(setting, "ADDITIONAL_FLAGS", "Performance Flags", "Adds a supported set of Java performance flags",
                List.of("None", "Aikar's Flags", "Velocity Flags"));
        rows += select(setting, "MINEHUT_SUPPORT", "Minehut Support", "Configures the forwarding flags required for Minehut",
                List.of("None", "Velocity", "Waterfall", "Bukkit"));
        rows += toggle(setting, "AUTOMATIC_UPDATING", "Automatic Updating", "Updates supported server software when the server starts");
        rows += toggle(setting, "SIMD_OPERATIONS", "SIMD Operations", "Enables vector operations for software that supports them");
        rows += toggle(setting, "REMOVE_UPDATE_WARNING", "Remove Update Warning", "Removes the supported server software update warning");
        rows += toggle(setting, "MALWARE_SCAN", "Malware Scan", "Scans server files for known malware when the server starts");
        return rows == 0 ? List.of() : List.of(setting.build());
    }

    private int select(Setting.Builder setting, String key, String name, String description, List<String> options) {
        if (!provider.available(key)) return 0;
        List<String> values = new ArrayList<>(options);
        String current = provider.value(key);
        String selected = values.stream().filter(value -> value.equalsIgnoreCase(current)).findFirst().orElse(values.getFirst());
        DropDownWidget<String> input = new DropDownWidget.Builder<>(values)
                .selectedItem(selected)
                .onSelectionChanged(value -> provider.value(key, value))
                .size(220, 20)
                .build();
        setting.addRow("", new MountableButtonWidget.Builder(name).description(description).addWidget(input).build());
        return 1;
    }

    private int toggle(Setting.Builder setting, String key, String name, String description) {
        if (!provider.available(key)) return 0;
        ToggleWidget input = new ToggleWidget.Builder().toggled(enabled(provider.value(key))).build();
        input.onChange = () -> provider.value(key, input.getValue() ? "1" : "0");
        setting.addRow("", new MountableButtonWidget.Builder(name).description(description).addWidget(input).build());
        return 1;
    }

    private boolean enabled(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
