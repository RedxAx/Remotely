package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.msmp.IMSMPApi;
import redxax.oxy.remotely.msmp.MSMPClient;
import redxax.oxy.remotely.msmp.dto.GameRule;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ServerGameRulesSettingsController {
    private final Instance instance;
    private IMSMPApi msmpApi;
    private Container rulesContainer;
    private AnimatedButton statusBadge;

    public ServerGameRulesSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Game Rules (Live)");

        rulesContainer = new Container(0, 0, 0, 0);
        rulesContainer.layout(new ManagedLayout()).columns(1).padding(2);

        statusBadge = new AnimatedButton.Builder().label("MSMP: Not connected").active(false).accentType(ThemeManager.getAccent("danger")).build();

        if (instance.getState() != InstanceState.RUNNING) {
            statusBadge.setMessage("Start the server to edit game rules live");
            statusBadge.accentType = (ThemeManager.getAccent("calm"));
            builder.addRow("", true, 20, statusBadge);
            builder.addRow("", true, false, 200, rulesContainer);
            return List.of(builder.build());
        }

        statusBadge.setMessage("MSMP: Connecting…");
        statusBadge.accentType = (ThemeManager.getAccent("calm"));
        builder.addRow("", true, 20, statusBadge);
        builder.addRow("", true, false, 200, rulesContainer);

        connectAndLoadRules();

        return List.of(builder.build());
    }

    private void connectAndLoadRules() {
        if (msmpApi != null && msmpApi.isConnected()) {
            loadRules();
            return;
        }

        msmpApi = new MSMPClient();

        boolean tls = Boolean.parseBoolean(instance.getServerProperties().getProperty("management-server-tls-enabled", instance.getServerProperties().getProperty("management.server.tls.enabled", "false")));

        String host = instance.getServerProperties().getProperty("management-server-host", "localhost");
        int port = 25585;
        try {
            int p = Integer.parseInt(instance.getServerProperties().getProperty("management-server-port", "25585"));
            port = p > 0 ? p : 25585;
        } catch (Exception ignored) {}

        if (instance.isRemote() && "localhost".equalsIgnoreCase(host) && instance.getRemoteHost() != null && instance.getRemoteHost().ip != null) {
            host = instance.getRemoteHost().ip;
        }

        String token = getPersistentSecret(instance.getServerProperties());
        if (token == null || token.isEmpty()) {
            setStatus("MSMP token/secret required. Set a persistent token in Management settings.", "danger");
            return;
        }

        String schemeHost = (tls ? "wss://" : "ws://") + host;

        msmpApi.connect(schemeHost, port, token).thenAccept(success -> {
            if (success) {
                ScreenManager.getInstance().execute(() -> {
                    setStatus("MSMP: Connected", "nice");
                    loadRules();
                });
            } else {
                ScreenManager.getInstance().execute(() -> setStatus("MSMP: Connection failed", "danger"));
            }
        }).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> setStatus("MSMP: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), "danger"));
            return null;
        });
    }

    private void loadRules() {
        if (msmpApi == null || !msmpApi.isConnected()) {
            setStatus("MSMP: Not connected", "danger");
            return;
        }
        msmpApi.getGameRules().thenAccept(rules -> ScreenManager.getInstance().execute(() -> buildRulesUI(rules))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> setStatus("Failed to load rules: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), "danger"));
            return null;
        });
    }

    private void buildRulesUI(List<GameRule> rules) {
        rulesContainer.clearWidgets();
        if (rules == null || rules.isEmpty()) {
            setStatus("No game rules available", "calm");
            rulesContainer.updateWidgetPositions();
            return;
        }
        setStatus(rules.size() + " rules loaded", "nice");

        rules.sort(Comparator.comparing(g -> g.name));

        for (GameRule rule : rules) {
            if ("boolean".equalsIgnoreCase(rule.type)) {
                ToggleWidget toggle = new ToggleWidget.Builder().toggled(Boolean.parseBoolean(rule.value)).build();
                toggle.onChange = () -> setRule(rule.name, String.valueOf(toggle.getValue()));
                MountableButtonWidget row = new MountableButtonWidget.Builder(rule.name).addWidget(toggle).build();
                rulesContainer.addWidget(row);
            } else {
                TextInputWidget text = new TextInputWidget.Builder().text(rule.value).build();
                text.onEnter = () -> setRule(rule.name, text.getText());
                MountableButtonWidget row = new MountableButtonWidget.Builder(rule.name).addWidget(text).build();
                rulesContainer.addWidget(row);
            }
        }
        rulesContainer.updateWidgetPositions();
    }

    private void setRule(String key, String value) {
        if (msmpApi == null || !msmpApi.isConnected()) return;
        msmpApi.setGameRule(key, value).exceptionally(e -> null);
    }

    private void setStatus(String text, String accentKey) {
        if (statusBadge != null) {
            statusBadge.setMessage(text);
            statusBadge.accentType = (ThemeManager.getAccent(accentKey));
        }
    }

    private String getPersistentSecret(Properties p) {
        String v1 = p.getProperty("management-server-secret");
        if (v1 != null && !v1.isEmpty()) return v1;
        String v2 = p.getProperty("management.server.secret");
        if (v2 != null && !v2.isEmpty()) return v2;
        String v3 = p.getProperty("management-server-token");
        if (v3 != null && !v3.isEmpty()) return v3;
        String v4 = p.getProperty("management.server.token");
        if (v4 != null && !v4.isEmpty()) return v4;
        return null;
    }
}