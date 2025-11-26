package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

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

        ConfigOption<Boolean> enableManagement = ConfigOption.<Boolean>builder("Enable Management API")
                .description("Allow remote management via MSMP.")
                .bind(() -> Boolean.parseBoolean(p.getProperty("management-server-enabled", "false")),
                      val -> {
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
                .defaultValue(false)
                .build();
        management.addOption(enableManagement);

        management.addOption(ConfigOption.<String>builder("Management Host")
                .description("IP address to bind the management server to.")
                .bind(() -> p.getProperty("management-server-host", isRemote ? "0.0.0.0" : "localhost"),
                      val -> p.setProperty("management-server-host", val))
                .defaultValue(isRemote ? "0.0.0.0" : "localhost")
                .dependsOn(enableManagement)
                .build());

        management.addOption(ConfigOption.<String>builder("Management Port")
                .description("Port for the management server.")
                .bind(() -> p.getProperty("management-server-port", "25585"),
                      val -> p.setProperty("management-server-port", val))
                .defaultValue("25585")
                .dependsOn(enableManagement)
                .build());

        management.addOption(ConfigOption.<String>builder("Secret (40 chars)")
                .description("Security token for authentication.")
                .bind(() -> p.getProperty("management-server-secret", p.getProperty("management-server-token", "")),
                      val -> {
                          if (!val.isEmpty() && !SECRET_PATTERN.matcher(val).matches()) {
                              // Validation logic or visual feedback could be added here
                          }
                          p.setProperty("management-server-secret", val);
                          p.remove("management-server-token");
                      })
                .defaultValue("")
                .dependsOn(enableManagement)
                .build());

        Setting.Builder tls = new Setting.Builder("TLS / SSL");

        ConfigOption<Boolean> enableTls = ConfigOption.<Boolean>builder("Enable TLS (SSL)")
                .description("Encrypt management traffic.")
                .bind(() -> Boolean.parseBoolean(p.getProperty("management-server-tls-enabled", p.getProperty("management.server.tls.enabled", "false"))),
                      val -> {
                          p.setProperty("management-server-tls-enabled", String.valueOf(val));
                          p.setProperty("management.server.tls.enabled", String.valueOf(val));
                      })
                .defaultValue(false)
                .dependsOn(enableManagement)
                .build();
        tls.addOption(enableTls);

        tls.addOption(ConfigOption.<String>builder("Keystore Path")
                .description("Path to the JKS keystore file.")
                .bind(() -> p.getProperty("management.server.tls.keystore.path", ""),
                      val -> p.setProperty("management.server.tls.keystore.path", val))
                .defaultValue("")
                .dependsOn(enableTls)
                .build());

        tls.addOption(ConfigOption.<String>builder("Keystore Password")
                .description("Password for the keystore.")
                .bind(() -> p.getProperty("management.server.tls.keystore.password", ""),
                      val -> p.setProperty("management.server.tls.keystore.password", val))
                .defaultValue("")
                .dependsOn(enableTls)
                .build());

        return List.of(management.build(), tls.build());
    }
}
