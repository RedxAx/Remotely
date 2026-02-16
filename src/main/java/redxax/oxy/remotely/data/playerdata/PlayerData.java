package redxax.oxy.remotely.data.playerdata;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record PlayerData(double health, int food, float saturation, int experienceLevel, float experienceProgress,
                         int totalExperience, PlayerLocation location, String gameMode, boolean flying,
                         boolean fallFlying, List<PlayerItem> inventory, List<PlayerItem> armor,
                         List<PlayerItem> offhand, PlayerEnderChest enderChest, List<PlayerEffect> effects,
                         List<PlayerAttributeValue> attributes, Map<String, Object> statistics,
                         List<PlayerStatistic> flattenedStatistics, long lastModified, boolean onlineOnly) {

    public static PlayerData empty() {
        return new PlayerData(-1, -1, -1f, -1, -1f, -1, null, null, false, false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PlayerEnderChest.empty(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), 0L, false);
    }
}
