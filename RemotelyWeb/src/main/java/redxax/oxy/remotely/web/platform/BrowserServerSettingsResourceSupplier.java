package redxax.oxy.remotely.web.platform;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

public final class BrowserServerSettingsResourceSupplier implements ResourceSupplier {
    private static final String[] RESOURCES = {
            "server-settings/purpur.yml",
            "server-settings/bukkit.yml",
            "server-settings/spigot.yml",
            "server-settings/paper-global.yml",
            "server-settings/paper-world.yml",
            "server-settings/server-properties.yml",
            "server-settings/velocity.yml"
    };

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        return RESOURCES.clone();
    }
}
