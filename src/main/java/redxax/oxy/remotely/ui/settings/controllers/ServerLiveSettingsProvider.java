package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.platform.Async;

import java.util.List;
import java.util.function.Consumer;

public interface ServerLiveSettingsProvider {
    String status();

    boolean connected();

    void connect();

    Subscription listenState(Runnable listener);

    Subscription listenStatus(Consumer<String> listener);

    Async<List<GameRuleValue>> gameRules();

    Async<Void> setGameRule(String name, String value);

    Async<List<LiveSettingValue>> liveSettings();

    Async<Void> setLiveSetting(LiveSettingValue setting, String value);

    record GameRuleValue(String name, String value, String type) {
    }

    record LiveSettingValue(Object key, String name, String description, String value, String type) {
    }

    interface Subscription extends AutoCloseable {
        Subscription NONE = () -> {};

        @Override
        void close();
    }
}
