package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.data.FlowGraph;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypedGraphCacheTest {
    @Test
    void identicalIdsRemainIsolatedByGraphType() {
        TypedGraphCache cache = new TypedGraphCache();
        FlowGraph flow = graph("shared", ReSyncResourceType.FLOW);
        FlowGraph function = graph("shared", ReSyncResourceType.FUNCTION);
        FlowGraph command = graph("shared", ReSyncResourceType.COMMAND);

        cache.cache("server", flow);
        cache.cache("server", function);
        cache.cache("server", command);

        assertEquals(flow, cache.get("server", ReSyncResourceType.FLOW, "shared"));
        assertEquals(function, cache.get("server", ReSyncResourceType.FUNCTION, "shared"));
        assertEquals(command, cache.get("server", ReSyncResourceType.COMMAND, "shared"));
        assertNull(cache.get("server", "shared"));
    }

    @Test
    void authoritativeListPrunesOnlyItsOwnType() {
        TypedGraphCache cache = new TypedGraphCache();
        cache.cache("server", graph("shared", ReSyncResourceType.FLOW));
        cache.cache("server", graph("shared", ReSyncResourceType.FUNCTION));

        cache.applyServerList("server", ReSyncResourceType.FLOW, List.of());

        assertNull(cache.get("server", ReSyncResourceType.FLOW, "shared"));
        assertNotNull(cache.get("server", ReSyncResourceType.FUNCTION, "shared"));
    }

    private FlowGraph graph(String id, ReSyncResourceType type) {
        FlowGraph graph = new FlowGraph();
        graph.setId(id);
        graph.setResourceType(type.typeId());
        graph.setFunction(type == ReSyncResourceType.FUNCTION);
        return graph;
    }
}
