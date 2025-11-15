package redxax.oxy.remotely.data.managed;

public class PlayerAction {
    public String name = "New Action";
    public String icon = "minecraft.png";
    public String command = "say Hello $name";

    public PlayerAction() {}

    public PlayerAction(String name, String icon, String command) {
        this.name = name;
        this.icon = icon;
        this.command = command;
    }
}