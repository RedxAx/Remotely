package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.util.DesktopAsyncTools;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.msmp.dto.LiveServerSetting;
import restudio.rebase.msmp.dto.GameRule;
import restudio.rebase.platform.Async;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DesktopServerLiveSettingsProvider implements ServerLiveSettingsProvider {
    private final Instance instance;
    private final MSMPManager manager;

    public DesktopServerLiveSettingsProvider(Instance instance) {
        this.instance = instance;
        manager = instance.getMSMPManager();
    }

    @Override public String status() { return manager.isConnected || api() != null && api().isConnected() ? "MSMP: Connected" : "MSMP: Not connected"; }
    @Override public boolean connected() { return manager.isConnected || api() != null && api().isConnected(); }
    @Override public void connect() { manager.connect(); }

    @Override
    public Subscription listenState(Runnable listener) {
        Consumer<InstanceState> stateListener = ignored -> listener.run();
        instance.addStateListener(stateListener);
        return once(() -> instance.removeStateListener(stateListener));
    }

    @Override
    public Subscription listenStatus(Consumer<String> listener) {
        manager.addStatusListener(listener);
        return once(() -> manager.removeStatusListener(listener));
    }

    @Override
    public Async<List<GameRuleValue>> gameRules() {
        if (api() == null) return Async.failed(new IllegalStateException("MSMP: Not connected"));
        return DesktopAsyncTools.<List<GameRule>>adapt(api().getGameRules()).thenApply(values -> values.stream()
                .map(value -> new GameRuleValue(value.name, value.value, value.type)).toList());
    }

    @Override
    public Async<Void> setGameRule(String name, String value) {
        return api() == null ? Async.failed(new IllegalStateException("MSMP: Not connected")) : DesktopAsyncTools.adapt(api().setGameRule(name, value));
    }

    @Override
    public Async<List<LiveSettingValue>> liveSettings() {
        if (api() == null) return Async.failed(new IllegalStateException("MSMP: Not connected"));
        return DesktopAsyncTools.<List<LiveServerSetting>>adapt(api().getLiveServerSettings()).thenApply(values -> values.stream()
                .map(value -> new LiveSettingValue(value, value.name, value.description, value.value, value.type)).toList());
    }

    @Override
    public Async<Void> setLiveSetting(LiveSettingValue setting, String value) {
        if (api() == null || !(setting.key() instanceof LiveServerSetting desktopSetting)) return Async.failed(new IllegalStateException("MSMP: Not connected"));
        return DesktopAsyncTools.adapt(api().setLiveServerSetting(desktopSetting, value));
    }

    private IMSMPApi api() {
        return manager.getApi();
    }

    private Subscription once(Runnable cleanup) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) cleanup.run();
        };
    }
}
