package redxax.oxy.remotely.ui.server;

import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.twin.ServerTwinManager;
import restudio.rescreen.util.Notification;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

import java.util.List;
import java.util.Objects;

public final class DesktopServerDevelopmentProvider implements ServerDevelopmentProvider {
    private final ServerTwinManager manager;

    public DesktopServerDevelopmentProvider() {
        ServerTwinManager resolved;
        try {
            resolved = Rebase.get().getTwinManager();
        } catch (Throwable ignored) {
            resolved = null;
        }
        manager = resolved;
    }

    @Override
    public boolean available() {
        return manager != null;
    }

    @Override
    public Workspace workspaceForSource(Object source) {
        Instance instance = instance(source);
        return instance == null || manager == null ? null : workspace(manager.getTwinForSource(instance));
    }

    @Override
    public Workspace workspaceForDevelopmentServer(Object candidate) {
        Instance instance = instance(candidate);
        if (instance == null || manager == null || instance.getInstanceId() == null) return null;
        return manager.getTwins().stream()
                .filter(twin -> Objects.equals(twin.twinInstanceId, instance.getInstanceId()))
                .findFirst()
                .map(this::workspace)
                .orElse(null);
    }

    @Override
    public Object sourceFor(Workspace workspace) {
        if (workspace == null || workspace.sourceInstanceId == null || workspace.sourceInstanceId.isBlank() || manager == null) return null;
        try {
            return Rebase.get().getInstanceManager().getInstanceById(workspace.sourceInstanceId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public Object developmentServer(Workspace workspace) {
        if (workspace == null || manager == null) return null;
        ServerTwinManager.ServerTwin twin = manager.getTwin(workspace.id);
        return twin == null ? null : manager.getTwinInstance(twin);
    }

    @Override
    public boolean isPreparing(Object source) {
        Instance instance = instance(source);
        return instance != null && manager != null && manager.isPreparingTwin(instance);
    }

    @Override
    public Async<Workspace> createWorkspace(Object source, CreateRequest request, Notification notification) {
        Instance instance = instance(source);
        if (instance == null || manager == null) return unavailable("Development Workspace Creation Is Unavailable");
        ServerTwinManager.CreateRequest nativeRequest = new ServerTwinManager.CreateRequest();
        nativeRequest.twinName = request == null ? "" : request.twinName;
        nativeRequest.initializeGit = request == null || request.initializeGit;
        nativeRequest.scope = scope(request == null ? null : request.scope);
        return JvmAsyncBridge.fromFuture(manager.createTwin(instance, nativeRequest, notification)).thenApply(this::workspace);
    }

    @Override
    public Async<Boolean> prepareGitWorkspace(Object source, String workspaceId) {
        Instance instance = instance(source);
        return instance == null || manager == null
                ? unavailable("Git Workspace Preparation Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.prepareGitWorkspace(instance, workspaceId));
    }

    @Override
    public Async<ChangePreview> buildChangeDetails(String workspaceId, String relativePath) {
        return manager == null ? unavailable("Change Preview Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.buildChangeDetails(workspaceId, relativePath)).thenApply(this::changePreview);
    }

    @Override
    public Async<Workspace> addIgnorePaths(String workspaceId, List<String> paths) {
        return manager == null ? unavailable("Ignored Paths Are Unavailable")
                : JvmAsyncBridge.fromFuture(manager.addTwinIgnorePaths(workspaceId, paths)).thenApply(this::workspace);
    }

    @Override
    public Async<Void> restoreWorkspacePath(String workspaceId, String path) {
        return manager == null ? unavailable("Workspace Restore Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.restoreWorkspacePath(workspaceId, path));
    }

    @Override
    public Async<Void> restoreWorkspacePaths(String workspaceId, List<String> paths) {
        return manager == null ? unavailable("Workspace Restore Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.restoreWorkspacePaths(workspaceId, paths));
    }

    @Override
    public Async<Workspace> updateSettings(String workspaceId, ScopeProfile scope, List<String> ignoredPaths) {
        return manager == null ? unavailable("Development Settings Are Unavailable")
                : JvmAsyncBridge.fromFuture(manager.updateTwinSettings(workspaceId, scope(scope), ignoredPaths)).thenApply(this::workspace);
    }

    @Override
    public Async<Workspace> pull(Object source, String workspaceId, Notification notification) {
        Instance instance = instance(source);
        return instance == null || manager == null ? unavailable("Development Sync Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.pullTwin(instance, workspaceId, notification)).thenApply(this::workspace);
    }

    @Override
    public Async<Inspection> inspectLocal(Object source, String workspaceId) {
        Instance instance = instance(source);
        return instance == null || manager == null ? unavailable("Development Inspection Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.inspectLocalTwin(instance, workspaceId)).thenApply(this::inspection);
    }

    @Override
    public Async<DeployResult> deploy(Object source, DeployRequest request, Notification notification) {
        Instance instance = instance(source);
        if (instance == null || manager == null) return unavailable("Development Publishing Is Unavailable");
        ServerTwinManager.TwinDeployRequest nativeRequest = new ServerTwinManager.TwinDeployRequest();
        nativeRequest.twinId = request.workspaceId;
        nativeRequest.label = request.label;
        nativeRequest.mode = switch (request.mode == null ? DeploymentMode.DIRECT : request.mode) {
            case DIRECT -> ServerTwinManager.DeploymentMode.DIRECT;
            case STAGED -> ServerTwinManager.DeploymentMode.STAGED;
            case RESTART_REQUIRED -> ServerTwinManager.DeploymentMode.RESTART_REQUIRED;
        };
        nativeRequest.selectedPaths = request.selectedPaths == null ? List.of() : List.copyOf(request.selectedPaths);
        nativeRequest.createGitCheckpoint = request.createGitCheckpoint;
        nativeRequest.failOnRemoteDrift = request.failOnRemoteDrift;
        return JvmAsyncBridge.fromFuture(manager.deployTwin(instance, nativeRequest, notification)).thenApply(result ->
                new DeployResult(deployment(result.deployment()), result.deployedChanges().stream().map(this::fileChange).toList()));
    }

    @Override
    public Async<Deployment> rollback(Object source, String workspaceId, String deploymentId, Notification notification) {
        Instance instance = instance(source);
        return instance == null || manager == null ? unavailable("Development Rollback Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.rollbackDeployment(instance, workspaceId, deploymentId, notification)).thenApply(this::deployment);
    }

    @Override
    public Async<Void> disable(Object source, boolean deleteWorkspace) {
        Instance instance = instance(source);
        return instance == null || manager == null ? unavailable("Development Workspace Removal Is Unavailable")
                : JvmAsyncBridge.fromFuture(manager.disableTwinForSource(instance, deleteWorkspace));
    }

    @Override
    public boolean isManagedIgnorePath(String path) {
        return ServerTwinManager.classifyManagedIgnorePath(path) != null;
    }

    @Override
    public boolean isRuntimeStatePath(String path) {
        return ServerTwinManager.isGitRuntimeStatePath(path);
    }

    @Override
    public boolean includesRuntimeStateInGit(Workspace workspace) {
        if (manager == null || workspace == null) return false;
        return ServerTwinManager.includesRuntimeStateInGit(manager.getTwin(workspace.id));
    }

    private Instance instance(Object value) {
        return value instanceof Instance instance ? instance : null;
    }

    private Workspace workspace(ServerTwinManager.ServerTwin twin) {
        if (twin == null) return null;
        Workspace result = new Workspace();
        result.id = safe(twin.id);
        result.name = safe(twin.name);
        result.sourceInstanceId = safe(twin.sourceInstanceId);
        result.sourcePath = safe(twin.sourcePath);
        result.workspacePath = safe(twin.workspacePath);
        result.twinInstanceId = safe(twin.twinInstanceId);
        result.currentState = safe(twin.currentState);
        result.gitEnabled = twin.gitEnabled;
        result.scope = scope(twin.scope);
        result.ignorePaths = twin.ignorePaths == null ? List.of() : List.copyOf(twin.ignorePaths);
        result.deployments = twin.deployments == null ? List.of() : twin.deployments.stream().map(this::deployment).toList();
        return result;
    }

    private Inspection inspection(ServerTwinManager.TwinInspection inspection) {
        if (inspection == null) return null;
        return new Inspection(workspace(inspection.twin()), inspection.visibleChanges().stream().map(this::fileChange).toList(),
                inspection.ignoredChanges().stream().map(this::ignoredChange).toList(),
                inspection.remoteDrift().stream().map(drift -> new RemoteDrift(drift.relativePath(), fileChangeType(drift.type()))).toList(),
                new GitSummary(inspection.gitSummary().installed(), inspection.gitSummary().repository(), inspection.gitSummary().branch(),
                        inspection.gitSummary().upstream(), inspection.gitSummary().ahead(), inspection.gitSummary().behind(), inspection.gitSummary().changedFiles()));
    }

    private ChangePreview changePreview(ServerTwinManager.ChangePreview preview) {
        return new ChangePreview(preview.relativePath(), fileChangeType(preview.type()), preview.text(), preview.baselineSize(), preview.workspaceSize(), preview.diff());
    }

    private FileChange fileChange(ServerTwinManager.FileChange change) {
        return new FileChange(change.relativePath(), fileChangeType(change.type()), change.directory(), change.baselineSize(), change.workspaceSize(), change.text());
    }

    private IgnoredChange ignoredChange(ServerTwinManager.IgnoredChange change) {
        return new IgnoredChange(change.relativePath(), fileChangeType(change.type()), change.reason(), change.directory());
    }

    private Deployment deployment(ServerTwinManager.DeploymentRecord deployment) {
        if (deployment == null) return null;
        return new Deployment(deployment.id, deployment.label, deploymentMode(deployment.mode), deployment.createdAt,
                deployment.deployedToSource, deployment.changes == null ? List.of() : deployment.changes.stream().map(this::fileChange).toList());
    }

    private FileChangeType fileChangeType(ServerTwinManager.FileChangeType type) {
        return switch (type == null ? ServerTwinManager.FileChangeType.MODIFIED : type) {
            case ADDED -> FileChangeType.ADDED;
            case MODIFIED -> FileChangeType.MODIFIED;
            case DELETED -> FileChangeType.DELETED;
        };
    }

    private DeploymentMode deploymentMode(ServerTwinManager.DeploymentMode mode) {
        return switch (mode == null ? ServerTwinManager.DeploymentMode.DIRECT : mode) {
            case DIRECT -> DeploymentMode.DIRECT;
            case STAGED -> DeploymentMode.STAGED;
            case RESTART_REQUIRED -> DeploymentMode.RESTART_REQUIRED;
        };
    }

    private ServerTwinManager.ScopeProfile scope(ScopeProfile value) {
        ServerTwinManager.ScopeProfile result = new ServerTwinManager.ScopeProfile();
        if (value == null) return result;
        result.fullInstance = value.fullInstance;
        result.configs = value.configs;
        result.plugins = value.plugins;
        result.mods = value.mods;
        result.dataPacks = value.dataPacks;
        result.scripts = value.scripts;
        result.playerData = value.playerData;
        result.stats = value.stats;
        result.advancements = value.advancements;
        result.world = value.world;
        result.includePaths = value.includePaths == null ? List.of() : List.copyOf(value.includePaths);
        result.excludePaths = value.excludePaths == null ? List.of() : List.copyOf(value.excludePaths);
        return result;
    }

    private ScopeProfile scope(ServerTwinManager.ScopeProfile value) {
        ScopeProfile result = ScopeProfile.defaults();
        if (value == null) return result;
        result.fullInstance = value.fullInstance;
        result.configs = value.configs;
        result.plugins = value.plugins;
        result.mods = value.mods;
        result.dataPacks = value.dataPacks;
        result.scripts = value.scripts;
        result.playerData = value.playerData;
        result.stats = value.stats;
        result.advancements = value.advancements;
        result.world = value.world;
        result.includePaths = value.includePaths == null ? List.of() : List.copyOf(value.includePaths);
        result.excludePaths = value.excludePaths == null ? List.of() : List.copyOf(value.excludePaths);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
