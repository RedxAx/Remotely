package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.twin.ServerTwinManager;
import restudio.rebase.twin.ServerTwinManager.CreateRequest;
import restudio.rebase.twin.ServerTwinManager.DeploymentMode;
import restudio.rebase.twin.ServerTwinManager.DeploymentRecord;
import restudio.rebase.twin.ServerTwinManager.FileChange;
import restudio.rebase.twin.ServerTwinManager.FileChangeType;
import restudio.rebase.twin.ServerTwinManager.ScopeProfile;
import restudio.rebase.twin.ServerTwinManager.ServerTwin;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rebase.ui.widgets.editor.plugins.impl.DiffPlugin;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static restudio.rescreen.config.Config.desktopMode;

public class ServerTwinScreen extends ReScreen {
    private static final long AUTO_REFRESH_INTERVAL_MS = 1500L;

    private final Object parent;
    private final RemotelyClient remotelyClient;
    private final Instance sourceInstance;
    private final ServerTwinManager twinManager;

    private Container overviewContainer;
    private Container changesContainer;
    private Container deploymentsContainer;
    private SidePanel detailsSidePanel;
    private Container detailsContainer;

    private TabsManager.Tab changesTab;
    private TabsManager.Tab deploymentsTab;

    private final Map<String, List<FileChange>> changeCache = new HashMap<>();
    private final Map<String, String> previewCache = new HashMap<>();
    private List<ServerTwin> twins = new ArrayList<>();
    private String selectedTwinId;
    private String selectedChangePath;
    private String selectedDeploymentId;
    private long changesRequestNonce;
    private long previewRequestNonce;
    private long nextAutoRefreshAtMs;
    private Widget activeDiffContainer;
    private CodeEditorWidget activeDiffLeftEditor;
    private CodeEditorWidget activeDiffRightEditor;
    private final Map<String, ParsedDiff> parsedPreviewCache = new HashMap<>();

    public ServerTwinScreen(Object parent, RemotelyClient remotelyClient, Instance sourceInstance) {
        super();
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.sourceInstance = sourceInstance;
        this.twinManager = Rebase.get().getTwinManager();
    }

    @Override
    public String getDesktopAppId() {
        return "server-twin";
    }

    @Override
    public String getDesktopAppTitle() {
        return "Server DevMode";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "merge.png";
    }

    @Override
    public void init() {
        super.init();
        clearActiveDiffPopup();
        if (tabsManager != null) {
            tabs().clearTabs();
        }
        header().reset();
        setupHeader();
        setupLayout();
        refreshAll();
    }

    @Override
    public void close() {
        clearActiveDiffPopup();
        remotelyClient.getHost().openParentScreen(this, parent);
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        int containerY = 60;
        int containerHeight = Math.max(80, height - 65);
        if (tabsManager != null) {
            tabsManager.setPosition(5, 36);
            tabsManager.setSize(width - 10, 18);
            tabsManager.rebuildAllTabWidgets();
        }
        updateContainerBounds(overviewContainer, containerY, containerHeight);
        updateContainerBounds(changesContainer, containerY, containerHeight);
        updateContainerBounds(deploymentsContainer, containerY, containerHeight);
        if (detailsSidePanel != null) {
            detailsSidePanel.width(280).y(containerY).height(containerHeight);
        }
        updateDetailsWidth();
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        syncActiveSplitDiffEditors();
        if (now < nextAutoRefreshAtMs) {
            return;
        }
        nextAutoRefreshAtMs = now + AUTO_REFRESH_INTERVAL_MS;
        pollTwinState();
    }

    private void syncActiveSplitDiffEditors() {
        if (activeDiffLeftEditor == null || activeDiffRightEditor == null) {
            return;
        }
        if (!activeDiffLeftEditor.isVisible() || !activeDiffRightEditor.isVisible()) {
            return;
        }
        Widget focusedWidget = activeDiffLeftEditor.isFocused() ? activeDiffLeftEditor : activeDiffRightEditor.isFocused() ? activeDiffRightEditor : null;
        if (focusedWidget == activeDiffLeftEditor) {
            float leftY = activeDiffLeftEditor.getScrollOffsetY();
            float leftX = activeDiffLeftEditor.getScrollOffsetX();
            activeDiffRightEditor.setScrollOffsetY(leftY);
            activeDiffRightEditor.setScrollOffsetX(leftX);
            return;
        }
        if (focusedWidget == activeDiffRightEditor) {
            float rightY = activeDiffRightEditor.getScrollOffsetY();
            float rightX = activeDiffRightEditor.getScrollOffsetX();
            activeDiffLeftEditor.setScrollOffsetY(rightY);
            activeDiffLeftEditor.setScrollOffsetX(rightX);
            return;
        }
    }

    private void setupHeader() {
        if (!desktopMode) {
            header().addRight("close.png", this::close, "Close");
        }
        header().addRight("delete.png", this::openDeleteTwinPopup, "Disable DevMode");
        header().addLeft("create.png", this::openCreateTwinPopup, "Enable DevMode");
        header().addLeft("explorer.png", this::openSelectedTwinWorkspace, "Workspace");
        header().addLeft("terminal.png", this::openSelectedTwinTerminal, "Terminal");
        header().addLeft("reload.png", this::pullSelectedTwin, "Pull");
        header().addLeft("edit.png", this::openDeployPopup, "Commit");
        header().addLeft("upload.png", this::pushSelectedTwin, "Push");
        header().build();
        refreshHeaderButtons();
    }

    private void setupLayout() {
        int containerY = 60;
        int containerHeight = Math.max(80, height - 65);

        tabs().builder()
                .position(5, 36)
                .size(width - 10, 18)
                .allowAdd(false)
                .allowClose(false)
                .allowRename(false)
                .allowReorder(false)
                .onTabSelected(this::onTwinTabSelected)
                .build();

        overviewContainer = createListContainer("twinOverview", containerY, containerHeight);
        changesContainer = createListContainer("twinChanges", containerY, containerHeight);
        deploymentsContainer = createListContainer("twinDeployments", containerY, containerHeight);

        changesTab = tabs().addTab("Changes", changesContainer);
        deploymentsTab = tabs().addTab("Commits", deploymentsContainer);

        detailsSidePanel = createSidePanel("twinDetails").width(280).y(containerY).height(containerHeight).show();
        detailsContainer = detailsSidePanel.container();
        detailsContainer.layout(new ManagedLayout()).columns(1).padding(6).verticalSpacing(4).scrolling(true).backgroundDrawing(true);
        updateDetailsWidth();

        tabs().setActiveTab(0);
    }

    private Container createListContainer(String id, int y, int height) {
        Container container = createContainer(id, 5, y, width - 10, height);
        container.layout(new ManagedLayout())
                .columns(1)
                .padding(5)
                .verticalSpacing(4)
                .scrolling(true)
                .backgroundDrawing(true)
                .enableSelecting(true);
        return container;
    }

    private void updateContainerBounds(Container container, int y, int height) {
        if (container == null) {
            return;
        }
        container.setPosition(5, y);
        container.setSize(width - 10, height);
        resizeRows(container);
        container.updateWidgetPositions();
    }

    private void updateDetailsWidth() {
        if (detailsContainer == null || detailsSidePanel == null) {
            return;
        }
        int width = Math.max(120, detailsSidePanel.container().getEffectiveWidth() - 12);
        detailsContainer.getWidgets().forEach(widget -> {
            if (widget instanceof MountableButtonWidget row) {
                row.setSize(width, Math.max(20, row.getHeight()));
            }
        });
        detailsContainer.updateWidgetPositions();
    }

    private void onTwinTabSelected(TabsManager.Tab tab) {
        setActiveContainer(tab.getContainer());
        updateDetailsForCurrentTab();
    }

