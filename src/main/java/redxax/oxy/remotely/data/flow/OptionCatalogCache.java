package redxax.oxy.remotely.data.flow;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OptionCatalogCache {
    private static final OptionCatalogCache INSTANCE = new OptionCatalogCache();
    private final Map<String, Catalog> catalogs = new ConcurrentHashMap<>();
    private final Set<String> inFlightRequests = ConcurrentHashMap.newKeySet();

    public static OptionCatalogCache getInstance() {
        return INSTANCE;
    }

    public void put(String serverId, String sourceId, String revision, List<String> values) {
        put(serverId, sourceId, revision, values, List.of());
    }

    public void put(String serverId, String sourceId, String revision, List<String> values, List<OptionCatalogItem> items) {
        List<OptionCatalogItem> safeItems = items != null ? List.copyOf(items) : List.of();
        List<String> safeValues = values != null && !values.isEmpty()
            ? List.copyOf(values)
            : safeItems.stream().map(OptionCatalogItem::getValue).filter(value -> value != null && !value.isBlank()).toList();
        catalogs.put(key(serverId, sourceId), new Catalog(revision, safeValues, safeItems));
        clearRequestInFlight(serverId, sourceId);
    }

    public List<String> getValues(String serverId, String sourceId) {
        Catalog catalog = catalogs.get(key(serverId, sourceId));
        return catalog != null ? catalog.values() : List.of();
    }

    public boolean hasValues(String serverId, String sourceId) {
        Catalog catalog = catalogs.get(key(serverId, sourceId));
        return catalog != null && !catalog.values().isEmpty();
    }

    public boolean hasCatalog(String serverId, String sourceId) {
        return catalogs.containsKey(key(serverId, sourceId));
    }

    public boolean markRequestInFlight(String serverId, String sourceId) {
        if (sourceId == null || sourceId.isBlank() || hasCatalog(serverId, sourceId)) {
            return false;
        }
        return inFlightRequests.add(key(serverId, sourceId));
    }

    public boolean isRequestInFlight(String serverId, String sourceId) {
        return inFlightRequests.contains(key(serverId, sourceId));
    }

    public void clearRequestInFlight(String serverId, String sourceId) {
        inFlightRequests.remove(key(serverId, sourceId));
    }

    public void clearRequestsInFlight(String serverId) {
        String prefix = (serverId != null ? serverId : "") + ":";
        inFlightRequests.removeIf(key -> key.startsWith(prefix));
    }

    public List<OptionCatalogItem> getItems(String serverId, String sourceId) {
        Catalog catalog = catalogs.get(key(serverId, sourceId));
        return catalog != null ? catalog.items() : List.of();
    }

    private String key(String serverId, String sourceId) {
        return (serverId != null ? serverId : "") + ":" + (sourceId != null ? sourceId : "");
    }

    private record Catalog(String revision, List<String> values, List<OptionCatalogItem> items) {
    }
}
