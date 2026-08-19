package redxax.oxy.remotely.data.playerdata;

import org.junit.jupiter.api.Test;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerDataManagerTest {
    @Test
    void concurrentRefreshesShareOneFlightAndAtomicProvenance() {
        PlayerDataManager manager = new PlayerDataManager();
        AtomicInteger fetches = new AtomicInteger();
        CompletableFuture<PlayerDataSnapshot> sourceFuture = new CompletableFuture<>();
        manager.registerSource(new PlayerDataSource() {
            @Override
            public String getId() {
                return "test";
            }

            @Override
            public int getPriority() {
                return 100;
            }

            @Override
            public boolean isOnlineOnly() {
                return false;
            }

            @Override
            public Async<PlayerDataSnapshot> fetch(UUID uuid, String name) {
                fetches.incrementAndGet();
                return JvmAsyncBridge.fromFuture(sourceFuture);
            }
        });
        UUID playerId = UUID.randomUUID();

        Async<PlayerData> first = manager.refreshIfDue(playerId, "Player", false, 0L);
        Async<PlayerData> second = manager.refreshIfDue(playerId, "Player", false, 0L);

        assertSame(first, second);
        PlayerData data = PlayerData.empty();
        data = new PlayerData(20, data.food(), data.saturation(), data.experienceLevel(), data.experienceProgress(), data.totalExperience(), data.location(), data.gameMode(), data.flying(),
                data.fallFlying(), data.inventory(), data.armor(), data.offhand(), data.enderChest(), data.effects(), data.attributes(), data.statistics(), data.flattenedStatistics(), data.lastModified(), data.onlineOnly());
        sourceFuture.complete(new PlayerDataSnapshot(playerId, data, "test", 100));
        first.join();
        assertEquals(1, fetches.get());
        assertEquals("test", manager.getSource(playerId));
    }
}
