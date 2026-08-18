package redxax.oxy.remotely.settings.server;

import redxax.oxy.remotely.libs.snakeyaml.LoaderOptions;
import redxax.oxy.remotely.libs.snakeyaml.Yaml;
import redxax.oxy.remotely.libs.snakeyaml.constructor.Construct;
import redxax.oxy.remotely.libs.snakeyaml.constructor.SafeConstructor;
import redxax.oxy.remotely.libs.snakeyaml.nodes.Node;
import redxax.oxy.remotely.libs.snakeyaml.nodes.ScalarNode;
import redxax.oxy.remotely.libs.snakeyaml.nodes.Tag;
import redxax.oxy.remotely.network.config.StructuredDocumentParser;

import java.math.BigDecimal;

public final class DesktopServerSettingsMetadataParser extends YamlServerSettingsMetadataParser {
    public DesktopServerSettingsMetadataParser() {
        super(new DesktopMetadataParser());
    }

    private static final class DesktopMetadataParser implements StructuredDocumentParser {
        private static final int MAX_CODE_POINTS = 1_000_000;
        private static final int MAX_ALIASES = 32;
        private static final int MAX_NESTING_DEPTH = 32;

        @Override
        public Object parse(String value) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(MAX_ALIASES);
            options.setCodePointLimit(MAX_CODE_POINTS);
            options.setNestingDepthLimit(MAX_NESTING_DEPTH);
            return new Yaml(new PrecisionSafeConstructor(options)).load(value);
        }

        private static final class PrecisionSafeConstructor extends SafeConstructor {
            private PrecisionSafeConstructor(LoaderOptions options) {
                super(options);
                Construct fallback = yamlConstructors.get(Tag.FLOAT);
                yamlConstructors.put(Tag.FLOAT, new Construct() {
                    @Override
                    public Object construct(Node node) {
                        String value = constructScalar((ScalarNode) node);
                        try {
                            return new BigDecimal(value.replace("_", ""));
                        } catch (NumberFormatException exception) {
                            return fallback.construct(node);
                        }
                    }

                    @Override
                    public void construct2ndStep(Node node, Object object) {
                        fallback.construct2ndStep(node, object);
                    }
                });
            }
        }
    }
}
