package redxax.oxy.remotely.ui.server;

import restudio.rebase.platform.Async;

import java.util.List;

public interface PanelServerProvider {
    boolean isPanelType(String type);

    String normalizePanelUrl(String value);

    String normalizePanelOrigin(String value);

    Async<List<?>> listServers(Object host);

    static PanelServerProvider unavailable() {
        return UnavailablePanelServerProvider.INSTANCE;
    }

    final class UnavailablePanelServerProvider implements PanelServerProvider {
        private static final UnavailablePanelServerProvider INSTANCE = new UnavailablePanelServerProvider();

        private UnavailablePanelServerProvider() {
        }

        @Override
        public boolean isPanelType(String type) {
            return false;
        }

        @Override
        public String normalizePanelUrl(String value) {
            return value == null ? "" : value.trim();
        }

        @Override
        public String normalizePanelOrigin(String value) {
            return normalizePanelUrl(value);
        }

        @Override
        public Async<List<?>> listServers(Object host) {
            return Async.failed(new UnsupportedOperationException("Panel Server Access Is Unavailable"));
        }
    }
}
