package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.ui.settings.controllers.PortManagementSettingsProvider;
import redxax.oxy.remotely.ui.settings.controllers.SubuserSettingsProvider;
import restudio.rebase.backend.feature.AsyncBackupFeature;
import restudio.rescreen.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.List;
import java.util.Objects;

public final class BrowserHostedSettingsProviders {
    private BrowserHostedSettingsProviders() { }

    public static AsyncBackupFeature backups(BrowserRemotelyServerApi api, String serverId) {
        Objects.requireNonNull(api, "api");
        String id = requireServerId(serverId);
        return new AsyncBackupFeature() {
            public Async<List<ServerModels.Backup>> getBackups() { return api.getBackups(id); }
            public Async<ServerModels.Backup> createBackup(String name, List<String> ignoredFiles, boolean locked) { return api.createBackup(id, name, ignoredFiles, locked); }
            public Async<Void> deleteBackup(String backupUuid) { return api.deleteBackup(id, backupUuid); }
            public Async<String> getDownloadUrl(String backupUuid) { return api.getBackupDownloadUrl(id, backupUuid); }
            public Async<Void> restoreBackup(String backupUuid, boolean truncate) { return api.restoreBackup(id, backupUuid, truncate); }
            public Async<ServerModels.Backup> toggleBackupLock(String backupUuid) { return api.toggleBackupLock(id, backupUuid); }
        };
    }

    public static PortManagementSettingsProvider ports(BrowserRemotelyServerApi api, String serverId) {
        Objects.requireNonNull(api, "api");
        String id = requireServerId(serverId);
        return new PortManagementSettingsProvider() {
            public Async<List<ServerModels.Allocation>> getAllocations() { return api.getAllocations(id); }
            public Async<ServerModels.Allocation> createAllocation() { return api.createAllocation(id); }
            public Async<ServerModels.Allocation> updateAllocation(Integer allocationId, String notes, boolean primary) { return api.updateAllocation(id, allocationId, notes, primary); }
            public Async<Void> deleteAllocation(Integer allocationId) { return api.deleteAllocation(id, allocationId); }
        };
    }

    public static SubuserSettingsProvider subusers(BrowserRemotelyServerApi api, String serverId) {
        Objects.requireNonNull(api, "api");
        String id = requireServerId(serverId);
        return new SubuserSettingsProvider() {
            public Async<List<ServerModels.Subuser>> getSubusers() { return api.getSubusers(id); }
            public Async<ServerModels.SystemPermissions> getSystemPermissions() { return api.getSystemPermissions(); }
            public Async<ServerModels.Subuser> createSubuser(String email, List<String> permissions) { return api.createSubuser(id, email, permissions); }
            public Async<ServerModels.Subuser> updateSubuser(String subuserUuid, List<String> permissions) { return api.updateSubuser(id, subuserUuid, permissions); }
            public Async<Void> deleteSubuser(String subuserUuid) { return api.deleteSubuser(id, subuserUuid); }
            public Async<List<ServerModels.ReStudioUserInfo>> searchUsers(String query) { return api.searchUsers(query); }
        };
    }

    private static String requireServerId(String serverId) {
        if (serverId == null || serverId.isBlank()) throw new IllegalArgumentException("Server Identifier Is Required");
        return serverId;
    }
}
