package redxax.oxy.remotely.packcontent;

import restudio.rebase.platform.Async;
import restudio.rescreen.util.Identifier;

import java.util.List;
import java.util.Optional;

public interface GlyphPreviewAccess {
    record Preview(String providerName, GlyphDefinition glyph, GlyphTagMatch match, List<GlyphPreviewFrame> frames) {
        public Preview {
            frames = frames == null ? List.of() : List.copyOf(frames);
        }
    }

    record Image(Identifier id, int width, int height) {
    }

    Async<Void> refresh();

    List<Preview> resolveGlyphs(String text);

    Optional<Preview> resolveGlyph(String providerId, String glyphId, Integer index);

    Async<Image> loadImage(GlyphDefinition glyph);

    default Async<Image> loadImage(GlyphDefinition glyph, Integer index) {
        return loadImage(glyph);
    }

    String sourceName(GlyphDefinition glyph);

    default boolean openSource(GlyphDefinition glyph) {
        return false;
    }

    default boolean openAsset(GlyphDefinition glyph) {
        return false;
    }
}
