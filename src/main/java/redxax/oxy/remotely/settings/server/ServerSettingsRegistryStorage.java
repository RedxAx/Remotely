package redxax.oxy.remotely.settings.server;

public interface ServerSettingsRegistryStorage extends AutoCloseable {
    void loadExternalFile(String file);

    void watchExternalDirectory(String directory);

    void watchExternalDirectory(String directory, long debounceMillis);

    void reloadExternalDirectory();

    @Override
    void close();

    static ServerSettingsRegistryStorage unavailable() {
        return Unavailable.INSTANCE;
    }

    enum Unavailable implements ServerSettingsRegistryStorage {
        INSTANCE;

        @Override
        public void loadExternalFile(String file) {
            throw unavailable();
        }

        @Override
        public void watchExternalDirectory(String directory) {
            throw unavailable();
        }

        @Override
        public void watchExternalDirectory(String directory, long debounceMillis) {
            throw unavailable();
        }

        @Override
        public void reloadExternalDirectory() {
        }

        @Override
        public void close() {
        }

        private static UnsupportedOperationException unavailable() {
            return new UnsupportedOperationException("Server settings storage is unavailable on this platform");
        }
    }
}
