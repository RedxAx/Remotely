package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.discord.DiscordRpcBridge;
import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.servers.ReProxyManager;
import redxax.oxy.remotely.session.TerminalSession;
import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import redxax.oxy.remotely.ui.server.containers.ResourceContainer;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedExecutionProvider;
import restudio.rebase.api.unified.internal.StandardOutputStateParser;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.feature.DataStreamFeature;
import restudio.rebase.backend.feature.ResourceUsageFeature;
import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.backend.impl.ReStudioBackend;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceFactory;
import restudio.rebase.instance.InstanceRepairer;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LocalServerControllerModels;
import restudio.rebase.localcontrol.LocalServerProcessDetector;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.instance.InstanceDetailsScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.Main;
import restudio.rescreen.debug.DebugManager;
import restudio.rescreen.debug.IDebugInfoProvider;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.*;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static restudio.rescreen.config.Config.desktopMode;

public class ServerDetailsScreen extends InstanceDetailsScreen implements IDebugInfoProvider, DesktopWindowBehaviorProvider {

    private final RemotelyClient remotelyClient;
    private final Object parent;
    private final Instance initialInstanceToOpen;
    private IconButton startIconButton;
    private Instance sidecarInstance;

    private final Map<TabContext, TerminalSession> contextInfos = new HashMap<>();
    private ScheduledExecutorService statusScheduler;
    private SearchMode headerSearchMode;
    private SearchMode resourcesSearchMode;
    private SearchMode playersSearchMode;
    private final Set<String> localControllerFailureNotices = new HashSet<>();
    private static final long LOCAL_STOP_GRACE_MS = 15_000;
    private static final long KILL_CONFIRM_MS = 5_000;
    private String killConfirmInstanceId;
    private String killingInstanceId;
    private long killConfirmUntilMs;
    private static final int TERMINAL_SCROLLBAR_WIDTH = 2;
    private final ScrollbarController terminalScrollbarController = new ScrollbarController();

    public ServerDetailsScreen(Object parent, RemotelyClient client) {
        this(parent, client, null);
    }

    public ServerDetailsScreen(Object parent, RemotelyClient client, Instance initialInstanceToOpen) {
        super(parent instanceof Screen ? (Screen) parent : null, null);
        this.parent = parent;
        this.remotelyClient = client;
        this.initialInstanceToOpen = initialInstanceToOpen;
    }

