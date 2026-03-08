package redxax.oxy.remotely.data.flow.world;

import java.util.List;

public class WorldSnapshot {
    private List<WorldDashboardEntry> dashboard;
    private List<WorldRegistryEntry> worlds;
    private List<WorldPortal> portals;
    private List<WorldGameRuleDescriptor> gameRuleDescriptors;
    private List<String> generatorHints;
    private long generatedAt;

    public List<WorldDashboardEntry> getDashboard() {
        return dashboard == null ? List.of() : dashboard;
    }

    public List<WorldRegistryEntry> getWorlds() {
        return worlds == null ? List.of() : worlds;
    }

    public List<WorldPortal> getPortals() {
        return portals == null ? List.of() : portals;
    }

    public List<WorldGameRuleDescriptor> getGameRuleDescriptors() {
        return gameRuleDescriptors == null ? List.of() : gameRuleDescriptors;
    }

    public List<String> getGeneratorHints() {
        return generatorHints == null ? List.of() : generatorHints;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }
}
