package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;

public record GlyphAssetRef(String value, boolean gif, Path resolvedPath) {}
