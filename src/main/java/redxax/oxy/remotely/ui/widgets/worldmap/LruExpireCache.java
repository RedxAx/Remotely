package redxax.oxy.remotely.ui.widgets.worldmap;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LruExpireCache<K, V> {
    private final int maxSize;
    private final long expireAfterAccessMillis;
    private final LinkedHashMap<K, Entry<V>> map;
    private final ScheduledExecutorService cleaner;

    private static class Entry<V> {
        V value;
        long lastAccess;
        Entry(V value, long lastAccess) { this.value = value; this.lastAccess = lastAccess; }
    }

    public LruExpireCache(int maxSize, long expireAfterAccessMillis) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize > 0 required");
        this.maxSize = maxSize;
        this.expireAfterAccessMillis = Math.max(0, expireAfterAccessMillis);

        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                return size() > LruExpireCache.this.maxSize;
            }
        };

        if (this.expireAfterAccessMillis > 0) {
            this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LruExpireCache-cleaner");
                t.setDaemon(true);
                return t;
            });
            long period = Math.max(1000, this.expireAfterAccessMillis);
            this.cleaner.scheduleAtFixedRate(this::cleanUp, period, period, TimeUnit.MILLISECONDS);
        } else {
            this.cleaner = null;
        }
    }

    public synchronized V get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return null;
        if (isExpired(e)) {
            map.remove(key);
            return null;
        }
        e.lastAccess = System.currentTimeMillis();
        return e.value;
    }

    public synchronized V getIfPresent(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return null;
        if (isExpired(e)) {
            map.remove(key);
            return null;
        }
        return e.value;
    }

    public synchronized void put(K key, V value) {
        map.put(key, new Entry<>(value, System.currentTimeMillis()));
    }

    public synchronized void invalidateAll() {
        map.clear();
    }

    public synchronized int size() {
        return map.size();
    }

    public void shutdown() {
        if (cleaner != null) {
            cleaner.shutdownNow();
        }
    }

    private synchronized void cleanUp() {
        if (expireAfterAccessMillis <= 0) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<K, Entry<V>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Entry<V>> me = it.next();
            Entry<V> e = me.getValue();
            if (now - e.lastAccess >= expireAfterAccessMillis) {
                it.remove();
            }
        }
    }

    private boolean isExpired(Entry<V> e) {
        return expireAfterAccessMillis > 0 && (System.currentTimeMillis() - e.lastAccess) >= expireAfterAccessMillis;
    }
}
