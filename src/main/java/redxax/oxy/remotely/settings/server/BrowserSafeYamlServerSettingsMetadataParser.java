package redxax.oxy.remotely.settings.server;

public final class BrowserSafeYamlServerSettingsMetadataParser extends YamlServerSettingsMetadataParser {
    public BrowserSafeYamlServerSettingsMetadataParser() {
        super(BrowserSafeYaml::parse);
    }
}
