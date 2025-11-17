package redxax.oxy.remotely.ui.server;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import redxax.oxy.remotely.ui.server.containers.ResourceContainer;
import redxax.oxy.remotely.ui.server.containers.SharedContainerSwitcher;

import java.nio.file.Path;
import java.util.*;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class ServerDetailsScreen extends restudio.rebase.ui.screens.instance.InstanceDetailsScreen {
    private final Object parent;
    private final RemotelyClient remotelyClient;
    private final Map<TabsManager.Tab, TabContext> tabContexts = new HashMap<>();

    private SharedContainerSwitcher containerSwitcher;

    private static class TabContext {
        Instance instance;
        String localTerminalId;
        TerminalWidget terminalWidget;
        Container mainContainer;
        ResourceContainer resourcesContainer;
        PlayersContainer playersContainer;
        final boolean isLocalTerminalMode;
        int selectedViewIndex = 0;

        TabContext(Instance instance, String localTerminalId) {
            this.instance = instance;
            this.localTerminalId = localTerminalId;
            this.isLocalTerminalMode = instance == null;
        }

        public void cleanup() {
            if (terminalWidget != null) {
                if (instance != null) {
                    TerminalWidget.shutdown(instance.getInstanceId());
                } else if (localTerminalId != null) {
                    TerminalWidget.shutdownLocal(localTerminalId);
                } else {
                    terminalWidget.shutdown();
                }
            }
        }
    }

    

    public ServerDetailsScreen(Object parent, RemotelyClient client) {
        super(parent instanceof restudio.rescreen.ui.core.Screen ? (restudio.rescreen.ui.core.Screen) parent : null, null);
        this.parent = parent;
        this.remotelyClient = client;
    }

    @Override
    public void init() {
        super.init();
        updatePositions();
    }

    @Override
    protected void setupHeader() {
        header().addRight("close.png", this::closeScreen, "Close");
        header().addRight("explorer.png", this::exploreInstanceFiles, "File Explorer");
        header().addRight("edit.png", this::openInstanceSettings, "Server Settings");
        header().addLeft("start.png", this::launchOrStopInstance, "Start Server");
        header().addLeft("stop.png", this::launchOrStopInstance, "Stop Server");
        header().addLeft("resources.png", () -> {
            TabContext ctx = getActiveContext();
            if (ctx != null && !ctx.isLocalTerminalMode && ctx.resourcesContainer != null) {
                ctx.resourcesContainer.openInstanceResources();
            }
        }, "Resources");
        Runnable reverseAction = () -> {
            TabContext context = getActiveContext();
            if (context != null && !context.isLocalTerminalMode) {
                ReverseProxyManager.reverse(context.instance, () -> ScreenManager.getInstance().execute(this::updateHeaderButtons));
            }
        };
        header().addLeft("reverse.png", reverseAction, "Open Server To The Public");
        header().addLeft("closeReverse.png", reverseAction, "Close Reverse Proxy");
        header().addLeft("download.png", () -> {
            TabContext ctx = getActiveContext();
            if (ctx != null && !ctx.isLocalTerminalMode && ctx.resourcesContainer != null) {
                ctx.resourcesContainer.showUpdateAllDialog();
            }
        }, "Update All Resources");
        header().build();
        updateHeaderButtons();
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

        if (!tabs().getTabs().isEmpty()) {
            onTabSelected(tabs().getActiveTab());
        }
    }

    private void onSharedSwitchChange(int i) {
        TabContext ctx = getActiveContext();
        if (ctx == null) return;
        
        int maxIndex = (ctx.isLocalTerminalMode || !ctx.instance.isServer()) ? 0 : 2;

        if (i < 0 || i > maxIndex) i = 0;
        ctx.selectedViewIndex = i;

        List<AnimatedWidget> widgets = ctx.mainContainer.getWidgets();
        if (i < widgets.size()) {
            ctx.mainContainer.scrollToWidget(widgets.get(i));
        }
        if (ctx.resourcesContainer != null) {
            ctx.resourcesContainer.setSelectorsVisible(i == 1);
        }
        header().setButtonVisible("download.png", i == 1);
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
            if (context != null) {
                newInstanceOrder.add(context.isLocalTerminalMode ? context.localTerminalId : context.instance);
            }
        }
        remotelyClient.getMultiTerminalTabs().clear();
        remotelyClient.getMultiTerminalTabs().addAll(newInstanceOrder);
        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
    }

    private void createAndAddTab(Object tabInfo, boolean setActive) {
        Instance inst = (tabInfo instanceof Instance) ? (Instance) tabInfo : null;
        String localId = (tabInfo instanceof String) ? (String) tabInfo : null;

        TabContext context = new TabContext(inst, localId);
        String containerId = inst != null ? "remotely-main-" + inst.getInstanceId() : "remotely-term-" + localId;
        Container mainContainer = createContainer(containerId, 5, 60, width - 10, height - 65);
        mainContainer.layout(new ManagedLayout()).backgroundDrawing(false).disableScissorRegion(false).verticalSpacing(14).padding(0);
        mainContainer.setRelativeScissor(- 1, - 1, - 1, - 3);
        context.mainContainer = mainContainer;

        context.terminalWidget = TerminalWidget.getOrCreate(inst, localId, 5, 60, width - 10, height - 66);
        mainContainer.addWidget(context.terminalWidget);

        if (!context.isLocalTerminalMode && inst != null && inst.isServer()) {
            context.resourcesContainer = new ResourceContainer(this, remotelyClient, inst, 5, 60, width - 10, height - 66);
            mainContainer.addWidget(context.resourcesContainer);

            context.playersContainer = new PlayersContainer(this, inst, context.terminalWidget, 5, 60, width - 10, height - 66);
            mainContainer.addWidget(context.playersContainer);
        } else {
            mainContainer.addWidget(new Container(0, 0, 0, 0));
            mainContainer.addWidget(new Container(0, 0, 0, 0));
        }

        String tabName;
        if (inst != null) {
            tabName = inst.getName();
        } else {
            long terminalCount = tabContexts.values().stream().filter(c -> c.isLocalTerminalMode).count() + 1;
            tabName = "Terminal " + terminalCount;
        }

        TabsManager.Tab tab = tabs().addTab(tabName, mainContainer);
        tabContexts.put(tab, context);

        if (setActive) {
            tabs().setActiveTab(tabs().getTabs().size() - 1);
            onTabSelected(tabs().getActiveTab());
        }
    }

    public void addInstanceTab(Instance instanceToAdd) {
        for (TabContext ctx : tabContexts.values()) {
            if (ctx.instance != null && ctx.instance.getInstanceId().equals(instanceToAdd.getInstanceId())) {
                for (Map.Entry<TabsManager.Tab, TabContext> entry : tabContexts.entrySet()) {
                    if (entry.getValue() == ctx) {
                        tabs().setActiveTab(tabs().getTabs().indexOf(entry.getKey()));
                        return;
                    }
                }
            }
        }
        createAndAddTab(instanceToAdd, true);
    }

    private void addNewTerminalTab() {
        String newId = java.util.UUID.randomUUID().toString();
        remotelyClient.getMultiTerminalTabs().add(newId);
        createAndAddTab(newId, true);
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (this.instance != null) {
            this.instance.removeStateListener(stateListener);
        }

        TabContext newContext = tabContexts.get(tab);
        if (newContext == null) return;

        this.instance = newContext.instance;

        if (containerSwitcher != null && containerSwitcher.getWidget() != null) {
            remove(containerSwitcher.getWidget());
            containerSwitcher = null;
        }

        if (this.instance != null) {
            this.instance.addStateListener(stateListener);
            onStateChanged(this.instance.getState());

            if (!newContext.isLocalTerminalMode && this.instance.isServer()) {
                containerSwitcher = new SharedContainerSwitcher(this, newContext.mainContainer);
                containerSwitcher.register("terminal.png", newContext.terminalWidget);
                containerSwitcher.register("resources.png", newContext.resourcesContainer);
                containerSwitcher.register("steve.png", newContext.playersContainer);
                containerSwitcher.setOnChange(this::onSharedSwitchChange);
                containerSwitcher.build();
                
                if (newContext.playersContainer != null) {
                    newContext.playersContainer.fullRefresh();
                }

                if (VersionUtil.isMSMPCompatible(this.instance.getVersionId()) && Boolean.parseBoolean(this.instance.getServerProperties().getProperty("management-server-enabled", "false"))) {
                     this.instance.getMSMPManager().handleInstanceStateChange(this.instance.getState());
                }
            }


            if (newContext.resourcesContainer != null) {
                newContext.resourcesContainer.ensureSelectorsSynced();
                newContext.resourcesContainer.loadResources();
            }

            if (containerSwitcher != null) {
                int maxIndex = (!newContext.isLocalTerminalMode && instance.isServer()) ? 2 : 0;
                if(newContext.selectedViewIndex > maxIndex) newContext.selectedViewIndex = 0;
                containerSwitcher.setActiveIndex(newContext.selectedViewIndex);
                onSharedSwitchChange(newContext.selectedViewIndex);
            }
        }

        updateHeaderButtons();
        updatePositions();

        if (newContext.resourcesContainer != null) {
            boolean showSelectors = !newContext.isLocalTerminalMode && newContext.selectedViewIndex == 1;
            newContext.resourcesContainer.setSelectorsVisible(showSelectors);
            newContext.resourcesContainer.ensureSelectorsSynced();
        }
        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
    }

    private void onTabClosed(TabsManager.Tab tab) {
        getGroupManager().onTabClosed(tab);
        TabContext context = tabContexts.remove(tab);
        if (context != null) {
            if (context.instance != null) {
                context.instance.removeStateListener(stateListener);
                remotelyClient.getMultiTerminalTabs().remove(context.instance);
            } else if (context.localTerminalId != null) {
                remotelyClient.getMultiTerminalTabs().remove(context.localTerminalId);
            }
            context.cleanup();
            if (context.resourcesContainer != null) {
                context.resourcesContainer.detachSelectors();
            }
        }
        if (tabs().getTabs().isEmpty()) {
            closeScreen();
        } else {
            remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
        }
    }

    

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            TabContext ctx = getActiveContext();
            if (ctx != null && ctx.resourcesContainer != null) {
                ctx.resourcesContainer.loadResources();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT && hasControlDown()) {
            if (getActiveContext() == null || containerSwitcher == null || containerSwitcher.getWidget() == null) return false;
            int i = getActiveContext().selectedViewIndex;
            containerSwitcher.setActiveIndex(i == 0 ? 1 : 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        ScreenManager.getInstance().execute(() -> {
            updateHeaderButtons();
            TabContext context = getActiveContext();
            if (context != null && context.playersContainer != null) {
                context.playersContainer.rebuildPlayerWidgets();
            }
        });
    }

    private void updateHeaderButtons() {
        TabContext context = getActiveContext();
        boolean isInstanceTab = context != null && !context.isLocalTerminalMode;

        header().setButtonVisible("explorer.png", isInstanceTab);

        if (isInstanceTab) {
            boolean isRunning = context.instance.getState() == InstanceState.RUNNING || context.instance.getState() == InstanceState.STARTING;
            header().setButtonVisible("start.png", !isRunning);
            header().setButtonVisible("stop.png", isRunning);

            ModLoader modLoader = context.instance.getModLoader();
            boolean showResources = modLoader != null;
            header().setButtonVisible("resources.png", showResources);

            boolean isReversed = ReverseProxyManager.isPortForwarded(context.instance);
            header().setButtonVisible("reverse.png", !isReversed);
            header().setButtonVisible("closeReverse.png", isReversed);
            header().setButtonVisible("download.png", context.selectedViewIndex == 1);
        } else {
            header().setButtonVisible("start.png", false);
            header().setButtonVisible("stop.png", false);
            header().setButtonVisible("resources.png", false);
            header().setButtonVisible("reverse.png", false);
            header().setButtonVisible("closeReverse.png", false);
            header().setButtonVisible("download.png", false);
        }
    }

    private void launchOrStopInstance() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;

        if (context.instance.getState() == InstanceState.RUNNING || context.instance.getState() == InstanceState.STARTING) {
            if (context.instance.isRemote()) {
                context.terminalWidget.executeCommand("stop");
            } else {
                context.terminalWidget.stopProcess();
                context.instance.setState(InstanceState.STOPPED);
                context.terminalWidget.clearLog();
            }
        } else {
            context.instance.setState(InstanceState.STARTING);
            RebaseAPI api = RebaseApiFactory.get(context.instance);
            api.launchServer(context.instance).thenAccept(command -> ScreenManager.getInstance().execute(() -> {
                if (context.instance.isRemote()) {
                    if (command != null && !command.isEmpty()) {
                        context.terminalWidget.executeCommand(command);
                    }
                } else {
                    context.terminalWidget.startServerProcess();
                }
            })).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> {
                    new Notification("Failed to start server", e.getMessage(), Notification.Type.ERROR);
                    context.instance.setState(InstanceState.STOPPED);
                });
                return null;
            });
        }
    }

    private void exploreInstanceFiles() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;
        client.setScreen((new FileExplorerScreen(this, context.instance, Path.of(context.instance.getPath()), Path.of(remotelyDir.toString(), "data"), false)));
    }

    public void openInstanceSettings() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;
        client.setScreen(new ServerConfigurationScreen(this, context.instance, context.instance.getRemoteHost(), remotelyClient));
    }

    

    



    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (tabsManager != null) {
            tabsManager.setPosition(5, 35);
            tabsManager.setSize(width - 10, 18);
        }

        for (TabContext context : tabContexts.values()) {
            if (context.mainContainer != null) {
                context.mainContainer.setPosition(5, 60);
                context.mainContainer.size(width - 10, height - 65);
                int containerWidth = context.mainContainer.getEffectiveWidth();
                for (Widget w : context.mainContainer.getWidgets()) {
                    if (w instanceof Container) {
                        w.setSize(containerWidth, height - 66);
                    }
                }
            }
        }
        if (containerSwitcher != null) {
            containerSwitcher.recreateButtons();
        }
        updatePositions();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        TabContext ctx = getActiveContext();
        if (containerSwitcher == null || containerSwitcher.getWidget() == null || ctx == null || ctx.resourcesContainer == null || ctx.resourcesContainer.getSelectorsRow() == null) return;
        int y = 36;
        int switchX = width - 5 - containerSwitcher.getWidget().getWidth();
        containerSwitcher.setPosition(switchX, y);
        int selectorsX = switchX - ctx.resourcesContainer.getSelectorsRow().getWidth() - 1;
        ctx.resourcesContainer.getSelectorsRow().setPosition(selectorsX, y);
    }

    @Override
    public void closeScreen() {
        remotelyClient.getHost().openParentScreen(this, parent);
    }

    @Override
    public void removed() {
        super.removed();
        for (TabContext context : tabContexts.values()) {
            if (context.instance != null) {
                context.instance.removeStateListener(stateListener);
            }
        }
        tabContexts.clear();
        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
    }

    private TabContext getActiveContext() {
        if (tabsManager == null || tabsManager.getActiveTab() == null) return null;
        return tabContexts.get(tabsManager.getActiveTab());
    }
}