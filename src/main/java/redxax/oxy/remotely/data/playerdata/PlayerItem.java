package redxax.oxy.remotely.data.playerdata;

import java.util.Collections;
import java.util.Map;

public record PlayerItem(String id, int count, int slot, Map<String, Object> tag) {
    public static PlayerItem of(String id, int count, int slot) {
        return new PlayerItem(id, count, slot, Collections.emptyMap());
    }
}
