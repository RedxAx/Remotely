package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncedResourceCacheTest {

    @Test
    void authoritativeServerListPrunesDeletedServerDraftsButKeepsNewLocalDrafts() {
        SyncedResourceCache<Resource> cache = new SyncedResourceCache<>(Resource::id, Resource::id);
        cache.cache("server", new Resource("deleted"));
        cache.putInDraft("server", new Resource("deleted"));
        cache.putInDraft("server", new Resource("new_local"));

        cache.applyServerList("server", List.of());

        assertFalse(cache.containsKey("server", "deleted"));
        assertTrue(cache.containsKey("server", "new_local"));
    }

    private record Resource(String id) {
    }
}
