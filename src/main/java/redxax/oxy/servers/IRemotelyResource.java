package redxax.oxy.servers;

public interface IRemotelyResource {
    String getName();
    String getVersion();
    String getDescription();
    String getFileName();
    String getIconUrl();
    int getDownloads();
    int getFollowers();
    String getSlug();
    String getProjectId();
    String getVersionId();
    String getAverageRating();
}
