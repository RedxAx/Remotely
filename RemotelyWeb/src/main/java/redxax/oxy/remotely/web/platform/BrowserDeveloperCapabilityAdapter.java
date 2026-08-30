package redxax.oxy.remotely.web.platform;

import restudio.rebase.backend.DeveloperCapabilityProvider;
import restudio.rebase.backend.DeveloperCapabilityProviders;
import restudio.rebase.backend.RemoteFileSystemProvider;
import restudio.rebase.backend.TerminalSessionProvider;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.Map;
import java.util.Objects;

public final class BrowserDeveloperCapabilityAdapter implements DeveloperCapabilityProviders.Adapter {
    private static volatile BrowserDeveloperCapabilityAdapter active;
    private final BrowserRemotelyServerApi api;

    public BrowserDeveloperCapabilityAdapter(BrowserRemotelyServerApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public static BrowserDeveloperCapabilityAdapter install(BrowserRemotelyServerApi api) {
        BrowserDeveloperCapabilityAdapter adapter = new BrowserDeveloperCapabilityAdapter(api);
        active = adapter;
        DeveloperCapabilityProviders.install(adapter);
        return adapter;
    }

    public static void clearIfCurrent(BrowserDeveloperCapabilityAdapter adapter) {
        if (active != adapter) {
            return;
        }
        active = null;
        DeveloperCapabilityProviders.install(DeveloperCapabilityProviders.Adapter.unavailable());
    }

    @Override
    public DeveloperCapabilityProvider resolve(Object context) {
        if (context instanceof DeveloperCapabilityProvider provider) {
            return provider;
        }
        String serverId = serverId(context);
        return serverId.isBlank() ? DeveloperCapabilityProvider.unavailable() : api.developer(serverId);
    }

    @Override
    public DeveloperCapabilityProvider local() {
        return DeveloperCapabilityProvider.unavailable();
    }

    @Override
    public RemoteFileSystemProvider filesystem(Object context) {
        String serverId = serverId(context);
        return serverId.isBlank() ? null : api.developer(serverId).logicalFilesystem();
    }

    public TerminalSessionProvider terminal(Object context, String id) {
        String serverId = serverId(context);
        if (serverId.isBlank() && context == null && id != null && id.startsWith("server:")) {
            serverId = id.substring("server:".length()).trim();
        }
        return serverId.isBlank() ? null : api.terminalSessionProvider(serverId);
    }

    private static String serverId(Object context) {
        if (context instanceof String value) {
            return value.trim();
        }
        if (context instanceof ServerModels.ClientServerView server) {
            if (server.identifier != null && !server.identifier.isBlank()) {
                return server.identifier;
            }
            return server.uuid == null ? "" : server.uuid;
        }
        if (context instanceof Map<?, ?> values) {
            Object identifier = values.get("identifier");
            if (identifier == null) identifier = values.get("serverId");
            return identifier == null ? "" : String.valueOf(identifier).trim();
        }
        return "";
    }
}
