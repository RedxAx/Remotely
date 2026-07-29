package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.config.SettingsScreenFactory;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.discord.DiscordRpcBridge;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkCreationMember;
import redxax.oxy.remotely.network.NetworkCreationRequest;
import redxax.oxy.remotely.network.NetworkHostScope;
import redxax.oxy.remotely.network.NetworkGroupAttachmentTransaction;
import redxax.oxy.remotely.network.NetworkJob;
import redxax.oxy.remotely.network.NetworkJobStatus;
import redxax.oxy.remotely.network.NetworkJobType;
import redxax.oxy.remotely.network.NetworkLifecycleOperation;
import redxax.oxy.remotely.network.NetworkLifecycleStatus;
import redxax.oxy.remotely.network.NetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkMemberRole;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.ui.widgets.ReactorPlanWidget;
import redxax.oxy.remotely.ui.widgets.management.PlayerDataPopup;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.restudio.AuthStateListener;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.SessionState;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.ui.screens.auth.ReStudioLoginScreen;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;
import restudio.rebase.ui.screens.feedback.FeedbackBrowserScreen;
import restudio.rebase.ui.screens.notification.InboxScreen;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LocalServerControllerModels;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.util.Executors;
import restudio.rebase.util.RebaseLogger;
import restudio.rebase.util.ssh.SSHManager;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopBounds;
import restudio.rescreen.ui.desktop.DesktopGroup;
import restudio.rescreen.ui.desktop.DesktopGroupWidget;
import restudio.rescreen.ui.desktop.DesktopIconWidget;
import restudio.rescreen.ui.desktop.DesktopMetrics;
import restudio.rescreen.ui.desktop.DesktopShellScreen;
import restudio.rescreen.ui.desktop.DesktopTaskbarHelper;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.ui.rescreen.layout.FreeLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.BrowserUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerManagerScreen extends DesktopShellScreen implements AuthStateListener {
    private final RemotelyClient remotelyClient;
    private PopupWidget createChoicePopup;
    private PopupWidget networkCreationPopup;
    private PopupWidget addServerPopup;
    private PopupWidget remoteHostPopup;
    private TextInputWidget remoteHostNameInput;
    private TextInputWidget remoteHostUserInput;
    private TextInputWidget remoteHostIpInput;
    private TextInputWidget remoteHostPortInput;
    private TextInputWidget remoteHostPasswordInput;
    private TextInputWidget remoteHostSftpPasswordInput;
    private TextInputWidget remoteHostKeyPathInput;
    private TextInputWidget remoteHostKeyPassphraseInput;
    private TabSwitchWidget remoteHostTypeSwitch;
    private TabSwitchWidget remoteHostAuthModeSwitch;
    private AnimatedButton remoteHostConfirmButton;
    private AnimatedButton remoteHostDeleteButton;
    private final Object parent;
    private IconButton userButton;
    private boolean serverManagerContextMenuPressed;
    private boolean networkOperationInFlight;

    private static final String ROW_REMOTE_HOST_TYPE = "remoteHostType";
    private static final String ROW_REMOTE_HOST_USER = "remoteHostUser";
    private static final String ROW_REMOTE_HOST_IP = "remoteHostIp";
    private static final String ROW_REMOTE_HOST_PORT = "remoteHostPort";
    private static final String ROW_REMOTE_HOST_PASSWORD = "remoteHostPassword";
    private static final String ROW_REMOTE_HOST_SFTP_PASSWORD = "remoteHostSftpPassword";
    private static final String ROW_REMOTE_HOST_AUTH_MODE = "remoteHostAuthMode";
    private static final String ROW_REMOTE_HOST_KEY_PATH = "remoteHostKeyPath";
    private static final String ROW_REMOTE_HOST_PASSPHRASE = "remoteHostPassphrase";

    private static Identifier unknown, serverIcon, paper, vanilla, fabric, forge, neoforge, waterfall, velocity, leaf, quilt, spigot, bukkit, purpur;
    private InstanceManager instanceManager;
    private final List<Instance> restudioInstances = new CopyOnWriteArrayList<>();
    private final Map<String, ServerModels.ClientServerView> restudioServerViews = new HashMap<>();
    private final ServerIconManager iconManager;
    private boolean initializedOnce;
    private final AtomicInteger remoteHostSelectionToken = new AtomicInteger();
    private static final long PTERO_STATE_POLL_MS = 30_000L;
    private long lastPteroStatePollMs;
    private boolean pteroStatePollInFlight;
    private Container noServersOverlay;
    private boolean noServersOverlayVisible;
    private IconMessage localNoServersIcon;
    private IconMessage reactorsNoServersIcon;
    private IconMessage pteroNoServersIcon;
    private IconButton reactorInfo;
    private IconButton pteroInfo;
    private final List<ReactorPlanWidget> reactorPlanCards = new ArrayList<>();
    private final List<ServerModels.Plan> reactorPlans = new ArrayList<>();
    private String selectedReactorPlanName;
    private boolean reactorPlanSelectionVisible;
    private static final int REACTOR_PLAN_CARD_GAP = 14;
    private static final int REACTOR_PLAN_CARD_SIDE_MARGIN = 14;
    private long lastPersistentLocalProcessPollMs;
    private volatile boolean persistentLocalProcessPollInFlight;
    private static final long PERSISTENT_LOCAL_PROCESS_POLL_MS = 2000;
    private final Runnable instanceChangeListener = this::queueServerRefresh;
    private final Consumer<List<NetworkDefinition>> networkChangeListener = networks -> queueServerRefresh();
    private final Consumer<NetworkRuntimeSnapshot> runtimeChangeListener = this::queueRuntimeRefresh;
    private final AtomicBoolean serverRefreshQueued = new AtomicBoolean();
    private final AtomicBoolean runtimeRefreshQueued = new AtomicBoolean();
    private final Map<String, NetworkRuntimeSnapshot> pendingRuntimeSnapshots = new ConcurrentHashMap<>();
    private List<DesktopGroup> instanceGroups = new ArrayList<>();
    private final Set<String> pendingNetworkMembershipInstances = new HashSet<>();
    private boolean instanceChangeListenerRegistered;
    private boolean networkChangeListenerRegistered;
    private boolean runtimeChangeListenerRegistered;
    private volatile boolean reactiveRefreshEnabled;

    private static final class NetworkCreationDraft {
        private final Instance proxy;
        private final List<Instance> backends;
        private String name;

        private NetworkCreationDraft(Instance proxy, Collection<Instance> backends) {
            this.proxy = proxy;
            this.backends = new ArrayList<>(backends);
            this.name = proxy.getName();
        }
    }

    public ServerManagerScreen(Object parent, RemotelyClient remotelyClient) {
        super();
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.iconManager = new ServerIconManager(remotelyDir);
        this.preserveStateOnDisplay = true;
    }

    public String getDesktopAppId() {
        return "server-manager";
    }

    public String getDesktopAppTitle() {
        return "Server Manager";
    }

    public String getDesktopAppIconPath() {
        return "remotely.png";
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return Config.desktopMode;
    }

    @Override
    public void init() {
        super.init();
        reactiveRefreshEnabled = true;
        DiscordRpcBridge.setManagerActive();
        if (initializedOnce) {
            ReStudio.getInstance().addListener(this);
            registerInstanceChangeListener();
            registerNetworkChangeListener();
            registerRuntimeChangeListener();
            initNoServersOverlay();
            if (Config.desktopMode && taskbarHelper != null) {
                taskbarHelper.attach();
            }
            refreshAccountButton();
            if (ReStudio.getInstance().isAuthenticated()) {
                maybeAddReStudioTab();
            } else {
                removeReStudioTab();
            }
            updatePositions();
            return;
        }
        this.instanceManager = Rebase.get().getInstanceManager();
        registerInstanceChangeListener();
        registerNetworkChangeListener();
        registerRuntimeChangeListener();
        reloadInstancesSmartly();
        loadIcons();
        createPopups();
        ReStudio.getInstance().addListener(this);

        Map<String, Identifier> defaultIcons = new HashMap<>();
        defaultIcons.put("vanilla", vanilla);
        defaultIcons.put("fabric", fabric);
        defaultIcons.put("forge", forge);
        defaultIcons.put("neoforge", neoforge);
        defaultIcons.put("paper", paper);
        defaultIcons.put("purpur", purpur);
        defaultIcons.put("quilt", quilt);
        defaultIcons.put("spigot", spigot);
        defaultIcons.put("bukkit", bukkit);
        defaultIcons.put("leaf", leaf);
        defaultIcons.put("velocity", velocity);
        defaultIcons.put("waterfall", waterfall);
        defaultIcons.put("unknown", unknown);
        iconManager.setDefaultIcons(defaultIcons);

        int taskbarHeight = DesktopMetrics.DEFAULT_TASKBAR_HEIGHT;
        String displayName = ReStudio.getInstance().getDisplayName();
        if (displayName == null || displayName.isBlank()) displayName = ReStudio.getInstance().getUsername();
        if (displayName == null || displayName.isBlank()) displayName = ReStudio.getInstance().getEmail();
        if (displayName == null) displayName = "Sign In";

        userButton = new IconButton.Builder()
            .imagePath("steve.png")
            .label(displayName)
            .onClick(this::onAccountButtonClick)
            .size(18, 18)
            .autoWidthOnTextChange(true)
            .build();

        ReStudio.getInstance().loadAvatarId().thenAccept(id -> ScreenManager.getInstance().execute(() -> {
            if (id != null && userButton != null) {
                userButton.setGeneratedIcon(id);
            }
        }));

        IconButton terminalButton = new IconButton.Builder()
            .imagePath("terminal.png")
            .hint("Terminal")
            .onClick(() -> remotelyClient.openMultiTerminal(this))
            .size(18, 18)
            .autoWidthOnTextChange(true)
            .build();

        IconButton fileExplorerButton = new IconButton.Builder()
            .imagePath("explorer.png")
            .hint("File Explorer")
            .onClick(this::openFileExplorer)
            .size(18, 18)
            .autoWidthOnTextChange(true)
            .build();

        IconButton settingsButton = new IconButton.Builder()
            .imagePath("remotely.png")
            .hint("Settings")
            .onClick(() -> client.setScreen(SettingsScreenFactory.createGlobalSettingsScreen(this, (RemotelyConfigManager) Rebase.get().getConfigManager())))
            .size(18, 18)
            .autoWidthOnTextChange(true)
            .build();

        setupDesktopTaskbar(taskbarHeight);
        header()
            .addLeft(terminalButton)
            .addLeft(fileExplorerButton)
            .addLeft(settingsButton)
            .addLeft(userButton)
            .build();

        if (Config.desktopMode) {
            taskbarHelper = new DesktopTaskbarHelper(this, header(), header().leftButtons.size(), () -> desktopBounds().taskbarTabs().x());
            taskbarHelper.pinApp("server-details", terminalButton, () -> remotelyClient.openMultiTerminal(this), "Terminal");
            taskbarHelper.pinApp("file-explorer", fileExplorerButton, this::openFileExplorer, "File Explorer");
            taskbarHelper.pinApp("global-settings", settingsButton, () -> client.setScreen(SettingsScreenFactory.createGlobalSettingsScreen(this, (RemotelyConfigManager) Rebase.get().getConfigManager())), "Settings");
            taskbarHelper.attach();
        } else {
            taskbarHelper = null;
        }

        TabsManager.Builder tabsBuilder = tabs().builder()
            .rightToLeft(true)
            .allowAdd(true)
            .allowRename(false).allowReorder(false).allowClose(false)
            .onPlusButtonClicked(() -> openRemoteHostPopup(false))
            .onTabSelected(this::onHostTabSelected)
            .onTabRenamed(this::onHostTabRenamed);
        layoutDesktopTabs(tabsBuilder);
        tabsBuilder.build();

        DesktopBounds.Bounds contentBounds = desktopBounds().content();
        Container desktopContainer = createContainer("desktop", contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height());
        DesktopLayout localLayout = new DesktopLayout();
        localLayout.setOnReorder(() -> saveServerOrder(desktopContainer, null));
        configureNetworkDrop(localLayout);
        desktopContainer.layout(localLayout).backgroundDrawing(false).enableSelecting(true).enableDoubleClick(false).disableScissorRegion(true).enableDoubleClick(false);

        setActiveContainer(desktopContainer);
        initNoServersOverlay();
        populateHostTabs();
        applyDesktopContentBounds();
        updatePositions();
        initializedOnce = true;
    }

    protected void setupNoServersOverlay(Container overlayContainer) {
        localNoServersIcon = new IconMessage(width / 2 - 32, height / 4, 64, 64, "Welcome To Remotely!\nI Guess We're Locally Now...\nClick Create To Start!\n\n\n Quick Tips:\nMiddle Click To Close Tabs\nSign In To Report Bugs & Give Feedback\nThere's A Very Powerfull Desktop Mode In The Settings!", "remotely.png");
        reactorsNoServersIcon = new IconMessage(width / 2 - 32, height / 4, 64, 64, "Reactor By ReStudio\nHigh-End & Affordable Hosting For Everyone.\nOrder And Control Your Server Right Here & Now!", "Reactor.png");
        pteroNoServersIcon = new IconMessage(width / 2 - 32, height / 4, 64, 64, "Pterodactyl Host\nLoading Servers...\nOpen Your Panel If No Servers Appear.", "server.png");
        reactorInfo = new IconButton.Builder().size(300, 18).label("Learn More Here").imagePath("external").autoWidthOnTextChange(true).onClick(() -> BrowserUtils.openBrowser("https://restudiomc.net/hosting")).build();
        reactorInfo.setX(width / 2 - (reactorInfo.getWidth() / 2));
        reactorInfo.setY(reactorsNoServersIcon.getY() + reactorsNoServersIcon.getHeight() + (12 * 5));
        pteroInfo = new IconButton.Builder().size(300, 18).label("Open Panel").imagePath("external").autoWidthOnTextChange(true).onClick(this::openActivePteroPanel).build();
        pteroInfo.setX(width / 2 - (pteroInfo.getWidth() / 2));
        pteroInfo.setY(pteroNoServersIcon.getY() + pteroNoServersIcon.getHeight() + (12 * 5));

        noServersOverlay.addWidget(localNoServersIcon);
        noServersOverlay.addWidget(reactorsNoServersIcon);
        noServersOverlay.addWidget(pteroNoServersIcon);
        noServersOverlay.addWidget(reactorInfo);
        noServersOverlay.addWidget(pteroInfo);
    }

    private void ensureReactorPlanSelectionCardsCreated() {
        if (!reactorPlanCards.isEmpty()) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            ReactorPlanWidget card = new ReactorPlanWidget(0, 0, 232, 132, null);
            card.setVisible(false);
            card.setActive(false);
            final int planIndex = i;
            card.setOnAction(() -> {
                if (!reactorPlanSelectionVisible) {
                    return;
                }
                if (planIndex >= reactorPlans.size()) {
                    return;
                }
                onReactorPlanSelected(reactorPlans.get(planIndex));
            });
            reactorPlanCards.add(card);
            addDrawableChild(card);
        }
    }

    private void initNoServersOverlay() {
        if (noServersOverlay != null) {
            return;
        }
        DesktopBounds.Bounds contentBounds = desktopBounds().content();
        noServersOverlay = createContainer("server_manager_no_servers_overlay", contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height());
        noServersOverlay.layout(new FreeLayout()).columns(1).padding(4).scrolling(false).enableSelecting(false).backgroundDrawing(false).disableScissorRegion(true);
        noServersOverlay.setVisible(false);
        noServersOverlay.setActive(false);
        setupNoServersOverlay(noServersOverlay);
        addDrawableChild(noServersOverlay);
    }

    private void updateNoServersOverlayVisibility(boolean visible) {
        if (noServersOverlay == null) {
            return;
        }

        Object tabData = (tabs().getActiveTab() != null) ? tabs().getActiveTab().getData() : null;
        updateOverlayIcons(tabData);

        if (noServersOverlayVisible == visible) {
            return;
        }
        noServersOverlayVisible = visible;
        noServersOverlay.setVisible(visible);
        noServersOverlay.setActive(visible);
    }

    private void showUserMenu() {
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this)
            .addHeaderButton("info.png", () -> ScreenManager.getInstance().setScreen(new InboxScreen(this)), "Inbox")
            .addHeaderButton("report.png", () -> ScreenManager.getInstance().setScreen(new FeedbackBrowserScreen(this, "Remotely")), "Reports And Feedback")
            .addHeaderButton("close.png", () -> ReStudio.getInstance().logoutFromWorkOs(), "Log Out", ThemeManager.getAccent("danger"));

        showContextMenu(userButton.getX(), desktopBounds().taskbar().y(), builder);
    }

    private boolean isMouseOverServerManagerContextMenu(double mouseX, double mouseY) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i) instanceof ContextMenuWidget menu && menu.isVisible() && menu.isOpen() && menu.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void onAccountButtonClick() {
        if (ReStudio.getInstance().isAuthenticated()) {
            showUserMenu();
            return;
        }
        openReStudioLogin();
    }

    private void updateOverlayIcons(Object tabData) {
        boolean isReactors = "RESTUDIO_MARKER".equals(tabData);
        boolean isPtero = tabData instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType());
        if (localNoServersIcon != null) localNoServersIcon.setVisible(!isReactors && !isPtero);
        if (reactorsNoServersIcon != null) reactorsNoServersIcon.setVisible(isReactors);
        if (reactorInfo != null) reactorInfo.setVisible(isReactors);
        if (pteroNoServersIcon != null) pteroNoServersIcon.setVisible(isPtero);
        if (pteroInfo != null) pteroInfo.setVisible(isPtero);
    }

    @Override
    public void onLogin(String email) {
        refreshAccountButton();
        if (tabs().getActiveTab() != null && "RESTUDIO_MARKER".equals(tabs().getActiveTab().getData())) {
            fetchReStudioServers();
        } else {
            maybeAddReStudioTab();
        }
        new Notification.Builder().message("Welcome back!").description("Email: " + (!Config.obfuscate ? "" : "§k") + email).type(Notification.Type.SUCCESS).build();
    }

    @Override
    public void onLogout() {
        ScreenManager.getInstance().execute(() -> {
            refreshAccountButton();
            restudioInstances.clear();
            restudioServerViews.clear();
            if (tabs().getActiveTab() != null && "RESTUDIO_MARKER".equals(tabs().getActiveTab().getData())) {
                loadServersForCurrentTab();
            }
        });
        new Notification.Builder().message("Bye Bye").type(Notification.Type.INFO).build();

    }

    @Override
    public void onSessionExpired() {
        ScreenManager.getInstance().execute(() -> {
            refreshAccountButton();
            restudioInstances.clear();
            restudioServerViews.clear();
            if (tabs().getActiveTab() != null && "RESTUDIO_MARKER".equals(tabs().getActiveTab().getData())) {
                loadServersForCurrentTab();
            }
        });
    }

    private void openReStudioLogin() {
        ScreenManager.getInstance().setScreen(new ReStudioLoginScreen(this, () -> {
            refreshAccountButton();
            maybeAddReStudioTab();
            ScreenManager.getInstance().setScreen(this);
        }));
    }

    private void openActivePteroPanel() {
        Object data = tabs().getActiveTab() != null ? tabs().getActiveTab().getData() : null;
        if (data instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType())) {
            BrowserUtils.openBrowser(PteroBackend.normalizePanelUrl(host.getIp()));
        }
    }

    private void refreshAccountButton() {
        if (userButton == null) {
            return;
        }
        ReStudio studio = ReStudio.getInstance();
        String displayName = studio.getDisplayName();
        if (displayName == null || displayName.isBlank()) displayName = studio.getUsername();
        if (displayName == null || displayName.isBlank()) displayName = studio.getEmail();
        if (displayName == null || displayName.isBlank()) displayName = switch (studio.getSessionState()) {
            case RESTORING -> "Restoring Session";
            case REFRESHING -> "Refreshing Session";
            case CONNECTION_LOST -> "Reconnecting";
            case REAUTH_REQUIRED -> "Sign In Required";
            default -> "Account";
        };
        userButton.setMessage(displayName);
        userButton.setOnClick(this::onAccountButtonClick);
        if (studio.isAuthenticated()) {
            studio.loadAvatarId().thenAccept(id -> ScreenManager.getInstance().execute(() -> {
                if (id != null && userButton != null) {
                    userButton.setGeneratedIcon(id);
                }
            }));
            return;
        }
        userButton.setIcon("steve.png");
    }


    private void reloadInstancesSmartly() {
        Map<String, SSHManager> activeSessions = new HashMap<>();
        if (instanceManager != null) {
            for (RemoteHost host : instanceManager.getRemoteHosts()) {
                if ("PTERO".equalsIgnoreCase(host.getType())) {
                    continue;
                }
                SSHManager mgr = host.getExistingSshManager();
                if (mgr != null && mgr.isConnected()) {
                    activeSessions.put(host.hostId, mgr);
                }
            }

            instanceManager.loadInstances();
            iconManager.clearAllRemoteTracking();

            for (RemoteHost host : instanceManager.getRemoteHosts()) {
                if ("PTERO".equalsIgnoreCase(host.getType())) {
                    continue;
                }
                if (activeSessions.containsKey(host.hostId)) {
                    SSHManager oldMgr = activeSessions.get(host.hostId);
                    RemoteHost oldHost = oldMgr.getRemoteHost();

                    if (Objects.equals(host.getIp(), oldHost.getIp()) && Objects.equals(host.getUser(), oldHost.getUser()) && host.getPort() == oldHost.getPort()) {
                        oldMgr.updateHostReference(host);
                        host.setSshManager(oldMgr);
                    } else {
                        try { oldMgr.disconnect(); } catch (Exception ignored) {}
                    }
                }
            }
        } else {
            this.instanceManager = Rebase.get().getInstanceManager();
            instanceManager.loadInstances();
            iconManager.clearAllRemoteTracking();
        }
    }

    private void loadIcons() {
        try {
            serverIcon = Identifier.icon("server.png");
            unknown = Identifier.icon("unknown.png");
            paper = Identifier.icon("paper.png");
            vanilla = Identifier.icon("vanilla.png");
            fabric = Identifier.icon("fabric.png");
            forge = Identifier.icon("forge.png");
            neoforge = Identifier.icon("neoforge.png");
            waterfall = Identifier.icon("waterfall.png");
            velocity = Identifier.icon("velocity.png");
            leaf = Identifier.icon("leaf.png");
            quilt = Identifier.icon("quilt.png");
            spigot = Identifier.icon("spigot.png");
            bukkit = Identifier.icon("bukkit.png");
            purpur = Identifier.icon("purpur.png");
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void populateHostTabs() {
        tabs().addTab("Local", activeContainer).setData(null);

        for (RemoteHost host : instanceManager.getRemoteHosts()) {
            DesktopBounds.Bounds contentBounds = desktopBounds().content();
            Container c = createContainer("desktop_remote_" + host.name, contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height());
            DesktopLayout remoteLayout = new DesktopLayout();
            remoteLayout.setOnReorder(() -> saveServerOrder(c, host));
            configureNetworkDrop(remoteLayout);
            c.layout(remoteLayout).backgroundDrawing(false).enableSelecting(true).disableScissorRegion(true);
            tabs().addTab(host.name, c).setData(host);

            instanceManager.fetchRemoteInstances(host)
                .whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> {
                    for (TabsManager.Tab tab : tabs().getTabs()) {
                        if (tab.getData() == host) {
                            loadServersForTab(tab);
                            break;
                        }
                    }
                }));
        }

        maybeAddReStudioTab();
        int savedIndex = remotelyClient.getSavedTabIndex();
        tabs().setActiveTab(Math.min(savedIndex, tabs().getTabs().size() - 1));
        loadServersForCurrentTab();
    }

    private void maybeAddReStudioTab() {
        if (hasReStudioTab()) {
            return;
        }
        DesktopBounds.Bounds contentBounds = desktopBounds().content();
        Container container = createContainer("desktop_restudio", contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height());
        DesktopLayout remoteLayout = new DesktopLayout();
        configureNetworkDrop(remoteLayout);
        container.layout(remoteLayout).backgroundDrawing(false).enableSelecting(true).disableScissorRegion(true);
        tabs().addTab("Reactors", container).setData("RESTUDIO_MARKER");
        updatePositions();
        int savedIndex = remotelyClient.getSavedTabIndex();
        if (savedIndex < tabs().getTabs().size()) {
            tabs().setActiveTab(savedIndex);
        }

        if (ReStudio.getInstance().isAuthenticated()) {
            fetchReStudioServers();
        }
    }

    private void removeReStudioTab() {
        restudioInstances.clear();
        restudioServerViews.clear();
        if (tabsManager == null) {
            return;
        }
        for (int i = 0; i < tabs().getTabs().size(); i++) {
            TabsManager.Tab tab = tabs().getTabs().get(i);
            if (!"RESTUDIO_MARKER".equals(tab.getData())) {
                continue;
            }
            boolean wasActive = tabs().getActiveTab() == tab;
            tabs().removeTab(i);
            if (wasActive) {
                loadServersForCurrentTab();
            }
            return;
        }
    }

    private boolean hasReStudioTab() {
        for (TabsManager.Tab tab : tabs().getTabs()) {
            if ("RESTUDIO_MARKER".equals(tab.getData())) {
                return true;
            }
        }
        return false;
    }

    private void loadServersForCurrentTab() {
        loadServersForTab(tabs().getActiveTab());
    }

    private void applyDesktopContentBounds() {
        if (tabsManager == null) {
            return;
        }
        DesktopBounds.Bounds contentBounds = desktopBounds().content();
        for (TabsManager.Tab tab : tabs().getTabs()) {
            Container container = tab.getContainer();
            if (container == null) {
                continue;
            }
            container.setPosition(contentBounds.x(), contentBounds.y());
            container.setSize(contentBounds.width(), contentBounds.height());
            container.updateWidgetPositions();
        }
    }

    private void loadServersForAllTabs() {
        if (tabsManager == null) {
            return;
        }
        applyDesktopContentBounds();
        for (TabsManager.Tab tab : tabs().getTabs()) {
            loadServersForTab(tab);
        }
    }

    private void queueServerRefresh() {
        if (!reactiveRefreshEnabled || !serverRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        ScreenManager.getInstance().execute(() -> {
            serverRefreshQueued.set(false);
            if (reactiveRefreshEnabled) {
                loadServersForCurrentTab();
            }
        });
    }

    private void queueRuntimeRefresh(NetworkRuntimeSnapshot snapshot) {
        if (!reactiveRefreshEnabled || snapshot == null) {
            return;
        }
        pendingRuntimeSnapshots.put(snapshot.networkId(), snapshot);
        scheduleRuntimeRefresh();
    }

    private void scheduleRuntimeRefresh() {
        if (runtimeRefreshQueued.compareAndSet(false, true)) {
            ScreenManager.getInstance().execute(this::applyQueuedRuntimeRefresh);
        }
    }

    private void applyQueuedRuntimeRefresh() {
        Map<String, NetworkRuntimeSnapshot> snapshots = Map.copyOf(pendingRuntimeSnapshots);
        snapshots.forEach(pendingRuntimeSnapshots::remove);
        runtimeRefreshQueued.set(false);
        if (reactiveRefreshEnabled) {
            snapshots.values().forEach(this::refreshRuntimeWidgets);
        }
        if (reactiveRefreshEnabled && !pendingRuntimeSnapshots.isEmpty()) {
            scheduleRuntimeRefresh();
        }
    }

    private void registerInstanceChangeListener() {
        if (instanceManager == null || instanceChangeListenerRegistered) {
            return;
        }
        instanceManager.addChangeListener(instanceChangeListener);
        instanceChangeListenerRegistered = true;
    }

    private void registerNetworkChangeListener() {
        NetworkManager networkManager = remotelyClient.getNetworkManager();
        if (networkManager == null || networkChangeListenerRegistered) {
            return;
        }
        networkManager.addListener(networkChangeListener);
        networkChangeListenerRegistered = true;
    }

    private void registerRuntimeChangeListener() {
        NetworkManager networkManager = remotelyClient.getNetworkManager();
        if (networkManager == null || runtimeChangeListenerRegistered) {
            return;
        }
        networkManager.addRuntimeListener(runtimeChangeListener);
        runtimeChangeListenerRegistered = true;
    }

    private void refreshRuntimeWidgets(NetworkRuntimeSnapshot snapshot) {
        if (snapshot == null || tabsManager == null) {
            return;
        }
        for (TabsManager.Tab tab : tabs().getTabs()) {
            Container container = tab.getContainer();
            if (container == null) {
                continue;
            }
            for (AnimatedWidget widget : container.getWidgets()) {
                if (!(widget instanceof DesktopIconWidget<?> rawWidget) || !(rawWidget.getItem() instanceof Instance instance)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                DesktopIconWidget<Instance> desktopIcon = (DesktopIconWidget<Instance>) rawWidget;
                NetworkDefinition network = remotelyClient.getNetworkManager() == null ? null : remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).orElse(null);
                if (network == null || !network.networkId().equals(snapshot.networkId())) {
                    continue;
                }
                desktopIcon.setMessage(serverDisplayLabel(instance));
                desktopIcon.setHint(serverDisplayHint(instance));
                desktopIcon.accentType = getDesktopIconAccent(instance, false);
            }
        }
    }

    private boolean refreshVisibleServerWidget(Instance instance) {
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab == null || activeTab.getContainer() == null || instance == null) {
            return false;
        }
        String key = getWidgetKey(instance);
        for (AnimatedWidget widget : activeTab.getContainer().getWidgets()) {
            if (!(widget instanceof DesktopIconWidget<?> rawWidget) || !(rawWidget.getItem() instanceof Instance item)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            DesktopIconWidget<Instance> desktopIcon = (DesktopIconWidget<Instance>) rawWidget;
            if (!key.equals(getWidgetKey(item))) {
                continue;
            }
            desktopIcon.setItem(instance);
            desktopIcon.accentType = getDesktopIconAccent(instance, false);
            return true;
        }
        return false;
    }

    private boolean isPersistentLocalInstance(Instance instance) {
        if (instance == null || !instance.isLocalLifecyclePersistent()) {
            return false;
        }
        BackendConfig config = instance.getBackendConfig();
        return config == null || config.type == null || "LOCAL".equalsIgnoreCase(config.type);
    }

    private void pollPersistentLocalServerStates(List<Instance> visibleInstances, boolean force) {
        if (visibleInstances == null || visibleInstances.isEmpty()) {
            return;
        }
        List<Instance> persistentInstances = visibleInstances.stream()
            .filter(this::isPersistentLocalInstance)
            .toList();
        if (persistentInstances.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (persistentLocalProcessPollInFlight || (!force && now - lastPersistentLocalProcessPollMs < PERSISTENT_LOCAL_PROCESS_POLL_MS)) {
            return;
        }
        lastPersistentLocalProcessPollMs = now;
        persistentLocalProcessPollInFlight = true;
        Thread.ofVirtual().name("Remotely Persistent Server State").start(() -> {
            try {
                Map<Instance, LocalServerControllerModels.StatusResponse> controllerStatuses = new HashMap<>();
                for (Instance instance : persistentInstances) {
                    LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
                    if (status != null) {
                        controllerStatuses.put(instance, status);
                    }
                }
                ScreenManager.getInstance().execute(() -> {
                    for (Instance instance : persistentInstances) {
                        LocalServerControllerModels.StatusResponse status = controllerStatuses.get(instance);
                        if (status != null && status.pid > 0 && "RUNNING".equalsIgnoreCase(status.state)) {
                            instance.setState(InstanceState.RUNNING);
                        } else if (status != null && "STARTING".equalsIgnoreCase(status.state)) {
                            instance.setState(InstanceState.STARTING);
                        } else if (status != null && "STOPPING".equalsIgnoreCase(status.state)) {
                            if ("RUNNING".equalsIgnoreCase(status.desiredState)) {
                                LifecycleManager.requestStart(instance);
                            } else {
                                LifecycleManager.requestStop(instance);
                            }
                            instance.setState(InstanceState.STOPPING);
                        } else if (status != null && ("STOPPED".equalsIgnoreCase(status.state) || "CRASHED".equalsIgnoreCase(status.state))) {
                            LifecycleManager.clear(instance);
                            instance.setState("CRASHED".equalsIgnoreCase(status.state) ? InstanceState.CRASHED : InstanceState.STOPPED);
                        }
                        refreshVisibleServerWidget(instance);
                    }
                    persistentLocalProcessPollInFlight = false;
                });
            } catch (Throwable ignored) {
                persistentLocalProcessPollInFlight = false;
            }
        });
    }

    private void loadServersForTab(TabsManager.Tab tab) {
        if (tab == null) {
            updateNoServersOverlayVisibility(false);
            return;
        }

        Container targetContainer = tab.getContainer();
        if (targetContainer == null) {
            return;
        }

        List<Instance> instances;
        Object tabData = tab.getData();

        if ("RESTUDIO_MARKER".equals(tabData)) {
            instances = new ArrayList<>(restudioInstances);
        } else if (tabData instanceof RemoteHost host) {
            instances = new ArrayList<>(instanceManager.getRemoteInstances(host));
        } else {
            instances = new ArrayList<>(instanceManager.getLocalInstances());
        }

        instances.removeIf(Instance::isHidden);

        if ("RESTUDIO_MARKER".equals(tabData)) {
            RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
            List<String> hiddenRestudio = config.getHiddenRestudioServers();
            instances.removeIf(inst -> hiddenRestudio.contains(inst.getName()));
        }

        String context = "local";
        if (tabData instanceof RemoteHost host) {
            context = "remote." + host.name;
        } else if ("RESTUDIO_MARKER".equals(tabData)) {
            context = "remote.restudio";
        }

        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        List<String> order = config.getInstanceOrder(context);

        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            orderMap.put(order.get(i), i);
        }
        Map<String, Integer> naturalOrder = new HashMap<>();
        for (int index = 0; index < instances.size(); index++) {
            naturalOrder.put(getWidgetKey(instances.get(index)), index);
        }
        Map<String, Integer> visualOrder = new HashMap<>();
        for (Instance instance : instances) {
            visualOrder.put(instance.getInstanceId(), orderMap.getOrDefault(getWidgetKey(instance), naturalOrder.getOrDefault(getWidgetKey(instance), Integer.MAX_VALUE)));
        }
        NetworkManager networkManager = remotelyClient.getNetworkManager();
        Map<String, NetworkDefinition> memberships = new HashMap<>();
        Map<String, Integer> networkAnchors = new HashMap<>();
        if (networkManager != null) {
            for (Instance instance : instances) {
                NetworkDefinition network = networkManager.getNetworkForInstance(instance.getInstanceId()).orElse(null);
                if (network == null) {
                    continue;
                }
                memberships.put(instance.getInstanceId(), network);
                networkAnchors.merge(network.networkId(), visualOrder.getOrDefault(instance.getInstanceId(), Integer.MAX_VALUE), Math::min);
            }
        }
        instances.sort(Comparator.comparingInt((Instance instance) -> {
                NetworkDefinition network = memberships.get(instance.getInstanceId());
                return network == null ? visualOrder.getOrDefault(instance.getInstanceId(), Integer.MAX_VALUE) : networkAnchors.getOrDefault(network.networkId(), Integer.MAX_VALUE);
            })
            .thenComparingInt(instance -> memberships.containsKey(instance.getInstanceId()) ? 0 : 1)
            .thenComparing(instance -> {
                NetworkDefinition network = memberships.get(instance.getInstanceId());
                return network == null ? getWidgetKey(instance) : network.networkId();
            }, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(this::networkRoleOrder)
            .thenComparingInt(instance -> visualOrder.getOrDefault(instance.getInstanceId(), Integer.MAX_VALUE)));
        if (targetContainer.getLayout() instanceof DesktopLayout desktopLayout) {
            desktopLayout.setTopMargin(8);
        }

        Map<String, DesktopIconWidget<Instance>> existingWidgets = new HashMap<>();
        DesktopIconWidget<Instance> createButton = null;

        for (AnimatedWidget w : targetContainer.getWidgets()) {
            if (w instanceof DesktopIconWidget<?> rawWidget) {
                if (rawWidget.getItem() == null) {
                    @SuppressWarnings("unchecked")
                    DesktopIconWidget<Instance> diw = (DesktopIconWidget<Instance>) rawWidget;
                    createButton = diw;
                } else if (rawWidget.getItem() instanceof Instance instance) {
                    @SuppressWarnings("unchecked")
                    DesktopIconWidget<Instance> diw = (DesktopIconWidget<Instance>) rawWidget;
                    existingWidgets.put(getWidgetKey(instance), diw);
                }
            }
        }

        List<AnimatedWidget> toKeep = new ArrayList<>();
        for (Instance server : instances) {
            DesktopIconWidget<Instance> existing = existingWidgets.get(getWidgetKey(server));
            if (existing != null) {
                existing.setItem(server);
                existing.setMessage(serverDisplayLabel(server));
                existing.setHint(serverDisplayHint(server));
                existing.accentType = getDesktopIconAccent(server, false);
                toKeep.add(existing);
                existingWidgets.remove(getWidgetKey(server));
            } else {
                DesktopIconWidget<Instance> widget = createServerWidget(server, false);
                toKeep.add(widget);
            }
        }
        toKeep = collapseServerGroups(toKeep, instances, context);

        boolean isPteroTab = tabData instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType());
        if (!isPteroTab && createButton == null) {
            createButton = createServerWidget(null, true);
        }
        if (!isPteroTab) {
            toKeep.add(createButton);
        }

        targetContainer.replaceWidgets(toKeep);
        if (tab == tabs().getActiveTab()) {
            updateNoServersOverlayVisibility(instances.isEmpty());
            if (!(tabData instanceof RemoteHost) && !"RESTUDIO_MARKER".equals(tabData)) {
                pollPersistentLocalServerStates(instances, true);
            }
        }
    }

    private List<AnimatedWidget> collapseServerGroups(List<AnimatedWidget> widgets, List<Instance> instances, String context) {
        Map<String, DesktopIconWidget<Instance>> icons = new LinkedHashMap<>();
        for (AnimatedWidget widget : widgets) {
            if (widget instanceof DesktopIconWidget<?> rawIcon && rawIcon.getItem() instanceof Instance instance) {
                @SuppressWarnings("unchecked")
                DesktopIconWidget<Instance> icon = (DesktopIconWidget<Instance>) rawIcon;
                icons.put(instance.getInstanceId(), icon);
            }
        }
        NetworkManager manager = remotelyClient.getNetworkManager();
        Map<String, DesktopGroup> groupsByMember = new HashMap<>();
        Map<String, DesktopGroupWidget<Instance>> groupWidgets = new HashMap<>();
        if (manager != null) {
            LinkedHashMap<String, List<Instance>> networkMembers = new LinkedHashMap<>();
            Map<String, NetworkDefinition> networks = new HashMap<>();
            for (Instance instance : instances) {
                NetworkDefinition network = manager.getNetworkForInstance(instance.getInstanceId()).orElse(null);
                if (network == null) {
                    continue;
                }
                networkMembers.computeIfAbsent(network.networkId(), ignored -> new ArrayList<>()).add(instance);
                networks.put(network.networkId(), network);
            }
            for (Map.Entry<String, List<Instance>> entry : networkMembers.entrySet()) {
                NetworkDefinition network = networks.get(entry.getKey());
                DesktopGroup group = new DesktopGroup("network:" + network.networkId(), network.name(), entry.getValue().stream().map(Instance::getInstanceId).toList());
                List<DesktopIconWidget<Instance>> members = entry.getValue().stream().map(Instance::getInstanceId).map(icons::get).filter(Objects::nonNull).toList();
                DesktopGroupWidget<Instance> groupWidget = new DesktopGroupWidget<>(group, members, (widget, button) -> showServerGroupMenu(widget, network));
                groupWidget.setRenameAction((widget, name) -> saveNetworkName(network, name));
                group.members().forEach(member -> groupsByMember.put(member, group));
                groupWidgets.put(group.id(), groupWidget);
            }
        }
        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        instanceGroups = config.getInstanceGroups(context);
        List<DesktopGroup> validManualGroups = new ArrayList<>();
        for (DesktopGroup group : instanceGroups) {
            List<String> members = group.members().stream().filter(icons::containsKey).filter(member -> !groupsByMember.containsKey(member)).distinct().toList();
            if (members.size() < 2) {
                continue;
            }
            DesktopGroup validGroup = new DesktopGroup(group.id(), group.name(), members);
            List<DesktopIconWidget<Instance>> memberIcons = members.stream().map(icons::get).toList();
            DesktopGroupWidget<Instance> groupWidget = new DesktopGroupWidget<>(validGroup, memberIcons, (widget, button) -> showServerGroupMenu(widget, null));
            groupWidget.setRenameAction((widget, name) -> renameServerGroup(widget.getGroup().id(), name));
            validManualGroups.add(validGroup);
            members.forEach(member -> groupsByMember.put(member, validGroup));
            groupWidgets.put(validGroup.id(), groupWidget);
        }
        if (!validManualGroups.equals(instanceGroups)) {
            instanceGroups = validManualGroups;
            config.setInstanceGroups(context, instanceGroups);
        }
        List<AnimatedWidget> collapsed = new ArrayList<>();
        Set<String> renderedGroups = new HashSet<>();
        for (AnimatedWidget widget : widgets) {
            if (!(widget instanceof DesktopIconWidget<?> icon) || !(icon.getItem() instanceof Instance instance)) {
                collapsed.add(widget);
                continue;
            }
            DesktopGroup group = groupsByMember.get(instance.getInstanceId());
            if (group == null) {
                collapsed.add(widget);
            } else if (renderedGroups.add(group.id())) {
                collapsed.add(groupWidgets.get(group.id()));
            }
        }
        return collapsed;
    }

    private void showServerGroupMenu(DesktopGroupWidget<Instance> groupWidget, NetworkDefinition network) {
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);
        if (network != null) {
            builder.addIconItem("Open Network", "network.png", () -> openNetworkOverview(network), groupWidget.getMembers().size() + " Servers");
        }
        for (DesktopIconWidget<Instance> member : groupWidget.getMembers()) {
            Instance instance = member.getItem();
            builder.addIconItem(serverDisplayLabel(instance), member.getIconId(), () -> onDesktopIconClick(member, 0), serverDisplayHint(instance));
        }
        if (network == null) {
            builder.addIconItem("Ungroup", "close.png", () -> {
                instanceGroups.removeIf(group -> group.id().equals(groupWidget.getGroup().id()));
                RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
                config.setInstanceGroups(activeServerGroupContext(), instanceGroups);
                loadServersForCurrentTab();
            }, "Keep Every Server");
        }
        showContextMenu(groupWidget.getX(), groupWidget.getY() + groupWidget.getHeight(), builder);
    }

    private String activeServerGroupContext() {
        TabsManager.Tab tab = tabs().getActiveTab();
        if (tab != null && tab.getData() instanceof RemoteHost host) {
            return "remote." + host.name;
        }
        return tab != null && "RESTUDIO_MARKER".equals(tab.getData()) ? "remote.restudio" : "local";
    }

    private void renameServerGroup(String groupId, String name) {
        instanceGroups.replaceAll(group -> group.id().equals(groupId) ? new DesktopGroup(group.id(), name, group.members()) : group);
        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        config.setInstanceGroups(activeServerGroupContext(), instanceGroups);
    }

    private void createServerGroup(List<Instance> instances) {
        LinkedHashSet<String> members = instances.stream().map(Instance::getInstanceId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (members.size() < 2) {
            return;
        }
        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        List<DesktopGroup> groups = new ArrayList<>(config.getInstanceGroups(activeServerGroupContext()));
        groups.replaceAll(group -> new DesktopGroup(group.id(), group.name(), group.members().stream().filter(member -> !members.contains(member)).toList()));
        groups.removeIf(group -> group.members().size() < 2);
        long number = groups.stream().filter(group -> group.name().startsWith("Group")).count() + 1;
        groups.add(new DesktopGroup(UUID.randomUUID().toString(), "Group " + number, new ArrayList<>(members)));
        instanceGroups = groups;
        config.setInstanceGroups(activeServerGroupContext(), groups);
        loadServersForCurrentTab();
    }

    private String getWidgetKey(Instance inst) {
        if (inst == null) return "create_button";
        BackendConfig config = inst.getBackendConfig();
        if (config != null && "RESTUDIO".equalsIgnoreCase(config.type)) {
            String identifier = config.credentials.get("identifier");
            if (identifier != null) return "RESTUDIO_" + identifier;
        }
        if (config != null && "PTERO".equalsIgnoreCase(config.type)) {
            String hostId = config.credentials.get("hostId");
            String identifier = config.credentials.get("identifier");
            if (identifier != null) return "PTERO_" + hostId + "_" + identifier;
        }
        return inst.getInstanceId();
    }

    private void saveServerOrder(Container container, RemoteHost host) {
        List<String> newOrderIds = new ArrayList<>();
        for (AnimatedWidget w : container.getWidgets()) {
            if (w instanceof DesktopIconWidget<?> diw && diw.getItem() instanceof Instance instance) {
                newOrderIds.add(getWidgetKey(instance));
            } else if (w instanceof DesktopGroupWidget<?> groupWidget) {
                groupWidget.getMembers().stream().map(DesktopIconWidget::getItem).filter(Instance.class::isInstance).map(Instance.class::cast).map(this::getWidgetKey).forEach(newOrderIds::add);
            }
        }

        String context = "local";
        if (host != null) {
            context = "remote." + host.name;
        } else if (tabs().getActiveTab() != null && "RESTUDIO_MARKER".equals(tabs().getActiveTab().getData())) {
            context = "remote.restudio";
        }

        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        config.setInstanceOrder(context, newOrderIds);
    }

    private DesktopIconWidget<Instance> createServerWidget(Instance info, boolean isCreate) {
        String label = isCreate || info == null ? "Create" : serverDisplayLabel(info);
        Identifier iconId = isCreate || info == null ? null : iconManager.getQuickIconId(info);
        DesktopIconWidget.Builder<Instance> builder = iconId != null
            ? new DesktopIconWidget.Builder<>(info, iconId, label)
            : new DesktopIconWidget.Builder<>(info, (Identifier) null, label);
        DesktopIconWidget<Instance> widget = builder
            .onClick(this::onDesktopIconClick)
            .build();
        if (iconId == null) {
            widget.setIcon(serverIcon);
        }
        widget.accentType = getDesktopIconAccent(info, isCreate);
        if (isCreate || info == null) {
            return widget;
        }
        iconManager.loadIconIdAsync(info, widget::setIcon);
        BackendConfig backendConfig = info.getBackendConfig();
        if (backendConfig != null && !"LOCAL".equalsIgnoreCase(backendConfig.type)) {
            iconManager.loadRemoteIconAsync(info, () -> iconManager.loadIconIdAsync(info, widget::setIcon));
        }
        return widget;
    }

    private boolean isVelocityInstance(Instance instance) {
        return instance.getModLoader() == ModLoader.VELOCITY || instance.getServerSoftwareCompatibility().stream().anyMatch(value -> "velocity".equalsIgnoreCase(value));
    }

    private String serverDisplayLabel(Instance instance) {
        return instance.getName();
    }

    private String serverDisplayHint(Instance instance) {
        NetworkDefinition network = remotelyClient.getNetworkManager() == null ? null : remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).orElse(null);
        if (network == null) {
            return instance.getName();
        }
        NetworkMember member = network.members().stream().filter(candidate -> candidate.instanceId().equals(instance.getInstanceId())).findFirst().orElse(null);
        String role = member != null && member.isProxy() ? "Proxy" : "Backend";
        NetworkNodePresence presence = runtimePresence(network, member);
        String live = presence == null || presence.status() == NetworkNodeStatus.OFFLINE || presence.status() == NetworkNodeStatus.REVOKED || member == null || member.isProxy() ? "" : " • " + presence.players() + (presence.capacity() > 0 ? "/" + presence.capacity() : "") + " Players";
        return instance.getName() + " • " + network.name() + " • " + role + live;
    }

    private int networkRoleOrder(Instance instance) {
        NetworkDefinition network = remotelyClient.getNetworkManager() == null ? null : remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).orElse(null);
        if (network == null) {
            return 2;
        }
        return network.proxyInstanceId().equals(instance.getInstanceId()) ? 0 : 1;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void onHostTabSelected(TabsManager.Tab tab) {
        int selectionToken = remoteHostSelectionToken.incrementAndGet();
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        applyDesktopContentBounds();
        setActiveContainer(tab.getContainer());

        Object data = tab.getData();
        if (data instanceof RemoteHost host) {
            CompletableFuture.supplyAsync(() -> "PTERO".equalsIgnoreCase(host.getType()) || host.getSshManager().isConnected())
                .exceptionally(e -> false)
                .thenAccept(connected -> ScreenManager.getInstance().execute(() -> handleRemoteHostTabSelected(tab, host, selectionToken, connected)));
        } else if ("RESTUDIO_MARKER".equals(data)) {
            fetchReStudioServers();
        }

        loadServersForCurrentTab();
    }

    private void handleRemoteHostTabSelected(TabsManager.Tab tab, RemoteHost host, int selectionToken, boolean connected) {
        if (selectionToken != remoteHostSelectionToken.get()) {
            return;
        }
        if (tabs().getActiveTab() != tab) {
            return;
        }
        if (!connected) {
            if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("calm"));
            connectRemoteHostAsync(host, () -> {
                if (selectionToken != remoteHostSelectionToken.get() || tabs().getActiveTab() != tab) {
                    return;
                }
                if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getDefaultAccent());
                instanceManager.fetchRemoteInstances(host)
                    .whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> {
                        if (selectionToken != remoteHostSelectionToken.get() || tabs().getActiveTab() != tab) {
                            return;
                        }
                        loadServersForTab(tab);
                    }));
            }, () -> {
                if (selectionToken != remoteHostSelectionToken.get() || tabs().getActiveTab() != tab) {
                    return;
                }
                if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("danger"));
            });
            return;
        }
        if (tab.getWidget() != null) {
            tab.getWidget().setAccent(ThemeManager.getDefaultAccent());
        }
        if ("PTERO".equalsIgnoreCase(host.getType()) || instanceManager.getRemoteInstances(host).isEmpty()) {
            if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("calm"));
            instanceManager.fetchRemoteInstances(host)
                .whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> {
                    if (selectionToken != remoteHostSelectionToken.get() || tabs().getActiveTab() != tab) {
                        return;
                    }
                    if (tab.getWidget() != null) {
                        tab.getWidget().setAccent(e == null ? ThemeManager.getDefaultAccent() : ThemeManager.getAccent("danger"));
                    }
                    loadServersForTab(tab);
                }));
        }
    }

    private void fetchReStudioServers() {
        if (!ReStudio.getInstance().isAuthenticated()) {
            return;
        }
        if (tabs().getActiveTab().getWidget() != null) tabs().getActiveTab().getWidget().setAccent(ThemeManager.getAccent("calm"));
        ReStudio.getInstance().getApi().getServers().thenCompose(servers -> {
            List<Instance> instances = new ArrayList<>();
            Map<String, ServerModels.ClientServerView> serverViews = new HashMap<>();
            List<CompletableFuture<Void>> stateFutures = new ArrayList<>();
            List<CompletableFuture<Void>> tokenFutures = new ArrayList<>();

            if (servers != null) {
                for (ServerModels.ClientServerView csv : servers) {
                    Map<String, String> creds = new HashMap<>();
                    creds.put("identifier", csv.identifier);
                    creds.put("host", csv.sftpIp);
                    creds.put("port", String.valueOf(csv.sftpPort));
                    creds.put("user", csv.sftpUser);
                    creds.put("password", "");
                    creds.put("installing", String.valueOf(csv.isInstalling));
                    creds.put("suspended", String.valueOf(csv.isSuspended));

                    BackendConfig config = new BackendConfig("RESTUDIO", creds);

                    Instance inst = new Instance(csv.name, "unknown", "");
                    inst.setBackendConfig(config);
                    inst.setServer(true);
                    if (csv.isInstalling) {
                        inst.setState(InstanceState.INSTALLING);
                    }
                    if (csv.isSuspended) {
                        inst.setState(InstanceState.STOPPED);
                    }

                    if (csv.loader != null) {
                        try {
                            inst.setModLoader(ModLoader.valueOf(csv.loader));
                        } catch (IllegalArgumentException ignored) {
                            inst.setModLoader(ModLoader.VANILLA);
                        }
                    }
                    if (csv.version != null) {
                        inst.setVersionId(csv.version);
                    }

                    instances.add(inst);
                    serverViews.put(csv.name, csv);

                    CompletableFuture<Void> tokenFuture = ReStudio.getInstance().getApi().getSftpToken(csv.identifier)
                            .thenAccept(token -> inst.getBackendConfig().credentials.put("password", token != null ? token : ""))
                            .exceptionally(ex -> {
                                inst.getBackendConfig().credentials.put("password", "");
                                return null;
                            });
                    tokenFutures.add(tokenFuture);

                    CompletableFuture<Void> stateFuture = ReStudio.getInstance().getApi().getServerResources(csv.identifier)
                        .thenAccept(stats -> {
                            if (stats == null) {
                                return;
                            }
                            if (csv.isSuspended || stats.isSuspended) {
                                inst.setState(InstanceState.STOPPED);
                                inst.getBackendConfig().credentials.put("suspended", "true");
                                return;
                            }
                            String currentState = stats.currentState != null ? stats.currentState.trim().toLowerCase() : "";
                            switch (currentState) {
                                case "running" -> inst.setState(InstanceState.RUNNING);
                                case "starting" -> inst.setState(InstanceState.STARTING);
                                case "stopping" -> inst.setState(InstanceState.STOPPING);
                                case "offline" -> inst.setState(InstanceState.STOPPED);
                            }
                        })
                        .exceptionally(ex -> null);
                    stateFutures.add(stateFuture);
                }
            }

            List<CompletableFuture<Void>> allFutures = new ArrayList<>(stateFutures.size() + tokenFutures.size());
            allFutures.addAll(stateFutures);
            allFutures.addAll(tokenFutures);
            return CompletableFuture.allOf(allFutures.toArray(CompletableFuture[]::new))
                .thenApply(v -> Map.entry(instances, serverViews));
        }).whenComplete((result, e) -> ScreenManager.getInstance().execute(() -> {
            if (tabs().getActiveTab().getWidget() != null) tabs().getActiveTab().getWidget().setAccent(ThemeManager.getDefaultAccent());
            if (e != null) {
                new Notification("Error fetching ReStudio servers", e.getMessage(), Notification.Type.ERROR);
                if (tabs().getActiveTab().getWidget() != null) tabs().getActiveTab().getWidget().setAccent(ThemeManager.getAccent("danger"));
                return;
            }
            restudioInstances.clear();
            restudioInstances.addAll(result.getKey());
            restudioServerViews.clear();
            restudioServerViews.putAll(result.getValue());
            remotelyClient.cacheReStudioServerViews(restudioServerViews);

            for (TabsManager.Tab tab : tabs().getTabs()) {
                if ("RESTUDIO_MARKER".equals(tab.getData())) {
                    loadServersForTab(tab);
                    break;
                }
            }
        }));
    }

    private void onHostTabRenamed(TabsManager.Tab tab) {
        if (tab != null && tab.getData() instanceof RemoteHost) {
            openRemoteHostPopup(true);
        }
    }

    private Accent getDesktopIconAccent(Instance info, boolean isCreate) {
        if (info == null || isCreate) {
            return ThemeManager.getDefaultAccent();
        }
        NetworkManager networkManager = remotelyClient.getNetworkManager();
        NetworkDefinition network = networkManager == null ? null : networkManager.getNetworkForInstance(info.getInstanceId()).orElse(null);
        NetworkMember member = network == null ? null : network.members().stream().filter(candidate -> candidate.instanceId().equals(info.getInstanceId())).findFirst().orElse(null);
        NetworkNodePresence presence = runtimePresence(network, member);
        if (presence != null) {
            return switch (presence.status()) {
                case ONLINE -> ThemeManager.getAccent("nice");
                case DRAINING, MAINTENANCE -> ThemeManager.getDefaultAccent();
                case OFFLINE, REVOKED -> ThemeManager.getAccent("danger");
            };
        }
        if (info.getState() == InstanceState.RUNNING || info.getState() == InstanceState.STARTING || info.getState() == InstanceState.STOPPING) {
            return ThemeManager.getAccent("nice");
        }
        if (info.getState() == InstanceState.CRASHED) {
            return ThemeManager.getAccent("danger");
        }
        return ThemeManager.getDefaultAccent();
    }

    private NetworkNodePresence runtimePresence(NetworkDefinition network, NetworkMember member) {
        if (network == null || member == null || remotelyClient.getNetworkManager() == null) {
            return null;
        }
        NetworkRuntimeSnapshot snapshot = remotelyClient.getNetworkManager().getRuntimeSnapshot(network.networkId());
        return snapshot.connected() ? snapshot.node(member.nodeId()).orElse(null) : null;
    }

    private void onDesktopIconClick(DesktopIconWidget<Instance> widget, int button) {
        if (button == 0) {
            if (widget.getItem() == null) {
                boolean isPteroTab = tabs().getActiveTab().getData() instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType());
                if (isPteroTab) {
                    new Notification("Panel Managed", "Create Servers In Pterodactyl", Notification.Type.WARN);
                    return;
                }
                if ("RESTUDIO_MARKER".equals(tabs().getActiveTab().getData()) && !ReStudio.getInstance().isAuthenticated()) {
                    new Notification.Builder()
                        .message("Not Authenticated")
                        .description("Click To Log-In")
                        .type(Notification.Type.ERROR)
                        .action(this::openReStudioLogin)
                        .build();
                    return;
                }
                playSound(Sound.CREATE);
                showCreateChoice();
            } else {
                openServerScreen(widget.getItem());
            }
        } else if (button == 1) {
            if (widget.getItem() != null) {
                List<Instance> selectedInstances = selectedServerInstances(widget);
                if (selectedInstances.size() > 1) {
                    showSelectedServersMenu(widget, selectedInstances);
                    return;
                }
                activeContainer.clearSelection();
                activeContainer.addSelectedWidget(widget);

                Instance inst = widget.getItem();
                RemoteHost rh = null;
                boolean isRestudio = false;
                boolean isPtero;

                if (inst.getBackendConfig() != null) {
                    if ("RESTUDIO".equalsIgnoreCase(inst.getBackendConfig().type)) {
                        isPtero = false;
                        isRestudio = true;
                    } else if ("PTERO".equalsIgnoreCase(inst.getBackendConfig().type)) {
                        isPtero = true;
                        String hostId = inst.getBackendConfig().credentials.get("hostId");
                        for (RemoteHost h : instanceManager.getRemoteHosts()) {
                            if (Objects.equals(hostId, h.hostId)) {
                                rh = h;
                                break;
                            }
                        }
                    } else {
                        isPtero = false;
                        if (!"LOCAL".equalsIgnoreCase(inst.getBackendConfig().type)) {
                            for(RemoteHost h : instanceManager.getRemoteHosts()) {
                                if(inst.getBackendConfig().credentials.getOrDefault("host", "").equals(h.getIp())) {
                                    rh = h;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    isPtero = false;
                }

                RemoteHost finalRh = rh;
                ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);

                boolean running = inst.getState() == InstanceState.RUNNING || inst.getState() == InstanceState.STARTING || inst.getState() == InstanceState.STOPPING;
                builder.addHeaderButton(running ? "stop.png" : "start.png", () -> setServerPower(inst, !running), running ? "Stop Server" : "Start Server",
                        running ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("nice"));

                if (inst.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(inst.getBackendConfig().type) && !isPtero) {
                    builder.addHeaderButton("merge.png", () -> remotelyClient.openServerTwin(this, inst), "DevMode");
                }
                if (!isPtero) {
                    builder.addHeaderButton("edit.png", () -> client.setScreen(new ServerConfigurationScreen(this, widget.getItem(), finalRh, remotelyClient)), "Edit Server's Settings");
                }
                builder.addHeaderButton("explorer.png", () -> client.setScreen(new FileExplorerScreen(this, widget.getItem(), Path.of(widget.getItem().getPath()), remotelyDir, false) {
                    public String getDesktopAppId() {
                        return "file-explorer";
                    }

                    public String getDesktopAppTitle() {
                        return "File Explorer";
                    }

                    public String getDesktopAppIconPath() {
                        return "explorer.png";
                    }
                }), "Open Server's Folder");

                if (rh == null) {
                    builder.addHeaderButton("map.png", () -> openWorldScreen(widget.getItem()), "View World Map");
                }

                final ServerModels.ClientServerView flowServerView = (inst.getBackendConfig() != null && "RESTUDIO".equalsIgnoreCase(inst.getBackendConfig().type))
                        ? restudioServerViews.get(inst.getName()) : null;
                builder.addHeaderButton("ReSync.png", () -> remotelyClient.openReSyncStudio(this, inst, flowServerView), "ReSync");
                NetworkDefinition managedNetwork = remotelyClient.getNetworkManager() == null ? null : remotelyClient.getNetworkManager().getNetworkForInstance(inst.getInstanceId()).orElse(null);
                if (managedNetwork != null) {
                    NetworkJob latestJob = remotelyClient.getNetworkManager().getJobManager().getJobs(managedNetwork.networkId()).stream().findFirst().orElse(null);
                    builder.addIconItem(managedNetwork.name(), "network.png", () -> openNetworkOverview(managedNetwork), latestJob == null ? "Managed Network" : networkJobStatusLabel(latestJob.status()));
                    if (latestJob != null && latestJob.canResume()) {
                        builder.addHeaderButton("reload.png", () -> resumeNetworkJob(latestJob), "Resume Network", ThemeManager.getAccent("calm"));
                    }
                    if (latestJob != null && latestJob.canRollback() && (latestJob.status() == NetworkJobStatus.INTERRUPTED || latestJob.status() == NetworkJobStatus.FAILED)) {
                        builder.addHeaderButton("history.png", () -> rollbackNetworkJob(latestJob), "Rollback Network", ThemeManager.getAccent("danger"));
                    }
                } else if (remotelyClient.getNetworkManager() != null) {
                    if (isVelocityProxy(inst)) {
                        builder.addIconItem("Import Network", "merge.png", () -> scanNetworkForAdoption(inst), "Scan Velocity Without Changes");
                    }
                }
                if (!isRestudio && !isPtero && managedNetwork == null) {
                    builder.addHeaderButton("copy.png", () -> duplicateInstance(inst), "Duplicate Server").addHeaderButton("delete.png", () -> {
                        showDeleteServerPopup(widget.getItem());
                    }, "Show Deletion Options", ThemeManager.getAccent("danger"));
                }

                builder.addIconItem("Customize Icon", "shades.png", () -> customizeIcon(inst, finalRh), "");
                showContextMenu(widget.getX() + widget.getWidth() + 4, widget.getY() + 24, builder);
            }
        }
    }

    private List<Instance> selectedServerInstances(DesktopIconWidget<Instance> clickedWidget) {
        if (!activeContainer.getSelectedWidgets().contains(clickedWidget)) {
            return List.of(clickedWidget.getItem());
        }
        return activeContainer.getSelectedWidgets().stream()
                .filter(DesktopIconWidget.class::isInstance)
                .map(widget -> ((DesktopIconWidget<?>) widget).getItem())
                .filter(Instance.class::isInstance)
                .map(Instance.class::cast)
                .toList();
    }

    private void openNetworkCreationFromSelection(List<Instance> selected) {
        List<Instance> proxies = selected.stream().filter(this::isVelocityInstance).toList();
        List<Instance> backends = selected.stream().filter(instance -> !isVelocityInstance(instance)).toList();
        if (proxies.size() > 1 || backends.stream().anyMatch(Instance::isProxyServer)) {
            new Notification("Invalid Selection", "Select Backends And At Most One Velocity Proxy", Notification.Type.ERROR);
            return;
        }
        if (!proxies.isEmpty()) {
            showNetworkCreation(new NetworkCreationDraft(proxies.getFirst(), backends));
            return;
        }
        createNetworkProxy(backends);
    }

    private void createNetworkProxy(List<Instance> backends) {
        Object data = tabs().getActiveTab() == null ? null : tabs().getActiveTab().getData();
        NetworkServerCreationContext context = NetworkServerCreationContext.active(data instanceof RemoteHost host ? host : null, "RESTUDIO_MARKER".equals(data));
        if (!context.supported()) {
            new Notification("Provider Managed", context.unavailableMessage(), Notification.Type.WARN);
            return;
        }
        client.setScreen(new ServerConfigurationScreen(this, context.remoteHost(), remotelyClient, ModLoader.VELOCITY, proxy -> {
            client.setScreen(this);
            ScreenManager.getInstance().execute(() -> showNetworkCreation(new NetworkCreationDraft(proxy, backends)));
        }));
    }

    private void showNetworkCreation(NetworkCreationDraft draft) {
        closeNetworkPopups();
        TextInputWidget[] nameInputRef = new TextInputWidget[1];
        TextInputWidget nameInput = new TextInputWidget.Builder().text(draft.name).placeholder("Network Name").maxLength(64).onChange(() -> draft.name = nameInputRef[0].getText()).build();
        nameInputRef[0] = nameInput;
        IconButton createBackend = new IconButton.Builder().label("Create Backend").imagePath("newFile.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> createNetworkBackend(draft)).build();
        IconButton create = new IconButton.Builder().label("Create Network").imagePath("save.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> createNetwork(draft, nameInput.getText())).build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Create Network").size(500, Math.min(Math.max(205, 142 + draft.backends.size() * 30), Math.max(205, height - 30))).setResizable(true);
        builder.addRow("networkName", "Network", nameInput);
        builder.addRow("networkProxy", "Proxy • Automatic Entry Port", new IconButton.Builder().label(draft.proxy.getName()).imagePath("network.png").accentType(ThemeManager.getAccent("calm")).build());
        for (Instance backend : draft.backends) {
            IconButton remove = new IconButton.Builder().label("Remove").imagePath("close.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
                draft.backends.removeIf(candidate -> candidate.getInstanceId().equals(backend.getInstanceId()));
                showNetworkCreation(draft);
            }).build();
            builder.addRow("backend_" + backend.getInstanceId(), backend.getName() + " • Automatic Port", remove);
        }
        builder.addRow("networkServers", "Servers", createBackend);
        builder.addTitleAction("Create", () -> create.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        networkCreationPopup = builder.build();
        networkCreationPopup.setX((width - networkCreationPopup.getWidth()) / 2);
        networkCreationPopup.setY((height - networkCreationPopup.getHeight()) / 2);
        addDrawableChild(networkCreationPopup);
        networkCreationPopup.show();
    }

    private void createNetworkBackend(NetworkCreationDraft draft) {
        NetworkServerCreationContext context = NetworkServerCreationContext.forInstance(draft.proxy);
        if (!context.supported()) {
            new Notification("Provider Managed", context.unavailableMessage(), Notification.Type.WARN);
            return;
        }
        networkCreationPopup.hide();
        client.setScreen(new ServerConfigurationScreen(this, context.remoteHost(), remotelyClient, ModLoader.PAPER, backend -> {
            client.setScreen(this);
            ScreenManager.getInstance().execute(() -> {
                if (draft.backends.stream().noneMatch(candidate -> candidate.getInstanceId().equals(backend.getInstanceId()))) {
                    draft.backends.add(backend);
                }
                showNetworkCreation(draft);
            });
        }));
    }

    private void createNetwork(NetworkCreationDraft draft, String requestedName) {
        if (networkOperationInFlight) {
            return;
        }
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isBlank()) {
            new Notification("Name Required", Notification.Type.ERROR);
            return;
        }
        if (draft.backends.isEmpty()) {
            new Notification("Backend Required", "Create Or Add At Least One Backend", Notification.Type.ERROR);
            return;
        }
        draft.name = name;
        showNetworkReSyncPrompt(draft);
    }

    private void showNetworkReSyncPrompt(NetworkCreationDraft draft) {
        closeNetworkPopups();
        PopupWidget[] popup = new PopupWidget[1];
        PopupWidget.Builder builder = new PopupWidget.Builder("Install ReSync").width(420);
        builder.addRow(new PopupWidget.PopupRow.Builder("Add Live Network Features").id("resync").description("Install The Latest ReSync On The Proxy And Backends For Player Controls, Shared Chat, Content, Events, And Live Status. The Network Still Works Without It.").build());
        builder.addTitleAction("Continue Without ReSync", () -> {
            popup[0].hide();
            runNetworkCreation(draft, false);
        }, PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Install ReSync", () -> {
            popup[0].hide();
            runNetworkCreation(draft, true);
        }, PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        networkCreationPopup = popup[0];
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void runNetworkCreation(NetworkCreationDraft draft, boolean installReSync) {
        networkOperationInFlight = true;
        closeNetworkPopups();
        List<Instance> instances = instanceManager.getAllInstances();
        List<Instance> affected = new ArrayList<>(draft.backends);
        affected.add(draft.proxy);
        NetworkCreationRequest request = new NetworkCreationRequest(draft.name, draft.proxy.getInstanceId(), 25565, defaultNetworkMembers(draft.proxy, draft.backends, installReSync), false);
        Notification notification = new Notification.Builder().message(installReSync ? "Installing ReSync" : "Creating Network").description(installReSync ? "Preparing Live Network Features" : "Configuring Ports And Velocity").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        CompletableFuture<Void> setup = stopNetworkInstances(affected);
        if (installReSync) {
            setup = setup.thenCompose(unused -> CompletableFuture.supplyAsync(() -> NetworkReSyncSetup.installLatest(affected), Executors.IO).thenApply(result -> {
                if (!result.successful()) {
                    throw new CompletionException(new IllegalStateException(result.failureMessage()));
                }
                return null;
            }));
        }
        setup.thenCompose(unused -> remotelyClient.getNetworkManager().prepareCreation(request, instances, List.of())).thenCompose(prepared -> remotelyClient.getNetworkManager().runPreparedCreation(prepared, instances, "Server Manager")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            networkOperationInFlight = false;
            if (throwable != null || job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Network Creation Failed").description(throwable != null ? rootMessage(throwable) : job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                showNetworkCreation(draft);
                return;
            }
            NetworkDefinition created = remotelyClient.getNetworkManager().getNetworkForInstance(draft.proxy.getInstanceId()).orElse(null);
            notification.update().message("Network Created").description(created == null ? draft.name : created.members().size() + " Servers Configured").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            loadServersForAllTabs();
        }));
    }

    private void openNetworkOverview(NetworkDefinition network) {
        NetworkManager manager = remotelyClient.getNetworkManager();
        NetworkDefinition current = manager == null ? null : manager.getNetwork(network.networkId()).orElse(null);
        if (current == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            return;
        }
        client.setScreen(new NetworkOverviewScreen(this, remotelyClient, current.networkId()));
    }

    void showNetworkSettings(String networkId) {
        NetworkManager manager = remotelyClient.getNetworkManager();
        NetworkDefinition network = manager == null ? null : manager.getNetwork(networkId).orElse(null);
        if (network == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            return;
        }
        openNetworkOverview(network);
    }

    private void closeNetworkPopups() {
        if (networkCreationPopup != null) {
            remove(networkCreationPopup);
            networkCreationPopup = null;
        }
    }

    private void attachNetworkServerAutomatically(NetworkDefinition network, Instance instance) {
        if (networkOperationInFlight) {
            loadServersForAllTabs();
            return;
        }
        boolean reSyncEnabled = network.runtime().enabled() || network.members().stream().anyMatch(NetworkMember::resyncEnabled);
        if (!reSyncEnabled) {
            runAutomaticNetworkAttach(network, instance, false);
            return;
        }
        closeNetworkPopups();
        PopupWidget[] popup = new PopupWidget[1];
        PopupWidget.Builder builder = new PopupWidget.Builder("Install ReSync").width(400);
        builder.addRow(new PopupWidget.PopupRow.Builder("Enable Live Server Features").id("resync").description("Install The Latest ReSync On " + instance.getName() + " For Player Controls, Shared Features, Events, And Live Status.").build());
        builder.addTitleAction("Continue Without ReSync", () -> {
            popup[0].hide();
            runAutomaticNetworkAttach(network, instance, false);
        }, PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Install ReSync", () -> {
            popup[0].hide();
            runAutomaticNetworkAttach(network, instance, true);
        }, PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void runAutomaticNetworkAttach(NetworkDefinition network, Instance instance, boolean installReSync) {
        networkOperationInFlight = true;
        pendingNetworkMembershipInstances.add(instance.getInstanceId());
        closeNetworkPopups();
        List<Instance> instances = instanceManager.getAllInstances();
        Instance proxy = instances.stream().filter(candidate -> candidate.getInstanceId().equals(network.proxyInstanceId())).findFirst().orElse(null);
        if (proxy == null) {
            networkOperationInFlight = false;
            pendingNetworkMembershipInstances.remove(instance.getInstanceId());
            new Notification("Proxy Unavailable", network.name(), Notification.Type.ERROR);
            return;
        }
        String route = uniqueRoute(network, instance.getName());
        String address = NetworkHostScope.resolve(proxy).equals(NetworkHostScope.resolve(instance)) ? "" : backendAddress(instance);
        Notification notification = new Notification.Builder().message(installReSync ? "Installing ReSync" : "Adding Server").description(installReSync ? "Preparing Live Server Features" : "Allocating Port And Updating Velocity").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        CompletableFuture<Void> setup = installReSync ? CompletableFuture.supplyAsync(() -> NetworkReSyncSetup.installLatest(List.of(instance)), Executors.IO).thenApply(result -> {
            if (!result.successful()) {
                throw new CompletionException(new IllegalStateException(result.failureMessage()));
            }
            return null;
        }) : CompletableFuture.completedFuture(null);
        setup.thenCompose(unused -> remotelyClient.getNetworkManager().prepareAttach(network, instance, route, NetworkMemberRole.GAMEPLAY, "", address, 0, 0, installReSync, instances, List.of())).thenCompose(prepared -> remotelyClient.getNetworkManager().runPreparedAttach(prepared, instances, "Server Manager")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            networkOperationInFlight = false;
            pendingNetworkMembershipInstances.remove(instance.getInstanceId());
            if (throwable != null || job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Add Server Failed").description(throwable != null ? rootMessage(throwable) : job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            } else {
                notification.update().message("Server Added").description(job.restartRequired() ? "Restart Affected Servers To Apply Changes" : instance.getName() + " Is Ready").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            }
            loadServersForAllTabs();
        }));
    }

    private void applyNetworkConfiguration(NetworkDefinition network) {
        if (networkOperationInFlight) {
            return;
        }
        networkOperationInFlight = true;
        closeNetworkPopups();
        List<Instance> instances = instanceManager.getAllInstances();
        Notification notification = new Notification.Builder().message("Syncing Network").description("Applying Ports, Routes, And Forwarding").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().runJob(network, instances, List.of(), NetworkJobType.RECONCILE, "Server Manager").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            networkOperationInFlight = false;
            if (throwable != null || job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Network Sync Failed").description(throwable != null ? rootMessage(throwable) : job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            } else {
                notification.update().message("Network Synced").description(job.restartRequired() ? "Restart Affected Servers To Apply Changes" : "Ports And Routes Are Current").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
                loadServersForAllTabs();
            }
        }));
    }

    private void dissolveNetwork(NetworkDefinition network) {
        if (networkOperationInFlight) {
            return;
        }
        networkOperationInFlight = true;
        closeNetworkPopups();
        List<Instance> instances = instanceManager.getAllInstances();
        List<Instance> managed = network.members().stream().filter(NetworkMember::isManaged).map(NetworkMember::instanceId).map(instanceId -> instances.stream().filter(candidate -> candidate.getInstanceId().equals(instanceId)).findFirst().orElse(null)).filter(Objects::nonNull).toList();
        Notification notification = new Notification.Builder().message("Dissolving Network").description("Restoring Standalone Settings").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        stopNetworkInstances(managed).thenCompose(unused -> remotelyClient.getNetworkManager().dissolveSafely(network, instances, "Server Manager")).whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            networkOperationInFlight = false;
            if (throwable != null || job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Dissolve Failed").description(throwable != null ? rootMessage(throwable) : job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Network Dissolved").description("Servers Restored As Standalone").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            loadServersForAllTabs();
        }));
    }

    private void saveNetworkName(NetworkDefinition network, String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isBlank()) {
            new Notification("Name Required", Notification.Type.ERROR);
            return;
        }
        try {
            NetworkDefinition updated = remotelyClient.getNetworkManager().save(network.renamed(name));
            remotelyClient.getNetworkManager().reconcileInstanceBindings(instanceManager.getAllInstances());
            loadServersForAllTabs();
            new Notification("Network Updated", updated.name(), Notification.Type.SUCCESS);
        } catch (RuntimeException exception) {
            new Notification("Edit Failed", rootMessage(exception), Notification.Type.ERROR);
        }
    }

    private void runNetworkLifecycle(NetworkDefinition network, NetworkLifecycleOperation operation) {
        closeNetworkPopups();
        Notification notification = new Notification.Builder().message(operation == NetworkLifecycleOperation.START ? "Starting Network" : operation == NetworkLifecycleOperation.STOP ? "Stopping Network" : "Restarting Network").description(network.name()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().runLifecycle(network, instanceManager.getAllInstances(), operation, "Server Manager").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null || job == null || job.status() != NetworkLifecycleStatus.SUCCEEDED) {
                notification.update().message("Network Needs Attention").description(throwable != null ? rootMessage(throwable) : job == null ? "Network operation did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            } else {
                notification.update().message("Network Ready").description(job.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            }
            loadServersForAllTabs();
        }));
    }

    private void scanNetworkForAdoption(Instance proxy) {
        Notification notification = new Notification.Builder().message("Scanning Network").description(proxy.getName()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().scanForAdoption(proxy, instanceManager.getAllInstances()).whenComplete((report, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Network Scan Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            if (report.issues().stream().anyMatch(issue -> issue.code().equals("adoption.stock-config"))) {
                notification.update().message("Fresh Proxy Ready").description("Velocity Example Routes Will Be Replaced").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
                showNetworkCreation(new NetworkCreationDraft(proxy, List.of()));
                return;
            }
            notification.update().message("Network Scan Ready").description(report.routes().size() + " Routes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkAdoptionScreen(this, remotelyClient, proxy, report));
        }));
    }

    private void scanLegacyMigration(Instance proxy) {
        Notification notification = new Notification.Builder().message("Scanning Legacy Network").description(proxy.getName()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().scanLegacyMigration(proxy, instanceManager.getAllInstances()).whenComplete((report, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                notification.update().message("Migration Scan Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Migration Scan Ready").description(report.routes().size() + " Routes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkMigrationScreen(this, remotelyClient, proxy, report));
        }));
    }

    private boolean isVelocityProxy(Instance instance) {
        return instance != null && (instance.getModLoader() == ModLoader.VELOCITY || instance.getServerSoftwareCompatibility().stream().anyMatch(value -> value.equalsIgnoreCase("velocity")));
    }

    private boolean isLegacyProxy(Instance instance) {
        return instance != null && (instance.getModLoader() == ModLoader.WATERFALL || instance.getModLoader() == ModLoader.BUNGEECORD || instance.getServerSoftwareCompatibility().stream().anyMatch(value -> value.equalsIgnoreCase("waterfall") || value.equalsIgnoreCase("bungeecord")));
    }

    private void resumeNetworkJob(NetworkJob job) {
        Notification notification = new Notification.Builder().message("Resuming Network").description(job.message()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().resumeJob(job.jobId(), instanceManager.getAllInstances(), List.of()).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishNetworkJobAction(notification, updated, throwable, "Network Recovered")));
    }

    private void rollbackNetworkJob(NetworkJob job) {
        Notification notification = new Notification.Builder().message("Rolling Back Network").description(job.message()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().rollbackJob(job.jobId(), instanceManager.getAllInstances()).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> finishNetworkJobAction(notification, updated, throwable, "Network Rolled Back")));
    }

    private void detachNetworkServer(NetworkDefinition network, Instance instance) {
        if (!canDetachNetworkBackend(network, instance)) {
            loadServersForAllTabs();
            return;
        }
        if (networkOperationInFlight) {
            loadServersForAllTabs();
            return;
        }
        List<Instance> instances = instanceManager.getAllInstances();
        Instance proxy = instances.stream().filter(candidate -> candidate.getInstanceId().equals(network.proxyInstanceId())).findFirst().orElse(null);
        if (proxy == null) {
            new Notification("Proxy Unavailable", network.name(), Notification.Type.ERROR);
            return;
        }
        networkOperationInFlight = true;
        pendingNetworkMembershipInstances.add(instance.getInstanceId());
        Notification notification = new Notification.Builder().message("Detaching Server").description(instance.getName()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().detachSafely(network, instance, instances, "Server Manager").whenComplete((job, throwable) -> ScreenManager.getInstance().execute(() -> {
            networkOperationInFlight = false;
            pendingNetworkMembershipInstances.remove(instance.getInstanceId());
            if (throwable != null || job == null || job.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Detach Failed").description(throwable != null ? rootMessage(throwable) : job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            } else {
                notification.update().message("Server Detached").description(job.restartRequired() ? "Restart Affected Servers To Apply Changes" : instance.getName() + " Is Standalone").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            }
            loadServersForAllTabs();
        }));
    }

    private boolean canDetachNetworkBackend(NetworkDefinition network, Instance instance) {
        if (network == null || instance == null || network.proxyInstanceId().equals(instance.getInstanceId())) {
            return false;
        }
        if (hasMultipleNetworkBackends(network)) {
            return true;
        }
        new Notification("Backend Required", "Dissolve Network To Remove Its Last Backend", Notification.Type.WARN);
        return false;
    }

    private boolean hasMultipleNetworkBackends(NetworkDefinition network) {
        return network != null && network.members().stream().filter(member -> !member.isProxy()).count() > 1;
    }

    private boolean canDeleteInstance(Instance instance) {
        BackendConfig backend = instance.getBackendConfig();
        return backend == null || backend.type == null || (!"PTERO".equalsIgnoreCase(backend.type) && !"RESTUDIO".equalsIgnoreCase(backend.type));
    }

    private void attachNetworkServer(NetworkDefinition network, Instance instance) {
        attachNetworkServerAutomatically(network, instance);
    }

    private void attachNetworkGroup(NetworkDefinition network, List<Instance> members) {
        Set<String> existingMembers = network.members().stream().map(NetworkMember::instanceId).collect(Collectors.toSet());
        List<Instance> candidates = members.stream().filter(instance -> !instance.isProxyServer() && !existingMembers.contains(instance.getInstanceId())).toList();
        if (candidates.isEmpty() || networkOperationInFlight) {
            return;
        }
        boolean reSyncEnabled = network.runtime().enabled() || network.members().stream().anyMatch(NetworkMember::resyncEnabled);
        if (!reSyncEnabled) {
            runNetworkGroupAttach(network, candidates, false);
            return;
        }
        closeNetworkPopups();
        PopupWidget[] popup = new PopupWidget[1];
        PopupWidget.Builder builder = new PopupWidget.Builder("Install ReSync").width(400);
        builder.addRow(new PopupWidget.PopupRow.Builder("Enable Live Server Features").id("resync").description("Install The Latest ReSync On " + candidates.size() + " Servers For Player Controls, Shared Features, Events, And Live Status.").build());
        builder.addTitleAction("Continue Without ReSync", () -> {
            popup[0].hide();
            runNetworkGroupAttach(network, candidates, false);
        }, PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Install ReSync", () -> {
            popup[0].hide();
            runNetworkGroupAttach(network, candidates, true);
        }, PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void runNetworkGroupAttach(NetworkDefinition network, List<Instance> members, boolean installReSync) {
        networkOperationInFlight = true;
        String groupContext = activeServerGroupContext();
        List<DesktopGroup> groups = new ArrayList<>(instanceGroups);
        members.stream().map(Instance::getInstanceId).forEach(pendingNetworkMembershipInstances::add);
        Notification notification = new Notification.Builder().message(installReSync ? "Installing ReSync" : "Adding Group").description(members.size() + " Servers").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        CompletableFuture<Void> setup = installReSync ? CompletableFuture.supplyAsync(() -> NetworkReSyncSetup.installLatest(members), Executors.IO).thenApply(result -> {
            if (!result.successful()) {
                throw new CompletionException(new IllegalStateException(result.failureMessage()));
            }
            return null;
        }) : CompletableFuture.completedFuture(null);
        setup.thenCompose(unused -> NetworkGroupAttachmentTransaction.execute(members,
            instance -> attachNetworkGroupMember(network.networkId(), instance, installReSync),
            instance -> detachNetworkGroupMember(network.networkId(), instance))).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            networkOperationInFlight = false;
            members.stream().map(Instance::getInstanceId).forEach(pendingNetworkMembershipInstances::remove);
            if (throwable != null) {
                notification.update().message("Add Group Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            } else {
                groups.removeIf(group -> group.members().stream().anyMatch(member -> members.stream().anyMatch(instance -> instance.getInstanceId().equals(member))));
                RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
                config.setInstanceGroups(groupContext, groups);
                if (groupContext.equals(activeServerGroupContext())) {
                    instanceGroups = groups;
                }
                notification.update().message("Group Added").description(members.size() + " Servers Joined " + network.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            }
            loadServersForAllTabs();
        }));
    }

    private CompletableFuture<Boolean> attachNetworkGroupMember(String networkId, Instance instance, boolean reSyncEnabled) {
        NetworkManager manager = remotelyClient.getNetworkManager();
        NetworkDefinition network = manager.getNetwork(networkId).orElseThrow(() -> new IllegalStateException("Network unavailable"));
        if (network.members().stream().anyMatch(member -> member.instanceId().equals(instance.getInstanceId()))) {
            return CompletableFuture.completedFuture(false);
        }
        List<Instance> instances = instanceManager.getAllInstances();
        Instance proxy = instances.stream().filter(candidate -> candidate.getInstanceId().equals(network.proxyInstanceId())).findFirst().orElseThrow(() -> new IllegalStateException("Proxy unavailable"));
        String route = uniqueRoute(network, instance.getName());
        String address = NetworkHostScope.resolve(proxy).equals(NetworkHostScope.resolve(instance)) ? "" : backendAddress(instance);
        return manager.prepareAttach(network, instance, route, NetworkMemberRole.GAMEPLAY, "", address, 0, 0, reSyncEnabled, instances, List.of())
                .thenCompose(prepared -> manager.runPreparedAttach(prepared, instances, "Server Manager"))
                .thenCompose(job -> job != null && job.status() == NetworkJobStatus.SUCCEEDED
                        ? CompletableFuture.completedFuture(true)
                        : CompletableFuture.failedFuture(new IllegalStateException(job == null ? "Network job did not finish" : job.message())));
    }

    private CompletableFuture<Void> detachNetworkGroupMember(String networkId, Instance instance) {
        NetworkManager manager = remotelyClient.getNetworkManager();
        NetworkDefinition network = manager.getNetwork(networkId).orElseThrow(() -> new IllegalStateException("Network unavailable during rollback"));
        if (network.members().stream().noneMatch(member -> member.instanceId().equals(instance.getInstanceId()))) {
            return CompletableFuture.completedFuture(null);
        }
        return manager.detachSafely(network, instance, instanceManager.getAllInstances(), "Server Manager Rollback")
            .thenCompose(job -> job != null && job.status() == NetworkJobStatus.SUCCEEDED
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IllegalStateException(job == null ? "Network rollback job did not finish" : job.message())));
    }

    private void configureNetworkDrop(DesktopLayout layout) {
        layout.setTopMargin(8);
        layout.setGroupSpacing(0, null);
        layout.setOnDrop(this::handleServerGroupDrop);
        layout.setDropTargetFilter(this::canGroupServerDrop);
        layout.setOnDropAt(null);
    }

    private boolean canGroupServerDrop(AnimatedWidget dragged, AnimatedWidget target) {
        boolean draggable = dragged instanceof DesktopIconWidget<?> icon && icon.getItem() instanceof Instance
                || dragged instanceof DesktopGroupWidget<?> group && !group.getGroup().id().startsWith("network:");
        boolean targetable = target instanceof DesktopIconWidget<?> icon && icon.getItem() instanceof Instance
                || target instanceof DesktopGroupWidget<?>;
        return draggable && targetable;
    }

    private void handleServerGroupDrop(AnimatedWidget dragged, AnimatedWidget target) {
        if (dragged instanceof DesktopGroupWidget<?> draggedGroup) {
            List<Instance> draggedMembers = draggedGroup.getMembers().stream().map(DesktopIconWidget::getItem).filter(Instance.class::isInstance).map(Instance.class::cast).collect(Collectors.toCollection(ArrayList::new));
            if (target instanceof DesktopGroupWidget<?> targetGroup && targetGroup.getGroup().id().startsWith("network:")) {
                NetworkManager manager = remotelyClient.getNetworkManager();
                if (manager != null) {
                    manager.getNetwork(targetGroup.getGroup().id().substring("network:".length())).ifPresent(network -> attachNetworkGroup(network, draggedMembers));
                }
                return;
            }
            if (target instanceof DesktopGroupWidget<?> targetGroup) {
                targetGroup.getMembers().stream().map(DesktopIconWidget::getItem).filter(Instance.class::isInstance).map(Instance.class::cast).forEach(draggedMembers::add);
                instanceGroups.removeIf(group -> group.id().equals(draggedGroup.getGroup().id()) || group.id().equals(targetGroup.getGroup().id()));
                instanceGroups.add(new DesktopGroup(targetGroup.getGroup().id(), targetGroup.getGroup().name(), draggedMembers.stream().map(Instance::getInstanceId).distinct().toList()));
                RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
                config.setInstanceGroups(activeServerGroupContext(), instanceGroups);
                loadServersForCurrentTab();
                return;
            }
            if (target instanceof DesktopIconWidget<?> targetIcon && targetIcon.getItem() instanceof Instance targetInstance) {
                draggedMembers.add(targetInstance);
                replaceServerGroup(draggedGroup.getGroup().id(), draggedMembers);
            }
            return;
        }
        if (!(dragged instanceof DesktopIconWidget<?> draggedIcon) || !(draggedIcon.getItem() instanceof Instance draggedInstance)) {
            return;
        }
        if (target instanceof DesktopGroupWidget<?> groupWidget && groupWidget.getGroup().id().startsWith("network:")) {
            NetworkManager manager = remotelyClient.getNetworkManager();
            if (manager == null || draggedInstance.isProxyServer()) {
                return;
            }
            manager.getNetwork(groupWidget.getGroup().id().substring("network:".length())).ifPresent(network -> attachNetworkServer(network, draggedInstance));
            return;
        }
        if (target instanceof DesktopIconWidget<?> targetIcon && targetIcon.getItem() instanceof Instance targetInstance) {
            createServerGroup(List.of(draggedInstance, targetInstance));
            return;
        }
        if (target instanceof DesktopGroupWidget<?> groupWidget) {
            List<Instance> members = groupWidget.getMembers().stream().map(DesktopIconWidget::getItem).filter(Instance.class::isInstance).map(Instance.class::cast).collect(Collectors.toCollection(ArrayList::new));
            members.add(draggedInstance);
            replaceServerGroup(groupWidget.getGroup().id(), members);
        }
    }

    private void replaceServerGroup(String groupId, List<Instance> members) {
        instanceGroups.replaceAll(group -> group.id().equals(groupId) ? new DesktopGroup(group.id(), group.name(), members.stream().map(Instance::getInstanceId).distinct().toList()) : group);
        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        config.setInstanceGroups(activeServerGroupContext(), instanceGroups);
        loadServersForCurrentTab();
    }

    @Override
    public void onSessionStateChanged(SessionState state) {
        ScreenManager.getInstance().execute(this::refreshAccountButton);
    }

    private void finishNetworkJobAction(Notification notification, NetworkJob job, Throwable throwable, String successMessage) {
        if (throwable != null) {
            notification.update().message("Network Recovery Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        if (job == null || (job.status() != NetworkJobStatus.SUCCEEDED && job.status() != NetworkJobStatus.ROLLED_BACK)) {
            notification.update().message("Network Needs Attention").description(job == null ? "Network job did not finish" : job.message()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            return;
        }
        notification.update().message(successMessage).description(job.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
        loadServersForAllTabs();
    }

    private String networkJobStatusLabel(NetworkJobStatus status) {
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

    private void showSelectedServersMenu(DesktopIconWidget<Instance> anchor, List<Instance> selected) {
        long running = selected.stream().filter(this::isServerActive).count();
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this)
                .addHeaderButton("start.png", () -> selected.stream().filter(instance -> !isServerActive(instance)).forEach(instance -> setServerPower(instance, true)), "Start Selected", ThemeManager.getAccent("nice"))
                .addHeaderButton("stop.png", () -> selected.stream().filter(this::isServerActive).forEach(instance -> setServerPower(instance, false)), "Stop Selected", ThemeManager.getAccent("danger"))
                .addHeaderButton("folder.png", () -> createServerGroup(selected), "Group Selected")
                .addIconItem(selected.size() + " Servers Selected", "info.png", () -> {}, running + " Running");
        if (selected.stream().allMatch(this::canDuplicateServer)) {
            builder.addHeaderButton("copy.png", () -> selected.forEach(this::duplicateInstance), "Duplicate Selected");
        }
        showContextMenu(anchor.getX() + anchor.getWidth() + 4, anchor.getY() + 24, builder);
    }

    private boolean isServerActive(Instance instance) {
        return instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING || instance.getState() == InstanceState.STOPPING;
    }

    private boolean canDuplicateServer(Instance instance) {
        BackendConfig backend = instance.getBackendConfig();
        return backend == null || "LOCAL".equalsIgnoreCase(backend.type);
    }

    private CompletableFuture<Void> stopNetworkInstances(Collection<Instance> requested) {
        List<Instance> active = requested.stream().filter(Objects::nonNull).distinct().filter(this::isServerActive).toList();
        List<CompletableFuture<Void>> operations = new ArrayList<>();
        for (Instance instance : active) {
            InstanceState previous = instance.getState();
            instance.setState(InstanceState.STOPPING);
            CompletableFuture<Void> operation;
            if (instance.getBackendConfig() == null || "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type)) {
                operation = CompletableFuture.runAsync(() -> {
                    try {
                        LocalServerControllerClient.stop(instance);
                    } catch (Exception exception) {
                        throw new CompletionException(exception);
                    }
                });
            } else {
                operation = InstanceApi.of(instance).console().stopServer();
            }
            operations.add(operation.whenComplete((unused, throwable) -> instance.setState(throwable == null ? InstanceState.STOPPED : previous)));
        }
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new));
    }

    private List<NetworkCreationMember> defaultNetworkMembers(Instance proxy, List<Instance> backends, boolean resyncEnabled) {
        Map<String, Integer> names = new LinkedHashMap<>();
        AtomicInteger index = new AtomicInteger();
        return backends.stream().map(backend -> {
            String baseRoute = normalizeNetworkRoute(backend.getName());
            int occurrence = names.merge(baseRoute, 1, Integer::sum);
            String route = occurrence == 1 ? baseRoute : baseRoute + "-" + occurrence;
            String address = NetworkHostScope.resolve(proxy).equals(NetworkHostScope.resolve(backend)) ? "" : backendAddress(backend);
            NetworkMemberRole role = index.getAndIncrement() == 0 ? NetworkMemberRole.LOBBY : NetworkMemberRole.GAMEPLAY;
            return new NetworkCreationMember(backend.getInstanceId(), route, role, address, 0, 0, resyncEnabled);
        }).toList();
    }

    private String uniqueRoute(NetworkDefinition network, String name) {
        String baseRoute = normalizeNetworkRoute(name);
        Set<String> routes = network.members().stream().map(NetworkMember::routeName).map(route -> route.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        String route = baseRoute;
        int suffix = 2;
        while (routes.contains(route.toLowerCase(Locale.ROOT))) {
            route = baseRoute + "-" + suffix++;
        }
        return route;
    }

    private String backendAddress(Instance instance) {
        BackendConfig backend = instance.getBackendConfig();
        if (backend == null || backend.credentials == null) {
            return "";
        }
        return backend.credentials.getOrDefault("host", "");
    }

    private String normalizeNetworkRoute(String value) {
        String route = value == null ? "server" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        return route.isBlank() ? "server" : route;
    }

    private void setServerPower(Instance instance, boolean start) {
        if (start) {
            LifecycleManager.requestStart(instance);
        } else {
            LifecycleManager.requestStop(instance);
        }
        instance.setState(start ? InstanceState.STARTING : InstanceState.STOPPING);
        CompletableFuture<?> operation;
        if (instance.getBackendConfig() == null || "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type)) {
            operation = CompletableFuture.runAsync(() -> {
                try {
                    if (start) {
                        LocalServerControllerClient.start(instance);
                    } else {
                        LocalServerControllerClient.stop(instance);
                    }
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            });
        } else {
            operation = start ? InstanceApi.of(instance).console().startServer() : InstanceApi.of(instance).console().stopServer();
        }
        operation.whenComplete((ignored, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                LifecycleManager.clear(instance);
                instance.setState(start ? InstanceState.STOPPED : InstanceState.RUNNING);
                Throwable error = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
                new Notification(start ? "Server Start Failed" : "Server Stop Failed", error.getMessage() == null ? instance.getName() : error.getMessage(), Notification.Type.ERROR);
            } else if (!start) {
                LifecycleManager.clear(instance);
                instance.setState(InstanceState.STOPPED);
            }
            refreshVisibleServerWidget(instance);
        }));
    }

    private void duplicateInstance(Instance instance) {
        Instance newInstance = instanceManager.duplicateInstance(instance, instance.getName() + " - Copy");
        if (newInstance != null) {
            new Notification("Server Duplicated", "Server duplicated successfully.", Notification.Type.SUCCESS);
            loadServersForCurrentTab();
        } else {
            new Notification("Duplication Failed", "Could not duplicate server.", Notification.Type.ERROR);
        }
    }

    private void customizeIcon(Instance instance, RemoteHost remoteHost) {
        List<Identifier> images = iconManager.loadIconAssetIds();

        if (images.isEmpty()) {
            new Notification("Error", "No icon assets found.", Notification.Type.ERROR);
            return;
        }

        List<Integer> tints = Arrays.asList(0xFFFFFF, 0xFF6F61, 0x6FCF97, 0x6CC4F1, 0xFFC800, 0x9B51E0, 0xDF3E23, 0xd6f264, 0x7FFBFF);
        IconCustomizerWidget popup = new IconCustomizerWidget("Icon Customizer", images, tints, result -> iconManager.customizeIcon(instance, remoteHost, result, this::loadServersForCurrentTab));

        addDrawableChild(popup);
        popup.show();
    }


    private List<Instance> getCurrentServers() {
        if (tabsManager == null) {
            return instanceManager.getLocalInstances();
        }
        TabsManager.Tab active = tabs().getActiveTab();
        if (active == null) {
            return instanceManager.getLocalInstances();
        }
        Object data = active.getData();
        if ("RESTUDIO_MARKER".equals(data)) {
            return restudioInstances;
        }
        if (!(data instanceof RemoteHost host)) {
            return instanceManager.getLocalInstances();
        }
        return instanceManager.getRemoteInstances(host);
    }

    private void openFileExplorer() {
        client.setScreen(new FileExplorerScreen(this, null, remotelyDir, remotelyDir, false) {
            public String getDesktopAppId() {
                return "file-explorer";
            }

            public String getDesktopAppTitle() {
                return "File Explorer";
            }

            public String getDesktopAppIconPath() {
                return "explorer.png";
            }
        });
    }

    public void openWorldScreen(Instance instance) {
        WorldMapScreen mapWidget = new WorldMapScreen(this, instance, location -> {
            if (location == null || location.uuid() == null) {
                return;
            }
            PlayerManagerController controller = PlayerManagerController.getOrCreate(instance);
            UnifiedPlayer player = new UnifiedPlayer(location.uuid(), location.name());
            new PlayerDataPopup(ScreenManager.getInstance().getCurrentScreen(), player, controller);
        });
        client.setScreen(mapWidget);
    }

    private void createPopups() {
        createChoicePopup();
        createAddServerPopup();
        createRemoteHostPopup();
        ensureReactorPlanSelectionCardsCreated();
    }

    private void createChoicePopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create").width(180);
        IconButton server = new IconButton.Builder().size(160, 34).label("Server").hint("Create, Install, Or Import A Server").imagePath("server.png").iconSize(32).iconPadding(2).centered(true).onClick(() -> {
            createChoicePopup.hide();
            showServerCreationOptions();
        }).build();
        IconButton network = new IconButton.Builder().size(160, 34).label("Network").hint("Create A Proxy And Managed Servers").imagePath("network.png").iconSize(32).iconPadding(2).centered(true).onClick(() -> {
            createChoicePopup.hide();
            List<Instance> selected = activeContainer.getSelectedWidgets().stream().filter(DesktopIconWidget.class::isInstance).map(widget -> ((DesktopIconWidget<?>) widget).getItem()).filter(Instance.class::isInstance).map(Instance.class::cast).toList();
            openNetworkCreationFromSelection(selected);
        }).build();
        builder.addRow("creationTypes", "", server, network);
        createChoicePopup = builder.build();
        createChoicePopup.hide();
        addDrawableChild(createChoicePopup);
    }

    private void showCreateChoice() {
        createChoicePopup.setX((width - createChoicePopup.getWidth()) / 2);
        createChoicePopup.setY((height - createChoicePopup.getHeight()) / 2);
        createChoicePopup.show();
    }

    private void showServerCreationOptions() {
        Object data = tabs().getActiveTab() == null ? null : tabs().getActiveTab().getData();
        boolean isPteroTab = data instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType());
        boolean isReStudioTab = "RESTUDIO_MARKER".equals(data);
        addServerPopup.setRowVisibility("createServerRow", !isPteroTab);
        addServerPopup.setRowVisibility("modpackServerRow", !isPteroTab);
        addServerPopup.setRowVisibility("importServerRow", !isReStudioTab && !isPteroTab);
        addServerPopup.setX((width - addServerPopup.getWidth()) / 2);
        addServerPopup.setY((height - addServerPopup.getHeight()) / 2);
        addServerPopup.show();
    }

    private void createAddServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create Server")
            .width(260);

        IconButton createBtn = new IconButton.Builder()
            .label(("Create And Customize An Empty Server"))
            .imagePath("create.png")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                Object data = (tabs().getActiveTab() != null) ? tabs().getActiveTab().getData() : null;
                if ("RESTUDIO_MARKER".equals(data)) {
                    openReactorPlanSelection();
                } else if (data instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType())) {
                    new Notification("Panel Managed", "Create Servers In Pterodactyl", Notification.Type.WARN);
                } else {
                    RemoteHost currentHost = (data instanceof RemoteHost) ? (RemoteHost) data : null;
                    client.setScreen(new ServerConfigurationScreen(this, null, currentHost, remotelyClient));
                }
                addServerPopup.hide();
            })
            .build();

        IconButton modpackBtn = new IconButton.Builder()
            .label(("Browse For Online Modpacks"))
            .imagePath("download.png")
            .accentType(ThemeManager.getAccent("calm"))
            .enableGradient(true)
            .onClick(() -> {
                addServerPopup.hide();
                openModpackInstallation();
            })
            .build();

        IconButton importBtn = new IconButton.Builder()
            .label(("Import Existing Server"))
            .imagePath("explorer.png")
            .onClick(() -> {
                addServerPopup.hide();
                openImportFileExplorer();
            })
            .build();

        builder.addRow("createServerRow", "", createBtn);
        builder.addRow("modpackServerRow", "", modpackBtn);
        builder.addRow("importServerRow", "", importBtn);

        addServerPopup = builder.build();
        addServerPopup.hide();
        addDrawableChild(addServerPopup);
    }

    private void showDeleteServerPopup(Instance instance) {
        Identifier iconId = iconManager.getQuickIconId(instance);
        IconButton entry = new IconButton.Builder()
            .label(serverDisplayLabel(instance))
            .identifier(iconId)
            .iconSize(24)
            .size(0, 30)
            .build();
        if (iconId == null) {
            entry.setIcon(serverIcon);
        }
        entry.setActive(false);
        iconManager.loadIconIdAsync(instance, entry::setIcon);
        DeletionPopup.show(this, List.of(entry),
            DeletionPopup.Action.permanent(popup -> deleteServer(instance, popup, true)),
            DeletionPopup.Action.trash(popup -> deleteServer(instance, popup, false)));
    }

    private void deleteServer(Instance instance, PopupWidget popup, boolean permanent) {
        playSound(Sound.DELETE);
        popup.hide();
        String progressTitle = permanent ? "Deleting Server" : "Moving Server To Trash";
        Notification notification = new Notification.Builder()
            .message(progressTitle)
            .description(instance.getName())
            .type(Notification.Type.INFO)
            .loading(true)
            .autoSlideOut(false)
            .build();
        CompletableFuture.runAsync(() -> {
            try {
                QuickServerSyncManager.stopAndSyncBack(instance);
                instanceManager.removeInstanceAsync(instance, permanent).join();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            Throwable error = throwable;
            if (error instanceof CompletionException completionException && completionException.getCause() != null) {
                error = completionException.getCause();
            }
            if (error != null) {
                notification.update()
                    .message(permanent ? "Delete Failed" : "Move Failed")
                    .description(error.getMessage() != null && !error.getMessage().isBlank() ? error.getMessage() : instance.getName())
                    .type(Notification.Type.ERROR)
                    .loading(false)
                    .autoSlideOut(true)
                    .commit();
                return;
            }
            loadServersForCurrentTab();
            notification.update()
                .message(permanent ? "Server Deleted" : "Moved To Trash")
                .description(instance.getName())
                .type(Notification.Type.SUCCESS)
                .loading(false)
                .autoSlideOut(true)
                .commit();
        }));
    }

    private void openReactorPlanSelection() {
        ensureReactorPlanSelectionCardsCreated();
        hideReactorPlanSelection();
        selectedReactorPlanName = null;
        reactorPlans.clear();

        List<ServerModels.Plan> fallbackPlans = List.of(
                createFallbackPlan("Relay", 4096, 200, 700),
                createFallbackPlan("Refine", 6144, 300, 1100),
                createFallbackPlan("Revenge", 8192, 400, 1400)
        );

        ReStudio.getInstance().getApi().getPlans().thenAccept(plans -> ScreenManager.getInstance().execute(() -> {
            List<ServerModels.Plan> source = plans == null || plans.isEmpty() ? fallbackPlans : plans;
            source.stream()
                    .filter(Objects::nonNull)
                    .filter(plan -> !"custom".equalsIgnoreCase(plan.name))
                    .sorted(Comparator.comparingLong(plan -> plan.priceCents))
                    .limit(3)
                    .forEach(reactorPlans::add);

            if (reactorPlans.isEmpty()) {
                reactorPlans.addAll(fallbackPlans);
            }

            rebuildReactorPlanSelectionCards();
            showReactorPlanSelection();
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                reactorPlans.addAll(fallbackPlans);
                rebuildReactorPlanSelectionCards();
                showReactorPlanSelection();
            });
            return null;
        });
    }

    private void rebuildReactorPlanSelectionCards() {
        ensureReactorPlanSelectionCardsCreated();
        if (reactorPlanCards.isEmpty()) {
            return;
        }

        List<ServerModels.Plan> items = reactorPlans.isEmpty() ? List.of(
                createFallbackPlan("Relay", 4096, 200, 700),
                createFallbackPlan("Refine", 6144, 300, 1100),
                createFallbackPlan("Revenge", 8192, 400, 1400)
        ) : reactorPlans;

        for (int i = 0; i < reactorPlanCards.size(); i++) {
            ReactorPlanWidget card = reactorPlanCards.get(i);
            if (i >= items.size()) {
                card.setVisible(false);
                card.setActive(false);
                continue;
            }
            ServerModels.Plan plan = items.get(i);
            card.setVisible(true);
            card.setActive(true);
            card.setContent(
                    plan.name == null || plan.name.isBlank() ? "Reactor Plan" : plan.name,
                    formatPlanFriendlyDescription(plan),
                    formatPlanSpecs(plan),
                    formatPlanPrice(plan.priceCents)
            );
            if (selectedReactorPlanName != null && selectedReactorPlanName.equalsIgnoreCase(plan.name)) {
                card.accentType = ThemeManager.getAccent("nice");
            } else {
                card.accentType = ThemeManager.getDefaultAccent();
            }
        }
        positionReactorPlanSelectionCards();
    }

    private void onReactorPlanSelected(ServerModels.Plan plan) {
        if (plan == null || plan.name == null || plan.name.isBlank()) {
            return;
        }
        selectedReactorPlanName = plan.name;
        for (int i = 0; i < reactorPlanCards.size(); i++) {
            ReactorPlanWidget card = reactorPlanCards.get(i);
            ServerModels.Plan candidate = i < reactorPlans.size() ? reactorPlans.get(i) : null;
            if (candidate != null && candidate.name != null && candidate.name.equalsIgnoreCase(selectedReactorPlanName)) {
                card.accentType = ThemeManager.getAccent("nice");
                continue;
            }
            card.accentType = ThemeManager.getDefaultAccent();
        }
        openReactorConfigWithPlan();
    }

    private void openReactorConfigWithPlan() {
        if (selectedReactorPlanName == null || selectedReactorPlanName.isBlank()) {
            new Notification("Select Plan", Notification.Type.WARN);
            return;
        }
        hideReactorPlanSelection();
        client.setScreen(new ServerConfigurationScreen(this, null, null, remotelyClient, true, selectedReactorPlanName));
    }

    private void hideReactorPlanSelection() {
        reactorPlanSelectionVisible = false;
        for (ReactorPlanWidget card : reactorPlanCards) {
            card.setVisible(false);
            card.setActive(false);
        }
        loadServersForCurrentTab();
    }

    private void showReactorPlanSelection() {
        if (reactorPlanCards.isEmpty()) {
            return;
        }
        reactorPlanSelectionVisible = true;
        positionReactorPlanSelectionCards();
        for (int i = 0; i < reactorPlanCards.size(); i++) {
            ReactorPlanWidget card = reactorPlanCards.get(i);
            boolean hasPlan = i < reactorPlans.size();
            card.setVisible(hasPlan);
            card.setActive(hasPlan);
        }
        updateNoServersOverlayVisibility(false);
    }

    private void positionReactorPlanSelectionCards() {
        if (!reactorPlanSelectionVisible || reactorPlanCards.isEmpty()) {
            return;
        }
        int targetCardWidth = resolveReactorPlanCardWidth();
        int targetCardHeight = resolveReactorPlanCardHeight(targetCardWidth);
        for (ReactorPlanWidget card : reactorPlanCards) {
            card.setWidth(targetCardWidth);
            card.setHeight(targetCardHeight);
        }

        ReactorPlanWidget center = reactorPlanCards.get(1);
        int centerCardWidth = center.getWidth();
        int centerCardHeight = center.getHeight();
        int centerX = (width - centerCardWidth) / 2;
        int maxBottom = Math.max(80, height - 42);
        int centerY = Math.max(22, (maxBottom - centerCardHeight) / 2);
        center.setPosition(centerX, centerY);

        ReactorPlanWidget left = reactorPlanCards.getFirst();
        left.setPosition(Math.max(REACTOR_PLAN_CARD_SIDE_MARGIN, centerX - left.getWidth() - REACTOR_PLAN_CARD_GAP), centerY);

        ReactorPlanWidget right = reactorPlanCards.get(2);
        right.setPosition(Math.min(width - right.getWidth() - REACTOR_PLAN_CARD_SIDE_MARGIN, centerX + centerCardWidth + REACTOR_PLAN_CARD_GAP), centerY);
    }

    private int resolveReactorPlanCardWidth() {
        int available = width - (REACTOR_PLAN_CARD_SIDE_MARGIN * 2) - (REACTOR_PLAN_CARD_GAP * 2);
        int byLayout = available / 3;
        int lowerBound = Math.max(184, Math.round(width * 0.16f));
        int upperBound = Math.max(212, Math.round(width * 0.24f));
        return Math.clamp(byLayout, lowerBound, upperBound);
    }

    private int resolveReactorPlanCardHeight(int cardWidth) {
        if (!reactorPlanCards.isEmpty()) {
            return reactorPlanCards.getFirst().getPreferredHeight();
        }
        return Math.max(1, Math.round(cardWidth * 0.45f));
    }

    private ServerModels.Plan createFallbackPlan(String name, int memoryMb, int cpuPercent, long priceCents) {
        ServerModels.Plan plan = new ServerModels.Plan();
        plan.name = name;
        plan.memoryMb = memoryMb;
        plan.diskMb = 102400;
        plan.cpuPercent = cpuPercent;
        plan.databases = 5;
        plan.backups = 5;
        plan.allocations = 5;
        plan.priceCents = priceCents;
        return plan;
    }

    private String formatPlanSpecs(ServerModels.Plan plan) {
        return formatStorage(plan.memoryMb) + " RAM • " + formatStorage(plan.diskMb) + " Disk • " + formatCpuThreads(plan.cpuPercent) + " Threads";
    }

    private String formatPlanFriendlyDescription(ServerModels.Plan plan) {
        return switch (plan.name) {
            case "Relay" -> "Perfect For 10-15 Players";
            case "Refine" -> "Realistic For Anything";
            case "Revenge" -> "As Fast As It Can Be";
            default -> "";
        };
    }

    private String formatStorage(int valueMb) {
        if (valueMb <= 0) {
            return "0 MB";
        }
        if (valueMb >= 1024) {
            double gb = valueMb / 1024.0;
            long rounded = Math.round(gb);
            if (Math.abs(gb - rounded) < 0.05) {
                return rounded + " GB";
            }
            return String.format(Locale.US, "%.1f GB", gb);
        }
        return valueMb + " MB";
    }

    private String formatCpuThreads(int cpuPercent) {
        int percent = Math.max(0, cpuPercent);
        int threads = Math.max(1, (int) Math.round(percent / 100.0));
        return String.valueOf(threads);
    }

    private String formatPlanPrice(long priceCents) {
        if (priceCents <= 0) {
            return "$0.00/mo";
        }
        return String.format(Locale.US, "$%.2f/mo", priceCents / 100.0);
    }

    private void createRemoteHostPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Connect Remote Host").onClose(this::closeRemoteHostPopup)
            .size(360, 310).setResizable(true)
            .setAntiOutOfBound(true).setBoundOffset(header().headerSize)
            .setMinSize(360, 310);

        remoteHostNameInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow("Host Name", remoteHostNameInput);

        remoteHostTypeSwitch = new TabSwitchWidget.Builder().options(List.of("SSH", "Pterodactyl")).currentIndex(0).build();
        builder.addRow(ROW_REMOTE_HOST_TYPE, "Host Type", remoteHostTypeSwitch);

        remoteHostUserInput = new TextInputWidget.Builder().size(18, 18).text("root").build();
        builder.addRow(ROW_REMOTE_HOST_USER, "User Name", remoteHostUserInput);

        remoteHostIpInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow(ROW_REMOTE_HOST_IP, "IP Or Domain", remoteHostIpInput);

        remoteHostPortInput = new TextInputWidget.Builder().size(18, 18).text("22").build();
        builder.addRow(ROW_REMOTE_HOST_PORT, "Port", remoteHostPortInput);

        remoteHostPasswordInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow(ROW_REMOTE_HOST_PASSWORD, "Password", remoteHostPasswordInput);

        remoteHostSftpPasswordInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow(ROW_REMOTE_HOST_SFTP_PASSWORD, "Panel Password", remoteHostSftpPasswordInput);

        remoteHostAuthModeSwitch = new TabSwitchWidget.Builder().options(List.of("Password", "SSH Key")).currentIndex(0).build();
        builder.addRow(ROW_REMOTE_HOST_AUTH_MODE, "Auth Mode", remoteHostAuthModeSwitch);

        remoteHostKeyPathInput = new TextInputWidget.Builder().size(18, 18).placeholder("Leave Empty For Auto Discovery").build();
        builder.addRow(ROW_REMOTE_HOST_KEY_PATH, "Key Path", remoteHostKeyPathInput);

        remoteHostKeyPassphraseInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow(ROW_REMOTE_HOST_PASSPHRASE, "Passphrase", remoteHostKeyPassphraseInput);

        remoteHostPopup = builder.build();
        remoteHostPopup.hide();
        addDrawableChild(remoteHostPopup);
    }

    private void connectRemoteHostAsync(RemoteHost hostInfo, Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                boolean connected;
                if ("PTERO".equalsIgnoreCase(hostInfo.getType())) {
                    PteroBackend.listServers(hostInfo.getIp(), hostInfo.getApiKey()).join();
                    connected = true;
                } else {
                    connected = hostInfo.getSshManager().connect();
                }
                if (connected) {
                    ScreenManager.getInstance().execute(() -> {
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                } else {
                    throw new Exception("Connection failed.");
                }
            } catch (Exception e) {
                RebaseLogger.log("Failed to connect to remote host " + hostInfo.name + ": " + e.getMessage());
                ScreenManager.getInstance().execute(() -> {
                    if (onFailure != null) {
                        onFailure.run();
                    }
                });
            }
        }).start();
    }

    private void testRemoteHostAsync(RemoteHost hostInfo, Notification notification, Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                boolean connected;
                if ("PTERO".equalsIgnoreCase(hostInfo.getType())) {
                    PteroBackend.listServers(hostInfo.getIp(), hostInfo.getApiKey()).join();
                    connected = true;
                } else {
                    connected = hostInfo.getSshManager().connect();
                }
                if (connected) {
                    ScreenManager.getInstance().execute(() -> {
                        notification.update()
                            .description("Connecting... 100%")
                            .progress(100, 100)
                            .commit();
                        notification.update()
                            .message("Host Ready")
                            .description("Connection ok")
                            .type(Notification.Type.SUCCESS)
                            .loading(false)
                            .autoSlideOut(true)
                            .commit();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                } else {
                    throw new Exception("Connection failed.");
                }
            } catch (Exception e) {
                RebaseLogger.log("Failed to connect to remote host " + hostInfo.name + ": " + e.getMessage());
                ScreenManager.getInstance().execute(() -> {
                    notification.update()
                        .message("Error While Testing Host")
                        .description(e.getMessage() != null ? e.getMessage() : "Connection failed.")
                        .type(Notification.Type.ERROR)
                        .loading(false)
                        .autoSlideOut(true)
                        .commit();
                    if (onFailure != null) {
                        onFailure.run();
                    }
                });
            }
        }).start();
    }

    private void openRemoteHostPopup(boolean isEditing) {
        int activeTabIndex = tabs().getActiveTabIndex();
        Object data = (tabs().getActiveTab() != null) ? tabs().getActiveTab().getData() : null;

        remoteHostPopup.clearRows();

        String nameText = "";
        String userText = "root";
        String ipText = "";
        String portText = "22";
        String passwordText = "";
        String sftpPasswordText = "";
        String hostType = "SSH";

        if (isEditing && activeTabIndex > 0 && data instanceof RemoteHost host) {
            nameText = host.name;
            userText = host.user;
            ipText = host.ip;
            portText = String.valueOf(host.port);
            hostType = host.getType();
            passwordText = "PTERO".equalsIgnoreCase(hostType) ? (host.getApiKey() != null ? host.getApiKey() : "") : (host.getPassword() != null ? host.getPassword() : "");
            sftpPasswordText = "PTERO".equalsIgnoreCase(hostType) && host.getPassword() != null ? host.getPassword() : "";

            remoteHostConfirmButton = new AnimatedButton.Builder().label(("Save")).size(18, 18).accentType(ThemeManager.getAccent("nice")).onClick(this::onConfirmRemoteHost).build();
            remoteHostDeleteButton = new AnimatedButton.Builder().label(("Delete")).size(18, 18).onClick(this::onDeleteRemoteHost).accentType(ThemeManager.getAccent("danger")).build();
        } else {
            remoteHostConfirmButton = new AnimatedButton.Builder().label(("Test & Add")).size(18, 18).accentType(ThemeManager.getAccent("nice")).onClick(this::onConfirmRemoteHost).build();
            remoteHostDeleteButton = null;
        }

        String authModeText = "PASSWORD";
        String keyPathText = "";
        if (isEditing && activeTabIndex > 0 && data instanceof RemoteHost host) {
            authModeText = host.getAuthMode();
            keyPathText = host.getKeyPath() != null ? host.getKeyPath() : "";
        }

        remoteHostNameInput.setText(nameText);
        remoteHostTypeSwitch.setCurrentIndex("PTERO".equalsIgnoreCase(hostType) ? 1 : 0);
        remoteHostUserInput.setText("PTERO".equalsIgnoreCase(hostType) ? "" : userText);
        remoteHostIpInput.setText(ipText);
        remoteHostPortInput.setText(portText);
        remoteHostPasswordInput.setText(passwordText);
        remoteHostSftpPasswordInput.setText(sftpPasswordText);
        remoteHostAuthModeSwitch.setCurrentIndex("KEY".equalsIgnoreCase(authModeText) ? 1 : 0);
        remoteHostKeyPathInput.setText(keyPathText);
        remoteHostKeyPassphraseInput.setText("");

        remoteHostPopup.addRow("Host Name", remoteHostNameInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_TYPE, "Host Type", remoteHostTypeSwitch);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_USER, "User Name", remoteHostUserInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_IP, "IP Or Domain", remoteHostIpInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_PORT, "Port", remoteHostPortInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_AUTH_MODE, "Auth Mode", remoteHostAuthModeSwitch);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_PASSWORD, "Secret", remoteHostPasswordInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_SFTP_PASSWORD, "Panel Password", remoteHostSftpPasswordInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_KEY_PATH, "Key Path", remoteHostKeyPathInput);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_PASSPHRASE, "Passphrase", remoteHostKeyPassphraseInput);

        remoteHostPopup.clearTitleActions();
        remoteHostPopup.addTitleAction(isEditing ? "Save" : "Test & Add", () -> remoteHostConfirmButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        if (isEditing && remoteHostDeleteButton != null) {
            remoteHostPopup.addTitleAction("Delete", () -> remoteHostDeleteButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.DESTRUCTIVE);
        }

        remoteHostTypeSwitch.setOnChange(this::updateRemoteHostAdvancedVisibility);
        remoteHostAuthModeSwitch.setOnChange(this::updateRemoteHostAdvancedVisibility);
        updateRemoteHostAdvancedVisibility();

        remoteHostNameInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(isPteroHostSelected() ? remoteHostIpInput : remoteHostUserInput));
        remoteHostUserInput.addOnEnter((w) -> {
            if (isPteroHostSelected()) {
                remoteHostPopup.setFocusedWidget(remoteHostSftpPasswordInput);
            } else {
                remoteHostPopup.setFocusedWidget(remoteHostIpInput);
            }
        });
        remoteHostIpInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(isPteroHostSelected() ? remoteHostPasswordInput : remoteHostPortInput));
        remoteHostPortInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostPasswordInput));
        remoteHostPasswordInput.addOnEnter((w) -> {
            if (isPteroHostSelected()) {
                remoteHostPopup.setFocusedWidget(remoteHostSftpPasswordInput);
                return;
            }
            if (remoteHostAuthModeSwitch.getCurrentIndex() == 1) {
                remoteHostPopup.setFocusedWidget(remoteHostKeyPathInput);
            } else {
                onConfirmRemoteHost();
            }
        });
        remoteHostSftpPasswordInput.addOnEnter((w) -> onConfirmRemoteHost());
        remoteHostKeyPathInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostKeyPassphraseInput));
        remoteHostKeyPassphraseInput.addOnEnter((w) -> onConfirmRemoteHost());

        remoteHostPopup.setX((this.width - remoteHostPopup.getWidth()) / 2);
        remoteHostPopup.setY((this.height - remoteHostPopup.getHeight()) / 2);
        remoteHostPopup.show();
    }

    private void onConfirmRemoteHost() {
        RemoteHost host;
        boolean isEditing = tabs().getActiveTabIndex() > 0 && "Save".equals(remoteHostConfirmButton.getMessage());
        String existingKeyPath = null;

        if (isEditing && tabs().getActiveTab() != null) {
            host = (RemoteHost) tabs().getActiveTab().getData();
            existingKeyPath = host.getKeyPath();
        } else {
            host = new RemoteHost();
        }

        boolean isPtero = isPteroHostSelected();
        host.setType(isPtero ? "PTERO" : "SSH");
        host.name = remoteHostNameInput.getText();
        host.ip = remoteHostIpInput.getText();
        if (isPtero) {
            host.user = "";
            host.port = 443;
            String apiKey = remoteHostPasswordInput.getText();
            if (apiKey == null || apiKey.isBlank()) {
                new Notification("Error", "API Key Cannot Be Empty", Notification.Type.ERROR);
                return;
            }
            host.setApiKey(apiKey);
            host.setAuthMode("PASSWORD");
            host.setPassword(remoteHostSftpPasswordInput.getText());
            host.setKeyPath("");
            host.setKeyPassphrase("");
        } else {
            host.user = remoteHostUserInput.getText();
            try {
                host.port = Integer.parseInt(remoteHostPortInput.getText());
            } catch (NumberFormatException e) {
                new Notification("Error", "Port must be a valid number.", Notification.Type.ERROR);
                return;
            }
            String authMode = remoteHostAuthModeSwitch.getCurrentIndex() == 1 ? "KEY" : "PASSWORD";
            host.setAuthMode(authMode);
            if ("KEY".equalsIgnoreCase(authMode)) {
                String keyPath = remoteHostKeyPathInput.getText();
                if (keyPath == null || keyPath.isBlank()) {
                    keyPath = host.getEffectiveKeyPath();
                }
                if (keyPath == null || keyPath.isBlank()) {
                    new Notification("Error", "Key Path not set.", Notification.Type.ERROR);
                    return;
                }
                try {
                    if (!Files.exists(Path.of(keyPath))) {
                        new Notification("Error", "Key Path not found.", Notification.Type.ERROR);
                        return;
                    }
                } catch (Exception ignored) {
                    new Notification("Error", "Invalid Key Path.", Notification.Type.ERROR);
                    return;
                }
                host.setKeyPath(keyPath);
                String passphraseInput = remoteHostKeyPassphraseInput.getText();
                boolean keyPathChanged = isEditing && !Objects.equals(existingKeyPath, keyPath);
                if (passphraseInput != null && !passphraseInput.isBlank()) {
                    host.setKeyPassphrase(passphraseInput);
                } else if (!isEditing || keyPathChanged) {
                    host.setKeyPassphrase("");
                }
            } else {
                host.setPassword(remoteHostPasswordInput.getText());
                host.setKeyPath("");
                host.setKeyPassphrase("");
            }
            host.setApiKey("");
        }

        if (host.name.isEmpty() || host.ip.isEmpty()) {
            new Notification("Error", "Host Name and IP cannot be empty.", Notification.Type.ERROR);
            return;
        }

        if (isEditing) {
            instanceManager.updateRemoteHost(host);
            tabs().getActiveTab().setName(host.name);
        } else {
            Notification notification = new Notification.Builder()
                .message("Testing Host")
                .description("Connecting... 0%")
                .type(Notification.Type.INFO)
                .loading(true)
                .progress(0, 100)
                .autoSlideOut(false)
                .build();
            testRemoteHostAsync(host, notification, () -> {
                instanceManager.addRemoteHost(host);
                DesktopBounds.Bounds contentBounds = desktopBounds().content();
                Container c = createContainer("desktop_remote_" + host.name, contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height());
                DesktopLayout remoteLayout = new DesktopLayout();
                remoteLayout.setOnReorder(() -> saveServerOrder(c, host));
                configureNetworkDrop(remoteLayout);
                c.layout(remoteLayout).backgroundDrawing(false).enableSelecting(true).disableScissorRegion(true);
                tabs().addTab(host.name, c).setData(host);
                tabs().setActiveTab(tabs().getTabs().size() - 1);
                closeRemoteHostPopup();
            }, null);
            return;
        }

        closeRemoteHostPopup();
    }

    private void updateRemoteHostAdvancedVisibility() {
        boolean usePtero = isPteroHostSelected();
        boolean useKey = !usePtero && remoteHostAuthModeSwitch != null && remoteHostAuthModeSwitch.getCurrentIndex() == 1;
        if (remoteHostPopup != null) {
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_TYPE, true);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_USER, !usePtero);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_IP, true);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_PORT, !usePtero);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_PASSWORD, usePtero || !useKey);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_AUTH_MODE, !usePtero);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_SFTP_PASSWORD, usePtero);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_KEY_PATH, useKey);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_PASSPHRASE, useKey);
        }
        if (remoteHostUserInput != null) {
            remoteHostUserInput.setActive(!usePtero);
            remoteHostUserInput.setVisible(!usePtero);
        }
        if (remoteHostPortInput != null) {
            remoteHostPortInput.setActive(!usePtero);
            remoteHostPortInput.setVisible(!usePtero);
        }
        if (remoteHostPasswordInput != null) {
            remoteHostPasswordInput.setActive(usePtero || !useKey);
            remoteHostPasswordInput.setVisible(usePtero || !useKey);
        }
        if (remoteHostSftpPasswordInput != null) {
            remoteHostSftpPasswordInput.setActive(usePtero);
            remoteHostSftpPasswordInput.setVisible(usePtero);
        }
        if (remoteHostKeyPathInput != null) {
            remoteHostKeyPathInput.setActive(useKey);
            remoteHostKeyPathInput.setVisible(useKey);
        }
        if (remoteHostKeyPassphraseInput != null) {
            remoteHostKeyPassphraseInput.setActive(useKey);
            remoteHostKeyPassphraseInput.setVisible(useKey);
        }
        if (remoteHostAuthModeSwitch != null) {
            remoteHostAuthModeSwitch.setActive(!usePtero);
            remoteHostAuthModeSwitch.setVisible(!usePtero);
        }
        if (remoteHostTypeSwitch != null) {
            remoteHostTypeSwitch.setActive(true);
            remoteHostTypeSwitch.setVisible(true);
        }
    }

    private boolean isPteroHostSelected() {
        return remoteHostTypeSwitch != null && remoteHostTypeSwitch.getCurrentIndex() == 1;
    }

    private void onDeleteRemoteHost() {
        if (tabs().getActiveTabIndex() > 0 && tabs().getActiveTab().getData() instanceof RemoteHost hostToRemove) {
            instanceManager.removeRemoteHost(hostToRemove);
            tabs().removeTab(tabs().getActiveTabIndex());
            tabs().setActiveTab(0);
            closeRemoteHostPopup();
        }
    }

    private void openServerScreen(Instance info) {
        remotelyClient.openInstanceInTerminal(this, info);
    }

    public static void openServerScreen(String path) {
        List<Instance> allInstances = new ArrayList<>(InstanceManager.getInstance().getLocalInstances());
        InstanceManager.getInstance().getRemoteHosts().forEach(h -> allInstances.addAll(InstanceManager.getInstance().getRemoteInstances(h)));
        for (Instance info : allInstances) {
            if (info.getPath().equals(path)) {
                RemotelyClient.INSTANCE.openInstanceInTerminal(ScreenManager.currentScreen, info);
                return;
            }
        }
    }

    private void closeRemoteHostPopup() {
        if(remoteHostPopup != null) {
            remoteHostPopup.hide();
        }
    }

    private void openImportFileExplorer() {
        Object data = (tabs().getActiveTab() != null) ? tabs().getActiveTab().getData() : null;
        if (data instanceof RemoteHost host) {
            Map<String, String> creds = new HashMap<>();
            creds.put("hostId", host.hostId);
            creds.put("host", host.getIp());
            BackendConfig config;
            if ("PTERO".equalsIgnoreCase(host.getType())) {
                creds.put("apiUrl", PteroBackend.normalizePanelUrl(host.getIp()));
                List<Instance> instances = instanceManager.getRemoteInstances(host);
                if (instances.isEmpty()) {
                    new Notification("No Panel Servers", "Select A Server First", Notification.Type.WARN);
                    return;
                }
                Instance firstInstance = instances.getFirst();
                BackendConfig existingConfig = instances.getFirst().getBackendConfig();
                if (existingConfig != null && existingConfig.credentials != null) {
                    creds.put("identifier", existingConfig.credentials.get("identifier"));
                    String virtualRoot = existingConfig.credentials.get("virtualRoot");
                    if (virtualRoot != null && !virtualRoot.isBlank()) {
                        creds.put("virtualRoot", virtualRoot);
                    }
                }
                config = new BackendConfig("PTERO", creds);
                Instance dummy = new Instance(host.name, "", firstInstance.getPath());
                dummy.setBackendConfig(config);
                FileSystemProvider provider = dummy.getBackend().getFileSystem();
                Path homePath = Path.of(firstInstance.getPath());
                client.setScreen(new FileExplorerScreen(this, null, homePath, remotelyDir, true, provider) {
                    public String getDesktopAppId() {
                        return "file-explorer";
                    }
                });
                return;
            } else {
                creds.put("port", String.valueOf(host.getPort()));
                creds.put("user", host.getUser());
                creds.put("authMode", host.getAuthMode());
                String password = host.getPassword();
                if (password != null && !password.isBlank()) {
                    creds.put("password", password);
                }
                if (host.getKeyPath() != null && !host.getKeyPath().isBlank()) {
                    creds.put("keyPath", host.getKeyPath());
                }
                config = new BackendConfig("SSH", creds);
            }
            Instance dummy = new Instance(host.name, "", "/");
            dummy.setBackendConfig(config);
            FileSystemProvider provider = dummy.getBackend().getFileSystem();
            String home = provider.getMetadata("homeDir");
            if (home == null || home.isBlank()) home = "/";
            Path homePath = Path.of(home);
            client.setScreen(new FileExplorerScreen(this, null, homePath, remotelyDir, true, provider) {
                public String getDesktopAppId() {
                    return "file-explorer";
                }

                public String getDesktopAppTitle() {
                    return "Import Server";
                }

                public String getDesktopAppIconPath() {
                    return "explorer.png";
                }
            });
            return;
        }
        client.setScreen(new FileExplorerScreen(this, null, remotelyDir, remotelyDir, true) {
            public String getDesktopAppId() {
                return "file-explorer";
            }

            public String getDesktopAppTitle() {
                return "Import Server";
            }

            public String getDesktopAppIconPath() {
                return "explorer.png";
            }
        });
    }

    private void openModpackInstallation() {
        Object data = (tabs().getActiveTab() != null) ? tabs().getActiveTab().getData() : null;
        if ("RESTUDIO_MARKER".equals(data)) {
            client.setScreen(new ResourceBrowserScreen(this, null, ResourceType.MODPACK, true, (RemoteHost) null, true));
        } else {
            RemoteHost currentHost = (tabs().getActiveTabIndex() > 0 && tabs().getActiveTab() != null && data instanceof RemoteHost) ? (RemoteHost) data : null;
            client.setScreen(new ResourceBrowserScreen(this, null, ResourceType.MODPACK, true, currentHost, false));
        }
    }

    @Override
    public void removed() {
        reactiveRefreshEnabled = false;
        serverRefreshQueued.set(false);
        runtimeRefreshQueued.set(false);
        pendingRuntimeSnapshots.clear();
        ReStudio.getInstance().removeListener(this);
        if (instanceManager != null && instanceChangeListenerRegistered) {
            instanceManager.removeChangeListener(instanceChangeListener);
            instanceChangeListenerRegistered = false;
        }
        NetworkManager networkManager = remotelyClient.getNetworkManager();
        if (networkManager != null && networkChangeListenerRegistered) {
            networkManager.removeListener(networkChangeListener);
            networkChangeListenerRegistered = false;
        }
        if (networkManager != null && runtimeChangeListenerRegistered) {
            networkManager.removeRuntimeListener(runtimeChangeListener);
            runtimeChangeListenerRegistered = false;
        }
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        if (taskbarHelper != null) {
            taskbarHelper.detach();
        }
        super.removed();
    }

    private long lastReloadTime = 0;

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        playSound(Sound.SERVERMANAGER);
        applyDesktopContentBounds();
        if (System.currentTimeMillis() - lastReloadTime > 5000) {
            reloadInstancesSmartly();
            lastReloadTime = System.currentTimeMillis();
        }
        loadServersForCurrentTab();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        relayoutDesktopTabs();
        applyDesktopContentBounds();
        positionReactorPlanSelectionCards();
        if (noServersOverlay != null) {
            DesktopBounds.Bounds contentBounds = desktopBounds().content();
            noServersOverlay.setPosition(contentBounds.x(), contentBounds.y());
            noServersOverlay.setSize(contentBounds.width(), contentBounds.height());
            noServersOverlay.updateWidgetPositions();
            reactorsNoServersIcon.setX(width / 2 - (reactorsNoServersIcon.getWidth() / 2));
            reactorsNoServersIcon.setY(height / 4);
            reactorInfo.setX(width / 2 - (reactorInfo.getWidth() / 2));
            reactorInfo.setY(reactorsNoServersIcon.getY() + reactorsNoServersIcon.getHeight() + (12 * 5));
            localNoServersIcon.setX(width / 2 - (localNoServersIcon.getWidth() / 2));
            localNoServersIcon.setY(height / 4);
            pteroNoServersIcon.setX(width / 2 - (pteroNoServersIcon.getWidth() / 2));
            pteroNoServersIcon.setY(height / 4);
            pteroInfo.setX(width / 2 - (pteroInfo.getWidth() / 2));
            pteroInfo.setY(pteroNoServersIcon.getY() + pteroNoServersIcon.getHeight() + (12 * 5));
        }
    }

    @Override
    public void tick() {
        super.tick();
        Object data = tabs().getActiveTab() != null ? tabs().getActiveTab().getData() : null;
        if (!(data instanceof RemoteHost) && !"RESTUDIO_MARKER".equals(data)) {
            pollPersistentLocalServerStates(getCurrentServers(), false);
        } else if (data instanceof RemoteHost host && "PTERO".equalsIgnoreCase(host.getType())) {
            pollPteroServerStates(host, false);
        }
    }

    private void pollPteroServerStates(RemoteHost host, boolean force) {
        if (host == null || instanceManager == null) return;
        long now = System.currentTimeMillis();
        if (pteroStatePollInFlight || (!force && now - lastPteroStatePollMs < PTERO_STATE_POLL_MS)) {
            return;
        }
        lastPteroStatePollMs = now;
        pteroStatePollInFlight = true;
        TabsManager.Tab activeTab = tabs().getActiveTab();
        instanceManager.fetchRemoteInstances(host).whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> {
            pteroStatePollInFlight = false;
            if (tabs().getActiveTab() == activeTab && activeTab != null && activeTab.getData() == host) {
                loadServersForTab(activeTab);
            }
        }));
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (reactorPlanSelectionVisible && event.key() == ReKey.ESCAPE) {
            hideReactorPlanSelection();
            return true;
        }
        if (reactorPlanSelectionVisible) {
            return true;
        }
        if (event.key() == ReKey.R && event.modifiers().control()) {
            reloadInstancesSmartly();
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (event.key() == ReKey.ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (activeContainer != null) {
            activeContainer.getWidgets().stream().filter(DesktopGroupWidget.class::isInstance).map(DesktopGroupWidget.class::cast).filter(DesktopGroupWidget::isExpanded).filter(group -> !group.isMouseOver(event.x(), event.y())).forEach(group -> group.setExpanded(false));
        }
        if (isMouseOverServerManagerContextMenu(event.x(), event.y())) {
            serverManagerContextMenuPressed = true;
            super.mouseClicked(event);
            return true;
        }
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseClicked(event.retarget(card, event.x(), event.y()))) {
                    return true;
                }
            }
            return true;
        }
        if (reactorInfo.isVisible() && reactorInfo.isHovered()) {
            return reactorInfo.mouseClicked(event.retarget(reactorInfo, event.x(), event.y()));
        }
        if (pteroInfo.isVisible() && pteroInfo.isHovered()) {
            return pteroInfo.mouseClicked(event.retarget(pteroInfo, event.x(), event.y()));
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        if (serverManagerContextMenuPressed) {
            serverManagerContextMenuPressed = false;
            return true;
        }
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseReleased(event.retarget(card, event.x(), event.y()))) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseDragged(event.retarget(card, event.x(), event.y(), event.deltaX(), event.deltaY()))) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseDragged(event);
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseScrolled(event.retarget(card, event.x(), event.y()))) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseScrolled(event);
    }

    @Override
    public void mouseMoved(ReMouseEvent event) {
        if (reactorPlanSelectionVisible) {
            for (ReactorPlanWidget card : reactorPlanCards) {
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                card.mouseMoved(event.retarget(card, event.x(), event.y()));
            }
        }
        super.mouseMoved(event);
    }

    public List<Instance> getRestudioInstances() {
        return restudioInstances;
    }

    @Override
    public void close() {
        if (parent == null && !remotelyClient.getHost().shouldCloseRootScreen()) {
            return;
        }
        remotelyClient.getHost().openParentScreen(this, parent);
    }
}
