package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.ui.settings.controllers.ServerLiveSettingsProvider;
import restudio.rescreen.platform.Async;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class BrowserReSyncServerLiveSettingsProvider implements ServerLiveSettingsProvider {
    private final FlowManager flowManager;
    private final String serverId;

    public BrowserReSyncServerLiveSettingsProvider(FlowManager flowManager, String serverId) {
        this.flowManager = flowManager;
        this.serverId = serverId;
    }

    @Override public String status() { return "MSMP: Connected"; }
    @Override public boolean connected() { return flowManager != null && serverId != null && !serverId.isBlank(); }
    @Override public void connect() { }
    @Override public Subscription listenState(Runnable listener) { return Subscription.NONE; }
    @Override public Subscription listenStatus(Consumer<String> listener) { listener.accept(status()); return Subscription.NONE; }

    @Override
    public Async<List<GameRuleValue>> gameRules() {
        return request("gameRulesList", Map.of()).thenApply(response -> BrowserJson.objects(response, "rules").stream()
                .map(rule -> new GameRuleValue(BrowserJson.string(rule, "key"), BrowserJson.string(rule, "value"), BrowserJson.string(rule, "type")))
                .toList());
    }

    @Override
    public Async<Void> setGameRule(String name, String value) {
        return request("gameRuleSet", Map.of("key", name, "value", value)).thenApply(ignored -> null);
    }

    @Override
    public Async<List<LiveSettingValue>> liveSettings() {
        return request("liveSettingsList", Map.of()).thenApply(response -> BrowserJson.objects(response, "settings").stream()
                .map(setting -> {
                    String key = BrowserJson.string(setting, "key");
                    return new LiveSettingValue(key, key, "", BrowserJson.string(setting, "value"), BrowserJson.string(setting, "type"));
                })
                .toList());
    }

    @Override
    public Async<Void> setLiveSetting(LiveSettingValue setting, String value) {
        return request("liveSettingSet", Map.of("key", String.valueOf(setting.key()), "value", value)).thenApply(ignored -> null);
    }

    private Async<JsonObject> request(String action, Map<String, Object> payload) {
        if (!connected()) return Async.failed(new IllegalStateException("ReSync Is Unavailable"));
        return flowManager.requestPlayerControl(serverId, action, null, payload).thenCompose(response -> {
            if (BrowserJson.bool(response, "success", false)) return Async.completed(response);
            String reason = BrowserJson.string(response, "reason");
            return Async.failed(new IllegalStateException(reason.isBlank() ? "ReSync Live Settings Request Failed" : reason));
        });
    }
}
