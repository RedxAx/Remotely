package redxax.oxy.remotely.packcontent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NexoGlyphConfigTest {
    @Test
    void parsesGlyphValuesAndRawCharacters() {
        Map<String, Map<String, Object>> glyphs = NexoGlyphConfig.parse("""
                test_icon:
                  texture: minecraft:custom/icon
                  ascent: 8
                  height: 8
                  char: ꐓ
                """);

        assertEquals("minecraft:custom/icon", glyphs.get("test_icon").get("texture"));
        assertEquals(8, glyphs.get("test_icon").get("height"));
        assertEquals("ꐓ", glyphs.get("test_icon").get("char"));
    }

    @Test
    void parsesBlockAndInlineCharacterRows() {
        Map<String, Map<String, Object>> glyphs = NexoGlyphConfig.parse("""
                block:
                  char:
                    - "😀😁"
                    - '😂😃'
                inline:
                  char: [ꐐ, ꐑ]
                """);

        assertEquals(List.of("😀😁", "😂😃"), glyphs.get("block").get("char"));
        assertEquals(List.of("ꐐ", "ꐑ"), glyphs.get("inline").get("char"));
    }

    @Test
    void preservesHashesInsideQuotedValues() {
        Map<String, Map<String, Object>> glyphs = NexoGlyphConfig.parse("""
                icon:
                  texture: "custom/icon#active" # note
                """);

        assertEquals("custom/icon#active", glyphs.get("icon").get("texture"));
    }
}
