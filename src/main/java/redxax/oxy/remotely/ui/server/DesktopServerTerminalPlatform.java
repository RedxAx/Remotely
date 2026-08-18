package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.packcontent.DesktopGlyphPreviewAccess;
import redxax.oxy.remotely.packcontent.GlyphPreviewAccess;
import redxax.oxy.remotely.packcontent.GlyphPreviewRenderer;
import redxax.oxy.remotely.packcontent.RemotelyPackContentIntegration;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.servers.ReProxyManager;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedFileSystemProvider;
import restudio.rebase.backend.feature.ResourceUsageFeature;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceOperation;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LocalServerControllerModels;
import restudio.rebase.ui.widgets.TerminalTextDecoration;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DesktopServerTerminalPlatform implements ServerTerminalPlatform {
    private static final long STATUS_POLL_MS = 2_000;
    private static final long RESOURCE_POLL_MS = 7_500;

    private final Instance instance;
    private final AtomicBoolean localServerStartIssued = new AtomicBoolean();
    private Consumer<InstanceState> stateListener;
    private Consumer<InstanceOperation> operationListener;
    private GlyphPreviewRenderer glyphPreviewRenderer;
    private long lastStatusPoll;
    private long lastResourcePoll;
    private long lastStartRequested;
    private String lastFailureNotice = "";

    public DesktopServerTerminalPlatform(Instance instance) {
        this.instance = instance;
    }

    @Override
    public void configure(ServerTerminal terminal) {
        if (instance == null || instance.getPath() == null) return;
        Path root = Path.of(instance.getPath());
        UnifiedFileSystemProvider provider = new UnifiedFileSystemProvider(InstanceApi.of(instance).files());
        RemotelyPackContentIntegration.refresh(instance, provider, root);
        GlyphPreviewAccess access = new DesktopGlyphPreviewAccess(instance, provider, root);
        glyphPreviewRenderer = new GlyphPreviewRenderer(access, null, null);
        terminal.setTextDecoration(new TerminalTextDecoration() {
            @Override
            public boolean draw(TerminalTextDecoration.TerminalTextDecorationContext context) {
                return glyphPreviewRenderer.replaceTerminal(context, RemotelyPackContentIntegration.mode());
            }

            @Override
            public void afterDraw(TerminalTextDecoration.TerminalTextDecorationOverlayContext context) {
                glyphPreviewRenderer.drawTerminalOverlay(context);
            }

            @Override
            public boolean mouseClicked(ReMouseEvent event) {
                return glyphPreviewRenderer.openHoveredAsset(event.x(), event.y(), event.nativeButton());
            }
        });
    }

    @Override
    public void attach(ServerTerminal terminal) {
        if (instance == null) return;
        stateListener = state -> applyState(terminal, state);
        operationListener = operation -> {
            boolean active = operation != null && operation.isActive()
                    && (operation.type() != InstanceOperation.Type.START || !terminal.isTerminalReady());
            String message = operation == null ? "Working..." : operation.hasProgress()
                    ? operation.message() + "\n" + operation.progress() + "%" : operation.message();
            terminal.setPlatformOperation(active, message);
        };
        instance.addStateListener(stateListener);
        instance.addOperationListener(operationListener);
        applyState(terminal, instance.getState());
        applyOperation(terminal, instance.getOperation());
    }

    @Override
    public void detach(ServerTerminal terminal) {
        if (instance != null) {
            if (stateListener != null) instance.removeStateListener(stateListener);
            if (operationListener != null) instance.removeOperationListener(operationListener);
        }
        terminal.setTextDecoration(null);
        stateListener = null;
        operationListener = null;
        glyphPreviewRenderer = null;
    }

    @Override
    public void tick(ServerTerminal terminal, long now) {
        if (instance == null) return;
        if (!isLocal()) {
            if (now - lastResourcePoll < RESOURCE_POLL_MS || instance.getBackend() == null) return;
            lastResourcePoll = now;
            if (instance.getBackendConfig() != null && PteroBackend.isPanelType(instance.getBackendConfig().type)) {
                instance.getBackend().getFeature(ResourceUsageFeature.class).ifPresent(feature -> feature.getResources().exceptionally(failure -> null));
            }
            return;
        }
        if (now - lastStatusPoll < STATUS_POLL_MS) return;
        lastStatusPoll = now;
        Instance target = instance;
        Thread.ofVirtual().name("Remotely Local Status Poll").start(() -> {
            LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(target);
            if (status == null || !status.ok || !status.knownSession) return;
            ScreenManager.getInstance().execute(() -> applyLocalStatus(terminal, status));
        });
    }

    @Override
    public void startRequested(ServerTerminal terminal) {
        localServerStartIssued.set(false);
        lastStartRequested = System.currentTimeMillis();
        if (instance == null) return;
        LifecycleManager.requestStart(instance);
        instance.setState(InstanceState.STARTING);
    }

    @Override
    public void stopRequested(ServerTerminal terminal) {
        localServerStartIssued.set(false);
        if (instance == null) return;
        InstanceState previousState = instance.getState();
        String stopOperationId = LifecycleManager.requestStop(instance);
        instance.setState(InstanceState.STOPPING);
        if (ReProxyManager.isForwarded(instance)) ReProxyManager.stop(instance.getPort(), null);
        if (!isLocal()) return;
        Thread.ofVirtual().name("Remotely Local Server Stop").start(() -> {
            try {
                LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.stop(instance);
                QuickServerSyncManager.syncBackAfterStop(instance);
                ScreenManager.getInstance().execute(() -> applyLocalStatus(terminal, status));
            } catch (Exception exception) {
                LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
                ScreenManager.getInstance().execute(() -> {
                    boolean stoppedStatus = status != null && ("STOPPED".equalsIgnoreCase(status.state)
                            || "CRASHED".equalsIgnoreCase(status.state));
                    boolean noKnownSession = exception.getMessage() != null
                            && exception.getMessage().toLowerCase(Locale.ROOT).contains("no running session");
                    boolean controllerUnavailable = status == null || !status.ok;
                    if (status != null && stoppedStatus) {
                        applyLocalStatus(terminal, status);
                        return;
                    }
                    if (isManagedServerAlive(status) || controllerUnavailable && !noKnownSession) {
                        LifecycleManager.restoreRunning(instance, stopOperationId, "Could Not Stop Instance");
                        InstanceState restoredState = previousState == InstanceState.STARTING ? InstanceState.STARTING : InstanceState.RUNNING;
                        instance.setState(restoredState);
                        terminal.acceptPlatformState(restoredState.name());
                        terminal.platformSetDesiredRunning(true);
                        notifyFailure(terminal, "Server Stop Failed", exception.getMessage());
                        return;
                    }
                    if (noKnownSession) {
                        LifecycleManager.complete(instance, stopOperationId, InstanceState.STOPPED);
                        instance.setState(InstanceState.STOPPED);
                        terminal.acceptPlatformState("stopped");
                        terminal.platformStopAndShowStopped();
                        return;
                    }
                    LifecycleManager.fail(instance, stopOperationId, InstanceState.CRASHED, "Server Stop Failed");
                    instance.setState(InstanceState.CRASHED);
                    terminal.acceptPlatformState("crashed");
                    terminal.platformSetDesiredRunning(false);
                    notifyFailure(terminal, "Server Stop Failed", exception.getMessage());
                });
            }
        });
    }

    @Override
    public boolean beforeStartServerProcess(ServerTerminal terminal) {
        return !isLocal() || localServerStartIssued.compareAndSet(false, true);
    }

    @Override
    public boolean replacesStatusPolling() {
        return isLocal();
    }

    @Override
    public void stopProcessAsync(ServerTerminal terminal) {
        Thread.ofVirtual().name("Remotely Terminal Close").start(terminal::platformStopProcess);
    }

    @Override
    public boolean isStaleLocalControllerStatus(Object value) {
        if (!(value instanceof LocalServerControllerModels.StatusResponse status)
                || lastStartRequested <= 0 || status.startTimeMs >= lastStartRequested) return false;
        String state = status.state == null ? "" : status.state.trim().toUpperCase(Locale.ROOT);
        return "STOPPING".equals(state) || "STOPPED".equals(state) || "CRASHED".equals(state);
    }

    @Override
    public boolean connectionLost(ServerTerminal terminal, String reason) {
        if (instance == null || !isLocal()) return false;
        Thread.ofVirtual().name("Remotely Local Connection Lost").start(() -> {
            LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
            if (status == null || !status.ok || !status.knownSession) return;
            ScreenManager.getInstance().execute(() -> applyLocalStatus(terminal, status));
        });
        return false;
    }

    private void applyState(ServerTerminal terminal, InstanceState state) {
        if (state == null) return;
        terminal.acceptPlatformState(state.name());
        if (state == InstanceState.RUNNING && !terminal.isTerminalReady()) {
            ScreenManager.getInstance().execute(() -> {
                if (!terminal.isTerminalReady()) {
                    terminal.start();
                }
            });
        }
        if (state == InstanceState.STARTING && !isLocal() && !terminal.isTerminalReady()) {
            ScreenManager.getInstance().execute(() -> {
                if (!terminal.isTerminalReady()) terminal.startServerProcess();
            });
        }
        if ((state == InstanceState.STOPPED || state == InstanceState.CRASHED) && !terminal.platformDesiredRunning()) {
            terminal.platformStopAndShowStopped();
        }
    }

    private void applyOperation(ServerTerminal terminal, InstanceOperation operation) {
        if (operationListener != null) operationListener.accept(operation);
    }

    private void applyLocalStatus(ServerTerminal terminal, LocalServerControllerModels.StatusResponse status) {
        if (status == null || !status.ok || isStaleLocalControllerStatus(status)) return;
        String state = status.state == null ? "" : status.state.trim().toUpperCase(Locale.ROOT);
        switch (state) {
            case "STARTING" -> terminal.acceptPlatformState("starting");
            case "RUNNING" -> {
                lastFailureNotice = "";
                terminal.acceptPlatformState("running");
                attachIfNeeded(terminal);
            }
            case "STOPPING" -> terminal.acceptPlatformState("stopping");
            case "STOPPED" -> {
                LifecycleManager.complete(instance, LifecycleManager.activeOperationId(instance), InstanceState.STOPPED);
                QuickServerSyncManager.syncBackAfterStop(instance);
                if (ReProxyManager.isForwarded(instance)) ReProxyManager.stopQuietly(instance.getPort(), null);
                terminal.acceptPlatformState("stopped");
                terminal.platformStopAndShowStopped();
            }
            case "CRASHED" -> {
                String detail = status.lastError == null || status.lastError.isBlank() ? "Server Crashed" : status.lastError;
                LifecycleManager.fail(instance, LifecycleManager.activeOperationId(instance), InstanceState.CRASHED, detail);
                notifyFailure(terminal, status.exitCode != null && status.exitCode == 0 ? "Server Stopped During Startup" : "Server Crashed", detail);
                boolean restart = instance.isLocalRestartOnCrash();
                terminal.acceptPlatformState(restart ? "starting" : "crashed");
                if (!restart) terminal.platformStopAndShowStopped();
            }
            default -> {
            }
        }
    }

    private void attachIfNeeded(ServerTerminal terminal) {
        if (!terminal.isTerminalReady()) terminal.start();
    }

    private void notifyFailure(ServerTerminal terminal, String title, String message) {
        String detail = message == null || message.isBlank() ? "No controller details were provided." : message;
        String key = title + "|" + detail;
        if (key.equals(lastFailureNotice)) return;
        lastFailureNotice = key;
        new Notification(title, detail, Notification.Type.ERROR);
        terminal.appendOutput(title + ": " + detail + System.lineSeparator());
    }

    private static boolean isManagedServerAlive(LocalServerControllerModels.StatusResponse status) {
        if (status == null || !status.ok || !status.knownSession) return false;
        boolean active = status.pid > 0 || status.wrapperPid > 0 || status.serverPid > 0
                || status.pids != null && status.pids.stream().anyMatch(value -> value != null && value > 0);
        if (!active) return false;
        String state = status.state == null ? "" : status.state.trim().toUpperCase(Locale.ROOT);
        return "STARTING".equals(state) || "RUNNING".equals(state) || "STOPPING".equals(state);
    }

    private boolean isLocal() {
        if (instance == null || instance.getBackendConfig() == null) return true;
        String type = instance.getBackendConfig().type;
        return type == null || type.isBlank() || "LOCAL".equalsIgnoreCase(type);
    }
}
