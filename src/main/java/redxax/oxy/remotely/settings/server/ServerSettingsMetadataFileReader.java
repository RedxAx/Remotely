package redxax.oxy.remotely.settings.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ServerSettingsMetadataFileReader {
    private final DesktopServerSettingsMetadataParser parser;

    public ServerSettingsMetadataFileReader() {
        this(new DesktopServerSettingsMetadataParser());
    }

    public ServerSettingsMetadataFileReader(DesktopServerSettingsMetadataParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public ServerSettingsMetadata read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parser.parse(reader, path.toString());
        }
    }
}
