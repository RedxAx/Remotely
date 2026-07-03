package redxax.oxy.remotely.packcontent;

import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.util.Executors;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NexoContentProvider extends AbstractPackContentProvider implements GlyphContentProvider, PackAssetProvider {
    private static final Pattern GLYPH_TAG = Pattern.compile("<(glyph|g):([^>]+)>");
    private static final Pattern SHIFT_TAG = Pattern.compile("<shift:([-+]?\\d+)>");
    private final Map<String, GlyphDefinition> glyphs = new LinkedHashMap<>();
    private final Map<String, List<GlyphPreviewFrame>> frameVariants = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<GlyphPreviewFrame>>> frameLoads = new ConcurrentHashMap<>();
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
    public Optional<Path> detectRoot(PackContentContext context) {
        Path primary = context.workspaceRoot().resolve("plugins").resolve("Nexo");
        if (exists(context, primary.resolve("glyphs")).join() && exists(context, primary.resolve("pack")).join()) {
            return Optional.of(primary);
        }
        if (exists(context, context.workspaceRoot().resolve("glyphs")).join() && exists(context, context.workspaceRoot().resolve("pack")).join()) {
            return Optional.of(context.workspaceRoot());
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Void> refresh(PackContentContext context) {
        root = context.providerRoot();
        glyphs.clear();
        frameVariants.clear();
        frameLoads.clear();
        diagnostics.clear();
        Path glyphRoot = root.resolve("glyphs");
        return walk(context.fileSystem(), glyphRoot).thenCompose(paths -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Path path : paths) {
                String name = path.getFileName() != null ? path.getFileName().toString().toLowerCase(Locale.ROOT) : "";
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    futures.add(context.fileSystem().read(path).thenAccept(content -> parseGlyphFile(context, path, content)).exceptionally(e -> {
                        diagnostics.add(new PackContentDiagnostic(id(), path, e.getMessage()));
                        return null;
                    }));
                }
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
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
            IndexRange range = parseIndexRange(parts);
            int shift = adjacentShift(text, matcher.start(), matcher.end());
            matches.add(new GlyphTagMatch(id(), parts[0], matcher.start(), matcher.end(), range.start(), range.end(), shift));
        }
        return matches;
    }

    @Override
    public Optional<Path> resolvePackAsset(PackContentContext context, String asset, boolean gif) {
        if (root == null || asset == null || asset.isBlank()) {
            return Optional.empty();
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
        List<String> namespaces = namespace != null ? List.of(namespace) : List.of("minecraft", "nexo");
        for (String ns : namespaces) {
            Path candidate = root.resolve("pack").resolve("assets").resolve(ns).resolve("textures").resolve(value);
            if (context.fileSystem().exists(candidate).exceptionally(e -> false).join()) {
                return Optional.of(candidate);
            }
        }
        if (namespace == null) {
            Path assets = root.resolve("pack").resolve("assets");
            for (String ns : namespacesFromAssets(context.fileSystem(), assets)) {
                Path candidate = assets.resolve(ns).resolve("textures").resolve(value);
                if (context.fileSystem().exists(candidate).exceptionally(e -> false).join()) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private void parseGlyphFile(PackContentContext context, Path source, String content) {
        try {
            Object loaded = loadYaml(content);
            if (!(loaded instanceof Map<?, ?> map)) {
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String glyphId) || !(entry.getValue() instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> raw = normalizeMap(rawMap);
                GlyphDefinition glyph = buildGlyph(context, source, glyphId, raw);
                glyphs.put(glyphId, glyph);
            }
        } catch (Exception e) {
            diagnostics.add(new PackContentDiagnostic(id(), source, e.getMessage()));
        }
    }

    private Object loadYaml(String content) {
        try {
            Class<?> yamlClass = Class.forName("org.yaml.snakeyaml.Yaml");
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

    private GlyphDefinition buildGlyph(PackContentContext context, Path source, String glyphId, Map<String, Object> raw) {
        String texture = string(raw.get("texture"));
        String gif = string(raw.get("gif"));
        boolean isGif = gif != null && !gif.isBlank();
        String assetValue = isGif ? gif : texture;
        GlyphAssetRef assetRef = null;
        if (assetValue != null && !assetValue.isBlank()) {
            assetRef = new GlyphAssetRef(assetValue, isGif, resolvePackAsset(context, assetValue, isGif).orElse(null));
        }
        int rows = intValue(raw.get("rows"), 1);
        int columns = intValue(raw.get("columns"), 1);
        int frameCount = intValue(raw.get("frame_count"), 0);
        GlyphDefinition pending = new GlyphDefinition(id(), glyphId, source, assetRef, intValue(raw.get("ascent"), 0), intValue(raw.get("height"), 0), string(raw.get("font")), rows, columns, string(raw.get("reference")), zeroBasedIndex(raw.get("index")), intValue(raw.get("offset"), 0), frameCount, raw, List.of());
        return pending.isReference() ? pending : pendingWithFrames(context, pending);
    }

    private GlyphDefinition pendingWithFrames(PackContentContext context, GlyphDefinition glyph) {
        List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), glyph.rows(), glyph.columns(), null);
        int frameCount = glyph.frameCount() > 0 ? glyph.frameCount() : frames.size();
        return new GlyphDefinition(glyph.providerId(), glyph.id(), glyph.sourceFile(), glyph.assetRef(), glyph.ascent(), glyph.height(), glyph.font(), glyph.rows(), glyph.columns(), glyph.reference(), glyph.index(), glyph.offset(), frameCount, glyph.raw(), frames);
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
        if (requestedIndex == null) {
            return glyph.frames();
        }
        String key = glyph.id() + "|" + refKey(glyph.assetRef()) + "|" + requestedIndex;
        List<GlyphPreviewFrame> cached = frameVariants.get(key);
        if (cached != null) {
            return cached;
        }
        if (!allowLoad) {
            frameLoads.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
                List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), glyph.rows(), glyph.columns(), requestedIndex);
                frameVariants.put(key, frames);
                return frames;
            }, Executors.IO).whenComplete((frames, e) -> frameLoads.remove(key)));
            return List.of();
        }
        List<GlyphPreviewFrame> frames = loadFrames(context, glyph.assetRef(), glyph.rows(), glyph.columns(), requestedIndex);
        frameVariants.put(key, frames);
        return frames;
    }

    private String refKey(GlyphAssetRef ref) {
        return ref != null && ref.resolvedPath() != null ? ref.resolvedPath().toString() : "";
    }

    private List<GlyphPreviewFrame> loadFrames(PackContentContext context, GlyphAssetRef ref, int rows, int columns, Integer requestedIndex) {
        if (ref == null || ref.resolvedPath() == null) {
            return List.of();
        }
        try {
            Path local = PackContentAssetCache.get().localPath(context, ref.resolvedPath());
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
                GlyphPreviewFrame frame = GlyphPreviewFrame.of(reader.read(i), gifDelay(reader.getImageMetadata(i)));
                if (frame != null) {
                    frames.add(frame);
                }
            }
            reader.dispose();
            return frames;
        }
    }

    private List<GlyphPreviewFrame> frameList(BufferedImage image, int delayMs) {
        GlyphPreviewFrame frame = GlyphPreviewFrame.of(image, delayMs);
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

    private List<String> namespacesFromAssets(FileSystemProvider fs, Path assets) {
        return fs.ls(assets).thenApply(entries -> entries.stream().filter(e -> e.isDirectory).map(e -> e.path.getFileName().toString()).toList()).exceptionally(e -> List.of()).join();
    }

    private IndexRange parseIndexRange(String[] parts) {
        Integer start = null;
        Integer end = null;
        for (int i = 1; i < parts.length; i++) {
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
