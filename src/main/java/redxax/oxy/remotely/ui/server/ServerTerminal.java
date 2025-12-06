package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.IconMessage;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerTerminal extends TerminalWidget {
    private final IconMessage stoppedMessage;
    private final IconMessage connectingMessage;
    private final IconMessage installingMessage;
    private static final Pattern PROGRESS_TAG_PATTERN = Pattern.compile("\\[Progress:(\\d{1,3})]\\s*(.*)");


    public ServerTerminal(int x, int y, int width, int height, Instance instance, ExecutionProvider executionProvider) {
        super(x, y, width, height, instance, executionProvider);
        this.stoppedMessage = new IconMessage(0, 0, 64, 64, "Ready When You Are", "zz.png");
        this.connectingMessage = new IconMessage(0, 0, 64, 64, "Connecting...", "reverse.png");
        this.installingMessage = new IconMessage(0, 0, 64, 64, "Installing...\nPreparing", "remotely.png");
        Consumer<String> logListener = this::onLogLine;
        if (getInstance() != null) getInstance().getLogger().addLogListener(logListener);
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

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        if (getInstance() != null && getInstance().getState() == InstanceState.INSTALLING) {
            installingMessage.setPosition(getX() + (getWidth() - installingMessage.getWidth()) / 2, getY() + (getHeight() - installingMessage.getHeight()) / 2 - 20);
            installingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else if (!isTerminalReady() && !(getInstance().getBackend() instanceof LocalBackend)) {
            connectingMessage.setPosition(getX() + (getWidth() - connectingMessage.getWidth()) / 2, getY() + (getHeight() - connectingMessage.getHeight()) / 2);
            connectingMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
        } else {
            boolean isStopped = false;
            if (getInstance() != null) {
                InstanceState state = getInstance().getState();
                isStopped = (state == InstanceState.STOPPED);
            }

            boolean hasContent = getHistoryLinesCount() > 0 || getCursorY() > 4;

            if (isStopped && !hasContent) {
                stoppedMessage.setPosition(getX() + (getWidth() - stoppedMessage.getWidth()) / 2, getY() + (getHeight() - stoppedMessage.getHeight()) / 2);
                stoppedMessage.render(ctx, mouseX, mouseY, Config.deltaTime);
            } else {
                super.drawContent(ctx, mouseX, mouseY);
            }
        }
    }

    private void onLogLine(String msg) {
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
