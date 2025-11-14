package redxax.oxy.remotely.msmp.dto;

import java.util.UUID;

public class Player {
    public String name;
    public UUID uuid;
    public int ping;
    public boolean isOperator;
    public String address;

    public Player(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }
}