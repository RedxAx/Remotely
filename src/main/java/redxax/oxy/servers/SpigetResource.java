package redxax.oxy.servers;

public class SpigetResource {
    public String name;
    public String tag;
    public String description;
    public String iconUrl;
    public int downloads;
    public int id;

    public SpigetResource(String name, String tag, String description, String iconUrl, int downloads, int id) {
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.id = id;
    }
}
