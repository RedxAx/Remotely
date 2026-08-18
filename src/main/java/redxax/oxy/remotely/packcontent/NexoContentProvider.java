package redxax.oxy.remotely.packcontent;

import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.util.AsyncTools;

import redxax.oxy.remotely.util.BrowserSafeState;

import restudio.rebase.backend.FileSystemProvider;


import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NexoContentProvider extends AbstractPackContentProvider implements GlyphContentProvider, PackAssetProvider {
    private static final Pattern GLYPH_TAG = Pattern.compile("<(glyph|g):([^>]+)>");
    private static final Pattern SHIFT_TAG = Pattern.compile("<shift:([-+]?\\d+)>");
    private final Map<String, GlyphDefinition> glyphs = new LinkedHashMap<>();
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
        glyphs.clear();
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
        return Map.copyOf(glyphs);
    }

    @Override
    public List<GlyphTagMatch> parseGlyphTags(String text) {
        List<GlyphTagMatch> matches = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return matches;
        }
        Matcher matcher = GLYPH_TAG.matcher(text);
        while (matcher.find()) {
            String[] parts = matcher.group(2).split(":");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            String glyphId = parts[0];
            int optionStart = 1;
            if (parts.length > 1 && (parts[0].equalsIgnoreCase(id()) || parts[0].equalsIgnoreCase("glyph"))) {
                glyphId = parts[1];
                optionStart = 2;
            }
            IndexRange range = parseIndexRange(parts, optionStart);
            int shift = adjacentShift(text, matcher.start(), matcher.end());
            matches.add(new GlyphTagMatch(id(), glyphId, matcher.start(), matcher.end(), range.start(), range.end(), shift));
        }
        return matches;
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
                parsed = parsed.thenCompose(ignored -> buildGlyph(context, source, glyphId, raw).thenAccept(glyph -> glyphs.put(glyphId, glyph)));
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
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        String currentGlyph = null;
        for (String line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            boolean topLevel = !line.startsWith(" ") && !line.startsWith("\t");
            if (topLevel) {
                if (trimmed.endsWith(":")) {
                    currentGlyph = trimmed.substring(0, trimmed.length() - 1).trim();
                    result.putIfAbsent(currentGlyph, new LinkedHashMap<>());
                } else {
                    currentGlyph = null;
                }
                continue;
            }
            if (currentGlyph == null) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            Object parsed = value.matches("-?\\d+") ? Integer.parseInt(value) : value;
            result.get(currentGlyph).put(key, parsed);
        }
        return result;
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
            int rows = intValue(raw.get("rows"), 1);
            int columns = intValue(raw.get("columns"), 1);
            int frameCount = intValue(raw.get("frame_count"), 0);
            GlyphDefinition pending = new GlyphDefinition(id(), glyphId, source.toString(), assetRef, intValue(raw.get("ascent"), 0), intValue(raw.get("height"), 0), string(raw.get("font")), rows, columns, string(raw.get("reference")), zeroBasedIndex(raw.get("index")), intValue(raw.get("offset"), 0), frameCount, raw, List.of());
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
            GlyphDefinition referenced = glyphs.get(glyph.reference());
            Integer index = glyph.index() != null ? glyph.index() : requestedIndex;
            return framesFor(context, referenced, index, allowLoad);
        }
        if (requestedIndex == null && !glyph.frames().isEmpty()) {
            return glyph.frames();
        }
        String key = glyph.id() + "|" + refKey(glyph.assetRef()) + "|" + (requestedIndex != null ? requestedIndex : "all");
        List<GlyphPreviewFrame> cached = frameVariants.get(key);
        if (cached != null) {
            return cached;
        }
        if (!allowLoad) {
            if (context != null && isRemoteProvider(context.fileSystem())) return List.of();
            frameLoads.computeIfAbsent(key, ignored -> AsyncTools.supply(TaskSchedulers.current(), () -> {
                List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), glyph.rows(), glyph.columns(), requestedIndex);
                frameVariants.put(key, frames);
                return frames;
            }).whenComplete((frames, e) -> frameLoads.remove(key)));
            return List.of();
        }
        List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), glyph.rows(), glyph.columns(), requestedIndex);
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

    private IndexRange parseIndexRange(String[] parts, int startIndex) {
        Integer start = null;
        Integer end = null;
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.equalsIgnoreCase("colorable") || part.equalsIgnoreCase("c") || part.equalsIgnoreCase("shadow") || part.equalsIgnoreCase("s")) {
                continue;
            }
            if (part.matches("\\d+\\.\\.\\d+")) {
                String[] range = part.split("\\.\\.");
                start = Math.max(0, Integer.parseInt(range[0]) - 1);
                end = Math.max(start, Integer.parseInt(range[1]) - 1);
                break;
            }
            if (part.matches("\\d+")) {
                start = Math.max(0, Integer.parseInt(part) - 1);
                end = start;
                break;
            }
        }
        return new IndexRange(start, end);
    }

    private int adjacentShift(String text, int start, int end) {
        Matcher before = SHIFT_TAG.matcher(text.substring(0, start));
        int shift = 0;
        while (before.find()) {
            if (before.end() == start) {
                shift += Integer.parseInt(before.group(1));
            }
        }
        Matcher after = SHIFT_TAG.matcher(text.substring(end));
        if (after.find() && after.start() == 0) {
            shift += Integer.parseInt(after.group(1));
        }
        return shift;
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

    private int intValue(Object value, int fallback) {
        Integer parsed = optionalInt(value);
        return parsed != null ? parsed : fallback;
    }

    private Integer optionalInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer zeroBasedIndex(Object value) {
        Integer parsed = optionalInt(value);
        return parsed != null ? Math.max(0, parsed - 1) : null;
    }

    private record IndexRange(Integer start, Integer end) {
    }
}
