package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.RemotelyServerApi;
import redxax.oxy.remotely.packcontent.GlyphAssetRef;
import redxax.oxy.remotely.packcontent.GlyphDefinition;
import redxax.oxy.remotely.packcontent.GlyphPreviewAccess;
import redxax.oxy.remotely.packcontent.GlyphTagMatch;
import redxax.oxy.remotely.packcontent.NexoGlyphCatalog;
import redxax.oxy.remotely.packcontent.NexoGlyphConfig;
import redxax.oxy.remotely.ui.server.ServerUiCapabilityProvider;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.platform.Async;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class BrowserGlyphPreviewAccess implements GlyphPreviewAccess {
    private static final String PROVIDER_ID = "nexo";
    private static final long REFRESH_INTERVAL_MS = 2_500L;
    private final RemotelyServerApi api;
    private final ServerUiCapabilityProvider files;
    private final ServerModels.ClientServerView server;
    private final NexoGlyphCatalog catalog = new NexoGlyphCatalog();
    private Async<Void> activeRefresh;
    private long refreshedAt;
    private long generation = 1L;
    private boolean closed;

    BrowserGlyphPreviewAccess(RemotelyServerApi api, ServerUiCapabilityProvider files, ServerModels.ClientServerView server) {
        this.api = api;
        this.files = files;
        this.server = server;
    }

    @Override
    public Async<Void> refresh() {
        if (closed) return Async.completed(null);
        if (activeRefresh != null) return activeRefresh;
        long now = System.currentTimeMillis();
        if (refreshedAt > 0 && now - refreshedAt < REFRESH_INTERVAL_MS) return Async.completed(null);
        long refreshGeneration = generation;
        Async<Void> pending = Async.pending();
        activeRefresh = pending;
        refreshCatalog(refreshGeneration).whenComplete((ignored, failure) -> {
            activeRefresh = null;
            if (failure == null && !closed && generation == refreshGeneration) {
                refreshedAt = System.currentTimeMillis();
                pending.complete(null);
            } else {
                if (failure == null || closed || generation != refreshGeneration) pending.complete(null);
                else pending.completeExceptionally(failure);
            }
        });
        return pending;
    }

    void close() {
        closed = true;
        generation++;
        catalog.clear();
    }

    private Async<Void> refreshCatalog(long refreshGeneration) {
        return detectRoot().thenCompose(detected -> {
            if (detected == null) {
                if (!closed && generation == refreshGeneration) catalog.clear();
                return Async.completed(null);
            }
            return walk(join(detected, "glyphs")).thenCompose(paths -> {
                Map<String, GlyphDefinition> next = new LinkedHashMap<>();
                Async<Void> load = Async.completed(null);
                for (String path : paths) {
                    String lower = path.toLowerCase(Locale.ROOT);
                    if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) continue;
                    load = load.thenCompose(ignored -> files.readFile(server, path)
                            .thenCompose(content -> parse(path, content, detected, next))
                            .exceptionally(failure -> null));
                }
                return load.thenRun(() -> {
                    if (!closed && generation == refreshGeneration) {
                        catalog.replace(next);
                    }
                });
            });
        });
    }

    @Override
    public List<Preview> resolveGlyphs(String text) {
        List<Preview> result = new ArrayList<>();
        for (GlyphTagMatch match : catalog.parse(text)) {
            GlyphDefinition preview = catalog.materialized(match.glyphId());
            if (preview != null) result.add(new Preview("Nexo", preview, selectedMatch(match, preview), List.of()));
        }
        return result;
    }

    @Override
    public Optional<Preview> resolveGlyph(String providerId, String glyphId, Integer index) {
        if (providerId != null && !PROVIDER_ID.equalsIgnoreCase(providerId)) return Optional.empty();
        GlyphDefinition glyph = catalog.materialized(glyphId);
        if (glyph == null) return Optional.empty();
        Integer selected = index != null ? index : glyph.index();
        return Optional.of(new Preview("Nexo", glyph,
                new GlyphTagMatch(PROVIDER_ID, glyphId, 0, glyphId.length(), selected, selected, 0), List.of()));
    }

    private GlyphTagMatch selectedMatch(GlyphTagMatch match, GlyphDefinition glyph) {
        if (match.indexStart() != null || glyph.index() == null) return match;
        return new GlyphTagMatch(match.providerId(), match.glyphId(), match.start(), match.end(), glyph.index(), glyph.index(), match.shift());
    }

    @Override
    public Async<Image> loadImage(GlyphDefinition glyph) {
        return loadImage(glyph, null);
    }

    @Override
    public Async<Image> loadImage(GlyphDefinition glyph, Integer index) {
        Integer selectedIndex = index != null ? index : glyph == null ? null : glyph.index();
        GlyphDefinition target = catalog.resolved(glyph);
        GlyphAssetRef asset = target == null ? null : target.assetRef();
        if (asset == null || asset.resolvedPath() == null || asset.resolvedPath().isBlank()) return Async.completed(null);
        return api.getFileDownloadUrl(serverId(), asset.resolvedPath()).thenApply(url -> image(target, selectedIndex, url));
    }

    @Override
    public String sourceName(GlyphDefinition glyph) {
        String source = glyph == null ? "" : glyph.sourceFile();
        int separator = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        return separator >= 0 ? source.substring(separator + 1) : source;
    }

    private Async<String> detectRoot() {
        return directory("plugins/Nexo/glyphs").thenCompose(primary -> primary
                ? Async.completed("plugins/Nexo")
                : directory("glyphs").thenApply(atRoot -> atRoot ? "" : null));
    }

    private Async<Boolean> directory(String path) {
        return files.listFiles(server, path).thenApply(ignored -> true).exceptionally(failure -> false);
    }

    private Async<List<String>> walk(String directory) {
        return files.listFiles(server, directory).thenCompose(entries -> {
            List<String> result = new ArrayList<>();
            Async<Void> children = Async.completed(null);
            if (entries != null) {
                for (ServerModels.PteroFileObjectAttributes entry : entries) {
                    if (entry == null || entry.name == null || entry.name.isBlank()) continue;
                    String path = join(directory, entry.name);
                    if (entry.isFile) result.add(path);
                    else children = children.thenCompose(ignored -> walk(path).thenAccept(result::addAll).exceptionally(failure -> null));
                }
            }
            return children.thenApply(ignored -> result);
        });
    }

    private Async<Void> parse(String source, String content, String detectedRoot, Map<String, GlyphDefinition> target) {
        Async<Void> parsed = Async.completed(null);
        for (Map.Entry<String, Map<String, Object>> entry : NexoGlyphConfig.parse(content).entrySet()) {
            Map<String, Object> raw = entry.getValue();
            String texture = string(raw.get("texture"));
            String gif = string(raw.get("gif"));
            boolean animated = gif != null && !gif.isBlank();
            String value = animated ? gif : texture;
            Async<String> assetPath = value == null || value.isBlank()
                    ? Async.completed(null)
                    : resolveAssetPath(detectedRoot, value, animated);
            parsed = parsed.thenCompose(ignored -> assetPath.thenAccept(path -> {
                GlyphAssetRef asset = value == null || value.isBlank() ? null : new GlyphAssetRef(value, animated, path);
                GlyphDefinition glyph = NexoGlyphCatalog.definition(entry.getKey(), source, asset, raw);
                target.put(glyph.id(), glyph);
            }));
        }
        return parsed;
    }

    private Image image(GlyphDefinition glyph, Integer requestedIndex, String url) {
        if (url == null || url.isBlank()) return null;
        NexoGlyphCatalog.Grid grid = glyph.isGif() ? new NexoGlyphCatalog.Grid(1, 1) : NexoGlyphCatalog.grid(glyph, requestedIndex);
        int rows = grid.rows();
        int columns = grid.columns();
        int height = Math.max(1, glyph.height());
        int total = Math.max(1, rows * columns);
        int selected = Math.max(0, Math.min(total - 1, requestedIndex == null ? 0 : requestedIndex));
        Identifier id = ScreenManager.getInstance().imageAssets().registerRemoteImage(url);
        return id == null ? null : new Image(id, height, height, rows, columns, selected);
    }

    private Async<String> resolveAssetPath(String detectedRoot, String asset, boolean gif) {
        String normalized = asset.replace('\\', '/');
        int colon = normalized.indexOf(':');
        String namespace = colon >= 0 ? normalized.substring(0, colon) : null;
        String value = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        String extension = gif ? ".gif" : ".png";
        if (!value.toLowerCase(Locale.ROOT).endsWith(extension)) value += extension;
        if (value.startsWith("textures/")) value = value.substring("textures/".length());
        String texture = value;
        if (namespace != null) {
            return existingAsset(detectedRoot, namespace, texture).thenApply(found -> found.orElse(null));
        }
        List<String> preferred = List.of("minecraft", "nexo");
        return firstExistingAsset(detectedRoot, preferred, texture, 0).thenCompose(found -> {
            if (found != null) return Async.completed(found);
            String assets = join(detectedRoot, "pack/assets");
            return files.listFiles(server, assets).thenCompose(entries -> {
                List<String> namespaces = new ArrayList<>();
                if (entries != null) {
                    for (ServerModels.PteroFileObjectAttributes entry : entries) {
                        if (entry != null && !entry.isFile && entry.name != null && !entry.name.isBlank() && !preferred.contains(entry.name)) {
                            namespaces.add(entry.name);
                        }
                    }
                }
                return firstExistingAsset(detectedRoot, namespaces, texture, 0);
            }).exceptionally(failure -> null);
        });
    }

    private Async<String> firstExistingAsset(String detectedRoot, List<String> namespaces, String texture, int index) {
        if (index >= namespaces.size()) return Async.completed(null);
        return existingAsset(detectedRoot, namespaces.get(index), texture).thenCompose(found -> found.isPresent()
                ? Async.completed(found.get())
                : firstExistingAsset(detectedRoot, namespaces, texture, index + 1));
    }

    private Async<Optional<String>> existingAsset(String detectedRoot, String namespace, String texture) {
        String path = join(detectedRoot, "pack/assets/" + namespace + "/textures/" + texture);
        int separator = path.lastIndexOf('/');
        String parent = separator < 0 ? "" : path.substring(0, separator);
        String name = separator < 0 ? path : path.substring(separator + 1);
        return files.listFiles(server, parent).thenApply(entries -> {
            if (entries != null) {
                for (ServerModels.PteroFileObjectAttributes entry : entries) {
                    if (entry != null && entry.isFile && name.equals(entry.name)) return Optional.of(path);
                }
            }
            return Optional.<String>empty();
        }).exceptionally(failure -> Optional.empty());
    }

    private String serverId() {
        if (server.identifier != null && !server.identifier.isBlank()) return server.identifier;
        return server.uuid == null ? "" : server.uuid;
    }

    private static String join(String parent, String child) {
        String left = parent == null ? "" : parent.replace('\\', '/').replaceAll("/+$", "");
        String right = child == null ? "" : child.replace('\\', '/').replaceAll("^/+", "");
        return left.isEmpty() ? right : right.isEmpty() ? left : left + "/" + right;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

}
