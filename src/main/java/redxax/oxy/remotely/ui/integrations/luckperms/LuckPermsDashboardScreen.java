package redxax.oxy.remotely.ui.integrations.luckperms;

import redxax.oxy.remotely.data.flow.OptionCatalogLoader;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsClient;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient.DistributionResult;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient.Snapshot;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient.TargetResult;
import redxax.oxy.remotely.flow.ui.OptionCatalogSelector;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.ScrollSelectorWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.resync.permissions.LuckPermsManagementContract;
import restudio.resync.permissions.LuckPermsManagementContract.ChangeSet;
import restudio.resync.permissions.LuckPermsManagementContract.EffectivePreview;
import restudio.resync.permissions.LuckPermsManagementContract.EntityCreate;
import restudio.resync.permissions.LuckPermsManagementContract.EntityDelete;
import restudio.resync.permissions.LuckPermsManagementContract.EntityType;
import restudio.resync.permissions.LuckPermsManagementContract.GroupPage;
import restudio.resync.permissions.LuckPermsManagementContract.GroupSummary;
import restudio.resync.permissions.LuckPermsManagementContract.NodeData;
import restudio.resync.permissions.LuckPermsManagementContract.NodeKind;
import restudio.resync.permissions.LuckPermsManagementContract.Overview;
import restudio.resync.permissions.LuckPermsManagementContract.PageRequest;
import restudio.resync.permissions.LuckPermsManagementContract.PreviewRequest;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectChange;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectDetail;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectRef;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectType;
import restudio.resync.permissions.LuckPermsManagementContract.TrackChange;
import restudio.resync.permissions.LuckPermsManagementContract.TrackDetail;
import restudio.resync.permissions.LuckPermsManagementContract.UserPage;
import restudio.resync.permissions.LuckPermsManagementContract.UserSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LuckPermsDashboardScreen extends ReScreen {
    private static final int PAGE_SIZE = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final ReSyncLuckPermsClient client;
    private final ReSyncLuckPermsNetworkClient networkClient;
    private final Map<SubjectRef, SubjectChange> subjectChanges = new LinkedHashMap<>();
    private final Map<String, TrackChange> trackChanges = new LinkedHashMap<>();
    private final Map<String, EntityCreate> creates = new LinkedHashMap<>();
    private final Map<String, EntityDelete> deletes = new LinkedHashMap<>();
    private final List<UserSummary> users = new ArrayList<>();
    private final List<GroupSummary> groups = new ArrayList<>();
    private final List<TrackDetail> tracks = new ArrayList<>();
    private final Map<String, EntryWidget> userWidgets = new LinkedHashMap<>();
    private final Map<String, EntryWidget> groupWidgets = new LinkedHashMap<>();
    private final Map<String, EntryWidget> trackWidgets = new LinkedHashMap<>();
    private final List<MetricWidget> metrics = new ArrayList<>();
    private final List<AuditWidget> auditWidgets = new ArrayList<>();
    private final List<StatusWidget> statusWidgets = new ArrayList<>();
    private final List<AnimatedWidget> overviewActivityWidgets = new ArrayList<>();
    private final List<AnimatedWidget> userDirectoryWidgets = new ArrayList<>();
    private final List<AnimatedWidget> groupDirectoryWidgets = new ArrayList<>();
    private final List<AnimatedWidget> trackDirectoryWidgets = new ArrayList<>();
    private final ReSyncLuckPermsClient.Listener listener = new ReSyncLuckPermsClient.Listener() {
        @Override
        public void onInvalidated(LuckPermsManagementContract.Invalidation invalidation) {
            ui(() -> refreshInvalidated(invalidation));
        }

        @Override
        public void onAvailability(boolean available, String message) {
            ui(() -> {
                boolean becameAvailable = available && !LuckPermsDashboardScreen.this.available;
                LuckPermsDashboardScreen.this.available = available;
                setStatus(available ? "Connected" : "Unavailable", message, available ? "nice" : "calm");
                if (becameAvailable) {
                    loadUsers(true);
                    loadGroups(true);
                    refreshActive();
                }
            });
        }
    };

    private ReSyncLuckPermsClient.Subscription subscription;
    private Container overviewContainer;
    private Container usersContainer;
    private Container groupsContainer;
    private Container tracksContainer;
    private Container networkContainer;
    private StatusWidget statusWidget;
    private StatusWidget networkStatusWidget;
    private LoadMoreWidget usersMore;
    private LoadMoreWidget groupsMore;
    private MessageWidget usersState;
    private MessageWidget groupsState;
    private MessageWidget tracksState;
    private Setting overviewAccess;
    private Setting overviewActivity;
    private Setting usersDirectory;
    private Setting usersDetail;
    private Setting groupsDirectory;
    private Setting groupsDetail;
    private Setting tracksDirectory;
    private Setting tracksDetail;
    private LuckPermsNetworkGraphWidget networkGraph;
    private Snapshot networkSnapshot;
    private String activeMode = "overview";
    private String userCursor = "";
    private String groupCursor = "";
    private boolean usersHasMore;
    private boolean groupsHasMore;
    private boolean loadingUsers;
    private boolean loadingGroups;
    private boolean saving;
    private boolean available;
    private boolean disposed;
    private boolean usersLoaded;
    private boolean groupsLoaded;
    private boolean previewReady;
    private boolean networkRefreshScheduled;
    private int networkRefreshAttempts;
    private long previewSequence;
    private String userFilter = "";
    private String groupFilter = "";
    private String trackFilter = "";
    private String operationId = UUID.randomUUID().toString();

    public LuckPermsDashboardScreen(Screen parent, ReSyncLuckPermsClient client) {
        this.parent = parent;
        this.client = client;
        this.networkClient = client.network();
    }

    @Override
    public String getDesktopAppId() {
        return "luckperms";
    }

    @Override
    public String getDesktopAppTitle() {
        return "Permissions";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "op.png";
    }

    @Override
    public void init() {
        super.init();
        disposed = false;
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        metrics.clear();
        auditWidgets.clear();
        statusWidgets.clear();
        overviewActivityWidgets.clear();
        userDirectoryWidgets.clear();
        groupDirectoryWidgets.clear();
        trackDirectoryWidgets.clear();
        userWidgets.clear();
        groupWidgets.clear();
        trackWidgets.clear();
        users.clear();
        groups.clear();
        tracks.clear();
        usersLoaded = false;
        groupsLoaded = false;
        previewReady = false;
        OptionCatalogLoader.preload(client.serverId(), List.of(
            OptionCatalogLoader.request("server:luckperms:permission"),
            OptionCatalogLoader.request("server:luckperms:group"),
            OptionCatalogLoader.request("server:luckperms:track"),
            OptionCatalogLoader.request("server:minecraft:world"),
            OptionCatalogLoader.request("server:resync:network_node")));
        header().addLeft("reload.png", this::refreshActive, "Refresh");
        header().addLeft("add.png", this::addForActiveTab, "Add");
        header().addLeft("search.png", this::openPreview, "Preview Permission");
        header().addLeft("save.png", this::save, "Save");
        header().addRight("close.png", this::close, "Close");
        header().build();

        tabs().builder().position(5, 36).size(width - 10, 18).onTabSelected(this::switchTab).allowAdd(false).allowClose(false).allowReorder(false).allowRename(false).build();
        overviewContainer = container("luckperms-overview", true);
        usersContainer = container("luckperms-users", true);
        groupsContainer = container("luckperms-groups", true);
        tracksContainer = container("luckperms-tracks", true);
        networkContainer = container("luckperms-network", true);

        addTab("Overview", "overview", overviewContainer);
        addTab("Users", "users", usersContainer);
        addTab("Groups", "groups", groupsContainer);
        addTab("Tracks", "tracks", tracksContainer);
        addTab("Network", "network", networkContainer);

        overviewAccess = new Setting.Builder("Check Access").build();
        overviewActivity = new Setting.Builder("Permission Activity").build();
        overviewContainer.addWidget(overviewAccess);
        overviewContainer.addWidget(overviewActivity);
        buildOverview();

        usersDirectory = new Setting.Builder("Players").build();
        usersDetail = new Setting.Builder("Selected Player").build();
        usersContainer.addWidget(usersDirectory);
        usersContainer.addWidget(usersDetail);
        addRow(usersDirectory, "status", "", "", 30, addStatus(usersContainer));
        addRow(usersDirectory, "filter", "", "", 20, filter("Search Players", value -> {
            userFilter = normalize(value);
            filterUsers();
        }));
        addRow(usersDirectory, "create", "", "", 30, actionRow(usersContainer, "Add Player", "Create Permissions For A Player By Name", "add.png", () -> showCreate(EntityType.USER)));
        usersState = new MessageWidget(contentWidth(usersContainer), "Users", "Open This Tab To Load Players");
        addRow(usersDirectory, "state", "", "", 30, usersState);
        usersMore = new LoadMoreWidget(contentWidth(usersContainer), this::loadMoreUsers);
        usersMore.visible = false;
        addRow(usersDirectory, "more", "", "", 30, usersMore);
        usersDirectory.setRowVisibility("more", false);
        addRow(usersDetail, "empty", "", "", 30, emptyWorkspace("Select A Player", "Their Rank And Current Permissions Appear Here"));

        groupsDirectory = new Setting.Builder("Groups").build();
        groupsDetail = new Setting.Builder("Selected Group").build();
        groupsContainer.addWidget(groupsDirectory);
        groupsContainer.addWidget(groupsDetail);
        addRow(groupsDirectory, "status", "", "", 30, addStatus(groupsContainer));
        addRow(groupsDirectory, "filter", "", "", 20, filter("Search Groups", value -> {
            groupFilter = normalize(value);
            filterGroups();
        }));
        addRow(groupsDirectory, "create", "", "", 30, actionRow(groupsContainer, "Create Group", "Add A New Rank Or Permission Group", "add.png", () -> showCreate(EntityType.GROUP)));
        groupsState = new MessageWidget(contentWidth(groupsContainer), "Groups", "Open This Tab To Load Groups");
        addRow(groupsDirectory, "state", "", "", 30, groupsState);
        groupsMore = new LoadMoreWidget(contentWidth(groupsContainer), this::loadMoreGroups);
        groupsMore.visible = false;
        addRow(groupsDirectory, "more", "", "", 30, groupsMore);
        groupsDirectory.setRowVisibility("more", false);
        addRow(groupsDetail, "empty", "", "", 30, emptyWorkspace("Select A Group", "Its Priority And Current Permissions Appear Here"));

        tracksDirectory = new Setting.Builder("Promotion Tracks").build();
        tracksDetail = new Setting.Builder("Selected Track").build();
        tracksContainer.addWidget(tracksDirectory);
        tracksContainer.addWidget(tracksDetail);
        addRow(tracksDirectory, "status", "", "", 30, addStatus(tracksContainer));
        addRow(tracksDirectory, "filter", "", "", 20, filter("Search Tracks", value -> {
            trackFilter = normalize(value);
            filterTracks();
        }));
        addRow(tracksDirectory, "create", "", "", 30, actionRow(tracksContainer, "Create Track", "Create An Ordered Promotion Path", "add.png", () -> showCreate(EntityType.TRACK)));
        tracksState = new MessageWidget(contentWidth(tracksContainer), "Tracks", "Open This Tab To Load Promotion Paths");
        addRow(tracksDirectory, "state", "", "", 30, tracksState);
        addRow(tracksDetail, "empty", "", "", 30, emptyWorkspace("Select A Track", "Its Promotion Order Appears Here"));

        buildNetwork();

        tabsManager.setActiveTab(0);
        setActiveContainer(overviewContainer);
        addRow(overviewAccess, "loading", "", "", 30, infoRow("Loading Permission Catalog", "Reading Players, Groups, And Permissions", "Please Wait"));
        subscription = client.subscribe(listener);
        refreshOverview();
        loadUsers(true);
        loadGroups(true);
    }

    private Container container(String id, boolean responsiveSplit) {
        Container container = createContainer(id, 5, 60, width - 10, height - 65);
        container.layout(new ManagedLayout()).padding(5).columns(1).verticalSpacing(4).scrolling(true).backgroundDrawing(true);
        return container;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        Container[] containers = {overviewContainer, usersContainer, groupsContainer, tracksContainer, networkContainer};
        for (Container container : containers) {
            if (container != null) {
                container.columns(1);
                container.updateWidgetPositions();
            }
        }
        if (networkGraph != null && networkContainer != null) {
            networkGraph.setWidth(Math.max(220, networkContainer.getEffectiveWidth() - 10));
            networkContainer.updateWidgetPositions();
        }
    }

    private void addTab(String title, String mode, Container container) {
        TabsManager.Tab tab = tabs().addTab(title, container);
        tab.setData(mode);
    }

    private void buildOverview() {
        statusWidget = addStatus(overviewContainer);
        addRow(overviewActivity, "status", "", "", 30, statusWidget);
        String[] titles = {"Players", "Permission Structure", "Saved Version", "Latest Change"};
        for (int i = 0; i < titles.length; i++) {
            String title = titles[i];
            MetricWidget metric = new MetricWidget(contentWidth(overviewContainer), title);
            metrics.add(metric);
            addRow(overviewActivity, "metric:" + i, "", "", 30, metric);
        }
        for (int i = 0; i < 6; i++) {
            AuditWidget audit = new AuditWidget(contentWidth(overviewContainer));
            audit.visible = false;
            auditWidgets.add(audit);
            addRow(overviewActivity, "audit:" + i, "", "", 30, audit);
            overviewActivity.setRowVisibility("audit:" + i, false);
        }
    }

    private StatusWidget addStatus(Container container) {
        StatusWidget status = new StatusWidget(contentWidth(container));
        statusWidgets.add(status);
        return status;
    }

    private TextInputWidget filter(String placeholder, Consumer<String> change) {
        return new TextInputWidget.Builder().size(220, 20).placeholder(placeholder).onChange(change).build();
    }

    private void buildNetwork() {
        networkStatusWidget = addStatus(networkContainer);
        Setting status = new Setting.Builder("Permission Changes").build();
        addRow(status, "status", "", "", 30, networkStatusWidget);
        networkContainer.addWidget(status);
        Snapshot initial = new Snapshot("", "This Network", client.serverId(), List.of());
        networkSnapshot = initial;
        networkGraph = new LuckPermsNetworkGraphWidget(0, 0, Math.max(220, networkContainer.getEffectiveWidth() - 10), initial,
            configuration -> complete(networkClient.configure(configuration), this::applyNetwork, "Could Not Update Permission Delivery"));
        networkContainer.addWidget(networkGraph);
        networkClient.snapshot().whenComplete((snapshot, error) -> ui(() -> {
            if (error == null) {
                networkSnapshot = snapshot;
                networkGraph.applySnapshot(snapshot);
                networkContainer.updateWidgetPositions();
                scheduleNetworkRefresh(snapshot);
            }
        }));
    }

    private void preparePreview() {
        if (usersLoaded && groupsLoaded && !previewReady) {
            previewReady = true;
            openPreview();
        }
    }

    private void switchTab(TabsManager.Tab tab) {
        Object data = tab.getData();
        activeMode = data instanceof String value ? value : "overview";
        Container target = switch (activeMode) {
            case "users" -> usersContainer;
            case "groups" -> groupsContainer;
            case "tracks" -> tracksContainer;
            case "network" -> networkContainer;
            default -> overviewContainer;
        };
        setActiveContainer(target);
        if ("users".equals(activeMode) && users.isEmpty()) {
            loadUsers(true);
        } else if ("groups".equals(activeMode) && groups.isEmpty()) {
            loadGroups(true);
        } else if ("tracks".equals(activeMode) && tracks.isEmpty()) {
            loadTracks();
        } else if ("network".equals(activeMode)) {
            refreshNetwork();
        }
    }

    private void refreshActive() {
        switch (activeMode) {
            case "users" -> loadUsers(true);
            case "groups" -> loadGroups(true);
            case "tracks" -> loadTracks();
            case "network" -> refreshNetwork();
            default -> refreshOverview();
        }
    }

    private void refreshOverview() {
        setStatus("Loading", "Reading Permission State", "calm");
        complete(client.overview(), this::applyOverview, "Could Not Load Overview");
    }

    private void applyOverview(Overview overview) {
        available = overview.available();
        List<String> values = List.of(
            overview.knownUsers() + " Known • " + overview.loadedUsers() + " Loaded • " + overview.onlineUsers() + " Online",
            overview.groups() + " Groups • " + overview.tracks() + " Tracks",
            overview.revision() + " • LuckPerms " + (overview.version().isBlank() ? "Unknown" : overview.version()),
            overview.lastChangedAt() > 0 ? TIME.format(Instant.ofEpochMilli(overview.lastChangedAt())) : "No Changes"
        );
        for (int i = 0; i < metrics.size(); i++) {
            metrics.get(i).setValue(values.get(i));
        }
        for (int i = 0; i < auditWidgets.size(); i++) {
            AuditWidget widget = auditWidgets.get(i);
            if (i < overview.audit().size()) {
                widget.setEntry(overview.audit().get(i));
                widget.visible = true;
                overviewActivity.setRowVisibility("audit:" + i, true);
            } else {
                widget.visible = false;
                overviewActivity.setRowVisibility("audit:" + i, false);
            }
        }
        setStatus(overview.available() ? overview.serverName() : "LuckPerms Unavailable", overview.available() ? "Live Permission Data" : "Install Or Enable LuckPerms", overview.available() ? "nice" : "calm");
        overviewContainer.updateWidgetPositions();
    }

    private void loadUsers(boolean reset) {
        if (loadingUsers) {
            return;
        }
        loadingUsers = true;
        usersState.setText("Loading Users", reset ? "Reading Players And Direct Permissions" : "Loading More Players");
        usersState.visible = true;
        String cursor = reset ? "" : userCursor;
        complete(client.users(new PageRequest(cursor, PAGE_SIZE, "")), page -> {
            loadingUsers = false;
            applyUsers(page, reset);
        }, "Could Not Load Users", () -> {
            loadingUsers = false;
            usersState.setText("Users Unavailable", "Refresh To Try The Connection Again");
            usersState.visible = true;
        });
    }

    private void applyUsers(UserPage page, boolean reset) {
        usersLoaded = true;
        if (reset) {
            users.clear();
            userCursor = "";
            userWidgets.values().forEach(widget -> widget.visible = false);
        }
        for (UserSummary user : page.items()) {
            int existing = findUser(user.uniqueId());
            if (existing >= 0) {
                users.set(existing, user);
            } else {
                users.add(user);
            }
            EntryWidget widget = userWidgets.computeIfAbsent(user.uniqueId(), ignored -> {
                EntryWidget created = new EntryWidget(contentWidth(usersContainer), () -> openSubject(new SubjectRef(SubjectType.USER, user.uniqueId())));
                usersDirectory.addRow("user:" + user.uniqueId(), "", List.of(created), 30, true, false);
                return created;
            });
            widget.setEntry(user.username().isBlank() ? shortId(user.uniqueId()) : user.username(), user.primaryGroup(), user.online() ? "Online" : user.directNodes() + " Direct", user.online() ? "nice" : "calm");
        }
        userCursor = page.nextCursor();
        usersHasMore = page.hasMore();
        usersState.setText(users.isEmpty() ? "No Users Found" : users.size() + " Users", users.isEmpty() ? "Players Appear After LuckPerms Has Seen Them" : "Select A Player To Review Permissions");
        usersState.visible = users.isEmpty();
        usersMore.visible = usersHasMore;
        usersDirectory.setRowVisibility("state", users.isEmpty());
        usersDirectory.setRowVisibility("more", usersHasMore);
        filterUsers();
        usersContainer.updateWidgetPositions();
        preparePreview();
    }

    private void loadGroups(boolean reset) {
        if (loadingGroups) {
            return;
        }
        loadingGroups = true;
        groupsState.setText("Loading Groups", reset ? "Reading Groups And Inheritance" : "Loading More Groups");
        groupsState.visible = true;
        String cursor = reset ? "" : groupCursor;
        complete(client.groups(new PageRequest(cursor, PAGE_SIZE, "")), page -> {
            loadingGroups = false;
            applyGroups(page, reset);
        }, "Could Not Load Groups", () -> {
            loadingGroups = false;
            groupsState.setText("Groups Unavailable", "Refresh To Try The Connection Again");
            groupsState.visible = true;
        });
    }

    private void applyGroups(GroupPage page, boolean reset) {
        groupsLoaded = true;
        if (reset) {
            groups.clear();
            groupCursor = "";
            groupWidgets.values().forEach(widget -> widget.visible = false);
        }
        for (GroupSummary group : page.items()) {
            int existing = findGroup(group.name());
            if (existing >= 0) {
                groups.set(existing, group);
            } else {
                groups.add(group);
            }
            EntryWidget widget = groupWidgets.computeIfAbsent(group.name(), ignored -> {
                EntryWidget created = new EntryWidget(contentWidth(groupsContainer), () -> openSubject(new SubjectRef(SubjectType.GROUP, group.name())));
                groupsDirectory.addRow("group:" + group.name(), "", List.of(created), 30, true, false);
                return created;
            });
            String detail = group.weight() == null ? group.directNodes() + " Direct" : "Weight " + group.weight();
            String display = group.displayName().isBlank() ? group.name() : group.displayName();
            String subtitle = display.equalsIgnoreCase(group.name()) ? detail : group.name() + " • " + detail;
            widget.setEntry(display, subtitle, group.directNodes() + " Nodes", "calm");
        }
        groupCursor = page.nextCursor();
        groupsHasMore = page.hasMore();
        groupsState.setText(groups.isEmpty() ? "No Groups Found" : groups.size() + " Groups", groups.isEmpty() ? "Create A Group To Build A Permission Structure" : "Select A Group To Review Permissions");
        groupsState.visible = groups.isEmpty();
        groupsMore.visible = groupsHasMore;
        groupsDirectory.setRowVisibility("state", groups.isEmpty());
        groupsDirectory.setRowVisibility("more", groupsHasMore);
        filterGroups();
        groupsContainer.updateWidgetPositions();
        preparePreview();
    }

    private void loadTracks() {
        tracksState.setText("Loading Tracks", "Reading Promotion Paths");
        tracksState.visible = true;
        complete(client.tracks(), this::applyTracks, "Could Not Load Tracks", () -> {
            tracksState.setText("Tracks Unavailable", "Refresh To Try The Connection Again");
            tracksState.visible = true;
        });
    }

    private void applyTracks(List<TrackDetail> values) {
        tracks.clear();
        tracks.addAll(values);
        trackWidgets.values().forEach(widget -> widget.visible = false);
        for (TrackDetail track : values) {
            EntryWidget widget = trackWidgets.computeIfAbsent(track.name(), ignored -> {
                EntryWidget created = new EntryWidget(contentWidth(tracksContainer), () -> openTrack(track));
                tracksDirectory.addRow("track:" + track.name(), "", List.of(created), 30, true, false);
                return created;
            });
            widget.setOnClick(() -> openTrack(track));
            widget.setEntry(track.name(), track.groups().size() + " Groups", String.join("  ›  ", track.groups()), "calm");
            widget.visible = true;
        }
        tracksState.setText(values.isEmpty() ? "No Tracks Found" : values.size() + " Tracks", values.isEmpty() ? "Create A Track For Ordered Promotions" : "Select A Track To Review Its Group Order");
        tracksState.visible = values.isEmpty();
        tracksDirectory.setRowVisibility("state", values.isEmpty());
        filterTracks();
        tracksContainer.updateWidgetPositions();
    }

    private void loadMoreUsers() {
        if (usersHasMore) {
            loadUsers(false);
        }
    }

    private void loadMoreGroups() {
        if (groupsHasMore) {
            loadGroups(false);
        }
    }

    private void filterUsers() {
        for (UserSummary user : users) {
            EntryWidget widget = userWidgets.get(user.uniqueId());
            if (widget != null) {
                widget.visible = matches(userFilter, user.username(), user.uniqueId(), user.primaryGroup(), user.prefix(), user.suffix());
                usersDirectory.setRowVisibility("user:" + user.uniqueId(), widget.visible);
            }
        }
        usersMore.visible = usersHasMore;
        usersDirectory.setRowVisibility("more", usersHasMore);
        usersContainer.updateWidgetPositions();
    }

    private void filterGroups() {
        for (GroupSummary group : groups) {
            EntryWidget widget = groupWidgets.get(group.name());
            if (widget != null) {
                widget.visible = matches(groupFilter, group.name(), group.displayName());
                groupsDirectory.setRowVisibility("group:" + group.name(), widget.visible);
            }
        }
        groupsMore.visible = groupsHasMore;
        groupsDirectory.setRowVisibility("more", groupsHasMore);
        groupsContainer.updateWidgetPositions();
    }

    private void filterTracks() {
        for (TrackDetail track : tracks) {
            EntryWidget widget = trackWidgets.get(track.name());
            if (widget != null) {
                widget.visible = matches(trackFilter, track.name(), String.join(" ", track.groups()));
                tracksDirectory.setRowVisibility("track:" + track.name(), widget.visible);
            }
        }
        tracksContainer.updateWidgetPositions();
    }

    private void openSubject(SubjectRef subject) {
        setStatus("Loading", "Opening " + subject.id(), "calm");
        complete(client.subject(subject), this::showSubject, "Could Not Load Player Or Group");
    }

    private void showSubject(SubjectDetail detail) {
        SubjectChange pending = subjectChanges.get(detail.subject());
        List<NodeData> originalNodes = pending == null ? detail.directNodes() : pending.nodes();
        TextInputWidget name = input("Display Name", pending == null ? detail.name() : pending.name(), 220);
        SelectorField primary = catalogField("Choose Main Group", "server:luckperms:group", () -> groupOptions(""),
            pending == null ? detail.primaryGroup() : pending.primaryGroup());
        TextInputWidget weight = input("Priority", value(pending == null ? detail.weight() : pending.weight()), 100);
        List<NodeEditor> nodeEditors = new ArrayList<>();
        Setting panel = subjectSetting(detail.subject().type());
        panel.clearRows();
        addRow(panel, "summary", "", "", 30, infoRow(displaySubject(detail), originalNodes.size() + " Current Permissions", "Saved"));
        if (detail.subject().type() == SubjectType.USER) {
            addRow(panel, "identity", "", "", 34, paired("Display Name", "The Name Players See", name,
                "Main Group", "The Player's Main Rank", primary, 0.5f));
        } else {
            addRow(panel, "identity", "", "", 34, paired("Display Name", "The Name Players See", name,
                "Priority", "Higher Groups Appear Above Lower Groups", weight, 0.72f));
        }
        for (int i = 0; i < originalNodes.size(); i++) {
            NodeData node = originalNodes.get(i);
            NodeEditor editor = new NodeEditor(node);
            nodeEditors.add(editor);
            String id = "permission:" + i;
            addRow(panel, id, "", "", 34, paired("Permission", permissionScope(node), editor.permission,
                "Value", "Allow: Grant. Deny: Block.", editor.enabled, 0.8f),
                rowAction("delete.png", "Remove Permission", () -> {
                    editor.removed = true;
                    panel.setRowVisibility(id, false);
                    subjectContainer(detail.subject().type()).updateWidgetPositions();
                }));
        }
        SelectorField newPermission = catalogField("Choose Permission", "server:luckperms:permission", () -> permissionOptions(""), "");
        ScrollSelectorWidget newValue = selector(List.of("Allow", "Deny"), ignored -> {});
        addRow(panel, "new-permission", "Add Permission", "Choose A Permission And Whether It Is Allowed", 20, newPermission, newValue,
            rowAction("add.png", "Add Permission", () -> {
                String permission = newPermission.value();
                if (permission.isBlank()) {
                    setStatus("Choose A Permission", "Select A Permission To Add", "calm");
                    return;
                }
                NodeEditor editor = new NodeEditor(new NodeData(UUID.randomUUID().toString(), NodeKind.PERMISSION, permission,
                    newValue.getSelectedIndex() == 0, null, Map.of(), null));
                nodeEditors.add(editor);
                String id = "permission:new:" + editor.original.id();
                addRow(panel, id, "", "", 34, paired("Permission", "Applies Everywhere", editor.permission,
                    "Value", "Allow: Grant. Deny: Block.", editor.enabled, 0.8f),
                    rowAction("delete.png", "Remove Permission", () -> {
                        editor.removed = true;
                        panel.setRowVisibility(id, false);
                        subjectContainer(detail.subject().type()).updateWidgetPositions();
                    }));
                subjectContainer(detail.subject().type()).updateWidgetPositions();
            }));
        Runnable apply = () -> {
            List<NodeData> nodes = nodeEditors.stream().filter(editor -> !editor.removed).map(NodeEditor::value).filter(node -> !node.key().isBlank()).toList();
            Integer parsedWeight = parseInteger(weight.getText());
            subjectChanges.put(detail.subject(), new SubjectChange(detail.subject(), detail.revision(), name.getText(), primary.value(), parsedWeight, nodes));
            setStatus("Ready To Save", displaySubject(detail), "nice");
            refreshDraftSummary();
        };
        addRow(panel, "actions", "", "", 30, actionRow(subjectContainer(detail.subject().type()), "Apply Changes", "Review Changes, Then Save From The Header", "checkmark.png", apply),
            actionButton(detail.subject().type() == SubjectType.USER ? "Remove Player" : "Remove Group", () -> stageDelete(detail.subject())));
        subjectContainer(detail.subject().type()).updateWidgetPositions();
    }

    private void openTrack(TrackDetail track) {
        TrackChange pending = trackChanges.get(track.name());
        List<String> order = new ArrayList<>(pending == null ? track.groups() : pending.groups());
        showTrackEditor(track, order);
    }

    private void showTrackEditor(TrackDetail track, List<String> order) {
        tracksDetail.clearRows();
        addRow(tracksDetail, "summary", "", "", 30, infoRow(track.name(), order.size() + " Groups", "Promotes From Top To Bottom"));
        for (int i = 0; i < order.size(); i++) {
            int index = i;
            String group = order.get(i);
            addRow(tracksDetail, "group:" + i, (i + 1) + ". " + group, index == 0 ? "Starting Group" : "Promotes After " + order.get(index - 1), 20,
                rowAction("up.png", "Move Up", () -> {
                    if (index > 0) {
                        String moved = order.remove(index);
                        order.add(index - 1, moved);
                        showTrackEditor(track, order);
                    }
                }),
                rowAction("down.png", "Move Down", () -> {
                    if (index < order.size() - 1) {
                        String moved = order.remove(index);
                        order.add(index + 1, moved);
                        showTrackEditor(track, order);
                    }
                }),
                rowAction("delete.png", "Remove Group", () -> {
                    order.remove(index);
                    showTrackEditor(track, order);
                }));
        }
        SelectorField group = catalogField("Choose Group", "server:luckperms:group", () -> groupOptions(""), "");
        addRow(tracksDetail, "add", "Add Group", "Choose The Next Group In This Promotion Path", 20, group,
            rowAction("add.png", "Add Group", () -> {
                String selected = group.value();
                if (!selected.isBlank() && !order.contains(selected)) {
                    order.add(selected);
                    showTrackEditor(track, order);
                }
            }));
        addRow(tracksDetail, "actions", "", "", 30,
            actionButton("Apply Changes", () -> {
                trackChanges.put(track.name(), new TrackChange(track.name(), track.revision(), List.copyOf(order)));
                setStatus("Ready To Save", track.name(), "nice");
                refreshDraftSummary();
            }),
            actionButton("Remove Track", () -> stageDelete(EntityType.TRACK, track.name())));
        tracksContainer.updateWidgetPositions();
    }

    private void addForActiveTab() {
        switch (activeMode) {
            case "users" -> showCreate(EntityType.USER);
            case "groups" -> showCreate(EntityType.GROUP);
            case "tracks" -> showCreate(EntityType.TRACK);
            case "network" -> openNetworkSelection();
            default -> showAddNode();
        }
    }

    private void showCreate(EntityType type) {
        TextInputWidget id = input(type == EntityType.USER ? "Player Name" : "Name", "", 220);
        Setting panel = entitySetting(type);
        panel.clearRows();
        addRow(panel, "summary", "", "", 30, infoRow("New " + entityName(type), "Enter A Name, Then Save", "Not Saved Yet"));
        addRow(panel, "name", type == EntityType.USER ? "Player Name" : "Name",
            type == EntityType.USER ? "Use The Player's Minecraft Name" : "Use A Short, Unique Name", 20, id);
        addRow(panel, "create", "", "", 30, actionButton(type == EntityType.USER ? "Add Player" : "Create " + entityName(type), () -> {
            String normalized = id.getText().trim();
            if (normalized.isBlank()) {
                setStatus(type == EntityType.USER ? "Player Required" : "Name Required", type == EntityType.USER ? "Enter A Player Name" : "Enter A Name", "calm");
                return;
            }
            EntityCreate create = new EntityCreate(type, normalized, type == EntityType.USER ? normalized : "");
            creates.put(entityKey(type, normalized), create);
            deletes.remove(entityKey(type, normalized));
            setStatus("Ready To Save", "New " + entityName(type), "nice");
            refreshDraftSummary();
        }));
        entityContainer(type).updateWidgetPositions();
    }

    private void showAddNode() {
        boolean[] allowed = {true};
        SelectorField target = choiceField("Choose Player Or Group", this::subjectChoices, "");
        SelectorField permission = catalogField("Choose Permission", "server:luckperms:permission", () -> permissionOptions(""), "");
        SelectorField scope = choiceField("Everywhere", this::scopeChoices, "everywhere");
        ScrollSelectorWidget value = selector(List.of("Allow", "Deny"), index -> allowed[0] = index == 0);
        List<AnimatedWidget> body = new ArrayList<>();
        body.add(infoRow("Add Permission", "Choose Who Receives It And Whether It Is Allowed", "Save When Ready"));
        body.add(paired("Player Or Group", "Choose An Existing Player Or Group", target,
            "Permission", "Choose From Permissions Reported By The Server", permission, 0.42f));
        body.add(paired("Value", "Allow: Grant The Permission. Deny: Block The Permission.", value,
            "Applies To", "Everywhere: Use On All Servers And Worlds. Server Or World: Limit This Permission.", scope, 0.24f));
        body.add(actions("Changes", "Review This Change, Then Save From The Header", actionButton("Add Permission", () -> {
            SubjectRef subject = selectedSubject(target);
            if (subject == null) {
                setStatus("Choose A Player Or Group", "Select Who Receives This Permission", "calm");
                return;
            }
            complete(client.subject(subject), detail -> {
                List<NodeData> nodes = new ArrayList<>(detail.directNodes());
                nodes.add(new NodeData(UUID.randomUUID().toString(), NodeKind.PERMISSION, permission.value(), allowed[0], null, selectedScope(scope), null));
                subjectChanges.put(subject, new SubjectChange(subject, detail.revision(), detail.name(), detail.primaryGroup(), detail.weight(), nodes));
                setStatus("Ready To Save", permission.value(), "nice");
                refreshDraftSummary();
            }, "Could Not Load Player Or Group");
        })));
        setRows(overviewAccess, body);
        overviewContainer.updateWidgetPositions();
    }

    private void openPreview() {
        if (!usersLoaded || !groupsLoaded) {
            setStatus("Loading Permission Catalog", "Players And Groups Will Appear Shortly", "calm");
            return;
        }
        Runnable[] previewAction = {() -> {}};
        SelectorField target = choiceField("Choose Player Or Group", this::subjectChoices, "");
        SelectorField permission = catalogField("Choose Permission", "server:luckperms:permission", () -> permissionOptions(""), "");
        SelectorField scope = choiceField("Everywhere", this::scopeChoices, "everywhere");
        MessageWidget result = new MessageWidget(360, "Permission Check", "Choose A Player Or Group And A Permission");
        List<AnimatedWidget> body = new ArrayList<>();
        body.add(infoRow("Effective Access", "See Why This Permission Is Allowed Or Denied", "Includes Unsaved Changes"));
        body.add(paired("Player Or Group", "Choose An Existing Player Or Group", target,
            "Permission", "Choose A Permission Reported By The Server", permission, 0.42f));
        body.add(titled("Applies To", "Everywhere: Check All Servers. Server: Check Only That Server. World: Check Only That World.", scope));
        body.add(result);
        body.add(actions("Explorer", "Resolve Shows Every Matching Direct Or Inherited Permission", actionButton("Resolve Access", () -> previewAction[0].run()), actionButton("Add Permission", this::showAddNode)));
        int fixedRows = body.size();
        previewAction[0] = () -> {
            SubjectRef subject = selectedSubject(target);
            if (subject == null || permission.value().isBlank()) {
                result.setText("Permission Check", "Choose A Player Or Group And A Permission");
                trim(body, fixedRows);
                setRows(overviewAccess, body);
                return;
            }
            long sequence = ++previewSequence;
            String requestId = Long.toString(sequence);
            PreviewRequest request = new PreviewRequest(requestId, subject, permission.value(), selectedScope(scope), draft());
            result.setText("Resolving", "Checking Effective Permission");
            complete(client.preview(request), preview -> {
                if (sequence != previewSequence || !requestId.equals(preview.requestId())) {
                    return;
                }
                applyPreview(result, preview, body, fixedRows);
            }, "Could Not Preview Permission");
        };
        setRows(overviewAccess, body);
        overviewContainer.updateWidgetPositions();
    }

    private void applyPreview(MessageWidget result, EffectivePreview preview, List<AnimatedWidget> body, int fixedRows) {
        trim(body, fixedRows);
        if (!preview.resolved()) {
            result.setText("Not Resolved", "No Matching Permission");
            body.add(emptyWorkspace("No Permission Path", "No Direct Or Inherited Permission Matched This Request"));
            setRows(overviewAccess, body);
            return;
        }
        String source = preview.matches().stream().filter(LuckPermsManagementContract.PreviewMatch::effective).findFirst().map(match -> match.source().id()).orElse("Direct");
        result.setText(preview.allowed() ? "Allowed" : "Denied", source + " • " + preview.matches().size() + " Matches");
        for (LuckPermsManagementContract.PreviewMatch match : preview.matches()) {
            String path = match.inheritancePath().isEmpty() ? "Direct" : String.join(" › ", match.inheritancePath());
            String state = match.node().value() ? "Allow" : "Deny";
            String appliesTo = formatContexts(match.node().contexts());
            String description = state + " • " + (appliesTo.isBlank() ? "Everywhere" : appliesTo) + " • " + path;
            body.add(infoRow(match.source().id(), description, match.effective() ? "Winning Match • " + match.explanation() : match.explanation()));
        }
        setRows(overviewAccess, body);
        overviewContainer.updateWidgetPositions();
    }

    private void save() {
        if (saving || !available) {
            return;
        }
        ChangeSet draft = draft();
        if (draft.subjects().isEmpty() && draft.tracks().isEmpty() && draft.creates().isEmpty() && draft.deletes().isEmpty()) {
            setStatus("No Changes", "Everything Is Saved", "calm");
            return;
        }
        saving = true;
        setStatus("Saving", "Applying Permission Changes", "calm");
        complete(networkClient.save(draft), this::applyDistributedSave, "Could Not Save Changes", () -> saving = false);
    }

    private void applyDistributedSave(DistributionResult result) {
        saving = false;
        if (!result.success()) {
            TargetResult failed = result.targets().stream().filter(target -> !target.success()).findFirst().orElse(null);
            String detail = failed == null ? "Changes Were Not Applied" : failed.name() + " • " + failed.message();
            setStatus("Review Required", detail, "calm");
            applyDistributionState(result);
            return;
        }
        subjectChanges.clear();
        trackChanges.clear();
        creates.clear();
        deletes.clear();
        operationId = UUID.randomUUID().toString();
        setStatus("Saved", result.targets().size() + " Servers Updated", "nice");
        applyDistributionState(result);
        refreshDraftSummary();
        refreshOverview();
        refreshActive();
    }

    private void applyDistributionState(DistributionResult result) {
        for (TargetResult target : result.targets()) {
            if (!target.success()) {
                setStatus("Some Servers Were Not Updated", target.name() + " • " + target.message(), "calm");
                return;
            }
        }
    }

    private ChangeSet draft() {
        return new ChangeSet(operationId, List.copyOf(subjectChanges.values()), List.copyOf(trackChanges.values()), List.copyOf(creates.values()), List.copyOf(deletes.values()));
    }

    private void stageDelete(SubjectRef subject) {
        stageDelete(subject.type() == SubjectType.USER ? EntityType.USER : EntityType.GROUP, subject.id());
        subjectChanges.remove(subject);
    }

    private void stageDelete(EntityType type, String id) {
        deletes.put(entityKey(type, id), new EntityDelete(type, id));
        creates.remove(entityKey(type, id));
        if (type == EntityType.TRACK) {
            trackChanges.remove(id);
        }
        setStatus("Ready To Remove", id + " Will Be Removed When You Save", "calm");
        refreshDraftSummary();
    }

    private void refreshInvalidated(LuckPermsManagementContract.Invalidation invalidation) {
        Set<EntityType> scopes = invalidation.scopes();
        if (scopes.isEmpty()) {
            refreshOverview();
            return;
        }
        if (scopes.contains(EntityType.USER) && "users".equals(activeMode)) {
            loadUsers(true);
        }
        if (scopes.contains(EntityType.GROUP) && "groups".equals(activeMode)) {
            loadGroups(true);
        }
        if (scopes.contains(EntityType.TRACK) && "tracks".equals(activeMode)) {
            loadTracks();
        }
        refreshOverview();
    }

    private void refreshNetwork() {
        networkRefreshAttempts = 0;
        complete(networkClient.snapshot(), this::applyNetwork, "Could Not Load Network");
    }

    private void openNetworkSelection() {
        if (!"network".equals(activeMode)) {
            tabsManager.setActiveTab(4);
            setActiveContainer(networkContainer);
            activeMode = "network";
        }
        refreshNetwork();
    }

    private void applyNetwork(Snapshot snapshot) {
        networkSnapshot = snapshot;
        networkGraph.applySnapshot(snapshot);
        networkGraph.setWidth(Math.max(220, networkContainer.getEffectiveWidth() - 10));
        networkContainer.updateWidgetPositions();
        long receiving = snapshot.targets().stream().filter(ReSyncLuckPermsNetworkClient.Target::selected).count();
        long ready = snapshot.targets().stream().filter(ReSyncLuckPermsNetworkClient.Target::available).count();
        boolean connecting = snapshot.targets().stream().anyMatch(target -> target.detail().contains("Connecting"));
        String title = snapshot.targets().isEmpty() ? "No Backend Servers"
            : connecting ? "Connecting Permission Servers"
            : ready < snapshot.targets().size() ? "Some Permission Servers Unavailable" : "Permission Delivery Ready";
        setStatus(title,
            snapshot.targets().isEmpty() ? "Add ReSync Backend Servers To This Network" : receiving + " Receiving • " + ready + " Ready", "calm");
        scheduleNetworkRefresh(snapshot);
    }

    private void scheduleNetworkRefresh(Snapshot snapshot) {
        boolean settling = snapshot.targets().stream().anyMatch(target -> target.detail().contains("Connecting")
            || target.connected() && !target.available());
        if (!settling) {
            networkRefreshAttempts = 0;
            return;
        }
        if (networkRefreshScheduled || networkRefreshAttempts >= 20) {
            return;
        }
        networkRefreshScheduled = true;
        networkRefreshAttempts++;
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() ->
            networkClient.snapshot().whenComplete((updated, error) -> ui(() -> {
                networkRefreshScheduled = false;
                if (error == null) {
                    applyNetwork(updated);
                }
            })));
    }

    @Override
    public void close() {
        ScreenManager.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        disposed = true;
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        super.removed();
    }

    private void setStatus(String title, String detail, String accent) {
        statusWidgets.forEach(status -> status.setStatus(title, detail, accent));
    }

    private <T> void complete(CompletableFuture<T> future, Consumer<T> success, String failure) {
        complete(future, success, failure, () -> {});
    }

    private <T> void complete(CompletableFuture<T> future, Consumer<T> success, String failure, Runnable failed) {
        future.whenComplete((value, error) -> ui(() -> {
            if (error != null) {
                failed.run();
                setStatus(failure, message(error), "danger");
                return;
            }
            success.accept(value);
        }));
    }

    private void ui(Runnable action) {
        ScreenManager.getInstance().execute(() -> {
            if (!disposed) {
                action.run();
            }
        });
    }

    private int contentWidth(Container container) {
        int available = Math.max(220, container.getEffectiveWidth() - 14);
        return width >= 980 ? Math.max(220, (available - 4) / 2) : available;
    }

    private MessageWidget emptyWorkspace(String title, String description) {
        return new MessageWidget(220, title, description);
    }

    private MessageWidget infoRow(String title, String description, String detail) {
        MessageWidget row = new MessageWidget(220, title, description);
        row.setHiddenText(detail);
        return row;
    }

    private TitledRowWidget titled(String title, String description, AnimatedWidget... widgets) {
        return new TitledRowWidget.Builder().title(title).description(description).padding(2).addWidget(widgets).size(220, 32).roundedCorners(false).build();
    }

    private TitledRowWidget paired(String firstTitle, String firstDescription, AnimatedWidget first, String secondTitle,
        String secondDescription, AnimatedWidget second, float firstShare) {
        return new TitledRowWidget.Builder().padding(2).fieldSpacing(2).minFieldWidth(90).percentageSplit(firstShare)
            .addField(firstTitle, firstDescription, first).addField(secondTitle, secondDescription, second)
            .size(220, 34).roundedCorners(false).build();
    }

    private TitledRowWidget actions(String title, String description, AnimatedWidget... buttons) {
        return titled(title, description, buttons);
    }

    private AnimatedButton actionButton(String title, Runnable action) {
        return new AnimatedButton.Builder().label(title).onClick(action).centered(false).size(120, 20).roundedCorners(false).build();
    }

    private ScrollSelectorWidget selector(List<String> options, Consumer<Integer> change) {
        return new ScrollSelectorWidget.Builder().options(options).selectedIndex(0).onChange(change).size(180, 20).roundedCorners(false).build();
    }

    private Setting subjectSetting(SubjectType type) {
        return type == SubjectType.USER ? usersDetail : groupsDetail;
    }

    private Setting entitySetting(EntityType type) {
        return switch (type) {
            case USER -> usersDetail;
            case GROUP -> groupsDetail;
            case TRACK -> tracksDetail;
        };
    }

    private Container subjectContainer(SubjectType type) {
        return type == SubjectType.USER ? usersContainer : groupsContainer;
    }

    private Container entityContainer(EntityType type) {
        return switch (type) {
            case USER -> usersContainer;
            case GROUP -> groupsContainer;
            case TRACK -> tracksContainer;
        };
    }

    private void refreshDraftSummary() {
        if (networkStatusWidget != null) {
            ChangeSet current = draft();
            int changes = current.subjects().size() + current.tracks().size() + current.creates().size() + current.deletes().size();
            networkStatusWidget.setStatus(changes == 0 ? "Everything Saved" : changes + " Unsaved Changes",
                changes == 0 ? "Permission Delivery Is Ready" : "Use Save When You Are Ready", "calm");
        }
    }

    private void setRows(Setting setting, List<? extends AnimatedWidget> widgets) {
        setting.clearRows();
        for (int i = 0; i < widgets.size(); i++) {
            addRow(setting, "row:" + i, "", "", Math.max(20, widgets.get(i).getHeight()), widgets.get(i));
        }
    }

    private void addRow(Setting setting, String id, String label, String description, int height, AnimatedWidget... widgets) {
        setting.addRow(id, label, description, new ArrayList<>(List.of(widgets)), height, true, false);
    }

    private static void trim(List<AnimatedWidget> widgets, int size) {
        while (widgets.size() > size) {
            widgets.removeLast();
        }
    }

    private MountableButtonWidget actionRow(Container container, String title, String description, String icon, Runnable action) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(description).iconPath(icon).addButton(rowAction(icon, title, action)).build();
        row.setSize(contentWidth(container), 30);
        row.setAccent(ThemeManager.getDefaultAccent());
        return row;
    }

    private SquareButtonWidget rowAction(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder().imagePath(icon).hint(hint).hintDelay(0.15f).onClick(action).accentType(ThemeManager.getDefaultAccent()).animateElevation(false).size(18, 18).build();
    }

    private int findUser(String id) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).uniqueId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int findGroup(String name) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static TextInputWidget input(String placeholder, String value, int width) {
        return new TextInputWidget.Builder().size(width, 20).placeholder(placeholder).text(value == null ? "" : value).build();
    }

    private SelectorField catalogField(String placeholder, String source, Supplier<List<String>> fallback, String selected) {
        return new SelectorField(placeholder, selected, () -> catalogChoices(source, fallback != null ? fallback.get() : List.of()), source);
    }

    private SelectorField choiceField(String placeholder, Supplier<List<SelectorChoice>> choices, String selected) {
        return new SelectorField(placeholder, selected, choices, "");
    }

    private List<SelectorChoice> catalogChoices(String source, List<String> fallback) {
        OptionCatalogLoader.Snapshot snapshot = OptionCatalogLoader.snapshot(client.serverId(), source);
        Map<String, SelectorChoice> choices = new LinkedHashMap<>();
        for (OptionCatalogItem item : snapshot.items()) {
            if (item.getValue() == null || item.getValue().isBlank()) {
                continue;
            }
            choices.putIfAbsent(item.getValue(), new SelectorChoice(item.getValue(), item.getLabel(), item.getDescription(), item.getIcon(),
                String.join(" ", item.getValue(), item.getLabel(), item.getDescription(), item.getGroup())));
        }
        snapshot.values().forEach(value -> choices.putIfAbsent(value, new SelectorChoice(value, value, "", "", value)));
        if (fallback != null) {
            fallback.forEach(value -> choices.putIfAbsent(value, new SelectorChoice(value, value, "", "", value)));
        }
        return List.copyOf(choices.values());
    }

    private List<String> permissionOptions(String selected) {
        Set<String> values = new LinkedHashSet<>(OptionCatalogLoader.snapshot(client.serverId(), "server:luckperms:permission").values());
        subjectChanges.values().stream().flatMap(change -> change.nodes().stream()).map(NodeData::key).filter(value -> !value.isBlank()).forEach(values::add);
        if (selected != null && !selected.isBlank()) {
            values.add(selected);
        }
        return List.copyOf(values);
    }

    private List<String> groupOptions(String selected) {
        Set<String> values = new LinkedHashSet<>(OptionCatalogLoader.snapshot(client.serverId(), "server:luckperms:group").values());
        groups.stream().map(GroupSummary::name).forEach(values::add);
        if (selected != null && !selected.isBlank()) {
            values.add(selected);
        }
        return List.copyOf(values);
    }

    private List<SelectorChoice> subjectChoices() {
        List<SelectorChoice> choices = new ArrayList<>();
        users.stream().sorted(Comparator.comparing(user -> user.username().isBlank() ? user.uniqueId() : user.username(), String.CASE_INSENSITIVE_ORDER))
            .map(user -> {
                String name = user.username().isBlank() ? shortId(user.uniqueId()) : user.username();
                return new SelectorChoice("user:" + user.uniqueId(), "Player • " + name, user.primaryGroup(), "steve.png",
                    String.join(" ", name, user.uniqueId(), user.primaryGroup(), user.prefix(), user.suffix()));
            }).forEach(choices::add);
        groups.stream().sorted(Comparator.comparing(GroupSummary::name, String.CASE_INSENSITIVE_ORDER))
            .map(group -> new SelectorChoice("group:" + group.name(), "Group • " + (group.displayName().isBlank() ? group.name() : group.displayName()),
                group.name(), "manager.png", String.join(" ", group.name(), group.displayName()))).forEach(choices::add);
        return List.copyOf(choices);
    }

    private SubjectRef selectedSubject(SelectorField field) {
        String value = field.value();
        if (value.startsWith("group:")) {
            return new SubjectRef(SubjectType.GROUP, value.substring("group:".length()));
        }
        if (value.startsWith("user:")) {
            return new SubjectRef(SubjectType.USER, value.substring("user:".length()));
        }
        return null;
    }

    private List<SelectorChoice> scopeChoices() {
        List<SelectorChoice> choices = new ArrayList<>();
        choices.add(new SelectorChoice("everywhere", "Everywhere", "Use On Every Server And World", "network.png", "all global"));
        if (networkSnapshot != null) {
            networkSnapshot.targets().stream().sorted(Comparator.comparing(ReSyncLuckPermsNetworkClient.Target::name, String.CASE_INSENSITIVE_ORDER))
                .map(target -> new SelectorChoice("server:" + target.name(), "Server • " + target.name(), "Only On This Server", "server.png",
                    String.join(" ", target.name(), target.instanceId(), "server"))).forEach(choices::add);
        }
        OptionCatalogLoader.Snapshot worlds = OptionCatalogLoader.snapshot(client.serverId(), "server:minecraft:world");
        Map<String, OptionCatalogItem> richWorlds = new LinkedHashMap<>();
        worlds.items().forEach(item -> richWorlds.putIfAbsent(item.getValue(), item));
        worlds.values().stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).map(value -> {
            OptionCatalogItem item = richWorlds.get(value);
            String label = item == null ? value : item.getLabel();
            String description = item == null ? "Only In This World" : item.getDescription();
            String icon = item == null ? "world.png" : item.getIcon();
            return new SelectorChoice("world:" + value, "World • " + label, description, icon, String.join(" ", value, label, "world"));
        }).forEach(choices::add);
        return List.copyOf(choices);
    }

    private static Map<String, List<String>> selectedScope(SelectorField field) {
        String value = field.value();
        if (value.startsWith("server:")) {
            return Map.of("server", List.of(value.substring("server:".length())));
        }
        if (value.startsWith("world:")) {
            return Map.of("world", List.of(value.substring("world:".length())));
        }
        return Map.of();
    }

    private static String entityName(EntityType type) {
        return switch (type) {
            case USER -> "Player";
            case GROUP -> "Group";
            case TRACK -> "Track";
        };
    }

    private static String displaySubject(SubjectDetail detail) {
        if (detail.subject().type() == SubjectType.GROUP) {
            return detail.name().isBlank() ? detail.subject().id() : detail.name();
        }
        return detail.name().isBlank() ? shortId(detail.subject().id()) : detail.name();
    }

    private static String permissionScope(NodeData node) {
        String appliesTo = formatContexts(node.contexts());
        return (node.value() ? "Allowed" : "Denied") + " • " + (appliesTo.isBlank() ? "Everywhere" : appliesTo);
    }

    private static boolean matches(String query, String... values) {
        if (query.isBlank()) {
            return true;
        }
        for (String value : values) {
            if (normalize(value).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String shortId(String value) {
        return value == null || value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String value(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatContexts(Map<String, List<String>> contexts) {
        List<String> values = new ArrayList<>();
        contexts.forEach((key, entries) -> values.add(key + "=" + String.join(",", entries)));
        return String.join("; ", values);
    }

    private static String entityKey(EntityType type, String id) {
        return type.name() + ":" + id;
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        boolean capitalize = true;
        for (char character : lower.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? "Permission Request Failed" : current.getMessage();
    }

    private record SelectorChoice(String value, String label, String description, String icon, String search) {
        private SelectorChoice {
            value = value == null ? "" : value;
            label = label == null || label.isBlank() ? value : label;
            description = description == null ? "" : description;
            icon = icon == null ? "" : icon;
            search = search == null ? "" : search;
        }
    }

    private final class SelectorField extends AnimatedButton {
        private final String placeholder;
        private final Supplier<List<SelectorChoice>> choices;
        private final String catalogSource;
        private String value;

        private SelectorField(String placeholder, String value, Supplier<List<SelectorChoice>> choices, String catalogSource) {
            super(0, 0, 220, 20, "");
            this.placeholder = placeholder;
            this.choices = choices;
            this.catalogSource = catalogSource;
            centered = false;
            roundedCorners = false;
            animateElevation = false;
            setAction(this::openSelector);
            setValue(value);
        }

        private String value() {
            return value;
        }

        private void setValue(String value) {
            this.value = value == null ? "" : value;
            String label = choices.get().stream().filter(choice -> choice.value().equals(this.value)).map(SelectorChoice::label).findFirst()
                .orElse(this.value.isBlank() ? placeholder : this.value);
            setMessage(label);
        }

        private void openSelector() {
            Screen overlay = ScreenManager.getInstance().getDesktopUiOverlay();
            Screen host = overlay != null ? overlay : LuckPermsDashboardScreen.this;
            ItemSelectorWidget[] reference = new ItemSelectorWidget[1];
            ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(host)
                .size(Math.clamp(Math.max(260, getWidth()), 260, 420), Math.clamp(LuckPermsDashboardScreen.this.height - 100, 180, 320))
                .entryHeight(24)
                .searchPlaceholder("Search")
                .emptyMessage("No Matching Options")
                .dismissOnSelect(true)
                .onClose(() -> {
                    if (reference[0] != null) {
                        host.remove(reference[0]);
                    }
                });
            if (catalogSource.isBlank()) {
                for (SelectorChoice choice : choices.get()) {
                    builder.addItem(choice.label(), choice.icon(), choice.description(), choice.search(), () -> setValue(choice.value()));
                }
            } else {
                builder.asyncItems(OptionCatalogSelector.refreshAction(client.serverId(), catalogSource),
                    () -> OptionCatalogSelector.snapshot(client.serverId(), catalogSource, Map.of(),
                        () -> choices.get().stream().map(SelectorChoice::value).toList(), this::value, this::setValue, "No Matching Options"));
            }
            reference[0] = builder.build();
            host.addDrawableChild(reference[0]);
            reference[0].show(getX(), getY() + getHeight());
        }
    }

    private static class MessageWidget extends MountableButtonWidget {
        MessageWidget(int width, String title, String detail) {
            super(title, "", detail, new CopyOnWriteArrayList<>(), null);
            setSize(width, 30);
            animateElevation = false;
            elevateOnFocused = false;
            enableHoverColors = false;
            roundedCorners = false;
            active = true;
        }

        void setText(String title, String detail) {
            setName(title);
            setDescription(detail);
        }

        String title() {
            return name;
        }
    }

    private static final class MetricWidget extends MessageWidget {
        private final String label;

        MetricWidget(int width, String label) {
            super(width, label, "Loading");
            this.label = label;
        }

        void setValue(String value) {
            setText(label, value);
        }
    }

    private static final class AuditWidget extends MessageWidget {
        AuditWidget(int width) {
            super(width, "", "");
        }

        void setEntry(LuckPermsManagementContract.AuditEntry entry) {
            String actor = entry.actor().isBlank() ? "Server" : entry.actor();
            setText(LuckPermsDashboardScreen.title(entry.action()) + " • " + entry.target(), actor + " • " + TIME.format(Instant.ofEpochMilli(entry.changedAt())));
        }
    }

    private static final class StatusWidget extends MessageWidget {
        StatusWidget(int width) {
            super(width, "Connecting", "Opening Permission Management");
        }

        void setStatus(String title, String detail, String accent) {
            setText(title, detail);
            accentType = ThemeManager.getDefaultAccent();
            active = true;
        }
    }

    private static final class EntryWidget extends MessageWidget {
        EntryWidget(int width, Runnable action) {
            super(width, "", "");
            setOnClick(action);
            selectable = true;
            enableHoverColors = true;
            active = true;
        }

        void setEntry(String title, String detail, String badge, String accent) {
            setName(title);
            setDescription(detail);
            setHiddenText(badge);
            accentType = ThemeManager.getDefaultAccent();
            visible = true;
        }
    }

    private static final class LoadMoreWidget extends MessageWidget {
        LoadMoreWidget(int width, Runnable action) {
            super(width, "Load More", "Continue Browsing");
            setOnClick(action);
            roundedCorners = false;
            selectable = true;
            enableHoverColors = true;
            active = true;
        }
    }

    private final class NodeEditor {
        private final NodeData original;
        private final SelectorField permission;
        private final ScrollSelectorWidget enabled;
        private boolean removed;

        NodeEditor(NodeData original) {
            this.original = original;
            permission = catalogField("Choose Permission", "server:luckperms:permission", () -> permissionOptions(original.key()), original.key());
            enabled = new ScrollSelectorWidget.Builder().options(List.of("Allow", "Deny")).selectedIndex(original.value() ? 0 : 1).size(90, 20).roundedCorners(false).build();
        }

        NodeData value() {
            return new NodeData(original.id(), original.kind(), permission.value(), enabled.getSelectedIndex() == 0, original.priority(), original.contexts(), original.expiresAt());
        }
    }
}
