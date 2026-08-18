package redxax.oxy.remotely.session;

import redxax.oxy.remotely.util.BrowserSafeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TerminalSessionManager {
    private final Map<Object, TerminalSession> activeSessions = BrowserSafeState.map();
    private final TerminalSessionLifecycle lifecycle;

    public TerminalSessionManager() {
        this(TerminalSessionLifecycle.browser());
    }

    public TerminalSessionManager(TerminalSessionLifecycle lifecycle) {
        this.lifecycle = lifecycle == null ? TerminalSessionLifecycle.browser() : lifecycle;
    }

    public TerminalSession getSession(Object tabId) {
        return activeSessions.get(tabId);
    }

    public TerminalSession createSession(Object tabId, Object instance, String localId) {
        TerminalSession session = new TerminalSession(tabId, instance, localId);
        activeSessions.put(tabId, session);
        return session;
    }

    public void destroySession(Object tabId) {
        TerminalSession session = activeSessions.remove(tabId);
        if (session != null) {
            session.cleanup();
            if (session.getTerminalWidget() != null) {
                session.getTerminalWidget().shutdown();
            }
            lifecycle.cleanup(session);
        }
    }

    public void shutdownAll() {
        List<Object> tabIds;
        synchronized (activeSessions) {
            tabIds = new ArrayList<>(activeSessions.keySet());
        }
        for (Object tabId : tabIds) {
            destroySession(tabId);
        }
    }
}
