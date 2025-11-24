package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.msmp.dto.LiveServerSetting;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ServerLiveSettingsController {
    private final Instance instance;
    private AnimatedButton statusBadge;
    private final Consumer<InstanceState> stateListener;
    private Setting liveSettingsContainer;
    private MSMPManager msmpManager;
    private final Consumer<String> statusListener;

    public ServerLiveSettingsController(Instance instance) {
        this.instance = instance;
        this.stateListener = newState -> {
            if (statusBadge != null) {
                ScreenManager.getInstance().execute(this::updateStatus);
            }
        };
        this.statusListener = this::onMsmpStatusChange;
        this.instance.addStateListener(stateListener);
    }

    public void cleanup() {
        instance.removeStateListener(stateListener);
        if (msmpManager != null) {
            msmpManager.removeStatusListener(statusListener);
        }
    }

    private void onMsmpStatusChange(String text) {
        setStatus(text);
        if (("MSMP: Connected".equals(text) || (msmpManager.getApi() != null && msmpManager.getApi().isConnected())) && liveSettingsContainer != null && liveSettingsContainer.getRows().size() <= 1) {
            loadSettings();
        }
    }

    private void updateStatus() {
        if (msmpManager.isConnected || (msmpManager.getApi() != null && msmpManager.getApi().isConnected())) {
            setStatus("MSMP: Connected");
            if (liveSettingsContainer.getRows().size() <= 1) {
                loadSettings();
            }
        }
    }

    public List<Setting> getSettings() {
        this.msmpManager = instance.getMSMPManager();
        Setting.Builder builder = new Setting.Builder("Live Server Settings");
        statusBadge = new AnimatedButton.Builder().label("...").active(false).build();
        builder.addRow("", true, false, 20, statusBadge);
        this.liveSettingsContainer = builder.build();

        msmpManager.addStatusListener(statusListener);

        if (!msmpManager.isConnected) {
            msmpManager.connect();
        }

        return List.of(liveSettingsContainer);
    }

    private void loadSettings() {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !api.isConnected()) {
            setStatus("MSMP: Not connected");
            return;
        }
        api.getLiveServerSettings().thenAccept(settings -> ScreenManager.getInstance().execute(() -> buildSettingsUI(settings))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> setStatus("Failed to load settings: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
            return null;
        });
    }

    private void buildSettingsUI(List<LiveServerSetting> settings) {
        liveSettingsContainer.clearRows();
        liveSettingsContainer.addRow("", List.of(statusBadge), 20, true, false);

        if (settings == null || settings.isEmpty()) {
            setStatus("No live settings available");
            return;
        }
        setStatus(settings.size() + " settings loaded");

        settings.sort(Comparator.comparing(s -> s.name));

        for (LiveServerSetting setting : settings) {
            MountableButtonWidget.Builder rowBuilder = new MountableButtonWidget.Builder(setting.name)
                    .description(setting.description);

            switch (setting.type) {
                case "boolean":
                    ToggleWidget toggle = new ToggleWidget.Builder().toggled(Boolean.parseBoolean(setting.value)).build();
                    toggle.onChange = () -> setSetting(setting, String.valueOf(toggle.getValue()));
                    rowBuilder.addWidget(toggle);
                    break;
                case "integer":
                case "string":
                default:
                    TextInputWidget text = new TextInputWidget.Builder().text(setting.value).build();
                    text.onEnter = () -> setSetting(setting, text.getText());
                    rowBuilder.addWidget(text);
                    break;
            }
            liveSettingsContainer.addRow("", List.of(rowBuilder.build()), 30, true, false);
        }
    }

    private void setSetting(LiveServerSetting setting, String value) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !api.isConnected()) return;
        api.setLiveServerSetting(setting, value)
                .exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> new Notification("Error", "Failed to set " + setting.name + ": " + e.getMessage(), Notification.Type.ERROR));
                    return null;
                });
    }

    private void setStatus(String text) {
        if (statusBadge != null) {
            statusBadge.setMessage(text);
        }
    }
}
