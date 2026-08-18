package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.data.flow.ReSyncStorage;
import redxax.oxy.remotely.util.BrowserSafeState;

import java.util.Map;

public final class BrowserReSyncStorage implements ReSyncStorage {
    private static final Map<String, Map<String, Object>> VALUES = BrowserSafeState.map();
    private final Map<String, Object> values;

    private BrowserReSyncStorage(String namespace) {
        values = VALUES.computeIfAbsent(namespace == null ? "" : namespace, ignored -> BrowserSafeState.map());
    }

    public static ReSyncStorage fromKey(Object key) {
        return new BrowserReSyncStorage(String.valueOf(key));
    }

    @Override
    public String read(String key) {
        Object value = key == null ? null : values.get(key);
        return value instanceof String text ? text : null;
    }

    @Override
    public void write(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    @Override
    public void remove(String key) {
        if (key != null) {
            values.remove(key);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readObject(String key, Class<T> type) {
        Object value = key == null ? null : values.get(key);
        return value == null ? null : (T) value;
    }

    @Override
    public void writeObject(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }
}
