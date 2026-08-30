package redxax.oxy.remotely.data.flow.world;

public class WorldGeneratorDescriptor {
    private String id;
    private String displayName;
    private boolean builtIn;
    private boolean configurable;
    private String configPlaceholder;
    private String defaultConfig;

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public boolean isConfigurable() {
        return configurable;
    }

    public String getConfigPlaceholder() {
        return configPlaceholder;
    }

    public String getDefaultConfig() {
        return defaultConfig;
    }

    void setId(String id) {
        this.id = id;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    void setConfigurable(boolean configurable) {
        this.configurable = configurable;
    }

    void setConfigPlaceholder(String configPlaceholder) {
        this.configPlaceholder = configPlaceholder;
    }

    void setDefaultConfig(String defaultConfig) {
        this.defaultConfig = defaultConfig;
    }
}
