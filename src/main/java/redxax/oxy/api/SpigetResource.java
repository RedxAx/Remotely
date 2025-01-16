package redxax.oxy.api;

public class SpigetResource implements IRemotelyResource {
    private final String name;
    private final String tag;
    private final String iconUrl;
    private final int downloads;
    private final int id;
    private final double averageRating;
    private final boolean external;
    private final String fileUrl;

    public SpigetResource(String name, String tag, String iconUrl, int downloads, int id, double averageRating, boolean external, String fileUrl) {
        this.name = name;
        this.tag = tag;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.id = id;
        this.averageRating = averageRating;
        this.external = external;
        this.fileUrl = fileUrl;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return tag != null && !tag.isBlank() ? tag : "Unknown";
    }

    @Override
    public String getDescription() {
        return tag != null && !tag.isBlank() ? tag : "Unknown";
    }

    @Override
    public String getFileName() {
        return name.replace(" ", "_") + ".jar";
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
        return 0;
    }

    @Override
    public String getSlug() {
        return "spigot_" + id;
    }

    @Override
    public String getProjectId() {
        return String.valueOf(id);
    }

    @Override
    public String getVersionId() {
        return "";
    }

    @Override
    public String getAverageRating() {
        return String.format("%.1f", averageRating);
    }

    public boolean isExternal() {
        return external;
    }

    public String getFileUrl() {
        return fileUrl;
    }
}
