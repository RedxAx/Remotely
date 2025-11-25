package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

public class ServerManagementSettingsController {
    private final Instance instance;
    private static final Pattern SECRET_PATTERN = Pattern.compile("^[a-zA-Z0-9]{40}$");

    public ServerManagementSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Properties p = instance.getServerProperties();
        boolean isRemote = instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);

        String host = p.getProperty("management-server-host", "");
        if (host.isEmpty()) {
            host = isRemote ? "0.0.0.0" : "localhost";
            p.setProperty("management-server-host", host);
        }

        String port = p.getProperty("management-server-port", "");
        if (port.isEmpty() || "0".equals(port)) p.setProperty("management-server-port", "25585");

        String tlsDash = p.getProperty("management-server-ssl-enabled");
        if (p.getProperty("management-server-tls-enabled") == null && p.getProperty("management.server.tls.enabled") == null) {
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
                    String currentHost = p.getProperty("management-server-host", "");
                    if (isRemote && (currentHost.isEmpty() || "localhost".equalsIgnoreCase(currentHost) || "127.0.0.1".equals(currentHost))) {
                        p.setProperty("management-server-host", "0.0.0.0");
                    }
                })
                .build();
        management.addRow("Enable Management API", false, 20, enabledWidget);

        TextInputWidget hostWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management-server-host", isRemote ? "0.0.0.0" : "localhost"))
                .onChange(val -> p.setProperty("management-server-host", val))
                .build();
        management.addRow("Management Host", true, 20, hostWidget);

        TextInputWidget portWidget = new TextInputWidget.Builder()
                .text(p.getProperty("management-server-port", "25585"))
                .onChange(val -> p.setProperty("management-server-port", val))
                .build();
        management.addRow("Management Port", true, 20, portWidget);

        String currentSecret = p.getProperty("management-server-secret", p.getProperty("management-server-token", ""));
        TextInputWidget secretWidget = new TextInputWidget.Builder()
                .text(currentSecret)
                .placeholder("40 char alphanumeric secret")
                .onChange(val -> {
                    if (!val.isEmpty() && !SECRET_PATTERN.matcher(val).matches()) {
                    }
                    p.setProperty("management-server-secret", val);
                    p.remove("management-server-token");
                })
                .build();

        management.addRow("Secret (40 chars)", true, 20, secretWidget);

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
