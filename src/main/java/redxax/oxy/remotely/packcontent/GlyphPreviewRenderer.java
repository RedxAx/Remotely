package redxax.oxy.remotely.packcontent;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.config.Config;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.api.unified.adapter.UnifiedFileSystemProvider;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.screens.editor.FileEditorScreen;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.widgets.TerminalTextDecoration;
import restudio.rebase.ui.widgets.editor.TextLineDecoration;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.text.FontRegistry;
import restudio.rescreen.text.StyledText;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static restudio.rescreen.render.TextRenderer.tr;

public class GlyphPreviewRenderer {
    private static final int HOVER_SIZE = 48;
    private static final long HOVER_TARGET_TTL_MS = 1200L;
    private static final long REFRESH_INTERVAL_MS = 2500L;
    private static final Pattern YAML_GLYPH_ID = Pattern.compile("^([A-Za-z0-9_.-]+):\\s*$");
    private final Instance instance;
    private final FileSystemProvider fileSystem;
    private final Path workspaceRoot;
    private final Path filePath;
    private final String language;
    private final PackContentRegistry registry;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private HoverTarget hoverTarget;
    private long hoverTargetSeenMs;
    private long lastRefreshMs;

    public GlyphPreviewRenderer(Instance instance, Path workspaceRoot) {
        this(instance, null, workspaceRoot, null, null);
    }

    public GlyphPreviewRenderer(Instance instance, FileSystemProvider fileSystem, Path workspaceRoot, Path filePath, String language) {
        this.instance = instance;
        this.fileSystem = fileSystem;
        this.workspaceRoot = workspaceRoot;
        this.filePath = filePath;
        this.language = language;
        this.registry = PackContentRegistry.get();
    }

    public void drawEditor(TextLineDecoration.TextLineDecorationContext context, GlyphPreviewMode mode) {
        refreshIfDue();
        draw(context.drawContext(), context.lineText(), context.drawX(), context.drawY(), context.lineHeight(), context.monospace() ? context.charWidth() : -1, context.mouseX(), context.mouseY(), mode);
        drawGlyphConfigPreview(context, mode);
    }

    public void drawTerminal(TerminalTextDecoration.TerminalTextDecorationContext context, GlyphPreviewMode mode) {
        draw(context.drawContext(), context.text(), context.segmentX(), context.segmentY(), context.lineHeight(), context.charWidth(), context.mouseX(), context.mouseY(), mode);
    }

    public boolean replaceTerminal(TerminalTextDecoration.TerminalTextDecorationContext context, GlyphPreviewMode mode) {
        expireHoverTarget();
        if (mode == null || mode == GlyphPreviewMode.OFF || context.text() == null || context.text().isEmpty()) {
            return false;
        }
        if (!mode.inline()) {
            draw(context.drawContext(), context.text(), context.segmentX(), context.segmentY(), context.lineHeight(), context.charWidth(), context.mouseX(), context.mouseY(), mode);
            return false;
        }
        List<PackContentRegistry.ResolvedGlyphPreview> previews = registry.resolveGlyphs(instance, workspaceRoot, context.text());
        if (previews.isEmpty()) {
            return false;
        }
        int rawCursor = 0;
        int visualX = context.segmentX();
        PackContentRegistry.ResolvedGlyphPreview hovered = null;
        int hoveredX = 0;
        int hoveredY = 0;
        for (PackContentRegistry.ResolvedGlyphPreview preview : previews) {
            int start = Math.max(0, Math.min(preview.match().start(), context.text().length()));
            int end = Math.max(start, Math.min(preview.match().end(), context.text().length()));
            if (start > rawCursor) {
                String plain = context.text().substring(rawCursor, start);
                drawTerminalPlain(context, plain, visualX);
                visualX += plain.length() * context.charWidth();
            }
            int tokenX = visualX + preview.match().shift();
            BufferedImage image = registry.currentFrame(preview);
            int imageW = Math.max(context.lineHeight(), context.charWidth());
            if (mode.inline() && image != null) {
                int h = Math.max(8, context.lineHeight() - 2);
                imageW = Math.max(context.charWidth(), Math.round((float) image.getWidth() * h / Math.max(1, image.getHeight())));
                context.drawContext().drawPixelArt(image, tokenX, context.segmentY() - 1, imageW, h);
            }
            int tokenW = Math.max(context.charWidth(), imageW);
            if (mode.hover() && context.mouseX() >= tokenX && context.mouseX() <= tokenX + tokenW && context.mouseY() >= context.segmentY() - 2 && context.mouseY() <= context.segmentY() + context.lineHeight()) {
                hovered = preview;
                hoveredX = tokenX;
                hoveredY = context.segmentY();
                rememberHoverTarget(preview, tokenX - 4, context.segmentY() - 4, tokenX + tokenW + 4, context.segmentY() + context.lineHeight() + 4);
            }
            visualX += tokenW + Math.max(1, context.charWidth() / 3);
            rawCursor = end;
        }
        if (rawCursor < context.text().length()) {
            String plain = context.text().substring(rawCursor);
            drawTerminalPlain(context, plain, visualX);
        }
        if (hovered != null) {
            drawHover(context.drawContext(), hovered, context.mouseX(), context.mouseY(), hoveredX, hoveredY);
        }
        return true;
    }

