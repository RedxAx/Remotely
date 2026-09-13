package redxax.oxy.remotely.packcontent;

import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.util.AsyncTools;

import redxax.oxy.remotely.util.BrowserSafeState;

import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.Instance;

import restudio.rescreen.util.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import restudio.rescreen.platform.Async;

public class PackContentRegistry {
    private static final PackContentRegistry INSTANCE = new PackContentRegistry();
    private final Map<String, ProviderSession> sessions = BrowserSafeState.map();
    private final Map<String, Async<Void>> refreshes = BrowserSafeState.map();

    public static PackContentRegistry get() {
        return INSTANCE;
    }

    public Async<Void> refresh(Instance instance, FileSystemProvider fileSystem, Path workspaceRoot) {
        if (fileSystem == null || workspaceRoot == null) {
            return Async.completed(null);
        }
        String key = key(instance, workspaceRoot);
        Async<Void> pending = Async.pending();
        Async<Void> active = refreshes.putIfAbsent(key, pending);
        if (active != null) {
            return active;
        }
        try {
            refreshSession(instance, fileSystem, workspaceRoot, key).whenComplete((ignored, error) -> {
                refreshes.remove(key, pending);
                if (error == null) {
                    pending.complete(null);
                } else {
                    pending.completeExceptionally(error);
                }
            });
        } catch (Exception e) {
            refreshes.remove(key, pending);
            pending.completeExceptionally(e);
        }
        return pending;
    }

    private Async<Void> refreshSession(Instance instance, FileSystemProvider fileSystem, Path workspaceRoot, String key) {
        PackContentContext base = new PackContentContext(instance, fileSystem, workspaceRoot, workspaceRoot, Map.of());
        ProviderSession session = new ProviderSession(base);
        Async<Void> refresh = Async.completed(null);
        for (PackContentProvider provider : providers()) {
            refresh = refresh.thenCompose(ignored -> detectRoot(provider, base).thenCompose(detected -> {
                if (detected.isEmpty()) {
                    return Async.completed(null);
                }
                PackContentContext providerContext = base.withProviderRoot(detected.get());
                session.contexts.put(provider.id(), providerContext);
                session.providers.put(provider.id(), provider);
                return provider.refresh(providerContext);
            }));
        }
        return refresh.thenRun(() -> {
            session.refreshedAt = System.currentTimeMillis();
            sessions.put(key, session);
        });
    }

    private Async<Optional<Path>> detectRoot(PackContentProvider provider, PackContentContext context) {
        try {
            return provider.detectRoot(context).exceptionally(error -> Optional.empty());
        } catch (Exception e) {
            return Async.completed(Optional.empty());
        }
    }

    public List<ResolvedGlyphPreview> resolveGlyphs(Instance instance, Path workspaceRoot, String text) {
        ProviderSession session = sessions.get(key(instance, workspaceRoot));
        if (session == null || text == null || text.isEmpty()) {
            return List.of();
        }
        List<ResolvedGlyphPreview> result = new ArrayList<>();
        for (PackContentProvider provider : session.providers.values()) {
            Optional<GlyphContentProvider> capability = provider.capability(GlyphContentProvider.class);
            if (capability.isEmpty()) {
                continue;
            }
            GlyphContentProvider glyphProvider = capability.get();
            for (GlyphTagMatch match : glyphProvider.parseGlyphTags(text)) {
                GlyphDefinition glyph = glyphProvider.glyphs().get(match.glyphId());
                if (glyph == null) {
                    continue;
                }
                GlyphDefinition previewGlyph = materialized(provider, glyph);
                if (previewGlyph == null) {
                    continue;
                }
                Integer index = match.indexStart() != null ? match.indexStart() : previewGlyph.index();
                GlyphTagMatch previewMatch = match.indexStart() != null || previewGlyph.index() == null ? match
                        : new GlyphTagMatch(match.providerId(), match.glyphId(), match.start(), match.end(), index, index, match.shift());
                List<GlyphPreviewFrame> frames = framesFor(session.contexts.get(provider.id()), provider, previewGlyph, index);
                if (!frames.isEmpty() || previewGlyph.assetRef() != null && previewGlyph.assetRef().resolvedPath() != null) {
                    result.add(new ResolvedGlyphPreview(provider.displayName(), previewGlyph, previewMatch, frames));
                }
            }
        }
        return result;
    }

