package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.impl.CalagopusBackend;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

import java.util.List;

public final class DesktopPanelServerProvider implements PanelServerProvider {
    private static final DesktopPanelServerProvider INSTANCE = new DesktopPanelServerProvider();

    private DesktopPanelServerProvider() {
    }

    public static PanelServerProvider instance() {
        return INSTANCE;
    }

    @Override
    public boolean isPanelType(String type) {
        return PteroBackend.isPanelType(type);
    }

    @Override
    public String normalizePanelUrl(String value) {
        return PteroBackend.normalizePanelUrl(value);
    }

    @Override
    public String normalizePanelOrigin(String value) {
        return PteroBackend.normalizePanelOrigin(value);
    }

    @Override
    public Async<List<?>> listServers(Object host) {
        if (!(host instanceof RemoteHost remoteHost)) {
            return Async.failed(new IllegalArgumentException("A Remote Host Is Required"));
        }
        Async<? extends List<?>> request = "CALAGOPUS".equalsIgnoreCase(remoteHost.getType())
                ? JvmAsyncBridge.fromFuture(CalagopusBackend.listServers(remoteHost.getIp(), remoteHost.getApiKey()))
                : JvmAsyncBridge.fromFuture(PteroBackend.listServers(remoteHost.getIp(), remoteHost.getApiKey()));
        return request.thenApply(value -> (List<?>) value);
    }
}
