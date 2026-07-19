package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class OptionCatalogCache {
    private static final int CACHE_SCHEMA_VERSION = 1;
    private static final OptionCatalogCache INSTANCE = new OptionCatalogCache();
    private final Gson gson = new GsonBuilder().create();
    private final Path cachePath;
    private final Map<String, Catalog> catalogs = new ConcurrentHashMap<>();
    private final Set<String> inFlightRequests = ConcurrentHashMap.newKeySet();
    private final Set<String> staleCatalogs = ConcurrentHashMap.newKeySet();

    private OptionCatalogCache() {
        this(remotelyDir.resolve("data").resolve("flow").resolve("option_catalog_cache.json"));
    }

    OptionCatalogCache(Path cachePath) {
        this.cachePath = cachePath;
        load();
    }

    public static OptionCatalogCache getInstance() {
        return INSTANCE;
    }

    public boolean put(String serverId, String sourceId, String revision, List<String> values) {
        return put(serverId, sourceId, revision, 0L, values, List.of());
    }

    public boolean put(String serverId, String sourceId, String revision, List<String> values, List<OptionCatalogItem> items) {
        return put(serverId, sourceId, revision, 0L, values, items);
    }

    public boolean put(String serverId, String sourceId, String revision, long sequence, List<String> values, List<OptionCatalogItem> items) {
        return put(serverId, sourceId, "", revision, sequence, values, items);
    }

    public boolean put(String serverId, String sourceId, String contextKey, String revision, long sequence, List<String> values, List<OptionCatalogItem> items) {
        return put(serverId, sourceId, contextKey, revision, sequence, values, items, "available", "");
    }

    public boolean put(String serverId, String sourceId, String contextKey, String revision, long sequence, List<String> values, List<OptionCatalogItem> items,
        String status, String diagnostic) {
        List<OptionCatalogItem> safeItems = items != null ? items.stream().filter(item -> item != null).toList() : List.of();
        List<String> safeValues = values != null ? values.stream().filter(value -> value != null && !value.isBlank()).toList() : List.of();
        if (safeValues.isEmpty() && !safeItems.isEmpty()) {
            safeValues = safeItems.stream().map(OptionCatalogItem::getValue).filter(value -> value != null && !value.isBlank()).toList();
        }
        Catalog next = new Catalog(revision, Math.max(0L, sequence), safeValues, safeItems, status, diagnostic);
        String key = key(serverId, sourceId, contextKey);
        AtomicBoolean changed = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean();
        boolean stale = staleCatalogs.contains(key);
        catalogs.compute(key, (ignored, previous) -> {
            if (!stale && previous != null && previous.isNewerThan(next)) {
                return previous;
            }
            accepted.set(true);
            changed.set(stale || previous == null || !previous.sameContent(next));
            return next;
        });
        clearRequestInFlight(serverId, sourceId, contextKey);
        if (accepted.get()) {
            staleCatalogs.remove(key);
            save();
        }
        return changed.get();
    }

    public List<String> getValues(String serverId, String sourceId) {
        return getValues(serverId, sourceId, "");
    }

    public List<String> getValues(String serverId, String sourceId, String contextKey) {
        Catalog catalog = catalogs.get(key(serverId, sourceId, contextKey));
        return catalog != null ? catalog.values() : List.of();
    }

    public boolean hasValues(String serverId, String sourceId) {
        Catalog catalog = catalogs.get(key(serverId, sourceId, ""));
        return catalog != null && !catalog.values().isEmpty();
    }

    public boolean hasCatalog(String serverId, String sourceId) {
        return hasCatalog(serverId, sourceId, "");
    }

    public boolean hasCatalog(String serverId, String sourceId, String contextKey) {
        return catalogs.containsKey(key(serverId, sourceId, contextKey));
    }

    public void invalidate(String serverId, String sourceId) {
        String prefix = key(serverId, sourceId, "");
        boolean changed = catalogs.keySet().removeIf(key -> key.startsWith(prefix));
        inFlightRequests.removeIf(key -> key.startsWith(prefix));
        staleCatalogs.removeIf(key -> key.startsWith(prefix));
        if (changed) {
            save();
        }
    }

    public void invalidate(String serverId, String sourceId, String contextKey) {
        String key = key(serverId, sourceId, contextKey);
        boolean changed = catalogs.remove(key) != null;
        inFlightRequests.remove(key);
        staleCatalogs.remove(key);
        if (changed) {
            save();
        }
    }

    public void invalidateAll(String serverId, List<String> sourceIds) {
        if (sourceIds == null) {
            return;
        }
        for (String sourceId : sourceIds) {
            invalidate(serverId, sourceId);
        }
    }

    public boolean markRequestInFlight(String serverId, String sourceId) {
        return markRequestInFlight(serverId, sourceId, "");
    }

    public boolean markRequestInFlight(String serverId, String sourceId, String contextKey) {
        if (sourceId == null || sourceId.isBlank() || hasCatalog(serverId, sourceId, contextKey) && !isStale(serverId, sourceId, contextKey)) {
            return false;
        }
        return inFlightRequests.add(key(serverId, sourceId, contextKey));
    }

    public boolean isRequestInFlight(String serverId, String sourceId) {
        return isRequestInFlight(serverId, sourceId, "");
    }

    public boolean isRequestInFlight(String serverId, String sourceId, String contextKey) {
        return inFlightRequests.contains(key(serverId, sourceId, contextKey));
    }

    public void clearRequestInFlight(String serverId, String sourceId) {
        clearRequestInFlight(serverId, sourceId, "");
    }

    public void clearRequestInFlight(String serverId, String sourceId, String contextKey) {
        inFlightRequests.remove(key(serverId, sourceId, contextKey));
    }

    public void clearRequestsInFlight(String serverId) {
        String prefix = (serverId != null ? serverId : "") + "\u0000";
        inFlightRequests.removeIf(key -> key.startsWith(prefix));
    }

    public void markServerStale(String serverId) {
        String prefix = (serverId != null ? serverId : "") + "\u0000";
        catalogs.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(staleCatalogs::add);
    }

    public boolean isStale(String serverId, String sourceId, String contextKey) {
        return staleCatalogs.contains(key(serverId, sourceId, contextKey));
    }

    public List<OptionCatalogItem> getItems(String serverId, String sourceId) {
        return getItems(serverId, sourceId, "");
    }

    public List<OptionCatalogItem> getItems(String serverId, String sourceId, String contextKey) {
        Catalog catalog = catalogs.get(key(serverId, sourceId, contextKey));
        return catalog != null ? catalog.items() : List.of();
    }

    public List<OptionCatalogItem> getItemsAcrossContexts(String serverId, String sourceId) {
        String prefix = key(serverId, sourceId, "");
        Map<String, OptionCatalogItem> items = new LinkedHashMap<>();
        catalogs.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .sorted(Map.Entry.comparingByKey())
            .flatMap(entry -> entry.getValue().items().stream())
            .filter(item -> item != null && item.getValue() != null && !item.getValue().isBlank())
            .forEach(item -> items.putIfAbsent(item.getValue(), item));
        return List.copyOf(items.values());
    }

    public String getStatus(String serverId, String sourceId, String contextKey) {
        Catalog catalog = catalogs.get(key(serverId, sourceId, contextKey));
        if (catalog == null) {
            return "missing";
        }
        return isStale(serverId, sourceId, contextKey) ? "stale" : catalog.status();
    }

    public String getDiagnostic(String serverId, String sourceId, String contextKey) {
        Catalog catalog = catalogs.get(key(serverId, sourceId, contextKey));
        if (catalog == null) {
            return "Catalog has not been loaded";
        }
        return isStale(serverId, sourceId, contextKey) ? "Cached catalog is awaiting refresh" : catalog.diagnostic();
    }

    private String key(String serverId, String sourceId) {
        return key(serverId, sourceId, "");
    }

    private String key(String serverId, String sourceId, String contextKey) {
        return (serverId != null ? serverId : "") + "\u0000" + (sourceId != null ? sourceId : "") + "\u0000" + (contextKey != null ? contextKey : "");
    }

    private void load() {
        if (cachePath == null || Files.notExists(cachePath)) {
            return;
        }
        try {
            PersistedState state = gson.fromJson(Files.readString(cachePath), PersistedState.class);
            if (state == null || state.schemaVersion != CACHE_SCHEMA_VERSION || state.catalogs == null) {
                return;
            }
            for (Map.Entry<String, Catalog> entry : state.catalogs.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    Catalog catalog = entry.getValue();
                    catalogs.put(entry.getKey(), new Catalog(catalog.revision(), catalog.sequence(), catalog.values(), catalog.items(), catalog.status(), catalog.diagnostic()));
                }
            }
            staleCatalogs.addAll(catalogs.keySet());
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Flow] Failed to load option catalog cache: " + exception.getMessage());
        }
    }

    private synchronized void save() {
        if (cachePath == null) {
            return;
        }
        try {
            Path parent = cachePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(new PersistedState(new HashMap<>(catalogs))));
            try {
                Files.move(temporary, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Flow] Failed to save option catalog cache: " + exception.getMessage());
        }
    }

    private record Catalog(String revision, long sequence, List<String> values, List<OptionCatalogItem> items, String status, String diagnostic) {
        private Catalog {
            revision = revision != null ? revision : "";
            sequence = Math.max(0L, sequence);
            values = values != null ? List.copyOf(values) : List.of();
            items = items != null ? items.stream().filter(item -> item != null).toList() : List.of();
            status = status != null && !status.isBlank() ? status : "available";
            diagnostic = diagnostic != null ? diagnostic : "";
        }

        private boolean sameContent(Catalog other) {
            return other != null && values.equals(other.values) && items.equals(other.items) && status.equals(other.status) && diagnostic.equals(other.diagnostic);
        }

        private boolean isNewerThan(Catalog other) {
            return sequence > 0L && (other.sequence <= 0L || sequence >= other.sequence);
        }
    }

    private static class PersistedState {
        private int schemaVersion = CACHE_SCHEMA_VERSION;
        private Map<String, Catalog> catalogs = new HashMap<>();

        private PersistedState() {
        }

        private PersistedState(Map<String, Catalog> catalogs) {
            this.catalogs = catalogs != null ? catalogs : new HashMap<>();
        }
    }
}
