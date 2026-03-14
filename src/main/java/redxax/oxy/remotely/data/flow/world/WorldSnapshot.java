package redxax.oxy.remotely.data.flow.world;

import java.util.List;

public class WorldSnapshot {
    private List<WorldDashboardEntry> dashboard;
    private List<WorldRegistryEntry> worlds;
    private List<WorldPortal> portals;
    private List<WorldInventoryGroup> inventoryGroups;
    private List<WorldSignPortal> signPortals;
    private List<WorldGameRuleDescriptor> gameRuleDescriptors;
    private List<WorldGeneratorDescriptor> generatorDescriptors;
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

    public List<WorldInventoryGroup> getInventoryGroups() {
        return inventoryGroups == null ? List.of() : inventoryGroups;
    }

    public List<WorldSignPortal> getSignPortals() {
        return signPortals == null ? List.of() : signPortals;
    }

    public List<WorldGameRuleDescriptor> getGameRuleDescriptors() {
        return gameRuleDescriptors == null ? List.of() : gameRuleDescriptors;
    }

    public List<WorldGeneratorDescriptor> getGeneratorDescriptors() {
        return generatorDescriptors == null ? List.of() : generatorDescriptors;
    }

    public List<String> getGeneratorHints() {
        return generatorHints == null ? List.of() : generatorHints;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }
}
