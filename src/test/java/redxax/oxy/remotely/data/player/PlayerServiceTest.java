package redxax.oxy.remotely.data.player;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.data.player.source.IPlayerSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerServiceTest {
    @Test
    void refreshUsesStableSourceSnapshotWhenSourcesChange() {
        PlayerService service = new PlayerService();
        AtomicInteger addedRefreshes = new AtomicInteger();
        IPlayerSource added = source(addedRefreshes::incrementAndGet);
        boolean[] registered = {false};
        service.registerSource(new IPlayerSource() {
            @Override
            public void init(PlayerService context) {
            }

            @Override
            public void enable() {
            }

            @Override
            public void disable() {
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void refresh() {
                if (!registered[0]) {
                    registered[0] = true;
                    service.registerSource(added);
                }
            }
        });

        assertDoesNotThrow(service::refreshSources);
        assertEquals(0, addedRefreshes.get());

        service.refreshSources();
        assertEquals(1, addedRefreshes.get());
        service.shutdown();
    }

    private static IPlayerSource source(Runnable refresh) {
        return new IPlayerSource() {
            @Override
            public void init(PlayerService context) {
            }

            @Override
            public void enable() {
            }

            @Override
            public void disable() {
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void refresh() {
                refresh.run();
            }
        };
    }
}
