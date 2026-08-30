package redxax.oxy.remotely.data.player.management;

import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.BrowserSafeState;
import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.player.PlayerFacetState;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.player.IPlayerHistoryProvider;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.platform.TaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import restudio.rescreen.platform.Async;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Set;
import java.util.EnumSet;

public final class PlayerManagementService {
    private final PlayerDataManager dataManager;
    private final IPlayerHistoryProvider historyProvider;
    private final Function<UUID, PlayerDossier> dossierGetter;
    private final Consumer<UUID> dossierRequester;
    private final Supplier<Boolean> reSyncReady;
    private final LocalPlayerManagementProvider localProvider;
    private final Map<UUID, Entry> entries = BrowserSafeState.map();
    private final TaskScheduler scheduler;
    private TaskScheduler.ScheduledTask pollingTask;

    public PlayerManagementService(PlayerDataManager dataManager, IPlayerHistoryProvider historyProvider, Function<UUID, PlayerDossier> dossierGetter,
                                   Consumer<UUID> dossierRequester, Supplier<Boolean> reSyncReady) {
        this(dataManager, historyProvider, dossierGetter, dossierRequester, reSyncReady, TaskSchedulers.current());
    }

    public PlayerManagementService(PlayerDataManager dataManager, IPlayerHistoryProvider historyProvider, Function<UUID, PlayerDossier> dossierGetter,
                                   Consumer<UUID> dossierRequester, Supplier<Boolean> reSyncReady, TaskScheduler scheduler) {
        this.dataManager = dataManager;
        this.historyProvider = historyProvider;
        this.dossierGetter = dossierGetter;
        this.dossierRequester = dossierRequester;
        this.reSyncReady = reSyncReady;
        this.localProvider = new LocalPlayerManagementProvider();
        this.scheduler = scheduler == null ? TaskScheduler.unavailable() : scheduler;
    }

    public Subscription subscribe(UnifiedPlayer player, Consumer<PlayerManagementSnapshot> listener) {
        Entry entry = entries.computeIfAbsent(player.getUuid(), ignored -> new Entry(player));
        entry.player = player;
        entry.listeners.add(listener);
        ensurePolling();
        if (entry.snapshot != null) dispatch(entry, listener, entry.snapshot);
        refresh(player, true);
        return () -> {
            entry.listeners.remove(listener);
            if (entry.listeners.isEmpty()) entries.remove(player.getUuid(), entry);
            stopPollingIfIdle();
        };
    }

    public PlayerManagementSnapshot getSnapshot(UnifiedPlayer player) {
        Entry entry = entries.get(player.getUuid());
        return entry != null ? entry.snapshot : initial(player);
    }

    public void refresh(UnifiedPlayer player, boolean force) {
        Entry entry = entries.computeIfAbsent(player.getUuid(), ignored -> new Entry(player));
        entry.player = player;
        if (!entry.refreshing.compareAndSet(false, true)) {
            if (force) {
                entry.forceAfterRefresh.set(true);
                if (!entry.refreshing.get() && entry.forceAfterRefresh.getAndSet(false)) refresh(entry.player, true);
            }
            return;
        }
        long generation = entry.generation.incrementAndGet();
        PlayerManagementSnapshot beforeRefresh = entry.snapshot;
        scheduler.schedule(() -> {
            if (!entry.refreshing.get() || generation != entry.generation.get() || entry.snapshot != beforeRefresh) return;
            entry.snapshot = loadingSnapshot(entry, generation);
            notifyListeners(entry);
        }, Duration.ofMillis(300));
        dossierRequester.accept(player.getUuid());

        long minInterval = force ? 0L : 2000L;
        Async<PlayerDataContribution> dataFuture = AsyncTools.withTimeout(localProvider.refresh(player.getUuid(), player.getName(), player.isOnline(), minInterval), scheduler, Duration.ofSeconds(5));
        dataFuture.whenComplete((contribution, error) -> {
            if (generation != entry.generation.get()) return;
            if (error == null && contribution != null && contribution.data() != null) {
                entry.localData = contribution.data();
                entry.localSource = contribution.source();
                entry.localUpdatedAt = contribution.updatedAt();
                entry.localSections = contribution.sections();
                entry.dataFailure = null;
            } else {
                entry.dataFailure = error != null ? error.getMessage() : "Player Data Unavailable";
            }
            rebuild(entry, generation);
        });

        Async<List<PlayerSession>> historyFuture = historyProvider != null
                ? AsyncTools.withTimeout(historyProvider.getSessions(player.getUuid()), scheduler, Duration.ofSeconds(5))
                : Async.completed(List.of());
        if (historyProvider != null) {
            historyFuture.whenComplete((sessions, error) -> {
                if (generation != entry.generation.get()) return;
                if (error == null && sessions != null) {
                    entry.sessions = List.copyOf(sessions);
                    entry.historyFailure = null;
                }
                else entry.historyFailure = error != null ? error.getMessage() : "History Unavailable";
                rebuild(entry, generation);
            });
        }
        Async.allOf(dataFuture.handle((value, error) -> null), historyFuture.handle((value, error) -> null)).whenComplete((ignored, error) -> {
            entry.refreshing.set(false);
            if (entry.forceAfterRefresh.getAndSet(false)) refresh(entry.player, true);
        });
        syncDossier(entry, generation);
    }

