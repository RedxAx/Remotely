package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.discord.DiscordRpcSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.PlayerActionsFileProvider;
import redxax.oxy.remotely.ui.settings.controllers.PortManagementSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.ServerExtraSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerFeatureSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.ServerGeneralSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.ServerManagementSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.ServerPlanSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.ServerJvmSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.ServerLiveSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.SubuserSettingsProvider;
import restudio.rebase.settings.controllers.BackupSettingsProvider;
import restudio.rebase.settings.controllers.ModpackSettingsProvider;
import restudio.rebase.settings.controllers.ModpackSettingsTarget;
import restudio.rebase.settings.controllers.VersionSettingsCatalog;
import restudio.rebase.settings.controllers.VersionSettingsTarget;
import restudio.rebase.settings.controllers.SettingsActionCapability;

public interface ServerConfigurationUiPlatform {
    Object target();

    Object originalTarget();

    String name();

    boolean linkedModpack();

    boolean managementCompatible();

    boolean managementEnabled();

    VersionSettingsTarget versionTarget();

    VersionSettingsCatalog versionCatalog();

    ModpackSettingsTarget modpackTarget();

    ModpackSettingsTarget managedModpackTarget();

    ModpackSettingsProvider modpackProvider();

    DiscordRpcSettingsController.InstanceSettings discordSettings();

    default SettingsActionCapability discordCapability() {
        return SettingsActionCapability.supported("settings.discord-rpc");
    }

    ServerJvmSettingsProvider jvmSettingsProvider();

    ServerFeatureSettingsProvider featureSettingsProvider();

    ServerGeneralSettingsProvider generalSettingsProvider();

    ServerManagementSettingsProvider managementSettings();

    ServerPlanSettingsProvider planSettingsProvider();

    BackupSettingsProvider backupProvider();

    PortManagementSettingsProvider portProvider();

    SubuserSettingsProvider subuserProvider();

    PlayerActionsFileProvider playerActionsFileProvider();

    ServerLiveSettingsProvider liveSettingsProvider();

    ServerExtraSettingsController.DocumentAccess documentAccess();
}
