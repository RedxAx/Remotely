package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.Config;
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
import redxax.oxy.remotely.network.NetworkLifecycleStatus;
import redxax.oxy.remotely.network.NetworkHostScope;
import redxax.oxy.remotely.network.NetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkMemberRole;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.network.NetworkSharedDataPolicy;
import redxax.oxy.remotely.network.NetworkPreflightStatus;
import redxax.oxy.remotely.network.NetworkValidationIssue;
import redxax.oxy.remotely.network.RoutingGroup;
import redxax.oxy.remotely.network.RoutingStrategy;
import redxax.oxy.remotely.network.SyncDataFamily;
import redxax.oxy.remotely.network.SyncLocationPolicy;
import redxax.oxy.remotely.network.SyncRealm;
import redxax.oxy.remotely.ui.widgets.LifecycleButtonWidget;
import redxax.oxy.remotely.ui.widgets.NetworkTopologyWidget;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.util.Executors;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class NetworkOverviewScreen extends ReScreen {
    static final List<String> TAB_NAMES = List.of("Overview", "Servers", "Sharing", "Settings");
    static final List<String> RIGHT_HEADER_ACTIONS = List.of("Save", "Close");
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final String networkId;
    private NetworkDefinition network;
    private NetworkDiscoveryResult discovery;
    private List<Instance> instances = List.of();
    private final Map<String, Instance> instancesById = new LinkedHashMap<>();
    private PopupWidget dissolvePopup;
    private TextInputWidget networkNameInput;
    private final List<ConfigOption<?>> sharingOptions = new ArrayList<>();
    private List<RoutingGroup> routingGroups = List.of();
    private List<SyncRealm> syncRealms = List.of();
    private Container overviewContainer;
    private Container serversContainer;
    private Container sharingContainer;
    private Container settingsContainer;
    private NetworkTopologyWidget topologyWidget;
    private Setting activitySetting;
    private Setting attentionSetting;
    private Setting serversSetting;
    private Setting routingSetting;
    private Setting playerDataSetting;
    private final Map<String, MountableButtonWidget> activityRows = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> attentionRows = new LinkedHashMap<>();
    private final Map<String, AttentionItem> attentionItems = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> serverRows = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> routingRows = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> playerDataRows = new LinkedHashMap<>();
    private final ServerIconManager iconManager = new ServerIconManager(Config.remotelyDir);
    private final Consumer<NetworkRuntimeSnapshot> runtimeChangeListener = this::queueRuntimeSnapshot;
    private NetworkRuntimeSnapshot runtimeSnapshot;
    private List<NetworkIncident> incidents = List.of();
    private List<NetworkLifecycleJob> lifecycleJobs = List.of();
    private List<NetworkJob> activeJobs = List.of();
    private Map<String, Integer> transferFailureHeat = Map.of();
    private String serverSearchQuery = "";
    private LifecycleButtonWidget networkPowerButton;
    private boolean applyingNetworkChange;
    private long refreshGeneration;
    private boolean sharedChat;
    private boolean sharedResources;
    private NetworkSharedDataPolicy.SelectionMode chatChannelMode;
    private String chatChannels;
    private long chatRetentionMillis;
    private NetworkSharedDataPolicy.SelectionMode resourceTypeMode;
    private String resourceTypes;
    private NetworkSharedDataPolicy.ConflictPolicy resourceConflictPolicy;
    private int maximumPayloadBytes;
    private boolean batchingLayout;
    private boolean runtimeChangeListenerRegistered;

    public NetworkOverviewScreen(Screen parent, RemotelyClient remotelyClient, String networkId) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.networkId = networkId;
    }

    public String getDesktopAppId() {
        return "network-overview";
    }

    public String getDesktopAppTitle() {
        return network == null ? "Network" : network.name();
    }

    public String getDesktopAppIconPath() {
        return "network.png";
    }

    @Override
    public void init() {
        super.init();
        NetworkManager manager = remotelyClient.getNetworkManager();
        if (manager == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            client.setScreen(parent);
            return;
        }
        registerRuntimeChangeListener(manager);
        applyingNetworkChange = true;
        activityRows.clear();
        attentionRows.clear();
        attentionItems.clear();
        serverRows.clear();
        routingRows.clear();
        playerDataRows.clear();
        serverSearchQuery = "";
        networkPowerButton = new LifecycleButtonWidget(this::toggleNetworkPower);
        header()
            .addLeft(networkPowerButton)
            .addRight("close.png", () -> client.setScreen(parent), RIGHT_HEADER_ACTIONS.getLast())
            .addRight("save.png", this::saveAll, RIGHT_HEADER_ACTIONS.getFirst())
            .build();
        int contentY = 60;
        int contentHeight = Math.max(80, height - contentY - 6);
        overviewContainer = tabContainer("network_overview", contentY, contentHeight);
        serversContainer = tabContainer("network_servers", contentY, contentHeight);
        sharingContainer = tabContainer("network_sharing", contentY, contentHeight);
        settingsContainer = tabContainer("network_settings", contentY, contentHeight);
        tabs().addTab(TAB_NAMES.get(0), overviewContainer);
        tabs().addTab(TAB_NAMES.get(1), serversContainer);
        tabs().addTab(TAB_NAMES.get(2), sharingContainer);
        tabs().addTab(TAB_NAMES.get(3), settingsContainer);
        tabsManager.builder().allowAdd(false).allowClose(false).allowRename(false).allowReorder(false).position(6, 36).size(width - 12, 18).onTabSelected(tab -> setActiveContainer(tab.getContainer())).build();
        tabs().setActiveTab(overviewContainer);
        setActiveContainer(overviewContainer);
        long generation = ++refreshGeneration;
        background(() -> CompletableFuture.completedFuture(loadState(manager))).whenComplete((state, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (generation != refreshGeneration) {
                return;
            }
            applyingNetworkChange = false;
            if (throwable != null) {
                new Notification("Network Unavailable", rootMessage(throwable), Notification.Type.ERROR);
                client.setScreen(parent);
                return;
            }
            applyInitialState(state);
            createDissolvePopup();
            buildContent();
            updateNetworkPowerButton();
        }));
    }

    private void saveAll() {
        if (applyingNetworkChange) {
            return;
        }
        sharingOptions.forEach(ConfigOption::apply);
        String name = networkNameInput.getText() == null ? "" : networkNameInput.getText().trim();
        if (name.isBlank()) {
            new Notification("Name Required", "Enter a network name before saving.", Notification.Type.ERROR);
            return;
        }
        Map<String, Boolean> features = new LinkedHashMap<>(network.features());
        features.put(NetworkDefinition.FEATURE_SHARED_CHAT, sharedChat);
        features.put(NetworkDefinition.FEATURE_SHARED_RESOURCES, sharedResources);
        NetworkSharedDataPolicy policy = new NetworkSharedDataPolicy(chatChannelMode, commaSet(chatChannels), chatRetentionMillis, resourceTypeMode, commaSet(resourceTypes), resourceConflictPolicy, maximumPayloadBytes);
        boolean nameChanged = !name.equals(network.name());
        boolean routingChanged = !routingGroups.equals(network.routingGroups());
        boolean sharingChanged = !syncRealms.equals(network.syncRealms()) || !features.equals(network.features()) || !policy.equals(network.sharedDataPolicy());
        if (!nameChanged && !routingChanged && !sharingChanged) {
            new Notification("Network Is Current", network.name(), Notification.Type.INFO);
            return;
        }
        applyingNetworkChange = true;
        NetworkManager manager = remotelyClient.getNetworkManager();
        List<Instance> targetInstances = List.copyOf(instances);
        List<RoutingGroup> targetRouting = List.copyOf(routingGroups);
        List<SyncRealm> targetRealms = List.copyOf(syncRealms);
        Notification notification = operationNotification("Saving Network", network.name());
        CompletableFuture<NetworkDefinition> save = background(() -> {
            NetworkDefinition current = manager.getNetwork(networkId).orElseThrow(() -> new IllegalStateException("Network no longer exists"));
            if (nameChanged) {
                current = manager.save(current.renamed(name));
                manager.reconcileInstanceBindings(targetInstances);
            }
            return CompletableFuture.completedFuture(current);
        });
        if (routingChanged) {
            save = save.thenCompose(current -> saveRouting(manager, current, targetRouting, targetInstances));
        }
        if (sharingChanged) {
            save = save.thenCompose(current -> saveSharing(manager, current, targetRealms, features, policy, targetInstances));
        }
        save.whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
            if (throwable != null) {
                notification.update().message("Save Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                refresh();
                return;
            }
            notification.update().message("Network Saved").description(updated.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            refresh();
        }));
    }

    private CompletableFuture<NetworkDefinition> saveRouting(NetworkManager manager, NetworkDefinition current, List<RoutingGroup> targetRouting, List<Instance> targetInstances) {
        return background(() -> manager.prepareRouting(current, targetRouting, targetInstances))
            .thenCompose(prepared -> background(() -> manager.runPreparedRouting(prepared, targetInstances, "Network Manager")))
            .thenApply(job -> savedNetwork(manager, job));
    }

    private CompletableFuture<NetworkDefinition> saveSharing(NetworkManager manager, NetworkDefinition current, List<SyncRealm> targetRealms, Map<String, Boolean> features, NetworkSharedDataPolicy policy, List<Instance> targetInstances) {
        return background(() -> manager.prepareSharedData(current, targetRealms, features, policy, targetInstances))
            .thenCompose(prepared -> background(() -> manager.runPreparedRealms(prepared, targetInstances, "Network Sharing")))
            .thenApply(job -> savedNetwork(manager, job));
    }

    private NetworkDefinition savedNetwork(NetworkManager manager, NetworkJob job) {
        if (job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
            throw new IllegalStateException(job == null ? "The network change did not finish" : job.message());
        }
        return manager.getNetwork(networkId).orElseThrow(() -> new IllegalStateException("Network no longer exists"));
    }

    private <T> CompletableFuture<T> background(Supplier<CompletableFuture<T>> operation) {
        return CompletableFuture.supplyAsync(operation, Executors.STREAMS).thenCompose(future -> future);
    }

    private RefreshState loadState(NetworkManager manager) {
        NetworkDefinition updated = manager.getNetwork(networkId).orElseThrow(() -> new IllegalStateException("Network no longer exists"));
        List<Instance> updatedInstances = List.copyOf(Rebase.get().getInstanceManager().getAllInstances());
        NetworkDiscoveryResult updatedDiscovery = manager.discover(updated, updatedInstances, List.of());
        return new RefreshState(updated, updatedInstances, updatedDiscovery, manager.getRuntimeSnapshot(networkId), manager.getIncidents(networkId), manager.getLifecycleJobManager().getJobs(networkId), manager.getJobManager().getJobs(networkId), manager.getTransferFailureHeat(networkId));
    }

    private void applyInitialState(RefreshState state) {
        network = state.network();
        instances = state.instances();
        instancesById.clear();
        instances.forEach(instance -> instancesById.put(instance.getInstanceId(), instance));
        discovery = state.discovery();
        runtimeSnapshot = latestRuntimeSnapshot(runtimeSnapshot, state.runtime());
        incidents = state.incidents();
        lifecycleJobs = state.lifecycleJobs();
        activeJobs = state.jobs();
        transferFailureHeat = state.transferFailureHeat();
        loadSharingDraft();
        routingGroups = List.copyOf(network.routingGroups());
        syncRealms = List.copyOf(network.syncRealms());
    }

    private Container tabContainer(String id, int y, int height) {
        return createContainer(id, 6, y, width - 12, height).columns(1).padding(4).verticalSpacing(2).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(true);
    }

    private void buildContent() {
        batchingLayout = true;
        try {
            populateOverviewTab(overviewContainer);
            populateServersTab(serversContainer);
            populateSharingTab(sharingContainer);
            populateSettingsTab(settingsContainer);
        } finally {
            batchingLayout = false;
        }
        overviewContainer.updateWidgetPositions();
        serversContainer.updateWidgetPositions();
        sharingContainer.updateWidgetPositions();
        settingsContainer.updateWidgetPositions();
    }

    private void requestLayout(Container container) {
        if (!batchingLayout && container != null) {
            container.updateWidgetPositions();
        }
    }

    private void populateOverviewTab(Container container) {
        int topologyWidth = Math.max(220, container.getEffectiveWidth() - 8);
        topologyWidget = new NetworkTopologyWidget(0, 0, topologyWidth, NetworkTopologyWidget.preferredHeight(topologyWidth, network.members().size()), network, discovery.observations(), () -> runtimeSnapshot, null, () -> transferFailureHeat, this::displayName, this::memberState, this::memberIcon, this::toggleMemberPower, this::openMember);
        container.addWidget(topologyWidget);

        populateCurrentJobs(container);
        populateAttention(container);
        requestLayout(container);
    }

    private void populateAttention(Container container) {
        attentionSetting = new Setting.Builder("Needs Attention").build();
        container.addWidget(attentionSetting);
        syncAttentionRows();
    }

    private AttentionItem attentionItem(String id, String rawMessage, String detail, boolean blocking, NetworkMember member) {
        String message = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.ROOT);
        String title;
        String description;
        if (message.contains("port")) {
            title = "Server Port";
            description = "The saved connection port does not match the server's current port.";
        } else if (message.contains("firewall") || message.contains("security") || message.contains("protection")) {
            title = "Server Protection";
            description = "Remotely has not confirmed that this backend rejects unsafe direct connections.";
        } else if (message.contains("runtime") || message.contains("reconnect") || message.contains("presence")) {
            title = "Server Connection";
            description = "This server has not finished connecting to the shared network.";
        } else {
            title = "Network Setup";
            description = "A saved network setting cannot currently be applied as configured.";
        }
        String label = member == null ? title : displayName(member) + " • " + title;
        return new AttentionItem(id, label, description, detail == null || detail.isBlank() ? rawMessage : detail, blocking, member);
    }

    private void populateServersTab(Container container) {
        populateServers(container);
        populateRouting(container);
    }

    private void populateSharingTab(Container container) {
        sharingOptions.clear();
        Setting.Builder chat = new Setting.Builder("Shared Chat");
        ConfigOption<Boolean> chatEnabled = ConfigOption.<Boolean>builder("Share Chat").description("Share network chat between servers.").bind(() -> sharedChat, value -> sharedChat = value).defaultValue(false).resettable(false).build();
        ConfigOption<NetworkSharedDataPolicy.SelectionMode> channelMode = ConfigOption.<NetworkSharedDataPolicy.SelectionMode>builder("Which Channels").description("All shares every channel. Only Listed shares the channels in Channel Names. All Except Listed shares every channel except those in Channel Names.").options(List.of(NetworkSharedDataPolicy.SelectionMode.values())).display(this::selectionModeLabel).bind(() -> chatChannelMode, value -> chatChannelMode = value).defaultValue(NetworkSharedDataPolicy.SelectionMode.ALL).resettable(false).build();
        ConfigOption<String> channelList = ConfigOption.<String>builder("Channel Names").description("Enter channel names separated by commas. These names are used by Which Channels.").bind(() -> chatChannels, value -> chatChannels = value).defaultValue("").resettable(false).dependsOn(() -> channelMode.get() != NetworkSharedDataPolicy.SelectionMode.ALL).build();
        ConfigOption<Long> retention = ConfigOption.<Long>builder("Offline Delivery").description("1 Minute covers brief reconnects. 2 Minutes covers restarts. 15 Minutes covers maintenance. 1 Hour covers longer outages. 1 Day keeps messages until tomorrow.").options(List.of(60_000L, 120_000L, 900_000L, 3_600_000L, 86_400_000L)).display(this::durationLabel).bind(() -> chatRetentionMillis, value -> chatRetentionMillis = value).defaultValue(120_000L).resettable(false).build();
        addSharingOption(chat, chatEnabled);
        addSharingOption(chat, channelMode);
        addSharingOption(chat, channelList);
        addSharingOption(chat, retention);
        container.addWidget(chat.build());

        Setting.Builder resources = new Setting.Builder("Shared Resources");
        ConfigOption<Boolean> resourcesEnabled = ConfigOption.<Boolean>builder("Share Custom Content").description("Keep ReSync content the same everywhere.").bind(() -> sharedResources, value -> sharedResources = value).defaultValue(false).resettable(false).build();
        ConfigOption<NetworkSharedDataPolicy.SelectionMode> typeMode = ConfigOption.<NetworkSharedDataPolicy.SelectionMode>builder("Which Content").description("All shares everything. Only Listed shares Content Names. All Except Listed skips Content Names. Folder Organization always matches the network.").options(List.of(NetworkSharedDataPolicy.SelectionMode.values())).display(this::selectionModeLabel).bind(() -> resourceTypeMode, value -> resourceTypeMode = value).defaultValue(NetworkSharedDataPolicy.SelectionMode.ALL).resettable(false).build();
        ConfigOption<String> typeList = ConfigOption.<String>builder("Content Names").description("Enter content type names separated by commas. These names are used by Which Content.").bind(() -> resourceTypes, value -> resourceTypes = value).defaultValue("").resettable(false).dependsOn(() -> typeMode.get() != NetworkSharedDataPolicy.SelectionMode.ALL).build();
        ConfigOption<NetworkSharedDataPolicy.ConflictPolicy> conflicts = ConfigOption.<NetworkSharedDataPolicy.ConflictPolicy>builder("When Both Changed").description("Network Wins keeps the last accepted shared copy. Local Wins keeps the copy on the server that changed it.").options(List.of(NetworkSharedDataPolicy.ConflictPolicy.values())).display(this::conflictPolicyLabel).bind(() -> resourceConflictPolicy, value -> resourceConflictPolicy = value).defaultValue(NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS).resettable(false).build();
        ConfigOption<Integer> maximumSize = ConfigOption.<Integer>builder("Largest Shared Item").description("64 KB suits small data. 256 KB is balanced. 512 KB supports larger content. 1 MB is the maximum.").options(List.of(65_536, 262_144, 524_288, 1_048_576)).display(this::sizeLabel).bind(() -> maximumPayloadBytes, value -> maximumPayloadBytes = value).defaultValue(NetworkSharedDataPolicy.DEFAULT_MAXIMUM_PAYLOAD_BYTES).resettable(false).build();
        addSharingOption(resources, resourcesEnabled);
        addSharingOption(resources, typeMode);
        addSharingOption(resources, typeList);
        addSharingOption(resources, conflicts);
        addSharingOption(resources, maximumSize);
        container.addWidget(resources.build());

        Setting.Builder playerData = new Setting.Builder("Player Data");
        playerData.addRow("add", "", true, 30, actionRow("Add Player Group", "Share selected player data between a group of servers.", "add.png", () -> openPlayerGroup(null)));
        AnimatedButton playerDataEmpty = inactive("Player Data Stays On Each Server", ThemeManager.getDefaultAccent());
        playerDataEmpty.setVisible(syncRealms.isEmpty());
        playerData.addRow("empty", "", true, 22, playerDataEmpty);
        for (SyncRealm realm : syncRealms) {
            MountableButtonWidget row = playerDataRow(realm);
            playerDataRows.put(realm.id(), row);
            playerData.addRow("realm:" + realm.id(), "", true, 30, row);
        }
        playerDataSetting = playerData.build();
        playerDataSetting.setRowVisibility("empty", syncRealms.isEmpty());
        container.addWidget(playerDataSetting);
        requestLayout(container);
    }

    private void populateSettingsTab(Container container) {
        Setting.Builder identity = new Setting.Builder("Network Name");
        networkNameInput = new TextInputWidget.Builder().text(network.name()).placeholder("Network Name").maxLength(64).build();
        identity.addRow("name", "Name", true, 22, networkNameInput);
        container.addWidget(identity.build());

        Setting.Builder maintenance = new Setting.Builder("Maintenance");
        maintenance.addRow("reapply", "", true, 30, actionRow("Reapply Network Settings", "Restore the saved proxy, server, and ReSync settings when files were changed outside Remotely.", "reload.png", this::reconcile));
        maintenance.addRow("entry", "", true, 30, actionRow("Test Player Entry", "Verify that the proxy can send players to the configured servers.", "checkmark.png", this::runPreflight));
        maintenance.addRow("key", "", true, 30, actionRow("Replace Connection Key", "Create a new private key shared by the proxy and managed servers. Use this if the current key may have been exposed.", "shades.png", this::prepareSecretRotation));
        container.addWidget(maintenance.build());

        if (network.runtime().enabled()) {
            Setting.Builder runtime = new Setting.Builder("Network Commands");
            TextInputWidget command = new TextInputWidget.Builder().placeholder("Run A Proxy Command").maxLength(2048).build();
            TextInputWidget broadcast = new TextInputWidget.Builder().placeholder("Message Every Player").maxLength(8192).build();
            runtime.addRow("command", "Proxy", true, 22, command, inlineAction("Run", "terminal.png", () -> executeProxyCommand(command)));
            runtime.addRow("broadcast", "Players", true, 22, broadcast, inlineAction("Send", "chat.png", () -> broadcastMessage(broadcast)));
            container.addWidget(runtime.build());
        }

        Setting.Builder safety = new Setting.Builder("Network Removal");
        safety.addRow("dissolve", "", true, 30, actionRow("Dissolve Network", "Restore every managed server to independent operation and remove this network without deleting server files or worlds.", "delete.png", ThemeManager.getAccent("danger"), this::openDissolvePopup));
        container.addWidget(safety.build());
        requestLayout(container);
    }

    private void loadSharingDraft() {
        sharedChat = network.featureEnabled(NetworkDefinition.FEATURE_SHARED_CHAT);
        sharedResources = network.featureEnabled(NetworkDefinition.FEATURE_SHARED_RESOURCES);
        NetworkSharedDataPolicy policy = network.sharedDataPolicy();
        chatChannelMode = policy.chatChannelMode();
        chatChannels = String.join(", ", policy.chatChannels());
        chatRetentionMillis = policy.chatRetentionMillis();
        resourceTypeMode = policy.resourceTypeMode();
        resourceTypes = String.join(", ", policy.resourceTypes());
        resourceConflictPolicy = policy.resourceConflictPolicy();
        maximumPayloadBytes = policy.maximumPayloadBytes();
    }

    private void addSharingOption(Setting.Builder setting, ConfigOption<?> option) {
        sharingOptions.add(option);
        setting.addOption(option);
    }

    private void openPlayerGroup(SyncRealm existing) {
        String[] name = {existing == null ? "Shared Players" : existing.name()};
        String[] servers = {existing == null ? "" : displayNames(existing.nodeIds())};
        String[] pluginData = {existing == null ? "" : String.join(", ", existing.persistentDataNamespaces())};
        String[] snapshots = {Integer.toString(existing == null ? 20 : existing.retainedSnapshots())};
        String[] days = {Integer.toString(existing == null ? 30 : existing.retentionDays())};
        Set<SyncDataFamily> selected = existing == null ? new LinkedHashSet<>(Set.of(SyncDataFamily.PRESENCE)) : new LinkedHashSet<>(existing.dataFamilies());
        SyncLocationPolicy[] location = {existing == null ? SyncLocationPolicy.NEVER : existing.locationPolicy()};
        PopupWidget[] popup = new PopupWidget[1];
        Runnable save = () -> {
            try {
                SyncRealm updated = buildPlayerGroup(existing, name[0], servers[0], selected, location[0], pluginData[0], snapshots[0], days[0]);
                List<SyncRealm> draft = new ArrayList<>(syncRealms);
                if (existing == null) {
                    draft.add(updated);
                } else {
                    draft.set(draft.indexOf(existing), updated);
                }
                syncRealms = List.copyOf(draft);
                popup[0].hide();
                syncPlayerDataRows();
            } catch (RuntimeException exception) {
                new Notification("Player Group Invalid", rootMessage(exception), Notification.Type.ERROR);
            }
        };
        PopupWidget.Builder builder = new PopupWidget.Builder(existing == null ? "Add Player Group" : "Edit " + existing.name()).size(330, 470).setResizable(true).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        if (existing != null) {
            builder.addTitleAction("Delete", () -> {
                syncRealms = syncRealms.stream().filter(realm -> !realm.equals(existing)).toList();
                popup[0].hide();
                syncPlayerDataRows();
            }, "Delete Player Group", PopupWidget.TitleActionRole.DESTRUCTIVE);
        }
        builder.addTitleAction(existing == null ? "Add" : "Update", save, existing == null ? "Add Player Group" : "Update Player Group", PopupWidget.TitleActionRole.PRIMARY);
        builder.addTextField("Name", "A clear name for the servers and player information that belong together.", name[0], value -> name[0] = value);
        builder.addTextField("Servers", "Enter the server names that should share this player information, separated with commas. A group normally needs at least two servers.", servers[0], value -> servers[0] = value);
        for (SyncDataFamily family : SyncDataFamily.values()) {
            builder.addToggle(playerDataLabel(family), playerDataDescription(family), selected.contains(family), value -> {
                if (value) {
                    selected.add(family);
                } else {
                    selected.remove(family);
                }
            });
        }
        builder.addScrollSelector("Return Location", "Keep Location Local keeps the current position. Return On Same Server restores it only on the same server. Use Group Return Point sends the player to the group's safe point. Follow Compatible World restores a matching world when available.", Arrays.stream(SyncLocationPolicy.values()).map(this::locationLabel).toList(), location[0].ordinal(), index -> location[0] = SyncLocationPolicy.values()[index]);
        builder.addTextField("Plugin Data", "Optional plugin namespaces whose saved player data should move with the player. Separate multiple namespaces with commas and leave this empty when no plugin data should be shared.", pluginData[0], value -> pluginData[0] = value);
        builder.addTextField("Recovery Copies", "How many recent player copies should remain available for recovery. Use a positive whole number.", snapshots[0], value -> snapshots[0] = value);
        builder.addTextField("Recovery Days", "How many days recovery copies should be kept before they expire. Use a positive whole number.", days[0], value -> days[0] = value);
        popup[0] = showPopup(builder.build());
    }

    private SyncRealm buildPlayerGroup(SyncRealm existing, String rawName, String rawServers, Set<SyncDataFamily> families, SyncLocationPolicy location, String rawPluginData, String rawSnapshots, String rawDays) {
        String id = existing == null ? nextPlayerGroupId() : existing.id();
        String name = rawName == null || rawName.isBlank() ? "Shared Players" : rawName.trim();
        Set<String> nodes = commaValues(rawServers).stream().map(value -> findMember(value).orElseThrow(() -> new IllegalArgumentException("Unknown Server " + value)).nodeId()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Choose At Least Two Servers");
        }
        if (families.isEmpty()) {
            throw new IllegalArgumentException("Choose At Least One Kind Of Player Information");
        }
        int retainedSnapshots = Integer.parseInt(rawSnapshots.trim());
        int retentionDays = Integer.parseInt(rawDays.trim());
        Set<String> namespaces = commaValues(rawPluginData).stream().collect(Collectors.toCollection(LinkedHashSet::new));
        return new SyncRealm(id, name, nodes, families, location, namespaces, retainedSnapshots, retentionDays);
    }

    private String nextPlayerGroupId() {
        int index = 1;
        while (syncRealms.stream().map(SyncRealm::id).toList().contains("players-" + index)) {
            index++;
        }
        return "players-" + index;
    }

    private String playerDataLabel(SyncDataFamily family) {
        return switch (family) {
            case PRESENCE -> "Online Presence";
            case INVENTORY -> "Inventory";
            case ENDER_CHEST -> "Ender Chest";
            case EXPERIENCE -> "Experience";
            case VITALS -> "Health And Hunger";
            case EFFECTS -> "Potion Effects";
            case PLAYER_STATE -> "Player State";
            case ADVANCEMENTS -> "Advancements";
            case RECIPES -> "Discovered Recipes";
            case STATISTICS -> "Statistics";
            case LOCATION -> "Location";
            case PERSISTENT_DATA -> "Plugin Data";
        };
    }

    private String playerDataDescription(SyncDataFamily family) {
        return switch (family) {
            case PRESENCE -> "Let every server know where the player is connected so transfers, messages, and shared features can find them.";
            case INVENTORY -> "Move the player's inventory, armor, off-hand item, and selected hotbar slot between these servers.";
            case ENDER_CHEST -> "Keep the same Ender Chest contents on every server in this player group.";
            case EXPERIENCE -> "Keep experience points and levels consistent when the player changes servers.";
            case VITALS -> "Share health, hunger, saturation, exhaustion, air, fire time, and related survival values.";
            case EFFECTS -> "Carry active potion and status effects to the next server.";
            case PLAYER_STATE -> "Share general player state that does not belong to inventory, experience, location, or plugin data.";
            case ADVANCEMENTS -> "Keep advancement progress and completed criteria consistent across these servers.";
            case RECIPES -> "Keep the same discovered crafting recipes when the player changes servers.";
            case STATISTICS -> "Share the player's Minecraft statistics across these servers.";
            case LOCATION -> "Remember a compatible location. Return Location controls where the player appears after changing servers.";
            case PERSISTENT_DATA -> "Share the plugin namespaces entered in Plugin Data.";
        };
    }

    private String locationLabel(SyncLocationPolicy policy) {
        return switch (policy) {
            case NEVER -> "Keep Location Local";
            case SAME_SERVER_ONLY -> "Return On Same Server";
            case REALM_RETURN_POINT -> "Use Group Return Point";
            case EXACT_COMPATIBLE_WORLD -> "Follow Compatible World";
        };
    }

    private Set<String> commaSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String displayNames(Iterable<String> nodeIds) {
        List<String> names = new ArrayList<>();
        nodeIds.forEach(nodeId -> names.add(member(nodeId).map(this::displayName).orElse("Missing Server")));
        return String.join(", ", names);
    }

    private Optional<NetworkMember> member(String nodeId) {
        return network.members().stream().filter(member -> member.nodeId().equals(nodeId)).findFirst();
    }

    private Optional<NetworkMember> findMember(String name) {
        String normalized = name == null ? "" : name.trim();
        return network.members().stream().filter(member -> !member.isProxy()).filter(member -> displayName(member).equalsIgnoreCase(normalized) || member.routeName().equalsIgnoreCase(normalized)).findFirst();
    }

    private List<String> commaValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private PopupWidget showPopup(PopupWidget popup) {
        popup.setX((width - popup.getWidth()) / 2);
        popup.setY((height - popup.getHeight()) / 2);
        addDrawableChild(popup);
        popup.show();
        return popup;
    }

    private String selectionModeLabel(NetworkSharedDataPolicy.SelectionMode mode) {
        return switch (mode) {
            case ALL -> "All";
            case ALLOW_LIST -> "Only Listed";
            case DENY_LIST -> "All Except Listed";
        };
    }

    private String conflictPolicyLabel(NetworkSharedDataPolicy.ConflictPolicy policy) {
        return policy == NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS ? "Network Wins" : "Local Wins";
    }

    private String durationLabel(long millis) {
        if (millis % 86_400_000 == 0) {
            long days = millis / 86_400_000;
            return days + (days == 1 ? " Day" : " Days");
        }
        if (millis % 3_600_000 == 0) {
            long hours = millis / 3_600_000;
            return hours + (hours == 1 ? " Hour" : " Hours");
        }
        long minutes = Math.max(1, millis / 60_000);
        return minutes + (minutes == 1 ? " Minute" : " Minutes");
    }

    private String sizeLabel(int bytes) {
        return bytes % 1_048_576 == 0 ? bytes / 1_048_576 + " MB" : bytes / 1_024 + " KB";
    }

    private IconButton inlineAction(String label, String icon, Runnable action) {
        return inlineAction(label, icon, ThemeManager.getDefaultAccent(), action);
    }

    private IconButton inlineAction(String label, String icon, Accent accent, Runnable action) {
        return new IconButton.Builder().label(label).imagePath(icon).accentType(accent).onClick(action).build();
    }

    private MountableButtonWidget actionRow(String label, String description, String icon, Runnable action) {
        return actionRow(label, description, icon, ThemeManager.getDefaultAccent(), action);
    }

    private MountableButtonWidget actionRow(String label, String description, String icon, Accent accent, Runnable action) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(label).description(description).iconPath(icon).onClick(action).build();
        row.setAccent(accent);
        row.setSize(220, 30);
        return row;
    }

    private AnimatedButton inactive(String label, Accent accent) {
        return new AnimatedButton.Builder().label(label).accentType(accent).active(false).build();
    }

    private MountableButtonWidget compactRow(String label, String hint, Accent accent) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(label).build();
        row.setHint(hint);
        row.setAccent(accent);
        row.setSize(220, 20);
        return row;
    }

    private MountableButtonWidget memberRow(Container container, NetworkMember member, NetworkRuntimeSnapshot runtime) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(displayName(member)).icon(memberIcon(member)).build();
        String nodeId = member.nodeId();
        row.setOnClick(() -> currentMember(nodeId).ifPresent(this::openMember));
        row.addMountedWidget(rowAction("terminal.png", "Open Terminal", () -> currentMember(nodeId).ifPresent(this::openMember)));
        if (!member.isProxy() && member.isManaged() && member.resyncEnabled()) {
            row.addMountedWidget(rowAction("checkmark.png", "Accept Players", () -> currentMember(nodeId).ifPresent(current -> setRuntimeMode(current, NetworkNodeStatus.ONLINE))));
            row.addMountedWidget(rowAction("close.png", "Drain Server", () -> currentMember(nodeId).ifPresent(current -> setRuntimeMode(current, NetworkNodeStatus.DRAINING))));
            row.addMountedWidget(rowAction("hide.png", "Maintenance Mode", () -> currentMember(nodeId).ifPresent(current -> setRuntimeMode(current, NetworkNodeStatus.MAINTENANCE))));
        }
        if (!member.isProxy()) {
            row.addMountedWidget(rowAction("unmerge.png", "Detach", () -> currentMember(nodeId).ifPresent(current -> confirmDetach(current, instancesById.get(current.instanceId()))), ThemeManager.getAccent("danger")));
        }
        updateMemberRow(container, row, member, runtime);
        loadMemberIcon(member, row);
        return row;
    }

    private void updateMemberRow(Container container, MountableButtonWidget row, NetworkMember member, NetworkRuntimeSnapshot runtime) {
        Instance instance = instancesById.get(member.instanceId());
        NetworkNodePresence presence = runtime == null ? null : runtime.node(member.nodeId()).orElse(null);
        boolean connected = runtime != null && runtime.connected();
        String status;
        String capacity = "";
        if (member.isProxy()) {
            status = connected ? "Online" : runtime == null ? "Unavailable" : titleCase(runtime.state().name());
        } else if (presence == null) {
            status = member.resyncEnabled() ? "Unavailable" : instance == null && member.isManaged() ? "Missing" : "Configured";
        } else {
            status = titleCase(presence.status().name());
            capacity = " • " + presence.players() + (presence.capacity() > 0 ? "/" + presence.capacity() : "") + " Players";
        }
        String management = member.isProxy() ? "Proxy" : member.isManaged() ? "Server" : "External Server";
        row.setName(displayName(member));
        row.setIcon(memberIcon(member));
        row.setDescription(management + capacity);
        row.setHiddenText(status);
        styleRow(container, row, memberAccent(member, presence, instance), 30);
    }

    private Optional<NetworkMember> currentMember(String nodeId) {
        return network.members().stream().filter(member -> member.nodeId().equals(nodeId)).findFirst();
    }

    private String displayName(NetworkMember member) {
        if (member == null) {
            return "Unavailable";
        }
        Instance instance = instancesById.get(member.instanceId());
        if (instance != null && instance.getName() != null && !instance.getName().isBlank()) {
            return instance.getName();
        }
        String route = member.routeName().replace('-', ' ').trim();
        return route.isBlank() ? member.isProxy() ? "Proxy" : "External Server" : titleCase(route);
    }

    private InstanceState memberState(NetworkMember member) {
        if (member == null || !member.isManaged()) {
            return null;
        }
        Instance instance = instancesById.get(member.instanceId());
        return instance == null ? null : instance.getState();
    }

    private InstanceState networkState() {
        List<InstanceState> states = network.members().stream().filter(NetworkMember::isManaged).map(this::memberState).filter(state -> state != null).toList();
        if (states.stream().anyMatch(state -> state == InstanceState.STARTING)) {
            return InstanceState.STARTING;
        }
        if (states.stream().anyMatch(state -> state == InstanceState.STOPPING)) {
            return InstanceState.STOPPING;
        }
        if (states.stream().anyMatch(state -> state == InstanceState.RUNNING || state == InstanceState.SAVED || state == InstanceState.SAVING)) {
            return InstanceState.RUNNING;
        }
        if (states.stream().anyMatch(state -> state == InstanceState.CRASHED)) {
            return InstanceState.CRASHED;
        }
        return InstanceState.STOPPED;
    }

    private void updateNetworkPowerButton() {
        if (networkPowerButton == null || network == null) {
            return;
        }
        networkPowerButton.update(networkState());
        header().requestLayoutUpdate();
    }

    private void toggleNetworkPower() {
        if (network == null) {
            return;
        }
        lifecycle(LifecycleButtonWidget.canStart(networkState()) ? NetworkLifecycleOperation.START : NetworkLifecycleOperation.STOP);
    }

    private void toggleMemberPower(NetworkMember member) {
        if (applyingNetworkChange || member == null || !member.isManaged()) {
            return;
        }
        Instance instance = instancesById.get(member.instanceId());
        if (instance == null) {
            new Notification("Server Unavailable", displayName(member), Notification.Type.ERROR);
            return;
        }
        NetworkLifecycleOperation operation = LifecycleButtonWidget.canStart(instance.getState()) ? NetworkLifecycleOperation.START : NetworkLifecycleOperation.STOP;
        applyingNetworkChange = true;
        instance.setState(operation == NetworkLifecycleOperation.START ? InstanceState.STARTING : InstanceState.STOPPING);
        Notification notification = operationNotification(operation == NetworkLifecycleOperation.START ? "Starting Server" : "Stopping Server", displayName(member));
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.runMemberLifecycle(network, member, instances, operation, "Network Overview")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
            if (throwable != null || job == null || job.status() != NetworkLifecycleStatus.SUCCEEDED) {
                notification.update().message("Server Action Failed").description(throwable == null ? job == null ? "The server action did not finish" : job.message() : rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            } else {
                notification.update().message(operation == NetworkLifecycleOperation.START ? "Server Started" : "Server Stopped").description(displayName(member)).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            }
            refresh();
        }));
    }

    private Accent memberAccent(NetworkMember member, NetworkNodePresence presence, Instance instance) {
        if (member.isManaged() && instance == null) {
            return ThemeManager.getAccent("danger");
        }
        if (presence != null && (presence.status() == NetworkNodeStatus.DRAINING || presence.status() == NetworkNodeStatus.MAINTENANCE)) {
            return ThemeManager.getDefaultAccent();
        }
        if (presence != null && presence.status() == NetworkNodeStatus.REVOKED) {
            return ThemeManager.getAccent("danger");
        }
        return ThemeManager.getDefaultAccent();
    }

    private Identifier memberIcon(NetworkMember member) {
        Instance instance = instancesById.get(member.instanceId());
        Identifier icon = instance == null ? null : iconManager.getQuickIconId(instance);
        return icon != null ? icon : Identifier.icon(member.isProxy() ? "network.png" : "server.png");
    }

    private void loadMemberIcon(NetworkMember member, MountableButtonWidget row) {
        Instance instance = instancesById.get(member.instanceId());
        if (instance == null) {
            return;
        }
        iconManager.loadIconIdAsync(instance, icon -> {
            row.setIcon(icon);
            if (topologyWidget != null) {
                topologyWidget.setMemberIcon(member.nodeId(), icon);
            }
        });
        iconManager.loadRemoteIconAsync(instance, () -> iconManager.loadIconIdAsync(instance, icon -> {
            row.setIcon(icon);
            if (topologyWidget != null) {
                topologyWidget.setMemberIcon(member.nodeId(), icon);
            }
        }));
    }

    private String serverSearchText(NetworkMember member) {
        return (displayName(member) + " " + member.routeName() + " " + member.address()).toLowerCase(Locale.ROOT);
    }

    private void syncServerRows() {
        if (serversSetting == null) {
            return;
        }
        Set<String> desired = network.members().stream().map(NetworkMember::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        for (String nodeId : new ArrayList<>(serverRows.keySet())) {
            if (desired.contains(nodeId)) {
                continue;
            }
            serverRows.remove(nodeId);
            serversSetting.removeRow("server:" + nodeId);
        }
        for (NetworkMember member : network.members()) {
            MountableButtonWidget row = serverRows.get(member.nodeId());
            if (row == null) {
                row = memberRow(serversContainer, member, runtimeSnapshot);
                serverRows.put(member.nodeId(), row);
                serversSetting.addRow("server:" + member.nodeId(), "", List.of(row), 30, true, false);
            } else {
                updateMemberRow(serversContainer, row, member, runtimeSnapshot);
            }
            serversSetting.setRowVisibility("server:" + member.nodeId(), serverSearchQuery.isBlank() || serverSearchText(member).contains(serverSearchQuery));
        }
        requestLayout(serversContainer);
    }

    private MountableButtonWidget routingRow(RoutingGroup group) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(group.name()).build();
        row.setOnClick(() -> openRoutingGroup(routingGroups.stream().filter(candidate -> candidate.id().equals(group.id())).findFirst().orElse(null)));
        row.addMountedWidget(rowAction("edit.png", "Edit Join Rule", () -> openRoutingGroup(routingGroups.stream().filter(candidate -> candidate.id().equals(group.id())).findFirst().orElse(null))));
        updateRoutingRow(row, group);
        return row;
    }

    private void updateRoutingRow(MountableButtonWidget row, RoutingGroup group) {
        String servers = displayNames(group.nodeIds());
        row.setName(group.name());
        row.setDescription(servers.isBlank() ? "No Servers Selected" : servers);
        row.setHiddenText(routingStrategyLabel(group.strategy()));
        row.setAccent(ThemeManager.getDefaultAccent());
        row.setSize(Math.max(220, serversContainer.getEffectiveWidth() - 10), 30);
    }

    private void syncRoutingRows() {
        if (routingSetting == null) {
            return;
        }
        Set<String> desired = routingGroups.stream().map(RoutingGroup::id).collect(Collectors.toCollection(LinkedHashSet::new));
        for (String id : new ArrayList<>(routingRows.keySet())) {
            if (desired.contains(id)) {
                continue;
            }
            routingRows.remove(id);
            routingSetting.removeRow("rule:" + id);
        }
        for (RoutingGroup group : routingGroups) {
            MountableButtonWidget row = routingRows.get(group.id());
            if (row == null) {
                row = routingRow(group);
                routingRows.put(group.id(), row);
                routingSetting.addRow("rule:" + group.id(), "", List.of(row), 30, true, false);
            } else {
                updateRoutingRow(row, group);
            }
        }
        routingSetting.setRowVisibility("empty", routingGroups.isEmpty());
        requestLayout(serversContainer);
    }

    private MountableButtonWidget playerDataRow(SyncRealm realm) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(realm.name()).build();
        row.setOnClick(() -> openPlayerGroup(syncRealms.stream().filter(candidate -> candidate.id().equals(realm.id())).findFirst().orElse(null)));
        row.addMountedWidget(rowAction("edit.png", "Edit Player Data", () -> openPlayerGroup(syncRealms.stream().filter(candidate -> candidate.id().equals(realm.id())).findFirst().orElse(null))));
        updatePlayerDataRow(row, realm);
        return row;
    }

    private void updatePlayerDataRow(MountableButtonWidget row, SyncRealm realm) {
        String families = realm.dataFamilies().stream().map(value -> titleCase(value.name())).collect(Collectors.joining(", "));
        row.setName(realm.name());
        row.setDescription(displayNames(realm.nodeIds()));
        row.setHiddenText(families);
        row.setAccent(ThemeManager.getDefaultAccent());
        row.setSize(Math.max(220, sharingContainer.getEffectiveWidth() - 10), 30);
    }

    private void syncPlayerDataRows() {
        if (playerDataSetting == null) {
            return;
        }
        Set<String> desired = syncRealms.stream().map(SyncRealm::id).collect(Collectors.toCollection(LinkedHashSet::new));
        for (String id : new ArrayList<>(playerDataRows.keySet())) {
            if (desired.contains(id)) {
                continue;
            }
            playerDataRows.remove(id);
            playerDataSetting.removeRow("realm:" + id);
        }
        for (SyncRealm realm : syncRealms) {
            MountableButtonWidget row = playerDataRows.get(realm.id());
            if (row == null) {
                row = playerDataRow(realm);
                playerDataRows.put(realm.id(), row);
                playerDataSetting.addRow("realm:" + realm.id(), "", List.of(row), 30, true, false);
            } else {
                updatePlayerDataRow(row, realm);
            }
        }
        playerDataSetting.setRowVisibility("empty", syncRealms.isEmpty());
        requestLayout(sharingContainer);
    }

    private void syncActivityRows() {
        if (activitySetting == null) {
            return;
        }
        List<NetworkLifecycleJob> visibleLifecycle = lifecycleJobs.stream().filter(job -> job.status() == NetworkLifecycleStatus.RUNNING || job.status() == NetworkLifecycleStatus.INTERRUPTED).limit(1).toList();
        List<NetworkJob> visibleJobs = activeJobs.stream().filter(job -> job.status() == NetworkJobStatus.RUNNING || job.status() == NetworkJobStatus.INTERRUPTED || job.status() == NetworkJobStatus.ROLLING_BACK).limit(1).toList();
        Set<String> desired = new LinkedHashSet<>();
        visibleLifecycle.forEach(job -> desired.add("lifecycle:" + job.jobId()));
        visibleJobs.forEach(job -> desired.add("job:" + job.jobId()));
        for (String id : new ArrayList<>(activityRows.keySet())) {
            if (desired.contains(id)) {
                continue;
            }
            activityRows.remove(id);
            activitySetting.removeRow(id);
        }
        for (NetworkLifecycleJob job : visibleLifecycle) {
            String id = "lifecycle:" + job.jobId();
            MountableButtonWidget row = activityRows.computeIfAbsent(id, ignored -> {
                MountableButtonWidget created = new MountableButtonWidget.Builder("").build();
                activitySetting.addRow(id, "", List.of(created), 28, true, false);
                return created;
            });
            long completed = job.steps().stream().filter(NetworkLifecycleStep::complete).count();
            row.setName(titleCase(job.operation().name()));
            row.setDescription(completed + " Of " + job.steps().size() + " Servers Complete");
            row.setHiddenText(lifecycleStatus(job.status()));
            row.setAccent(ThemeManager.getDefaultAccent());
            row.setOnClick(job.canResume() ? () -> resumeLifecycle(job) : () -> {});
            row.clearMountedWidgets();
            row.addMountedWidget(job.canResume() ? rowAction("reload.png", "Continue", () -> resumeLifecycle(job)) : rowAction("info.png", "In Progress", () -> {}));
        }
        for (NetworkJob job : visibleJobs) {
            String id = "job:" + job.jobId();
            MountableButtonWidget row = activityRows.computeIfAbsent(id, ignored -> {
                MountableButtonWidget created = new MountableButtonWidget.Builder("").build();
                activitySetting.addRow(id, "", List.of(created), 28, true, false);
                return created;
            });
            row.setName(friendlyJobName(job.type()));
            row.setDescription("Remotely Is Applying Network Changes");
            row.setHiddenText(jobStatus(job.status()));
            row.setAccent(ThemeManager.getDefaultAccent());
            row.setOnClick(job.canResume() ? () -> resume(job) : () -> {});
            row.clearMountedWidgets();
            if (job.canResume()) {
                row.addMountedWidget(rowAction("reload.png", "Resume", () -> resume(job)));
            }
            if (job.canRollback() && (job.status() == NetworkJobStatus.INTERRUPTED || job.status() == NetworkJobStatus.FAILED)) {
                row.addMountedWidget(rowAction("history.png", "Rollback", () -> rollback(job), ThemeManager.getAccent("danger")));
            }
        }
        activitySetting.setVisible(!desired.isEmpty());
        requestLayout(overviewContainer);
    }

    private void syncAttentionRows() {
        if (attentionSetting == null) {
            return;
        }
        Map<String, AttentionItem> desired = new LinkedHashMap<>();
        discovery.issues().stream().filter(issue -> issue.severity() != NetworkValidationIssue.Severity.INFO).forEach(issue -> {
            NetworkMember member = attentionMember(issue.subject());
            String id = "issue:" + issue.code() + ":" + issue.subject();
            desired.put(id, attentionItem(id, issue.message(), issue.message(), issue.blocksPersistence(), member));
        });
        incidents.stream().filter(incident -> incident.status() == NetworkIncidentStatus.OPEN).forEach(incident -> {
            NetworkMember member = attentionMember(incident.nodeId());
            String detail = incident.detail().isBlank() ? incident.summary() : incident.detail();
            desired.put("incident:" + incident.incidentId(), attentionItem("incident:" + incident.incidentId(), incident.summary(), detail, incident.severity() == NetworkIncidentSeverity.CRITICAL, member));
        });
        attentionItems.clear();
        attentionItems.putAll(desired);
        for (String id : new ArrayList<>(attentionRows.keySet())) {
            if (desired.containsKey(id)) {
                continue;
            }
            attentionRows.remove(id);
            attentionSetting.removeRow(id);
        }
        desired.forEach((id, item) -> {
            MountableButtonWidget row = attentionRows.computeIfAbsent(id, ignored -> {
                MountableButtonWidget created = new MountableButtonWidget.Builder("").build();
                created.addMountedWidget(rowAction("info.png", "Open Details", () -> openAttention(id)));
                attentionSetting.addRow(id, "", List.of(created), 30, true, false);
                return created;
            });
            row.setName(item.title());
            row.setDescription(item.description());
            row.setAccent(item.blocking() ? ThemeManager.getAccent("danger") : ThemeManager.getDefaultAccent());
            row.setOnClick(() -> openAttention(id));
        });
        attentionSetting.setVisible(!desired.isEmpty());
        requestLayout(overviewContainer);
    }

    private NetworkMember attentionMember(String subject) {
        String value = subject == null ? "" : subject.trim();
        return network.members().stream().filter(member -> member.nodeId().equalsIgnoreCase(value) || member.instanceId().equalsIgnoreCase(value) || member.routeName().equalsIgnoreCase(value) || displayName(member).equalsIgnoreCase(value)).findFirst().orElse(null);
    }

    private void openAttention(String id) {
        AttentionItem item = attentionItems.get(id);
        if (item == null) {
            return;
        }
        PopupWidget[] popup = new PopupWidget[1];
        PopupWidget.Builder builder = new PopupWidget.Builder(item.title()).size(330, 125).onClose(() -> popup[0].hide());
        if (item.member() != null) {
            builder.addTitleAction("Open Server", () -> {
                popup[0].hide();
                openMember(item.member());
            }, PopupWidget.TitleActionRole.PRIMARY);
        }
        builder.addMarkdown("", item.description() + "\n\n" + item.detail(), 82);
        popup[0] = showPopup(builder.build());
    }

    private void populateCurrentJobs(Container container) {
        activitySetting = new Setting.Builder("Current Activity").build();
        container.addWidget(activitySetting);
        syncActivityRows();
    }

    private String friendlyJobName(NetworkJobType type) {
        return switch (type) {
            case QUICK_CREATE -> "Creating Network";
            case ATTACH -> "Adding Server";
            case DETACH -> "Removing Server";
            case ROUTING -> "Updating Join Rules";
            case REALMS -> "Updating Shared Data";
            case ROTATE_SECRET -> "Refreshing Connection Security";
            case RECONCILE -> "Repairing Network";
            case DELETE -> "Dissolving Network";
            case ADOPT -> "Connecting Existing Network";
            case LIFECYCLE -> "Updating Network";
        };
    }

    private MountableButtonWidget infoRow(Container container, String title, String description, String hiddenText) {
        return infoRow(container, title, description, hiddenText, ThemeManager.getDefaultAccent());
    }

    private MountableButtonWidget infoRow(Container container, String title, String description, String hiddenText, Accent accent) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(description).hiddenText(hiddenText).build();
        styleRow(container, row, accent, 32);
        return row;
    }

    private SquareButtonWidget rowAction(String icon, String hint, Runnable action) {
        return rowAction(icon, hint, action, ThemeManager.getDefaultAccent());
    }

    private SquareButtonWidget rowAction(String icon, String hint, Runnable action, Accent accent) {
        return new SquareButtonWidget.Builder().imagePath(icon).hint(hint).hintDelay(0.15f).onClick(action).accentType(accent).animateElevation(false).size(18, 18).build();
    }

    private void styleRow(Container container, MountableButtonWidget row, Accent accent, int height) {
        row.setAccent(accent);
        row.setSize(Math.max(220, container.getEffectiveWidth() - 10), height);
    }

    private void populateServers(Container container) {
        TextInputWidget search = new TextInputWidget.Builder().size(Math.max(220, container.getEffectiveWidth() - 8), 20).placeholder("Search Servers").onChange(value -> {
            serverSearchQuery = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            network.members().forEach(member -> serversSetting.setRowVisibility("server:" + member.nodeId(), serverSearchQuery.isBlank() || serverSearchText(member).contains(serverSearchQuery)));
            requestLayout(container);
        }).build();
        Setting.Builder servers = new Setting.Builder("Network Servers");
        servers.addRow("search", "", true, 20, search);
        MountableButtonWidget addServer = actionRow("Add Server", "Add an existing Remotely server or register a server managed elsewhere.", "merge.png", this::openAddServer);
        styleRow(container, addServer, ThemeManager.getDefaultAccent(), 30);
        servers.addRow("add", "", true, 30, addServer);
        for (NetworkMember member : network.members()) {
            MountableButtonWidget row = memberRow(container, member, runtimeSnapshot);
            serverRows.put(member.nodeId(), row);
            servers.addRow("server:" + member.nodeId(), "", true, 30, row);
        }
        serversSetting = servers.build();
        network.members().forEach(member -> serversSetting.setRowVisibility("server:" + member.nodeId(), serverSearchQuery.isBlank() || serverSearchText(member).contains(serverSearchQuery)));
        container.addWidget(serversSetting);
        requestLayout(container);
    }

    private void openAddServer() {
        background(() -> CompletableFuture.completedFuture(availableServers())).whenComplete((available, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                new Notification("Servers Unavailable", rootMessage(throwable), Notification.Type.ERROR);
                return;
            }
            openAddServer(available);
        }));
    }

    private void openAddServer(List<Instance> available) {
        List<String> serverIds = available.stream().map(Instance::getInstanceId).toList();
        String[] selectedServer = {serverIds.isEmpty() ? "" : serverIds.getFirst()};
        List<String> joinRules = new ArrayList<>();
        joinRules.add("");
        routingGroups.stream().map(RoutingGroup::id).forEach(joinRules::add);
        String[] joinRule = {""};
        PopupWidget[] popup = new PopupWidget[1];
        Runnable add = () -> {
            Instance instance = instancesById.get(selectedServer[0]);
            if (instance == null) {
                new Notification("Choose A Server", "Create a server in Remotely first, or register an external server.", Notification.Type.ERROR);
                return;
            }
            popup[0].hide();
            attachManaged(instance, joinRule[0]);
        };
        Runnable external = () -> {
            popup[0].hide();
            openExternalServer();
        };
        PopupWidget.Builder builder = new PopupWidget.Builder("Add Server").size(260, available.isEmpty() ? 105 : 135).setExpandWithDropdowns(true).onClose(() -> popup[0].hide())
            .addTitleAction("External", external, "Register External Server", PopupWidget.TitleActionRole.SECONDARY);
        if (available.isEmpty()) {
            builder.addRow("available", "Remotely Servers", "Every existing Remotely server is already part of a network. Create another server first, or register a server that is managed elsewhere.", true, 22, inactive("No Available Servers", ThemeManager.getDefaultAccent()));
        } else {
            builder.addTitleAction("Add", add, "Add Server", PopupWidget.TitleActionRole.PRIMARY);
            builder.addDropdown("Server", "Choose a Remotely server that is not already connected to a network.", serverIds, selectedServer[0], this::instanceName, value -> selectedServer[0] = value);
            builder.addDropdown("Join Rule", "Choose Later leaves the server out of player routing. A named rule makes the server available through that join path.", joinRules, joinRule[0], value -> value.isBlank() ? "Choose Later" : routingGroupName(value), value -> joinRule[0] = value);
        }
        popup[0] = showPopup(builder.build());
    }

    private void openExternalServer() {
        String[] name = {"External Server"};
        String[] address = {""};
        String[] port = {"25565"};
        String[] capacity = {"0"};
        Boolean[] ownership = {Boolean.FALSE};
        List<String> joinRules = new ArrayList<>();
        joinRules.add("");
        routingGroups.stream().map(RoutingGroup::id).forEach(joinRules::add);
        String[] joinRule = {""};
        PopupWidget[] popup = new PopupWidget[1];
        Runnable add = () -> {
            if (!ownership[0]) {
                new Notification("Confirm Server Ownership", "Confirm that you manage this server and its protection before registering it.", Notification.Type.ERROR);
                return;
            }
            try {
                popup[0].hide();
                attachExternal(name[0], address[0], parsePort(port[0]), parseCapacity(capacity[0]), joinRule[0]);
            } catch (RuntimeException exception) {
                new Notification("Server Details Invalid", rootMessage(exception), Notification.Type.ERROR);
            }
        };
        PopupWidget.Builder builder = new PopupWidget.Builder("Register External Server").size(280, 270).setExpandWithDropdowns(true).onClose(() -> popup[0].hide())
            .addTitleAction("Register", add, "Register External Server", PopupWidget.TitleActionRole.PRIMARY);
        builder.addTextField("Name", "Enter the server name shown in Remotely and used by proxy routing.", name[0], value -> name[0] = value);
        builder.addTextField("Address", "The private address the proxy uses to reach this server. Do not use a public address unless the backend is securely protected.", address[0], value -> address[0] = value);
        builder.addTextField("Port", "The Minecraft port this server listens on. It must be a whole number between 1 and 65535.", port[0], value -> port[0] = value);
        builder.addTextField("Player Limit", "Optional capacity used when choosing a server. Enter 0 when the server does not publish a fixed limit.", capacity[0], value -> capacity[0] = value);
        builder.addDropdown("Join Rule", "Choose Later leaves the server out of player routing. A named rule makes the server available through that join path.", joinRules, joinRule[0], value -> value.isBlank() ? "Choose Later" : routingGroupName(value), value -> joinRule[0] = value);
        builder.addToggle("Manual Ownership", "Confirm that Remotely only manages the proxy route. You remain responsible for starting this server, configuring forwarding, and protecting it from direct public access.", false, value -> ownership[0] = value);
        popup[0] = showPopup(builder.build());
    }

    private void attachManaged(Instance instance, String joinRule) {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        Notification notification = operationNotification("Adding Server", instance.getName());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.prepareAttach(network, instance, instance.getName(), NetworkMemberRole.CUSTOM, joinRule, defaultAddress(instance), observedPort(instance, 25566), 0, true, instances, List.of()))
            .thenCompose(prepared -> background(() -> manager.runPreparedAttach(prepared, instances, "Network Manager")))
            .whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
                applyingNetworkChange = false;
                finishOperation(notification, job, throwable, "Server Added");
            }));
    }

    private void attachExternal(String name, String address, int port, int capacity, String joinRule) {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        Notification notification = operationNotification("Registering Server", name);
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.prepareExternalAttach(network, name, NetworkMemberRole.CUSTOM, joinRule, address, port, capacity, instances, List.of()))
            .thenCompose(prepared -> background(() -> manager.runPreparedAttach(prepared, instances, "Network Manager")))
            .whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
                applyingNetworkChange = false;
                finishOperation(notification, job, throwable, "Server Registered");
            }));
    }

    private List<Instance> availableServers() {
        return instances.stream().filter(instance -> !instance.isProxyServer()).filter(instance -> remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).isEmpty()).toList();
    }

    private String instanceName(String instanceId) {
        Instance instance = instancesById.get(instanceId);
        return instance == null ? "Unavailable Server" : instance.getName();
    }

    private String defaultAddress(Instance instance) {
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

    private int parsePort(String value) {
        int port = Integer.parseInt(value == null ? "" : value.trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port Must Be Between 1 And 65535");
        }
        return port;
    }

    private int parseCapacity(String value) {
        int capacity = Integer.parseInt(value == null ? "" : value.trim());
        if (capacity < 0) {
            throw new IllegalArgumentException("Player Limit Cannot Be Negative");
        }
        return capacity;
    }

    private void populateRouting(Container container) {
        Setting.Builder rules = new Setting.Builder("Player Join Rules");
        rules.addRow("add", "", true, 30, actionRow("Add Join Rule", "Choose where players are sent when they join.", "add.png", () -> openRoutingGroup(null)));
        AnimatedButton routingEmpty = inactive("No Join Rules Yet", ThemeManager.getDefaultAccent());
        routingEmpty.setVisible(routingGroups.isEmpty());
        rules.addRow("empty", "", true, 22, routingEmpty);
        for (RoutingGroup group : routingGroups) {
            MountableButtonWidget row = routingRow(group);
            routingRows.put(group.id(), row);
            rules.addRow("rule:" + group.id(), "", true, 30, row);
        }
        routingSetting = rules.build();
        routingSetting.setRowVisibility("empty", routingGroups.isEmpty());
        container.addWidget(routingSetting);
        requestLayout(container);
    }

    private void openRoutingGroup(RoutingGroup existing) {
        String[] name = {existing == null ? "Join Rule" : existing.name()};
        String[] servers = {existing == null ? "" : displayNames(existing.nodeIds())};
        String[] weights = {existing == null ? "" : routingWeights(existing)};
        String[] addresses = {existing == null ? "" : String.join(", ", existing.forcedHosts())};
        String[] permission = {existing == null ? "" : existing.permission()};
        RoutingStrategy[] strategy = {existing == null ? RoutingStrategy.ORDERED : existing.strategy()};
        List<String> fallbacks = new ArrayList<>();
        fallbacks.add("");
        routingGroups.stream().filter(group -> existing == null || !group.id().equals(existing.id())).map(RoutingGroup::id).forEach(fallbacks::add);
        String[] fallback = {existing == null ? "" : existing.fallbackGroupId()};
        PopupWidget[] popup = new PopupWidget[1];
        Runnable save = () -> {
            try {
                RoutingGroup updated = buildRoutingGroup(existing, name[0], strategy[0], servers[0], weights[0], addresses[0], fallback[0], permission[0]);
                List<RoutingGroup> draft = new ArrayList<>(routingGroups);
                if (existing == null) {
                    draft.add(updated);
                } else {
                    draft.set(draft.indexOf(existing), updated);
                }
                routingGroups = List.copyOf(draft);
                popup[0].hide();
                syncRoutingRows();
            } catch (RuntimeException exception) {
                new Notification("Join Rule Invalid", rootMessage(exception), Notification.Type.ERROR);
            }
        };
        PopupWidget.Builder builder = new PopupWidget.Builder(existing == null ? "Add Join Rule" : "Edit " + existing.name()).size(300, 310).setResizable(true).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        if (existing != null) {
            builder.addTitleAction("Delete", () -> {
                if (routingGroups.stream().anyMatch(group -> group.fallbackGroupId().equals(existing.id()))) {
                    new Notification("Rule Still Used", "Choose another fallback for the rules that depend on it first.", Notification.Type.ERROR);
                    return;
                }
                routingGroups = routingGroups.stream().filter(group -> !group.equals(existing)).toList();
                popup[0].hide();
                syncRoutingRows();
            }, "Delete Join Rule", PopupWidget.TitleActionRole.DESTRUCTIVE);
        }
        builder.addTitleAction(existing == null ? "Add" : "Update", save, existing == null ? "Add Join Rule" : "Update Join Rule", PopupWidget.TitleActionRole.PRIMARY);
        builder.addTextField("Name", "Enter a short name for this player join rule.", name[0], value -> name[0] = value);
        builder.addScrollSelector("Player Choice", "Use Listed Order tries Servers from left to right. Choose Least Busy uses the server with the fewest players. Use Custom Priorities uses the values in Priorities.", Arrays.stream(RoutingStrategy.values()).map(this::routingStrategyLabel).toList(), strategy[0].ordinal(), index -> strategy[0] = RoutingStrategy.values()[index]);
        builder.addTextField("Servers", "List server names in the order players should try them, separated with commas.", servers[0], value -> servers[0] = value);
        builder.addTextField("Priorities", "Only used with Custom Priorities. Enter each server name followed by = and a positive number, such as Survival=3.", weights[0], value -> weights[0] = value);
        builder.addTextField("Join Addresses", "Optional addresses that should send players through this rule, such as play.example.com. Separate multiple addresses with commas.", addresses[0], value -> addresses[0] = value);
        builder.addDropdown("If Unavailable", "No Other Rule ends this path when every server is unavailable. A named rule tries that player path next.", fallbacks, fallback[0], value -> value.isBlank() ? "No Other Rule" : routingGroupName(value), value -> fallback[0] = value);
        builder.addTextField("Access Permission", "Optional permission required to use this rule. Leave it empty when every player may use it.", permission[0], value -> permission[0] = value);
        popup[0] = showPopup(builder.build());
    }

    private RoutingGroup buildRoutingGroup(RoutingGroup existing, String rawName, RoutingStrategy strategy, String rawServers, String rawWeights, String rawAddresses, String rawFallback, String permission) {
        String id = existing == null ? nextRoutingId() : existing.id();
        String name = rawName == null || rawName.isBlank() ? "Join Rule" : rawName.trim();
        Map<String, NetworkMember> members = network.members().stream().filter(member -> !member.isProxy()).collect(Collectors.toMap(member -> displayName(member).toLowerCase(Locale.ROOT), member -> member, (first, second) -> first, LinkedHashMap::new));
        network.members().stream().filter(member -> !member.isProxy()).forEach(member -> members.putIfAbsent(member.routeName().toLowerCase(Locale.ROOT), member));
        List<String> nodeIds = commaValues(rawServers).stream().map(value -> {
            NetworkMember member = members.get(value.toLowerCase(Locale.ROOT));
            if (member == null) {
                throw new IllegalArgumentException("Unknown Server " + value);
            }
            return member.nodeId();
        }).distinct().toList();
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String value : commaValues(rawWeights)) {
            int separator = value.indexOf('=');
            if (separator <= 0 || separator >= value.length() - 1) {
                throw new IllegalArgumentException("Priorities Use Server=Number");
            }
            NetworkMember member = members.get(value.substring(0, separator).trim().toLowerCase(Locale.ROOT));
            if (member == null || !nodeIds.contains(member.nodeId())) {
                throw new IllegalArgumentException("Priority Uses A Server That Is Not In This Rule");
            }
            int weight = Integer.parseInt(value.substring(separator + 1).trim());
            if (weight < 1 || weight > 10_000) {
                throw new IllegalArgumentException("Priorities Must Be Between 1 And 10000");
            }
            weights.put(member.nodeId(), weight);
        }
        if (strategy == RoutingStrategy.WEIGHTED && nodeIds.stream().anyMatch(nodeId -> !weights.containsKey(nodeId))) {
            throw new IllegalArgumentException("Custom Priorities Need A Number For Every Server");
        }
        Set<String> addresses = commaValues(rawAddresses).stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
        String fallback = rawFallback == null ? "" : rawFallback.trim();
        return new RoutingGroup(id, name, strategy, nodeIds, weights, fallback, addresses, permission);
    }

    private String nextRoutingId() {
        if (routingGroups.stream().noneMatch(group -> group.id().equals("fallback"))) {
            return "fallback";
        }
        int index = 1;
        List<String> ids = routingGroups.stream().map(RoutingGroup::id).toList();
        while (ids.contains("rule-" + index)) {
            index++;
        }
        return "rule-" + index;
    }

    private String routingGroupName(String id) {
        return routingGroups.stream().filter(group -> group.id().equals(id)).map(RoutingGroup::name).findFirst().orElse("No Other Rule");
    }

    private String routingStrategyLabel(RoutingStrategy strategy) {
        return switch (strategy) {
            case ORDERED -> "Use Listed Order";
            case LEAST_PLAYERS -> "Choose Least Busy";
            case WEIGHTED -> "Use Custom Priorities";
        };
    }

    private String routingWeights(RoutingGroup group) {
        return group.nodeIds().stream().filter(group.weights()::containsKey).map(nodeId -> member(nodeId).map(this::displayName).orElse("Missing Server") + "=" + group.weights().get(nodeId)).collect(Collectors.joining(", "));
    }

    private void runPreflight() {
        Notification notification = operationNotification("Checking Join Path", network.name());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.runPreflight(network, instances)).whenComplete((report, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Player Join Check Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                refresh();
                return;
            }
            boolean passed = report.status() == NetworkPreflightStatus.SUCCEEDED;
            notification.update().message(passed ? "Join Path Ready" : "Join Path Needs Attention").description(report.summary()).type(passed ? Notification.Type.SUCCESS : Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            refresh();
        }));
    }

    private void prepareSecretRotation() {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        Notification notification = operationNotification("Refreshing Connection Security", network.name());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.prepareSecretRotation(network, instances))
            .thenCompose(prepared -> background(() -> manager.runPreparedSecretRotation(prepared, instances, "Network Manager")))
            .whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
                applyingNetworkChange = false;
                finishOperation(notification, job, throwable, "Connection Security Refreshed");
            }));
    }

    private void createDissolvePopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Dissolve Network").size(300, 92).onClose(() -> dissolvePopup.hide())
            .addTitleAction("Dissolve", this::dissolve, "Restore Servers And Dissolve", PopupWidget.TitleActionRole.DESTRUCTIVE);
        builder.addRow("confirmDissolve", "Restore Independent Servers", "Remotely removes shared routing, forwarding, and ReSync settings, then restores each managed server. Server files and worlds remain.", true, 24, inactive("Servers And Worlds Are Kept", ThemeManager.getDefaultAccent()));
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
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        dissolvePopup.hide();
        Notification notification = operationNotification("Dissolving Network", network.name());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.dissolveSafely(network, instances, "Network Overview")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
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

    private void openMember(NetworkMember member) {
        if (!member.isManaged()) {
            new Notification("External Server", member.address() + ":" + member.port(), Notification.Type.INFO);
            return;
        }
        Instance instance = instancesById.get(member.instanceId());
        if (instance == null) {
            new Notification("Server Unavailable", displayName(member), Notification.Type.ERROR);
            return;
        }
        remotelyClient.openInstanceInTerminal(this, instance);
    }

    private void confirmDetach(NetworkMember member, Instance instance) {
        PopupWidget[] popup = new PopupWidget[1];
        Runnable detach = () -> {
            popup[0].hide();
            if (member.isManaged()) {
                if (instance == null) {
                    new Notification("Server Unavailable", displayName(member), Notification.Type.ERROR);
                    return;
                }
                detachManaged(member, instance);
            } else {
                detachExternal(member);
            }
        };
        PopupWidget.Builder builder = new PopupWidget.Builder("Detach " + displayName(member)).size(290, 92).onClose(() -> popup[0].hide())
            .addTitleAction("Detach", detach, "Restore Independent Settings", PopupWidget.TitleActionRole.DESTRUCTIVE);
        builder.addRow("detachServer", "Make Server Independent", "Remotely removes this server from routing and shared ReSync settings, then restores its independent configuration. Files and worlds remain.", true, 24, inactive("Server Files Are Kept", ThemeManager.getDefaultAccent()));
        popup[0] = showPopup(builder.build());
    }

    private void setRuntimeMode(NetworkMember member, NetworkNodeStatus status) {
        String action = status == NetworkNodeStatus.ONLINE ? "Resuming" : status == NetworkNodeStatus.DRAINING ? "Draining" : "Starting Maintenance";
        Notification notification = operationNotification(action + " Server", displayName(member));
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.setRuntimeNodeMode(networkId, member.nodeId(), status)).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Server Update Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Server Updated").description(displayName(member) + " • " + titleCase(status.name())).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            refresh();
        }));
    }

    private void executeProxyCommand(TextInputWidget input) {
        String command = input.getText().trim();
        Notification notification = operationNotification("Running Proxy Command", network.name());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.executeRuntimeProxyCommand(networkId, command)).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
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
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.broadcastRuntimeMessage(networkId, message)).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Broadcast Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            input.setText("");
            notification.update().message("Broadcast Sent").description(network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        }));
    }

    private void detachExternal(NetworkMember member) {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        Notification notification = operationNotification("Detaching External Server", displayName(member));
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.detachExternalSafely(network, member, instances, "Network Overview")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
            finishOperation(notification, job, throwable, "External Server Removed");
        }));
    }

    private void detachManaged(NetworkMember member, Instance instance) {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        Notification notification = operationNotification("Detaching Server", displayName(member));
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.detachSafely(network, instance, instances, "Network Overview")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
            finishOperation(notification, job, throwable, "Server Detached");
        }));
    }

    private void reconcile() {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        Notification notification = operationNotification("Healing Network", network.name());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> {
            manager.reconcileInstanceBindings(instances);
            return manager.runJob(network, instances, List.of(), NetworkJobType.RECONCILE, "Network Overview");
        }).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
            finishOperation(notification, job, throwable);
        }));
    }

    private void resume(NetworkJob job) {
        Notification notification = operationNotification("Resuming Network", job.message());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.resumeJob(job.jobId(), instances, List.of())).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, updated, throwable)));
    }

    private void rollback(NetworkJob job) {
        Notification notification = operationNotification("Rolling Back Network", job.message());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.rollbackJob(job.jobId(), instances)).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishOperation(notification, updated, throwable)));
    }

    private void lifecycle(NetworkLifecycleOperation operation) {
        if (applyingNetworkChange) {
            return;
        }
        applyingNetworkChange = true;
        networkPowerButton.update(operation == NetworkLifecycleOperation.START ? InstanceState.STARTING : InstanceState.STOPPING);
        header().requestLayoutUpdate();
        Notification notification = operationNotification(titleCase(operation.name()) + " Network", network.name());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.runLifecycle(network, instances, operation, "Network Overview")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            applyingNetworkChange = false;
            finishLifecycle(notification, job, throwable);
        }));
    }

    private void resumeLifecycle(NetworkLifecycleJob job) {
        Notification notification = operationNotification("Resuming " + titleCase(job.operation().name()), job.message());
        NetworkManager manager = remotelyClient.getNetworkManager();
        background(() -> manager.resumeLifecycle(job.jobId(), instances)).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishLifecycle(notification, updated, throwable)));
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
            refresh();
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
            notification.update().message("Network Needs Attention").description(job == null ? "The network action did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            refresh();
            return;
        }
        notification.update().message("Network Ready").description(job.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        refresh();
    }

    private void refresh() {
        NetworkManager manager = remotelyClient.getNetworkManager();
        if (manager == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            return;
        }
        long generation = ++refreshGeneration;
        NetworkDefinition previous = network;
        boolean nameDirty = networkNameInput != null && !networkNameInput.getText().trim().equals(previous.name());
        boolean routingDirty = !routingGroups.equals(previous.routingGroups());
        boolean realmsDirty = !syncRealms.equals(previous.syncRealms());
        background(() -> CompletableFuture.completedFuture(loadState(manager))).whenComplete((state, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (generation != refreshGeneration) {
                return;
            }
            if (throwable != null) {
                new Notification("Refresh Failed", rootMessage(throwable), Notification.Type.ERROR);
                return;
            }
            network = state.network();
            instances = state.instances();
            instancesById.clear();
            instances.forEach(instance -> instancesById.put(instance.getInstanceId(), instance));
            discovery = state.discovery();
            runtimeSnapshot = latestRuntimeSnapshot(runtimeSnapshot, state.runtime());
            incidents = state.incidents();
            lifecycleJobs = state.lifecycleJobs();
            activeJobs = state.jobs();
            transferFailureHeat = state.transferFailureHeat();
            if (!routingDirty) {
                routingGroups = List.copyOf(network.routingGroups());
            }
            if (!realmsDirty) {
                syncRealms = List.copyOf(network.syncRealms());
            }
            if (!nameDirty && networkNameInput != null) {
                networkNameInput.setText(network.name());
            }
            updateNetworkPowerButton();
            topologyWidget.applyNetwork(network, discovery.observations());
            batchingLayout = true;
            try {
                syncServerRows();
                syncRoutingRows();
                syncPlayerDataRows();
                syncActivityRows();
                syncAttentionRows();
            } finally {
                batchingLayout = false;
            }
            overviewContainer.updateWidgetPositions();
            serversContainer.updateWidgetPositions();
            sharingContainer.updateWidgetPositions();
        }));
    }

    private void registerRuntimeChangeListener(NetworkManager manager) {
        if (runtimeChangeListenerRegistered) {
            return;
        }
        manager.addRuntimeListener(runtimeChangeListener);
        runtimeChangeListenerRegistered = true;
    }

    private void queueRuntimeSnapshot(NetworkRuntimeSnapshot snapshot) {
        if (snapshot == null || !networkId.equals(snapshot.networkId())) {
            return;
        }
        ScreenManager.getInstance().execute(() -> applyRuntimeSnapshot(snapshot));
    }

    private void applyRuntimeSnapshot(NetworkRuntimeSnapshot snapshot) {
        if (!runtimeChangeListenerRegistered || snapshot == null || !networkId.equals(snapshot.networkId())) {
            return;
        }
        NetworkRuntimeSnapshot latest = latestRuntimeSnapshot(runtimeSnapshot, snapshot);
        if (latest == runtimeSnapshot) {
            return;
        }
        runtimeSnapshot = latest;
        if (network == null || serversContainer == null) {
            return;
        }
        for (NetworkMember member : network.members()) {
            MountableButtonWidget row = serverRows.get(member.nodeId());
            if (row != null) {
                updateMemberRow(serversContainer, row, member, runtimeSnapshot);
            }
        }
    }

    private NetworkRuntimeSnapshot latestRuntimeSnapshot(NetworkRuntimeSnapshot current, NetworkRuntimeSnapshot candidate) {
        if (candidate == null || !networkId.equals(candidate.networkId())) {
            return current;
        }
        return current == null || candidate.updatedAt() >= current.updatedAt() ? candidate : current;
    }

    @Override
    public void removed() {
        refreshGeneration++;
        NetworkManager manager = remotelyClient.getNetworkManager();
        if (manager != null && runtimeChangeListenerRegistered) {
            manager.removeRuntimeListener(runtimeChangeListener);
        }
        runtimeChangeListenerRegistered = false;
        super.removed();
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

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record AttentionItem(String id, String title, String description, String detail, boolean blocking, NetworkMember member) {
    }

    private record RefreshState(NetworkDefinition network, List<Instance> instances, NetworkDiscoveryResult discovery, NetworkRuntimeSnapshot runtime, List<NetworkIncident> incidents, List<NetworkLifecycleJob> lifecycleJobs, List<NetworkJob> jobs, Map<String, Integer> transferFailureHeat) {
    }
}
