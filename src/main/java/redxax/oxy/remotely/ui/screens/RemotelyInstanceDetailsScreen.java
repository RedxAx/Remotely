package redxax.oxy.remotely.ui.screens;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.msmp.IMSMPApi;
import redxax.oxy.remotely.msmp.MSMPClient;
import redxax.oxy.remotely.msmp.dto.Player;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import redxax.oxy.remotely.ui.widgets.msmp.PlayerEntryWidget;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.preset.ResourceList;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.UpdateInfo;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.DownloadProgressWidget;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.platform.IDrawContext;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInstanceDetailsScreen extends restudio.rebase.ui.screens.instance.InstanceDetailsScreen {
    private final Object parent;
    private final RemotelyClient remotelyClient;
    private final Map<TabsManager.Tab, TabContext> tabContexts = new HashMap<>();
    private LoadingAnimationWidget loadingWidget;
    private static final Pattern MSMP_TOKEN_PATTERN = Pattern.compile(".*Generated one-time management server token: ([a-zA-Z0-9]+).*");
    private static final Pattern MSMP_TOKEN_PATTERN_ALT = Pattern.compile(".*one-time management.*token.*: ([a-zA-Z0-9]+).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_READY_PATTERN = Pattern.compile(".*Done \\([0-9.]+s\\)!.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_READY_PATTERN_ALT = Pattern.compile(".*For help, type \"help\".*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_STOP_PATTERN = Pattern.compile(".*(Stopping the server|Stopping server|Server stopped).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MANAGEMENT_LISTEN_PATTERN = Pattern.compile(".*json\\s*-?\\s*rpc.*(?:listening on|server on)\\s+([^:]+):(\\d+).*", Pattern.CASE_INSENSITIVE);

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
        IMSMPApi msmpApi;
        boolean msmpConnected = false;
        CompletableFuture<String> msmpTokenFuture;
        List<InstanceResource> currentResources = new ArrayList<>();
        Map<String, List<String>> resourceGroups;
        ContentSort currentSort = ContentSort.NAME_AZ;
        ContentFilter currentFilter = ContentFilter.ALL;
        final boolean isLocalTerminalMode;
        int selectedViewIndex = 0;
        AnimatedButton msmpStatusPlayers;
        TerminalWidget.OutputListener lifecycleListener;
        boolean serverMarkedRunning;
        boolean serverMarkedStopped;
        boolean msmpConnectRequested;
        String discoveredHost;
        int discoveredPort;
        boolean msmpListenDetected;

        TabContext(Instance instance, String localTerminalId) {
            this.instance = instance;
            this.localTerminalId = localTerminalId;
            this.isLocalTerminalMode = instance == null;
            if (instance != null) this.resourceGroups = instance.getResourceGroups();
            else this.resourceGroups = new HashMap<>();
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
                if (msmpApi != null) {
                    msmpApi.close();
                    msmpApi = null;
                    msmpConnected = false;
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

        if (sharedContainerSwitch == null) {
            sharedContainerSwitch = new TabSwitchWidget.Builder()
                    .entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT)
                    .size(56, 18)
                    .options(List.of("terminal.png", "resources.png", "steve.png"))
                    .iconMode(true)
                    .onChange(this::onSharedSwitchChange).build();
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
        int maxIndex = ctx.isLocalTerminalMode ? 0 : 2;
        if (i < 0 || i > maxIndex) i = 0;
        ctx.selectedViewIndex = i;
        List<AnimatedWidget> widgets = ctx.mainContainer.getWidgets();
        if (i >= 0 && i < widgets.size()) {
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
        if (ctx.msmpStatusPlayers != null) {
            ctx.playersContainer.removeWidget(ctx.msmpStatusPlayers);
        }
        ctx.msmpStatusPlayers = createStatusBadge(text, accent);
        ctx.playersContainer.addWidget(ctx.msmpStatusPlayers);
        ctx.playersContainer.updateWidgetPositions();
    }

    private void attachLifecycleListeners(TabContext context) {
        if (context.isLocalTerminalMode || context.terminalWidget == null) return;
        if (context.lifecycleListener != null) {
            context.terminalWidget.removeOutputListener(context.lifecycleListener);
        }
        context.lifecycleListener = line -> {
            Matcher listen = MANAGEMENT_LISTEN_PATTERN.matcher(line);
            if (listen.matches()) {
                context.msmpListenDetected = true;
                context.discoveredHost = listen.group(1);
                try {
                    context.discoveredPort = Integer.parseInt(listen.group(2));
                } catch (Exception ignored) {
                    context.discoveredPort = 25585;
                }
                if (context.instance != null && context.instance.isRemote() && "localhost".equalsIgnoreCase(context.discoveredHost)) {
                    if (context.instance.getRemoteHost() != null && context.instance.getRemoteHost().ip != null) {
                        context.discoveredHost = context.instance.getRemoteHost().ip;
                    }
                }
                ScreenManager.getInstance().execute(() -> {
                    setPlayersStatus(context, "MSMP: Connecting...", "calm");
                    connectToMSMP(context);
                });
            }

            boolean ready = SERVER_READY_PATTERN.matcher(line).matches() || SERVER_READY_PATTERN_ALT.matcher(line).matches();
            if (ready && !context.serverMarkedRunning) {
                context.serverMarkedRunning = true;
                ScreenManager.getInstance().execute(() -> {
                    if (context.instance != null) setInstanceStateByName(context.instance, "RUNNING", InstanceState.RUNNING);
                    setPlayersStatus(context, "MSMP: Connecting...", "calm");
                    connectToMSMP(context);
                });
            }
            boolean stopping = SERVER_STOP_PATTERN.matcher(line).matches();
            if (stopping && !context.serverMarkedStopped) {
                context.serverMarkedStopped = true;
                ScreenManager.getInstance().execute(() -> {
                    if (context.instance != null) setInstanceStateByName(context.instance, "STOPPED", InstanceState.STOPPED);
                    setPlayersStatus(context, "Server stopped", "danger");
                });
            }
        };
        context.terminalWidget.addOutputListener(context.lifecycleListener);
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
            context.msmpTokenFuture = new CompletableFuture<>();
            context.terminalWidget.addOutputListener(line -> {
                if (context.msmpTokenFuture.isDone()) return;
                Matcher m1 = MSMP_TOKEN_PATTERN.matcher(line);
                Matcher m2 = MSMP_TOKEN_PATTERN_ALT.matcher(line);
                if (m1.matches()) context.msmpTokenFuture.complete(m1.group(1));
                else if (m2.matches()) context.msmpTokenFuture.complete(m2.group(1));
            });

            context.resourcesContainer = new Container(5, 60, width - 10, height - 66);
            context.resourcesContainer.layout(new ManagedLayout()).columns(1).padding(2).enableSelecting(true);
            mainContainer.addWidget(context.resourcesContainer);

            context.playersContainer = new Container(5, 60, width - 10, height - 66);
            context.playersContainer.layout(new ManagedLayout()).columns(1).padding(2);
            mainContainer.addWidget(context.playersContainer);

            setPlayersStatus(context, "MSMP: Disconnected", "danger");

            attachLifecycleListeners(context);
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
        TabContext oldContext = getActiveContext();
        if (oldContext != null && oldContext.msmpApi != null) {
            oldContext.msmpApi.close();
            oldContext.msmpApi = null;
            oldContext.msmpConnected = false;
        }

        TabContext newContext = tabContexts.get(tab);
        if (newContext == null) return;

        this.instance = newContext.instance;
        if (this.instance != null) {
            this.instance.addStateListener(stateListener);
            InstanceState st = this.instance.getState();
            if (st == InstanceState.RUNNING) {
                setPlayersStatus(newContext, "MSMP: Connecting...", "calm");
                connectToMSMP(newContext);
            } else if (st == InstanceState.STARTING) {
                setPlayersStatus(newContext, "Server starting...", "calm");
                waitForTokenThenConnect(newContext);
            } else {
                setPlayersStatus(newContext, "Server is not running", "danger");
            }
        }
        updateHeaderButtons();
        updatePositions();

        if (!newContext.isLocalTerminalMode) {
            if (newContext.currentResources.isEmpty()) {
                loadResources();
            }
            attachLifecycleListeners(newContext);
        }

        if (sharedContainerSwitch != null) {
            boolean showSwitch = !newContext.isLocalTerminalMode;
            sharedContainerSwitch.setVisible(showSwitch);
            if (showSwitch) {
                sharedContainerSwitch.recreateButtons();
                int maxIndex = newContext.isLocalTerminalMode ? 0 : 2;
                if (newContext.selectedViewIndex < 0 || newContext.selectedViewIndex > maxIndex) {
                    newContext.selectedViewIndex = 0;
                }
                sharedContainerSwitch.handleTabClick(newContext.selectedViewIndex);
                onSharedSwitchChange(newContext.selectedViewIndex);
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
        ScreenManager.getInstance().execute(() -> {
            TabContext context = getActiveContext();
            if (context == null || context.isLocalTerminalMode) return;
            updateHeaderButtons();

            if (newState == InstanceState.RUNNING && !context.msmpConnected) {
                setPlayersStatus(context, "MSMP: Connecting...", "calm");
                connectToMSMP(context);
            } else if (newState == InstanceState.STARTING) {
                setPlayersStatus(context, "Server starting...", "calm");
                waitForTokenThenConnect(context);
            } else if (newState == InstanceState.STOPPED || newState == InstanceState.CRASHED) {
                if (context.msmpApi != null) {
                    context.msmpApi.close();
                    context.msmpApi = null;
                    context.msmpConnected = false;
                }
                setPlayersStatus(context, "Server stopped", "danger");
            }
        });
    }

    private void waitForTokenThenConnect(TabContext context) {
        if (context == null || context.isLocalTerminalMode || context.msmpConnected || context.msmpConnectRequested) return;

        boolean enabledLocally = Boolean.parseBoolean(context.instance.getServerProperties().getProperty("management-server-enabled", "false")) || Boolean.parseBoolean(context.instance.getServerProperties().getProperty("management.server.enabled", "false"));
        boolean shouldGate = !context.instance.isRemote();
        if (shouldGate && !enabledLocally) {
            setPlayersStatus(context, "MSMP disabled in server.properties", "danger");
            return;
        }

        context.msmpConnectRequested = true;

        String persistentSecret = getPersistentSecret(context);
        if (persistentSecret != null && !persistentSecret.isEmpty()) {
            setPlayersStatus(context, "MSMP: Connecting...", "calm");
            connectToMSMP(context);
            return;
        }

        if (context.msmpTokenFuture != null) {
            setPlayersStatus(context, "Waiting for management token…", "calm");
            context.msmpTokenFuture.thenRun(() -> ScreenManager.getInstance().execute(() -> connectToMSMP(context)));
        } else {
            setPlayersStatus(context, "Waiting for management token…", "calm");
        }
    }

    private String getPersistentSecret(TabContext context) {
        Properties p = context.instance.getServerProperties();
        String v1 = p.getProperty("management-server-secret");
        if (v1 != null && !v1.isEmpty()) return v1;
        String v2 = p.getProperty("management.server.secret");
        if (v2 != null && !v2.isEmpty()) return v2;
        String v3 = p.getProperty("management-server-token");
        if (v3 != null && !v3.isEmpty()) return v3;
        String v4 = p.getProperty("management.server.token");
        if (v4 != null && !v4.isEmpty()) return v4;
        return null;
    }

    private void connectToMSMP(TabContext context) {
        if (context.isLocalTerminalMode || context.msmpConnected || context.instance == null) return;

        boolean enabledLocally = Boolean.parseBoolean(context.instance.getServerProperties().getProperty("management-server-enabled", "false")) || Boolean.parseBoolean(context.instance.getServerProperties().getProperty("management.server.enabled", "false"));
        boolean shouldGate = !context.instance.isRemote();
        if (shouldGate && !enabledLocally) {
            setPlayersStatus(context, "MSMP disabled in server.properties", "danger");
            return;
        }

        boolean tls = Boolean.parseBoolean(context.instance.getServerProperties().getProperty("management-server-tls-enabled",
                context.instance.getServerProperties().getProperty("management.server.tls.enabled", "false")));
        String hostProp = context.instance.getServerProperties().getProperty("management-server-host", "localhost");
        int portProp = 0;
        try {
            portProp = Integer.parseInt(context.instance.getServerProperties().getProperty("management-server-port", "25585"));
        } catch (Exception ignored) {}

        String host = context.discoveredHost != null ? context.discoveredHost : hostProp;
        int port = context.discoveredPort > 0 ? context.discoveredPort : (portProp > 0 ? portProp : 25585);
        if (context.instance.isRemote() && "localhost".equalsIgnoreCase(host) && context.instance.getRemoteHost() != null && context.instance.getRemoteHost().ip != null) {
            host = context.instance.getRemoteHost().ip;
        }

        String schemeHost = (tls ? "wss://" : "ws://") + host;

        setPlayersStatus(context, "MSMP: Connecting...", "calm");

        context.msmpApi = new MSMPClient();

        String persistentSecret = getPersistentSecret(context);

        CompletableFuture<String> tokenFuture;
        if (persistentSecret != null && !persistentSecret.isEmpty()) {
            tokenFuture = CompletableFuture.completedFuture(persistentSecret);
        } else {
            tokenFuture = context.msmpTokenFuture != null ? context.msmpTokenFuture : CompletableFuture.failedFuture(new IllegalStateException("No token or secret available"));
        }

        tokenFuture.thenAccept(token -> context.msmpApi.connect(schemeHost, port, token).thenAccept(success -> {
            if (success) {
                ScreenManager.getInstance().execute(() -> {
                    context.msmpConnected = true;
                    setPlayersStatus(context, "MSMP: Connected", "nice");
                    new Notification("MSMP Connected", "Live server data is now active.", Notification.Type.SUCCESS);

                    context.msmpApi.subscribeToLifecycle(event -> ScreenManager.getInstance().execute(() -> handleLifecycleEvent(context, event)));
                    context.msmpApi.subscribeToPlayers(players -> ScreenManager.getInstance().execute(() -> updatePlayerList(context, players)));
                    context.msmpApi.getPlayers().thenAccept(players -> ScreenManager.getInstance().execute(() -> updatePlayerList(context, players)));
                });
            } else {
                ScreenManager.getInstance().execute(() -> {
                    setPlayersStatus(context, "MSMP: Connection failed", "danger");
                    new Notification("MSMP Connection Failed", "Unknown error", Notification.Type.ERROR);
                });
            }
        }).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                setPlayersStatus(context, "MSMP Error: " + msg, "danger");
                new Notification("MSMP Connection Failed", msg, Notification.Type.ERROR);
            });
            return null;
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                setPlayersStatus(context, "Token/secret unavailable: " + msg, "danger");
            });
            return null;
        });
    }

    private void handleLifecycleEvent(TabContext ctx, String event) {
        if (ctx == null || ctx.isLocalTerminalMode) return;
        if (ctx.instance == null) return;
        if ("started".equalsIgnoreCase(event)) {
            setInstanceStateByName(ctx.instance, "RUNNING", InstanceState.RUNNING);
            setPlayersStatus(ctx, "Server started", "nice");
        } else if ("stopping".equalsIgnoreCase(event)) {
            setInstanceStateByName(ctx.instance, "STOPPED", InstanceState.STOPPED);
            setPlayersStatus(ctx, "Server stopping", "danger");
        } else if ("saving".equalsIgnoreCase(event)) {
            setInstanceStateByName(ctx.instance, "SAVING", InstanceState.STARTING);
            setPlayersStatus(ctx, "Server saving…", "calm");
        } else if ("saved".equalsIgnoreCase(event)) {
            setInstanceStateByName(ctx.instance, "SAVED", InstanceState.RUNNING);
            setPlayersStatus(ctx, "Server saved", "nice");
        }
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
                setInstanceStateByName(context.instance, "STOPPED", InstanceState.STOPPED);
                context.terminalWidget.clearLog();
            }
        } else {
            setInstanceStateByName(context.instance, "STARTING", InstanceState.STARTING);
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
                    waitForTokenThenConnect(context);
                });
            }).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> {
                    new Notification("Failed to start server", e.getMessage(), Notification.Type.ERROR);
                    setInstanceStateByName(context.instance, "STOPPED", InstanceState.STOPPED);
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

        AtomicBoolean createBackup = new AtomicBoolean(true);
        ToggleWidget backupToggle = new ToggleWidget.Builder().toggled(true).onChange(() -> createBackup.set(!createBackup.get())).build();

        DownloadProgressWidget progress = new DownloadProgressWidget.DownloadProgressBuilder().size(builder.getWidget().getWidth() - 20, 18).build();
        progress.setVisible(false);

        builder.addTitleButton(() -> {
            List<UpdateInfo> selectedUpdates = resourceWidgets.stream().filter(w -> w.includedInUpdate).map(w -> new UpdateInfo(w.getResource(), w.getResource().availableUpdate)).collect(Collectors.toList());

            if (selectedUpdates.isEmpty()) {
                new Notification("No Resources Selected", "You must select at least one resource to update.", Notification.Type.INFO);
                return;
            }
            progress.setVisible(true);
            Rebase.get().getUpdateManager().performBulkUpdate(context.instance, selectedUpdates, progress::updateProgress, this::loadResources, createBackup.get(), 7).whenComplete((v, ex) -> ScreenManager.getInstance().execute(() -> {
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
        TabContext context = getActiveContext();
        if (context == null || context.mainContainer == null || sharedContainerSwitch == null || sharedSelectorsRow == null) return;

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
            if (context.instance != null) context.instance.removeStateListener(stateListener);
            IMSMPApi msmpApi = context.msmpApi;
            if (msmpApi != null) msmpApi.close();
            if (context.terminalWidget != null && context.lifecycleListener != null) {
                context.terminalWidget.removeOutputListener(context.lifecycleListener);
                context.lifecycleListener = null;
            }
        }
        tabContexts.clear();
        remotelyClient.setActiveMultiTerminalTabIndex(tabs().getActiveTabIndex());
    }

    private TabContext getActiveContext() {
        if (tabsManager == null || tabsManager.getActiveTab() == null) return null;
        return tabContexts.get(tabsManager.getActiveTab());
    }

    private void updatePlayerList(TabContext ctx, List<Player> players) {
        if (ctx == null || ctx.isLocalTerminalMode) return;
        ctx.playersContainer.clearWidgets();
        if (!ctx.msmpConnected) {
            setPlayersStatus(ctx, "MSMP: Disconnected", "danger");
            ctx.playersContainer.updateWidgetPositions();
            return;
        }
        if (players.isEmpty()) {
            setPlayersStatus(ctx, "No players online", "calm");
        } else {
            setPlayersStatus(ctx, players.size() + " player(s) online", "nice");
            for (Player p : players) {
                PlayerEntryWidget widget = new PlayerEntryWidget(p, player -> ctx.msmpApi.kickPlayer(player.uuid.toString(), "Kicked by operator."),
                        player -> ctx.msmpApi.banPlayer(player.uuid.toString(), "Banned by operator."), player -> {
                            CompletableFuture<Void> future = player.isOperator ? ctx.msmpApi.deopPlayer(player.uuid.toString()) : ctx.msmpApi.opPlayer(player.uuid.toString(), 4);
                            future.thenRun(() -> ctx.msmpApi.getPlayers().thenAccept(updatedPlayers -> ScreenManager.getInstance().execute(() -> updatePlayerList(ctx, updatedPlayers))));
                        }
                );
                ctx.playersContainer.addWidget(widget);
            }
        }
        ctx.playersContainer.updateWidgetPositions();
    }

    private void setInstanceStateByName(Instance instance, String stateName, InstanceState fallback) {
        try {
            InstanceState s = InstanceState.valueOf(stateName.toUpperCase());
            instance.setState(s);
        } catch (Exception e) {
            instance.setState(fallback);
        }
    }
}