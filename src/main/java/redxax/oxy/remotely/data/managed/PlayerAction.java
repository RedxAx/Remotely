package redxax.oxy.remotely.data.managed;

import java.util.UUID;

public class PlayerAction {
    public String uuid;
    public String name;
    public String icon;
    public String command;

    public PlayerAction(String name, String icon, String command) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.icon = icon;
        this.command = command;
    }
}