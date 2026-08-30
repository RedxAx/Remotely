package redxax.oxy.remotely.settings.server;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface ServerSettingsMetadataReader {
    ServerSettingsMetadata parse(InputStream input, String sourceName) throws IOException;
}