    private void refreshAll() {
        ServerTwin twin = twinManager.getTwinForSource(sourceInstance);
        twins = twin == null ? new ArrayList<>() : new ArrayList<>(List.of(twin));
        twins.sort(Comparator.comparingLong((ServerTwin entry) -> entry.createdAt).reversed());
        if (selectedTwinId != null && twins.stream().noneMatch(entry -> Objects.equals(entry.id, selectedTwinId))) {
            selectedTwinId = null;
            selectedChangePath = null;
            selectedDeploymentId = null;
        }
        if (selectedTwinId == null && !twins.isEmpty()) {
            selectedTwinId = twins.getFirst().id;
        }
        ServerTwin selectedTwin = getSelectedTwin();
        if (selectedTwin != null && selectedDeploymentId != null && selectedTwin.deployments.stream().noneMatch(record -> Objects.equals(record.id, selectedDeploymentId))) {
            selectedDeploymentId = null;
        }
        refreshHeaderButtons();
        refreshOverviewRows();
        refreshChangesRows();
        refreshDeploymentRows();
        updateDetailsForCurrentTab();
    }

    private void refreshHeaderButtons() {
        boolean hasTwin = getSelectedTwin() != null;
        header().setButtonVisible("explorer.png", hasTwin);
        header().setButtonVisible("terminal.png", hasTwin);
        header().setButtonVisible("reload.png", hasTwin);
        header().setButtonVisible("edit.png", hasTwin);
        header().setButtonVisible("upload.png", hasTwin);
        header().setButtonVisible("delete.png", hasTwin);
    }

