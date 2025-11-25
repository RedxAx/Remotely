package redxax.oxy.remotely.ui.settings.controllers;

import java.util.List;
import java.util.Properties;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

public class ServerFeatureSettingsController {
    private final Instance instance;

    public ServerFeatureSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Features & Integrations");
        Properties s = instance.getSettings();

        boolean msmpEnabled = Boolean.parseBoolean(s.getProperty("provider.msmp.enabled", "true"));
        ToggleWidget msmpToggle = new ToggleWidget.Builder().toggled(msmpEnabled).onChange(val -> s.setProperty("provider.msmp.enabled", String.valueOf(val))).build();
        builder.addRow("Enable MSMP", false, 20, msmpToggle);

        boolean backendEnabled = Boolean.parseBoolean(s.getProperty("provider.backend.enabled", "true"));
        ToggleWidget backendToggle = new ToggleWidget.Builder().toggled(backendEnabled).onChange(val -> s.setProperty("provider.backend.enabled", String.valueOf(val))).build();
        builder.addRow("Enable Backend API", false, 20, backendToggle);

        boolean standardEnabled = Boolean.parseBoolean(s.getProperty("provider.standard.enabled", "true"));
        ToggleWidget standardToggle = new ToggleWidget.Builder().toggled(standardEnabled).onChange(val -> s.setProperty("provider.standard.enabled", String.valueOf(val))).build();
        builder.addRow("Enable Standard Provider", false, 20, standardToggle);

        String priorityAll = s.getProperty("provider.priority", "msmp,backend,standard");
        TextInputWidget priorityAllInput = new TextInputWidget.Builder().text(priorityAll).placeholder("msmp,backend,standard").onChange(val -> s.setProperty("provider.priority", val)).build();
        builder.addRow("Global Priority", true, 20, priorityAllInput);

        String filesP = s.getProperty("provider.priority.files", "");
        TextInputWidget filesPI = new TextInputWidget.Builder().text(filesP).placeholder("backend").onChange(val -> s.setProperty("provider.priority.files", val)).build();
        builder.addRow("Files Priority", true, 20, filesPI);

        String consoleP = s.getProperty("provider.priority.console", "");
        TextInputWidget consolePI = new TextInputWidget.Builder().text(consoleP).placeholder("backend").onChange(val -> s.setProperty("provider.priority.console", val)).build();
        builder.addRow("Console Priority", true, 20, consolePI);

        String playersP = s.getProperty("provider.priority.players", "");
        TextInputWidget playersPI = new TextInputWidget.Builder().text(playersP).placeholder("msmp,backend").onChange(val -> s.setProperty("provider.priority.players", val)).build();
        builder.addRow("Players Priority", true, 20, playersPI);

        String eventsP = s.getProperty("provider.priority.events", "");
        TextInputWidget eventsPI = new TextInputWidget.Builder().text(eventsP).placeholder("msmp,standard").onChange(val -> s.setProperty("provider.priority.events", val)).build();
        builder.addRow("Events Priority", true, 20, eventsPI);

        String lifeP = s.getProperty("provider.priority.lifecycle", "");
        TextInputWidget lifePI = new TextInputWidget.Builder().text(lifeP).placeholder("msmp,standard").onChange(val -> s.setProperty("provider.priority.lifecycle", val)).build();
        builder.addRow("Lifecycle Priority", true, 20, lifePI);

        String wmP = s.getProperty("provider.priority.worldmap", "");
        TextInputWidget wmPI = new TextInputWidget.Builder().text(wmP).placeholder("msmp").onChange(val -> s.setProperty("provider.priority.worldmap", val)).build();
        builder.addRow("WorldMap Priority", true, 20, wmPI);
        return List.of(builder.build());
    }
}