    @Override
    public void init() {
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            statusScheduler = null;
        }
        statusContexts.clear();
        super.init();
        statusBar().size(14).visible(false).build();
        applyStatusBarForActiveTab();
        startStatusScheduler();
        header().reset();
        setupHeader();
        TabContext ctx = getActiveContext();
        if (ctx != null && ctx.selectedViewIndex < ctx.views.size()) {
            onViewChanged(ctx, ctx.views.get(ctx.selectedViewIndex));
        }
    }

    public String getDesktopAppId() {
        return "server-details";
    }

    public String getDesktopAppTitle() {
        return "Terminal";
    }

    public String getDesktopAppIconPath() {
        return "terminal.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.MERGE_INTO_EXISTING;
    }

    @Override
    public boolean mergeIntoExistingDesktopWindow(Screen existingScreen) {
        if (!(existingScreen instanceof ServerDetailsScreen existing)) {
            return false;
        }
        if (initialInstanceToOpen != null) {
            existing.addInstanceTab(initialInstanceToOpen);
        }
        return true;
    }

    @Override
    public void close() {
        if (desktopMode && isDesktopWindow()) {
            var overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
            if (overlay != null) {
                overlay.requestCloseWindowForScreen(this);
                return;
            }
        }
        remotelyClient.getHost().openParentScreen(this, parent);
    }

    public void closeScreen() {
        close();
    }

    @Override
    protected void setupHeader() {
        if (!desktopMode) {
            header().addRight("close.png", this::closeScreen, "Close");
        }
        header().addRight("ReSync.png", this::openReSyncStudio, "ReSync");
        header().addRight("explorer.png", this::exploreInstanceFiles, "File Explorer");
        header().addRight("edit.png", this::openInstanceSettings, "Server Settings");
        header().addRight("merge.png", this::openDevModeScreen, "DevMode");

        startIconButton = new IconButton.Builder()
            .imagePath("start.png")
            .onClick(this::launchOrStopInstance)
            .hint("Start Server")
            .accentType(ThemeManager.getAccent("nice"))
            .size(18, 18)
            .elevateOnFocused(false)
            .animateLayout(true)
            .autoWidthOnTextChange(true)
            .build();
        header().addLeft(startIconButton);

        header().addLeft("resources.png", () -> {
            TerminalSession info = getCurrentInfo();
            if (info != null && !info.isLocalTerminalMode() && info.getResourceContainer() != null) {
                info.getResourceContainer().openInstanceResources();
            }
        }, "Resources");

        Runnable reverseAction = () -> {
            if (instance != null) {
                ReProxyManager.start(instance, () -> ScreenManager.getInstance().execute(() -> onViewChanged(getActiveContext(), null)));
            }
        };
        header().addLeft("reverse.png", reverseAction, "Start ReProxy");
        header().addLeft("closeReverse.png", reverseAction, "Stop ReProxy");
        header().addLeft("download.png", () -> {
            TerminalSession info = getCurrentInfo();
            if (info != null && !info.isLocalTerminalMode() && info.getResourceContainer() != null) {
                info.getResourceContainer().showUpdateAllDialog();
            }
        }, "Update All Resources");

        if (headerSearchMode == null) {
            headerSearchMode = new SearchMode(false);
            headerSearchMode.setPlaceholder("Search Logs...");
            headerSearchMode.setOnTextChange(query -> {
                TerminalSession info = getCurrentInfo();
                if (info != null && info.getTerminalWidget() != null && header().liveUpdate) {
                    info.getTerminalWidget().search(query);
                }
            });
            headerSearchMode.setOnSearchEnter(query -> {
                TerminalSession info = getCurrentInfo();
                if (info != null && info.getTerminalWidget() != null) {
                    if (!header().liveUpdate) {
                        info.getTerminalWidget().search(query);
                    }
                    info.getTerminalWidget().nextMatch();
                }
            });
        }
        if (resourcesSearchMode == null) {
            resourcesSearchMode = new SearchMode(false);
            resourcesSearchMode.setPlaceholder("Search Resources...");
            resourcesSearchMode.setOnTextChange(query -> {
                TerminalSession info = getCurrentInfo();
                if (info != null && info.getResourceContainer() != null && header().liveUpdate) {
                    info.getResourceContainer().search(query);
                }
            });
            resourcesSearchMode.setOnSearchEnter(query -> {
                TerminalSession info = getCurrentInfo();
                if (info != null && info.getResourceContainer() != null) {
                    if (!header().liveUpdate) {
                        info.getResourceContainer().search(query);
                    }
                }
            });
        }
        if (playersSearchMode == null) {
            playersSearchMode = new SearchMode(false);
            playersSearchMode.setPlaceholder("Search Players...");
            playersSearchMode.setOnTextChange(query -> {
                TerminalSession info = getCurrentInfo();
                if (info != null && info.getPlayersContainer() != null && header().liveUpdate) {
                    info.getPlayersContainer().search(query);
                }
            });
            playersSearchMode.setOnSearchEnter(query -> {
                TerminalSession info = getCurrentInfo();
                if (info != null && info.getPlayersContainer() != null) {
                    if (!header().liveUpdate) {
                        info.getPlayersContainer().search(query);
                    }
                }
            });
        }
        header().setSearchMode(headerSearchMode, true);

        header().build();
    }

    @Override
    protected void setupTabs() {
        tabs().builder()
            .position(5, 35).size(width - 10, 18)
            .allowAdd(true).allowClose(true).allowReorder(true).allowRename(true)
            .onPlusButtonClicked(this::addNewTerminalTab)
            .onTabSelected(this::onTabSelected)
            .onTabClosed(this::onTabClosed)
            .onTabsReordered(this::onTabsReordered)
            .onTabRenamed(this::onTabRenamed)
            .build();

        List<Object> tabStore = getTabStore();
        if (tabStore.isEmpty()) {
            if (initialInstanceToOpen != null) {
                tabStore.add(initialInstanceToOpen);
            } else {
                tabStore.add(UUID.randomUUID().toString());
            }
        }
        int activeIndex = getSavedTabIndex();
        if (activeIndex < 0 || activeIndex >= tabStore.size()) {
            activeIndex = 0;
        }

        for (int i = 0; i < tabStore.size(); i++) {
            createAndAddTab(tabStore.get(i), false, i == activeIndex);
        }

        if (activeIndex >= 0 && activeIndex < tabs().getTabs().size()) {
            tabs().setActiveTab(activeIndex);
        } else if (!tabs().getTabs().isEmpty()) {
            tabs().setActiveTab(0);
        }
    }

    private List<Object> getTabStore() {
        return remotelyClient.getMultiTerminalTabs();
    }

    private int getSavedTabIndex() {
        return remotelyClient.getActiveMultiTerminalTabIndex();
    }

    private void setSavedTabIndex(int index) {
        remotelyClient.setActiveMultiTerminalTabIndex(index);
    }

    private void createAndAddTab(Object tabInfo, boolean setActive) {
        createAndAddTab(tabInfo, setActive, true);
    }

    private void createAndAddTab(Object tabInfo, boolean setActive, boolean initialize) {
        Instance inst = (tabInfo instanceof Instance) ? (Instance) tabInfo : null;
        String localId = (tabInfo instanceof String) ? (String) tabInfo : null;
        String name = inst != null ? inst.getName() : "Terminal";
        if (inst == null && localId == null) {
            long count = contextInfos.values().stream().filter(TerminalSession::isLocalTerminalMode).count() + 1;
            name = "Terminal " + count;
        }

        int statusPad = inst != null ? 15 : 0;
        Container main = createContainer("root", 5, 60, width - 10, height - 65 - statusPad);
        main.layout(new ManagedLayout()).backgroundDrawing(true).disableScissorRegion(false).verticalSpacing(14).padding(0).setRelativeScissor(-1, -1, -1, -3);

        TabContext ctx = inst != null ? new ServerTabStatusContext(inst, tabInfo) : new TabContext(null, tabInfo);
        ctx.mainContainer = main;

        if (initialize) {
            initializeTabContext(ctx, tabInfo, inst, localId, statusPad);
        }

        contextInfos.put(ctx, initialize ? remotelyClient.getSessionManager().getSession(tabInfo) : null);
        TabsManager.Tab tab = tabs().addTab(name, main);
        registerTab(tab, ctx);
        if (ctx instanceof TabStatusContext statusContext) {
            registerStatusContext(tab, statusContext);
        }

        if (setActive) {
            tabs().setActiveTab(tabs().getTabs().size() - 1);
        }
    }

    private TerminalSession initializeTabContext(TabContext ctx) {
        if (ctx == null) return null;
        TerminalSession existing = contextInfos.get(ctx);
        if (existing != null && existing.getTerminalWidget() != null) return existing;

        Object tabInfo = ctx.id;
        Instance inst = ctx.instance;
        String localId = (tabInfo instanceof String) ? (String) tabInfo : null;
        int statusPad = inst != null ? 15 : 0;
        TerminalSession info = initializeTabContext(ctx, tabInfo, inst, localId, statusPad);
        contextInfos.put(ctx, info);
        return info;
    }

    private TerminalSession initializeTabContext(TabContext ctx, Object tabInfo, Instance inst, String localId, int statusPad) {
        TerminalSession info = remotelyClient.getSessionManager().getSession(tabInfo);
        if (info == null) {
            info = remotelyClient.getSessionManager().createSession(tabInfo, inst, localId);

            ExecutionProvider exec;
            if (inst != null) {
                exec = new UnifiedExecutionProvider(InstanceApi.of(inst).console());
            } else {
                exec = new LocalBackend(new BackendConfig("LOCAL", new HashMap<>()), null).getExecution();
            }

            TerminalWidget terminal;
            if (inst != null) {
                terminal = ServerTerminal.getOrCreate(inst, exec, 5, 60, width - 10, height - 66 - statusPad);
            } else {
                terminal = TerminalWidget.getOrCreate(null, exec, localId, 5, 60, width - 10, height - 66);
            }
            terminal.entranceAnimationEnabled = false;
            info.setTerminalWidget(terminal);

            if (inst != null) {
                terminal.start();

                inst.attachTerminalListener(terminal);
                setupTerminalListeners(inst, info);
            }

            if (inst != null && inst.isServer()) {
                ResourceContainer res = new ResourceContainer(this, inst, 5, 60, width - 10, height - 66 - statusPad);
                info.setResourceContainer(res);

                PlayersContainer players = new PlayersContainer(this, inst, terminal, 5, 60, width - 10, height - 66 - statusPad);
                info.setPlayersContainer(players);
            }
        } else {
            if (info.getResourceContainer() != null) info.getResourceContainer().setHost(this);
            if (info.getPlayersContainer() != null) info.getPlayersContainer().setHost(this);
        }

        configureTerminalInput(inst, info.getTerminalWidget());

        if (ctx.views.isEmpty()) {
            ctx.addView(info.getTerminalWidget(), "terminal.png", "Terminal", null);

            if (inst != null && inst.isServer()) {
                ResourceContainer res = info.getResourceContainer();
                if (res != null) {
                    List<AnimatedWidget> resTools = new ArrayList<>();
                    resTools.add(res.getSelectorsRow());
                    ctx.addView(res, "resources.png", "Resources", resTools);
                }

                PlayersContainer players = info.getPlayersContainer();
                if (players != null) {
                    ctx.addView(players, "steve.png", "Players", null);
                }
            }
        }

        return info;
    }

    private void setupTerminalListeners(Instance inst, TerminalSession info) {
        if (info.getStandardParser() != null) {
            inst.removeLogListener(info.getStandardParser());
            info.setStandardParser(null);
        }
        if (info.getStreamDataParser() != null) {
            inst.removeLogListener(info.getStreamDataParser());
            info.setStreamDataParser(null);
        }

        boolean standardEnabled = Boolean.parseBoolean(inst.getSettings().getProperty("provider.standard.enabled", "true"));
        String priority = inst.getSettings().getProperty("provider.priority.lifecycle", "msmp,standard");

        if (standardEnabled && priority.contains("standard")) {
            StandardOutputStateParser parser = new StandardOutputStateParser(inst);
            inst.addLogListener(parser);
            info.setStandardParser(parser);
        }

        if (inst.getBackend() != null) {
            Optional<DataStreamFeature> dataStreamFeature = inst.getBackend().getFeature(DataStreamFeature.class);
            if (dataStreamFeature.isPresent()) {
                StreamDataParser dataParser = new StreamDataParser(PlayerManagerController.getOrCreate(inst));
                info.setStreamDataParser(dataParser);

                String logPath = inst.getPath() + "/logs/latest.log";
                String opsPath = inst.getPath() + "/ops.json";
                String bannedPlayersPath = inst.getPath() + "/banned-players.json";
                String bannedIpsPath = inst.getPath() + "/banned-ips.json";
                String whitelistPath = inst.getPath() + "/whitelist.json";
                String usercachePath = inst.getPath() + "/usercache.json";
                List<String> preFiles = Arrays.asList(opsPath, bannedPlayersPath, bannedIpsPath, whitelistPath, usercachePath);

                DebugManager.getInstance().recordEvent(inst.getInstanceId(), "DataStream", "ServerDetails", "Found DataStreamFeature, attaching...");
                dataStreamFeature.get().streamData(logPath, preFiles, dataParser);
            } else {
                DebugManager.getInstance().recordEvent(inst.getInstanceId(), "DataStream", "ServerDetails", "DataStreamFeature not available");
            }
        }
    }

    @Override
    protected void onViewChanged(TabContext context, ViewEntry activeView) {
        TerminalSession info = contextInfos.get(context);
        if (info == null) return;

        boolean isInstance = !info.isLocalTerminalMode();
        if (isInstance) {
            DiscordRpcBridge.setServerActive(context.instance, viewName(activeView));
        } else {
            DiscordRpcBridge.setLocalTerminalActive();
        }
        header().setButtonVisible("explorer.png", isInstance);
        boolean pteroInstance = isPteroInstance(context.instance);
        header().setButtonVisible("edit.png", isInstance && !pteroInstance);
        header().setButtonVisible("merge.png", isInstance && !pteroInstance && isDevModeEligible(context.instance));

        if (activeView != null) {
            switch (activeView.widget()) {
                case TerminalWidget terminal -> {
                    terminal.setShowSearchNavigation(true);
                    terminal.setFocused(true);
                    setFocusedWidget(terminal);
                    if (header().searchBox != null) {
                        terminal.search(header().searchBox.getText());
                    }
                    header().setSearchMode(headerSearchMode, true);
                }
                case ResourceContainer resources -> {
                    if (header().searchBox != null) {
                        resources.search(header().searchBox.getText());
                    }
                    header().setSearchMode(resourcesSearchMode, true);
                }
                case PlayersContainer players -> {
                    if (header().searchBox != null) {
                        players.search(header().searchBox.getText());
                    }
                    header().setSearchMode(playersSearchMode, true);
                    players.fullRefresh();
                }
                case null, default -> header().setSearchMode(null, false);
            }
        }

        if (startIconButton != null) {
            updateStartButton(context, info);
        }

        if (isInstance) {
            ModLoader modLoader = context.instance.getModLoader();
            boolean showResources = modLoader != null;
            header().setButtonVisible("resources.png", showResources);

            boolean isReversed = ReProxyManager.isForwarded(context.instance);
            boolean isLocal = isLocalInstance(context.instance);
            header().setButtonVisible("reverse.png", !isReversed && isLocal);
            header().setButtonVisible("closeReverse.png", isReversed && isLocal);

            boolean isResView = activeView != null && activeView.widget() instanceof ResourceContainer;
            header().setButtonVisible("download.png", isResView);
            if (info.getResourceContainer() != null) {
                info.getResourceContainer().setSelectorsVisible(isResView);
                if (isResView) {
                    info.getResourceContainer().ensureSelectorsSynced();
                    info.getResourceContainer().loadResources();
                } else {
                    info.getResourceContainer().resetLoadingState();
                }
            }
        } else {
            header().setButtonVisible("resources.png", false);
            header().setButtonVisible("reverse.png", false);
            header().setButtonVisible("closeReverse.png", false);
            header().setButtonVisible("download.png", false);
        }
    }

    @Override
    protected void onTabSelected(TabsManager.Tab tab) {
        for (TerminalSession session : contextInfos.values()) {
            if (session != null && session.getResourceContainer() != null) {
                session.getResourceContainer().setSelectorsVisible(false);
            }
        }

        if (sidecarInstance != null && sidecarInstance.getBackend() != null) {
            sidecarInstance.getBackend().disconnect();
            sidecarInstance = null;
        }

        TabContext selectedContext = tabContexts.get(tab);
        initializeTabContext(selectedContext);

        super.onTabSelected(tab);
        applyStatusBarForActiveTab();
        if (headerSearchMode != null) {
            header().setSearchMode(headerSearchMode, true);
        }

        TabContext ctx = getActiveContext();
        if (ctx == null) return;
        TerminalSession info = contextInfos.get(ctx);

        if (info.isLocalTerminalMode()) {
            DiscordRpcBridge.setLocalTerminalActive();
            DebugManager.getInstance().setViewContext(info.getLocalTerminalId());
        } else {
            DiscordRpcBridge.setServerActive(ctx.instance, currentViewName(ctx));
            DebugManager.getInstance().setViewContext(ctx.instance.getInstanceId());
            ctx.instance.reloadSettingsFromBackend().thenRun(() -> ScreenManager.getInstance().execute(() -> {
                setupTerminalListeners(ctx.instance, info);
                PlayerManagerController.getOrCreate(ctx.instance).reloadProviders();

                if (info.getPlayersContainer() != null) info.getPlayersContainer().fullRefresh();
            })).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> setupTerminalListeners(ctx.instance, info));
                return null;
            });

            if (VersionUtil.isMSMPCompatible(ctx.instance.getVersionId())) {
                ctx.instance.getMSMPManager().handleInstanceStateChange(ctx.instance.getState());
            }

            CompletableFuture.runAsync(() -> {
                if (Boolean.parseBoolean(ctx.instance.getSettings().getProperty("provider.msmp.enabled", "true"))) {
                    if (!ctx.instance.getMSMPManager().isConnected) {
                        ctx.instance.getMSMPManager().connect();
                    }
                }
            });


        }

        setSavedTabIndex(tabs().getActiveTabIndex());
        int idx = ctx.selectedViewIndex < ctx.views.size() ? ctx.selectedViewIndex : 0;
        if (!ctx.views.isEmpty()) {
            onViewChanged(ctx, ctx.views.get(idx));
        }
        Main.setTitle(tab.getName() + " - Remotely Terminal");
    }

    private void onTabClosed(TabsManager.Tab tab) {
        getGroupManager().onTabClosed(tab);
        TabContext ctx = tabContexts.remove(tab);
        if (ctx != null) {
            TerminalSession info = contextInfos.remove(ctx);

            if (info != null) {
                remotelyClient.getSessionManager().destroySession(info.getTabId());
            }

            if (ctx.instance != null) {
                ctx.instance.removeStateListener(stateListener);
                ctx.instance.getMSMPManager().disconnect();
            }

        }
        syncTabStoreFromTabs();
        if (tabs().getTabs().isEmpty()) {
            setSavedTabIndex(-1);
            closeScreen();
        } else {
            setSavedTabIndex(tabs().getActiveTabIndex());
        }
    }

    private void onTabRenamed(TabsManager.Tab tab) {
        TabContext context = tabContexts.get(tab);
        if (context != null && context.instance != null) {
            String newName = tab.getName();
            String oldName = context.instance.getName();
            Instance instance = context.instance;
            InstanceManager.getInstance().renameInstance(instance, newName).thenRun(() -> {
                if (instance.getBackend() instanceof ReStudioBackend reStudioBackend) {
                    String serverId = reStudioBackend.getServerId();
                    restudio.rebase.restudio.ReStudio.getInstance().getApi().renameServer(serverId, newName).exceptionally(e -> {
                        ScreenManager.getInstance().execute(() -> new Notification("Panel Rename Failed", e.getMessage(), Notification.Type.WARN));
                        return null;
                    });
                }
            }).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> {
                    tab.setName(oldName);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    new Notification("Rename Failed", cause.getMessage(), Notification.Type.ERROR);
                });
                return null;
            });
        }
    }

    private void onTabsReordered(List<TabsManager.Tab> newOrder) {
        syncTabStoreFromTabs(newOrder);
        setSavedTabIndex(tabs().getActiveTabIndex());
    }

    private void syncTabStoreFromTabs() {
        syncTabStoreFromTabs(tabs().getTabs());
    }

    private void syncTabStoreFromTabs(List<TabsManager.Tab> tabOrder) {
        List<Object> newTabOrder = new ArrayList<>();
        for (TabsManager.Tab tab : tabOrder) {
            TabContext context = tabContexts.get(tab);
            if (context != null) {
                newTabOrder.add(context.instance != null ? context.instance : context.id);
            }
        }
        List<Object> tabStore = getTabStore();
        tabStore.clear();
        tabStore.addAll(newTabOrder);
    }

    private void addNewTerminalTab() {
        String newId = UUID.randomUUID().toString();
        getTabStore().add(newId);
        createAndAddTab(newId, true);
    }

    public void addInstanceTab(Instance instanceToAdd) {
        List<TabsManager.Tab> openTabs = tabs().getTabs();
        for (int i = 0; i < openTabs.size(); i++) {
            TabContext context = tabContexts.get(openTabs.get(i));
            if (context != null && sameInstance(context.instance, instanceToAdd)) {
                tabs().setActiveTab(i);
                setSavedTabIndex(i);
                return;
            }
        }
        List<Object> tabStore = getTabStore();
        if (tabStore.stream().noneMatch(tab -> tab instanceof Instance instance && sameInstance(instance, instanceToAdd))) {
            tabStore.add(instanceToAdd);
        }
        createAndAddTab(instanceToAdd, true);
    }

    private boolean sameInstance(Instance a, Instance b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        String aId = a.getInstanceId();
        String bId = b.getInstanceId();
        if (aId != null && bId != null && !aId.isBlank() && !bId.isBlank()) {
            return aId.equals(bId);
        }
        return a.equals(b);
    }

    private void launchOrStopInstance() {
        TabContext context = getActiveContext();
        if (context == null) return;
        TerminalSession info = contextInfos.get(context);
        if (info == null || info.isLocalTerminalMode()) return;
        if (context.instance.getState() == InstanceState.STOPPING) {
            if (isKilling(context.instance)) {
                return;
            }
            if (isKillConfirmationActive(context.instance)) {
                killStoppingServer(context, info);
                return;
            }
            armKillConfirmation(context.instance);
            updateStartButton(context, info);
            return;
        }

        InstanceApi api = InstanceApi.of(context.instance);
        if (context.instance.getState() == InstanceState.RUNNING || context.instance.getState() == InstanceState.STARTING) {
            if (info.getTerminalWidget() instanceof ServerTerminal st) {
                st.notifyStopRequested();
            }
            String t = context.instance.getBackend() != null ? context.instance.getBackend().getFileSystem().getMetadata("type") : "";
            stopReProxyIfForwarded(context.instance);
            if ("LOCAL".equalsIgnoreCase(t)) {
                api.console().stopServer();
                LifecycleManager.requestStop(context.instance);
                context.instance.setState(InstanceState.STOPPING);
            }
            if (!"LOCAL".equalsIgnoreCase(t)) {
                context.instance.setState(InstanceState.STOPPING);
                api.console().stopServer().thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    if ("PTERO".equalsIgnoreCase(t)) {
                        return;
                    }
                    if (info.getTerminalWidget() != null) {
                        info.getTerminalWidget().stopProcess();
                    }
                    context.instance.setState(InstanceState.STOPPED);
                })).exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> {
                        Throwable cause = unwrapThrowable(e);
                        String message = cause.getMessage() != null ? cause.getMessage() : "Remote stop failed.";
                        if (message.toLowerCase(Locale.ROOT).contains("not running")) {
                            if (info.getTerminalWidget() != null) {
                                info.getTerminalWidget().stopProcess();
                            }
                            context.instance.setState(InstanceState.STOPPED);
                            return;
                        }
                        context.instance.setState(InstanceState.RUNNING);
                        new Notification("Server Stop Failed", message, Notification.Type.ERROR);
                    });
                    return null;
                });
            }
        } else {
            api.health().check().thenAccept(status -> ScreenManager.getInstance().execute(() -> {
                String startupScriptPath = context.instance.getSettings().getProperty("startupScriptPath");
                if (!status.hasServerJar() && (startupScriptPath == null || startupScriptPath.isBlank())) {
                    showFixPopup("Server Jar Missing", "The server jar was not found.", "Download Jar", () -> {
                        Notification dlNotif = new Notification.Builder().message("Starting Download...").type(Notification.Type.INFO).loading(true).build();
                        new InstanceFactory().downloadMissingServerJar(context.instance, dlNotif).thenRun(() -> ScreenManager.getInstance().execute(() -> {
                            dlNotif.update().message("Download Complete").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true);
                            launchOrStopInstance();
                        })).exceptionally(e -> {
                            ScreenManager.getInstance().execute(() -> dlNotif.update().message("Download Failed").description(e.getMessage()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true));
                            return null;
                        });
                    }, () -> proceedWithServerStart(context, info));
                    return;
                }

                if (!status.hasStartScript()) {
                    showFixPopup("Start Script Missing", "The startup script is missing.", "Create Script", () -> InstanceRepairer.createStartScript(context.instance).thenRun(() -> ScreenManager.getInstance().execute(() -> new Notification("Script Created", Notification.Type.SUCCESS))), () -> proceedWithServerStart(context, info));
                    return;
                }

                if (!status.eulaAccepted()) {
                    showEulaPopup(context, info);
                    return;
                }

                proceedWithServerStart(context, info);
            })).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> new Notification("Health Check Failed", e.getMessage(), Notification.Type.ERROR));
                return null;
            });
        }
    }

    private void updateStartButton(TabContext context, TerminalSession info) {
        if (startIconButton == null) {
            return;
        }
        boolean isInstance = info != null && !info.isLocalTerminalMode();
        startIconButton.setVisible(isInstance);
        if (!isInstance || context == null || context.instance == null) {
            header().requestLayoutUpdate();
            return;
        }
        InstanceState state = context.instance.getState();
        boolean showSquare = state == InstanceState.STOPPED || state == InstanceState.CRASHED;
        if (showSquare) {
            clearKillConfirmation(context.instance);
            startIconButton.setMessage("");
            startIconButton.setWidth(18);
            startIconButton.setIcon("start.png");
            startIconButton.accentType = state == InstanceState.CRASHED ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("nice");
            header().requestLayoutUpdate();
            return;
        }
        if (state != InstanceState.STOPPING) {
            clearKillConfirmation(context.instance);
        }
        boolean killConfirmActive = isKillConfirmationActive(context.instance);
        boolean killing = isKilling(context.instance);
        startIconButton.setMessage(killing ? "Killing" : (killConfirmActive ? "Kill Server?" : state.toString().toLowerCase().substring(0, 1).toUpperCase() + state.name().toLowerCase().substring(1)));
        if (state == InstanceState.STARTING) {
            startIconButton.setIcon(Identifier.animatedIcon("loadingGreen"));
            startIconButton.accentType = ThemeManager.getAccent("nice");
        } else if (state == InstanceState.STOPPING) {
            startIconButton.setIcon(killConfirmActive ? Identifier.icon("report.png") : Identifier.animatedIcon("loadingRed"));
            startIconButton.accentType = ThemeManager.getAccent("danger");
        } else if (state == InstanceState.SAVED || state == InstanceState.SAVING) {
            startIconButton.setIcon("stop.png");
            startIconButton.accentType = ThemeManager.getAccent("calm");
        } else if (state == InstanceState.RUNNING) {
            startIconButton.setIcon("stop.png");
            startIconButton.accentType = ThemeManager.getAccent("danger");
        }
        header().requestLayoutUpdate();
    }

    private void armKillConfirmation(Instance instance) {
        if (instance == null) {
            return;
        }
        killConfirmInstanceId = killKey(instance);
        killConfirmUntilMs = System.currentTimeMillis() + KILL_CONFIRM_MS;
    }

    private boolean isKillConfirmationActive(Instance instance) {
        if (instance == null || killConfirmInstanceId == null || System.currentTimeMillis() > killConfirmUntilMs) {
            return false;
        }
        return Objects.equals(killConfirmInstanceId, killKey(instance));
    }

    private boolean isKilling(Instance instance) {
        return instance != null && killingInstanceId != null && Objects.equals(killingInstanceId, killKey(instance));
    }

    private void clearKillConfirmation(Instance instance) {
        if (instance == null || Objects.equals(killConfirmInstanceId, killKey(instance))) {
            killConfirmInstanceId = null;
            killConfirmUntilMs = 0;
        }
        if (instance == null || Objects.equals(killingInstanceId, killKey(instance))) {
            killingInstanceId = null;
        }
    }

    private String killKey(Instance instance) {
        if (instance == null) {
            return "";
        }
        if (instance.getInstanceId() != null && !instance.getInstanceId().isBlank()) {
            return instance.getInstanceId();
        }
        return instance.getPath() != null && !instance.getPath().isBlank() ? instance.getPath() : String.valueOf(System.identityHashCode(instance));
    }

    private void killStoppingServer(TabContext context, TerminalSession info) {
        Instance instance = context.instance;
        killingInstanceId = killKey(instance);
        killConfirmInstanceId = null;
        killConfirmUntilMs = 0;
        updateStartButton(context, info);
        stopReProxyIfForwarded(instance);
        InstanceApi.of(instance).console().killServer().thenRun(() -> ScreenManager.getInstance().execute(() -> {
            clearKillConfirmation(instance);
            LifecycleManager.clear(instance);
            if (info.getTerminalWidget() != null) {
                info.getTerminalWidget().stopProcess();
            }
            instance.setState(InstanceState.STOPPED);
            QuickServerSyncManager.syncBackAfterStop(instance);
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                killingInstanceId = null;
                Throwable cause = unwrapThrowable(e);
                String message = cause.getMessage() != null ? cause.getMessage() : "Server kill failed.";
                if (isPteroInstance(instance) && message.contains("429")) {
                    instance.setState(InstanceState.STOPPING);
                    if (instance.getBackend() != null) {
                        instance.getBackend().getFeature(ResourceUsageFeature.class).ifPresent(feature -> feature.getResources().exceptionally(ex -> null));
                    }
                    updateStartButton(context, info);
                    return;
                }
                new Notification("Server Kill Failed", message, Notification.Type.ERROR);
                updateStartButton(context, info);
            });
            return null;
        });
    }

    private void stopReProxyIfForwarded(Instance instance) {
        if (instance != null && ReProxyManager.isForwarded(instance)) {
            ReProxyManager.stop(instance.getPort(), null);
        }
    }

    private void stopQuickServerReProxyIfForwarded(Instance instance) {
        if (isQuickServer(instance) && ReProxyManager.isForwarded(instance)) {
            ReProxyManager.stop(instance.getPort(), null);
        }
    }

    private boolean isQuickServer(Instance instance) {
        return instance != null && "true".equalsIgnoreCase(instance.getSettings().getProperty("quickServer.enabled"));
    }

    private boolean isQuickServerRuntimeOpen(Instance instance) {
        return isQuickServer(instance) && ReProxyManager.isForwarded(instance) && isLocalPortOpen(instance.getPort());
    }

    private boolean isLocalPortOpen(int port) {
        if (port <= 0 || port > 65535) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 350);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showFixPopup(String title, String desc, String buttonText, Runnable action, Runnable onIgnore) {
        PopupWidget.Builder builder = new PopupWidget.Builder(title).size(300, 70).setResizable(false);
        AnimatedButton textWidget = new AnimatedButton.Builder().label(desc).active(false).flat(true).build();
        builder.addRow("", true, 20, textWidget);

        AnimatedButton actionBtn = new AnimatedButton.Builder()
            .label(buttonText)
            .accentType(ThemeManager.getAccent("nice"))
            .build();

        AnimatedButton ignoreBtn = new AnimatedButton.Builder()
            .label("Launch Anyway")
            .accentType(ThemeManager.getAccent("danger"))
            .build();

        builder.addRow("", true, 30, actionBtn, ignoreBtn);
        PopupWidget popup = builder.build();

        actionBtn.setAction(() -> {
            action.run();
            popup.hide();
        });

        ignoreBtn.setAction(() -> {
            if (onIgnore != null) onIgnore.run();
            popup.hide();
        });

        addDrawableChild(popup);
        popup.show();
    }

    private void proceedWithServerStart(TabContext context, TerminalSession info) {
        String backendType = context.instance.getBackendConfig() != null ? context.instance.getBackendConfig().type : "LOCAL";
        if ("LOCAL".equalsIgnoreCase(backendType)) {
            if (info.getTerminalWidget() != null) {
                info.getTerminalWidget().shutdown();
                TerminalWidget.shutdown(context.instance.getInstanceId());
                context.mainContainer.removeWidget(info.getTerminalWidget());
            }

            ExecutionProvider exec = new UnifiedExecutionProvider(InstanceApi.of(context.instance).console());
            TerminalWidget tw = ServerTerminal.getOrCreate(context.instance, exec, 5, 60, width - 10, height - 66);
            configureTerminalInput(context.instance, tw);
            info.setTerminalWidget(tw);
            if (info.getPlayersContainer() != null) {
                info.getPlayersContainer().setTerminalWidget(tw);
            }

            tw.setForceDirectLaunch(true);
            tw.clearLog();
            LifecycleManager.requestStart(context.instance);
            if (tw instanceof ServerTerminal st) {
                st.notifyStartRequested();
            }
            startLocalServerWhenReady(context, tw);
            context.instance.attachTerminalListener(tw);

            for (int i = 0; i < context.views.size(); i++) {
                ViewEntry entry = context.views.get(i);
                if (entry.hint().equals("Terminal")) {
                    context.views.set(i, new ViewEntry(tw, entry.icon(), entry.hint(), entry.toolbarWidgets()));
                    if (context.selectedViewIndex == i) {
                        context.mainContainer.addWidget(tw);
                    }
                    break;
                }
            }
            context.instance.setState(InstanceState.STARTING);
            return;
        }

        InstanceApi api = InstanceApi.of(context.instance);
        context.instance.setState(InstanceState.STARTING);
        if (info.getTerminalWidget() instanceof ServerTerminal st) {
            st.notifyStartRequested();
        }
        api.console().startServer().thenAccept(command -> ScreenManager.getInstance().execute(() -> {
            String type = context.instance.getBackend() != null ? context.instance.getBackend().getFileSystem().getMetadata("type") : "";
            if ("SSH".equalsIgnoreCase(type)) {
                if (info.getTerminalWidget() != null && !info.getTerminalWidget().isTerminalReady()) {
                    info.getTerminalWidget().startServerProcess();
                }
                return;
            }
            if ("PTERO".equalsIgnoreCase(type)) {
                if (info.getTerminalWidget() != null && !info.getTerminalWidget().isTerminalReady()) {
                    info.getTerminalWidget().startServerProcess();
                }
                return;
            }
            if (command != null && !command.isEmpty()) {
                info.getTerminalWidget().executeCommand(command);
                return;
            }
            if ("LOCAL".equalsIgnoreCase(type)) {
                info.getTerminalWidget().startServerProcess();
            }
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                Throwable cause = unwrapThrowable(e);
                context.instance.setState(InstanceState.STOPPED);
                if (info.getTerminalWidget() instanceof ServerTerminal st) {
                    st.notifyStopRequested();
                } else if (info.getTerminalWidget() != null) {
                    info.getTerminalWidget().stopProcess();
                }
                String message = cause.getMessage() != null ? cause.getMessage() : "Remote startup failed.";
                new Notification("Server Start Failed", message, Notification.Type.ERROR);
            });
            return null;
        });
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current != null ? current : throwable;
    }

    private void startLocalServerWhenReady(TabContext context, TerminalWidget terminal) {
        if (context == null || context.instance == null || terminal == null) {
            return;
        }
        Thread.ofVirtual().name("Remotely Local Start Gate").start(() -> {
            long deadline = System.currentTimeMillis() + LOCAL_STOP_GRACE_MS;
            while (System.currentTimeMillis() < deadline) {
                LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(context.instance);
                if (status == null || !status.knownSession || "STOPPED".equalsIgnoreCase(status.state) || "CRASHED".equalsIgnoreCase(status.state)) {
                    break;
                }
                if ("RUNNING".equalsIgnoreCase(status.state)) {
                    if (!LifecycleManager.isStopPending(context.instance)) {
                        break;
                    }
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            ScreenManager.getInstance().execute(() -> {
                LifecycleManager.beginStart(context.instance);
                terminal.startServerProcess();
            });
        });
    }

    private void showEulaPopup(TabContext context, TerminalSession info) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Mojang EULA Agreement").size(327, 145).setResizable(false);
        AnimatedButton textWidget = new AnimatedButton.Builder().label("Before You Start, Please Agree To The EULA.").active(false).flat(true).build();
        builder.addRow("", true, 20, textWidget);
        builder.addMarkdown("", "By Click The Agree Button Below, You Agree To The [Minecraft EULA](https://www.minecraft.net/en-us/eula).", 20);
        PopupWidget popup = builder.build();

        builder.addRow("", true, 18, new IconButton.Builder().imagePath("checkmark").centered(true).accentType(ThemeManager.getAccent("nice"))
            .label("I have read and agree to the EULA").size(0, 18).onClick(() -> {
                final RebaseAPI api = RebaseApiFactory.get(context.instance);
                final Path eulaPath = Path.of(context.instance.getPath(), "eula.txt");
                context.instance.getServerProperties().setProperty("eula", "true");
                context.instance.saveServerProperties().thenCompose(v -> api.writeFile(eulaPath, "eula=true")).thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    popup.hide();
                    proceedWithServerStart(context, info);
                }));
            }).build());

        AnimatedButton ignoreBtn = new AnimatedButton.Builder()
            .label("Launch Anyway")
            .accentType(ThemeManager.getAccent("danger"))
            .build();

        ignoreBtn.setAction(() -> {
            popup.hide();
            proceedWithServerStart(context, info);
        });

        builder.addRow("", true, 20, ignoreBtn);

        addDrawableChild(popup);
        popup.show();
    }

    private Instance ensureSidecar() {
        TabContext context = getActiveContext();
        if (context == null || context.instance == null) return null;

        if (sidecarInstance == null) {
            sidecarInstance = new Instance(context.instance, context.instance.getName());
            BackendConfig bc = context.instance.getBackendConfig();
            if (bc != null && "SSH".equalsIgnoreCase(bc.type)) {
                Map<String, String> creds = new HashMap<>(bc.credentials);
                String originalHostId = creds.getOrDefault("hostId", UUID.randomUUID().toString());
                creds.put("hostId", originalHostId + "-sidecar");
                sidecarInstance.setBackendConfig(new BackendConfig(bc.type, creds));
            } else {
                sidecarInstance.setBackendConfig(bc);
            }
        }
        return sidecarInstance;
    }

    private void exploreInstanceFiles() {
        Instance target = ensureSidecar();
        if (target == null) return;
        client.setScreen(new FileExplorerScreen(this, target, Paths.get(target.getPath()), Path.of(remotelyDir.toString(), "data"), false) {
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

    private void openReSyncStudio() {
        TabContext context = getActiveContext();
        if (context == null || context.instance == null) return;
        remotelyClient.openReSyncStudio(this, context.instance);
    }

    private boolean isDevModeEligible(Instance instance) {
        if (instance == null || instance.getBackendConfig() == null || instance.getBackendConfig().type == null) {
            return false;
        }
        return !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
    }

    private void openDevModeScreen() {
        TabContext context = getActiveContext();
        if (context == null || context.instance == null) {
            return;
        }
        if (!isDevModeEligible(context.instance)) {
            new Notification.Builder().message("DevMode Unavailable").description("Remote Servers Only").type(Notification.Type.ERROR).build();
            return;
        }
        ScreenManager.getInstance().setScreen(new ServerTwinScreen(this, remotelyClient, context.instance));
    }

    public void openInstanceSettings() {
        Instance target = ensureSidecar();
        if (target == null) return;
        if (isPteroInstance(target)) {
            new Notification("Panel Managed", "Use Files For Pterodactyl Settings", Notification.Type.WARN);
            return;
        }
        RemoteHost host = null;
        BackendConfig cfg = target.getBackendConfig();
        if (cfg != null && !"LOCAL".equalsIgnoreCase(cfg.type)) {
            for(RemoteHost h : InstanceManager.getInstance().getRemoteHosts()) {
                if(cfg.credentials.getOrDefault("host", "").equals(h.getIp())) {
                    host = h;
                    break;
                }
            }
        }
        client.setScreen(new ServerConfigurationScreen(this, target, host, remotelyClient));
    }

    private boolean isPteroInstance(Instance instance) {
        BackendConfig config = instance != null ? instance.getBackendConfig() : null;
        return config != null && "PTERO".equalsIgnoreCase(config.type);
    }

    private void configureTerminalInput(Instance instance, TerminalWidget terminal) {
        if (terminal == null) return;
        if (!isPteroInstance(instance)) {
            terminal.disableFakeInput();
            return;
        }
        ExecutionProvider exec = new UnifiedExecutionProvider(InstanceApi.of(instance).console());
        terminal.enableFakeInput("> ", command -> exec.sendCommand(command).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                Throwable cause = unwrapThrowable(e);
                String message = cause.getMessage() != null ? cause.getMessage() : "Failed To Send Command";
                new Notification("Command Failed", message, Notification.Type.ERROR);
            });
            return null;
        }));
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateKillConfirmationExpiry();
        super.render(context, mouseX, mouseY, delta);
        renderTerminalScrollbar(context, mouseX, mouseY);
    }

    private void updateKillConfirmationExpiry() {
        if (killConfirmInstanceId == null || System.currentTimeMillis() <= killConfirmUntilMs) {
            return;
        }
        killConfirmInstanceId = null;
        killConfirmUntilMs = 0;
        TabContext context = getActiveContext();
        if (context == null) {
            return;
        }
        TerminalSession info = contextInfos.get(context);
        updateStartButton(context, info);
    }

    private void renderTerminalScrollbar(IDrawContext context, int mouseX, int mouseY) {
        TerminalWidget terminal = getActiveTerminalWidget();
        if (!shouldRenderTerminalScrollbar(terminal)) return;
        terminalScrollbarController.render(
            context,
            mouseX,
            mouseY,
            getTerminalScrollbarTotalHeight(terminal),
            getTerminalScrollbarOffset(terminal),
            getTerminalScrollbarX(terminal),
            getTerminalScrollbarY(terminal),
            TERMINAL_SCROLLBAR_WIDTH,
            getTerminalScrollbarHeight(terminal)
        );
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (handleTerminalScrollbarPressed(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        TerminalWidget terminal = getActiveTerminalWidget();
        if (shouldRenderTerminalScrollbar(terminal) && terminalScrollbarController.handleMouseDragged((int) event.y(), getTerminalScrollbarTotalHeight(terminal), getTerminalScrollbarHeight(terminal))) {
            terminal.setScrollOffset(getTerminalScrollOffsetFromScrollbar(terminal, terminalScrollbarController.getPendingOffset()));
            return true;
        }
        return super.mouseDragged(event);
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        boolean handled = terminalScrollbarController.isDragging();
        if (handled) {
            terminalScrollbarController.handleMouseReleased();
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean handleTerminalScrollbarPressed(double mouseX, double mouseY) {
        TerminalWidget terminal = getActiveTerminalWidget();
        if (!shouldRenderTerminalScrollbar(terminal)) return false;
        boolean handled = terminalScrollbarController.handleMousePressed(
            (int) mouseX,
            (int) mouseY,
            getTerminalScrollbarTotalHeight(terminal),
            getTerminalScrollbarOffset(terminal),
            getTerminalScrollbarX(terminal),
            getTerminalScrollbarY(terminal),
            TERMINAL_SCROLLBAR_WIDTH,
            getTerminalScrollbarHeight(terminal)
        );
        if (handled) {
            terminal.setScrollOffset(getTerminalScrollOffsetFromScrollbar(terminal, terminalScrollbarController.getPendingOffset()));
        }
        return handled;
    }

    private TerminalWidget getActiveTerminalWidget() {
        TabContext ctx = getActiveContext();
        if (ctx == null || ctx.selectedViewIndex < 0 || ctx.selectedViewIndex >= ctx.views.size()) return null;
        return ctx.views.get(ctx.selectedViewIndex).widget() instanceof TerminalWidget terminal ? terminal : null;
    }

    private String currentViewName(TabContext ctx) {
        if (ctx == null || ctx.selectedViewIndex < 0 || ctx.selectedViewIndex >= ctx.views.size()) {
            return "Terminal";
        }
        return viewName(ctx.views.get(ctx.selectedViewIndex));
    }

    private String viewName(ViewEntry activeView) {
        return activeView != null && activeView.hint() != null && !activeView.hint().isBlank() ? activeView.hint() : "Terminal";
    }

    private boolean shouldRenderTerminalScrollbar(TerminalWidget terminal) {
        return terminal != null && getTerminalScrollbarHeight(terminal) > 0 && terminal.getContentHeight() > 0;
    }

    private int getTerminalScrollbarTotalHeight(TerminalWidget terminal) {
        return terminal.getContentHeight() + getTerminalScrollbarHeight(terminal);
    }

    private float getTerminalScrollbarOffset(TerminalWidget terminal) {
        return Math.max(0f, terminal.getContentHeight() - terminal.getScrollOffset());
    }

    private float getTerminalScrollOffsetFromScrollbar(TerminalWidget terminal, float scrollbarOffset) {
        return Math.max(0f, terminal.getContentHeight() - scrollbarOffset);
    }

    private int getTerminalScrollbarX(TerminalWidget terminal) {
        return terminal.getX() + terminal.getWidth() + 2;
    }

    private int getTerminalScrollbarY(TerminalWidget terminal) {
        return terminal.getY();
    }

    private int getTerminalScrollbarHeight(TerminalWidget terminal) {
        return terminal.getHeight();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();

        for (TabContext c : tabContexts.values()) {
            if (c == null || c.mainContainer == null) continue;
            int pad = c.instance != null ? 15 : 0;
            c.mainContainer.setWidth(width - 10);
            c.mainContainer.setHeight(height - 65 - pad);
            if (!getGroupManager().isManaged(c.mainContainer)) {
                c.mainContainer.updateWidgetPositions();
            }
        }

        TabContext ctx = getActiveContext();
        if (ctx == null) return;

        int pad = ctx.instance != null ? 15 : 0;
        if (ctx.selectedViewIndex < ctx.views.size()) {
            ViewEntry view = ctx.views.get(ctx.selectedViewIndex);
            int newW = width - 10;
            int newH = height - 65 - pad;
            if (view.widget() instanceof Container c) {
                c.setWidth(newW);
                c.setHeight(newH);
                c.updateWidgetPositions();
            } else if (view.widget() instanceof AnimatedWidget w) {
                w.setWidth(newW);
                w.setHeight(newH);
            }
        }
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (event.key() == ReKey.ESCAPE) {
            close();
            return true;
        }
        if (event.key() == ReKey.R && event.modifiers().control()) {
            TerminalSession info = getCurrentInfo();
            if (info != null && info.getResourceContainer() != null) info.getResourceContainer().loadResources(true);
            return true;
        }
        if (event.key() == ReKey.GRAVE_ACCENT && event.modifiers().control()) {
            if (viewSwitcher != null) {
                TabContext ctx = getActiveContext();
                int i = ctx.selectedViewIndex + 1;
                if(i >= ctx.views.size()) i = 0;
                viewSwitcher.setActiveIndex(i);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        ScreenManager.getInstance().execute(() -> {
            TabContext ctx = getActiveContext();
            TerminalSession info = contextInfos.get(ctx);
            if (ctx != null && !ctx.views.isEmpty()) onViewChanged(ctx, ctx.views.get(ctx.selectedViewIndex));
            if (info != null && info.getPlayersContainer() != null) info.getPlayersContainer().fullRefresh();
        });
    }

    private TerminalSession getCurrentInfo() {
        TabContext ctx = getActiveContext();
        return ctx == null ? null : contextInfos.get(ctx);
    }

    private void startStatusScheduler() {
        if (statusScheduler != null) return;
        statusScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "Remotely-StatusBar-Resources");
            t.setDaemon(true);
            return t;
        });
        statusScheduler.scheduleAtFixedRate(() -> ScreenManager.getInstance().execute(this::refreshActiveStatusBarResources), 0, 1, TimeUnit.SECONDS);
    }

    private void applyStatusBarForActiveTab() {
        TabContext ctx = getActiveContext();
        boolean show = ctx instanceof ServerTabStatusContext;

        if (statusBarBuilder != null) {
            statusBarBuilder.visible(show);
            if (!show) {
                statusBarBuilder.clear();
                statusBarBuilder.build();
            }
        }

        if (show) {
            updateActiveStatusBar();
        }
    }

    private void refreshActiveStatusBarResources() {
        if (!shouldRefreshStatusResources()) return;
        TabContext ctx = getActiveContext();
        if (!(ctx instanceof ServerTabStatusContext statusCtx)) return;
        TerminalSession info = contextInfos.get(ctx);

        long nowMs = System.currentTimeMillis();
        statusCtx.refreshConnectionInfo(nowMs);
        if (!statusCtx.tryStartRequest(nowMs)) return;

        if (ctx.instance == null || ctx.instance.getBackend() == null) {
            statusCtx.finishRequest();
            statusCtx.update(null);
            return;
        }

        ctx.instance.getBackend().getFeature(ResourceUsageFeature.class).ifPresentOrElse(feature -> {
            feature.getResources().thenAccept(usage -> {
                Thread.ofVirtual().name("Remotely Details Local Status").start(() -> {
                    LocalServerControllerModels.StatusResponse localStatus = localControllerStatus(ctx);
                    boolean quickServerRuntimeOpen = isQuickServerRuntimeOpen(ctx.instance);
                    boolean localServerStillRunning = shouldVerifyLocalProcess(localStatus) && LocalServerProcessDetector.isRunning(ctx.instance);
                    ScreenManager.getInstance().execute(() -> {
                        TabContext active = getActiveContext();
                        if (active == ctx) {
                            boolean resourceRunning = usage != null && usage.uptimeMs() > 0;
                            if (quickServerRuntimeOpen) {
                                ctx.instance.setState(InstanceState.RUNNING);
                            } else if (isLocalInstance(ctx.instance) && (!resourceRunning || localStatus == null || "RUNNING".equalsIgnoreCase(localStatus.state))) {
                                applyLocalControllerState(ctx, info, localStatus, localServerStillRunning);
                            }
                            statusCtx.update(usage);
                            boolean controllerAllowsRunning = localStatus == null || !localStatus.knownSession || localStatus.ready || "RUNNING".equalsIgnoreCase(localStatus.state);
                            if (isLocalInstance(ctx.instance) && controllerAllowsRunning && resourceRunning && ctx.instance.getState() == InstanceState.STOPPED) {
                                ctx.instance.setState(InstanceState.RUNNING);
                            } else if (!quickServerRuntimeOpen && isLocalInstance(ctx.instance) && (localStatus == null || !localStatus.knownSession) && (usage == null || usage.uptimeMs() <= 0) && ctx.instance.getState() == InstanceState.RUNNING) {
                                ctx.instance.setState(InstanceState.STOPPED);
                            }
                        }
                        statusCtx.finishRequest();
                    });
                });
            }).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> {
                    statusCtx.finishRequest();
                    statusCtx.update(null);
                });
                return null;
            });
        }, () -> {
            statusCtx.finishRequest();
            statusCtx.update(null);
        });

    }

    private boolean shouldRefreshStatusResources() {
        if (!desktopMode) return ScreenManager.getInstance().getCurrentScreen() == this;
        if (isDesktopWindow()) {
            var overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
            var activeWindow = overlay != null ? overlay.getActiveWindow() : null;
            return activeWindow != null && activeWindow.getScreen() == this && activeWindow.isVisible() && !activeWindow.isMinimized();
        }
        return ScreenManager.getInstance().getCurrentScreen() == this;
    }

    private LocalServerControllerModels.StatusResponse localControllerStatus(TabContext ctx) {
        if (ctx == null || ctx.instance == null || !isLocalInstance(ctx.instance)) {
            return null;
        }
        return LocalServerControllerClient.status(ctx.instance);
    }

    private boolean isLocalInstance(Instance instance) {
        if (instance == null || instance.getBackendConfig() == null || instance.getBackendConfig().type == null) {
            return true;
        }
        return "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
    }

    private void applyLocalControllerState(TabContext ctx, TerminalSession info, LocalServerControllerModels.StatusResponse status, boolean localServerStillRunning) {
        if (status == null || !status.knownSession) {
            return;
        }
        String state = status.state != null ? status.state.trim().toUpperCase(Locale.ROOT) : "";
        if (LifecycleManager.isStartPending(ctx.instance) && "CRASHED".equals(state)) {
            ctx.instance.setState(InstanceState.STARTING);
            return;
        }
        if (("CRASHED".equals(state) || "STOPPED".equals(state)) && localServerStillRunning && !LifecycleManager.isStopPending(ctx.instance)) {
            clearLocalControllerFailureNotice(ctx.instance);
            ctx.instance.setState("CRASHED".equals(state) ? InstanceState.CRASHED : InstanceState.STOPPED);
            return;
        }
        if ("CRASHED".equals(state) && isTransientControllerDisconnect(status)) {
            clearLocalControllerFailureNotice(ctx.instance);
            ctx.instance.setState(InstanceState.STOPPED);
            if (info != null && info.getTerminalWidget() instanceof ServerTerminal st && st.isTerminalReady()) {
                st.stopProcessAsync();
            }
            return;
        }
        switch (state) {
            case "STARTING" -> {
                ctx.instance.setState(InstanceState.STARTING);
            }
            case "RUNNING" -> {
                clearLocalControllerFailureNotice(ctx.instance);
                ctx.instance.setState(InstanceState.RUNNING);
            }
            case "STOPPING" -> {
                clearLocalControllerFailureNotice(ctx.instance);
                ctx.instance.setState(InstanceState.STOPPING);
            }
            case "STOPPED" -> {
                clearLocalControllerFailureNotice(ctx.instance);
                ctx.instance.setState(InstanceState.STOPPED);
                stopQuickServerReProxyIfForwarded(ctx.instance);
                QuickServerSyncManager.syncBackAfterStop(ctx.instance);
                if (ctx.instance.getState() == InstanceState.STOPPED && info != null && info.getTerminalWidget() instanceof ServerTerminal st && st.isTerminalReady()) {
                    st.stopProcessAsync();
                }
            }
            case "CRASHED" -> {
                notifyLocalControllerFailure(ctx, status);
                ctx.instance.setState(InstanceState.CRASHED);
                stopQuickServerReProxyIfForwarded(ctx.instance);
                if (ctx.instance.getState() == InstanceState.CRASHED && info != null && info.getTerminalWidget() instanceof ServerTerminal st && st.isTerminalReady()) {
                    st.stopProcessAsync();
                }
            }
        }
    }

    private boolean shouldVerifyLocalProcess(LocalServerControllerModels.StatusResponse status) {
        if (status == null || status.state == null) {
            return false;
        }
        return "CRASHED".equalsIgnoreCase(status.state) || "STOPPED".equalsIgnoreCase(status.state);
    }

    private boolean isTransientControllerDisconnect(LocalServerControllerModels.StatusResponse status) {
        if (status == null || status.lastError == null) {
            return false;
        }
        String error = status.lastError.toLowerCase(Locale.ROOT);
        return error.contains("connection reset") || error.contains("unexpected end of file") || error.contains("read timed out");
    }

    private void notifyLocalControllerFailure(TabContext ctx, LocalServerControllerModels.StatusResponse status) {
        if (ctx == null || ctx.instance == null || status == null || status.lastError == null || status.lastError.isBlank()) {
            return;
        }
        String id = ctx.instance.getInstanceId() != null && !ctx.instance.getInstanceId().isBlank() ? ctx.instance.getInstanceId() : ctx.instance.getPath();
        String key = id + "|" + status.lastError;
        if (!localControllerFailureNotices.add(key)) {
            return;
        }
        new Notification("Server Crashed", status.lastError, Notification.Type.ERROR);
    }

    private void clearLocalControllerFailureNotice(Instance instance) {
        if (instance == null) {
            return;
        }
        String id = instance.getInstanceId() != null && !instance.getInstanceId().isBlank() ? instance.getInstanceId() : instance.getPath();
        localControllerFailureNotices.removeIf(key -> key.startsWith(id + "|"));
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "-";
        double b = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (b >= 1024 && idx < units.length - 1) {
            b /= 1024;
            idx++;
        }
        if (idx <= 1) return String.format(Locale.ROOT, "%.0f%s", b, units[idx]);
        return String.format(Locale.ROOT, "%.1f%s", b, units[idx]);
    }

    private static String formatUptime(long uptimeMs) {
        if (uptimeMs <= 0) return "-";
        long totalSeconds = uptimeMs / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static final class ServerTabStatusContext extends TabContext implements TabStatusContext {
        private static final long CONNECTION_REFRESH_MS = 30000;
        private static final long COPIED_DISPLAY_MS = 2000;

        private IconButton connectionWidget;
        private IconButton uptimeWidget;
        private IconButton cpuWidget;
        private IconButton ramWidget;
        private String connectionInfo = "Loading...";
        private long copiedUntilMs;
        private long copyActionToken;
        private boolean connectionRequestInFlight;
        private long lastConnectionRequestAtMs;
        private boolean requestInFlight;
        private long lastRequestAtMs;

        private ServerTabStatusContext(Instance instance, Object id) {
            super(instance, id);
        }

        @Override
        public String getContextId() {
            if (instance != null && instance.getInstanceId() != null) return instance.getInstanceId();
            return id != null ? id.toString() : "";
        }

        @Override
        public void setupStatusBar(StatusBarBuilder builder) {
            if (connectionWidget == null) {
                connectionWidget = new IconButton.Builder()
                    .imagePath("clipboard")
                    .label(connectionInfo)
                    .hint("Click To Copy")
                    .autoWidthOnTextChange(true)
                    .size(0, 14)
                    .iconSize(12).iconPadding(2)
                    .transparent(true).animateElevation(false).entranceAnimation(false).elevateOnFocused(false)
                    .build();
            }
            updateConnectionWidget();

            if (uptimeWidget == null) {
                uptimeWidget = new IconButton.Builder()
                        .label("Uptime: -")
                        .autoWidthOnTextChange(true).transparent(true).animateElevation(false).entranceAnimation(false)
                        .build();
            }
            if (cpuWidget == null) {
                cpuWidget = new IconButton.Builder()
                        .label("Cpu: -")
                        .autoWidthOnTextChange(true).transparent(true).animateElevation(false).entranceAnimation(false)
                        .build();
            }
            if (ramWidget == null) {
                ramWidget = new IconButton.Builder()
                        .label("Ram: -")
                        .autoWidthOnTextChange(true).transparent(true).animateElevation(false).entranceAnimation(false)
                        .build();
            }

            builder.addLeft(connectionWidget);
            builder.addRight(uptimeWidget);
            builder.addRight(cpuWidget);
            builder.addRight(ramWidget);
        }

        private void refreshConnectionInfo(long nowMs) {
            if (connectionRequestInFlight) {
                if (nowMs - lastConnectionRequestAtMs > 5000) {
                    connectionRequestInFlight = false;
                } else {
                    return;
                }
            }
            if (nowMs - lastConnectionRequestAtMs < CONNECTION_REFRESH_MS) return;
            lastConnectionRequestAtMs = nowMs;
            connectionRequestInFlight = true;

            if (instance == null || instance.getBackend() == null) {
                connectionRequestInFlight = false;
                updateConnectionInfo("Unknown");
                return;
            }
            String forwardedAddress = quickServerReProxyAddress(instance);
            if (!forwardedAddress.isBlank()) {
                connectionRequestInFlight = false;
                updateConnectionInfo(forwardedAddress);
                return;
            }

            instance.getBackend().getFeature(ServerInfoFeature.class).ifPresentOrElse(feature -> feature.getConnectionInfo()
                    .thenAccept(info -> ScreenManager.getInstance().execute(() -> {
                        connectionRequestInFlight = false;
                        String address = quickServerReProxyAddress(instance);
                        updateConnectionInfo(address.isBlank() ? (info == null ? "Unknown" : info.getDisplayString()) : address);
                    }))
                    .exceptionally(e -> {
                        ScreenManager.getInstance().execute(() -> {
                            connectionRequestInFlight = false;
                            updateConnectionInfo("Unknown");
                        });
                        return null;
                    }),
                () -> ScreenManager.getInstance().execute(() -> {
                    connectionRequestInFlight = false;
                    updateConnectionInfo("Unknown");
                })
            );
        }

        private void updateConnectionInfo(String value) {
            connectionInfo = value == null || value.isBlank() ? "Unknown" : value.endsWith(":25565") ? value.substring(0, value.length() - 6) : value;
            updateConnectionWidget();
        }

        private String quickServerReProxyAddress(Instance instance) {
            if (instance == null || !"true".equalsIgnoreCase(instance.getSettings().getProperty("quickServer.enabled"))) {
                return "";
            }
            String address = ReProxyManager.getForwardedAddress(instance);
            return address == null ? "" : address;
        }

        private void updateConnectionWidget() {
            if (connectionWidget == null) return;
            long nowMs = System.currentTimeMillis();
            boolean showCopied = nowMs < copiedUntilMs;
            connectionWidget.setMessage(showCopied ? "Copied" : connectionInfo);
            connectionWidget.setIcon(showCopied ? "checkmark.png" : "clipboard.png");
            if ("Unknown".equals(connectionInfo) || "Loading...".equals(connectionInfo)) {
                connectionWidget.setOnClick(null);
            } else {
                connectionWidget.setOnClick(() -> {
                    try {
                        FileUtils.setClipboard(connectionInfo);
                        copiedUntilMs = System.currentTimeMillis() + COPIED_DISPLAY_MS;
                        long token = ++copyActionToken;
                        updateConnectionWidget();
                        CompletableFuture.runAsync(
                            () -> ScreenManager.getInstance().execute(() -> {
                                if (copyActionToken != token) return;
                                copiedUntilMs = 0;
                                updateConnectionWidget();
                            }),
                            CompletableFuture.delayedExecutor(COPIED_DISPLAY_MS, TimeUnit.MILLISECONDS)
                        );
                        ScreenManager.getInstance().execute(() -> new Notification.Builder()
                            .message("IP Copied!")
                            .autoSlideOut(true)
                            .dismissAfterSeconds(1)
                            .type(Notification.Type.SUCCESS)
                        );
                    } catch (Exception ignored) {}
                });
            }
        }

        private boolean tryStartRequest(long nowMs) {
            if (requestInFlight) {
                if (nowMs - lastRequestAtMs > 5000) {
                    requestInFlight = false;
                } else {
                    return false;
                }
            }
            if (nowMs - lastRequestAtMs < 1000) return false;
            requestInFlight = true;
            lastRequestAtMs = nowMs;
            return true;
        }

        private void finishRequest() {
            requestInFlight = false;
        }

        private void update(ResourceUsageFeature.ResourceUsage usage) {
            int players = 0;
            try {
                players = PlayerManagerController.getOrCreate(instance).getOnlinePlayerCount();
            } catch (Exception ignored) {
            }
            if (instance != null) {
                DiscordRpcBridge.updateServerMetrics(
                        instance,
                        players,
                        usage != null ? usage.uptimeMs() : 0,
                        usage != null ? usage.cpuPercent() : 0,
                        usage != null ? usage.memoryBytes() : 0,
                        usage != null ? usage.memoryLimitBytes() : 0
                );
            }
            if (usage == null) {
                uptimeWidget.setMessage("");
                cpuWidget.setMessage("");
                ramWidget.setMessage("");
                return;
            }

            uptimeWidget.setMessage("Uptime: " + formatUptime(usage.uptimeMs()));
            cpuWidget.setMessage("CPU: " + String.format(Locale.ROOT, "%.0f%%", usage.cpuPercent()));

            if (usage.memoryLimitBytes() > 0) {
                ramWidget.setMessage("RAM: " + formatBytes(usage.memoryBytes()) + "/" + formatBytes(usage.memoryLimitBytes()));
            } else {
                ramWidget.setMessage("RAM: " + formatBytes(usage.memoryBytes()));
            }
        }
    }

    @Override
    public List<String> getLeftLines() {
        List<String> info = new ArrayList<>();
        TabContext ctx = getActiveContext();
        if (ctx == null) { info.add("No Active Context"); return info; }
        TerminalSession sInfo = contextInfos.get(ctx);

        if (sInfo.isLocalTerminalMode()) {
            info.add("Mode: Local Terminal");
            info.add("Term ID: " + sInfo.getLocalTerminalId());
        } else if (ctx.instance != null) {
            info.add("Mode: Instance (" + ctx.instance.getName() + ")");
            info.add("State: " + ctx.instance.getState());
            MSMPManager msmp = ctx.instance.getMSMPManager();
            info.add("MSMP: " + (msmp.isConnected ? "Connected" : "Disconnected"));
            if (ctx.instance.getBackend() != null) info.add("Backend: " + ctx.instance.getBackendConfig().type);
            else info.add("Backend: None (Local)");
        }
        return info;
    }

    @Override
    public List<String> getRightLines() {
        List<String> info = new ArrayList<>();
        TabContext ctx = getActiveContext();
        if (ctx != null && ctx.instance != null) {
            PlayerManagerController pmc = PlayerManagerController.getOrCreate(ctx.instance);
            LuckPermsService lp = pmc.getLuckPermsService();
            if (lp != null) info.add("LuckPerms: " + (lp.isEnabled() ? "Enabled" : "Disabled"));
            info.add("View: " + ctx.selectedViewIndex);
        }
        return info;
    }

    @Override
    public void removed() {
        syncTabStoreFromTabs();
        super.removed();
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            statusScheduler = null;
        }
        if (instance != null) {
            instance.removeStateListener(stateListener);
        }
    }

    private static class StreamDataParser implements BiConsumer<Integer, String> {
        private final PlayerManagerController controller;
        private final Pattern startPattern = Pattern.compile("\\[FILE_START:(.+)]");
        private final Pattern endPattern = Pattern.compile("\\[FILE_END:(.+)]");
        private boolean isReading = false;
        private String currentFile = null;
        private final StringBuilder buffer = new StringBuilder();

        public StreamDataParser(PlayerManagerController controller) {
            this.controller = controller;
        }

        @Override
        public void accept(Integer integer, String line) {
            if (line == null) return;
            String trimmedLine = line.trim();

            if (isReading) {
                Matcher endMatcher = endPattern.matcher(trimmedLine);
                if (endMatcher.find()) {
                    int markerStart = line.indexOf("[FILE_END:");
                    if (markerStart > 0) {
                        buffer.append(line, 0, markerStart).append("\n");
                    }
                    String fileName = endMatcher.group(1);
                    if (fileName.equals(currentFile)) {
                        DebugManager.getInstance().log("StreamDataParser", "Finished reading file: " + fileName);
                        controller.handleFileUpdate(fileName, buffer.toString());
                        isReading = false;
                        currentFile = null;
                        buffer.setLength(0);
                    }
                } else {
                    buffer.append(line).append("\n");
                }
            } else {
                Matcher startMatcher = startPattern.matcher(trimmedLine);
                if (startMatcher.find()) {
                    currentFile = startMatcher.group(1);
                    DebugManager.getInstance().log("StreamDataParser", "Started reading file: " + currentFile);
                    isReading = true;
                    buffer.setLength(0);
                    int markerEnd = line.indexOf(']');
                    if (markerEnd >= 0 && markerEnd + 1 < line.length()) {
                        buffer.append(line.substring(markerEnd + 1)).append("\n");
                    }
                }
            }
        }
    }
}
