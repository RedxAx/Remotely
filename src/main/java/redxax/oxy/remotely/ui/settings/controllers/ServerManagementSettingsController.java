package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.List;
import java.util.Properties;

public class ServerManagementSettingsController {
    private final Instance instance;

    public ServerManagementSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Properties p = instance.getServerProperties();

        String host = p.getProperty("management-server-host", "");
        if (host.isEmpty()) p.setProperty("management-server-host", "localhost");

        String port = p.getProperty("management-server-port", "");
        if (port.isEmpty() || "0".equals(port)) p.setProperty("management-server-port", "25585");

        String tlsDash = p.getProperty("management-server-tls-enabled");
        String tlsDot = p.getProperty("management.server.tls.enabled");
        if (tlsDash == null && tlsDot == null) {
            p.setProperty("management-server-tls-enabled", "false");
            p.setProperty("management.server.tls.enabled", "false");
        }

        Setting.Builder management = new Setting.Builder("Server Management Protocol");

        ToggleWidget enabledWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(p.getProperty("management-server-enabled", "false")))
                .onChange(val -> {
                    p.setProperty("management-server-enabled", String.valueOf(val));
                    String currentPort = p.getProperty("management-server-port", "0");
                    if (currentPort.isEmpty() || "0".equals(currentPort)) {
                        p.setProperty("management-server-port", "25585");
                    }
                    String tDash = p.getProperty("management-server-tls-enabled", "");
                    String tDot = p.getProperty("management.server.tls.enabled", "");
                    if (tDash.isEmpty() && tDot.isEmpty()) {
                        p.setProperty("management-server-tls-enabled", "false");
                        p.setProperty("management.server.tls.enabled", "false");
                    }
                })
                .build();
        management.addRow("Enable Management API", false, 20, enabledWidget);

        TextInputWidget hostWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management-server-host", "localhost"))
                .onChange(val -> p.setProperty("management-server-host", val))
                .build();
        management.addRow("Management Host", true, 20, hostWidget);

        TextInputWidget portWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management-server-port", "25585"))
                .onChange(val -> p.setProperty("management-server-port", val))
                .build();
        management.addRow("Management Port", true, 20, portWidget);

        TextInputWidget tokenWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management-server-token", ""))
                .placeholder("Leave empty for one-time token")
                .onChange(val -> p.setProperty("management-server-token", val))
                .build();
        management.addRow("Persistent Token", true, 20, tokenWidget);

        Setting.Builder tls = new Setting.Builder("TLS / SSL");

        boolean tlsEnabled = Boolean.parseBoolean(p.getProperty("management-server-tls-enabled", p.getProperty("management.server.tls.enabled", "false")));
        ToggleWidget tlsEnabledWidget = new ToggleWidget.Builder()
                .toggled(tlsEnabled)
                .onChange(val -> {
                    p.setProperty("management-server-tls-enabled", String.valueOf(val));
                    p.setProperty("management.server.tls.enabled", String.valueOf(val));
                })
                .build();
        tls.addRow("Enable TLS (SSL)", false, 20, tlsEnabledWidget);

        TextInputWidget keystorePathWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management.server.tls.keystore.path", ""))
                .placeholder("e.g., keystore.jks")
                .onChange(val -> p.setProperty("management.server.tls.keystore.path", val))
                .build();
        tls.addRow("Keystore Path", true, 20, keystorePathWidget);

        TextInputWidget keystorePasswordWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management.server.tls.keystore.password", ""))
                .placeholder("Keystore Password")
                .onChange(val -> p.setProperty("management.server.tls.keystore.password", val))
                .build();
        tls.addRow("Keystore Password", true, 20, keystorePasswordWidget);

        return List.of(management.build(), tls.build());
    }
}