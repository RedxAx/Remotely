package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.ui.settings.controllers.ServerLiveSettingsProvider.LiveSettingValue;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public class ServerLiveSettingsController {
    private final ServerLiveSettingsProvider provider;
    private AnimatedButton statusBadge;
    private Setting liveSettingsContainer;
    private ServerLiveSettingsProvider.Subscription stateSubscription = ServerLiveSettingsProvider.Subscription.NONE;
    private ServerLiveSettingsProvider.Subscription statusSubscription = ServerLiveSettingsProvider.Subscription.NONE;
    private boolean subscribed;
    private boolean cleaned;

    public ServerLiveSettingsController(Object instance) {
        this(instance instanceof ServerLiveSettingsProvider settingsProvider
                ? settingsProvider
                : new UnavailableServerLiveSettingsProvider("Live Server Settings Are Unavailable"));
    }

    public ServerLiveSettingsController(ServerLiveSettingsProvider provider) {
        this.provider = provider;
    }

    public synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        stateSubscription.close();
        statusSubscription.close();
    }

    private void onMsmpStatusChange(String text) {
        setStatus(text);
        if (("MSMP: Connected".equals(text) || provider.connected()) && liveSettingsContainer != null && liveSettingsContainer.getRows().size() <= 1) {
            loadSettings();
        }
    }

    private void updateStatus() {
        if (provider.connected()) {
            setStatus("MSMP: Connected");
            if (liveSettingsContainer.getRows().size() <= 1) {
                loadSettings();
            }
        }
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Live Server Settings");
        statusBadge = new AnimatedButton.Builder().label("...").active(false).build();
        builder.addRow("", statusBadge);
        this.liveSettingsContainer = builder.build();

        subscribe();

        if (!provider.connected()) {
            setStatus(provider.status());
            provider.connect();
        } else {
            updateStatus();
        }

        return List.of(liveSettingsContainer);
    }

    private void loadSettings() {
        if (!provider.connected()) {
            setStatus(provider.status());
            return;
        }
        provider.liveSettings()
                .thenAccept(settings -> ScreenManager.getInstance().execute(() -> buildSettingsUI(settings))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> setStatus("Failed to load settings: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
            return null;
        });
    }

    private void buildSettingsUI(List<LiveSettingValue> settings) {
        liveSettingsContainer.clearRows();
        liveSettingsContainer.addRow("", statusBadge);

        if (settings == null || settings.isEmpty()) {
            setStatus("No live settings available");
            return;
        }
        setStatus(settings.size() + " settings loaded");

        settings = new ArrayList<>(settings);
        settings.sort(Comparator.comparing(LiveSettingValue::name));

        for (LiveSettingValue setting : settings) {
            MountableButtonWidget.Builder rowBuilder = new MountableButtonWidget.Builder(setting.name())
                    .description(setting.description());

            switch (setting.type()) {
                case "boolean":
                    ToggleWidget toggle = new ToggleWidget.Builder().toggled(Boolean.parseBoolean(setting.value())).build();
                    toggle.onChange = () -> setSetting(setting, String.valueOf(toggle.getValue()));
                    rowBuilder.addWidget(toggle);
                    break;
                case "integer":
                case "string":
                default:
                    TextInputWidget text = new TextInputWidget.Builder().text(setting.value()).build();
                    text.onEnter = () -> setSetting(setting, text.getText());
                    rowBuilder.addWidget(text);
                    break;
            }
            liveSettingsContainer.addRow("", rowBuilder.build());
        }
    }

    private void setSetting(LiveSettingValue setting, String value) {
        if (!provider.connected()) return;
        provider.setLiveSetting(setting, value)
                .exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> new Notification("Error", "Failed to set " + setting.name() + ": " + e.getMessage(), Notification.Type.ERROR));
                    return null;
                });
    }

    private void setStatus(String text) {
        if (statusBadge != null) {
            statusBadge.setMessage(text);
        }
    }

    private synchronized void subscribe() {
        if (subscribed || cleaned) return;
        subscribed = true;
        stateSubscription = provider.listenState(() -> {
            if (statusBadge != null) ScreenManager.getInstance().execute(this::updateStatus);
        });
        statusSubscription = provider.listenStatus(this::onMsmpStatusChange);
    }
}
