package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.discord.DiscordRpcBridge;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsClient;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.servers.ReProxyManager;
import redxax.oxy.remotely.session.TerminalSession;
import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.Rebase;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedExecutionProvider;
import restudio.rebase.api.unified.internal.StandardOutputStateParser;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.feature.DataStreamFeature;
import restudio.rebase.backend.feature.ResourceUsageFeature;
import restudio.rebase.backend.feature.ServerHealthFeature;
import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.backend.impl.ReStudioBackend;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceFactory;
import restudio.rebase.instance.InstanceRepairer;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.resource.InstanceDropImporter;
import restudio.rebase.twin.ServerTwinManager.ServerTwin;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LocalServerControllerModels;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.instance.InstanceDetailsScreen;
import restudio.rebase.ui.widgets.LifecycleButtonWidget;
import restudio.rebase.ui.screens.resources.ResourceContainer;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rebase.util.Executors;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.debug.DebugManager;
import restudio.rescreen.debug.IDebugInfoProvider;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReDropEvent;
import restudio.rescreen.platform.input.ReKeyEvent;
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
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.BrowserUtils;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static restudio.rescreen.config.Config.desktopMode;

public class ServerDetailsScreen extends InstanceDetailsScreen implements IDebugInfoProvider, DesktopWindowBehaviorProvider {

    private final RemotelyClient remotelyClient;
    private final Object parent;
    private final Instance initialInstanceToOpen;
    private boolean openDevelopmentOnStart;
    private LifecycleButtonWidget startIconButton;
    private ToggleWidget developmentModeToggle;
    private boolean applyingDevelopmentMode;
    private PopupWidget networkSummaryPopup;
    private PopupWidget serverHealthPopup;
    private TabContext serverHealthPopupContext;
    private Instance sidecarInstance;
    private ServerDevelopmentPanel developmentPanel;

    private final Map<TabContext, TerminalSession> contextInfos = new HashMap<>();
    private final Map<TabContext, DevelopmentTabState> developmentTabs = new IdentityHashMap<>();
    private ScheduledExecutorService statusScheduler;
    private SearchMode headerSearchMode;
    private SearchMode resourcesSearchMode;
    private SearchMode playersSearchMode;
    private final Set<String> localControllerFailureNotices = new HashSet<>();
    private final Map<String, Long> serverHealthChecksInFlight = new ConcurrentHashMap<>();
    private final Set<String> serverHealthRepairsInFlight = ConcurrentHashMap.newKeySet();
    private final AtomicLong serverHealthRequestSequence = new AtomicLong();
    private final Map<String, Consumer<InstanceState>> restartListeners = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private static final long LOCAL_STOP_GRACE_MS = 15_000;
    private static final long KILL_CONFIRM_MS = 5_000;
    private String killConfirmInstanceId;
    private String killingInstanceId;
    private long killConfirmUntilMs;
    private static final int TERMINAL_SCROLLBAR_WIDTH = 2;
    private final ScrollbarController terminalScrollbarController = new ScrollbarController();

    private enum ServerHealthRepair {
        NONE,
        EULA,
        SERVER_JAR,
        START_SCRIPT
    }

    private static final class DevelopmentTabState {
        private final Instance remote;
        private final Instance local;
        private final List<ViewEntry> remoteViews;
        private final List<ViewEntry> localViews;
        private final TerminalSession remoteSession;
        private final TerminalSession localSession;
        private int remoteView;
        private int localView;
        private boolean localActive;

        private DevelopmentTabState(Instance remote, Instance local, List<ViewEntry> remoteViews, List<ViewEntry> localViews,
                                    TerminalSession remoteSession, TerminalSession localSession, int remoteView) {
            this.remote = remote;
            this.local = local;
            this.remoteViews = remoteViews;
            this.localViews = localViews;
            this.remoteSession = remoteSession;
            this.localSession = localSession;
            this.remoteView = remoteView;
        }
    }

    public ServerDetailsScreen(Object parent, RemotelyClient client) {
        this(parent, client, null, false);
    }

    public ServerDetailsScreen(Object parent, RemotelyClient client, Instance initialInstanceToOpen) {
        this(parent, client, initialInstanceToOpen, false);
    }

    public ServerDetailsScreen(Object parent, RemotelyClient client, Instance initialInstanceToOpen, boolean openDevelopmentOnStart) {
        super(parent instanceof Screen ? (Screen) parent : null, null);
        this.parent = parent;
        this.remotelyClient = client;
        this.initialInstanceToOpen = initialInstanceToOpen;
        this.openDevelopmentOnStart = openDevelopmentOnStart;
    }

