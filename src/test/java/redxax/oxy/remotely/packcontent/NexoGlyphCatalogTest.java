package redxax.oxy.remotely.packcontent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NexoGlyphCatalogTest {
    @Test
    void materializesReferenceMetadataAndOneBasedIndex() {
        GlyphAssetRef asset = new GlyphAssetRef("custom:faces", false, "pack/assets/custom/textures/faces.png");
        GlyphDefinition target = NexoGlyphCatalog.definition("faces", "glyphs/faces.yml", asset,
                Map.of("rows", 2, "columns", 2, "height", 16));
        GlyphDefinition reference = NexoGlyphCatalog.definition("third", "glyphs/faces.yml", null,
                Map.of("reference", "faces", "index", 3));
        NexoGlyphCatalog catalog = new NexoGlyphCatalog();
        catalog.put(target);
        catalog.put(reference);

        GlyphDefinition resolved = catalog.materialized("third");

        assertEquals(asset, resolved.assetRef());
        assertEquals(2, resolved.index());
        assertEquals(2, resolved.rows());
        assertEquals(2, resolved.columns());
    }

    @Test
    void rejectsReferenceCycles() {
        NexoGlyphCatalog catalog = new NexoGlyphCatalog();
        catalog.put(NexoGlyphCatalog.definition("one", "glyphs/test.yml", null, Map.of("reference", "two")));
        catalog.put(NexoGlyphCatalog.definition("two", "glyphs/test.yml", null, Map.of("reference", "one")));

        assertNull(catalog.materialized("one"));
    }

    @Test
    void infersRawCharacterAtlasGridByCodePoint() {
        String supplementary = new String(Character.toChars(0xF0001));
        GlyphDefinition glyph = NexoGlyphCatalog.definition("faces", "glyphs/faces.yml", null,
                Map.of("unicode", List.of("ꐓ" + supplementary, "ꐔꐕ")));

        assertEquals(new NexoGlyphCatalog.Grid(2, 2), NexoGlyphCatalog.grid(glyph, 3));
    }
}
