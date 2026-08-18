package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.util.BrowserSafeState;
import redxax.oxy.remotely.flow.data.FlowJson;

import java.util.Map;
import java.util.Objects;

public interface ReSyncStorage {
    String read(String key);

    void write(String key, String value);

    void remove(String key);

    default <T> T readObject(String key, Class<T> type) {
        return null;
    }

    default void writeObject(String key, Object value) {
    }

    @FunctionalInterface
    interface Provider {
        ReSyncStorage create(Object key);
    }

    class ProviderState {
        private static volatile Provider value = key ->
            ReSyncStorage.memory("legacy:" + FlowJson.text(key));

        private ProviderState() {
        }
    }

    static ReSyncStorage memory() {
        return memory("");
    }

    static ReSyncStorage memory(String namespace) {
        return new Memory(namespace == null ? "" : namespace);
    }

    static void installDesktop(Provider provider) {
        ProviderState.value = Objects.requireNonNull(provider, "provider");
    }

    static ReSyncStorage legacy(Object key) {
        if (key instanceof ReSyncStorage storage) {
            return storage;
        }
        return ProviderState.value.create(key);
    }

    final class Memory implements ReSyncStorage {
        private static final Map<String, Map<String, Object>> VALUES = BrowserSafeState.map();
        private final Map<String, Object> values;

        private Memory(String namespace) {
            values = VALUES.computeIfAbsent(namespace, ignored -> BrowserSafeState.map());
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
        public <T> T readObject(String key, Class<T> type) {
            Object value = key == null ? null : values.get(key);
            return type != null && type.isInstance(value) ? type.cast(value) : null;
        }

        @Override
        public void writeObject(String key, Object value) {
            if (key == null) return;
            if (value == null) values.remove(key);
            else values.put(key, value);
        }
    }
}
