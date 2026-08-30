package redxax.oxy.remotely.ui.settings.data;

import redxax.oxy.remotely.settings.server.ServerSettingsPack;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class DesktopServerSettingsPackMatcher {
    private DesktopServerSettingsPackMatcher() {
    }

    public static boolean matches(ServerSettingsPack pack, Instance instance) {
        return pack != null && pack.appliesTo(softwareTokens(instance));
    }

    public static Collection<String> softwareTokens(Instance instance) {
        if (instance == null) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        ModLoader loader = instance.getModLoader();
        if (loader != null) {
            addTokenVariants(tokens, loader.name());
        }
        addTokenVariants(tokens, instance.getServerSoftwareType());
        instance.getServerSoftwareCompatibility().forEach(token -> addTokenVariants(tokens, token));
        instance.getServerSoftwareCategories().forEach(token -> addTokenVariants(tokens, token));
        return tokens;
    }

    private static void addTokenVariants(Collection<String> tokens, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        tokens.add(normalized);
        tokens.add(normalized.replace('_', '-'));
        tokens.add(normalized.replace('-', '_'));
        tokens.add(normalized.replace(' ', '-'));
    }
}
