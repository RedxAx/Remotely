package redxax.oxy.remotely.data.flow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OptionCatalogCache {
    private static final OptionCatalogCache INSTANCE = new OptionCatalogCache();
    private final Map<String, Catalog> catalogs = new ConcurrentHashMap<>();

    public static OptionCatalogCache getInstance() {
        return INSTANCE;
    }

    public void put(String serverId, String sourceId, String revision, List<String> values) {
        catalogs.put(key(serverId, sourceId), new Catalog(revision, values != null ? List.copyOf(values) : List.of()));
    }

    public List<String> getValues(String serverId, String sourceId) {
        Catalog catalog = catalogs.get(key(serverId, sourceId));
        return catalog != null ? catalog.values() : List.of();
    }

    public boolean hasValues(String serverId, String sourceId) {
        Catalog catalog = catalogs.get(key(serverId, sourceId));
        return catalog != null && !catalog.values().isEmpty();
    }

    private String key(String serverId, String sourceId) {
        return (serverId != null ? serverId : "") + ":" + (sourceId != null ? sourceId : "");
    }

    private record Catalog(String revision, List<String> values) {
    }
}
