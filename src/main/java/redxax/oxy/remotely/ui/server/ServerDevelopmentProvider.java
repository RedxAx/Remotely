package redxax.oxy.remotely.ui.server;

import restudio.rescreen.platform.Async;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.List;

public interface ServerDevelopmentProvider {
    default boolean available() {
        return false;
    }

    default Workspace workspaceForSource(Object source) {
        return null;
    }

    default Workspace workspaceForDevelopmentServer(Object candidate) {
        return null;
    }

    default Object sourceFor(Workspace workspace) {
        return null;
    }

    default Object developmentServer(Workspace workspace) {
        return null;
    }

    default boolean isPreparing(Object source) {
        return false;
    }

    default Async<Workspace> createWorkspace(Object source, CreateRequest request, Notification notification) {
        return unavailable("Development Workspace Creation Is Unavailable");
    }

    default Async<Boolean> prepareGitWorkspace(Object source, String workspaceId) {
        return unavailable("Git Workspace Preparation Is Unavailable");
    }

    default Async<ChangePreview> buildChangeDetails(String workspaceId, String relativePath) {
        return unavailable("Change Preview Is Unavailable");
    }

    default Async<Workspace> addIgnorePaths(String workspaceId, List<String> paths) {
        return unavailable("Ignored Paths Are Unavailable");
    }

    default Async<Void> restoreWorkspacePath(String workspaceId, String path) {
        return unavailable("Workspace Restore Is Unavailable");
    }

    default Async<Void> restoreWorkspacePaths(String workspaceId, List<String> paths) {
        return unavailable("Workspace Restore Is Unavailable");
    }

    default Async<Workspace> updateSettings(String workspaceId, ScopeProfile scope, List<String> ignoredPaths) {
        return unavailable("Development Settings Are Unavailable");
    }

    default Async<Workspace> pull(Object source, String workspaceId, Notification notification) {
        return unavailable("Development Sync Is Unavailable");
    }

    default Async<Inspection> inspectLocal(Object source, String workspaceId) {
        return unavailable("Development Inspection Is Unavailable");
    }

    default Async<DeployResult> deploy(Object source, DeployRequest request, Notification notification) {
        return unavailable("Development Publishing Is Unavailable");
    }

    default Async<Deployment> rollback(Object source, String workspaceId, String deploymentId, Notification notification) {
        return unavailable("Development Rollback Is Unavailable");
    }

    default Async<Void> disable(Object source, boolean deleteWorkspace) {
        return unavailable("Development Workspace Removal Is Unavailable");
    }

    default boolean isManagedIgnorePath(String path) {
        return false;
    }

    default boolean isRuntimeStatePath(String path) {
        return false;
    }

    default boolean includesRuntimeStateInGit(Workspace workspace) {
        return false;
    }

    default <T> Async<T> unavailable(String message) {
        return Async.failed(new UnsupportedOperationException(message));
    }

    static ServerDevelopmentProvider unavailableProvider() {
        return new ServerDevelopmentProvider() {
        };
    }

    enum FileChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }

    enum DeploymentMode {
        DIRECT,
        STAGED,
        RESTART_REQUIRED
    }

    record FileChange(String relativePath, FileChangeType type, boolean directory, long baselineSize,
                      long workspaceSize, boolean text) {
    }

    record IgnoredChange(String relativePath, FileChangeType type, String reason, boolean directory) {
    }

    record ChangePreview(String relativePath, FileChangeType type, boolean text, long baselineSize,
                         long workspaceSize, String diff) {
    }

    record Inspection(Workspace workspace, List<FileChange> visibleChanges, List<IgnoredChange> ignoredChanges,
                      List<RemoteDrift> remoteDrift, GitSummary gitSummary) {
        public Inspection {
            visibleChanges = visibleChanges == null ? List.of() : List.copyOf(visibleChanges);
            ignoredChanges = ignoredChanges == null ? List.of() : List.copyOf(ignoredChanges);
            remoteDrift = remoteDrift == null ? List.of() : List.copyOf(remoteDrift);
            gitSummary = gitSummary == null ? GitSummary.empty() : gitSummary;
        }
    }

    record RemoteDrift(String relativePath, FileChangeType type) {
    }

    record GitSummary(boolean installed, boolean repository, String branch, String upstream, int ahead,
                      int behind, int changedFiles) {
        static GitSummary empty() {
            return new GitSummary(false, false, "", "", 0, 0, 0);
        }
    }

    record Deployment(String id, String label, DeploymentMode mode, long createdAt, boolean deployedToSource,
                      List<FileChange> changes) {
        public Deployment {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }

    record DeployResult(Deployment deployment, List<FileChange> deployedChanges) {
        public DeployResult {
            deployedChanges = deployedChanges == null ? List.of() : List.copyOf(deployedChanges);
        }
    }

    final class ScopeProfile {
        public boolean fullInstance;
        public boolean configs = true;
        public boolean plugins = true;
        public boolean mods = true;
        public boolean dataPacks = true;
        public boolean scripts = true;
        public boolean playerData;
        public boolean stats;
        public boolean advancements;
        public boolean world;
        public List<String> includePaths = new ArrayList<>();
        public List<String> excludePaths = new ArrayList<>();

        public static ScopeProfile defaults() {
            return new ScopeProfile();
        }

        public ScopeProfile copy() {
            ScopeProfile copy = new ScopeProfile();
            copy.fullInstance = fullInstance;
            copy.configs = configs;
            copy.plugins = plugins;
            copy.mods = mods;
            copy.dataPacks = dataPacks;
            copy.scripts = scripts;
            copy.playerData = playerData;
            copy.stats = stats;
            copy.advancements = advancements;
            copy.world = world;
            copy.includePaths = new ArrayList<>(includePaths == null ? List.of() : includePaths);
            copy.excludePaths = new ArrayList<>(excludePaths == null ? List.of() : excludePaths);
            return copy;
        }
    }

    final class Workspace {
        public String id = "";
        public String name = "";
        public String sourceInstanceId = "";
        public String sourcePath = "";
        public String workspacePath = "";
        public String twinInstanceId = "";
        public String currentState = "";
        public boolean gitEnabled = true;
        public ScopeProfile scope = ScopeProfile.defaults();
        public List<String> ignorePaths = new ArrayList<>();
        public List<Deployment> deployments = new ArrayList<>();
    }

    final class CreateRequest {
        public String twinName = "";
        public ScopeProfile scope = ScopeProfile.defaults();
        public boolean initializeGit = true;
    }

    final class DeployRequest {
        public String workspaceId = "";
        public String label = "";
        public DeploymentMode mode = DeploymentMode.DIRECT;
        public List<String> selectedPaths = new ArrayList<>();
        public boolean createGitCheckpoint;
        public boolean failOnRemoteDrift = true;
    }
}
