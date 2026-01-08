package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ServerEggSettingsController {

    private final Map<String, String> remoteVariables;
    private final boolean isRemote;

    public ServerEggSettingsController(Map<String, String> remoteVariables, boolean isRemote) {
        this.remoteVariables = remoteVariables;
        this.isRemote = isRemote;
    }

    public List<Setting> getSettings() {
        if (!isRemote) return List.of();

        Setting.Builder installer = new Setting.Builder("Installer & Modpacks");

        List<String> sources = Arrays.asList("None", "curseforge", "modrinth");
        installer.addOption(ConfigOption.<String>builder("Modpack Source")
                .description("Source for modpack installation (e.g. CurseForge, Modrinth).")
                .options(sources)
                .bind(() -> {
                    String val = remoteVariables.getOrDefault("MODPACK_SOURCE", "");
                    return val.isEmpty() ? "None" : val;
                }, val -> remoteVariables.put("MODPACK_SOURCE", "None".equals(val) ? "" : val))
                .defaultValue("None")
                .build());

        installer.addOption(ConfigOption.<String>builder("Download URL")
                .description("Direct URL to modpack zip or file to download.")
                .bind(() -> remoteVariables.getOrDefault("DOWNLOAD_URL", ""),
                      val -> remoteVariables.put("DOWNLOAD_URL", val))
                .defaultValue("")
                .build());

        installer.addOption(ConfigOption.<String>builder("CurseForge API Key")
                .description("Optional API key for CurseForge downloads.")
                .bind(() -> remoteVariables.getOrDefault("CURSEFORGE_API_KEY", ""),
                      val -> remoteVariables.put("CURSEFORGE_API_KEY", val))
                .defaultValue("")
                .build());

        installer.addOption(ConfigOption.<String>builder("Modrinth API Key")
                .description("Optional API key for Modrinth downloads.")
                .bind(() -> remoteVariables.getOrDefault("MODRINTH_API_KEY", ""),
                      val -> remoteVariables.put("MODRINTH_API_KEY", val))
                .defaultValue("")
                .build());

        Setting.Builder advanced = new Setting.Builder("Advanced Startup");

        List<String> flags = Arrays.asList("None", "Aikar's Flags", "Velocity Flags");
        advanced.addOption(ConfigOption.<String>builder("Additional Flags")
                .description("Startup flags optimization.")
                .options(flags)
                .bind(() -> remoteVariables.getOrDefault("ADDITIONAL_FLAGS", "None"),
                      val -> remoteVariables.put("ADDITIONAL_FLAGS", val))
                .defaultValue("None")
                .build());

        advanced.addOption(ConfigOption.<Boolean>builder("Override Startup")
                .description("Override startup command to support variables.")
                .bind(() -> "1".equals(remoteVariables.getOrDefault("OVERRIDE_STARTUP", "1")),
                      val -> remoteVariables.put("OVERRIDE_STARTUP", val ? "1" : "0"))
                .defaultValue(true)
                .build());

        advanced.addOption(ConfigOption.<Boolean>builder("Auto Update")
                .description("Automatically update server software on restart.")
                .bind(() -> "1".equals(remoteVariables.getOrDefault("AUTOMATIC_UPDATING", "0")),
                      val -> remoteVariables.put("AUTOMATIC_UPDATING", val ? "1" : "0"))
                .defaultValue(false)
                .build());

        advanced.addOption(ConfigOption.<Boolean>builder("SIMD Operations")
                .description("Enable SIMD (Vector API) for Java 16-21.")
                .bind(() -> "1".equals(remoteVariables.getOrDefault("SIMD_OPERATIONS", "0")),
                      val -> remoteVariables.put("SIMD_OPERATIONS", val ? "1" : "0"))
                .defaultValue(false)
                .build());

        return List.of(installer.build(), advanced.build());
    }
}
