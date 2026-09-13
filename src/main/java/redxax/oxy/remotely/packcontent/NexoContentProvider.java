package redxax.oxy.remotely.packcontent;

import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.BrowserSafeState;
import redxax.oxy.remotely.util.TaskSchedulers;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import restudio.rescreen.platform.Async;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class NexoContentProvider extends AbstractPackContentProvider implements GlyphContentProvider, PackAssetProvider {
    private final NexoGlyphCatalog catalog = new NexoGlyphCatalog();
    private final Map<String, List<GlyphPreviewFrame>> frameVariants = BrowserSafeState.map();
    private final Map<String, Async<List<GlyphPreviewFrame>>> frameLoads = BrowserSafeState.map();
    private Path root;

    @Override
    public String id() {
        return "nexo";
    }

    @Override
    public String displayName() {
        return "Nexo";
    }

    @Override
    public Async<Optional<Path>> detectRoot(PackContentContext context) {
        Path primary = context.workspaceRoot().resolve("plugins").resolve("Nexo");
        return exists(context, primary.resolve("glyphs")).thenCompose(primaryExists -> {
            if (primaryExists) {
                return Async.completed(Optional.of(primary));
            }
            return exists(context, context.workspaceRoot().resolve("glyphs"))
                    .thenApply(rootExists -> rootExists ? Optional.of(context.workspaceRoot()) : Optional.empty());
        });
    }

    @Override
    public Async<Void> refresh(PackContentContext context) {
        root = context.providerRoot();
        catalog.clear();
        frameVariants.clear();
        frameLoads.clear();
        diagnostics.clear();
        Path glyphRoot = root.resolve("glyphs");
        return walk(context.fileSystem(), glyphRoot).thenCompose(paths -> {
            Async<Void> refresh = Async.completed(null);
            for (Path path : paths) {
                String name = path.getFileName() != null ? path.getFileName().toString().toLowerCase(Locale.ROOT) : "";
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    refresh = refresh.thenCompose(ignored -> JvmAsyncBridge.fromFuture(context.fileSystem().read(path)).thenCompose(content -> parseGlyphFile(context, path, content)).exceptionally(e -> {
                        diagnostics.add(new PackContentDiagnostic(id(), path.toString(), e.getMessage()));
                        return null;
                    }));
                }
            }
            return refresh;
        });
    }

    @Override
    public Map<String, GlyphDefinition> glyphs() {
        return catalog.glyphs();
    }

    @Override
    public List<GlyphTagMatch> parseGlyphTags(String text) {
        return catalog.parse(text);
    }

    GlyphDefinition materialized(GlyphDefinition glyph) {
        return catalog.materialized(glyph);
    }

    List<GlyphTagMatch> parseRawGlyphChars(String text, Iterable<GlyphDefinition> definitions, List<GlyphTagMatch> excluded) {
        return NexoGlyphText.parseRaw(id(), text, definitions, excluded);
    }

    @Override
    public Async<Optional<Path>> resolvePackAsset(PackContentContext context, String asset, boolean gif) {
        if (root == null || asset == null || asset.isBlank()) {
            return Async.completed(Optional.empty());
        }
        String normalized = asset.replace('\\', '/');
        String namespace = null;
        String value = normalized;
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            namespace = normalized.substring(0, colon);
            value = normalized.substring(colon + 1);
        }
        String ext = gif ? ".gif" : ".png";
        if (!value.toLowerCase(Locale.ROOT).endsWith(ext)) {
            value += ext;
        }
        String assetPath = value;
        List<String> namespaces = namespace != null ? List.of(namespace) : List.of("minecraft", "nexo");
        List<Path> candidates = new ArrayList<>();
        for (String ns : namespaces) {
            Path assetRoot = root.resolve("pack").resolve("assets").resolve(ns);
            candidates.add(assetRoot.resolve("textures").resolve(assetPath));
            if (assetPath.startsWith("textures/")) {
                candidates.add(assetRoot.resolve(assetPath));
            }
        }
        String explicitNamespace = namespace;
        return firstExisting(context.fileSystem(), candidates, 0).thenCompose(found -> {
            if (found.isPresent() || explicitNamespace != null) {
                return Async.completed(found);
            }
            Path assets = root.resolve("pack").resolve("assets");
            return JvmAsyncBridge.fromFuture(context.fileSystem().ls(assets)).thenCompose(entries -> {
                List<Path> discovered = new ArrayList<>();
                for (FileSystemProvider.FileEntry entry : entries) {
                    if (entry.isDirectory) {
                        discovered.add(entry.path.resolve("textures").resolve(assetPath));
                    }
                }
                return firstExisting(context.fileSystem(), discovered, 0);
            }).exceptionally(error -> Optional.empty());
        });
    }

    private Async<Optional<Path>> firstExisting(FileSystemProvider fileSystem, List<Path> candidates, int index) {
        if (index >= candidates.size()) {
            return Async.completed(Optional.empty());
        }
        Path candidate = candidates.get(index);
        return JvmAsyncBridge.fromFuture(fileSystem.exists(candidate)).exceptionally(error -> false).thenCompose(found -> found
                ? Async.completed(Optional.of(candidate))
                : firstExisting(fileSystem, candidates, index + 1));
    }

    private Async<Void> parseGlyphFile(PackContentContext context, Path source, String content) {
        try {
            Object loaded = loadYaml(content);
            if (!(loaded instanceof Map<?, ?> map)) {
                return Async.completed(null);
            }
            Async<Void> parsed = Async.completed(null);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String glyphId) || !(entry.getValue() instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> raw = normalizeMap(rawMap);
                parsed = parsed.thenCompose(ignored -> buildGlyph(context, source, glyphId, raw).thenAccept(catalog::put));
            }
            return parsed;
        } catch (Exception e) {
            diagnostics.add(new PackContentDiagnostic(id(), source.toString(), e.getMessage()));
            return Async.completed(null);
        }
    }

    private Object loadYaml(String content) {
        try {
            Class<?> yamlClass = Class.forName("redxax.oxy.remotely.libs.snakeyaml.Yaml");
            Object yaml = yamlClass.getConstructor().newInstance();
            return yamlClass.getMethod("load", String.class).invoke(yaml, content);
        } catch (Throwable ignored) {
            return loadSimpleGlyphYaml(content);
        }
    }

    private Map<String, Map<String, Object>> loadSimpleGlyphYaml(String content) {
        return NexoGlyphConfig.parse(content);
    }

    private Async<GlyphDefinition> buildGlyph(PackContentContext context, Path source, String glyphId, Map<String, Object> raw) {
        String texture = string(raw.get("texture"));
        String gif = string(raw.get("gif"));
        boolean isGif = gif != null && !gif.isBlank();
        String assetValue = isGif ? gif : texture;
        Async<Optional<Path>> resolved = assetValue != null && !assetValue.isBlank()
                ? resolvePackAsset(context, assetValue, isGif)
                : Async.completed(Optional.empty());
        return resolved.thenApply(path -> {
            GlyphAssetRef assetRef = assetValue != null && !assetValue.isBlank() ? new GlyphAssetRef(assetValue, isGif, path.map(Path::toString).orElse(null)) : null;
            GlyphDefinition pending = NexoGlyphCatalog.definition(glyphId, source.toString(), assetRef, raw);
            return pending.isReference() ? pending : pendingWithFrames(context, pending);
        });
    }

    private GlyphDefinition pendingWithFrames(PackContentContext context, GlyphDefinition glyph) {
        if (isRemoteProvider(context.fileSystem())) {
            int frameCount = glyph.frameCount() > 0 ? glyph.frameCount() : Math.max(1, glyph.rows() * glyph.columns());
            return new GlyphDefinition(glyph.providerId(), glyph.id(), glyph.sourceFile(), glyph.assetRef(), glyph.ascent(), glyph.height(), glyph.font(), glyph.rows(), glyph.columns(), glyph.reference(), glyph.index(), glyph.offset(), frameCount, glyph.raw(), List.of());
        }
        List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), glyph.rows(), glyph.columns(), null);
        int frameCount = glyph.frameCount() > 0 ? glyph.frameCount() : frames.size();
        return new GlyphDefinition(glyph.providerId(), glyph.id(), glyph.sourceFile(), glyph.assetRef(), glyph.ascent(), glyph.height(), glyph.font(), glyph.rows(), glyph.columns(), glyph.reference(), glyph.index(), glyph.offset(), frameCount, glyph.raw(), frames);
    }

    private boolean isRemoteProvider(FileSystemProvider provider) {
        String type = provider != null ? provider.getMetadata("type") : null;
        return type != null && !"LOCAL".equalsIgnoreCase(type);
    }

    public List<GlyphPreviewFrame> framesFor(PackContentContext context, GlyphDefinition glyph, Integer requestedIndex) {
        return framesFor(context, glyph, requestedIndex, true);
    }

    public List<GlyphPreviewFrame> framesFor(PackContentContext context, GlyphDefinition glyph, Integer requestedIndex, boolean allowLoad) {
        if (glyph == null) {
            return List.of();
        }
        if (glyph.isReference()) {
            GlyphDefinition referenced = catalog.get(glyph.reference());
            Integer index = glyph.index() != null ? glyph.index() : requestedIndex;
            return framesFor(context, referenced, index, allowLoad);
        }
        if (requestedIndex == null && !glyph.frames().isEmpty()) {
            return glyph.frames();
        }
        NexoGlyphCatalog.Grid grid = NexoGlyphCatalog.grid(glyph, requestedIndex);
        String key = glyph.id() + "|" + refKey(glyph.assetRef()) + "|" + (requestedIndex != null ? requestedIndex : "all");
        List<GlyphPreviewFrame> cached = frameVariants.get(key);
        if (cached != null) {
            return cached;
        }
        if (!allowLoad) {
            if (context != null && isRemoteProvider(context.fileSystem())) return List.of();
            frameLoads.computeIfAbsent(key, ignored -> AsyncTools.supply(TaskSchedulers.current(), () -> {
                List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), grid.rows(), grid.columns(), requestedIndex);
                frameVariants.put(key, frames);
                return frames;
            }).whenComplete((frames, e) -> frameLoads.remove(key)));
            return List.of();
        }
        List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), grid.rows(), grid.columns(), requestedIndex);
        frameVariants.put(key, frames);
        return frames;
    }

    private String refKey(GlyphAssetRef ref) {
        return ref != null && ref.resolvedPath() != null ? ref.resolvedPath() : "";
    }

    private List<GlyphPreviewFrame> loadFrames(PackContentContext context, GlyphAssetRef ref, int rows, int columns, Integer requestedIndex) {
        if (ref == null || ref.resolvedPath() == null) {
            return List.of();
        }
        try {
            Path local = PackContentAssetCache.get().localPath(context, Path.of(ref.resolvedPath()));
            if (local == null || !Files.exists(local)) {
                return List.of();
            }
            if (ref.gif()) {
                return readGifFrames(local);
            }
            BufferedImage image = ImageIO.read(local.toFile());
            if (image == null) {
                return List.of();
            }
            if (rows > 1 || columns > 1) {
                int total = Math.max(1, rows * columns);
                int index = requestedIndex != null ? Math.max(0, Math.min(total - 1, requestedIndex)) : 0;
                int cellW = Math.max(1, image.getWidth() / Math.max(1, columns));
                int cellH = Math.max(1, image.getHeight() / Math.max(1, rows));
                int x = index % Math.max(1, columns);
                int y = index / Math.max(1, columns);
                return frameList(image.getSubimage(x * cellW, y * cellH, Math.min(cellW, image.getWidth() - x * cellW), Math.min(cellH, image.getHeight() - y * cellH)), 100);
            }
            return frameList(image, 100);
        } catch (Exception e) {
            diagnostics.add(new PackContentDiagnostic(id(), ref.resolvedPath(), e.getMessage()));
            return List.of();
        }
    }

    private List<GlyphPreviewFrame> readGifFrames(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                BufferedImage single = ImageIO.read(new ByteArrayInputStream(bytes));
                return frameList(single, 100);
            }
            ImageReader reader = readers.next();
            reader.setInput(stream);
            int count = reader.getNumImages(true);
            List<GlyphPreviewFrame> frames = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                GlyphPreviewFrame frame = DesktopGlyphPreviewFrame.of(reader.read(i), gifDelay(reader.getImageMetadata(i)));
                if (frame != null) {
                    frames.add(frame);
                }
            }
            reader.dispose();
            return frames;
        }
    }

    private List<GlyphPreviewFrame> frameList(BufferedImage image, int delayMs) {
        GlyphPreviewFrame frame = DesktopGlyphPreviewFrame.of(image, delayMs);
        return frame != null ? List.of(frame) : List.of();
    }

    private int gifDelay(IIOMetadata metadata) {
        if (metadata == null) {
            return 100;
        }
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
            Node graphics = findNode(root, "GraphicControlExtension");
            if (graphics == null) {
                return 100;
            }
            NamedNodeMap attributes = graphics.getAttributes();
            Node delay = attributes != null ? attributes.getNamedItem("delayTime") : null;
            if (delay == null) {
                return 100;
            }
            return Math.max(20, Integer.parseInt(delay.getNodeValue()) * 10);
        } catch (Exception ignored) {
            return 100;
        }
    }

    private Node findNode(Node node, String name) {
        if (node == null) {
            return null;
        }
        if (name.equals(node.getNodeName())) {
            return node;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Map<String, Object> normalizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

}
