package redxax.oxy.remotely.packcontent;

import java.util.List;
import java.util.Map;

public interface GlyphContentProvider extends PackContentCapability {
    Map<String, GlyphDefinition> glyphs();
    List<GlyphTagMatch> parseGlyphTags(String text);
}
