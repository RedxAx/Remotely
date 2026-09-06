package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.network.NetworkMemberRole;
import redxax.oxy.remotely.ui.settings.data.ServerSettingsDataController;

import java.util.List;

public record NetworkCreationPlan(String name, int entryPort, List<Server> servers) {
    public NetworkCreationPlan {
        name = name == null ? "" : name.trim();
        servers = servers == null ? List.of() : List.copyOf(servers);
    }

    public record Server(String existingId, Object template, ServerScreenHost.HostView host, String location,
                         ServerSettingsDataController settings, boolean proxy, String route, NetworkMemberRole role,
                         int capacity, boolean reSync) {
        public Server {
            existingId = existingId == null ? "" : existingId.trim();
            location = location == null ? "" : location.trim();
            route = route == null ? "" : route.trim();
            role = proxy ? NetworkMemberRole.PROXY : role == null || role == NetworkMemberRole.PROXY ? NetworkMemberRole.GAMEPLAY : role;
            capacity = Math.max(0, capacity);
        }

        public boolean existing() {
            return !existingId.isBlank();
        }
    }
}
