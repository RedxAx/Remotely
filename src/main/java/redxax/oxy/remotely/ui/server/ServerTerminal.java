package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedFileSystemProvider;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.ui.widgets.TerminalWidget;
import redxax.oxy.remotely.packcontent.GlyphPreviewRenderer;
import redxax.oxy.remotely.packcontent.RemotelyPackContentIntegration;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.IconMessage;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerTerminal extends TerminalWidget {
    private final IconMessage stoppedMessage;
    private final IconMessage connectingMessage;
    private final IconMessage installingMessage;
    private final IconMessage reconnectingMessage;
    private static final Pattern PROGRESS_TAG_PATTERN = Pattern.compile("\\[Progress:(\\d{1,3})]\\s*(.*)");

    private boolean isReconnecting = false;
    private volatile boolean explicitDisconnect = false;
    private boolean forceStoppedView = false;
    private String reconnectReason = "";
    private int reconnectCountdown = 3;
    private int reconnectDelaySeconds = 3;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 15;
    private long lastTick = 0;
    private long lastStatusPollMs = 0;
    private long lastStartRequestedMs = 0;
    private long lastStopRequestedMs = 0;
    private long lastConnectAttemptMs = 0;
    private static final long START_GRACE_MS = 90_000;
    private static final long STOP_GRACE_MS = 10_000;
    private static final long CONNECT_ATTEMPT_COOLDOWN_MS = 3_000;
    private static final long STATUS_POLL_MS = 2000;

    private enum DesiredPower {
        UNKNOWN, RUNNING, STOPPED
    }

    private volatile DesiredPower desiredPower = DesiredPower.UNKNOWN;

    private final Consumer<InstanceState> stateListener;
    private final Consumer<String> logListener;
    private GlyphPreviewRenderer glyphPreviewRenderer;

    public ServerTerminal(int x, int y, int width, int height, Instance instance, ExecutionProvider executionProvider) {
        super(x, y, width, height, instance, executionProvider);
        setCursorHoverReactive(false);
        this.stoppedMessage = new IconMessage(0, 0, 64, 64, "Ready When You Are", "zz.png");
        this.connectingMessage = new IconMessage(0, 0, 64, 64, "Connecting...", "reverse.png");
        this.installingMessage = new IconMessage(0, 0, 64, 64, "Installing...", "remotely.png");
        this.reconnectingMessage = new IconMessage(0, 0, 64, 64, "Connection Lost\nReconnecting...", "reverse.png");

        this.logListener = this::onLogLine;
        this.stateListener = this::onStateChange;

        if (getInstance() != null) {
            getInstance().getLogger().addLogListener(logListener);
            getInstance().addStateListener(stateListener);
        }

        this.addOutputListener(this::onTerminalOutput);
        this.setOnConnectionLost(this::handleConnectionLost);
        installGlyphPreview();

        Instance inst = getInstance();
        if (inst != null) {
            if (isLocalInstance(inst)) {
                setForceDirectLaunch(true);
            }
            InstanceState state = inst.getState();
            if (state == InstanceState.STARTING || state == InstanceState.RUNNING) {
                ScreenManager.getInstance().execute(() -> {
                    forceStoppedView = false;
                    explicitDisconnect = false;
                    if (!isTerminalReady() && !isReconnecting) {
                        if (isLocalInstance(inst)) {
                            start();
                        } else {
                            startServerProcess();
                        }
                    }
                });
            }
        }
    }

    public static ServerTerminal getOrCreate(Instance instance, ExecutionProvider provider, int x, int y, int width, int height) {
        if (instance == null) {
            return new ServerTerminal(x, y, width, height, null, provider);
        }

        TerminalWidget cached = getCached(instance.getInstanceId());
        if (cached instanceof ServerTerminal) {
            ServerTerminal serverTerminal = (ServerTerminal) cached;
            if (serverTerminal.isLocalInstance(instance)) {
                serverTerminal.setForceDirectLaunch(true);
            }
            return serverTerminal;
        } else if (cached != null) {
            shutdown(instance.getInstanceId());
        }

        ServerTerminal widget = new ServerTerminal(x, y, width, height, instance, provider);
        putCached(instance.getInstanceId(), widget);
        return widget;
    }

    private void handleConnectionLost(String reason) {
        if (getInstance() != null && getInstance().getBackend() instanceof LocalBackend) {
            ScreenManager.getInstance().execute(() -> {
                if (desiredPower == DesiredPower.STOPPED || explicitDisconnect) {
                    isReconnecting = false;
                    forceStoppedView = true;
                    explicitDisconnect = true;
                    stopProcess();
                    return;
                }
                desiredPower = DesiredPower.RUNNING;
                isReconnecting = false;
                forceStoppedView = false;
                explicitDisconnect = false;
                stopProcess();
            });
            return;
        }

        ScreenManager.getInstance().execute(() -> {
            Instance inst = getInstance();
            if (desiredPower == DesiredPower.STOPPED) {
                isReconnecting = false;
                forceStoppedView = true;
                explicitDisconnect = true;
                stopProcess();
                return;
            }
            if (explicitDisconnect) {
                explicitDisconnect = false;
                isReconnecting = false;
                forceStoppedView = true;
                stopProcess();
                return;
            }

            if (inst != null) {
                InstanceState state = inst.getState();
                if (state == InstanceState.STOPPED || state == InstanceState.CRASHED || state == InstanceState.INSTALLING) {
                    isReconnecting = false;
                    forceStoppedView = true;
                    stopProcess();
                    return;
                }
            }

            scheduleReconnectAttempt((reason != null && !reason.isBlank()) ? reason : "Disconnected");
        });
    }

    private void onStateChange(InstanceState newState) {
        Instance inst = getInstance();
        if (isLocalInstance(inst)) {
            if (newState == InstanceState.STARTING || newState == InstanceState.RUNNING) {
                desiredPower = DesiredPower.RUNNING;
                lastStartRequestedMs = 0;
                explicitDisconnect = false;
                forceStoppedView = false;
            } else if (newState == InstanceState.CRASHED && inst.isLocalRestartOnCrash()) {
                desiredPower = DesiredPower.RUNNING;
                explicitDisconnect = false;
                forceStoppedView = false;
                isReconnecting = false;
            } else if (newState == InstanceState.STOPPED || newState == InstanceState.CRASHED) {
                desiredPower = DesiredPower.STOPPED;
                explicitDisconnect = true;
                forceStoppedView = true;
                isReconnecting = false;
            }
            return;
        }
        if (newState == InstanceState.RUNNING) {
            desiredPower = DesiredPower.RUNNING;
        } else if (newState == InstanceState.STOPPED || newState == InstanceState.CRASHED) {
            desiredPower = DesiredPower.STOPPED;
        }
    }

    @Override
    public void tick() {
        super.tick();
        pollLocalStatusIfNeeded();
        pollReStudioStatusIfNeeded();
        if (isReconnecting) {
            long now = System.currentTimeMillis();
            if (now - lastTick >= 1000) {
                lastTick = now;
                reconnectCountdown--;
                if (reconnectCountdown <= 0) {
                    isReconnecting = false;
                    stopProcess();
                    startServerProcess();
                } else {
                    reconnectingMessage.setMessage("Connection Lost: " + reconnectReason + "\nReconnecting in " + reconnectCountdown + "s");
                }
            }
        }
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        if (getInstance() != null && getInstance().getState() == InstanceState.INSTALLING) {
            installingMessage.setPosition(getX() + (getWidth() - installingMessage.getWidth()) / 2, getY() + (getHeight() - installingMessage.getHeight()) / 2 - 20);
            installingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else if (isReconnecting) {
            reconnectingMessage.setPosition(getX() + (getWidth() - reconnectingMessage.getWidth()) / 2, getY() + (getHeight() - reconnectingMessage.getHeight()) / 2);
            reconnectingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else if (!isTerminalReady() && !explicitDisconnect && !forceStoppedView && !(getInstance() != null && getInstance().getBackend() instanceof LocalBackend)) {
            connectingMessage.setPosition(getX() + (getWidth() - connectingMessage.getWidth()) / 2, getY() + (getHeight() - connectingMessage.getHeight()) / 2);
            connectingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else {
            boolean isStopped = false;
            if (getInstance() != null) {
                InstanceState state = getInstance().getState();
                isStopped = (state == InstanceState.STOPPED);
            }

            boolean hasContent = getHistoryLinesCount() > 0 || getCursorY() > 4;

            boolean showStoppedOverlay = isStopped && (!hasContent || forceStoppedView);
            if (showStoppedOverlay && !isReStudioInstance()) {
                showStoppedOverlay = true;
            } else if (showStoppedOverlay) {
                showStoppedOverlay = desiredPower == DesiredPower.STOPPED || explicitDisconnect;
            }

            if (showStoppedOverlay) {
                stoppedMessage.setPosition(getX() + (getWidth() - stoppedMessage.getWidth()) / 2, getY() + (getHeight() - stoppedMessage.getHeight()) / 2);
                stoppedMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
            } else {
                super.drawContent(ctx, mouseX, mouseY);
            }
        }
    }

    private void onTerminalOutput(String msg) {
        if (msg == null) return;
        if (msg.contains("Server is offline.")) {
            if (desiredPower == DesiredPower.STOPPED) {
                explicitDisconnect = true;
                isReconnecting = false;
                forceStoppedView = true;
                stopProcess();
                return;
            }
            handleConnectionLost("Server is offline");
        }
    }

    private void installGlyphPreview() {
        Instance inst = getInstance();
        if (inst == null || inst.getPath() == null) {
            return;
        }
        Path root = Path.of(inst.getPath());
        var provider = new UnifiedFileSystemProvider(InstanceApi.of(inst).files());
        RemotelyPackContentIntegration.refresh(inst, provider, root);
        glyphPreviewRenderer = new GlyphPreviewRenderer(inst, provider, root, null, null);
        setTextDecoration(new restudio.rebase.ui.widgets.TerminalTextDecoration() {
            @Override
            public boolean draw(TerminalTextDecorationContext context) {
                return glyphPreviewRenderer.replaceTerminal(context, RemotelyPackContentIntegration.mode());
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return glyphPreviewRenderer.openHoveredAsset(mouseX, mouseY, button);
            }
        });
    }

    private void onLogLine(String msg) {
        if (msg == null) return;
        Matcher m = PROGRESS_TAG_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                int pct = Integer.parseInt(m.group(1));
                String status = m.group(2).trim();
                String lines = status + "\n" + (pct + "%");
                installingMessage.setMessage(lines);
            } catch (Exception ignored) {}
        }
    }

    private boolean isReStudioInstance() {
        Instance inst = getInstance();
        BackendConfig cfg = inst != null ? inst.getBackendConfig() : null;
        return cfg != null && cfg.type != null && cfg.type.equalsIgnoreCase("RESTUDIO");
    }

    private boolean isLocalInstance(Instance inst) {
        return inst != null && (inst.getBackendConfig() == null || inst.getBackendConfig().type == null || "LOCAL".equalsIgnoreCase(inst.getBackendConfig().type));
    }

    private String getReStudioServerId() {
        Instance inst = getInstance();
        BackendConfig cfg = inst != null ? inst.getBackendConfig() : null;
        if (cfg == null || cfg.credentials == null) return null;
        return cfg.credentials.get("identifier");
    }

    private void scheduleReconnectAttempt(String reason) {
        if (desiredPower == DesiredPower.STOPPED) return;
        if (isReconnecting) return;
        isReconnecting = true;
        reconnectReason = reason;
        reconnectCountdown = Math.max(1, reconnectDelaySeconds);
        reconnectDelaySeconds = Math.min(MAX_RECONNECT_DELAY_SECONDS, reconnectDelaySeconds * 2);
        lastTick = System.currentTimeMillis();
        reconnectingMessage.setMessage("Connection Lost: " + reconnectReason + "\nReconnecting in " + reconnectCountdown + "s");
    }

    private void pollReStudioStatusIfNeeded() {
        Instance inst = getInstance();
        if (inst == null) return;
        if (!isReStudioInstance()) return;
        if (inst.getBackend() instanceof LocalBackend) return;

        long now = System.currentTimeMillis();
        if (now - lastStatusPollMs < STATUS_POLL_MS) return;
        lastStatusPollMs = now;

        String serverId = getReStudioServerId();
        if (serverId == null || serverId.isBlank()) return;

        ReStudio.getInstance().getApi().getServerStatus(serverId).whenComplete((status, ex) -> {
            if (ex != null || status == null) return;
            ScreenManager.getInstance().execute(() -> {
                Instance i = getInstance();
                if (i == null) return;

                if (status.suspended) {
                    desiredPower = DesiredPower.STOPPED;
                    i.setState(InstanceState.STOPPED);
                    isReconnecting = false;
                    forceStoppedView = true;
                    explicitDisconnect = true;
                    stopProcess();
                    return;
                }

                if (status.installing) {
                    desiredPower = DesiredPower.RUNNING;
                    i.setState(InstanceState.INSTALLING);
                    isReconnecting = false;
                    forceStoppedView = false;
                    explicitDisconnect = false;
                    stopProcess();
                    return;
                }

                String cs = status.currentState != null ? status.currentState.trim().toLowerCase() : "";
                InstanceState remoteState = switch (cs) {
                    case "running" -> InstanceState.RUNNING;
                    case "starting" -> InstanceState.STARTING;
                    case "offline" -> InstanceState.STOPPED;
                    default -> null;
                };

                long now2 = System.currentTimeMillis();
                boolean withinStartGrace = desiredPower == DesiredPower.RUNNING && lastStartRequestedMs > 0 && now2 - lastStartRequestedMs < START_GRACE_MS;
                boolean withinStopGrace = desiredPower == DesiredPower.STOPPED && lastStopRequestedMs > 0 && now2 - lastStopRequestedMs < STOP_GRACE_MS;

                if (withinStopGrace && (remoteState == InstanceState.RUNNING || remoteState == InstanceState.STARTING)) {
                    i.setState(InstanceState.STOPPED);
                    isReconnecting = false;
                    forceStoppedView = true;
                    explicitDisconnect = true;
                    stopProcess();
                    return;
                }

                if (withinStartGrace && remoteState == InstanceState.STOPPED) {
                    remoteState = InstanceState.STARTING;
                }

                if (remoteState == null) return;

                if (remoteState == InstanceState.STOPPED) {
                    desiredPower = DesiredPower.STOPPED;
                    lastStartRequestedMs = 0;
                    i.setState(InstanceState.STOPPED);
                    isReconnecting = false;
                    forceStoppedView = true;
                    explicitDisconnect = true;
                    stopProcess();
                    return;
                }

                desiredPower = DesiredPower.RUNNING;
                lastStartRequestedMs = 0;
                lastStopRequestedMs = 0;
                i.setState(remoteState);
                forceStoppedView = false;
                explicitDisconnect = false;
                if (!isTerminalReady() && !isReconnecting && now2 - lastConnectAttemptMs >= CONNECT_ATTEMPT_COOLDOWN_MS) {
                    lastConnectAttemptMs = now2;
                    startServerProcess();
                }
            });
        });
    }

    private void pollLocalStatusIfNeeded() {
        Instance inst = getInstance();
        if (!isLocalInstance(inst)) return;

        long now = System.currentTimeMillis();
        if (now - lastStatusPollMs < STATUS_POLL_MS) return;
        lastStatusPollMs = now;

        Thread.ofVirtual().name("Remotely Local Status Poll").start(() -> {
            var status = LocalServerControllerClient.status(inst);
            if (status == null || !status.knownSession) return;
            ScreenManager.getInstance().execute(() -> applyLocalControllerStatus(status));
        });
    }

    private void applyLocalControllerStatus(restudio.rebase.localcontrol.LocalServerControllerModels.StatusResponse status) {
        Instance inst = getInstance();
        if (inst == null || status == null) return;

        String state = status.state != null ? status.state.trim().toUpperCase(java.util.Locale.ROOT) : "";

        switch (state) {
            case "STARTING" -> {
                desiredPower = DesiredPower.RUNNING;
                inst.setState(InstanceState.STARTING);
                explicitDisconnect = false;
                forceStoppedView = false;
                attachLocalControllerIfNeeded();
            }
            case "RUNNING" -> {
                desiredPower = DesiredPower.RUNNING;
                inst.setState(InstanceState.RUNNING);
                if (inst.getState() == InstanceState.RUNNING) {
                    lastStartRequestedMs = 0;
                    explicitDisconnect = false;
                    forceStoppedView = false;
                }
                attachLocalControllerIfNeeded();
            }
            case "STOPPING", "STOPPED" -> {
                lastStopRequestedMs = 0;
                inst.setState(InstanceState.STOPPED);
                if (inst.getState() == InstanceState.STOPPED) {
                    desiredPower = DesiredPower.STOPPED;
                    explicitDisconnect = true;
                    forceStoppedView = true;
                    isReconnecting = false;
                    if (isTerminalReady()) {
                        stopProcess();
                    }
                } else {
                    desiredPower = DesiredPower.RUNNING;
                    explicitDisconnect = false;
                    forceStoppedView = false;
                }
            }
            case "CRASHED" -> {
                lastStopRequestedMs = 0;
                inst.setState(InstanceState.CRASHED);
                desiredPower = inst.isLocalRestartOnCrash() ? DesiredPower.RUNNING : DesiredPower.STOPPED;
                explicitDisconnect = !inst.isLocalRestartOnCrash();
                forceStoppedView = !inst.isLocalRestartOnCrash();
                isReconnecting = false;
                if (isTerminalReady()) {
                    stopProcess();
                }
            }
        }
    }

    private void attachLocalControllerIfNeeded() {
        long now = System.currentTimeMillis();
        if (isTerminalReady() || isReconnecting || now - lastConnectAttemptMs < CONNECT_ATTEMPT_COOLDOWN_MS) {
            return;
        }
        lastConnectAttemptMs = now;
        start();
    }

    public void notifyStartRequested() {
        ScreenManager.getInstance().execute(() -> {
            desiredPower = DesiredPower.RUNNING;
            lastStartRequestedMs = System.currentTimeMillis();
            lastStopRequestedMs = 0;
            forceStoppedView = false;
            explicitDisconnect = false;
            clearLog();
            Instance inst = getInstance();
            if (inst != null) {
                LifecycleManager.requestStart(inst);
                inst.setState(InstanceState.STARTING);
            }
        });
    }

    public void notifyStopRequested() {
        ScreenManager.getInstance().execute(() -> {
            desiredPower = DesiredPower.STOPPED;
            lastStopRequestedMs = System.currentTimeMillis();
            lastStartRequestedMs = 0;
            isReconnecting = false;
            explicitDisconnect = true;
            forceStoppedView = true;
            stopProcess();
            Instance inst = getInstance();
            if (inst != null) {
                LifecycleManager.requestStop(inst);
                if (inst.getBackend() instanceof LocalBackend) {
                    Thread.ofVirtual().name("Remotely Local Server Stop").start(() -> LocalServerControllerClient.stop(inst));
                }
                inst.setState(InstanceState.STOPPED);
            }
        });
    }
}
