package redxax.oxy.remotely.flow.ui.studio;

import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;

public record StudioDocument(String type, String id, String title, FlowGraph graph, ReSyncStudioView view, StudioViewportState viewport) {
    public String key() {
        return ReSyncProjectMetadata.resourceKey(type, id);
    }
}
