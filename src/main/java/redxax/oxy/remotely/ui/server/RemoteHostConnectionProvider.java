package redxax.oxy.remotely.ui.server;

import restudio.rescreen.platform.Async;

import java.util.Optional;

public interface RemoteHostConnectionProvider {
    Async<Boolean> connect(Object host);

    boolean isConnected(Object host);

    Optional<Session> existingSession(Object host);

    void restoreSession(Object host, Session session);

    void close(Session session);

    interface Session {
        Object host();
    }

    static RemoteHostConnectionProvider unavailable() {
        return UnavailableRemoteHostConnectionProvider.INSTANCE;
    }

    final class UnavailableRemoteHostConnectionProvider implements RemoteHostConnectionProvider {
        private static final UnavailableRemoteHostConnectionProvider INSTANCE = new UnavailableRemoteHostConnectionProvider();

        private UnavailableRemoteHostConnectionProvider() {
        }

        @Override
        public Async<Boolean> connect(Object host) {
            return Async.completed(false);
        }

        @Override
        public boolean isConnected(Object host) {
            return false;
        }

        @Override
        public Optional<Session> existingSession(Object host) {
            return Optional.empty();
        }

        @Override
        public void restoreSession(Object host, Session session) {
        }

        @Override
        public void close(Session session) {
        }
    }
}
