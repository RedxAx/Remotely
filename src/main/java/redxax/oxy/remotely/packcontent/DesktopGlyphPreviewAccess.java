package redxax.oxy.remotely.packcontent;

import redxax.oxy.remotely.DesktopRemotelyPaths;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.Instance;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import restudio.rebase.ui.screens.editor.FileEditorScreen;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class DesktopGlyphPreviewAccess implements GlyphPreviewAccess {
    private final Instance instance;
    private final FileSystemProvider fileSystem;
    private final Path workspaceRoot;

    public DesktopGlyphPreviewAccess(Instance instance, FileSystemProvider fileSystem, Path workspaceRoot) {
        this.instance = instance;
        this.fileSystem = fileSystem;
        this.workspaceRoot = workspaceRoot;
    }

    @Override
    public Async<Void> refresh() {
        return JvmAsyncBridge.fromFuture(PackContentRegistry.get().refresh(instance, fileSystem, workspaceRoot));
    }

    @Override
    public List<Preview> resolveGlyphs(String text) {
        return PackContentRegistry.get().resolveGlyphs(instance, workspaceRoot, text).stream().map(DesktopGlyphPreviewAccess::preview).toList();
    }

    @Override
    public Optional<Preview> resolveGlyph(String providerId, String glyphId, Integer index) {
        return PackContentRegistry.get().resolveGlyph(instance, workspaceRoot, providerId, glyphId, index).map(DesktopGlyphPreviewAccess::preview);
    }

    @Override
    public Async<Image> loadImage(GlyphDefinition glyph) {
        return JvmAsyncBridge.fromFuture(PackContentRegistry.get().loadImage(instance, workspaceRoot, glyph))
                .thenApply(frame -> frame == null ? null : new GlyphPreviewAccess.Image(frame.image(), frame.width(), frame.height()));
    }

    @Override
    public Async<Image> loadImage(GlyphDefinition glyph, Integer index) {
        return JvmAsyncBridge.fromFuture(PackContentRegistry.get().loadImage(instance, workspaceRoot, glyph, index))
                .thenApply(frame -> frame == null ? null : new GlyphPreviewAccess.Image(frame.image(), frame.width(), frame.height()));
    }

    private static Preview preview(PackContentRegistry.ResolvedGlyphPreview preview) {
        return new Preview(preview.providerName(), preview.glyph(), preview.match(), preview.frames());
    }

    @Override
    public String sourceName(GlyphDefinition glyph) {
        Path source = glyph == null || glyph.sourceFile() == null ? null : Path.of(glyph.sourceFile());
        return source != null && source.getFileName() != null ? source.getFileName().toString() : "Unknown";
    }

    @Override
    public boolean openSource(GlyphDefinition glyph) {
        Path target = glyph == null || glyph.sourceFile() == null ? null : Path.of(glyph.sourceFile());
        if (target == null) {
            return false;
        }
        ScreenManager.getInstance().execute(() -> {
            Screen parent = ScreenManager.getInstance().getCurrentScreen();
            FileEditorScreen.canOpen(fileSystem, target).thenAccept(can -> ScreenManager.getInstance().execute(() -> {
                if (can) {
                    ScreenManager.getInstance().setScreen(new FileEditorScreen(parent, instance, fileSystem,
                            workspaceRoot, target, DesktopRemotelyPaths.appDir().resolve("data")));
                } else {
                    new Notification("Open Failed", sourceName(glyph), Notification.Type.ERROR);
                }
            }));
        });
        return true;
    }

    @Override
    public boolean openAsset(GlyphDefinition glyph) {
        GlyphAssetRef ref = glyph == null ? null : glyph.assetRef();
        Path target = ref == null || ref.resolvedPath() == null ? null : Path.of(ref.resolvedPath());
        if (target == null) {
            return false;
        }
        Path parent = target.getParent() == null ? target : target.getParent();
        ScreenManager.getInstance().execute(() -> ScreenManager.getInstance().setScreen(new FileExplorerScreen(
                ScreenManager.getInstance().getCurrentScreen(), instance, parent,
                DesktopRemotelyPaths.appDir().resolve("data"), false, fileSystem)));
        return true;
    }
}
