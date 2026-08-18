package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.session.TerminalSession;
import redxax.oxy.remotely.session.TerminalSessionLifecycle;
import restudio.rebase.api.unified.internal.StandardOutputStateParser;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;

public final class DesktopTerminalSessionLifecycle implements TerminalSessionLifecycle {
    @Override
    public void cleanup(TerminalSession session) {
        if (session == null) return;
        Object value = session.getInstance();
        if (value instanceof Instance instance) {
            instance.detachTerminalListener();
            if (session.getStandardParser() instanceof StandardOutputStateParser parser) {
                instance.removeLogListener(parser);
            }
            if (session.getStreamDataParser() != null) {
                instance.removeLogListener(session.getStreamDataParser());
            }
            ServerTerminal.shutdown(instance.getInstanceId());
        } else if (session.getLocalTerminalId() != null) {
            TerminalWidget.shutdownLocal(session.getLocalTerminalId());
        }
    }
}
