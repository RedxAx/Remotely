package redxax.oxy.remotely.ui.widgets.management;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.RemotelyServerApi;
import redxax.oxy.remotely.ui.server.ServerUiCapabilityProvider;
import restudio.rebase.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.ui.core.ScreenManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerManagerControllerCapabilityTest {
    @Test
    void loadsWhenPlayerCapabilityBecomesReady() {
        ServerModels.ClientServerView server = server("capability-ready");
        MutablePlayerCapabilities capabilities = new MutablePlayerCapabilities();
        PlayerManagerController controller = PlayerManagerController.getOrCreate(server, capabilities);

        controller.setUiBindings(null, null);

        assertEquals(PlayerManagerController.LoadState.LOADING, controller.getLoadState());
        assertEquals(0, capabilities.requests);

        capabilities.supported = true;
        ScreenManager screenManager = ScreenManager.getInstance();
        var runtime = screenManager.runtime();
        screenManager.restoreRuntimeReference(null);
        try {
            capabilities.notifyListeners();
            for (int attempt = 0; attempt < 100 && capabilities.requests == 0; attempt++) screenManager.processTasks();
        } finally {
            screenManager.restoreRuntimeReference(runtime);
        }

        assertEquals(1, capabilities.requests);
        assertEquals(PlayerManagerController.LoadState.LOADED, controller.getLoadState());
    }

    @Test
    void exposesPlayerLoadFailure() {
        ServerModels.ClientServerView server = server("capability-failure");
        MutablePlayerCapabilities capabilities = new MutablePlayerCapabilities();
        capabilities.supported = true;
        capabilities.failure = new IllegalStateException("Player Snapshot Timed Out");
        PlayerManagerController controller = PlayerManagerController.getOrCreate(server, capabilities);

        controller.setUiBindings(null, null);

        assertEquals(PlayerManagerController.LoadState.FAILED, controller.getLoadState());
        assertEquals("Player Snapshot Timed Out", controller.getLoadFailure());
    }

    private static ServerModels.ClientServerView server(String id) {
        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.identifier = id;
        server.name = id;
        return server;
    }

    private static final class MutablePlayerCapabilities implements ServerUiCapabilityProvider {
        private final List<Runnable> listeners = new ArrayList<>();
        private boolean supported;
        private RuntimeException failure;
        private int requests;

        @Override
        public Availability availability(ServerModels.ClientServerView server, Capability capability) {
            return supported ? Availability.supported() : Availability.missing("Server Capabilities Are Loading");
        }

        @Override
        public Async<List<RemotelyServerApi.Player>> players(ServerModels.ClientServerView server) {
            requests++;
            return failure == null ? Async.completed(List.of()) : Async.failed(failure);
        }

        @Override
        public void addCapabilityListener(Runnable listener) {
            listeners.add(listener);
        }

        @Override
        public void removeCapabilityListener(Runnable listener) {
            listeners.remove(listener);
        }

        private void notifyListeners() {
            List.copyOf(listeners).forEach(Runnable::run);
        }
    }
}
