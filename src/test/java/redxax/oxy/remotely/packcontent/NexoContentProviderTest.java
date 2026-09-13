package redxax.oxy.remotely.packcontent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.impl.LocalBackend;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexoContentProviderTest {
    private final NexoContentProvider provider = new NexoContentProvider();

    @TempDir
    Path workspace;

    @Test
    void keepsExistingGlyphTagMatching() {
        List<GlyphTagMatch> matches = provider.parseGlyphTags("<g:test_icon>");

        assertEquals(List.of(new GlyphTagMatch("nexo", "test_icon", 0, 13, null, null, 0)), matches);
    }

    @Test
    void keepsOneBasedGlyphTagRanges() {
        List<GlyphTagMatch> matches = provider.parseGlyphTags("<g:faces:2..4>");

        assertEquals(List.of(new GlyphTagMatch("nexo", "faces", 0, 14, 1, 3, 0)), matches);
    }

    @Test
    void matchesAssignedRawGlyphCharacters() {
        GlyphDefinition glyph = glyph("logo", 1, 1, null, "ꐐ");

        List<GlyphTagMatch> matches = provider.parseRawGlyphChars("Prefix ꐐ suffix", List.of(glyph), List.of());

        assertEquals(List.of(new GlyphTagMatch("nexo", "logo", 7, 8, null, null, 0)), matches);
    }

    @Test
    void treatsSupplementaryGlyphAsOneCharacter() {
        String assigned = new String(Character.toChars(0xF0001));
        GlyphDefinition glyph = glyph("logo", 1, 1, null, assigned);

        List<GlyphTagMatch> matches = provider.parseRawGlyphChars(assigned, List.of(glyph), List.of());

        assertEquals(List.of(new GlyphTagMatch("nexo", "logo", 0, 2, null, null, 0)), matches);
    }

    @Test
    void mapsMultiBitmapCharactersToTheirFrame() {
        GlyphDefinition glyph = glyph("faces", 2, 2, null, List.of("ꐐꐑ", "ꐒꐓ"));

        List<GlyphTagMatch> matches = provider.parseRawGlyphChars("ꐒ", List.of(glyph), List.of());

        assertEquals(List.of(new GlyphTagMatch("nexo", "faces", 0, 1, 2, 2, 0)), matches);
    }

    @Test
    void ignoresRawCharactersInsideGlyphTags() {
        GlyphDefinition glyph = glyph("letter", 1, 1, null, "g");
        GlyphTagMatch tag = new GlyphTagMatch("nexo", "logo", 0, 8, null, null, 0);

        List<GlyphTagMatch> matches = provider.parseRawGlyphChars("<g:logo>", List.of(glyph), List.of(tag));

        assertEquals(List.of(), matches);
    }

    @Test
    void keepsAdjacentShiftForRawCharacters() {
        GlyphDefinition glyph = glyph("logo", 1, 1, null, "ꐐ");

        List<GlyphTagMatch> matches = provider.parseRawGlyphChars("<shift:-4>ꐐ", List.of(glyph), List.of());

        assertEquals(List.of(new GlyphTagMatch("nexo", "logo", 10, 11, null, null, -4)), matches);
    }

    @Test
    void matchesNexoUnicodeAliasesAndEscapes() {
        GlyphDefinition glyph = new GlyphDefinition("nexo", "logo", "glyphs/test.yml", null, 8, 8,
                "minecraft:default", 1, 1, null, null, 0, 0, Map.of("unicode", "\\uA413"), List.of());

        List<GlyphTagMatch> matches = provider.parseRawGlyphChars("ꐓ", List.of(glyph), List.of());

        assertEquals(List.of(new GlyphTagMatch("nexo", "logo", 0, 1, null, null, 0)), matches);
    }

    @Test
    void resolvesConfiguredGlyphPreviewFromServerWorkspace() throws Exception {
        Path nexo = workspace.resolve("plugins").resolve("Nexo");
        Path glyphs = nexo.resolve("glyphs");
        Path texture = nexo.resolve("pack").resolve("assets").resolve("minecraft").resolve("textures").resolve("custom").resolve("icon.png");
        Files.createDirectories(glyphs);
        Files.createDirectories(texture.getParent());
        Files.writeString(glyphs.resolve("test.yml"), "test_icon:\n  texture: minecraft:custom/icon\n  ascent: 8\n  height: 8\n  char: ꐓ\ntest_alias:\n  reference: test_icon\n  index: 1\n");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", texture.toFile());
        FileSystemProvider files = new LocalBackend(new BackendConfig("LOCAL", Map.of()), null).getFileSystem();

        PackContentRegistry.get().refresh(null, files, workspace).join();
        var preview = PackContentRegistry.get().resolveGlyph(null, workspace, "nexo", "test_icon", null);

        assertTrue(preview.isPresent());
        assertFalse(preview.orElseThrow().frames().isEmpty());

        PackContentRegistry.get().refresh(null, files, nexo).join();
        var editorPreview = PackContentRegistry.get().resolveGlyph(null, nexo, "nexo", "test_icon", null);

        assertTrue(editorPreview.isPresent());
        assertFalse(editorPreview.orElseThrow().frames().isEmpty());

        var alias = PackContentRegistry.get().resolveGlyph(null, nexo, "nexo", "test_alias", null);

        assertTrue(alias.isPresent());
        assertEquals("test_alias", alias.orElseThrow().glyph().id());
        assertEquals(Integer.valueOf(0), alias.orElseThrow().match().indexStart());
        assertEquals("minecraft:custom/icon", alias.orElseThrow().glyph().assetRef().value());
        assertNotNull(alias.orElseThrow().glyph().assetRef().resolvedPath());
    }

    private GlyphDefinition glyph(String id, int rows, int columns, Integer index, Object chars) {
        return new GlyphDefinition("nexo", id, "glyphs/test.yml", null, 8, 8, "minecraft:default", rows, columns, null, index, 0, 0, Map.of("char", chars), List.of());
    }
}
