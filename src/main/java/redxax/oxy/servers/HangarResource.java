package redxax.oxy.servers;

public class HangarResource implements IRemotelyResource {
    private final String id;
    private final String name;
    private final String description;
    private final String owner;
    private final String visibility;
    private final int stars;
    private final int watchers;
    private final int downloads;
    private final String avatarUrl;

    public HangarResource(String id, String name, String description, String owner, String visibility, int stars, int watchers, int downloads, String avatarUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.visibility = visibility;
        this.stars = stars;
        this.watchers = watchers;
        this.downloads = downloads;
        this.avatarUrl = avatarUrl;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return watchers + " Followers";
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getFileName() {
        return name.replace(" ", "_") + ".jar";
    }

    @Override
    public String getIconUrl() {
        return avatarUrl;
    }

    @Override
    public int getDownloads() {
        return downloads;
    }

    @Override
    public int getFollowers() {
        return stars;
    }

    @Override
    public String getSlug() {
        return "hangar_" + name.toLowerCase().replace(" ", "_");
    }

    @Override
    public String getProjectId() {
        return id;
    }

    @Override
    public String getVersionId() {
        return "";
    }

    @Override
    public String getAverageRating() {
        return "";
    }
}
