package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.Screen;

public interface FlowManagerUiAdapter {
    default boolean activate(ApplicationHost host, Screen studioScreen, boolean fullEditor) {
        return false;
    }

    default Object resolveDesignerParent(ApplicationHost host, Object parent) {
        return parent == null ? host.getCurrentScreen() : parent;
    }

    default Object captureCurrentParent(ApplicationHost host) {
        return host.getCurrentScreen();
    }

    default boolean closeStudio(ApplicationHost host, Screen studioScreen, Object parent, boolean saved) {
        return false;
    }

    default boolean openWorldMap(FlowManager manager, ApplicationHost host, String serverId, ClientServerView server,
                                 String worldName, Object parent) {
        return false;
    }

    default void onWorldAuditSnapshot(ApplicationHost host, String serverId, Object data) {
    }

    default void onWorldOperationResult(ApplicationHost host, String serverId, WorldOperationResult result) {
    }

    default void onWorldSaveStarted(ApplicationHost host, String serverId, String targetName, String title, long sequence) {
    }

    default void onWorldSaveFinished(ApplicationHost host, String serverId, String targetName, String title, String message,
                                     ReSyncNotificationLevel level, long sequence) {
    }

    default void openServerScreen(FlowManager manager, ApplicationHost host, Screen parent, String serverId,
                                  ClientServerView server) {
    }

    static FlowManagerUiAdapter forHost(ApplicationHost host) {
        if (host == null) return unavailable();
        FlowManagerUiAdapter adapter = host.flowManagerUiAdapter();
        return adapter == null ? generic() : adapter;
    }

    static FlowManagerUiAdapter generic() {
        return new FlowManagerUiAdapter() {
            @Override
            public boolean activate(ApplicationHost host, Screen studioScreen, boolean fullEditor) {
                if (host == null || studioScreen == null) return false;
                host.setScreen(studioScreen);
                return true;
            }

            @Override
            public Object resolveDesignerParent(ApplicationHost host, Object parent) {
                return parent == null && host != null ? host.getCurrentScreen() : parent;
            }

            @Override
            public Object captureCurrentParent(ApplicationHost host) {
                return host == null ? null : host.getCurrentScreen();
            }

            @Override
            public boolean closeStudio(ApplicationHost host, Screen studioScreen, Object parent, boolean saved) {
                if (host == null) return false;
                host.openParentScreen(studioScreen, parent);
                return true;
            }
        };
    }

    static FlowManagerUiAdapter unavailable() {
        return new FlowManagerUiAdapter() {
        };
    }
}
