package redxax.oxy.servers;

import java.util.List;

public class ModrinthResource implements IRemotelyResource {
    private final String name;
    private final String version;
    private final String description;
    private final String fileName;
    private final String iconUrl;
    private final int downloads;
    private final int followers;
    private final String slug;
    private final List<String> dependencies;
    private final String projectId;
    private final String versionId;

    public ModrinthResource(String name, String version, String description, String fileName, String iconUrl, int downloads, int followers, String slug, List<String> dependencies, String projectId, String versionId) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.fileName = fileName;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.followers = followers;
        this.slug = slug;
        this.dependencies = dependencies;
        this.projectId = projectId;
        this.versionId = versionId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    @Override
    public int getDownloads() {
        return downloads;
    }

    @Override
    public int getFollowers() {
        return followers;
    }

    @Override
    public String getSlug() {
        return slug;
    }

    @Override
    public String getProjectId() {
        return projectId;
    }

    @Override
    public String getVersionId() {
        return versionId;
    }

    @Override
    public String getAverageRating() {
        return "";
    }
}
