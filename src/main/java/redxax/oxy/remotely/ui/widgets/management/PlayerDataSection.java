package redxax.oxy.remotely.ui.widgets.management;

public enum PlayerDataSection {
    Overview("Overview"),
    Inventory("Inventory"),
    EnderChest("Ender Chest"),
    Effects("Effects"),
    Stats("Stats");

    private final String label;

    PlayerDataSection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
