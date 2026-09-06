package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.util.AsyncTools;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.DesktopNetworkAccess;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkHostScope;
import redxax.oxy.remotely.network.NetworkMemberRole;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.SettingsForm;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import restudio.rescreen.platform.Async;


import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.parseCapacity;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.parsePort;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.providerManaged;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.rootMessage;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.routeName;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.titleCase;

public class NetworkAttachScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final String networkId;
    private final String selectedInstanceId;
    private NetworkDefinition network;
    private List<Instance> instances = List.of();
    private NetworkRouteMappingFlow routeMappingFlow;
    private boolean preparing;

    public NetworkAttachScreen(Screen parent, RemotelyClient remotelyClient, String networkId) {
        this(parent, remotelyClient, networkId, "");
    }

    public NetworkAttachScreen(Screen parent, RemotelyClient remotelyClient, String networkId, String selectedInstanceId) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.networkId = networkId;
        this.selectedInstanceId = selectedInstanceId == null ? "" : selectedInstanceId;
    }

    public String getDesktopAppId() {
        return "network-attach";
    }

    public String getDesktopAppTitle() {
        return "Add Server";
    }

    public String getDesktopAppIconPath() {
        return "merge.png";
    }

    void openNetworkManager() {
        client.setScreen(parent);
        if (parent instanceof ServerManagerScreen serverManager) {
            serverManager.showNetworkSettings(networkId);
        }
    }

    @Override
    public void init() {
        super.init();
        routeMappingFlow = new NetworkRouteMappingFlow(this, remotelyClient, screen -> client.setScreen(screen));
        network = DesktopNetworkAccess.capability(remotelyClient).getNetwork(networkId).orElse(null);
        if (network == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            client.setScreen(parent);
            return;
        }
        instances = Rebase.get().getInstanceManager().getAllInstances();
        header().addLeft("close.png", () -> client.setScreen(parent), "Back").build();
        Container servers = createContainer("network_attach_servers", 6, 38, width - 12, Math.max(80, height - 44)).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populate(servers);
        setActiveContainer(servers);
        if (!selectedInstanceId.isBlank()) {
            availableServers().stream().filter(instance -> instance.getInstanceId().equals(selectedInstanceId)).findFirst().ifPresent(this::configure);
        }
    }

    private void populate(Container container) {
        List<Instance> available = availableServers();
        container.addWidget(summary(available.size() + " Available Servers", "Select A Server To Review Attach", available.isEmpty() ? "warning" : "nice"));
        Instance proxy = proxy();
        NetworkServerCreationContext creationContext = NetworkServerCreationContext.forInstance(proxy);
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Create Backend").hint(creationContext.supported() ? "Default Host • " + creationContext.hostLabel() : "Choose A Local Or SSH Host").imagePath("newFile.png").accentType(ThemeManager.getAccent("nice")).onClick(this::createBackend).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Register External").hint("Route Only • Manual Backend Ownership").imagePath("link.png").accentType(ThemeManager.getDefaultAccent()).onClick(this::configureExternal).build());
        if (available.isEmpty()) {
            container.addWidget(summary("No Unassigned Backends", "Create Or Register A Backend", "warning"));
            return;
        }
        String proxyScope = network.proxyMember() == null ? "" : network.proxyMember().hostScope();
        for (Instance instance : available) {
            boolean providerManaged = providerManaged(instance);
            boolean sameHost = !providerManaged && NetworkHostScope.resolve(instance).equals(proxyScope);
            String hint = titleCase(instance.getState().name()) + " • " + NetworkHostScope.resolve(instance) + " • " + (providerManaged ? network.forwarding().firewallVerified() ? "Provider Allocation • Protection Verified" : "Provider Allocation • Protection Required" : sameHost ? "Loopback Route" : network.forwarding().firewallVerified() ? "Protected Cross-Host Route" : "Protection Verification Required");
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 30).label(instance.getName()).hint(hint).imagePath("server.png").accentType(ThemeManager.getAccent(sameHost || network.forwarding().firewallVerified() ? "calm" : "warning")).onClick(() -> configure(instance)).build());
        }
    }

    private AnimatedButton summary(String label, String hint, String accent) {
        return new AnimatedButton.Builder().size(Math.max(220, width - 44), 22).label(label).hint(hint).accentType(ThemeManager.getAccent(accent)).build();
    }

    private void configure(Instance instance) {
        String[] route = {routeName(instance.getName())};
        NetworkMemberRole[] role = {NetworkMemberRole.GAMEPLAY};
        List<String> groupIds = new ArrayList<>();
        groupIds.add("");
        network.routingGroups().stream().map(group -> group.id()).forEach(groupIds::add);
        String[] group = {""};
        String[] address = {defaultAddress(instance)};
        String[] port = {String.valueOf(observedPort(instance, 25566))};
        String[] capacity = {"0"};
        Boolean[] resync = {Boolean.TRUE};
        routeMappingFlow.showForm(close -> {
            AnimatedButton review = new AnimatedButton.Builder().size(100, 20).label("Review Attach").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
                if (preparing) {
                    return;
                }
                try {
                    int preferredPort = parsePort(port[0]);
                    int resolvedCapacity = parseCapacity(capacity[0]);
                    String requestedRoute = route[0];
                    NetworkMemberRole requestedRole = role[0];
                    String requestedGroup = group[0];
                    String requestedAddress = address[0];
                    boolean installReSync = resync[0];
                    preparing = true;
                    Notification notification = new Notification.Builder().message(installReSync ? "Installing ReSync" : "Preparing Attach").description(instance.getName()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
                    List<Instance> reSyncTargets = new ArrayList<>();
                    reSyncTargets.add(instance);
                    instances.stream().filter(candidate -> candidate.getInstanceId().equals(network.proxyInstanceId())).findFirst().ifPresent(reSyncTargets::add);
                    Async<Void> setup = installReSync ? AsyncTools.supply(TaskSchedulers.current(), () -> NetworkReSyncSetup.installLatest(reSyncTargets)).thenApply(result -> {
                        if (!result.successful()) {
                            throw new IllegalStateException(new IllegalStateException(result.failureMessage()));
                        }
                        return null;
                    }) : Async.completed(null);
                    setup.thenCompose(unused -> DesktopNetworkAccess.capability(remotelyClient).prepareAttach(network, instance, requestedRoute, requestedRole, requestedGroup, requestedAddress, preferredPort, resolvedCapacity, installReSync, instances, List.of())).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
                        preparing = false;
                        if (throwable != null) {
                            notification.update().message("Attach Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                            return;
                        }
                        close.run();
                        notification.update().message("Attach Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
                        client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
                    }));
                } catch (RuntimeException exception) {
                    preparing = false;
                    new Notification("Attach Settings Invalid", rootMessage(exception), Notification.Type.ERROR);
                }
            }).build();
            PopupWidget.Builder builder = new SettingsForm.Builder("Add " + instance.getName()).onClose(close);
            builder.addTextField("Route", "The name used to send players to this server.", route[0], value -> route[0] = value);
            builder.addDropdown("Role", Arrays.asList(NetworkMemberRole.LOBBY, NetworkMemberRole.FALLBACK, NetworkMemberRole.GAMEPLAY, NetworkMemberRole.RESTRICTED, NetworkMemberRole.MAINTENANCE, NetworkMemberRole.CUSTOM), role[0], value -> titleCase(value.name()), value -> role[0] = value);
            builder.addDropdown("Routing Group", groupIds, group[0], value -> value.isBlank() ? "None" : value, value -> group[0] = value);
            builder.addTextField("Address", "The address the proxy uses to reach this server.", address[0], value -> address[0] = value);
            builder.addTextField("Port", "The port this server accepts connections on.", port[0], value -> port[0] = value);
            builder.addTextField("Capacity", "The player limit for this route. Use zero for no limit.", capacity[0], value -> capacity[0] = value);
            builder.addDropdown("ReSync", List.of(Boolean.TRUE, Boolean.FALSE), resync[0], value -> value ? "Enabled" : "Disabled", value -> resync[0] = value);
            builder.addTitleAction("Review", () -> review.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
            return builder.build();
        });
    }

    private void configureExternal() {
        String[] route = {"external"};
        NetworkMemberRole[] role = {NetworkMemberRole.GAMEPLAY};
        List<String> groupIds = new ArrayList<>();
        groupIds.add("");
        network.routingGroups().stream().map(group -> group.id()).forEach(groupIds::add);
        String[] group = {""};
        String[] address = {""};
        String[] port = {"25565"};
        String[] capacity = {"0"};
        Boolean[] acknowledged = {Boolean.FALSE};
        routeMappingFlow.showForm(close -> {
            AnimatedButton review = new AnimatedButton.Builder().size(110, 20).label("Review External").accentType(ThemeManager.getDefaultAccent()).onClick(() -> {
                if (preparing) {
                    return;
                }
                if (!acknowledged[0]) {
                    new Notification("Ownership Confirmation Required", "Forwarding, Firewall, Files, And Lifecycle Stay Manual", Notification.Type.WARN);
                    return;
                }
                try {
                    int resolvedPort = parsePort(port[0]);
                    int resolvedCapacity = parseCapacity(capacity[0]);
                    preparing = true;
                    Notification notification = new Notification.Builder().message("Preparing External Route").description(route[0]).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
                    DesktopNetworkAccess.capability(remotelyClient).prepareExternalAttach(network, route[0], role[0], group[0], address[0], resolvedPort, resolvedCapacity, instances, List.of()).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
                        preparing = false;
                        if (throwable != null) {
                            notification.update().message("External Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                            return;
                        }
                        close.run();
                        notification.update().message("External Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
                        client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
                    }));
                } catch (RuntimeException exception) {
                    preparing = false;
                    new Notification("External Settings Invalid", rootMessage(exception), Notification.Type.ERROR);
                }
            }).build();
            PopupWidget.Builder builder = new SettingsForm.Builder("Register External Backend").onClose(close);
            builder.addTextField("Route", route[0], value -> route[0] = value);
            builder.addDropdown("Role", Arrays.asList(NetworkMemberRole.LOBBY, NetworkMemberRole.FALLBACK, NetworkMemberRole.GAMEPLAY, NetworkMemberRole.RESTRICTED, NetworkMemberRole.MAINTENANCE, NetworkMemberRole.CUSTOM), role[0], value -> titleCase(value.name()), value -> role[0] = value);
            builder.addDropdown("Routing Group", groupIds, group[0], value -> value.isBlank() ? "None" : value, value -> group[0] = value);
            builder.addTextField("Address", "The address the proxy uses to reach this server.", address[0], value -> address[0] = value);
            builder.addTextField("Port", "The port this server accepts connections on.", port[0], value -> port[0] = value);
            builder.addTextField("Capacity", "The player limit for this route. Use zero for no limit.", capacity[0], value -> capacity[0] = value);
            builder.addDropdown("Ownership", List.of(Boolean.FALSE, Boolean.TRUE), acknowledged[0], value -> value ? "I Manage This Backend" : "Confirm Manual Ownership", value -> acknowledged[0] = value);
            builder.addRow(new PopupWidget.PopupRow.Builder("Remotely Only Manages The Proxy Route").id("reviewExternal").build());
            builder.addTitleAction("Review", () -> review.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
            return builder.build();
        });
    }

    private List<Instance> availableServers() {
        return instances.stream().filter(instance -> !instance.isProxyServer()).filter(instance -> DesktopNetworkAccess.capability(remotelyClient).getNetworkForInstance(instance.getInstanceId()).isEmpty()).toList();
    }

    private void createBackend() {
        NetworkAttachScreen continuation = new NetworkAttachScreen(parent, remotelyClient, networkId);
        routeMappingFlow.openBackendCreation(continuation, proxy(), backendSoftware(), instance -> client.setScreen(new NetworkAttachScreen(parent, remotelyClient, networkId, instance.getInstanceId())));
    }

    private Instance proxy() {
        return network == null ? null : instances.stream().filter(instance -> instance.getInstanceId().equals(network.proxyInstanceId())).findFirst().orElse(null);
    }

    private List<ModLoader> backendSoftware() {
        return List.of(ModLoader.PAPER, ModLoader.PURPUR, ModLoader.FOLIA, ModLoader.LEAF, ModLoader.FABRIC, ModLoader.QUILT, ModLoader.NEOFORGE, ModLoader.FORGE, ModLoader.SPIGOT, ModLoader.VANILLA);
    }

    private String defaultAddress(Instance instance) {
        if (providerManaged(instance)) {
            return "";
        }
        if (network.proxyMember() != null && network.proxyMember().hostScope().equals(NetworkHostScope.resolve(instance))) {
            return "127.0.0.1";
        }
        return instance.getBackendConfig() == null || instance.getBackendConfig().credentials == null ? "" : instance.getBackendConfig().credentials.getOrDefault("host", "");
    }

    private int observedPort(Instance instance, int fallback) {
        try {
            int port = Integer.parseInt(instance.getServerProperties().getProperty("server-port", String.valueOf(fallback)).trim());
            return port >= 1 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
