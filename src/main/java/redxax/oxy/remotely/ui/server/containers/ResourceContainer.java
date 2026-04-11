package redxax.oxy.remotely.ui.server.containers;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.preset.ResourceList;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceEvent;
import restudio.rebase.resource.ResourceViewModel;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.UpdateInfo;
import restudio.rebase.resource.provider.OnlineResourceVersion;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.DownloadProgressWidget;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.SearchUtils;
import restudio.rescreen.util.WatchServiceManager;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ResourceContainer extends Container {
    private boolean hasLoaded = false;
    private boolean isLoading = false;

    public enum ContentSort {
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

    public enum ContentFilter {
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

    private ReScreen host;
    private Instance instance;
    private final ResourceViewModel viewModel;
    private Map<String, List<String>> resourceGroups = new HashMap<>();

    private ContentSort currentSort = ContentSort.NAME_AZ;
    private ContentFilter currentFilter = ContentFilter.ALL;
    private String searchQuery = "";
    private RowWidget selectorsRow;
    private DropDownWidget<String> sortSelector;
    private DropDownWidget<String> filterSelector;
    private final List<Path> watchedPaths = new ArrayList<>();

    public ResourceContainer(ReScreen host, Instance instance, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.host = host;
        this.instance = instance;
        this.viewModel = new ResourceViewModel(instance);
        layout(new ManagedLayout()).columns(1).padding(2).enableSelecting(true).setRelativeScissor(- 1, - 1, - 1, - 3);
        initializeSelectors();
        if (instance != null) {
            if (instance.getResourceGroups() != null) {
                resourceGroups = new HashMap<>(instance.getResourceGroups());
            }
            Rebase.get().getResourceStateManager().addListener(instance, this::onResourceEvent);
        }
        startFileWatchers();
    }

    private void onResourceEvent(ResourceEvent event) {
        ScreenManager.getInstance().execute(() -> {
            switch (event.type()) {
                case ADDED:
                    viewModel.addResource(event.resource());
                    addResourceWidgetIncremental(event.resource());
                    break;
                case REMOVED:
                    viewModel.removeResource(event.resource().getFileName());
                    removeResourceWidgetIncremental(event.resource());
                    break;
                case MODIFIED:
                case UPDATED:
                    viewModel.updateResource(event.resource());
                    updateWidgetInPlace(event.resource());
                    break;
                case LOAD_STARTED:
                    viewModel.setGlobalLoadState(ResourceViewModel.LoadState.LOADING, null);
                    break;
                case LOAD_COMPLETED:
                viewModel.clear();
                viewModel.setGlobalLoadState(ResourceViewModel.LoadState.LOADED, null);
                if (event.resources() != null) {
                    viewModel.addResources(event.resources());
                    refreshExistingWidgets(event.resources());
                }
                rebuildResourcesTab();
                break;
                case LOAD_FAILED:
                    viewModel.setGlobalLoadState(ResourceViewModel.LoadState.ERROR, "Failed to load");
                    break;
            }
        });
    }

    private void addResourceWidgetIncremental(InstanceResource resource) {
        if (!matchesFilter(resource) || !matchesSearch(resource)) return;

        String baseFileName = resource.getFileName().replace(".disabled", "");

        for (AnimatedWidget w : getWidgets()) {
            if (w instanceof InstanceResourceWidget irw) {
                String existingBase = irw.getResource().getFileName().replace(".disabled", "");
                if (existingBase.equals(baseFileName)) {
                    irw.setResource(resource);
                    irw.refresh();
                    return;
                }
            }
        }

        InstanceResourceWidget widget = new InstanceResourceWidget(host, instance, resource, this::loadResources);
        widget.setHeight(30);

        List<InstanceResourceWidget> sortedWidgets = getWidgets().stream()
                .filter(InstanceResourceWidget.class::isInstance)
                .map(InstanceResourceWidget.class::cast)
                .sorted(getWidgetComparator())
                .toList();

        int insertIndex = sortedWidgets.size();
        for (int i = 0; i < sortedWidgets.size(); i++) {
            if (getWidgetComparator().compare(widget, sortedWidgets.get(i)) < 0) {
                insertIndex = i;
                break;
            }
        }

        insertWidget(widget, insertIndex);
    }

    private void removeResourceWidgetIncremental(InstanceResource resource) {
        String baseFileName = resource.getFileName().replace(".disabled", "");

        for (AnimatedWidget w : getWidgets()) {
            if (w instanceof InstanceResourceWidget irw) {
                String existingBase = irw.getResource().getFileName().replace(".disabled", "");
                if (existingBase.equals(baseFileName)) {
                    boolean stillExists = viewModel.getLoadedResources().stream()
                            .anyMatch(r -> r.getFileName().replace(".disabled", "").equals(baseFileName));
                    if (stillExists) {
                        irw.setResource(resource);
                        irw.refresh();
                        return;
                    }
                    removeWidgetAnimated(irw);
                    break;
                }
            }
        }
    }

    private void updateWidgetInPlace(InstanceResource resource) {
        for (AnimatedWidget w : getWidgets()) {
            if (w instanceof InstanceResourceWidget irw) {
                if (irw.getResource().getFileName().equals(resource.getFileName())) {
                    irw.setResource(resource);
                    irw.refresh();
                    break;
                }
            }
        }
    }

    private void refreshExistingWidgets(List<InstanceResource> updatedResources) {
        for (AnimatedWidget w : getWidgets()) {
            if (w instanceof InstanceResourceWidget irw) {
                updatedResources.stream()
                    .filter(r -> r.getFileName().equals(irw.getResource().getFileName()))
                    .findFirst()
                    .ifPresent(irw::setResource);
                irw.refresh();
            }
        }
    }

    private boolean matchesFilter(InstanceResource r) {
        if (currentFilter == ContentFilter.ALL) return true;
        return switch (currentFilter) {
            case MODS -> r.getType() == ResourceType.MOD;
            case RESOURCE_PACKS -> r.getType() == ResourcePackType();
            case SHADER_PACKS -> r.getType() == ResourceType.SHADER_PACK;
            case DATA_PACKS -> r.getType() == ResourceType.DATA_PACK;
            case UPDATE_AVAILABLE -> r.availableUpdate != null;
            case DISABLED -> !r.isEnabled();
            default -> true;
        };
    }

    private boolean matchesSearch(InstanceResource r) {
        if (searchQuery == null || searchQuery.isBlank()) return true;
        return SearchUtils.isFuzzyMatch(r.getName(), searchQuery) || SearchUtils.isFuzzyMatch(r.getFileName(), searchQuery);
    }

    public void search(String query) {
        this.searchQuery = query == null ? "" : query.trim();
        rebuildResourcesTab();
    }

    private ResourceType ResourcePackType() {
        return ResourceType.RESOURCE_PACK;
    }

    private void checkForUpdates() {
        Rebase.get().getUpdateManager().checkForUpdates(instance).thenAcceptAsync(updates -> {
            for (AnimatedWidget w : getWidgets()) {
                if (w instanceof InstanceResourceWidget irw) {
                    InstanceResource r = irw.getResource();
                    if (r.getProjectId() != null && updates.containsKey(r.getProjectId())) {
                        r.availableUpdate = updates.get(r.getProjectId());
                        irw.refresh();
                    }
                }
            }
        }, ScreenManager.getInstance()::execute);
    }

    private Comparator<InstanceResource> getResourceComparator() {
        return (r1, r2) -> {
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

    private void loadResourcesFromModel() {
        rebuildResourcesTab();
    }

    public void setInstance(Instance newInstance) {
        this.instance = newInstance;
        if (newInstance != null) {
            resourceGroups = new HashMap<>();
            if (newInstance.getResourceGroups() != null) {
                resourceGroups.putAll(newInstance.getResourceGroups());
            }
        }
        startFileWatchers();
    }

    public void setHost(ReScreen host) {
        this.host = host;
    }

    private void initializeSelectors() {
        List<String> sortOptions = Arrays.stream(ContentSort.values()).map(ContentSort::toString).toList();
        sortSelector = new DropDownWidget.Builder<>(sortOptions).entranceCorner(EntranceCorner.TOP_RIGHT).size(90, 18).onSelectionChanged(this::onSortChanged).animateElevation(false).build();
        sortSelector.setPriority(100);
        List<String> filterOptions = Arrays.stream(ContentFilter.values()).map(ContentFilter::toString).toList();
        filterSelector = new DropDownWidget.Builder<>(filterOptions).entranceCorner(EntranceCorner.TOP_RIGHT).size(90, 18).onSelectionChanged(this::onFilterChanged).animateElevation(false).build();
        filterSelector.setPriority(100);
        selectorsRow = new RowWidget.Builder().addWidget(filterSelector, sortSelector).entranceCorner(EntranceCorner.TOP_RIGHT).padding(1).size(181, 18).build();
        selectorsRow.setVisible(false);
        selectorsRow.setPriority(100);
        host.addDrawableChild(selectorsRow);
    }

    public RowWidget getSelectorsRow() { return selectorsRow; }

    public void setSelectorsVisible(boolean visible) {
        if (selectorsRow != null) selectorsRow.setVisible(visible);
    }

    public void detachSelectors() {
        if (selectorsRow != null) host.remove(selectorsRow);
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

    public void ensureSelectorsSynced() {
        if (filterSelector != null) filterSelector.setSelectedItem(currentFilter.toString());
        if (sortSelector != null) sortSelector.setSelectedItem(currentSort.toString());
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

    public void rebuildResourcesTab() {
        if (instance == null) return;
        clearWidgets();
        List<InstanceResource> currentResources = viewModel.getLoadedResources();
        if (currentResources.isEmpty()) {
            addWidget(new AnimatedButton.Builder().label("No resources found.").active(false).build());
            updateWidgetPositions();
            return;
        }
        List<InstanceResource> filteredResources = currentResources.stream().filter(this::matchesFilter).filter(this::matchesSearch).toList();
        if (filteredResources.isEmpty()) {
            addWidget(new AnimatedButton.Builder().label("No resources match filter.").active(false).build());
            updateWidgetPositions();
            return;
        }
        Set<String> groupedResourceFiles = new HashSet<>();
        if (resourceGroups != null) {
            resourceGroups.values().forEach(groupedResourceFiles::addAll);
        }
        if (resourceGroups != null) {
            for (Map.Entry<String, List<String>> entry : resourceGroups.entrySet()) {
                String groupName = entry.getKey();
                List<String> resourceFiles = entry.getValue();
                List<InstanceResourceWidget> groupMemberWidgets = new ArrayList<>();
                for (String resourceFile : resourceFiles) {
                    filteredResources.stream().filter(r -> r.getFileName().equals(resourceFile)).findFirst().ifPresent(resource -> {
                        InstanceResourceWidget widget = new InstanceResourceWidget(host, instance, resource, () -> loadResources());
                        widget.setHeight(30);
                        widget.selectable = false;
                        groupMemberWidgets.add(widget);
                    });
                }
                if (groupMemberWidgets.isEmpty()) continue;
                PopupWidget.Builder groupBuilder = new PopupWidget.Builder(groupName).enableCollapseOnClose(true).setExpandWithDropdowns(true).addTitleButton(() -> {
                    resourceGroups.remove(groupName);
                    instance.setResourceGroups(resourceGroups);
                    instance.save();
                    rebuildResourcesTab();
                }, "Ungroup", ThemeManager.getAccent("calm"));
                groupMemberWidgets.sort(getWidgetComparator());
                for (InstanceResourceWidget widget : groupMemberWidgets) {
                    groupBuilder.addRow("", true, false, widget.getHeight(), widget);
                }
                PopupWidget groupPopup = groupBuilder.build();
                addWidget(groupPopup);
                groupPopup.setLayer(0);
            }
        }
        List<InstanceResourceWidget> ungroupedWidgets = new ArrayList<>();
        for (InstanceResource resource : filteredResources) {
            if (!groupedResourceFiles.contains(resource.getFileName())) {
                InstanceResourceWidget widget = new InstanceResourceWidget(host, instance, resource, () -> loadResources());
                widget.setHeight(30);
                ungroupedWidgets.add(widget);
            }
        }
        ungroupedWidgets.sort(getWidgetComparator());
        for (InstanceResourceWidget widget : ungroupedWidgets) {
            addWidget(widget);
        }
        updateWidgetPositions();
    }

    public CompletableFuture<List<InstanceResource>> loadResources() {
        return loadResources(false);
    }

    public CompletableFuture<List<InstanceResource>> loadResources(boolean force) {
        if (instance == null) return CompletableFuture.completedFuture(List.of());
        if (isLoading) return CompletableFuture.completedFuture(List.of());
        if (hasLoaded && !force) return CompletableFuture.completedFuture(List.of());

        isLoading = true;
        host.setLoading(true);

        return Rebase.get().getResourceManager().getResources(instance).whenComplete((resources, e) -> ScreenManager.getInstance().execute(() -> {
            isLoading = false;
            host.setLoading(false);
            if (e != null) {
                new Notification("Failed to load resources", e.getMessage(), Notification.Type.ERROR);
            } else {
                hasLoaded = true;
            }
        }));
    }

    public void resetLoadingState() {
        host.setLoading(false);
    }

    public void deleteResources(List<InstanceResource> resourcesToDelete) {
        if (resourcesToDelete == null || resourcesToDelete.isEmpty()) return;
        if (instance == null) return;
        List<Path> paths = resourcesToDelete.stream().map(InstanceResource::getPath).toList();
        instance.getBackend().getFileSystem().delete(paths).thenRun(() -> {
            for (InstanceResource resource : resourcesToDelete) {
                Rebase.get().getResourceManager().handleResourceRemoved(instance, resource);
                Rebase.get().getResourceStateManager().notifyRemoved(instance, resource);
            }
            Rebase.get().getResourceManager().invalidateCache(instance);
            ScreenManager.getInstance().execute(() -> {
            List<String> deletedFileNames = resourcesToDelete.stream().map(InstanceResource::getFileName).toList();
            boolean changed = false;
            if (resourceGroups != null) {
                for (List<String> groupFiles : resourceGroups.values()) {
                    if (groupFiles.removeAll(deletedFileNames)) {
                        changed = true;
                    }
                }
                if (resourceGroups.entrySet().removeIf(e -> e.getValue().isEmpty())) {
                    changed = true;
                }
            }
            if (changed) {
                instance.setResourceGroups(resourceGroups);
                instance.save();
            }
            hasLoaded = false;
            loadResources(true);
            });
        }).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> new Notification("Failed To Delete: ", e.getMessage(), Notification.Type.ERROR));
            return null;
        });
    }

    public void showUpdateAllDialog() {
        if (instance == null) return;
        List<InstanceResource> updatableResources = getCurrentResources().stream().filter(r -> r.availableUpdate != null).toList();

        if (updatableResources.isEmpty()) {
            showCheckUpdatesDialog();
        } else {
            showUpdateDialog(updatableResources);
        }
    }

    private Notification checkingNotification;

    private void showCheckUpdatesDialog() {
        if (checkingNotification != null) return;

        checkingNotification = new Notification.Builder()
                .message("Checking for Updates")
                .description("Checking all installed resources...")
                .type(Notification.Type.INFO)
                .loading(true)
                .autoSlideOut(false)
                .build();

        Rebase.get().getUpdateManager().checkForUpdates(instance, (current, total) -> {
            InstanceResource currentResource = getCurrentResources().get(Math.min(current - 1, getCurrentResources().size() - 1));
            String resourceName = currentResource != null ? currentResource.getName() : "Unknown";
            String progress = String.format("(%d/%d) %d%%", current, total, total > 0 ? (int) ((current * 100.0) / total) : 0);
            checkingNotification.update().message("Checking " + resourceName).description(progress);
        }).thenAcceptAsync(updates -> {
            getCurrentResources().forEach(r -> {
                if (r.getProjectId() != null && updates.containsKey(r.getProjectId())) {
                    r.availableUpdate = updates.get(r.getProjectId());
                }
            });
            loadResources();
            checkingNotification.update()
                    .message("Update Check Complete")
                    .description(getUpdateCountDescription(updates))
                    .loading(false)
                    .type(updates.isEmpty() ? Notification.Type.INFO : Notification.Type.SUCCESS)
                    .autoSlideOut(true);
            checkingNotification = null;
            List<InstanceResource> availableUpdates = getCurrentResources().stream().filter(r -> r.availableUpdate != null).toList();
            if (!availableUpdates.isEmpty()) {
                showUpdateDialog(availableUpdates);
            }
        }, ScreenManager.getInstance()::execute).whenComplete((v, ex) -> {
            if (ex != null) {
                checkingNotification.update()
                        .message("Check Failed")
                        .description(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage())
                        .type(Notification.Type.ERROR)
                        .loading(false)
                        .autoSlideOut(true);
                checkingNotification = null;
            }
        });
    }

    private String getUpdateCountDescription(Map<String, OnlineResourceVersion> updates) {
        int count = updates.size();
        if (count == 0) return "No updates available";
        return count + " resource" + (count == 1 ? "" : "s") + " have updates available";
    }

    private void showUpdateDialog(List<InstanceResource> updatableResources) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Update All Resources").size(400, 200).setResizable(true);
        List<InstanceResourceWidget> resourceWidgets = new ArrayList<>();
        for (InstanceResource resource : updatableResources) {
            InstanceResourceWidget widget = new InstanceResourceWidget(host, instance, resource, () -> loadResources());
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
Rebase.get().getUpdateManager().performBulkUpdate(instance, selectedUpdates, progress::updateProgress, () -> loadResources().thenRun(() -> {}), backupToggle.getValue(), 7).whenComplete((v, ex) -> ScreenManager.getInstance().execute(() -> {
                builder.getWidget().setVisible(false);
                if (ex != null) {
                    new Notification("Update Failed", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), Notification.Type.ERROR);
                }
                loadResources().thenRun(() -> {});
            }));
        }, "Download Selected", ThemeManager.getAccent("nice"));
        builder.addRow("Backup?", false, 18, backupToggle);
        builder.addRow("Progress", false, false, 20, progress);
        PopupWidget popup = builder.build();
        host.addDrawableChild(popup);
        popup.show();
    }

    public void openInstanceResources() {
        if (instance == null) return;
        ResourceType defaultType = ResourceType.MOD;
        if (instance.isServer()) {
            defaultType = switch (instance.getModLoader()) {
                case PAPER, SPIGOT, BUKKIT, PURPUR, LEAF, VELOCITY, WATERFALL, BUNGEECORD -> ResourceType.PLUGIN;
                default -> ResourceType.MOD;
            };
        }
        ScreenManager.getInstance().setScreen(new ResourceBrowserScreen(host, instance, defaultType, true));
    }

    private void showContentContextMenu(double mouseX, double mouseY) {
        List<AnimatedWidget> selected = getSelectedWidgets();
        if (selected.isEmpty()) return;
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(host).addHeaderButton("delete.png", () -> {
            List<InstanceResource> resourcesToDelete = selected.stream().filter(InstanceResourceWidget.class::isInstance).map(w -> ((InstanceResourceWidget) w).getResource()).toList();
            deleteResources(resourcesToDelete);
        }, "Delete Selected", ThemeManager.getAccent("danger"));
        if (selected.size() > 1) {
            builder.addHeaderButton("merge.png", () -> showCreateGroupPopup(selected), "Group Selected");
        }
        host.showContextMenu((int) mouseX, (int) mouseY, builder);
    }

    private void showCreateGroupPopup(List<AnimatedWidget> widgetsToGroup) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create Resource Group").size(300, 100).setResizable(false);
        if (instance == null) return;
        TextInputWidget nameField = new TextInputWidget.Builder().placeholder("Group Name").size(240, 18).build();
        SquareButtonWidget createButton = new SquareButtonWidget.Builder().imagePath("create.png").onClick(() -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                new Notification("Error", "Group name cannot be empty.", Notification.Type.ERROR);
                return;
            }
            List<String> resourceFileNames = widgetsToGroup.stream().filter(InstanceResourceWidget.class::isInstance).map(w -> ((InstanceResourceWidget) w).getResource().getFileName()).toList();
            if (resourceGroups == null) resourceGroups = new HashMap<>();
            resourceGroups.put(groupName, resourceFileNames);
            instance.setResourceGroups(resourceGroups);
            instance.save();
            rebuildResourcesTab();
            builder.getWidget().setVisible(false);
        }).accentType(ThemeManager.getAccent("nice")).build();
        SquareButtonWidget saveAsPreset = new SquareButtonWidget.Builder().imagePath("download.png").onClick(() -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                new Notification("Error", "Please enter a name for the preset.", Notification.Type.ERROR);
                return;
            }
            List<String> resourcesIDs = widgetsToGroup.stream().filter(InstanceResourceWidget.class::isInstance).map(w -> ((InstanceResourceWidget) w).getResource().getProjectId()).filter(Objects::nonNull).toList();
            ResourceList resourceList = new ResourceList(groupName, resourcesIDs);
            new Notification("Preset Saved", "Preset '" + resourceList.name + "' Was Saved.", Notification.Type.SUCCESS);
        }).hint("Save As Preset").accentType(ThemeManager.getAccent("calm")).build();
        builder.addRow("", false, false, 20, nameField, createButton, saveAsPreset);
        PopupWidget popup = builder.build();
        host.addDrawableChild(popup);
        popup.show();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (isMouseOver(mouseX, mouseY)) {
                List<AnimatedWidget> selectedWidgets = getSelectedWidgets();
                if (!selectedWidgets.isEmpty()) {
                    boolean mouseOverSelected = false;
                    for (AnimatedWidget widget : selectedWidgets) {
                        if (widget.isMouseOver(mouseX, mouseY)) {
                            mouseOverSelected = true;
                            break;
                        }
                    }
                    if (mouseOverSelected) {
                        showContentContextMenu(mouseX, mouseY);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void setSortAndFilter(ContentSort sort, ContentFilter filter) {
        if (sort != null) currentSort = sort;
        if (filter != null) currentFilter = filter;
        ensureSelectorsSynced();
        rebuildResourcesTab();
    }

    public void stopFileWatchers() {
        WatchServiceManager manager = WatchServiceManager.getInstance();
        for (Path path : watchedPaths) {
            manager.unregisterAll(path);
        }
        watchedPaths.clear();
    }

    public void startFileWatchers() {
        stopFileWatchers();
        if (instance == null) return;
        if (instance.getBackendConfig() != null && instance.getBackendConfig().type != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type)) {
            return;
        }
        Path instancePath = Path.of(instance.getPath());
        WatchServiceManager manager = WatchServiceManager.getInstance();
        for (String relativeDir : getWatchedResourceDirectories()) {
            Path path = instancePath.resolve(relativeDir);
            manager.registerIncrementalWithDebounce(path, events -> {
                for (WatchServiceManager.FileChangeEvent event : events) {
                    Path p = event.path();
                    if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        viewModel.getLoadedResources().stream().filter(r -> r.getPath().equals(p)).findFirst().ifPresent(r -> {
                            Rebase.get().getResourceManager().handleResourceRemoved(instance, r);
                            Rebase.get().getResourceStateManager().notifyRemoved(instance, r);
                        });
                    } else {
                        Rebase.get().getResourceManager().loadResource(instance, p);
                    }
                }
            }, 300);
            watchedPaths.add(path);
        }
    }

    private List<String> getWatchedResourceDirectories() {
        if (instance == null) return List.of();
        if (instance.isServer()) {
            String modsEquivalent = switch (instance.getModLoader()) {
                case PAPER, SPIGOT, BUKKIT, PURPUR, LEAF, VELOCITY, WATERFALL, BUNGEECORD -> "plugins";
                default -> "mods";
            };
            String worldName = instance.getServerProperties().getProperty("level-name", "world");
            return List.of(modsEquivalent, worldName + "/datapacks");
        }
        return List.of("mods", "resourcepacks", "shaderpacks", "datapacks");
    }

    public List<InstanceResource> getCurrentResources() { return viewModel.getLoadedResources(); }
}
