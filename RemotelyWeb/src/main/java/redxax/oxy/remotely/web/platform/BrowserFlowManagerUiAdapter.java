package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowManagerUiAdapter;
import redxax.oxy.remotely.data.flow.ReSyncWorldMapDataProvider;
import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rescreen.ui.core.Screen;

public final class BrowserFlowManagerUiAdapter implements FlowManagerUiAdapter {
    @Override
    public boolean openWorldMap(FlowManager manager, ApplicationHost host, String serverId, ClientServerView server,
                                String worldName, Object parent) {
        String actualServerId = server != null && server.identifier != null && !server.identifier.isBlank() ? server.identifier : serverId;
        if (actualServerId == null || actualServerId.isBlank()) {
            return false;
        }
        Screen parentScreen = parent instanceof Screen screen ? screen : host.getCurrentScreen();
        ReSyncWorldMapDataProvider provider = new ReSyncWorldMapDataProvider(manager, actualServerId);
        WorldMapScreen screen = new WorldMapScreen(parentScreen, provider, worldName) {
            @Override
            public void close() {
                provider.close();
                super.close();
            }
        };
        host.setScreen(screen);
        return true;
    }
}
