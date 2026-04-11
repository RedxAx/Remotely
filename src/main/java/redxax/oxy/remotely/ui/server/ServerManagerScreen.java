package redxax.oxy.remotely.ui.server;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.config.SettingsScreenFactory;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.ui.widgets.DesktopIconWidget;
import redxax.oxy.remotely.ui.widgets.ReactorPlanWidget;
import redxax.oxy.remotely.ui.widgets.management.PlayerDataPopup;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.restudio.AuthStateListener;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.ui.screens.auth.ReStudioLoginScreen;
import restudio.rebase.ui.screens.feedback.FeedbackBrowserScreen;
import restudio.rebase.ui.screens.notification.InboxScreen;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.util.RebaseLogger;
import restudio.rebase.util.ssh.SSHManager;
import restudio.rescreen.Main;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopTaskbarHelper;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.ui.rescreen.layout.FreeLayout;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.BrowserUtils;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerManagerScreen extends ReScreen implements AuthStateListener {
    private final RemotelyClient remotelyClient;
    private Instance instanceForDeletion;
    private PopupWidget addServerPopup;
    private PopupWidget deleteServerPopup;
    private PopupWidget remoteHostPopup;
    private TextInputWidget remoteHostNameInput;
    private TextInputWidget remoteHostUserInput;
    private TextInputWidget remoteHostIpInput;
    private TextInputWidget remoteHostPortInput;
    private TextInputWidget remoteHostPasswordInput;
    private TextInputWidget remoteHostKeyPathInput;
    private TextInputWidget remoteHostKeyPassphraseInput;
    private TabSwitchWidget remoteHostAuthModeSwitch;
    private AnimatedButton remoteHostConfirmButton;
    private AnimatedButton remoteHostDeleteButton;
    private final Object parent;
    private IconButton userButton;
    private DesktopTaskbarHelper taskbarHelper;
    private boolean restudioTabRequested;
    int bx1 = 0, by1 = 0, bx2 = 0, by2 = 0;

    private static final String ROW_REMOTE_HOST_PASSWORD = "remoteHostPassword";
    private static final String ROW_REMOTE_HOST_AUTH_MODE = "remoteHostAuthMode";
    private static final String ROW_REMOTE_HOST_KEY_PATH = "remoteHostKeyPath";
    private static final String ROW_REMOTE_HOST_PASSPHRASE = "remoteHostPassphrase";

    private static BufferedImage unknown, serverIcon, paper, vanilla, fabric, forge, neoforge, waterfall, velocity, leaf, quilt, spigot, bukkit, purpur;
    private InstanceManager instanceManager;
    private final List<Instance> restudioInstances = new CopyOnWriteArrayList<>();
    private final Map<String, ServerModels.ClientServerView> restudioServerViews = new HashMap<>();
    private final ServerIconManager iconManager;
    private boolean initializedOnce;
    private volatile int remoteHostSelectionToken;
    private Container noServersOverlay;
    private boolean noServersOverlayVisible;
    private boolean forceNoServersOverlay = false;
    private IconMessage localNoServersIcon;
    private IconMessage reactorsNoServersIcon;
    private IconButton reactorInfo;
    private final List<ReactorPlanWidget> reactorPlanCards = new ArrayList<>();
    private final List<ServerModels.Plan> reactorPlans = new ArrayList<>();
    private String selectedReactorPlanName;
    private boolean reactorPlanSelectionVisible;
    private static final int REACTOR_PLAN_CARD_GAP = 14;
    private static final int REACTOR_PLAN_CARD_SIDE_MARGIN = 14;

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
        if (initializedOnce) {
            ReStudio.getInstance().addListener(this);
            initNoServersOverlay();
            if (restudio.rescreen.config.Config.desktopMode && taskbarHelper != null) {
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
        reloadInstancesSmartly();
        loadIcons();
        createPopups();
        ReStudio.getInstance().addListener(this);

        Map<String, BufferedImage> defaultIcons = new HashMap<>();
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

        int taskbarHeight = 28;
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

        ReStudio.getInstance().loadAvatar().thenAccept(img -> ScreenManager.getInstance().execute(() -> {
            if (img != null && userButton != null) {
                userButton.setIcon(img);
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

        header().position(HeaderBuilder.Position.BOTTOM).size(taskbarHeight)
            .addLeft(terminalButton)
            .addLeft(fileExplorerButton)
            .addLeft(settingsButton)
            .addLeft(userButton)
            .build();

        if (restudio.rescreen.config.Config.desktopMode) {
            taskbarHelper = new DesktopTaskbarHelper(this, header(), header().leftButtons.size(), () -> width / 2);
            taskbarHelper.pinApp("server-details", terminalButton, () -> remotelyClient.openMultiTerminal(this), "Terminal");
            taskbarHelper.pinApp("file-explorer", fileExplorerButton, this::openFileExplorer, "File Explorer");
            taskbarHelper.pinApp("global-settings", settingsButton, () -> client.setScreen(SettingsScreenFactory.createGlobalSettingsScreen(this, (RemotelyConfigManager) Rebase.get().getConfigManager())), "Settings");
            taskbarHelper.attach();
        } else {
            taskbarHelper = null;
        }

        tabs().builder()
            .position(width / 2, height - taskbarHeight + 5)
            .size(width / 2 - 5, 18)
            .rightToLeft(true)
            .allowAdd(true)
            .allowRename(false).allowReorder(false).allowClose(false)
            .onPlusButtonClicked(() -> openRemoteHostPopup(false))
            .onTabSelected(this::onHostTabSelected)
            .onTabRenamed(this::onHostTabRenamed)
            .build();

        Container desktopContainer = createContainer("desktop", 0, 0, width, height - 35);
        DesktopLayout localLayout = new DesktopLayout();
        localLayout.setOnReorder(() -> saveServerOrder(desktopContainer, null));
        desktopContainer.layout(localLayout).backgroundDrawing(false).enableSelecting(true).enableDoubleClick(false).disableScissorRegion(true).enableDoubleClick(false);

        setActiveContainer(desktopContainer);
        initNoServersOverlay();
        populateHostTabs();
        updatePositions();
        initializedOnce = true;
    }

    protected void setupNoServersOverlay(Container overlayContainer) {
        localNoServersIcon = new IconMessage(width / 2 - 32, height / 4, 64, 64, "Welcome To Remotely!\nI Guess We're Locally Now...\nClick New Server To Start!\n\n\n Quick Tips:\nMiddle Click To Close Tabs\nSign In To Report Bugs & Give Feedback\nThere's A Very Powerfull Desktop Mode In The Settings!", "remotely.png");
        reactorsNoServersIcon = new IconMessage(width / 2 - 32, height / 4, 64, 64, "Reactor By ReStudio\nHigh-End & Affordable Hosting For Everyone.\nOrder And Control Your Server Right Here & Now!", "Reactor.png");
        reactorInfo = new IconButton.Builder().size(300, 18).label("Learn More Here").imagePath("external").autoWidthOnTextChange(true).onClick(() -> BrowserUtils.openBrowser("https://restudiomc.net/hosting")).build();
        reactorInfo.setX(width / 2 - (reactorInfo.getWidth() / 2));
        reactorInfo.setY(reactorsNoServersIcon.getY() + reactorsNoServersIcon.getHeight() + (12 * 5));

        noServersOverlay.addWidget(localNoServersIcon);
        noServersOverlay.addWidget(reactorsNoServersIcon);
        noServersOverlay.addWidget(reactorInfo);
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
                if (planIndex < 0 || planIndex >= reactorPlans.size()) {
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
        noServersOverlay = createContainer("server_manager_no_servers_overlay", 0, 0, width, height - 35);
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
            .addHeaderButton("chat.png", () -> ScreenManager.getInstance().setScreen(new InboxScreen(this)), "Inbox")
            .addHeaderButton("report.png", () -> ScreenManager.getInstance().setScreen(new FeedbackBrowserScreen(this, "Remotely")), "Reports And Feedback")
            .addHeaderButton("close.png", () -> ReStudio.getInstance().logoutFromWorkOs(), "Log Out", ThemeManager.getAccent("danger"));

        showContextMenu(userButton.getX(), height - 35, builder);
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
        if (localNoServersIcon != null) localNoServersIcon.setVisible(!isReactors);
        if (reactorsNoServersIcon != null) reactorsNoServersIcon.setVisible(isReactors);
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
            new Notification("Session Expired", "Your session has expired. Please log in again.", Notification.Type.WARN);
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

    private void refreshAccountButton() {
        if (userButton == null) {
            return;
        }
        String displayName = ReStudio.getInstance().getDisplayName();
        if (displayName == null || displayName.isBlank()) displayName = ReStudio.getInstance().getUsername();
        if (displayName == null || displayName.isBlank()) displayName = ReStudio.getInstance().getEmail();
        if (displayName == null || displayName.isBlank()) displayName = "Account";
        userButton.setMessage(displayName);
        userButton.setOnClick(this::onAccountButtonClick);
        if (ReStudio.getInstance().isAuthenticated()) {
            ReStudio.getInstance().loadAvatar().thenAccept(img -> ScreenManager.getInstance().execute(() -> {
                if (img != null && userButton != null) {
                    userButton.setIcon(img);
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
                SSHManager mgr = host.getExistingSshManager();
                if (mgr != null && mgr.isConnected()) {
                    activeSessions.put(host.hostId, mgr);
                }
            }

            instanceManager.loadInstances();
            iconManager.clearAllRemoteTracking();

            for (RemoteHost host : instanceManager.getRemoteHosts()) {
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
            serverIcon = loadResourceIcon("server.png");
            unknown = loadResourceIcon("unknown.png");
            paper = loadResourceIcon("paper.png");
            vanilla = loadResourceIcon("vanilla.png");
            fabric = loadResourceIcon("fabric.png");
            forge = loadResourceIcon("forge.png");
            neoforge = loadResourceIcon("neoforge.png");
            waterfall = loadResourceIcon("waterfall.png");
            velocity = loadResourceIcon("velocity.png");
            leaf = loadResourceIcon("leaf.png");
            quilt = loadResourceIcon("quilt.png");
            spigot = loadResourceIcon("spigot.png");
            bukkit = loadResourceIcon("bukkit.png");
            purpur = loadResourceIcon("purpur.png");
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void populateHostTabs() {
        tabs().addTab("Local", activeContainer).setData(null);

        for (RemoteHost host : instanceManager.getRemoteHosts()) {
            Container c = createContainer("desktop_remote_" + host.name, 0, 0, width, height - 35);
            DesktopLayout remoteLayout = new DesktopLayout();
            remoteLayout.setOnReorder(() -> saveServerOrder(c, host));
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
        Container container = createContainer("desktop_restudio", 0, 0, width, height - 35);
        DesktopLayout remoteLayout = new DesktopLayout();
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
        restudioTabRequested = false;
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

        if (!order.isEmpty()) {
            Map<String, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                orderMap.put(order.get(i), i);
            }

            instances.sort((a, b) -> {
                int idxA = orderMap.getOrDefault(getWidgetKey(a), Integer.MAX_VALUE);
                int idxB = orderMap.getOrDefault(getWidgetKey(b), Integer.MAX_VALUE);
                return Integer.compare(idxA, idxB);
            });
        }

        Map<String, DesktopIconWidget> existingWidgets = new HashMap<>();
        DesktopIconWidget createButton = null;

        for (AnimatedWidget w : targetContainer.getWidgets()) {
            if (w instanceof DesktopIconWidget diw) {
                if (diw.isCreateButton()) {
                    createButton = diw;
                } else if (diw.getInstance() != null) {
                    existingWidgets.put(getWidgetKey(diw.getInstance()), diw);
                }
            }
        }

        List<AnimatedWidget> toKeep = new ArrayList<>();
        for (Instance server : instances) {
            DesktopIconWidget existing = existingWidgets.get(getWidgetKey(server));
            if (existing != null) {
                existing.setInstance(server);
                toKeep.add(existing);
                existingWidgets.remove(getWidgetKey(server));
            } else {
                DesktopIconWidget widget = createServerWidget(server, false);
                toKeep.add(widget);
            }
        }

        if (createButton == null) {
            createButton = createServerWidget(null, true);
        }
        toKeep.add(createButton);

        targetContainer.clearWidgets();
        for (AnimatedWidget w : toKeep) {
            targetContainer.addWidget(w);
        }

        targetContainer.updateWidgetPositions();
        if (tab == tabs().getActiveTab()) {
            updateNoServersOverlayVisibility(forceNoServersOverlay || instances.isEmpty());
        }
    }

    private String getWidgetKey(Instance inst) {
        if (inst == null) return "create_button";
        BackendConfig config = inst.getBackendConfig();
        if (config != null && "RESTUDIO".equalsIgnoreCase(config.type)) {
            String identifier = config.credentials.get("identifier");
            if (identifier != null) return "RESTUDIO_" + identifier;
        }
        return inst.getInstanceId();
    }

    private void saveServerOrder(Container container, RemoteHost host) {
        List<String> newOrderIds = new ArrayList<>();
        for (AnimatedWidget w : container.getWidgets()) {
            if (w instanceof DesktopIconWidget diw && !diw.isCreateButton()) {
                newOrderIds.add(getWidgetKey(diw.getInstance()));
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

    private DesktopIconWidget createServerWidget(Instance info, boolean isCreate) {
        DesktopIconWidget widget = new DesktopIconWidget.Builder(info, isCreate, isCreate ? serverIcon : iconManager.getQuickIcon(info))
            .onClick(this::onDesktopIconClick)
            .build();
        if (isCreate || info == null) {
            return widget;
        }
        iconManager.loadIconAsync(info, widget::setIcon);
        BackendConfig backendConfig = info.getBackendConfig();
        if (backendConfig != null && !"LOCAL".equalsIgnoreCase(backendConfig.type)) {
            iconManager.loadRemoteIconAsync(info, () -> iconManager.loadIconAsync(info, widget::setIcon));
        }
        return widget;
    }

    private void addServerWidget(Instance info, boolean isCreate) {
        activeContainer.addWidget(createServerWidget(info, isCreate));
    }

    private void onHostTabSelected(TabsManager.Tab tab) {
        int selectionToken = ++remoteHostSelectionToken;
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        setActiveContainer(tab.getContainer());

        Object data = tab.getData();
        if (data instanceof RemoteHost host) {
            CompletableFuture.supplyAsync(() -> host.getSshManager().isConnected())
                .exceptionally(e -> false)
                .thenAccept(connected -> ScreenManager.getInstance().execute(() -> handleRemoteHostTabSelected(tab, host, selectionToken, connected)));
        } else if ("RESTUDIO_MARKER".equals(data)) {
            fetchReStudioServers();
        }

        loadServersForCurrentTab();
        Main.setTitle(tab.getName() + " Host - Remotely Server Manager");
    }

    private void handleRemoteHostTabSelected(TabsManager.Tab tab, RemoteHost host, int selectionToken, boolean connected) {
        if (selectionToken != remoteHostSelectionToken) {
            return;
        }
        if (tabs().getActiveTab() != tab) {
            return;
        }
        if (!connected) {
            if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("calm"));
            connectRemoteHostAsync(host, () -> {
                if (selectionToken != remoteHostSelectionToken || tabs().getActiveTab() != tab) {
                    return;
                }
                if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getDefaultAccent());
                instanceManager.fetchRemoteInstances(host)
                    .whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> {
                        if (selectionToken != remoteHostSelectionToken || tabs().getActiveTab() != tab) {
                            return;
                        }
                        loadServersForTab(tab);
                    }));
            }, () -> {
                if (selectionToken != remoteHostSelectionToken || tabs().getActiveTab() != tab) {
                    return;
                }
                if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("danger"));
            });
            return;
        }
        if (tab.getWidget() != null) {
            tab.getWidget().setAccent(ThemeManager.getDefaultAccent());
        }
        if (instanceManager.getRemoteInstances(host).isEmpty()) {
            if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("calm"));
            instanceManager.fetchRemoteInstances(host)
                .whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> {
                    if (selectionToken != remoteHostSelectionToken || tabs().getActiveTab() != tab) {
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
                            if ("running".equals(currentState)) {
                                inst.setState(InstanceState.RUNNING);
                            } else if ("starting".equals(currentState)) {
                                inst.setState(InstanceState.STARTING);
                            } else if ("offline".equals(currentState)) {
                                inst.setState(InstanceState.STOPPED);
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

    private void onDesktopIconClick(DesktopIconWidget widget, int button) {
        if (button == 0) {
            if (widget.isCreateButton()) {
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
                boolean isReStudioTab = "RESTUDIO_MARKER".equals(tabs().getActiveTab().getData());
                addServerPopup.setRowVisibility("importServerRow", !isReStudioTab);
                addServerPopup.setX((this.width - addServerPopup.getWidth())/2);
                addServerPopup.setY((this.height - addServerPopup.getHeight())/2);
                addServerPopup.show();
            } else {
                openServerScreen(widget.getInstance());
            }
        } else if (button == 1) {
            if (!widget.isCreateButton()) {
                activeContainer.clearSelection();
                activeContainer.addSelectedWidget(widget);

                Instance inst = widget.getInstance();
                RemoteHost rh = null;
                boolean isRestudio = false;

                if (inst.getBackendConfig() != null) {
                    if ("RESTUDIO".equalsIgnoreCase(inst.getBackendConfig().type)) {
                        isRestudio = true;
                    } else if (!"LOCAL".equalsIgnoreCase(inst.getBackendConfig().type)) {
                        for(RemoteHost h : instanceManager.getRemoteHosts()) {
                            if(inst.getBackendConfig().credentials.getOrDefault("host", "").equals(h.getIp())) {
                                rh = h;
                                break;
                            }
                        }
                    }
                }

                RemoteHost finalRh = rh;
                ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);

                if (inst.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(inst.getBackendConfig().type)) {
                    builder.addHeaderButton("merge.png", () -> remotelyClient.openServerTwin(this, inst), "DevMode");
                }
                builder.addHeaderButton("edit.png", () -> client.setScreen(new ServerConfigurationScreen(this, widget.getInstance(), finalRh, remotelyClient)), "Edit Server's Settings");
                builder.addHeaderButton("explorer.png", () -> client.setScreen(new FileExplorerScreen(this, widget.getInstance(), Path.of(widget.getInstance().getPath()), remotelyDir, false) {
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
                    builder.addHeaderButton("map.png", () -> openWorldScreen(widget.getInstance()), "View World Map");
                }

                final ServerModels.ClientServerView flowServerView = (inst.getBackendConfig() != null && "RESTUDIO".equalsIgnoreCase(inst.getBackendConfig().type))
                        ? restudioServerViews.get(inst.getName()) : null;
                builder.addHeaderButton("ReSync.png", () -> remotelyClient.openFlowManager(this, inst, flowServerView), "Flow Manager");
                if (!isRestudio) {
                    builder.addHeaderButton("copy.png", () -> duplicateInstance(inst), "Duplicate Server").addHeaderButton("delete.png", () -> {
                        instanceForDeletion = widget.getInstance();
                        deleteServerPopup.setX((this.width - deleteServerPopup.getWidth())/2);
                        deleteServerPopup.setY((this.height - deleteServerPopup.getHeight())/2);
                        deleteServerPopup.show();
                    }, "Show Deletion Options", ThemeManager.getAccent("danger"));
                }

                builder.addIconItem("Customize Icon", "shades.png", () -> customizeIcon(inst, finalRh), "");
                showContextMenu(widget.getX() + widget.getWidth() + 4, widget.getY() + 24, builder);
            }
        }
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
        List<BufferedImage> images = iconManager.loadIconAssets();

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
        Main.setTitle("Viewing " + instance.getName() + "'s Map - Remotely World Viewer");
    }

    private void createPopups() {
        createAddServerPopup();
        createDeleteServerPopup();
        createRemoteHostPopup();
        ensureReactorPlanSelectionCardsCreated();
    }

    private void createAddServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Add a Server")
            .size(260, 140)
            .onClose(() -> addServerPopup.hide());

        IconButton createBtn = new IconButton.Builder()
            .label(("Create And Customize An Empty Server"))
            .imagePath("create.png")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                Object data = (tabs().getActiveTab() != null) ? tabs().getActiveTab().getData() : null;
                if ("RESTUDIO_MARKER".equals(data)) {
                    openReactorPlanSelection();
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

        builder.addRow("", true, 27, createBtn);
        builder.addRow("", true, 27, modpackBtn);
        builder.addRow("importServerRow", "", true, 27, importBtn);

        addServerPopup = builder.build();
        addServerPopup.hide();
        addDrawableChild(addServerPopup);
    }

    private void createDeleteServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Are You Sure?").size(124, 140).onClose(() -> deleteServerPopup.hide());

        IconButton deleteTrashBtn = new IconButton.Builder()
            .label(("Delete The Server"))
            .imagePath("delete.png")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                playSound(Sound.DELETE);
                deleteServerPopup.hide();
                if (instanceForDeletion == null) {
                    return;
                }
                Instance deletingInstance = instanceForDeletion;
                instanceForDeletion = null;
                Notification notification = new Notification.Builder()
                    .message("Deleting Server")
                    .description(deletingInstance.getName())
                    .type(Notification.Type.INFO)
                    .loading(true)
                    .autoSlideOut(false)
                    .build();
                CompletableFuture.runAsync(() -> instanceManager.removeInstance(deletingInstance))
                    .whenComplete((v, throwable) -> ScreenManager.getInstance().execute(() -> {
                        Throwable error = throwable;
                        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
                            error = completionException.getCause();
                        }
                        if (error != null) {
                            notification.update()
                                .message("Delete Failed")
                                .description(error.getMessage() != null && !error.getMessage().isBlank() ? error.getMessage() : deletingInstance.getName())
                                .type(Notification.Type.ERROR)
                                .loading(false)
                                .autoSlideOut(true)
                                .commit();
                            return;
                        }
                        loadServersForCurrentTab();
                        notification.update()
                            .message("Server Deleted")
                            .description(deletingInstance.getName())
                            .type(Notification.Type.SUCCESS)
                            .loading(false)
                            .autoSlideOut(true)
                            .commit();
                    }));
            })
            .build();

        IconButton remove = new IconButton.Builder()
            .label(("Hide From List"))
            .imagePath("hide.png")
            .onClick(() -> {
                playSound(Sound.CLICK);
                if (instanceForDeletion != null) {
                    BackendConfig backend = instanceForDeletion.getBackendConfig();
                    if (backend != null && "RESTUDIO".equalsIgnoreCase(backend.type)) {
                        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
                        config.hideRestudioServer(instanceForDeletion.getName());
                    } else {
                        instanceForDeletion.setHidden(true);
                        instanceForDeletion.save();
                    }
                    loadServersForCurrentTab();
                }
                deleteServerPopup.hide();
            })
            .build();

        builder.addRow("", true, 20, deleteTrashBtn);
        builder.addRow("", true, 20, remove);

        deleteServerPopup = builder.build();
        deleteServerPopup.hide();
        addDrawableChild(deleteServerPopup);
    }

    private void openReactorPlanSelection() {
        ensureReactorPlanSelectionCardsCreated();
        hideReactorPlanSelection();
        selectedReactorPlanName = null;
        reactorPlans.clear();

        List<ServerModels.Plan> fallbackPlans = List.of(
                createFallbackPlan("Starter", 4096, 20480, 125, 1, 2, 1, 699),
                createFallbackPlan("Standard", 8192, 40960, 200, 3, 4, 2, 1499),
                createFallbackPlan("Pro", 12288, 61440, 300, 5, 8, 3, 2499)
        );

        ReStudio.getInstance().getApi().getPlans().thenAccept(plans -> ScreenManager.getInstance().execute(() -> {
            List<ServerModels.Plan> source = plans == null || plans.isEmpty() ? fallbackPlans : plans;
            source.stream()
                    .filter(Objects::nonNull)
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
                createFallbackPlan("Starter", 4096, 20480, 125, 1, 2, 1, 699),
                createFallbackPlan("Standard", 8192, 40960, 200, 3, 4, 2, 1499),
                createFallbackPlan("Pro", 12288, 61440, 300, 5, 8, 3, 2499)
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
            if (selectedReactorPlanName != null && plan.name != null && plan.name.equalsIgnoreCase(selectedReactorPlanName)) {
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
        return Math.max(lowerBound, Math.min(upperBound, byLayout));
    }

    private int resolveReactorPlanCardHeight(int cardWidth) {
        if (!reactorPlanCards.isEmpty()) {
            return reactorPlanCards.getFirst().getPreferredHeight();
        }
        return Math.max(1, Math.round(cardWidth * 0.45f));
    }

    private ServerModels.Plan createFallbackPlan(String name, int memoryMb, int diskMb, int cpuPercent, int databases, int backups, int allocations, long priceCents) {
        ServerModels.Plan plan = new ServerModels.Plan();
        plan.name = name;
        plan.memoryMb = memoryMb;
        plan.diskMb = diskMb;
        plan.cpuPercent = cpuPercent;
        plan.databases = databases;
        plan.backups = backups;
        plan.allocations = allocations;
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
            .size(360, 250).setResizable(true)
            .setAntiOutOfBound(true).setBoundOffset(header().headerSize)
            .setMinSize(360, 250);

        remoteHostNameInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow("Host Name", true, 18, remoteHostNameInput);

        remoteHostUserInput = new TextInputWidget.Builder().size(18, 18).text("root").build();
        builder.addRow("User Name", true, 18, remoteHostUserInput);

        remoteHostIpInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow("IP Or Domain", true, 18, remoteHostIpInput);

        remoteHostPortInput = new TextInputWidget.Builder().size(18, 18).text("22").build();
        builder.addRow("Port", true, 18, remoteHostPortInput);

        remoteHostPasswordInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow(ROW_REMOTE_HOST_PASSWORD, "Password", true, 18, remoteHostPasswordInput);

        remoteHostAuthModeSwitch = new TabSwitchWidget.Builder().options(List.of("Password", "SSH Key")).currentIndex(0).build();
        builder.addRow(ROW_REMOTE_HOST_AUTH_MODE, "Auth Mode", true, 18, remoteHostAuthModeSwitch);

        remoteHostKeyPathInput = new TextInputWidget.Builder().size(18, 18).placeholder("Leave Empty For Auto Discovery").build();
        builder.addRow(ROW_REMOTE_HOST_KEY_PATH, "Key Path", true, 18, remoteHostKeyPathInput);

        remoteHostKeyPassphraseInput = new TextInputWidget.Builder().size(18, 18).build();
        builder.addRow(ROW_REMOTE_HOST_PASSPHRASE, "Passphrase", true, 18, remoteHostKeyPassphraseInput);

        remoteHostPopup = builder.build();
        remoteHostPopup.hide();
        addDrawableChild(remoteHostPopup);
    }

    private void connectRemoteHostAsync(RemoteHost hostInfo, Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                if (hostInfo.getSshManager().connect()) {
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
                if (hostInfo.getSshManager().connect()) {
                    ScreenManager.getInstance().execute(() -> {
                        notification.update()
                            .description("Connecting... 100%")
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

        AnimatedButton cancelButton;

        if (isEditing && activeTabIndex > 0 && data instanceof RemoteHost host) {
            nameText = host.name;
            userText = host.user;
            ipText = host.ip;
            portText = String.valueOf(host.port);
            passwordText = host.getPassword() != null ? host.getPassword() : "";

            remoteHostConfirmButton = new AnimatedButton.Builder().label(("Save")).size(18, 18).accentType(ThemeManager.getAccent("nice")).onClick(this::onConfirmRemoteHost).build();
            cancelButton = new AnimatedButton.Builder().label(("Cancel")).size(18, 18).onClick(this::closeRemoteHostPopup).build();
            remoteHostDeleteButton = new AnimatedButton.Builder().label(("Delete")).size(18, 18).onClick(this::onDeleteRemoteHost).accentType(ThemeManager.getAccent("danger")).build();
        } else {
            remoteHostConfirmButton = new AnimatedButton.Builder().label(("Test & Add")).size(18, 18).accentType(ThemeManager.getAccent("nice")).onClick(this::onConfirmRemoteHost).build();
            cancelButton = new AnimatedButton.Builder().label(("Cancel")).size(18, 18).onClick(this::closeRemoteHostPopup).build();
            remoteHostDeleteButton = null;
        }

        String authModeText = "PASSWORD";
        String keyPathText = "";
        if (isEditing && activeTabIndex > 0 && data instanceof RemoteHost host) {
            authModeText = host.getAuthMode();
            keyPathText = host.getKeyPath() != null ? host.getKeyPath() : "";
        }

        remoteHostNameInput.setText(nameText);
        remoteHostUserInput.setText(userText);
        remoteHostIpInput.setText(ipText);
        remoteHostPortInput.setText(portText);
        remoteHostPasswordInput.setText(passwordText);
        remoteHostAuthModeSwitch.setCurrentIndex("KEY".equalsIgnoreCase(authModeText) ? 1 : 0);
        remoteHostKeyPathInput.setText(keyPathText);
        remoteHostKeyPassphraseInput.setText("");

        remoteHostPopup.addRow("Host Name", Collections.singletonList(remoteHostNameInput), 18, true, false);
        remoteHostPopup.addRow("User Name", Collections.singletonList(remoteHostUserInput), 18, true, false);
        remoteHostPopup.addRow("IP Or Domain", Collections.singletonList(remoteHostIpInput), 18, true, false);
        remoteHostPopup.addRow("Port", Collections.singletonList(remoteHostPortInput), 18, true, false);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_AUTH_MODE, "Auth Mode", Collections.singletonList(remoteHostAuthModeSwitch), 18, true, false);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_PASSWORD, "Password", Collections.singletonList(remoteHostPasswordInput), 18, true, false);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_KEY_PATH, "Key Path", Collections.singletonList(remoteHostKeyPathInput), 18, true, false);
        remoteHostPopup.addRow(ROW_REMOTE_HOST_PASSPHRASE, "Passphrase", Collections.singletonList(remoteHostKeyPassphraseInput), 18, true, false);

        if (isEditing && activeTabIndex > 0 && data instanceof RemoteHost) {
            remoteHostPopup.addRow("", Arrays.asList(remoteHostConfirmButton, cancelButton, remoteHostDeleteButton), 18, true, false);
        } else {
            remoteHostPopup.addRow("", Arrays.asList(remoteHostConfirmButton, cancelButton), 18, true, false);
        }

        remoteHostAuthModeSwitch.setOnChange(this::updateRemoteHostAdvancedVisibility);
        updateRemoteHostAdvancedVisibility();

        remoteHostNameInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostUserInput));
        remoteHostUserInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostIpInput));
        remoteHostIpInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostPortInput));
        remoteHostPortInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostPasswordInput));
        remoteHostPasswordInput.addOnEnter((w) -> {
            if (remoteHostAuthModeSwitch.getCurrentIndex() == 1) {
                remoteHostPopup.setFocusedWidget(remoteHostKeyPathInput);
            } else {
                onConfirmRemoteHost();
            }
        });
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

        host.name = remoteHostNameInput.getText();
        host.user = remoteHostUserInput.getText();
        host.ip = remoteHostIpInput.getText();
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
                .autoSlideOut(false)
                .build();
            testRemoteHostAsync(host, notification, () -> {
                instanceManager.addRemoteHost(host);
                Container c = createContainer("desktop_remote_" + host.name, 0, 0, width, height - 35);
                DesktopLayout remoteLayout = new DesktopLayout();
                remoteLayout.setOnReorder(() -> saveServerOrder(c, host));
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
        boolean useKey = remoteHostAuthModeSwitch != null && remoteHostAuthModeSwitch.getCurrentIndex() == 1;
        if (remoteHostPopup != null) {
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_PASSWORD, !useKey);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_AUTH_MODE, true);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_KEY_PATH, useKey);
            remoteHostPopup.setRowVisibility(ROW_REMOTE_HOST_PASSPHRASE, useKey);
        }
        if (remoteHostPasswordInput != null) {
            remoteHostPasswordInput.setActive(!useKey);
            remoteHostPasswordInput.setVisible(!useKey);
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
            remoteHostAuthModeSwitch.setActive(true);
            remoteHostAuthModeSwitch.setVisible(true);
        }
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

    private String flowAvailabilityMessage(String issue) {
        if (remotelyClient == null || remotelyClient.getFlowManager() == null) {
            return "ReSync Isn't Installed/Enabled";
        }
        return remotelyClient.getFlowManager().normalizeReSyncNotificationMessage(issue);
    }

    public void openGuiDesignerForServer(String serverId, ServerModels.ClientServerView server) {
        FlowManager flowManager = remotelyClient.getFlowManager();
        if (flowManager != null) {
            if (server == null) {
                new Notification.Builder().message("GUI Designer only works with ReStudio servers").type(Notification.Type.WARN).build();
                return;
            }
            flowManager.openGuiDesigner(serverId, server);
        } else {
            new Notification.Builder().message("Flow Manager not available").type(Notification.Type.WARN).build();
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
            creds.put("host", host.getIp());
            creds.put("port", String.valueOf(host.getPort()));
            creds.put("user", host.getUser());
            creds.put("authMode", host.getAuthMode());
            creds.put("hostId", host.hostId);
            String password = host.getPassword();
            if (password != null && !password.isBlank()) {
                creds.put("password", password);
            }
            if (host.getKeyPath() != null && !host.getKeyPath().isBlank()) {
                creds.put("keyPath", host.getKeyPath());
            }
            BackendConfig config = new BackendConfig("SSH", creds);
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
        ReStudio.getInstance().removeListener(this);
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
        if (System.currentTimeMillis() - lastReloadTime > 5000) {
            reloadInstancesSmartly();
            lastReloadTime = System.currentTimeMillis();
        }
        loadServersForCurrentTab();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        tabs().setPosition(width - tabs().getWidth(), height - 28 + 5);
        positionReactorPlanSelectionCards();
        if (noServersOverlay != null) {
            noServersOverlay.setPosition(0, 0);
            noServersOverlay.setSize(width, height - 35);
            noServersOverlay.updateWidgetPositions();
            reactorsNoServersIcon.setX(width / 2 - (reactorsNoServersIcon.getWidth() / 2));
            reactorsNoServersIcon.setY(height / 4);
            reactorInfo.setX(width / 2 - (reactorInfo.getWidth() / 2));
            reactorInfo.setY(reactorsNoServersIcon.getY() + reactorsNoServersIcon.getHeight() + (12 * 5));
            localNoServersIcon.setX(width / 2 - (localNoServersIcon.getWidth() / 2));
            localNoServersIcon.setY(height / 4);

        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (activeContainer != null && activeContainer.getLayout() instanceof DesktopLayout layout) {
            int accentColor = ThemeManager.getDefaultAccent().getAccentColor();
            int fill = ThemeManager.getAnimatedColor("selection_fill".hashCode(), layout.isSelecting() ? ((accentColor & 0x00FFFFFF) | 0x44000000) : 0x00000000);
            int border = ThemeManager.getAnimatedColor("selection_border".hashCode(), layout.isSelecting() ? ((accentColor & 0x00FFFFFF) | 0xAA000000) : 0x00000000);

            if (layout.isSelecting()) {
                double[] box = layout.getSelectionBox();
                if (box != null) {
                    bx1 = (int) box[0];
                    by1 = (int) box[1];
                    bx2 = (int) box[2];
                    by2 = (int) box[3];
                }
            }
            context.fill(bx1, by1, bx2, by2, fill);
            context.fillBorder(bx1, by1, bx2, by2, 1, border);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (reactorPlanSelectionVisible && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            hideReactorPlanSelection();
            return true;
        }
        if (reactorPlanSelectionVisible) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R && hasControlDown()) {
            reloadInstancesSmartly();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && RemotelyClient.INSTANCE.getHost().getGameVersion() == null) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return true;
        }
        if (reactorInfo.isHovered()) {
            return reactorInfo.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (reactorPlanSelectionVisible) {
            for (int i = reactorPlanCards.size() - 1; i >= 0; i--) {
                ReactorPlanWidget card = reactorPlanCards.get(i);
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                if (card.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (reactorPlanSelectionVisible) {
            for (ReactorPlanWidget card : reactorPlanCards) {
                if (!card.isVisible() || !card.isActive()) {
                    continue;
                }
                card.mouseMoved(mouseX, mouseY);
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    public List<Instance> getRestudioInstances() {
        return restudioInstances;
    }

    @Override
    public void close() {
        remotelyClient.getHost().openParentScreen(this, parent);
    }
}
