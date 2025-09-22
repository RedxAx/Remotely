package redxax.oxy.remotely.ui.screens;

import net.minecraft.client.MinecraftClient;
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
import restudio.rebase.ui.screens.instance.InstanceDetailsScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInstanceDetailsScreen extends InstanceDetailsScreen {

    private net.minecraft.client.gui.screen.Screen mcParent;
    private final RemotelyClient remotelyClient;
    private final Map<TabsManager.Tab, TabContext> tabContexts = new HashMap<>();

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
        TabSwitchWidget containerSwitch;
        RowWidget selectorsRow;
        DropDownWidget<String> contentSortSelector;
        DropDownWidget<String> contentFilterSelector;

        TabContext(Instance instance, String localTerminalId) {
            this.instance = instance;
            this.localTerminalId = localTerminalId;
            this.isLocalTerminalMode = (instance == null);
        }

        public void cleanup() {
            if (terminalWidget != null) {
                if(instance != null) {
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

    public RemotelyInstanceDetailsScreen(Screen parent, RemotelyClient client) {
        super(parent, null);
        this.remotelyClient = client;
    }

    public RemotelyInstanceDetailsScreen(net.minecraft.client.gui.screen.Screen mcParent, RemotelyClient client) {
        super(null, null);
        this.mcParent = mcParent;
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

        Container mainContainer = createContainer("main", 5, 60, width - 10, height - 65);
        mainContainer.backgroundDrawing(false).disableScissorRegion(true).verticalSpacing(14).padding(0).layout(new ManagedLayout());
        mainContainer.setScissorRegion(mainContainer.getX() - 2, mainContainer.getY() - 2, mainContainer.getWidth() + mainContainer.getX() + 4,  mainContainer.getY() + mainContainer.getHeight() + 6);
        context.mainContainer = mainContainer;

        context.terminalWidget = TerminalWidget.getOrCreate(inst, localId, 5, 60, width - 10, height - 85);
        mainContainer.addWidget(context.terminalWidget);

        if (!context.isLocalTerminalMode) {
            List<String> sortOptions = Arrays.stream(ContentSort.values()).map(ContentSort::toString).collect(Collectors.toList());
            context.contentSortSelector = new DropDownWidget.Builder<>(sortOptions).entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(90, 18).onSelectionChanged(s -> onSortChanged(context, s)).animateElevation(false).build();
            context.contentSortSelector.setSelectedItem(context.currentSort.toString());
            context.contentSortSelector.setPriority(100);

            List<String> filterOptions = Arrays.stream(ContentFilter.values()).map(ContentFilter::toString).collect(Collectors.toList());
            context.contentFilterSelector = new DropDownWidget.Builder<>(filterOptions).entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(90, 18).onSelectionChanged(s -> onFilterChanged(context, s)).animateElevation(false).build();
            context.contentFilterSelector.setSelectedItem(context.currentFilter.toString());
            context.contentFilterSelector.setPriority(100);

            context.selectorsRow = new RowWidget.Builder().addWidget(context.contentFilterSelector, context.contentSortSelector).entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).padding(1).size(181, 18).build();
            context.selectorsRow.setVisible(false);
            context.selectorsRow.setPriority(100);
            addDrawableChild(context.selectorsRow);

            context.containerSwitch = new TabSwitchWidget.Builder().entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(37, 18).options(List.of("terminal.png", "resources.png")).iconMode(true).onChange(i -> {
                if (i == 0) {
                    context.mainContainer.scrollToWidget(context.terminalWidget);
                    context.selectorsRow.setVisible(false);
                } else {
                    context.mainContainer.scrollToWidget(context.resourcesContainer);
                    context.selectorsRow.setVisible(true);
                }
            }).build();
            addDrawableChild(context.containerSwitch);
            context.containerSwitch.recreateButtons();


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
        String newId = UUID.randomUUID().toString();
        remotelyClient.getMultiTerminalTabs().add(newId);
        createAndAddTab(newId, true);
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (this.instance != null) {
            this.instance.removeStateListener(stateListener);
        }

        tabContexts.values().forEach(ctx -> {
            if(ctx.containerSwitch != null) ctx.containerSwitch.setVisible(false);
            if(ctx.selectorsRow != null) ctx.selectorsRow.setVisible(false);
        });

        TabContext newContext = tabContexts.get(tab);
        if (newContext == null) return;

        this.instance = newContext.instance;
        if (this.instance != null) {
            this.instance.addStateListener(stateListener);
        }

        if(newContext.containerSwitch != null) newContext.containerSwitch.setVisible(true);
        if(newContext.selectorsRow != null) {
            newContext.selectorsRow.setVisible(newContext.containerSwitch.getCurrentIndex() == 1);
        }

        updateHeaderButtons();
        updatePositions();

        if (!newContext.isLocalTerminalMode && newContext.currentResources.isEmpty()) {
            loadResources();
        }
        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
    }

    private void onTabClosed(TabsManager.Tab tab) {
        TabContext context = tabContexts.remove(tab);
        if (context != null) {
            if (context.instance != null) {
                context.instance.removeStateListener(stateListener);
                remotelyClient.getMultiTerminalTabs().remove(context.instance);
            } else if (context.localTerminalId != null) {
                remotelyClient.getMultiTerminalTabs().remove(context.localTerminalId);
            }
            context.cleanup();
            if(context.containerSwitch != null) remove(context.containerSwitch);
            if(context.selectorsRow != null) remove(context.selectorsRow);
        }
        if (tabs().getTabs().isEmpty()) {
            closeScreen();
        } else {
            remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
        }
    }

    private void onSortChanged(TabContext context, String selection) {
        for (ContentSort sort : ContentSort.values()) {
            if (sort.toString().equals(selection)) {
                context.currentSort = sort;
                break;
            }
        }
        rebuildResourcesTab();
    }

    private void onFilterChanged(TabContext context, String selection) {
        for (ContentFilter filter : ContentFilter.values()) {
            if (filter.toString().equals(selection)) {
                context.currentFilter = filter;
                break;
            }
        }
        rebuildResourcesTab();
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
        }));
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;
        updateHeaderButtons();
    }

    private void updateHeaderButtons() {
        header().reset();
        header().addRight("close.png", this::closeScreen, "Close");

        TabContext context = getActiveContext();
        if (context != null && !context.isLocalTerminalMode) {
            header().addLeft("explorer.png", this::exploreInstanceFiles, "File Explorer");

            ModLoader modLoader = context.instance.getModLoader();
            if (modLoader != null && modLoader != ModLoader.VELOCITY && modLoader != ModLoader.WATERFALL && modLoader != ModLoader.BUNGEECORD) {
                header().addLeft("resources.png", this::openInstanceResources, "Resources");
            }

            boolean isRunning = context.instance.getState() == InstanceState.RUNNING || context.instance.getState() == InstanceState.STARTING;
            header().addLeft(isRunning ? "stop.png" : "start.png", this::launchOrStopInstance, isRunning ? "Stop Server" : "Start Server");

            boolean isProxy = context.instance.getModLoader() != null && List.of("velocity", "waterfall", "bungeecord").contains(context.instance.getModLoader().name().toLowerCase(Locale.getDefault()));
            header().setButtonVisible("resources.png", !isProxy);

            boolean isReversed = ReverseProxyManager.isPortForwarded(context.instance);
            header().addLeft(isReversed ? "closeReverse.png" : "reverse.png", () -> ReverseProxyManager.reverse(context.instance), isReversed ? "Close Reverse Proxy" : "Open Server To The Public");
        }
        header().build();
    }

    private void launchOrStopInstance() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;

        if (context.instance.getState() == InstanceState.RUNNING || context.instance.getState() == InstanceState.STARTING) {
            if (context.instance.isRemote()) {
                context.terminalWidget.executeCommand("stop");
            } else {
                TerminalWidget.shutdown(context.instance.getInstanceId());
                context.instance.setState(InstanceState.STOPPED);
                updateHeaderButtons();
                context.mainContainer.removeWidget(context.terminalWidget);
                context.terminalWidget = TerminalWidget.getOrCreate(context.instance, 5, 60, width - 10, height - 85);
                context.mainContainer.addWidget(context.terminalWidget);
            }
        } else {
            if (context.instance.isRemote()) {
                try {
                    RebaseAPI api = RebaseApiFactory.get(context.instance);
                    api.launchServer(context.instance, command -> {
                        if (command != null && !command.isEmpty()) {
                            context.terminalWidget.executeCommand(command);
                        }
                    });
                } catch (Exception e) {
                    new Notification("Failed to start server", e.getMessage(), Notification.Type.ERROR);
                }
            } else {
                context.terminalWidget.startServerProcess();
            }
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
        client.setScreen(new ResourceBrowserScreen(this, context.instance));
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        for (TabContext context : tabContexts.values()) {
            if (context.terminalWidget != null && context.mainContainer != null) {
                context.terminalWidget.setSize(context.mainContainer.getEffectiveWidth(), context.mainContainer.getHeight());
            }
        }
        updatePositions();
    }

    @Override
    public void updatePositions() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode || context.mainContainer == null || context.containerSwitch == null || context.selectorsRow == null) {
            return;
        }

        int y = 36;
        int switchX = context.mainContainer.getX() + context.mainContainer.getEffectiveWidth() - context.containerSwitch.getWidth();
        context.containerSwitch.setPosition(switchX, y);

        int selectorsX = switchX - context.selectorsRow.getWidth() - 1;
        context.selectorsRow.setPosition(selectorsX, y);
    }

    @Override
    public void closeScreen() {
        if (parent != null) {
            client.setScreen(parent);
        } else if (mcParent != null) {
            MinecraftClient.getInstance().setScreen(mcParent);
        } else {
            super.closeScreen();
        }
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