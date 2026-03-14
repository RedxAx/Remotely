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
}
