package redxax.oxy.remotely.ui.screens;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
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
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInstanceDetailsScreen extends InstanceDetailsScreen {

    private TerminalWidget terminalWidget;
    private net.minecraft.client.gui.screen.Screen mcParent;
    private final boolean isLocalTerminalMode;
    private List<InstanceResource> currentResources = new ArrayList<>();
    private TabSwitchWidget containerSwitch;
    private RowWidget selectorsRow;
    private DropDownWidget<String> contentSortSelector;
    private DropDownWidget<String> contentFilterSelector;
    private ContentSort currentSort = ContentSort.NAME_AZ;
    private ContentFilter currentFilter = ContentFilter.ALL;

    Container mainContainer = createContainer("terminal", 5, 60, width - 10, height - 65);
    Container resourcesContainer;

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

    public RemotelyInstanceDetailsScreen(Screen parent, Instance instance) {
        super(parent, instance);
        this.isLocalTerminalMode = (instance == null);
    }

    public RemotelyInstanceDetailsScreen(net.minecraft.client.gui.screen.Screen mcParent, Instance instance) {
        super(null, instance);
        this.mcParent = mcParent;
        this.isLocalTerminalMode = (instance == null);
    }

    @Override
    public void init() {
        super.init();
        if (!isLocalTerminalMode) {
            loadResources();
        }
        updatePositions();
    }

    @Override
    protected void setupHeader() {
        header().addRight("close.png", this::closeScreen, "Close");
        if (!isLocalTerminalMode) {
            header().addLeft("explorer.png", this::exploreInstanceFiles, "File Explorer");

            ModLoader modLoader = instance.getModLoader();
            if (modLoader != null && modLoader != ModLoader.VELOCITY && modLoader != ModLoader.WATERFALL && modLoader != ModLoader.BUNGEECORD) {
                header().addLeft("resources.png", this::openInstanceResources, "Resources");
            }
            updateHeaderButtons();
        }
        header().build();
    }

    @Override
    protected void setupTabs() {
        tabs().builder().position(5, 35).size(width - 10, 18).build();

        mainContainer.backgroundDrawing(false).disableScissorRegion(true).verticalSpacing(14).padding(0).layout(new ManagedLayout());
        mainContainer.setScissorRegion(mainContainer.getX() - 2, mainContainer.getY() - 2, mainContainer.getWidth() + mainContainer.getX() + 4,  mainContainer.getY() + mainContainer.getHeight() + 6);

        this.terminalWidget = TerminalWidget.getOrCreate(instance, 5, 60, width - 10, height - 85);
        mainContainer.addWidget(this.terminalWidget);

        tabs().addTab(instance != null ? instance.getName() : tabs().getTabs().size() > 1 ?  "Terminal " + tabs().getTabs().size() : "Terminal", mainContainer);

        if (!isLocalTerminalMode) {
            List<String> sortOptions = Arrays.stream(ContentSort.values()).map(ContentSort::toString).collect(Collectors.toList());
            contentSortSelector = new DropDownWidget.Builder<>(sortOptions).size(90, 18).onSelectionChanged(this::onSortChanged).animateElevation(false).build();
            contentSortSelector.setSelectedItem(currentSort.toString());
            contentSortSelector.setPriority(100);

            List<String> filterOptions = Arrays.stream(ContentFilter.values()).map(ContentFilter::toString).collect(Collectors.toList());
            contentFilterSelector = new DropDownWidget.Builder<>(filterOptions).size(90, 18).onSelectionChanged(this::onFilterChanged).animateElevation(false).build();
            contentFilterSelector.setSelectedItem(currentFilter.toString());
            contentFilterSelector.setPriority(100);

            selectorsRow = new RowWidget.Builder().addWidget(contentFilterSelector, contentSortSelector).padding(1).size(181, 18).build();
            selectorsRow.setVisible(false);
            selectorsRow.setPriority(100);
            addDrawableChild(selectorsRow);

            containerSwitch = new TabSwitchWidget.Builder().size(37, 18).options(List.of("terminal.png", "resources.png")).iconMode(true).onChange(i -> {
                if (i == 0) {
                    mainContainer.scrollToWidget(terminalWidget);
                    selectorsRow.setVisible(false);
                } else {
                    mainContainer.scrollToWidget(resourcesContainer);
                    selectorsRow.setVisible(true);
                }
            }).build();
            addDrawableChild(containerSwitch);
            containerSwitch.recreateButtons();


            resourcesContainer = new Container(5, 60, width - 10, height - 66);
            resourcesContainer.layout(new ManagedLayout()).columns(1).padding(2);
            mainContainer.addWidget(resourcesContainer);
        }
        tabs().setActiveTab(mainContainer);
    }

    private void onSortChanged(String selection) {
        for (ContentSort sort : ContentSort.values()) {
            if (sort.toString().equals(selection)) {
                currentSort = sort;
                break;
            }
        }
        rebuildResourcesTab();
    }

    private void onFilterChanged(String selection) {
        for (ContentFilter filter : ContentFilter.values()) {
            if (filter.toString().equals(selection)) {
                currentFilter = filter;
                break;
            }
        }
        rebuildResourcesTab();
    }

    private Comparator<InstanceResourceWidget> getWidgetComparator() {
        return (w1, w2) -> {
            InstanceResource r1 = w1.getResource();
            InstanceResource r2 = w2.getResource();
            int result = switch (currentSort) {
                case NAME_AZ -> r1.getName().compareToIgnoreCase(r2.getName());
                case NAME_ZA -> r2.getName().compareToIgnoreCase(r1.getName());
                case AUTHOR -> String.join(", ", r1.getAuthors()).compareToIgnoreCase(String.join(", ", r2.getAuthors()));
                case TYPE -> r1.getType().getDisplayName().compareTo(r2.getType().getDisplayName());
                case ENABLED -> Boolean.compare(r2.isEnabled(), r1.isEnabled());
                case UPDATE_AVAILABLE -> Boolean.compare(r2.availableUpdate != null, r1.availableUpdate != null);
            };
            if (result == 0 && currentSort != ContentSort.NAME_AZ) {
                return r1.getName().compareToIgnoreCase(r2.getName());
            }
            return result;
        };
    }

    private void rebuildResourcesTab() {
        if (resourcesContainer == null) return;
        resourcesContainer.clearWidgets();

        if (currentResources.isEmpty()) {
            resourcesContainer.addWidget(new AnimatedButton.Builder().label("No resources found.").active(false).build());
            resourcesContainer.updateWidgetPositions();
            return;
        }

        List<InstanceResource> filteredResources = currentResources.stream().filter(r -> {
            if (currentFilter == ContentFilter.ALL) return true;
            return switch (currentFilter) {
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
            resourcesContainer.addWidget(widget);
        }

        resourcesContainer.updateWidgetPositions();
    }


    private void loadResources() {
        if (resourcesContainer == null) return;
        resourcesContainer.clearWidgets();

        Rebase.get().getResourceManager().getResources(instance).thenCompose(resources -> Rebase.get().getUpdateManager().checkForUpdates(instance).thenApply(updates -> {
            for (InstanceResource resource : resources) {
                resource.availableUpdate = null;
                if (resource.getFileHash() != null && updates.containsKey(resource.getFileHash())) {
                    resource.availableUpdate = updates.get(resource.getFileHash());
                }
            }
            return resources;
        })).thenAccept(loadedResources -> client.execute(() -> {
            this.currentResources = loadedResources;
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
        if (isLocalTerminalMode) return;
        updateHeaderButtons();
    }

    private void updateHeaderButtons() {
        if (isLocalTerminalMode) return;

        header().setButtonVisible("start.png", false);
        header().setButtonVisible("stop.png", false);
        header().setButtonVisible("reverse.png", false);
        header().setButtonVisible("closeReverse.png", false);

        boolean isRunning = instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING;
        header().addLeft(isRunning ? "stop.png" : "start.png", this::launchOrStopInstance, isRunning ? "Stop Server" : "Start Server");

        boolean isProxy = instance.getModLoader() != null && List.of("velocity", "waterfall", "bungeecord").contains(instance.getModLoader().name().toLowerCase(Locale.getDefault()));
        header().setButtonVisible("resources.png", !isProxy);

        boolean isReversed = ReverseProxyManager.isPortForwarded(instance);
        header().addLeft(isReversed ? "closeReverse.png" : "reverse.png", () -> ReverseProxyManager.reverse(instance), isReversed ? "Close Reverse Proxy" : "Open Server To The Public");

        header().build();
    }

    private void launchOrStopInstance() {
        if (isLocalTerminalMode) return;

        if (instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING) {
            if (instance.isRemote()) {
                terminalWidget.executeCommand("stop");
            } else {
                TerminalWidget.shutdown(instance.getInstanceId());
                instance.setState(InstanceState.STOPPED);
                updateHeaderButtons();
                Container terminalContainer = getContainer("terminal");
                terminalContainer.clearWidgets();
                this.terminalWidget = TerminalWidget.getOrCreate(instance, 5, 60, width - 10, height - 85);
                terminalContainer.addWidget(this.terminalWidget);
            }
        } else {
            if (instance.isRemote()) {
                try {
                    RebaseAPI api = RebaseApiFactory.get(instance);
                    api.launchServer(instance, command -> {
                        if (command != null && !command.isEmpty()) {
                            terminalWidget.executeCommand(command);
                        }
                    });
                } catch (Exception e) {
                    new Notification("Failed to start server", e.getMessage(), Notification.Type.ERROR);
                }
            } else {
                terminalWidget.startServerProcess();
            }
        }
    }

    private void exploreInstanceFiles() {
        if (isLocalTerminalMode) return;
        client.setScreen((new FileExplorerScreen(this, instance, Path.of(instance.getPath()), Path.of(remotelyDir.toString(), "data"), false)));
    }

    private void openInstanceResources() {
        if (isLocalTerminalMode) return;
        client.setScreen(new ResourceBrowserScreen(this, instance));
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (terminalWidget != null && activeContainer != null) {
            terminalWidget.setSize(activeContainer.getEffectiveWidth(), activeContainer.getHeight());
        }
        updatePositions();
    }

    @Override
    public void updatePositions() {
        if (isLocalTerminalMode || mainContainer == null || containerSwitch == null || selectorsRow == null) {
            return;
        }

        int y = 36;
        int switchX = mainContainer.getX() + mainContainer.getEffectiveWidth() - containerSwitch.getWidth();
        containerSwitch.setPosition(switchX, y);

        int selectorsX = switchX - selectorsRow.getWidth() - 1;
        selectorsRow.setPosition(selectorsX, y);
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
}