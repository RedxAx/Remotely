package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.RemotelyManager;
import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.servers.ReverseProxyManager;
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
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.git.GitControlScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.Main;
import restudio.rescreen.debug.DebugManager;
import restudio.rescreen.debug.IDebugInfoProvider;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class ServerDetailsScreen extends restudio.rebase.ui.screens.instance.InstanceDetailsScreen implements IDebugInfoProvider {

    private final RemotelyClient remotelyClient;
    private IconButton startIconButton;
    private Instance sidecarInstance;

    private static class ServerContextInfo {
        String localTerminalId;
        TerminalWidget terminalWidget;
        StandardOutputStateParser standardParser;
        StreamDataParser streamDataParser;
        ResourceContainer resourceContainer;
        PlayersContainer playersContainer;
        boolean isLocalTerminalMode;
    }

    private final Map<TabContext, ServerContextInfo> contextInfos = new HashMap<>();

    public ServerDetailsScreen(Object parent, RemotelyClient client) {
        super(parent instanceof Screen ? (Screen) parent : null, null);
        this.remotelyClient = client;
    }

    @Override
    protected void setupHeader() {
        header().addRight("close.png", this::closeScreen, "Close");
        header().addRight("explorer.png", this::exploreInstanceFiles, "File Explorer");
        header().addRight("edit.png", this::openInstanceSettings, "Server Settings");
        header().addRight("git.png", this::openGitControl, "Version Control");

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
            ServerContextInfo info = getCurrentInfo();
            if (info != null && !info.isLocalTerminalMode && info.resourceContainer != null) {
                info.resourceContainer.openInstanceResources();
            }
        }, "Resources");

        Runnable reverseAction = () -> {
            if (instance != null) {
                ReverseProxyManager.reverse(instance, () -> ScreenManager.getInstance().execute(() -> onViewChanged(getActiveContext(), null)));
            }
        };
        header().addLeft("reverse.png", reverseAction, "Open Server To The Public");
        header().addLeft("closeReverse.png", reverseAction, "Close Reverse Proxy");
        header().addLeft("download.png", () -> {
            ServerContextInfo info = getCurrentInfo();
            if (info != null && !info.isLocalTerminalMode && info.resourceContainer != null) {
                info.resourceContainer.showUpdateAllDialog();
            }
        }, "Update All Resources");

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

        for (Object tabInfo : remotelyClient.getMultiTerminalTabs()) {
            createAndAddTab(tabInfo, false);
        }

        int activeIndex = remotelyClient.getActiveMultiTerminalTabIndex();
        if (activeIndex >= 0 && activeIndex < tabs().getTabs().size()) {
            tabs().setActiveTab(activeIndex);
        } else if (!tabs().getTabs().isEmpty()) {
            tabs().setActiveTab(0);
        }
    }

    private void createAndAddTab(Object tabInfo, boolean setActive) {
        Instance inst = (tabInfo instanceof Instance) ? (Instance) tabInfo : null;
        String localId = (tabInfo instanceof String) ? (String) tabInfo : null;
        String name = inst != null ? inst.getName() : "Terminal";
        if (inst == null && localId == null) {
            long count = contextInfos.values().stream().filter(i -> i.isLocalTerminalMode).count() + 1;
            name = "Terminal " + count;
        }

        Container main = createContainer("root", 5, 60, width - 10, height - 65);
        main.layout(new ManagedLayout()).backgroundDrawing(false).disableScissorRegion(false).verticalSpacing(14).padding(0).setRelativeScissor(-1, -1, -1, -3);

        TabContext ctx = new TabContext(inst, tabInfo);
        ctx.mainContainer = main;

        ServerContextInfo info = new ServerContextInfo();
        info.isLocalTerminalMode = (inst == null);
        info.localTerminalId = localId;

        ExecutionProvider exec;
        if (inst != null) {
            exec = new UnifiedExecutionProvider(InstanceApi.of(inst).console());
        } else {
            exec = new LocalBackend(new BackendConfig("LOCAL", new HashMap<>()), null).getExecution();
        }

        if (inst != null) {
            info.terminalWidget = ServerTerminal.getOrCreate(inst, exec, 5, 60, width - 10, height - 66);
        } else {
            info.terminalWidget = TerminalWidget.getOrCreate(null, exec, localId, 5, 60, width - 10, height - 66);
        }

        if (inst != null) {
            info.terminalWidget.addOutputListener(inst.getMSMPManager()::handleConsoleLine);
            info.terminalWidget.start();
            inst.attachTerminalListener(info.terminalWidget);
            setupTerminalListeners(inst, info);
        }
        ctx.addView(info.terminalWidget, "terminal.png", "Terminal", null);

        if (inst != null && inst.isServer()) {
            ResourceContainer res = new ResourceContainer(this, remotelyClient, inst, 5, 60, width - 10, height - 66);
            info.resourceContainer = res;
            List<AnimatedWidget> resTools = new ArrayList<>();
            resTools.add(res.getSelectorsRow());
            ctx.addView(res, "resources.png", "Resources", resTools);

            PlayersContainer players = new PlayersContainer(this, inst, info.terminalWidget, 5, 60, width - 10, height - 66);
            info.playersContainer = players;
            ctx.addView(players, "steve.png", "Players", null);
        }

        contextInfos.put(ctx, info);
        TabsManager.Tab tab = tabs().addTab(name, main);
        registerTab(tab, ctx);

        if (setActive) {
            tabs().setActiveTab(tab.getContainer());
        }
    }

    private void setupTerminalListeners(Instance inst, ServerContextInfo info) {
        if (info.standardParser != null) {
            inst.removeLogListener(info.standardParser);
            info.standardParser = null;
        }
        if (info.streamDataParser != null) {
            inst.removeLogListener(info.streamDataParser);
            info.streamDataParser = null;
        }

        boolean standardEnabled = Boolean.parseBoolean(inst.getSettings().getProperty("provider.standard.enabled", "true"));
        String priority = inst.getSettings().getProperty("provider.priority.lifecycle", "msmp,standard");

        if (standardEnabled && priority.contains("standard")) {
            StandardOutputStateParser parser = new StandardOutputStateParser(inst);
            inst.addLogListener(parser);
            info.standardParser = parser;
        }

        if (inst.getBackend() != null) {
            Optional<DataStreamFeature> dataStreamFeature = inst.getBackend().getFeature(DataStreamFeature.class);
            if (dataStreamFeature.isPresent()) {
                StreamDataParser dataParser = new StreamDataParser(PlayerManagerController.getOrCreate(inst));
                inst.addLogListener(dataParser);
                info.streamDataParser = dataParser;

                String logPath = inst.getPath() + "/logs/latest.log";
                String opsPath = inst.getPath() + "/ops.json";
                String bannedPlayersPath = inst.getPath() + "/banned-players.json";
                String bannedIpsPath = inst.getPath() + "/banned-ips.json";
                String whitelistPath = inst.getPath() + "/whitelist.json";
                List<String> preFiles = Arrays.asList(opsPath, bannedPlayersPath, bannedIpsPath, whitelistPath);

                DebugManager.getInstance().recordEvent(inst.getInstanceId(), "DataStream", "ServerDetails", "Found DataStreamFeature, attaching...");
                dataStreamFeature.get().streamData(logPath, preFiles, inst::onLogOutput);
            } else {
                DebugManager.getInstance().recordEvent(inst.getInstanceId(), "DataStream", "ServerDetails", "DataStreamFeature not available");
            }
        }
    }

    @Override
    protected void onViewChanged(TabContext context, ViewEntry activeView) {
        ServerContextInfo info = contextInfos.get(context);
        if (info == null) return;

        boolean isInstance = !info.isLocalTerminalMode;
        header().setButtonVisible("explorer.png", isInstance);
        header().setButtonVisible("git.png", isInstance);

        if (startIconButton != null) {
            startIconButton.setVisible(isInstance);
            if (isInstance) {
                InstanceState state = context.instance.getState();
                boolean showSquare = state == InstanceState.STOPPED || state == InstanceState.CRASHED;
                if (showSquare) {
                    startIconButton.setMessage("");
                    startIconButton.setWidth(18);
                    startIconButton.setIcon("start.png");
                    startIconButton.accentType = state == InstanceState.CRASHED ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("nice");
                } else {
                    startIconButton.setMessage(state.toString().toLowerCase().substring(0, 1).toUpperCase() + state.name().toLowerCase().substring(1));
                    if (state == InstanceState.STARTING || state == InstanceState.SAVED || state == InstanceState.SAVING) {
                        startIconButton.setIcon("stop.png");
                        startIconButton.accentType = ThemeManager.getAccent("calm");
                    } else if (state == InstanceState.RUNNING) {
                        startIconButton.setIcon("stop.png");
                        startIconButton.accentType = ThemeManager.getAccent("danger");
                    }
                }
                header().requestLayoutUpdate();
            }
        }

        if (isInstance) {
            ModLoader modLoader = context.instance.getModLoader();
            boolean showResources = modLoader != null;
            header().setButtonVisible("resources.png", showResources);

            boolean isReversed = ReverseProxyManager.isPortForwarded(context.instance);
            header().setButtonVisible("reverse.png", !isReversed);
            header().setButtonVisible("closeReverse.png", isReversed);

            boolean isResView = activeView != null && activeView.widget() instanceof ResourceContainer;
            header().setButtonVisible("download.png", isResView);
            if (info.resourceContainer != null) {
                info.resourceContainer.setSelectorsVisible(isResView);
                if (isResView) info.resourceContainer.ensureSelectorsSynced();
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
        if (sidecarInstance != null && sidecarInstance.getBackend() != null) {
            sidecarInstance.getBackend().disconnect();
            sidecarInstance = null;
        }

        super.onTabSelected(tab);

        TabContext ctx = getActiveContext();
        if (ctx == null) return;
        ServerContextInfo info = contextInfos.get(ctx);

        if (info.isLocalTerminalMode) {
            DebugManager.getInstance().setViewContext(info.localTerminalId);
        } else {
            DebugManager.getInstance().setViewContext(ctx.instance.getInstanceId());
            ctx.instance.reloadSettingsFromBackend().thenRun(() -> ScreenManager.getInstance().execute(() -> {
                setupTerminalListeners(ctx.instance, info);
                PlayerManagerController.getOrCreate(ctx.instance).reloadProviders();
                if (Boolean.parseBoolean(ctx.instance.getSettings().getProperty("provider.msmp.enabled", "true"))) {
                    if (!ctx.instance.getMSMPManager().isConnected) {
                        ctx.instance.getMSMPManager().connect();
                    }
                }
                if (info.playersContainer != null) info.playersContainer.fullRefresh();
            }));

            if (VersionUtil.isMSMPCompatible(ctx.instance.getVersionId())) {
                ctx.instance.getMSMPManager().handleInstanceStateChange(ctx.instance.getState());
            }
            if (info.resourceContainer != null) info.resourceContainer.loadResources();
        }

        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
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
            ServerContextInfo info = contextInfos.remove(ctx);
            if (ctx.instance != null) {
                ctx.instance.removeStateListener(stateListener);
                remotelyClient.getMultiTerminalTabs().removeIf(o -> (o instanceof Instance i && i.getInstanceId().equals(ctx.instance.getInstanceId())));
                ctx.instance.getMSMPManager().disconnect();
            } else if (info.localTerminalId != null) {
                remotelyClient.getMultiTerminalTabs().remove(info.localTerminalId);
            }
            if (info.terminalWidget != null) {
                if (info.standardParser != null) ctx.instance.removeLogListener(info.standardParser);
                if (ctx.instance != null) TerminalWidget.shutdown(ctx.instance.getInstanceId());
                else TerminalWidget.shutdownLocal(info.localTerminalId);
            }
            if (info.resourceContainer != null) info.resourceContainer.detachSelectors();
        }
        if (tabs().getTabs().isEmpty()) {
            remotelyClient.setActiveMultiTerminalTabIndex(-1);
            closeScreen();
        } else {
            remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
        }
    }

    private void onTabRenamed(TabsManager.Tab tab) {
        TabContext context = tabContexts.get(tab);
        if (context != null && context.instance != null) {
            context.instance.setName(tab.getName());
            context.instance.save();
        }
    }

    private void onTabsReordered(List<TabsManager.Tab> newOrder) {
        List<Object> newInstanceOrder = new ArrayList<>();
        for (TabsManager.Tab tab : newOrder) {
            TabContext context = tabContexts.get(tab);
            ServerContextInfo info = contextInfos.get(context);
            if (context != null && info != null) {
                newInstanceOrder.add(info.isLocalTerminalMode ? info.localTerminalId : context.instance);
            }
        }
        remotelyClient.getMultiTerminalTabs().clear();
        remotelyClient.getMultiTerminalTabs().addAll(newInstanceOrder);
        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
    }

    private void addNewTerminalTab() {
        String newId = UUID.randomUUID().toString();
        remotelyClient.getMultiTerminalTabs().add(newId);
        createAndAddTab(newId, true);
    }

    public void addInstanceTab(Instance instanceToAdd) {
        for (Map.Entry<TabsManager.Tab, TabContext> entry : tabContexts.entrySet()) {
            if (entry.getValue().instance != null && entry.getValue().instance.getInstanceId().equals(instanceToAdd.getInstanceId())) {
                tabs().setActiveTab(entry.getValue().mainContainer);
                return;
            }
        }
        createAndAddTab(instanceToAdd, true);
    }

    private void launchOrStopInstance() {
        TabContext context = getActiveContext();
        ServerContextInfo info = contextInfos.get(context);
        if (context == null || info.isLocalTerminalMode) return;

        InstanceApi api = InstanceApi.of(context.instance);
        if (context.instance.getState() == InstanceState.RUNNING || context.instance.getState() == InstanceState.STARTING) {
            api.console().stopServer();
            String t = context.instance.getBackend() != null ? context.instance.getBackend().getFileSystem().getMetadata("type") : "";
            if ("LOCAL".equalsIgnoreCase(t)) {
                info.terminalWidget.stopProcess();
                context.instance.setState(InstanceState.STOPPED);
            }
        } else {
            BackendConfig bc = context.instance.getBackendConfig();
            boolean isRestudio = bc != null && "RESTUDIO".equalsIgnoreCase(bc.type);
            boolean isSsh = bc != null && "SSH".equalsIgnoreCase(bc.type);

            if (isSsh || isRestudio) {
                proceedWithServerStart(context, info);
                return;
            }

            final Path eulaPath = Path.of(context.instance.getPath(), "eula.txt");
            RebaseAPI legacy = RebaseApiFactory.get(context.instance);
            legacy.readFile(eulaPath).exceptionally(t -> "").thenAccept(content -> ScreenManager.getInstance().execute(() -> {
                boolean eulaAccepted = content != null && content.contains("eula=true");
                if (eulaAccepted) {
                    proceedWithServerStart(context, info);
                } else {
                    showEulaPopup(context, info);
                }
            }));
        }
    }

    private void proceedWithServerStart(TabContext context, ServerContextInfo info) {
        InstanceApi api = InstanceApi.of(context.instance);
        context.instance.setState(InstanceState.STARTING);
        api.console().startServer().thenAccept(command -> ScreenManager.getInstance().execute(() -> {
            if (command != null && !command.isEmpty()) info.terminalWidget.executeCommand(command);
            else {
                String type = context.instance.getBackend() != null ? context.instance.getBackend().getFileSystem().getMetadata("type") : "";
                if ("LOCAL".equalsIgnoreCase(type)) info.terminalWidget.startServerProcess();
            }
        }));
    }

    private void showEulaPopup(TabContext context, ServerContextInfo info) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Mojang EULA Agreement").size(327, 120).setResizable(false);
        AnimatedButton textWidget = new AnimatedButton.Builder().label("Before You Start, Please Agree To The EULA.").active(false).flat(true).build();
        builder.addRow("", true, 20, textWidget);
        builder.addMarkdown("", "By Click The Agree Button Below, You Agree To The [Minecraft EULA](https://www.minecraft.net/en-us/eula).", 20);
        PopupWidget popup = builder.build();

        builder.addRow("", true, 18, new IconButton.Builder().imagePath("checkmark").centered(true).accentType(ThemeManager.getAccent("nice"))
            .label("I have read and agree to the EULA").size(0, 18).onClick(() -> {
                final RebaseAPI api = RebaseApiFactory.get(context.instance);
                final Path eulaPath = Path.of(context.instance.getPath(), "eula.txt");
                context.instance.getServerProperties().setProperty("eula", "true");
                CompletableFuture.runAsync(context.instance::saveServerProperties).thenCompose(v -> api.writeFile(eulaPath, "eula=true")).thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    popup.hide();
                    proceedWithServerStart(context, info);
                }));
            }).build());
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
        client.setScreen((new FileExplorerScreen(this, target, Paths.get(target.getPath()), Path.of(remotelyDir.toString(), "data"), false)));
    }

    public void openInstanceSettings() {
        Instance target = ensureSidecar();
        if (target == null) return;
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

    private void openGitControl() {
        Instance target = ensureSidecar();
        if (target == null) return;
        client.setScreen(new GitControlScreen(this, target));
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            ServerContextInfo info = getCurrentInfo();
            if (info != null && info.resourceContainer != null) info.resourceContainer.loadResources();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT && hasControlDown()) {
            if (viewSwitcher != null) {
                TabContext ctx = getActiveContext();
                int i = ctx.selectedViewIndex + 1;
                if(i >= ctx.views.size()) i = 0;
                viewSwitcher.setActiveIndex(i);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        ScreenManager.getInstance().execute(() -> {
            TabContext ctx = getActiveContext();
            ServerContextInfo info = contextInfos.get(ctx);
            if (ctx != null && !ctx.views.isEmpty()) onViewChanged(ctx, ctx.views.get(ctx.selectedViewIndex));
            if (info != null && info.playersContainer != null) info.playersContainer.rebuildPlayerWidgets();
            if (ctx != null && ctx.instance != null) ctx.instance.getMSMPManager().handleInstanceStateChange(newState);
        });
    }

    private ServerContextInfo getCurrentInfo() {
        TabContext ctx = getActiveContext();
        return ctx == null ? null : contextInfos.get(ctx);
    }

    @Override
    public List<String> getLeftLines() {
        List<String> info = new ArrayList<>();
        TabContext ctx = getActiveContext();
        if (ctx == null) { info.add("No Active Context"); return info; }
        ServerContextInfo sInfo = contextInfos.get(ctx);

        if (sInfo.isLocalTerminalMode) {
            info.add("Mode: Local Terminal");
            info.add("Term ID: " + sInfo.localTerminalId);
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
            line = line.trim();

            if (isReading) {
                Matcher endMatcher = endPattern.matcher(line);
                if (endMatcher.find()) {
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
                Matcher startMatcher = startPattern.matcher(line);
                if (startMatcher.find()) {
                    currentFile = startMatcher.group(1);
                    DebugManager.getInstance().log("StreamDataParser", "Started reading file: " + currentFile);
                    isReading = true;
                    buffer.setLength(0);
                }
            }
        }
    }
}
