package redxax.oxy.remotely.packcontent;

import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.Instance;
import restudio.rebase.util.Executors;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PackContentRegistry {
    private static final PackContentRegistry INSTANCE = new PackContentRegistry();
    private final Map<String, ProviderSession> sessions = new ConcurrentHashMap<>();

    public static PackContentRegistry get() {
        return INSTANCE;
    }

    public CompletableFuture<Void> refresh(Instance instance, FileSystemProvider fileSystem, Path workspaceRoot) {
        if (fileSystem == null || workspaceRoot == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            PackContentContext base = new PackContentContext(instance, fileSystem, workspaceRoot, workspaceRoot, Map.of());
            ProviderSession session = new ProviderSession(base);
            List<CompletableFuture<Void>> refreshes = new ArrayList<>();
            for (PackContentProvider provider : providers()) {
                Optional<Path> detected;
                try {
                    detected = provider.detectRoot(base);
                } catch (Exception e) {
                    detected = Optional.empty();
                }
                detected.ifPresent(root -> {
                    PackContentContext providerContext = base.withProviderRoot(root);
                    session.contexts.put(provider.id(), providerContext);
                    session.providers.put(provider.id(), provider);
                    refreshes.add(provider.refresh(providerContext));
                });
            }
            return new RefreshPlan(session, refreshes);
        }, Executors.IO).thenCompose(plan -> {
            String key = key(instance, workspaceRoot);
            if (plan.refreshes().isEmpty()) {
                plan.session().refreshedAt = System.currentTimeMillis();
                sessions.put(key, plan.session());
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.allOf(plan.refreshes().toArray(CompletableFuture[]::new)).thenRun(() -> {
                plan.session().refreshedAt = System.currentTimeMillis();
                sessions.put(key, plan.session());
            });
        });
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
                List<GlyphPreviewFrame> frames = framesFor(session.contexts.get(provider.id()), provider, glyph, match.indexStart());
                if (!frames.isEmpty()) {
                    result.add(new ResolvedGlyphPreview(provider.displayName(), glyph, match, frames));
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
            List<GlyphPreviewFrame> frames = framesFor(session.contexts.get(provider.id()), provider, glyph, index);
            if (!frames.isEmpty()) {
                GlyphTagMatch match = new GlyphTagMatch(provider.id(), glyphId, 0, glyphId.length(), index, index, 0);
                return Optional.of(new ResolvedGlyphPreview(provider.displayName(), glyph, match, frames));
            }
        }
        return Optional.empty();
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
                        BufferedImage image = glyph.frames().isEmpty() ? null : glyph.frames().getFirst().image();
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

    public BufferedImage currentFrame(ResolvedGlyphPreview preview) {
        if (preview == null || preview.frames().isEmpty()) {
            return null;
        }
        int total = 0;
        for (GlyphPreviewFrame frame : preview.frames()) {
            total += Math.max(20, frame.delayMs());
        }
        if (total <= 0) {
            return preview.frames().getFirst().image();
        }
        int cursor = (int) (System.currentTimeMillis() % total);
        int elapsed = 0;
        for (GlyphPreviewFrame frame : preview.frames()) {
            elapsed += Math.max(20, frame.delayMs());
            if (cursor < elapsed) {
                return frame.image();
            }
        }
        return preview.frames().getLast().image();
    }

    private List<GlyphPreviewFrame> framesFor(PackContentContext context, PackContentProvider provider, GlyphDefinition glyph, Integer index) {
        if (provider instanceof NexoContentProvider nexo) {
            return nexo.framesFor(context, glyph, index, false);
        }
        return glyph.frames();
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

    private record RefreshPlan(ProviderSession session, List<CompletableFuture<Void>> refreshes) {
    }

    public record ResolvedGlyphPreview(String providerName, GlyphDefinition glyph, GlyphTagMatch match, List<GlyphPreviewFrame> frames) {
    }

    public record ProviderStatus(String sessionKey, String workspaceName, String providerName, Path workspaceRoot, Path root, int glyphCount, int frameCount, int diagnosticCount, long refreshedAt) {
    }

    public record PackAssetOption(String providerId, String providerName, String id, String texture, BufferedImage preview) {
    }
}
