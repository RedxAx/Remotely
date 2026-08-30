package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.RemotelyServerApi;
import redxax.oxy.remotely.config.RemotelyViewStateStore;
import redxax.oxy.remotely.ui.server.NewTerminalTargetProvider;
import redxax.oxy.remotely.ui.server.ServerUiCapabilityProvider;
import restudio.rescreen.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserTerminalTargetProviderTest {
    @Test
    void restoresOnlyLiveCapableTargetsInPersistedOrder() {
        ServerModels.ClientServerView first = server("first");
        ServerModels.ClientServerView second = server("second");
        RemotelyViewStateStore.State saved = new RemotelyViewStateStore.State("", "", "", "", List.of(
                new RemotelyViewStateStore.TerminalTab("second", "Second Name"),
                new RemotelyViewStateStore.TerminalTab("unsupported", "Unsupported"),
                new RemotelyViewStateStore.TerminalTab("first", "First Name")), 0);

        NewTerminalTargetProvider.State restored = BrowserServerScreenHost.restoredTerminalState(List.of(first, second),
                new NewTerminalTargetProvider.State(List.of(), 0), saved);

        assertEquals(List.of(second, first), restored.tabs().stream().map(NewTerminalTargetProvider.Tab::target).toList());
        assertEquals(List.of("Second Name", "First Name"), restored.tabs().stream().map(NewTerminalTargetProvider.Tab::name).toList());
        assertEquals(0, restored.activeIndex());
    }

    @Test
    void dropsProviderlessLocalTargetsDuringRestore() {
        ServerModels.ClientServerView first = server("first");
        ServerModels.ClientServerView second = server("second");
        RemotelyViewStateStore.State saved = new RemotelyViewStateStore.State("", "", "", "", List.of(
                new RemotelyViewStateStore.TerminalTab("first", "First")), 0);
        NewTerminalTargetProvider.State current = new NewTerminalTargetProvider.State(List.of(
                new NewTerminalTargetProvider.Tab("local-uuid", "Terminal"),
                new NewTerminalTargetProvider.Tab(second, "Second")), 1);

        NewTerminalTargetProvider.State restored = BrowserServerScreenHost.restoredTerminalState(List.of(first, second), current, saved);

        assertEquals(List.of(first, second), restored.tabs().stream().map(NewTerminalTargetProvider.Tab::target).toList());
        assertEquals(1, restored.activeIndex());
    }

    @Test
    void requiresRefreshedConsoleTerminalCapability() {
        ServerModels.ClientServerView server = server("hosted");
        CapabilityProvider unsupported = new CapabilityProvider(false);
        CapabilityProvider supported = new CapabilityProvider(true);

        assertEquals(null, BrowserServerScreenHost.terminalServer(unsupported, server).join());
        assertEquals(server, BrowserServerScreenHost.terminalServer(supported, server).join());
        assertEquals(1, unsupported.refreshes);
        assertEquals(1, supported.refreshes);
    }

    private static ServerModels.ClientServerView server(String id) {
        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.identifier = id;
        server.name = id;
        return server;
    }

    private static final class CapabilityProvider implements ServerUiCapabilityProvider {
        private final boolean terminal;
        private int refreshes;

        private CapabilityProvider(boolean terminal) {
            this.terminal = terminal;
        }

        @Override
        public Async<RemotelyServerApi.ServerCapabilities> refresh(ServerModels.ClientServerView server) {
            refreshes++;
            return Async.completed(null);
        }

        @Override
        public Availability availability(ServerModels.ClientServerView server, Capability capability) {
            return terminal && capability == Capability.TERMINAL ? Availability.supported() : Availability.missing("Unavailable");
        }
    }
}
