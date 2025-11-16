package redxax.oxy.remotely.data.managed;

import java.util.UUID;

public class PlayerAction {
    public String uuid;
    public String name = "New Action";
    public String icon = "minecraft.png";
    public String command = "say Hello $name";

    public PlayerAction() {
        this.uuid = UUID.randomUUID().toString();
    }

    public PlayerAction(String name, String icon, String command) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.icon = icon;
        this.command = command;
    }
}