package redxax.oxy.remotely.session;

import restudio.rebase.ui.widgets.TerminalWidget;

public interface TerminalSessionLifecycle {
    void cleanup(TerminalSession session);

    static TerminalSessionLifecycle browser() {
        return session -> {
            if (session.getInstance() == null && session.getLocalTerminalId() != null) {
                TerminalWidget.shutdownLocal(session.getLocalTerminalId());
            }
        };
    }
}
