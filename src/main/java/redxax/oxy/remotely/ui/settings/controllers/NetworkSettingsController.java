package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkEntryPoint;
import redxax.oxy.remotely.network.NetworkJob;
import redxax.oxy.remotely.network.NetworkLifecycleOperation;
import redxax.oxy.remotely.network.NetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.network.RoutingGroup;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.util.Identifier;
import restudio.resync.network.NetworkNodePresence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NetworkSettingsController {
    public static final String GENERAL_TAB = "General";
    public static final String SERVERS_TAB = "Servers";
    public static final String ROUTING_TAB = "Routing";
    public static final String RESYNC_TAB = "ReSync";
    public static final String OPERATIONS_TAB = "Operations";
    public static final String ADVANCED_TAB = "Advanced";

    private static final String EMPTY_ROW = "empty";
    private static final String RECOVERY_ACTIONS_ROW = "recovery-actions";

    private final Screen parentScreen;
    private final RemotelyClient remotelyClient;
    private final String networkId;
    private final BiConsumer<NetworkDefinition, String> renameAction;
    private final BiConsumer<NetworkDefinition, NetworkLifecycleOperation> lifecycleAction;
    private final Consumer<NetworkDefinition> syncAction;
    private final Consumer<NetworkDefinition> dissolveAction;
    private final Consumer<NetworkJob> resumeAction;
    private final Consumer<NetworkJob> rollbackAction;
    private final Consumer<Instance> openServerAction;
    private final Consumer<List<NetworkDefinition>> networkListener;
    private final Consumer<NetworkRuntimeSnapshot> runtimeListener;
    private final Consumer<List<NetworkJob>> jobListener;
    private final Map<String, MountableButtonWidget> entryPointCards = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> memberCards = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> routingCards = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> nodeCards = new LinkedHashMap<>();
    private SettingsScreen settingsScreen;
    private Setting entryPointSetting;
    private Setting memberSetting;
    private Setting routingSetting;
    private Setting nodeSetting;
    private Setting latestOperationSetting;
    private AnimatedButton generalServerCount;
    private AnimatedButton generalPlayerState;
    private AnimatedButton generalRuntimeHub;
    private AnimatedButton resyncState;
    private AnimatedButton resyncPlayers;
    private AnimatedButton resyncHub;
    private AnimatedButton operationState;
    private AnimatedButton resumeButton;
    private AnimatedButton rollbackButton;
    private AnimatedButton forwardingMode;
    private AnimatedButton proxyMode;
    private AnimatedButton revision;
    private AnimatedButton identifier;
    private String pendingName;
    private boolean listenersRegistered;

    public NetworkSettingsController(Screen parentScreen, RemotelyClient remotelyClient, NetworkDefinition network, BiConsumer<NetworkDefinition, String> renameAction, BiConsumer<NetworkDefinition, NetworkLifecycleOperation> lifecycleAction, Consumer<NetworkDefinition> syncAction, Consumer<NetworkDefinition> dissolveAction, Consumer<NetworkJob> resumeAction, Consumer<NetworkJob> rollbackAction, Consumer<Instance> openServerAction) {
        this.parentScreen = parentScreen;
        this.remotelyClient = remotelyClient;
        this.networkId = network.networkId();
        this.pendingName = network.name();
        this.renameAction = renameAction;
        this.lifecycleAction = lifecycleAction;
        this.syncAction = syncAction;
        this.dissolveAction = dissolveAction;
        this.resumeAction = resumeAction;
        this.rollbackAction = rollbackAction;
        this.openServerAction = openServerAction;
        this.networkListener = ignored -> ScreenManager.getInstance().execute(this::applyDefinitionUpdate);
        this.runtimeListener = snapshot -> {
            if (snapshot.networkId().equals(networkId)) {
                ScreenManager.getInstance().execute(() -> applyRuntimeUpdate(snapshot));
            }
        };
        this.jobListener = ignored -> ScreenManager.getInstance().execute(this::applyJobUpdate);
    }

    public Map<String, Supplier<List<Setting>>> categories() {
        Map<String, Supplier<List<Setting>>> categories = new LinkedHashMap<>();
        categories.put(GENERAL_TAB, this::getGeneralSettings);
        categories.put(SERVERS_TAB, this::getServerSettings);
        categories.put(ROUTING_TAB, this::getRoutingSettings);
        categories.put(RESYNC_TAB, this::getReSyncSettings);
        categories.put(OPERATIONS_TAB, this::getOperationSettings);
        categories.put(ADVANCED_TAB, this::getAdvancedSettings);
        return categories;
    }

    public void bind(SettingsScreen settingsScreen) {
        this.settingsScreen = settingsScreen;
        if (listenersRegistered) return;
        manager().addListener(networkListener);
        manager().addRuntimeListener(runtimeListener);
        manager().getJobManager().addListener(jobListener);
        listenersRegistered = true;
    }

    public void cleanup() {
        if (!listenersRegistered) return;
        manager().removeListener(networkListener);
        manager().removeRuntimeListener(runtimeListener);
        manager().getJobManager().removeListener(jobListener);
        listenersRegistered = false;
    }

    public void save() {
        NetworkDefinition network = network();
        String name = pendingName == null ? "" : pendingName.trim();
        if (network == null || name.isBlank() || name.equals(network.name())) return;
        renameAction.accept(network, name);
    }

    public List<Setting> getGeneralSettings() {
        NetworkDefinition network = network();
        if (network == null) return unavailable();
        NetworkRuntimeSnapshot snapshot = manager().getRuntimeSnapshot(networkId);

        Setting.Builder identity = new Setting.Builder("Network Identity");
        identity.addOption(ConfigOption.<String>builder("Network Name").description("The name used throughout Remotely and ReSync.").bind(() -> pendingName, value -> pendingName = value).defaultValue(network.name()).resettable(false).build());
        generalServerCount = inactive(network.members().size() + " Servers", "calm");
        generalPlayerState = inactive(runtimeLabel(snapshot), runtimeAccent(snapshot));
        identity.addRow("", true, 22, generalServerCount, generalPlayerState);

        Setting.Builder entryPoints = new Setting.Builder("Entry Points");
        entryPoints.addRow(EMPTY_ROW, "", true, 22, inactive("No Entry Point", "warning"));
        entryPointSetting = entryPoints.build();
        reconcileEntryPoints(network);

        Setting.Builder runtime = new Setting.Builder("Network Runtime");
        generalRuntimeHub = inactive(network.runtime().enabled() ? network.runtime().hubUrl() : "ReSync Runtime Disabled", network.runtime().enabled() ? "calm" : "warning");
        runtime.addRow("", true, 22, generalRuntimeHub);
        return List.of(identity.build(), entryPointSetting, runtime.build());
    }

    public List<Setting> getServerSettings() {
        NetworkDefinition network = network();
        if (network == null) return unavailable();
        Setting.Builder members = new Setting.Builder("Network Servers");
        members.addRow(EMPTY_ROW, "", true, 22, inactive("No Network Servers", "warning"));
        memberSetting = members.build();
        reconcileMembers(network, manager().getRuntimeSnapshot(networkId));
        return List.of(memberSetting);
    }

    public List<Setting> getRoutingSettings() {
        NetworkDefinition network = network();
        if (network == null) return unavailable();
        Setting.Builder routing = new Setting.Builder("Routing Groups");
        routing.addRow(EMPTY_ROW, "", true, 22, inactive("No Routing Groups", "calm"));
        routingSetting = routing.build();
        reconcileRouting(network);
        return List.of(routingSetting);
    }

    public List<Setting> getReSyncSettings() {
        NetworkDefinition network = network();
        if (network == null) return unavailable();
        NetworkRuntimeSnapshot snapshot = manager().getRuntimeSnapshot(networkId);
        Setting.Builder runtime = new Setting.Builder("ReSync Network Runtime");
        resyncState = inactive(format(snapshot.state().name()), snapshot.connected() ? "nice" : "warning");
        resyncPlayers = inactive(snapshot.players() + " Shared Players", snapshot.connected() ? "nice" : "calm");
        runtime.addRow("", true, 22, resyncState, resyncPlayers);
        resyncHub = inactive(network.runtime().enabled() ? network.runtime().hubUrl() : "Runtime Disabled", network.runtime().transportReady() ? "calm" : "warning");
        runtime.addRow("", true, 22, resyncHub);

        Setting.Builder nodes = new Setting.Builder("ReSync Nodes");
        nodes.addRow(EMPTY_ROW, "", true, 22, inactive("No Enrolled Backends", "warning"));
        nodeSetting = nodes.build();
        reconcileNodes(network, snapshot);
        return List.of(runtime.build(), nodeSetting);
    }

    public List<Setting> getOperationSettings() {
        NetworkDefinition network = network();
        if (network == null) return unavailable();
        Setting.Builder lifecycle = new Setting.Builder("Network Power");
        lifecycle.addRow("", true, 28,
            action("Start", "nice", () -> lifecycleAction.accept(currentNetwork(network), NetworkLifecycleOperation.START)),
            action("Stop", "danger", () -> lifecycleAction.accept(currentNetwork(network), NetworkLifecycleOperation.STOP)),
            action("Restart", "warning", () -> lifecycleAction.accept(currentNetwork(network), NetworkLifecycleOperation.RESTART)));

        Setting.Builder configuration = new Setting.Builder("Configuration");
        configuration.addRow("", true, 22, inactive("Ports • Routes • Forwarding • ReSync", "calm"));
        configuration.addRow("", true, 26, action("Sync Config", "nice", () -> syncAction.accept(currentNetwork(network))));

        Setting.Builder latest = new Setting.Builder("Latest Operation");
        operationState = inactive("No Operations Yet", "calm");
        latest.addRow("", true, 22, operationState);
        resumeButton = action("Resume", "nice", () -> {});
        rollbackButton = action("Rollback", "danger", () -> {});
        latest.addRow(RECOVERY_ACTIONS_ROW, "", true, 26, resumeButton, rollbackButton);
        latestOperationSetting = latest.build();
        applyJobUpdate();
        return List.of(lifecycle.build(), configuration.build(), latestOperationSetting);
    }

    public List<Setting> getAdvancedSettings() {
        NetworkDefinition network = network();
        if (network == null) return unavailable();
        Setting.Builder security = new Setting.Builder("Forwarding And Identity");
        forwardingMode = inactive(format(network.forwarding().mode().name()) + " Forwarding", "calm");
        proxyMode = inactive(network.forwarding().proxyOnlineMode() ? "Proxy Online Mode" : "Proxy Offline Mode", network.forwarding().proxyOnlineMode() ? "nice" : "warning");
        security.addRow("", true, 22, forwardingMode, proxyMode);
        revision = inactive("Revision " + network.revision(), "calm");
        identifier = inactive(network.networkId(), "calm");
        security.addRow("", true, 22, revision, identifier);

        Setting.Builder destructive = new Setting.Builder("Dissolve Network");
        destructive.addRow("", true, 22, inactive("Restore Every Server To Standalone", "warning"));
        destructive.addRow("", true, 26, action("Dissolve Network", "danger", this::showDissolveConfirmation));
        return List.of(security.build(), destructive.build());
    }

    private void applyDefinitionUpdate() {
        NetworkDefinition network = network();
        if (network == null) {
            if (settingsScreen != null && ScreenManager.getInstance().getCurrentScreen() == settingsScreen) {
                cleanup();
                ScreenManager.getInstance().setScreen(parentScreen);
            }
            return;
        }
        if (pendingName == null || pendingName.isBlank()) pendingName = network.name();
        NetworkRuntimeSnapshot snapshot = manager().getRuntimeSnapshot(networkId);
        setInactive(generalServerCount, network.members().size() + " Servers", "calm");
        setInactive(generalRuntimeHub, network.runtime().enabled() ? network.runtime().hubUrl() : "ReSync Runtime Disabled", network.runtime().enabled() ? "calm" : "warning");
        setInactive(resyncHub, network.runtime().enabled() ? network.runtime().hubUrl() : "Runtime Disabled", network.runtime().transportReady() ? "calm" : "warning");
        setInactive(forwardingMode, format(network.forwarding().mode().name()) + " Forwarding", "calm");
        setInactive(proxyMode, network.forwarding().proxyOnlineMode() ? "Proxy Online Mode" : "Proxy Offline Mode", network.forwarding().proxyOnlineMode() ? "nice" : "warning");
        setInactive(revision, "Revision " + network.revision(), "calm");
        setInactive(identifier, network.networkId(), "calm");
        reconcileEntryPoints(network);
        reconcileMembers(network, snapshot);
        reconcileRouting(network);
        reconcileNodes(network, snapshot);
        applyRuntimeUpdate(snapshot);
    }

    private void applyRuntimeUpdate(NetworkRuntimeSnapshot snapshot) {
        NetworkDefinition network = network();
        if (network == null) return;
        setInactive(generalPlayerState, runtimeLabel(snapshot), runtimeAccent(snapshot));
        setInactive(resyncState, format(snapshot.state().name()), snapshot.connected() ? "nice" : "warning");
        setInactive(resyncPlayers, snapshot.players() + " Shared Players", snapshot.connected() ? "nice" : "calm");
        Map<String, Instance> instances = instanceIndex();
        for (NetworkMember member : network.members()) {
            MountableButtonWidget card = memberCards.get(member.instanceId());
            if (card != null) updateMemberCard(card, member, instances.get(member.instanceId()), snapshot);
            MountableButtonWidget nodeCard = nodeCards.get(member.nodeId());
            if (nodeCard != null) updateNodeCard(nodeCard, member, snapshot);
        }
    }

    private void applyJobUpdate() {
        if (latestOperationSetting == null) return;
        NetworkJob latest = manager().getJobManager().getJobs(networkId).stream().findFirst().orElse(null);
        if (latest == null) {
            setInactive(operationState, "No Operations Yet", "calm");
            latestOperationSetting.setRowVisibility(RECOVERY_ACTIONS_ROW, false);
            return;
        }
        setInactive(operationState, format(latest.type().name()) + " • " + format(latest.status().name()), latest.status().requiresRecovery() ? "warning" : "calm");
        resumeButton.setAction(() -> resumeAction.accept(latest));
        rollbackButton.setAction(() -> rollbackAction.accept(latest));
        resumeButton.setActive(latest.canResume());
        rollbackButton.setActive(latest.canRollback());
        latestOperationSetting.setRowVisibility(RECOVERY_ACTIONS_ROW, latest.canResume() || latest.canRollback());
        resumeButton.setVisible(latest.canResume());
        rollbackButton.setVisible(latest.canRollback());
    }

    private void reconcileEntryPoints(NetworkDefinition network) {
        if (entryPointSetting == null) return;
        Set<String> desired = new HashSet<>();
        for (NetworkEntryPoint point : network.entryPoints()) desired.add(point.id());
        removeMissingRows(entryPointSetting, entryPointCards, desired, "entry:");
        for (NetworkEntryPoint point : network.entryPoints()) {
            MountableButtonWidget card = entryPointCards.get(point.id());
            if (card == null) {
                card = new MountableButtonWidget.Builder(format(point.id())).iconPath("velocity.png").build();
                entryPointCards.put(point.id(), card);
                entryPointSetting.addRow("entry:" + point.id(), "", List.of(card), 30, true, false);
            }
            String host = point.bindAddress().isBlank() || point.bindAddress().equals("0.0.0.0") ? "All Interfaces" : point.bindAddress();
            updateCard(card, format(point.id()), host + ":" + point.port(), point.forcedHosts().isEmpty() ? "No Forced Hosts" : String.join(", ", point.forcedHosts()));
        }
        entryPointSetting.setRowVisibility(EMPTY_ROW, network.entryPoints().isEmpty());
    }

    private void reconcileMembers(NetworkDefinition network, NetworkRuntimeSnapshot snapshot) {
        if (memberSetting == null) return;
        Map<String, Instance> instances = instanceIndex();
        Set<String> desired = new HashSet<>();
        for (NetworkMember member : network.members()) desired.add(member.instanceId());
        removeMissingRows(memberSetting, memberCards, desired, "member:");
        for (NetworkMember member : network.members()) {
            MountableButtonWidget card = memberCards.get(member.instanceId());
            Instance instance = instances.get(member.instanceId());
            if (card == null) {
                MountableButtonWidget.Builder builder = new MountableButtonWidget.Builder(instance == null ? member.routeName() : instance.getName()).iconPath(member.isProxy() ? "velocity.png" : "server.png");
                if (instance != null) builder.addButton(new SquareButtonWidget.Builder().identifier(Identifier.icon("terminal.png")).hint("Open Server").onClick(() -> openServer(instance)).build());
                card = builder.build();
                memberCards.put(member.instanceId(), card);
                memberSetting.addRow("member:" + member.instanceId(), "", List.of(card), 34, true, false);
            }
            updateMemberCard(card, member, instance, snapshot);
        }
        memberSetting.setRowVisibility(EMPTY_ROW, network.members().isEmpty());
    }

    private void reconcileRouting(NetworkDefinition network) {
        if (routingSetting == null) return;
        Set<String> desired = new HashSet<>();
        for (RoutingGroup group : network.routingGroups()) desired.add(group.id());
        removeMissingRows(routingSetting, routingCards, desired, "routing:");
        for (RoutingGroup group : network.routingGroups()) {
            MountableButtonWidget card = routingCards.get(group.id());
            if (card == null) {
                card = new MountableButtonWidget.Builder(group.name().isBlank() ? format(group.id()) : group.name()).iconPath("graph.png").build();
                routingCards.put(group.id(), card);
                routingSetting.addRow("routing:" + group.id(), "", List.of(card), 32, true, false);
            }
            List<String> routes = group.nodeIds().stream().map(nodeId -> routeForNode(network, nodeId)).filter(route -> !route.isBlank()).toList();
            String details = routes.isEmpty() ? "No Routes" : String.join(" → ", routes);
            if (!group.forcedHosts().isEmpty()) details += " • " + String.join(", ", group.forcedHosts());
            updateCard(card, group.name().isBlank() ? format(group.id()) : group.name(), format(group.strategy().name()) + " • " + group.nodeIds().size() + " Servers", details);
        }
        routingSetting.setRowVisibility(EMPTY_ROW, network.routingGroups().isEmpty());
    }

    private void reconcileNodes(NetworkDefinition network, NetworkRuntimeSnapshot snapshot) {
        if (nodeSetting == null) return;
        List<NetworkMember> enrolled = network.members().stream().filter(member -> !member.isProxy() && member.resyncEnabled()).toList();
        Set<String> desired = new HashSet<>();
        for (NetworkMember member : enrolled) desired.add(member.nodeId());
        removeMissingRows(nodeSetting, nodeCards, desired, "node:");
        for (NetworkMember member : enrolled) {
            MountableButtonWidget card = nodeCards.get(member.nodeId());
            if (card == null) {
                card = new MountableButtonWidget.Builder(member.routeName()).iconPath("ReSync.png").build();
                nodeCards.put(member.nodeId(), card);
                nodeSetting.addRow("node:" + member.nodeId(), "", List.of(card), 32, true, false);
            }
            updateNodeCard(card, member, snapshot);
        }
        nodeSetting.setRowVisibility(EMPTY_ROW, enrolled.isEmpty());
    }

    private void updateMemberCard(MountableButtonWidget card, NetworkMember member, Instance instance, NetworkRuntimeSnapshot snapshot) {
        NetworkNodePresence presence = snapshot.node(member.nodeId()).orElse(null);
        String name = instance == null ? member.routeName() : instance.getName();
        String role = member.isProxy() ? "Proxy" : format(member.role().name());
        String route = member.isProxy() ? member.address() + ":" + member.port() : member.routeName() + " • " + member.address() + ":" + member.port();
        String runtime = presence == null ? member.resyncEnabled() ? "ReSync Offline" : "ReSync Disabled" : format(presence.status().name()) + " • " + presence.players() + (presence.capacity() > 0 ? "/" + presence.capacity() : "") + " Players";
        updateCard(card, name, role + " • " + route, runtime);
    }

    private void updateNodeCard(MountableButtonWidget card, NetworkMember member, NetworkRuntimeSnapshot snapshot) {
        NetworkNodePresence presence = snapshot.node(member.nodeId()).orElse(null);
        String state = presence == null ? "Offline" : format(presence.status().name());
        String metrics = presence == null ? member.routeName() : presence.players() + (presence.capacity() > 0 ? "/" + presence.capacity() : "") + " Players • " + String.format(Locale.ROOT, "%.1f TPS • %.1f MSPT", presence.tps(), presence.mspt());
        updateCard(card, member.routeName(), state, metrics);
    }

    private <T> void removeMissingRows(Setting setting, Map<String, T> cards, Set<String> desired, String rowPrefix) {
        List<String> removed = cards.keySet().stream().filter(id -> !desired.contains(id)).toList();
        for (String id : removed) {
            cards.remove(id);
            setting.removeRow(rowPrefix + id);
        }
    }

    private void showDissolveConfirmation() {
        NetworkDefinition network = network();
        Screen current = ScreenManager.getInstance().getCurrentScreen();
        if (network == null || current == null) return;
        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton confirm = action("Restore Servers And Dissolve", "danger", () -> {
            popupRef[0].hide();
            dissolveAction.accept(currentNetwork(network));
        });
        PopupWidget.Builder builder = new PopupWidget.Builder("Dissolve " + network.name()).size(380, 108);
        builder.addRow("", true, 22, inactive("Servers Become Standalone", "warning"));
        builder.addRow("", true, 28, confirm);
        PopupWidget popup = builder.build();
        popupRef[0] = popup;
        popup.setX((current.width - popup.getWidth()) / 2);
        popup.setY((current.height - popup.getHeight()) / 2);
        current.addDrawableChild(popup);
        popup.show();
    }

    private void openServer(Instance instance) {
        cleanup();
        openServerAction.accept(instance);
    }

    private List<Setting> unavailable() {
        Setting.Builder setting = new Setting.Builder("Network Unavailable");
        setting.addRow("", true, 22, inactive("This Network No Longer Exists", "danger"));
        return List.of(setting.build());
    }

    private NetworkDefinition currentNetwork(NetworkDefinition fallback) {
        NetworkDefinition current = network();
        return current == null ? fallback : current;
    }

    private NetworkDefinition network() {
        return manager().getNetwork(networkId).orElse(null);
    }

    private NetworkManager manager() {
        return remotelyClient.getNetworkManager();
    }

    private Map<String, Instance> instanceIndex() {
        return indexInstances(Rebase.get().getInstanceManager().getAllInstances());
    }

    private Map<String, Instance> indexInstances(Collection<Instance> instances) {
        Map<String, Instance> indexed = new LinkedHashMap<>();
        if (instances != null) instances.stream().filter(instance -> instance != null && !instance.getInstanceId().isBlank()).forEach(instance -> indexed.put(instance.getInstanceId(), instance));
        return indexed;
    }

    private String routeForNode(NetworkDefinition network, String nodeId) {
        return network.members().stream().filter(member -> member.nodeId().equals(nodeId)).map(NetworkMember::routeName).findFirst().orElse("");
    }

    private String runtimeLabel(NetworkRuntimeSnapshot snapshot) {
        return snapshot.connected() ? snapshot.players() + " Players" : format(snapshot.state().name());
    }

    private String runtimeAccent(NetworkRuntimeSnapshot snapshot) {
        return snapshot.connected() ? "nice" : "warning";
    }

    private void updateCard(MountableButtonWidget card, String name, String description, String hiddenText) {
        if (!Objects.equals(card.name, name)) card.setName(name);
        if (!Objects.equals(card.description, description)) card.setDescription(description);
        if (!Objects.equals(card.hiddenText, hiddenText)) card.setHiddenText(hiddenText);
    }

    private void setInactive(AnimatedButton button, String label, String accent) {
        if (button == null) return;
        if (!Objects.equals(button.getMessage(), label)) button.setMessage(label);
        button.setAccent(ThemeManager.getAccent(accent));
    }

    private AnimatedButton inactive(String label, String accent) {
        return new AnimatedButton.Builder().label(label).active(false).accentType(ThemeManager.getAccent(accent)).build();
    }

    private AnimatedButton action(String label, String accent, Runnable action) {
        return new AnimatedButton.Builder().label(label).accentType(ThemeManager.getAccent(accent)).onClick(action).build();
    }

    private String format(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String[] parts = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        List<String> formatted = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) formatted.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", formatted);
    }
}