    @Override
    public void init() {
        TabContext previousContext = getActiveContext();
        DevelopmentTabState developmentToRestore = previousContext == null ? null : developmentTabs.get(previousContext);
        boolean restoreDevelopmentPanel = developmentPanel != null && developmentPanel.isRequestedVisible();
        closed = false;
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            statusScheduler = null;
        }
        statusContexts.clear();
        developmentTabs.clear();
        hideServerHealthPopup();
        serverHealthChecksInFlight.clear();
        super.init();
        statusBar().size(14).visible(false).build();
        applyStatusBarForActiveTab();
        startStatusScheduler();
        header().reset();
        setupHeader();
        setupDevelopmentPanel();
        TabContext ctx = getActiveContext();
        if (ctx != null) {
            developmentPanel.activeServerChanged(ctx.instance);
        }
        if (ctx != null && ctx.selectedViewIndex < ctx.views.size()) {
            onViewChanged(ctx, ctx.views.get(ctx.selectedViewIndex));
        }
        if (developmentToRestore != null) {
            ServerTwin twin = Rebase.get().getTwinManager().getTwinForSource(developmentToRestore.remote);
            Instance local = twin == null ? null : Rebase.get().getTwinManager().getTwinInstance(twin);
            if (local != null) {
                openDevelopmentTab(developmentToRestore.remote, local);
                TabContext restoredContext = getActiveContext();
                DevelopmentTabState restored = restoredContext == null ? null : developmentTabs.get(restoredContext);
                if (restored != null) {
                    restored.remoteView = developmentToRestore.remoteView;
                    restored.localView = developmentToRestore.localView;
                }
                setDevelopmentLocal(false);
                if (developmentToRestore.localActive) setDevelopmentLocal(true);
                developmentPanel.restoreVisibility(restoreDevelopmentPanel);
            }
            openDevelopmentOnStart = false;
        } else if (openDevelopmentOnStart && ctx != null && ctx.instance != null) {
            developmentPanel.open(ctx.instance);
            openDevelopmentOnStart = false;
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
            if (openDevelopmentOnStart) {
                existing.openDevelopment(initialInstanceToOpen);
            }
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
        header().addRight("merge.png", this::openDevModeScreen, "Development");

        startIconButton = new LifecycleButtonWidget(this::launchOrStopInstance, "Server")
                .shiftAction("Restart Server", "Restart Server", "reload.png", state -> state == InstanceState.RUNNING);
        developmentModeToggle = new ToggleWidget.Builder().label("Local").toggled(true).size(66, 18).animateElevation(false).entranceAnimation(false).onChange(() -> {
            if (!applyingDevelopmentMode) setDevelopmentLocal(developmentModeToggle.getValue());
        }).build();
        developmentModeToggle.setHint("Switch Local And Remote");
        developmentModeToggle.setVisible(false);
        header().addLeft(developmentModeToggle);
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
        List<Object> canonicalTabs = new ArrayList<>();
        for (Object entry : tabStore) {
            Object canonical = entry instanceof Instance instance ? developmentSource(instance) : entry;
            boolean duplicate = canonical instanceof Instance candidate && canonicalTabs.stream()
                    .anyMatch(existing -> existing instanceof Instance instance && sameInstance(instance, candidate));
            if (!duplicate) canonicalTabs.add(canonical);
        }
        tabStore.clear();
        tabStore.addAll(canonicalTabs);
        Instance requestedInstance = developmentSource(initialInstanceToOpen);
        int activeIndex = requestedInstance == null ? getSavedTabIndex() : indexOfInstance(tabStore, requestedInstance);
        if (requestedInstance != null && activeIndex < 0) {
            tabStore.add(requestedInstance);
            activeIndex = tabStore.size() - 1;
        } else if (tabStore.isEmpty()) {
            tabStore.add(UUID.randomUUID().toString());
            activeIndex = 0;
        }
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
        tab.setData(tabInfo);
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
                ResourceContainer res = new ResourceContainer(this, inst, 5, 60, width - 10, height - 66 - statusPad, InstanceResourceWidget::new, true);
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

                ReLog.logger(LogTypes.FLOW).source(LogSource.instance(inst.getInstanceId(), inst.getName())).component(ServerDetailsScreen.class).operation("Attach Data Stream").debug("Data stream attached");
                dataStreamFeature.get().streamData(logPath, preFiles, dataParser);
            } else {
                ReLog.logger(LogTypes.FLOW).source(LogSource.instance(inst.getInstanceId(), inst.getName())).component(ServerDetailsScreen.class).operation("Attach Data Stream").warn("Data stream is unavailable");
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
        boolean developmentTab = developmentTabs.containsKey(context);
        header().setButtonVisible("merge.png", developmentTab || isInstance && !pteroInstance && isDevModeEligible(context.instance));
        if (developmentModeToggle != null) {
            developmentModeToggle.setVisible(developmentTab);
            DevelopmentTabState development = developmentTabs.get(context);
            if (development != null) updateDevelopmentModeToggle(development.localActive);
        }

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
        if (developmentPanel != null) {
            DevelopmentTabState development = developmentTabs.get(ctx);
            if (development != null) {
                developmentPanel.developmentModeChanged(development.remote, development.local, development.localActive);
            } else {
                developmentPanel.activeServerChanged(ctx.instance);
            }
        }
    }

