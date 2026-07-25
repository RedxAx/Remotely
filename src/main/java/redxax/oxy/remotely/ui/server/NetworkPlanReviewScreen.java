package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkConfigMutation;
import redxax.oxy.remotely.network.NetworkAttachPreparedPlan;
import redxax.oxy.remotely.network.NetworkCreationPreparedPlan;
import redxax.oxy.remotely.network.NetworkJob;
import redxax.oxy.remotely.network.NetworkJobStatus;
import redxax.oxy.remotely.network.NetworkJobType;
import redxax.oxy.remotely.network.NetworkLifecycleOperation;
import redxax.oxy.remotely.network.NetworkLifecycleStatus;
import redxax.oxy.remotely.network.NetworkMutationAction;
import redxax.oxy.remotely.network.NetworkPreflightStatus;
import redxax.oxy.remotely.network.NetworkPreparedPlan;
import redxax.oxy.remotely.network.NetworkRealmPreparedPlan;
import redxax.oxy.remotely.network.NetworkRoutingPreparedPlan;
import redxax.oxy.remotely.network.NetworkSecretRotationPreparedPlan;
import redxax.oxy.remotely.network.NetworkValidationIssue;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.util.Notification;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class NetworkPlanReviewScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final NetworkPreparedPlan prepared;
    private final NetworkRoutingPreparedPlan routingPrepared;
    private final NetworkRealmPreparedPlan realmPrepared;
    private final NetworkCreationPreparedPlan creationPrepared;
    private final NetworkAttachPreparedPlan attachPrepared;
    private final NetworkSecretRotationPreparedPlan rotationPrepared;
    private List<Instance> instances = List.of();
    private boolean applying;

    public NetworkPlanReviewScreen(Screen parent, RemotelyClient remotelyClient, NetworkPreparedPlan prepared) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.prepared = prepared;
        this.routingPrepared = null;
        this.realmPrepared = null;
        this.creationPrepared = null;
        this.attachPrepared = null;
        this.rotationPrepared = null;
    }

    public NetworkPlanReviewScreen(Screen parent, RemotelyClient remotelyClient, NetworkRoutingPreparedPlan routingPrepared) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.prepared = routingPrepared.prepared();
        this.routingPrepared = routingPrepared;
        this.realmPrepared = null;
        this.creationPrepared = null;
        this.attachPrepared = null;
        this.rotationPrepared = null;
    }

    public NetworkPlanReviewScreen(Screen parent, RemotelyClient remotelyClient, NetworkCreationPreparedPlan creationPrepared) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.prepared = creationPrepared.prepared();
        this.routingPrepared = null;
        this.realmPrepared = null;
        this.creationPrepared = creationPrepared;
        this.attachPrepared = null;
        this.rotationPrepared = null;
    }

    public NetworkPlanReviewScreen(Screen parent, RemotelyClient remotelyClient, NetworkAttachPreparedPlan attachPrepared) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.prepared = attachPrepared.prepared();
        this.routingPrepared = null;
        this.realmPrepared = null;
        this.creationPrepared = null;
        this.attachPrepared = attachPrepared;
        this.rotationPrepared = null;
    }

    public NetworkPlanReviewScreen(Screen parent, RemotelyClient remotelyClient, NetworkSecretRotationPreparedPlan rotationPrepared) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.prepared = rotationPrepared.prepared();
        this.routingPrepared = null;
        this.realmPrepared = null;
        this.creationPrepared = null;
        this.attachPrepared = null;
        this.rotationPrepared = rotationPrepared;
    }

    public NetworkPlanReviewScreen(Screen parent, RemotelyClient remotelyClient, NetworkRealmPreparedPlan realmPrepared) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.prepared = realmPrepared.prepared();
        this.routingPrepared = null;
        this.realmPrepared = realmPrepared;
        this.creationPrepared = null;
        this.attachPrepared = null;
        this.rotationPrepared = null;
    }

    public String getDesktopAppId() {
        return "network-change-review";
    }

    public String getDesktopAppTitle() {
        return "Network Changes";
    }

    public String getDesktopAppIconPath() {
        return "edit.png";
    }

    @Override
    public void init() {
        super.init();
        instances = Rebase.get().getInstanceManager().getAllInstances();
        header().addLeft("close.png", this::back, "Back").addRight("checkmark.png", this::apply, "Apply Changes").build();
        int contentY = 60;
        int contentHeight = Math.max(80, height - contentY - 6);
        Container changes = createContainer("network_review_changes", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        Container issues = createContainer("network_review_issues", 6, contentY, width - 12, contentHeight).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populateChanges(changes);
        populateIssues(issues);
        tabs().addTab("Changes", changes);
        tabs().addTab("Findings", issues);
        tabsManager.builder().allowAdd(false).allowClose(false).allowRename(false).allowReorder(false).position(6, 36).size(width - 12, 18).onTabSelected(tab -> setActiveContainer(tab.getContainer())).build();
        tabs().setActiveTab(changes);
        setActiveContainer(changes);
    }

    private void populateChanges(Container container) {
        List<NetworkConfigMutation> changes = prepared.plan().changes();
        int documentCount = (int) changes.stream().map(mutation -> mutation.instanceId() + "\u0000" + mutation.path()).distinct().count();
        String impact = prepared.plan().restartRequired() ? "Restart Required" : "Live Safe";
        container.addWidget(summaryButton(changes.size() + " Changes • " + documentCount + " Files • " + impact, prepared.plan().planId(), prepared.plan().canApply() ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")));
        if (changes.isEmpty()) {
            container.addWidget(summaryButton("No Configuration Drift", "Nothing To Apply", ThemeManager.getAccent("nice")));
            return;
        }
        for (NetworkConfigMutation mutation : changes) {
            String label = mutation.path() + " • " + mutation.key();
            String before = mutation.displayCurrentValue().isBlank() ? "Unset" : mutation.displayCurrentValue();
            String after = mutation.displayDesiredValue().isBlank() ? "Empty" : mutation.displayDesiredValue();
            String hint = before + " → " + after + " • " + mutation.description();
            Accent accent = mutation.sensitive() ? ThemeManager.getDefaultAccent() : mutation.restartRequired() ? ThemeManager.getAccent("calm") : ThemeManager.getAccent("nice");
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 30).label(label).hint(hint).imagePath(mutation.sensitive() ? "shades.png" : mutation.action() == NetworkMutationAction.REMOVE ? "delete.png" : "edit.png").accentType(accent).build());
        }
    }

    private void populateIssues(Container container) {
        if (prepared.plan().issues().isEmpty()) {
            container.addWidget(summaryButton("No Findings", "Plan Validation", ThemeManager.getAccent("nice")));
            return;
        }
        for (NetworkValidationIssue issue : prepared.plan().issues()) {
            Accent accent = switch (issue.severity()) {
                case INFO -> ThemeManager.getAccent("calm");
                case WARNING -> ThemeManager.getDefaultAccent();
                case ERROR -> ThemeManager.getAccent("danger");
            };
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 28).label(issue.message()).hint(issue.code()).imagePath(issue.blocksPersistence() ? "report.png" : "info.png").accentType(accent).build());
        }
    }

    private AnimatedButton summaryButton(String label, String hint, Accent accent) {
        return new AnimatedButton.Builder().size(Math.max(220, width - 44), 22).label(label).hint(hint).accentType(accent).build();
    }

    private void apply() {
        if (applying) {
            return;
        }
        if (!prepared.plan().canApply()) {
            new Notification("Changes Blocked", "Resolve Blocking Findings", Notification.Type.ERROR);
            return;
        }
        applying = true;
        Notification notification = new Notification.Builder().message("Applying Network").description(prepared.plan().changes().size() + " Changes").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        var operation = creationPrepared != null ? remotelyClient.getNetworkManager().runPreparedCreation(creationPrepared, instances, "Creation Review") : attachPrepared != null ? remotelyClient.getNetworkManager().runPreparedAttach(attachPrepared, instances, "Attach Review") : routingPrepared != null ? remotelyClient.getNetworkManager().runPreparedRouting(routingPrepared, instances, "Routing Review") : realmPrepared != null ? remotelyClient.getNetworkManager().runPreparedRealms(realmPrepared, instances, "Realm Review") : rotationPrepared != null ? remotelyClient.getNetworkManager().runPreparedSecretRotation(rotationPrepared, instances, "Secret Rotation Review") : remotelyClient.getNetworkManager().runPreparedJob(prepared, instances, NetworkJobType.RECONCILE, "Change Review");
        operation.whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> finishApply(notification, job, throwable)));
    }

    private void finishApply(Notification notification, NetworkJob job, Throwable throwable) {
        applying = false;
        if (throwable != null) {
            notification.update().message("Network Apply Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        if (job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
            notification.update().message("Network Needs Attention").description(job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        if (creationPrepared != null) {
            startCreatedNetwork(notification);
            return;
        }
        notification.update().message(rotationPrepared == null ? "Network Ready" : "Forwarding Secret Rotated").description(job.restartRequired() ? "Restart Affected Servers To Apply Changes" : job.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        openNetworkManager(job.networkId());
    }

    private void startCreatedNetwork(Notification notification) {
        applying = true;
        var network = remotelyClient.getNetworkManager().getNetwork(creationPrepared.candidate().networkId()).orElse(null);
        if (network == null) {
            applying = false;
            notification.update().message("Network Commit Failed").description("Created Network Is Unavailable").type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        notification.update().message("Starting Network").description(network.name()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).commit();
        remotelyClient.getNetworkManager().runLifecycle(network, instances, NetworkLifecycleOperation.START, "Network Creation").whenComplete((lifecycle, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null || lifecycle == null || lifecycle.status() != NetworkLifecycleStatus.SUCCEEDED) {
                applying = false;
                String detail = throwable != null ? rootMessage(throwable) : lifecycle == null ? "Lifecycle job did not finish" : lifecycle.message();
                notification.update().message("Network Created").description("Startup Needs Attention • " + detail).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                openNetworkManager(network.networkId());
                return;
            }
            notification.update().message("Checking Join Path").description(network.name()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).commit();
            remotelyClient.getNetworkManager().getNetwork(network.networkId()).ifPresentOrElse(current -> remotelyClient.getNetworkManager().runPreflight(current, instances).whenComplete((report, preflightThrowable) -> ScreenManager.getInstance().execute(() -> finishCreatedNetwork(notification, current.networkId(), report == null ? null : report.status(), report == null ? "Preflight did not finish" : report.summary(), preflightThrowable))), () -> ScreenManager.getInstance().execute(() -> finishCreatedNetwork(notification, network.networkId(), null, "Created Network Is Unavailable", null)));
        }));
    }

    private void finishCreatedNetwork(Notification notification, String networkId, NetworkPreflightStatus status, String detail, Throwable throwable) {
        applying = false;
        boolean ready = throwable == null && status == NetworkPreflightStatus.SUCCEEDED;
        notification.update().message(ready ? "Network Ready" : "Network Needs Attention").description(throwable == null ? detail : rootMessage(throwable)).type(ready ? Notification.Type.SUCCESS : Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
        openNetworkManager(networkId);
    }

    private void openNetworkManager(String networkId) {
        if (parent instanceof NetworkCreationScreen creation) {
            creation.openCreatedNetwork(networkId);
            return;
        }
        if (parent instanceof NetworkAttachScreen attach) {
            attach.openNetworkManager();
            return;
        }
        if (parent instanceof NetworkRoutingScreen routing) {
            routing.openNetworkManager();
            return;
        }
        if (parent instanceof NetworkRealmScreen realm) {
            realm.openNetworkManager();
            return;
        }
        client.setScreen(parent);
        if (parent instanceof ServerManagerScreen serverManager) {
            serverManager.showNetworkSettings(networkId);
        }
    }

    private void back() {
        if (!applying && creationPrepared != null) {
            remotelyClient.getNetworkManager().discardPreparedCreation(creationPrepared);
        }
        if (!applying && rotationPrepared != null) {
            remotelyClient.getNetworkManager().discardPreparedSecretRotation(rotationPrepared);
        }
        client.setScreen(parent);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
