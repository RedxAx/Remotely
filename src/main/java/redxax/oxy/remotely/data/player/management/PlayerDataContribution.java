package redxax.oxy.remotely.data.player.management;

import redxax.oxy.remotely.data.playerdata.PlayerData;

import java.util.Set;

public record PlayerDataContribution(PlayerData data, Set<PlayerSection> sections, String source, long updatedAt, long connectionGeneration, long revision) {
}
