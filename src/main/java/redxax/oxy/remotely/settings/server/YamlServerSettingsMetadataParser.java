package redxax.oxy.remotely.settings.server;

import redxax.oxy.remotely.network.config.StructuredDocumentParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class YamlServerSettingsMetadataParser implements ServerSettingsMetadataReader {
    private final ServerSettingsMetadataParser parser;

    public YamlServerSettingsMetadataParser(StructuredDocumentParser structuredParser) {
        this(new ServerSettingsMetadataParser(structuredParser));
    }

    protected YamlServerSettingsMetadataParser(ServerSettingsMetadataParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public ServerSettingsMetadata parse(String yaml) {
        return parse(yaml, "metadata");
    }

    public ServerSettingsMetadata parse(String yaml, String sourceName) {
        return parser.parse(yaml, sourceName);
    }

    public ServerSettingsMetadata parse(InputStream input) throws IOException {
        return parse(input, "metadata");
    }

    @Override
    public ServerSettingsMetadata parse(InputStream input, String sourceName) throws IOException {
        Objects.requireNonNull(input, "input");
        return parse(new InputStreamReader(input, StandardCharsets.UTF_8), sourceName);
    }

    public ServerSettingsMetadata parse(Reader reader) {
        return parse(reader, "metadata");
    }

    public ServerSettingsMetadata parse(Reader reader, String sourceName) {
        return parser.parse(reader, sourceName);
    }
}