    public Optional<ResolvedGlyphPreview> resolveGlyph(Instance instance, Path workspaceRoot, String providerId, String glyphId, Integer index) {
        ProviderSession session = sessions.get(key(instance, workspaceRoot));
        if (session == null || glyphId == null || glyphId.isBlank()) {
            return Optional.empty();
        }
        for (PackContentProvider provider : session.providers.values()) {
            if (providerId != null && !provider.id().equals(providerId)) {
                continue;
            }
            Optional<GlyphContentProvider> capability = provider.capability(GlyphContentProvider.class);
            if (capability.isEmpty()) {
                continue;
            }
            GlyphDefinition glyph = capability.get().glyphs().get(glyphId);
            if (glyph == null) {
                continue;
            }
            GlyphDefinition previewGlyph = materialized(provider, glyph);
            if (previewGlyph == null) {
                continue;
            }
            Integer previewIndex = index != null ? index : previewGlyph.index();
            List<GlyphPreviewFrame> frames = framesFor(session.contexts.get(provider.id()), provider, previewGlyph, previewIndex);
            if (!frames.isEmpty() || previewGlyph.assetRef() != null && previewGlyph.assetRef().resolvedPath() != null) {
                GlyphTagMatch match = new GlyphTagMatch(provider.id(), glyphId, 0, glyphId.length(), previewIndex, previewIndex, 0);
                return Optional.of(new ResolvedGlyphPreview(provider.displayName(), previewGlyph, match, frames));
            }
        }
        return Optional.empty();
    }

    public Async<GlyphPreviewFrame> loadImage(Instance instance, Path workspaceRoot, GlyphDefinition glyph) {
        return loadImage(instance, workspaceRoot, glyph, null);
    }

    public Async<GlyphPreviewFrame> loadImage(Instance instance, Path workspaceRoot, GlyphDefinition glyph, Integer index) {
        if (glyph == null) {
            return Async.completed(null);
        }
        ProviderSession session = sessions.get(key(instance, workspaceRoot));
        if (session == null) {
            return Async.completed(null);
        }
        PackContentProvider provider = session.providers.get(glyph.providerId());
        if (provider == null) {
            provider = session.providers.values().stream()
                    .filter(candidate -> candidate.capability(GlyphContentProvider.class)
                            .map(capability -> capability.glyphs().containsKey(glyph.id())).orElse(false))
                    .findFirst().orElse(null);
        }
        if (provider == null) {
            return Async.completed(null);
        }
        PackContentProvider selected = provider;
        PackContentContext context = session.contexts.get(provider.id());
        return AsyncTools.supply(TaskSchedulers.current(), () -> {
            List<GlyphPreviewFrame> frames = selected instanceof NexoContentProvider nexo
                    ? nexo.framesFor(context, glyph, index == null ? glyph.index() : index, true)
                    : glyph.frames();
            return frames.isEmpty() ? null : frames.getFirst();
        });
    }

    public List<PackContentDiagnostic> diagnostics(Instance instance, Path workspaceRoot) {
        ProviderSession session = sessions.get(key(instance, workspaceRoot));
        if (session == null) {
            return List.of();
        }
        List<PackContentDiagnostic> result = new ArrayList<>();
        for (PackContentProvider provider : session.providers.values()) {
            result.addAll(provider.diagnostics());
        }
        return result;
    }

    public List<PackContentDiagnostic> diagnostics() {
        List<PackContentDiagnostic> result = new ArrayList<>();
        for (ProviderSession session : sessions.values()) {
            for (PackContentProvider provider : session.providers.values()) {
                result.addAll(provider.diagnostics());
            }
        }
        return result;
    }

    public List<ProviderStatus> statuses() {
        List<ProviderStatus> result = new ArrayList<>();
        for (Map.Entry<String, ProviderSession> entry : sessions.entrySet()) {
            ProviderSession session = entry.getValue();
            String workspaceName = workspaceName(session.baseContext);
            Path workspaceRoot = session.baseContext.workspaceRoot();
            if (session.providers.isEmpty()) {
                result.add(new ProviderStatus(entry.getKey(), workspaceName, "None", workspaceRoot, null, 0, 0, 0, session.refreshedAt));
                continue;
            }
            for (PackContentProvider provider : session.providers.values()) {
                int glyphs = provider.capability(GlyphContentProvider.class).map(capability -> capability.glyphs().size()).orElse(0);
                int frames = provider.capability(GlyphContentProvider.class).map(capability -> capability.glyphs().values().stream().mapToInt(glyph -> glyph.frames().size()).sum()).orElse(0);
                PackContentContext context = session.contexts.get(provider.id());
                Path root = context != null ? context.providerRoot() : null;
                result.add(new ProviderStatus(entry.getKey(), workspaceName, provider.displayName(), workspaceRoot, root, glyphs, frames, provider.diagnostics().size(), session.refreshedAt));
            }
        }
        result.sort((a, b) -> (a.workspaceName() + a.providerName()).compareToIgnoreCase(b.workspaceName() + b.providerName()));
        return result;
    }

