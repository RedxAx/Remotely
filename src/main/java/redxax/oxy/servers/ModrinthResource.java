package redxax.oxy.servers;

public class ModrinthResource {
    public final String name;
    public final String version;
    public final String description;
    public final String fileName;
    public final String iconUrl;
    public final int downloads;

    public ModrinthResource(String name, String version, String description, String fileName, String iconUrl, int downloads) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.fileName = fileName;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
    }
}