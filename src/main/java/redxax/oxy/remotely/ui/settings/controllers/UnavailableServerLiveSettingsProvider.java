package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.platform.Async;

import java.util.List;
import java.util.function.Consumer;

public final class UnavailableServerLiveSettingsProvider implements ServerLiveSettingsProvider {
    private final String reason;

    public UnavailableServerLiveSettingsProvider(String reason) {
        this.reason = reason;
    }

    @Override public String status() { return reason; }
    @Override public boolean connected() { return false; }
    @Override public void connect() { }
    @Override public Subscription listenState(Runnable listener) { return Subscription.NONE; }
    @Override public Subscription listenStatus(Consumer<String> listener) { listener.accept(reason); return Subscription.NONE; }
    @Override public Async<List<GameRuleValue>> gameRules() { return Async.failed(new IllegalStateException(reason)); }
    @Override public Async<Void> setGameRule(String name, String value) { return Async.failed(new IllegalStateException(reason)); }
    @Override public Async<List<LiveSettingValue>> liveSettings() { return Async.failed(new IllegalStateException(reason)); }
    @Override public Async<Void> setLiveSetting(LiveSettingValue setting, String value) { return Async.failed(new IllegalStateException(reason)); }
}
