package redxax.oxy.remotely.resources;

public interface IRemotelyResource {
    String getName();
    String getVersion();
    String getDescription();
    String getFileName();
    String getIconUrl();
    String getBannerUrl();
    int getDownloads();
    int getFollowers();
    String getSlug();
    String getProjectId();
    String getVersionId();
    double getAverageRating();
    String getAuthor();
    String getMinecraftVersions();
    String getLoaderPlatforms();
}