    public void shutdown() {
        if (pollingTask != null) pollingTask.cancel();
        entries.clear();
    }

    private synchronized void ensurePolling() {
        if (pollingTask != null && !pollingTask.isCancelled()) return;
        pollingTask = scheduler.scheduleAtFixedRate(this::poll, Duration.ofMillis(250), Duration.ofMillis(250));
    }

    private synchronized void stopPollingIfIdle() {
        if (!entries.isEmpty() || pollingTask == null) return;
        pollingTask.cancel();
        pollingTask = null;
    }

    private void poll() {
        long now = System.currentTimeMillis();
        for (Entry entry : entries.values()) {
            boolean online = entry.player.isOnline();
            if (entry.lastOnline && !online) {
                entry.lastOnline = false;
                if (entry.refreshing.get()) entry.forceAfterRefresh.set(true);
                else refresh(entry.player, true);
                continue;
            }
            entry.lastOnline = online;
            boolean connected = reSyncReady.get();
            if (connected != entry.lastTransportConnected) rebuild(entry, entry.generation.get());
            syncDossier(entry, entry.generation.get());
            PlayerDataContribution reSync = ReSyncPlayerDataAdapter.adapt(entry.dossier, entry.generation.get());
            boolean needsFallback = !reSyncReady.get() || reSync == null || !reSync.sections().containsAll(EnumSet.of(PlayerSection.OVERVIEW, PlayerSection.INVENTORY, PlayerSection.ENDER_CHEST, PlayerSection.EFFECTS));
            if (entry.player.isOnline() && needsFallback && now - entry.lastDataRefreshAt >= 2000L) {
                entry.lastDataRefreshAt = now;
                refresh(entry.player, false);
            }
        }
    }

    private void syncDossier(Entry entry, long generation) {
        PlayerDossier next = dossierGetter.apply(entry.player.getUuid());
        if (next == entry.dossier) return;
        entry.dossier = next;
        rebuild(entry, generation);
    }

