package redxax.oxy.remotely.network.config;

import redxax.oxy.remotely.libs.snakeyaml.LoaderOptions;
import redxax.oxy.remotely.libs.snakeyaml.Yaml;
import redxax.oxy.remotely.libs.snakeyaml.constructor.SafeConstructor;

public final class DesktopStructuredDocumentParser implements StructuredDocumentParser {
    @Override
    public Object parse(String value) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options)).load(value);
    }
}
