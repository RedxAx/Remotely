package redxax.oxy.remotely.servers;

import redxax.oxy.remotely.util.BrowserSafeState;

import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.restudio.AuthStateListener;
import restudio.rebase.restudio.ReStudio;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ReProxyAutoStartService implements AuthStateListener {
    private final InstanceManager instances;
    private final Map<Instance, Consumer<InstanceState>> listeners = BrowserSafeState.map();
    private final Set<Instance> starting = BrowserSafeState.set();

    public ReProxyAutoStartService(InstanceManager instances) {
        this.instances = instances;
    }

    public void start() {
        refresh();
        instances.addChangeListener(this::refresh);
        ReStudio.getInstance().addListener(this);
    }

    private synchronized void refresh() {
        Set<Instance> current = new HashSet<>();
        instances.getAllInstances().stream().filter(ReProxyAutoStartService::isLocalServer).forEach(current::add);
        listeners.entrySet().removeIf(entry -> {
            if (current.contains(entry.getKey())) {
                return false;
            }
            entry.getKey().removeStateListener(entry.getValue());
            starting.remove(entry.getKey());
            return true;
        });
        for (Instance instance : current) {
            if (!listeners.containsKey(instance)) {
                Consumer<InstanceState> listener = state -> stateChanged(instance, state);
                instance.addStateListener(listener);
                listeners.put(instance, listener);
            }
            stateChanged(instance, instance.getState());
        }
    }

    private void stateChanged(Instance instance, InstanceState state) {
        if (state != InstanceState.RUNNING) {
            starting.remove(instance);
            return;
        }
        boolean autoStart = Boolean.parseBoolean(instance.getSettings().getProperty("reproxy.autoStart", "false"));
        if (!isLocalServer(instance) || !autoStart || ReProxyManager.isForwarded(instance)) {
            starting.remove(instance);
            return;
        }
        if (starting.add(instance)) {
            ReProxyManager.startQuietly(instance, () -> starting.remove(instance));
        }
    }

    private static boolean isLocalServer(Instance instance) {
        return instance != null && instance.isServer()
                && (instance.getBackendConfig() == null || "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type));
    }

    @Override
    public void onLogin(String email) {
        refresh();
    }

    @Override
    public void onLogout() {
    }

    @Override
    public void onSessionExpired() {
    }
}
