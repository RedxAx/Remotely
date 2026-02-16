package redxax.oxy.remotely.data.playerdata;

import redxax.oxy.remotely.data.player.model.PlayerAttribute;
import restudio.rescreen.debug.DebugManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlayerDataManager {
    private final ConcurrentMap<UUID, PlayerAttribute<PlayerData>> dataByPlayer = new ConcurrentHashMap<>();
    private final List<PlayerDataSource> sources = new ArrayList<>();
    private final ConcurrentMap<UUID, Long> lastRefreshByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<PlayerData>> inFlight = new ConcurrentHashMap<>();

    public void registerSource(PlayerDataSource source) {
        if (source == null) return;
        sources.add(source);
        sources.sort(Comparator.comparingInt(PlayerDataSource::getPriority).reversed());
    }

    public CompletableFuture<PlayerData> get(UUID uuid, String name, boolean online) {
        if (uuid == null) return CompletableFuture.completedFuture(PlayerData.empty());
        PlayerAttribute<PlayerData> cached = dataByPlayer.get(uuid);
        if (cached != null && cached.getValue() != null) {
            return CompletableFuture.completedFuture(cached.getValue());
        }
        return refresh(uuid, name, online);
    }

    public CompletableFuture<PlayerData> refresh(UUID uuid, String name, boolean online) {
        if (uuid == null) return CompletableFuture.completedFuture(PlayerData.empty());
        Comparator<PlayerDataSource> comparator = Comparator.comparingInt(PlayerDataSource::getPriority).reversed();
        if (online) {
            comparator = Comparator.comparingInt((PlayerDataSource s) -> s.isOnlineOnly() ? 1 : 0).reversed()
                    .thenComparing(comparator);
        }
        PlayerAttribute<PlayerData> cached = dataByPlayer.get(uuid);
        PlayerData cachedValue = cached != null ? cached.getValue() : null;

        List<PlayerDataSource> chain = sources.stream()
                .filter(s -> online || !s.isOnlineOnly())
                .sorted(comparator)
                .toList();
        if (online && cachedValue != null && cachedValue.onlineOnly()) {
            chain = chain.stream().filter(PlayerDataSource::isOnlineOnly).toList();
        }
        if (chain.isEmpty()) {
            return CompletableFuture.completedFuture(PlayerData.empty());
        }
        return tryChain(chain, 0, uuid, name, online);
    }

    public CompletableFuture<PlayerData> refreshIfDue(UUID uuid, String name, boolean online, long minIntervalMs) {
        if (uuid == null) return CompletableFuture.completedFuture(PlayerData.empty());
        long now = System.currentTimeMillis();
        Long last = lastRefreshByPlayer.get(uuid);
        CompletableFuture<PlayerData> flight = inFlight.get(uuid);
        if (flight != null && !flight.isDone()) {
            return flight;
        }
        PlayerAttribute<PlayerData> cached = dataByPlayer.get(uuid);
        if (cached != null && cached.getValue() != null && last != null && minIntervalMs > 0 && now - last < minIntervalMs) {
            return CompletableFuture.completedFuture(cached.getValue());
        }
        CompletableFuture<PlayerData> next = refresh(uuid, name, online)
                .handle((data, ex) -> {
                    if (ex != null) {
                        if (cached != null && cached.getValue() != null) return cached.getValue();
                        return PlayerData.empty();
                    }
                    if (data == null) {
                        if (cached != null && cached.getValue() != null) return cached.getValue();
                        return PlayerData.empty();
                    }
                    if (isEffectivelyEmpty(data)) {
                        if (cached != null && cached.getValue() != null) return cached.getValue();
                    }
                    return data;
                })
                .whenComplete((data, ex) -> inFlight.remove(uuid));
        CompletableFuture<PlayerData> existing = inFlight.putIfAbsent(uuid, next);
        if (existing != null) return existing;
        return next.whenComplete((data, ex) -> {
            if (minIntervalMs > 0) {
                lastRefreshByPlayer.put(uuid, System.currentTimeMillis());
            }
        });
    }

    private boolean isEffectivelyEmpty(PlayerData data) {
        if (data == null) return true;
        if (data.health() >= 0) return false;
        if (data.food() >= 0) return false;
        if (data.saturation() >= 0) return false;
        if (data.experienceLevel() >= 0) return false;
        if (data.experienceProgress() >= 0) return false;
        if (data.totalExperience() >= 0) return false;
        if (data.location() != null) return false;
        if (data.gameMode() != null && !data.gameMode().isBlank()) return false;
        if (data.inventory() != null && !data.inventory().isEmpty()) return false;
        if (data.armor() != null && !data.armor().isEmpty()) return false;
        if (data.offhand() != null && !data.offhand().isEmpty()) return false;
        if (data.enderChest() != null && data.enderChest().items() != null && !data.enderChest().items().isEmpty()) return false;
        if (data.effects() != null && !data.effects().isEmpty()) return false;
        if (data.attributes() != null && !data.attributes().isEmpty()) return false;
        if (data.statistics() != null && !data.statistics().isEmpty()) return false;
        if (data.flattenedStatistics() != null && !data.flattenedStatistics().isEmpty()) return false;
        return true;
    }

    private CompletableFuture<PlayerData> tryChain(List<PlayerDataSource> chain, int index, UUID uuid, String name, boolean online) {
        if (index >= chain.size()) {
            return CompletableFuture.completedFuture(PlayerData.empty());
        }
        PlayerDataSource source = chain.get(index);
        return source.fetch(uuid, name).thenCompose(snapshot -> {
            if (snapshot == null || snapshot.data() == null) {
                return tryChain(chain, index + 1, uuid, name, online);
            }
            dataByPlayer.put(uuid, new PlayerAttribute<>(snapshot.data(), snapshot.source(), snapshot.priority()));
            lastRefreshByPlayer.put(uuid, System.currentTimeMillis());
            return CompletableFuture.completedFuture(snapshot.data());
        }).exceptionallyCompose(ex -> {
            DebugManager.getInstance().log("PlayerData", source.getId() + " failed: " + ex.getMessage());
            return tryChain(chain, index + 1, uuid, name, online);
        });
    }

    public void clear(UUID uuid) {
        if (uuid != null) {
            dataByPlayer.remove(uuid);
            lastRefreshByPlayer.remove(uuid);
            inFlight.remove(uuid);
        }
    }

    public String getSource(UUID uuid) {
        if (uuid == null) return null;
        PlayerAttribute<PlayerData> cached = dataByPlayer.get(uuid);
        if (cached == null) return null;
        return cached.getSource();
    }
}
