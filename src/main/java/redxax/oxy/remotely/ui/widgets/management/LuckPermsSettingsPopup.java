package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.ui.integrations.luckperms.LuckPermsDashboardScreen;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

public class LuckPermsSettingsPopup extends PopupWidget {

    public LuckPermsSettingsPopup(LuckPermsService service, Runnable onSave) {
        super(0, 0, 350, 280, "LuckPerms Integration Settings");
        setLayer(500);

        LuckPermsService.Config config = service.getConfig();

        Builder builder = new Builder("LuckPerms Settings").size(350, 240).setResizable(false);

        var ref = new Object() {
            TextInputWidget urlInput = new TextInputWidget.Builder().text(config.apiUrl).placeholder("http://localhost:8080").build();
            TextInputWidget keyInput = new TextInputWidget.Builder().text(config.apiKey).placeholder("API Key").build();
            ToggleWidget enabledToggle = new ToggleWidget.Builder().toggled(config.enabled).build();
        };

        builder.addRow("Enable Integration", false, 20, ref.enabledToggle);
        builder.addRow("API URL", true, 20, ref.urlInput);
        builder.addRow("API Key", true, 20, ref.keyInput);

        AnimatedButton dashboardButton = new AnimatedButton.Builder()
            .label("Open Dashboard")
            .onClick(() -> {
                if (service.isEnabled()) {
                    hide();
                    ScreenManager.getInstance().setScreen(new LuckPermsDashboardScreen(ScreenManager.getInstance().getCurrentScreen(), service));
                } else {
                    new Notification("Error", "Please enable the integration and save first.", Notification.Type.ERROR);
                }
            })
            .accentType(ThemeManager.getAccent("calm"))
            .build();

        builder.addRow("", true, 30, dashboardButton);

        builder.addTitleButton(() -> {
            LuckPermsService.Config newConfig = new LuckPermsService.Config();
            newConfig.enabled = ref.enabledToggle.getValue();
            newConfig.apiUrl = ref.urlInput.getText();
            newConfig.apiKey = ref.keyInput.getText();

            service.updateConfig(newConfig);
            new Notification("Settings Saved", "LuckPerms configuration updated.", Notification.Type.SUCCESS);
            if (onSave != null) onSave.run();
            hide();
        }, "Save", ThemeManager.getAccent("nice"));

        PopupWidget configured = builder.build();
        this.rows.addAll(configured.rows);
        this.titleButtons.addAll(configured.titleButtons);
        this.setSize(configured.getWidth(), configured.getHeight());
        this.setPosition(configured.getX(), configured.getY());

        show();
    }
}
