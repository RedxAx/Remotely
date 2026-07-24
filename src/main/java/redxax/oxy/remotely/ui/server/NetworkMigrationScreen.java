package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkAdoptionReport;
import redxax.oxy.remotely.network.NetworkAdoptionRoute;
import redxax.oxy.remotely.network.NetworkCreationMember;
import redxax.oxy.remotely.network.NetworkCreationRequest;
import redxax.oxy.remotely.network.NetworkHostScope;
import redxax.oxy.remotely.network.NetworkMemberManagement;
import redxax.oxy.remotely.network.NetworkMemberRole;
import redxax.oxy.remotely.network.NetworkValidationIssue;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.loopback;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.providerManaged;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.rootMessage;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.titleCase;

public class NetworkMigrationScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final Instance legacyProxy;
    private final NetworkAdoptionReport report;
    private final String initialName;
    private final String targetInstanceId;
    private final boolean initialFirewallVerified;
    private List<Instance> instances = List.of();
    private Instance targetProxy;
    private TextInputWidget nameInput;
    private NetworkRouteMappingFlow routeMappingFlow;
    private boolean firewallVerified;
    private boolean preparing;

    public NetworkMigrationScreen(Screen parent, RemotelyClient remotelyClient, Instance legacyProxy, NetworkAdoptionReport report) {
        this(parent, remotelyClient, legacyProxy, report, legacyProxy.getName() + " Network", "", false);
    }

    private NetworkMigrationScreen(Screen parent, RemotelyClient remotelyClient, Instance legacyProxy, NetworkAdoptionReport report, String initialName, String targetInstanceId, boolean firewallVerified) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.legacyProxy = legacyProxy;
        this.report = report;
        this.initialName = initialName;
        this.targetInstanceId = targetInstanceId;
        this.initialFirewallVerified = firewallVerified;
    }

    public String getDesktopAppId() {
        return "network-migration";
    }

    public String getDesktopAppTitle() {
        return "Velocity Migration";
    }

    public String getDesktopAppIconPath() {
        return "merge.png";
    }

    @Override
    public void init() {
        super.init();
        routeMappingFlow = new NetworkRouteMappingFlow(this, remotelyClient, screen -> client.setScreen(screen));
        instances = Rebase.get().getInstanceManager().getAllInstances();
        List<Instance> targets = velocityTargets();
        targetProxy = targets.stream().filter(instance -> instance.getInstanceId().equals(targetInstanceId)).findFirst().orElse(targets.isEmpty() ? null : targets.getFirst());
        firewallVerified = initialFirewallVerified;
        header().addLeft("close.png", () -> client.setScreen(parent), "Back").addRight("checkmark.png", this::review, "Review Migration").build();
        int contentY = 60;
        int contentHeight = Math.max(80, height - contentY - 6);
        Container routes = createContainer("network_migration_routes", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container findings = createContainer("network_migration_findings", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populateRoutes(routes);
        populateFindings(findings);
        tabs().addTab("Migration", routes);
        tabs().addTab("Findings", findings);
        tabsManager.builder().allowAdd(false).allowClose(false).allowRename(false).allowReorder(false).position(6, 36).size(width - 12, 18).onTabSelected(tab -> setActiveContainer(tab.getContainer())).build();
        tabs().setActiveTab(routes);
        setActiveContainer(routes);
    }

    private void populateRoutes(Container container) {
        nameInput = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).text(initialName).placeholder("Network Name").build();
        container.addWidget(nameInput);
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 26).label(targetProxy == null ? "Select Velocity Proxy" : "Velocity • " + targetProxy.getName()).hint(targetProxy == null ? "Create Or Select A Velocity Server" : NetworkHostScope.resolve(targetProxy) + " • Click To Change").imagePath("network.png").accentType(ThemeManager.getAccent(targetProxy == null ? "danger" : "nice")).onClick(this::selectTarget).build());
        container.addWidget(summary(titleCase(legacyProxy.getModLoader().name()) + " • " + report.bindAddress() + ":" + report.entryPort(), "Legacy Source Remains Unmanaged", ThemeManager.getDefaultAccent()));
        long matched = report.routes().stream().filter(NetworkAdoptionRoute::matched).count();
        container.addWidget(summary(matched + "/" + report.routes().size() + " Routes Matched", report.canAdopt() ? "Ready For Velocity Review" : "Resolve Route Findings", report.canAdopt() ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")));
        for (NetworkAdoptionRoute route : report.routes()) {
            boolean external = route.management() == NetworkMemberManagement.EXTERNAL;
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(route.routeName() + " • " + route.address() + ":" + route.port()).hint(route.finding() + " • Click To Map").imagePath(route.matched() && !external ? "link.png" : "report.png").accentType(external ? ThemeManager.getDefaultAccent() : ThemeManager.getAccent(route.matched() ? "nice" : "danger")).onClick(() -> mapRoute(route)).build());
        }
        if (!report.fallbackRoutes().isEmpty()) {
            container.addWidget(summary("Fallback • " + String.join(" → ", report.fallbackRoutes()), "Legacy Priority Order", ThemeManager.getAccent("calm")));
        }
        report.forcedHosts().forEach((host, routes) -> container.addWidget(summary(host + " • " + String.join(" → ", routes), "Forced Host", ThemeManager.getAccent("calm"))));
        if (crossHost()) {
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label(firewallVerified ? "Network Protection Verified" : "Verify Network Protection").hint("Private Network Or Firewall Rules").imagePath(firewallVerified ? "checkmark.png" : "report.png").accentType(ThemeManager.getAccent(firewallVerified ? "nice" : "danger")).onClick(() -> refresh(report, targetProxy, !firewallVerified)).build());
        }
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Review Velocity Migration").hint("Modern Forwarding, Routes, Ports, And Cutover").imagePath("checkmark.png").accentType(ThemeManager.getAccent("nice")).onClick(this::review).build());
    }

    private void populateFindings(Container container) {
        for (NetworkValidationIssue issue : report.issues()) {
            Accent accent = switch (issue.severity()) {
                case INFO -> ThemeManager.getAccent("calm");
                case WARNING -> ThemeManager.getDefaultAccent();
                case ERROR -> ThemeManager.getAccent("danger");
            };
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(issue.message()).hint(issue.code()).imagePath(issue.blocksPersistence() ? "report.png" : "info.png").accentType(accent).build());
        }
    }

    private AnimatedButton summary(String label, String hint, Accent accent) {
        return new AnimatedButton.Builder().size(Math.max(220, width - 44), 22).label(label).hint(hint).accentType(accent).build();
    }

    private void selectTarget() {
        List<Instance> targets = velocityTargets();
        if (targets.isEmpty()) {
            new Notification("Velocity Required", "Create A Velocity Server First", Notification.Type.ERROR);
            return;
        }
        Instance current = targetProxy == null ? targets.getFirst() : targetProxy;
        Instance[] selection = {current};
        routeMappingFlow.showPopup(close -> {
            AnimatedButton select = new AnimatedButton.Builder().size(90, 20).label("Select Proxy").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
                close.run();
                refresh(report, selection[0], firewallVerified);
            }).build();
            PopupWidget.Builder builder = new PopupWidget.Builder("Select Velocity Proxy").size(380, 125).setExpandWithDropdowns(true).onClose(close);
            builder.addDropdown("Velocity", targets, current, Instance::getName, instance -> selection[0] = instance);
            builder.addRow("selectProxy", "", true, 24, select);
            return builder.build();
        });
    }

    private void mapRoute(NetworkAdoptionRoute route) {
        String targetId = targetProxy == null ? "" : targetProxy.getInstanceId();
        routeMappingFlow.openMapping(new NetworkRouteMappingFlow.RouteMapping(report, route, instances, legacyProxy, () -> nameInput == null ? initialName : nameInput.getText(), (updated, name) -> new NetworkMigrationScreen(parent, remotelyClient, legacyProxy, updated, name, targetId, firewallVerified), Instance::getName, 90, false));
    }

    private void review() {
        if (preparing) {
            return;
        }
        if (targetProxy == null || !report.canAdopt()) {
            new Notification("Migration Blocked", targetProxy == null ? "Select A Velocity Proxy" : "Resolve Route Findings", Notification.Type.ERROR);
            return;
        }
        String name = nameInput == null ? initialName : nameInput.getText();
        Set<String> fallback = new LinkedHashSet<>(report.fallbackRoutes());
        List<NetworkCreationMember> backends = report.routes().stream().map(route -> new NetworkCreationMember(route.instanceId(), route.routeName(), fallback.stream().findFirst().orElse("").equals(route.routeName()) ? NetworkMemberRole.LOBBY : NetworkMemberRole.GAMEPLAY, migrationAddress(route), route.port(), 0, route.management() != NetworkMemberManagement.EXTERNAL, route.management())).toList();
        NetworkCreationRequest request = new NetworkCreationRequest(name, targetProxy.getInstanceId(), report.entryPort(), backends, firewallVerified, report.fallbackRoutes(), report.forcedHosts());
        preparing = true;
        Notification notification = new Notification.Builder().message("Preparing Migration").description(name).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().prepareCreation(request, instances, List.of()).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
            preparing = false;
            if (throwable != null) {
                notification.update().message("Migration Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Migration Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
        }));
    }

    private List<Instance> velocityTargets() {
        return instances.stream().filter(instance -> instance.getModLoader() == ModLoader.VELOCITY || instance.getServerSoftwareCompatibility().stream().anyMatch(value -> value.equalsIgnoreCase("velocity"))).filter(instance -> remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).isEmpty()).toList();
    }

    private boolean crossHost() {
        if (targetProxy == null) {
            return false;
        }
        if (providerManaged(targetProxy)) {
            return true;
        }
        String targetScope = NetworkHostScope.resolve(targetProxy);
        return report.routes().stream().anyMatch(route -> {
            if (route.management() == NetworkMemberManagement.EXTERNAL) {
                String address = migrationAddress(route);
                return address.isBlank() || !loopback(address);
            }
            Instance backend = instance(route.instanceId());
            return backend != null && (providerManaged(backend) || !NetworkHostScope.resolve(backend).equals(targetScope));
        });
    }

    private String migrationAddress(NetworkAdoptionRoute route) {
        if (route.management() == NetworkMemberManagement.EXTERNAL) {
            if (!loopback(route.address()) || targetProxy == null || NetworkHostScope.resolve(legacyProxy).equals(NetworkHostScope.resolve(targetProxy))) {
                return route.address();
            }
            return legacyProxy.getBackendConfig() == null || legacyProxy.getBackendConfig().credentials == null ? "" : legacyProxy.getBackendConfig().credentials.getOrDefault("host", "");
        }
        Instance backend = instance(route.instanceId());
        if (backend == null || targetProxy == null || NetworkHostScope.resolve(backend).equals(NetworkHostScope.resolve(targetProxy))) {
            return "127.0.0.1";
        }
        if (!loopback(route.address())) {
            return route.address();
        }
        return backend.getBackendConfig() == null || backend.getBackendConfig().credentials == null ? "" : backend.getBackendConfig().credentials.getOrDefault("host", "");
    }

    private Instance instance(String instanceId) {
        return instances.stream().filter(instance -> instance.getInstanceId().equals(instanceId)).findFirst().orElse(null);
    }

    private void refresh(NetworkAdoptionReport updated, Instance target, boolean verified) {
        String name = nameInput == null ? initialName : nameInput.getText();
        client.setScreen(new NetworkMigrationScreen(parent, remotelyClient, legacyProxy, updated, name, target == null ? "" : target.getInstanceId(), verified));
    }
}
