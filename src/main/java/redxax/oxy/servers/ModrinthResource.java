package redxax.oxy.servers;

import java.nio.file.Path;

public class ModrinthResource {
    public String name;
    public String version;
    public Path downloadUrl;
    public String description;
    public String fileName;

    public ModrinthResource(String name, String version, Path downloadUrl, String description, String fileName) {
        this.name = name;
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.description = description;
        this.fileName = fileName;
    }
}
