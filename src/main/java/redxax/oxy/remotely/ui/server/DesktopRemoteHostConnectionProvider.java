package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.TaskSchedulers;
import restudio.rebase.hosting.RemoteHost;
import restudio.rescreen.platform.Async;
import restudio.rebase.util.ssh.SSHManager;

import java.util.Objects;
import java.util.Optional;

public final class DesktopRemoteHostConnectionProvider implements RemoteHostConnectionProvider {
    private static final DesktopRemoteHostConnectionProvider INSTANCE = new DesktopRemoteHostConnectionProvider();

    private DesktopRemoteHostConnectionProvider() {
    }

    public static RemoteHostConnectionProvider instance() {
        return INSTANCE;
    }

    @Override
    public Async<Boolean> connect(Object host) {
        if (!(host instanceof RemoteHost remoteHost)) return Async.completed(false);
        return AsyncTools.supply(TaskSchedulers.current(), () -> {
            try {
                return remoteHost.getSshManager().connect();
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    @Override
    public boolean isConnected(Object host) {
        return host instanceof RemoteHost remoteHost && remoteHost.getSshManager().isConnected();
    }

    @Override
    public Optional<Session> existingSession(Object host) {
        if (!(host instanceof RemoteHost remoteHost)) return Optional.empty();
        SSHManager manager = remoteHost.getExistingSshManager();
        return manager == null || !manager.isConnected() ? Optional.empty() : Optional.of(new DesktopSession(manager));
    }

    @Override
    public void restoreSession(Object host, Session session) {
        if (!(host instanceof RemoteHost remoteHost) || !(session instanceof DesktopSession desktopSession)) return;
        SSHManager manager = desktopSession.manager();
        RemoteHost previous = manager.getRemoteHost();
        if (previous != null && Objects.equals(remoteHost.getIp(), previous.getIp())
                && Objects.equals(remoteHost.getUser(), previous.getUser()) && remoteHost.getPort() == previous.getPort()) {
            manager.updateHostReference(remoteHost);
            remoteHost.setSshManager(manager);
        } else {
            close(session);
        }
    }

    @Override
    public void close(Session session) {
        if (session instanceof DesktopSession desktopSession) {
            try {
                desktopSession.manager().disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private record DesktopSession(SSHManager manager) implements Session {
        @Override
        public Object host() {
            return manager.getRemoteHost();
        }
    }
}