    private void refreshOverviewRows() {
        overviewContainer.clearWidgets();
        ServerTwin selectedTwin = getSelectedTwin();
        if (selectedTwin == null) {
            overviewContainer.addWidget(createInfoRow(overviewContainer, "DevMode Off", "Enable DevMode To Start"));
            overviewContainer.addWidget(createInfoRow(overviewContainer, "Source", sourceInstance.getName()));
            overviewContainer.updateWidgetPositions();
            return;
        }
        overviewContainer.addWidget(createInfoRow(overviewContainer, "DevMode", selectedTwin.name));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "Source", valueOrDash(selectedTwin.sourceName)));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "Backend", valueOrDash(selectedTwin.sourceBackendType)));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "Scope", selectedTwin.scope == null ? "empty" : selectedTwin.scope.summary()));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "Git", selectedTwin.gitEnabled ? "Enabled" : "Disabled"));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "Status", selectedTwin.statusSummary()));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "Workspace", valueOrDash(selectedTwin.workspacePath)));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "LastPull", formatTime(selectedTwin.lastPulledAt)));
        overviewContainer.addWidget(createInfoRow(overviewContainer, "LastCommit", formatTime(selectedTwin.lastDeployedAt)));
        overviewContainer.updateWidgetPositions();
    }

    private void refreshChangesRows() {
        changesContainer.clearWidgets();
        ServerTwin selectedTwin = getSelectedTwin();
        if (selectedTwin == null) {
            changesContainer.addWidget(createInfoRow(changesContainer, "DevMode Off", "Enable DevMode First"));
            changesContainer.updateWidgetPositions();
            updateDetailsForCurrentTab();
            return;
        }
        changesContainer.addWidget(createInfoRow(changesContainer, "Loading Changes", selectedTwin.name));
        changesContainer.updateWidgetPositions();
        long nonce = ++changesRequestNonce;
        twinManager.listChanges(selectedTwin.id).whenComplete((changes, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (nonce != changesRequestNonce || !Objects.equals(selectedTwinId, selectedTwin.id)) {
                return;
            }
            if (throwable != null) {
                changesContainer.clearWidgets();
                changesContainer.addWidget(createInfoRow(changesContainer, "Load Failed", resolveThrowable(throwable)));
                changesContainer.updateWidgetPositions();
                updateDetailsForCurrentTab();
                return;
            }
            changeCache.put(selectedTwin.id, changes);
            renderChangeRows(selectedTwin, changes);
        }));
    }

    private void renderChangeRows(ServerTwin twin, List<FileChange> changes) {
        changesContainer.clearWidgets();
        if (changes.isEmpty()) {
            changesContainer.addWidget(createInfoRow(changesContainer, "No Changes", twin.name));
            changesContainer.updateWidgetPositions();
            if (Objects.equals(selectedTwinId, twin.id)) {
                selectedChangePath = null;
                updateDetailsForCurrentTab();
            }
            return;
        }
        boolean selectedExists = false;
        for (FileChange change : changes) {
            boolean selected = Objects.equals(change.relativePath, selectedChangePath);
            if (selected) {
                selectedExists = true;
            }
            String hidden = change.summary();
            String description = change.directory
                    ? formatSize(change.baselineSize) + " • " + formatSize(change.workspaceSize) + " • directory"
                    : formatSize(change.baselineSize) + " → " + formatSize(change.workspaceSize);
            MountableButtonWidget row = new MountableButtonWidget.Builder(change.relativePath)
                    .hiddenText(hidden)
                    .description(description)
                    .onClick(() -> selectChange(change.relativePath))
                    .addButton(actionButton("edit.png", "Preview", () -> openChangePreview(twin, change)))
                    .addButton(actionButton("reload.png", "Restore Path", () -> restoreChange(twin, change), ThemeManager.getAccent("calm")))
                    .build();
            styleRow(row, changesContainer, accentForChangeType(change.type), 34);
            changesContainer.addWidget(row);
        }
        changesContainer.updateWidgetPositions();
        if (!selectedExists) {
            selectedChangePath = changes.getFirst().relativePath;
        }
        updateDetailsForCurrentTab();
    }

    private void refreshDeploymentRows() {
        deploymentsContainer.clearWidgets();
        ServerTwin selectedTwin = getSelectedTwin();
        if (selectedTwin == null) {
            deploymentsContainer.addWidget(createInfoRow(deploymentsContainer, "DevMode Off", "Enable DevMode First"));
            deploymentsContainer.updateWidgetPositions();
            updateDetailsForCurrentTab();
            return;
        }
        if (selectedTwin.deployments.isEmpty()) {
            deploymentsContainer.addWidget(createInfoRow(deploymentsContainer, "No Commits", selectedTwin.name));
            deploymentsContainer.updateWidgetPositions();
            selectedDeploymentId = null;
            updateDetailsForCurrentTab();
            return;
        }
        for (DeploymentRecord deployment : selectedTwin.deployments) {
            boolean selected = Objects.equals(deployment.id, selectedDeploymentId);
            MountableButtonWidget row = new MountableButtonWidget.Builder(deployment.label)
                    .hiddenText(selected ? "active • " + formatDeploymentMode(deployment.mode) : formatDeploymentMode(deployment.mode))
                    .description(formatTime(deployment.createdAt) + " • " + deployment.changes.size() + " changes" + (deployment.gitCommitShortHash == null || deployment.gitCommitShortHash.isBlank() ? "" : " • " + deployment.gitCommitShortHash))
                    .onClick(() -> selectDeployment(deployment.id))
                    .addButton(actionButton("goback.png", "Rollback", () -> rollbackDeployment(selectedTwin, deployment), ThemeManager.getAccent("calm")))
                    .build();
            styleRow(row, deploymentsContainer, selected ? ThemeManager.getAccent("calm") : ThemeManager.getDefaultAccent(), 34);
            deploymentsContainer.addWidget(row);
        }
        deploymentsContainer.updateWidgetPositions();
        if (selectedDeploymentId == null) {
            selectedDeploymentId = selectedTwin.deployments.getFirst().id;
        }
        updateDetailsForCurrentTab();
    }

    private void pollTwinState() {
        ServerTwin nextTwin = twinManager.getTwinForSource(sourceInstance);
        List<ServerTwin> nextTwins = nextTwin == null ? new ArrayList<>() : new ArrayList<>(List.of(nextTwin));
        nextTwins.sort(Comparator.comparingLong((ServerTwin twin) -> twin.createdAt).reversed());
        boolean twinsChanged = !sameTwinSnapshot(twins, nextTwins);
        twins = nextTwins;
        if (selectedTwinId != null && twins.stream().noneMatch(twin -> Objects.equals(twin.id, selectedTwinId))) {
            selectedTwinId = null;
            selectedChangePath = null;
            selectedDeploymentId = null;
            twinsChanged = true;
        }
        if (selectedTwinId == null && !twins.isEmpty()) {
            selectedTwinId = twins.getFirst().id;
            twinsChanged = true;
        }
        ServerTwin selectedTwin = getSelectedTwin();
        if (selectedTwin != null && selectedDeploymentId != null && selectedTwin.deployments.stream().noneMatch(record -> Objects.equals(record.id, selectedDeploymentId))) {
            selectedDeploymentId = null;
            twinsChanged = true;
        }
        if (twinsChanged) {
            refreshHeaderButtons();
            refreshOverviewRows();
            refreshDeploymentRows();
        }
        if (selectedTwin == null) {
            if (twinsChanged) {
                refreshChangesRows();
                updateDetailsForCurrentTab();
            }
            return;
        }
        boolean twinsChangedSnapshot = twinsChanged;
        long nonce = ++changesRequestNonce;
        twinManager.listChanges(selectedTwin.id).whenComplete((changes, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (nonce != changesRequestNonce || !Objects.equals(selectedTwinId, selectedTwin.id)) {
                return;
            }
            if (throwable != null) {
                return;
            }
            List<FileChange> cachedChanges = changeCache.get(selectedTwin.id);
            if (sameChangeSnapshot(cachedChanges, changes)) {
                if (twinsChangedSnapshot) {
                    updateDetailsForCurrentTab();
                }
                return;
            }
            changeCache.put(selectedTwin.id, changes);
            previewCache.keySet().removeIf(key -> key.startsWith(selectedTwin.id + "::"));
            renderChangeRows(selectedTwin, changes);
        }));
    }

    private void selectChange(String relativePath) {
        selectedChangePath = relativePath;
        updateDetailsForCurrentTab();
    }

    private void selectDeployment(String deploymentId) {
        selectedDeploymentId = deploymentId;
        updateDetailsForCurrentTab();
    }

    private void updateDetailsForCurrentTab() {
        if (detailsContainer == null) {
            return;
        }
        detailsContainer.clearWidgets();
        ServerTwin selectedTwin = getSelectedTwin();
        if (selectedTwin == null) {
            addDetailsRow("Source", sourceInstance.getName(), ThemeManager.getDefaultAccent());
            addDetailsRow("State", "DevMode Off", ThemeManager.getAccent("danger"));
            addDetailsRow("Action", "Enable DevMode", ThemeManager.getAccent("nice"));
            detailsContainer.updateWidgetPositions();
            updateDetailsWidth();
            return;
        }
        TabsManager.Tab activeTab = tabsManager == null ? null : tabsManager.getActiveTab();
        if (activeTab == changesTab) {
            updateChangeDetails(selectedTwin);
            detailsContainer.updateWidgetPositions();
            updateDetailsWidth();
            return;
        }
        if (activeTab == deploymentsTab) {
            buildDeploymentDetailsRows(selectedTwin);
            detailsContainer.updateWidgetPositions();
            updateDetailsWidth();
            return;
        }
        buildTwinDetailsRows(selectedTwin);
        detailsContainer.updateWidgetPositions();
        updateDetailsWidth();
    }

    private void updateChangeDetails(ServerTwin selectedTwin) {
        List<FileChange> changes = changeCache.get(selectedTwin.id);
        if (changes == null) {
            addDetailsRow("Changes", "Loading", ThemeManager.getAccent("calm"));
            return;
        }
        long added = changes.stream().filter(change -> change.type == FileChangeType.ADDED).count();
        long modified = changes.stream().filter(change -> change.type == FileChangeType.MODIFIED).count();
        long deleted = changes.stream().filter(change -> change.type == FileChangeType.DELETED).count();
        addDetailsRow("DevMode", selectedTwin.name, ThemeManager.getDefaultAccent());
        addDetailsRow("Total", String.valueOf(changes.size()), ThemeManager.getDefaultAccent());
        addDetailsRow("Added", String.valueOf(added), ThemeManager.getAccent("nice"));
        addDetailsRow("Modified", String.valueOf(modified), ThemeManager.getAccent("calm"));
        addDetailsRow("Deleted", String.valueOf(deleted), ThemeManager.getAccent("danger"));
        if (changes.isEmpty()) {
            return;
        }
        if (selectedChangePath == null || changes.stream().noneMatch(change -> Objects.equals(change.relativePath, selectedChangePath))) {
            selectedChangePath = changes.getFirst().relativePath;
        }
        FileChange selectedChange = changes.stream()
                .filter(change -> Objects.equals(change.relativePath, selectedChangePath))
                .findFirst()
                .orElse(changes.getFirst());
        addDetailsRow("Path", selectedChange.relativePath, accentForChangeType(selectedChange.type));
        addDetailsRow("State", formatChangeType(selectedChange.type), accentForChangeType(selectedChange.type));
        addDetailsRow("Kind", selectedChange.directory ? "Directory" : selectedChange.text ? "Text" : "Binary", ThemeManager.getDefaultAccent());
        addDetailsRow("Baseline", formatSize(selectedChange.baselineSize), ThemeManager.getDefaultAccent());
        addDetailsRow("Workspace", formatSize(selectedChange.workspaceSize), ThemeManager.getDefaultAccent());
    }

    private void buildTwinDetailsRows(ServerTwin twin) {
        addDetailsRow("Name", twin.name, ThemeManager.getDefaultAccent());
        addDetailsRow("Source", valueOrDash(twin.sourceName), ThemeManager.getDefaultAccent());
        addDetailsRow("Backend", valueOrDash(twin.sourceBackendType), ThemeManager.getDefaultAccent());
        addDetailsRow("Scope", twin.scope == null ? "empty" : twin.scope.summary(), ThemeManager.getDefaultAccent());
        addDetailsRow("Git", twin.gitEnabled ? "Enabled" : "Disabled", twin.gitEnabled ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger"));
        addDetailsRow("Status", twin.statusSummary(), "ready".equalsIgnoreCase(twin.statusSummary()) ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger"));
        addDetailsRow("Workspace", valueOrDash(twin.workspacePath), ThemeManager.getDefaultAccent());
        addDetailsRow("Baseline", valueOrDash(twin.baselinePath), ThemeManager.getDefaultAccent());
        addDetailsRow("Created", formatTime(twin.createdAt), ThemeManager.getDefaultAccent());
        addDetailsRow("LastPull", formatTime(twin.lastPulledAt), ThemeManager.getAccent("calm"));
        addDetailsRow("LastCommit", formatTime(twin.lastDeployedAt), ThemeManager.getAccent("nice"));
        if (twin.lastError != null && !twin.lastError.isBlank()) {
            addDetailsRow("LastError", twin.lastError, ThemeManager.getAccent("danger"));
        }
    }

    private void buildDeploymentDetailsRows(ServerTwin twin) {
        DeploymentRecord selected = twin.deployments.stream().filter(record -> Objects.equals(record.id, selectedDeploymentId)).findFirst().orElse(twin.deployments.isEmpty() ? null : twin.deployments.getFirst());
        if (selected == null) {
            addDetailsRow("Commits", "No Commits", ThemeManager.getDefaultAccent());
            return;
        }
        addDetailsRow("Label", selected.label, ThemeManager.getAccent("nice"));
        addDetailsRow("Mode", formatDeploymentMode(selected.mode), ThemeManager.getDefaultAccent());
        addDetailsRow("Created", formatTime(selected.createdAt), ThemeManager.getDefaultAccent());
        addDetailsRow("Rollback", selected.rollbackAvailable ? "Available" : "Disabled", selected.rollbackAvailable ? ThemeManager.getAccent("calm") : ThemeManager.getAccent("danger"));
        addDetailsRow("Backup", valueOrDash(selected.backupPath), ThemeManager.getDefaultAccent());
        addDetailsRow("Changes", String.valueOf(selected.changes.size()), ThemeManager.getDefaultAccent());
        if (selected.gitCommitShortHash != null && !selected.gitCommitShortHash.isBlank()) {
            addDetailsRow("Commit", selected.gitCommitShortHash, ThemeManager.getAccent("nice"));
        }
        if (selected.gitCommitSubject != null && !selected.gitCommitSubject.isBlank()) {
            addDetailsRow("Subject", selected.gitCommitSubject, ThemeManager.getDefaultAccent());
        }
        selected.changes.stream().limit(20).forEach(change -> addDetailsRow("Path", change.relativePath + " • " + change.summary(), accentForChangeType(change.type)));
    }

    private void addDetailsRow(String title, String value, Accent accent) {
        String safeTitle = title == null || title.isBlank() ? "Detail" : title;
        String safeValue = value == null || value.isBlank() ? "-" : value;
        MountableButtonWidget row = new MountableButtonWidget.Builder(safeTitle)
                .description(safeValue)
                .build();
        row.setActive(true);
        row.setSize(Math.max(120, detailsSidePanel.container().getEffectiveWidth() - 12), 30);
        row.setAccent(accent == null ? ThemeManager.getDefaultAccent() : accent);
        detailsContainer.addWidget(row);
    }

    private void openCreateTwinPopup() {
        TextInputWidget twinNameInput = new TextInputWidget.Builder().text(sourceInstance.getName() + " Dev").build();
        TextInputWidget includeInput = new TextInputWidget.Builder().placeholder("plugins/MyPlugin, config/custom").build();
        TextInputWidget excludeInput = new TextInputWidget.Builder().placeholder("logs, cache, start.sh").build();

        ToggleWidget fullInstanceToggle = new ToggleWidget.Builder().toggled(false).build();
        ToggleWidget configsToggle = new ToggleWidget.Builder().toggled(true).build();
        ToggleWidget pluginsToggle = new ToggleWidget.Builder().toggled(true).build();
        ToggleWidget modsToggle = new ToggleWidget.Builder().toggled(true).build();
        ToggleWidget dataPacksToggle = new ToggleWidget.Builder().toggled(true).build();
        ToggleWidget scriptsToggle = new ToggleWidget.Builder().toggled(false).build();
        ToggleWidget worldToggle = new ToggleWidget.Builder().toggled(false).build();
        ToggleWidget playerDataToggle = new ToggleWidget.Builder().toggled(false).build();
        ToggleWidget statsToggle = new ToggleWidget.Builder().toggled(false).build();
        ToggleWidget advancementsToggle = new ToggleWidget.Builder().toggled(false).build();
        ToggleWidget gitToggle = new ToggleWidget.Builder().toggled(true).build();
        ToggleWidget replaceWorkspaceToggle = new ToggleWidget.Builder().toggled(false).build();

        PopupWidget.Builder builder = new PopupWidget.Builder("Enable DevMode")
                .size(420, 442)
                .setResizable(true)
                .setExpandWithDropdowns(true)
                .setAntiOutOfBound(true)
                .setBoundOffset(desktopMode ? 34 : 0)
                .setMinSize(360, 320);
        builder.addRow("twinName", "Dev Name", true, 18, twinNameInput);
        builder.addRow("git", "Init Git", false, 18, gitToggle);
        builder.addRow("replaceWorkspace", "Reset Duplicate", false, 18, replaceWorkspaceToggle);
        builder.addRow("fullInstance", "Full Instance", false, 18, fullInstanceToggle);
        builder.addRow("configs", "Configs", false, 18, configsToggle);
        builder.addRow("plugins", "Plugins", false, 18, pluginsToggle);
        builder.addRow("mods", "Mods", false, 18, modsToggle);
        builder.addRow("dataPacks", "Data Packs", false, 18, dataPacksToggle);
        builder.addRow("scripts", "Scripts", false, 18, scriptsToggle);
        builder.addRow("world", "World", false, 18, worldToggle);
        builder.addRow("playerData", "Player Data", false, 18, playerDataToggle);
        builder.addRow("stats", "Stats", false, 18, statsToggle);
        builder.addRow("advancements", "Advancements", false, 18, advancementsToggle);
        builder.addRow("includePaths", "Include Paths", true, 18, includeInput);
        builder.addRow("excludePaths", "Exclude Paths", true, 18, excludeInput);

        AnimatedButton createButton = new AnimatedButton.Builder().label("Enable").accentType(ThemeManager.getAccent("nice")).size(90, 18).build();
        AnimatedButton cancelButton = new AnimatedButton.Builder().label("Cancel").accentType(ThemeManager.getAccent("danger")).size(90, 18).build();
        builder.addRow("", true, 22, createButton, cancelButton);

        PopupWidget popup = builder.build();
        Runnable visibilityUpdater = () -> updateCreatePopupVisibility(popup, fullInstanceToggle, worldToggle);
        fullInstanceToggle.setOnChange(visibilityUpdater);
        worldToggle.setOnChange(visibilityUpdater);
        visibilityUpdater.run();

        createButton.setAction(() -> {
            popup.hide();
            ScopeProfile scope = new ScopeProfile();
            scope.fullInstance = fullInstanceToggle.getValue();
            scope.configs = configsToggle.getValue();
            scope.plugins = pluginsToggle.getValue();
            scope.mods = modsToggle.getValue();
            scope.dataPacks = dataPacksToggle.getValue();
            scope.scripts = scriptsToggle.getValue();
            scope.world = worldToggle.getValue();
            scope.playerData = playerDataToggle.getValue();
            scope.stats = statsToggle.getValue();
            scope.advancements = advancementsToggle.getValue();
            scope.includePaths = parsePathList(includeInput.getText());
            scope.excludePaths = parsePathList(excludeInput.getText());
            CreateRequest request = new CreateRequest();
            request.twinName = twinNameInput.getText();
            request.scope = scope;
            request.initializeGit = gitToggle.getValue();
            request.deleteWorkspaceOnReplace = replaceWorkspaceToggle.getValue();
            createTwin(request);
        });
        cancelButton.setAction(popup::hide);

        addDrawableChild(popup);
        popup.show();
    }

    private void clearActiveDiffPopup() {
        if (activeDiffContainer != null) {
            remove(activeDiffContainer);
            activeDiffContainer = null;
            activeDiffLeftEditor = null;
            activeDiffRightEditor = null;
        }
    }

    private void updateCreatePopupVisibility(PopupWidget popup, ToggleWidget fullInstanceToggle, ToggleWidget worldToggle) {
        boolean fullInstance = fullInstanceToggle.getValue();
        boolean world = worldToggle.getValue();
        popup.setRowVisibility("configs", !fullInstance);
        popup.setRowVisibility("plugins", !fullInstance);
        popup.setRowVisibility("mods", !fullInstance);
        popup.setRowVisibility("dataPacks", !fullInstance);
        popup.setRowVisibility("scripts", !fullInstance);
        popup.setRowVisibility("world", !fullInstance);
        popup.setRowVisibility("playerData", !fullInstance && !world);
        popup.setRowVisibility("stats", !fullInstance && !world);
        popup.setRowVisibility("advancements", !fullInstance && !world);
    }

    private void createTwin(CreateRequest request) {
        Notification notification = loadingNotification("Enabling DevMode", sourceInstance.getName());
        twinManager.createTwin(sourceInstance, request, notification).whenComplete((twin, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Enable Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            finishNotification(notification, "DevMode Enabled", twin.name, Notification.Type.SUCCESS);
            selectedTwinId = twin.id;
            selectedChangePath = null;
            selectedDeploymentId = null;
            refreshAll();
        }));
    }

    private void pullSelectedTwin() {
        ServerTwin twin = getSelectedTwin();
        if (twin != null) {
            pullTwin(twin);
        }
    }

    private void pullTwin(ServerTwin twin) {
        Notification notification = loadingNotification("Pulling DevMode", twin.name);
        twinManager.pullTwin(sourceInstance, twin.id, notification).whenComplete((result, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Pull Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            finishNotification(notification, "Pull Complete", twin.name, Notification.Type.SUCCESS);
            selectedTwinId = twin.id;
            changeCache.remove(twin.id);
            previewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            parsedPreviewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            refreshAll();
        }));
    }

    private void pushSelectedTwin() {
        ServerTwin twin = getSelectedTwin();
        if (twin != null) {
            pushTwin(twin);
        }
    }

    private void pushTwin(ServerTwin twin) {
        Notification notification = loadingNotification("Pushing DevMode", twin.name);
        twinManager.pushTwin(sourceInstance, twin.id, notification).whenComplete((result, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Push Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            finishNotification(notification, "Push Complete", twin.name, Notification.Type.SUCCESS);
            selectedTwinId = twin.id;
            changeCache.remove(twin.id);
            previewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            parsedPreviewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            refreshAll();
        }));
    }

    private void openDeployPopup() {
        ServerTwin twin = getSelectedTwin();
        if (twin != null) {
            openDeployPopup(twin);
        }
    }

    private void openDeployPopup(ServerTwin twin) {
        TextInputWidget labelInput = new TextInputWidget.Builder().placeholder("Commit Message").build();
        List<DeploymentMode> modes = List.of(DeploymentMode.DIRECT, DeploymentMode.STAGED);
        DropDownWidget<DeploymentMode> modeDropdown = new DropDownWidget.Builder<>(modes)
                .displayFunction(this::formatDeploymentMode)
                .selectedItem(DeploymentMode.DIRECT)
                .size(180, 18)
                .build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Commit Changes").size(320, 150).setResizable(false);
        builder.addRow("label", "Message", true, 18, labelInput);
        builder.addRow("mode", "Mode", true, 18, modeDropdown);
        AnimatedButton deployButton = new AnimatedButton.Builder().label("Commit").accentType(ThemeManager.getAccent("nice")).size(90, 18).build();
        AnimatedButton cancelButton = new AnimatedButton.Builder().label("Cancel").accentType(ThemeManager.getAccent("danger")).size(90, 18).build();
        builder.addRow("", true, 22, deployButton, cancelButton);
        PopupWidget popup = builder.build();
        deployButton.setAction(() -> {
            popup.hide();
            deployTwin(twin, labelInput.getText(), modeDropdown.getSelectedItem());
        });
        cancelButton.setAction(popup::hide);
        addDrawableChild(popup);
        popup.show();
    }

    private void deployTwin(ServerTwin twin, String label, DeploymentMode mode) {
        Notification notification = loadingNotification("Committing", twin.name);
        twinManager.deployTwin(sourceInstance, twin.id, label, mode, notification).whenComplete((record, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Commit Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            finishNotification(notification, "Commit Created", record.gitCommitShortHash == null || record.gitCommitShortHash.isBlank() ? record.label : record.label + " • " + record.gitCommitShortHash, Notification.Type.SUCCESS);
            selectedTwinId = twin.id;
            selectedDeploymentId = record.id;
            changeCache.remove(twin.id);
            previewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            parsedPreviewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            refreshAll();
        }));
    }

    private void rollbackDeployment(ServerTwin twin, DeploymentRecord deployment) {
        Notification notification = loadingNotification("Rolling Back", deployment.label);
        twinManager.rollbackDeployment(sourceInstance, twin.id, deployment.id, notification).whenComplete((record, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Rollback Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            selectedTwinId = twin.id;
            changeCache.remove(twin.id);
            previewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            refreshAll();
        }));
    }

    private void restoreChange(ServerTwin twin, FileChange change) {
        Notification notification = loadingNotification("Restoring Path", change.relativePath);
        twinManager.restoreWorkspacePath(twin.id, change.relativePath).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Restore Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            finishNotification(notification, "Path Restored", change.relativePath, Notification.Type.SUCCESS);
            changeCache.remove(twin.id);
            previewCache.remove(buildPreviewKey(twin.id, change.relativePath));
            parsedPreviewCache.remove(buildPreviewKey(twin.id, change.relativePath));
            refreshChangesRows();
        }));
    }

    private void openChangePreview(ServerTwin twin, FileChange change) {
        selectChange(change.relativePath);
        String previewKey = buildPreviewKey(twin.id, change.relativePath);
        parsedPreviewCache.remove(previewKey);
        String cachedPreview = previewCache.get(previewKey);
        if (cachedPreview != null) {
            showDiffPreviewPopup(twin, change.relativePath, cachedPreview);
            return;
        }
        Notification notification = loadingNotification("Loading Preview", change.relativePath);
        long nonce = ++previewRequestNonce;
        twinManager.buildChangePreview(twin.id, change.relativePath).whenComplete((preview, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (nonce != previewRequestNonce || !Objects.equals(selectedTwinId, twin.id)) {
                return;
            }
            if (throwable != null) {
                finishNotification(notification, "Preview Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            previewCache.put(previewKey, preview);
            finishNotification(notification, "Preview Ready", change.relativePath, Notification.Type.SUCCESS);
            showDiffPreviewPopup(twin, change.relativePath, preview);
            updateDetailsForCurrentTab();
        }));
    }

    private void showDiffPreviewPopup(ServerTwin twin, String relativePath, String preview) {
        String previewKey = buildPreviewKey(twin.id, relativePath);
        ParsedDiff parsedDiff = parsedPreviewCache.get(previewKey);
        if (parsedDiff == null) {
            parsedDiff = parseDiffPreview(preview);
            parsedPreviewCache.put(previewKey, parsedDiff);
        }
        RenderedSplitDiff splitRendered = renderPairedSplitDiff(parsedDiff);

        int padding = 8;
        int popupWidth = Math.max(360, width - padding * 2);
        int popupHeight = Math.max(260, height - padding * 2);
        int gutter = 8;
        int halfWidth = Math.max(160, (popupWidth - 12 - gutter) / 2);
        int editorHeight = Math.max(120, popupHeight - 16 - 12);

        CodeEditorWidget leftEditor = buildDiffEditor(splitRendered.leftText(), splitRendered.leftLineTypes(), halfWidth, editorHeight);
        CodeEditorWidget rightEditor = buildDiffEditor(splitRendered.rightText(), splitRendered.rightLineTypes(), halfWidth, editorHeight);
        leftEditor.registerPlugin(new DiffPlugin(splitRendered.leftLineTypes()));
        rightEditor.registerPlugin(new DiffPlugin(splitRendered.rightLineTypes()));

        PopupWidget popup = new PopupWidget(padding, padding, popupWidth, popupHeight, "") {
            @Override
            public void tick() {
                super.tick();
                if (isResizing) {
                    int currentHalfWidth = Math.max(160, (getWidth() - 12 - gutter) / 2);
                    int currentEditorHeight = Math.max(120, getHeight() - 16 - 12);
                    rows.stream()
                            .filter(row -> "splitRow".equals(row.id) && row.widgets.size() >= 2)
                            .findFirst()
                            .ifPresent(row -> {
                                var leftWidget = row.widgets.get(0);
                                var rightWidget = row.widgets.get(1);
                                if (leftWidget.getWidth() != currentHalfWidth) {
                                    leftWidget.setWidth(currentHalfWidth);
                                }
                                if (rightWidget.getWidth() != currentHalfWidth) {
                                    rightWidget.setWidth(currentHalfWidth);
                                }
                                if (leftWidget.getHeight() != currentEditorHeight) {
                                    leftWidget.setHeight(currentEditorHeight);
                                }
                                if (rightWidget.getHeight() != currentEditorHeight) {
                                    rightWidget.setHeight(currentEditorHeight);
                                }
                            });
                }
            }

        };
        clearActiveDiffPopup();
        activeDiffContainer = popup;
        activeDiffLeftEditor = leftEditor;
        activeDiffRightEditor = rightEditor;
        popup.resizable = true;
        popup.addRow("splitRow", "", List.of(leftEditor, rightEditor), editorHeight, true, false);
        popup.setRowVisibility("splitRow", true);

        leftEditor.setFocused(true);
        rightEditor.setFocused(false);
        popup.setFocusedWidget(leftEditor);

        addDrawableChild(popup);
        popup.show();
    }

    private void openDeleteTwinPopup() {
        ServerTwin twin = getSelectedTwin();
        if (twin != null) {
            openDeleteTwinPopup(twin);
        }
    }

    private void openDeleteTwinPopup(ServerTwin twin) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Disable DevMode").size(340, 140).setResizable(false);
        AnimatedButton deleteTwinButton = new AnimatedButton.Builder().label("Disable").accentType(ThemeManager.getAccent("danger")).size(96, 18).build();
        AnimatedButton deleteAllButton = new AnimatedButton.Builder().label("Disable+Purge").accentType(ThemeManager.getAccent("danger")).size(96, 18).build();
        AnimatedButton cancelButton = new AnimatedButton.Builder().label("Cancel").accentType(ThemeManager.getAccent("calm")).size(90, 18).build();
        builder.addRow("", true, 22, deleteTwinButton, deleteAllButton, cancelButton);
        PopupWidget popup = builder.build();
        deleteTwinButton.setAction(() -> {
            popup.hide();
            deleteTwin(twin, false);
        });
        deleteAllButton.setAction(() -> {
            popup.hide();
            deleteTwin(twin, true);
        });
        cancelButton.setAction(popup::hide);
        addDrawableChild(popup);
        popup.show();
    }

    private void deleteTwin(ServerTwin twin, boolean deleteWorkspace) {
        Notification notification = loadingNotification(deleteWorkspace ? "Disabling DevMode" : "Disabling DevMode", twin.name);
        twinManager.disableTwinForSource(sourceInstance, deleteWorkspace).whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null) {
                finishNotification(notification, "Disable Failed", resolveThrowable(throwable), Notification.Type.ERROR);
                return;
            }
            finishNotification(notification, deleteWorkspace ? "DevMode Disabled+Purged" : "DevMode Disabled", twin.name, Notification.Type.SUCCESS);
            changeCache.remove(twin.id);
            previewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            parsedPreviewCache.keySet().removeIf(key -> key.startsWith(twin.id + "::"));
            if (Objects.equals(selectedTwinId, twin.id)) {
                selectedTwinId = null;
                selectedChangePath = null;
                selectedDeploymentId = null;
            }
            refreshAll();
        }));
    }

    private void openSelectedTwinWorkspace() {
        ServerTwin twin = getSelectedTwin();
        if (twin != null) {
            openTwinWorkspace(twin);
        }
    }

    private void openTwinWorkspace(ServerTwin twin) {
        Instance twinInstance = twinManager.getTwinInstance(twin);
        if (twinInstance == null) {
            new Notification.Builder().message("Workspace Missing").description(twin.name).type(Notification.Type.ERROR).build();
            return;
        }
        client.setScreen(new FileExplorerScreen(this, twinInstance, Path.of(twinInstance.getPath()), Path.of(remotelyDir.toString(), "data"), false) {
            @Override
            public void close() {
                if (desktopMode && isDesktopWindow()) {
                    var overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
                    if (overlay != null) {
                        overlay.requestCloseWindowForScreen(this);
                        return;
                    }
                }
                ScreenManager.getInstance().setScreen(ServerTwinScreen.this);
            }

            @Override
            public String getDesktopAppId() {
                return "file-explorer";
            }

            @Override
            public String getDesktopAppTitle() {
                return "Dev Workspace";
            }

            @Override
            public String getDesktopAppIconPath() {
                return "explorer.png";
            }
        });
    }

    private void openSelectedTwinTerminal() {
        ServerTwin twin = getSelectedTwin();
        if (twin != null) {
            openTwinTerminal(twin);
        }
    }

    private void openTwinTerminal(ServerTwin twin) {
        Instance twinInstance = twinManager.getTwinInstance(twin);
        if (twinInstance == null) {
            new Notification.Builder().message("DevMode Missing").description(twin.name).type(Notification.Type.ERROR).build();
            return;
        }
        remotelyClient.openInstanceInTerminal(this, twinInstance);
    }

    private MountableButtonWidget createInfoRow(Container container, String title, String description) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title)
                .description(description)
                .build();
        row.setActive(true);
        styleRow(row, container, ThemeManager.getDefaultAccent(), 34);
        return row;
    }

    private SquareButtonWidget actionButton(String iconPath, String hint, Runnable action) {
        return actionButton(iconPath, hint, action, ThemeManager.getDefaultAccent());
    }

    private SquareButtonWidget actionButton(String iconPath, String hint, Runnable action, Accent accent) {
        return new SquareButtonWidget.Builder()
                .imagePath(iconPath)
                .onClick(action)
                .hint(hint)
                .accentType(accent)
                .animateElevation(false)
                .size(18, 18)
                .build();
    }

    private void styleRow(MountableButtonWidget row, Container container, Accent accent, int height) {
        row.setAccent(accent);
        row.setSize(Math.max(140, container.getEffectiveWidth() - 10), height);
    }

    private void resizeRows(Container container) {
        if (container == null) {
            return;
        }
        int rowWidth = Math.max(140, container.getEffectiveWidth() - 10);
        container.getWidgets().forEach(widget -> {
            if (widget instanceof MountableButtonWidget row) {
                row.setSize(rowWidth, Math.max(18, row.getHeight()));
            }
        });
    }

    private Accent accentForChangeType(FileChangeType type) {
        return switch (type == null ? FileChangeType.MODIFIED : type) {
            case ADDED -> ThemeManager.getAccent("nice");
            case MODIFIED -> ThemeManager.getAccent("calm");
            case DELETED -> ThemeManager.getAccent("danger");
        };
    }

    private ServerTwin getSelectedTwin() {
        if (selectedTwinId == null || selectedTwinId.isBlank()) {
            return null;
        }
        ServerTwin twin = twinManager.getTwin(selectedTwinId);
        if (twin != null) {
            return twin;
        }
        return twins.stream().filter(item -> Objects.equals(item.id, selectedTwinId)).findFirst().orElse(null);
    }

    private Notification loadingNotification(String message, String description) {
        return new Notification.Builder()
                .message(message)
                .description(description)
                .type(Notification.Type.INFO)
                .loading(true)
                .autoSlideOut(false)
                .build();
    }

    private void finishNotification(Notification notification, String message, String description, Notification.Type type) {
        notification.update()
                .message(message)
                .description(description)
                .type(type)
                .loading(false)
                .autoSlideOut(true)
                .commit();
    }

    private List<String> parsePathList(String value) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        for (String part : value.split("[,\\r\\n]+")) {
            String trimmed = part.trim().replace('\\', '/');
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return new ArrayList<>(values);
    }

    private String formatDeploymentMode(DeploymentMode mode) {
        return formatEnum(mode == null ? DeploymentMode.DIRECT.name() : mode.name());
    }

    private String formatChangeType(FileChangeType type) {
        return formatEnum(type == null ? FileChangeType.MODIFIED.name() : type.name());
    }

    private String formatEnum(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (normalized.isBlank()) {
            return "Unknown";
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current == ' ') {
                builder.append(current);
                capitalizeNext = true;
                continue;
            }
            builder.append(capitalizeNext ? Character.toUpperCase(current) : current);
            capitalizeNext = false;
        }
        return builder.toString();
    }

    private String formatTime(long time) {
        return time <= 0 ? "Never" : Instant.ofEpochMilli(time).toString();
    }

    private long lastTwinEventTime(ServerTwin twin) {
        return Math.max(twin.lastDeployedAt, Math.max(twin.lastPulledAt, twin.createdAt));
    }

    private String formatSize(long size) {
        if (size < 0) {
            return "-";
        }
        if (size < 1024) {
            return size + " B";
        }
        double value = size;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = -1;
        while (value >= 1024 && unitIndex + 1 < units.length) {
            value /= 1024d;
            unitIndex++;
        }
        if (unitIndex <= 0) {
            return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[Math.max(0, unitIndex)]);
        }
        return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
    }

    private String buildPreviewKey(String twinId, String relativePath) {
        return twinId + "::" + relativePath;
    }

    private CodeEditorWidget buildDiffEditor(String text, Map<Integer, DiffPlugin.LineType> lineTypes, int width, int height) {
        CodeEditorWidget editor = new CodeEditorWidget(0, 0, width, height);
        editor.setText(text == null ? "" : text);
        editor.setReadOnly(true);
        editor.setMonospace(true);
        editor.setWordWrap(false);
        editor.setShowLineNumbers(false);
        editor.setShowSearchNavigation(false);
        editor.setShowCursorLineHighlight(false);
        editor.setShowSearchMatchHighlight(false);
        editor.setDimNonMatchingLines(false);
        editor.setAutoScrollEnabled(false);
        editor.setScrollOffsetY(0f);
        editor.setScrollOffsetX(0f);
        editor.setLanguage("plain");
        return editor;
    }

    private ParsedDiff parseDiffPreview(String preview) {
        if (preview == null || preview.isBlank()) {
            return new ParsedDiff(List.of(), 0, 0, "No visible diff", 0, 0);
        }
        List<ParsedHunk> hunks = new ArrayList<>();
        List<ParsedDiffLine> currentLines = new ArrayList<>();
        String currentHeader = null;
        int added = 0;
        int removed = 0;
        String normalizedPreview = preview.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedPreview.split("\n", -1);
        int totalLines = lines.length;
        int visibleLines = 0;
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            if (line.equals("…")) {
                if (currentHeader != null && !currentLines.isEmpty()) {
                    hunks.add(new ParsedHunk(currentHeader, List.copyOf(currentLines)));
                    currentLines = new ArrayList<>();
                }
                currentHeader = null;
                continue;
            }
            if (line.startsWith("@@") && line.endsWith("@@")) {
                if (currentHeader != null && !currentLines.isEmpty()) {
                    hunks.add(new ParsedHunk(currentHeader, List.copyOf(currentLines)));
                    currentLines = new ArrayList<>();
                }
                currentHeader = line;
                continue;
            }
            if (line.equals("No visible diff")) {
                continue;
            }
            char marker = line.isEmpty() ? ' ' : line.charAt(0);
            String content;
            if (marker == '+' || marker == '-' || marker == ' ') {
                content = line.length() > 1 ? line.substring(1) : "";
            } else {
                marker = ' ';
                content = line;
            }
            if (currentHeader == null) {
                currentHeader = "@@ -1,0 +1,0 @@";
            }
            if (marker == '+') {
                added++;
            } else if (marker == '-') {
                removed++;
            }
            currentLines.add(new ParsedDiffLine(marker, content));
            visibleLines++;
        }
        if (currentHeader != null && !currentLines.isEmpty()) {
            hunks.add(new ParsedHunk(currentHeader, List.copyOf(currentLines)));
        }
        if (hunks.isEmpty()) {
            return new ParsedDiff(List.of(), 0, 0, normalizedPreview.isBlank() ? "No visible diff" : normalizedPreview, totalLines, totalLines);
        }
        return new ParsedDiff(List.copyOf(hunks), added, removed, "", visibleLines, totalLines);
    }

    private RenderedDiff renderUnifiedDiff(ParsedDiff parsed) {
        if (parsed == null || parsed.hunks().isEmpty()) {
            String fallback = parsed == null || parsed.fallbackText() == null || parsed.fallbackText().isBlank() ? "No visible diff" : parsed.fallbackText();
            return new RenderedDiff(fallback, Map.of());
        }
        List<String> out = new ArrayList<>();
        Map<Integer, DiffPlugin.LineType> types = new TreeMap<>();
        int lineIndex = 0;
        int oldWidth = lineNumberWidth(parsed, true);
        int newWidth = lineNumberWidth(parsed, false);
        for (int hunkIndex = 0; hunkIndex < parsed.hunks().size(); hunkIndex++) {
            ParsedHunk hunk = parsed.hunks().get(hunkIndex);
            out.add(hunk.header());
            types.put(lineIndex++, DiffPlugin.LineType.HEADER);
            int oldLine = parseOldStart(hunk.header());
            int newLine = parseNewStart(hunk.header());
            for (ParsedDiffLine line : hunk.lines()) {
                char marker = line.marker();
                if (marker == '+') {
                    out.add(formatUnifiedLine(-1, newLine, marker, line.content(), oldWidth, newWidth));
                    types.put(lineIndex++, DiffPlugin.LineType.ADD);
                    newLine++;
                    continue;
                }
                if (marker == '-') {
                    out.add(formatUnifiedLine(oldLine, -1, marker, line.content(), oldWidth, newWidth));
                    types.put(lineIndex++, DiffPlugin.LineType.REMOVE);
                    oldLine++;
                    continue;
                }
                out.add(formatUnifiedLine(oldLine, newLine, ' ', line.content(), oldWidth, newWidth));
                types.put(lineIndex, DiffPlugin.LineType.CONTEXT);
                lineIndex++;
                oldLine++;
                newLine++;
            }
            if (hunkIndex + 1 < parsed.hunks().size()) {
                out.add("…");
                types.put(lineIndex++, DiffPlugin.LineType.HEADER);
            }
        }
        return new RenderedDiff(String.join("\n", out), types);
    }

    private RenderedSplitDiff renderPairedSplitDiff(ParsedDiff parsed) {
        if (parsed == null || parsed.hunks().isEmpty()) {
            String fallback = parsed == null || parsed.fallbackText() == null || parsed.fallbackText().isBlank() ? "No visible diff" : parsed.fallbackText();
            return new RenderedSplitDiff(fallback, fallback, Map.of(), Map.of());
        }
        List<String> leftOut = new ArrayList<>();
        List<String> rightOut = new ArrayList<>();
        Map<Integer, DiffPlugin.LineType> leftTypes = new TreeMap<>();
        Map<Integer, DiffPlugin.LineType> rightTypes = new TreeMap<>();
        int lineIndex = 0;
        int oldWidth = lineNumberWidth(parsed, true);
        int newWidth = lineNumberWidth(parsed, false);
        for (int hunkIndex = 0; hunkIndex < parsed.hunks().size(); hunkIndex++) {
            ParsedHunk hunk = parsed.hunks().get(hunkIndex);
            leftOut.add(hunk.header());
            rightOut.add(hunk.header());
            leftTypes.put(lineIndex, DiffPlugin.LineType.HEADER);
            rightTypes.put(lineIndex, DiffPlugin.LineType.HEADER);
            lineIndex++;

            List<SplitLine> splitLines = buildSplitLines(hunk);
            for (SplitLine splitLine : splitLines) {
                char leftMarker = splitLine.marker() == '-' || splitLine.marker() == '±' ? '-' : ' ';
                char rightMarker = splitLine.marker() == '+' || splitLine.marker() == '±' ? '+' : ' ';
                leftOut.add(formatSplitLine(splitLine.oldLine(), leftMarker, splitLine.oldText(), oldWidth));
                rightOut.add(formatSplitLine(splitLine.newLine(), rightMarker, splitLine.newText(), newWidth));

                DiffPlugin.LineType leftType = switch (splitLine.marker()) {
                    case '-', '±' -> DiffPlugin.LineType.REMOVE;
                    default -> DiffPlugin.LineType.CONTEXT;
                };
                DiffPlugin.LineType rightType = switch (splitLine.marker()) {
                    case '+', '±' -> DiffPlugin.LineType.ADD;
                    default -> DiffPlugin.LineType.CONTEXT;
                };
                leftTypes.put(lineIndex, leftType);
                rightTypes.put(lineIndex, rightType);
                lineIndex++;
            }

            if (hunkIndex + 1 < parsed.hunks().size()) {
                leftOut.add("…");
                rightOut.add("…");
                leftTypes.put(lineIndex, DiffPlugin.LineType.HEADER);
                rightTypes.put(lineIndex, DiffPlugin.LineType.HEADER);
                lineIndex++;
            }
        }
        return new RenderedSplitDiff(
                String.join("\n", leftOut),
                String.join("\n", rightOut),
                leftTypes,
                rightTypes
        );
    }

    private int lineNumberWidth(ParsedDiff parsed, boolean oldSide) {
        if (parsed == null || parsed.hunks().isEmpty()) {
            return 4;
        }
        int maxLine = 1;
        for (ParsedHunk hunk : parsed.hunks()) {
            int cursor = oldSide ? parseOldStart(hunk.header()) : parseNewStart(hunk.header());
            maxLine = Math.max(maxLine, cursor);
            for (ParsedDiffLine line : hunk.lines()) {
                if (oldSide && line.marker() == '+') {
                    continue;
                }
                if (!oldSide && line.marker() == '-') {
                    continue;
                }
                maxLine = Math.max(maxLine, cursor);
                cursor++;
            }
        }
        return Math.max(4, String.valueOf(maxLine).length());
    }

    private String formatSplitLine(int lineNumber, char marker, String content, int numberWidth) {
        String lineText = lineNumber > 0
                ? String.format(Locale.ROOT, "%" + numberWidth + "d", lineNumber)
                : " ".repeat(Math.max(1, numberWidth));
        String body = content == null ? "" : content;
        return lineText + " " + marker + " " + body;
    }

    private List<SplitLine> buildSplitLines(ParsedHunk hunk) {
        List<SplitLine> result = new ArrayList<>();
        if (hunk == null || hunk.lines().isEmpty()) {
            return result;
        }
        int oldLine = parseOldStart(hunk.header());
        int newLine = parseNewStart(hunk.header());
        int index = 0;
        while (index < hunk.lines().size()) {
            ParsedDiffLine current = hunk.lines().get(index);
            char marker = current.marker();
            if (marker == '-') {
                if (index + 1 < hunk.lines().size() && hunk.lines().get(index + 1).marker() == '+') {
                    ParsedDiffLine next = hunk.lines().get(index + 1);
                    result.add(new SplitLine(oldLine, newLine, '±', current.content(), next.content()));
                    oldLine++;
                    newLine++;
                    index += 2;
                    continue;
                }
                result.add(new SplitLine(oldLine, -1, '-', current.content(), ""));
                oldLine++;
                index++;
                continue;
            }
            if (marker == '+') {
                result.add(new SplitLine(-1, newLine, '+', "", current.content()));
                newLine++;
                index++;
                continue;
            }
            result.add(new SplitLine(oldLine, newLine, ' ', current.content(), current.content()));
            oldLine++;
            newLine++;
            index++;
        }
        return result;
    }

    private int parseOldStart(String header) {
        if (header == null || header.isBlank()) {
            return 1;
        }
        String[] parts = header.split(" ");
        for (String part : parts) {
            if (part.startsWith("-")) {
                String value = part.substring(1);
                int comma = value.indexOf(',');
                String number = comma >= 0 ? value.substring(0, comma) : value;
                try {
                    return Integer.parseInt(number);
                } catch (Exception ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private int parseNewStart(String header) {
        if (header == null || header.isBlank()) {
            return 1;
        }
        String[] parts = header.split(" ");
        for (String part : parts) {
            if (part.startsWith("+")) {
                String value = part.substring(1);
                int comma = value.indexOf(',');
                String number = comma >= 0 ? value.substring(0, comma) : value;
                try {
                    return Integer.parseInt(number);
                } catch (Exception ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private String formatUnifiedLine(int oldLine, int newLine, char marker, String content, int oldWidth, int newWidth) {
        String oldText = oldLine > 0 ? String.format(Locale.ROOT, "%" + oldWidth + "d", oldLine) : " ".repeat(Math.max(1, oldWidth));
        String newText = newLine > 0 ? String.format(Locale.ROOT, "%" + newWidth + "d", newLine) : " ".repeat(Math.max(1, newWidth));
        return oldText + " " + newText + " " + marker + " " + content;
    }

    private boolean sameTwinSnapshot(List<ServerTwin> current, List<ServerTwin> next) {
        if (current == next) {
            return true;
        }
        if (current == null || next == null || current.size() != next.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!Objects.equals(buildTwinSnapshotSignature(current.get(i)), buildTwinSnapshotSignature(next.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private String buildTwinSnapshotSignature(ServerTwin twin) {
        if (twin == null) {
            return "";
        }
        String latestDeployment = twin.deployments.isEmpty() ? "" : twin.deployments.getFirst().id + ":" + twin.deployments.getFirst().createdAt + ":" + twin.deployments.getFirst().changes.size() + ":" + valueOrDash(twin.deployments.getFirst().gitCommitShortHash);
        return String.join("|",
                valueOrDash(twin.id),
                valueOrDash(twin.name),
                String.valueOf(twin.createdAt),
                String.valueOf(twin.lastPulledAt),
                String.valueOf(twin.lastDeployedAt),
                valueOrDash(twin.lastError),
                String.valueOf(twin.deployments.size()),
                latestDeployment,
                valueOrDash(twin.statusSummary())
        );
    }

    private boolean sameChangeSnapshot(List<FileChange> current, List<FileChange> next) {
        if (current == next) {
            return true;
        }
        if (current == null || next == null || current.size() != next.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            FileChange left = current.get(i);
            FileChange right = next.get(i);
            if (!Objects.equals(left.relativePath, right.relativePath)
                    || left.type != right.type
                    || left.directory != right.directory
                    || left.baselineSize != right.baselineSize
                    || left.workspaceSize != right.workspaceSize
                    || left.text != right.text) {
                return false;
            }
        }
        return true;
    }

    private record ParsedDiffLine(char marker, String content) {
    }

    private record ParsedHunk(String header, List<ParsedDiffLine> lines) {
    }

    private record SplitLine(int oldLine, int newLine, char marker, String oldText, String newText) {
    }

    private record ParsedDiff(List<ParsedHunk> hunks, int added, int removed, String fallbackText, int visibleLineCount, int totalLineCount) {
    }

    private record RenderedDiff(String text, Map<Integer, DiffPlugin.LineType> lineTypes) {
    }

    private record RenderedSplitDiff(String leftText, String rightText, Map<Integer, DiffPlugin.LineType> leftLineTypes, Map<Integer, DiffPlugin.LineType> rightLineTypes) {
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String resolveThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? current.getClass().getSimpleName() : current.getMessage();
    }
}
