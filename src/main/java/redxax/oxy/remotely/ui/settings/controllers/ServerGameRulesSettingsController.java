package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.ui.settings.controllers.ServerLiveSettingsProvider.GameRuleValue;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public class ServerGameRulesSettingsController {
    private final ServerLiveSettingsProvider provider;
    private AnimatedButton statusBadge;
    private Setting gameRulesSetting;
    private ServerLiveSettingsProvider.Subscription stateSubscription = ServerLiveSettingsProvider.Subscription.NONE;
    private ServerLiveSettingsProvider.Subscription statusSubscription = ServerLiveSettingsProvider.Subscription.NONE;
    private boolean subscribed;
    private boolean cleaned;

    public ServerGameRulesSettingsController(Object instance) {
        this(instance instanceof ServerLiveSettingsProvider settingsProvider
                ? settingsProvider
                : new UnavailableServerLiveSettingsProvider("Live Game Rules Are Unavailable"));
    }

    public ServerGameRulesSettingsController(ServerLiveSettingsProvider provider) {
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
        if (("MSMP: Connected".equals(text) || provider.connected()) && gameRulesSetting != null && gameRulesSetting.getRows().size() <= 1) {
            loadRules();
        }
    }

    private void updateStatus() {
        if (provider.connected()) {
            setStatus("MSMP: Connected");
            if (gameRulesSetting.getRows().size() <= 1) {
                loadRules();
            }
        } else {
            setStatus(provider.status());
            provider.connect();
        }
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Game Rules (Live)");
        statusBadge = new AnimatedButton.Builder().label("...").active(false).build();
        builder.addRow("", statusBadge);
        this.gameRulesSetting = builder.build();

        subscribe();
        updateStatus();

        return List.of(gameRulesSetting);
    }

    private void loadRules() {
        if (!provider.connected()) {
            setStatus(provider.status());
            return;
        }
        provider.gameRules()
                .thenAccept(rules -> ScreenManager.getInstance().execute(() -> buildRulesUI(rules))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> setStatus("Failed to load rules: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
            return null;
        });
    }

    private void buildRulesUI(List<GameRuleValue> rules) {
        gameRulesSetting.clearRows();
        gameRulesSetting.addRow("", statusBadge);

        if (rules == null || rules.isEmpty()) {
            setStatus("No game rules available");
            return;
        }
        setStatus(rules.size() + " rules loaded");

        rules = new ArrayList<>(rules);
        rules.sort(Comparator.comparing(GameRuleValue::name));

        for (GameRuleValue rule : rules) {
            MountableButtonWidget.Builder rowBuilder = new MountableButtonWidget.Builder(rule.name());

            if ("boolean".equalsIgnoreCase(rule.type())) {
                ToggleWidget toggle = new ToggleWidget.Builder().toggled(Boolean.parseBoolean(rule.value())).build();
                toggle.onChange = () -> setRule(rule.name(), String.valueOf(toggle.getValue()));
                rowBuilder.addWidget(toggle);
            } else {
                TextInputWidget text = new TextInputWidget.Builder().text(rule.value()).build();
                text.onEnter = () -> setRule(rule.name(), text.getText());
                rowBuilder.addWidget(text);
            }
            gameRulesSetting.addRow("", rowBuilder.build());
        }
    }

    private void setRule(String key, String value) {
        if (!provider.connected()) return;
        provider.setGameRule(key, value).exceptionally(e -> null);
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
