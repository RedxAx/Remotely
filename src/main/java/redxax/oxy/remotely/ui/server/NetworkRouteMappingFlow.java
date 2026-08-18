package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.DesktopNetworkAccess;
import redxax.oxy.remotely.DesktopRemotelyPaths;
import redxax.oxy.remotely.network.NetworkAdoptionReport;
import redxax.oxy.remotely.network.NetworkAdoptionRoute;
import restudio.rebase.Rebase;
import restudio.rebase.api.unified.adapter.UnifiedFileSystemProvider;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.backend.RemoteFileSystemProvider;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class NetworkRouteMappingFlow {
    private final ReScreen host;
    private final RemotelyClient remotelyClient;
    private final Consumer<Screen> navigator;

    NetworkRouteMappingFlow(ReScreen host, RemotelyClient remotelyClient, Consumer<Screen> navigator) {
        this.host = host;
        this.remotelyClient = remotelyClient;
        this.navigator = navigator;
    }

    void openMapping(RouteMapping mapping) {
        NetworkAdoptionRoute route = mapping.route();
        Set<String> assigned = mapping.report().routes().stream().filter(candidate -> !candidate.routeName().equals(route.routeName())).map(NetworkAdoptionRoute::instanceId).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        List<Instance> candidates = mapping.instances().stream().filter(instance -> !instance.isProxyServer() && (mapping.sourceProxy() == null || !instance.getInstanceId().equals(mapping.sourceProxy().getInstanceId())) && !assigned.contains(instance.getInstanceId()) && DesktopNetworkAccess.capability(remotelyClient).getNetworkForInstance(instance.getInstanceId()).isEmpty()).toList();
        Instance current = candidates.stream().filter(instance -> instance.getInstanceId().equals(route.instanceId())).findFirst().orElse(candidates.isEmpty() ? null : candidates.getFirst());
        Instance[] selection = {current};
        showPopup(close -> {
            AnimatedButton assign = new AnimatedButton.Builder().size(mapping.assignButtonWidth(), 20).label("Assign Server").accentType(ThemeManager.getAccent("nice")).onClick(() -> assign(mapping, selection[0], close)).build();
            AnimatedButton create = new AnimatedButton.Builder().size(100, 20).label("Create Backend").accentType(ThemeManager.getAccent("calm")).onClick(() -> {
                close.run();
                createBackend(mapping);
            }).build();
            AnimatedButton importServer = new AnimatedButton.Builder().size(100, 20).label("Import Backend").accentType(ThemeManager.getAccent("calm")).onClick(() -> {
                close.run();
                importBackend(mapping);
            }).build();
            AnimatedButton external = new AnimatedButton.Builder().size(100, 20).label("Register External").accentType(ThemeManager.getDefaultAccent()).onClick(() -> {
                close.run();
                confirmExternal(mapping);
            }).build();
            PopupWidget.Builder builder = new PopupWidget.Builder("Map " + route.routeName()).width(380).setExpandWithDropdowns(true).onClose(close);
            if (!candidates.isEmpty()) {
                builder.addDropdown("Server", candidates, current, mapping.candidateLabel(), instance -> selection[0] = instance);
                builder.addTitleAction("Assign", () -> assign.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
            }
            builder.addTitleAction("Create", () -> create.onClick(0, 0, 0), PopupWidget.TitleActionRole.SECONDARY);
            builder.addTitleAction("Import", () -> importServer.onClick(0, 0, 0), PopupWidget.TitleActionRole.SECONDARY);
            builder.addTitleAction("External", () -> external.onClick(0, 0, 0), PopupWidget.TitleActionRole.SECONDARY);
            return builder.build();
        });
    }

    void openBackendCreation(Screen continuation, Instance preferredHost, List<ModLoader> softwareOptions, Consumer<Instance> onCreated) {
        List<NetworkServerCreationContext> contexts = NetworkServerCreationContext.available();
        NetworkServerCreationContext preferred = NetworkServerCreationContext.forInstance(preferredHost);
        NetworkServerCreationContext[] context = {contexts.stream().filter(candidate -> candidate.hostLabel().equals(preferred.hostLabel())).findFirst().orElse(contexts.getFirst())};
        ModLoader[] software = {softwareOptions.getFirst()};
        showPopup(close -> {
            AnimatedButton create = new AnimatedButton.Builder().size(100, 20).label("Create Backend").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
                close.run();
            navigator.accept(new ServerConfigurationScreen(continuation, context[0].remoteHost(), remotelyClient, software[0], onCreated));
            }).build();
            PopupWidget.Builder builder = new PopupWidget.Builder("Create Backend").width(390).setExpandWithDropdowns(true).onClose(close);
            builder.addDropdown("Host", contexts, context[0], NetworkServerCreationContext::hostLabel, value -> context[0] = value);
            builder.addDropdown("Software", softwareOptions, software[0], ModLoader::toString, value -> software[0] = value);
            builder.addTitleAction("Create", () -> create.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
            return builder.build();
        });
    }

    void showPopup(Function<Runnable, PopupWidget> factory) {
        PopupWidget[] popup = new PopupWidget[1];
        Runnable close = () -> {
            if (popup[0] != null) {
                popup[0].hide();
            }
        };
        popup[0] = factory.apply(close);
        popup[0].setX((host.getWidth() - popup[0].getWidth()) / 2);
        popup[0].setY((host.getHeight() - popup[0].getHeight()) / 2);
        host.addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void assign(RouteMapping mapping, Instance selection, Runnable close) {
        if (selection == null) {
            return;
        }
        try {
            NetworkAdoptionReport updated = DesktopNetworkAccess.capability(remotelyClient).resolveAdoptionRoute(mapping.report(), mapping.route().routeName(), selection, mapping.instances());
            close.run();
            navigate(mapping, updated);
        } catch (RuntimeException exception) {
            new Notification("Mapping Failed", rootMessage(exception), Notification.Type.ERROR);
        }
    }

    private void createBackend(RouteMapping mapping) {
        NetworkServerCreationContext context = routeContext(mapping.sourceProxy(), mapping.route());
        if (context == null) {
            new Notification("Route Host Unavailable", "Connect " + mapping.route().address() + " As An SSH Host Or Register Its Backend", Notification.Type.WARN);
            return;
        }
        if (!mapping.confirmCreationHost()) {
            launchRouteBackend(mapping, context);
            return;
        }
        NetworkServerCreationContext[] selection = {context};
        showPopup(close -> {
            AnimatedButton create = new AnimatedButton.Builder().size(100, 20).label("Configure Backend").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
                close.run();
                launchRouteBackend(mapping, selection[0]);
            }).build();
            PopupWidget.Builder builder = new PopupWidget.Builder("Create " + mapping.route().routeName()).width(390).setExpandWithDropdowns(true).onClose(close);
            builder.addDropdown("Host", List.of(context), selection[0], NetworkServerCreationContext::hostLabel, value -> selection[0] = value);
            builder.addTitleAction("Configure", () -> create.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
            return builder.build();
        });
    }

    private void launchRouteBackend(RouteMapping mapping, NetworkServerCreationContext context) {
        String name = mapping.networkName().get();
        Screen continuation = mapping.continuation().apply(mapping.report(), name);
        NetworkAdoptionRoute route = mapping.route();
        navigator.accept(new ServerConfigurationScreen(continuation, context.remoteHost(), remotelyClient, ModLoader.PAPER, (Instance instance) -> {
            instance.getServerProperties().setProperty("server-port", String.valueOf(route.port()));
            if (loopback(route.address())) {
                instance.getServerProperties().setProperty("server-ip", "127.0.0.1");
            }
        }, (Instance instance) -> finishCreatedBackend(mapping, instance, name)));
    }

    private void importBackend(RouteMapping mapping) {
        NetworkAdoptionRoute route = mapping.route();
        NetworkServerCreationContext context = routeContext(mapping.sourceProxy(), route);
        if (context == null) {
            new Notification("Route Host Unavailable", "Connect " + route.address() + " As An SSH Host Before Importing", Notification.Type.WARN);
            return;
        }
        String name = mapping.networkName().get();
        Screen continuation = mapping.continuation().apply(mapping.report(), name);
        if (context.remoteHost() == null) {
            navigator.accept(new FileExplorerScreen(continuation, null, DesktopRemotelyPaths.appDir(), DesktopRemotelyPaths.appDir(), true, null, (Instance instance) -> finishCreatedBackend(mapping, instance, name)));
            return;
        }
        RemoteHost remoteHost = context.remoteHost();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("hostId", remoteHost.hostId);
        credentials.put("host", remoteHost.getIp());
        credentials.put("port", String.valueOf(remoteHost.getPort()));
        credentials.put("user", remoteHost.getUser());
        credentials.put("authMode", remoteHost.getAuthMode());
        if (remoteHost.getPassword() != null && !remoteHost.getPassword().isBlank()) {
            credentials.put("password", remoteHost.getPassword());
        }
        if (remoteHost.getKeyPath() != null && !remoteHost.getKeyPath().isBlank()) {
            credentials.put("keyPath", remoteHost.getKeyPath());
        }
        if (remoteHost.getInstanceRegistryPath() != null && !remoteHost.getInstanceRegistryPath().isBlank()) {
            credentials.put("registryPath", remoteHost.getInstanceRegistryPath());
        }
        Instance remote = new Instance(remoteHost.name, "", "/");
        remote.setBackendConfig(new BackendConfig("SSH", credentials));
        RemoteFileSystemProvider provider = remote.getBackend().getFileSystem().logical();
        String home = provider.getMetadata("homeDir");
        RemotePath start = RemotePath.of(home == null || home.isBlank() ? "/" : home);
        FileSystemProvider legacyProvider = new UnifiedFileSystemProvider(provider);
            navigator.accept(new FileExplorerScreen(continuation, null, Path.of(start.asString()), DesktopRemotelyPaths.appDir(), true, legacyProvider, (Instance instance) -> finishCreatedBackend(mapping, instance, name)));
    }

    private void finishCreatedBackend(RouteMapping mapping, Instance instance, String name) {
        try {
            List<Instance> instances = Rebase.get().getInstanceManager().getAllInstances();
            NetworkAdoptionReport updated = DesktopNetworkAccess.capability(remotelyClient).resolveAdoptionRoute(mapping.report(), mapping.route().routeName(), instance, instances);
            navigator.accept(mapping.continuation().apply(updated, name));
        } catch (RuntimeException exception) {
            new Notification("Mapping Failed", rootMessage(exception), Notification.Type.ERROR);
            navigator.accept(mapping.continuation().apply(mapping.report(), name));
        }
    }

    private void confirmExternal(RouteMapping mapping) {
        showPopup(close -> {
            AnimatedButton confirm = new AnimatedButton.Builder().size(150, 20).label("I Manage This Backend").accentType(ThemeManager.getDefaultAccent()).onClick(() -> {
                close.run();
                try {
                    NetworkAdoptionReport updated = DesktopNetworkAccess.capability(remotelyClient).resolveExternalAdoptionRoute(mapping.report(), mapping.route().routeName());
                    navigate(mapping, updated);
                } catch (RuntimeException exception) {
                    new Notification("External Registration Failed", rootMessage(exception), Notification.Type.ERROR);
                }
            }).build();
            PopupWidget.Builder builder = new PopupWidget.Builder("Register External Backend").width(410).onClose(close);
            builder.addRow(new PopupWidget.PopupRow.Builder("Forwarding, Firewall, Lifecycle, And Files Stay Manual").id("confirmExternal").build());
            builder.addTitleAction("Confirm", () -> confirm.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
            return builder.build();
        });
    }

    private void navigate(RouteMapping mapping, NetworkAdoptionReport report) {
        navigator.accept(mapping.continuation().apply(report, mapping.networkName().get()));
    }

    private NetworkServerCreationContext routeContext(Instance sourceProxy, NetworkAdoptionRoute route) {
        List<NetworkServerCreationContext> contexts = NetworkServerCreationContext.available();
        if (!loopback(route.address())) {
            return contexts.stream().filter(context -> context.remoteHost() != null && route.address().equalsIgnoreCase(context.remoteHost().getIp())).findFirst().orElse(null);
        }
        NetworkServerCreationContext sourceContext = NetworkServerCreationContext.forInstance(sourceProxy);
        return sourceContext.supported() ? contexts.stream().filter(context -> context.hostLabel().equals(sourceContext.hostLabel())).findFirst().orElse(null) : null;
    }

    static boolean loopback(String address) {
        return address != null && (address.equalsIgnoreCase("localhost") || address.equals("127.0.0.1") || address.equals("::1"));
    }

    static boolean providerManaged(Instance instance) {
        BackendConfig backend = instance == null ? null : instance.getBackendConfig();
        return backend != null && backend.type != null && (PteroBackend.isPanelType(backend.type) || backend.type.equalsIgnoreCase("RESTUDIO"));
    }

    static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port Must Be Between 1 And 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Port Must Be A Number", exception);
        }
    }

    static int parseCapacity(String value) {
        try {
            int capacity = Integer.parseInt(value == null ? "" : value.trim());
            if (capacity < 0) {
                throw new IllegalArgumentException("Capacity Cannot Be Negative");
            }
            return capacity;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Capacity Must Be A Number", exception);
        }
    }

    static String routeName(String value) {
        String route = value == null ? "server" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")
            .replaceAll("^[-_]+|[-_]+$", "");
        return route.isBlank() ? "server" : route;
    }

    static String titleCase(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    record RouteMapping(NetworkAdoptionReport report, NetworkAdoptionRoute route, List<Instance> instances, Instance sourceProxy, Supplier<String> networkName, BiFunction<NetworkAdoptionReport, String, Screen> continuation, Function<Instance, String> candidateLabel, int assignButtonWidth, boolean confirmCreationHost) {
    }
}
