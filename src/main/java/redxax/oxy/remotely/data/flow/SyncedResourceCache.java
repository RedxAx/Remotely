package redxax.oxy.remotely.data.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SyncedResourceCache<T> {

    private final Map<String, T> cache = new ConcurrentHashMap<>();
    private final Map<String, T> drafts = new ConcurrentHashMap<>();
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final Map<String, SyncedResourceState> states = new ConcurrentHashMap<>();
    private final Set<String> serverIds = ConcurrentHashMap.newKeySet();
    private final Set<String> loadedServerLists = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> pendingParents = new ConcurrentHashMap<>();
    private final Function<T, String> idExtractor;
    private final Function<T, String> defaultNameExtractor;

    public SyncedResourceCache(Function<T, String> idExtractor, Function<T, String> defaultNameExtractor) {
        this.idExtractor = idExtractor;
        this.defaultNameExtractor = defaultNameExtractor;
    }

    public String key(String serverId, String resourceId) {
        return serverId + ":" + resourceId;
    }

    public String stripPrefix(String key, String serverId) {
        String prefix = serverId + ":";
        return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }

    public void clearForServer(String serverId) {
        String prefix = serverId + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
        drafts.keySet().removeIf(k -> k.startsWith(prefix));
        serverIds.removeIf(k -> k.startsWith(prefix));
        names.keySet().removeIf(k -> k.startsWith(prefix));
        states.keySet().removeIf(k -> k.startsWith(prefix));
        pendingParents.keySet().removeIf(k -> k.startsWith(prefix));
        loadedServerLists.remove(serverId);
    }

    public Map<String, T> getForServer(String serverId) {
        Map<String, T> result = new HashMap<>();
        String prefix = serverId + ":";
        for (var entry : cache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(stripPrefix(entry.getKey(), serverId), entry.getValue());
            }
        }
        for (var entry : drafts.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(stripPrefix(entry.getKey(), serverId), entry.getValue());
            }
        }
        return result;
    }

    public T get(String serverId, String resourceId) {
        String k = key(serverId, resourceId);
        T item = drafts.get(k);
        return item != null ? item : cache.get(k);
    }

    public T getFromCache(String serverId, String resourceId) {
        return cache.get(key(serverId, resourceId));
    }

    public T getFromDraft(String serverId, String resourceId) {
        return drafts.get(key(serverId, resourceId));
    }

    public void putInCache(String serverId, T item) {
        String k = key(serverId, idExtractor.apply(item));
        cache.put(k, item);
    }

    public void putInDraft(String serverId, T item) {
        String k = key(serverId, idExtractor.apply(item));
        drafts.put(k, item);
        states.put(k, SyncedResourceState.DIRTY);
    }

    public void replaceFromServer(String serverId, T item) {
        if (item == null) {
            return;
        }
        String id = idExtractor.apply(item);
        if (id == null) {
            return;
        }
        String k = key(serverId, id);
        drafts.remove(k);
        cache.put(k, item);
        names.putIfAbsent(k, defaultNameExtractor.apply(item));
        serverIds.add(k);
        states.put(k, SyncedResourceState.CLEAN);
    }

    public void discardDraft(String serverId, String resourceId) {
        String k = key(serverId, resourceId);
        drafts.remove(k);
        if (cache.containsKey(k) || serverIds.contains(k)) {
            states.put(k, SyncedResourceState.CLEAN);
        } else {
            states.remove(k);
        }
    }

    public void markSaving(String serverId, String resourceId) {
        states.put(key(serverId, resourceId), SyncedResourceState.SAVING);
    }

    public void markFailed(String serverId, String resourceId) {
        states.put(key(serverId, resourceId), SyncedResourceState.FAILED);
    }

    public void markStale(String serverId, String resourceId) {
        states.put(key(serverId, resourceId), SyncedResourceState.STALE);
    }

    public SyncedResourceState getState(String serverId, String resourceId) {
        return states.getOrDefault(key(serverId, resourceId), SyncedResourceState.CLEAN);
    }

    public void putNameIfAbsent(String serverId, String resourceId, String name) {
        names.putIfAbsent(key(serverId, resourceId), name);
    }

    public void putName(String serverId, String resourceId, String name) {
        names.put(key(serverId, resourceId), name);
    }

    public String getName(String serverId, String resourceId) {
        return names.getOrDefault(key(serverId, resourceId), resourceId);
    }

    public String removeName(String serverId, String resourceId) {
        return names.remove(key(serverId, resourceId));
    }

    public boolean containsServerId(String serverId, String resourceId) {
        return serverIds.contains(key(serverId, resourceId));
    }

    public void addServerId(String serverId, String resourceId) {
        serverIds.add(key(serverId, resourceId));
    }

    public void removeServerId(String serverId, String resourceId) {
        serverIds.remove(key(serverId, resourceId));
    }

    public boolean containsKey(String serverId, String resourceId) {
        String k = key(serverId, resourceId);
        return cache.containsKey(k) || drafts.containsKey(k) || serverIds.contains(k);
    }

    public boolean hasLoadedServerList(String serverId) {
        return loadedServerLists.contains(serverId);
    }

    public void remove(String serverId, String resourceId) {
        String k = key(serverId, resourceId);
        cache.remove(k);
        drafts.remove(k);
        serverIds.remove(k);
        names.remove(k);
        states.remove(k);
    }

    public void cache(String serverId, T item) {
        if (item == null) {
            return;
        }
        String id = idExtractor.apply(item);
        if (id == null) {
            return;
        }
        String k = key(serverId, id);
        cache.put(k, item);
        names.putIfAbsent(k, defaultNameExtractor.apply(item));
        serverIds.add(k);
        if (drafts.containsKey(k)) {
            states.put(k, SyncedResourceState.STALE);
        } else {
            states.put(k, SyncedResourceState.CLEAN);
        }
    }

    public void markSaved(String serverId, String resourceId) {
        String k = key(serverId, resourceId);
        serverIds.add(k);
        T draft = drafts.remove(k);
        if (draft != null) {
            cache.put(k, draft);
        }
        states.put(k, SyncedResourceState.SAVED);
    }

    public void setPendingParent(String serverId, String resourceId, Object parent) {
        pendingParents.put(key(serverId, resourceId), parent);
    }

    public Object removePendingParent(String serverId, String resourceId) {
        return pendingParents.remove(key(serverId, resourceId));
    }

    public boolean rename(String serverId, String oldId, String newId, BiConsumer<T, String> applyRename) {
        if (serverId == null || oldId == null || newId == null) {
            return false;
        }
        String trimmedId = newId.trim();
        if (trimmedId.isEmpty()) {
            return false;
        }
        String oldKey = key(serverId, oldId);
        String newKey = key(serverId, trimmedId);
        if (oldKey.equals(newKey)) {
            return false;
        }
        if (cache.containsKey(newKey) || drafts.containsKey(newKey) || serverIds.contains(newKey)) {
            return false;
        }

        T item = cache.get(oldKey);
        boolean wasDraft = false;
        if (item == null) {
            item = drafts.get(oldKey);
            wasDraft = true;
        }
        if (item == null) {
            return false;
        }

        boolean wasServer = serverIds.contains(oldKey);
        applyRename.accept(item, trimmedId);

        cache.remove(oldKey);
        drafts.remove(oldKey);

        if (wasDraft && !wasServer) {
            drafts.put(newKey, item);
        } else {
            cache.put(newKey, item);
        }

        serverIds.remove(oldKey);
        SyncedResourceState state = states.remove(oldKey);
        if (wasServer) {
            serverIds.add(newKey);
        }
        if (state != null) {
            states.put(newKey, state);
        }

        return true;
    }

    public String resolveDisplayName(String serverId, String oldId, String newId) {
        String oldKey = key(serverId, oldId);
        String displayName = names.remove(oldKey);
        if (displayName == null || displayName.isBlank() || displayName.equals(oldId)) {
            displayName = newId;
        }
        String newKey = key(serverId, newId);
        names.put(newKey, displayName);
        return displayName;
    }

    public void applyServerList(String serverId, List<String> ids) {
        String prefix = serverId + ":";
        Set<String> listedKeys = new HashSet<>();
        Set<String> previouslyServerBacked = new HashSet<>();
        for (String key : serverIds) {
            if (key.startsWith(prefix)) {
                previouslyServerBacked.add(key);
            }
        }
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    listedKeys.add(prefix + id);
                }
            }
        }
        drafts.keySet().removeIf(k -> k.startsWith(prefix) && previouslyServerBacked.contains(k) && !listedKeys.contains(k));
        cache.keySet().removeIf(k -> k.startsWith(prefix) && !listedKeys.contains(k) && !drafts.containsKey(k));
        names.keySet().removeIf(k -> k.startsWith(prefix) && !listedKeys.contains(k) && !drafts.containsKey(k));
        states.keySet().removeIf(k -> k.startsWith(prefix) && !listedKeys.contains(k) && !drafts.containsKey(k));
        pendingParents.keySet().removeIf(k -> k.startsWith(prefix) && !listedKeys.contains(k) && !drafts.containsKey(k));
        serverIds.removeIf(k -> k.startsWith(prefix));
        loadedServerLists.add(serverId);
        for (String k : listedKeys) {
            String id = stripPrefix(k, serverId);
            serverIds.add(k);
            names.putIfAbsent(k, id);
            states.putIfAbsent(k, SyncedResourceState.CLEAN);
        }
    }

    public List<String> getResourceIds(String serverId) {
        List<String> result = new ArrayList<>();
        String prefix = serverId + ":";
        for (String k : serverIds) {
            if (k.startsWith(prefix)) {
                result.add(k.substring(prefix.length()));
            }
        }
        return result;
    }
}
