package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionCatalogLoaderTest {
    @Test
    void profilesDiscardInvalidSourcesAndKeepTheirOrder() {
        OptionCatalogLoader.Profile profile = OptionCatalogLoader.profile(
            "server:minecraft:material", "", null, "server:minecraft:world");

        assertEquals(2, profile.requests().size());
        assertEquals("server:minecraft:material", profile.requests().getFirst().source());
        assertEquals("server:minecraft:world", profile.requests().getLast().source());
    }

    @Test
    void requestsOwnAnImmutableContextSnapshot() {
        Map<String, Object> context = new HashMap<>();
        context.put("provider", "itemsadder");

        OptionCatalogLoader.Request request = OptionCatalogLoader.request("server:custom_content:asset", context);
        context.put("provider", "oraxen");

        assertEquals(Map.of("provider", "itemsadder"), request.context());
        assertThrows(UnsupportedOperationException.class, () -> request.context().put("content_type", "item"));
    }
}