    private void drawTerminalPlain(TerminalTextDecoration.TerminalTextDecorationContext context, String text, int x) {
        if (text == null || text.isEmpty()) {
            return;
        }
        context.drawContext().drawStyledText(new StyledText(text, context.foregroundColor(), FontRegistry.MONO_FONT), x, context.segmentY(), 0, false);
    }

    public boolean openHoveredAsset(double mouseX, double mouseY, int button) {
        expireHoverTarget();
        if (button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || hoverTarget == null || mouseX < hoverTarget.x1() || mouseX > hoverTarget.x2() || mouseY < hoverTarget.y1() || mouseY > hoverTarget.y2()) {
            return false;
        }
        if (filePath == null) {
            Path source = hoverTarget.preview().glyph().sourceFile();
            if (source == null) {
                return false;
            }
            openGlyphSourceInEditor(source);
        } else {
            GlyphAssetRef ref = hoverTarget.preview().glyph().assetRef();
            Path asset = ref != null ? ref.resolvedPath() : null;
            if (asset == null) {
                return false;
            }
            openAssetPathInExplorer(asset);
        }
        return true;
    }

    private void draw(IDrawContext ctx, String text, int drawX, int drawY, int lineHeight, int charWidth, int mouseX, int mouseY, GlyphPreviewMode mode) {
        expireHoverTarget();
        if (mode == null || mode == GlyphPreviewMode.OFF || text == null || text.isEmpty()) {
            return;
        }
        List<PackContentRegistry.ResolvedGlyphPreview> previews = registry.resolveGlyphs(instance, workspaceRoot, text);
        PackContentRegistry.ResolvedGlyphPreview hovered = null;
        int hoveredX = 0;
        int hoveredY = 0;
        for (PackContentRegistry.ResolvedGlyphPreview preview : previews) {
            int tokenX = drawX + textWidth(text, 0, preview.match().start(), charWidth) + preview.match().shift();
            int tokenW = Math.max(lineHeight, textWidth(text, preview.match().start(), preview.match().end(), charWidth));
            if (mode.inline()) {
                BufferedImage image = registry.currentFrame(preview);
                if (image != null) {
                    int h = Math.max(8, lineHeight - 2);
                    int w = Math.max(8, Math.round((float) image.getWidth() * h / Math.max(1, image.getHeight())));
                    ctx.drawPixelArt(image, tokenX, drawY - 1, w, h);
                }
            }
            if (mode.hover() && mouseX >= tokenX && mouseX <= tokenX + tokenW && mouseY >= drawY - 2 && mouseY <= drawY + lineHeight) {
                hovered = preview;
                hoveredX = tokenX;
                hoveredY = drawY;
                rememberHoverTarget(preview, tokenX - 4, drawY - 4, tokenX + tokenW + 4, drawY + lineHeight + 4);
            }
        }
        if (hovered != null) {
            drawHover(ctx, hovered, mouseX, mouseY, hoveredX, hoveredY);
        }
    }

