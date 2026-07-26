package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.msmp.dto.GameRule;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ServerGameRulesSettingsController {
    private final Instance instance;
    private AnimatedButton statusBadge;
    private final Consumer<InstanceState> stateListener;
    private Setting gameRulesSetting;
    private MSMPManager msmpManager;
    private final Consumer<String> statusListener;

    public ServerGameRulesSettingsController(Instance instance) {
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
        if (("MSMP: Connected".equals(text) || (msmpManager.getApi() != null && msmpManager.getApi().isConnected())) && gameRulesSetting != null && gameRulesSetting.getRows().size() <= 1) {
            loadRules();
        }
    }

    private void updateStatus() {
        IMSMPApi api = msmpManager.getApi();
        if (api != null && api.isConnected()) {
            setStatus("MSMP: Connected");
            if (gameRulesSetting.getRows().size() <= 1) {
                loadRules();
            }
        } else {
            msmpManager.connect();
        }
    }

    public List<Setting> getSettings() {
        this.msmpManager = instance.getMSMPManager();
        Setting.Builder builder = new Setting.Builder("Game Rules (Live)");
        statusBadge = new AnimatedButton.Builder().label("...").active(false).build();
        builder.addRow("", statusBadge);
        this.gameRulesSetting = builder.build();

        msmpManager.addStatusListener(statusListener);
        updateStatus();

        return List.of(gameRulesSetting);
    }

    private void loadRules() {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !api.isConnected()) {
            setStatus("MSMP: Not connected");
            return;
        }
        api.getGameRules().thenAccept(rules -> ScreenManager.getInstance().execute(() -> buildRulesUI(rules))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> setStatus("Failed to load rules: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
            return null;
        });
    }

    private void buildRulesUI(List<GameRule> rules) {
        gameRulesSetting.clearRows();
        gameRulesSetting.addRow("", statusBadge);

        if (rules == null || rules.isEmpty()) {
            setStatus("No game rules available");
            return;
        }
        setStatus(rules.size() + " rules loaded");

        rules.sort(Comparator.comparing(g -> g.name));

        for (GameRule rule : rules) {
            MountableButtonWidget.Builder rowBuilder = new MountableButtonWidget.Builder(rule.name);

            if ("boolean".equalsIgnoreCase(rule.type)) {
                ToggleWidget toggle = new ToggleWidget.Builder().toggled(Boolean.parseBoolean(rule.value)).build();
                toggle.onChange = () -> setRule(rule.name, String.valueOf(toggle.getValue()));
                rowBuilder.addWidget(toggle);
            } else {
                TextInputWidget text = new TextInputWidget.Builder().text(rule.value).build();
                text.onEnter = () -> setRule(rule.name, text.getText());
                rowBuilder.addWidget(text);
            }
            gameRulesSetting.addRow("", rowBuilder.build());
        }
    }

    private void setRule(String key, String value) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !api.isConnected()) return;
        api.setGameRule(key, value).exceptionally(e -> null);
    }

    private void setStatus(String text) {
        if (statusBadge != null) {
            statusBadge.setMessage(text);
        }
    }
}