    private void rebuild(Entry entry, long generation) {
        if (generation != entry.generation.get()) return;
        PlayerDataContribution reSync = ReSyncPlayerDataAdapter.adapt(entry.dossier, generation);
        boolean transportConnected = reSyncReady.get();
        if (entry.lastTransportConnected && !transportConnected) entry.dossierAtDisconnect = entry.dossier;
        if (transportConnected && entry.dossier != entry.dossierAtDisconnect) entry.dossierAtDisconnect = null;
        entry.lastTransportConnected = transportConnected;
        boolean reSyncConnected = transportConnected && (entry.dossierAtDisconnect == null || entry.dossier != entry.dossierAtDisconnect);
        PlayerData live = reSync != null && reSyncConnected && entry.player.isOnline() ? reSync.data() : null;
        PlayerData merged = merge(live, entry.localData);
        if (merged == null && reSync != null) merged = reSync.data();
        String source = live != null ? "resync" : entry.localSource != null ? entry.localSource : reSync != null ? "resync" : null;
        long updatedAt = live != null ? reSync.updatedAt() : entry.localUpdatedAt > 0L ? entry.localUpdatedAt : reSync != null ? reSync.updatedAt() : 0L;
        long revision = live != null ? reSync.revision() : 0L;

        EnumMap<PlayerSection, PlayerSectionState<?>> states = new EnumMap<>(PlayerSection.class);
        states.put(PlayerSection.OVERVIEW, PlayerSectionState.ready(merged, source, updatedAt, revision, false, merged == null));
        boolean hasPlayerDataSource = source != null && merged != null;
        boolean liveInventory = live != null && reSync.sections().contains(PlayerSection.INVENTORY);
        boolean liveEnderChest = live != null && reSync.sections().contains(PlayerSection.ENDER_CHEST);
        boolean liveEffects = live != null && reSync.sections().contains(PlayerSection.EFFECTS);
        boolean inventorySupported = liveInventory || entry.localSections.contains(PlayerSection.INVENTORY);
        boolean enderSupported = liveEnderChest || entry.localSections.contains(PlayerSection.ENDER_CHEST);
        boolean effectsSupported = liveEffects || entry.localSections.contains(PlayerSection.EFFECTS);
        states.put(PlayerSection.INVENTORY, dataState(merged, liveInventory ? "resync" : entry.localSource, liveInventory ? reSync.updatedAt() : entry.localUpdatedAt,
                liveInventory ? reSync.revision() : 0L, inventorySupported, merged == null || merged.inventory().isEmpty(), liveInventory, liveInventory ? null : entry.dataFailure));
        states.put(PlayerSection.ENDER_CHEST, dataState(merged, liveEnderChest ? "resync" : entry.localSource, liveEnderChest ? reSync.updatedAt() : entry.localUpdatedAt,
                liveEnderChest ? reSync.revision() : 0L, enderSupported, merged == null || merged.enderChest() == null || merged.enderChest().items().isEmpty(), liveEnderChest, liveEnderChest ? null : entry.dataFailure));
        states.put(PlayerSection.EFFECTS, dataState(merged, liveEffects ? "resync" : entry.localSource, liveEffects ? reSync.updatedAt() : entry.localUpdatedAt,
                liveEffects ? reSync.revision() : 0L, effectsSupported, merged == null || merged.effects().isEmpty(), false, liveEffects ? null : entry.dataFailure));
        boolean statsSupported = entry.localSections.contains(PlayerSection.STATS);
        states.put(PlayerSection.STATS, dataState(merged, entry.localSource, entry.localUpdatedAt, 0L, statsSupported, merged == null || merged.flattenedStatistics().isEmpty(), false, entry.dataFailure));
        boolean historySupported = historyProvider != null || entry.dossier != null;
        states.put(PlayerSection.HISTORY, historySupported ? historyState(entry, updatedAt) : PlayerSectionState.unsupported());
        boolean activitySupported = historyProvider != null || entry.dossier != null;
        boolean activityEmpty = entry.dossier == null || entry.dossier.getRecentEvents().isEmpty();
        states.put(PlayerSection.ACTIVITY, activitySupported ? activityState(entry, updatedAt, activityEmpty) : PlayerSectionState.unsupported());
        Map<String, PlayerFacetState> extensions = extensions(entry.dossier);
        states.put(PlayerSection.EXTENSIONS, extensions.isEmpty() ? PlayerSectionState.unsupported() : PlayerSectionState.ready(extensions, "resync", updatedAt, revision, false, false));
        if (!reSyncConnected && reSync != null && entry.localData == null) {
            states.put(PlayerSection.OVERVIEW, PlayerSectionState.stale(merged, "resync", reSync.updatedAt(), reSync.revision(), "ReSync Disconnected"));
            states.put(PlayerSection.INVENTORY, PlayerSectionState.stale(merged, "resync", reSync.updatedAt(), reSync.revision(), "ReSync Disconnected"));
            states.put(PlayerSection.ENDER_CHEST, PlayerSectionState.stale(merged, "resync", reSync.updatedAt(), reSync.revision(), "ReSync Disconnected"));
            states.put(PlayerSection.EFFECTS, PlayerSectionState.stale(merged, "resync", reSync.updatedAt(), reSync.revision(), "ReSync Disconnected"));
        }
        if (!reSyncConnected && entry.dossier != null) {
            states.put(PlayerSection.ACTIVITY, PlayerSectionState.stale(entry.dossier, "resync+local", updatedAt, revision, "ReSync Disconnected"));
            states.put(PlayerSection.HISTORY, PlayerSectionState.stale(entry.sessions, "resync+local", updatedAt, revision, "ReSync Disconnected"));
        }
        if (!reSyncConnected && !extensions.isEmpty()) states.put(PlayerSection.EXTENSIONS, PlayerSectionState.stale(extensions, "resync", updatedAt, revision, "ReSync Disconnected"));
        entry.snapshot = new PlayerManagementSnapshot(entry.player, merged, entry.dossier, entry.sessions, extensions, states, generation);
        notifyListeners(entry);
    }

