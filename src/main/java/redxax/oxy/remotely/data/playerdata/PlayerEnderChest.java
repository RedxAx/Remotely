package redxax.oxy.remotely.data.playerdata;

import java.util.Collections;
import java.util.List;

public record PlayerEnderChest(List<PlayerItem> items) {
    public static PlayerEnderChest empty() {
        return new PlayerEnderChest(Collections.emptyList());
    }
}
