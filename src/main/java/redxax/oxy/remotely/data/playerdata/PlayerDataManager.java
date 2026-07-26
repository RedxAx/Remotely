package redxax.oxy.remotely.data.playerdata;

import redxax.oxy.remotely.data.player.model.PlayerAttribute;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;

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
        List<PlayerDataSource> chain = sources.stream()
                .filter(s -> online || !s.isOnlineOnly())
                .sorted(comparator)
                .toList();
        if (chain.isEmpty()) {
            return CompletableFuture.completedFuture(PlayerData.empty());
        }
        return tryChain(chain, 0, uuid, name, online);
    }

    public CompletableFuture<PlayerData> refreshIfDue(UUID uuid, String name, boolean online, long minIntervalMs) {
        if (uuid == null) return CompletableFuture.completedFuture(PlayerData.empty());
        synchronized (inFlight) {
            long now = System.currentTimeMillis();
            CompletableFuture<PlayerData> current = inFlight.get(uuid);
            if (current != null && !current.isDone()) return current;
            PlayerAttribute<PlayerData> cached = dataByPlayer.get(uuid);
            Long last = lastRefreshByPlayer.get(uuid);
            if (cached != null && cached.getValue() != null && last != null && minIntervalMs > 0 && now - last < minIntervalMs) {
                return CompletableFuture.completedFuture(cached.getValue());
            }
            CompletableFuture<PlayerData> next = refresh(uuid, name, online).thenApply(data -> {
                if (data == null || isEffectivelyEmpty(data)) throw new CompletionException(new IllegalStateException("Player Data Unavailable"));
                return data;
            });
            inFlight.put(uuid, next);
            next.whenComplete((data, error) -> {
                inFlight.remove(uuid, next);
            });
            return next;
        }
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
            PlayerData next = snapshot.data();
            PlayerAttribute<PlayerData> cached = dataByPlayer.get(uuid);
            if ("world".equalsIgnoreCase(snapshot.source()) && online) {
                next = preserveStatsIfMissing(next, cached);
            }
            if ("rcon".equalsIgnoreCase(snapshot.source())) {
                next = preserveStatsIfMissing(next, cached);
            }
            if (("rcon".equalsIgnoreCase(snapshot.source()) || "rcon-core".equalsIgnoreCase(snapshot.source())) && index + 1 < chain.size()) {
                PlayerData live = next;
                return tryChain(chain, index + 1, uuid, name, online).thenApply(saved -> {
                    PlayerData merged = mergeRcon(live, saved, "rcon-core".equalsIgnoreCase(snapshot.source()));
                    String savedSource = getSource(uuid);
                    String mergedSource = !isEffectivelyEmpty(saved) && "world".equalsIgnoreCase(savedSource) ? snapshot.source() + "+world" : snapshot.source();
                    dataByPlayer.put(uuid, new PlayerAttribute<>(merged, mergedSource, snapshot.priority()));
                    lastRefreshByPlayer.put(uuid, System.currentTimeMillis());
                    return merged;
                });
            }
            dataByPlayer.put(uuid, new PlayerAttribute<>(next, snapshot.source(), snapshot.priority()));
            lastRefreshByPlayer.put(uuid, System.currentTimeMillis());
            return CompletableFuture.completedFuture(next);
        }).exceptionallyCompose(ex -> {
            ReLog.logger(LogTypes.MINECRAFT).source(LogSource.resource(source.getId(), source.getId())).component(PlayerDataManager.class).operation("Load Player Data").error("Player data source failed", ex);
            return tryChain(chain, index + 1, uuid, name, online);
        });
    }

    private PlayerData preserveStatsIfMissing(PlayerData next, PlayerAttribute<PlayerData> cached) {
        if (next == null || cached == null || cached.getValue() == null) {
            return next;
        }
        Map<String, Object> currentStats = cached.getValue().statistics();
        if (currentStats == null || currentStats.isEmpty()) {
            return next;
        }
        Map<String, Object> nextStats = next.statistics();
        if (nextStats != null && !nextStats.isEmpty()) {
            return next;
        }
        return new PlayerData(next.health(), next.food(), next.saturation(), next.experienceLevel(),
                next.experienceProgress(), next.totalExperience(), next.location(), next.gameMode(),
                next.flying(), next.fallFlying(), next.inventory(), next.armor(), next.offhand(),
                next.enderChest(), next.effects(), next.attributes(),
                currentStats, cached.getValue().flattenedStatistics(),
                next.lastModified(), next.onlineOnly());
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

    private PlayerData mergeRcon(PlayerData live, PlayerData saved, boolean coreOnly) {
        if (saved == null || isEffectivelyEmpty(saved)) return live;
        PlayerData base = coreOnly ? saved : live;
        return new PlayerData(live.health() >= 0 ? live.health() : saved.health(), live.food() >= 0 ? live.food() : saved.food(), live.saturation() >= 0 ? live.saturation() : saved.saturation(),
                live.experienceLevel() >= 0 ? live.experienceLevel() : saved.experienceLevel(), live.experienceProgress() >= 0 ? live.experienceProgress() : saved.experienceProgress(),
                live.totalExperience() >= 0 ? live.totalExperience() : saved.totalExperience(), live.location() != null ? live.location() : saved.location(),
                live.gameMode() != null ? live.gameMode() : saved.gameMode(), live.flying(), live.fallFlying(), base.inventory(), base.armor(), base.offhand(), base.enderChest(),
                base.effects(), base.attributes(), saved.statistics(), saved.flattenedStatistics(), Math.max(live.lastModified(), saved.lastModified()), true);
    }

    public long getLastRefreshAt(UUID uuid) {
        return uuid == null ? 0L : lastRefreshByPlayer.getOrDefault(uuid, 0L);
    }
}