    private void drawGlyphConfigPreview(TextLineDecoration.TextLineDecorationContext context, GlyphPreviewMode mode) {
        if (mode == null || mode == GlyphPreviewMode.OFF || !isGlyphYaml()) {
            return;
        }
        Matcher matcher = YAML_GLYPH_ID.matcher(context.lineText());
        if (!matcher.matches()) {
            return;
        }
        String glyphId = matcher.group(1);
        Optional<PackContentRegistry.ResolvedGlyphPreview> preview = registry.resolveGlyph(instance, workspaceRoot, "nexo", glyphId, null);
        if (preview.isEmpty()) {
            return;
        }
        BufferedImage image = registry.currentFrame(preview.get());
        if (image == null) {
            return;
        }
        int size = Math.max(8, context.lineHeight() - 2);
        int x = context.drawX() + textWidth(context.lineText(), 0, context.lineText().length(), context.monospace() ? context.charWidth() : -1) + 8;
        int y = context.drawY() - 1;
        if (mode.inline()) {
            context.drawContext().drawPixelArt(image, x, y, size, size);
        }
        if (mode.hover() && context.mouseX() >= x && context.mouseX() <= x + size && context.mouseY() >= y && context.mouseY() <= y + size) {
            rememberHoverTarget(preview.get(), x - 4, y - 4, x + size + 4, y + size + 4);
            drawHover(context.drawContext(), preview.get(), context.mouseX(), context.mouseY(), x, y);
        }
    }

    private void rememberHoverTarget(PackContentRegistry.ResolvedGlyphPreview preview, int x1, int y1, int x2, int y2) {
        hoverTarget = new HoverTarget(preview, x1, y1, x2, y2);
        hoverTargetSeenMs = System.currentTimeMillis();
    }

    private void expireHoverTarget() {
        if (hoverTarget != null && System.currentTimeMillis() - hoverTargetSeenMs > HOVER_TARGET_TTL_MS) {
            hoverTarget = null;
        }
    }

    private void openGlyphSourceInEditor(Path path) {
        ScreenManager.getInstance().execute(() -> {
            try {
                if (path == null) {
                    return;
                }
                Path target = normalize(path);
                FileSystemProvider provider = openProvider();
                Screen parent = ScreenManager.getInstance().getCurrentScreen();
                if (provider == null) {
                    return;
                }
                Path root = workspaceRoot != null ? workspaceRoot : target.getParent();
                FileEditorScreen.canOpen(provider, target).thenAccept(can -> ScreenManager.getInstance().execute(() -> {
                    if (can) {
                        ScreenManager.getInstance().setScreen(new FileEditorScreen(parent, instance, provider, root, target, Config.remotelyDir.resolve("data")));
                    } else {
                        new Notification("Open Failed", target.getFileName() != null ? target.getFileName().toString() : target.toString(), Notification.Type.ERROR);
                    }
                }));
            } catch (Exception e) {
                new Notification("Open Failed", e.getMessage(), Notification.Type.ERROR);
            }
        });
    }

    private void openAssetPathInExplorer(Path path) {
        ScreenManager.getInstance().execute(() -> {
            try {
                if (path == null) {
                    return;
                }
                Path target = normalize(path);
                FileSystemProvider provider = openProvider();
                if (provider == null) {
                    return;
                }
                Path explorerPath = target.getParent();
                if (explorerPath == null) {
                    explorerPath = target;
                }
                ScreenManager.getInstance().setScreen(new FileExplorerScreen(ScreenManager.getInstance().getCurrentScreen(), instance, explorerPath, Config.remotelyDir.resolve("data"), false, provider));
            } catch (Exception e) {
                new Notification("Open Failed", e.getMessage(), Notification.Type.ERROR);
            }
        });
    }

