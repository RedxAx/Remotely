package redxax.oxy.remotely.ui.screens;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import redxax.oxy.remotely.ui.widgets.msmp.PlayerManagerController;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.preset.ResourceList;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.UpdateInfo;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.DownloadProgressWidget;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.enableDebugTools;
import static redxax.oxy.remotely.config.Config.remotelyDir;
import static restudio.rescreen.config.Config.shadow;

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
        Container playersContainer;
        PlayerManagerController playerManagerController;
        List<InstanceResource> currentResources = new ArrayList<>();
        Map<String, List<String>> resourceGroups;
        ContentSort currentSort = ContentSort.NAME_AZ;
        ContentFilter currentFilter = ContentFilter.ALL;
        final boolean isLocalTerminalMode;
        int selectedViewIndex = 0;
        AnimatedButton msmpStatusBadge;

        TabContext(Instance instance, String localTerminalId) {
            this.instance = instance;
            this.localTerminalId = localTerminalId;
            this.isLocalTerminalMode = instance == null;
            this.resourceGroups = instance != null ? instance.getResourceGroups() : new HashMap<>();
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
        header().addLeft("download.png", this::showUpdateAllDialog, "Update All Resources");
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

        boolean msmpAvailable = !ctx.isLocalTerminalMode && VersionUtil.isMSMPCompatible(ctx.instance.getVersionId()) && Boolean.parseBoolean(ctx.instance.getServerProperties().getProperty("management-server-enabled", "false"));
        int maxIndex = msmpAvailable ? 2 : 1;
        if (ctx.isLocalTerminalMode) maxIndex = 0;

        if (i < 0 || i > maxIndex) i = 0;
        ctx.selectedViewIndex = i;

        List<AnimatedWidget> widgets = ctx.mainContainer.getWidgets();
        if (i < widgets.size()) {
            ctx.mainContainer.scrollToWidget(widgets.get(i));
        }
        sharedSelectorsRow.setVisible(i == 1);
        header().setButtonVisible("download.png", i == 1);
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

    private AnimatedButton createStatusBadge(String text, String accentKey) {
        return new AnimatedButton.Builder().label(text).accentType(ThemeManager.getAccent(accentKey)).enableGradient(accentKey.equals("calm")).build();
    }

    private void setPlayersStatus(TabContext ctx, String text, String accent) {
        if (ctx.msmpStatusBadge != null) {
            ctx.playersContainer.removeWidget(ctx.msmpStatusBadge);
        }
        ctx.msmpStatusBadge = createStatusBadge(text, accent);
        ctx.playersContainer.addWidget(ctx.msmpStatusBadge);
        ctx.playersContainer.updateWidgetPositions();
    }

    private void updateStatusBadge(TabContext ctx, String status) {
        String accent = "calm";
        if (status.contains("Connected")) accent = "nice";
        else if (status.contains("failed") || status.contains("timed out") || status.contains("Error") || status.contains("stopped")) accent = "danger";
        else if(status.contains("disabled")) accent = "danger";

        setPlayersStatus(ctx, status, accent);
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
            inst.attachTerminalListener(context.terminalWidget);
            context.resourcesContainer = new Container(5, 60, width - 10, height - 66);
            context.resourcesContainer.layout(new ManagedLayout()).columns(1).padding(2).enableSelecting(true);
            mainContainer.addWidget(context.resourcesContainer);

            context.playersContainer = new Container(5, 60, width - 10, height - 66);
            context.playersContainer.layout(new ManagedLayout()).columns(1).padding(2);
            context.playerManagerController = new PlayerManagerController(inst, RebaseApiFactory.get(inst), context.playersContainer, context.terminalWidget);
            mainContainer.addWidget(context.playersContainer);

            setPlayersStatus(context, "MSMP: Disconnected", "danger");
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
            if (this.instance.isServer()) {
                this.instance.getMSMPManager().setOnPlayersChange(null);
                this.instance.getMSMPManager().setOnStatusChange(null);
            }
        }

        TabContext newContext = tabContexts.get(tab);
        if (newContext == null) return;

        this.instance = newContext.instance;

        if (sharedContainerSwitch != null) {
            remove(sharedContainerSwitch);
            sharedContainerSwitch = null;
        }

        if (this.instance != null) {
            this.instance.addStateListener(stateListener);
            onStateChanged(this.instance.getState());

            boolean msmpAvailable = VersionUtil.isMSMPCompatible(this.instance.getVersionId()) &&
                    Boolean.parseBoolean(this.instance.getServerProperties().getProperty("management-server-enabled", "false"));

            if (!newContext.isLocalTerminalMode) {
                List<String> options = new ArrayList<>(List.of("terminal.png", "resources.png"));
                if (msmpAvailable) {
                    options.add("steve.png");
                }
                sharedContainerSwitch = new TabSwitchWidget.Builder()
                        .entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT)
                        .size(options.size() == 3 ? 56 : 38, 18)
                        .options(options)
                        .iconMode(true)
                        .onChange(this::onSharedSwitchChange).build();
                addDrawableChild(sharedContainerSwitch);
            }

            if (this.instance.isServer()) {
                MSMPManager manager = this.instance.getMSMPManager();
                if (msmpAvailable) {
                    manager.setOnPlayersChange(players -> ScreenManager.getInstance().execute(() -> {
                        if (newContext.playerManagerController != null) {
                            newContext.playerManagerController.updateOnlinePlayers(players);
                        }
                    }));
                    manager.setOnStatusChange(status -> ScreenManager.getInstance().execute(() -> {
                        updateStatusBadge(newContext, status);
                        IMSMPApi api = this.instance.getMSMPManager().getApi();
                        if (newContext.playerManagerController != null) {
                            newContext.playerManagerController.setMsmpApi(api);
                            newContext.playerManagerController.fullRefresh();
                        }
                    }));
                } else {
                    newContext.playersContainer.clearWidgets();
                    if (!VersionUtil.isMSMPCompatible(this.instance.getVersionId())) {
                        newContext.playersContainer.addWidget(new AnimatedButton.Builder().label("Player management requires Minecraft 25w35a or newer.").active(false).build());
                    } else {
                        newContext.playersContainer.addWidget(new AnimatedButton.Builder().label("The management API is not enabled in server.properties.").active(false).build());
                    }
                    newContext.playersContainer.updateWidgetPositions();
                }
                manager.handleInstanceStateChange(this.instance.getState());
            }

            if (newContext.currentResources.isEmpty()) {
                loadResources();
            }

            if (sharedContainerSwitch != null) {
                int maxIndex = msmpAvailable ? 2 : 1;
                if(newContext.selectedViewIndex > maxIndex) newContext.selectedViewIndex = 0;
                sharedContainerSwitch.handleTabClick(newContext.selectedViewIndex);
                onSharedSwitchChange(newContext.selectedViewIndex);
            }
        }

        updateHeaderButtons();
        updatePositions();

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
                if (context.instance.isServer()) {
                    context.instance.getMSMPManager().setOnPlayersChange(null);
                    context.instance.getMSMPManager().setOnStatusChange(null);
                }
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
        if (context == null) return (w1, w2) -> 0;
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

        Set<String> groupedResourceFiles = new HashSet<>();
        if (context.resourceGroups != null) {
            context.resourceGroups.values().forEach(groupedResourceFiles::addAll);
        }

        if (context.resourceGroups != null) {
            for (Map.Entry<String, List<String>> entry : context.resourceGroups.entrySet()) {
                String groupName = entry.getKey();
                List<String> resourceFiles = entry.getValue();

                List<InstanceResourceWidget> groupMemberWidgets = new ArrayList<>();
                for (String resourceFile : resourceFiles) {
                    filteredResources.stream().filter(r -> r.getFileName().equals(resourceFile)).findFirst().ifPresent(resource -> {
                        InstanceResourceWidget widget = new InstanceResourceWidget(this, instance, resource, this::loadResources);
                        widget.setHeight(30);
                        widget.selectable = false;
                        groupMemberWidgets.add(widget);
                    });
                }

                if (groupMemberWidgets.isEmpty()) continue;

                PopupWidget.Builder groupBuilder = new PopupWidget.Builder(groupName).enableCollapseOnClose(true).setExpandWithDropdowns(true).addTitleButton(() -> {
                    context.resourceGroups.remove(groupName);
                    context.instance.setResourceGroups(context.resourceGroups);
                    context.instance.save();
                    rebuildResourcesTab();
                }, "Ungroup", ThemeManager.getAccent("calm"));

                groupMemberWidgets.sort(getWidgetComparator());

                for (InstanceResourceWidget widget : groupMemberWidgets) {
                    groupBuilder.addRow("", true, false, widget.getHeight(), widget);
                }
                PopupWidget groupPopup = groupBuilder.build();
                context.resourcesContainer.addWidget(groupPopup);
                groupPopup.setLayer(0);
            }
        }

        List<InstanceResourceWidget> ungroupedWidgets = new ArrayList<>();
        for (InstanceResource resource : filteredResources) {
            if (!groupedResourceFiles.contains(resource.getFileName())) {
                InstanceResourceWidget widget = new InstanceResourceWidget(this, instance, resource, this::loadResources);
                widget.setHeight(30);
                ungroupedWidgets.add(widget);
            }
        }

        ungroupedWidgets.sort(getWidgetComparator());

        for (InstanceResourceWidget widget : ungroupedWidgets) {
            context.resourcesContainer.addWidget(widget);
        }

        context.resourcesContainer.updateWidgetPositions();
    }

    private void loadResources() {
        TabContext context = getActiveContext();
        if (context == null || context.resourcesContainer == null || context.instance == null) return;
        context.resourcesContainer.clearWidgets();

        if (loadingWidget == null) loadingWidget = new LoadingAnimationWidget(0, 0, 0, 0);

        List<InstanceResource> cached = Rebase.get().getResourceManager().getCachedResourcesSync(context.instance);
        if (!cached.isEmpty()) {
            context.currentResources = cached;
            rebuildResourcesTab();
        }
        loadingWidget.setSize(context.resourcesContainer.getEffectiveWidth(), 100);
        loadingWidget.setPosition(0, (context.resourcesContainer.getHeight() - 100) / 2);
        context.resourcesContainer.addWidget(loadingWidget);
        context.resourcesContainer.updateWidgetPositions();

        Rebase.get().getResourceManager().getResources(context.instance).thenCompose(resources ->
                Rebase.get().getUpdateManager().checkForUpdates(context.instance).thenApply(updates -> {
                    for (InstanceResource resource : resources) {
                        resource.availableUpdate = null;
                        if (resource.getFileHash() != null && updates.containsKey(resource.getFileHash())) {
                            resource.availableUpdate = updates.get(resource.getFileHash());
                        }
                    }
                    return resources;
                })
        ).thenAccept(loadedResources -> client.execute(() -> {
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        TabContext context = getActiveContext();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && context != null && !context.isLocalTerminalMode && context.selectedViewIndex == 1) {
            Container contentContainer = context.resourcesContainer;
            if (contentContainer.isMouseOver(mouseX, mouseY)) {
                List<AnimatedWidget> selectedWidgets = contentContainer.getSelectedWidgets();
                if (!selectedWidgets.isEmpty()) {
                    boolean mouseOverSelected = false;
                    for (AnimatedWidget widget : selectedWidgets) {
                        if (widget.isMouseOver(mouseX, mouseY)) {
                            mouseOverSelected = true;
                            break;
                        }
                    }
                    if (mouseOverSelected) {
                        showContentContextMenu(mouseX, mouseY, selectedWidgets);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        ScreenManager.getInstance().execute(this::updateHeaderButtons);
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

    private void showUpdateAllDialog() {
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;

        List<InstanceResource> updatableResources = context.currentResources.stream().filter(r -> r.availableUpdate != null).collect(Collectors.toList());

        if (updatableResources.isEmpty()) {
            new Notification("No Updates Available", "All your resources are up to date.", Notification.Type.INFO);
            return;
        }

        PopupWidget.Builder builder = new PopupWidget.Builder("Update All Resources").size(400, 200).setResizable(true);
        List<InstanceResourceWidget> resourceWidgets = new ArrayList<>();
        for (InstanceResource resource : updatableResources) {
            InstanceResourceWidget widget = new InstanceResourceWidget(this, context.instance, resource, this::loadResources);
            widget.setRenderingMode(InstanceResourceWidget.RenderingMode.COMPACT_UPDATE);
            resourceWidgets.add(widget);
            builder.addRow("", true, false, 18, widget);
        }

        ToggleWidget backupToggle = new ToggleWidget.Builder().toggled(true).build();

        DownloadProgressWidget progress = new DownloadProgressWidget.DownloadProgressBuilder().size(builder.getWidget().getWidth() - 20, 18).build();
        progress.setVisible(false);

        builder.addTitleButton(() -> {
            List<UpdateInfo> selectedUpdates = resourceWidgets.stream().filter(w -> w.includedInUpdate).map(w -> new UpdateInfo(w.getResource(), w.getResource().availableUpdate)).collect(Collectors.toList());

            if (selectedUpdates.isEmpty()) {
                new Notification("No Resources Selected", "You must select at least one resource to update.", Notification.Type.INFO);
                return;
            }
            progress.setVisible(true);
            Rebase.get().getUpdateManager().performBulkUpdate(context.instance, selectedUpdates, progress::updateProgress, this::loadResources, backupToggle.getValue(), 7).whenComplete((v, ex) -> ScreenManager.getInstance().execute(() -> {
                builder.getWidget().setVisible(false);
                if (ex != null) {
                    new Notification("Update Failed", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), Notification.Type.ERROR);
                }
                loadResources();
            }));
        }, "Download Selected", ThemeManager.getAccent("nice"));

        builder.addRow("Backup?", false, 18, backupToggle);
        builder.addRow("Progress", false, false, 20, progress);

        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private void showContentContextMenu(double mouseX, double mouseY, List<AnimatedWidget> selectedWidgets) {
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this).addHeaderButton("delete.png", () -> {
            List<InstanceResource> resourcesToDelete = selectedWidgets.stream().filter(InstanceResourceWidget.class::isInstance).map(w -> ((InstanceResourceWidget) w).getResource()).collect(Collectors.toList());
            deleteResources(resourcesToDelete);
        }, "Delete Selected", ThemeManager.getAccent("danger"));

        if (selectedWidgets.size() > 1) {
            builder.addHeaderButton("merge.png", () -> showCreateGroupPopup(selectedWidgets), "Group Selected");
        }

        showContextMenu((int) mouseX, (int) mouseY, builder);
    }

    private void showCreateGroupPopup(List<AnimatedWidget> widgetsToGroup) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create Resource Group").size(300, 100).setResizable(false);
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;

        TextInputWidget nameField = new TextInputWidget.Builder().placeholder("Group Name").size(240, 18).build();

        SquareButtonWidget createButton = new SquareButtonWidget.Builder().imagePath("create.png").onClick(() -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                new Notification("Error", "Group name cannot be empty.", Notification.Type.ERROR);
                return;
            }

            List<String> resourceFileNames = widgetsToGroup.stream().filter(InstanceResourceWidget.class::isInstance).map(w -> ((InstanceResourceWidget) w).getResource().getFileName()).collect(Collectors.toList());

            if (context.resourceGroups == null) context.resourceGroups = new HashMap<>();
            context.resourceGroups.put(groupName, resourceFileNames);
            context.instance.setResourceGroups(context.resourceGroups);
            context.instance.save();

            rebuildResourcesTab();
            builder.getWidget().setVisible(false);
        }).accentType(ThemeManager.getAccent("nice")).build();

        SquareButtonWidget saveAsPreset = new SquareButtonWidget.Builder().imagePath("download.png").onClick(() -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                new Notification("Error", "Please enter a name for the preset.", Notification.Type.ERROR);
                return;
            }
            List<String> resourcesIDs = widgetsToGroup.stream().filter(InstanceResourceWidget.class::isInstance).map(w -> ((InstanceResourceWidget) w).getResource().getProjectId()).filter(Objects::nonNull).collect(Collectors.toList());
            ResourceList resourceList = new ResourceList(groupName, resourcesIDs);
            new Notification("Preset Saved", "Preset '" + resourceList.name + "' Was Saved.", Notification.Type.SUCCESS);
        }).hint("Save As Preset").accentType(ThemeManager.getAccent("calm")).build();

        builder.addRow("", false, false, 20, nameField, createButton, saveAsPreset);
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    public void deleteResources(List<InstanceResource> resourcesToDelete) {
        if (resourcesToDelete == null || resourcesToDelete.isEmpty()) return;
        TabContext context = getActiveContext();
        if (context == null || context.isLocalTerminalMode) return;

        for (InstanceResource resource : resourcesToDelete) {
            try {
                Files.delete(resource.getPath());
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        client.execute(() -> {
            List<String> deletedFileNames = resourcesToDelete.stream().map(InstanceResource::getFileName).collect(Collectors.toList());
            context.currentResources.removeAll(resourcesToDelete);

            boolean changed = false;
            if (context.resourceGroups != null) {
                for (List<String> groupFiles : context.resourceGroups.values()) {
                    if (groupFiles.removeAll(deletedFileNames)) {
                        changed = true;
                    }
                }
                if (context.resourceGroups.entrySet().removeIf(e -> e.getValue().isEmpty())) {
                    changed = true;
                }
            }
            if (changed) {
                context.instance.setResourceGroups(context.resourceGroups);
                context.instance.save();
            }
            loadResources();
        });
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
        if (sharedContainerSwitch != null) {
            sharedContainerSwitch.recreateButtons();
        }
        updatePositions();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (sharedContainerSwitch == null || sharedSelectorsRow == null) return;
        int y = 36;
        int switchX = width - 5 - sharedContainerSwitch.getWidth();
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
                if (context.instance.isServer()) {
                    context.instance.getMSMPManager().setOnPlayersChange(null);
                    context.instance.getMSMPManager().setOnStatusChange(null);
                }
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