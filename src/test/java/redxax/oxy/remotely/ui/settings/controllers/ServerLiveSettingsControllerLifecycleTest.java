package redxax.oxy.remotely.ui.settings.controllers;

import org.junit.jupiter.api.Test;
import restudio.rescreen.platform.Async;
import restudio.rescreen.theme.ThemeManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerLiveSettingsControllerLifecycleTest {
    @Test
    void repeatedSettingsAndCleanupBalanceSubscriptions() {
        ThemeManager.Snapshot theme = ThemeManager.snapshot();
        ThemeManager.initBrowserDefaults();
        try {
            verifyBalancedSubscriptions();
        } finally {
            ThemeManager.restore(theme);
        }
    }

    private void verifyBalancedSubscriptions() {
        CountingProvider provider = new CountingProvider();
        ServerGameRulesSettingsController rules = new ServerGameRulesSettingsController(provider);
        ServerLiveSettingsController live = new ServerLiveSettingsController(provider);

        rules.getSettings();
        rules.getSettings();
        live.getSettings();
        live.getSettings();

        assertEquals(2, provider.stateSubscriptions.get());
        assertEquals(2, provider.statusSubscriptions.get());

        rules.cleanup();
        rules.cleanup();
        live.cleanup();
        live.cleanup();

        assertEquals(2, provider.stateCleanup.get());
        assertEquals(2, provider.statusCleanup.get());
    }

    private static final class CountingProvider implements ServerLiveSettingsProvider {
        private final AtomicInteger stateSubscriptions = new AtomicInteger();
        private final AtomicInteger statusSubscriptions = new AtomicInteger();
        private final AtomicInteger stateCleanup = new AtomicInteger();
        private final AtomicInteger statusCleanup = new AtomicInteger();

        @Override public String status() { return "Live Settings Are Unavailable"; }
        @Override public boolean connected() { return false; }
        @Override public void connect() { }

        @Override
        public Subscription listenState(Runnable listener) {
            stateSubscriptions.incrementAndGet();
            return stateCleanup::incrementAndGet;
        }

        @Override
        public Subscription listenStatus(Consumer<String> listener) {
            statusSubscriptions.incrementAndGet();
            return statusCleanup::incrementAndGet;
        }

        @Override public Async<List<GameRuleValue>> gameRules() { return Async.completed(List.of()); }
        @Override public Async<Void> setGameRule(String name, String value) { return Async.completed(null); }
        @Override public Async<List<LiveSettingValue>> liveSettings() { return Async.completed(List.of()); }
        @Override public Async<Void> setLiveSetting(LiveSettingValue setting, String value) { return Async.completed(null); }
    }
}
