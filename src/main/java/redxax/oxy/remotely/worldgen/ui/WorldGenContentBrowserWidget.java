package redxax.oxy.remotely.worldgen.ui;

import redxax.oxy.remotely.worldgen.data.WorldGenStage;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.ui.screens.editor.CompactWorkspaceBrowserWidget;
import restudio.rebase.ui.screens.editor.WorkspaceTreeExplorer;
import restudio.rebase.ui.widgets.FileEntryWidget;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.util.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class WorldGenContentBrowserWidget {
    private static final int TOP = 38;
    private static final int BOTTOM = 10;
    private static final int DEFAULT_WIDTH = 320;
    private static final int MIN_WIDTH = 260;
    private static final int MAX_WIDTH = 440;
    private static final int ENTRY_HEIGHT = 22;

    private final WorldGenEditorScreen screen;
    private final Path root = Path.of("worldgen");
    private final StageProvider provider = new StageProvider();
    private final CompactWorkspaceBrowserWidget browser;
    private WorldGenStage selectedStage;

    public WorldGenContentBrowserWidget(WorldGenEditorScreen screen) {
        this.screen = screen;
        SquareButtonWidget projects = tool("folder.png", "Projects", screen::showProjectPopup);
        SquareButtonWidget settings = tool("info.png", "World Settings", screen::showSettingsPopup);
        SquareButtonWidget preview = tool("start.png", "Preview", screen::showPreviewPopup);
        SquareButtonWidget stop = tool("stop.png", "Stop Preview", screen::stopPreview);
        browser = new CompactWorkspaceBrowserWidget(screen, "worldgenContentBrowser", TOP, BOTTOM, DEFAULT_WIDTH, MIN_WIDTH, 120,
            MAX_WIDTH, ENTRY_HEIGHT, this::openStage, false, SidePanel.Anchor.RIGHT, projects, settings, preview, stop);
        browser.treeExplorer().setToggleDirectoriesOnActivation(false);
        browser.treeExplorer().setOnNodeActivated(ref -> openStage(ref.path()));
        browser.treeExplorer().setOnNodeOpened(ref -> openStage(ref.path()));
        browser.treeExplorer().setOnNodePrepared(this::prepareNode);
        browser.sidePanel().show();
    }

    private SquareButtonWidget tool(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder()
            .identifier(Identifier.icon(icon))
            .size(18, 18)
            .hint(hint)
            .onClick(action)
            .build();
    }

    public void rebuild(WorldGenStage stage) {
        selectedStage = stage;
        provider.rebuild();
        browser.setWorkspace(root, provider, true);
        browser.layout();
    }

    public void layout() {
        browser.layout();
    }

    public int visibleLayoutWidth() {
        return browser.visibleLayoutWidth();
    }

    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        browser.layout();
        SidePanel panel = browser.sidePanel();
        panel.update();
        panel.container().render(context, mouseX, mouseY, delta);
        panel.renderHeader(context, mouseX, mouseY);
        panel.container().renderHintOverlay(context);
    }

    public boolean mouseClicked(ReMouseEvent event) {
        return browser.mouseClicked(event);
    }

    public boolean mouseReleased(ReMouseEvent event) {
        return browser.mouseReleased(event);
    }

    public boolean mouseDragged(ReMouseEvent event) {
        return browser.mouseDragged(event);
    }

    public boolean mouseScrolled(ReScrollEvent event) {
        return browser.mouseScrolled(event);
    }

    public boolean keyPressed(ReKeyEvent event) {
        return browser.keyPressed(event);
    }

    public boolean textInput(ReTextInputEvent event) {
        return browser.textInput(event);
    }

    private void openStage(Path path) {
        WorldGenStage stage = provider.stage(path);
        if (stage != null) {
            screen.switchStage(stage);
        }
    }

    private void prepareNode(WorkspaceTreeExplorer.NodeRef ref, FileEntryWidget widget) {
        WorldGenStage stage = provider.stage(ref.path());
        widget.setPersistentHighlight(stage == selectedStage);
        widget.setPersistentAccent(stage == selectedStage ? ThemeManager.getAccent("calm") : null);
        widget.setGradientEnabled(false);
        widget.setTrailingWidgets(List.of());
        if (stage != null) {
            widget.setHint(screen.stageDescription(stage));
        }
    }

    private final class StageProvider implements FileSystemProvider {
        private final Map<Path, WorldGenStage> stages = new HashMap<>();
        private final Map<WorldGenStage, Path> paths = new EnumMap<>(WorldGenStage.class);
        private List<FileEntry> entries = List.of();

        private void rebuild() {
            stages.clear();
            paths.clear();
            List<FileEntry> rebuilt = new ArrayList<>();
            for (WorldGenStage stage : WorldGenStage.values()) {
                Path path = root.resolve(stage.name().toLowerCase());
                stages.put(path, stage);
                paths.put(stage, path);
                int nodeCount = screen.stageNodeCount(stage);
                FileEntry entry = new FileEntry(path, false, "", "", stage.displayName() + " · " + nodeCount + " Nodes");
                entry.metadata.put("icon", "graph.png");
                entry.metadata.put("description", screen.stageDescription(stage));
                rebuilt.add(entry);
            }
            entries = List.copyOf(rebuilt);
        }

        private WorldGenStage stage(Path path) {
            return stages.get(path);
        }

        @Override
        public CompletableFuture<List<FileEntry>> ls(Path path) {
            return CompletableFuture.completedFuture(root.equals(path) ? entries : List.of());
        }

        @Override
        public CompletableFuture<Void> copy(List<Path> sources, Path destination) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> move(List<Path> sources, Path destination) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> delete(List<Path> paths) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<String> read(Path path) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public CompletableFuture<Void> write(Path path, String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> download(List<Path> remotePaths, Path localPath) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> rename(Path oldPath, Path newPath) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> createFile(Path path) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> createDirectory(Path path) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> exists(Path path) {
            return CompletableFuture.completedFuture(root.equals(path) || stages.containsKey(path));
        }

        @Override
        public String getMetadata(String key) {
            return switch (key) {
                case "type" -> "WORLDGEN";
                case "rootIcon" -> "world.png";
                default -> null;
            };
        }
    }
}