    public List<PackAssetOption> assetOptions(String providerId) {
        List<PackAssetOption> result = new ArrayList<>();
        for (ProviderSession session : sessions.values()) {
            for (PackContentProvider provider : session.providers.values()) {
                if (providerId != null && !providerId.isBlank() && !provider.id().equalsIgnoreCase(providerId)) {
                    continue;
                }
                provider.capability(GlyphContentProvider.class).ifPresent(capability -> {
                    for (GlyphDefinition glyph : capability.glyphs().values()) {
                        Identifier image = glyph.frames().isEmpty() ? null : glyph.frames().getFirst().image();
                        String texture = glyph.assetRef() != null ? glyph.assetRef().value() : "";
                        result.add(new PackAssetOption(provider.id(), provider.displayName(), glyph.id(), texture, image));
                    }
                });
            }
        }
        result.sort((a, b) -> (a.providerName() + a.id()).compareToIgnoreCase(b.providerName() + b.id()));
        return result;
    }

    private String workspaceName(PackContentContext context) {
        if (context == null) {
            return "Unknown";
        }
        Instance instance = context.instance();
        if (instance != null && instance.getName() != null && !instance.getName().isBlank()) {
            return instance.getName();
        }
        Path root = context.workspaceRoot();
        return root != null && root.getFileName() != null ? root.getFileName().toString() : "Local";
    }

    public GlyphPreviewImage currentFramePreview(ResolvedGlyphPreview preview) {
        GlyphPreviewFrame frame = currentFrame(preview);
        if (frame == null) {
            return null;
        }
        return new GlyphPreviewImage(frame.image(), frame.width(), frame.height());
    }

    private GlyphPreviewFrame currentFrame(ResolvedGlyphPreview preview) {
        if (preview == null || preview.frames().isEmpty()) {
            return null;
        }
        int total = 0;
        for (GlyphPreviewFrame frame : preview.frames()) {
            total += Math.max(20, frame.delayMs());
        }
        if (total <= 0) {
            return preview.frames().getFirst();
        }
        int cursor = (int) (System.currentTimeMillis() % total);
        int elapsed = 0;
        for (GlyphPreviewFrame frame : preview.frames()) {
            elapsed += Math.max(20, frame.delayMs());
            if (cursor < elapsed) {
                return frame;
            }
        }
        return preview.frames().getLast();
    }

    private List<GlyphPreviewFrame> framesFor(PackContentContext context, PackContentProvider provider, GlyphDefinition glyph, Integer index) {
        if (provider instanceof NexoContentProvider nexo) {
            return nexo.framesFor(context, glyph, index, false);
        }
        return glyph.frames();
    }

    private GlyphDefinition materialized(PackContentProvider provider, GlyphDefinition glyph) {
        return provider instanceof NexoContentProvider nexo ? nexo.materialized(glyph) : glyph;
    }

    private String key(Instance instance, Path workspaceRoot) {
        String instanceKey = instance != null ? instance.getInstanceId() : "local";
        return instanceKey + "|" + workspaceRoot.toAbsolutePath().normalize();
    }

    private List<PackContentProvider> providers() {
        return List.of(new NexoContentProvider(), new ItemsAdderContentProvider());
    }

    private static class ProviderSession {
        final PackContentContext baseContext;
        final Map<String, PackContentProvider> providers = new HashMap<>();
        final Map<String, PackContentContext> contexts = new HashMap<>();
        long refreshedAt;

        ProviderSession(PackContentContext baseContext) {
            this.baseContext = baseContext;
        }
    }

    public record ResolvedGlyphPreview(String providerName, GlyphDefinition glyph, GlyphTagMatch match, List<GlyphPreviewFrame> frames) {
    }

    public record GlyphPreviewImage(Identifier id, int width, int height) {
    }

    public record ProviderStatus(String sessionKey, String workspaceName, String providerName, Path workspaceRoot, Path root, int glyphCount, int frameCount, int diagnosticCount, long refreshedAt) {
    }

    public record PackAssetOption(String providerId, String providerName, String id, String texture, Identifier preview) {
    }
}
