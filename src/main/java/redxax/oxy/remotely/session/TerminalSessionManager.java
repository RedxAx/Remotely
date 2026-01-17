package redxax.oxy.remotely.session;

import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerminalSessionManager {
    private final Map<Object, TerminalSession> activeSessions = new ConcurrentHashMap<>();

    public TerminalSession getSession(Object tabId) {
        return activeSessions.get(tabId);
    }

    public TerminalSession createSession(Object tabId, Instance instance, String localId) {
        TerminalSession session = new TerminalSession(tabId, instance, localId);
        activeSessions.put(tabId, session);
        return session;
    }

    public void destroySession(Object tabId) {
        TerminalSession session = activeSessions.remove(tabId);
        if (session != null) {
            session.cleanup();
            if (session.getInstance() != null) {
                if (session.getStandardParser() != null) {
                     session.getInstance().removeLogListener(session.getStandardParser());
                }
                if (session.getStreamDataParser() != null) {
                     session.getInstance().removeLogListener(session.getStreamDataParser());
                }

                TerminalWidget.shutdown(session.getInstance().getInstanceId());
            } else if (session.getLocalTerminalId() != null) {
                TerminalWidget.shutdownLocal(session.getLocalTerminalId());
            }
             if (session.getTerminalWidget() != null) {
                session.getTerminalWidget().shutdown();
            }
        }
    }

    public void shutdownAll() {
        for (Object tabId : activeSessions.keySet()) {
            destroySession(tabId);
        }
    }
}
