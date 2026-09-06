package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.settings.server.ServerSettingsRegistry;
import redxax.oxy.remotely.ui.settings.data.ServerSettingsDataController;
import restudio.rescreen.platform.Async;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class ServerConfigurationDraft {
    private final ServerConfigurationTarget target;
    private final ServerSettingsDataController settings;
    private final ServerScreenHost.ConfigurationUi ui;
    private final List<Setting> sections;
    private boolean closed;

    private ServerConfigurationDraft(ServerConfigurationTarget target, ServerSettingsDataController settings,
                                     ServerScreenHost.ConfigurationUi ui, List<Setting> sections) {
        this.target = target;
        this.settings = settings;
        this.ui = ui;
        this.sections = List.copyOf(sections);
    }

    static Async<ServerConfigurationDraft> create(ReScreen owner, ServerScreenHost host, Object preset, String name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(host, "host");
        ServerConfigurationTarget target = host.createConfigurationTarget();
        host.configureTargetDefaults(target);
        host.applyTargetPreset(target, preset);
        target.name(name);
        Async<Void> properties;
        try {
            properties = Objects.requireNonNull(host.loadInstanceProperties(target.raw(), false));
        } catch (RuntimeException error) {
            properties = Async.failed(error);
        }
        return host.configurationLoad("Server Configuration", properties, () -> null).thenCompose(ignored -> {
            ServerSettingsDataController settings = host.createServerSettingsController(target.raw(), ServerSettingsRegistry.getInstance().snapshot(target.raw()));
            Async<Void> loaded;
            try {
                loaded = Objects.requireNonNull(settings.load());
            } catch (RuntimeException error) {
                settings.close();
                return Async.failed(error);
            }
            return host.configurationLoad("Server Settings", loaded, () -> null).thenApply(value -> build(owner, host, target, settings))
                .whenComplete((draft, error) -> {
                    if (error != null) settings.close();
                });
        });
    }

    private static ServerConfigurationDraft build(ReScreen owner, ServerScreenHost host, ServerConfigurationTarget target,
                                                  ServerSettingsDataController settings) {
        ServerScreenHost.ConfigurationUi ui = null;
        try {
            ServerScreenHost.ConfigurationState state = new ServerScreenHost.ConfigurationState(null, target.raw(), null, false, false, false, "", "");
            ui = host.createConfigurationUi(owner, state, settings, new LinkedHashMap<>(), List.of(), () -> {}, () -> true);
            List<Setting> sections = new ArrayList<>();
            add(ui.settings(), "General", sections);
            add(ui.settings(), "Java", sections);
            return new ServerConfigurationDraft(target, settings, ui, sections);
        } catch (RuntimeException error) {
            if (ui != null) ui.cleanup().run();
            throw error;
        }
    }

    private static void add(Map<String, Supplier<List<Setting>>> settings, String name, List<Setting> destination) {
        Supplier<List<Setting>> supplier = settings.get(name);
        if (supplier == null) return;
        List<Setting> values = supplier.get();
        if (values != null) values.stream().filter(Objects::nonNull).forEach(destination::add);
    }

    ServerConfigurationTarget target() {
        return target;
    }

    List<Setting> sections() {
        return sections;
    }

    String location() {
        return ui.localLocation().get();
    }

    ServerSettingsDataController settings() {
        return settings;
    }

    void apply() {
        sections.forEach(Setting::applyChanges);
    }

    void close() {
        if (closed) return;
        closed = true;
        ui.cleanup().run();
        settings.close();
    }
}