    private PlayerSectionState<PlayerData> dataState(PlayerData data, String source, long updatedAt, long revision, boolean supported, boolean empty, boolean writable, String failure) {
        if (!supported) return failure == null ? PlayerSectionState.unsupported() : PlayerSectionState.failed(null, source, failure);
        if (failure != null) return PlayerSectionState.stale(data, source, updatedAt, revision, failure);
        return PlayerSectionState.ready(data, source, updatedAt, revision, writable, empty);
    }

    private PlayerSectionState<List<PlayerSession>> historyState(Entry entry, long updatedAt) {
        if (entry.historyFailure != null) return PlayerSectionState.failed(entry.sessions, "local", entry.historyFailure);
        return PlayerSectionState.ready(entry.sessions, historyProvider != null ? "local+resync" : "resync", updatedAt, 0L, false,
                entry.sessions.isEmpty() && (entry.dossier == null || entry.dossier.getSessions().isEmpty()));
    }

    private PlayerSectionState<PlayerDossier> activityState(Entry entry, long updatedAt, boolean empty) {
        if (entry.historyFailure != null && entry.dossier == null) return PlayerSectionState.failed(null, "local", entry.historyFailure);
        return PlayerSectionState.ready(entry.dossier, entry.dossier != null ? "resync+local" : "local", updatedAt, 0L, false, empty && entry.sessions.isEmpty());
    }

    private PlayerData merge(PlayerData live, PlayerData saved) {
        if (live == null) return saved;
        if (saved == null || saved.statistics().isEmpty()) return live;
        return new PlayerData(live.health(), live.food(), live.saturation(), live.experienceLevel(), live.experienceProgress(), live.totalExperience(), live.location(), live.gameMode(),
            live.flying(), live.fallFlying(), live.inventory(), live.armor(), live.offhand(), live.enderChest(), live.effects(), live.attributes(), saved.statistics(), saved.flattenedStatistics(),
            Math.max(live.lastModified(), saved.lastModified()), true);
    }

