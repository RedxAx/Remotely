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
    public List<String> authors;
    public List<String> categories;
    public List<String> dependencies;
    public String projectId; // New field
    public String versionId; // Retain version ID if needed

    public ModrinthResource(String name, String version, String description, String fileName, String iconUrl, int downloads, String slug, List<String> gameVersions, String projectId, String versionId) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.fileName = fileName;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.slug = slug;
        this.gameVersions = gameVersions;
        this.authors = List.of();
        this.categories = List.of();
        this.dependencies = List.of();
        this.projectId = projectId;
        this.versionId = versionId;
    }
}
