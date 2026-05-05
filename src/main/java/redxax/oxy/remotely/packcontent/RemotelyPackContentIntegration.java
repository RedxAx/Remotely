package redxax.oxy.remotely.packcontent;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedFileSystemProvider;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.ui.screens.editor.EditorDecorationBinding;
import restudio.rebase.ui.screens.editor.FileEditorScreen;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.ui.widgets.editor.TextLineDecoration;
import restudio.rescreen.config.Config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class RemotelyPackContentIntegration {
    private RemotelyPackContentIntegration() {
    }

    public static void install() {
        FileEditorScreen.setEditorDecorationBinder(RemotelyPackContentIntegration::bindEditor);
    }

    public static void refresh(Instance instance, FileSystemProvider provider, Path workspaceRoot) {
        if (provider == null || workspaceRoot == null) {
            return;
        }
        PackContentRegistry.get().refresh(instance, provider, workspaceRoot);
    }

    public static GlyphPreviewMode mode() {
        if (Config.configManager instanceof RemotelyConfigManager remotelyConfigManager) {
            return remotelyConfigManager.getGlyphPreviewMode();
        }
        return GlyphPreviewMode.INLINE_HOVER;
    }

    public static void refreshInstance(Instance instance) {
        if (instance == null || instance.getPath() == null || instance.getBackend() == null) {
            return;
        }
        refresh(instance, new UnifiedFileSystemProvider(InstanceApi.of(instance).files()), Path.of(instance.getPath()));
    }

    public static CompletableFuture<Integer> refreshAllInstances() {
        List<Instance> instances = knownInstances();
        List<CompletableFuture<Void>> refreshes = new ArrayList<>();
        for (Instance instance : instances) {
            if (instance == null || instance.getPath() == null || instance.getBackend() == null) {
                continue;
            }
            Path root = Path.of(instance.getPath());
            FileSystemProvider provider = new UnifiedFileSystemProvider(InstanceApi.of(instance).files());
            refreshes.add(PackContentRegistry.get().refresh(instance, provider, root));
        }
        if (refreshes.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.allOf(refreshes.toArray(CompletableFuture[]::new)).thenApply(v -> refreshes.size());
    }

    private static List<Instance> knownInstances() {
        List<Instance> instances = new ArrayList<>();
        InstanceManager manager = InstanceManager.getInstance();
        instances.addAll(manager.getLocalInstances());
        for (RemoteHost host : manager.getRemoteHosts()) {
            instances.addAll(manager.getRemoteInstances(host));
        }
        return instances;
    }

    private static void bindEditor(EditorDecorationBinding binding) {
        if (binding == null || binding.editor() == null || binding.provider() == null || binding.workspaceRoot() == null) {
            return;
        }
        Path contentRoot = contentRootFor(binding.workspaceRoot(), binding.filePath());
        refresh(binding.instance(), binding.provider(), contentRoot);
        GlyphPreviewRenderer renderer = new GlyphPreviewRenderer(binding.instance(), binding.provider(), contentRoot, binding.filePath(), binding.language());
        binding.editor().setLineDecoration(new TextLineDecoration() {
            @Override
            public void draw(TextLineDecorationContext context) {
                renderer.drawEditor(context, mode());
            }

            @Override
            public boolean mouseClicked(TextLineDecorationClickContext context) {
                return renderer.openHoveredAsset(context.mouseX(), context.mouseY(), context.button());
            }
        });
    }

    private static Path contentRootFor(Path workspaceRoot, Path filePath) {
        if (filePath == null) {
            return workspaceRoot;
        }
        Path current = filePath.toAbsolutePath().normalize();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && name.toString().equalsIgnoreCase("Nexo")) {
                return current;
            }
            current = current.getParent();
        }
        current = filePath.toAbsolutePath().normalize();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && name.toString().equalsIgnoreCase("ItemsAdder")) {
                return current;
            }
            current = current.getParent();
        }
        return workspaceRoot;
    }
}
