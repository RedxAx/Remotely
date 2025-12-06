package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.IconMessage;

public class ServerTerminal extends TerminalWidget {
    private final IconMessage stoppedMessage;
    private final IconMessage connectingMessage;

    public ServerTerminal(int x, int y, int width, int height, Instance instance, ExecutionProvider executionProvider) {
        super(x, y, width, height, instance, executionProvider);
        this.stoppedMessage = new IconMessage(0, 0, 64, 64, "Ready When You Are", "zz.png");
        this.connectingMessage = new IconMessage(0, 0, 64, 64, "Connecting...", "reverse.png");
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
        if (!isTerminalReady() && !(getInstance().getBackend() instanceof LocalBackend)) {
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
}
