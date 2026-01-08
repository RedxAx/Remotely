package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.IconMessage;

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
    // explicitDisconnect indicates a "soft" disconnect was detected (e.g. "Server is offline"),
    // used to prevent the "Connecting..." flicker before the connection loss handler runs.
    private volatile boolean explicitDisconnect = false;
    private boolean forceStoppedView = false;
    private String reconnectReason = "";
    private int reconnectCountdown = 3;
    private long lastTick = 0;

    private final Consumer<InstanceState> stateListener;
    private final Consumer<String> logListener;

    public ServerTerminal(int x, int y, int width, int height, Instance instance, ExecutionProvider executionProvider) {
        super(x, y, width, height, instance, executionProvider);
        this.stoppedMessage = new IconMessage(0, 0, 64, 64, "Ready When You Are", "zz.png");
        this.connectingMessage = new IconMessage(0, 0, 64, 64, "Connecting...", "reverse.png");
        this.installingMessage = new IconMessage(0, 0, 64, 64, "Installing...\nPreparing", "remotely.png");
        this.reconnectingMessage = new IconMessage(0, 0, 64, 64, "Connection Lost\nReconnecting...", "reverse.png");

        this.logListener = this::onLogLine;
        this.stateListener = this::onStateChange;

        if (getInstance() != null) {
            getInstance().getLogger().addLogListener(logListener);
            getInstance().addStateListener(stateListener);
        }

        this.addOutputListener(this::onTerminalOutput);
        this.setOnConnectionLost(this::handleConnectionLost);
    }

    public static ServerTerminal getOrCreate(Instance instance, ExecutionProvider provider, int x, int y, int width, int height) {
        if (instance == null) {
            return new ServerTerminal(x, y, width, height, null, provider);
        }

        TerminalWidget cached = getCached(instance.getInstanceId());
        if (cached instanceof ServerTerminal) {
            return (ServerTerminal) cached;
        } else if (cached != null) {
            shutdown(instance.getInstanceId());
        }

        ServerTerminal widget = new ServerTerminal(x, y, width, height, instance, provider);
        putCached(instance.getInstanceId(), widget);
        return widget;
    }

    private void handleConnectionLost(String reason) {
        if (getInstance() != null && getInstance().getBackend() instanceof LocalBackend) {
            return;
        }

        ScreenManager.getInstance().execute(() -> {
            if (explicitDisconnect) {
                explicitDisconnect = false;
                isReconnecting = false;
                forceStoppedView = true;
                if (getInstance() != null) {
                    getInstance().setState(InstanceState.STOPPED);
                }
                stopProcess();
                return;
            }

            if (!isReconnecting) {
                isReconnecting = true;
                reconnectReason = reason != null ? reason : "Unknown error";
                reconnectCountdown = 3;
                lastTick = System.currentTimeMillis();
            }
        });
    }

    private void onStateChange(InstanceState newState) {
        if (newState == InstanceState.STARTING || newState == InstanceState.RUNNING) {
            ScreenManager.getInstance().execute(() -> {
                forceStoppedView = false;
                explicitDisconnect = false;
                if (!isTerminalReady() && !isReconnecting) {
                    startServerProcess();
                }
            });
        }
    }

    @Override
    public void tick() {
        super.tick();
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
            // Only show "Connecting..." if we are not in an explicit disconnect state and not forced to stopped view.
            connectingMessage.setPosition(getX() + (getWidth() - connectingMessage.getWidth()) / 2, getY() + (getHeight() - connectingMessage.getHeight()) / 2);
            connectingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else {
            boolean isStopped = false;
            if (getInstance() != null) {
                InstanceState state = getInstance().getState();
                isStopped = (state == InstanceState.STOPPED);
            }

            boolean hasContent = getHistoryLinesCount() > 0 || getCursorY() > 4;

            if (isStopped && (!hasContent || forceStoppedView)) {
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
            explicitDisconnect = true;
        }
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
}
