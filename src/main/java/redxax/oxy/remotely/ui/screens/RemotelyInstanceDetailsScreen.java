package redxax.oxy.remotely.ui.screens;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.LoadingAnimationWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInstanceDetailsScreen extends restudio.rebase.ui.screens.instance.InstanceDetailsScreen {
    private final Object parent;
    private final RemotelyClient remotelyClient;
    private final Map<TabsManager.Tab, TabContext> tabContexts = new HashMap<>();
    private LoadingAnimationWidget loadingWidget;

    private TabSwitchWidget sharedContainerSwitch;
    private RowWidget sharedSelectorsRow;
    private DropDownWidget<String> sharedContentSortSelector;
    private DropDownWidget<String> sharedContentFilterSelector;

    private static class TabContext {
        Instance instance;
        String localTerminalId;
        TerminalWidget terminalWidget;
        Container mainContainer;
        Container resourcesContainer;
        List<InstanceResource> currentResources = new ArrayList<>();
        ContentSort currentSort = ContentSort.NAME_AZ;
        ContentFilter currentFilter = ContentFilter.ALL;
        final boolean isLocalTerminalMode;
        int selectedViewIndex = 0;
        TabContext(Instance instance, String localTerminalId) {
            this.instance = instance;
            this.localTerminalId = localTerminalId;
            this.isLocalTerminalMode = (instance == null);
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

    private enum ContentSort {
        NAME_AZ("Name (A-Z)"),
        NAME_ZA("Name (Z-A)"),
        AUTHOR("Author"),
        TYPE("Type"),
        ENABLED("Enabled"),
        UPDATE_AVAILABLE("Update Available");
        private final String displayName;
        ContentSort(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    private enum ContentFilter {
        ALL("All"),
        MODS("Mods"),
        RESOURCE_PACKS("Resource Packs"),
        SHADER_PACKS("Shader Packs"),
        DATA_PACKS("Data Packs"),
        UPDATE_AVAILABLE("Update Available"),
        DISABLED("Disabled");
        private final String displayName;
        ContentFilter(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    public RemotelyInstanceDetailsScreen(Object parent, RemotelyClient client) {
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
        header().addLeft("start.png", this::launchOrStopInstance, "Start Server");
        header().addLeft("stop.png", this::launchOrStopInstance, "Stop Server");
        header().addLeft("resources.png", this::openInstanceResources, "Resources");
        Runnable reverseAction = () -> {
            TabContext context = getActiveContext();
            if (context != null && !context.isLocalTerminalMode) {
                ReverseProxyManager.reverse(context.instance, () -> ScreenManager.getInstance().execute(this::updateHeaderButtons));
            }
        };
        header().addLeft("reverse.png", reverseAction, "Open Server To The Public");
        header().addLeft("closeReverse.png", reverseAction, "Close Reverse Proxy");
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

        if (sharedContentSortSelector == null) {
            List<String> sortOptions = Arrays.stream(ContentSort.values()).map(ContentSort::toString).collect(Collectors.toList());
            sharedContentSortSelector = new DropDownWidget.Builder<>(sortOptions).entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(90, 18).onSelectionChanged(this::onSharedSortChanged).animateElevation(false).build();
            sharedContentSortSelector.setPriority(100);
            List<String> filterOptions = Arrays.stream(ContentFilter.values()).map(ContentFilter::toString).collect(Collectors.toList());
            sharedContentFilterSelector = new DropDownWidget.Builder<>(filterOptions).entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(90, 18).onSelectionChanged(this::onSharedFilterChanged).animateElevation(false).build();
            sharedContentFilterSelector.setPriority(100);
            sharedSelectorsRow = new RowWidget.Builder().addWidget(sharedContentFilterSelector, sharedContentSortSelector).entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).padding(1).size(181, 18).build();
            sharedSelectorsRow.setVisible(false);
            sharedSelectorsRow.setPriority(100);
            addDrawableChild(sharedSelectorsRow);
        }

        if (sharedContainerSwitch == null) {
            sharedContainerSwitch = new TabSwitchWidget.Builder().entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(37, 18).options(List.of("terminal.png", "resources.png")).iconMode(true).onChange(this::onSharedSwitchChange).build();
            addDrawableChild(sharedContainerSwitch);
            sharedContainerSwitch.recreateButtons();
            sharedContainerSwitch.setVisible(false);
        }

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

    private void onSharedSwitchChange(int i) {
        TabContext ctx = getActiveContext();
        if (ctx == null) return;
        ctx.selectedViewIndex = i;
        if (i == 0) {
            ctx.mainContainer.scrollToWidget(ctx.terminalWidget);
            sharedSelectorsRow.setVisible(false);
        } else {
            ctx.mainContainer.scrollToWidget(ctx.resourcesContainer);
            sharedSelectorsRow.setVisible(true);
        }
    }

    private void onSharedSortChanged(String selection) {
        TabContext ctx = getActiveContext();
        if (ctx == null) return;
        for (ContentSort sort : ContentSort.values()) {
            if (sort.toString().equals(selection)) {
                ctx.currentSort = sort;
                break;
            }
        }
        rebuildResourcesTab();
    }

    private void onSharedFilterChanged(String selection) {
        TabContext ctx = getActiveContext();
        if (ctx == null) return;
        for (ContentFilter filter : ContentFilter.values()) {
            if (filter.toString().equals(selection)) {
                ctx.currentFilter = filter;
                break;
            }
        }
        rebuildResourcesTab();
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
        mainContainer.setScissorRegion(mainContainer.getX() - 2, mainContainer.getY() - 2, mainContainer.getWidth() + mainContainer.getX() + 4, mainContainer.getY() + mainContainer.getHeight() + 6);
        context.mainContainer = mainContainer;

        context.terminalWidget = TerminalWidget.getOrCreate(inst, localId, 5, 60, width - 10, height - 66);
        mainContainer.addWidget(context.terminalWidget);

        if (!context.isLocalTerminalMode) {
            context.resourcesContainer = new Container(5, 60, width - 10, height - 66);
            context.resourcesContainer.layout(new ManagedLayout()).columns(1).padding(2);
            mainContainer.addWidget(context.resourcesContainer);
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
        if (this.instance != null) {
            this.instance.addStateListener(stateListener);
        }
        updateHeaderButtons();
        updatePositions();

        if (!newContext.isLocalTerminalMode && newContext.currentResources.isEmpty()) {
            loadResources();
        }
        if (sharedContainerSwitch != null) {
            boolean showSwitch = !newContext.isLocalTerminalMode;
            sharedContainerSwitch.setVisible(showSwitch);
            if (showSwitch) {
                sharedContainerSwitch.handleTabClick(Math.max(0, Math.min(1, newContext.selectedViewIndex)));
            }
        }
        if (sharedSelectorsRow != null) {
            boolean showSelectors = !newContext.isLocalTerminalMode && newContext.selectedViewIndex == 1;
            sharedSelectorsRow.setVisible(showSelectors);
            if (!newContext.isLocalTerminalMode) {
                if (sharedContentFilterSelector != null) {
                    sharedContentFilterSelector.setSelectedItem(newContext.currentFilter.toString());
                }
                if (sharedContentSortSelector != null) {
                    sharedContentSortSelector.setSelectedItem(newContext.currentSort.toString());
                }
            }
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
        }
        if (tabs().getTabs().isEmpty()) {
            closeScreen();
        } else {
            remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
        }
    }

    private Comparator<InstanceResourceWidget> getWidgetComparator() {
        TabContext context = getActiveContext();
        return (w1, w2) -> {
            InstanceResource r1 = w1.getResource();
            InstanceResource r2 = w2.getResource();
            int result = switch (context.currentSort) {
                case NAME_AZ -> r1.getName().compareToIgnoreCase(r2.getName());
                case NAME_ZA -> r2.getName().compareToIgnoreCase(r1.getName());
                case AUTHOR -> String.join(", ", r1.getAuthors()).compareToIgnoreCase(String.join(", ", r2.getAuthors()));
                case TYPE -> r1.getType().getDisplayName().compareTo(r2.getType().getDisplayName());
                case ENABLED -> Boolean.compare(r2.isEnabled(), r1.isEnabled());
                case UPDATE_AVAILABLE -> Boolean.compare(r2.availableUpdate != null, r1.availableUpdate != null);
            };
            if (result == 0 && context.currentSort != ContentSort.NAME_AZ) {
                return r1.getName().compareToIgnoreCase(r2.getName());
            }
            return result;
        };
    }

    private void rebuildResourcesTab() {
        TabContext context = getActiveContext();
        if (context == null || context.resourcesContainer == null) return;
        context.resourcesContainer.clearWidgets();

        if (context.currentResources.isEmpty()) {
            context.resourcesContainer.addWidget(new AnimatedButton.Builder().label("No resources found.").active(false).build());
            context.resourcesContainer.updateWidgetPositions();
            return;
        }

        List<InstanceResource> filteredResources = context.currentResources.stream().filter(r -> {
            if (context.currentFilter == ContentFilter.ALL) return true;
            return switch (context.currentFilter) {
                case MODS -> r.getType() == ResourceType.MOD;
                case RESOURCE_PACKS -> r.getType() == ResourceType.RESOURCE_PACK;
                case SHADER_PACKS -> r.getType() == ResourceType.SHADER_PACK;
                case DATA_PACKS -> r.getType() == ResourceType.DATA_PACK;
                case UPDATE_AVAILABLE -> r.availableUpdate != null;
                case DISABLED -> !r.isEnabled();
                default -> true;
            };
        }).toList();

        if (filteredResources.isEmpty()) {
            context.resourcesContainer.addWidget(new AnimatedButton.Builder().label("No resources match filter.").active(false).build());
            context.resourcesContainer.updateWidgetPositions();
            return;
        }

        List<InstanceResourceWidget> widgets = new ArrayList<>();
        for (InstanceResource resource : filteredResources) {
            InstanceResourceWidget widget = new InstanceResourceWidget(this, instance, resource, this::loadResources);
            widget.setHeight(30);
            widgets.add(widget);
        }

        widgets.sort(getWidgetComparator());

        for (InstanceResourceWidget widget : widgets) {
            context.resourcesContainer.addWidget(widget);
        }

        context.resourcesContainer.updateWidgetPositions();
    }

    private void loadResources() {
        TabContext context = getActiveContext();
        if (context == null || context.resourcesContainer == null || context.instance == null) return;
        context.resourcesContainer.clearWidgets();

        if (loadingWidget == null) {
            loadingWidget = new LoadingAnimationWidget(0, 0, 0, 0);
        }
        List<InstanceResource> cached = Rebase.get().getResourceManager().getCachedResourcesSync(context.instance);
        if (!cached.isEmpty()) {
            context.currentResources = cached;
            rebuildResourcesTab();
        }
        loadingWidget.setSize(context.resourcesContainer.getEffectiveWidth(), 100);
        loadingWidget.setPosition(0, (context.resourcesContainer.getHeight() - 100) / 2);
        context.resourcesContainer.addWidget(loadingWidget);
        context.resourcesContainer.updateWidgetPositions();

        Rebase.get().getResourceManager().getResources(context.instance).thenCompose(resources -> Rebase.get().getUpdateManager().checkForUpdates(context.instance).thenApply(updates -> {
            for (InstanceResource resource : resources) {
                resource.availableUpdate = null;
                if (resource.getFileHash() != null && updates.containsKey(resource.getFileHash())) {
                    resource.availableUpdate = updates.get(resource.getFileHash());
                }
            }
            return resources;
        })).thenAccept(loadedResources -> client.execute(() -> {
            context.currentResources = loadedResources;
            rebuildResourcesTab();
            context.resourcesContainer.removeWidget(loadingWidget);
            context.resourcesContainer.updateWidgetPositions();
        })).exceptionally(e -> {
            client.execute(() -> {
                context.resourcesContainer.clearWidgets();
                context.resourcesContainer.addWidget(new AnimatedButton.Builder().label("Failed to load resources.").active(false).build());
                context.resourcesContainer.updateWidgetPositions();
            });
            return null;
        });
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            loadResources();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT && hasControlDown()) {
            if (getActiveContext() == null || sharedContainerSwitch == null) return false;
            int i = getActiveContext().selectedViewIndex;
            sharedContainerSwitch.handleTabClick(i == 0 ? 1 : 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        ScreenManager.getInstance().execute(() -> {
            TabContext context = getActiveContext();
            if (context == null || context.isLocalTerminalMode) return;
            updateHeaderButtons();
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

        } else {
            header().setButtonVisible("start.png", false);
            header().setButtonVisible("stop.png", false);
            header().setButtonVisible("resources.png", false);
            header().setButtonVisible("reverse.png", false);
            header().setButtonVisible("closeReverse.png", false);
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
            api.launchServer(context.instance).thenAccept(command -> {
                ScreenManager.getInstance().execute(() -> {
                    if (context.instance.isRemote()) {
                        if (command != null && !command.isEmpty()) {
                            context.terminalWidget.executeCommand(command);
                        }
                    } else {
                        context.terminalWidget.startServerProcess();
                    }
                });
            }).exceptionally(e -> {
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

    private void openInstanceResources() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;
        ResourceType defaultType = ResourceType.MOD;
        if (context.instance.isServer()) {
            defaultType = switch (context.instance.getModLoader()) {
                case PAPER, SPIGOT, BUKKIT, PURPUR, LEAF, VELOCITY, WATERFALL, BUNGEECORD -> ResourceType.PLUGIN;
                default -> ResourceType.MOD;
            };
        }
        client.setScreen(new ResourceBrowserScreen(this, context.instance, defaultType, true));
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
                if (context.terminalWidget != null) {
                    context.terminalWidget.setSize(containerWidth, height - 65);
                }
                if (context.resourcesContainer != null) {
                    context.resourcesContainer.size(containerWidth, height - 66);
                }
            }
        }
        updatePositions();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        TabContext context = getActiveContext();
        if (context == null || context.mainContainer == null || sharedContainerSwitch == null || sharedSelectorsRow == null) {
            return;
        }

        int y = 36;
        int switchX = 5 + width - 10 - sharedContainerSwitch.getWidth();
        sharedContainerSwitch.setPosition(switchX, y);
        int selectorsX = switchX - sharedSelectorsRow.getWidth() - 1;
        sharedSelectorsRow.setPosition(selectorsX, y);
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