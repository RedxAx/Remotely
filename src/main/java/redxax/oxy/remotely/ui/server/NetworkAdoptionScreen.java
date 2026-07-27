package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkAdoptionReport;
import redxax.oxy.remotely.network.NetworkAdoptionRoute;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkJobStatus;
import redxax.oxy.remotely.network.NetworkMemberManagement;
import redxax.oxy.remotely.network.NetworkValidationIssue;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.util.Executors;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.rootMessage;
import static redxax.oxy.remotely.ui.server.NetworkRouteMappingFlow.titleCase;

public class NetworkAdoptionScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final Instance proxy;
    private final NetworkAdoptionReport report;
    private final String initialName;
    private TextInputWidget nameInput;
    private NetworkRouteMappingFlow routeMappingFlow;
    private boolean adopting;

    public NetworkAdoptionScreen(Screen parent, RemotelyClient remotelyClient, Instance proxy, NetworkAdoptionReport report) {
        this(parent, remotelyClient, proxy, report, proxy.getName() + " Network");
    }

    private NetworkAdoptionScreen(Screen parent, RemotelyClient remotelyClient, Instance proxy, NetworkAdoptionReport report, String initialName) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.proxy = proxy;
        this.report = report;
        this.initialName = initialName;
    }

    public String getDesktopAppId() {
        return "network-adoption";
    }

    public String getDesktopAppTitle() {
        return "Import Network";
    }

    public String getDesktopAppIconPath() {
        return "merge.png";
    }

    @Override
    public void init() {
        super.init();
        routeMappingFlow = new NetworkRouteMappingFlow(this, remotelyClient, screen -> client.setScreen(screen));
        header().addLeft("close.png", () -> client.setScreen(parent), "Back").addRight("checkmark.png", this::adopt, "Import Network").build();
        int contentY = 60;
        int contentHeight = Math.max(80, height - contentY - 6);
        Container routes = createContainer("network_adoption_routes", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container findings = createContainer("network_adoption_findings", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populateRoutes(routes);
        populateFindings(findings);
        tabs().addTab("Routes", routes);
        tabs().addTab("Findings", findings);
        tabsManager.builder().allowAdd(false).allowClose(false).allowRename(false).allowReorder(false).position(6, 36).size(width - 12, 18).onTabSelected(tab -> setActiveContainer(tab.getContainer())).build();
        tabs().setActiveTab(routes);
        setActiveContainer(routes);
    }

    private void populateRoutes(Container container) {
        nameInput = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).text(initialName).placeholder("Network Name").build();
        container.addWidget(nameInput);
        container.addWidget(summary(report.bindAddress() + ":" + report.entryPort() + " • " + titleCase(report.forwardingMode().name()), report.proxyOnlineMode() ? "Proxy Online Mode" : "Proxy Offline Mode", ThemeManager.getAccent("calm")));
        long matched = report.routes().stream().filter(NetworkAdoptionRoute::matched).count();
        container.addWidget(summary(matched + "/" + report.routes().size() + " Routes Matched", report.canAdopt() ? "Ready To Import" : "Resolve Findings", report.canAdopt() ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")));
        for (NetworkAdoptionRoute route : report.routes()) {
            String label = route.routeName() + " • " + route.address() + ":" + route.port();
            boolean external = route.management() == NetworkMemberManagement.EXTERNAL;
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(label).hint(route.matched() ? route.finding() + " • Click To Change" : route.finding() + " • Click To Resolve").imagePath(route.matched() && !external ? "link.png" : "report.png").accentType(external ? ThemeManager.getDefaultAccent() : route.matched() ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")).onClick(() -> openRouteMapping(route)).build());
        }
        if (!report.fallbackRoutes().isEmpty()) {
            container.addWidget(summary("Fallback • " + String.join(" → ", report.fallbackRoutes()), "Velocity Try Order", ThemeManager.getAccent("calm")));
        }
        report.forcedHosts().forEach((host, routeNames) -> container.addWidget(summary(host + " • " + String.join(" → ", routeNames), "Forced Host", ThemeManager.getAccent("calm"))));
    }

    private void populateFindings(Container container) {
        if (report.issues().isEmpty()) {
            container.addWidget(summary("No Blocking Findings", "No Files Have Been Changed", ThemeManager.getAccent("nice")));
            return;
        }
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

    private void openRouteMapping(NetworkAdoptionRoute route) {
        List<Instance> instances = Rebase.get().getInstanceManager().getAllInstances();
        routeMappingFlow.openMapping(new NetworkRouteMappingFlow.RouteMapping(report, route, instances, proxy, () -> nameInput == null ? initialName : nameInput.getText(), (updated, name) -> new NetworkAdoptionScreen(parent, remotelyClient, proxy, updated, name), instance -> instance.getName() + " • " + instance.getInstanceId(), 100, true));
    }

    private void adopt() {
        if (adopting) {
            return;
        }
        if (!report.canAdopt()) {
            new Notification("Import Blocked", "Resolve Adoption Findings", Notification.Type.ERROR);
            return;
        }
        String name = nameInput == null ? "" : nameInput.getText();
        if (name == null || name.isBlank()) {
            new Notification("Network Name Required", Notification.Type.ERROR);
            return;
        }
        List<Instance> instances = Rebase.get().getInstanceManager().getAllInstances();
        List<Instance> targets = reSyncTargets(instances);
        if (targets.size() <= 1) {
            runAdoption(name, instances, false);
            return;
        }
        PopupWidget[] popup = new PopupWidget[1];
        PopupWidget.Builder builder = new PopupWidget.Builder("Install ReSync").width(360).onClose(() -> popup[0].hide());
        builder.addRow(new PopupWidget.PopupRow.Builder("Add Live Network Features").id("resync").description("Install The Latest ReSync On The Proxy And Managed Servers For Player Controls, Shared Chat, Content, Events, And Live Status. The Network Still Works Without It.").build());
        builder.addTitleAction("Continue Without ReSync", () -> {
            popup[0].hide();
            runAdoption(name, instances, false);
        }, PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Install ReSync", () -> {
            popup[0].hide();
            runAdoption(name, instances, true);
        }, PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void runAdoption(String name, List<Instance> instances, boolean installReSync) {
        adopting = true;
        Notification notification = new Notification.Builder().message(installReSync ? "Installing ReSync" : "Importing Network").description(proxy.getName()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        CompletableFuture<NetworkReSyncSetup.SetupResult> setup = installReSync
            ? CompletableFuture.supplyAsync(() -> NetworkReSyncSetup.installLatest(reSyncTargets(instances)), Executors.IO)
            : CompletableFuture.completedFuture(new NetworkReSyncSetup.SetupResult(0, 0, Map.of()));
        setup.thenCompose(result -> {
            if (!result.successful()) {
                return CompletableFuture.failedFuture(new IllegalStateException(result.failureMessage()));
            }
            return remotelyClient.getNetworkManager().adoptNetwork(name, report, instances);
        }).thenCompose(network -> {
            if (!installReSync) {
                return CompletableFuture.completedFuture(network);
            }
            List<String> backendIds = report.routes().stream().filter(route -> route.management() == NetworkMemberManagement.MANAGED).map(NetworkAdoptionRoute::instanceId).toList();
            return remotelyClient.getNetworkManager().enableReSyncSafely(network, backendIds, instances, "Network Import").thenApply(job -> {
                if (job.status() != NetworkJobStatus.SUCCEEDED) {
                    throw new CompletionException(new IllegalStateException(job.message()));
                }
                return remotelyClient.getNetworkManager().getNetwork(network.networkId()).orElse(network);
            });
        }).whenComplete((network, throwable) -> ScreenManager.getInstance().execute(() -> finishAdoption(notification, network, throwable)));
    }

    private List<Instance> reSyncTargets(List<Instance> instances) {
        List<String> ids = report.routes().stream().filter(route -> route.management() == NetworkMemberManagement.MANAGED).map(NetworkAdoptionRoute::instanceId).toList();
        return instances.stream().filter(instance -> instance.getInstanceId().equals(proxy.getInstanceId()) || ids.contains(instance.getInstanceId())).toList();
    }

    private void finishAdoption(Notification notification, NetworkDefinition network, Throwable throwable) {
        adopting = false;
        if (throwable != null) {
            notification.update().message("Import Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        notification.update().message("Network Imported").description(network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        client.setScreen(parent);
        if (parent instanceof ServerManagerScreen serverManager) {
            serverManager.showNetworkSettings(network.networkId());
        }
    }
}
