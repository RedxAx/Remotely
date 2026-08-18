package redxax.oxy.remotely.ui.server;

public interface ServerTerminalLifecycle {
    void notifyStartRequested();

    void notifyStopRequested();

    boolean isStaleLocalControllerStatus(Object status);

    void stopProcessAsync();
}
