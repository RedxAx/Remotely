package redxax.oxy.servers;

import java.util.List;

public class ModrinthResource {
    public String name;
    public String version;
    public String description;
    public String fileName;
    public String iconUrl;
    public int downloads;
    public String slug;
    public List<String> gameVersions;

    public ModrinthResource(String name, String version, String description, String fileName, String iconUrl, int downloads, String slug, List<String> gameVersions) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.fileName = fileName;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.slug = slug;
        this.gameVersions = gameVersions;
    }
}