    private void onTabClosed(TabsManager.Tab tab) {
        TabContext ctx = tabContexts.remove(tab);
        if (ctx != null) {
            if (serverHealthPopupContext == ctx) {
                hideServerHealthPopup();
            }
            if (ctx.instance != null) {
                serverHealthChecksInFlight.remove(killKey(ctx.instance));
            }
            DevelopmentTabState development = developmentTabs.remove(ctx);
            TerminalSession info = contextInfos.remove(ctx);

            if (development != null) {
                if (development.remoteSession != null) remotelyClient.getSessionManager().destroySession(development.remoteSession.getTabId());
                if (development.localSession != null) remotelyClient.getSessionManager().destroySession(development.localSession.getTabId());
                development.remote.removeStateListener(stateListener);
                development.local.removeStateListener(stateListener);
                development.remote.getMSMPManager().disconnect();
                development.local.getMSMPManager().disconnect();
            } else if (info != null) {
                remotelyClient.getSessionManager().destroySession(info.getTabId());
            }

            if (development == null && ctx.instance != null) {
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
            DevelopmentTabState development = developmentTabs.get(context);
            Instance instance = development == null ? context.instance : development.remote;
            String oldName = instance.getName();
            InstanceManager.getInstance().renameInstance(instance, newName).thenRun(() -> {
                if (instance.getBackend() instanceof ReStudioBackend reStudioBackend) {
                    String serverId = reStudioBackend.getServerId();
                    ReStudio.getInstance().getApi().renameServer(serverId, newName).exceptionally(e -> {
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
                DevelopmentTabState development = developmentTabs.get(context);
                newTabOrder.add(development != null ? development.remote : context.instance != null ? context.instance : context.id);
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
        Instance canonicalInstance = developmentSource(instanceToAdd);
        if (canonicalInstance == null) return;
        List<TabsManager.Tab> openTabs = tabs().getTabs();
        for (int i = 0; i < openTabs.size(); i++) {
            TabContext context = tabContexts.get(openTabs.get(i));
            DevelopmentTabState development = context == null ? null : developmentTabs.get(context);
            if (context != null && (sameInstance(context.instance, canonicalInstance)
                    || development != null && (sameInstance(development.remote, canonicalInstance) || sameInstance(development.local, canonicalInstance)))) {
                tabs().setActiveTab(i);
                setSavedTabIndex(i);
                return;
            }
        }
        List<Object> tabStore = getTabStore();
        if (tabStore.stream().noneMatch(tab -> tab instanceof Instance instance && sameInstance(instance, canonicalInstance))) {
            tabStore.add(canonicalInstance);
        }
        createAndAddTab(canonicalInstance, true);
    }

    void openDevelopmentTab(Instance remote, Instance local) {
        if (remote == null || local == null) return;
        addInstanceTab(remote);
        TabsManager.Tab tab = tabs().getActiveTab();
        TabContext context = tab == null ? null : tabContexts.get(tab);
        if (context == null) return;
        DevelopmentTabState state = developmentTabs.get(context);
        if (state == null || !sameInstance(state.remote, remote) || !sameInstance(state.local, local)) {
            TerminalSession remoteSession = contextInfos.get(context);
            TabContext localContext = new ServerTabStatusContext(local, local);
            localContext.mainContainer = context.mainContainer;
            TerminalSession localSession = initializeTabContext(localContext, local, local, null, 15);
            state = new DevelopmentTabState(remote, local, new ArrayList<>(context.views), new ArrayList<>(localContext.views), remoteSession, localSession, context.selectedViewIndex);
            developmentTabs.put(context, state);
            tab.setData(remote);
            tab.setName(remote.getName());
        }
        switchDevelopmentMode(context, tab, state, true);
    }

    void setDevelopmentLocal(boolean local) {
        TabsManager.Tab tab = tabs().getActiveTab();
        TabContext context = tab == null ? null : tabContexts.get(tab);
        DevelopmentTabState state = context == null ? null : developmentTabs.get(context);
        if (state != null) switchDevelopmentMode(context, tab, state, local);
    }

    boolean isActiveDevelopment(Instance remote) {
        TabContext context = getActiveContext();
        DevelopmentTabState state = context == null ? null : developmentTabs.get(context);
        return state != null && sameInstance(state.remote, remote);
    }

    private void switchDevelopmentMode(TabContext context, TabsManager.Tab tab, DevelopmentTabState state, boolean local) {
        if (state.localActive == local && sameInstance(context.instance, local ? state.local : state.remote)) return;
        for (ViewEntry view : context.views) {
            List<AnimatedWidget> tools = view.loadedToolbarWidgets();
            if (tools != null) tools.forEach(tool -> tool.setVisible(false));
        }
        if (state.localActive) state.localView = context.selectedViewIndex;
        else state.remoteView = context.selectedViewIndex;
        state.localActive = local;
        context.instance = local ? state.local : state.remote;
        context.id = state.remote;
        context.views.clear();
        context.views.addAll(local ? state.localViews : state.remoteViews);
        context.selectedViewIndex = Math.clamp(local ? state.localView : state.remoteView, 0, Math.max(0, context.views.size() - 1));
        contextInfos.put(context, local ? state.localSession : state.remoteSession);
        tab.setData(state.remote);
        tab.setName(state.remote.getName());
        updateDevelopmentModeToggle(local);
        onTabSelected(tab);
    }

    private void updateDevelopmentModeToggle(boolean local) {
        if (developmentModeToggle == null) return;
        applyingDevelopmentMode = true;
        developmentModeToggle.setValue(local);
        developmentModeToggle.setMessage(local ? "Local" : "Remote");
        developmentModeToggle.setAccent(ThemeManager.getDefaultAccent());
        developmentModeToggle.setVisible(true);
        applyingDevelopmentMode = false;
    }

    private Instance developmentSource(Instance candidate) {
        if (candidate == null) return null;
        ServerTwin twin = Rebase.get().getTwinManager().getTwins().stream()
                .filter(item -> Objects.equals(item.twinInstanceId, candidate.getInstanceId()))
                .findFirst()
                .orElse(null);
        if (twin == null || twin.sourceInstanceId == null || twin.sourceInstanceId.isBlank()) return candidate;
        Instance source = InstanceManager.getInstance().getInstanceById(twin.sourceInstanceId);
        return source == null ? candidate : source;
    }

    private int indexOfInstance(List<Object> entries, Instance candidate) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof Instance instance && sameInstance(instance, candidate)) return i;
        }
        return -1;
    }

    void closeInstanceTab(Instance instanceToClose) {
        if (instanceToClose == null) return;
        List<TabsManager.Tab> openTabs = tabs().getTabs();
        for (int i = 0; i < openTabs.size(); i++) {
            TabContext context = tabContexts.get(openTabs.get(i));
            DevelopmentTabState development = context == null ? null : developmentTabs.get(context);
            if (context != null && (sameInstance(context.instance, instanceToClose)
                    || development != null && (sameInstance(development.remote, instanceToClose) || sameInstance(development.local, instanceToClose)))) {
                tabs().removeTab(i);
                return;
            }
        }
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
        launchOrStopInstance(true);
    }

    private void launchOrStopInstance(boolean allowRestart) {
        TabContext context = getActiveContext();
        if (context == null) return;
        TerminalSession info = contextInfos.get(context);
        if (info == null || info.isLocalTerminalMode()) return;
        if (allowRestart && hasShiftDown() && context.instance.getState() == InstanceState.RUNNING) {
            restartInstance(context, info);
            return;
        }
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
            boolean terminalHandlesLocalStop = info.getTerminalWidget() instanceof ServerTerminal;
            if (terminalHandlesLocalStop) {
                ServerTerminal st = (ServerTerminal) info.getTerminalWidget();
                st.notifyStopRequested();
            }
            String t = context.instance.getBackend() != null ? context.instance.getBackend().getFileSystem().getMetadata("type") : "";
            stopReProxyIfForwarded(context.instance);
            if ("LOCAL".equalsIgnoreCase(t) && !terminalHandlesLocalStop) {
                String stopOperationId = LifecycleManager.requestStop(context.instance);
                context.instance.setState(InstanceState.STOPPING);
                api.console().stopServer().thenRun(() -> LifecycleManager.complete(context.instance, stopOperationId, InstanceState.STOPPED)).exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> LifecycleManager.restoreRunning(context.instance, stopOperationId, unwrapThrowable(e).getMessage()));
                    return null;
                });
            }
            if (!"LOCAL".equalsIgnoreCase(t)) {
                String stopOperationId = LifecycleManager.requestStop(context.instance);
                context.instance.setState(InstanceState.STOPPING);
                api.console().stopServer().thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    if (PteroBackend.isPanelType(t)) {
                        return;
                    }
                    if (info.getTerminalWidget() != null) {
                        info.getTerminalWidget().stopProcess();
                    }
                    LifecycleManager.complete(context.instance, stopOperationId, InstanceState.STOPPED);
                })).exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> {
                        Throwable cause = unwrapThrowable(e);
                        String message = cause.getMessage() != null ? cause.getMessage() : "Remote stop failed.";
                        if (message.toLowerCase(Locale.ROOT).contains("not running")) {
                            if (info.getTerminalWidget() != null) {
                                info.getTerminalWidget().stopProcess();
                            }
                            LifecycleManager.complete(context.instance, stopOperationId, InstanceState.STOPPED);
                            return;
                        }
                        LifecycleManager.restoreRunning(context.instance, stopOperationId, message);
                        new Notification("Server Stop Failed", message, Notification.Type.ERROR);
                    });
                    return null;
                });
            }
        } else {
            startInstance(context, info);
        }
    }

    private void restartInstance(TabContext context, TerminalSession info) {
        Instance target = context.instance;
        String key = killKey(target);
        Consumer<InstanceState> listener = new Consumer<>() {
            private boolean stopping;

            @Override
            public void accept(InstanceState state) {
                if (state == InstanceState.STOPPING) {
                    stopping = true;
                    return;
                }
                if (!stopping) {
                    return;
                }
                if (state == InstanceState.STOPPED || state == InstanceState.CRASHED) {
                    target.removeStateListener(this);
                    if (restartListeners.remove(key, this)) {
                        ScreenManager.getInstance().execute(() -> startInstance(context, info));
                    }
                } else if (state == InstanceState.RUNNING) {
                    target.removeStateListener(this);
                    restartListeners.remove(key, this);
                }
            }
        };
        if (restartListeners.putIfAbsent(key, listener) != null) {
            return;
        }
        target.addStateListener(listener);
        launchOrStopInstance(false);
    }

    private void startInstance(TabContext context, TerminalSession info) {
        if (!canContinueServerStart(context, info)) {
            return;
        }
        String healthKey = killKey(context.instance);
        long requestId = serverHealthRequestSequence.incrementAndGet();
        if (serverHealthChecksInFlight.putIfAbsent(healthKey, requestId) != null) {
            return;
        }
        InstanceApi api = InstanceApi.of(context.instance);
        api.health().check().whenComplete((status, error) -> {
            ScreenManager.getInstance().execute(() -> {
                if (!serverHealthChecksInFlight.remove(healthKey, requestId)) {
                    return;
                }
                if (!canContinueServerStart(context, info)) {
                    return;
                }
                if (error != null) {
                    Throwable cause = unwrapThrowable(error);
                    String message = cause.getMessage() == null || cause.getMessage().isBlank() ? "The Server Health Check Could Not Be Completed." : cause.getMessage();
                    new Notification("Health Check Failed", message, Notification.Type.ERROR);
                    return;
                }
                if (status == null) {
                    new Notification("Health Check Failed", "The Server Health Status Was Unavailable.", Notification.Type.ERROR);
                    return;
                }
                if (!status.isHealthy()) {
                    showServerHealthPopup(context, info, status);
                    return;
                }

                proceedWithServerStart(context, info);
            });
        });
    }

    private boolean canContinueServerStart(TabContext context, TerminalSession info) {
        return !closed && context != null && context.instance != null && info != null && tabContexts.containsValue(context)
                && contextInfos.get(context) == info && LifecycleButtonWidget.canStart(context.instance.getState());
    }

    private boolean isServerContextAvailable(TabContext context, TerminalSession info) {
        return !closed && context != null && context.instance != null && info != null && tabContexts.containsValue(context) && contextInfos.get(context) == info;
    }

    private void hideServerHealthPopup() {
        if (serverHealthPopup != null) {
            serverHealthPopup.hide();
        }
        serverHealthPopup = null;
        serverHealthPopupContext = null;
    }

    private void hideServerHealthPopup(PopupWidget popup) {
        if (popup != null) {
            popup.hide();
        }
        if (serverHealthPopup == popup) {
            serverHealthPopup = null;
            serverHealthPopupContext = null;
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
        if (LifecycleButtonWidget.canStart(state)) {
            clearKillConfirmation(context.instance);
            startIconButton.update(state);
            header().requestLayoutUpdate();
            return;
        }
        if (state != InstanceState.STOPPING) {
            clearKillConfirmation(context.instance);
        }
        boolean killConfirmActive = isKillConfirmationActive(context.instance);
        boolean killing = isKilling(context.instance);
        startIconButton.update(state, killConfirmActive, killing);
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
        String stopOperationId = LifecycleManager.requestStop(instance);
        instance.setState(InstanceState.STOPPING);
        InstanceApi.of(instance).console().killServer().thenRun(() -> ScreenManager.getInstance().execute(() -> {
            clearKillConfirmation(instance);
            if (info.getTerminalWidget() != null) {
                info.getTerminalWidget().stopProcess();
            }
            LifecycleManager.complete(instance, stopOperationId, InstanceState.STOPPED);
            QuickServerSyncManager.syncBackAfterStop(instance);
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                killingInstanceId = null;
                Throwable cause = unwrapThrowable(e);
                String message = cause.getMessage() != null ? cause.getMessage() : "Server kill failed.";
                if (isPteroInstance(instance) && message.contains("429")) {
                    LifecycleManager.restoreRunning(instance, stopOperationId, message);
                    if (instance.getBackend() != null) {
                        instance.getBackend().getFeature(ResourceUsageFeature.class).ifPresent(feature -> feature.getResources().exceptionally(ex -> null));
                    }
                    updateStartButton(context, info);
                    return;
                }
                LifecycleManager.restoreRunning(instance, stopOperationId, message);
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

    private void showServerHealthPopup(TabContext context, TerminalSession info, ServerHealthFeature.ServerHealthStatus status) {
        hideServerHealthPopup();
        ServerHealthPopupState popupState = new ServerHealthPopupState(context, info, status);
        serverHealthPopup = popupState.popup;
        serverHealthPopupContext = context;
        popupState.show();
    }

    private final class ServerHealthPopupState {
        private final TabContext context;
        private final TerminalSession info;
        private final String healthKey;
        private final PopupWidget popup;
        private final IconButton eulaButton;
        private final IconButton serverJarButton;
        private final IconButton startScriptButton;
        private final SquareButtonWidget eulaFixButton;
        private final SquareButtonWidget serverJarFixButton;
        private final SquareButtonWidget startScriptFixButton;
        private final IconButton launchButton;
        private final IconButton launchAnywayButton;
        private boolean eulaAccepted;
        private boolean serverJarReady;
        private boolean startScriptReady;
        private ServerHealthRepair repair = ServerHealthRepair.NONE;

        private ServerHealthPopupState(TabContext context, TerminalSession info, ServerHealthFeature.ServerHealthStatus status) {
            this.context = context;
            this.info = info;
            healthKey = killKey(context.instance);
            eulaAccepted = status.eulaAccepted();
            serverJarReady = status.hasServerJar();
            startScriptReady = status.hasStartScript();

            PopupWidget.Builder builder = new PopupWidget.Builder("Server Health • " + context.instance.getName()).width(350).setResizable(false).setAntiOutOfBound(true);
            popup = builder.getWidget();
            eulaButton = new IconButton.Builder().size(0, 18).active(false).inClickableWhenInactive(true).hint("Open Minecraft EULA")
                    .onClick(() -> BrowserUtils.openBrowser("https://www.minecraft.net/en-us/eula")).build();
            serverJarButton = new IconButton.Builder().size(0, 18).active(false).build();
            startScriptButton = new IconButton.Builder().size(0, 18).active(false).build();
            eulaFixButton = healthFixButton("Fix EULA", this::acceptEula);
            serverJarFixButton = healthFixButton("Fix Server Jar", this::downloadServerJar);
            startScriptFixButton = healthFixButton("Fix Start Script", this::createStartScript);
            launchButton = new IconButton.Builder().size(0, 18).label("Launch Server").imagePath("start.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> launch(false)).build();
            launchAnywayButton = new IconButton.Builder().size(0, 18).label("Launch Anyway").imagePath("report.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> launch(true)).build();

            builder.addRow(new PopupWidget.PopupRow.Builder("", eulaButton, eulaFixButton).gap(1).build());
            builder.addRow(new PopupWidget.PopupRow.Builder("", serverJarButton, serverJarFixButton).gap(1).build());
            builder.addRow(new PopupWidget.PopupRow.Builder("", startScriptButton, startScriptFixButton).gap(1).build());
            builder.addRow(new PopupWidget.PopupRow.Builder("", launchButton, launchAnywayButton).gap(1).build());
            builder.build();
            popup.onClose = () -> {
                if (serverHealthPopup == popup) {
                    serverHealthPopup = null;
                    serverHealthPopupContext = null;
                }
            };
            refresh();
        }

        private void show() {
            addDrawableChild(popup);
            popup.show();
        }

        private void refresh() {
            updateHealthButton(eulaButton, eulaFixButton, eulaAccepted, ServerHealthRepair.EULA, "EULA", "Accepted", "Needs Agreement", "Saving Agreement...", "info.png");
            updateHealthButton(serverJarButton, serverJarFixButton, serverJarReady, ServerHealthRepair.SERVER_JAR, "Server Jar", "Ready", "Missing", "Downloading...", "java.png");
            updateHealthButton(startScriptButton, startScriptFixButton, startScriptReady, ServerHealthRepair.START_SCRIPT, "Start Script", "Ready", "Missing", "Creating...", "script.png");
            launchButton.setActive(repair == ServerHealthRepair.NONE && isHealthy());
            launchAnywayButton.setActive(repair == ServerHealthRepair.NONE);
        }

        private SquareButtonWidget healthFixButton(String hint, Runnable action) {
            return new SquareButtonWidget.Builder().size(18, 18).identifier(Identifier.icon("checkmark.png")).hint(hint)
                    .accentType(ThemeManager.getAccent("nice")).onClick(action).build();
        }

        private void updateHealthButton(IconButton button, SquareButtonWidget fixButton, boolean ready, ServerHealthRepair target, String name, String readyLabel,
                                        String missingLabel, String progressLabel, String icon) {
            boolean repairing = repair == target;
            button.setMessage(name + " • " + (ready ? readyLabel : repairing ? progressLabel : missingLabel));
            button.setIcon(icon);
            button.setAccent(ready ? ThemeManager.getAccent("nice") : ThemeManager.getDefaultAccent());
            fixButton.setActive(!ready && repair == ServerHealthRepair.NONE);
        }

        private void acceptEula() {
            if (!beginRepair(ServerHealthRepair.EULA, eulaAccepted)) {
                return;
            }
            String previous = context.instance.getServerProperties().getProperty("eula");
            context.instance.getServerProperties().setProperty("eula", "true");
            context.instance.saveServerProperties().whenComplete((ignored, error) -> completeRepair(ServerHealthRepair.EULA, error, () -> eulaAccepted = true, () -> {
                if (previous == null) {
                    context.instance.getServerProperties().remove("eula");
                } else {
                    context.instance.getServerProperties().setProperty("eula", previous);
                }
            }, "EULA Agreement Failed"));
        }

        private void downloadServerJar() {
            if (!beginRepair(ServerHealthRepair.SERVER_JAR, serverJarReady)) {
                return;
            }
            Notification notification = new Notification.Builder().message("Downloading Server Jar...").type(Notification.Type.INFO).loading(true).build();
            new InstanceFactory().downloadMissingServerJar(context.instance, notification).whenComplete((ignored, error) -> ScreenManager.getInstance().execute(() -> {
                if (error == null) {
                    serverJarReady = true;
                    notification.update().message("Server Jar Downloaded").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true);
                } else {
                    notification.update().message("Server Jar Download Failed").description(failureMessage(error)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true);
                }
                finishRepair(ServerHealthRepair.SERVER_JAR);
            }));
        }

        private void createStartScript() {
            if (!beginRepair(ServerHealthRepair.START_SCRIPT, startScriptReady)) {
                return;
            }
            InstanceRepairer.createStartScript(context.instance).whenComplete((ignored, error) -> completeRepair(ServerHealthRepair.START_SCRIPT, error,
                    () -> startScriptReady = true, () -> { }, "Start Script Creation Failed"));
        }

        private boolean beginRepair(ServerHealthRepair target, boolean ready) {
            if (!isServerContextAvailable(context, info)) {
                hideServerHealthPopup(popup);
                return false;
            }
            if (ready || repair != ServerHealthRepair.NONE) {
                return false;
            }
            if (!serverHealthRepairsInFlight.add(healthKey)) {
                new Notification("Server Repair In Progress", "Wait For The Current Health Repair To Finish.", Notification.Type.INFO);
                return false;
            }
            repair = target;
            refresh();
            return true;
        }

        private void completeRepair(ServerHealthRepair target, Throwable error, Runnable success, Runnable failure, String errorTitle) {
            ScreenManager.getInstance().execute(() -> {
                if (error == null) {
                    success.run();
                } else {
                    failure.run();
                    new Notification(errorTitle, failureMessage(error), Notification.Type.ERROR);
                }
                finishRepair(target);
            });
        }

        private void finishRepair(ServerHealthRepair target) {
            if (repair != target) {
                return;
            }
            serverHealthRepairsInFlight.remove(healthKey);
            repair = ServerHealthRepair.NONE;
            refresh();
            if (serverHealthPopup != null && serverHealthPopup != popup && serverHealthPopupContext != null
                    && Objects.equals(killKey(serverHealthPopupContext.instance), healthKey)) {
                hideServerHealthPopup();
            }
        }

        private boolean isHealthy() {
            return eulaAccepted && serverJarReady && startScriptReady;
        }

        private void launch(boolean anyway) {
            if (!canContinueServerStart(context, info)) {
                hideServerHealthPopup(popup);
                return;
            }
            if (repair != ServerHealthRepair.NONE || !anyway && !isHealthy()) {
                return;
            }
            hideServerHealthPopup(popup);
            proceedWithServerStart(context, info);
        }

        private String failureMessage(Throwable throwable) {
            Throwable cause = unwrapThrowable(throwable);
            return cause.getMessage() == null || cause.getMessage().isBlank() ? "The Server Health Repair Could Not Be Completed." : cause.getMessage();
        }
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
            TerminalWidget tw = ServerTerminal.getOrCreate(context.instance, exec, context.mainContainer.getX(), context.mainContainer.getContentTop(),
                    context.mainContainer.getEffectiveWidth(), context.mainContainer.getContentHeight());
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
        String startOperationId = LifecycleManager.requestStart(context.instance);
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
            if (PteroBackend.isPanelType(type)) {
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
                String message = LocalServerControllerClient.describeFailure(cause.getMessage());
                boolean serverRunning = context.instance.getState() == InstanceState.RUNNING;
                if (!serverRunning) {
                    LifecycleManager.fail(context.instance, startOperationId, InstanceState.CRASHED, message);
                    if (info.getTerminalWidget() instanceof ServerTerminal st) {
                        st.notifyStopRequested();
                    } else if (info.getTerminalWidget() != null) {
                        info.getTerminalWidget().stopProcess();
                    }
                }
                new Notification(serverRunning ? "Terminal Unavailable" : "Server Start Failed", message, Notification.Type.ERROR);
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
                if (status == null || !status.knownSession || "STOPPED".equalsIgnoreCase(status.state)
                        || "CRASHED".equalsIgnoreCase(status.state) && (status.pids == null || status.pids.isEmpty())) {
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
                if (LifecycleManager.isStopPending(context.instance) || context.instance.getState() == InstanceState.STOPPING) {
                    return;
                }
                LifecycleManager.beginStart(context.instance);
                terminal.startServerProcess();
            });
        });
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
        DevelopmentTabState development = developmentTabs.get(context);
        if (development != null) {
            developmentPanel.toggle(development.remote);
            return;
        }
        if (!isDevModeEligible(context.instance)) {
            new Notification.Builder().message("Development Unavailable").description("Remote Servers Only").type(Notification.Type.ERROR).build();
            return;
        }
        if (developmentPanel != null) {
            developmentPanel.toggle(context.instance);
        }
    }

    private void setupDevelopmentPanel() {
        if (developmentPanel != null) {
            developmentPanel.dispose();
        }
        developmentPanel = new ServerDevelopmentPanel(this, remotelyClient);
    }

    @Override
    protected void onSidePanelWidthChanged() {
        super.onSidePanelWidthChanged();
        if (developmentPanel != null) {
            developmentPanel.layout();
        }
    }

    private void openDevelopment(Instance source) {
        if (developmentPanel != null && source != null) {
            developmentPanel.open(source);
        }
    }

    public void openInstanceSettings() {
        Instance target = ensureSidecar();
        if (target == null) return;
        if (isPteroInstance(target)) {
            new Notification("Panel Managed", "Use Files For Panel Settings", Notification.Type.WARN);
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
        return config != null && PteroBackend.isPanelType(config.type);
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

        if (developmentPanel != null) {
            developmentPanel.layout();
        }

        for (TabContext c : tabContexts.values()) {
            if (c == null || c.mainContainer == null) continue;
            if (!getGroupManager().isManaged(c.mainContainer)) {
                int pad = c.instance != null ? 15 : 0;
                c.mainContainer.setWidth(width - 10);
                c.mainContainer.setHeight(height - 65 - pad);
                c.mainContainer.updateWidgetPositions();
            }
        }

        TabContext ctx = getActiveContext();
        if (ctx == null) return;

        int pad = ctx.instance != null ? 15 : 0;
        if (ctx.selectedViewIndex < ctx.views.size()) {
            ViewEntry view = ctx.views.get(ctx.selectedViewIndex);
            boolean grouped = getGroupManager().isManaged(ctx.mainContainer);
            int newW = ctx.mainContainer.getEffectiveWidth();
            int newH = grouped ? ctx.mainContainer.getContentHeight() : height - 65 - pad;
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
    public void tick() {
        super.tick();
        if (developmentPanel != null) {
            developmentPanel.tick();
        }
    }

    @Override
    public void filesDropped(ReDropEvent event) {
        TabContext context = getActiveContext();
        Instance target = context != null ? context.instance : null;
        if (target == null || !target.isServer()) return;
        CompletableFuture.supplyAsync(() -> InstanceDropImporter.importFiles(target, event.files()), Executors.IO).thenAccept(result ->
                ScreenManager.getInstance().execute(() -> {
                    if (closed) return;
                    if (result.imported() > 0) {
                        new Notification("Import Complete", "Added " + result.imported() + " " + itemLabel(result.imported()) + ".", Notification.Type.SUCCESS);
                        TerminalSession session = contextInfos.get(context);
                        if (result.resourcesChanged() && session != null && session.getResourceContainer() != null) {
                            session.getResourceContainer().loadResources(true);
                        }
                    }
                    if (result.failed() > 0) {
                        new Notification("Import Incomplete", "Could Not Add " + result.failed() + " " + itemLabel(result.failed()) + ".", Notification.Type.WARN);
                    }
                }));
    }

    private static String itemLabel(int count) {
        return count == 1 ? "Item" : "Items";
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
                    ScreenManager.getInstance().execute(() -> {
                        TabContext active = getActiveContext();
                        if (active == ctx) {
                            boolean resourceRunning = usage != null && usage.uptimeMs() > 0;
                            if (quickServerRuntimeOpen) {
                                ctx.instance.setState(InstanceState.RUNNING);
                            } else if (isLocalInstance(ctx.instance)) {
                                applyLocalControllerState(ctx, info, localStatus);
                            }
                            statusCtx.update(usage);
                            boolean controllerAllowsRunning = localStatus == null || !localStatus.knownSession || localStatus.ready || "RUNNING".equalsIgnoreCase(localStatus.state);
                            if (isLocalInstance(ctx.instance) && controllerAllowsRunning && resourceRunning && ctx.instance.getState() == InstanceState.STOPPED) {
                                ctx.instance.setState(InstanceState.RUNNING);
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

    private void applyLocalControllerState(TabContext ctx, TerminalSession info, LocalServerControllerModels.StatusResponse status) {
        if (status == null || !status.knownSession) {
            return;
        }
        if (info != null && info.getTerminalWidget() instanceof ServerTerminal terminal && terminal.isStaleLocalControllerStatus(status)) {
            return;
        }
        String state = status.state != null ? status.state.trim().toUpperCase(Locale.ROOT) : "";
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
                if ("RUNNING".equalsIgnoreCase(status.desiredState)) {
                    LifecycleManager.requestStart(ctx.instance);
                } else {
                    LifecycleManager.requestStop(ctx.instance);
                }
                ctx.instance.setState(InstanceState.STOPPING);
            }
            case "STOPPED" -> {
                clearLocalControllerFailureNotice(ctx.instance);
                LifecycleManager.complete(ctx.instance, LifecycleManager.activeOperationId(ctx.instance), InstanceState.STOPPED);
                stopQuickServerReProxyIfForwarded(ctx.instance);
                QuickServerSyncManager.syncBackAfterStop(ctx.instance);
                if (ctx.instance.getState() == InstanceState.STOPPED && info != null && info.getTerminalWidget() instanceof ServerTerminal st && st.isTerminalReady()) {
                    st.stopProcessAsync();
                }
            }
            case "CRASHED" -> {
                notifyLocalControllerFailure(ctx, status);
                String message = status.lastError == null || status.lastError.isBlank() ? "Server Crashed" : status.lastError;
                LifecycleManager.fail(ctx.instance, LifecycleManager.activeOperationId(ctx.instance), InstanceState.CRASHED, message);
                stopQuickServerReProxyIfForwarded(ctx.instance);
                if (ctx.instance.getState() == InstanceState.CRASHED && info != null && info.getTerminalWidget() instanceof ServerTerminal st && st.isTerminalReady()) {
                    st.stopProcessAsync();
                }
            }
        }
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
        new Notification(Objects.equals(status.exitCode, 0) ? "Server Stopped During Startup" : "Server Crashed", status.lastError, Notification.Type.ERROR);
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

    private void showNetworkSummary(Instance instance) {
        if (instance == null || remotelyClient.getNetworkManager() == null) {
            return;
        }
        NetworkDefinition network = remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).orElse(null);
        if (network == null) {
            return;
        }
        NetworkMember member = network.members().stream().filter(candidate -> candidate.instanceId().equals(instance.getInstanceId())).findFirst().orElse(null);
        NetworkRuntimeSnapshot snapshot = remotelyClient.getNetworkManager().getRuntimeSnapshot(network.networkId());
        if (networkSummaryPopup != null) {
            remove(networkSummaryPopup);
        }
        String role = member == null ? "Member" : member.isProxy() ? "Proxy" : formatNetworkRole(member.role().name());
        String route = member == null ? "Unavailable" : member.routeName() + " • " + member.address() + ":" + member.port();
        String presence = snapshot.connected() ? snapshot.players() + " Shared Players" : "ReSync " + formatNetworkRole(snapshot.state().name());
        AnimatedButton identity = new AnimatedButton.Builder().label(role + " • " + network.members().size() + " Servers").active(false).accentType(ThemeManager.getAccent("calm")).build();
        AnimatedButton endpoint = new AnimatedButton.Builder().label(route).active(false).accentType(ThemeManager.getAccent("calm")).build();
        AnimatedButton runtime = new AnimatedButton.Builder().label(presence).active(false).accentType(ThemeManager.getAccent(snapshot.connected() ? "nice" : "warning")).build();
        PopupWidget.Builder builder = new PopupWidget.Builder(network.name()).width(390);
        builder.addRow("identity", "Network", identity);
        builder.addRow("route", "Route", endpoint);
        builder.addRow("presence", "Presence", runtime);
        networkSummaryPopup = builder.build();
        networkSummaryPopup.setX((width - networkSummaryPopup.getWidth()) / 2);
        networkSummaryPopup.setY((height - networkSummaryPopup.getHeight()) / 2);
        addDrawableChild(networkSummaryPopup);
        networkSummaryPopup.show();
    }

    private static String formatNetworkRole(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    private final class ServerTabStatusContext extends TabContext implements TabStatusContext {
        private static final long CONNECTION_REFRESH_MS = 30000;
        private static final long COPIED_DISPLAY_MS = 2000;

        private IconButton connectionWidget;
        private IconButton uptimeWidget;
        private IconButton cpuWidget;
        private IconButton ramWidget;
        private IconButton networkWidget;
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

            if (networkWidget == null) {
                networkWidget = new IconButton.Builder()
                    .imagePath("velocity.png")
                    .autoWidthOnTextChange(true)
                    .size(0, 14)
                    .iconSize(12).iconPadding(2)
                    .transparent(true).animateElevation(false).entranceAnimation(false).elevateOnFocused(false)
                    .build();
            }
            updateNetworkWidget();

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
            builder.addLeft(networkWidget);
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
            updateNetworkWidget();
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

        private void updateNetworkWidget() {
            if (networkWidget == null || instance == null || remotelyClient.getNetworkManager() == null) {
                return;
            }
            NetworkDefinition network = remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).orElse(null);
            if (network == null) {
                networkWidget.setVisible(false);
                networkWidget.setOnClick(null);
                return;
            }
            NetworkMember member = network.members().stream().filter(candidate -> candidate.instanceId().equals(instance.getInstanceId())).findFirst().orElse(null);
            NetworkRuntimeSnapshot snapshot = remotelyClient.getNetworkManager().getRuntimeSnapshot(network.networkId());
            String role = member == null ? "Member" : member.isProxy() ? "Proxy" : formatNetworkRole(member.role().name());
            String players = snapshot.connected() ? " • " + snapshot.players() + " Players" : "";
            networkWidget.setMessage(network.name() + " • " + role + players);
            networkWidget.setHint(snapshot.connected() ? "Network-Wide ReSync Presence" : "ReSync " + formatNetworkRole(snapshot.state().name()));
            networkWidget.setOnClick(() -> showNetworkSummary(instance));
            networkWidget.setVisible(true);
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
            ReSyncLuckPermsClient lp = pmc.getLuckPermsClient();
            if (lp != null) info.add("LuckPerms: " + (lp.isAvailable() ? "Available" : "Unavailable"));
            info.add("View: " + ctx.selectedViewIndex);
        }
        return info;
    }

    @Override
    public void removed() {
        closed = true;
        hideServerHealthPopup();
        serverHealthChecksInFlight.clear();
        if (developmentPanel != null) {
            developmentPanel.dispose();
            developmentPanel = null;
        }
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
