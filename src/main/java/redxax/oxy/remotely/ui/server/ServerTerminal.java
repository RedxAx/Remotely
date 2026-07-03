package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LocalServerControllerModels;
import restudio.rebase.localcontrol.LocalServerProcessDetector;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedFileSystemProvider;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.ui.widgets.TerminalWidget;
import redxax.oxy.remotely.packcontent.GlyphPreviewRenderer;
import redxax.oxy.remotely.packcontent.RemotelyPackContentIntegration;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.servers.ReProxyManager;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.IconMessage;
import restudio.rescreen.util.Notification;

import java.net.InetSocketAddress;
import java.net.Socket;
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
    private String lastLocalFailureNotice = "";
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
        Instance localInstance = getInstance();
        if (isLocalInstance(localInstance)) {
            Thread.ofVirtual().name("Remotely Local Connection Lost").start(() -> {
                boolean serverStillRunning = LocalServerProcessDetector.isRunning(localInstance);
                ScreenManager.getInstance().execute(() -> {
                    Instance inst = getInstance();
                    if (desiredPower == DesiredPower.STOPPED || explicitDisconnect) {
                        isReconnecting = false;
                        forceStoppedView = true;
                        explicitDisconnect = true;
                        stopProcessAsync();
                        return;
                    }
                    desiredPower = DesiredPower.RUNNING;
                    isReconnecting = false;
                    forceStoppedView = false;
                    explicitDisconnect = false;
                    if (inst != null && serverStillRunning) {
                        inst.setState(InstanceState.RUNNING);
                        stopProcessAsync();
                        return;
                    } else if (inst != null && LifecycleManager.isStartPending(inst)) {
                        desiredPower = DesiredPower.RUNNING;
                        inst.setState(InstanceState.STARTING);
                        explicitDisconnect = false;
                        forceStoppedView = false;
                        isReconnecting = false;
                        stopProcessAsync();
                        return;
                    } else if (inst != null) {
                        LifecycleManager.clear(inst);
                        inst.setState(InstanceState.CRASHED);
                    }
                    if (reason != null && !"Disconnected".equalsIgnoreCase(reason.trim())) {
                        notifyLocalFailure("Server Start Failed", reason);
                    }
                    stopProcessAsync();
                });
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
                if (state == InstanceState.STOPPED || state == InstanceState.STOPPING || state == InstanceState.CRASHED || state == InstanceState.INSTALLING) {
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
            if (newState == InstanceState.STARTING) {
                desiredPower = DesiredPower.RUNNING;
                explicitDisconnect = false;
                forceStoppedView = false;
            } else if (newState == InstanceState.RUNNING) {
                desiredPower = DesiredPower.RUNNING;
                lastStartRequestedMs = 0;
                explicitDisconnect = false;
                forceStoppedView = false;
            } else if (newState == InstanceState.CRASHED && inst.isLocalRestartOnCrash()) {
                desiredPower = DesiredPower.RUNNING;
                explicitDisconnect = false;
                forceStoppedView = false;
                isReconnecting = false;
            } else if (newState == InstanceState.STOPPING) {
                desiredPower = DesiredPower.STOPPED;
                explicitDisconnect = true;
                forceStoppedView = true;
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
            explicitDisconnect = false;
            forceStoppedView = false;
            isReconnecting = false;
        } else if (newState == InstanceState.STOPPING) {
            desiredPower = DesiredPower.STOPPED;
            explicitDisconnect = true;
            forceStoppedView = true;
            isReconnecting = false;
        } else if (newState == InstanceState.STOPPED || newState == InstanceState.CRASHED) {
            desiredPower = DesiredPower.STOPPED;
            explicitDisconnect = true;
            forceStoppedView = true;
            isReconnecting = false;
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
        } else if (!isTerminalReady() && !explicitDisconnect && !forceStoppedView && !isLocalInstance(getInstance())) {
            connectingMessage.setPosition(getX() + (getWidth() - connectingMessage.getWidth()) / 2, getY() + (getHeight() - connectingMessage.getHeight()) / 2);
            connectingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else {
            boolean isStopped = false;
            if (getInstance() != null) {
                InstanceState state = getInstance().getState();
                isStopped = (state == InstanceState.STOPPED);
            }
            boolean isStopping = getInstance() != null && getInstance().getState() == InstanceState.STOPPING;

            boolean hasContent = getHistoryLinesCount() > 0 || getCursorY() > 4;

            boolean stopViewRequested = desiredPower == DesiredPower.STOPPED && (explicitDisconnect || forceStoppedView);
            boolean showStoppedOverlay = !isStopping && (isStopped || stopViewRequested) && (!hasContent || forceStoppedView || explicitDisconnect);
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
                stopProcessAsync();
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
            public void afterDraw(TerminalTextDecorationOverlayContext context) {
                glyphPreviewRenderer.drawTerminalOverlay(context);
            }

            @Override
            public boolean mouseClicked(ReMouseEvent event) {
                return glyphPreviewRenderer.openHoveredAsset(event.x(), event.y(), event.nativeButton());
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

    private boolean isQuickServerRuntimeOpen(Instance inst) {
        return isQuickServer(inst) && ReProxyManager.isForwarded(inst) && isLocalPortOpen(inst.getPort());
    }

    private boolean isQuickServer(Instance inst) {
        return inst != null && "true".equalsIgnoreCase(inst.getSettings().getProperty("quickServer.enabled"));
    }

    private boolean isLocalPortOpen(int port) {
        if (port <= 0 || port > 65535) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 350);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
        if (isLocalInstance(inst)) return;

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
                    case "stopping" -> InstanceState.STOPPING;
                    case "offline" -> InstanceState.STOPPED;
                    default -> null;
                };

                long now2 = System.currentTimeMillis();
                boolean withinStartGrace = desiredPower == DesiredPower.RUNNING && lastStartRequestedMs > 0 && now2 - lastStartRequestedMs < START_GRACE_MS;
                boolean withinStopGrace = desiredPower == DesiredPower.STOPPED && lastStopRequestedMs > 0 && now2 - lastStopRequestedMs < STOP_GRACE_MS;

                if (withinStopGrace && (remoteState == InstanceState.RUNNING || remoteState == InstanceState.STARTING)) {
                    isReconnecting = false;
                    forceStoppedView = false;
                    explicitDisconnect = false;
                    return;
                }

                if (withinStartGrace && remoteState == InstanceState.STOPPED) {
                    remoteState = InstanceState.STARTING;
                }

                if (remoteState == null) return;

                if (remoteState == InstanceState.STOPPING) {
                    desiredPower = DesiredPower.STOPPED;
                    i.setState(InstanceState.STOPPING);
                    isReconnecting = false;
                    forceStoppedView = true;
                    explicitDisconnect = true;
                    return;
                }

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
            if (isQuickServerRuntimeOpen(inst)) {
                ScreenManager.getInstance().execute(this::applyQuickServerRuntimeOpen);
                return;
            }
            if (status == null || !status.knownSession) return;
            boolean serverStillRunning = shouldVerifyLocalProcess(status) && LocalServerProcessDetector.isRunning(inst);
            ScreenManager.getInstance().execute(() -> applyLocalControllerStatus(status, serverStillRunning));
        });
    }

    private void applyQuickServerRuntimeOpen() {
        Instance inst = getInstance();
        if (inst == null) return;
        desiredPower = DesiredPower.RUNNING;
        inst.setState(InstanceState.RUNNING);
        if (inst.getState() == InstanceState.RUNNING) {
            lastStartRequestedMs = 0;
            explicitDisconnect = false;
            forceStoppedView = false;
            isReconnecting = false;
        }
        attachLocalControllerIfNeeded();
    }

    private void applyLocalControllerStatus(restudio.rebase.localcontrol.LocalServerControllerModels.StatusResponse status, boolean serverStillRunning) {
        Instance inst = getInstance();
        if (inst == null || status == null) return;

        String state = status.state != null ? status.state.trim().toUpperCase(java.util.Locale.ROOT) : "";
        if (LifecycleManager.isStartPending(inst) && "CRASHED".equals(state)) {
            desiredPower = DesiredPower.RUNNING;
            inst.setState(InstanceState.STARTING);
            explicitDisconnect = false;
            forceStoppedView = false;
            isReconnecting = false;
            attachLocalControllerIfNeeded();
            return;
        }
        if (("CRASHED".equals(state) || "STOPPED".equals(state)) && serverStillRunning) {
            lastLocalFailureNotice = "";
            desiredPower = DesiredPower.RUNNING;
            inst.setState(InstanceState.RUNNING);
            explicitDisconnect = false;
            forceStoppedView = false;
            isReconnecting = false;
            return;
        }
        if ("CRASHED".equals(state) && isTransientControllerDisconnect(status)) {
            lastLocalFailureNotice = "";
            lastStopRequestedMs = 0;
            inst.setState(InstanceState.STOPPED);
            desiredPower = DesiredPower.STOPPED;
            explicitDisconnect = true;
            forceStoppedView = true;
            isReconnecting = false;
            if (isTerminalReady()) {
                stopProcessAsync();
            }
            return;
        }

        switch (state) {
            case "STARTING" -> {
                desiredPower = DesiredPower.RUNNING;
                inst.setState(InstanceState.STARTING);
                explicitDisconnect = false;
                forceStoppedView = false;
                attachLocalControllerIfNeeded();
            }
            case "RUNNING" -> {
                lastLocalFailureNotice = "";
                desiredPower = DesiredPower.RUNNING;
                inst.setState(InstanceState.RUNNING);
                if (inst.getState() == InstanceState.RUNNING) {
                    lastStartRequestedMs = 0;
                    explicitDisconnect = false;
                    forceStoppedView = false;
                }
                attachLocalControllerIfNeeded();
            }
            case "STOPPING" -> {
                desiredPower = DesiredPower.STOPPED;
                inst.setState(InstanceState.STOPPING);
                explicitDisconnect = true;
                forceStoppedView = true;
                isReconnecting = false;
            }
            case "STOPPED" -> {
                lastLocalFailureNotice = "";
                lastStopRequestedMs = 0;
                inst.setState(InstanceState.STOPPED);
                QuickServerSyncManager.syncBackAfterStop(inst);
                if (inst.getState() == InstanceState.STOPPED) {
                    desiredPower = DesiredPower.STOPPED;
                    explicitDisconnect = true;
                    forceStoppedView = true;
                    isReconnecting = false;
                    if (isTerminalReady()) {
                        stopProcessAsync();
                    }
                } else {
                    desiredPower = DesiredPower.RUNNING;
                    explicitDisconnect = false;
                    forceStoppedView = false;
                }
            }
            case "CRASHED" -> {
                lastStopRequestedMs = 0;
                LifecycleManager.clear(inst);
                inst.setState(InstanceState.CRASHED);
                notifyLocalFailure("Server Crashed", status.lastError);
                desiredPower = inst.isLocalRestartOnCrash() ? DesiredPower.RUNNING : DesiredPower.STOPPED;
                explicitDisconnect = !inst.isLocalRestartOnCrash();
                forceStoppedView = !inst.isLocalRestartOnCrash();
                isReconnecting = false;
                if (isTerminalReady()) {
                    stopProcessAsync();
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
            broadcastStopFeedback("Stop Requested...");
            broadcastStopFeedback("Waiting For Shutdown...");
            Instance inst = getInstance();
            if (inst != null) {
                LifecycleManager.requestStop(inst);
                inst.setState(InstanceState.STOPPING);
                if (ReProxyManager.isForwarded(inst)) {
                    ReProxyManager.stop(inst.getPort(), null);
                }
                if (isLocalInstance(inst)) {
                    Thread.ofVirtual().name("Remotely Local Server Stop").start(() -> {
                        try {
                            LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.stop(inst);
                            QuickServerSyncManager.syncBackAfterStop(inst);
                            ScreenManager.getInstance().execute(() -> applyLocalControllerStatus(status, false));
                        } catch (Exception e) {
                            boolean serverStillRunning = LocalServerProcessDetector.isRunning(inst);
                            ScreenManager.getInstance().execute(() -> {
                                LocalServerControllerModels.StatusResponse status = e instanceof LocalServerControllerClient.ControllerRequestException controllerException ? controllerException.getStatus() : null;
                                boolean stoppedStatus = status != null && ("STOPPED".equalsIgnoreCase(status.state) || "CRASHED".equalsIgnoreCase(status.state));
                                boolean noKnownSession = e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("no running session");
                                if (serverStillRunning) {
                                    desiredPower = DesiredPower.RUNNING;
                                    forceStoppedView = false;
                                    explicitDisconnect = false;
                                    inst.setState(InstanceState.RUNNING);
                                } else if (noKnownSession || stoppedStatus) {
                                    LifecycleManager.clear(inst);
                                    inst.setState(status != null && "CRASHED".equalsIgnoreCase(status.state) ? InstanceState.CRASHED : InstanceState.STOPPED);
                                    forceStoppedView = true;
                                    explicitDisconnect = true;
                                    stopProcessAsync();
                                } else {
                                    desiredPower = DesiredPower.RUNNING;
                                    forceStoppedView = false;
                                    explicitDisconnect = false;
                                    if (inst != null) {
                                        inst.setState(InstanceState.RUNNING);
                                    }
                                }
                                notifyLocalFailure("Server Stop Failed", e.getMessage());
                            });
                        }
                    });
                }
            }
        });
    }

    void stopProcessAsync() {
        Thread.ofVirtual().name("Remotely Terminal Close").start(() -> super.stopProcess());
    }

    private boolean shouldVerifyLocalProcess(LocalServerControllerModels.StatusResponse status) {
        if (status == null || status.state == null) {
            return false;
        }
        return "CRASHED".equalsIgnoreCase(status.state) || "STOPPED".equalsIgnoreCase(status.state);
    }

    private boolean isTransientControllerDisconnect(LocalServerControllerModels.StatusResponse status) {
        if (status == null || status.lastError == null) {
            return false;
        }
        String error = status.lastError.toLowerCase(java.util.Locale.ROOT);
        return error.contains("connection reset") || error.contains("unexpected end of file") || error.contains("read timed out");
    }

    private void notifyLocalFailure(String title, String message) {
        String detail = message == null || message.isBlank() ? "No controller details were provided." : message;
        String key = title + "|" + detail;
        if (key.equals(lastLocalFailureNotice)) {
            return;
        }
        lastLocalFailureNotice = key;
        new Notification(title, detail, Notification.Type.ERROR);
        broadcastStopFeedback(title + ": " + detail);
    }

    private void broadcastStopFeedback(String line) {
        getTerminal().writeString(line);
        Instance inst = getInstance();
        if (inst != null) {
            inst.getLogger().addLog(line);
        }
    }
}