    private FileSystemProvider openProvider() {
        if (fileSystem != null) {
            return fileSystem;
        }
        if (instance != null && instance.getBackend() != null) {
            return new UnifiedFileSystemProvider(InstanceApi.of(instance).files());
        }
        return null;
    }

    private Path normalize(Path path) {
        try {
            return path.toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return path;
        }
    }

    private boolean isGlyphYaml() {
        if (filePath == null) {
            return false;
        }
        String name = filePath.getFileName() != null ? filePath.getFileName().toString().toLowerCase() : "";
        if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
            return false;
        }
        String normalized = filePath.toString().replace('\\', '/').toLowerCase();
        return "yaml".equals(language) && normalized.contains("/glyphs/");
    }

    private void refreshIfDue() {
        if (fileSystem == null || workspaceRoot == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_INTERVAL_MS || !refreshing.compareAndSet(false, true)) {
            return;
        }
        lastRefreshMs = now;
        registry.refresh(instance, fileSystem, workspaceRoot).whenComplete((v, e) -> refreshing.set(false));
    }

    private void drawHover(IDrawContext ctx, PackContentRegistry.ResolvedGlyphPreview preview, int mouseX, int mouseY, int tokenX, int tokenY) {
        BufferedImage image = registry.currentFrame(preview);
        if (image == null) {
            return;
        }
        GlyphDefinition glyph = preview.glyph();
        List<String> lines = Stream.of(
                preview.providerName() + " " + glyph.id(),
                "Source " + fileName(glyph.sourceFile()),
                "Size " + image.getWidth() + "x" + image.getHeight(),
                "Ascent " + glyph.ascent() + " Height " + glyph.height(),
                "Font " + (glyph.font() == null || glyph.font().isBlank() ? "Default" : glyph.font()),
                glyph.isGif() ? "Frames " + Math.max(1, glyph.frameCount()) : ""
        ).filter(s -> !s.isBlank()).toList();
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, tr.getWidth(line));
        }
        int padding = 6;
        int lineHeight = ITextRenderer.fontHeight + 2;
        int width = Math.max(180, HOVER_SIZE + textWidth + padding * 3);
        int height = Math.max(HOVER_SIZE + padding * 2, lines.size() * lineHeight + padding * 2);
        int x = mouseX + 14;
        int y = mouseY + 14;
        if (x + width > tokenX + 420) {
            x = Math.max(4, mouseX - width - 14);
        }
        int bg = ThemeManager.getColor(ThemeColor.innerBackground);
        int border = ThemeManager.getColor(ThemeColor.innerBorder);
        int text = ThemeManager.getColor(ThemeColor.text);
        int secondary = ThemeManager.getColor(ThemeColor.textDark);
        ctx.fill(x, y, x + width, y + height, bg);
        ctx.fillBorder(x, y, x + width, y + height, 1, border);
        ctx.drawPixelArt(image, x + padding, y + padding, HOVER_SIZE, HOVER_SIZE);
        int tx = x + HOVER_SIZE + padding * 2;
        int ty = y + padding;
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawText(lines.get(i), tx, ty, i == 0 ? text : secondary, false);
            ty += lineHeight;
        }
    }

    private int textWidth(String text, int start, int end, int charWidth) {
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        if (charWidth > 0) {
            return (safeEnd - safeStart) * charWidth;
        }
        return tr.getWidth(text.substring(safeStart, safeEnd));
    }

    private String fileName(Path path) {
        return path != null && path.getFileName() != null ? path.getFileName().toString() : "Unknown";
    }

    private record HoverTarget(PackContentRegistry.ResolvedGlyphPreview preview, int x1, int y1, int x2, int y2) {}
}