    private Map<String, PlayerFacetState> extensions(PlayerDossier dossier) {
        if (dossier == null) return Map.of();
        Map<String, PlayerFacetState> result = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerFacetState> entry : dossier.getFacets().entrySet()) {
            if (!"playerData".equals(entry.getKey()) && entry.getValue() != null) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private PlayerManagementSnapshot loadingSnapshot(Entry entry, long generation) {
        PlayerManagementSnapshot previous = entry.snapshot;
        EnumMap<PlayerSection, PlayerSectionState<?>> states = new EnumMap<>(PlayerSection.class);
        states.put(PlayerSection.OVERVIEW, PlayerSectionState.loading(previous != null ? previous.playerData() : null, previous != null ? previous.<PlayerData>section(PlayerSection.OVERVIEW).source() : null));
        for (PlayerSection section : PlayerSection.values()) {
            if (section == PlayerSection.OVERVIEW) continue;
            PlayerSectionState<?> old = previous != null ? previous.section(section) : PlayerSectionState.unsupported();
            states.put(section, old.supported() ? PlayerSectionState.loading(old.value(), old.source()) : old);
        }
        return new PlayerManagementSnapshot(entry.player, previous != null ? previous.playerData() : null, entry.dossier, entry.sessions, previous != null ? previous.extensionFacets() : Map.of(), states, generation);
    }

    private PlayerManagementSnapshot initial(UnifiedPlayer player) {
        EnumMap<PlayerSection, PlayerSectionState<?>> states = new EnumMap<>(PlayerSection.class);
        states.put(PlayerSection.OVERVIEW, PlayerSectionState.loading(null, null));
        return new PlayerManagementSnapshot(player, null, null, List.of(), Map.of(), states, 0L);
    }

    private void notifyListeners(Entry entry) {
        PlayerManagementSnapshot snapshot = entry.snapshot;
        if (snapshot == null) return;
        for (Consumer<PlayerManagementSnapshot> listener : entry.listeners) dispatch(entry, listener, snapshot);
    }

    private void dispatch(Entry entry, Consumer<PlayerManagementSnapshot> listener, PlayerManagementSnapshot snapshot) {
        ScreenManager.getInstance().execute(() -> {
            if (entry.listeners.contains(listener)) listener.accept(snapshot);
        });
    }

    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private static final class Entry {
        private volatile UnifiedPlayer player;
        private final BrowserSafeState.LongValue generation = new BrowserSafeState.LongValue();
        private final BrowserSafeState.BooleanValue refreshing = new BrowserSafeState.BooleanValue();
        private final BrowserSafeState.BooleanValue forceAfterRefresh = new BrowserSafeState.BooleanValue();
        private final List<Consumer<PlayerManagementSnapshot>> listeners = BrowserSafeState.list();
        private volatile PlayerManagementSnapshot snapshot;
        private volatile PlayerData localData;
        private volatile String localSource;
        private volatile long localUpdatedAt;
        private volatile PlayerDossier dossier;
        private volatile List<PlayerSession> sessions = new ArrayList<>();
        private volatile String dataFailure;
        private volatile String historyFailure;
        private volatile long lastDataRefreshAt;
        private volatile Set<PlayerSection> localSections = Set.of();
        private volatile boolean lastOnline;
        private volatile boolean lastTransportConnected;
        private volatile PlayerDossier dossierAtDisconnect;

        private Entry(UnifiedPlayer player) {
            this.player = player;
            this.lastOnline = player.isOnline();
        }
    }

    private final class LocalPlayerManagementProvider implements PlayerManagementProvider {
        @Override
        public String getId() {
            return "local";
        }

        @Override
        public PlayerProviderCapabilities capabilities(UUID playerId, boolean online) {
            return new PlayerProviderCapabilities(getId(), EnumSet.of(PlayerSection.OVERVIEW, PlayerSection.INVENTORY, PlayerSection.ENDER_CHEST, PlayerSection.EFFECTS, PlayerSection.STATS), Set.of());
        }

        @Override
        public Async<PlayerDataContribution> refresh(UUID playerId, String playerName, boolean online) {
            return refresh(playerId, playerName, online, 2000L);
        }

        private Async<PlayerDataContribution> refresh(UUID playerId, String playerName, boolean online, long minInterval) {
            return dataManager.refreshIfDue(playerId, playerName, online, minInterval).thenApply(data -> {
                String source = dataManager.getSource(playerId);
                EnumSet<PlayerSection> sections = EnumSet.of(PlayerSection.OVERVIEW);
                if ("world".equalsIgnoreCase(source) || "rcon".equalsIgnoreCase(source) || "rcon+world".equalsIgnoreCase(source) || "rcon-core+world".equalsIgnoreCase(source)) {
                    sections.add(PlayerSection.INVENTORY);
                    sections.add(PlayerSection.ENDER_CHEST);
                    sections.add(PlayerSection.EFFECTS);
                }
                if (source != null && source.toLowerCase().contains("world") || data != null && !data.statistics().isEmpty()) sections.add(PlayerSection.STATS);
                return new PlayerDataContribution(data, sections, source, dataManager.getLastRefreshAt(playerId), 0L, 0L);
            });
        }
    }
}
