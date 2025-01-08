package redxax.oxy.servers;

public class HangarResource {
    public String name;
    public String description;
    public String owner;
    public String visibility;
    public int stars;
    public int watchers;
    public int downloads;

    public HangarResource(String name, String description, String owner, String visibility, int stars, int watchers, int downloads) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.visibility = visibility;
        this.stars = stars;
        this.watchers = watchers;
        this.downloads = downloads;
    }
}
