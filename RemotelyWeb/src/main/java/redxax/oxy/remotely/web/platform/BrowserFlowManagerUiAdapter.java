package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonElement;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowManagerUiAdapter;
import redxax.oxy.remotely.data.flow.ReSyncServerIdentity;
import redxax.oxy.remotely.data.flow.ReSyncNotificationLevel;
import redxax.oxy.remotely.data.flow.ReSyncWorldMapDataProvider;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.host.ApplicationHostRegistry;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rescreen.ui.core.Screen;

public final class BrowserFlowManagerUiAdapter implements FlowManagerUiAdapter {
    private long worldMapGeneration;

    @Override
    public boolean openWorldMap(FlowManager manager, ApplicationHost host, String serverId, ClientServerView server,
                                String worldName, Object parent) {
        long generation = nextWorldMapGeneration();
        String actualServerId = ReSyncServerIdentity.from(serverId, server).serverId();
        if (actualServerId == null || actualServerId.isBlank()) {
            return false;
        }
        Screen parentScreen = parent instanceof Screen screen ? screen : host.getCurrentScreen();
        if (generation != worldMapGeneration || ApplicationHostRegistry.current() != host
                || host.getCurrentScreen() != parentScreen || !manager.isFlowClientReady(actualServerId)) {
            notifyUnavailable(host, "ReSync Unavailable");
            return false;
        }
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

    private void notifyUnavailable(ApplicationHost host, String message) {
        host.notify("World Map", message, ReSyncNotificationLevel.WARN);
    }

    private long nextWorldMapGeneration() {
        worldMapGeneration++;
        if (worldMapGeneration <= 0L) {
            worldMapGeneration = 1L;
        }
        return worldMapGeneration;
    }

    @Override
    public void onWorldAuditSnapshot(ApplicationHost host, String serverId, Object data) {
        if (!(data instanceof JsonElement element)) {
            return;
        }
        host.execute(() -> FlowEditorScreen.handleWorldAuditSnapshotForServer(serverId, element));
    }

    @Override
    public void onWorldOperationResult(ApplicationHost host, String serverId, WorldOperationResult result) {
        host.execute(() -> FlowEditorScreen.handleWorldOperationResultForServer(serverId, result));
    }

    @Override
    public void onWorldSaveStarted(ApplicationHost host, String serverId, String targetName, String title, long sequence) {
        host.execute(() -> {
            if (sequence > 0) {
                FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                if (studioScreen != null) {
                    studioScreen.markStudioDocumentSaving(ReSyncResourceDragPayload.WORLD, targetName, sequence);
                }
            }
            host.notify(title, targetName, ReSyncNotificationLevel.INFO);
        });
    }

    @Override
    public void onWorldSaveFinished(ApplicationHost host, String serverId, String targetName, String title, String message,
                                    ReSyncNotificationLevel level, long sequence) {
        host.execute(() -> {
            if (sequence > 0 && level == ReSyncNotificationLevel.SUCCESS) {
                FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                if (studioScreen != null) {
                    studioScreen.markStudioDocumentSaved(ReSyncResourceDragPayload.WORLD, targetName, sequence);
                }
            }
            host.notify(title, message, level == null ? ReSyncNotificationLevel.INFO : level);
        });
    }
}
