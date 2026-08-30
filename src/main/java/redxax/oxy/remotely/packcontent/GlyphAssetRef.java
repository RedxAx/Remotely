package redxax.oxy.remotely.packcontent;

public record GlyphAssetRef(String value, boolean gif, String resolvedPath) {
    public String logicalPath() {
        return resolvedPath == null ? "" : resolvedPath.replace('\\', '/');
    }
}
