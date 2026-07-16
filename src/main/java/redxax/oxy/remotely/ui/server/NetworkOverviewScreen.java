package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkDiscoveryResult;
import redxax.oxy.remotely.network.NetworkJob;
import redxax.oxy.remotely.network.NetworkJobStatus;
import redxax.oxy.remotely.network.NetworkJobType;
import redxax.oxy.remotely.network.NetworkIncident;
import redxax.oxy.remotely.network.NetworkIncidentSeverity;
import redxax.oxy.remotely.network.NetworkIncidentStatus;
import redxax.oxy.remotely.network.NetworkLifecycleJob;
import redxax.oxy.remotely.network.NetworkLifecycleOperation;
import redxax.oxy.remotely.network.NetworkLifecycleStep;
import redxax.oxy.remotely.network.NetworkLifecycleStepStatus;
import redxax.oxy.remotely.network.NetworkLifecycleStatus;
import redxax.oxy.remotely.network.NetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkRuntimeConnectionState;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.network.NetworkTopologyHeat;
import redxax.oxy.remotely.network.NetworkPreflightCheckStatus;
import redxax.oxy.remotely.network.NetworkPreflightReport;
import redxax.oxy.remotely.network.NetworkPreflightStatus;
import redxax.oxy.remotely.network.NetworkValidationIssue;
import redxax.oxy.remotely.network.SyncDataFamily;
import redxax.oxy.remotely.ui.widgets.NetworkTopologyWidget;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class NetworkOverviewScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final String networkId;
    private final boolean openSettingsOnInit;
    private NetworkDefinition network;
    private NetworkDiscoveryResult discovery;
    private List<Instance> instances = List.of();
    private final Map<String, Instance> instancesById = new LinkedHashMap<>();
    private PopupWidget dissolvePopup;
    private PopupWidget settingsPopup;
    private TextInputWidget networkNameInput;
    private NetworkTopologyHeat topologyHeat = NetworkTopologyHeat.STATUS;

    public NetworkOverviewScreen(Screen parent, RemotelyClient remotelyClient, String networkId) {
        this(parent, remotelyClient, networkId, false);
    }

    public NetworkOverviewScreen(Screen parent, RemotelyClient remotelyClient, String networkId, boolean openSettingsOnInit) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.networkId = networkId;
        this.openSettingsOnInit = openSettingsOnInit;
    }

    public String getDesktopAppId() {
        return "network-overview";
    }

    public String getDesktopAppTitle() {
        return network == null ? "Network" : network.name();
    }

    public String getDesktopAppIconPath() {
        return "map.png";
    }

    @Override
    public void init() {
        super.init();
        NetworkManager manager = remotelyClient.getNetworkManager();
        network = manager == null ? null : manager.getNetwork(networkId).orElse(null);
        if (network == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            client.setScreen(parent);
            return;
        }
        instances = Rebase.get().getInstanceManager().getAllInstances();
        instancesById.clear();
        instances.forEach(instance -> instancesById.put(instance.getInstanceId(), instance));
        discovery = manager.discover(network, instances, List.of());
        header()
            .addLeft("close.png", () -> client.setScreen(parent), "Back")
            .addRight("edit.png", this::openSettingsPopup, "Network Settings")
            .addRight("reload.png", this::refresh, "Refresh")
            .build();
        createSettingsPopup();
        createDissolvePopup();
        int contentY = 60;
        int contentHeight = Math.max(80, height - contentY - 6);
        Container overview = createContainer("network_overview", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container servers = createContainer("network_servers", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container operations = createContainer("network_operations", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container routing = createContainer("network_routing_summary", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container state = createContainer("network_state", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container incidents = createContainer("network_incidents", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container verification = createContainer("network_verification", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container security = createContainer("network_security", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populateOverview(overview);
        populateServers(servers);
        populateOperations(operations);
        populateRouting(routing);
        populateState(state);
        populateIncidents(incidents);
        populateVerification(verification);
        populateSecurity(security);
        tabs().addTab("Overview", overview);
        tabs().addTab("Servers", servers);
        tabs().addTab("Operations", operations);
        tabs().addTab("Routing", routing);
        tabs().addTab("State", state);
        tabs().addTab("Incidents", incidents);
        tabs().addTab("Verification", verification);
        tabs().addTab("Security", security);
        tabsManager.builder().allowAdd(false).allowClose(false).allowRename(false).allowReorder(false).position(6, 36).size(width - 12, 18).onTabSelected(tab -> setActiveContainer(tab.getContainer())).build();
        tabs().setActiveTab(overview);
        setActiveContainer(overview);
        if (openSettingsOnInit) {
            openSettingsPopup();
        }
    }

    private void createSettingsPopup() {
        networkNameInput = new TextInputWidget.Builder().text(network.name()).placeholder("Network Name").maxLength(64).build();
        IconButton save = new IconButton.Builder().label("Save Name").imagePath("save.png").accentType(ThemeManager.getAccent("nice")).onClick(this::saveNetworkName).build();
        IconButton servers = new IconButton.Builder().label("Add Server").hint("Attach An Existing Backend").imagePath("server.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> client.setScreen(new NetworkAttachScreen(this, remotelyClient, networkId))).build();
        IconButton routing = new IconButton.Builder().label("Routing").hint("Fallbacks, Forced Hosts, And Strategies").imagePath("edit.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> client.setScreen(new NetworkRoutingScreen(this, remotelyClient, networkId))).build();
        IconButton realms = new IconButton.Builder().label("Realms").hint("Shared State And Presence").imagePath("merge.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> client.setScreen(new NetworkRealmScreen(this, remotelyClient, networkId))).build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Network Settings").size(430, 150).onClose(() -> settingsPopup.hide());
        builder.addRow("networkName", "Name", true, 24, networkNameInput, save);
        builder.addRow("networkManagement", "Manage", true, 28, servers, routing, realms);
        settingsPopup = builder.build();
        settingsPopup.hide();
        addDrawableChild(settingsPopup);
    }

    private void openSettingsPopup() {
        if (settingsPopup == null) {
            return;
        }
        networkNameInput.setText(network.name());
        settingsPopup.setX((width - settingsPopup.getWidth()) / 2);
        settingsPopup.setY((height - settingsPopup.getHeight()) / 2);
        settingsPopup.show();
    }

    private void saveNetworkName() {
        String name = networkNameInput.getText() == null ? "" : networkNameInput.getText().trim();
        if (name.isBlank()) {
            new Notification("Name Required", Notification.Type.ERROR);
            return;
        }
        if (name.equals(network.name())) {
            settingsPopup.hide();
            return;
        }
        try {
            NetworkManager manager = remotelyClient.getNetworkManager();
            network = manager.save(network.renamed(name));
            manager.reconcileInstanceBindings(instances);
            settingsPopup.hide();
            new Notification("Network Updated", network.name(), Notification.Type.SUCCESS);
            refresh();
        } catch (RuntimeException exception) {
            new Notification("Edit Failed", rootMessage(exception), Notification.Type.ERROR);
        }
    }

    private void populateOverview(Container container) {
        int topologyWidth = Math.max(220, width - 44);
        int topologyHeight = NetworkTopologyWidget.preferredHeight(topologyWidth, network.members().size());
        IconButton[] heatButton = new IconButton[1];
        heatButton[0] = new IconButton.Builder().size(topologyWidth, 22).label("Heat • " + titleCase(topologyHeat.name())).imagePath("map.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> {
            NetworkTopologyHeat[] modes = NetworkTopologyHeat.values();
            topologyHeat = modes[(topologyHeat.ordinal() + 1) % modes.length];
            heatButton[0].setMessage("Heat • " + titleCase(topologyHeat.name()));
        }).build();
        container.addWidget(heatButton[0]);
        container.addWidget(new NetworkTopologyWidget(0, 0, topologyWidth, topologyHeight, network, discovery.observations(), () -> remotelyClient.getNetworkManager().getRuntimeSnapshot(networkId), () -> topologyHeat, () -> remotelyClient.getNetworkManager().getTransferFailureHeat(networkId), this::openMember));
        int blocking = (int) discovery.issues().stream().filter(NetworkValidationIssue::blocksPersistence).count();
        int warnings = (int) discovery.issues().stream().filter(issue -> issue.severity() == NetworkValidationIssue.Severity.WARNING).count();
        List<NetworkIncident> incidents = remotelyClient.getNetworkManager().getIncidents(networkId);
        int openIncidents = (int) incidents.stream().filter(incident -> incident.status() == NetworkIncidentStatus.OPEN).count();
        int criticalIncidents = (int) incidents.stream().filter(incident -> incident.status() == NetworkIncidentStatus.OPEN && incident.severity() == NetworkIncidentSeverity.CRITICAL).count();
        container.addWidget(statusButton("Entry • " + primaryEntry(), "Proxy Entry", ThemeManager.getAccent("calm")));
        if (network.runtime().enabled()) {
            NetworkRuntimeSnapshot runtime = remotelyClient.getNetworkManager().getRuntimeSnapshot(networkId);
            String runtimeLabel = runtime.connected() ? runtime.players() + " Players • Runtime Connected" : titleCase(runtime.state().name());
            container.addWidget(statusButton(runtimeLabel, runtime.message(), runtime.connected() ? ThemeManager.getAccent("nice") : runtime.state() == NetworkRuntimeConnectionState.UNAVAILABLE ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("warning")));
        }
        container.addWidget(statusButton(network.members().size() + " Servers • " + openIncidents + " Incidents", blocking + " Blocking • " + warnings + " Warnings", blocking > 0 || criticalIncidents > 0 ? ThemeManager.getAccent("danger") : warnings > 0 || openIncidents > 0 ? ThemeManager.getAccent("warning") : ThemeManager.getAccent("nice")));
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 22).label("Review Changes").imagePath("edit.png").accentType(ThemeManager.getAccent("calm")).onClick(this::prepareReview).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 22).label("Reconcile Network").imagePath("reload.png").accentType(ThemeManager.getAccent("nice")).onClick(this::reconcile).build());
    }

    private void populateServers(Container container) {
        Map<IconButton, String> searchableRows = new LinkedHashMap<>();
        TextInputWidget search = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).placeholder("Search Servers").onChange(value -> {
            String query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            searchableRows.forEach((button, text) -> button.setVisible(query.isBlank() || text.contains(query)));
            container.updateWidgetPositions();
        }).build();
        container.addWidget(search);
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Add Server").hint("Attach An Existing Backend").imagePath("merge.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> client.setScreen(new NetworkAttachScreen(this, remotelyClient, networkId))).build());
        for (NetworkMember member : network.members()) {
            Instance instance = instancesById.get(member.instanceId());
            String label = member.routeName() + " • " + titleCase(member.role().name());
            String hint = (member.isManaged() ? member.hostScope() : "External") + " • " + member.address() + ":" + member.port();
            Accent accent = !member.isManaged() ? ThemeManager.getAccent("warning") : instance == null ? ThemeManager.getAccent("danger") : discovery.observations().stream().filter(observation -> observation.nodeId().equals(member.nodeId())).findFirst().map(observation -> switch (observation.state()) {
                case HEALTHY -> ThemeManager.getAccent("nice");
                case UNKNOWN, DRIFTED -> ThemeManager.getAccent("warning");
                case DEGRADED, INSECURE, UNREACHABLE -> ThemeManager.getAccent("danger");
            }).orElse(ThemeManager.getAccent("warning"));
            IconButton button = new IconButton.Builder().size(Math.max(220, width - 44), 26).label(label).hint(hint).imagePath(member.isProxy() ? "velocity.png" : "server.png").accentType(accent).onClick(() -> openMember(member)).build();
            searchableRows.put(button, (label + " " + hint).toLowerCase(Locale.ROOT));
            container.addWidget(button);
        }
    }

    private void populateOperations(Container container) {
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Start Network").imagePath("start.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> lifecycle(NetworkLifecycleOperation.START)).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Stop Network").imagePath("stop.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> lifecycle(NetworkLifecycleOperation.STOP)).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Restart Network").imagePath("reload.png").accentType(ThemeManager.getAccent("warning")).onClick(() -> lifecycle(NetworkLifecycleOperation.RESTART)).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Rolling Restart").hint("Keep Healthy Backend Capacity Online").imagePath("reload.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> lifecycle(NetworkLifecycleOperation.ROLLING_RESTART)).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Check Drain").hint("Requires Every Backend To Be Empty").imagePath("stop.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> lifecycle(NetworkLifecycleOperation.DRAIN)).build());
        NetworkRuntimeSnapshot runtime = remotelyClient.getNetworkManager().getRuntimeSnapshot(networkId);
        for (NetworkMember member : network.members().stream().filter(candidate -> !candidate.isProxy() && candidate.isManaged() && candidate.resyncEnabled()).toList()) {
            NetworkNodePresence presence = runtime.connected() ? runtime.node(member.nodeId()).orElse(null) : null;
            String status = presence == null ? "Unavailable" : titleCase(presence.status().name());
            String hint = presence == null ? runtime.message() : presence.players() + (presence.capacity() > 0 ? "/" + presence.capacity() : "") + " Players";
            Accent accent = presence == null ? ThemeManager.getAccent("warning") : switch (presence.status()) {
                case ONLINE -> ThemeManager.getAccent("nice");
                case DRAINING, MAINTENANCE -> ThemeManager.getAccent("warning");
                case OFFLINE, REVOKED -> ThemeManager.getAccent("danger");
            };
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label(member.routeName() + " • " + status).hint(hint).imagePath("server.png").accentType(accent).onClick(() -> openRuntimeControls(member)).build());
        }
        if (network.runtime().enabled()) {
            TextInputWidget command = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).placeholder("Proxy Command").maxLength(2048).build();
            container.addWidget(command);
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 22).label("Run Proxy Command").imagePath("terminal.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> executeProxyCommand(command)).build());
            TextInputWidget broadcast = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).placeholder("Broadcast Message").maxLength(8192).build();
            container.addWidget(broadcast);
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 22).label("Broadcast").imagePath("chat.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> broadcastMessage(broadcast)).build());
        }
        List<NetworkLifecycleJob> lifecycleJobs = remotelyClient.getNetworkManager().getLifecycleJobManager().getJobs(network.networkId());
        for (NetworkLifecycleJob job : lifecycleJobs) {
            long completed = job.steps().stream().filter(step -> step.complete()).count();
            String label = titleCase(job.operation().name()) + " • " + lifecycleStatus(job.status());
            IconButton.Builder builder = new IconButton.Builder().size(Math.max(220, width - 44), 26).label(label).hint(completed + "/" + job.steps().size() + " • " + job.message()).imagePath(job.status() == NetworkLifecycleStatus.SUCCEEDED ? "checkmark.png" : job.status() == NetworkLifecycleStatus.FAILED ? "report.png" : "reload.png").accentType(lifecycleAccent(job));
            if (job.canResume()) {
                builder.onClick(() -> resumeLifecycle(job));
            } else if (job.status() == NetworkLifecycleStatus.FAILED) {
                builder.onClick(() -> openFailedLifecycleServer(job));
            }
            container.addWidget(builder.build());
        }
        List<NetworkJob> jobs = remotelyClient.getNetworkManager().getJobManager().getJobs(network.networkId());
        if (jobs.isEmpty() && lifecycleJobs.isEmpty()) {
            container.addWidget(statusButton("No Network Jobs", "Operations", ThemeManager.getDefaultAccent()));
        }
        for (NetworkJob job : jobs) {
            String label = titleCase(job.type().name()) + " • " + jobStatus(job.status());
            IconButton.Builder builder = new IconButton.Builder().size(Math.max(220, width - 44), 26).label(label).hint(job.message()).imagePath(job.status() == NetworkJobStatus.SUCCEEDED ? "checkmark.png" : job.status() == NetworkJobStatus.FAILED || job.status() == NetworkJobStatus.BLOCKED ? "report.png" : "reload.png").accentType(jobAccent(job));
            if (job.canResume()) {
                builder.onClick(() -> resume(job));
            }
            container.addWidget(builder.build());
            if (job.canRollback() && (job.status() == NetworkJobStatus.INTERRUPTED || job.status() == NetworkJobStatus.FAILED)) {
                container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 22).label("Rollback " + titleCase(job.type().name())).imagePath("history.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> rollback(job)).build());
            }
        }
    }

    private void populateRouting(Container container) {
        if (network.routingGroups().isEmpty()) {
            container.addWidget(statusButton("No Routing Groups", "Add A Fallback Or Forced Host Route", ThemeManager.getAccent("warning")));
        }
        for (var group : network.routingGroups()) {
            String routes = group.nodeIds().stream().map(nodeId -> network.members().stream().filter(member -> member.nodeId().equals(nodeId)).map(NetworkMember::routeName).findFirst().orElse("Missing")).collect(Collectors.joining(" → "));
            container.addWidget(statusButton(group.name() + " • " + (routes.isBlank() ? "No Servers" : routes), group.forcedHosts().isEmpty() ? titleCase(group.strategy().name()) : String.join(", ", group.forcedHosts()), ThemeManager.getAccent(group.nodeIds().isEmpty() ? "warning" : "calm")));
        }
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Manage Routing").hint("Fallback Order, Forced Hosts, And Strategies").imagePath("edit.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> client.setScreen(new NetworkRoutingScreen(this, remotelyClient, networkId))).build());
    }

    private void populateState(Container container) {
        long stateful = network.syncRealms().stream().filter(realm -> realm.dataFamilies().stream().anyMatch(family -> family != SyncDataFamily.PRESENCE)).count();
        int members = network.syncRealms().stream().mapToInt(realm -> realm.nodeIds().size()).sum();
        container.addWidget(statusButton(network.syncRealms().size() + " Realms • " + stateful + " Stateful", members + " Realm Memberships", network.syncRealms().isEmpty() ? ThemeManager.getAccent("warning") : ThemeManager.getAccent("nice")));
        for (var realm : network.syncRealms()) {
            String families = realm.dataFamilies().stream().map(family -> titleCase(family.name())).collect(Collectors.joining(", "));
            container.addWidget(statusButton(realm.name() + " • " + realm.nodeIds().size() + " Servers", families + " • " + titleCase(realm.locationPolicy().name()), ThemeManager.getAccent(realm.nodeIds().size() < 2 ? "warning" : "calm")));
        }
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Manage Realms").hint("Servers, State Families, Location, And Retention").imagePath("merge.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> client.setScreen(new NetworkRealmScreen(this, remotelyClient, networkId))).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Player Snapshots").hint("Inspect, Pin, And Restore Player State").imagePath("history.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> client.setScreen(new NetworkSnapshotScreen(this, remotelyClient, networkId))).build());
    }

    private void populateIncidents(Container container) {
        List<NetworkIncident> incidents = remotelyClient.getNetworkManager().getIncidents(networkId);
        long open = incidents.stream().filter(incident -> incident.status() == NetworkIncidentStatus.OPEN).count();
        long critical = incidents.stream().filter(incident -> incident.status() == NetworkIncidentStatus.OPEN && incident.severity() == NetworkIncidentSeverity.CRITICAL).count();
        container.addWidget(statusButton(open + " Open • " + critical + " Critical", incidents.size() + " Retained Incidents", critical > 0 ? ThemeManager.getAccent("danger") : open > 0 ? ThemeManager.getAccent("warning") : ThemeManager.getAccent("nice")));
        if (incidents.isEmpty()) {
            container.addWidget(statusButton("No Incidents", "Runtime And Drift History", ThemeManager.getAccent("nice")));
            return;
        }
        incidents.stream().limit(100).forEach(incident -> {
            String state = incident.status() == NetworkIncidentStatus.OPEN ? "Open" : "Resolved";
            String hint = state + " • " + incidentAge(incident.updatedAt()) + (incident.detail().isBlank() ? "" : " • " + incident.detail());
            Accent accent = incident.status() == NetworkIncidentStatus.RESOLVED ? ThemeManager.getDefaultAccent() : incident.severity() == NetworkIncidentSeverity.CRITICAL ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("warning");
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(incident.summary()).hint(hint).imagePath(incident.status() == NetworkIncidentStatus.OPEN ? "report.png" : "checkmark.png").accentType(accent).build());
        });
    }

    private void populateVerification(Container container) {
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Run Join Preflight").hint("Configuration, Readiness, Security, And Proxy Status").imagePath("checkmark.png").accentType(ThemeManager.getAccent("nice")).onClick(this::runPreflight).build());
        List<NetworkPreflightReport> reports = remotelyClient.getNetworkManager().getPreflightManager().getReports(network.networkId());
        if (reports.isEmpty()) {
            container.addWidget(statusButton("No Preflight Reports", "Run Verification After Starting The Network", ThemeManager.getDefaultAccent()));
            return;
        }
        NetworkPreflightReport latest = reports.getFirst();
        container.addWidget(statusButton(latest.summary(), latest.checks().size() + " Checks • Revision " + latest.networkRevision(), preflightAccent(latest.status())));
        for (var check : latest.checks()) {
            Accent accent = switch (check.status()) {
                case PASSED -> ThemeManager.getAccent("nice");
                case WARNING -> ThemeManager.getAccent("warning");
                case FAILED -> ThemeManager.getAccent("danger");
            };
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(check.label()).hint(check.detail()).imagePath(check.status() == NetworkPreflightCheckStatus.PASSED ? "checkmark.png" : check.status() == NetworkPreflightCheckStatus.WARNING ? "info.png" : "report.png").accentType(accent).build());
        }
        reports.stream().skip(1).limit(9).forEach(report -> container.addWidget(statusButton(report.summary(), "Revision " + report.networkRevision(), preflightAccent(report.status()))));
    }

    private void runPreflight() {
        Notification notification = operationNotification("Checking Join Path", network.name());
        remotelyClient.getNetworkManager().runPreflight(network, instances).whenComplete((report, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Preflight Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                refresh();
                return;
            }
            boolean passed = report.status() == NetworkPreflightStatus.SUCCEEDED;
            notification.update().message(passed ? "Join Path Ready" : "Join Path Needs Attention").description(report.summary()).type(passed ? Notification.Type.SUCCESS : Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            refresh();
        }));
    }

    private Accent preflightAccent(NetworkPreflightStatus status) {
        return switch (status) {
            case RUNNING -> ThemeManager.getAccent("calm");
            case SUCCEEDED -> ThemeManager.getAccent("nice");
            case FAILED -> ThemeManager.getAccent("danger");
        };
    }

    private void populateSecurity(Container container) {
        if (discovery.issues().isEmpty()) {
            container.addWidget(statusButton("No Findings", "Security", ThemeManager.getAccent("nice")));
        } else {
            for (NetworkValidationIssue issue : discovery.issues()) {
                Accent accent = switch (issue.severity()) {
                    case INFO -> ThemeManager.getAccent("calm");
                    case WARNING -> ThemeManager.getAccent("warning");
                    case ERROR -> ThemeManager.getAccent("danger");
                };
                container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(issue.message()).hint(issue.code()).imagePath(issue.blocksPersistence() ? "report.png" : "info.png").accentType(accent).build());
            }
        }
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Rotate Forwarding Secret").hint("Stopped Managed Networks Only").imagePath("reload.png").accentType(ThemeManager.getAccent("warning")).onClick(this::confirmSecretRotation).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Dissolve Network").imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(this::openDissolvePopup).build());
    }

    private void confirmSecretRotation() {
        PopupWidget[] popup = new PopupWidget[1];
        IconButton review = new IconButton.Builder().label("Review Secret Rotation").hint("Updates Proxy And Every Managed Backend").imagePath("reload.png").accentType(ThemeManager.getAccent("warning")).onClick(() -> {
            popup[0].hide();
            prepareSecretRotation();
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Rotate Forwarding Secret").size(410, 115).onClose(() -> popup[0].hide());
        builder.addRow("confirmSecretRotation", "Every Managed Network Server Must Be Stopped", true, 32, review);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void prepareSecretRotation() {
        Notification notification = operationNotification("Preparing Secret Rotation", network.name());
        remotelyClient.getNetworkManager().prepareSecretRotation(network, instances).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Secret Rotation Blocked").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Secret Rotation Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
        }));
    }

    private void createDissolvePopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Dissolve Network").size(300, 90).onClose(() -> dissolvePopup.hide());
        IconButton confirm = new IconButton.Builder().label("Restore Servers And Dissolve").imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(this::dissolve).build();
        builder.addRow("confirmDissolve", "", true, 28, confirm);
        dissolvePopup = builder.build();
        dissolvePopup.hide();
        addDrawableChild(dissolvePopup);
    }

    private void openDissolvePopup() {
        dissolvePopup.setX((width - dissolvePopup.getWidth()) / 2);
        dissolvePopup.setY((height - dissolvePopup.getHeight()) / 2);
        dissolvePopup.show();
    }

    private void dissolve() {
        dissolvePopup.hide();
        Notification notification = operationNotification("Dissolving Network", network.name());
        remotelyClient.getNetworkManager().dissolveSafely(network, instances, "Network Overview").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Dissolve Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            if (job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Network Needs Attention").description(job == null ? "Dissolve job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Network Dissolved").description(network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(parent);
        }));
    }

    private AnimatedButton statusButton(String label, String hint, Accent accent) {
        return new AnimatedButton.Builder().size(Math.max(220, width - 44), 22).label(label).hint(hint).accentType(accent).build();
    }

    private void openMember(NetworkMember member) {
        if (!member.isManaged()) {
            openExternalMember(member);
            return;
        }
        Instance instance = instancesById.get(member.instanceId());
        if (instance == null) {
            new Notification("Server Unavailable", member.routeName(), Notification.Type.ERROR);
            return;
        }
        PopupWidget[] popup = new PopupWidget[1];
        IconButton open = new IconButton.Builder().label("Open Server").imagePath("terminal.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
            popup[0].hide();
            remotelyClient.openInstanceInTerminal(this, instance);
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder(member.routeName()).size(380, 105).onClose(() -> popup[0].hide());
        if (member.isProxy()) {
            builder.addRow("managedServer", "Proxy Entry", true, 28, open);
        } else {
            IconButton detach = new IconButton.Builder().label("Detach Server").hint("Restore Independent Settings").imagePath("unmerge.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
                popup[0].hide();
                detachManaged(member, instance);
            }).build();
            builder.addRow("managedServer", "Managed Backend", true, 28, open, detach);
        }
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void openExternalMember(NetworkMember member) {
        PopupWidget[] popup = new PopupWidget[1];
        IconButton detach = new IconButton.Builder().label("Detach External Route").hint(member.address() + ":" + member.port()).imagePath("unmerge.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
            popup[0].hide();
            detachExternal(member);
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder(member.routeName() + " • External").size(360, 105).onClose(() -> popup[0].hide());
        builder.addRow("externalStatus", "Files, Forwarding, Firewall, And Lifecycle Stay Manual", true, 28, detach);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void openRuntimeControls(NetworkMember member) {
        PopupWidget[] popup = new PopupWidget[1];
        IconButton drain = new IconButton.Builder().label("Drain").hint("Stop New Connections").imagePath("stop.png").accentType(ThemeManager.getAccent("warning")).onClick(() -> setRuntimeMode(member, NetworkNodeStatus.DRAINING, popup[0])).build();
        IconButton maintenance = new IconButton.Builder().label("Maintenance").hint("Redirect Players").imagePath("edit.png").accentType(ThemeManager.getAccent("calm")).onClick(() -> setRuntimeMode(member, NetworkNodeStatus.MAINTENANCE, popup[0])).build();
        IconButton resume = new IconButton.Builder().label("Resume").hint("Accept Connections").imagePath("start.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> setRuntimeMode(member, NetworkNodeStatus.ONLINE, popup[0])).build();
        PopupWidget.Builder builder = new PopupWidget.Builder(member.routeName() + " Runtime").size(430, 88).onClose(() -> popup[0].hide());
        builder.addRow("runtimeMode", "Live Proxy Routing", true, 28, drain, maintenance, resume);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void setRuntimeMode(NetworkMember member, NetworkNodeStatus status, PopupWidget popup) {
        popup.hide();
        String action = status == NetworkNodeStatus.ONLINE ? "Resuming" : status == NetworkNodeStatus.DRAINING ? "Draining" : "Starting Maintenance";
        Notification notification = operationNotification(action + " Server", member.routeName());
        remotelyClient.getNetworkManager().setRuntimeNodeMode(networkId, member.nodeId(), status).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Runtime Operation Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Runtime Updated").description(member.routeName() + " • " + titleCase(status.name())).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            refresh();
        }));
    }

    private void executeProxyCommand(TextInputWidget input) {
        String command = input.getText().trim();
        Notification notification = operationNotification("Running Proxy Command", network.name());
        remotelyClient.getNetworkManager().executeRuntimeProxyCommand(networkId, command).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Proxy Command Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            input.setText("");
            notification.update().message("Proxy Command Complete").description(network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        }));
    }

    private void broadcastMessage(TextInputWidget input) {
        String message = input.getText().trim();
        Notification notification = operationNotification("Broadcasting Message", network.name());
        remotelyClient.getNetworkManager().broadcastRuntimeMessage(networkId, message).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Broadcast Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            input.setText("");
            notification.update().message("Broadcast Sent").description(network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        }));
    }

    private void detachExternal(NetworkMember member) {
        Notification notification = operationNotification("Detaching External Route", member.routeName());
        remotelyClient.getNetworkManager().detachExternalSafely(network, member, instances, "Network Overview").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, job, throwable, "External Route Detached")));
    }

    private void detachManaged(NetworkMember member, Instance instance) {
        Notification notification = operationNotification("Detaching Server", member.routeName());
        remotelyClient.getNetworkManager().detachSafely(network, instance, instances, "Network Overview").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, job, throwable, "Server Detached")));
    }

    private void reconcile() {
        Notification notification = operationNotification("Reconciling Network", network.name());
        remotelyClient.getNetworkManager().runJob(network, instances, List.of(), NetworkJobType.RECONCILE, "Network Overview").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, job, throwable)));
    }

    private void prepareReview() {
        Notification notification = operationNotification("Preparing Review", network.name());
        try {
            var plan = remotelyClient.getNetworkManager().plan(network, instances, List.of());
            remotelyClient.getNetworkManager().prepare(plan, instances).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
                if (throwable != null) {
                    notification.update().message("Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                    return;
                }
                notification.update().message("Review Ready").description(network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
                client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
            }));
        } catch (RuntimeException exception) {
            notification.update().message("Review Failed").description(rootMessage(exception)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
        }
    }

    private void resume(NetworkJob job) {
        Notification notification = operationNotification("Resuming Network", job.message());
        remotelyClient.getNetworkManager().resumeJob(job.jobId(), instances, List.of()).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, updated, throwable)));
    }

    private void rollback(NetworkJob job) {
        Notification notification = operationNotification("Rolling Back Network", job.message());
        remotelyClient.getNetworkManager().rollbackJob(job.jobId(), instances).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, updated, throwable)));
    }

    private void lifecycle(NetworkLifecycleOperation operation) {
        Notification notification = operationNotification(titleCase(operation.name()) + " Network", network.name());
        remotelyClient.getNetworkManager().runLifecycle(network, instances, operation, "Network Overview").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> finishLifecycle(notification, job, throwable)));
    }

    private void resumeLifecycle(NetworkLifecycleJob job) {
        Notification notification = operationNotification("Resuming " + titleCase(job.operation().name()), job.message());
        remotelyClient.getNetworkManager().resumeLifecycle(job.jobId(), instances).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishLifecycle(notification, updated, throwable)));
    }

    private Notification operationNotification(String message, String description) {
        return new Notification.Builder().message(message).description(description).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
    }

    private void finishOperation(Notification notification, NetworkJob job, Throwable throwable) {
        finishOperation(notification, job, throwable, "Network Ready");
    }

    private void finishOperation(Notification notification, NetworkJob job, Throwable throwable, String successMessage) {
        if (throwable != null) {
            notification.update().message("Network Operation Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        if (job == null || (job.status() != NetworkJobStatus.SUCCEEDED && job.status() != NetworkJobStatus.ROLLED_BACK)) {
            notification.update().message("Network Needs Attention").description(job == null ? "Network operation did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            refresh();
            return;
        }
        notification.update().message(job.status() == NetworkJobStatus.ROLLED_BACK ? "Network Rolled Back" : successMessage).description(job.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        refresh();
    }

    private void finishLifecycle(Notification notification, NetworkLifecycleJob job, Throwable throwable) {
        if (throwable != null) {
            notification.update().message("Network Operation Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            refresh();
            return;
        }
        if (job == null || job.status() != NetworkLifecycleStatus.SUCCEEDED) {
            notification.update().message("Network Needs Attention").description(job == null ? "Lifecycle job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            if (job == null || !openFailedLifecycleServer(job)) {
                refresh();
            }
            return;
        }
        notification.update().message("Network Ready").description(job.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        refresh();
    }

    private void refresh() {
        client.setScreen(new NetworkOverviewScreen(parent, remotelyClient, networkId));
    }

    private boolean openFailedLifecycleServer(NetworkLifecycleJob job) {
        NetworkLifecycleStep failed = job == null ? null : job.steps().stream().filter(step -> step.status() == NetworkLifecycleStepStatus.FAILED).findFirst().orElse(null);
        Instance instance = failed == null ? null : instancesById.get(failed.instanceId());
        if (instance == null) {
            return false;
        }
        remotelyClient.openInstanceInTerminal(this, instance);
        return true;
    }

    private String primaryEntry() {
        return network.entryPoints().isEmpty() ? "Unavailable" : network.entryPoints().getFirst().bindAddress() + ":" + network.entryPoints().getFirst().port();
    }

    private Accent jobAccent(NetworkJob job) {
        return switch (job.status()) {
            case SUCCEEDED -> ThemeManager.getAccent("nice");
            case BLOCKED, FAILED -> ThemeManager.getAccent("danger");
            case INTERRUPTED, ROLLING_BACK -> ThemeManager.getAccent("warning");
            default -> ThemeManager.getAccent("calm");
        };
    }

    private Accent lifecycleAccent(NetworkLifecycleJob job) {
        return switch (job.status()) {
            case SUCCEEDED -> ThemeManager.getAccent("nice");
            case FAILED -> ThemeManager.getAccent("danger");
            case INTERRUPTED -> ThemeManager.getAccent("warning");
            default -> ThemeManager.getAccent("calm");
        };
    }

    private String lifecycleStatus(NetworkLifecycleStatus status) {
        return switch (status) {
            case READY -> "Ready";
            case RUNNING -> "Running";
            case INTERRUPTED -> "Interrupted";
            case SUCCEEDED -> "Complete";
            case FAILED -> "Failed";
        };
    }

    private String jobStatus(NetworkJobStatus status) {
        return switch (status) {
            case PLANNING -> "Planning";
            case READY -> "Ready";
            case RUNNING -> "Running";
            case INTERRUPTED -> "Interrupted";
            case ROLLING_BACK -> "Rolling Back";
            case SUCCEEDED -> "Complete";
            case ROLLED_BACK -> "Rolled Back";
            case FAILED -> "Failed";
            case BLOCKED -> "Blocked";
        };
    }

    private String titleCase(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    private String incidentAge(long timestamp) {
        long seconds = Math.max(0, (System.currentTimeMillis() - timestamp) / 1000);
        if (seconds < 60) {
            return "Now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m Ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h Ago";
        }
        return hours / 24 + "d Ago";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
