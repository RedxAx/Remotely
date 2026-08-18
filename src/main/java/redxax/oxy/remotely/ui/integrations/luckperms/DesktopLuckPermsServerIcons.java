package redxax.oxy.remotely.ui.integrations.luckperms;

import redxax.oxy.remotely.DesktopRemotelyPaths;
import redxax.oxy.remotely.ui.server.DesktopServerIconProvider;
import redxax.oxy.remotely.ui.server.ServerIconManager;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rescreen.util.Identifier;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class DesktopLuckPermsServerIcons extends LuckPermsServerIcons {
    private final ServerIconManager icons = new ServerIconManager(new DesktopServerIconProvider(DesktopRemotelyPaths.appDir()));
    private Map<String, Instance> instances = Map.of();

    public DesktopLuckPermsServerIcons() {
        refresh();
    }

    @Override
    public Identifier icon(String instanceId) {
        Instance instance = instances.get(instanceId);
        Identifier icon = instance == null ? null : icons.getQuickIconId(instance);
        return icon == null ? Identifier.icon("server.png") : icon;
    }

    @Override
    public void load(String instanceId, Consumer<Identifier> loaded) {
        Instance instance = instances.get(instanceId);
        if (instance == null) {
            if (loaded != null) loaded.accept(Identifier.icon("server.png"));
            return;
        }
        icons.loadIconIdAsync(instance, loaded);
        icons.loadRemoteIconAsync(instance, () -> icons.loadIconIdAsync(instance, loaded));
    }

    @Override
    public void refresh() {
        try {
            instances = Rebase.get().getInstanceManager().getAllInstances().stream().filter(instance -> instance != null)
                .collect(Collectors.toUnmodifiableMap(Instance::getInstanceId, instance -> instance, (first, second) -> first));
        } catch (IllegalStateException exception) {
            instances = Map.of();
        }
    }
}
