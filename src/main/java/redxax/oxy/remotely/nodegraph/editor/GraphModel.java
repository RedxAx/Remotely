package redxax.oxy.remotely.nodegraph.editor;

import java.util.List;
import java.util.Map;

public interface GraphModel {
    Map<String, ? extends GraphNode> getNodes();

    List<? extends GraphConnection> getConnections();

    String getId();

    void setId(String id);
}
