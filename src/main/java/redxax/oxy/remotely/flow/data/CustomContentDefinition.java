package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomContentDefinition {
    private String id;
    private String flowId;
    private String type;
    private String displayName;
    private String provider = "vanilla";
    private String externalId = "";
    private String material = "STICK";
    private Integer customModelData;
    private String armorSlot = "";
    private int version = 1;
    private FlowGraph graph;
    private List<String> lore = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<CustomAbilityBinding> abilities = new ArrayList<>();
    private Map<String, Object> components = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public Integer getCustomModelData() { return customModelData; }
    public void setCustomModelData(Integer customModelData) { this.customModelData = customModelData; }
    public String getArmorSlot() { return armorSlot; }
    public void setArmorSlot(String armorSlot) { this.armorSlot = armorSlot; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public FlowGraph getGraph() { return graph; }
    public void setGraph(FlowGraph graph) { this.graph = graph; }
    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore != null ? lore : new ArrayList<>(); }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public List<CustomAbilityBinding> getAbilities() { return abilities; }
    public void setAbilities(List<CustomAbilityBinding> abilities) { this.abilities = abilities != null ? abilities : new ArrayList<>(); }
    public Map<String, Object> getComponents() { return components; }
    public void setComponents(Map<String, Object> components) { this.components = components != null ? new LinkedHashMap<>(components) : new LinkedHashMap<>(); }
}
