package redxax.oxy.remotely.network;

import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import java.util.Locale;
import java.util.Set;

public enum NetworkBackendForwardingAdapter {
    PAPER,
    FABRIC_PROXY_LITE,
    PROXY_COMPATIBLE_FORGE,
    UNSUPPORTED;

    private static final Set<String> PAPER_SOFTWARE = Set.of("paper", "folia", "purpur", "leaf", "pufferfish", "canvas", "aspaper", "divinemc");
    private static final Set<String> FABRIC_BRIDGES = Set.of("velocity-modern", "fabricproxy-lite", "fabric-proxy-lite");
    private static final Set<String> FORGE_BRIDGES = Set.of("velocity-modern", "proxy-compatible-forge", "proxycompatibleforge");

    public static NetworkBackendForwardingAdapter resolve(Instance instance) {
        if (instance == null) {
            return UNSUPPORTED;
        }
        ModLoader loader = instance.getModLoader();
        String software = normalize(instance.getServerSoftwareType());
        if (PAPER_SOFTWARE.contains(software) || loader == ModLoader.PAPER || loader == ModLoader.FOLIA || loader == ModLoader.PURPUR || loader == ModLoader.LEAF) {
            return PAPER;
        }
        String bridge = normalize(instance.getSettings().getProperty("network.forwarding.bridge", ""));
        if ((loader == ModLoader.FABRIC || loader == ModLoader.QUILT) && FABRIC_BRIDGES.contains(bridge)) {
            return FABRIC_PROXY_LITE;
        }
        if ((loader == ModLoader.FORGE || loader == ModLoader.NEOFORGE) && FORGE_BRIDGES.contains(bridge)) {
            return PROXY_COMPATIBLE_FORGE;
        }
        return UNSUPPORTED;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
