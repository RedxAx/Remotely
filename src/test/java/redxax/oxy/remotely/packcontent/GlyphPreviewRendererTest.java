package redxax.oxy.remotely.packcontent;

import com.jediterm.terminal.util.CharUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphPreviewRendererTest {
    @Test
    void consumesTerminalDoubleWidthTrailerWithGlyph() {
        String text = "ꐓ" + CharUtils.DWC;

        assertEquals(2, GlyphPreviewRenderer.terminalTokenEnd(text, 0, 1));
    }

    @Test
    void preservesPrivateUseCharacterAfterGlyphTag() {
        String text = "<g:logo>" + CharUtils.DWC;

        assertEquals(8, GlyphPreviewRenderer.terminalTokenEnd(text, 0, 8));
    }

    @Test
    void leavesUnmatchedPrivateUseCharactersAlone() {
        String text = "plain" + CharUtils.DWC;

        assertEquals(text, GlyphPreviewRenderer.terminalPlainText(text));
    }

}
