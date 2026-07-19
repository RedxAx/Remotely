package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonElement;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.flow.data.FlowGraph;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.Screen;

public class FlowEditorScreen extends FlowGraphDesignerScreen {
    public FlowEditorScreen(FlowGraph graph, String serverId, Screen parent) {
        super(graph, serverId, parent);
    }

    public FlowEditorScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint) {
        super(graph, serverId, parent, startupServer, loaderHint);
    }

    public FlowEditorScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint, String serverTitle) {
        super(graph, serverId, parent, startupServer, loaderHint, serverTitle);
    }

    @Override
    public FlowEditorScreen enableStudioMode() {
        super.enableStudioMode();
        return this;
    }

    public static FlowEditorScreen getStudioScreen(String serverId) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen instanceof FlowEditorScreen editor
                && screen.isStudioMode()
                && serverId != null
                && serverId.equals(screen.getServerId())) {
                return editor;
            }
        }
        return null;
    }

    public static boolean hasOpenStudioScreenForServer(String serverId) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen instanceof FlowEditorScreen
                && screen.isStudioMode()
                && serverId != null
                && serverId.equals(screen.getServerId())) {
                return true;
            }
        }
        return false;
    }

    public static void refreshCatalogForServer(String serverId) {
        FlowGraphDesignerScreen.refreshCatalogForServer(serverId);
    }

    public static void refreshCatalogForServer(String serverId, String sourceId) {
        FlowGraphDesignerScreen.refreshCatalogForServer(serverId, sourceId);
    }

    public static void refreshWorldsForServer(String serverId) {
        FlowGraphDesignerScreen.refreshWorldsForServer(serverId);
    }

    public static void handleWorldOperationResultForServer(String serverId, WorldOperationResult result) {
        FlowGraphDesignerScreen.handleWorldOperationResultForServer(serverId, result);
    }

    public static void handleWorldAuditSnapshotForServer(String serverId, JsonElement data) {
        FlowGraphDesignerScreen.handleWorldAuditSnapshotForServer(serverId, data);
    }
}
