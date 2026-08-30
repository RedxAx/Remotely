package redxax.oxy.remotely.packcontent;

import java.util.List;
import java.util.Map;

public record GlyphDefinition(
        String providerId,
        String id,
        String sourceFile,
        GlyphAssetRef assetRef,
        int ascent,
        int height,
        String font,
        int rows,
        int columns,
        String reference,
        Integer index,
        int offset,
        int frameCount,
        Map<String, Object> raw,
        List<GlyphPreviewFrame> frames
) {
    public boolean isReference() {
        return reference != null && !reference.isBlank();
    }

    public boolean isMultiBitmap() {
        return rows > 1 || columns > 1;
    }

    public boolean isGif() {
        return assetRef != null && assetRef.gif();
    }
}
